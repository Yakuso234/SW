package com.jiake.jk.video.template;

import com.jiake.jk.common.exception.YHClientException;
import com.jiake.jk.common.utils.SnowflakeUtils;
import com.jiake.jk.video.pojo.mq.VideoInteractionMessage;
import com.jiake.jk.video.pojo._enum.InteractionStatus;
import com.jiake.jk.video.cache.VideoUserLikeCache;
import com.jiake.jk.video.constant.RabbitMQConstant;
import com.jiake.jk.video.mapper.VideoUserLikeMapper;
import lombok.RequiredArgsConstructor;
import org.apache.coyote.BadRequestException;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LikeInteraction extends InteractionTemplate {
    private final VideoUserLikeCache videoUserLikeCache;
    private final RabbitTemplate rabbitTemplate;
    private final SnowflakeUtils snowflakeUtils;

    @Override
    protected void tryInteract(Long userId, Long videoId, InteractionStatus status) {
        // 判断当前操作是否合法
        if (!((status.equals(InteractionStatus.FRONT) && videoUserLikeCache.tryLike(userId, videoId))||
                (status.equals(InteractionStatus.BACK) && videoUserLikeCache.tryUnlike(userId, videoId)))) {
            throw new YHClientException("异常操作！");
        }

    }

    @Override
    protected Long nextEventId() {
        return snowflakeUtils.nextId();
    }

    @Override
    protected void sendMessageToQueue(VideoInteractionMessage videoInteractionMessage) {
        // 信息发送到消息队列异步处理
        rabbitTemplate.convertAndSend(RabbitMQConstant.VIDEO_INTERACTION_TOPIC_EXCHANGE, RabbitMQConstant.VIDEO_LIKE_QUEUE_KEY, videoInteractionMessage);
    }
}
