package com.jiake.jk.order.controller._public;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradeWapPayRequest;
import com.jiake.jk.order.mapper.OrderMapper;
import com.jiake.jk.order.pojo.entity.Order;
import com.jiake.jk.order.properties.AliPayProperties;
import com.jiake.jk.order.service.PayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;

@Tag(name = "PayPublic")
@RestController
@RequestMapping("/public/pay")
@RequiredArgsConstructor
public class PayPublicController {

   private final PayService payService;

    @Operation(summary = "支付宝支付成功回调")
    @PostMapping("/notify")
    public String payNotify(@RequestParam Map<String, String> params) throws AlipayApiException {
        return payService.payNotify(params);
    }

}
