package com.jiake.jk.user.feign;

import com.jiake.jk.common.response.Result;
import com.jiake.jk.user.pojo.response.UserInfoInListResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

@FeignClient(value = "userService", contextId = "UserPrivateClient")
public interface UserPrivateClient {
    @GetMapping("/user/api/private/name")
    Result<String> getName(@RequestParam Long id);

    @GetMapping("/user/api/private/name/batch")
    Result<Map<Long, String>> getNameBatch(@RequestParam List<Long> idList);

    @GetMapping("/user/api/private/info-in-list")
    Result<List<UserInfoInListResponse>> getUserInfoInList(@RequestParam List<Long> idList);
}
