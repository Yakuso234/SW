package com.jiake.jk.video.controller._public;

import com.jiake.jk.common.response.Result;
import com.jiake.jk.video.pojo.response.UserCouponResponse;
import com.jiake.jk.video.pojo.response.VideoProductCardResponse;
import com.jiake.jk.video.service.VideoCommerceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/public/commerce")
@RequiredArgsConstructor
public class VideoCommercePublicController {
    private final VideoCommerceService commerceService;

    @GetMapping("/videos/{videoId}/product")
    public Result<VideoProductCardResponse> getProduct(@PathVariable Long videoId) {
        return Result.success(commerceService.getProductByVideo(videoId));
    }

    @GetMapping("/creators/{creatorId}/coupons")
    public Result<List<UserCouponResponse>> getCoupons(@PathVariable Long creatorId) {
        return Result.success(commerceService.getClaimableCoupons(creatorId));
    }
}

