# API Contract: feature-gaps-closure

**Change ID:** `feature-gaps-closure` · 2026-10-16 定稿，三端与后端并行开发以此为准。
统一响应 `R<T> = {code, msg, data}`；`code=0` 成功，`401` 未登录，`403` 无权限，`429` 超限，其他非 0 = 业务失败（msg 可直接展示）。
日期 `YYYY-MM-DD`，时间 ISO `YYYY-MM-DDTHH:mm:ss`。下文 `level ∈ ENOUGH | LOW | NONE`。

---

## Deviations（后端实现说明，2026-10-16）

> 均为契约未写明处的取舍或超集，前端按此对接无需改结构。

1. `cooking_record.source` 落库值为小写 `dish`（与存量 `menu` 及食记 `source` 筛选一致），非 `DISH`；不暴露在接口。
2. `PUT /member/me` 返回更新后的 `{id,name,avatarUrl,phone,isAdmin}`（契约未定义返回，超集）；`avatarUrl: ""` = 清除头像；本站地址规则 `^(/gudu)?/uploads/...`。
3. `GET /pantry/expiring`：档位为 `NONE`（已用完）的不返回；`days` 夹紧到 0–365。「用完」会同时清除该食材 `bestBefore`。
4. `/dish/search?sort=coverage`：在条件命中的前 200 条候选池内按覆盖率排序再分页（`total ≤ 200`）；未登录时 `coverage=null` 且退化为默认排序。
5. `POST /cook/dish/{id}`：`servings` 可为小数，范围 (0, 99]；同一食材同时出现在 `usedUp` 与 `partiallyUsed` 时以「用完」为准；「用了一些」对快用完/没有/未建档的食材不降档。
6. `/ingredient/frequent`、`/cookbook/recent`、`/recommend/home`、`/member/me` 需登录（未登录 `code=401`），身份一律取登录账号。
7. `GET /feedback` 分页 `pageSize` 夹紧 1–100（默认 10）；当日超 10 条返回 `code=429`。

---

## 0. 公共结构

```ts
type Coverage = {            // 本家余量对一道菜用料的覆盖（调料不计入）
  enough: number             // 档位为 ENOUGH 或 LOW 的用料数
  total: number              // 非调料用料总数
  missing: { ingredientId: number; name: string }[]   // 档位 NONE 或未建档
}
type DishCard = {
  dishId: number; name: string; coverImage: string | null
  cookMinutes: number | null; difficulty: number | null
  cookedCount: number        // 做过次数（当前账号）
  coverage: Coverage | null  // 未登录为 null
}
```

## 1. 余量保质期（G2/G6）

| 方法 | 路径 | 变更 |
|------|------|------|
| PUT | `/pantry/{ingredientId}/level` | body 增 `bestBefore?: string \| null`（不传=不改，传 null=清除） |
| POST | `/pantry/manual` | body 增 `bestBefore?: string` |
| GET | `/pantry/grouped` | items 每项增 `bestBefore: string \| null`、`daysLeft: number \| null`（今天到期=0，已过期为负） |
| GET | `/pantry/item?ingredientId=` | 同上增两字段 |
| GET | `/pantry/expiring?days=3` | **返回结构变更**：`ExpiringItem[]`，数据源改为三档库存（不再读旧 pantry 批次表），按 daysLeft 升序，含已过期 |
| GET | `/pantry/shelf-life?ingredientId=` | **新增**：`{ suggestedDays: number \| null }`，按食材采购品类给默认保质天数建议（录入时预填 bestBefore=今天+suggestedDays） |

```ts
type ExpiringItem = { ingredientId: number; ingredientName: string; level: string; bestBefore: string; daysLeft: number }
```

## 2. 单菜直做（G1）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/cook/dish/{dishId}/materials?servings=1` | 做菜确认弹窗数据，结构同 `GET /menu/{id}/cook-materials`：`{menuId: null, items: [{ingredientId, ingredientName, usageTexts[], level, isCondiment}]}` |
| POST | `/cook/dish/{dishId}` | body `{ servings?: number(默认1), usedUp?: number[], partiallyUsed?: number[], note?: string(≤200) }` → `{ recordId: number, dishId: number }`；写 cooking_record(source=DISH)，按确认回写档位（用完→NONE，用了一些→降一档），食记可见 |

## 3. 菜谱覆盖率（G3）

- `GET/POST /dish/search` 每条记录增 `coverage: Coverage | null`（登录才算）。
- `sort` 新增取值 `coverage`：按 `enough/total` 降序，其次 missing 数升序。

## 4. 最近做过 / 常用食材（G4/G11）

| 方法 | 路径 | 返回 |
|------|------|------|
| GET | `/cookbook/recent?limit=10` | `{ dishId, name, coverImage, lastCookedAt, cookedCount }[]`（当前账号，按菜去重，最近优先；limit ≤ 30） |
| GET | `/ingredient/frequent?limit=8` | `{ id, name }[]`（按当前账号做过的菜 + 录入菜谱的用料频次；无数据回退食材库前 N） |

## 5. 首页智荐（G5）

`GET /recommend/home`（需登录）→
```ts
{
  expiring: ExpiringItem[]          // 3 天内到期（最多 5）
  expiringPicks: DishCard[]         // 用到临期食材的菜，覆盖率降序，最多 3
  frequent: DishCard[]              // 你常做（做过次数降序），最多 6
  seasonal: DishCard[]              // 季节鲜（当月时令食材相关菜），最多 6
}
```
任一分区无数据返回空数组，前端隐藏该分区。

## 6. 个人资料（G7）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/member/me` | 当前登录成员 `{ id, name, avatarUrl, phone(脱敏 138****0001 / null), isAdmin }` |
| PUT | `/member/me` | body `{ name?: string(1–20), avatarUrl?: string }`（avatarUrl 必须是本站 `/uploads/` 地址） |

## 7. 意见反馈（G14）

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| POST | `/feedback` | 登录 | `{ content: string(5–500), contact?: string(≤50), images?: string[](≤3, 本站 uploads 地址), clientInfo?: string(≤200) }` → `id`；每账号每日 ≤ 10 条 |
| GET | `/feedback?status=&pageNum=&pageSize=` | 超管 | 分页 `{ id, memberId, memberName, content, contact, images, clientInfo, status(NEW/DONE), createTime }` |
| PUT | `/feedback/{id}/status` | 超管 | `{ status: 'NEW' \| 'DONE' }` |

## 8. 运营仪表盘（G12）

`GET /ops/stats?days=14`（超管）→
```ts
{
  totals: { members: number; accounts: number; dishes: number; cookRecords: number; feedbackOpen: number }
  last7d: { newAccounts: number; activeMembers: number; cookRecords: number; aiCalls: number; aiCostYuan: number }
  trend: { date: string; newAccounts: number; activeMembers: number; cookRecords: number; aiCalls: number }[]
}
```
activeMembers = 当日在 cooking_record / daily_log / ai_call_log / review 任一有记录的去重成员数。

## 9. 静态页（G14）

用户协议 / 隐私政策 / 关于：三端内置静态页（文案来源 `docs/legal/terms.md`、`docs/legal/privacy.md`，主体信息用 `【运营主体】` 占位待用户补）。不新增接口。
