package com.jiake.jk.videoprocessor.consumer;

import com.jiake.jk.common.response.Result;
import com.jiake.jk.video.feign.VideoPrivateClient;
import com.jiake.jk.video.pojo.mq.VideoReviewMessage;
import com.jiake.jk.videoprocessor.service.VideoTranscodingService;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VideoConsumerTest {

    @Test
    void shouldClaimPendingReviewVideoBeforeProcessing() throws Exception {
        VideoPrivateClient client = mock(VideoPrivateClient.class);
        VideoTranscodingService transcodingService = mock(VideoTranscodingService.class);
        when(client.claimVideoProcessing(701L))
                .thenReturn(Result.success(true));
        when(transcodingService.transcode(701L, "2026/08/source.mp4"))
                .thenReturn(new VideoTranscodingService.TranscodingResult("processed/701.mp4", "cover/701.jpg"));
        when(client.completeVideoProcessing(eq(701L), eq(new VideoPrivateClient.VideoProcessingResultRequest("processed/701.mp4", "cover/701.jpg"))))
                .thenReturn(Result.success());
        VideoReviewMessage message = new VideoReviewMessage();
        message.setVideoId(701L);
        message.setVideoUrl("2026/08/source.mp4");

        new VideoConsumer(client, transcodingService).handleVideoPostMessage(message);

        verify(client).claimVideoProcessing(eq(701L));
        verify(client).completeVideoProcessing(eq(701L),
                eq(new VideoPrivateClient.VideoProcessingResultRequest("processed/701.mp4", "cover/701.jpg")));
    }

    @Test
    void shouldDeadLetterMessageWhenResultCallbackIsUnavailable() throws Exception {
        VideoPrivateClient client = mock(VideoPrivateClient.class);
        VideoTranscodingService transcodingService = mock(VideoTranscodingService.class);
        when(client.claimVideoProcessing(702L)).thenReturn(Result.success(true));
        when(transcodingService.transcode(702L, "2026/08/source.mp4"))
                .thenReturn(new VideoTranscodingService.TranscodingResult("processed/702.mp4", "cover/702.jpg"));
        when(client.completeVideoProcessing(eq(702L), eq(new VideoPrivateClient.VideoProcessingResultRequest("processed/702.mp4", "cover/702.jpg"))))
                .thenReturn(Result.error("video service unavailable"));
        VideoReviewMessage message = new VideoReviewMessage();
        message.setVideoId(702L);
        message.setVideoUrl("2026/08/source.mp4");

        assertThrows(AmqpRejectAndDontRequeueException.class,
                () -> new VideoConsumer(client, transcodingService).handleVideoPostMessage(message));

        verify(client, never()).failVideoProcessing(eq(702L), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldDeadLetterMessageWhenCompletionCallbackThrows() throws Exception {
        VideoPrivateClient client = mock(VideoPrivateClient.class);
        VideoTranscodingService transcodingService = mock(VideoTranscodingService.class);
        when(client.claimVideoProcessing(703L)).thenReturn(Result.success(true));
        when(transcodingService.transcode(703L, "2026/08/source.mp4"))
                .thenReturn(new VideoTranscodingService.TranscodingResult("processed/703.mp4", "cover/703.jpg"));
        doThrow(new IllegalStateException("connection refused"))
                .when(client).completeVideoProcessing(eq(703L),
                        eq(new VideoPrivateClient.VideoProcessingResultRequest("processed/703.mp4", "cover/703.jpg")));
        VideoReviewMessage message = new VideoReviewMessage();
        message.setVideoId(703L);
        message.setVideoUrl("2026/08/source.mp4");

        assertThrows(AmqpRejectAndDontRequeueException.class,
                () -> new VideoConsumer(client, transcodingService).handleVideoPostMessage(message));

        verify(client, never()).failVideoProcessing(eq(703L), org.mockito.ArgumentMatchers.any());
    }
}
