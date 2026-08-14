package com.jiake.jk.video.config;

import com.jiake.jk.video.constant.RabbitMQConstant;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Bean
    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        return new RabbitAdmin(connectionFactory);
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter() {
            @Override
            protected Message createMessage(Object object, MessageProperties props) {
                props.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                return super.createMessage(object, props);
            }
        };
    }

    @Bean
    public RabbitTemplate rabbitTemplate(CachingConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);

        rabbitTemplate.setMessageConverter(messageConverter);

        // 设置强制执行退回回调
        rabbitTemplate.setMandatory(true);

        return rabbitTemplate;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory batchContainerFactory(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setBatchListener(true); // 启用批量监听
        factory.setConsumerBatchEnabled(true); // 消费者批量拉取
        factory.setBatchSize(500); // 每批最多处理500条
        factory.setReceiveTimeout(5000L); // 关键！超时时间5秒（即使未满batchSize）
        factory.setPrefetchCount(500); // 预取数量需足够大
        factory.setMessageConverter(jsonMessageConverter);
        return factory;
    }

    @Bean("videoPublishInboxContainerFactory")
    public SimpleRabbitListenerContainerFactory videoPublishInboxContainerFactory(
            ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter);
        // 最终异常由消费者显式 reject；此处兜底避免容器默认无限 requeue。
        factory.setDefaultRequeueRejected(false);
        return factory;
    }

    @Bean
    public Queue queue() {
        return QueueBuilder.durable(RabbitMQConstant.VIDEO_REVIEW_QUEUE)
                .deadLetterExchange("")
                .deadLetterRoutingKey(RabbitMQConstant.VIDEO_REVIEW_DEAD_QUEUE)
                .build();
    }

    @Bean
    public Queue videoReviewDeadQueue() {
        return QueueBuilder.durable(RabbitMQConstant.VIDEO_REVIEW_DEAD_QUEUE).build();
    }

    @Bean
    public Queue videoPublishInboxQueue() {
        return QueueBuilder.durable(RabbitMQConstant.VIDEO_PUBLISH_INBOX_QUEUE)
                .deadLetterExchange("")
                .deadLetterRoutingKey(RabbitMQConstant.VIDEO_PUBLISH_INBOX_DEAD_QUEUE)
                .build();
    }

    /**
     * 关注流扇出短暂失败时由 Broker 持久化等待，TTL 到期后回流主队列，而不是占用消费者线程重试。
     */
    @Bean
    public Queue videoPublishInboxRetryQueue() {
        return QueueBuilder.durable(RabbitMQConstant.VIDEO_PUBLISH_INBOX_RETRY_QUEUE)
                .ttl(5_000)
                .deadLetterExchange("")
                .deadLetterRoutingKey(RabbitMQConstant.VIDEO_PUBLISH_INBOX_QUEUE)
                .build();
    }

    @Bean
    public Queue videoPublishInboxDeadQueue() {
        return QueueBuilder.durable(RabbitMQConstant.VIDEO_PUBLISH_INBOX_DEAD_QUEUE).build();
    }

    @Bean
    public Queue videoCommentReliableQueue() {
        return QueueBuilder.durable(RabbitMQConstant.VIDEO_COMMENT_RELIABLE_QUEUE)
                .deadLetterExchange("")
                .deadLetterRoutingKey(RabbitMQConstant.VIDEO_COMMENT_RELIABLE_DEAD_QUEUE)
                .build();
    }

    @Bean
    public Queue videoCommentReliableDeadQueue() {
        return QueueBuilder.durable(RabbitMQConstant.VIDEO_COMMENT_RELIABLE_DEAD_QUEUE).build();
    }
}
