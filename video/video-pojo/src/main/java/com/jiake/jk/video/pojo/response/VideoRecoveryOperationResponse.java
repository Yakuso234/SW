package com.jiake.jk.video.pojo.response;

import java.time.LocalDateTime;

/** DG/FlowPilot 用于恢复执行和未知结果对账的稳定回执合同。 */
public record VideoRecoveryOperationResponse(
        String recoveryId,
        Long videoId,
        String idempotencyKey,
        String status,
        String reason,
        String outboxId,
        String traceId,
        String requestedBy,
        boolean replayed,
        LocalDateTime createdAt
) {
}
