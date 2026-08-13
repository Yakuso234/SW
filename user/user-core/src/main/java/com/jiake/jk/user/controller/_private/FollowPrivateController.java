package com.jiake.jk.user.controller._private;

import com.jiake.jk.common.response.Result;
import com.jiake.jk.user.service.FollowService;
import com.jiake.jk.user.pojo.response.FollowerIdPageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Follow")
@RestController
@RequestMapping("/private/follow")
public class FollowPrivateController {

    @Autowired
    private FollowService followService;

    @Operation(summary = "获取关注状态")
    @GetMapping("/status")
    public Result<List<Boolean>> getFollowStatus(@RequestParam Long followerId, @RequestParam List<Long> followeeIdList) {
        return Result.success(followService.getFollowStatus(followerId, followeeIdList));
    }

    @Operation(summary = "分页获取指定创作者的粉丝 ID，供发布事件扇出使用")
    @GetMapping("/followers")
    public Result<FollowerIdPageResponse> getFollowerIds(@RequestParam Long followeeId,
                                                         @RequestParam(required = false) Long lastFollowId,
                                                         @RequestParam(defaultValue = "500") Integer pageSize) {
        return Result.success(followService.getFollowerIdPage(followeeId, lastFollowId, pageSize));
    }
}
