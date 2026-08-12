package com.jiake.jk.order.controller;

import com.alipay.api.AlipayApiException;
import com.jiake.jk.common.response.Result;
import com.jiake.jk.common.utils.UserContext;
import com.jiake.jk.order.pojo.request.PostCartOrderRequest;
import com.jiake.jk.order.pojo.request.PostOrderRequest;
import com.jiake.jk.order.pojo.response.PendingPaymentOrderResponse;
import com.jiake.jk.order.pojo.response.PostOrderResponse;
import com.jiake.jk.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.coyote.BadRequestException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Order")
@RestController
@RequestMapping("/me")
public class OrderController {

    @Autowired
    private OrderService orderService;

    @Operation(summary = "生成订单（商品详情）")
    @PostMapping
    public Result<PostOrderResponse> postOrder(@RequestBody PostOrderRequest postOrderRequest) throws AlipayApiException {
        return Result.success(orderService.postOrder(UserContext.getUser(), postOrderRequest));
    }

    @Operation(summary = "生成订单（购物车）")
    @PostMapping("/cart")
    public Result<PostOrderResponse> postCartOrder(@RequestBody PostCartOrderRequest postCartOrderRequest) throws AlipayApiException {
        return Result.success(orderService.postCartOrder(UserContext.getUser(), postCartOrderRequest));
    }

    @Operation(summary = "获取待支付订单")
    @GetMapping("/pending-payment")
    public Result<List<PendingPaymentOrderResponse>> getPendingPaymentOrders() {
        return Result.success(orderService.getPendingPaymentOrders(UserContext.getUser()));
    }

    @Operation(summary = "获取订单支付状态")
    @GetMapping("/pay-status/{orderId}")
    public Result<Boolean> getIsPaid(@PathVariable Long orderId) {
        return Result.success(orderService.getIsPaid(orderId));
    }
}
