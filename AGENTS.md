# SW 项目开发说明

本文件适用于仓库内所有后端模块。开发时优先遵循本说明，再参考具体模块已有代码风格。

## 项目概览

SW 是基于 Spring Cloud 的智能短视频平台。秋招核心主线聚焦用户、视频、异步处理、内容互动与 AI 创作者助手；商城、订单、直播、聊天和管理端存量域已从当前工作树移除。

主要模块：

- `parent`：统一依赖版本管理。
- `common`：公共能力，包括统一响应、异常、工具类、认证上下文、Redis、MyBatis Plus、Feign、Nacos、Sentinel 等。
- `gateway`：API 网关，负责路由、认证过滤和文档聚合。
- `user`：用户、登录注册、关注、地址等。
- `video`：短视频、互动、评论、收藏、标签、搜索、审核等。
- `video-processor`：视频转码与处理服务。
- `ai`：智能对话、Spring AI、响应式接口等。
- `mcp-server`：探索性工具适配模块，不属于当前完成主线。

## 技术栈

- Java 21
- Spring Boot 3.2.0
- Spring Cloud 2023.0.0
- Spring Cloud Alibaba 2023.0.3.3
- MyBatis / MyBatis Plus
- OpenFeign
- Nacos
- Redis / Redisson
- RabbitMQ
- Elasticsearch
- XXL-Job
- MinIO / S3
- Seata
- Sentinel
- Knife4j / springdoc
- Spring AI

## 模块结构约定

常见领域服务通常拆分为：

- `{domain}-pojo`：实体、枚举、请求 DTO、响应 DTO、MQ 消息体。
- `{domain}-feign`：跨服务调用的 Feign Client。
- `{domain}-core`：业务实现，包括 controller、service、mapper、consumer、config 等。

常见包职责：

- `controller._public`：前端或外部接口。
- `controller._private`：内部 Feign 接口实现。
- `service` / `service.impl`：业务接口与实现。
- `mapper`：MyBatis Plus Mapper。
- `pojo.entity`：数据库实体。
- `pojo.request`：请求对象。
- `pojo.response`：响应对象。
- `pojo.mq`：MQ 消息体。
- `constant`：常量，如 exchange、queue、routing key。
- `consumer`：消息消费者。
- `config`：配置类。

部分模块还有额外的子包约定，编写或查找代码时优先按这些目录理解：

- `video-processor`：主要包含 `consumer`、`config`、`constant`、`entity`、`mapper`、`serivce` / `serivce.impl`，其中目录名就是 `serivce`。
- `ai`：除通用分层外，还会使用 `cache`、`filter`、`pool`、`repository`、`websocket`。
- `mcp-server`：工具类通常直接放在根包下，作为 MCP tool provider 使用。

## 开发原则

- 优先复用 `common` 中已有能力，避免重复实现公共逻辑。
- HTTP 接口统一使用 `Result<T>` 返回，除非现有代码已有其他约定。
- Controller 保持薄层，复杂业务放到 Service。
- 涉及登录用户时，优先复用已有用户上下文/JWT 处理方式，不要重复解析 token。
- 新增跨服务调用时，需要同时维护：
    - 调用方 `*-feign`
    - 服务方 `controller._private`
    - 相关 DTO
    - Feign 扫描配置
- Feign Client 的 `value` 使用服务注册名，`contextId` 保持唯一。
- 公开接口和内部 Feign 接口分开放置。
- 新增实体、Mapper、Service 时，遵循现有 MyBatis Plus 风格。
- 涉及 MQ 时：
    - 消息体放入 `pojo.mq`
    - exchange / queue / routing key 放入 `constant`
    - 消费者需考虑幂等、重复消费和异常重试
- 涉及缓存、互动计数、任务恢复、Feed 扇出或分布式锁时，注意幂等性、事务边界和并发安全。
- 涉及 AI 或响应式接口时，避免在 Reactor 链路中直接执行阻塞调用。
- 注释请使用中文。

## 项目记忆与验证

- 进度不确定时，先阅读 `docs/SW-工作流程副记忆.md`，再阅读 `docs/SW-项目记忆.md`。
- 开发中出现有面试价值的故障、取舍或验证结论时，更新 `docs/SW-面试问题清单.md`。
- “已完成”必须对应代码、测试、脚本、日志、指标或现场联调证据；生产化增强项与当前缺陷分开记录。
- Docker Compose 只运行 `sw-dev` 隔离中间件，Java 服务由 IDEA 使用 JDK 21 启动。
