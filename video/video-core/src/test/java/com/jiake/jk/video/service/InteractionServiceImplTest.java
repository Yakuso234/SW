package com.jiake.jk.video.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiake.jk.common.utils.SnowflakeUtils;
import com.jiake.jk.common.response.Result;
import com.jiake.jk.user.feign.UserPrivateClient;
import com.jiake.jk.video.mapper.MessageOutBoxMapper;
import com.jiake.jk.video.mapper.VideoMapper;
import com.jiake.jk.video.mapper.VideoUserCommentMapper;
import com.jiake.jk.video.mapper.VideoUserLikeMapper;
import com.jiake.jk.video.pojo.entity.MessageOutbox;
import com.jiake.jk.video.pojo.entity.Video;
import com.jiake.jk.video.pojo.mq.VideoCommentMessage;
import com.jiake.jk.video.pojo.request.PostCommentRequest;
import com.jiake.jk.video.pojo.response.GetDirectCommentResponse;
import com.jiake.jk.video.pojo.entity.VideoUserComment;
import com.jiake.jk.video.service.impl.InteractionServiceImpl;
import com.jiake.jk.video.template.FavoriteInteraction;
import com.jiake.jk.video.template.LikeInteraction;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InteractionServiceImplTest {

    @BeforeAll
    static void initializeMybatisPlusLambdaCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "InteractionServiceImplTest");
        TableInfoHelper.initTableInfo(assistant, MessageOutbox.class);
    }

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void comment_shouldPersistReliableOutboxWithActualRootId() throws Exception {
        VideoMapper videoMapper = mock(VideoMapper.class);
        VideoUserCommentMapper commentMapper = mock(VideoUserCommentMapper.class);
        MessageOutBoxMapper outboxMapper = mock(MessageOutBoxMapper.class);
        SnowflakeUtils snowflakeUtils = mock(SnowflakeUtils.class);
        when(videoMapper.selectIsExistById(2001L)).thenReturn(true);
        when(snowflakeUtils.nextId()).thenReturn(1001L);
        when(outboxMapper.insert(any(MessageOutbox.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, MessageOutbox.class).setId(3001L);
            return 1;
        });
        InteractionServiceImpl service = new InteractionServiceImpl();
        inject(service, "videoMapper", videoMapper);
        inject(service, "videoUserCommentMapper", commentMapper);
        inject(service, "messageOutBoxMapper", outboxMapper);
        inject(service, "snowflakeUtils", snowflakeUtils);
        inject(service, "objectMapper", new ObjectMapper());
        inject(service, "outboxMessagePublisher", mock(OutboxMessagePublisher.class));
        TransactionSynchronizationManager.initSynchronization();

        PostCommentRequest request = new PostCommentRequest();
        request.setContent("可靠评论事件");
        assertEquals("1001", service.comment(2001L, 4001L, request));

        ArgumentCaptor<MessageOutbox> outboxCaptor = ArgumentCaptor.forClass(MessageOutbox.class);
        verify(outboxMapper).insert(outboxCaptor.capture());
        assertEquals("video.comment.reliable.queue", outboxCaptor.getValue().getRoutingKey());
        VideoCommentMessage event = new ObjectMapper().readValue(outboxCaptor.getValue().getMessageBody(), VideoCommentMessage.class);
        assertEquals(1001L, event.getId());
        assertEquals(1001L, event.getRootId());
    }

    @Test
    void directComment_shouldKeepCommentVisibleWhenUserProfileIsMissing() throws Exception {
        VideoUserCommentMapper commentMapper = mock(VideoUserCommentMapper.class);
        UserPrivateClient userPrivateClient = mock(UserPrivateClient.class);
        VideoUserComment comment = new VideoUserComment();
        comment.setId(5001L);
        comment.setVideoId(2001L);
        comment.setUserId(900002L);
        comment.setContent("用户资料缺失时评论仍可见");
        when(commentMapper.selectDirectComment(2001L, null)).thenReturn(List.of(comment));
        when(userPrivateClient.getUserInfoInList(List.of(900002L)))
                .thenReturn(Result.success(Collections.emptyList()));

        InteractionServiceImpl service = new InteractionServiceImpl();
        inject(service, "videoUserCommentMapper", commentMapper);
        inject(service, "userPrivateClient", userPrivateClient);

        List<GetDirectCommentResponse> result = service.directComment(2001L, null);

        assertEquals(1, result.size());
        assertEquals("已注销用户", result.get(0).getName());
        assertEquals("用户资料缺失时评论仍可见", result.get(0).getContent());
    }

    private static void inject(Object target, String fieldName, Object value) throws ReflectiveOperationException {
        var field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
