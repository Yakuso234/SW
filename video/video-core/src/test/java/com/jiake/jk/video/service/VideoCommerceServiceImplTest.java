package com.jiake.jk.video.service;

import com.jiake.jk.common.exception.YHClientException;
import com.jiake.jk.common.utils.SnowflakeUtils;
import com.jiake.jk.video.mapper.*;
import com.jiake.jk.video.pojo.entity.VideoCommerceOrder;
import com.jiake.jk.video.pojo.entity.VideoFlashSale;
import com.jiake.jk.video.pojo.entity.VideoProduct;
import com.jiake.jk.video.pojo.request.CreateCommerceOrderRequest;
import com.jiake.jk.video.service.impl.VideoCommerceServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VideoCommerceServiceImplTest {
    @BeforeAll
    static void initializeMybatisPlusLambdaCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "VideoCommerceServiceImplTest");
        TableInfoHelper.initTableInfo(assistant, VideoCommerceOrder.class);
    }

    @Mock private VideoMapper videoMapper;
    @Mock private VideoProductMapper productMapper;
    @Mock private VideoFlashSaleMapper flashSaleMapper;
    @Mock private VideoCouponTemplateMapper couponTemplateMapper;
    @Mock private VideoUserCouponMapper userCouponMapper;
    @Mock private VideoCommerceOrderMapper orderMapper;
    @Mock private VideoRefundRequestMapper refundMapper;
    @Mock private SnowflakeUtils snowflakeUtils;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private RedisScript<Long> reserveScript;
    @Mock private RedisScript<Long> releaseScript;
    @Mock private RedisScript<Long> restockScript;

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void createOrder_shouldRejectWhenLuaReportsSoldOut() {
        VideoCommerceServiceImpl service = newService();
        stubActiveSale();
        when(redisTemplate.hasKey(anyString())).thenReturn(true);
        when(redisTemplate.execute(eq(reserveScript), anyList(), any(), any(), any())).thenReturn(-1L);

        YHClientException exception = assertThrows(YHClientException.class,
                () -> service.createOrder(10L, orderRequest()));

        assertEquals("秒杀库存已抢完", exception.getMessage());
        verify(orderMapper, never()).insert(any());
    }

    @Test
    void createOrder_shouldReleaseStockAndBuyerWhenDatabaseTransactionRollsBack() {
        VideoCommerceServiceImpl service = newService();
        stubActiveSale();
        when(redisTemplate.hasKey(anyString())).thenReturn(true);
        when(redisTemplate.execute(eq(reserveScript), anyList(), any(), any(), any())).thenReturn(1L);
        when(snowflakeUtils.nextId()).thenReturn(900L);
        when(orderMapper.insert(any(VideoCommerceOrder.class))).thenReturn(1);
        TransactionSynchronizationManager.initSynchronization();

        service.createOrder(10L, orderRequest());
        verify(redisTemplate, never()).execute(eq(releaseScript), anyList(), any());

        TransactionSynchronizationUtils.triggerAfterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        verify(redisTemplate).execute(eq(releaseScript), anyList(), eq("10"));
    }

    @Test
    void cancel_shouldRestockOnlyAfterDatabaseCommit() {
        VideoCommerceServiceImpl service = newService();
        VideoCommerceOrder order = new VideoCommerceOrder();
        order.setId(700L);
        order.setBuyerId(10L);
        order.setFlashSaleId(20L);
        order.setStatus(VideoCommerceOrder.Status.PENDING_PAYMENT);
        when(orderMapper.selectById(700L)).thenReturn(order);
        when(orderMapper.update(any())).thenReturn(1);
        TransactionSynchronizationManager.initSynchronization();

        service.cancel(10L, 700L);
        verify(redisTemplate, never()).execute(eq(restockScript), anyList());

        TransactionSynchronizationUtils.triggerAfterCommit();
        verify(redisTemplate).execute(eq(restockScript), anyList());
        verify(redisTemplate, never()).execute(eq(releaseScript), anyList(), any());
    }

    private void stubActiveSale() {
        VideoFlashSale sale = new VideoFlashSale();
        sale.setId(20L);
        sale.setProductId(30L);
        sale.setSalePriceCent(990);
        sale.setTotalStock(10);
        sale.setSoldCount(0);
        sale.setStartsAt(LocalDateTime.now().minusMinutes(1));
        sale.setEndsAt(LocalDateTime.now().plusMinutes(10));
        sale.setStatus(VideoFlashSale.Status.ACTIVE);
        VideoProduct product = new VideoProduct();
        product.setId(30L);
        product.setVideoId(40L);
        product.setCreatorId(50L);
        product.setName("测试商品");
        product.setStatus(VideoProduct.Status.ON_SHELF);
        when(flashSaleMapper.selectById(20L)).thenReturn(sale);
        when(productMapper.selectById(30L)).thenReturn(product);
    }

    private CreateCommerceOrderRequest orderRequest() {
        return new CreateCommerceOrderRequest(20L, null, "测试用户", "13800000000", "测试地址");
    }

    private VideoCommerceServiceImpl newService() {
        return new VideoCommerceServiceImpl(videoMapper, productMapper, flashSaleMapper,
                couponTemplateMapper, userCouponMapper, orderMapper, refundMapper, snowflakeUtils,
                redisTemplate, reserveScript, releaseScript, restockScript);
    }
}
