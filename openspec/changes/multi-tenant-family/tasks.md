# Implementation Tasks: 多用户多家庭

**Change ID:** `multi-tenant-family`
**设计依据**：[design.md](./design.md)

---

## Phase A：领域基建与身份上下文
- [ ] A.1 （与 backend-ddd-architecture 1.1 合并）`common/domain`：`AggregateRoot<ID>`、`DomainEvent`、`AbstractDomainEvent`、`DomainEventPublisher`、`DomainException`
- [ ] A.2 `common/tenant`：`FamilyId`、`TenantContext`、`TenantContextHolder`（含 `runAs`）
- [ ] A.3 `identity` 四层：Account 聚合 + PhoneNumber/WechatIdentity 值对象 + AccountRepository + 领域单测
- [ ] A.4 `AuthService` 改为基于 Account 登录（手机号密码 / 微信）；登录响应增加 `families[]`、`currentFamilyId`

## Phase B：家庭上下文
- [ ] B.1 `family` 四层：Family/Membership/Invitation 聚合 + 不变量 I1–I4 + 领域事件
- [ ] B.2 `FamilyMemberProfile` 聚合 + HealthProfile 值对象 + `NutritionTargetCalculator`（从 MemberController 下沉）
- [ ] B.3 `MembershipPolicy`（每账号 ≤5 家庭）
- [ ] B.4 `FamilyAppService` + 全部 `/family/**` 接口 + `MemberJoined → 自动建档案` 监听
- [ ] B.5 `FamilyQueryApi`（isMember/roleOf/listProfiles）+ Redis 缓存与事件失效
- [ ] B.6 领域单测：不变量全分支 + 角色矩阵；应用层单测

## Phase C：租户隔离基建
- [ ] C.1 `TenantInterceptor`（成员校验、403 FAMILY_ACCESS_DENIED）
- [ ] C.2 `TenantLineInnerInterceptor` 接入 + 忽略表清单 + 无上下文访问家庭域表抛异常
- [ ] C.3 `NotificationScheduler` 等后台任务改用 `TenantContextHolder.runAs`
- [ ] C.4 ArchUnit：家庭域 PO 必须含 `familyId` 字段；手写 `@Select` 需在白名单登记

## Phase D：数据库迁移
- [ ] D.1 V64 建 account/family/family_membership/family_invitation
- [ ] D.2 V65 家庭域表加 family_id（可空）+ 索引；dish 加 family_id/source_dish_id；member 加 family_id/account_id
- [ ] D.3 V66 数据迁移（默认家庭、account 拆分、回填）
- [ ] D.4 V67 family_id 改 NOT NULL（dish 除外）
- [ ] D.5 校验 SQL `sql/verify/tenant_migration_check.sql` + 回滚脚本 `sql/rollback/V64-V67.sql`
- [ ] D.6 `e2e-seed.sql` 同步新列与 E2E 家庭清理

## Phase E：业务上下文适配
- [ ] E.1 dish：可见性规则（读公共+本家/写本家）、`POST /dish/{id}/fork`、公共菜谱写需 OPERATOR；`DishQueryApi.findVisible`；语义搜索/向量检索按可见性过滤
- [ ] E.2 menu（含 prep/together/cook）、pantry、shopping、cookbook、dailylog、foodlog、review、notification、ai 逐一适配：创建时写入 familyId；去掉对客户端 memberId 的信任，改从 TenantContext 取 profileId
- [ ] E.3 聚餐协同（together）：访客 X-Guest-Key 仅能访问被邀请的那个菜单
- [ ] E.4 `MpPerm` 改为 FamilyRole 默认模板 + 档案级勾选
- [ ] E.5 `/member/**` 改为本家成员档案（去 isAdmin/password）
- [ ] E.6 账号注销 `DELETE /account/me`（OWNER 校验 + 匿名化 + AccountDeactivated → 退出全部家庭）
- [ ] E.7 账号合并 `POST /account/merge-phone`：`Account.absorb` + 领域单测（冲突/幂等/已合并）；`AccountMerged` 监听迁移 Membership、成员档案、Agent 会话/反馈/通知/同意记录；`account_merge_log`；W token 失效
- [ ] E.8 存量修复：迁移脚本识别「同一人两个账号」不自动合并（无法证明是同一人），只在用户主动发起时合并

## Phase F：前端三端
- [ ] F.1 menu-mini：登录后家庭上下文存储；顶部/我的页家庭切换器；家庭页（成员列表/角色/移除/退出/转让/解散）；邀请生成与分享（小程序 `onShareAppMessage` 带 code）；邀请落地页；首次登录引导；成员档案页；公共菜谱「复制到我家」；注销入口；403 FAMILY_ACCESS_DENIED 处理
- [ ] F.2 menu-flutter：同上（家庭页、切换、邀请链接/邀请码输入、档案、fork、注销）
- [ ] F.3 menu-admin：OPERATOR 鉴权；公共菜谱库管理（仅 family_id IS NULL）；用户/家庭只读统计；移除家庭私有数据页面（菜单/库存/采购/周计划）
- [ ] F.4 三端测试：mini unit+Playwright（家庭切换、邀请加入）；Flutter widget+integration（家庭页、邀请）；admin Playwright（非 OPERATOR 拒绝）

## Phase G：E2E 与上线
- [ ] G.1 `GuduE2ETenantIsolationTest` 越权套件（覆盖全部家庭域写接口）
- [ ] G.2 `GuduE2EFlowTest` 新增：注册→建家→邀请→加入→切换→退出 主链路；微信建号→写数据→并入手机号账号→微信重新登录进入手机号账号且数据可见
- [ ] G.3 staging 生产快照迁移演练 + 校验 SQL + 三端冒烟
- [ ] G.4 生产：备份 → 低峰迁移 → 校验 → 发布 → 监控 24h

## Quality Gate
- [ ] 领域单测覆盖 Family 全部行为分支
- [ ] 越权套件 100% 通过（0 条跨家庭可访问）
- [ ] ArchUnit 全绿，跨模块 Mapper 基线不增
- [ ] 迁移校验 SQL 全过；回滚脚本在 staging 验证可用
