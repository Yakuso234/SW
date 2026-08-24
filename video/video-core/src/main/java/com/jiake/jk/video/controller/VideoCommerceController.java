package com.jiake.jk.video.controller;

import com.jiake.jk.common.response.Result;
import com.jiake.jk.common.utils.UserContext;
import com.jiake.jk.video.pojo.request.*;
import com.jiake.jk.video.pojo.response.*;
import com.jiake.jk.video.service.VideoCommerceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/me/commerce")
@RequiredArgsConstructor
public class VideoCommerceController {
    private final VideoCommerceService commerceService;

    @PostMapping("/products")
    public Result<VideoProductCardResponse> createProduct(@RequestBody CreateVideoProductRequest request) {
        return Result.success(commerceService.createProduct(UserContext.getUser(), request));
    }

    @GetMapping("/creator/products")
    public Result<List<VideoProductCardResponse>> creatorProducts() {
        return Result.success(commerceService.getCreatorProducts(UserContext.getUser()));
    }

    @PostMapping("/flash-sales")
    public Result<VideoProductCardResponse> createFlashSale(@RequestBody CreateFlashSaleRequest request) {
        return Result.success(commerceService.createFlashSale(UserContext.getUser(), request));
    }

    @PostMapping("/coupon-templates")
    public Result<UserCouponResponse> createCouponTemplate(@RequestBody CreateCouponTemplateRequest request) {
        return Result.success(commerceService.createCouponTemplate(UserContext.getUser(), request));
    }

    @PostMapping("/coupons/{templateId}/claim")
    public Result<Void> claimCoupon(@PathVariable Long templateId) {
        commerceService.claimCoupon(UserContext.getUser(), templateId);
        return Result.success();
    }

    @GetMapping("/coupons")
    public Result<List<UserCouponResponse>> coupons() {
        return Result.success(commerceService.getMyCoupons(UserContext.getUser()));
    }

    @PostMapping("/orders")
    public Result<CommerceOrderResponse> createOrder(@RequestBody CreateCommerceOrderRequest request) {
        return Result.success(commerceService.createOrder(UserContext.getUser(), request));
    }

    @GetMapping("/orders")
    public Result<List<CommerceOrderResponse>> orders() {
        return Result.success(commerceService.getMyOrders(UserContext.getUser()));
    }

    @GetMapping("/creator/orders")
    public Result<List<CommerceOrderResponse>> creatorOrders() {
        return Result.success(commerceService.getCreatorOrders(UserContext.getUser()));
    }

    @PostMapping("/orders/{orderId}/pay")
    public Result<Void> pay(@PathVariable Long orderId) {
        commerceService.pay(UserContext.getUser(), orderId);
        return Result.success();
    }

    @PostMapping("/orders/{orderId}/cancel")
    public Result<Void> cancel(@PathVariable Long orderId) {
        commerceService.cancel(UserContext.getUser(), orderId);
        return Result.success();
    }

    @PostMapping("/creator/orders/{orderId}/ship")
    public Result<Void> ship(@PathVariable Long orderId) {
        commerceService.ship(UserContext.getUser(), orderId);
        return Result.success();
    }

    @PostMapping("/orders/{orderId}/complete")
    public Result<Void> complete(@PathVariable Long orderId) {
        commerceService.complete(UserContext.getUser(), orderId);
        return Result.success();
    }

    @PostMapping("/orders/{orderId}/refund")
    public Result<RefundResponse> refund(@PathVariable Long orderId, @RequestBody CreateRefundRequest request) {
        return Result.success(commerceService.requestRefund(UserContext.getUser(), orderId, request));
    }

    @GetMapping("/refunds")
    public Result<List<RefundResponse>> refunds() {
        return Result.success(commerceService.getMyRefunds(UserContext.getUser()));
    }

    @GetMapping("/creator/refunds")
    public Result<List<RefundResponse>> creatorRefunds() {
        return Result.success(commerceService.getCreatorRefunds(UserContext.getUser()));
    }

    @PostMapping("/creator/refunds/{refundId}/review")
    public Result<Void> reviewRefund(@PathVariable Long refundId, @RequestBody ReviewRefundRequest request) {
        commerceService.reviewRefund(UserContext.getUser(), refundId, request);
        return Result.success();
    }
}
