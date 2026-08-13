package com.jiake.jk.video.pojo.mq;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class VideoPublishedMessage {
    private Long videoId;
    private Long creatorId;
    private LocalDateTime publishedAt;
    private String traceId;
}
