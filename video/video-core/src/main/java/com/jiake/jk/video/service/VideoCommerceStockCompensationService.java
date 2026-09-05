package com.jiake.jk.video.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.jiake.jk.video.mapper.VideoCommerceStockCompensationMapper;
import com.jiake.jk.video.mapper.VideoFlashSaleMapper;
import com.jiake.jk.video.pojo.entity.VideoCommerceStockCompensation;
import com.jiake.jk.video.pojo.entity.VideoFlashSale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 库存回补不依赖一次 afterCommit 回调完成：任务先随订单状态变化持久化，
 * 然后由本类立即或定时执行。Lua 使用订单号作为幂等标记，因此崩溃重试不会重复加库存。
 */
@Slf4j
@Service
public class VideoCommerceStockCompensationService {
    private static final int BATCH_SIZE = 100;
    private static final int LEASE_SECONDS = 30;
    private static final String CAMPAIGN_KEY = "sw:commerce:flash-sale:%d";
    private static final String COMPENSATION_MARKER_KEY = "sw:commerce:flash-sale:%d:restocked:%d";

    private final VideoCommerceStockCompensationMapper compensationMapper;
    private final VideoFlashSaleMapper flashSaleMapper;
    private final StringRedisTemplate redisTemplate;
    private final RedisScript<Long> restockScript;

    public VideoCommerceStockCompensationService(VideoCommerceStockCompensationMapper compensationMapper,
                                                  VideoFlashSaleMapper flashSaleMapper,
                                                  StringRedisTemplate redisTemplate,
                                                  @Qualifier("flashSaleRestockScript") RedisScript<Long> restockScript) {
        this.compensationMapper = compensationMapper;
        this.flashSaleMapper = flashSaleMapper;
        this.redisTemplate = redisTemplate;
        this.restockScript = restockScript;
    }

    /** 在外层事务提交后调用；失败会保留任务给定时扫描。 */
    public void processAfterCommit(Long compensationId) {
        process(compensationId);
    }

    @Scheduled(fixedDelayString = "${sw.commerce.stock-compensation-scan-ms:5000}")
    public void retryDueCompensations() {
        LocalDateTime now = LocalDateTime.now();
        reclaimExpiredLeases(now);
        List<VideoCommerceStockCompensation> due = compensationMapper.selectList(
                new LambdaQueryWrapper<VideoCommerceStockCompensation>()
                        .select(VideoCommerceStockCompensation::getId)
                        .in(VideoCommerceStockCompensation::getStatus,
                                VideoCommerceStockCompensation.Status.PENDING,
                                VideoCommerceStockCompensation.Status.FAILED)
                        .and(wrapper -> wrapper.isNull(VideoCommerceStockCompensation::getNextRetryAt)
                                .or().le(VideoCommerceStockCompensation::getNextRetryAt, now))
                        .orderByAsc(VideoCommerceStockCompensation::getCreatedAt)
                        .last("LIMIT " + BATCH_SIZE));
        due.forEach(task -> process(task.getId()));
    }

    void process(Long compensationId) {
        LocalDateTime now = LocalDateTime.now();
        int claimed = compensationMapper.update(new LambdaUpdateWrapper<VideoCommerceStockCompensation>()
                .set(VideoCommerceStockCompensation::getStatus, VideoCommerceStockCompensation.Status.PROCESSING)
                .set(VideoCommerceStockCompensation::getLeaseExpireAt, now.plusSeconds(LEASE_SECONDS))
                .set(VideoCommerceStockCompensation::getNextRetryAt, null)
                .eq(VideoCommerceStockCompensation::getId, compensationId)
                .in(VideoCommerceStockCompensation::getStatus,
                        VideoCommerceStockCompensation.Status.PENDING,
                        VideoCommerceStockCompensation.Status.FAILED)
                .and(wrapper -> wrapper.isNull(VideoCommerceStockCompensation::getNextRetryAt)
                        .or().le(VideoCommerceStockCompensation::getNextRetryAt, now)));
        if (claimed == 0) {
            return;
        }

        VideoCommerceStockCompensation task = compensationMapper.selectById(compensationId);
        if (task == null) {
            return;
        }
        try {
            Long result = redisTemplate.execute(restockScript,
                    List.of(campaignKey(task.getFlashSaleId()), markerKey(task.getFlashSaleId(), task.getOrderId())),
                    String.valueOf(markerTtlSeconds(task.getFlashSaleId())));
            if (!Objects.equals(result, 0L) && !Objects.equals(result, 1L)) {
                throw new IllegalStateException("库存回补脚本返回未知结果: " + result);
            }
            compensationMapper.update(new LambdaUpdateWrapper<VideoCommerceStockCompensation>()
                    .set(VideoCommerceStockCompensation::getStatus, VideoCommerceStockCompensation.Status.SUCCESS)
                    .set(VideoCommerceStockCompensation::getCompletedAt, LocalDateTime.now())
                    .set(VideoCommerceStockCompensation::getLeaseExpireAt, null)
                    .set(VideoCommerceStockCompensation::getLastError, null)
                    .eq(VideoCommerceStockCompensation::getId, compensationId)
                    .eq(VideoCommerceStockCompensation::getStatus, VideoCommerceStockCompensation.Status.PROCESSING));
        } catch (RuntimeException exception) {
            markRetry(compensationId, exception);
        }
    }

    private void reclaimExpiredLeases(LocalDateTime now) {
        compensationMapper.update(new LambdaUpdateWrapper<VideoCommerceStockCompensation>()
                .set(VideoCommerceStockCompensation::getStatus, VideoCommerceStockCompensation.Status.FAILED)
                .set(VideoCommerceStockCompensation::getNextRetryAt, now)
                .set(VideoCommerceStockCompensation::getLeaseExpireAt, null)
                .eq(VideoCommerceStockCompensation::getStatus, VideoCommerceStockCompensation.Status.PROCESSING)
                .le(VideoCommerceStockCompensation::getLeaseExpireAt, now));
    }

    private void markRetry(Long compensationId, RuntimeException exception) {
        VideoCommerceStockCompensation task = compensationMapper.selectById(compensationId);
        if (task == null || task.getStatus() != VideoCommerceStockCompensation.Status.PROCESSING) {
            return;
        }
        int retryCount = (task.getRetryCount() == null ? 0 : task.getRetryCount()) + 1;
        LocalDateTime nextRetryAt = LocalDateTime.now().plusSeconds(1L << Math.min(retryCount, 6));
        int updated = compensationMapper.update(new LambdaUpdateWrapper<VideoCommerceStockCompensation>()
                .set(VideoCommerceStockCompensation::getStatus, VideoCommerceStockCompensation.Status.FAILED)
                .set(VideoCommerceStockCompensation::getRetryCount, retryCount)
                .set(VideoCommerceStockCompensation::getNextRetryAt, nextRetryAt)
                .set(VideoCommerceStockCompensation::getLeaseExpireAt, null)
                .set(VideoCommerceStockCompensation::getLastError, abbreviate(exception.getMessage()))
                .eq(VideoCommerceStockCompensation::getId, compensationId)
                .eq(VideoCommerceStockCompensation::getStatus, VideoCommerceStockCompensation.Status.PROCESSING));
        if (updated == 1) {
            log.warn("视频秒杀库存回补失败，任务={}, 下次重试={}", compensationId, nextRetryAt, exception);
        }
    }

    private String campaignKey(Long saleId) {
        return CAMPAIGN_KEY.formatted(saleId);
    }

    private String markerKey(Long saleId, Long orderId) {
        return COMPENSATION_MARKER_KEY.formatted(saleId, orderId);
    }

    private long markerTtlSeconds(Long saleId) {
        VideoFlashSale sale = flashSaleMapper.selectById(saleId);
        if (sale == null || sale.getEndsAt() == null) {
            return 60;
        }
        return Math.max(60, java.time.Duration.between(LocalDateTime.now(), sale.getEndsAt().plusHours(1)).toSeconds());
    }

    private String abbreviate(String message) {
        if (message == null || message.isBlank()) {
            return "Redis库存回补失败";
        }
        return message.length() <= 255 ? message : message.substring(0, 255);
    }
}
