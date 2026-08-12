package com.jiake.jk.videoprocessor.config;

import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    private static final String VIDEO_REVIEW_QUEUE = "video.review.queue";
    private static final String VIDEO_REVIEW_DEAD_QUEUE = "video.review.dead.queue";

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
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

    @Bean
    public Queue videoReviewQueue() {
        return QueueBuilder.durable(VIDEO_REVIEW_QUEUE)
                .deadLetterExchange("")
                .deadLetterRoutingKey(VIDEO_REVIEW_DEAD_QUEUE)
                .build();
    }

    @Bean
    public Queue videoReviewDeadQueue() {
        return QueueBuilder.durable(VIDEO_REVIEW_DEAD_QUEUE).build();
    }
}
