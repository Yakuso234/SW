package com.jiake.jk.video.service;

import com.jiake.jk.video.pojo.response.VideoRecoveryOperationResponse;

public interface VideoProcessingTaskService {

    boolean claimVideoProcessing(Long videoId);

    void completeVideoProcessing(Long videoId, String processedVideoKey, String coverKey);

    void failVideoProcessing(Long videoId, String errorMessage);

    int recoverExpiredProcessingTasks();

    /** 受限内网编排器按稳定幂等键恢复指定的过期处理中任务。 */
    VideoRecoveryOperationResponse recoverExpiredProcessingTask(Long videoId, String idempotencyKey,
                                                                 String traceId, String requestedBy);

    /** 只读查询某个幂等键对应的持久化恢复回执。 */
    VideoRecoveryOperationResponse getRecoveryStatus(Long videoId, String idempotencyKey,
                                                      String traceId, String requestedBy);

    ProcessingOperationsOverview getProcessingOperationsOverview();

    record ProcessingOperationsOverview(long reviewQueueMessages,
                                        long deadLetterQueueMessages,
                                        long processingTaskCount,
                                        long failedTaskCount) {
    }
}
