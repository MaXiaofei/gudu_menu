# Delta: Agent Observability（Agent 可观测与评测）

**Change ID:** `showcase-ai-community`

## ADDED

### Requirement: 每轮对话可追溯
每轮对话生成一条 trace，每次模型调用、检索、工具调用、护栏判定为一个 span，记录耗时、tokens、成本与脱敏摘要。

#### Scenario: 后台查看
- GIVEN 用户反馈某次回答错误并附报错编号
- WHEN 运营在后台按 traceId 搜索
- THEN 看到该轮的调用链瀑布图与每个 span 的输入输出摘要（手机号等已脱敏）

### Requirement: 评测集作为发布门禁
#### Scenario: 提示词改动
- GIVEN 修改了系统提示词
- WHEN 跑评测集
- THEN 工具选择正确率低于基线（90%）时标记为不可发布，并列出退化用例

### Requirement: 差评回流
#### Scenario: 用户点踩
- GIVEN 用户对某条回答点踩
- WHEN 运营在后台点「加入评测集」
- THEN 生成一条评测用例，包含原始输入与该轮工具调用作为参考
