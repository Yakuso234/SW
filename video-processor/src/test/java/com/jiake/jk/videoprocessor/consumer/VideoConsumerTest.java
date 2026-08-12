package com.jiake.jk.videoprocessor.consumer;

import com.jiake.jk.common.response.Result;
import com.jiake.jk.video.feign.VideoPrivateClient;
import com.jiake.jk.video.pojo.entity.Video;
import com.jiake.jk.video.pojo.mq.VideoReviewMessage;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VideoConsumerTest {

    @Test
    void shouldClaimPendingReviewVideoBeforeProcessing() {
        VideoPrivateClient client = mock(VideoPrivateClient.class);
        when(client.transitionVideoStatus(701L, Video.VideoStatus.PENDING_REVIEW, Video.VideoStatus.PROCESSING))
                .thenReturn(Result.success(true));
        VideoReviewMessage message = new VideoReviewMessage();
        message.setVideoId(701L);

        new VideoConsumer(client).handleVideoPostMessage(message);

        verify(client).transitionVideoStatus(eq(701L), eq(Video.VideoStatus.PENDING_REVIEW), eq(Video.VideoStatus.PROCESSING));
    }
}
