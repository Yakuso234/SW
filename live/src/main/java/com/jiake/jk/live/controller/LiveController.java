package com.jiake.jk.live.controller;

import com.jiake.jk.common.response.Result;
import com.jiake.jk.common.utils.UserContext;
import com.jiake.jk.live.request.StartLiveRequest;
import com.jiake.jk.live.service.LiveService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@Tag(name = "Live")
@RestController
@RequestMapping("/me")
public class LiveController {

    @Autowired
    LiveService liveService;

    @Operation(summary = "请求开始直播")
    @PostMapping("/start")
    public Result<String> postStartLive(@ModelAttribute StartLiveRequest startLiveRequest) throws IOException {
        return Result.success(liveService.postStartLive(UserContext.getUser(), startLiveRequest));
    }

}
