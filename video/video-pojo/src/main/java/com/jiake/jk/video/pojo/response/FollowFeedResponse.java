package com.jiake.jk.video.pojo.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FollowFeedResponse {
    private List<VideoMainResponse> items;
    private String nextCursor;
    private boolean hasMore;
}
