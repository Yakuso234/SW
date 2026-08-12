package com.jiake.jk.user.controller._public;

import com.jiake.jk.common.response.Result;
import com.jiake.jk.common.utils.UserContext;
import com.jiake.jk.user.pojo.response.UserSearchResponse;
import com.jiake.jk.user.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/public")
public class UserPublicController {

    private final UserService userService;

    public UserPublicController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/search")
    public Result<List<UserSearchResponse>> search(@RequestParam String query) {
        return Result.success(userService.search(UserContext.getUser(), query));
    }

}
