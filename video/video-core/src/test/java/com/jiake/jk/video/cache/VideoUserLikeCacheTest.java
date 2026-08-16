package com.jiake.jk.video.cache;

import com.jiake.jk.video.mapper.VideoUserLikeMapper;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VideoUserLikeCacheTest {

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void tryLike_shouldPopulateTargetStateWhenCacheIsCold() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        VideoUserLikeMapper likeMapper = mock(VideoUserLikeMapper.class);
        RedissonClient redissonClient = mock(RedissonClient.class);
        RLock lock = mock(RLock.class);
        HashOperations hashOperations = mock(HashOperations.class);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any(), any())).thenReturn(0L);
        when(redissonClient.getLock("video:user:like:lock:2001:3001")).thenReturn(lock);
        when(likeMapper.isLike(2001L, 3001L)).thenReturn(false);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);

        VideoUserLikeCache cache = new VideoUserLikeCache();
        inject(cache, "stringRedisTemplate", redisTemplate);
        inject(cache, "videoUserLikeMapper", likeMapper);
        inject(cache, "redissonClient", redissonClient);
        inject(cache, "interactionScript", mock(RedisScript.class));

        assertTrue(cache.tryLike(2001L, 3001L));

        verify(hashOperations).put("video:user:like:2001", "3001", "1");
        verify(redisTemplate).expire("video:user:like:2001", 600, TimeUnit.SECONDS);
        verify(lock).unlock();
    }

    private static void inject(Object target, String fieldName, Object value) throws ReflectiveOperationException {
        var field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
