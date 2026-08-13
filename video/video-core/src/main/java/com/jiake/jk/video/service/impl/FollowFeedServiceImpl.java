package com.jiake.jk.video.service.impl;

import com.jiake.jk.common.exception.YHClientException;
import com.jiake.jk.common.exception.YHServerException;
import com.jiake.jk.common.response.Result;
import com.jiake.jk.common.utils.AWSUtils;
import com.jiake.jk.common.utils.SnowflakeUtils;
import com.jiake.jk.user.feign.UserFollowPrivateClient;
import com.jiake.jk.user.feign.UserPrivateClient;
import com.jiake.jk.user.pojo.response.FollowerIdPageResponse;
import com.jiake.jk.user.pojo.response.UserInfoInListResponse;
import com.jiake.jk.video.mapper.VideoFeedInboxMapper;
import com.jiake.jk.video.mapper.multi.VideoMultiMapper;
import com.jiake.jk.video.mapstruct.VideoMapStruct;
import com.jiake.jk.video.pojo.entity.VideoFeedInbox;
import com.jiake.jk.video.pojo.entity.multi.VideoWithInteractionStatus;
import com.jiake.jk.video.pojo.mq.VideoPublishedMessage;
import com.jiake.jk.video.pojo.response.FollowFeedResponse;
import com.jiake.jk.video.pojo.response.VideoMainResponse;
import com.jiake.jk.video.service.FollowFeedService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FollowFeedServiceImpl implements FollowFeedService {

    private static final int FOLLOWER_PAGE_SIZE = 500;
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 20;

    private final VideoFeedInboxMapper videoFeedInboxMapper;
    private final VideoMultiMapper videoMultiMapper;
    private final UserFollowPrivateClient userFollowPrivateClient;
    private final UserPrivateClient userPrivateClient;
    private final SnowflakeUtils snowflakeUtils;
    private final AWSUtils awsUtils;

    @Override
    public void fanoutPublishedVideo(VideoPublishedMessage message) {
        if (message.getPublishedAt() == null) {
            throw new YHServerException("发布事件缺少发布时间");
        }
        Long lastFollowId = null;
        do {
            Result<FollowerIdPageResponse> result = userFollowPrivateClient.getFollowerIds(
                    message.getCreatorId(), lastFollowId, FOLLOWER_PAGE_SIZE);
            if (result.isError() || result.getData() == null) {
                throw new YHServerException("获取粉丝列表失败: " + result.getMsg());
            }
            FollowerIdPageResponse page = result.getData();
            insertInboxItems(page.getFollowerIds(), message);
            lastFollowId = page.getNextFollowId();
            if (!page.isHasMore()) {
                return;
            }
        } while (lastFollowId != null);
    }

    @Override
    public FollowFeedResponse getFollowFeed(Long recipientId, String cursor, Integer pageSize) {
        FeedCursor feedCursor = decodeCursor(cursor);
        int limit = normalizePageSize(pageSize) + 1;
        List<VideoWithInteractionStatus> rows = videoMultiMapper.selectFollowFeed(
                recipientId, feedCursor.publishedAt(), feedCursor.feedId(), limit);
        boolean hasMore = rows.size() == limit;
        if (hasMore) {
            rows = rows.subList(0, limit - 1);
        }
        List<VideoMainResponse> items = enrich(rows);
        String nextCursor = hasMore && !rows.isEmpty() ? encodeCursor(rows.get(rows.size() - 1)) : null;
        return new FollowFeedResponse(items, nextCursor, hasMore);
    }

    private void insertInboxItems(List<Long> followerIds, VideoPublishedMessage message) {
        if (followerIds == null || followerIds.isEmpty()) {
            return;
        }
        List<VideoFeedInbox> inboxItems = new ArrayList<>(followerIds.size());
        for (Long followerId : followerIds) {
            VideoFeedInbox item = new VideoFeedInbox();
            item.setId(snowflakeUtils.nextId());
            item.setRecipientId(followerId);
            item.setVideoId(message.getVideoId());
            item.setCreatorId(message.getCreatorId());
            item.setPublishedAt(message.getPublishedAt());
            inboxItems.add(item);
        }
        videoFeedInboxMapper.insertIgnoreBatch(inboxItems);
    }

    private List<VideoMainResponse> enrich(List<VideoWithInteractionStatus> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> creatorIds = rows.stream().map(VideoWithInteractionStatus::getCreatorId).distinct().toList();
        Result<List<UserInfoInListResponse>> userResult = userPrivateClient.getUserInfoInList(creatorIds);
        if (userResult.isError()) {
            throw new YHServerException("获取创作者信息失败: " + userResult.getMsg());
        }
        Map<Long, UserInfoInListResponse> users = userResult.getData().stream()
                .collect(Collectors.toMap(UserInfoInListResponse::getId, item -> item));
        return rows.stream().map(row -> {
            VideoMainResponse response = VideoMapStruct.INSTANCE.toVideoMainResponse(row);
            response.setUrl(awsUtils.generateAccessUrl(response.getUrl()));
            UserInfoInListResponse user = users.get(response.getCreatorId());
            if (user != null) {
                response.setCreatorName(user.getName());
                response.setCreatorAvatar(user.getAvatarUrl());
            }
            response.setIsFollowed(true);
            return response;
        }).toList();
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null) {
            return DEFAULT_PAGE_SIZE;
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new YHClientException("pageSize 必须在 1 到 20 之间");
        }
        return pageSize;
    }

    private FeedCursor decodeCursor(String cursor) {
        if (!StringUtils.hasText(cursor)) {
            return new FeedCursor(null, null);
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int delimiter = decoded.lastIndexOf('|');
            return new FeedCursor(LocalDateTime.parse(decoded.substring(0, delimiter)),
                    Long.parseLong(decoded.substring(delimiter + 1)));
        } catch (RuntimeException exception) {
            throw new YHClientException("分页游标无效");
        }
    }

    private String encodeCursor(VideoWithInteractionStatus last) {
        String value = last.getPublishedAt() + "|" + last.getFeedId();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private record FeedCursor(LocalDateTime publishedAt, Long feedId) {
    }
}
