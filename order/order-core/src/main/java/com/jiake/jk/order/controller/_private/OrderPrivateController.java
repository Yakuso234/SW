package com.jiake.jk.order.controller._private;

import com.jiake.jk.common.response.Result;
import com.jiake.jk.order.pojo.response.MerchantDailySalesResponse;
import com.jiake.jk.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "OrderPrivate")
@RestController
@RequestMapping("/private")
public class OrderPrivateController {

    @Autowired
    private OrderService orderService;

    @Operation(summary = "get merchant daily sales")
    @PostMapping("/merchant/daily-sales")
    public Result<MerchantDailySalesResponse> getMerchantDailySales(@RequestBody List<Long> productIdList) {
        return Result.success(orderService.getMerchantDailySales(productIdList));
    }
}
