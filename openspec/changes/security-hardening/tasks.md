# Implementation Tasks: P0 安全止血

**Change ID:** `security-hardening`

---

## 1. 鉴权与游客白名单（S1）
- [x] 1.1 `SaTokenConfig`：`auth.login-check` 默认 true；新增 `GuestWhitelist`（GET 菜谱 list/search/详情/营养、GET /dict、GET /ingredient 只读）
- [x] 1.2 `application.yml` 默认 true；`application-dev.yml`/`-test.yml` 保留 `${AUTH_LOGIN_CHECK:false}`；prod compose 强制 `AUTH_LOGIN_CHECK: "true"`（不再读 .env 默认）
- [x] 1.3 单测：匿名访问受保护接口 → 401；访问白名单 → 200

## 2. 身份与成员接口（S2/S3/S4）
- [x] 2.1 `MemberSaveRequest` DTO（name/phone/healthProfile/goal/activityLevel/roleTags；admin 才可写 isAdmin/mpPermissions/password）
- [x] 2.2 `POST /member/current`：仅允许本人或无账号成员档案，禁止切到 isAdmin=1
- [x] 2.3 `PUT /member`：非超管只能改本人或纯成员档案的档案字段；`POST` 非超管只能建纯档案；`DELETE` 非超管只能删纯档案、任何人不能删自己
- [x] 2.4 `MpPermissionAspect`：改为**始终按登录账号**判定权限（不再读 currentMemberId），切换就餐成员无法借用他人权限；非超管只能新建/编辑/删除「纯成员档案」（无密码无 openid）
- [x] 2.5 单测：普通用户切超管 → 拒绝；改他人 → 拒绝；改自己 isAdmin → 字段被忽略

## 3. 平台管理接口（S5/S6）
- [x] 3.1 `@RequireAdmin` + `AdminGuardAspect`（loginId 对应 member.isAdmin=1）
- [x] 3.2 覆盖：`GET/PUT /ai/provider`、`GET /ai/call-log`、`GET /ai/usage`、`POST /dish/vector/rebuild`、nutrition-metric 写、dict PUT/DELETE
  - ⚠️ 调整：`POST /dict`、`/ingredient` 写**不收紧**——小程序/App 录菜时会现场新建食材与字典项（`menu-mini/src/api/ingredient.ts`、`menu-flutter/lib/services/ingredient_service.dart:46,86`）。共享库写权限随 `multi-tenant-family` 一并重设计（家庭私有食材 / 公共库仅 OPERATOR）
- [x] 3.3 `/auth/login` 支持 `scope=admin`，非超管返回「无后台权限」
- [x] 3.4 menu-admin：登录传 scope=admin；路由守卫校验 userInfo.isAdmin；401/403 统一处理
- [ ] 3.5 （延后至 multi-tenant-family）ArchUnit：`modules.ai`/`dict` 写方法必须标注 `@RequireAdmin`（白名单制）

## 4. SSRF（S7）
- [x] 4.1 `SafeUrlFetcher`：协议白名单、DNS 解析后拒私网/回环/链路本地/组播/保留地址、手动重定向 ≤3 跳逐跳复检、2MB/8s 上限
- [x] 4.2 `RecipeImporter` 改用 SafeUrlFetcher
- [x] 4.3 单测：127.0.0.1 / 10.x / 172.16.x / 192.168.x / 169.254.169.254 / `[::1]` / file:// / 重定向到内网 → 全部拒绝

## 5. 异常 / CORS / 分页 / AI 限流（S8–S11）
- [x] 5.1 `GlobalExceptionHandler`：生成 traceId（MDC）；`gudu.expose-error-detail` 默认 false；处理 `MethodArgumentNotValidException`/`NotPermissionException`
- [x] 5.2 `CorsConfig`：`gudu.cors.allowed-origins`（prod: https://imxf.cloud；dev: *）
- [x] 5.3 `MybatisPlusConfig.setMaxLimit` + `PageQuery` 钳制（负数/0 回默认）。⚠️ 上限暂定 1000：三端「拉全量下拉」现用 pageSize=1000，待提供全量选项接口后收紧到 100
- [x] 5.4 `@AiRateLimited`（覆盖 nutrition/fill、menu/recommend、dish/estimate；semantic-search 走本地 embedding 不计 AI 额度，由 nginx 限流兜底）：切面按 loginId 计 Redis 日次数；移除 AiService 里信任客户端 memberId 的旧计数
- [x] 5.5 单测：限流超额返回 429；异常不回显根因

## 6. 部署迁移登记（S12）
- [x] 6.1 `sql/V63__schema_migration_log.sql`（建表，幂等）
- [x] 6.2 deploy.sh：首次检测到登记表为空 → 登记现有全部文件（不执行）；之后仅执行未登记文件并记录 checksum；checksum 变化报错中止
- [x] 6.3 V61/V62 补守卫（按标签名判断，已处理则 no-op）
- [ ] 6.4 staging 演练：连续部署两次，标签关联数不变

## 7. 生产端口与 Redis 密码（S13）
- [x] 7.1 prod compose：MySQL/Redis/pgvector/Ollama `ports` 改 `127.0.0.1:` 绑定
- [x] 7.2 Redis `--requirepass ${REDIS_PASSWORD}`；`application-prod.yml` 读 `REDIS_PASSWORD`；`.env.example` 增项
- [x] 7.3 运维手册：生产 `.env` 补 `REDIS_PASSWORD`、`AUTH_LOGIN_CHECK=true`

## 8. 小程序线上 bug（S14）
- [x] 8.1 `dish/Create.vue` import `deleteDraft`；`difficulty: difficulty.value`（两处）
- [x] 8.2 vue-tsc 6 个 error 清零（uview-plus 类型声明、`addedBy` 类型、`:key` null）
- [x] 8.3 unit：草稿保存成功后删除草稿、难度为数值

## Quality Gate
- [x] `./mvnw test`（含 ArchUnit）全绿——426/426（2026-10-16）
- [ ] staging 开启 `AUTH_LOGIN_CHECK=true` 跑三端冒烟（小程序 Playwright、admin Playwright、Flutter integration smoke）
- [x] 安全回归**单测**全部通过（匿名/提权/越权/SSRF/限流）；E2E 层随 staging 冒烟补
- [ ] 生产部署后验证：匿名调用 `/menu` → 401；`ss -lnt` 不再监听 0.0.0.0:3306/6379
