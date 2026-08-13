package com.jiake.jk.video.constant;

public class RabbitMQConstant {
    /**
     * review
     */
    public final static String VIDEO_REVIEW_QUEUE = "video.review.queue";
    public final static String VIDEO_REVIEW_DEAD_QUEUE = "video.review.dead.queue";

    public final static String VIDEO_PUBLISH_INBOX_QUEUE = "video.publish.inbox.queue";
    public final static String VIDEO_PUBLISH_INBOX_DEAD_QUEUE = "video.publish.inbox.dead.queue";

    /**
     * Interaction
     */
    public final static String VIDEO_INTERACTION_TOPIC_EXCHANGE = "video.interaction.topic";
    public final static String VIDEO_LIKE_QUEUE = "video.like.queue";
    public final static String VIDEO_LIKE_QUEUE_KEY = "video.interaction.like";
    public final static String VIDEO_FAVORITE_QUEUE = "video.favorite.queue";
    public final static String VIDEO_FAVORITE_QUEUE_KEY = "video.interaction.favorite";
    public final static String VIDEO_COMMENT_QUEUE = "video.comment.queue";
    public final static String VIDEO_COMMENT_QUEUE_KEY = "video.interaction.comment";
    /** Reliable comment-counter event queue, delivered by the local Outbox through default exchange. */
    public final static String VIDEO_COMMENT_RELIABLE_QUEUE = "video.comment.reliable.queue";
    public final static String VIDEO_COMMENT_RELIABLE_DEAD_QUEUE = "video.comment.reliable.dead.queue";
}
