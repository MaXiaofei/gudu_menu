# Design: 账号 / 家庭领域模型与租户隔离

**Change ID:** `multi-tenant-family`

---

## 1. 通用语言（Ubiquitous Language）

| 术语 | 含义 | 代码名 |
|------|------|--------|
| 账号 | 能登录的人（凭证 + 平台角色） | `Account` |
| 家庭 | 数据隔离单元（租户），一起做饭吃饭的一群人 | `Family` |
| 家庭成员关系 | 某账号在某家庭中的身份与角色 | `Membership` |
| 主理人 | 家庭创建者/最高权限，每个家庭**恰好一个** | `FamilyRole.OWNER` |
| 成员档案 | 一起吃饭的人的健康/口味档案，可无账号（宝宝、老人） | `FamilyMemberProfile` |
| 邀请 | 加入家庭的一次性/限时凭证 | `Invitation` |
| 当前家庭 | 本次会话正在操作的家庭 | `TenantContext.familyId` |
| 公共菜谱 | 平台维护、所有家庭可见 | `Dish.familyId == null` |
| 本家菜谱 | 家庭私有 | `Dish.familyId == 当前家庭` |
| 复制到我家 | 把公共菜谱拷贝为本家菜谱并记录来源 | `Dish.forkTo(familyId)` |
| 平台运营 | 维护公共库/字典的工作人员，**不是**家庭角色 | `PlatformRole.OPERATOR` |

## 2. 限界上下文映射

```
┌──────────────┐  accountId   ┌──────────────────────────────┐
│  identity    │◄─────────────│  family                      │
│  Account     │              │  Family(+Membership,Invite)  │
│              │              │  FamilyMemberProfile         │
└──────┬───────┘              └──────────────┬───────────────┘
       │ 登录签发 token                       │ 发布 TenantContext（OHS 开放主机服务）
       ▼                                     ▼
┌────────────────────────────────────────────────────────────┐
│  common.tenant：TenantContext(familyId, accountId, profileId, familyRole)│
└────────────────────────────────────────────────────────────┘
       │ 只读消费（Conformist）
       ▼
 dish · menu · pantry · shopping · cookbook · dailylog · review · foodlog · ai · notification
```

- 业务上下文**只认** `TenantContext` 与 `FamilyId` 值对象，不引用 `Family` 聚合。
- `family` 上下文对外暴露 `FamilyQueryApi`（application 层）：`isMember(familyId, accountId)`、`roleOf(...)`、`listProfiles(familyId)`。

## 3. 聚合设计

### 3.1 `Account`（identity 上下文）
```
Account (AggregateRoot<AccountId>)
├── phone: PhoneNumber?        值对象，唯一
├── wechat: WechatIdentity?    值对象(openid, unionid)，openid 唯一
├── passwordHash: String?
├── platformRole: PlatformRole USER | OPERATOR | SUPER_ADMIN
├── status: ACTIVE | DEACTIVATED
├── nickname, avatarUrl
行为：
  registerByWechat(openid, unionid) / registerByPhone(phone, rawPwd)
  changePassword(old, new)          校验旧密码
  bindPhone(phone)
  grantPlatformRole(role, by)       仅 SUPER_ADMIN 可调用（应用层校验操作人）
  deactivate(now)                   匿名化：清 phone/openid/昵称/头像，status=DEACTIVATED
  absorb(source: Account, now)      账号合并（本账号=手机号账号为主）：source 必须 ACTIVE 且有 wechat、无 phone；
                                    本账号不能已绑定另一个 openid（冲突 → ACCOUNT_WECHAT_CONFLICT）；
                                    wechat 身份移到本账号，source.status=MERGED、mergedInto=本账号
事件：AccountRegistered, AccountDeactivated, AccountMerged(sourceId, targetId)
```

**账号合并流程**（`AccountMergeAppService`，单事务，幂等）：
1. 当前登录 = 微信账号 W；用户提交手机号 + 密码，校验得到手机号账号 P（失败计入登录限流）。
2. `P.absorb(W)`；保存两个聚合。
3. 发布 `AccountMerged` → family 上下文监听：W 的每个 `Membership` 转给 P（P 已在该家庭则保留角色较高者）；W 关联的成员档案 `accountId` 改指 P（P 在同一家庭已有档案时保留 P 的，W 的档案改为无账号档案，不删数据）。
4. 其余按账号归属的个人数据（Agent 会话、反馈、通知、同意记录）由各上下文监听 `AccountMerged` 把 accountId 从 W 改为 P。
5. 使 W 的 token 失效，为 P 签发新 token 返回；当前家庭沿用 W 的当前家庭。
6. 审计：`account_merge_log(source_id, target_id, operator, created_at, detail)`，不可回滚但可追溯。
7. 资料取舍：昵称/头像优先保留用户改过的那份（P 的为空或仍是默认值「微信用户」时取 W 的）；平台角色取 P 的（不因合并提权或降权）。

**账号合并 · 数据归属表**（W = 微信账号，P = 手机号账号；全仓 grep `member_id / account_id / created_by / author_id / user_id / *_by / operator_id` 等列逐表判定）。
归属分两类：**按账号**（列里存的是 account.id = 登录 loginId）→ 合并时改指 P；**按成员档案**（列里存的是 member.id = 当前就餐成员）→ 不改，W 的档案在 family 监听里已改指 P（P 在同家庭已有档案时，W 的档案降为无账号档案、数据随该档案保留）。

| 表.列 | 归属 | 合并处理 | 实现 |
|------|------|---------|------|
| account（W 行） | 账号本身 | W.status=MERGED、merged_into=P，wx_openid 移到 P | `Account.absorb` |
| account_merge_log | 审计 | 新增一行（不可回滚） | `AccountMergeAppService` |
| family_membership.account_id | 账号 | **改指 P**；同家庭都在则保留较高角色，OWNER 随之转移 | `Family.reassignAccount` |
| family.owner_account_id | 账号（冗余） | **改指 P**（保存时按 OWNER 成员重算） | `MybatisFamilyRepository.save` |
| family_invitation.created_by | 账号 | **改指 P** | `Family.reassignAccount` |
| member.account_id（成员档案） | 账号 | **改指 P**；P 在同家庭已有档案 → W 的档案 account_id 置空（无账号档案） | `FamilyAccountListener.onMerged` |
| feedback.member_id | 账号（loginId） | **改指 P** | `FeedbackAccountMergedListener` |
| vision_job.member_id | 账号（loginId） | **改指 P** | `VisionAccountMergedListener` |
| community_post.author_id / community_comment.author_id | 账号 | **改指 P** | `CommunityAccountMergedListener` |
| community_reaction.user_id、community_copy.user_id、community_report.reporter_id、community_topic_checkin.user_id | 账号（有唯一键） | **改指 P**；与 P 已有行冲突时保留 P 的、删 W 的 | `MybatisInteractionRepository.reassignAccount` |
| community_follow.follower_id / followee_id | 账号（有唯一键） | **改指 P**（双向），去重并去掉自关注 | 同上 |
| community_appeal.author_id | 账号 | **改指 P** | `MybatisAppealRepository.reassignAccount` |
| community_ban.user_id | 账号（主键） | **改指 P**，解封时间取两者较晚 | `MybatisInteractionRepository.reassignAccount` |
| community_moderation_log.operator_id | 运营人员 | 保留（审计，不改写历史） | — |
| menu_join.member_id | 账号（聚餐登录身份） | **改指 P**；同一食集 W、P 都加入过 → 保留 P 的、删 W 的 | `TogetherAccountMergedListener` |
| menu_invite.created_by | 账号 | **改指 P** | 同上 |
| menu_activity.member_id | 账号 | **改指 P**（昵称已冗余在行内） | 同上 |
| menu_dish.added_by_member_id | 混合（聚餐 = 账号，Agent / 餐程 = 档案） | 保留：仅作留痕，展示用行内冗余的 added_by_nickname；两种 id 混存无法安全改写 | — |
| agent_session / agent_message / agent_trace.member_id | 成员档案（currentMemberId） | 不涉及：随档案归属 | — |
| favorite.member_id、review.member_id、cooking_record.member_id、daily_log.member_id、dish_draft.user_id、menu.target_member_id、meal_plan_draft.member_id | 成员档案 | 不涉及：随档案归属 | — |
| ai_call_log.member_id | 成员档案（请求里的就餐成员） | 保留（调用审计 / 计费日志，不改写） | — |
| ai_gateway_audit.actor、ai_route.updated_by | 字符串审计（`member:{loginId}`） | 保留（审计） | — |
| user（V01 遗留后台账号表） | 已废弃 | 不涉及 | — |
| Redis：AI 限流计数、登录失败计数、Sa-Token 会话 | 按 loginId | W 的 token 全部下线（`StpUtil.logout(W)`），计数自然过期 | `AccountController.mergePhone` |
| notification.recipient_account_id（V91） | 账号（反馈回复、厨房圈互动） | **改指 P** | `NotificationAccountMergedListener` |
| notification.recipient_profile_id（V91） | 成员档案（临期、采购） | 不涉及：随档案归属 | — |
| user_preference.member_id（主键） | 账号 | **改指 P**；P 已有偏好 → 保留 P 的、删 W 的 | `PreferenceAccountMergedListener` |
| content_media_check.member_id、content_text_violation.account_id | 账号（上传 / 违规账号） | **改指 P**（P 注销时要能找到 W 当年上传的头像文件） | `ContentSecurityAccountMergedListener` |
| 同意记录 | 账号 | **改指 P** | `ConsentAccountMergedListener` |

「以 P 为主」只决定**保留哪个账号 ID**（P 带着密码、手机号和平台角色，App 端手机号登录必须继续可用），不决定谁的数据留下：两边的家庭都保留，合并后无论用微信还是手机号登录，看到的都是同一个账号、同样的数据。

### 3.2 `Family`（family 上下文，**核心不变量都在这里**）
```
Family (AggregateRoot<FamilyId>)
├── name: FamilyName            1–20 字
├── memberships: List<Membership>   (accountId, role, joinedAt)
├── invitations: List<Invitation>   (code, createdBy, expiresAt, maxUses, usedCount, revoked)
├── status: ACTIVE | DISSOLVED
不变量：
  I1 恰好一个 OWNER
  I2 同一 accountId 至多一条 membership
  I3 成员数 ≤ 20
  I4 DISSOLVED 后拒绝一切变更
行为（operator 为发起人 accountId，权限判断在聚合内）：
  create(name, ownerAccountId)                       → FamilyCreated
  invite(operator, ttl=7d, maxUses=10) : Invitation  仅 OWNER/ADMIN
  join(accountId, code, now)                         校验未过期/未撤销/未用尽/I2/I3 → MemberJoined
  revokeInvitation(operator, code)                   仅 OWNER/ADMIN
  changeRole(operator, target, role)                 仅 OWNER；不可借此产生第二个 OWNER
  remove(operator, target)                           OWNER 可移任何人(除自己)；ADMIN 只能移 MEMBER → MemberLeft
  leave(accountId)                                   OWNER 不可直接离开（须先转让）→ MemberLeft
  transferOwnership(operator, newOwner)              仅 OWNER，新主须为成员；原 OWNER 降为 ADMIN
  dissolve(operator)                                 仅 OWNER → FamilyDissolved
```
跨账号限制（每个账号最多加入 5 个家庭）属于**跨聚合规则**，放在领域服务 `MembershipPolicy`，由应用层在 `join/create` 前调用（查询 `FamilyRepository.countActiveByAccount`）。

### 3.3 `FamilyMemberProfile`（family 上下文，独立聚合）
```
FamilyMemberProfile (AggregateRoot<ProfileId>)
├── familyId: FamilyId
├── accountId: AccountId?       有账号的成员与其账号一一关联（同一家庭内唯一）
├── displayName, avatar
├── health: HealthProfile        值对象（身高/体重/年龄/性别/忌口/过敏/特殊人群：高血压|高血糖|高血脂|宝宝辅食）
├── goal: DietGoal, activityLevel: ActivityLevel
行为：updateHealth(...)、linkAccount(accountId)、archive()
领域服务：NutritionTargetCalculator（原 MemberController 内 BMR 计算下沉为纯函数）
```
- 设计取舍：Profile 不放进 Family 聚合——档案编辑频繁、与成员关系的一致性无强约束，拆开避免大聚合锁竞争。账号加入家庭时由 `MemberJoined` 事件**自动创建**关联档案。

### 3.4 业务聚合的租户化
所有家庭域聚合根增加 `FamilyId familyId`（构造时必填、不可变）：
`Menu`、`IngredientStock`、`ShoppingList`、`CookingRecord`、`Favorite`、`DailyLog`、`Review`、`DishDraft`、`MealPlan`、`MenuTemplate`、`Notification`(按 account)。
`Dish`：`familyId` 可空（公共），行为 `forkTo(FamilyId)`、`assertEditableBy(TenantContext)`（公共菜谱仅 OPERATOR 可改）。

## 4. 租户上下文与隔离实现

### 4.1 TenantContext 的建立
1. 登录成功：返回 token + `families[]` + `currentFamilyId`（上次使用的家庭；无则唯一家庭）。session 存 `familyId`。
2. 切换：`POST /family/current {familyId}` → 校验成员关系 → 写 session。
3. 每次请求 `TenantInterceptor`（在 Sa-Token 之后）：
   - 读 session.familyId → `FamilyQueryApi.roleOf(familyId, accountId)`（Redis 缓存 5 分钟，`MemberLeft/FamilyDissolved` 事件主动失效）
   - 非成员 → 清 session.familyId，返回 403 `FAMILY_ACCESS_DENIED`，前端回到家庭选择
   - 成功 → `TenantContextHolder.set(...)`（ThreadLocal，请求结束清理）
4. 游客白名单接口无 TenantContext，仅能读公共数据。

### 4.2 数据层自动过滤
- `TenantLineInnerInterceptor`，`tenantIdColumn = family_id`，`getTenantId()` 从 TenantContextHolder 取。
- **忽略表**（全局）：`ingredient`、`ingredient_nutrition`、`nutrition_metric`、`sys_dict`、`ingredient_unit_gram`、`account`、`family`、`family_membership`、`family_invitation`、`ai_call_log`（按 account）、`schema_migration_log`；`dish`/`dish_ingredient`/`dish_step`（可见性规则手写）。
- 无 TenantContext 时访问家庭域表 → **抛异常而非放行**（防后台任务漏设）；定时任务（NotificationScheduler）用 `TenantContextHolder.runAs(familyId, ...)` 显式切换。

### 4.3 dish 可见性（手写，不进拦截器）
- 读：`family_id IS NULL OR family_id = :current`
- 写：`family_id = :current`；公共菜谱写操作需 `PlatformRole ≥ OPERATOR`
- 子表 `dish_ingredient/dish_step` 只经 `Dish` 聚合访问
- 其他上下文按 dishId 引用菜谱时，必须经 `DishQueryApi.findVisible(dishId)`（返回空即 404）

### 4.4 越权语义
跨家庭访问资源统一返回 **404**（不泄露资源存在性）；角色不足返回 403。

## 5. 数据库变更（V75–V79）

```sql
account(id, phone UNIQUE, wx_openid UNIQUE, wx_unionid, password_hash, nickname, avatar_url,
        platform_role, status, last_family_id, created_at, deactivated_at)
family(id, name, owner_account_id, status, created_at, dissolved_at)
family_membership(id, family_id, account_id, role, joined_at, UNIQUE(family_id, account_id), INDEX(account_id))
family_invitation(id, family_id, code UNIQUE, created_by, expires_at, max_uses, used_count, revoked, created_at)
-- member → family_member_profile 语义：保留表名 member 以减少改动，新增 family_id、account_id 列
ALTER TABLE member ADD family_id BIGINT, ADD account_id BIGINT, UNIQUE(family_id, account_id)
-- 家庭域表：ADD family_id BIGINT NOT NULL(回填后) + INDEX(family_id, ...)
menu, menu_dish*, menu_prep_status, menu_activity, menu_invite, menu_join, menu_cook_usage, menu_template,
ingredient_stock, pantry, pantry_change_log, stock_log, shopping_list, shopping_item*, cooking_record, favorite,
daily_log, daily_log_item*, review, review_score*, dish_draft, dish_history, meal_plan, meal_plan_item*, notification
-- * 子表通过父表归属；为让拦截器生效统一冗余 family_id
dish ADD family_id BIGINT NULL, ADD source_dish_id BIGINT NULL, INDEX(family_id)
```

### 迁移步骤（单次低峰窗口，先全量备份）
1. 建新表；插入「默认家庭」（name=「咕嘟之家」）。
2. `member` 中有 phone/openid/password_hash 的行 → 生成 `account`（保留原 BCrypt 哈希，密码不变）；原 isAdmin=1 → `SUPER_ADMIN`。
3. 全部 member → `family_id = 默认家庭`、有账号的写 `account_id`；有账号者写 `family_membership`（原 isAdmin=1 为 OWNER，其余 MEMBER）。
4. 家庭域表 `UPDATE ... SET family_id = 默认家庭`；再改 `NOT NULL`。
5. `dish.family_id` 保持 NULL（进入公共库）；`dish_draft` 归默认家庭。
6. **校验 SQL**：每张家庭域表 `COUNT(*) WHERE family_id IS NULL` = 0；`family` 恰一个 OWNER；account 数 = 有凭证 member 数。
7. 回滚脚本：`DROP COLUMN family_id` 系列 + 删新表（member 原列未删，旧代码可直接运行）。
8. `member.phone/openid/password_hash/is_admin` 旧列保留一个版本周期，下一 change 删除。

## 6. API 设计（新增）

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | `/account/me` | 账号信息 + families[] + currentFamilyId | 登录 |
| PUT | `/account/me` | 昵称/头像 | 登录 |
| PUT | `/account/password` | 改密码 | 登录 |
| DELETE | `/account/me` | 注销（OWNER 须先处理家庭） | 登录 |
| POST | `/account/merge-phone` | 微信账号并入手机号账号 `{phone, password}` → 新 token | 登录（仅微信账号） |
| POST | `/family` | 创建家庭 | 登录（≤5） |
| GET | `/family/current` | 当前家庭详情（成员+角色） | 成员 |
| POST | `/family/current` | 切换当前家庭 | 成员 |
| PUT | `/family/{id}` | 改名 | OWNER/ADMIN |
| POST | `/family/{id}/invitations` | 生成邀请码/链接 | OWNER/ADMIN |
| DELETE | `/family/{id}/invitations/{code}` | 撤销邀请 | OWNER/ADMIN |
| GET | `/family/invitations/{code}` | 邀请预览（家庭名/邀请人） | 游客 |
| POST | `/family/join` | 凭邀请码加入 | 登录 |
| PUT | `/family/{id}/members/{accountId}/role` | 调整角色 | OWNER |
| DELETE | `/family/{id}/members/{accountId}` | 移除成员 | OWNER/ADMIN |
| POST | `/family/{id}/leave` | 退出 | 成员（非 OWNER） |
| POST | `/family/{id}/transfer` | 转让主理人 | OWNER |
| DELETE | `/family/{id}` | 解散 | OWNER |
| POST | `/dish/{id}/fork` | 公共菜谱复制到本家 | 成员 |
| `/member/**` | 语义变更 | 仅本家成员档案 CRUD；无 isAdmin/password 字段 | 成员（写：OWNER/ADMIN 或本人档案） |

`MpPerm` 权限矩阵改为基于 `FamilyRole` 的默认模板 + 档案级勾选，作用域限定当前家庭。

## 7. 包结构（原生四层样板）

```
modules/family/
├── interfaces/   FamilyController, InvitationController, dto/*
├── application/  FamilyAppService, FamilyQueryApi(对外), MembershipPolicy 调用, 事件监听(MemberJoined→建档案)
├── domain/
│   ├── model/    Family, Membership, Invitation, FamilyRole, FamilyId, FamilyName, InviteCode, FamilyMemberProfile, HealthProfile
│   ├── service/  MembershipPolicy, NutritionTargetCalculator
│   ├── event/    FamilyCreated, MemberJoined, MemberLeft, FamilyDissolved, OwnershipTransferred
│   └── repository/ FamilyRepository, ProfileRepository (接口)
└── infrastructure/ po/*, mapper/*, converter/*, FamilyRepositoryImpl
modules/identity/  同构：Account、PhoneNumber、WechatIdentity、PasswordPolicy、AccountRepository
common/tenant/     TenantContext, TenantContextHolder, TenantInterceptor, FamilyId(共享内核)
```

## 8. 测试策略

- **领域单测（纯 JUnit，毫秒级）**：Family 不变量 I1–I4 全分支、邀请过期/用尽/撤销、角色矩阵（3 角色 × 6 行为）、转让后角色、解散后拒绝变更。
- **应用层**：Mockito 断言事件发布与 MembershipPolicy 调用。
- **越权 E2E 套件** `GuduE2ETenantIsolationTest`：建 E2E 家庭 A、B → B 对 A 的每个资源（菜单/库存/采购/食记/点评/草稿/成员档案/私有菜谱）执行读写删 → 全部 404；公共菜谱 B 可读不可写。
- **迁移演练**：staging 恢复生产快照 → 执行迁移 → 校验 SQL 全过 → 三端冒烟。

## 9. 账号 id 与档案 id 归属

### 9.1 问题与结构性兜底

系统里有两种「人」的 id：**账号 id**（`account.id` = Sa-Token loginId）与**成员档案 id**（`member.id` = `CurrentMember.id()` = 当前就餐成员）。V77 让存量账号沿用旧 `member.id`，之后新账号、新档案各自自增，**会撞号**（账号 120 与档案 120 可能是两个人）。凡是把一种 id 当另一种比较 / 查询 / 删除的地方，都会串号或误删。

- **V91** 把 `account` 的 `AUTO_INCREMENT` 设为 `GREATEST(10000000, 当前自增值, MAX(id)+1)`：新账号 id 恒 ≥ 1000 万，档案 id 远小于此，**两者取值范围永不重叠**。存量账号与本人旧 member.id 相同（同一个人），无害。
- 防回退：`AccountIdSpaceCheck`（启动自检，低于下限打 ERROR）；`AccountIdSpaceCheckTest`（V91 常量与代码常量一致，且之后的迁移不得降低 account 自增）；`scripts/local-migration-rehearsal.sh` 的 `v91_assertions`。
- 编码约定：**一列只存一种 id**；列名带 `account` / `profile` 的即为该种；老列 `member_id` 的含义以下表为准。按档案归属的数据，账号侧要经 `FamilyQueryApi.profileIdsOf(accountId)` 取本人档案 id，**绝不能拿账号 id 直接当 member_id**。

### 9.2 全量审计（表.列 / 应存 / 实际写入 / 读取 / 是否有问题 / 处理）

「有问题」列带 **❌** 的是审计发现并已修复的；其余经核对一致。

| 表.列 | 应存 | 实际写入 | 读取 | 有问题 | 处理 |
|---|---|---|---|---|---|
| notification.member_id（V13） | 混用，应拆 | 反馈回复 / 厨房圈互动写**账号 id**；临期 / 采购写**档案 id** | 控制器按「当前档案 + 登录账号」`IN (…)` 合并查两份 → 账号 id 与别人档案 id 同号时**串号**；`markRead` 同理可误标；注销 `deleteByMember(accountId)` 按 `member_id = 账号 id` 删 → **误删同号档案的通知** | ❌ | V91 拆 `recipient_account_id` / `recipient_profile_id`（二选一非空）；`NotificationRecipient` 发送方必须显式指定种类；读 / 已读 / 注销 / 合并分别比各自的列；旧列保留为可空遗留列 |
| notification.family_id | —（账号级消息不属于任何家庭） | 管理员回复写入时落在**管理员的家庭**，收件人在自己的家庭里被租户过滤看不到 | 租户插件按当前家庭过滤 | ❌ | notification 不再是家庭域表（移出 `TenantTables`），按收件人可见；family_id 改可空遗留列 |
| community 发帖 / AI 帮写：`cookingRecords.refOf().memberId` 与 `userId` | 档案 id ↔ 账号 id 需换算 | `cooking_record.member_id` 是**档案 id** | `CommunityPostAppService` 用 `userId.equals(ref.memberId())` 校验「只能分享自己的食记」→ 新账号永远被拒、同号时**能分享别人的食记** | ❌ | 改为 `familyQuery.profileIdsOf(userId).contains(ref.memberId())` |
| user_preference.member_id（主键） | 账号 | 账号（loginId） | 账号 | 合并遗漏 ❌ | 补 `PreferenceAccountMergedListener`（P 已有则保留 P 的） |
| content_media_check.member_id | 账号（上传者） | 账号（`FileController` loginId） | 账号（注销按账号找头像文件） | 合并遗漏 ❌ | 补 `ContentSecurityAccountMergedListener`；列注释「member.id」实指账号 id |
| content_text_violation.account_id | 账号 | 账号 | 账号 | 合并遗漏 ❌ | 同上 |
| account.id / member.account_id / family_membership.account_id / family.owner_account_id / family_invitation.created_by / account_merge_log.operator_id | 账号 | 账号 | 账号 | 否 | — |
| member.id（档案）/ menu.target_member_id | 档案 | 档案 | 档案 | 否 | — |
| cooking_record.member_id、review.member_id、favorite.member_id、daily_log.member_id | 档案 | 档案（`CurrentMember.id()`） | 档案；导出按 `profileIds`；`CookbookController` 的 `memberId` 参数由客户端传档案 id，受家庭过滤 | 否 | — |
| dish_draft.user_id、meal_plan_draft.member_id | 档案 | 档案（`CurrentMember.id()`） | 档案 | 否（列名 / 注释易误导） | — |
| agent_session / agent_message / agent_trace.member_id | 档案 | 档案 | 档案；注销 / 导出经 `profileIdsOf`（13e24e5 已修） | 否（上一轮已修） | — |
| ai_call_log.member_id | 档案 | 档案（请求里的就餐成员，可空） | 仅审计 | 否 | — |
| feedback.member_id / replied_by | 账号 / 超管账号 | 账号 | 账号；回复通知现发给**账号** | 否（回复通知见 notification 行） | — |
| vision_job.member_id | 账号（发起人） | 账号（loginId） | 账号 | 否 | — |
| consent_record.account_id | 账号 | 账号 | 账号；导出 / 保留 | 否 | — |
| dish.created_by | 账号 | 账号（`AuditFieldFillHandler` 取 `TenantContext.accountId`） | 账号（导出、合并） | 否 | — |
| community_*（author_id / user_id / reporter_id / follower_id / followee_id / operator_id）、community_ban.user_id | 账号 | 账号（controller 取 loginId） | 账号 | 否 | — |
| menu_join.member_id、menu_activity.member_id、menu_invite.created_by | 账号（聚餐登录身份，游客为空） | 账号 | 账号 | 否 | — |
| menu_dish.added_by_member_id | 混合（聚餐 = 账号，Agent / 餐程 = 档案） | 混合 | **无任何读取路径**（展示用行内冗余的 added_by_nickname） | 混存但无读取，无串号面 | 保留：仅作留痕，禁止作为身份依据；见 9.1 约定 |
| account_ban.account_id、admin_operation_log.operator_id、sys_switch_log.operator_id、community_moderation_log.operator_id | 账号 | 账号（`StpUtil.getLoginIdAsLong()`） | 账号 | 否 | — |
| 登录 / 会话：`StpUtil.getLoginIdAsLong()` 与 `CurrentMember.id()` | 前者账号、后者档案 | 控制器按场景取用 | `NotificationController` 现分别传 service，不再合并成同一个 id 列表 | 见 notification 行 | — |

### 9.3 存量通知回填口径（V91）

按**写入来源**判定（可从代码确认）：`type IN ('feedback_reply','community')` → 账号列；其余（`expiry` / `shopping` / `prep` …，来自临期扫描与采购生成，写的是档案 id）→ 档案列。无法判定的（type 不在已知两类内且来源不明）按旧逻辑保守视为档案级——存量中这类行只可能来自 V13 起的档案级通知。V77 之前的老数据账号 id = 档案 id（同一个人），无论落哪一列都能被本人看到。

### 9.4 测试

- `NotificationRecipientIsolationTest`（H2 + MyBatis-Plus 真实 SQL）：账号 120 与别人档案 120 同号 → 列表 / 未读 / 已读 / 注销 / 合并互不可见。
- `CommunityPostAppServiceTest`：账号 5 与他人档案 5 同号不能分享对方食记；新账号分享自己档案食记放行。
- `AccountErasureStepsTest`、`AccountMergedListenersTest`、`FeedbackAppServiceTest`、`NotificationSchedulerTest`：收件人种类断言。
- E2E：`GuduE2ETenantIsolationTest.通知收件人隔离_…`（新账号 id ≥ 1000 万、反馈回复仅账号可见、采购通知仅档案可见）。
