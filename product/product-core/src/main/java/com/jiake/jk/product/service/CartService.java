package com.jiake.jk.product.service;

import com.jiake.jk.product.pojo.request.PostCartItemRequest;
import com.jiake.jk.product.pojo.request.PutCartItemQuantityRequest;
import com.jiake.jk.product.pojo.response.CartItemResponse;
import org.apache.coyote.BadRequestException;

import java.util.List;

public interface CartService {
    void postCartItem(Long userId, PostCartItemRequest postCartItemRequest) throws BadRequestException;

    List<CartItemResponse> getCartItem(Long userId);

    void putCartItemQuantity(Long userId, Long cartItemId, PutCartItemQuantityRequest putCartItemQuantityRequest);

    void deleteCartItem(Long userId, Long cartItemId);
}
