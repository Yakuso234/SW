package com.jiake.jk.video.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiake.jk.common.exception.GlobalExceptionHandler;
import com.jiake.jk.common.exception.YHClientException;
import com.jiake.jk.video.controller._private.VideoPrivateController;
import com.jiake.jk.video.pojo.response.VideoRecoveryOperationResponse;
import com.jiake.jk.video.service.VideoProcessingTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class VideoRecoveryControllerTest {

    private VideoProcessingTaskService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(VideoProcessingTaskService.class);
        VideoPrivateController controller = new VideoPrivateController();
        ReflectionTestUtils.setField(controller, "videoProcessingTaskService", service);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(objectMapper))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void postShouldPassHeadersAndReturnStructuredReceipt() throws Exception {
        VideoRecoveryOperationResponse response = response(false);
        when(service.recoverExpiredProcessingTask(7321572775443310L, "proposal-1:recover_expired_video_processing",
                "dg-sw-reconcile-demo-1", "flowpilot")).thenReturn(response);

        mockMvc.perform(post("/private/processing/{videoId}/recover-expired", 7321572775443310L)
                        .header("Idempotency-Key", "proposal-1:recover_expired_video_processing")
                        .header("X-Trace-Id", "dg-sw-reconcile-demo-1")
                        .header("X-FlowPilot-Service", "flowpilot"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recoveryId").value("193000000000000001"))
                .andExpect(jsonPath("$.data.outboxId").value("193000000000000002"))
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.replayed").value(false));

        verify(service).recoverExpiredProcessingTask(7321572775443310L,
                "proposal-1:recover_expired_video_processing", "dg-sw-reconcile-demo-1", "flowpilot");
    }

    @Test
    void getShouldReturnNotFoundAndConflictWithoutWriting() throws Exception {
        when(service.getRecoveryStatus(7001L, "missing-key", "trace-get", "flowpilot"))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "恢复回执不存在"));
        mockMvc.perform(get("/private/processing/{videoId}/recovery-status", 7001L)
                        .header("Idempotency-Key", "missing-key")
                        .header("X-Trace-Id", "trace-get")
                        .header("X-FlowPilot-Service", "flowpilot"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.msg").value("恢复回执不存在"));

        when(service.getRecoveryStatus(7002L, "conflict-key", "trace-get", "flowpilot"))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "幂等键已绑定到其他视频或服务"));
        mockMvc.perform(get("/private/processing/{videoId}/recovery-status", 7002L)
                        .header("Idempotency-Key", "conflict-key")
                        .header("X-Trace-Id", "trace-get")
                        .header("X-FlowPilot-Service", "flowpilot"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.msg").value("幂等键已绑定到其他视频或服务"));
    }

    @Test
    void missingRequiredHeaderShouldReturnBadRequest() throws Exception {
        when(service.recoverExpiredProcessingTask(7003L, null, "trace-missing", "flowpilot"))
                .thenThrow(new YHClientException("Idempotency-Key 不能为空"));

        mockMvc.perform(post("/private/processing/{videoId}/recover-expired", 7003L)
                        .header("X-Trace-Id", "trace-missing")
                        .header("X-FlowPilot-Service", "flowpilot"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg").value("Idempotency-Key 不能为空"));

        verify(service).recoverExpiredProcessingTask(7003L, null, "trace-missing", "flowpilot");
    }

    private VideoRecoveryOperationResponse response(boolean replayed) {
        return new VideoRecoveryOperationResponse(
                "193000000000000001",
                7321572775443310L,
                "proposal-1:recover_expired_video_processing",
                "ACCEPTED",
                null,
                "193000000000000002",
                "dg-sw-reconcile-demo-1",
                "flowpilot",
                replayed,
                LocalDateTime.of(2026, 8, 24, 10, 0));
    }
}
