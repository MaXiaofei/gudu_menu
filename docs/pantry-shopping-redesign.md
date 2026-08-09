# 库存 + 采购 + 聚餐 重设计 v0.7（APP 优先）

> **状态**：设计稿 v0.7（采购改为批量保存入库；评价改为统一评价页；聚餐 v0.1 定稿）
> **日期**：2026-08-09
> **范围**：menu-flutter（APP）+ menu-api（后端）；小程序朋友端后续批次
> **v0.6 → v0.7 变更**：① 采购勾选=选择、底部批量「保存入库」统一记充足、✕ 撤回恢复入库前档位（原型 shopping-restock.html 定稿）；② 评价入口统一为「去评价」→ 统一评价页（原型 menu-review.html，替代逐菜按钮）
> **上一版**：v0.4（3 档）→ 本版：删单菜直做、采购拎出食集、备菜一键加采购、文案修订

---

## 0. 定稿决策

1. **库存不做自动扣减**：整集做菜前弹确认弹窗，用户手动确认哪些用完/用了一些；
2. **库存不用克数**：**3 档模糊级别**——`ENOUGH`（充足）/ `LOW`（不足）/ `NONE`（用完）；三色映射：NONE=红 / LOW=黄 / ENOUGH=绿；
3. **不做够不够判断**：菜谱/食集只显示"有这个食材"；
4. **做菜确认弹窗**：每项三态（用完了/用了一些/没动），默认食材=用完了、调料=用了一些，用户可自由切换；
5. **删除单菜直做入口**（APP + 小程序都不做；后端 `/dish/{id}/cook-now` 一并删除）；
6. **采购拎出食集**：食集详情删采购 Tab（菜/备菜/一起吃 3 Tab），采购唯一家园=独立采购页；备菜 Tab 加「一键加采购」；
7. **备菜 Tab 加库存徽标**（家里：充足/不足/用完）；
8. **文案修订**：「整集做菜」→「开始做饭」（见 §2.2）。

---

## 1. 新数据模型

### 1.1 ingredient_stock 表（替换 pantry 批次表）

```sql
CREATE TABLE ingredient_stock (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  ingredient_id BIGINT NOT NULL,
  level         VARCHAR(16) NOT NULL COMMENT 'ENOUGH/LOW/NONE',
  update_time   DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_stock_ingredient (ingredient_id)
) COMMENT '食材库存档位（模糊 3 档，不做克数）';
```

可选简版流水 `stock_log(id, ingredient_id, action, note, create_time)`：记录「用完/用了一些/采购入库/手动修正」，支撑库存页「上次变动」标签与详情流水。

### 1.2 档位变化规则（唯一规则表）

| 动作 | 规则 |
|---|---|
| 做菜确认·用完了 | → `NONE` |
| 做菜确认·用了一些 | 降一档（`ENOUGH→LOW`；`LOW` 不变；`NONE` 不变） |
| 采购·保存入库（批量） | 统一 `ENOUGH`（原型定稿：不再逐项选档位，个别不对之后库存页改） |
| 采购·撤回入库 | 恢复该食材**入库前档位**（`prev_level`，原型 ✕ 撤回） |
| 手动入库（朋友送/补登） | 默认 `ENOUGH`，可选 `LOW` |
| 库存页改档位 | 直接设任意档（含「用完了」快捷按钮） |

### 1.3 存量迁移（V42）

```
Σgrams ≤ 0                  → NONE
0 < Σgrams < low_threshold  → LOW
Σgrams ≥ low_threshold      → ENOUGH
grams 全 NULL → 按 amount 近似映射（不精确没关系，用户会改）
```

### 1.4 后端退役清单

| 模块 | 处置 |
|---|---|
| `PantryDeductionPlanner` / `deductByIngredient` / `deduct` / `adjust` / `stockUpByIngredient` / `planStockUp` | 删除（替换为设档位） |
| `/dish/{id}/cook-now` + `CookService.cookByDish` | **删除**（单菜直做入口废弃） |
| `UnitConvertService` 库存侧用法 / `UnitMatcher`（pantry） | 库存侧退役（菜谱 `dish_ingredient.grams` 保留给价格/营养） |
| `pantry_change_log` 复杂流水 | 简化为 `stock_log` |
| `cooking_record.memo` 欠量 / `StockClassifier` 差 X g | 删除 |
| `CookService.markPurchasedByMenu` 自动全勾选 | 删除（采购与食集状态完全解耦） |

---

## 2. 核心交互

### 2.1 做菜确认弹窗（整集做菜唯一扣减入口）

```
这顿饭用了什么
番茄炒蛋 + 清蒸鲈鱼 + 蒜蓉菠菜

本次用到的食材（每项选一个状态）
番茄     家里：充足    [✓ 用完了] [ 用了一些] [ 这次没用]
鸡蛋     家里：不足    [✓ 用完了] [ 用了一些] [ 这次没用]
鲈鱼     家里：用完    [✓ 用完了] [ 用了一些] [ 这次没用]
盐       家里：充足    [ 用完了]  [✓ 用了一些] [ 这次没用]   ← 调料默认
食用油   家里：充足    [ 用完了]  [✓ 用了一些] [ 这次没用]   ← 调料默认

[跳过，不更新库存]              [确认已做完]
```

- **时机**：点「开始做饭」后**立刻弹出**（做饭前/备料时最接近真相），点「确认已做完」即完成这顿饭；【已确认】
- **数据源**：`GET /menu/{id}/cook-materials`——本次用到的食材 + 当前档位 + 是否调料（默认值用），不判断够不够；
- **默认值**：食材=用完了，调料=用了一些（按 `ingredient.purchase_category_id` 判定）；用户可自由切换三态（用完了/用了一些/这次没用）；
- **提交**：`POST /menu/{id}/cook`，body `{usedUp:[ingredientIds], partiallyUsed:[ingredientIds]}`；
- **跳过** → 本次不改库存，只写 cooking_record + 食集完成（下次做饭还会再问）；
- **写库**：cooking_record（食记）+ 食集 DONE + 备菜全 READY（保留）；
- **结果提示**：「做好了，库存已更新」；
- **评价（v0.7）**：结果页只留一个「去评价 ›」入口 → **统一评价页**（`menu-review.html`：食集整体 + 每道菜分别评价，复用单菜四维度表单）。评价入口共三处，都指向同一评价页：做菜结果页（立刻评）/ 食集完成态「去评价」/ 食记单条详情。

### 2.2 文案修订（「整集做菜」让人不明白）

| 位置 | 旧文案 | 新文案 |
|---|---|---|
| 食集详情·菜 Tab 底部按钮 | 整集做菜 | **开始做饭** |
| 确认弹窗标题 | — | 这顿饭用了什么 |
| 确认弹窗主按钮 | 确认做菜，扣库存 | **确认已做完** |
| 确认弹窗次按钮 | 先去采购 | 先去采购（保留） |
| 完成态按钮 | 已完成 | 已完成（保留） |
| 结果提示 SnackBar | 已做菜，库存已扣；缺量：N 项 | **做好了，库存已更新** |

### 2.3 备菜 Tab（三处变化）

1. **库存徽标**：备料清单每行加「家里：充足/不足/用完」小徽标（备菜 VO 加 stockLevel 字段）；
2. **一键加采购**（新入口，放备菜清单标题行右侧）：
   - 点击 → 弹「加入采购清单」选择弹窗：备菜清单项（食材 + 用量 + 家里档位），**默认勾选家里=用完/不足 的项**，用户可改；
   - 确认 → `POST /shopping/from-prep {menuId, ingredientIds}`：后端找该食集采购清单（`by-menu`），无则新建，逐项追加（去重，referenceGrams 取备菜聚合用量）；
   - 成功后提示「已加入采购清单」→ 可跳转采购页；
3. **完成态**：备菜只读保留；「一键加采购」**完成态隐藏**（与食集只读一致）——做完饭后采购入口走库存页「去采购」。

### 2.4 采购（唯一家园=独立采购页 /shopping）

- 食集详情删除采购 Tab（4 Tab → 菜/备菜/聚餐）；采购页/食集 Tab 旧文件废弃（`menu-detail-caigou.html`/`v2` 已标废弃横幅）；
- 采购页（`shopping-page.html`）：清单列表 + 详情，**列表页无大标题（§13.1）**；清单来源：备菜 Tab「一键加采购」/「+ 新建清单」自定义 / 周计划生成；库存页「去采购」进这一页；
- badge：**用完（红）/ 不足（黄）/ 有（绿）**，删除「差 X g」；
- **批量入库（v0.7 定稿，原型 shopping-restock.html）**：
  - 勾选 = **选择**（只改 purchased 标记，不入库），点错可先取消；
  - 底部「保存入库 · N 项」→ **一次性批量入库**：清单内已勾选项统一设档位 `ENOUGH`，标已入库（`restocked=1`），并记录每项**入库前档位**（`prev_level`）；
  - 已入库项行尾 ✕ = **撤回入库**：恢复该食材入库前档位 + 回到未勾选状态；
  - 未入库项行尾 ✕ = 移除该项；
- 库存页「去采购」→ 采购页（不变）；周计划一键生成 → 采购页（不变）；
- 完成态食集不影响采购页（可随时勾选）。

### 2.5 菜谱详情

- 食材用量列表每行加徽标：**家里有（绿）/ 家里没有（灰）**；
- 不显示「差 X g」，不做任何判断；
- 数据：dish 详情接口批量填充 stockLevel。

### 2.6 库存页

- 每行：食材 + 档位文字（充足/不足/用完，三色）+ 上次变动 + ›；
- 详情页：改档位（3 档单选 + 「用完了」快捷按钮）+ 简版流水；
- 顶部：「去采购」（主）+「入库」（设档位弹窗，默认充足）；
- 空态文案更新（不再提"自动入库"）。

---

## 3. 其余场景（沿用 v0.4 结论）

| 场景 | 行为 | 优先级 |
|---|---|---|
| 周计划 | 零改动（生成逻辑不变，badge 随采购页自动简化） | — |
| 食记/价格/营养/分享 | 零改动（走菜谱 grams，与库存无关） | — |
| 食材库 | 新建食材不再需要单位换算；默认单位保留（菜谱用量用）；采购品类保留（弹窗默认值规则依赖） | P0 |
| 找菜/按食材找菜 | Flutter 尚无此页；未来做「库存→用家里有啥找菜」入口+预勾选（模糊档位够用） | P2 |
| AI 推荐 | 未来可做「优先消耗不足的食材」推荐 | P2 |
| 管理后台 | 后端表变更后 admin 库存页失效，P5 同步（档位列表或隐藏入口） | 同步项 |

---

## 4. 后端接口改动

| 接口 | 改动 |
|---|---|
| `GET /pantry`（或 `/stock`） | 3 档列表（三色分组 + 上次变动） |
| `PUT /pantry/{ingredientId}/level` | 设档位（写 stock_log） |
| `GET /menu/{id}/cook-materials` | **新增**：本次用到的食材 + 当前档位 + 是否调料 |
| `POST /menu/{id}/cook` | body 带 `usedUp/partiallyUsed`；删欠量/自动勾选逻辑 |
| `DELETE /dish/{id}/cook-now` | **删除**（单菜直做废弃） |
| `POST /shopping/from-prep` | **新增**：备菜一键加采购 `{menuId, ingredientIds}` → 找/建清单 → 追加（去重） |
| `PUT /shopping/item/{id}/purchased` | **只改勾选标记**（不再触发入库，v0.7） |
| `POST /shopping/{listId}/restock` | **新增（v0.7）**：批量保存入库——清单内已勾选项统一设档位 ENOUGH + 记录入库前档位（`prev_level`）+ 标 `restocked=1` + 写 stock_log(purchase) |
| `POST /shopping/item/{id}/unrestock` | **新增（v0.7）**：撤回入库——恢复该食材入库前档位（`prev_level`）+ `restocked=0`（用于 ✕ 撤回） |
| `POST /pantry/manual` | 改为设档位（选食材 + 档位，新建档无需单位换算） |
| dish / menu / prep 详情接口 | 批量填充 stockLevel 徽标字段 |
| 删除 | 扣减链、adjust、差 X g、markPurchasedByMenu |

> **采购数据模型（v0.7）**：`shopping_item` 加两列——`restocked TINYINT DEFAULT 0`（是否已入库）、`prev_level VARCHAR(16) NULL`（入库前档位，撤回恢复用）。

---

## 5. 实施计划

| 阶段 | 内容 | 跑通验证 |
|---|---|---|
| **P1 后端瘦身** | V42（ingredient_stock + stock_log + 迁移）；cook-materials / cook 改造 / from-prep / purchased 增强 / manual 改档位 / level 接口；删 cook-now、扣减链、adjust；详情接口填徽标；单测重写 | 迁移后档位正确；确认用材/设档位生效；from-prep 追加去重 |
| **P2 库存页** | list_page 3 档展示；详情页改档位；入库弹窗（选食材+档位） | 入库→列表刷新→详情改档→刷新 |
| **P3 做菜闭环** | 确认弹窗（三态+默认值）→ cook → 结果提示；文案「开始做饭」全套替换；菜谱详情徽标 | 做菜→弹窗→确认→档位正确变化 |
| **P4 备菜+采购** | 备菜徽标 + 一键加采购弹窗；食集删采购 Tab；采购页 badge 3 档 + **批量保存入库/撤回**（restock/unrestock 接口） | 一键加采购→采购页可见；勾选→保存入库→库存新档位；✕ 撤回→档位恢复 |
| **P5 回归与同步** | 单测全绿、flutter analyze、手工 e2e；admin pantry 页同步（或隐藏） | 全链路一致 |

---

## 6. 风险与边界

| 项 | 说明 |
|---|---|
| 确认负担 → 数据陈旧 | 时机（备料时弹）+ 默认值（食材=用完/调料=用了一些）+ 降级保护（LOW 不自动变 NONE）；陈旧可见、一键可改 |
| 多人共用家庭 | 一人记一人不记 → 部分准确；档位文字降低误导性，无法根治 |
| 失去精确量 | 价格/营养走菜谱 grams 不受影响；库存本身不再需要量 |
| 失去保质期/临期/先买先吃 | UI 从未展示临期（`listExpiring` 死代码），实际损失为 0 |
| 备菜一键加采购 vs 原有采购 Tab 生成 | 采购 Tab 删除后，整单生成的入口只剩独立采购页；备菜弹窗是"选着加"，两语义不冲突 |
| 完成态「一键加采购」隐藏 | 与食集只读一致；做完饭后采购入口走库存页「去采购」 |

---

## 8. 聚餐（一起点菜，v0.6 新增 / v0.7 文案定稿：Tab 名=聚餐）

> 原型：`menu-detail-xietong.html`（APP 房主视角）+ `friend-pick.html`（小程序朋友视角）

### 8.1 定稿决策（2026-08-09）

| # | 决策 | 定稿 |
|---|---|---|
| T1 | 入口 | 放食集里：「一起吃」Tab 为主入口，**「邀请朋友」快捷按钮在 Tab 内**（不另放菜 Tab）；不单独拎出 |
| T2 | 权限 | 朋友加菜**直接进食集**；**朋友可删食集里的任意菜**（不限自己加的），**记录谁加的/谁删的**；主人可撤任何菜 |
| T3 | 身份 | 微信授权拿昵称头像（小程序天然支持，不算注册门槛）；不做游客 |
| T4 | 实时性 | **轮询**（10s）：清单刷新 + **轮询即心跳**（更新 last_active_at）；成员=加入过的人，显示「最后活跃 X 分钟前」，不强制下线 |
| T5 | 落地切分 | 后端接口 + APP 房主侧**先行**；小程序朋友端**后续批次**（小程序要重新做） |
| T6 | 邀请 | **一次生成，三载体同效**：口令(6位) + 二维码 + 微信分享指向同一 token，谁拿到都能进；不要求每次选一种 |
| T7 | 忌口 | **v1 不做**（未设计设置忌口的入口，原型已移除）；后续要时先在个人资料设计忌口设置再联动 |

### 8.2 流程

```
① 房主：一起吃 Tab →「邀请朋友」→ 生成 口令(6位) + 二维码 + 微信分享卡片（同一 token）
② 朋友：扫码/点链接 → 小程序轻量页 → 微信授权（昵称头像）→ 进入点菜页
③ 朋友：从菜谱库搜菜/自由输入 → 加菜 → 直接写 menu_dish（标「XX 点的」）；可删食集里任意菜（记谁删的）
④ 房主：一起吃 Tab 轮询看到新菜/动态 → 可撤任何菜 → 份数/备注照常
⑤ 做菜：确认弹窗/备菜/采购照旧（朋友点的菜同样参与用量聚合）
```

### 8.3 数据模型

```sql
-- menu_dish 加列（V43）：记录谁加的
ALTER TABLE menu_dish
  ADD COLUMN added_by_member_id BIGINT NULL COMMENT '谁加的（null=房主；朋友=其 member_id）',
  ADD COLUMN added_by_nickname  VARCHAR(32) NULL COMMENT '冗余昵称（房主加的不填）';

-- 邀请凭证（V43）：一次生成三载体（code/token 同效）
CREATE TABLE menu_invite (
  id         BIGINT PRIMARY KEY AUTO_INCREMENT,
  menu_id    BIGINT NOT NULL,
  code       VARCHAR(8) NOT NULL COMMENT '6 位口令（短码分享）',
  token      VARCHAR(64) NOT NULL COMMENT '深链 token（二维码/链接）',
  created_by BIGINT NOT NULL COMMENT '房主 member_id',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_invite_code (code),
  UNIQUE KEY uk_invite_token (token)
) COMMENT '食集一起吃的邀请凭证';

-- 成员 + 轮询心跳（V43）：参与过的人 + 最后活跃时间
CREATE TABLE menu_join (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  menu_id       BIGINT NOT NULL,
  member_id     BIGINT NOT NULL,
  nickname      VARCHAR(32) NOT NULL,
  last_active_at DATETIME NOT NULL COMMENT '轮询时更新（心跳）',
  UNIQUE KEY uk_menu_member (menu_id, member_id)
) COMMENT '一起吃成员（轮询即心跳）';

-- 活动流（V43）：加菜/删菜留痕（谁删的）
CREATE TABLE menu_activity (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  menu_id     BIGINT NOT NULL,
  member_id   BIGINT NULL,
  nickname    VARCHAR(32) NULL,
  action      VARCHAR(16) NOT NULL COMMENT 'add 点菜 / remove 删菜 / create 建食集',
  dish_id     BIGINT NULL,
  dish_name   VARCHAR(64) NULL,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_activity_menu (menu_id, create_time DESC)
) COMMENT '一起吃活动流（记录谁加的/谁删的）';
```

- 朋友 = 微信授权登录后的正式 member（复用现有账号体系），加菜/删菜按 member_id 鉴权；
- 删菜：删除 menu_dish 行 + 写 menu_activity(action=remove, member_id=谁删的, dish_name)；
- 成员展示：menu_join 列表（最后活跃 = 轮询接口更新时间），食集完成前都算成员，不强制下线；
- `menu_invite` 不设过期（食集完成后失效/删除即可，家庭场景足够）。

### 8.4 接口（后端先行）

| 接口 | 说明 |
|---|---|
| `POST /menu/{id}/invite` | 房主生成/刷新邀请（返回 code + token），幂等 |
| `GET /menu/{id}/together` | 轮询：成员（昵称+最后活跃）+ 菜列表（含 added_by 标记）+ 活动流；**同时更新本人 last_active_at（心跳）** |
| `POST /menu/{id}/together/items` | 朋友加菜：`{dishId 或 customName, note?}` → 写 menu_dish（added_by=本人，note=备注）+ 活动流 |
| `DELETE /menu/{id}/together/items/{menuDishId}` | 删菜（任意已加入成员可删）：删 menu_dish + 活动流记录谁删的 |
| `GET /invite/{token}` | 小程序入口：token → 食集信息（校验后进入） |

### 8.5 批次

- **B1 后端**（V43 + 5 个接口 + 测试）—— 可先做，与库存 P2~P4 并行；
- **B2 APP 房主侧**：一起吃 Tab（邀请生成/分享/清单轮询）+ 菜 Tab「邀请朋友」快捷入口 + 菜行「XX 点的」标记；
- **B3 小程序朋友端**：随小程序重做批次（微信授权 + 点菜页，原型 friend-pick.html 已定稿）。

---

*本版废弃 v0.2/v0.3/v0.4 中「食集采购 Tab」「单菜直做」设计；小程序流程后续按本版同步。*
