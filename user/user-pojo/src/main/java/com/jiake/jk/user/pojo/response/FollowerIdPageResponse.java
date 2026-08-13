package com.jiake.jk.user.pojo.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FollowerIdPageResponse {
    private List<Long> followerIds;
    private Long nextFollowId;
    private boolean hasMore;
}
