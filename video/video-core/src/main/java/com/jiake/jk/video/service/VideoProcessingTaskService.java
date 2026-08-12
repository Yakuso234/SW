package com.jiake.jk.video.service;

public interface VideoProcessingTaskService {

    boolean claimVideoProcessing(Long videoId);

    void completeVideoProcessing(Long videoId, String processedVideoKey, String coverKey);

    void failVideoProcessing(Long videoId, String errorMessage);

    int recoverExpiredProcessingTasks();
}
