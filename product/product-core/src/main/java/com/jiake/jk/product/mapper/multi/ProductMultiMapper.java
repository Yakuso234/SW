package com.jiake.jk.product.mapper.multi;

import com.jiake.jk.product.pojo.model.multi.ProductPartOfCartItem;
import com.jiake.jk.product.pojo.response.ProductDetailResponse;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ProductMultiMapper {
    ProductDetailResponse selectProductDetail(Long productId);

    List<ProductPartOfCartItem> selectPartOfCartItem(List<Long> productIdList);
}
