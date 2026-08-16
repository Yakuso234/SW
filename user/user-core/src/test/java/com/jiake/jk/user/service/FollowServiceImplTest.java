package com.jiake.jk.user.service;

import com.jiake.jk.common.utils.AWSUtils;
import com.jiake.jk.common.utils.SnowflakeUtils;
import com.jiake.jk.user.mapper.FollowMapper;
import com.jiake.jk.user.service.impl.FollowServiceImpl;
import org.apache.coyote.BadRequestException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FollowServiceImplTest {

    @Test
    void follow_shouldOnlyPersistSocialGraphRelation() throws Exception {
        FollowMapper followMapper = mock(FollowMapper.class);
        SnowflakeUtils snowflakeUtils = mock(SnowflakeUtils.class);
        when(snowflakeUtils.nextId()).thenReturn(1001L);
        when(followMapper.insertIgnore(any())).thenReturn(true);
        FollowServiceImpl service = new FollowServiceImpl(followMapper, snowflakeUtils, mock(AWSUtils.class));

        service.follow(2001L, 3001L);

        verify(followMapper).insertIgnore(any());
        verify(followMapper, never()).selectIsRelationExist(any(), any());
    }

    @Test
    void follow_shouldRejectSelfFollowBeforeWritingRelation() {
        FollowMapper followMapper = mock(FollowMapper.class);
        FollowServiceImpl service = new FollowServiceImpl(followMapper, mock(SnowflakeUtils.class), mock(AWSUtils.class));

        assertThrows(BadRequestException.class, () -> service.follow(2001L, 2001L));

        verify(followMapper, never()).insertIgnore(any());
    }
}
