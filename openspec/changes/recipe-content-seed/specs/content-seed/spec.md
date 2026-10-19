# Delta: Content Seed（内容填充）

**Change ID:** `recipe-content-seed`

## ADDED

### Requirement: 种子脚本可重复执行且不破坏引用
以 `source_url` 为自然键 upsert 公共菜谱，重复执行不改变已有 dish.id。

#### Scenario: 二次执行
- GIVEN staging 已执行过一次种子脚本，且某导入菜谱被一个食集引用
- WHEN 再次执行 `node scripts/seed/recipes.mjs --env=staging`
- THEN 报告新增 0 条；该菜谱 id 不变；食集仍能打开该菜谱详情

### Requirement: 种子脚本拒绝写入生产
导入的第三方内容只能进入测试/本地环境。

#### Scenario: 指向生产
- GIVEN 连接参数解析为生产库
- WHEN 执行种子脚本
- THEN 进程以非 0 退出码结束，且未执行任何写操作

### Requirement: 不新建字典词条
分类与标签只映射到现有字典；未映射项丢弃并出现在报告中。

#### Scenario: 未映射分类
- GIVEN 源数据分类「电饭煲」不在映射表
- WHEN 执行种子脚本
- THEN sys_dict 行数不变；报告的「未映射分类」包含「电饭煲」

### Requirement: 用量解析
用量文本解析为数量 + 字典单位。

#### Scenario: 分数与区间
- WHEN 解析「1/2 小匙」与「2-3 根」
- THEN 分别得到 (0.5, 小匙) 与 (3, 根)

### Requirement: 演示家庭
staging 提供与原型一致的演示家庭，可一键重建。

#### Scenario: 打开演示账号
- GIVEN 已执行 `demo-family.mjs --reset`
- WHEN 用演示账号登录任一端
- THEN 智荐「快到期的」至少 3 样；菜谱列表前 5 道均有封面与覆盖率；食记近 7 天非空
