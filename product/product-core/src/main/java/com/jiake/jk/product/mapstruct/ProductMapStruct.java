package com.jiake.jk.product.mapstruct;

import com.jiake.jk.product.pojo.model.entity.Product;
import com.jiake.jk.product.pojo.response.ProductSummaryResponse;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface ProductMapStruct {
    ProductMapStruct INSTANCE = Mappers.getMapper(ProductMapStruct.class);

    ProductSummaryResponse toProductSummaryResponse(Product product);
}
