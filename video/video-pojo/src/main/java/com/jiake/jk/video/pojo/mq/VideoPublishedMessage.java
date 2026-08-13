package com.jiake.jk.video.pojo.mq;

import lombok.Data;

@Data
public class VideoPublishedMessage {
    private Long videoId;
    private Long creatorId;
    /** ISO-8601 local date-time. Keep MQ payload independent from Jackson defaults. */
    private String publishedAt;
    private String traceId;
}
