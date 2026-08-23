package com.jiake.jk.video.pojo.response;

import lombok.Data;

import java.util.List;

@Data
public class CreatorAnalyticsOverviewResponse {
    private Long publishedCount;
    private Long views;
    private Long likes;
    private Long comments;
    private Long favorites;
    private List<CreatorAnalyticsTrendResponse> trends;
}
