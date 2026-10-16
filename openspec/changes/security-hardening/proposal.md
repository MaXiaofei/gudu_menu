# Proposal: P0 安全止血

**Change ID:** `security-hardening`
**Created:** 2026-10-16
**Status:** Draft
**Roadmap:** #1（[ROADMAP](../../ROADMAP.md)）

---

## Problem Statement

2026-10-16 全栈审计发现**生产环境当前即存在**的高危问题（证据均为代码行）：

| # | 问题 | 证据 | 后果 |
|---|------|------|------|
| S1 | 登录校验默认关闭，生产 `.env` 亦为 false | `SaTokenConfig.java:19`、`application.yml:92`、`docker-compose.prod.yml:75` | 全部 119 个接口可匿名调用 |
| S2 | 身份切换可冒充任意人（含超管） | `MemberController.java:35` `POST /member/current?memberId=` 直接写 session；`MpPermissionAspect` 读该值鉴权、admin 全放行 | 任意登录用户提权为超管 |
| S3 | 成员写接口收整个实体 | `MemberController.java:59-72` `POST/PUT /member` 接收 `Member`（含 `isAdmin`/`password`/`mpPermissions`）；`DELETE /member/{id}` 无校验 | 自改超管、改他人密码、删人 |
| S4 | `MpPermissionAspect` 无 currentMemberId 时放行 | `MpPermissionAspect.java:35-38` | 权限矩阵形同虚设 |
| S5 | 平台管理接口无保护 | `AiController.java:78/90/101`（provider/call-log/usage）、`DishController.java:66`（vector/rebuild）、字典/食材写接口 | 任意用户切换 AI 供应商、看全站用量、重建向量索引 |
| S6 | 管理后台无角色校验 | `menu-admin/src/router/index.ts:85-90` 只看 token 存在 | 任意 C 端用户可登录后台 |
| S7 | SSRF | `RecipeImporter.java:93` `Jsoup.connect(url).followRedirects(true)` 无 host 白名单 | 探测容器内网（ollama/pgvector/nacos/mysql） |
| S8 | 异常原文回显 | `GlobalExceptionHandler.java:24-35` 透出根因消息（SQL/表名） | 信息泄露 |
| S9 | CORS `*` + credentials | `CorsConfig.java:23-26` | 任意站点可携带凭据跨域调用 |
| S10 | AI 限流可绕过 | `AiService.java:124` 用客户端传入 memberId 计数；`/ai/nutrition/fill`、`/ai/dish/estimate` 不限流 | AI 费用被刷 |
| S11 | 分页无上限 | `MybatisPlusConfig.java:18` 未设 maxLimit；`PageQuery` 不校验 | 单请求拖库 |
| S12 | **每次部署重跑 V42+ 全部 SQL** | `scripts/deploy.sh:63-73`；V61 为非幂等 `DELETE`，V62 用新 id 重建「饭」标签 | **每次部署清掉用户手打的标签关联，字典 id 漂移** |
| S13 | 生产数据层端口绑 0.0.0.0，Redis 无密码 | `docker-compose.prod.yml:18-19,28-29,40-41` | 仅靠云安全组兜底 |
| S14 | 小程序新建菜谱线上 bug | `menu-mini/src/pages/dish/Create.vue:519` `deleteDraft` 未 import；`:443/:507` `difficulty` 传 Ref | 保存提示失败→用户重点→**重复菜谱**；难度丢失 |

谁受影响：所有生产用户的数据安全与完整性；平台 AI 成本。

## Proposed Solution

**原则：最小改动先堵洞，不等多租户改造**（多租户后 S2/S3 由家庭模型彻底替代，见 `multi-tenant-family`）。

1. **鉴权默认开启**（S1）：`auth.login-check` 默认 `true`；生产 compose 强制 `true`。为满足**微信小程序审核「免登录可浏览」**要求，新增**游客白名单**（只读公共接口：菜谱浏览/搜索/详情、字典、食材营养查询），其余全部要求登录。dev/test profile 可继续用环境变量关闭。
2. **身份与成员接口收口**（S2/S3/S4）：
   - `POST /member/current` 只允许切到「本人」或「无登录账号的家庭成员档案（passwordHash 为空且非 admin）」；
   - `POST/PUT/DELETE /member` 改用 `MemberSaveRequest` DTO（白名单字段）；`isAdmin`/`mpPermissions`/他人密码 仅超管可改；非超管只能改自己的资料；
   - `MpPermissionAspect` 无 currentMemberId 时**回落到 loginId** 校验，而不是放行。
3. **平台管理接口加超管校验**（S5/S6）：新增 `@RequireAdmin` 注解 + 切面（基于登录账号 `isAdmin=1`），覆盖 AI 供应商/日志/用量、向量重建、字典与食材写接口；`/auth/login` 增加 `scope=admin` 参数，非超管拒绝后台登录；admin 前端路由守卫校验 `isAdmin`。
4. **SSRF 防护**（S7）：`import-url` 仅允许 http/https；解析 host 后**拒绝私网/回环/链路本地/保留地址**；关闭自动重定向，手动跟随 ≤3 跳且每跳复检；响应体上限 2MB、超时 8s。
5. **异常与 CORS**（S8/S9）：生产（`gudu.expose-error-detail=false`，默认 false）只回「服务器开小差了，请稍后再试」+ traceId；CORS 改为 `gudu.cors.allowed-origins` 配置白名单（生产 `https://imxf.cloud`；dev 保持 `*`）。
6. **AI 限流**（S10）：统一切面 `@AiRateLimited`，按**登录账号**计每日次数（Redis），覆盖全部 AI 写接口；超限返回业务码 429。
7. **分页上限**（S11）：`PaginationInnerInterceptor.setMaxLimit(100)`。
8. **部署不再重跑已执行迁移**（S12）：deploy.sh 引入 `schema_migration_log(filename, checksum, applied_at)` 记录表，已执行且 checksum 一致的跳过；**首次启用时把当前已存在的 V42–V62 全部登记为已执行**（不重跑）。V61/V62 补幂等守卫。正式迁移工具（Flyway）在 `production-ops-baseline` 落地。
9. **生产端口收口**（S13）：prod compose 中 MySQL/Redis/pgvector/Ollama 端口改绑 `127.0.0.1` 或删除 `ports`；Redis 加 `requirepass`（从 `.env` 读）。**staging/dev 按用户决定保持现状**。
10. **小程序 bug**（S14）：补 import、`difficulty.value`；修复 vue-tsc 现存 6 个 error。

## Scope

### In Scope
- menu-api：SaTokenConfig、MemberController/MpPermissionAspect、新增 RequireAdmin / AiRateLimited、RecipeImporter、GlobalExceptionHandler、CorsConfig、MybatisPlusConfig、AuthService
- scripts/deploy.sh、docker-compose.prod.yml、.env.example
- menu-admin：登录与路由守卫
- menu-mini：Create.vue 两处 bug + vue-tsc 清零
- 安全回归测试：匿名访问/提权/越权/SSRF 用例

### Out of Scope
- 家庭/多租户数据隔离（→ `multi-tenant-family`）
- Flyway、CI 门禁、备份（→ `production-ops-baseline`）
- staging/dev 网络收口（用户决定保持放开）

## Impact Analysis

| Component | Change | Details |
|-----------|--------|---------|
| API 契约 | 小 | 新增 401/403/429 业务码语义；`/member` 写接口字段收窄；`/auth/login` 加可选 `scope` |
| menu-mini | 小 | 需确认游客态页面在 401 时引导登录（request.ts 已有 401 重登） |
| menu-flutter | 无 | 已登录使用 |
| menu-admin | 小 | 登录带 scope=admin；守卫校验 isAdmin |
| 部署 | 中 | 首次部署执行一次「迁移登记」；生产需在 `.env` 新增 `REDIS_PASSWORD` 并设 `AUTH_LOGIN_CHECK=true` |

## Risks / Trade-offs

- **开启鉴权可能暴露前端遗漏的匿名调用**：上线前用 staging 开启 `AUTH_LOGIN_CHECK=true` 跑三端 E2E 冒烟。
- **小程序审核**：游客白名单保证审核员不登录也能浏览菜谱。
- Redis 加密码需同时更新 menu-api 配置并重启，存在秒级不可用窗口，安排低峰执行。
