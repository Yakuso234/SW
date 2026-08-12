package com.jiake.jk.video.pojo.mq;

import lombok.Data;

@Data
public class VideoCommentMessage {
    Long id;
    Long rootId;
    Long videoId;
    Long userId;
    String content;
}