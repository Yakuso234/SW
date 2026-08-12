package com.jiake.jk.video.service;

import com.jiake.jk.video.pojo.request.GetPresignUrlRequest;
import com.jiake.jk.video.pojo.request.DeletePublishedVideoRequest;
import com.jiake.jk.video.pojo.response.*;
import com.jiake.jk.video.pojo.request.PostVideoMessageRequest;
import com.jiake.jk.video.pojo.entity.Video;

import java.util.List;
import java.util.Map;

public interface VideoService {
    List<VideoMainResponse> getVideos(Long userId);

    VideoMainResponse getVideo(Long userId, Long videoId);

    List<VideoSearchResponse> searchVideos(String keyword);

    String startUploadPart(Long userId, Integer totalChunks);

    Map<Integer, String> presignUploadPart(Long userId, GetPresignUrlRequest getPresignUrlRequest);

    PresignPutObjectResponse presignPutObject(Long userId);

    PostVideoEndResponse postVideoEnd(Long userId, Long taskId);

    void postVideoMessage(Long userId, PostVideoMessageRequest postVideoMessageRequest) throws Exception;

    void putVideoStatusToPublished(Long videoId);

    boolean transitionVideoStatus(Long videoId, Video.VideoStatus expectedStatus, Video.VideoStatus targetStatus);

    List<GetUploadedVideoResponse> getUploadedVideo(Long userId);

    List<GetPublishedVideoResponse> getPublishedVideo(Long userId, Long lastMinId);

    void deletePublishedVideoBatch(Long userId, DeletePublishedVideoRequest deletePublishedVideoRequest);

    List<GetProcessingVideoResponse> getProcessingVideo(Long userId, Long lastMinId);

    List<GetRejectedVideoResponse> getRejectedVideo(Long userId, Long lastMinId);

    List<GetLikeVideoResponse> getLikeVideo(Long userId, Long lastMinId);

    List<GetFavoriteVideoResponse> getFavoriteVideo(Long userId, Long lastMinId);
}
