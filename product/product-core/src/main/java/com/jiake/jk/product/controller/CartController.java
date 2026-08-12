package com.jiake.jk.product.controller;

import com.jiake.jk.common.response.Result;
import com.jiake.jk.common.utils.UserContext;
import com.jiake.jk.product.pojo.request.PostCartItemRequest;
import com.jiake.jk.product.pojo.request.PutCartItemQuantityRequest;
import com.jiake.jk.product.pojo.response.CartItemResponse;
import com.jiake.jk.product.service.CartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.coyote.BadRequestException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Cart")
@RestController
@RequestMapping("/me/cart")
public class CartController {

    @Autowired
    private CartService cartService;

    @Operation(summary = "商品加入购物车")
    @PostMapping
    public Result<Void> postCartItem(@RequestBody PostCartItemRequest postCartItemRequest) throws BadRequestException {
        cartService.postCartItem(UserContext.getUser(), postCartItemRequest);
        return Result.success();
    }

    @Operation(summary = "获取购物车商品")
    @GetMapping
    public Result<List<CartItemResponse>> getCartItem() {
        return Result.success(cartService.getCartItem(UserContext.getUser()));
    }

    @Operation(summary = "购物车商品数量变化")
    @PutMapping("/quantity/{cartItemId}")
    public Result<Void> putCartItemQuantity(@PathVariable Long cartItemId, @RequestBody PutCartItemQuantityRequest putCartItemQuantityRequest) {
        cartService.putCartItemQuantity(UserContext.getUser(), cartItemId, putCartItemQuantityRequest);
        return Result.success();
    }

    @Operation(summary = "删除购物车项")
    @DeleteMapping("/{cartItemId}")
    public Result<Void> deleteCartItem(@PathVariable Long cartItemId) {
        cartService.deleteCartItem(UserContext.getUser(), cartItemId);
        return Result.success();
    }
}
