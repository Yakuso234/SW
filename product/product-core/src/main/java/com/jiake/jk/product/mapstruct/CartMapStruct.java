package com.jiake.jk.product.mapstruct;

import com.jiake.jk.product.pojo.model.entity.CartItem;
import com.jiake.jk.product.pojo.request.PostCartItemRequest;
import com.jiake.jk.product.pojo.response.CartItemResponse;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface CartMapStruct {
    CartMapStruct INSTANCE = Mappers.getMapper(CartMapStruct.class);

    CartItem postCartItemRequestToCartItem(PostCartItemRequest postCartItemRequest);
    CartItemResponse cartItemToCartItemResponse(CartItem cartItem);
}
