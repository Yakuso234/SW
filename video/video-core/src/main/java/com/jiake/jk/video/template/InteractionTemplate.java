package com.jiake.jk.video.template;

import com.jiake.jk.video.pojo.mq.VideoInteractionMessage;
import com.jiake.jk.video.pojo._enum.InteractionStatus;

public abstract class InteractionTemplate {

    public void handle(Long userId, Long videoId, InteractionStatus status) {
        tryInteract(userId, videoId, status);
        /*
         * 如果把数据库操作和发送消息队列放在一个事务，则可能出现数据库操作回滚了，但是消息还是成功发送了。
         * 如果只把数据库操作放在一个事务，等事务结束后再发送消息，这就可能事务成功了，但是消息未能成功发送。
         * 但是点赞记录丢失其实影响并不会很大（至于多出的点赞数，可以定时对账）。
         */
        // 构建消息
        VideoInteractionMessage videoInteractionMessage = new VideoInteractionMessage();
        videoInteractionMessage.setEventId(nextEventId())
                .setUserId(userId)
                .setVideoId(videoId)
                .setStatus(status);
        // 发送到队列异步处理
        sendMessageToQueue(videoInteractionMessage);
    }

    protected abstract void tryInteract(Long userId, Long videoId, InteractionStatus interactionStatus);

    /** 每次状态变更生成全局唯一事件标识，供消费者去重。 */
    protected abstract Long nextEventId();

    protected abstract void sendMessageToQueue(VideoInteractionMessage videoInteractionMessage);
}
