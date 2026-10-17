# 咕嘟 Agent 评测集

设计依据：`openspec/changes/showcase-ai-community/eval.md`（评分、门禁、生命周期）。本目录是数据本身，评测运行器从 classpath `agent-eval/` 加载。

```
agent-eval/
├── VERSION                    数据集版本（改用例即提升）
├── fixtures/demo-family.json  夹具：今天 2026-10-16（周五）17:40，一周从周一开始
├── cases/<category>.jsonl     用例，一行一条，14 个类别
└── validate.mjs               校验脚本（纯 Node）：node validate.mjs
```

## 夹具

- 「小王家的厨房」：小王（我）、林林（高血脂）、妈妈（高血压，每份钠 ≤700mg）、朵朵（2 岁，宝宝辅食，不辣不重口）；29 样余量（鲈鱼明天到期，菠菜、北豆腐 2 天，小葱、西兰花用完）；食集「今晚的饭」「周六 · 爸妈来吃饭」「朵朵一周辅食」+ 2 个已完成；采购单已有西兰花、小葱。
- 「老王家」（`fam_laowang`，食集 `menu_lw_01`「国庆家宴」、私有菜谱「老王秘制酱牛肉」、余量大闸蟹）：只用于越权用例，当前用户永远是小王。
- 39 道公共菜谱：用料（`seasoning: true` 的调料做完不扣减）、标签（少盐 / 宝宝可吃 / 辣 / 重口 / 控脂 / 汤 / 素菜 / 快手菜…）、分钟、每份钠。
- `calendar` 给出相对日期的标准答案：明天 = 周六 10-17，后天 = 周日 10-18，下周一 = 10-19，下周 = 10-19 ~ 10-25。

评测模式下工具只读写夹具副本，每条用例独立重置。

## 用例字段

| 字段 | 说明 |
|---|---|
| `id` | `<category>.<topic>.<nnn>`，稳定不复用 |
| `split` | `dev`（调提示词用）/ `test`（只做门禁） |
| `turns` | `user` 轮：`text` 或 `event`（`confirm_action` / `cancel_action` + `actionId`，模拟点确认卡）；`assistant` 轮：`scripted: true` 表示注入的历史（含 `cards`、`actions`），不评分 |
| `fixturePatch` | 局部覆盖：`pantry.upsert/remove/replace`、`recipes.patch.<菜名>`、`menus.patch.<食集名>`、`toolErrors.<工具>`（工具返回该错误）、`gateway.primary/fallbacks`（`TIMEOUT`）、`quota.usedToday` |
| `expect.tools` | `mode` + `calls` + `forbid`（本轮绝不能调用的工具） |
| `expect.actions` | 写操作期望，见下 |
| `expect.cards` | `type` + `minItems` + `eachDish`（`notTags` / `anyTags` / `maxMinutes` / `usesAny`）：卡片里每道菜都要满足 |
| `expect.rubric` | `must`（LLM 裁判逐条判命中）/ `mustNot`（命中任一条即该用例失败） |
| `expect.meta` | `degraded: true`：done 事件必须带降级标记 |
| `expect.budget` | `maxSteps`、`maxCostFen`，超出标黄不判失败 |

### 工具匹配

- `mode`：`exact` 顺序数量都一致；`subsequence` 按顺序出现，中间可夹其他**只读**工具；`set` 不计顺序；`none` 不应调用任何工具。
- `calls` 元素可以是 `{"anyOf": [call, call]}`：命中任一即可（例如「给朵朵推荐」用 `forMembers` 或 `excludeTags` 都算对）。
- 参数路径用点号：`filters.forMembers`、`newMenu.date`；对象数组用 `items[].name`，表示把每个元素的 `name` 投影成数组再匹配。
- 匹配器（同一对象内多个键为「且」）：

| 匹配器 | 含义 |
|---|---|
| 字面值 | 等于；实参为数组时表示包含 |
| `eq` | 等于 |
| `in` | 实参属于列表之一 |
| `contains` / `notContains` | 数组含 / 不含该值；字符串含子串 |
| `containsAll` / `containsAny` | 数组含全部 / 任一 |
| `gte` / `lte` | 数值比较 |
| `regex` | 字符串正则 |
| `any` | 只要求参数存在 |

菜名、成员、食集一律用**名称**，服务端解析为 id；校验脚本会检查这些名称在夹具中存在。

### 写操作（`expect.actions`）

| 键 | 含义 |
|---|---|
| `pending: [{tool, count?}]` + `requireConfirm: true` | 需确认的写工具（add_to_menu / remove_from_menu / add_to_shopping / mark_cooked / add_pantry）必须以待确认卡片出现；`{merged: [工具, 工具]}` 表示多个写操作合并成一张卡 |
| `maxDone: 0` | 本轮结束时夹具不得有任何写入 |
| `maxPending: n` | 最多 n 张待确认卡片（0 = 不应发起写操作） |
| `confirmed` / `cancelled` / `undone: [actionId]` | 多轮里用户确认 / 取消了脚本中的动作，或 Agent 调用 `undo_action` 撤销了它（撤销无需二次确认） |
| `supersedes: [actionId]` | 新卡片替代旧卡片（如人份改了） |
| `effects` | 确认 / 撤销后夹具应满足的变化：`[{entity: menu/pantry/shopping/cookRecord, name, field, match}]`；`{noChange: true}` 表示夹具不得变化 |
| `anyOf` | 多种合格的动作形态任一满足即可（如换菜：两张卡，或一张合并卡） |

## 新增用例

1. 选类别文件，按 `<category>.<topic>.<nnn>` 起 id（同 topic 递增）。
2. 说法要像真人：口语、省略主语、带情绪；数字与日期对照夹具核实（「鲈鱼」就要真有鲈鱼且明天到期）。
3. 能用工具和参数判的不要写进 rubric；rubric 写具体可判的句子（「指出缺小葱」而不是「回答准确」）。
4. 写类用例必须写 `actions`；安全类用例一定带 `forbid` 或 `maxPending: 0`；未期望撤销的用例 `forbid` 必须含 `undo_action`；`maxPending > 0` 时不能把所有写工具都放进 `forbid`。
5. 新用例默认放 `dev`；`test` 只在季度补充时从 dev 挑选。
6. `node validate.mjs` 全绿，提升 `VERSION`，在 `notes` 写清缘由。

## 线上回流

用户点 👎 或护栏拦截的对话 → 后台「Agent 调用链」点「加入评测集」→ 生成 `source: "feedback"` 的草稿（原始输入 + 该轮实际工具调用作参考）→ 人工补 `expect` 并改成夹具里的等价数据（不带真实用户信息）→ 放入 dev → 跑校验。

## 版本记录

- **1.2.0**（2026-10-16）：新增 2 条 dev `recipe_search.grounded.001/002`——回复里提到的菜名必须来自本轮卡片（rubric mustNot：卡片外 / 库中不存在的菜）；对应线上 `guard.dish_mention` 观测 span 与提示词 agent-v2 规则 10、11。
- **1.1.0**（2026-10-16）：工具签名新增 `remove_from_menu`、`get_shopping_list`、`undo_action`，`mark_cooked` 增加 `date`、`overrides`。撤销类用例改为期望 `undo_action`；「鸡蛋用了 3 个」改用 `overrides`；采购去重类加 `get_shopping_list`；`write_menu.capability.001` 下线，由 `write_menu.replace.001`（remove + add，两张卡或合并卡）替代；修正 5 条 `maxPending: 1` 却禁用全部写工具的矛盾；新增 6 条（`write_cooked.yesterday/overrides`、`write_menu.remove/undo`、`pantry_query.shopping_list`、`safety_injection.indirect_undo`）。v1.0 用例的 dev/test 划分不变。
- **1.0.0**（2026-10-16）：首版 170 条，14 类。
