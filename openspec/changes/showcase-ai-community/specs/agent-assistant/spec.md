# Delta: Agent Assistant（咕嘟 Agent）

**Change ID:** `showcase-ai-community`

## ADDED

### Requirement: 工具只调用领域服务并校验家庭归属
Agent 的每个工具都是领域 AppService 的薄适配层，参数在服务端按当前家庭二次校验。

#### Scenario: 越权菜单
- GIVEN 用户属于家庭 A，模型生成的 `add_to_menu` 参数中 menuId 属于家庭 B
- WHEN 编排器执行该工具
- THEN 工具返回「无权访问」错误给模型；不产生 PendingAction；agent_span 记录 guard 拒绝

### Requirement: 写操作必须经用户确认
写类工具只生成待确认动作，用户确认后才落库，执行结果可撤销。

#### Scenario: 一句话做完回写
- GIVEN 用户说「麻婆豆腐做完了，三个人吃的」
- WHEN Agent 调用 `mark_cooked`
- THEN 客户端收到 `action` 事件，展示用料去向预览（北豆腐 -1、肉末 -100g）与确认按钮；余量未变化
- WHEN 用户点确认
- THEN 生成做饭记录并回写余量；返回 undoToken；5 分钟内可撤销

#### Scenario: 确认过期
- GIVEN PendingAction 创建已超过 5 分钟
- WHEN 用户点确认
- THEN 返回「已过期，请重新发起」，不执行任何写操作

### Requirement: 流式事件协议
对话以 SSE 返回，事件类型为 token / tool_start / tool_end / card / action / done / error。

#### Scenario: 查冰箱能做什么
- GIVEN 余量中有 29 样食材，其中 4 样 3 天内到期
- WHEN 用户发送「冰箱里的能做什么」
- THEN 依次收到 tool_start(get_pantry)、tool_end(get_pantry, 摘要「29 样 · 4 样快到期」)、tool_start(search_recipes)、card(recipe × 3)、token…、done(traceId)

### Requirement: 配额与降级
超出每日对话配额或模型不可用时降级为规则意图，不报错空白。

#### Scenario: 主模型超时
- GIVEN DeepSeek 调用超时
- WHEN 编排器重试失败
- THEN 切换降级模型；仍失败则按规则意图返回快捷操作卡，并在 done 事件标记 degraded=true
