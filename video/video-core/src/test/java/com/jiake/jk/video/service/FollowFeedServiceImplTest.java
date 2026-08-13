package com.jiake.jk.video.service;

import com.jiake.jk.common.response.Result;
import com.jiake.jk.common.utils.AWSUtils;
import com.jiake.jk.common.utils.SnowflakeUtils;
import com.jiake.jk.user.feign.UserFollowPrivateClient;
import com.jiake.jk.user.feign.UserPrivateClient;
import com.jiake.jk.user.pojo.response.FollowerIdPageResponse;
import com.jiake.jk.video.mapper.VideoFeedInboxMapper;
import com.jiake.jk.video.mapper.multi.VideoMultiMapper;
import com.jiake.jk.video.pojo.mq.VideoPublishedMessage;
import com.jiake.jk.video.service.impl.FollowFeedServiceImpl;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FollowFeedServiceImplTest {

    @Test
    void fanoutPublishedVideo_shouldPageFollowersAndUseIdempotentBatchInsert() {
        VideoFeedInboxMapper inboxMapper = mock(VideoFeedInboxMapper.class);
        VideoMultiMapper multiMapper = mock(VideoMultiMapper.class);
        UserFollowPrivateClient followClient = mock(UserFollowPrivateClient.class);
        UserPrivateClient userClient = mock(UserPrivateClient.class);
        SnowflakeUtils snowflakeUtils = mock(SnowflakeUtils.class);
        AWSUtils awsUtils = mock(AWSUtils.class);
        when(snowflakeUtils.nextId()).thenReturn(1L, 2L, 3L);
        when(followClient.getFollowerIds(eq(100L), isNull(), eq(500)))
                .thenReturn(Result.success(new FollowerIdPageResponse(List.of(11L, 12L), 99L, true)));
        when(followClient.getFollowerIds(eq(100L), eq(99L), eq(500)))
                .thenReturn(Result.success(new FollowerIdPageResponse(List.of(13L), null, false)));

        FollowFeedServiceImpl service = new FollowFeedServiceImpl(
                inboxMapper, multiMapper, followClient, userClient, snowflakeUtils, awsUtils);
        VideoPublishedMessage event = new VideoPublishedMessage();
        event.setVideoId(200L);
        event.setCreatorId(100L);
        event.setPublishedAt(LocalDateTime.now().toString());

        service.fanoutPublishedVideo(event);

        verify(followClient).getFollowerIds(100L, null, 500);
        verify(followClient).getFollowerIds(100L, 99L, 500);
        verify(inboxMapper, times(2)).insertIgnoreBatch(anyList());
    }
}
