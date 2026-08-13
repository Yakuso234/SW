package com.jiake.jk.video.pojo.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 按发布时间倒序的游标 Feed 响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PublishedFeedResponse {
    private List<VideoMainResponse> items;
    private String nextCursor;
    private boolean hasMore;
}
