package com.jiake.jk.video.cache;

import com.jiake.jk.video.constant.RedisConstant;
import com.jiake.jk.video.mapper.VideoUserLikeMapper;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;

@Component
public class VideoUserLikeCache {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private VideoUserLikeMapper videoUserLikeMapper;
    @Autowired
    private RedisScript<Long> interactionScript;
    @Autowired
    private RedissonClient redissonClient;

    public boolean tryLike(Long userId, Long videoId) {
        return tryChange(userId, videoId, true);
    }

    public boolean tryUnlike(Long userId, Long videoId) {
        return tryChange(userId, videoId, false);
    }

    /**
     * Redis 命中时由 Lua 原子校验并写入目标状态；冷缓存时先以数据库为准，
     * 再在同一用户-视频粒度锁内回填目标状态。旧实现在冷缓存时只回填旧状态，
     * 会导致首次点赞/收藏被误判为异常操作。
     */
    private boolean tryChange(Long userId, Long videoId, boolean targetLiked) {
        String key = RedisConstant.VIDEO_USER_LIKE_KEY_PREFIX + userId;
        String target = targetLiked ? "1" : "0";
        Long result = stringRedisTemplate.execute(interactionScript, Collections.singletonList(key), videoId.toString(), target, "600");
        if (RedisConstant.InteractionLua.OK.getValue() == (result == null ? -1L : result)) {
            return true;
        }
        if (RedisConstant.InteractionLua.ERROR.getValue() == (result == null ? -1L : result)) {
            return false;
        }

        RLock lock = redissonClient.getLock(RedisConstant.VIDEO_USER_LIKE_LOCK_PREFIX + userId + ":" + videoId);
        lock.lock();
        try {
            boolean currentLiked = videoUserLikeMapper.isLike(userId, videoId);
            if (currentLiked == targetLiked) {
                return false;
            }
            stringRedisTemplate.opsForHash().put(key, videoId.toString(), target);
            stringRedisTemplate.expire(key, 600, java.util.concurrent.TimeUnit.SECONDS);
            return true;
        } finally {
            lock.unlock();
        }
    }

}
