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

### 2026-08-23：校准 AI 前后端联调配置

- 状态：`配置与回归已验证，待真实 Key 联调`
- 本次目标：让本机 Qwen Key、AI Service、Gateway 和 `SW-web` 的 SSE 链路使用一致配置。
- 完成内容：调用百炼兼容接口的 AI/MCP 配置统一使用 `QWEN_API_KEY`；AI Nacos 默认地址改为 SW 隔离端口 `28848`；AI Redis 默认地址改为 SW 隔离端口 `16379`；README 和演示 Runbook 补充 IDEA 密钥配置、启动顺序、登录要求及 SSE/Tool Calling 验证步骤。
- 验证证据：`sw-dev` 的 MySQL、Redis、RabbitMQ、MinIO、Nacos 已按独立 Compose 项目启动；修正后的全部 Nacos YAML 已重新导入 `dev` 命名空间；JDK 21 下 `mvn -pl ai -am -DskipTests compile` 成功；`CreatorAssistantServiceImplTest` 与 `VideoProcessingToolsTest` 共 7 个测试全部通过；`SW-web` 的 `npm run build` 成功。当前系统用户环境中未检测到 `QWEN_API_KEY`，不会读取或记录密钥内容。
- 新发现的面试价值：文档、Nacos 占位符和 IDEA 环境变量若命名不一致，会出现“已配置但服务读不到”的隐蔽故障；项目隔离还必须覆盖宿主机 Java 进程的连接地址。已写入面试问题清单第 53 题。
- 当前风险/阻塞：真实模型调用必须由用户在 IDEA 本地配置 Key 后进行；未登录时 Gateway 不会向 AI 服务注入用户身份。
- 下一步：用户在 AI Run Configuration 配置 Key 后启动 User、Video、Video Processor、AI、Gateway，再从前端验证标题建议与本人视频状态查询；验证完成后补充真实 SSE 现场证据。

### 2026-08-23：恢复 IDEA Java 服务运行环境变量

- 状态：`已完成并验证，待重启 IDEA 生效`
- 本次目标：恢复重装后丢失的 Application 中间件凭据、固定端口和 Java/Maven/Node 用户环境。
- 完成内容：从 Git 忽略的本机 `.env` 同步 MySQL、Redis、RabbitMQ、MinIO、Nacos、XXL-Job 变量；设置 `SW_NACOS_SERVER_ADDR=localhost:28848`、MySQL `13306`、Redis `16379`、RabbitMQ `25672`、MinIO `29000`；恢复 JDK 21、Maven、Node 用户 PATH；设置默认模型 `qwen-plus`。
- 验证证据：在实际 Windows 用户上下文中逐项核验 23 个变量均已配置，检查过程只输出变量名和布尔状态、不输出敏感值；`QWEN_API_KEY` 明确保留为未配置，等待用户在 AI Run Configuration 本地填写。
- 新发现的面试价值：沿用面试问题第 50、53 题——Compose `.env`、Windows 用户环境和 IDEA Run Configuration 是三个不同注入层；配置恢复必须验证实际宿主进程上下文，不能以文档记录或沙箱进程视图代替。
- 当前风险/阻塞：已经打开的 IDEA 不会自动获得新变量，必须完整退出后重新打开；真实 AI 联调还缺本地 `QWEN_API_KEY`。
- 下一步：重启 IDEA，确认 Project SDK/Maven Runner 为 JDK 21；各核心 Application 的 Program arguments/VM options 留空，启动后检查健康端点。

### 2026-08-23：排查 Video Redis 主机解析失败与本地 Key 暴露

- 状态：`Redis 配置与 Nacos 远端已验证，待 Application 启动验证；旧 Key 必须轮换`
- 本次目标：定位 `Failed to resolve 'redis'`，同时处理诊断输出意外暴露本地 AI Key 的安全事件。
- 完成内容：确认 Windows 用户变量为 `SW_REDIS_HOST=localhost`、`SW_REDIS_PORT=16379`；确认 Nacos `common-dev.yaml` 正确；继续沿 `RedissonConfig` 定位到 `video-dev.yaml` 的独立 `redisson.address` 仍硬编码 `redis://redis:6379`，已将 Video/Product/Live 的 Redisson 地址统一改为 `SW_REDIS_HOST/SW_REDIS_PORT` 占位符。
- 验证证据：完整异常底层为 Redisson `UnknownHostException: redis`；实际用户变量和 Spring Data Redis 表达式均正确；代码检索确认 `RedissonConfig` 只读取 `redisson.address`；重新导入后 Nacos 远端 `video-dev.yaml` 已核验为 `redis://${SW_REDIS_HOST:localhost}:${SW_REDIS_PORT:16379}`；`.idea/workspace.xml` 被 `.gitignore` 排除且未被 Git 跟踪。
- 新发现的面试价值：写入面试问题第 54、55 题。Git 忽略不等于运行时秘密不会进入诊断输出；同一中间件存在多个客户端时必须核对失败 Bean 的实际配置前缀。
- 当前风险/阻塞：原 Qwen Key 已出现在诊断输出中，必须在百炼控制台立即撤销并生成新 Key；当前 IDEA 需完整退出并重新打开后再启动服务。
- 下一步：用户轮换 Key 后重新启动 Video 验证 Redisson，再在 AI Run Configuration 中写入新 Key 并验证 AI SSE。

- 补充修复：Nacos 导入脚本原先丢弃发布返回值并立即打印成功，已增加发布结果检查以及远端内容重试比对；本轮 10 份 YAML 均显示 `Imported and verified`。

### 2026-08-23：强化 2077 式前端并恢复真实刷视频条件

- 状态：`前端已完成并验证；真实 Feed 数据待重启 Processor 后生成`
- 本次目标：增强 2077 式视觉，显式展示已有业务能力，并定位 Video 已启动但 Public Feed 为空的问题。
- 完成内容：Feed 改为竖向沉浸式播放器，支持真实 MinIO URL、公开/关注切换、游标加载、搜索、点赞、收藏和评论；空数据与后端不可达分开表达，并提供明确标注的视觉 Demo；AI 增加标题、简介/标签、选题、发布节奏、处理进度和失败诊断六类快捷入口；视觉改为原创警示黄黑、神经青、故障红、巨大 77 编码和切角 HUD。修正成功/失败/性能 E2E 脚本的 `sw-dev-mysql-1` 容器名及 `25672/29000` 隔离端口。
- 验证证据：Gateway/Video Public Feed 均返回 HTTP 200 和空 `items`；只读查询确认本机 `video` 表没有记录；前端两次 `npm run build` 成功；浏览器验证 Feed 空状态、Demo 竖向信号流、Creator Ops 和 AI 六类入口，控制台无 warning/error。
- 新发现的面试价值：写入面试问题第 56 题。服务健康不等于业务数据就绪；Feed 的 `PUBLISHED + published_at` 条件必须通过完整异步链路产生。
- 环境恢复：本机已安装 FFmpeg 9.0，并持久化 `SW_VIDEO_PROCESSING_FFMPEG_COMMAND`；当前运行中的 IDEA/Processor 尚未继承新变量，需要完整重启 IDEA。
- 当前风险/阻塞：重启 Processor 前不要运行成功 E2E，否则任务会因找不到 FFmpeg 进入失败状态；真实 Feed 仍无本地公开视频。
- 下一步：重启 IDEA 后运行 `scripts/verify-video-e2e.ps1` 生成真实 `PUBLISHED` 视频，再验证前端 `<video>` 播放、评论和互动。

### 2026-08-23：完成真实视频播放与互动闭环

- 状态：`已完成并验证`
- 本次目标：解决公开视频无法播放、点赞评论不可用，并把前端调整为登录后才能访问具体业务页面。
- 完成内容：为 `sw-dev` MinIO 幂等初始化五个私有 Bucket；视频访问改为 60 分钟预签名 GET；评论聚合对缺失/注销用户降级；公共对象工具对空头像 Key 返回 null；前端修正评论作者字段并增加独立身份门禁。
- 验证证据：真实视频 `2091483955474989057` 达到 `PUBLISHED / SUCCEEDED / Outbox SUCCESS`；Gateway 与 Video Feed 均返回 1 条数据；签名媒体 Range 请求 HTTP 206；浏览器 `<video>` 为 `readyState=4`、时长 2 秒、无 error；JWT 注册登录、点赞、收藏、评论写入和回读业务码均为 1；浏览器点赞从 3 变 4，评论可见且作者为“路人甲”；注销用户评论降级为“已注销用户”；退出后业务页面重新隐藏。
- 自动化证据：`InteractionServiceImplTest` 2/2、`AWSUtilsTest` 1/1 通过；`SW-web npm run build` 成功；浏览器控制台无 warning/error。
- 新发现的面试价值：写入面试问题第 57—60 题，覆盖基础设施就绪与业务资源就绪、私有对象签名访问、跨服务聚合部分失败、空对象 Key 边界和前后端双层认证策略。
- 当前运行状态：Docker 中间件和本轮终端测试服务正在运行；日常开发仍建议由 IDEA 使用 JDK 21 启动 Java 服务。
- 下一步：提交本轮修复；如需现场演示，在 IDEA 重启 User、Video、Processor、Gateway 后运行同一 E2E 和浏览器流程。

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
