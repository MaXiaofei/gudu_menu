# 采购清单三色余色 Implementation Plan (Plan B)

> 承接 spec §7 gap ③ + 原型 `menu-detail-caigou-v2.html`。Plan A（做菜扣库存链）已落地，本计划是其下游闭环。

## 目标

采购清单每项标三色余量，按 `ingredientId` 比对 pantry 总克数 vs `referenceGrams`：

| 色 | 状态 | 判定 | shortageGrams |
|---|---|---|---|
| 🔴 | `RED_NONE` 没有 | pantryGrams null/0 | = needGrams |
| 🟡 | `YELLOW_SHORT` 差 X | pantryGrams < needGrams | = needGrams − pantryGrams |
| 🟢 | `GREEN_ENOUGH` 够 | pantryGrams >= needGrams | = 0 |

**不标记**（`stockStatus=null`，前端标灰「手动加」）：customName 手动加项（`ingredientId=null`）或无用量（`referenceGrams` null/0）。

**铁律**：采购不减库存（只读 pantry）。够的可删（`deleteItem` 已存在，前端用）。

## 架构

纯函数 → Service 编排 → VO，与 Plan A 同构：

```
ShoppingService.getDetail
  → fillVoNames(rows)
    → pantryMapper.selectList(ingredient_id IN (...))   // 批量，一次查
    → Java 按 ingredientId sum grams                     → Map<ingId, grams>
    → 每项 StockClassifier.classify(referenceGrams, pantryGrams)
    → VO 填 pantryGrams / stockStatus / shortageGrams
```

## 文件结构

| 文件 | 动作 | 说明 |
|---|---|---|
| `shopping/StockClassifier.java` | new | 纯函数 `classify(need, have)` → `Result(status, shortage)` |
| `shopping/StockClassifierTest.java` | new | 6 用例纯函数 |
| `shopping/ShoppingItemVO.java` | mod | `+pantryGrams / +stockStatus / +shortageGrams` |
| `shopping/ShoppingService.java` | mod | `+PantryMapper` 依赖；`fillVoNames` 加三色比对 |
| `shopping/ShoppingServiceTest.java` | mod | `+getDetail` 三色测试（mock pantryMapper） |

## Tasks（TDD）

### T1：StockClassifier 纯函数 + 6 单测
- `classify(null, *)` → null（无用量不标记）
- `classify(0, *)` → null
- `classify(100, null)` → RED_NONE, shortage=100
- `classify(100, 0)` → RED_NONE, shortage=100
- `classify(100, 30)` → YELLOW_SHORT, shortage=70
- `classify(100, 100)` → GREEN_ENOUGH, shortage=0（刚好）
- `classify(100, 150)` → GREEN_ENOUGH, shortage=0

### T2：ShoppingItemVO 加字段 + ShoppingService 三色 + getDetail 测试
- VO 加 3 字段（pantryGrams / stockStatus / shortageGrams）
- ShoppingService 构造加 `PantryMapper`（第 10 个依赖）
- `fillVoNames`：收集非 null ingredientId → 批量查 pantry → sum grams → classify 填 VO
- ShoppingServiceTest 加 1 个 getDetail 三色测试（RED+YELLOW+GREEN+customName 灰 四种）

### T3：全量回归
- `mvn test -Dtest='!GuduE2EFlowTest'` 确认 0 新增（当前基线 242 绿）

## Self-Review

- [x] 纯函数无 Spring 依赖（参照 NeedAggregator / ShoppingAggregator）
- [x] customName 项（ingredientId=null）：pantryGrams=null，stockStatus=null（前端标灰）
- [x] pantry 查询批量 IN，非逐条（性能）
- [x] 不减库存（只读 pantry，与 Plan A 扣减链解耦）
- [x] shortageGrams 精度：BigDecimal 透传，前端格式化
- [x] GREEN 时 shortageGrams=0（非 null），前端免空判
- [x] `@InjectMocks` 自动注入新 @Mock PantryMapper（构造参按类型匹配）

## Scope 裁剪（不做）

- 采购回写 pantry（买完入库）→ Plan D（`shopping-restock.html`）
- 低库存阈值（`lowThreshold`）联动余色 → 后续
- 前端联调 → 后端就绪后另开 task
