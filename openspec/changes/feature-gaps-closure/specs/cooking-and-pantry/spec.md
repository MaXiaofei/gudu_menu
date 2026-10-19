# Delta: Cooking & Pantry（做菜与余量）

**Change ID:** `feature-gaps-closure`

## ADDED

### Requirement: 单菜直做
用户可不建食集直接做一道菜，做完记入食记并按确认回写余量。

#### Scenario: 单菜做完
- GIVEN 本家有菜谱「番茄炒蛋」，库存 鸡蛋=够、番茄=低
- WHEN `POST /cook/dish/{id}`，确认 番茄「用完」、鸡蛋「用了一部分」
- THEN 生成 cooking_record；番茄档位变为缺；鸡蛋档位按规则下调；`/cookbook/recent` 首条为该菜

### Requirement: 余量带保质期并可查询临期
三档库存可记录保质期，临期查询基于三档库存。

#### Scenario: 临期查询
- GIVEN 牛奶 best_before = 今天+2
- WHEN `GET /pantry/expiring?days=3`
- THEN 结果包含牛奶及剩余天数 2

### Requirement: 菜谱余量覆盖率
菜谱列表返回本家余量对该菜用料的覆盖情况，并支持按覆盖率排序。

#### Scenario: 覆盖率
- GIVEN 菜谱用料 4 样，本家够或低的有 3 样
- WHEN `GET /dish/search`
- THEN 该菜 `coverage = {enough: 3, total: 4, missing: [缺的那样]}`

### Requirement: 首页智荐
首页聚合临期主推、你常做与季节鲜。

#### Scenario: 有临期食材
- GIVEN 本家有 3 天内到期的菠菜
- WHEN `GET /recommend/home`
- THEN `expiringPicks` 至少包含一道用到菠菜且对本家可见的菜，按覆盖率降序
