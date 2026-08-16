package com.jiake.jk.video.template;

import com.jiake.jk.common.exception.YHClientException;
import com.jiake.jk.common.utils.SnowflakeUtils;
import com.jiake.jk.video.pojo.mq.VideoInteractionMessage;
import com.jiake.jk.video.pojo._enum.InteractionStatus;
import com.jiake.jk.video.cache.VideoUserFavoriteCache;
import com.jiake.jk.video.constant.RabbitMQConstant;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FavoriteInteraction extends InteractionTemplate {
    private final VideoUserFavoriteCache videoUserFavoriteCache;
    private final RabbitTemplate rabbitTemplate;
    private final SnowflakeUtils snowflakeUtils;

    protected void tryInteract(Long userId, Long videoId, InteractionStatus status) {
        // 判断当前操作是否合法
        if (!((status.equals(InteractionStatus.FRONT) && videoUserFavoriteCache.tryFavorite(userId, videoId)) ||
                (status.equals(InteractionStatus.BACK) && videoUserFavoriteCache.tryUnFavorite(userId, videoId)))) {
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
        rabbitTemplate.convertAndSend(RabbitMQConstant.VIDEO_INTERACTION_TOPIC_EXCHANGE, RabbitMQConstant.VIDEO_FAVORITE_QUEUE_KEY, videoInteractionMessage);
    }
}
