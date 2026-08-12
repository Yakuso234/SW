package com.jiake.jk.order.feign;

import com.jiake.jk.common.response.Result;
import com.jiake.jk.order.pojo.response.MerchantDailySalesResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(value = "orderService", contextId = "OrderPrivateClient")
public interface OrderPrivateClient {
    @PostMapping("/order/api/private/merchant/daily-sales")
    Result<MerchantDailySalesResponse> getMerchantDailySales(@RequestBody List<Long> productIdList);
}
