# Proposal: 多用户多家庭（账号 / 家庭 / 成员档案）+ 全量租户隔离 + 公共菜谱库

**Change ID:** `multi-tenant-family`
**Created:** 2026-10-16
**Status:** Draft
**Roadmap:** #3（依赖 `security-hardening`、`backend-ddd-architecture` Phase 1.1 事件基建）

---

## Problem Statement

产品定位已确认为**对外开放、多用户**的成熟产品，但当前数据模型是**单家庭自用**：

1. **没有租户概念**：`dish`、`ingredient_stock`、`pantry`、`menu`、`shopping_list`、`cooking_record`、`daily_log`、`review` 等表均无 `family_id`；全库搜不到租户字段。
2. **开放注册与共享数据并存**：`AuthService.wxLogin` 为任意微信用户自动建号，但所有人读写同一份菜谱/库存/食集/采购清单——**第二个真实用户登录即可看到并删除第一个家庭的全部数据**。
3. **账号与家庭成员混为一谈**：`member` 表同时承担「登录账号」（phone/openid/passwordHash/isAdmin）与「家庭成员档案」（healthProfile/goal/忌口）两个概念（V29 合并 user 所致）。无法表达「一个账号属于多个家庭」「宝宝没有账号但有档案」「平台运营人员 ≠ 家庭主理人」。
4. **超管与家庭管理员混用**：`member.isAdmin` 既是平台超管又是家庭内全权，多家庭下语义冲突。

谁受影响：所有对外用户的数据隐私与正确性；这是对外开放的**硬前置条件**。

## Proposed Solution

以**领域模型**重建身份与租户（详见 [design.md](./design.md)），新上下文**原生按 DDD 四层**实现，作为 `backend-ddd-architecture` 的首个完整样板：

1. **三个新限界上下文**
   - `identity`（账号）：`Account` 聚合——登录凭证（手机号/微信 openid/unionid/密码）、平台角色（USER / OPERATOR / SUPER_ADMIN）、状态（ACTIVE / DEACTIVATED）、注销。
   - `family`（家庭 = 租户）：`Family` 聚合——家庭资料、成员关系 `Membership`（OWNER / ADMIN / MEMBER）、邀请 `Invitation`；`FamilyMemberProfile` 聚合——就餐成员档案（可关联账号，也可无账号）。
   - 上下文映射：其余业务上下文通过 `TenantContext`（当前 familyId + accountId + profileId）消费，**不直接依赖 family 内部模型**。
2. **租户隔离（纵深防御三层）**
   - 请求层：`TenantInterceptor` 从 token session 取当前家庭，**每次请求校验账号仍是该家庭成员**，失败 403。
   - 数据层：MyBatis-Plus `TenantLineInnerInterceptor` 对家庭域表自动追加 `family_id = ?`；全局表（食材/营养/字典/单位换算）入忽略名单。
   - 领域层：家庭域聚合根持有 `FamilyId` 值对象，Repository 保存时断言租户一致；ArchUnit + 越权 E2E 守护。
3. **公共菜谱库 + 家庭私有菜谱**：`dish` 增加 `family_id`（NULL = 平台公共）与 `source_dish_id`（复制来源）。家庭可读「公共 + 本家」、只能写本家；`POST /dish/{id}/fork` 复制公共菜谱到本家改编；公共库由平台运营（OPERATOR）在后台维护。
4. **家庭协作**：创建家庭、邀请码/分享链接加入（7 天有效、可撤销）、切换当前家庭、角色调整、移除成员、退出、转让主理人、解散家庭。
5. **新用户引导**：首次登录自动创建「我的家」（OWNER），引导填写成员档案；被邀请链接进入则直接加入目标家庭。
6. **账号注销**（应用商店/微信审核硬性要求）：OWNER 须先转让或解散；注销后匿名化个人数据，家庭数据随家庭保留。
6a. **微信账号并入手机号账号**（2026-10-26 用户体验发现）：同一个人先微信登录（自动建号）、后用手机号账号登录，会变成两个互不相干的账号。微信登录态下验证手机号账号（V1 密码；短信通道接入后加验证码）后，**以手机号账号为主自动合并**：微信身份（openid/unionid）移到手机号账号，微信账号的家庭成员关系与个人数据并入，微信账号标记 MERGED 并失效。此后微信登录直接进手机号账号。小程序只保留微信登录，手机号仅作可选绑定项。
7. **存量迁移**：现有菜谱 → 公共库（`family_id = NULL`）；其余家庭域数据 → 「默认家庭」；现有 `member` 中有登录凭证的拆出 `account`，全部成员档案归入默认家庭；原 `isAdmin=1` → 平台 SUPER_ADMIN + 默认家庭 OWNER。
8. **后台定位调整**：menu-admin 成为**平台运营后台**（公共菜谱库、食材营养库、字典、用户与家庭只读概览、AI 用量），**不可浏览家庭私有数据**（隐私）。家庭成员管理移入 C 端（小程序/App「家庭」页）。

## Scope

### In Scope
- menu-api：identity / family 两个新上下文（四层）、TenantContext 基建、租户拦截器、全部家庭域表加 `family_id` 与索引、各 Service/Mapper 适配、dish 可见性规则与 fork、注销
- 迁移脚本（含回滚脚本）与迁移校验 SQL
- 三端：登录后家庭上下文、家庭页（成员/邀请/切换/角色）、邀请落地页、成员档案页改造、注销入口
- menu-admin：运营后台权限改造（OPERATOR），移除家庭数据页面或改为只读统计
- 测试：领域单测（Family 不变量）、越权 E2E 套件（A 家庭对 B 家庭全部写接口 → 404）

### Out of Scope
- UGC 社区公开发布与审核（后期另立 change）
- 家庭私有食材（V1 食材库全局共享；家庭自定义食材后续评估）
- 跨家庭数据迁移/合并（账号合并时两个账号的家庭**都保留**、合并后账号同属多个家庭，不把一个家庭的数据搬进另一个家庭）

## Impact Analysis

| Component | Change | Details |
|-----------|--------|---------|
| Database | **Yes（大）** | 新表 account / family / family_membership / family_invitation；~20 张家庭域表加 `family_id`；member 改造为 family_member_profile |
| API | Yes | 新增 `/account/*`、`/family/*`；登录返回 `families[]` 与 `currentFamilyId`；`/member` 语义变为「本家成员档案」；数据接口 URL 不变，按当前家庭隐式过滤 |
| menu-mini / menu-flutter | Yes | 家庭切换器、家庭页、邀请落地、首次引导、注销 |
| menu-admin | Yes | 改为运营后台，鉴权改 OPERATOR |
| 性能 | 小 | 所有家庭域查询带 `family_id` 前缀索引 |
| backend-ddd-architecture | 调整 | 原 Out of Scope「不改表结构」由本 change 解除；Phase 1 样板改为 identity/family（新建）+ dish（迁移） |

## Risks / Trade-offs

- **迁移风险最高**：生产库一次性加列 + 回填。缓解：staging 用生产快照全量演练 → 迁移校验 SQL（每表 `family_id IS NULL` 计数为 0，dish 除外）→ 备份后低峰执行 → 回滚脚本。
- **漏过滤风险**：拦截器 + Repository 断言 + 越权 E2E 三重兜底；任何手写 SQL（`@Select`/XML）必须在 ArchUnit/代码审查清单里登记。
- **TenantLineInnerInterceptor 与 dish「公共 OR 本家」读规则冲突**：dish 不进自动拦截，由 `DishRepository` 显式实现可见性规则，并有专门测试。
- 前端三端同时改造工作量大：先后端兼容（未选家庭时自动落到唯一/默认家庭），前端分批切换。
