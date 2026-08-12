package com.jiake.jk.user.service;

import com.jiake.jk.user.pojo.response.UserFriendResponse;
import com.jiake.jk.user.pojo.response.FollowListResponse;
import org.apache.coyote.BadRequestException;

import java.util.List;

public interface FollowService {
    void follow(Long followerId, Long followeeId) throws BadRequestException;

    void unfollow(Long followerId, Long followeeId);

    List<Boolean> getFollowStatus(Long followerId, List<Long> followeeIdList);

    List<UserFriendResponse> getFriends(Long user);

    FollowListResponse getFollowingList(Long userId, Long lastId);

    FollowListResponse getFollowerList(Long userId, Long lastId);
}
