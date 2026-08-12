package com.jiake.jk.video.pojo.mq;

import lombok.Data;

import java.util.List;

@Data
public class VideoReviewMessage {
    Long videoId;
    String videoUrl;
    String description;
    List<String> tagNameList;
}
