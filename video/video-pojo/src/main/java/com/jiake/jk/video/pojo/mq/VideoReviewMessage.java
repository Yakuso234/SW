package com.jiake.jk.video.pojo.mq;

import lombok.Data;

import java.util.List;

@Data
public class VideoReviewMessage {
    String traceId;
    Long videoId;
    String videoUrl;
    String description;
    List<String> tagNameList;
}
