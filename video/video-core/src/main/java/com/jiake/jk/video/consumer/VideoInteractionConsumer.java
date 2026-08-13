package com.jiake.jk.video.consumer;

import com.jiake.jk.video.constant.RabbitMQConstant;
import com.jiake.jk.video.mapper.VideoMapper;
import com.jiake.jk.video.mapper.VideoCommentEventConsumptionMapper;
import com.jiake.jk.video.mapper.VideoUserCommentMapper;
import com.jiake.jk.video.mapper.VideoUserLikeMapper;
import com.jiake.jk.video.pojo._enum.InteractionStatus;
import com.jiake.jk.video.pojo.entity.VideoCommentEventConsumption;
import com.jiake.jk.video.pojo.mq.VideoCommentIncrMessage;
import com.jiake.jk.video.pojo.mq.VideoCommentMessage;
import com.jiake.jk.video.pojo.mq.VideoInteractionMessage;
import com.jiake.jk.video.pojo.request.VideoInteractionBatchRequest;
import com.jiake.jk.video.service.InteractionService;
import com.jiake.jk.video.service.VideoService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Consumer;

@Component
@RequiredArgsConstructor
public class VideoInteractionConsumer {

    private final InteractionService interactionService;
    private final VideoMapper videoMapper;
    private final VideoUserCommentMapper videoUserCommentMapper;
    private final VideoCommentEventConsumptionMapper videoCommentEventConsumptionMapper;

    @Transactional
    @RabbitListener(queues = RabbitMQConstant.VIDEO_COMMENT_RELIABLE_QUEUE, containerFactory = "batchContainerFactory")
    public void handleVideoCommentMessage(List<VideoCommentMessage> videoCommentMessageList) {
        Map<Long, Integer> videoCommentIncrMap = new HashMap<>();
        Map<Long, Integer> commentReplyIncrMap = new HashMap<>();
        /* 统计视频评论数增量和评论回复数增量 */
        for (VideoCommentMessage videoCommentMessage : videoCommentMessageList) {
            VideoCommentEventConsumption consumption = new VideoCommentEventConsumption();
            consumption.setCommentId(videoCommentMessage.getId());
            if (videoCommentEventConsumptionMapper.insertIgnore(consumption) == 0) {
                continue;
            }
            // 统计视频评论数增量
            videoCommentIncrMap.put(videoCommentMessage.getVideoId(),
                    videoCommentIncrMap.getOrDefault(videoCommentMessage.getVideoId(), 0) + 1);
            // 统计评论回复数增量
            if (!Objects.equals(videoCommentMessage.getRootId(), videoCommentMessage.getId())) { // 如果评论的根评论ID不等于评论ID，说明这是一个回复
                commentReplyIncrMap.put(videoCommentMessage.getRootId(),
                        commentReplyIncrMap.getOrDefault(videoCommentMessage.getRootId(), 0) + 1);
            }
        }
        /* 构造增量消息 */
        VideoCommentIncrMessage videoCommentIncrMessage = new VideoCommentIncrMessage();
        List<VideoCommentIncrMessage.CommentIncr> commentIncrList = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : videoCommentIncrMap.entrySet()) {
            commentIncrList.add(new VideoCommentIncrMessage.CommentIncr(entry.getKey(), entry.getValue()));
        }
        List<VideoCommentIncrMessage.ReplyIncr> replyIncrList = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : commentReplyIncrMap.entrySet()) {
            replyIncrList.add(new VideoCommentIncrMessage.ReplyIncr(entry.getKey(), entry.getValue()));
        }
        videoCommentIncrMessage.setCommentIncrList(commentIncrList);
        videoCommentIncrMessage.setReplyIncrList(replyIncrList);

        /* 操作数据库 */
        if (!videoCommentIncrMessage.getCommentIncrList().isEmpty()) { // 常理来说这里不会为null
            videoMapper.updateCommentBatch(videoCommentIncrMessage.getCommentIncrList());
        }
        if (!videoCommentIncrMessage.getReplyIncrList().isEmpty()) {
            videoUserCommentMapper.updateReplyBatch(videoCommentIncrMessage.getReplyIncrList());
        }
    }

    @RabbitListener(
            bindings = @QueueBinding(
                    exchange = @Exchange(name = RabbitMQConstant.VIDEO_INTERACTION_TOPIC_EXCHANGE, type = ExchangeTypes.TOPIC),
                    key = RabbitMQConstant.VIDEO_LIKE_QUEUE_KEY,
                    value = @Queue(name = RabbitMQConstant.VIDEO_LIKE_QUEUE)
            ),
            containerFactory = "batchContainerFactory"
    )
    public void handleVideoLikeMessage(List<VideoInteractionMessage> videoInteractionMessageList) {
        processInteractionMessages(videoInteractionMessageList, interactionService::likeBatch);
    }

    @RabbitListener(
            bindings = @QueueBinding(
                    exchange = @Exchange(name = RabbitMQConstant.VIDEO_INTERACTION_TOPIC_EXCHANGE, type = ExchangeTypes.TOPIC),
                    key = RabbitMQConstant.VIDEO_FAVORITE_QUEUE_KEY,
                    value = @Queue(name = RabbitMQConstant.VIDEO_FAVORITE_QUEUE)
            ),
            containerFactory = "batchContainerFactory"
    )
    public void handleVideoFavoriteMessage(List<VideoInteractionMessage> videoInteractionMessageList) {
        processInteractionMessages(videoInteractionMessageList, interactionService::favoriteBatch);
    }

    private void processInteractionMessages(List<VideoInteractionMessage> videoInteractionMessageList,
                                            Consumer<VideoInteractionBatchRequest> batchProcessor) {
        /* 统计每个视频的增量数 */
        Map<Long, Integer> interactionIncrMap = new HashMap<>();
        for (VideoInteractionMessage videoInteractionMessage : videoInteractionMessageList) {
            int incrNumber = videoInteractionMessage.getStatus() == InteractionStatus.FRONT ? 1 : -1;
            interactionIncrMap.put(videoInteractionMessage.getVideoId(),
                    interactionIncrMap.getOrDefault(videoInteractionMessage.getVideoId(), 0) + incrNumber);
        }
        /* 构造批量请求对象 */
        VideoInteractionBatchRequest videoInteractionBatchRequest = new VideoInteractionBatchRequest();
        List<VideoInteractionBatchRequest.Incr> interactionIncrList = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : interactionIncrMap.entrySet()) {
            interactionIncrList.add(new VideoInteractionBatchRequest.Incr(entry.getKey(), entry.getValue()));
        }
        List<VideoInteractionBatchRequest.Record> interactionRecordList = new ArrayList<>();
        for (VideoInteractionMessage videoInteractionMessage : videoInteractionMessageList) {
            interactionRecordList.add(new VideoInteractionBatchRequest.Record(videoInteractionMessage.getUserId(),
                    videoInteractionMessage.getVideoId(),
                    videoInteractionMessage.getStatus()));
        }
        videoInteractionBatchRequest.setInteractionRecordList(interactionRecordList);
        videoInteractionBatchRequest.setInteractionIncrList(interactionIncrList);
        /* 调用批量处理方法 */
        batchProcessor.accept(videoInteractionBatchRequest);
    }
}
