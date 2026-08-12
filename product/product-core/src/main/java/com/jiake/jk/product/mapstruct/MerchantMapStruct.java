package com.jiake.jk.product.mapstruct;

import com.jiake.jk.product.pojo.model.entity.Product;
import com.jiake.jk.product.pojo.model.entity.ProductSku;
import com.jiake.jk.product.pojo.request.PutProductBasicInfoRequest;
import com.jiake.jk.product.pojo.request.PutSkuRequest;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface MerchantMapStruct {
    MerchantMapStruct INSTANCE = Mappers.getMapper(MerchantMapStruct.class);

    Product putProductBasicInfoRequestToProduct(PutProductBasicInfoRequest putProductBasicInfoRequest);
    ProductSku putSkuRequestToProductSku(PutSkuRequest.PutSku putSku);
}
