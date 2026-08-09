# P1 后端瘦身实施计划：手动库存（ingredient_stock + cook 确认 + 采购联动）

> **日期**：2026-08-08 ｜ **设计来源**：`docs/pantry-shopping-redesign.md`（v0.5 定稿）
> **原则**：TDD，每 task 先写失败测试再实现；删除即删除（不留死代码）；admin 旧 CRUD 接口保留过渡（P5 再同步）。

## 架构

```
ingredient_stock（每食材一行 3 档）← 唯一真相
    ├─ setLevel（手动/采购入库）
    ├─ useUp / partialUse（做菜确认）
    └─ grouped / itemDetail（读）
stock_log（简版流水：action/note/time）
pantry 表：保留不再更新（admin 过渡读），P5 迁移 admin 后废弃
```

## Task 1：V42 迁移

- `menu-api/sql/V42__ingredient_stock.sql`：
  - 建 `ingredient_stock`（ingredient_id UNIQUE, level ENUM 存 VARCHAR, update_time）+ `stock_log`（ingredient_id, action, note, create_time）；
  - 存量映射（幂等 INSERT）：pantry 按 ingredient_id 聚合 `SUM(grams)` vs `ingredient.low_threshold` → `NONE/LOW/ENOUGH`，`grams` 全 NULL 时按 `SUM(amount)` 近似（>0 即 ENOUGH）。

## Task 2：实体 + Mapper

- `modules/pantry/IngredientStock.java` + `mapper/IngredientStockMapper.java`
- `modules/pantry/StockLog.java` + `mapper/StockLogMapper.java`
- 常量：`Level.ENOUGH/LOW/NONE`；`StockAction.COOK/COOK_PARTIAL/PURCHASE/MANUAL`

## Task 3：PantryService 重写（新语义）

删除：`deduct / deductByIngredient / adjust / planStockUp / stockUpByIngredient / saveWithGrams` + `PantryDeductionPlanner` 字段。
保留（admin 过渡）：`page / listExpiring / saveBatch`（写 pantry 表）+ controller 的 add/update/del。

新方法：
- `grouped()`：读 ingredient_stock join ingredient → 3 档分组 + lastChange（stock_log 最近一条，含 source/note/time）；Item VO 去掉 totalAmount/totalGrams/unitId，加 `level`。
- `itemDetail(ingredientId)`：level + stock_log 最近 6 条。
- `setLevel(ingredientId, level, action, note)`：upsert ingredient_stock + 写 stock_log（action 由调用方给：purchase/manual/…）。
- `useUp(ingredientId, action, note)`：→ NONE + 流水。
- `partialUse(ingredientId, action, note)`：降一档（ENOUGH→LOW；LOW/NONE 不变）+ 流水。
- `manualAdd(ingredientId, name, level, sourceNote)`：按名匹配/新建食材（**无需单位换算**）→ setLevel（默认 ENOUGH）。

## Task 4：PantryController

- 删 `/{id}/deduct`、`/adjust`；`/manual` 请求体改 `{ingredientId|name, level, sourceNote}`；
- 新增 `PUT /pantry/{ingredientId}/level` body `{level, note}`（action=manual）；
- 保留：`GET /pantry`（admin）、`/expiring`、`/low`（读 grouped 过滤 LOW）、`POST /pantry`、`/batch`、`PUT /pantry`、`DELETE /pantry/{id}`。

## Task 5：CookService 改造

- `cookMaterials(menuId)`：NeedAggregator 聚合用量 → 食材列表（id/name/needGrams/level/**isCondiment**）+ 调味料判定（dict `purchase_category` name='调味料' 的 id 集合）。**新增 `GET /menu/{id}/cook-materials`**。
- `cookByMenu(menuId, memberId, usedUp, partiallyUsed)`：
  - usedUp → `pantryService.useUp(action=COOK)`；partiallyUsed → `partialUse(action=COOK_PARTIAL)`；
  - 每菜写 cooking_record（无 memo）；menu → DONE；备菜全 READY 保留；**删 markPurchasedByMenu**。
- 删 `cookByDish` + `POST /dish/{id}/cook-now`；`CookResult` 简化为 `(menuId, cookingRecordIds)`。
- `POST /menu/{id}/cook` body `{usedUp:[], partiallyUsed:[]}`。

## Task 6：ShoppingService 改造

- `togglePurchased(itemId, level)`：0→1 且 ingredientId 非空 → `setLevel(ingredientId, level==null?'ENOUGH':level, action=PURCHASE)`；1→0 不变。**`PUT /shopping/item/{id}/purchased` body 可选 `{level}`**。
- 删 `markPurchasedByMenu`。
- `fromPrep(menuId, ingredientIds)`：查 by-menu 清单（无则新建带 source_menu_id）→ 逐项追加（`uk_list_ing_unit` 已存在则跳过）→ reference_grams 用 NeedAggregator 聚合用量。**新增 `POST /shopping/from-prep`**。

## Task 7：测试与回归

- 重写：`PantryServiceTest`（setLevel/useUp/partialUse/grouped/manualAdd）、`PantryControllerTest`、`CookServiceTest`（cookByMenu 确认语义 + cookMaterials）、`CookControllerTest`、`ShoppingServiceTest`（togglePurchased 新签名 + fromPrep）。
- 删除：`PantryDeductByIngredientTest`、`PantryDeductionPlannerTest`、`StockClassifierTest`（差 X g 退役，若独立保留则改写）。
- 调整：`GuduE2EFlowTest`（cook body 带 usedUp/partiallyUsed）。
- 全量回归：`./mvnw -pl menu-api test`。
