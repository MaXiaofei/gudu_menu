# Delta: Dish Search API（菜谱搜索接口）

**Change ID:** `cookbook-search-revamp`
**Affects:** `GET /dish/search`、`Dish` 实体、`DishSearchDTO`

---

## ADDED

### Requirement: Dish 返回 cookedCount（做过次数）

`GET /dish/search` 返回的每条 Dish 新增 `cookedCount` 字段，表示当前就餐成员做过这道菜的次数。

#### Scenario: 有做菜记录
- GIVEN 当前会话 currentMemberId = 99，菜品 id=1 被该成员做过 6 次
- WHEN GET /dish/search
- THEN 返回的 dish.id=1 的 `cookedCount` = 6

#### Scenario: 没做过 / 无 member
- GIVEN 菜品 id=2 从未被当前成员做过，或会话无 currentMemberId
- WHEN GET /dish/search
- THEN 返回的 dish.id=2 的 `cookedCount` = 0（不报错）

#### Scenario: 批量回填无 N+1
- GIVEN 一次 search 返回 20 条菜
- WHEN 后端 fillCookedCount 执行
- THEN 只发 1 条 SQL（`SELECT dish_id, COUNT(*) FROM cooking_record WHERE member_id=? AND dish_id IN(...) GROUP BY dish_id`），不逐菜查询

---

### Requirement: DishSearchDTO 加 sort 排序参数

`GET /dish/search` 支持按 `sort` 参数排序结果。

#### Scenario: sort=cooked（做过最多）
- GIVEN 菜品 A 做过 6 次、菜品 B 做过 2 次、菜品 C 没做过
- WHEN GET /dish/search?sort=cooked
- THEN 返回顺序为 A(6) → B(2) → C(0)，cookedCount 降序

#### Scenario: sort 缺省（最新）
- GIVEN 不传 sort 参数
- WHEN GET /dish/search
- THEN 按 create_time DESC（现有默认行为，不变）

---

## MODIFIED

### Requirement: Dish 实体字段

Dish 新增非持久化字段 `cookedCount`。

```
@TableField(exist = false)
private Integer cookedCount;
```

不影响现有 dish 表结构，纯内存回填（与 cuisineNames/categoryNames/tagNames 同模式）。

---

## REMOVED

（无）
