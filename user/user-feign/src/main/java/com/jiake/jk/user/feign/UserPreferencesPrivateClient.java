package com.jiake.jk.user.feign;

import com.jiake.jk.common.response.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(value = "userService", contextId = "UserPreferencesPrivateClient")
public interface UserPreferencesPrivateClient {
    @GetMapping("/user/api/private/preferences/video/{userId}")
    Result<float[]> getUserVideoPreferences(@PathVariable Long userId);
}
