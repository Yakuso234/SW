CREATE TABLE IF NOT EXISTS video_product
(
    id             BIGINT UNSIGNED NOT NULL PRIMARY KEY,
    video_id       BIGINT UNSIGNED NOT NULL,
    creator_id     BIGINT UNSIGNED NOT NULL,
    name           VARCHAR(80)     NOT NULL,
    description    VARCHAR(255)    NULL,
    image_url      VARCHAR(255)    NULL,
    price_cent     INT             NOT NULL,
    stock          INT             NOT NULL,
    sold_count     INT             NOT NULL DEFAULT 0,
    status         TINYINT         NOT NULL DEFAULT 1,
    created_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_video_product_video (video_id),
    KEY idx_video_product_creator (creator_id, created_at)
) COMMENT '视频挂载商品';

CREATE TABLE IF NOT EXISTS video_flash_sale
(
    id              BIGINT UNSIGNED NOT NULL PRIMARY KEY,
    product_id      BIGINT UNSIGNED NOT NULL,
    creator_id      BIGINT UNSIGNED NOT NULL,
    sale_price_cent INT             NOT NULL,
    total_stock     INT             NOT NULL,
    sold_count      INT             NOT NULL DEFAULT 0,
    per_user_limit  INT             NOT NULL DEFAULT 1,
    starts_at       DATETIME        NOT NULL,
    ends_at         DATETIME        NOT NULL,
    status          TINYINT         NOT NULL DEFAULT 1,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_flash_sale_product (product_id),
    KEY idx_flash_sale_creator (creator_id, created_at)
) COMMENT '视频商品限时秒杀活动';

CREATE TABLE IF NOT EXISTS video_coupon_template
(
    id                  BIGINT UNSIGNED NOT NULL PRIMARY KEY,
    creator_id          BIGINT UNSIGNED NOT NULL,
    name                VARCHAR(80)     NOT NULL,
    threshold_cent      INT             NOT NULL DEFAULT 0,
    discount_cent       INT             NOT NULL,
    total_stock         INT             NOT NULL,
    claimed_count       INT             NOT NULL DEFAULT 0,
    starts_at           DATETIME        NOT NULL,
    ends_at             DATETIME        NOT NULL,
    status              TINYINT         NOT NULL DEFAULT 1,
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_coupon_template_creator (creator_id, created_at)
) COMMENT '视频电商优惠券模板';

CREATE TABLE IF NOT EXISTS video_user_coupon
(
    id              BIGINT UNSIGNED NOT NULL PRIMARY KEY,
    template_id     BIGINT UNSIGNED NOT NULL,
    user_id         BIGINT UNSIGNED NOT NULL,
    status          TINYINT         NOT NULL DEFAULT 0,
    locked_order_id BIGINT UNSIGNED NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_coupon (template_id, user_id),
    KEY idx_user_coupon_status (user_id, status, created_at)
) COMMENT '用户持有优惠券';

CREATE TABLE IF NOT EXISTS video_commerce_order
(
    id                  BIGINT UNSIGNED NOT NULL PRIMARY KEY,
    buyer_id            BIGINT UNSIGNED NOT NULL,
    creator_id          BIGINT UNSIGNED NOT NULL,
    video_id            BIGINT UNSIGNED NOT NULL,
    product_id          BIGINT UNSIGNED NOT NULL,
    flash_sale_id       BIGINT UNSIGNED NULL,
    user_coupon_id      BIGINT UNSIGNED NULL,
    product_name        VARCHAR(80)     NOT NULL,
    quantity            INT             NOT NULL DEFAULT 1,
    original_amount_cent INT            NOT NULL,
    discount_amount_cent INT            NOT NULL DEFAULT 0,
    payable_amount_cent INT             NOT NULL,
    receiver_name       VARCHAR(40)     NOT NULL,
    receiver_phone      VARCHAR(24)     NOT NULL,
    receiver_address    VARCHAR(255)    NOT NULL,
    status              TINYINT         NOT NULL DEFAULT 0,
    expire_at           DATETIME        NOT NULL,
    paid_at             DATETIME        NULL,
    shipped_at          DATETIME        NULL,
    completed_at        DATETIME        NULL,
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_flash_sale_buyer (flash_sale_id, buyer_id),
    KEY idx_commerce_order_buyer (buyer_id, status, created_at),
    KEY idx_commerce_order_creator (creator_id, status, created_at)
) COMMENT '视频电商订单';

CREATE TABLE IF NOT EXISTS video_refund_request
(
    id           BIGINT UNSIGNED NOT NULL PRIMARY KEY,
    order_id     BIGINT UNSIGNED NOT NULL,
    buyer_id     BIGINT UNSIGNED NOT NULL,
    creator_id   BIGINT UNSIGNED NOT NULL,
    reason       VARCHAR(255)    NOT NULL,
    status       TINYINT         NOT NULL DEFAULT 0,
    reply        VARCHAR(255)    NULL,
    created_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_refund_order (order_id),
    KEY idx_refund_creator (creator_id, status, created_at)
) COMMENT '视频电商售后申请';

