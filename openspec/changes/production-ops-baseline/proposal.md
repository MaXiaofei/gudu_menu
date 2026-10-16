# Proposal: 生产运维与上架基线

**Change ID:** `production-ops-baseline`
**Created:** 2026-10-16
**Status:** Draft
**Roadmap:** #5（依赖 `security-hardening`；可与 #3/#4 并行）

---

## Problem Statement

| 领域 | 现状（证据） | 风险 |
|------|-------------|------|
| 迁移 | 无 Flyway、无版本表；V43/V45/V50 版本号重复；V01–V41 靠 initdb | 无法得知库处于哪个版本，执行顺序不确定 |
| 备份 | 无任何 MySQL/pgvector/uploads 备份（仅 `docs/deploy.md:480` 手工步骤） | 一次误删 = 永久丢数据 |
| CI/CD | `deploy.yml` push main 直接 SSH 部署生产，无测试门禁；e2e 仅手动；镜像 `-DskipTests`；root 密码 SSH + `StrictHostKeyChecking=no` | 坏代码直达生产；凭据泄露面大 |
| 回滚 | 服务器原地 build，不打 tag；健康检查失败只打日志；无 concurrency | 出问题无法快速回退 |
| 隔离 | 生产 nginx 接入 staging 网络，staging 部署会 reload 生产 nginx；staging 用 prod profile | 预发变更波及生产 |
| 资源 | 无 mem_limit、无 healthcheck、JVM 无参数；Ollama 与两套 MySQL 同机 | OOM 连带杀进程 |
| 观测 | 无 actuator/指标/告警/错误追踪；docker 日志无轮转 | 故障靠用户反馈发现 |
| nginx | 仅 HSTS；无 CSP/X-Content-Type-Options/X-Frame-Options/Referrer-Policy；无 gzip；无 limit_req；静态资源无缓存 | 性能与安全基线缺失 |
| 镜像 | ollama/certbot/minio 用 latest；容器 root 运行；`npm ci || npm install` 静默降级 | 不可复现、权限过大 |
| Swagger | 生产开放且免登录，健康检查依赖它 | 接口面暴露 |
| App 上架 | debug 签名、包名为脚手架默认且两端不一致、无混淆/R8、明文 HTTP、无启动图、无崩溃上报 | 无法上架/线上问题不可见 |
| 小程序上架 | 无隐私授权处理（chooseMedia/setClipboardData）、无分包、uview iconfont 远程域名、无协议勾选 | 审核驳回/真机图标空白 |
| 仓库卫生 | playwright-report、test-results、uploads 图片已入库；废弃 compose 文件 | 噪音与误用 |

## Proposed Solution

1. **Flyway**：`flyway-mysql` 接入；以「当前生产结构」为 `V1__baseline`（由 mysqldump --no-data 生成 + 字典种子），旧 V01–V63 归档到 `sql/legacy/`；已有库执行 `baselineOnMigrate` 于版本 1；之后的变更（含 multi-tenant 的 V64+ 重编号为 V2+）全部由 Flyway 管理；deploy.sh 删除手工迁移循环。
2. **备份**：每日 03:00 `mysqldump --single-transaction` + pgvector `pg_dump` + uploads tar → gzip + 加密 → 上传腾讯云 COS（保留 30 天日备 + 12 个月备），本机保留 3 天；**每月一次自动恢复演练**到临时容器并校验行数；失败告警。
3. **CI/CD**：
   - PR：后端 `mvnw test`（单测 + ArchUnit）、admin/mini `vue-tsc + vitest + build`、Flutter `analyze + test`、禁用词 grep（建设中/mock/敬请期待）
   - main：上述全绿 → 构建镜像推送腾讯云 TCR（tag = git sha）→ 部署 staging → 冒烟 E2E → **人工审批** → 生产按 tag 部署
   - 部署账号改为非 root 专用用户 + SSH 密钥 + 固定 known_hosts；workflow `concurrency` 互斥
   - 回滚：`deploy.sh rollback <sha>` 切回上一镜像 tag；健康检查失败自动回滚
4. **环境隔离**：生产 nginx 不再接入 staging 网络，staging 改由独立 nginx 容器（不同端口/子域）承载；staging 使用 `staging` profile。
5. **资源与健康**：所有服务 `mem_limit` + `healthcheck` + `depends_on: condition: service_healthy`；JVM `-XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError`；Ollama 限内存并评估迁出生产机；docker 日志 `max-size=50m,max-file=5`。
6. **观测**：Spring Boot Actuator（health/liveness/readiness/prometheus，仅内网）；结构化 JSON 日志 + traceId；Prometheus + Grafana（或腾讯云可观测平台）看板：QPS/错误率/P95/JVM/MySQL；告警（5xx 率、健康检查失败、磁盘 > 80%、备份失败）推企业微信机器人；错误上报（**2026-10-16 用户决定：选免费方案**）：Flutter/admin/H5/后端 → Sentry 免费 Developer 版（5k errors/月，sentry_flutter / @sentry/vue / sentry-spring-boot）；微信小程序 → 微信原生「运维中心错误日志 + RealtimeLogManager 实时日志」（免费、免域名白名单）。
7. **nginx**：gzip/brotli、安全头全集（CSP 先 Report-Only 一周再强制）、`limit_req`（登录 5r/s、AI 1r/s、全局 20r/s 每 IP）、带 hash 静态资源 `immutable` 一年缓存、HSTS includeSubDomains、上传 10m。
8. **镜像**：全部固定到具体版本；三镜像 `USER app` 非 root；去掉 `|| npm install`；Swagger 在 prod profile 关闭，健康检查改 actuator。
9. **App 上架**：正式包名（**需用户确认**，建议 `cloud.imxf.gudu`）两端统一；release keystore（存 GitHub Secrets）；R8 + `--obfuscate --split-debug-info`；关闭 cleartext；`flutter_native_splash` 启动图；崩溃上报（Sentry 免费版）；`flutter_localizations` 中文；版本号策略。
10. **小程序上架**：`__usePrivacyCheck__` + 隐私弹窗组件（`onNeedPrivacyAuthorization`）；登录页协议勾选；uview iconfont 本地化；按 tab 外页面分包（菜谱编辑/食记/点评/聚餐/成员）；服务器域名清单文档。
11. **仓库卫生**：清理已入库产物、补 `.gitignore`、删除废弃 compose；`.env` 权限 600；`application-dev.yml` 去掉硬编码 IP 改读 `.env`。

## Scope
In：上述 1–11。Out：多机/K8s 化、异地多活（规模上来后另立 change）；staging/dev 网络收口（用户决定保持放开）。

## Impact Analysis
| Component | Change | Details |
|-----------|--------|---------|
| 部署 | 大 | 镜像仓库 + 审批式发布 + 回滚 |
| 数据库 | 中 | Flyway baseline，一次性切换 |
| 成本 | 小 | COS 存储 + 镜像仓库（个人版免费额度） |
| App/小程序 | 中 | 包名一旦提审不可改，必须先确认 |

## Risks / Trade-offs
- Flyway baseline 切换需确保 staging 与 prod 结构一致：先用 `mysqldump --no-data` 对比两库 diff。
- CSP 可能误拦三方资源：先 Report-Only。
