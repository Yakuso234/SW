package com.jiake.jk.video.service;

import com.jiake.jk.video.pojo.mq.VideoPublishedMessage;
import com.jiake.jk.video.pojo.response.FollowFeedResponse;

public interface FollowFeedService {
    void fanoutPublishedVideo(VideoPublishedMessage message);

    FollowFeedResponse getFollowFeed(Long recipientId, String cursor, Integer pageSize);
}
