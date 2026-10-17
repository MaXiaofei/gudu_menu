# Delta: Meal Plan & Nutrition（一周餐程与营养报告）

**Change ID:** `showcase-ai-community`

## ADDED

### Requirement: 一周从周一开始
#### Scenario: 周五生成
- GIVEN 今天是 2026-10-16（周五）
- WHEN 用户生成一周餐程且未指定起始日
- THEN 草稿范围为 2026-10-19（周一）至 2026-10-25（周日）

### Requirement: 餐程满足成员档案约束
#### Scenario: 家有高血压成员
- GIVEN 家庭成员「妈妈」档案为高血压
- WHEN 生成一周餐程
- THEN 每日估算钠摄入不超过档案阈值；超出的日程被回炉重排，最多 2 轮，仍不满足时在该日标注提醒

### Requirement: 单格可替换且确认后批量生成
#### Scenario: 换一道
- GIVEN 草稿周三晚餐为红烧肉
- WHEN 用户点「换一道」
- THEN 仅替换该格，新菜满足同样约束且本周不重复
- WHEN 用户点「生成食集和采购清单」
- THEN 按天生成食集，采购清单合并同类食材并扣除余量已有部分

### Requirement: 营养报告按成员对照阈值
#### Scenario: 周报告
- GIVEN 过去 7 天有食记
- WHEN 查看妈妈的周报告
- THEN 展示五项营养素摄入与阈值对比；超标项给出替换建议
