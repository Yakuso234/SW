package com.jiake.jk.video.service;

public interface VideoProcessingTaskService {

    boolean claimVideoProcessing(Long videoId);

    int recoverExpiredProcessingTasks();
}
