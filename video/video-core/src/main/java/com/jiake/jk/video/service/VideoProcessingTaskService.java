package com.jiake.jk.video.service;

public interface VideoProcessingTaskService {

    boolean claimVideoProcessing(Long videoId);

    void completeVideoProcessing(Long videoId, String processedVideoKey, String coverKey);

    void failVideoProcessing(Long videoId, String errorMessage);

    int recoverExpiredProcessingTasks();

    /**
     * 运维按视频维度触发已过期租约的恢复。不会直接重放 DLQ 原消息，
     * 而是复用恢复逻辑创建新的 Outbox 消息。
     */
    boolean recoverExpiredProcessingTask(Long videoId);

    ProcessingOperationsOverview getProcessingOperationsOverview();

    record ProcessingOperationsOverview(long reviewQueueMessages,
                                        long deadLetterQueueMessages,
                                        long processingTaskCount,
                                        long failedTaskCount) {
    }
}
