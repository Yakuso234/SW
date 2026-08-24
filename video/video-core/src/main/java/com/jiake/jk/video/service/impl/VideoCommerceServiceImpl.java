package com.jiake.jk.video.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.jiake.jk.common.exception.YHClientException;
import com.jiake.jk.common.utils.SnowflakeUtils;
import com.jiake.jk.video.mapper.*;
import com.jiake.jk.video.pojo.entity.*;
import com.jiake.jk.video.pojo.request.*;
import com.jiake.jk.video.pojo.response.*;
import com.jiake.jk.video.service.VideoCommerceService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

@Service
public class VideoCommerceServiceImpl implements VideoCommerceService {
    private static final String CAMPAIGN_KEY = "sw:commerce:flash-sale:%d";
    private static final String BUYER_KEY = "sw:commerce:flash-sale:%d:buyers";

    private final VideoMapper videoMapper;
    private final VideoProductMapper productMapper;
    private final VideoFlashSaleMapper flashSaleMapper;
    private final VideoCouponTemplateMapper couponTemplateMapper;
    private final VideoUserCouponMapper userCouponMapper;
    private final VideoCommerceOrderMapper orderMapper;
    private final VideoRefundRequestMapper refundMapper;
    private final SnowflakeUtils snowflakeUtils;
    private final StringRedisTemplate redisTemplate;
    private final RedisScript<Long> reserveScript;
    private final RedisScript<Long> releaseScript;
    private final RedisScript<Long> restockScript;

    public VideoCommerceServiceImpl(VideoMapper videoMapper,
                                    VideoProductMapper productMapper,
                                    VideoFlashSaleMapper flashSaleMapper,
                                    VideoCouponTemplateMapper couponTemplateMapper,
                                    VideoUserCouponMapper userCouponMapper,
                                    VideoCommerceOrderMapper orderMapper,
                                    VideoRefundRequestMapper refundMapper,
                                    SnowflakeUtils snowflakeUtils,
                                    StringRedisTemplate redisTemplate,
                                    @Qualifier("flashSaleReserveScript") RedisScript<Long> reserveScript,
                                    @Qualifier("flashSaleReleaseScript") RedisScript<Long> releaseScript,
                                    @Qualifier("flashSaleRestockScript") RedisScript<Long> restockScript) {
        this.videoMapper = videoMapper;
        this.productMapper = productMapper;
        this.flashSaleMapper = flashSaleMapper;
        this.couponTemplateMapper = couponTemplateMapper;
        this.userCouponMapper = userCouponMapper;
        this.orderMapper = orderMapper;
        this.refundMapper = refundMapper;
        this.snowflakeUtils = snowflakeUtils;
        this.redisTemplate = redisTemplate;
        this.reserveScript = reserveScript;
        this.releaseScript = releaseScript;
        this.restockScript = restockScript;
    }

    @Override
    @Transactional
    public VideoProductCardResponse createProduct(Long creatorId, CreateVideoProductRequest request) {
        require(request != null && request.videoId() != null, "请选择已发布视频");
        requireText(request.name(), 80, "商品名称");
        require(request.priceCent() != null && request.priceCent() > 0, "商品价格必须大于 0");
        require(request.stock() != null && request.stock() > 0 && request.stock() <= 1_000_000, "商品库存范围无效");
        Video video = videoMapper.selectById(request.videoId());
        require(video != null && Objects.equals(video.getCreatorId(), creatorId)
                && video.getStatus() == Video.VideoStatus.PUBLISHED, "只能为自己的已发布视频挂载商品");
        require(productMapper.selectCount(new LambdaQueryWrapper<VideoProduct>()
                .eq(VideoProduct::getVideoId, request.videoId())) == 0, "该视频已挂载商品");

        VideoProduct product = new VideoProduct();
        product.setId(snowflakeUtils.nextId());
        product.setVideoId(request.videoId());
        product.setCreatorId(creatorId);
        product.setName(request.name().trim());
        product.setDescription(trimToNull(request.description(), 255));
        product.setImageUrl(trimToNull(request.imageUrl(), 255));
        product.setPriceCent(request.priceCent());
        product.setStock(request.stock());
        product.setSoldCount(0);
        product.setStatus(VideoProduct.Status.ON_SHELF);
        productMapper.insert(product);
        return toProductCard(product, null);
    }

    @Override
    @Transactional
    public VideoProductCardResponse createFlashSale(Long creatorId, CreateFlashSaleRequest request) {
        require(request != null && request.productId() != null, "请选择商品");
        VideoProduct product = productMapper.selectById(request.productId());
        require(product != null && Objects.equals(product.getCreatorId(), creatorId), "商品不存在或无权操作");
        require(request.salePriceCent() != null && request.salePriceCent() > 0
                && request.salePriceCent() <= product.getPriceCent(), "秒杀价必须大于 0 且不高于原价");
        require(request.totalStock() != null && request.totalStock() > 0
                && request.totalStock() <= product.getStock(), "活动库存不能超过商品库存");
        require(request.perUserLimit() != null && request.perUserLimit() == 1, "当前版本每人限购 1 件");
        require(request.startsAt() != null && request.endsAt() != null
                && request.startsAt().isBefore(request.endsAt()), "活动时间范围无效");
        require(flashSaleMapper.selectCount(new LambdaQueryWrapper<VideoFlashSale>()
                .eq(VideoFlashSale::getProductId, product.getId())) == 0, "该商品已配置秒杀活动");

        VideoFlashSale sale = new VideoFlashSale();
        sale.setId(snowflakeUtils.nextId());
        sale.setProductId(product.getId());
        sale.setCreatorId(creatorId);
        sale.setSalePriceCent(request.salePriceCent());
        sale.setTotalStock(request.totalStock());
        sale.setSoldCount(0);
        sale.setPerUserLimit(1);
        sale.setStartsAt(request.startsAt());
        sale.setEndsAt(request.endsAt());
        sale.setStatus(VideoFlashSale.Status.ACTIVE);
        flashSaleMapper.insert(sale);
        cacheCampaign(sale);
        return toProductCard(product, sale);
    }

    @Override
    @Transactional
    public UserCouponResponse createCouponTemplate(Long creatorId, CreateCouponTemplateRequest request) {
        require(request != null, "优惠券参数不能为空");
        requireText(request.name(), 80, "优惠券名称");
        require(request.thresholdCent() != null && request.thresholdCent() >= 0, "使用门槛无效");
        require(request.discountCent() != null && request.discountCent() > 0
                && request.discountCent() <= Math.max(1, request.thresholdCent()), "优惠金额无效");
        require(request.totalStock() != null && request.totalStock() > 0, "优惠券数量无效");
        require(request.startsAt() != null && request.endsAt() != null
                && request.startsAt().isBefore(request.endsAt()), "优惠券有效期无效");
        VideoCouponTemplate template = new VideoCouponTemplate();
        template.setId(snowflakeUtils.nextId());
        template.setCreatorId(creatorId);
        template.setName(request.name().trim());
        template.setThresholdCent(request.thresholdCent());
        template.setDiscountCent(request.discountCent());
        template.setTotalStock(request.totalStock());
        template.setClaimedCount(0);
        template.setStartsAt(request.startsAt());
        template.setEndsAt(request.endsAt());
        template.setStatus(VideoCouponTemplate.Status.ACTIVE);
        couponTemplateMapper.insert(template);
        return toCoupon(template, null);
    }

    @Override
    public List<VideoProductCardResponse> getCreatorProducts(Long creatorId) {
        return productMapper.selectList(new LambdaQueryWrapper<VideoProduct>()
                        .eq(VideoProduct::getCreatorId, creatorId).orderByDesc(VideoProduct::getCreatedAt))
                .stream().map(product -> toProductCard(product, findSale(product.getId()))).toList();
    }

    @Override
    public VideoProductCardResponse getProductByVideo(Long videoId) {
        VideoProduct product = productMapper.selectOne(new LambdaQueryWrapper<VideoProduct>()
                .eq(VideoProduct::getVideoId, videoId).eq(VideoProduct::getStatus, VideoProduct.Status.ON_SHELF));
        return product == null ? null : toProductCard(product, findSale(product.getId()));
    }

    @Override
    public List<UserCouponResponse> getClaimableCoupons(Long creatorId) {
        LocalDateTime now = LocalDateTime.now();
        return couponTemplateMapper.selectList(new LambdaQueryWrapper<VideoCouponTemplate>()
                        .eq(VideoCouponTemplate::getCreatorId, creatorId)
                        .eq(VideoCouponTemplate::getStatus, VideoCouponTemplate.Status.ACTIVE)
                        .le(VideoCouponTemplate::getStartsAt, now).gt(VideoCouponTemplate::getEndsAt, now)
                        .orderByDesc(VideoCouponTemplate::getCreatedAt))
                .stream().map(template -> toCoupon(template, "CLAIMABLE")).toList();
    }

    @Override
    @Transactional
    public void claimCoupon(Long userId, Long templateId) {
        VideoCouponTemplate template = couponTemplateMapper.selectById(templateId);
        LocalDateTime now = LocalDateTime.now();
        require(template != null && template.getStatus() == VideoCouponTemplate.Status.ACTIVE
                && !now.isBefore(template.getStartsAt()) && now.isBefore(template.getEndsAt()), "优惠券当前不可领取");
        require(userCouponMapper.selectCount(new LambdaQueryWrapper<VideoUserCoupon>()
                .eq(VideoUserCoupon::getTemplateId, templateId).eq(VideoUserCoupon::getUserId, userId)) == 0, "请勿重复领取");
        int reserved = couponTemplateMapper.update(new LambdaUpdateWrapper<VideoCouponTemplate>()
                .setSql("claimed_count = claimed_count + 1")
                .eq(VideoCouponTemplate::getId, templateId)
                .lt(VideoCouponTemplate::getClaimedCount, template.getTotalStock())
                .eq(VideoCouponTemplate::getStatus, VideoCouponTemplate.Status.ACTIVE));
        require(reserved == 1, "优惠券已领完");
        VideoUserCoupon coupon = new VideoUserCoupon();
        coupon.setId(snowflakeUtils.nextId());
        coupon.setTemplateId(templateId);
        coupon.setUserId(userId);
        coupon.setStatus(VideoUserCoupon.Status.AVAILABLE);
        try {
            userCouponMapper.insert(coupon);
        } catch (DataIntegrityViolationException exception) {
            throw new YHClientException("请勿重复领取");
        }
    }

    @Override
    public List<UserCouponResponse> getMyCoupons(Long userId) {
        return userCouponMapper.selectList(new LambdaQueryWrapper<VideoUserCoupon>()
                        .eq(VideoUserCoupon::getUserId, userId).orderByDesc(VideoUserCoupon::getCreatedAt))
                .stream().map(coupon -> toCoupon(couponTemplateMapper.selectById(coupon.getTemplateId()), coupon.getStatus().name(), coupon.getId()))
                .toList();
    }

    @Override
    @Transactional
    public CommerceOrderResponse createOrder(Long buyerId, CreateCommerceOrderRequest request) {
        require(request != null && request.flashSaleId() != null, "请选择秒杀活动");
        requireText(request.receiverName(), 40, "收货人");
        requireText(request.receiverPhone(), 24, "联系电话");
        requireText(request.receiverAddress(), 255, "收货地址");
        VideoFlashSale sale = flashSaleMapper.selectById(request.flashSaleId());
        require(sale != null && sale.getStatus() == VideoFlashSale.Status.ACTIVE, "秒杀活动不存在或已关闭");
        VideoProduct product = productMapper.selectById(sale.getProductId());
        require(product != null && product.getStatus() == VideoProduct.Status.ON_SHELF, "商品已下架");
        ensureCampaignCache(sale);
        Long reserved = redisTemplate.execute(reserveScript,
                List.of(campaignKey(sale.getId()), buyerKey(sale.getId())),
                String.valueOf(System.currentTimeMillis()), String.valueOf(buyerId),
                String.valueOf(Math.max(60, Duration.between(LocalDateTime.now(), sale.getEndsAt().plusHours(1)).toSeconds())));
        handleReserveResult(reserved);

        VideoCommerceOrder order = new VideoCommerceOrder();
        order.setId(snowflakeUtils.nextId());
        order.setBuyerId(buyerId);
        order.setCreatorId(product.getCreatorId());
        order.setVideoId(product.getVideoId());
        order.setProductId(product.getId());
        order.setFlashSaleId(sale.getId());
        order.setProductName(product.getName());
        order.setQuantity(1);
        order.setOriginalAmountCent(sale.getSalePriceCent());
        order.setDiscountAmountCent(0);
        order.setReceiverName(request.receiverName().trim());
        order.setReceiverPhone(request.receiverPhone().trim());
        order.setReceiverAddress(request.receiverAddress().trim());
        order.setStatus(VideoCommerceOrder.Status.PENDING_PAYMENT);
        order.setExpireAt(LocalDateTime.now().plusMinutes(15));

        registerReservationCompensation(sale.getId(), buyerId);
        if (request.userCouponId() != null) {
            applyCoupon(order, request.userCouponId(), buyerId);
        }
        order.setPayableAmountCent(Math.max(1, order.getOriginalAmountCent() - order.getDiscountAmountCent()));
        try {
            orderMapper.insert(order);
        } catch (DataIntegrityViolationException exception) {
            throw new YHClientException("当前活动每人只能下单一次");
        }
        return toOrder(order);
    }

    @Override
    public List<CommerceOrderResponse> getMyOrders(Long buyerId) {
        return orderMapper.selectList(new LambdaQueryWrapper<VideoCommerceOrder>()
                        .eq(VideoCommerceOrder::getBuyerId, buyerId).orderByDesc(VideoCommerceOrder::getCreatedAt))
                .stream().map(this::toOrder).toList();
    }

    @Override
    public List<CommerceOrderResponse> getCreatorOrders(Long creatorId) {
        return orderMapper.selectList(new LambdaQueryWrapper<VideoCommerceOrder>()
                        .eq(VideoCommerceOrder::getCreatorId, creatorId).orderByDesc(VideoCommerceOrder::getCreatedAt))
                .stream().map(this::toOrder).toList();
    }

    @Override
    @Transactional
    public void pay(Long buyerId, Long orderId) {
        VideoCommerceOrder order = getBuyerOrder(buyerId, orderId);
        require(order.getStatus() == VideoCommerceOrder.Status.PENDING_PAYMENT
                && order.getExpireAt().isAfter(LocalDateTime.now()), "订单已无法支付");
        int updated = orderMapper.update(new LambdaUpdateWrapper<VideoCommerceOrder>()
                .set(VideoCommerceOrder::getStatus, VideoCommerceOrder.Status.PAID)
                .set(VideoCommerceOrder::getPaidAt, LocalDateTime.now())
                .eq(VideoCommerceOrder::getId, orderId)
                .eq(VideoCommerceOrder::getStatus, VideoCommerceOrder.Status.PENDING_PAYMENT));
        require(updated == 1, "订单状态已变化，请刷新");
        if (order.getUserCouponId() != null) {
            userCouponMapper.update(new LambdaUpdateWrapper<VideoUserCoupon>()
                    .set(VideoUserCoupon::getStatus, VideoUserCoupon.Status.USED)
                    .eq(VideoUserCoupon::getId, order.getUserCouponId())
                    .eq(VideoUserCoupon::getLockedOrderId, orderId)
                    .eq(VideoUserCoupon::getStatus, VideoUserCoupon.Status.LOCKED));
        }
        flashSaleMapper.update(new LambdaUpdateWrapper<VideoFlashSale>()
                .setSql("sold_count = sold_count + 1").eq(VideoFlashSale::getId, order.getFlashSaleId()));
        productMapper.update(new LambdaUpdateWrapper<VideoProduct>()
                .setSql("sold_count = sold_count + 1").eq(VideoProduct::getId, order.getProductId()));
    }

    @Override
    @Transactional
    public void cancel(Long buyerId, Long orderId) {
        VideoCommerceOrder order = getBuyerOrder(buyerId, orderId);
        require(order.getStatus() == VideoCommerceOrder.Status.PENDING_PAYMENT, "只有待支付订单可以取消");
        closeUnpaidOrder(order, VideoCommerceOrder.Status.CANCELLED);
    }

    @Override
    public void ship(Long creatorId, Long orderId) {
        int updated = orderMapper.update(new LambdaUpdateWrapper<VideoCommerceOrder>()
                .set(VideoCommerceOrder::getStatus, VideoCommerceOrder.Status.SHIPPED)
                .set(VideoCommerceOrder::getShippedAt, LocalDateTime.now())
                .eq(VideoCommerceOrder::getId, orderId).eq(VideoCommerceOrder::getCreatorId, creatorId)
                .eq(VideoCommerceOrder::getStatus, VideoCommerceOrder.Status.PAID));
        require(updated == 1, "只有已支付订单可以发货");
    }

    @Override
    public void complete(Long buyerId, Long orderId) {
        int updated = orderMapper.update(new LambdaUpdateWrapper<VideoCommerceOrder>()
                .set(VideoCommerceOrder::getStatus, VideoCommerceOrder.Status.COMPLETED)
                .set(VideoCommerceOrder::getCompletedAt, LocalDateTime.now())
                .eq(VideoCommerceOrder::getId, orderId).eq(VideoCommerceOrder::getBuyerId, buyerId)
                .eq(VideoCommerceOrder::getStatus, VideoCommerceOrder.Status.SHIPPED));
        require(updated == 1, "只有已发货订单可以确认收货");
    }

    @Override
    @Transactional
    public RefundResponse requestRefund(Long buyerId, Long orderId, CreateRefundRequest request) {
        require(request != null, "退款参数不能为空");
        requireText(request.reason(), 255, "退款原因");
        VideoCommerceOrder order = getBuyerOrder(buyerId, orderId);
        require(EnumSet.of(VideoCommerceOrder.Status.PAID, VideoCommerceOrder.Status.SHIPPED,
                VideoCommerceOrder.Status.COMPLETED, VideoCommerceOrder.Status.REFUND_REJECTED).contains(order.getStatus()), "当前订单状态不能申请退款");
        require(refundMapper.selectCount(new LambdaQueryWrapper<VideoRefundRequest>()
                .eq(VideoRefundRequest::getOrderId, orderId)) == 0, "该订单已有售后记录");
        VideoRefundRequest refund = new VideoRefundRequest();
        refund.setId(snowflakeUtils.nextId());
        refund.setOrderId(orderId);
        refund.setBuyerId(buyerId);
        refund.setCreatorId(order.getCreatorId());
        refund.setReason(request.reason().trim());
        refund.setStatus(VideoRefundRequest.Status.PENDING);
        refundMapper.insert(refund);
        orderMapper.update(new LambdaUpdateWrapper<VideoCommerceOrder>()
                .set(VideoCommerceOrder::getStatus, VideoCommerceOrder.Status.REFUND_REQUESTED)
                .eq(VideoCommerceOrder::getId, orderId));
        return toRefund(refund);
    }

    @Override
    public List<RefundResponse> getMyRefunds(Long buyerId) {
        return refundMapper.selectList(new LambdaQueryWrapper<VideoRefundRequest>()
                        .eq(VideoRefundRequest::getBuyerId, buyerId).orderByDesc(VideoRefundRequest::getCreatedAt))
                .stream().map(this::toRefund).toList();
    }

    @Override
    public List<RefundResponse> getCreatorRefunds(Long creatorId) {
        return refundMapper.selectList(new LambdaQueryWrapper<VideoRefundRequest>()
                        .eq(VideoRefundRequest::getCreatorId, creatorId).orderByDesc(VideoRefundRequest::getCreatedAt))
                .stream().map(this::toRefund).toList();
    }

    @Override
    @Transactional
    public void reviewRefund(Long creatorId, Long refundId, ReviewRefundRequest request) {
        VideoRefundRequest refund = refundMapper.selectById(refundId);
        require(refund != null && Objects.equals(refund.getCreatorId(), creatorId)
                && refund.getStatus() == VideoRefundRequest.Status.PENDING, "售后申请不存在或已处理");
        VideoRefundRequest.Status refundStatus = request.approved()
                ? VideoRefundRequest.Status.APPROVED : VideoRefundRequest.Status.REJECTED;
        VideoCommerceOrder.Status orderStatus = request.approved()
                ? VideoCommerceOrder.Status.REFUNDED : VideoCommerceOrder.Status.REFUND_REJECTED;
        refundMapper.update(new LambdaUpdateWrapper<VideoRefundRequest>()
                .set(VideoRefundRequest::getStatus, refundStatus)
                .set(VideoRefundRequest::getReply, trimToNull(request.reply(), 255))
                .eq(VideoRefundRequest::getId, refundId)
                .eq(VideoRefundRequest::getStatus, VideoRefundRequest.Status.PENDING));
        orderMapper.update(new LambdaUpdateWrapper<VideoCommerceOrder>()
                .set(VideoCommerceOrder::getStatus, orderStatus)
                .eq(VideoCommerceOrder::getId, refund.getOrderId())
                .eq(VideoCommerceOrder::getStatus, VideoCommerceOrder.Status.REFUND_REQUESTED));
    }

    @Scheduled(fixedDelayString = "${sw.commerce.order-expire-scan-ms:30000}")
    @Transactional
    public void expireUnpaidOrders() {
        List<VideoCommerceOrder> expired = orderMapper.selectList(new LambdaQueryWrapper<VideoCommerceOrder>()
                .eq(VideoCommerceOrder::getStatus, VideoCommerceOrder.Status.PENDING_PAYMENT)
                .le(VideoCommerceOrder::getExpireAt, LocalDateTime.now()).last("LIMIT 100"));
        expired.forEach(order -> closeUnpaidOrder(order, VideoCommerceOrder.Status.EXPIRED));
    }

    private void applyCoupon(VideoCommerceOrder order, Long couponId, Long buyerId) {
        VideoUserCoupon coupon = userCouponMapper.selectById(couponId);
        require(coupon != null && Objects.equals(coupon.getUserId(), buyerId)
                && coupon.getStatus() == VideoUserCoupon.Status.AVAILABLE, "优惠券不可用");
        VideoCouponTemplate template = couponTemplateMapper.selectById(coupon.getTemplateId());
        LocalDateTime now = LocalDateTime.now();
        require(template != null && template.getStatus() == VideoCouponTemplate.Status.ACTIVE
                && !now.isBefore(template.getStartsAt()) && now.isBefore(template.getEndsAt())
                && order.getOriginalAmountCent() >= template.getThresholdCent(), "优惠券不满足使用条件");
        int locked = userCouponMapper.update(new LambdaUpdateWrapper<VideoUserCoupon>()
                .set(VideoUserCoupon::getStatus, VideoUserCoupon.Status.LOCKED)
                .set(VideoUserCoupon::getLockedOrderId, order.getId())
                .eq(VideoUserCoupon::getId, couponId).eq(VideoUserCoupon::getUserId, buyerId)
                .eq(VideoUserCoupon::getStatus, VideoUserCoupon.Status.AVAILABLE));
        require(locked == 1, "优惠券已被其他订单占用");
        order.setUserCouponId(couponId);
        order.setDiscountAmountCent(Math.min(template.getDiscountCent(), order.getOriginalAmountCent() - 1));
    }

    private void closeUnpaidOrder(VideoCommerceOrder order, VideoCommerceOrder.Status status) {
        int updated = orderMapper.update(new LambdaUpdateWrapper<VideoCommerceOrder>()
                .set(VideoCommerceOrder::getStatus, status)
                .eq(VideoCommerceOrder::getId, order.getId())
                .eq(VideoCommerceOrder::getStatus, VideoCommerceOrder.Status.PENDING_PAYMENT));
        if (updated == 0) return;
        registerRestockAfterCommit(order.getFlashSaleId());
        if (order.getUserCouponId() != null) {
            userCouponMapper.update(new LambdaUpdateWrapper<VideoUserCoupon>()
                    .set(VideoUserCoupon::getStatus, VideoUserCoupon.Status.AVAILABLE)
                    .set(VideoUserCoupon::getLockedOrderId, null)
                    .eq(VideoUserCoupon::getId, order.getUserCouponId())
                    .eq(VideoUserCoupon::getLockedOrderId, order.getId())
                    .eq(VideoUserCoupon::getStatus, VideoUserCoupon.Status.LOCKED));
        }
    }

    private void registerReservationCompensation(Long saleId, Long buyerId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) releaseReservation(saleId, buyerId);
            }
        });
    }

    private void releaseReservation(Long saleId, Long buyerId) {
        redisTemplate.execute(releaseScript, List.of(campaignKey(saleId), buyerKey(saleId)), String.valueOf(buyerId));
    }

    private void registerRestockAfterCommit(Long saleId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            restock(saleId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                restock(saleId);
            }
        });
    }

    private void restock(Long saleId) {
        redisTemplate.execute(restockScript, List.of(campaignKey(saleId)));
    }

    private void ensureCampaignCache(VideoFlashSale sale) {
        if (Boolean.FALSE.equals(redisTemplate.hasKey(campaignKey(sale.getId())))) cacheCampaign(sale);
    }

    private void cacheCampaign(VideoFlashSale sale) {
        String key = campaignKey(sale.getId());
        List<VideoCommerceOrder> orders = orderMapper.selectList(new LambdaQueryWrapper<VideoCommerceOrder>()
                .eq(VideoCommerceOrder::getFlashSaleId, sale.getId()));
        long pendingCount = orders.stream()
                .filter(order -> order.getStatus() == VideoCommerceOrder.Status.PENDING_PAYMENT)
                .count();
        int remainingStock = (int) Math.max(0, sale.getTotalStock() - sale.getSoldCount() - pendingCount);
        Map<String, String> values = Map.of(
                "status", sale.getStatus().name(),
                "stock", String.valueOf(remainingStock),
                "startsAt", String.valueOf(epochMillis(sale.getStartsAt())),
                "endsAt", String.valueOf(epochMillis(sale.getEndsAt())));
        redisTemplate.opsForHash().putAll(key, values);
        Duration ttl = Duration.ofSeconds(Math.max(60,
                Duration.between(LocalDateTime.now(), sale.getEndsAt().plusHours(1)).toSeconds()));
        redisTemplate.expire(key, ttl);
        String buyerSetKey = buyerKey(sale.getId());
        redisTemplate.delete(buyerSetKey);
        Set<String> buyerIds = new HashSet<>();
        orders.forEach(order -> buyerIds.add(String.valueOf(order.getBuyerId())));
        if (!buyerIds.isEmpty()) redisTemplate.opsForSet().add(buyerSetKey, buyerIds.toArray(String[]::new));
        redisTemplate.expire(buyerSetKey, ttl);
    }

    private void handleReserveResult(Long result) {
        if (Objects.equals(result, 1L)) return;
        if (Objects.equals(result, -1L)) throw new YHClientException("秒杀库存已抢完");
        if (Objects.equals(result, -2L)) throw new YHClientException("当前活动每人只能下单一次");
        if (Objects.equals(result, -5L)) throw new YHClientException("秒杀活动尚未开始");
        if (Objects.equals(result, -6L)) throw new YHClientException("秒杀活动已经结束");
        throw new YHClientException("秒杀活动暂不可用，请稍后重试");
    }

    private VideoFlashSale findSale(Long productId) {
        return flashSaleMapper.selectOne(new LambdaQueryWrapper<VideoFlashSale>()
                .eq(VideoFlashSale::getProductId, productId).last("LIMIT 1"));
    }

    private VideoProductCardResponse toProductCard(VideoProduct product, VideoFlashSale sale) {
        Integer remaining = product.getStock() - product.getSoldCount();
        String activityStatus = "NORMAL";
        if (sale != null) {
            Object cached = redisTemplate.opsForHash().get(campaignKey(sale.getId()), "stock");
            remaining = cached == null ? remainingStock(sale) : Integer.valueOf(cached.toString());
            LocalDateTime now = LocalDateTime.now();
            activityStatus = sale.getStatus() == VideoFlashSale.Status.CLOSED ? "CLOSED"
                    : now.isBefore(sale.getStartsAt()) ? "UPCOMING" : !now.isBefore(sale.getEndsAt()) ? "ENDED" : "ACTIVE";
        }
        return VideoProductCardResponse.builder().productId(product.getId()).videoId(product.getVideoId())
                .name(product.getName()).description(product.getDescription()).imageUrl(product.getImageUrl())
                .originalPriceCent(product.getPriceCent()).salePriceCent(sale == null ? null : sale.getSalePriceCent())
                .remainingStock(Math.max(0, remaining)).flashSaleId(sale == null ? null : sale.getId())
                .perUserLimit(sale == null ? null : sale.getPerUserLimit()).startsAt(sale == null ? null : sale.getStartsAt())
                .endsAt(sale == null ? null : sale.getEndsAt()).activityStatus(activityStatus).build();
    }

    private UserCouponResponse toCoupon(VideoCouponTemplate template, String status) {
        return toCoupon(template, status, null);
    }

    private UserCouponResponse toCoupon(VideoCouponTemplate template, String status, Long couponId) {
        if (template == null) return null;
        return UserCouponResponse.builder().id(couponId).templateId(template.getId()).name(template.getName())
                .thresholdCent(template.getThresholdCent()).discountCent(template.getDiscountCent())
                .status(status == null ? template.getStatus().name() : status)
                .startsAt(template.getStartsAt()).endsAt(template.getEndsAt()).build();
    }

    private CommerceOrderResponse toOrder(VideoCommerceOrder order) {
        return CommerceOrderResponse.builder().id(order.getId()).videoId(order.getVideoId())
                .productId(order.getProductId()).productName(order.getProductName())
                .originalAmountCent(order.getOriginalAmountCent()).discountAmountCent(order.getDiscountAmountCent())
                .payableAmountCent(order.getPayableAmountCent()).status(order.getStatus().name())
                .expireAt(order.getExpireAt()).createdAt(order.getCreatedAt()).build();
    }

    private RefundResponse toRefund(VideoRefundRequest refund) {
        return RefundResponse.builder().id(refund.getId()).orderId(refund.getOrderId()).reason(refund.getReason())
                .status(refund.getStatus().name()).reply(refund.getReply()).createdAt(refund.getCreatedAt()).build();
    }

    private VideoCommerceOrder getBuyerOrder(Long buyerId, Long orderId) {
        VideoCommerceOrder order = orderMapper.selectById(orderId);
        require(order != null && Objects.equals(order.getBuyerId(), buyerId), "订单不存在");
        return order;
    }

    private String campaignKey(Long saleId) { return CAMPAIGN_KEY.formatted(saleId); }
    private String buyerKey(Long saleId) { return BUYER_KEY.formatted(saleId); }
    private int remainingStock(VideoFlashSale sale) {
        long pendingCount = orderMapper.selectCount(new LambdaQueryWrapper<VideoCommerceOrder>()
                .eq(VideoCommerceOrder::getFlashSaleId, sale.getId())
                .eq(VideoCommerceOrder::getStatus, VideoCommerceOrder.Status.PENDING_PAYMENT));
        return (int) Math.max(0, sale.getTotalStock() - sale.getSoldCount() - pendingCount);
    }
    private long epochMillis(LocalDateTime time) { return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(); }

    private static void require(boolean condition, String message) {
        if (!condition) throw new YHClientException(message);
    }

    private static void requireText(String value, int maxLength, String field) {
        require(value != null && !value.isBlank() && value.trim().length() <= maxLength, field + "不能为空或超过长度限制");
    }

    private static String trimToNull(String value, int maxLength) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }
}
