-- 已存在的 Docker MySQL 数据卷不会重新执行 .file/sql/yh.sql。
-- 对本地开发库执行本脚本即可，无需重置数据。
create table if not exists video_processing_task
(
    id              bigint unsigned not null comment '处理任务id'
        primary key,
    video_id        bigint unsigned not null comment '视频id',
    status          tinyint unsigned not null comment '0待处理，1处理中，2成功，3失败',
    retry_count     int unsigned    not null default 0 comment '已抢占处理次数',
    lease_expire_at datetime        null comment '处理租约到期时间',
    error_message   varchar(512)    null comment '最近一次处理失败原因',
    updated_at      datetime        null comment '更新时间',
    created_at      datetime        null comment '创建时间',
    constraint uk_video_processing_task_video_id unique (video_id)
)
    comment '视频异步处理任务表' charset = utf8mb3;

create index idx_video_processing_task_recovery
    on video_processing_task (status, lease_expire_at);
