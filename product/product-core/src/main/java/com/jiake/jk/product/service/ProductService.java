package com.jiake.jk.product.service;

import com.jiake.jk.common.mybatis.BaseIService;
import com.jiake.jk.product.pojo.model.entity.Product;
import com.jiake.jk.product.pojo.response.GetOnSaleProductForLiveResponse;
import com.jiake.jk.product.pojo.response.PartOfCartOrderResponse;
import com.jiake.jk.product.pojo.response.PartOfOrderResponse;
import com.jiake.jk.product.pojo.response.ProductDetailResponse;
import com.jiake.jk.product.pojo.response.ProductSummaryResponse;
import org.apache.coyote.BadRequestException;

import java.util.List;
import java.util.Map;

public interface ProductService extends BaseIService<Product> {
    List<ProductSummaryResponse> getProducts();

    List<ProductSummaryResponse> searchProducts(String keyword);

    ProductDetailResponse getDetailProducts(Long productId);

    Boolean isMerchantOnSaleProduct(Long merchantId, Long productId);

    Map<Long, GetOnSaleProductForLiveResponse> getProductsForLive(List<Long> productIdList);

    PartOfOrderResponse getPartOfOrder(Long orderId, Long productId, Long skuId, Integer quantity) throws BadRequestException, InterruptedException;

    Map<Long, PartOfCartOrderResponse> getPartOfCartOrder(Long orderId, List<Long> cartItemIdList);

    void putReservedStock(Long skuId, Integer quantity);

    void increaseSalesVolume(Map<Long, Integer> productQuantityMap);
}
