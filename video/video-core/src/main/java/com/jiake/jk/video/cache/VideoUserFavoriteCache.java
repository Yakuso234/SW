package com.jiake.jk.video.cache;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jiake.jk.video.constant.RedisConstant;
import com.jiake.jk.video.mapper.VideoUserCollectionsItemMapper;
import com.jiake.jk.video.pojo.entity.VideoUserCollectionsItem;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;

@Component
public class VideoUserFavoriteCache {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private RedisScript<Long> interactionScript;
    @Autowired
    private VideoUserCollectionsItemMapper videoUserCollectionsItemMapper;
    @Autowired
    private RedissonClient redissonClient;

    public boolean tryFavorite(Long userId, Long videoId) {
        return tryChange(userId, videoId, true);
    }

    public boolean tryUnFavorite(Long userId, Long videoId) {
        return tryChange(userId, videoId, false);
    }

    /** 冷缓存以持久化收藏关系为准，避免首次操作只写回旧状态。 */
    private boolean tryChange(Long userId, Long videoId, boolean targetFavorited) {
        String key = RedisConstant.VIDEO_USER_FAVORITE_KEY_PREFIX + userId;
        String target = targetFavorited ? "1" : "0";
        Long result = stringRedisTemplate.execute(interactionScript, Collections.singletonList(key), videoId.toString(), target, "600");
        if (RedisConstant.InteractionLua.OK.getValue() == (result == null ? -1L : result)) {
            return true;
        }
        if (RedisConstant.InteractionLua.ERROR.getValue() == (result == null ? -1L : result)) {
            return false;
        }

        RLock lock = redissonClient.getLock(RedisConstant.VIDEO_USER_FAVORITE_LOCK_PREFIX + userId + ":" + videoId);
        lock.lock();
        try {
            boolean currentFavorited = videoUserCollectionsItemMapper.selectCount(new LambdaQueryWrapper<VideoUserCollectionsItem>()
                    .eq(VideoUserCollectionsItem::getUserId, userId)
                    .eq(VideoUserCollectionsItem::getVideoId, videoId)
                    .last("LIMIT 1")) > 0;
            if (currentFavorited == targetFavorited) {
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
