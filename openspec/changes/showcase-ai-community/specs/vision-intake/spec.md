# Delta: Vision Intake（拍照识别）

**Change ID:** `showcase-ai-community`

## ADDED

### Requirement: 识别结果按置信度分级并需确认入库
#### Scenario: 冰箱照片
- GIVEN 用户以「冰箱」模式上传照片
- WHEN 识别完成
- THEN 置信度 ≥ 0.8 的食材默认勾选，0.5–0.8 标黄待确认，< 0.5 不展示；用户点「放进余量」前不写库

### Requirement: 识别名称对齐食材字典
#### Scenario: 别名
- GIVEN 模型返回「西红柿」
- WHEN 对齐字典
- THEN 关联到食材「番茄」，并带出插画 iconKey 与默认保质期

### Requirement: 原图短期保留
#### Scenario: 清理
- GIVEN 识别任务完成已超过 24 小时
- WHEN 清理任务执行
- THEN 原图被删除，结构化结果保留至 7 天
