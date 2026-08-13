package com.jiake.jk.user.feign;

import com.jiake.jk.common.response.Result;
import com.jiake.jk.user.pojo.response.FollowerIdPageResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(value = "userService", contextId = "UserFollowPrivateClient")
public interface UserFollowPrivateClient {
    @GetMapping("/user/api/private/follow/status")
    Result<List<Boolean>> getFollowStatus(@RequestParam Long followerId, @RequestParam List<Long> followeeIdList);

    @GetMapping("/user/api/private/follow/followers")
    Result<FollowerIdPageResponse> getFollowerIds(@RequestParam Long followeeId,
                                                  @RequestParam(required = false) Long lastFollowId,
                                                  @RequestParam Integer pageSize);
}
