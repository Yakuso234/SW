package com.jiake.jk.videoprocessor.consumer;

import com.jiake.jk.common.response.Result;
import com.jiake.jk.video.feign.VideoPrivateClient;
import com.jiake.jk.video.pojo.mq.VideoReviewMessage;
import com.jiake.jk.videoprocessor.service.VideoTranscodingService;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
}
