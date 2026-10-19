# 接口契约（前后端并行开发以此为准）

**Change ID:** `showcase-ai-community`

- 统一包装：`R<T> = { code: 0 | 非 0, msg: string, data: T }`；时间 ISO-8601（`2026-10-16T18:40:00`）；日期 `2026-10-19`；金额单位「分」(int)。
- 身份：现有 Sa-Token（`StpUtil` 登录 id = user.id，session `currentMemberId` = 当前成员）。`multi-tenant-family` 落地前，「家庭」= 当前登录用户可见的 member 集合，与现有 pantry/menu 行为一致；社区身份 = user.id。
- 游标分页：请求 `cursor?: string, size?: number(默认 20, ≤50)`，响应 `{ items: T[], nextCursor: string | null }`。
- 迁移编号预分配：**V70 agent**、**V71 community**、**V72 vision + mealplan**。各自在 `e2e-seed.sql` 末尾追加独立区块（`-- [agent] ...`、`-- [community] ...`、`-- [vision/mealplan] ...`），编辑前重新读取文件。
- 后端 E2E 各自新建测试类：`GuduE2EAgentTest`、`GuduE2ECommunityTest`、`GuduE2EVisionMealPlanTest`（匹配 `GuduE2E*`）。

## 共用类型

```ts
type DishCard = { id: number; name: string; coverUrl: string | null; minutes: number | null;
  difficulty: string | null; coverage: { have: number; total: number; missing: string[] };
  tags: string[] }                                   // tags 如 "少盐" "宝宝可吃"
type IngredientLine = { ingredientId: number | null; name: string; iconKey: string | null;
  amount: string; unit: string | null }
type MemberLite = { memberId: number; name: string; avatarUrl: string | null }
type UserLite = { userId: number; nickname: string; avatarUrl: string | null }
```

## 1. 咕嘟 Agent

| 方法 | 路径 | 请求 | 响应 data |
|---|---|---|---|
| POST | `/agent/sessions` | `{ title?: string }` | `{ sessionId: string }` |
| GET | `/agent/sessions` | `cursor,size` | `Page<{ sessionId, title, lastMessage, updatedAt, coverUrl: string\|null }>` |
| GET | `/agent/sessions/{id}/messages` | `cursor,size` | `Page<AgentMessage>`（倒序） |
| POST | `/agent/sessions/{id}/messages` | `{ text?: string, audioUrl?: string, context?: { page: string, dishId?: number, step?: number } }`（语音先 `POST /file/upload` 拿 URL；ASR 供应商未定，当前 Mock 转写） | **SSE**（`text/event-stream`） |
| POST | `/agent/actions/{actionId}/confirm` | — | `{ status: "DONE", result: object, undoToken: string \| null }` |
| POST | `/agent/actions/{actionId}/cancel` | — | `{ status: "CANCELLED" }` |
| POST | `/agent/actions/undo` | `{ undoToken }` | `{ status: "UNDONE" }` |
| POST | `/agent/messages/{messageId}/feedback` | `{ rating: 1 \| -1, comment?: string }` | `{}` |
| GET | `/agent/suggestions` | — | `{ greeting: string, tonight: DishCard \| null, expiring: { name, iconKey, daysLeft }[], prompts: string[] }` |

```ts
type AgentMessage = { messageId: string; role: "user" | "assistant"; text: string;
  tools: { name: string; label: string; summary: string; ms: number; ok: boolean }[];
  cards: AgentCard[]; actions: PendingAction[]; traceId: string | null; createdAt: string }
type AgentCard =
  | { type: "recipes"; items: DishCard[] }
  | { type: "menu"; menuId: number; name: string; date: string; dishCount: number; coverUrls: string[] }
  | { type: "pantry"; items: { name: string; iconKey: string|null; status: "ENOUGH"|"LOW"|"OUT"; daysLeft: number|null }[] }
  | { type: "plan"; draftId: number; range: string; days: number }
type PendingAction = { actionId: string; tool: "add_to_menu"|"remove_from_menu"|"add_to_shopping"|"mark_cooked"|"add_pantry";
  title: string; preview: object; args: object;      // args = 规范化后的原始工具参数（revise 以它为底）
  expiresAt: string; status: "PENDING"|"DONE"|"CANCELLED"|"EXPIRED"|"SUPERSEDED" }
// mark_cooked.preview = { dish: DishCard, servings: number, eaters: MemberLite[],
//   lines: { name, iconKey, delta: string, after: string, status: "USED_UP"|"LEFT"|"SKIP" }[] }
```

**SSE 事件**（`event:` 名 + `data:` JSON，每行一条）：

| event | data |
|---|---|
| `meta` | `{ messageId, traceId }` |
| `token` | `{ text }` |
| `tool_start` | `{ name, label }` |
| `tool_end` | `{ name, summary, ms, ok }` |
| `card` | `AgentCard` |
| `action` | `PendingAction` |
| `done` | `{ usage: { promptTokens, completionTokens, costFen }, degraded: boolean }` |
| `error` | `{ code, message, errorId }` |

后台：
- `GET /admin/agent/traces?cursor&size&rating=-1&status=` → `Page<{ traceId, user: UserLite, firstText, ms, status: "OK"|"DOWNVOTED"|"GUARDED"|"DEGRADED", createdAt }>`
- `GET /admin/agent/traces/{traceId}` → `{ trace, spans: { spanId, parentId, type: "guard"|"llm"|"tool"|"retrieval"|"action", name, startMs, ms, tokens, costFen, ok, input: object, output: object }[] }`
- `GET /admin/agent/stats?date=` → `{ turns, toolSuccessRate, p95FirstTokenMs, costFen, guardBlocks, byTool: { name, calls, successRate, p95Ms }[] }`
- `GET /admin/agent/eval/cases?intent=` / `POST /admin/agent/eval/cases`（可带 `fromTraceId`）
- `POST /admin/agent/eval/runs { promptVersion }` → `{ runId }`；`GET /admin/agent/eval/runs` → `{ runId, promptVersion, toolAccuracy, argAccuracy, pointCoverage, avgCostFen, p95Ms, passed, createdAt }[]`；`GET /admin/agent/eval/runs/{id}/failures`

## 2. 厨房圈

```ts
type PostCard = { postId: number; author: UserLite; title: string; coverUrl: string; coverRatio: number;
  imageCount: number; likeCount: number; liked: boolean; copyCount: number; tags: string[] }
type PostDetail = PostCard & { text: string; images: string[]; topics: { topicId, name }[];
  recipe: (DishCard & { copyCount: number }) | null; commentCount: number; collectCount: number;
  collected: boolean; following: boolean; createdAt: string; city: string | null;
  status: "PUBLISHED" | "REVIEWING" | "HIDDEN" }
type Comment = { commentId: number; author: UserLite; text: string; isAuthor: boolean; likeCount: number;
  createdAt: string; replies: Comment[] }
```

| 方法 | 路径 | 请求 | 响应 data |
|---|---|---|---|
| GET | `/community/feed` | `tab=recommend\|follow\|topic&topicId?&cursor&size` | `Page<PostCard>` |
| GET | `/community/banner` | — | `{ topic: Topic \| null, followingUpdates: UserLite[] }` |
| POST | `/community/posts` | `{ title, text, imageUrls: string[], dishId?: number, topicIds: number[], visibility: "PUBLIC"\|"FOLLOWERS", coverRatio?: number, fromCookRecordId?: number, fromFoodLogId?: number }` | `{ postId, status }` |
| GET | `/community/posts/{id}` | — | `PostDetail` |
| DELETE | `/community/posts/{id}` | — | `{}` |
| POST | `/community/posts/{id}/reactions` | `{ type: "LIKE"\|"COLLECT", on: boolean }` | `{ likeCount, collectCount, liked, collected }` |
| POST | `/community/posts/{id}/copy-recipe` | — | `{ dishId }` |
| GET | `/community/posts/{id}/copies` | `size` | `{ items: { user: UserLite, imageUrl: string }[] }` |
| GET/POST | `/community/posts/{id}/comments` | POST `{ text, parentId? }` | `Page<Comment>` / `Comment` |
| POST | `/community/users/{id}/follow` | `{ on: boolean }` | `{ following: boolean }` |
| GET | `/community/topics/{id}` | — | `Topic & { myProgress: { days: { date, done, coverUrl\|null }[], doneCount }, ranking: { user: UserLite, days, likeCount, rank }[], myRank: number\|null }` |
| POST | `/community/drafts/ai-text` | `{ dishId?, cookRecordId?, imageUrls?: string[], style?: "default"\|"casual"\|"short" }` | `{ text }` |
| POST | `/community/reports` | `{ postId, reason }` | `{}` |

`Topic = { topicId, name, intro, rule, startDate, endDate, participantCount, postCount, coverUrls: string[] }`（挑战周期 = 周一至周日）

后台：`GET /admin/community/queue?risk=HIGH|MEDIUM|LOW&cursor` → `Page<{ postId, coverUrl, title, author, risk, reasons: string[], reportCount, status, createdAt }>`；`GET /admin/community/stats`；`POST /admin/community/posts/{id}/moderate { action: "APPROVE"|"HIDE"|"BAN_7D", reason, note }`（写审计日志）。

## 3. 拍照识别

| 方法 | 路径 | 请求 | 响应 data |
|---|---|---|---|
| POST | `/vision/jobs` | `{ imageUrl: string, mode: "FRIDGE"\|"RECEIPT"\|"RECIPE" }`（只接受本站上传地址；`fileId` 不支持） | `{ jobId, status }` |
| GET | `/vision/jobs/{id}` | — | `VisionJob` |
| POST | `/vision/jobs/{id}/apply` | `{ items: { name, ingredientId?, amount, unit, bestBefore? }[] }` | `{ added: number }` |

```ts
type VisionJob = { jobId; mode; status: "PENDING"|"DONE"|"FAILED"; error?: string; imageExpiresAt: string;
  items: { name: string; rawName: string; ingredientId: number|null; iconKey: string|null;
    amount: number; unit: string; confidence: number; level: "AUTO"|"CONFIRM";
    alternatives: string[]; suggestedBestBefore: string|null }[];
  recipe?: { title, ingredients: IngredientLine[], steps: string[] } }   // RECIPE 模式
```
视觉供应商未定：`VisionProvider` 接口 + `MockVisionProvider`（默认，返回固定示例）+ OpenAI 兼容多模态实现（配置 `gudu.vision.provider`，默认 mock）。

## 4. 一周餐程与营养

- 周 = **周一至周日**；默认生成**下周一到周日**（今天是周一时生成本周）。

| 方法 | 路径 | 请求 | 响应 data |
|---|---|---|---|
| POST | `/mealplan/drafts` | `{ weekStart?: "2026-10-19", meals: ("LUNCH"\|"DINNER")[], servings, memberIds: number[], weekdayMaxMinutes?, budgetFen?, preferExpiring: boolean }` | `MealPlanDraft` |
| GET | `/mealplan/drafts/{id}` | — | `MealPlanDraft` |
| POST | `/mealplan/drafts/{id}/slots/{slotId}/alternatives` | — | `{ items: (DishCard & { sodiumMg })[] }` |
| PUT | `/mealplan/drafts/{id}/slots/{slotId}` | `{ dishId }` | `MealPlanDraft` |
| POST | `/mealplan/drafts/{id}/apply` | — | `{ menuIds: number[], shoppingItemCount: number }` |
| GET | `/nutrition/report` | `memberId?`（缺省 = 家里有健康档案的人，高血压 → 高血脂 → 宝宝辅食优先）, `range=week\|month` | `NutritionReport` |

```ts
type MealPlanDraft = { draftId; weekStart; weekEnd; constraints: string[];
  check: { passed: boolean; rounds: number; notes: string[] };
  days: { date; weekday: string; note: string|null;
    slots: { slotId; meal: "LUNCH"|"DINNER"; dish: DishCard; badges: ("EXPIRING"|"BABY")[] }[] }[];
  summary: { estCostFen; newItems; usesExpiring } }
type NutritionReport = { member: MemberLite & { profile: string }; range: { from, to }; recordCount;
  radar: { key: "energy"|"protein"|"fat"|"sodium"|"fiber"; label; actual; target; unit; ratio }[];
  alerts: { key; label; dailyAvg; limit; unit; sources: DishCard[]; suggestions: DishCard[] }[];
  trend: { key: "sodium"; points: { date, value }[]; limit };
  goods: string[] }
```

## 5. 跟做模式

纯客户端：步骤计时解析（「8 分钟」「3:00」「半小时」→ 秒）；问咕嘟复用 `POST /agent/sessions/{id}/messages`，带 `context: { page: "cook_mode", dishId, step }`。

## 6. 模型网关（后台配置与切换）

- 所有模型调用（Agent 对话/工具调用、餐程排布、文案生成、拍照识别、营养补全等）统一经 `ModelGateway`，按**能力**路由：`CHAT`、`TOOLS`、`VISION`。
- 默认（2026-10-16 调研 DeepSeek 官方文档并实测）：**CHAT / TOOLS / VISION 全部 → DeepSeek `deepseek-flash`**（DeepSeek-V4.1-Flash，2026-09-10 发布，原生多模态，输入 text+image，1M 上下文，支持 Tool Calls / JSON Output）；可选 `deepseek-v4-pro`（仅文本，更强推理，单价约 4.5 倍）。旧名 `deepseek-chat` 目前被路由到 deepseek-flash，配置统一改用 `deepseek-flash`。降级链 → GLM（如配置）→ Mock（规则）。key 初始种子取 `DEEPSEEK_API_KEY`。
- DeepSeek 调用要点：
  - **思考模式默认开启**：路由配置增加 `thinking: "disabled"|"low"|"high"|"max"`；对话/识别默认 `disabled`（实测图片识别 0.9s），餐程排布可用 `low`。开启思考且带 tools 时，历史轮次的 `reasoning_content` 必须原样回传。思考模式下 temperature 不生效。
  - **图片**：OpenAI 兼容 content 块 `{"type":"image_url","image_url":{"url": "data:image/jpeg;base64,..." 或公网 URL, "detail":"low"}}`；JPEG/PNG/GIF/WebP；公网 URL 需 60 秒内可下载、≤32MiB；每图 token 上限 1024（low 档 512×512 更省）。识别优先传 staging/生产图片公网 URL，失败退回 base64。
  - **JSON**：`response_format: {"type":"json_object"}`，提示词里给出字段结构。
  - **峰谷计价**：北京时间工作日 9–12、14–18 点为高峰（价格翻倍）；单价配置拆为 peak/offPeak，成本按调用时刻计算。
- 供应商 key 入库 AES-GCM 加密（密钥取环境变量 `GUDU_SECRET_KEY`，缺失时只允许环境变量来源的 key），接口只返回掩码；切换即时生效（Redis 广播失效本地缓存），每次切换写审计日志。
- 迁移编号：**V73 ai_gateway**（B1 组）。

| 方法 | 路径 | 请求 | 响应 data |
|---|---|---|---|
| GET | `/admin/ai/gateway` | — | `{ providers: Provider[], routes: Route[] }` |
| POST | `/admin/ai/gateway/providers` | `{ name, baseUrl, apiKey, enabled, models: Model[] }` | `Provider` |
| PUT | `/admin/ai/gateway/providers/{id}` | 同上（`apiKey` 留空 = 不修改） | `Provider` |
| DELETE | `/admin/ai/gateway/providers/{id}` | — | `{}`（被路由引用时 409） |
| POST | `/admin/ai/gateway/providers/{id}/test` | `{ model, capability }` | `{ ok, latencyMs, error: string\|null, sample: string\|null }` |
| PUT | `/admin/ai/gateway/routes/{capability}` | `{ primary: { providerId, model }, fallbacks: { providerId, model }[], timeoutMs, maxRetries, thinking }` | `Route` |
| GET | `/admin/ai/gateway/usage` | `range=today\|7d` | `{ byRoute: { capability, provider, model, calls, failRate, p95Ms, costFen }[] }` |

```ts
type Provider = { providerId: number; name: string; type: "OPENAI_COMPATIBLE" | "MOCK"; baseUrl: string;
  apiKeyMasked: string | null; keySource: "DB" | "ENV" | "NONE"; enabled: boolean; models: Model[];
  health: { ok: boolean; latencyMs: number | null; checkedAt: string | null } }
type Model = { model: string; capabilities: ("CHAT"|"TOOLS"|"VISION")[];
  price: { inputCacheHitFen: number; inputFen: number; outputFen: number };   // 每百万 token，高峰价；空闲 = 高峰 / 2
  peakHours: string | null }                                                  // 如 "Mon-Fri 09-12,14-18 Asia/Shanghai"
type Route = { capability: "CHAT"|"TOOLS"|"VISION"; primary: { providerId; model };
  fallbacks: { providerId; model }[]; timeoutMs: number; maxRetries: number;
  thinking: "disabled"|"low"|"high"|"max"; updatedAt: string; updatedBy: string }
```

## 7. 实现对齐（2026-10-16 三个后端分支合并 + 集成后，以本节为准）

> 前面各节是开发前约定；下面是实现与约定不同或新增的地方。客户端以本节为准。

**通用**
- `GET /member`：平台超管（后台）返回全部；App 侧普通成员只返回本家「成员档案」——本人 + 有健康档案的非超管成员，排除 `E2E` 前缀测试成员与厨房圈演示作者（`cm_` 账号）。「家里几口人」、营养报告成员列表直接用它。
- 社区身份 `userId` = 登录的 `member.id`（多家庭未落地）。

**1. 咕嘟 Agent**
- 工具 15 个（eval.md §7）+ `cancel_action`（用户说「算了」）；口头确认（「确认 / 好的」）记为内部 `confirm_action`，不对模型暴露。
- 新增 `POST /agent/actions/{actionId}/revise { args }` → 新 `PendingAction`（旧卡 `SUPERSEDED`）。`mark_cooked` 的 args 支持 `servings`、`eaters`（成员名）或 `eaterIds`、`overrides: {name, amount, unit?}[]` 或 `lines: {name, ingredientId?, status: USED_UP|LEFT|SKIP, amount?}[]`、`date`；未传字段沿用原 args。
- `plan` 卡片：`{ type: "plan", draftId, range, days, weekStart, items: {id, name, coverUrl}[] }`，`draftId` 是 `/mealplan/drafts/{id}` 的真实草稿（午晚两餐）。
- 后台：`GET /admin/agent/traces/{traceId}/messages`（完整对话）；trace 详情带 `firstTokenMs, tokens, costFen, promptVersion, model, phoneMasked, feedback`；评测 `GET /admin/agent/eval/cases` → `{ total, byIntent: {intent, count, testCount}[], items }`，runs 带 `byCategory: {category, score, baseline}[]` 与 `hardGateFailures: {gate: "confirm"|"safety", label, failed, total, caseIds}[]`，failures 项 `{ caseId, intent, input, expectedTools, actualTools, note, newSinceBaseline }`。
- 回复菜名一致性：回复里提到的菜名不在本轮卡片 / 动作 / 工具结果 / 用户原话里时，调用链记一条 `guard.dish_mention` span（只观测、不拦截）；提示词 `agent-v2` 规则 10、11。
- `/agent/suggestions` 的 `tonight` 与 `recommend_tonight`：有封面且有步骤的完整菜谱优先，用到越快到期食材的越靠前。

**2. 厨房圈**
- 表名统一 `community_` 前缀；帖子图片存 JSON 列（无 `post_image` 表）；新增 `community_appeal`、`community_ban`、`community_moderation_log`。
- `PostCard` / 详情新增 `coverRatio`（宽高比 0.5–2，瀑布流用）；`Topic` 新增 `weekNo`，挑战话题的 `startDate` / `endDate` 为本周周一 / 周日。
- 新增 `GET /community/topics?status=ACTIVE|ALL&size=`；`POST /community/posts/{id}/appeal { text }`（被隐藏帖作者，一次）。
- 发帖 `fromFoodLogId`（= 食记列表 / 详情新增的 `foodLogId`，一顿饭的代表做菜记录 id）：带入整顿饭的菜与最多 9 张图，不带成员名。
- 站外图片：不整帖拒绝，拦下不展示、记入 `blockedImages`，整帖判 HIGH 进人工审核；只有站外图片时拒绝发布。
- 预审：规则先行，规则判 HIGH 直接定论；其余经网关 CHAT 判定（3 秒超时退回规则），风险取两者较高。「AI 帮写」经网关 CHAT，失败退回模板。
- `Comment.likeCount` 恒为 0（暂无评论点赞接口）。
- 后台：`GET /admin/community/queue?status=PENDING|DONE|APPEALED&risk=`，队列项另有 `aiScores, aiSummary, reports, moderation, appeal, authorHistory`；`GET /admin/community/stats` → `{ newPosts, fromCookLog, autoPassRate, pending, pendingHigh, pendingMedium, reports, autoHidden, avgHandleMinutes, appealed }`；`GET /admin/community/posts/{id}`（管理员视角，含 `blockedImages`、`logs`）。

**3. 拍照识别**
- 经网关 VISION（默认 deepseek-flash）；本站上传图读成 base64 发给模型。真实模型全部失败或输出无法解析时退回示例识别，`VisionJob.degraded = true`（客户端提示「示例结果，请核对」）；用了后备模型同样标记。
- `apply`：余量是三档模型，`amount` / `unit` 不写入，入库记为「充足」，有 `bestBefore` 则登记保质期。

**4. 一周餐程与营养**
- 排布经网关 CHAT（`thinking: low`，JSON）→ 结果只接受候选池内、本周未用、时长合规的菜，某天不完整整天交启发式补；失败整体退回启发式；之后照常规则校验回环（≤2 轮）。校验新增「每餐至少一道正菜」（粥 / 馒头 / 甜品不单独成餐）。
- `apply` 生成的每日食集用名称表示日期（如「10.19 周一 · 一周餐程」），采购清单名「一周餐程 10.19–10.25」（食集表无日期列）。
- 「用掉快到期」只计保质期落在餐程那一周内的食材。
- 出参多出：`DishCard.sodiumMg`、`MealPlanDraft.status`（DRAFT / APPLIED）、`radar[]` / `alerts[]` 的 `label`、`unit`。钠与膳食纤维为估算值；多家庭落地前报告按全家做饭记录计。

