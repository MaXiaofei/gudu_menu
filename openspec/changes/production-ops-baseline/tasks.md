# Implementation Tasks: 生产运维与上架基线

**Change ID:** `production-ops-baseline`

## 1. 迁移（Flyway）
- [ ] 1.1 对比 staging/prod 结构 diff，统一
- [ ] 1.2 生成 `V1__baseline.sql`；旧脚本归档 `sql/legacy/`；接入 flyway-mysql（baselineOnMigrate=1）
- [ ] 1.3 deploy.sh 移除手工迁移循环；`e2e-seed.sql` 适配
- [ ] 1.4 staging 演练：空库 + 存量库两种启动均成功

## 2. 备份与恢复
- [ ] 2.1 `scripts/backup/backup.sh`（mysql/pgvector/uploads → 加密 → COS，保留策略）+ cron
- [ ] 2.2 `scripts/backup/restore-drill.sh` 月度演练 + 行数校验 + 告警
- [ ] 2.3 运维手册：恢复步骤（RPO 24h / RTO 1h）

## 3. CI/CD
- [ ] 3.1 `ci.yml`（PR）：后端/admin/mini/Flutter 测试与构建 + 禁用词 grep
- [ ] 3.2 `release.yml`：构建推 TCR（tag=sha）→ staging 部署 + 冒烟 → environment 人工审批 → prod
- [ ] 3.3 部署专用账号 + SSH 密钥 + known_hosts；concurrency
- [ ] 3.4 `deploy.sh rollback <sha>`；健康检查失败自动回滚
- [ ] 3.5 Dockerfile 移除 `-DskipTests`（测试在 CI 跑，镜像构建复用产物）

## 4. 隔离 / 资源 / 健康
- [ ] 4.1 staging 独立 nginx；生产 nginx 移除 staging 网络；staging profile
- [ ] 4.2 mem_limit / healthcheck / depends_on healthy / 日志轮转
- [ ] 4.3 JVM 参数；Ollama 内存上限
- [ ] 4.4 镜像版本固定；非 root；去 `|| npm install`

## 5. 观测
- [ ] 5.1 actuator（内网）+ JSON 日志 + traceId
- [ ] 5.2 指标看板 + 告警规则 → 企业微信机器人
- [ ] 5.3 错误上报（已定免费方案）：Sentry Developer 免费版接 Flutter/admin/H5/后端（DSN 放 .env）；小程序用微信运维中心 + RealtimeLogManager

## 6. nginx
- [ ] 6.1 gzip、安全头、CSP Report-Only、limit_req、静态缓存、HSTS includeSubDomains、上传 10m
- [ ] 6.2 生产关闭 Swagger

## 7. App 上架
- [ ] 7.1 包名确认并统一（**待用户确认**）
- [ ] 7.2 release 签名 + R8 + 混淆 + 关闭 cleartext
- [ ] 7.3 启动图、图标核对、中文 localizations、崩溃上报

## 8. 小程序上架
- [ ] 8.1 隐私授权组件 + `__usePrivacyCheck__`；登录协议勾选
- [ ] 8.2 iconfont 本地化；分包；主包 < 2MB 校验进 CI
- [ ] 8.3 公众平台：服务器域名、隐私保护指引填写清单（文档）

## 8A. 内容合规（来自 `recipe-content-seed` 6.1）
- [ ] 8A.1 生产公共库不含第三方导入内容：上线前及每次部署执行
  `SELECT COUNT(*) FROM dish WHERE source_name = '下厨房' AND deleted = 0;` 结果必须为 0（CI/部署脚本断言）
- [ ] 8A.2 生产图片目录不含 `xc_*` 文件（`find /var/www/images -name 'xc_*' | wc -l` = 0）

## 9. 仓库卫生
- [ ] 9.1 移除已入库产物，补 .gitignore；删废弃 compose
- [ ] 9.2 application-dev.yml 去硬编码 IP

## Quality Gate
- [ ] 一次完整「PR → staging → 审批 → prod → 回滚」演练成功
- [ ] 备份恢复演练成功并有记录
- [ ] securityheaders.com 评级 ≥ A
