# SW 工作流程副记忆

> 这是 SW 的工作进度和协作流程记录，不替代面试价值文档。进度不确定时，先阅读本文件。

## 1. 使用规则

- 面试价值、技术问题、故障复盘和设计取舍写入 [SW-面试问题清单](SW-面试问题清单.md)。
- 当前进度、已完成工作、验证证据、阻塞项和下一步写入本文件。
- 每次完成一个较大的开发/分析阶段后更新本文件；没有形成新结论时不重复记录流水账。
- 进度分类统一使用：`已完成并验证`、`已实现待补证据`、`规划中`、`存量扩展`、`阻塞`。

## 2. 当前项目目标

- 主项目：SW 短视频微服务平台。
- 求职目标：中国大陆 2026 秋招，Java 后端和 Python Agent 应用开发。
- 当前阶段：恢复项目记忆，建立可持续的面试准备资料体系。
- 当前主线：可靠视频发布、内容消费互动、受控 AI 创作者助手和可观测性。
- 非当前主线：`duoagent` 尚未完成重构；Product、Order、Live、Chat、Admin 和 MCP 扩展不作为当前核心面试闭环。

## 3. 已完成并验证

- 已完成项目结构和核心模块梳理，确认 Gateway、User、Video、Video Processor、AI 是当前核心链路。
- 已建立 [SW 项目专属记忆](SW-项目记忆.md)。
- 已建立 [面试架构理解文档](SW-面试架构理解.md)。
- 已建立 [面试问题清单/面试价值文档](SW-面试问题清单.md)。
- 已阅读并参考已有 `README.md`、`STUDY.md`、`docs/SW-简历项目说明.md` 和 `docs/DEMO_RUNBOOK.md`。
- 已核对视频 Outbox、处理状态机、租约恢复、Feed Inbox、延迟重试、DLQ 审计恢复、互动消费幂等、Gateway 限流、AI ToolContext 权限和核心测试入口。
- README 已增加四份项目准备文档的入口。
- 已创建本机 `.env`，设置 `COMPOSE_PROJECT_NAME=sw-dev`；README 和 Runbook 的 Docker 命令显式使用 `sw-dev`，用于隔离 SW 的容器、网络和数据卷。

## 4. 已实现待补证据

- 继续确认本地成功上传、无效媒体失败、处理回写不可用后的租约恢复和关注流死信恢复的现场证据是否仍可在重装系统后的环境复现。
- 继续核对固定环境性能基线的测试时间、机器配置、样本口径和脚本输出；不得把回归基线表述成生产性能。
- 如果后续调整代码，优先为关键改动补测试、脚本、日志或指标，再更新面试价值文档。
- 本机已安装并启动 Docker Desktop，已实际拉取并启动 MySQL、Redis、RabbitMQ、MinIO、Nacos；Java 服务仍由 IDEA 启动。

## 5. 规划中：面向 2026 秋招的增强项

1. 点赞/收藏事件完全 Outbox 化，补齐发送确认和重试。
2. 私有 Feign 接口增加服务间身份认证，不只依赖 Gateway 阻断。
3. 用 Testcontainers/Compose CI 固化真实中间件集成回归，并规范数据库迁移。
4. 增加 FFmpeg 并发、磁盘、超时和进程资源隔离。
5. 处理高粉丝创作者的 Feed 扇出放大和 Inbox 清理。
6. 统一 Reactor、Feign、RabbitMQ 的标准链路追踪。
7. 为 AI 工具增加评测集、结构化结果、提示词注入防护和模型降级。
8. 明确 MCP 与 Python Agent 的工具边界，再逐步扩展单 Agent 工具编排。

## 6. 后续每次工作的建议流程

```text
先读本文件确认进度
  -> 再读 SW-项目记忆确认事实边界
  -> 定位代码、测试、脚本和现有文档
  -> 做修改或分析
  -> 运行比例合适的验证
  -> 发现高价值问题/取舍/故障时更新面试问题清单
  -> 更新本文件的完成状态、证据和下一步
```

## 7. 工作记录

### 2026-08-18：恢复项目记忆和面试材料

- 状态：`已完成并验证`
- 完成：梳理仓库结构、核心配置、主链路代码、已有项目说明和 Runbook。
- 产出：项目专属记忆、架构理解文档、面试价值文档。
- 重要结论：SW 核心项目基本完成；可靠视频异步处理是第一主线；AI 目前是受控只读助手，不包装成多 Agent；后续优化单独列为增强路线。
- 下一步：继续以开发中出现的真实问题和证据为单位维护两份记忆文档。

### 2026-08-18：配置 SW 本地 Docker 中间件隔离

- 状态：`已实现待补证据`
- 本次目标：为 SW 配置本机中间件环境，并让容器实例与其他项目分区隔离。
- 完成内容：创建被 Git 忽略的 `.env`；设置 `COMPOSE_PROJECT_NAME=sw-dev`；将 MySQL 配置为独立的 `sw_local` 用户；README、Runbook 和启动脚本统一使用 `--project-name sw-dev`。
- 验证证据：静态核对 Compose 配置、`.env` 忽略规则和启动脚本；当前机器未发现 Docker CLI 或 Docker Desktop，尚未实际启动容器。
- 新发现的面试价值：Docker Compose project name 可以同时隔离容器、网络和命名卷；本地数据库不应使用 root 作为应用连接用户。
- 当前风险/阻塞：需要安装并启动 Docker Desktop，重新打开 PowerShell 后才能执行中间件启动和健康检查。
- 下一步：执行 `docker compose --project-name sw-dev up -d mysql redis rabbitmq minio nacos`，再核对容器状态、端口和 Nacos 配置导入。

### 2026-08-18：配置 Maven 与 Java 21

- 状态：`已完成并验证`
- 本次目标：补齐项目 Maven/Java 构建环境，并确认后续 Java 服务由 IDEA 启动。
- 完成内容：安装 Temurin JDK 21.0.12；配置用户级 `JAVA_HOME`、`MAVEN_HOME`、`MAVEN_USER_HOME` 和 Maven PATH；使用 IntelliJ 自带 Maven 3.9.16；将依赖缓存固定到 `C:\Users\yujia\.m2\repository`；创建用户 Maven settings。
- 验证证据：`gateway + common` 执行 `compile` 成功，编译器使用 `javac [debug release 21]`。
- 运行边界：Docker Compose 只启动 MySQL、Redis、RabbitMQ、MinIO、Nacos 和可选 Prometheus/Grafana；Gateway、User、Video、Video Processor、AI 等 Java 服务由 IDEA 启动，不纳入 Compose app profile。
- 注意事项：已打开的终端/IDEA 可能不会自动读取新用户环境变量，需要重启终端或 IDEA；Maven 首次依赖下载耗时较长属于正常现象。
- 下一步：安装并启动 Docker Desktop，执行 SW 中间件启动脚本，再导入 Nacos 配置并做基础设施健康检查。

### 2026-08-18：完成 SW Docker 中间件隔离和 IDEA 启动验证

- 状态：`已完成并验证`
- 本次目标：启动项目专属中间件，导入 Nacos 配置，并确认 Java 服务可从 IDEA 访问这些中间件。
- 完成内容：Docker Compose 使用项目名 `sw-dev`；创建 `sw-dev_default` 网络和 `sw-dev_*` 数据卷；启动 MySQL、Redis、RabbitMQ、MinIO、Nacos；导入 `.file/config/*.yaml` 到 Nacos `dev` 命名空间；补齐 IDEA 进程所需的用户级中间件凭据。
- 验证证据：容器均处于 `Up`；Nacos readiness 返回 HTTP 200；网关临时启动成功并监听 `10086`；网关 `/actuator/health` 返回 HTTP 200；网关成功从 Nacos 加载 `common-dev.yaml`、`gateway-dev.yaml`，并完成 Nacos 注册。
- 端口分区：MySQL `13306`、Redis `16379`、RabbitMQ `25672/25673`、MinIO `29000/29001`、Nacos `28848/29848`；Java 网关端口 `10086` 仍由 IDEA 使用。
- 新发现的面试价值：`.env` 只对 Compose 生效，IDEA 直接启动的 Java 进程不会自动读取 `.env`；因此需要通过用户环境变量或 IDEA Run Configuration 注入凭据。第一次网关健康检查因缺少 Redis 密码返回 `WRONGPASS`，补齐环境变量后恢复为 `UP`。
- 当前风险/阻塞：数据库初始化脚本和完整视频 E2E 尚未在本轮重装后的环境重新验收；现阶段只完成基础设施和网关启动级验证。
- 下一步：重启 IDEA 后按 User、Video、Video Processor、Gateway 顺序启动；执行 `scripts/verify-video-e2e.ps1`，补充 MySQL、RabbitMQ、MinIO 和 FFmpeg 的现场证据。

### 2026-08-18：重建同仓库 SW-web 前端

- 状态：`已完成并验证`
- 本次目标：参考原始 `yh-fe` 的 Vue/Vite 结构，在 SW 同一 GitHub 仓库内恢复一个只服务简历主线的可演示前端。
- 完成内容：新增 `SW-web`；保留公开/关注 Feed、点赞收藏评论、登录注册、预签名直传投稿、处理状态/失败列表和 AI 创作者助手；Vite 代理 `/user`、`/video`、`/ai` 到 Gateway `10086`；补充后端不可用时的演示数据模式；视觉统一为原创赛博朋克 HUD 风格。
- 验证证据：Node.js `22.14.0`；`npm install` 无漏洞；`npm run build` 成功；本地 Vite `18888` 可访问；浏览器已验证 Feed、Creator Ops、AI Assistant 三个入口和标题高亮渲染；浏览器控制台无错误。
- 当前风险/阻塞：完整真实前端联调需要同时启动 User、Video、AI 等 Java 服务；当前浏览器验证使用了后端不可用时的演示数据回退，尚未重新完成真实上传和 AI SSE 现场验收。
- 已完成：前端已提交并推送到 `Yakuso234/SW` 的 `main` 分支，commit 为 `05430bb`。
- 下一步：重启完整 Java 服务后，用真实账号验证登录、Feed、互动、投稿和 AI 流式响应；README 根据最新验证边界持续维护。

### 2026-08-22：更新 GitHub README

- 状态：`已完成并验证`
- 本次目标：让仓库首页准确呈现当前后端主线、同仓库 `SW-web` 前端、启动方式、验证边界和面试资料入口。
- 完成内容：补充前端演示范围、赛博朋克 HUD 视觉说明、Node.js 环境要求、真实服务与 Demo Data 的边界、Docker 隔离说明、原始前端参考地址和面试资料链接；修正重装系统后完整 E2E 尚待复验的口径。
- 验证证据：`git diff --check` 无内容错误；README 改动仅涉及文档；当前工作区待提交 README 与本条记忆更新。
- 当前风险/阻塞：无；完整多服务真实联调仍是项目后续验证项。
- 下一步：提交并推送 README 更新。

## 8. 更新模板

```text
### YYYY-MM-DD：工作主题

- 状态：已完成并验证 / 已实现待补证据 / 规划中 / 存量扩展 / 阻塞
- 本次目标：
- 完成内容：
- 验证证据：
- 新发现的面试价值：写入 SW-面试问题清单，或说明无
- 当前风险/阻塞：
- 下一步：
```
