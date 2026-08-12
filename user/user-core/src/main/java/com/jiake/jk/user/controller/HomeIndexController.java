package com.jiake.jk.user.controller;

import com.jiake.jk.user.pojo.response.MarketplaceHomeDataResponse;
import com.jiake.jk.common.response.Result;
import com.jiake.jk.common.utils.UserContext;
import com.jiake.jk.user.service.HomeIndexService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "HomeIndex")
@RestController
@RequestMapping("/me")
public class HomeIndexController {

    @Autowired
    private HomeIndexService homeIndexService;

    @Operation(summary = "获取商城主页需要的用户基本数据")
    @GetMapping("/marketplace/home/data")
    public Result<MarketplaceHomeDataResponse> getMarketplaceHomeData() {
        return Result.success(homeIndexService.getMarketplaceHomeData(UserContext.getUser()));
    }

}
