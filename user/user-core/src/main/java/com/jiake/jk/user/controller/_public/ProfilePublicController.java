package com.jiake.jk.user.controller._public;

import com.jiake.jk.common.response.Result;
import com.jiake.jk.common.utils.UserContext;
import com.jiake.jk.user.pojo.response.ProfileResponse;
import com.jiake.jk.user.pojo.response.ProfileStatsResponse;
import com.jiake.jk.user.service.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Profile")
@RestController
@RequestMapping("/public/profile")
@RequiredArgsConstructor
public class ProfilePublicController {

    private final ProfileService profileService;

    @Operation(summary = "获取用户简介")
    @GetMapping("/{userId}")
    public Result<ProfileResponse> getProfile(@PathVariable Long userId) {
        return Result.success(profileService.getProfile(userId, UserContext.getUser()));
    }

    @Operation(summary = "获取用户简介统计数据")
    @GetMapping("/stats/{userId}")
    public Result<ProfileStatsResponse> getProfileStats(@PathVariable Long userId) {
        return Result.success(profileService.getProfileStats(userId));
    }
}
