package com.jiake.jk.user.controller;

import com.jiake.jk.user.pojo.request.PostMerchantRequest;
import com.jiake.jk.user.pojo.request.PutMerchantRequest;
import com.jiake.jk.common.response.Result;
import com.jiake.jk.common.utils.UserContext;
import com.jiake.jk.user.pojo.response.MerchantBasicDataResponse;
import com.jiake.jk.user.service.MerchantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Tag(name = "Merchant")
@RestController
@RequestMapping("/me/merchant")
public class MerchantController {

    @Autowired
    private MerchantService merchantService;

    @Operation(summary = "申请开店")
    @PostMapping
    public Result<Void> postMerchant(@ModelAttribute PostMerchantRequest postMerchantRequest) throws IOException {
        merchantService.postMerchant(UserContext.getUser(), postMerchantRequest);
        return Result.success();
    }

    @Operation(summary = "获取店铺基本信息")
    @GetMapping("/basic")
    public Result<MerchantBasicDataResponse> getBasicMerchant() {
        return Result.success(merchantService.getBasicMerchant(UserContext.getUser()));
    }

    @Operation(summary = "修改店铺基本信息")
    @PutMapping
    public Result<Void> putMerchant(@RequestBody PutMerchantRequest putMerchantRequest) throws IOException {
        merchantService.putMerchant(UserContext.getUser(), putMerchantRequest);
        return Result.success();
    }

    @Operation(summary = "上传（修改）店铺头像")
    @PostMapping("/avatar")
    public Result<String> postAvatar(MultipartFile avatar) throws IOException {
        return Result.success(merchantService.postAvatar(UserContext.getUser(), avatar));
    }
}
