package com.jiake.jk.video.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiake.jk.common.exception.YHClientException;
import com.jiake.jk.common.exception.YHServerException;
import com.jiake.jk.common.response.Result;
import com.jiake.jk.common.utils.AWSUtils;
import com.jiake.jk.common.utils.SnowflakeUtils;
import com.jiake.jk.common.trace.TraceContext;
import com.jiake.jk.user.feign.UserFollowPrivateClient;
import com.jiake.jk.user.feign.UserPreferencesPrivateClient;
import com.jiake.jk.user.feign.UserPrivateClient;
import com.jiake.jk.user.pojo.response.UserInfoInListResponse;
import com.jiake.jk.video.constant.RabbitMQConstant;
import com.jiake.jk.video.mapper.*;
import com.jiake.jk.video.mapstruct.VideoMapStruct;
import com.jiake.jk.video.pojo.entity.MessageOutbox;
import com.jiake.jk.video.pojo.entity.Video;
import com.jiake.jk.video.pojo.entity.VideoTag;
import com.jiake.jk.video.mapper.multi.VideoMultiMapper;
import com.jiake.jk.video.pojo.entity.multi.VideoTagMp;
import com.jiake.jk.video.pojo.entity.multi.VideoWithFavorite;
import com.jiake.jk.video.pojo.entity.multi.VideoWithInteractionStatus;
import com.jiake.jk.video.pojo.entity.multi.VideoWithLike;
import com.jiake.jk.video.pojo.mq.VideoReviewMessage;
import com.jiake.jk.video.pojo.entity.VideoUploadTask;
import com.jiake.jk.video.pojo.entity.VideoProcessingTask;
import com.jiake.jk.video.pojo.request.DeletePublishedVideoRequest;
import com.jiake.jk.video.pojo.request.GetPresignUrlRequest;
import com.jiake.jk.video.pojo.response.*;
import com.jiake.jk.video.pojo.request.PostVideoMessageRequest;
import com.jiake.jk.video.service.VideoService;
import com.jiake.jk.video.service.OutboxMessagePublisher;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.SearchResp;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;

@Service
public class VideoServiceImpl implements VideoService {

    private static final int DEFAULT_FEED_PAGE_SIZE = 10;
    private static final int MAX_FEED_PAGE_SIZE = 20;

    @Autowired
    private VideoMapper videoMapper;
    @Autowired
    private VideoTagMapper videoTagMapper;
    @Autowired
    private VideoTagMpMapper videoTagMpMapper;
    @Autowired
    private OutboxMessagePublisher outboxMessagePublisher;
    @Autowired
    private SnowflakeUtils snowflakeUtils;
    @Autowired
    private UserPreferencesPrivateClient userPreferencesPrivateClient;
    @Autowired
    private VideoMultiMapper videoMultiMapper;
    @Autowired
    private MilvusClientV2 milvusClient;
    @Autowired
    private UserFollowPrivateClient userFollowPrivateClient;
    @Autowired
    private AWSUtils awsUtils;
    @Autowired
    private VideoUploadTaskMapper videoUploadTaskMapper;
    @Autowired
    private UserPrivateClient userPrivateClient;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private MessageOutBoxMapper messageOutBoxMapper;
    @Autowired
    private VideoProcessingTaskMapper videoProcessingTaskMapper;
    @Autowired
    private ThreadPoolExecutor videoExecutor;
    @Autowired
    private VideoAnalyticsMapper videoAnalyticsMapper;

    @Override
    public List<VideoMainResponse> getVideos(Long userId) {
        List<VideoWithInteractionStatus> statusList =
                videoMultiMapper.selectMainRandom(userId);

        return enrichVideoMainResponses(userId, statusList);
    }

    private List<VideoMainResponse> enrichVideoMainResponses(Long userId,
                                                              List<VideoWithInteractionStatus> statusList) {
        if (statusList.isEmpty()) {
            return Collections.emptyList();
        }
        List<VideoMainResponse> responses = statusList.stream()
                .map(status -> {
                    VideoMainResponse response = VideoMapStruct.INSTANCE.toVideoMainResponse(status);
                    response.setUrl(awsUtils.generateAccessUrl(response.getUrl()));
                    return response;
                })
                .toList();

        List<Long> creatorIdList = responses.stream()
                .map(VideoMainResponse::getCreatorId)
                .distinct()
                .toList();

        CompletableFuture<Map<Long, UserInfoInListResponse>> userInfoFuture =
                CompletableFuture.supplyAsync(
                        () -> fetchAndCacheUserInfo(creatorIdList),
                        videoExecutor
                );

        CompletableFuture<Map<Long, Boolean>> followStatusFuture =
                userId == null
                        ? CompletableFuture.completedFuture(Map.of())
                        : CompletableFuture.supplyAsync(
                        () -> fetchAndCacheFollowStatus(userId, creatorIdList),
                        videoExecutor
                );

        Map<Long, UserInfoInListResponse> idToUserInfoMap = userInfoFuture.join();
        Map<Long, Boolean> idToFollowStatusMap = followStatusFuture.join();

        responses.forEach(response -> {
            UserInfoInListResponse userInfo = idToUserInfoMap.get(response.getCreatorId());
            if (userInfo != null) {
                response.setCreatorAvatar(userInfo.getAvatarUrl());
                response.setCreatorName(userInfo.getName());
            }

            if (userId != null) {
                response.setIsFollowed(
                        idToFollowStatusMap.getOrDefault(response.getCreatorId(), false)
                );
            }
        });

        return responses;
    }

    @Override
    public PublishedFeedResponse getPublishedFeed(Long userId, String cursor, Integer pageSize) {
        FeedCursor feedCursor = decodeFeedCursor(cursor);
        int limit = normalizeFeedPageSize(pageSize) + 1;
        List<VideoWithInteractionStatus> rows = videoMultiMapper.selectPublishedFeed(
                userId, feedCursor.publishedAt(), feedCursor.videoId(), limit);
        boolean hasMore = rows.size() == limit;
        if (hasMore) {
            rows = rows.subList(0, limit - 1);
        }
        List<VideoMainResponse> items = enrichVideoMainResponses(userId, rows);
        String nextCursor = hasMore && !rows.isEmpty()
                ? encodeFeedCursor(rows.get(rows.size() - 1))
                : null;
        return new PublishedFeedResponse(items, nextCursor, hasMore);
    }

    private int normalizeFeedPageSize(Integer pageSize) {
        if (pageSize == null) {
            return DEFAULT_FEED_PAGE_SIZE;
        }
        if (pageSize < 1 || pageSize > MAX_FEED_PAGE_SIZE) {
            throw new YHClientException("pageSize 必须在 1 到 20 之间");
        }
        return pageSize;
    }

    private FeedCursor decodeFeedCursor(String cursor) {
        if (!StringUtils.hasText(cursor)) {
            return new FeedCursor(null, null);
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int delimiter = decoded.lastIndexOf('|');
            if (delimiter <= 0 || delimiter == decoded.length() - 1) {
                throw new IllegalArgumentException("invalid delimiter");
            }
            return new FeedCursor(LocalDateTime.parse(decoded.substring(0, delimiter)),
                    Long.parseLong(decoded.substring(delimiter + 1)));
        } catch (IllegalArgumentException exception) {
            throw new YHClientException("分页游标无效");
        }
    }

    private String encodeFeedCursor(VideoWithInteractionStatus lastVideo) {
        if (lastVideo.getPublishedAt() == null) {
            throw new YHServerException("无法生成下一页游标");
        }
        String value = lastVideo.getPublishedAt() + "|" + lastVideo.getId();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private record FeedCursor(LocalDateTime publishedAt, Long videoId) {
    }

    private Map<Long, UserInfoInListResponse> fetchAndCacheUserInfo(List<Long> creatorIdList) {
        Map<Long, UserInfoInListResponse> idToUserInfoMap = new HashMap<>();
        if (!creatorIdList.isEmpty()) {
            Result<List<UserInfoInListResponse>> result = userPrivateClient.getUserInfoInList(creatorIdList);
            List<UserInfoInListResponse> userInfoList = result.getData();
            for (UserInfoInListResponse userInfo : userInfoList) {
                idToUserInfoMap.put(userInfo.getId(), userInfo);
            }
        }
        return idToUserInfoMap;
    }

    private Map<Long, Boolean> fetchAndCacheFollowStatus(Long userId, List<Long> creatorIdList) {
        Map<Long, Boolean> idToFollowStatusMap = new HashMap<>();
        if (!creatorIdList.isEmpty()) {
            Result<List<Boolean>> result = userFollowPrivateClient.getFollowStatus(userId, creatorIdList);
            List<Boolean> followStatusList = result.getData();
            for (int i = 0; i < creatorIdList.size(); i++) {
                Long creatorId = creatorIdList.get(i);
                Boolean followStatus = followStatusList.get(i);
                idToFollowStatusMap.put(creatorId, followStatus);
            }
        }
        return idToFollowStatusMap;
    }

    @Override
    public VideoMainResponse getVideo(Long userId, Long videoId) {
        VideoWithInteractionStatus videoWithInteractionStatus = videoMultiMapper.selectMainOne(videoId, userId);
        if (videoWithInteractionStatus == null) {
            return null;
        }

        VideoMainResponse videoMainResponse = VideoMapStruct.INSTANCE.toVideoMainResponse(videoWithInteractionStatus);
        videoMainResponse.setUrl(awsUtils.generateAccessUrl(videoMainResponse.getUrl()));

        UserInfoInListResponse userInfo = fetchAndCacheUserInfo(List.of(videoMainResponse.getCreatorId()))
                .get(videoMainResponse.getCreatorId());
        if (userInfo != null) {
            videoMainResponse.setCreatorName(userInfo.getName());
            videoMainResponse.setCreatorAvatar(userInfo.getAvatarUrl());
        }

        if (userId != null) {
            Boolean followStatus = fetchAndCacheFollowStatus(userId, List.of(videoMainResponse.getCreatorId()))
                    .get(videoMainResponse.getCreatorId());
            videoMainResponse.setIsFollowed(Boolean.TRUE.equals(followStatus));
        } else {
            videoMainResponse.setIsFollowed(false);
        }

        return videoMainResponse;
    }

    @Override
    public List<VideoSearchResponse> searchVideos(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return Collections.emptyList();
        }

        List<VideoSearchResponse> videoSearchResponseList =
                videoMapper.searchPublishedVideoByDescriptionPrefix(keyword.trim());
        if (videoSearchResponseList.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> creatorIdList = videoSearchResponseList.stream()
                .map(VideoSearchResponse::getCreatorId)
                .distinct()
                .toList();
        Result<Map<Long, String>> result = userPrivateClient.getNameBatch(creatorIdList);
        if (result.isError()) {
            throw new YHServerException(result.getMsg());
        }
        Map<Long, String> idToNameMap = result.getData();

        videoSearchResponseList.forEach(response -> {
            response.setCreatorName(idToNameMap.get(response.getCreatorId()));
            response.setCoverUrl(awsUtils.generateAccessUrl(response.getCoverUrl()));
        });

        return videoSearchResponseList;
    }

    @Override
    @Transactional
    public boolean recordView(Long userId, Long videoId) {
        Long creatorId = videoAnalyticsMapper.selectPublishedCreatorId(videoId);
        if (creatorId == null) {
            throw new YHClientException("视频不存在或尚未发布！");
        }
        boolean created = videoAnalyticsMapper.insertDailyView(snowflakeUtils.nextId(), videoId, creatorId, userId, LocalDate.now()) == 1;
        if (created) {
            videoAnalyticsMapper.incrementView(videoId);
        }
        return created;
    }

    @Override
    public CreatorAnalyticsOverviewResponse getCreatorAnalytics(Long userId) {
        CreatorAnalyticsOverviewResponse overview = videoAnalyticsMapper.selectOverview(userId);
        if (overview == null) {
            overview = new CreatorAnalyticsOverviewResponse();
        }
        LocalDate fromDate = LocalDate.now().minusDays(6);
        Map<String, Long> viewsByDate = videoAnalyticsMapper.selectDailyViews(userId, fromDate).stream()
                .collect(Collectors.toMap(CreatorAnalyticsTrendResponse::getDate, CreatorAnalyticsTrendResponse::getViews));
        List<CreatorAnalyticsTrendResponse> trends = new ArrayList<>(7);
        for (int offset = 0; offset < 7; offset++) {
            LocalDate date = fromDate.plusDays(offset);
            trends.add(new CreatorAnalyticsTrendResponse(date.toString(), viewsByDate.getOrDefault(date.toString(), 0L)));
        }
        overview.setTrends(trends);
        return overview;
    }

    @Override
    public String startUploadPart(Long userId, Integer totalChunks) {

        String key = awsUtils.generateKey();

        // 调用开启分片上传（该分片有可能不被记录然后不会被清理）
        String uploadId = awsUtils.createMultipartUpload(key, "video/mp4");

        // 插入 task 对象
        VideoUploadTask task = new VideoUploadTask();
        task.setUserId(userId);
        task.setKey(key);
        task.setTotalChunks(totalChunks);
        task.setUploadId(uploadId);
        task.setUploadType(VideoUploadTask.UploadType.MULTIPART);
        task.setStatus(VideoUploadTask.UploadStatus.UPLOADING);
        task.setExpireAt(LocalDateTime.now().plusMinutes(15));

        videoUploadTaskMapper.insert(task);

        return task.getId().toString();
    }

    @Override
    public Map<Integer, String> presignUploadPart(Long userId, GetPresignUrlRequest getPresignUrlRequest) {
        VideoUploadTask videoUploadTask = videoUploadTaskMapper.selectForPresign(getPresignUrlRequest.getTaskId());

        if (videoUploadTask == null || !videoUploadTask.getUserId().equals(userId)) {
            throw new YHClientException("异常的上传任务！");
        }

        videoUploadTaskMapper.update(new LambdaUpdateWrapper<VideoUploadTask>()
                .set(VideoUploadTask::getExpireAt, LocalDateTime.now().plusMinutes(10))
                .eq(VideoUploadTask::getId, videoUploadTask.getId())
                .eq(VideoUploadTask::getStatus, VideoUploadTask.UploadStatus.UPLOADING));

        return awsUtils.presignUploadPart(videoUploadTask.getKey(), videoUploadTask.getUploadId(), getPresignUrlRequest.getPartNumberList(), Duration.ofMinutes(10));
    }

    @Override
    public PresignPutObjectResponse presignPutObject(Long userId) {
        // 插入 task 对象
        VideoUploadTask task = new VideoUploadTask();
        task.setUserId(userId);
        task.setKey(awsUtils.generateKey());
        task.setTotalChunks(1);
        task.setUploadType(VideoUploadTask.UploadType.NORMAL);
        task.setStatus(VideoUploadTask.UploadStatus.UPLOADING);
        task.setExpireAt(LocalDateTime.now().plusMinutes(15));

        videoUploadTaskMapper.insert(task);

        return new PresignPutObjectResponse(task.getId(), awsUtils.presignPutObject(task.getKey(), "video/mp4", Duration.ofMinutes(15)));
    }

    @Override
    @Transactional
    public PostVideoEndResponse postVideoEnd(Long userId, Long taskId) {
        VideoUploadTask videoUploadTask = videoUploadTaskMapper.selectForComplete(taskId);

        if (videoUploadTask == null || !videoUploadTask.getUserId().equals(userId)) {
            throw new YHClientException("异常的上传任务！");
        }

        if (videoUploadTask.getUploadType() == VideoUploadTask.UploadType.MULTIPART) {
            List<Integer> uploadedPartNumberList = awsUtils.completeMultipartUpload(
                    videoUploadTask.getKey(),
                    videoUploadTask.getUploadId(),
                    videoUploadTask.getTotalChunks()
            );

            // 代表实际上仍有分片未上传
            if (!uploadedPartNumberList.isEmpty()) {
                return new PostVideoEndResponse(uploadedPartNumberList, null);
            }
        }

        // 已经上传完毕，正式插入视频表
        Video video = new Video();
        video.setCreatorId(userId);
        video.setUrl(videoUploadTask.getKey());
        video.setStatus(Video.VideoStatus.DRAFT);

        videoMapper.insert(video);

        // 修改任务表状态
        videoUploadTaskMapper.update(new LambdaUpdateWrapper<VideoUploadTask>()
                .set(VideoUploadTask::getStatus, VideoUploadTask.UploadStatus.COMPLETED)
                .eq(VideoUploadTask::getId, taskId));

        return new PostVideoEndResponse(null, video.getId());
    }

    private List<VideoMainResponse> getRecommendedVideos(Long userId) {
        // 获取用户视频偏好向量
        float[] vector = userPreferencesPrivateClient.getUserVideoPreferences(userId).getData();

        // 根据用户视频偏好向量搜索相似视频
        SearchResp searchR = milvusClient.search(SearchReq.builder()
                .collectionName("video_collection")
                .annsField("recommend_feature")
                .data(Collections.singletonList(new FloatVec(vector)))
                .topK(10)
                .outputFields(List.of("video_id"))
                .searchParams(Map.of("metric_type", "COSINE", "params", Map.of("nprobe", 10)))
                .build());
        List<List<SearchResp.SearchResult>> searchResults = searchR.getSearchResults();
        for (List<SearchResp.SearchResult> results : searchResults) {
            for (SearchResp.SearchResult result : results) {
                System.out.println(result.getId());
            }
        }
        return null;
    }

    @Override
    @Transactional
    public void postVideoMessage(Long userId, PostVideoMessageRequest postVideoMessageRequest) throws IOException {
        /* 获取所需视频数据 */
        Video video = videoMapper.selectOne(new LambdaQueryWrapper<Video>()
                .select(Video::getId, Video::getCreatorId, Video::getUrl, Video::getStatus)
                .eq(Video::getId, postVideoMessageRequest.getVideoId()));

        /* 校验 */
        if (video == null || !video.getCreatorId().equals(userId)) {
            throw new YHClientException("上传视频数据异常！");
        }
        if (video.getStatus() != Video.VideoStatus.DRAFT) {
            throw new YHClientException("请勿重复提交！");
        }

        /* 乐观锁 */
        String coverObjectKey = "cover/" + postVideoMessageRequest.getVideoId().toString();
        int result = videoMapper.update(null, new LambdaUpdateWrapper<Video>()
                .set(Video::getStatus, Video.VideoStatus.PENDING_REVIEW)
                .set(Video::getCoverUrl, coverObjectKey)
                .set(Video::getDescription, postVideoMessageRequest.getDescription())
                .eq(Video::getId, postVideoMessageRequest.getVideoId())
                .eq(Video::getStatus, Video.VideoStatus.DRAFT));
        if (result == 0) {
            throw new YHClientException("请勿重复提交！");
        }

        /* 上传封面（使用固定key解决文件上传幂等问题 ） */
        /* 优化可改为直传或者拆分出当前数据库事务 */
        awsUtils.putObject(coverObjectKey, postVideoMessageRequest.getCover());

        /* 视频标签 */
        handleTag(postVideoMessageRequest);

        /* 发送消息到消息队列 */
        // 构建消息对象
        VideoReviewMessage videoReviewMessage = new VideoReviewMessage();
        videoReviewMessage.setTraceId(TraceContext.getOrCreateTraceId());
        videoReviewMessage.setVideoId(video.getId());
        videoReviewMessage.setVideoUrl(video.getUrl());
        videoReviewMessage.setTagNameList(postVideoMessageRequest.getAddedTagList());
        videoReviewMessage.setDescription(postVideoMessageRequest.getDescription());

        // 插入本地消息表
        MessageOutbox messageOutbox = new MessageOutbox();
        messageOutbox.setBusinessId(video.getId());
        messageOutbox.setExchangeName("");
        messageOutbox.setRoutingKey(RabbitMQConstant.VIDEO_REVIEW_QUEUE);
        messageOutbox.setMessageBody(objectMapper.writeValueAsString(videoReviewMessage));
        messageOutbox.setStatus(MessageOutbox.OutboxStatus.PENDING);
        messageOutBoxMapper.insert(messageOutbox);

        VideoProcessingTask processingTask = new VideoProcessingTask();
        processingTask.setVideoId(video.getId());
        processingTask.setStatus(VideoProcessingTask.ProcessingStatus.PENDING);
        processingTask.setRetryCount(0);
        videoProcessingTaskMapper.insert(processingTask);

        // 注册事务提交后发送消息
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        outboxMessagePublisher.publish(messageOutbox.getId());
                    }
                }
        );
    }

    private void handleTag(PostVideoMessageRequest postVideoMessageRequest) {
        // 查询已存在标签
        List<VideoTag> videoTagList = videoTagMapper
                .selectSimplyByNames(postVideoMessageRequest.getAddedTagList());

        // 已存在标签 name -> id
        Map<String, Long> existTagMap = videoTagList.stream()
                .collect(Collectors.toMap(VideoTag::getName, VideoTag::getId));

        // 分类：已存在 / 不存在
        List<Long> existTagIdList = new ArrayList<>();
        List<String> notExistTagNameList = new ArrayList<>();
        for (String tagName : postVideoMessageRequest.getAddedTagList()) {
            if (existTagMap.containsKey(tagName)) {
                existTagIdList.add(existTagMap.get(tagName));
            } else {
                notExistTagNameList.add(tagName);
            }
        }

        // 插入不存在的标签
        List<Long> newTagIdList = new ArrayList<>();
        if (!notExistTagNameList.isEmpty()) {
            List<VideoTag> newTags = notExistTagNameList.stream().map(name -> {
                VideoTag tag = new VideoTag();
                tag.setId(snowflakeUtils.nextId());
                tag.setName(name);
                tag.setCreatedTime(LocalDateTime.now());
                return tag;
            }).toList();
            // 批量插入
            videoTagMapper.insertBatch(newTags);
            // 插入后需要拿到 id
            newTagIdList = newTags.stream()
                    .map(VideoTag::getId)
                    .toList();
        }

        // 汇总所有标签ID
        List<Long> allTagIdList = new ArrayList<>();
        allTagIdList.addAll(existTagIdList);
        allTagIdList.addAll(newTagIdList);

        // 建立视频-标签关联
        videoTagMpMapper.insertBatch(postVideoMessageRequest.getVideoId(), allTagIdList);
    }

    @Override
    public void putVideoStatusToPublished(Long videoId) {
        transitionVideoStatus(videoId, Video.VideoStatus.PROCESSING, Video.VideoStatus.PUBLISHED);
    }

    @Override
    public boolean transitionVideoStatus(Long videoId, Video.VideoStatus expectedStatus, Video.VideoStatus targetStatus) {
        if (!isAllowedTransition(expectedStatus, targetStatus)) {
            throw new YHClientException("非法的视频状态迁移");
        }
        return videoMapper.update(null, new LambdaUpdateWrapper<Video>()
                .set(Video::getStatus, targetStatus)
                .set(targetStatus == Video.VideoStatus.PUBLISHED, Video::getPublishedAt, LocalDateTime.now())
                .eq(Video::getId, videoId)
                .eq(Video::getStatus, expectedStatus)) == 1;
    }

    private boolean isAllowedTransition(Video.VideoStatus expectedStatus, Video.VideoStatus targetStatus) {
        return (expectedStatus == Video.VideoStatus.PENDING_REVIEW
                && (targetStatus == Video.VideoStatus.REVIEWING || targetStatus == Video.VideoStatus.PROCESSING
                || targetStatus == Video.VideoStatus.REJECTED))
                || (expectedStatus == Video.VideoStatus.REVIEWING
                && (targetStatus == Video.VideoStatus.PROCESSING || targetStatus == Video.VideoStatus.REJECTED))
                || (expectedStatus == Video.VideoStatus.PROCESSING
                && (targetStatus == Video.VideoStatus.PUBLISHED || targetStatus == Video.VideoStatus.REJECTED));
    }

    @Override
    public List<GetUploadedVideoResponse> getUploadedVideo(Long userId) {
        return videoMapper.selectUploadedVideoByUserId(userId)
                .stream()
                .map(VideoMapStruct.INSTANCE::toGetUploadedVideoResponse)
                .toList();
    }

    @Override
    public List<GetPublishedVideoResponse> getPublishedVideo(Long userId, Long lastMinId) {
        return videoMapper.selectPublishedVideoByUserId(userId, lastMinId)
                .stream()
                .map(v -> {
                    GetPublishedVideoResponse response = VideoMapStruct.INSTANCE.toGetPublishedVideoResponse(v);
                    response.setUrl(awsUtils.generateAccessUrl(response.getUrl()));
                    response.setCoverUrl(awsUtils.generateAccessUrl(response.getCoverUrl()));
                    return response;
                })
                .toList();
    }

    @Override
    @Transactional
    public void deletePublishedVideoBatch(Long userId, DeletePublishedVideoRequest deletePublishedVideoRequest) {
        if (deletePublishedVideoRequest == null) {
            throw new YHClientException("请选择要删除的视频！");
        }
        List<Long> videoIdList = deletePublishedVideoRequest.getIds();
        if (videoIdList == null || videoIdList.isEmpty()) {
            throw new YHClientException("请选择要删除的视频！");
        }

        List<Long> distinctVideoIdList = videoIdList.stream().distinct().toList();
        List<Video> videoList = videoMapper.selectList(new LambdaQueryWrapper<Video>()
                .select(Video::getId, Video::getCreatorId, Video::getStatus)
                .in(Video::getId, distinctVideoIdList));

        if (videoList.size() != distinctVideoIdList.size()) {
            throw new YHClientException("存在视频不存在或无权限删除！");
        }
        boolean hasInvalidVideo = videoList.stream()
                .anyMatch(video -> !video.getCreatorId().equals(userId)
                        || video.getStatus() != Video.VideoStatus.PUBLISHED);
        if (hasInvalidVideo) {
            throw new YHClientException("只能删除已发布的视频！");
        }

        videoTagMpMapper.delete(new LambdaQueryWrapper<VideoTagMp>()
                .in(VideoTagMp::getVideoId, distinctVideoIdList));

        int deleted = videoMapper.delete(new LambdaQueryWrapper<Video>()
                .in(Video::getId, distinctVideoIdList)
                .eq(Video::getCreatorId, userId)
                .eq(Video::getStatus, Video.VideoStatus.PUBLISHED));
        if (deleted != distinctVideoIdList.size()) {
            throw new YHClientException("视频状态已变更，请刷新后重试！");
        }
    }

    @Override
    public List<GetProcessingVideoResponse> getProcessingVideo(Long userId, Long lastMinId) {
        return videoMapper.selectProcessingVideoByUserId(userId, lastMinId)
                .stream()
                .map(VideoMapStruct.INSTANCE::toGetProcessingVideoResponse)
                .toList();
    }

    @Override
    public VideoProcessingStatusResponse getVideoProcessingStatus(Long userId, Long videoId) {
        Video video = videoMapper.selectOne(new LambdaQueryWrapper<Video>()
                .select(Video::getId, Video::getStatus)
                .eq(Video::getId, videoId)
                .eq(Video::getCreatorId, userId));
        if (video == null) {
            throw new YHClientException("视频不存在或无访问权限");
        }

        VideoProcessingTask task = videoProcessingTaskMapper.selectOne(
                new LambdaQueryWrapper<VideoProcessingTask>()
                        .select(VideoProcessingTask::getStatus,
                                VideoProcessingTask::getRetryCount,
                                VideoProcessingTask::getLeaseExpireAt,
                                VideoProcessingTask::getErrorMessage,
                                VideoProcessingTask::getUpdatedAt)
                        .eq(VideoProcessingTask::getVideoId, videoId));
        if (task == null) {
            return new VideoProcessingStatusResponse(videoId, video.getStatus(), null,
                    null, null, null, null);
        }
        return new VideoProcessingStatusResponse(videoId, video.getStatus(), task.getStatus(),
                task.getRetryCount(), task.getLeaseExpireAt(), task.getErrorMessage(), task.getUpdatedAt());
    }

    @Override
    public List<GetRejectedVideoResponse> getRejectedVideo(Long userId, Long lastMinId) {
        return videoMapper.selectRejectedVideoByUserId(userId, lastMinId)
                .stream()
                .map(VideoMapStruct.INSTANCE::toGetRejectedVideoResponse)
                .toList();
    }

    @Override
    public List<GetLikeVideoResponse> getLikeVideo(Long userId, Long lastMinId) {
        // 获取数据
        List<VideoWithLike> videoWithLikeList = videoMapper.selectLikeVideoByUserId(userId, lastMinId);
        if (videoWithLikeList.isEmpty()) {
            return Collections.emptyList();
        }

        // 转换格式
        List<GetLikeVideoResponse> likeVideoResponseList = videoMapper.selectLikeVideoByUserId(userId, lastMinId)
                .stream()
                .map(VideoMapStruct.INSTANCE::toGetLikeVideoResponse)
                .toList();

        // 提取 creatorId
        List<Long> creatorIdList = likeVideoResponseList.stream()
                .map(GetLikeVideoResponse::getCreatorId)
                .toList();

        // 获取对应 creatorName
        Result<Map<Long, String>> result = userPrivateClient.getNameBatch(creatorIdList);
        if (result.isError()) {
            throw new YHServerException(result.getMsg());
        }
        Map<Long, String> idToNameMap = result.getData();

        // 完善数据
        likeVideoResponseList.forEach(response -> {
            response.setUrl(awsUtils.generateAccessUrl(response.getUrl()));
            response.setCoverUrl(awsUtils.generateAccessUrl(response.getCoverUrl()));
            response.setCreatorName(idToNameMap.get(response.getCreatorId()));
        });

        return likeVideoResponseList;
    }

    @Override
    public List<GetFavoriteVideoResponse> getFavoriteVideo(Long userId, Long lastMinId) {
        // 获取数据
        List<VideoWithFavorite> videoWithFavoriteList = videoMapper.selectFavoriteVideoByUserId(userId, lastMinId);
        if (videoWithFavoriteList.isEmpty()) {
            return Collections.emptyList();
        }

        // 转换格式
        List<GetFavoriteVideoResponse> favoriteVideoResponseList = videoWithFavoriteList
                .stream()
                .map(VideoMapStruct.INSTANCE::toGetFavoriteVideoResponse)
                .toList();

        // 提取 creatorId
        List<Long> creatorIdList = favoriteVideoResponseList.stream()
                .map(GetFavoriteVideoResponse::getCreatorId)
                .toList();

        // 获取对应 creatorName
        Result<Map<Long, String>> result = userPrivateClient.getNameBatch(creatorIdList);
        if (result.isError()) {
            throw new YHServerException(result.getMsg());
        }
        Map<Long, String> idToNameMap = result.getData();

        // 完善数据
        favoriteVideoResponseList.forEach(response -> {
            response.setCreatorName(idToNameMap.get(response.getCreatorId()));
        });

        return favoriteVideoResponseList;
    }
}
