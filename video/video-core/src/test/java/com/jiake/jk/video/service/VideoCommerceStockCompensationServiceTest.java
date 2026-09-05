package com.jiake.jk.video.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.jiake.jk.video.mapper.VideoCommerceStockCompensationMapper;
import com.jiake.jk.video.mapper.VideoFlashSaleMapper;
import com.jiake.jk.video.pojo.entity.VideoCommerceStockCompensation;
import com.jiake.jk.video.pojo.entity.VideoFlashSale;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VideoCommerceStockCompensationServiceTest {
    @BeforeAll
    static void initializeMybatisPlusLambdaCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration,
                "VideoCommerceStockCompensationServiceTest");
        TableInfoHelper.initTableInfo(assistant, VideoCommerceStockCompensation.class);
    }

    @Mock private VideoCommerceStockCompensationMapper compensationMapper;
    @Mock private VideoFlashSaleMapper flashSaleMapper;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private RedisScript<Long> restockScript;

    @Test
    void process_shouldMarkSuccessWhenLuaAppliedStock() {
        VideoCommerceStockCompensation task = processingTask();
        when(compensationMapper.update(any())).thenReturn(1, 1);
        when(compensationMapper.selectById(101L)).thenReturn(task);
        stubMarkerTtl();
        when(redisTemplate.execute(eq(restockScript), anyList(), anyString())).thenReturn(1L);

        newService().processAfterCommit(101L);

        verify(redisTemplate).execute(eq(restockScript),
                eq(java.util.List.of("sw:commerce:flash-sale:202", "sw:commerce:flash-sale:202:restocked:303")),
                anyString());
        verify(compensationMapper, times(2)).update(any());
    }

    @Test
    void process_shouldTreatLuaIdempotentReplayAsSuccess() {
        VideoCommerceStockCompensation task = processingTask();
        when(compensationMapper.update(any())).thenReturn(1, 1);
        when(compensationMapper.selectById(101L)).thenReturn(task);
        stubMarkerTtl();
        when(redisTemplate.execute(eq(restockScript), anyList(), anyString())).thenReturn(0L);

        newService().processAfterCommit(101L);

        verify(compensationMapper, times(2)).update(any());
    }

    @Test
    void process_shouldKeepDurableTaskForRetryWhenRedisFails() {
        VideoCommerceStockCompensation task = processingTask();
        when(compensationMapper.update(any())).thenReturn(1, 1);
        when(compensationMapper.selectById(101L)).thenReturn(task);
        stubMarkerTtl();
        when(redisTemplate.execute(eq(restockScript), anyList(), anyString()))
                .thenThrow(new RedisConnectionFailureException("redis unavailable"));

        newService().processAfterCommit(101L);

        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<VideoCommerceStockCompensation>>
                captor = ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper.class);
        verify(compensationMapper, times(2)).update(captor.capture());
        assertEquals(2, captor.getAllValues().size());
    }

    private VideoCommerceStockCompensationService newService() {
        return new VideoCommerceStockCompensationService(compensationMapper, flashSaleMapper, redisTemplate, restockScript);
    }

    private void stubMarkerTtl() {
        VideoFlashSale sale = new VideoFlashSale();
        sale.setEndsAt(java.time.LocalDateTime.now().plusMinutes(30));
        when(flashSaleMapper.selectById(202L)).thenReturn(sale);
    }

    private VideoCommerceStockCompensation processingTask() {
        VideoCommerceStockCompensation task = new VideoCommerceStockCompensation();
        task.setId(101L);
        task.setFlashSaleId(202L);
        task.setOrderId(303L);
        task.setStatus(VideoCommerceStockCompensation.Status.PROCESSING);
        task.setRetryCount(0);
        return task;
    }
}
