# SW 短视频微服务平台：简历项目说明

> 定位：个人重构与扩展项目，参考原项目并经作者许可。聚焦“视频上传到异步发布”的可靠性闭环；不将商城、直播、订单等存量 CRUD 作为简历亮点。

## 一句话概述

面向创作者的视频平台后端，围绕大文件直传、异步转码发布、下游故障隔离与可观测性重构核心链路；以轻量 AI 创作助手提供权限受控的处理状态查询和失败诊断。

## 建议放在简历中的写法

**SW 短视频微服务平台｜个人重构项目**  
`Java 21 · Spring Boot 3 · Spring Cloud Alibaba · Gateway · MySQL · Redis · RabbitMQ · MinIO · FFmpeg · Prometheus/Grafana · Spring AI · Vue 3`

- 设计“预签名直传 MinIO → 本地事务 Outbox → RabbitMQ → FFmpeg 转码/抽帧 → 状态回写”的视频异步发布链路，将视频、处理任务和消息投递状态解耦；通过状态机、唯一约束和消费者幂等应对重复投递。
- 针对处理回写不可用引入处理租约与 Outbox 补偿恢复；针对关注流下游不可用设计 RabbitMQ TTL 延迟重试、最终 DLQ 和审计式人工恢复，恢复不直接 requeue，仍复用 Outbox 保证可追踪投递。
- 接入 Actuator、Micrometer、Prometheus/Grafana 与 TraceId，覆盖转码、Outbox、关注流重试/死信/恢复和 AI 工具调用；固定开发机串行 5 样本端到端基线为 P50 **3.50 s**、P95 **3.51 s**（预签名请求至 `PUBLISHED`，包含 MinIO/RabbitMQ/FFmpeg，不代表生产 TPS）。
- 基于 Spring AI + Qwen 构建只读创作助手：Gateway JWT 透传身份，SSE 流式回复，ToolContext 在服务端绑定创作者身份；真实验证状态查询工具调用并由 Prometheus 记录低基数指标，避免前端传入用户 ID 或模型越权查询。

## 可追问的架构事实

| 面试问题 | 可回答的事实 |
| --- | --- |
| 为什么不用同步上传后立刻发布？ | 上传成功不代表媒体可转码或封面可抽取。业务提交只创建处理任务和 Outbox，Processor 成功后才推进为 `PUBLISHED`。 |
| 为什么需要 Outbox？ | 业务状态与待投递消息同事务落库，避免“库已提交但 MQ 未发”或“消息已发但库回滚”；投递失败可由扫描补偿。 |
| 消费重复如何处理？ | 处理任务使用合法状态迁移，关注流 Inbox 用 `(recipient_id, video_id)` 唯一键，恢复记录用 `(message_digest, recovery_attempt)` 区分同一次崩溃补偿与新恢复尝试。 |
| 下游服务不可用怎么办？ | 发布主状态机不回滚；关注流消息在 Broker 中延迟重试 2 次，第 3 次进入 DLQ。恢复先落审计和新的 Outbox，再 ACK 原死信消息，避免直接 requeue 失去治理边界。 |
| AI 为什么不是另一个 Agent 项目？ | AI 只解决创作者“查状态/看失败原因/生成文案”的辅助需求；工具只读、服务端身份约束、无长期记忆和写操作，重点仍是微服务可靠性。 |

## 证据与边界

- 核心回归已覆盖 Gateway 私有路由拦截与限流、视频状态机/Outbox/租约恢复、关注流重试和死信恢复、Processor 消费、AI 工具权限与诊断；GitHub Actions `Core Regression` 已在远端多次成功。
- 已真实演练：无效媒体进入 `REJECTED / FAILED / SUCCESS`；处理回写不可用后经租约恢复到 `PUBLISHED / SUCCEEDED`；关注流最终 DLQ 通过审计式恢复清空并由唯一键保护业务去重。
- 本机前端以 Vue 3 仅覆盖公开流、JWT 登录、创作者工作台、上传状态与创作助手；不以页面数量作为项目亮点。
- 所有性能数据都限定在单机 Docker 与 IDEA 本地服务、串行测试媒体的回归场景，不能推导生产并发上限、通用 QPS 或线上 SLA。
