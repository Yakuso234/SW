package com.jiake.jk.video.consumer;

import com.jiake.jk.video.mapper.VideoCommentEventConsumptionMapper;
import com.jiake.jk.video.mapper.VideoMapper;
import com.jiake.jk.video.mapper.VideoUserCommentMapper;
import com.jiake.jk.video.pojo.entity.VideoCommentEventConsumption;
import com.jiake.jk.video.pojo.mq.VideoCommentIncrMessage;
import com.jiake.jk.video.pojo.mq.VideoCommentMessage;
import com.jiake.jk.video.service.InteractionService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VideoInteractionConsumerTest {

    @Test
    void handleCommentMessage_shouldOnlyIncrementForFirstConsumption() {
        InteractionService interactionService = mock(InteractionService.class);
        VideoMapper videoMapper = mock(VideoMapper.class);
        VideoUserCommentMapper commentMapper = mock(VideoUserCommentMapper.class);
        VideoCommentEventConsumptionMapper consumptionMapper = mock(VideoCommentEventConsumptionMapper.class);
        when(consumptionMapper.insertIgnore(any(VideoCommentEventConsumption.class))).thenReturn(1, 0);
        VideoInteractionConsumer consumer = new VideoInteractionConsumer(
                interactionService, videoMapper, commentMapper, consumptionMapper);

        VideoCommentMessage first = event(1001L, 2001L, 1001L);
        VideoCommentMessage duplicate = event(1001L, 2001L, 1001L);
        consumer.handleVideoCommentMessage(List.of(first, duplicate));

        ArgumentCaptor<List<VideoCommentIncrMessage.CommentIncr>> commentCaptor = ArgumentCaptor.forClass(List.class);
        verify(videoMapper).updateCommentBatch(commentCaptor.capture());
        assertEquals(1, commentCaptor.getValue().size());
        assertEquals(2001L, commentCaptor.getValue().getFirst().getVideoId());
        assertEquals(1, commentCaptor.getValue().getFirst().getIncrNumber());
        verify(commentMapper, never()).updateReplyBatch(any());
    }

    @Test
    void handleCommentMessage_shouldIncrementRootReplyOnlyOnce() {
        InteractionService interactionService = mock(InteractionService.class);
        VideoMapper videoMapper = mock(VideoMapper.class);
        VideoUserCommentMapper commentMapper = mock(VideoUserCommentMapper.class);
        VideoCommentEventConsumptionMapper consumptionMapper = mock(VideoCommentEventConsumptionMapper.class);
        when(consumptionMapper.insertIgnore(any(VideoCommentEventConsumption.class))).thenReturn(1);
        VideoInteractionConsumer consumer = new VideoInteractionConsumer(
                interactionService, videoMapper, commentMapper, consumptionMapper);

        consumer.handleVideoCommentMessage(List.of(event(1002L, 2001L, 1001L)));

        ArgumentCaptor<List<VideoCommentIncrMessage.ReplyIncr>> replyCaptor = ArgumentCaptor.forClass(List.class);
        verify(commentMapper).updateReplyBatch(replyCaptor.capture());
        assertEquals(1, replyCaptor.getValue().size());
        assertEquals(1001L, replyCaptor.getValue().getFirst().getRootId());
        assertTrue(replyCaptor.getValue().getFirst().getIncrNumber() == 1);
    }

    private VideoCommentMessage event(Long commentId, Long videoId, Long rootId) {
        VideoCommentMessage event = new VideoCommentMessage();
        event.setId(commentId);
        event.setVideoId(videoId);
        event.setRootId(rootId);
        event.setUserId(3001L);
        return event;
    }
}
