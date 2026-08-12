package com.jiake.jk.user.pojo.response;

import lombok.Data;

import java.util.List;

@Data
public class FollowListResponse {
    private List<FollowUserResponse> list;
}
