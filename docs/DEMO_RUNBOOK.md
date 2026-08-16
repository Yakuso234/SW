# SW 本地演示 Runbook

本文只覆盖简历主线：可靠视频异步处理、内容消费互动、受限 AI 创作工具与可观测性。执行前请确认仅在本机开发环境操作；脚本会产生本地测试数据，但不输出密码或模型密钥。

## 0. 演示边界

- 演示的核心不是页面数量，而是“创作者可可靠发布，消费者可刷流互动，失败可诊断，下游故障可隔离并可审计恢复”。
- 固定环境延迟脚本只用于同一台机器的改动前后回归，不能用于生产 TPS 宣传。
- AI 服务的 Qwen Key 仅配置在 IDEA 本地环境变量 `QWEN_API_KEY`，不写入仓库、`.env` 或演示录屏。

## 1. 启动与健康检查

启动 Docker 基础设施：

```powershell
docker compose up -d mysql redis rabbitmq minio nacos
docker compose --profile observability up -d prometheus grafana
```

在 IDEA 依次启动：User `10088`、Video `10091`、Video Processor `10092`、Gateway `10086`。AI `10094` 仅在已配置本地 `QWEN_API_KEY` 时启动。

核验重点：

```text
Gateway:         http://localhost:10086/actuator/health
Video:           http://localhost:10091/video/api/actuator/health
Video Processor: http://localhost:10092/video-processor/api/actuator/health
AI:              http://localhost:10094/ai/api/actuator/health
Prometheus:      http://localhost:9090/-/ready
Grafana:         http://localhost:3000
```

Prometheus Targets 中 Gateway、Video、Video Processor 应为 `UP`；启动 AI 后，`sw-ai` 也应为 `UP`。如未启动 AI，不把 SSE 工具视为本次现场可演示能力。

## 2. 成功上传：异步处理主链路

可选页面演示：在 `C:\Users\52373\Desktop\SW-web` 启动 Vite，注册/登录后从发布页上传一段短 MP4，再在作品工作台观察状态变为已发布。

也可用可复现脚本验证：

```powershell
.\scripts\verify-video-e2e.ps1
```

成功条件：脚本返回同一条测试视频的 `PUBLISHED / SUCCEEDED / SUCCESS`，分别对应视频、处理任务和 Outbox 状态。讲解时按以下顺序展示：预签名直传 MinIO -> 本地事务写业务数据与 Outbox -> RabbitMQ 消费 -> FFmpeg -> 回写状态。

## 2.5 内容消费：刷流、互动与关注

打开 `http://127.0.0.1:18889/`，注册临时本地账号后演示：

1. 在“发现”页查看公开时间 Feed，点击“加载更多”验证游标分页。
2. 对一条公开视频执行点赞、收藏和评论；评论提交后提示“互动计数会异步聚合”，刷新后以服务端计数为准。
3. 关注创作者并切换“关注”页；新发布视频会由发布事件写入关注者 Inbox，再以游标读取关注 Feed。

讲解边界：点赞/收藏先在 Redis 原子切换用户状态，消息携带 `eventId`；消费者将事件写入幂等表后才更新聚合计数。评论使用 Outbox，因此互动接口追求低延迟，计数采用最终一致而不是同步强一致。

## 3. 失败处理：状态、指标与受限诊断

```powershell
.\scripts\verify-video-failure-e2e.ps1
```

该脚本会提交内容故意无效的隔离媒体。成功条件是状态自然进入 `REJECTED / FAILED / SUCCESS`，并可在 Prometheus 查看 `sw_video_transcoding_failures_total` 增加。若 AI `10094` 已启动，可由该视频所属创作者在助手页面询问失败原因；工具只返回可操作的摘要与建议，不泄露对象路径、FFmpeg 命令或原始异常。

## 4. 下游故障：延迟重试与审计式恢复

该段用于解释可靠性边界，不建议在常规页面联调时频繁触发。

1. 临时停止 User Service，提交有效测试视频。
2. 视频处理主状态机仍可到 `PUBLISHED / SUCCEEDED`；关注流消息以 5 秒 Broker 延迟重试，耗尽 3 次预算后进入 `video.publish.inbox.dead.queue`。
3. 恢复 User Service 后，仅在受限内网直连 Video Service 调用：

```text
POST /video/api/private/follow-feed/operations/recover-dead?batchSize=10
```

恢复动作不是直接 requeue：先在事务内写恢复审计和新的 PENDING Outbox，提交后 ACK 原死信消息，随后复用 Outbox 投递。业务写入继续受 `(recipient_id, video_id)` 唯一约束保护。

## 5. 固定环境基线与可观测性

```powershell
.\scripts\measure-video-e2e.ps1
```

默认执行 1 次预热和 5 次串行测量。当前一组有效本机样本的 Min/P50/P95/Max 为 3470/3500/3506/3506 ms；使用时必须说明它是“同机、串行、2 秒隔离媒体、从预签名到已发布”的回归口径。

Grafana 看板 `SW / SW 核心链路可观测性` 可展示：网关限流拒绝、Outbox 失败、转码耗时与失败、关注流重试/最终死信/人工恢复。AI 工具调用可在 Prometheus 查询 `sw_ai_creator_assistant_tool_invocations_total`，只按工具名计数，不记录用户、视频或提示词。优先展示与本次演练对应的非零指标；没有样本时如实说明，不制造曲线。

## 6. 面试讲解顺序（约 6 分钟）

1. 业务闭环：创作者上传并可靠发布，消费者刷流、互动、关注，创作者可查询处理状态。
2. 一致性方案：业务状态与 Outbox 同事务；点赞/收藏以事件 ID 去重，评论异步聚合计数。
3. 故障边界：处理回写有租约恢复；关注流下游故障有 Broker 延迟重试与最终 DLQ。
4. 恢复治理：DLQ 恢复需审计和补偿 Outbox，不能直接重放旧消息。
5. 证据：核心回归、E2E 成功/失败脚本、互动接口验收、Prometheus/Grafana、固定环境延迟基线。
6. AI 定位：仅为创作者提供有权限约束的状态查询和失败诊断，不把它包装成多 Agent 系统。
