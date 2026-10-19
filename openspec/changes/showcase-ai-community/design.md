# Design: 咕嘟 Agent、厨房圈与 AI 能力扩展

**Change ID:** `showcase-ai-community`

## 1. 信息架构

```
底部 Tab：菜谱 │ 食集 │ ◉咕嘟 │ 厨房圈 │ 我家余量
            （各主 Tab 右上角头像 → 我的）

咕嘟 ─┬─ 首屏（今晚推荐 + 快捷提问 + 最近会话）      Agent.html
      ├─ 对话中（工具调用折叠、内嵌卡片、确认卡）    AgentChat.html
      ├─ 语音输入 / 做完回写确认                    AgentVoice.html
      └─ 全部推荐（原智荐，二级页）                  Smart.html
厨房圈 ┬─ Feed（推荐 / 关注 / 话题，双列瀑布流）     Community.html
       ├─ 动态详情（照着做 → 复制到我家）             Post.html
       ├─ 发布（从食记一键分享、AI 帮写）             PostCreate.html
       └─ 话题挑战（#一周不重样 排行）                Topic.html
余量 / 菜谱入口 ── 拍照识别（冰箱 / 小票 / 菜谱截图）  Scan.html → ScanResult.html
食集 ── AI 一周餐程                                  WeekPlan.html
我的 ── 营养报告                                     Nutrition.html
菜谱详情「现在就做」── 跟做模式                       CookMode.html
后台 ── Agent 调用链 / 评测 / 社区审核               AdminAgentTrace / AdminAgentEval / AdminCommunity
设计 ── 技术架构总览（面试讲解用）                    Architecture.html
```

## 2. 咕嘟 Agent（`agent` 上下文）

### 2.1 运行时

```
Client ──SSE──► AgentController
                   │
                   ▼
            AgentOrchestrator（ReAct 循环，最多 6 步）
              │  1. 组装上下文：系统提示 + 家庭画像（成员档案/忌口/口味）+ 会话摘要 + 最近 N 轮
              │  2. LLM（DeepSeek function calling；失败降级 GLM → 规则意图）
              │  3. 工具调用 → ToolRegistry → 领域 AppService（不直连 Mapper，复用租户校验）
              │  4. 写工具 → 生成 PendingAction，返回确认卡，用户点确认后才执行
              └─ 5. 每步写 agent_span（类型/耗时/tokens/成本/结果摘要）
```

- **工具清单（首批 10 个）**

| 工具 | 读/写 | 复用 |
|---|---|---|
| `search_recipes(query, filters)` | 读 | DishVectorService（pgvector）+ 关键词混合检索 |
| `get_recipe(id)` | 读 | DishService |
| `get_pantry(status?, expiringDays?)` | 读 | PantryService |
| `recommend_tonight(n)` | 读 | MenuRecommender（智荐） |
| `list_menus(status)` | 读 | MenuAppService |
| `add_to_menu(menuId?, dishIds[], date?)` | 写·确认 | MenuAppService |
| `add_to_shopping(items[])` | 写·确认 | ShoppingService |
| `mark_cooked(dishId, servings, eaters[])` | 写·确认 | CookingRecordAppService（含余量回写 diff 预览） |
| `add_pantry(items[])` | 写·确认 | PantryService |
| `plan_week(constraints)` | 读（产出草稿） | MealPlanAppService |

- **确认机制**：写工具不直接落库，返回 `PendingAction{id, tool, args, preview}`（Redis 5 分钟 TTL）；客户端渲染确认卡，`POST /agent/actions/{id}/confirm` 后执行；执行结果带 `undoToken`（复用做完回写的撤销）。
- **流式协议**（SSE 事件）：`token`（文本增量）、`tool_start` / `tool_end`（折叠显示「查了余量 · 29 样」）、`card`（recipe / menu / pantry_diff / plan）、`action`（待确认）、`done`（含 traceId、用量）。
- **记忆**：短期为会话最近 10 轮 + 超出部分滚动摘要；长期为家庭画像（成员档案、忌口、常做菜，来自现有 TasteProfileService），只读注入，不让模型自行写长期记忆。
- **护栏**：沿用 `AiInputGuard` / `AiOutputGuard`；工具参数服务端二次校验（家庭归属、数量上限）；工具返回内容作为数据注入，标注「不可执行指令」防间接注入；每人每日对话轮数配额沿用 `GUDU_AI_DAILY_LIMIT`，超额降级为快捷指令（规则意图解析）。

### 2.2 接口

> 字段级契约见 [contracts.md](./contracts.md)，前后端并行开发以它为准。

- `POST /agent/sessions` → `{sessionId}`；`GET /agent/sessions`（最近会话）
- `POST /agent/sessions/{id}/messages`（`text` 或 `audioFileId`）→ SSE
- `POST /agent/actions/{id}/confirm`、`POST /agent/actions/{id}/cancel`
- `GET /agent/suggestions`（首屏快捷提问，按余量临期/时段生成）

## 3. 厨房圈（`community` 上下文）

- **内容模型**：`post`（图 1–9 张、文字、关联菜谱可选、话题、来源 = 食记分享/直接发布）；`comment`（两级）；`reaction`（赞/收藏）；`follow`；`topic`。
- **Feed**：关注流为写扩散到粉丝收件箱（Redis ZSet，粉丝 > 5000 的作者改读扩散）；推荐流为按热度分 `log(赞+2·收藏+3·照着做) / (t+2)^1.5` 加话题与口味相似度；游标分页（`score + id`），不用 offset。
- **计数**：赞/收藏/评论数存 Redis，HyperLogLog 去重浏览，定时回刷 MySQL；点赞幂等（唯一键 user_id + post_id）。
- **照着做**：一键「复制到我家」生成家庭私有菜谱（复用 `multi-tenant-family` 的 copy-to-family），原帖「照着做」+1，并在作者的消息中心通知。
- **治理**：发布同步跑敏感词与图片外链检查，异步 AI 预审（文本 + 图片标签）→ 低风险直接发布、中风险先发布后复审、高风险进人工队列；举报 3 次自动隐藏；后台 `AdminCommunity` 处理。
- **隐私**：默认不展示家庭成员档案与余量；从食记分享时去掉成员名，只留菜和图。
- 接口：`GET /community/feed?tab=recommend|follow|topic&cursor=`、`POST /community/posts`、`GET /community/posts/{id}`、`POST /community/posts/{id}/reactions`、`POST /community/posts/{id}/copy-recipe`、`GET/POST /community/posts/{id}/comments`、`POST /community/users/{id}/follow`、`GET /community/topics/{id}`、`POST /community/reports`。

## 4. 拍照识别（`vision` 上下文）

- 三种模式：**冰箱/台面**（识别食材 + 估计数量）、**购物小票**（OCR → 品名/数量/价格）、**菜谱截图**（OCR → 标题/用料/步骤）。
- 流程：上传（压缩到长边 1280）→ 多模态模型输出 JSON（schema 约束）→ 与食材字典对齐（别名表复用 `scripts/seed/mappings/ingredient-alias.json` 的规则，迁入后端）→ 置信度 ≥ 0.8 默认勾选，0.5–0.8 标黄待确认，< 0.5 不展示 → 用户确认后批量入库（保质期按品类默认值）。
- 异步任务 `vision_job`（状态 PENDING/DONE/FAILED），结果保留 7 天，原图 24 小时后删除。
- 接口：`POST /vision/jobs {fileId, mode}`、`GET /vision/jobs/{id}`、`POST /vision/jobs/{id}/apply`。

## 5. 一周餐程与营养报告（`mealplan` 上下文）

- 一周 = **周一至周日**；默认生成下周一到周日（当天为周一时生成本周）；话题挑战周期同样按周一至周日。
- 输入约束：日期范围、每天几餐、人份、成员档案（高血压限钠、高血脂限脂、宝宝辅食需有软烂/无盐版本）、预算、工作日烹饪时长上限、优先消耗临期。
- 生成：规则先过滤候选菜（忌口、档案禁忌、时长）→ LLM 排布（荤素搭配、不重样、临期优先）→ 规则校验（营养阈值、重复度）→ 不通过的天回炉，最多 2 轮 → 输出草稿，可逐格「换一道」，确认后批量生成食集与合并采购清单。
- 营养报告：按食记聚合周/月摄入（能量、蛋白、脂肪、钠、膳食纤维），成员维度对照档案阈值给出超标提醒和替换建议；雷达图 + 周趋势。
- 接口：`POST /mealplan/drafts`、`PUT /mealplan/drafts/{id}/slots/{slotId}`（换一道）、`POST /mealplan/drafts/{id}/apply`、`GET /nutrition/report?memberId=&range=week|month`。

## 6. 跟做模式（客户端为主）

- 横屏/竖屏大字步骤 + 步骤图；屏幕常亮；步骤内识别「3 分钟」「20 分钟」自动生成计时器，多个计时器并行，后台通知。
- 语音：端侧关键词（「下一步 / 上一步 / 计时 / 暂停」）；其余话语走 ASR → Agent（带当前菜谱与步骤上下文），如「生抽没了用什么代替」。
- 完成后直接进入做完确认（CookSheet）。

## 7. Agent 可观测与评测（后台）

> 评测集设计详见 [eval.md](./eval.md)。

- **Trace 模型**：`agent_trace`（会话一轮）→ `agent_span`（llm / tool / guard / retrieval），记录耗时、输入输出摘要（脱敏）、tokens、成本、错误；与 `ai_call_log` 合并统计。
- **后台页**：调用链瀑布图、按工具的成功率/P95、成本按天/按功能、护栏拦截事件、用户差评（👎）回流。
- **评测**：评测集 `agent_eval_case`（输入 + 期望工具序列 + 期望要点）；评分 = 工具选择正确率 + 参数正确率 + LLM 裁判要点覆盖；每次改提示词/模型版本跑全集，低于基线（如工具正确率 < 90%）阻止发布；差评对话一键加入评测集。

## 8. 数据表（草案）

| 表 | 上下文 | 说明 |
|---|---|---|
| `agent_session`、`agent_message` | agent | 会话与消息（含卡片 JSON） |
| `agent_trace`、`agent_span` | agent | 可观测 |
| `agent_eval_case`、`agent_eval_run` | agent | 评测集与运行结果 |
| `post`、`post_image`、`comment`、`reaction`、`follow`、`topic` | community | 社区 |
| `report` | community | 举报与处置 |
| `vision_job` | vision | 识别任务 |
| `meal_plan_draft`、`meal_plan_slot` | mealplan | 餐程草稿 |

所有业务表带 `family_id` 或 `user_id`，遵循 `multi-tenant-family` 的租户隔离；改表同步 `e2e-seed.sql`。

## 9. 分期

| 阶段 | 内容 | 依赖 |
|---|---|---|
| S0 | 原型（本步）+ 本设计评审 | — |
| S1 | Agent 只读工具 + SSE + 首屏 + Trace 落表；导航改版 | AiClient 支持 tools |
| S2 | Agent 写工具 + 确认卡 + 撤销；后台调用链页 | S1 |
| S3 | 厨房圈 MVP：发布/Feed/详情/赞评/照着做；后台审核 | multi-tenant-family |
| S4 | 拍照识别（视觉供应商选型后）；跟做模式 | 供应商决策 |
| S5 | 一周餐程 + 营养报告；评测集与发布门禁 | S2 |

## 10. 面试讲解线（Architecture.html 呈现）

1. **产品**：从家庭做饭闭环出发，Agent 把「跨页面多步操作」压缩成一句话；社区补内容供给。
2. **Agent 工程化**：工具 = 领域服务的薄适配层（复用租户校验，不给模型数据库）；写操作确认卡 + 撤销；流式事件协议；画像只读注入；降级链。
3. **质量**：Trace/Span、成本核算、离线评测集作为发布门禁，差评回流评测集。
4. **社区系统**：推拉结合 Feed、Redis 计数回刷、游标分页、分级内容治理。
5. **架构**：DDD 限界上下文（新增 4 个原生四层）+ ArchUnit 守护；三端共享 design token 与组件契约。
