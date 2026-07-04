# 做菜扣库存链 Implementation Plan (Plan A)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 点「开始做这顿饭」→ 按 ingredientId 聚合各菜用量（dish_ingredient.grams × menu_dish.servingFactor）→ FIFO 扣 pantry（扣到 0 不记负、欠量记 cooking_record.memo）→ 每菜写一条 cooking_record（带 menuId/servingFactor/source）→ menu 标 DONE。

**Architecture:** 三层分离——
1. **纯函数层**（可单测、不碰 DB）：`NeedAggregator`（聚合用量）、`PantryDeductionPlanner`（FIFO 扣减规划）
2. **DB 层**：`PantryService.deductByIngredient(ingredientId, needGrams)`（查批次+按规划扣+updateById）
3. **编排层**：`CookService.cookByMenu / cookByDish`（串联聚合→扣减→写 record→标完成）；`CookController` 暴露 REST

**Tech Stack:** Spring Boot 3 / MyBatis-Plus（ServiceImpl + QueryWrapper）/ Lombok / Sa-Token / JUnit5 + Mockito + MockMvc / 手写 SQL 迁移（`menu-api/sql/V##__name.sql` 范式，最新 V35，本计划从 V36 起）。

**对应 spec：** `docs/superpowers/specs/2026-07-02-core-flow-prototype-design.md` §4.8、§5 铁律 4/5、§7 gap ②
**对应 audit：** `docs/redesign-audit.md` §1（扣减未实现）、§5（多批次 FIFO 无逻辑）、§7/§8（cooking_record 无来源/份数）
**前置依赖：** 单位换算（V35，已落地）—— 本计划复用 `DishIngredient.grams` / `Pantry.grams` 作为扣减基准（克）。

---

## File Structure

| 文件 | 责任 | 动作 |
|---|---|---|
| `menu-api/sql/V36__cooking_deduction.sql` | schema：cooking_record 加 menu_id/serving_factor/source/memo；menu 加 status/finished_at | Create |
| `menu-api/.../cookbook/CookingRecord.java` | 实体加 4 字段 | Modify |
| `menu-api/.../menu/Menu.java` | 实体加 status/finishedAt | Modify |
| `menu-api/.../menu/NeedAggregator.java` | 纯函数：聚合各菜用量 by ingredientId | Create |
| `menu-api/.../menu/NeedAggregatorTest.java` | 纯函数单测 | Create |
| `menu-api/.../pantry/PantryDeductionPlanner.java` | 纯函数：FIFO 扣减规划 + record | Create |
| `menu-api/.../pantry/PantryDeductionPlannerTest.java` | 纯函数单测 | Create |
| `menu-api/.../pantry/PantryService.java` | 加 `deductByIngredient` + `DeductResult` record | Modify |
| `menu-api/.../pantry/PantryDeductByIngredientTest.java` | DB 方法测试（spy ServiceImpl） | Create |
| `menu-api/.../menu/CookService.java` | 编排：cookByMenu / cookByDish | Create |
| `menu-api/.../menu/CookResult.java` | 返回值 record | Create |
| `menu-api/.../menu/CookServiceTest.java` | 编排测试（mock mappers） | Create |
| `menu-api/.../menu/CookController.java` | REST：POST /menu/{id}/cook、POST /dish/{id}/cook-now | Create |
| `menu-api/.../menu/CookControllerTest.java` | @WebMvcTest | Create |

---

## Task 1: V36 schema 迁移 + 实体加字段

**Files:**
- Create: `menu-api/sql/V36__cooking_deduction.sql`
- Modify: `menu-api/src/main/java/com/gudu/xsd/modules/cookbook/CookingRecord.java`
- Modify: `menu-api/src/main/java/com/gudu/xsd/modules/menu/Menu.java`

- [ ] **Step 1: 写 V36 迁移 SQL（幂等 ALTER，照 V35 范式）**

创建 `menu-api/sql/V36__cooking_deduction.sql`：

```sql
-- ============================================================
-- V36 做菜扣库存链：cooking_record 加来源/份数/欠量；menu 加状态/完成时间
-- 背景：docs/redesign-audit.md §7 §8（cooking_record 无 menuId/servingFactor/source）
-- 幂等：ALTER/ADD KEY 用 information_schema 判存在（照 V35 范式）
-- ============================================================

-- 1) cooking_record 加 menu_id / serving_factor / source / memo
SET @c1 := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='cooking_record' AND COLUMN_NAME='menu_id');
SET @s1 := IF(@c1=0,'ALTER TABLE cooking_record ADD COLUMN menu_id BIGINT NULL AFTER dish_id','SELECT 1');
PREPARE p1 FROM @s1; EXECUTE p1; DEALLOCATE PREPARE p1;

SET @c2 := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='cooking_record' AND COLUMN_NAME='serving_factor');
SET @s2 := IF(@c2=0,'ALTER TABLE cooking_record ADD COLUMN serving_factor DECIMAL(5,2) NULL','SELECT 1');
PREPARE p2 FROM @s2; EXECUTE p2; DEALLOCATE PREPARE p2;

SET @c3 := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='cooking_record' AND COLUMN_NAME='source');
SET @s3 := IF(@c3=0,'ALTER TABLE cooking_record ADD COLUMN source VARCHAR(16) NULL','SELECT 1');
PREPARE p3 FROM @s3; EXECUTE p3; DEALLOCATE PREPARE p3;

SET @c4 := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='cooking_record' AND COLUMN_NAME='memo');
SET @s4 := IF(@c4=0,'ALTER TABLE cooking_record ADD COLUMN memo VARCHAR(1024) NULL','SELECT 1');
PREPARE p4 FROM @s4; EXECUTE p4; DEALLOCATE PREPARE p4;

-- 2) menu 加 status / finished_at
SET @c5 := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='menu' AND COLUMN_NAME='status');
SET @s5 := IF(@c5=0,'ALTER TABLE menu ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT ''ACTIVE''','SELECT 1');
PREPARE p5 FROM @s5; EXECUTE p5; DEALLOCATE PREPARE p5;

SET @c6 := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='menu' AND COLUMN_NAME='finished_at');
SET @s6 := IF(@c6=0,'ALTER TABLE menu ADD COLUMN finished_at DATETIME NULL','SELECT 1');
PREPARE p6 FROM @s6; EXECUTE p6; DEALLOCATE PREPARE p6;

-- 3) cooking_record 加 menu_id 索引（按食集回溯"这顿饭做了啥"）
SET @k1 := (SELECT COUNT(*) FROM information_schema.STATISTICS
             WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='cooking_record' AND INDEX_NAME='idx_menu');
SET @sk := IF(@k1=0,'ALTER TABLE cooking_record ADD KEY idx_menu (menu_id)','SELECT 1');
PREPARE pk FROM @sk; EXECUTE pk; DEALLOCATE PREPARE pk;
```

- [ ] **Step 2: 应用迁移、验证列已加**

项目的 SQL 文件在 `menu-api/sql/` 下，按现有部署流程执行（手动 `mysql < V36...` 或重建库时自动带）。验证：

```bash
mysql -u root -p<pwd> -e "DESC cooking_record" <db>
# 预期：能看到 menu_id / serving_factor / source / memo 四列
mysql -u root -p<pwd> -e "DESC menu" <db>
# 预期：能看到 status / finished_at 两列
```

- [ ] **Step 3: CookingRecord 实体加 4 字段**

`CookingRecord.java` 在 `note` 字段后追加（保留 import 区加 `java.math.BigDecimal`）：

```java
    private String note;

    /** 关联食集（整集做时填；单菜直做为 null）。V36 加。 */
    private Long menuId;

    /** 份数（该菜这次做了几份）。V36 加。 */
    private BigDecimal servingFactor;

    /** 来源：menu=整集做 / dish=单菜直做 / manual=旧 markDone。V36 加。 */
    private String source;

    /** 欠量明细（家里不够、没扣成的部分），格式 "ingredientId:克g;..."。V36 加。 */
    private String memo;

    private LocalDateTime createTime;
```

文件头 import 处加：`import java.math.BigDecimal;`

- [ ] **Step 4: Menu 实体加 status / finishedAt**

`Menu.java` 在 `servingCount` 后、`createTime` 前追加：

```java
    /** 份数 / 人数。 */
    private Integer servingCount;

    /** 状态：ACTIVE 进行中 / DONE 已完成。V36 加。 */
    private String status;

    /** 完成时间（做菜扣库存成功后写）。V36 加。 */
    private LocalDateTime finishedAt;

    private LocalDateTime createTime;
```

- [ ] **Step 5: 跑现有测试确认不回归**

```bash
./mvnw -pl menu-api test -Dtest='PantryServiceTest,CookbookServiceTest,MenuServiceTest'
```
Expected: 全 PASS（实体只加字段，不改现有逻辑）。

- [ ] **Step 6: Commit**

```bash
git add menu-api/sql/V36__cooking_deduction.sql \
  menu-api/src/main/java/com/gudu/xsd/modules/cookbook/CookingRecord.java \
  menu-api/src/main/java/com/gudu/xsd/modules/menu/Menu.java
git commit -m "feat(menu-api): V36 cooking_record/menu 加扣减链字段(menuId/servingFactor/source/memo/status/finishedAt)"
```

---

## Task 2: NeedAggregator 纯函数（聚合用量）

**Files:**
- Create: `menu-api/src/main/java/com/gudu/xsd/modules/menu/NeedAggregator.java`
- Test: `menu-api/src/test/java/com/gudu/xsd/modules/menu/NeedAggregatorTest.java`

- [ ] **Step 1: 写失败测试**

创建 `NeedAggregatorTest.java`：

```java
package com.gudu.xsd.modules.menu;

import com.gudu.xsd.modules.dish.DishIngredient;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 用量聚合纯函数测试。照 PantryServiceTest 范式：new NeedAggregator() 不依赖 Spring。 */
class NeedAggregatorTest {

    private final NeedAggregator agg = new NeedAggregator();

    private MenuDish md(long dishId, String factor) {
        MenuDish m = new MenuDish();
        m.setDishId(dishId);
        m.setServingFactor(new BigDecimal(factor));
        return m;
    }

    private DishIngredient di(long dishId, long ingId, String grams) {
        DishIngredient d = new DishIngredient();
        d.setDishId(dishId);
        d.setIngredientId(ingId);
        d.setGrams(new BigDecimal(grams));
        return d;
    }

    @Test
    void 单菜单份_用量等于grams() {
        Map<Long, BigDecimal> need = agg.aggregate(
                List.of(md(1, "1")),
                Map.of(1L, List.of(di(1, 10, "100"))));
        assertThat(need).containsEntry(10L, new BigDecimal("100"));
    }

    @Test
    void 份数翻倍_用量乘份数() {
        Map<Long, BigDecimal> need = agg.aggregate(
                List.of(md(1, "2")),
                Map.of(1L, List.of(di(1, 10, "100"), di(1, 11, "50"))));
        assertThat(need).containsEntry(10L, new BigDecimal("200"))
                        .containsEntry(11L, new BigDecimal("100"));
    }

    @Test
    void 多菜共用食材_用量相加() {
        // 菜1用葱50g、菜2用葱30g，各1份 → 葱共需80g
        Map<Long, BigDecimal> need = agg.aggregate(
                List.of(md(1, "1"), md(2, "1")),
                Map.of(1L, List.of(di(1, 10, "50")),
                       2L, List.of(di(2, 10, "30"))));
        assertThat(need).containsEntry(10L, new BigDecimal("80"));
    }

    @Test
    void 同菜多条menu_dish_份数累加() {
        // audit §6：菜1误加入两次各1份 → 按2份算（扣减只对现有数据正确聚合，去重在加菜接口）
        Map<Long, BigDecimal> need = agg.aggregate(
                List.of(md(1, "1"), md(1, "1")),
                Map.of(1L, List.of(di(1, 10, "100"))));
        assertThat(need).containsEntry(10L, new BigDecimal("200"));
    }

    @Test
    void grams为空_跳过该行() {
        Map<Long, BigDecimal> need = agg.aggregate(
                List.of(md(1, "1")),
                Map.of(1L, List.of(di(1, 10, "100"), di(1, 11, null))));
        assertThat(need).containsOnlyKeys(10L);
    }

    @Test
    void 空输入_返回空map() {
        assertThat(agg.aggregate(List.of(), Map.of())).isEmpty();
        assertThat(agg.aggregate(null, null)).isEmpty();
    }
}
```

- [ ] **Step 2: 跑测试，确认编译失败（NeedAggregator 不存在）**

```bash
./mvnw -pl menu-api test -Dtest=NeedAggregatorTest
```
Expected: 编译错误 `cannot find symbol: class NeedAggregator`。

- [ ] **Step 3: 实现 NeedAggregator**

创建 `NeedAggregator.java`：

```java
package com.gudu.xsd.modules.menu;

import com.gudu.xsd.modules.dish.DishIngredient;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用量聚合（纯函数）：把食集里各菜的食材用量，按 ingredientId 汇总成"共需多少克"。
 *
 * 算法：① 按 dishId 合并 servingFactor（同菜多行=多份，audit §6 数据层正确聚合）
 *      ② Σ(grams × factor) by ingredientId
 * 不碰任何 Mapper，便于单测。调用方负责查数据传入。
 */
@Component
public class NeedAggregator {

    public Map<Long, BigDecimal> aggregate(List<MenuDish> menuDishes,
                                            Map<Long, List<DishIngredient>> dishIngredientsByDish) {
        Map<Long, BigDecimal> needByIng = new HashMap<>();
        if (menuDishes == null || menuDishes.isEmpty() || dishIngredientsByDish == null) {
            return needByIng;
        }
        // 1) 按 dishId 合并份数（同菜多行累加）
        Map<Long, BigDecimal> factorByDish = new HashMap<>();
        for (MenuDish md : menuDishes) {
            if (md == null || md.getDishId() == null) continue;
            BigDecimal f = md.getServingFactor() == null ? BigDecimal.ONE : md.getServingFactor();
            factorByDish.merge(md.getDishId(), f, BigDecimal::add);
        }
        // 2) 按食材聚合
        for (Map.Entry<Long, BigDecimal> e : factorByDish.entrySet()) {
            List<DishIngredient> ings = dishIngredientsByDish.get(e.getKey());
            if (ings == null) continue;
            for (DishIngredient di : ings) {
                if (di == null || di.getIngredientId() == null || di.getGrams() == null) continue;
                BigDecimal need = di.getGrams().multiply(e.getValue());
                needByIng.merge(di.getIngredientId(), need, BigDecimal::add);
            }
        }
        return needByIng;
    }
}
```

- [ ] **Step 4: 跑测试，确认通过**

```bash
./mvnw -pl menu-api test -Dtest=NeedAggregatorTest
```
Expected: 6 个测试全 PASS。

- [ ] **Step 5: Commit**

```bash
git add menu-api/src/main/java/com/gudu/xsd/modules/menu/NeedAggregator.java \
        menu-api/src/test/java/com/gudu/xsd/modules/menu/NeedAggregatorTest.java
git commit -m "feat(menu): NeedAggregator 用量聚合纯函数(按ingredientId聚合grams×份数)"
```

---

## Task 3: PantryDeductionPlanner 纯函数（FIFO 扣减规划）

**Files:**
- Create: `menu-api/src/main/java/com/gudu/xsd/modules/pantry/PantryDeductionPlanner.java`
- Test: `menu-api/src/test/java/com/gudu/xsd/modules/pantry/PantryDeductionPlannerTest.java`

- [ ] **Step 1: 写失败测试**

创建 `PantryDeductionPlannerTest.java`：

```java
package com.gudu.xsd.modules.pantry;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** FIFO 扣减规划纯函数测试。调用方负责批次排序，这里只测算法。 */
class PantryDeductionPlannerTest {

    private final PantryDeductionPlanner planner = new PantryDeductionPlanner();

    private Pantry batch(long id, String grams, String amount) {
        Pantry p = new Pantry();
        p.setId(id);
        p.setGrams(new BigDecimal(grams));
        p.setAmount(new BigDecimal(amount));
        return p;
    }

    @Test
    void 单批次够_扣部分_剩余量_金额按比例缩() {
        PantryDeductionPlanner.DeductPlan plan = planner.plan(
                List.of(batch(1, "100", "2")), new BigDecimal("30"));
        assertThat(plan.ops()).hasSize(1);
        assertThat(plan.ops().get(0).deductGrams()).isEqualByComparingTo("30");
        assertThat(plan.ops().get(0).remainGrams()).isEqualByComparingTo("70");
        assertThat(plan.ops().get(0).newAmount()).isEqualByComparingTo("1.40"); // 2 × 70/100
        assertThat(plan.shortageGrams()).isEqualByComparingTo("0");
    }

    @Test
    void 单批次不够_全扣完_记欠量() {
        PantryDeductionPlanner.DeductPlan plan = planner.plan(
                List.of(batch(1, "30", "1")), new BigDecimal("100"));
        assertThat(plan.ops()).hasSize(1);
        assertThat(plan.ops().get(0).deductGrams()).isEqualByComparingTo("30");
        assertThat(plan.ops().get(0).remainGrams()).isEqualByComparingTo("0");
        assertThat(plan.ops().get(0).newAmount()).isEqualByComparingTo("0.00");
        assertThat(plan.shortageGrams()).isEqualByComparingTo("70");
    }

    @Test
    void 多批次_FIFO_先扣完早的再扣下一批() {
        PantryDeductionPlanner.DeductPlan plan = planner.plan(
                List.of(batch(1, "50", "1"), batch(2, "60", "2")),
                new BigDecimal("100"));
        assertThat(plan.ops()).hasSize(2);
        assertThat(plan.ops().get(0).pantryId()).isEqualTo(1L);    // 第一批先扣完
        assertThat(plan.ops().get(0).deductGrams()).isEqualByComparingTo("50");
        assertThat(plan.ops().get(0).remainGrams()).isEqualByComparingTo("0");
        assertThat(plan.ops().get(1).pantryId()).isEqualTo(2L);
        assertThat(plan.ops().get(1).deductGrams()).isEqualByComparingTo("50");
        assertThat(plan.ops().get(1).remainGrams()).isEqualByComparingTo("10");
        assertThat(plan.shortageGrams()).isEqualByComparingTo("0");
    }

    @Test
    void 需求为零_空操作() {
        PantryDeductionPlanner.DeductPlan plan = planner.plan(
                List.of(batch(1, "100", "2")), BigDecimal.ZERO);
        assertThat(plan.ops()).isEmpty();
        assertThat(plan.shortageGrams()).isEqualByComparingTo("0");
    }

    @Test
    void 没有批次_全记欠() {
        PantryDeductionPlanner.DeductPlan plan = planner.plan(
                List.of(), new BigDecimal("80"));
        assertThat(plan.ops()).isEmpty();
        assertThat(plan.shortageGrams()).isEqualByComparingTo("80");
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

```bash
./mvnw -pl menu-api test -Dtest=PantryDeductionPlannerTest
```
Expected: 编译错误 `cannot find symbol: class PantryDeductionPlanner`。

- [ ] **Step 3: 实现 PantryDeductionPlanner**

创建 `PantryDeductionPlanner.java`：

```java
package com.gudu.xsd.modules.pantry;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * FIFO 扣减规划（纯函数）：给定已排好序的 pantry 批次和需求克数，
 * 算出每个批次扣多少、最终欠多少。不碰 DB，便于单测。
 *
 * 调用方须保证 batches 已按"过期日升序（null 最后）、id 升序"排好（FIFO 先扣早过期的）。
 * amount（按 unitId 的个数）随 grams 按比例缩放，保证两字段一致。
 */
public class PantryDeductionPlanner {

    public DeductPlan plan(List<Pantry> batchesSorted, BigDecimal needGrams) {
        if (needGrams == null || needGrams.signum() <= 0) {
            return new DeductPlan(List.of(), BigDecimal.ZERO);
        }
        BigDecimal remaining = needGrams;
        List<BatchDeduction> ops = new ArrayList<>();
        if (batchesSorted != null) {
            for (Pantry p : batchesSorted) {
                if (p == null || p.getId() == null) continue;
                if (remaining.signum() <= 0) break;
                BigDecimal avail = p.getGrams() == null ? BigDecimal.ZERO : p.getGrams();
                if (avail.signum() <= 0) continue;
                BigDecimal take = avail.min(remaining);
                BigDecimal remainAfter = avail.subtract(take);
                BigDecimal newAmount = scaleAmount(p.getAmount(), avail, take);
                ops.add(new BatchDeduction(p.getId(), take, remainAfter, newAmount));
                remaining = remaining.subtract(take);
            }
        }
        return new DeductPlan(ops, remaining.signum() > 0 ? remaining : BigDecimal.ZERO);
    }

    /** amount 按所扣比例缩放：newAmount = amount × (1 - take/avail)。avail=0 时原值不动。 */
    private BigDecimal scaleAmount(BigDecimal amount, BigDecimal avail, BigDecimal take) {
        if (amount == null) return null;
        if (avail.signum() <= 0) return amount;
        BigDecimal remainRatio = BigDecimal.ONE.subtract(take.divide(avail, 6, RoundingMode.HALF_UP));
        return amount.multiply(remainRatio).setScale(2, RoundingMode.HALF_UP);
    }

    /** 单批次扣减结果。 */
    public record BatchDeduction(Long pantryId, BigDecimal deductGrams,
                                 BigDecimal remainGrams, BigDecimal newAmount) {}

    /** 整体扣减规划：对各批次的操作 + 最终欠量（pantry 不记负，扣不动的进 shortage）。 */
    public record DeductPlan(List<BatchDeduction> ops, BigDecimal shortageGrams) {}
}
```

- [ ] **Step 4: 跑测试确认通过**

```bash
./mvnw -pl menu-api test -Dtest=PantryDeductionPlannerTest
```
Expected: 5 个测试全 PASS。

- [ ] **Step 5: Commit**

```bash
git add menu-api/src/main/java/com/gudu/xsd/modules/pantry/PantryDeductionPlanner.java \
        menu-api/src/test/java/com/gudu/xsd/modules/pantry/PantryDeductionPlannerTest.java
git commit -m "feat(pantry): PantryDeductionPlanner FIFO扣减规划纯函数(扣到0不记负+欠量)"
```

---

## Task 4: PantryService.deductByIngredient（DB 扣减）

**Files:**
- Modify: `menu-api/src/main/java/com/gudu/xsd/modules/pantry/PantryService.java`
- Test: `menu-api/src/test/java/com/gudu/xsd/modules/pantry/PantryDeductByIngredientTest.java`

- [ ] **Step 1: 写失败测试**

创建 `PantryDeductByIngredientTest.java`。PantryService 继承 ServiceImpl，用 Mockito spy 桩 `list/getById/updateById`（不走真实 baseMapper）：

```java
package com.gudu.xsd.modules.pantry;

import com.gudu.xsd.modules.nutrition.mapper.IngredientMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** deductByIngredient 测试：spy PantryService 桩 list/getById/updateById，不碰真实 DB。 */
class PantryDeductByIngredientTest {

    private Pantry batch(long id, long ingId, String grams, String amount) {
        Pantry p = new Pantry();
        p.setId(id);
        p.setIngredientId(ingId);
        p.setGrams(new BigDecimal(grams));
        p.setAmount(new BigDecimal(amount));
        return p;
    }

    @Test
    void 够_单批次_扣后grams和amount同步缩() {
        PantryService svc = spy(new PantryService(mock(IngredientMapper.class)));
        Pantry p = batch(1, 10, "100", "2");
        doReturn(List.of(p)).when(svc).list(any());
        doReturn(p).when(svc).getById(1L);
        doReturn(true).when(svc).updateById(any(Pantry.class));

        PantryService.DeductResult r = svc.deductByIngredient(10L, new BigDecimal("30"));

        assertThat(r.deductedGrams()).isEqualByComparingTo("30");
        assertThat(r.shortageGrams()).isEqualByComparingTo("0");
        verify(svc).updateById(argThat(x ->
                ((Pantry) x).getGrams().isEqualByComparingTo("70")
                && ((Pantry) x).getAmount().isEqualByComparingTo("1.40")));
    }

    @Test
    void 不够_全扣到0_欠量返回() {
        PantryService svc = spy(new PantryService(mock(IngredientMapper.class)));
        Pantry p = batch(1, 10, "30", "1");
        doReturn(List.of(p)).when(svc).list(any());
        doReturn(p).when(svc).getById(1L);
        doReturn(true).when(svc).updateById(any(Pantry.class));

        PantryService.DeductResult r = svc.deductByIngredient(10L, new BigDecimal("100"));

        assertThat(r.deductedGrams()).isEqualByComparingTo("30");
        assertThat(r.shortageGrams()).isEqualByComparingTo("70");
        verify(svc).updateById(argThat(x ->
                ((Pantry) x).getGrams().isEqualByComparingTo("0")));
    }

    @Test
    void 多批次_FIFO_按查询返回顺序扣() {
        PantryService svc = spy(new PantryService(mock(IngredientMapper.class)));
        Pantry a = batch(1, 10, "40", "1");
        Pantry b = batch(2, 10, "60", "2");
        doReturn(List.of(a, b)).when(svc).list(any());   // 已按 FIFO 排序
        doReturn(a).when(svc).getById(1L);
        doReturn(b).when(svc).getById(2L);
        doReturn(true).when(svc).updateById(any(Pantry.class));

        PantryService.DeductResult r = svc.deductByIngredient(10L, new BigDecimal("70"));

        assertThat(r.shortageGrams()).isEqualByComparingTo("0");
        assertThat(r.batches()).hasSize(2);
        verify(svc, times(2)).updateById(any(Pantry.class));
    }

    @Test
    void 需求为零_不查不扣() {
        PantryService svc = spy(new PantryService(mock(IngredientMapper.class)));
        PantryService.DeductResult r = svc.deductByIngredient(10L, BigDecimal.ZERO);
        assertThat(r.deductedGrams()).isEqualByComparingTo("0");
        verify(svc, never()).list(any());
        verify(svc, never()).updateById(any(Pantry.class));
    }

    // 静态导入断言
    static <T> org.assertj.core.api.Assertions.AssertionProxyFactory<T> assertThat(T actual) {
        throw new UnsupportedOperationException();
    }
}
```

> ⚠️ 上面测试文件去掉末尾那个占位 `assertThat` 重载（误植）。**实际测试文件用标准静态导入**，文件头加：
```java
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
```
并删除文件末尾的 `static <T> ... assertThat` 占位方法。正确写法见 Step 3 实现 + 这两个 import。

- [ ] **Step 2: 跑测试确认失败（deductByIngredient 方法不存在）**

```bash
./mvnw -pl menu-api test -Dtest=PantryDeductByIngredientTest
```
Expected: 编译错误 `cannot find symbol: method deductByIngredient`。

- [ ] **Step 3: 在 PantryService 加 deductByIngredient + DeductResult**

`PantryService.java` 在类字段区加（紧跟 `unitConvert` 字段后）：

```java
    private final PantryDeductionPlanner deductionPlanner = new PantryDeductionPlanner();
```

在 `deduct(Long id, BigDecimal amount)` 方法后追加：

```java
    // ===================== 按食材 FIFO 扣减（做菜用） =====================

    /**
     * 按食材 FIFO 扣减：查该食材所有 grams>0 的批次（过期日升序、null 最后、id 升序），
     * 逐批扣到 0 为止，扣不动的记 shortage 返回（pantry 不记负）。
     *
     * @return DeductResult 含实扣克数、欠量、各批次明细
     */
    @org.springframework.transaction.annotation.Transactional
    public DeductResult deductByIngredient(Long ingredientId, BigDecimal needGrams) {
        if (ingredientId == null) {
            throw new BizException("食材 id 不能为空");
        }
        if (needGrams == null || needGrams.signum() <= 0) {
            return new DeductResult(ingredientId, BigDecimal.ZERO, BigDecimal.ZERO, List.of());
        }
        List<Pantry> batches = list(new QueryWrapper<Pantry>()
                .eq("ingredient_id", ingredientId)
                .gt("grams", 0)
                .last("ORDER BY expire_date IS NULL, expire_date ASC, id ASC"));
        PantryDeductionPlanner.DeductPlan plan = deductionPlanner.plan(batches, needGrams);
        for (PantryDeductionPlanner.BatchDeduction op : plan.ops()) {
            Pantry p = getById(op.pantryId());
            if (p == null) continue;
            p.setGrams(op.remainGrams());
            if (op.newAmount() != null) {
                p.setAmount(op.newAmount());
            }
            updateById(p);
        }
        BigDecimal shortage = plan.shortageGrams();
        BigDecimal deducted = needGrams.subtract(shortage);
        List<DeductResult.BatchOut> outs = plan.ops().stream()
                .map(op -> new DeductResult.BatchOut(op.pantryId(), op.deductGrams(), op.remainGrams()))
                .collect(java.util.stream.Collectors.toList());
        return new DeductResult(ingredientId, deducted, shortage, outs);
    }

    /** 单食材扣减结果。 */
    public record DeductResult(Long ingredientId,
                               BigDecimal deductedGrams,   // 实际扣掉的克数
                               BigDecimal shortageGrams,    // 没扣成的欠量
                               List<BatchOut> batches) {    // 各批次明细
        public record BatchOut(Long pantryId, BigDecimal deductedGrams, BigDecimal remainGrams) {}
    }
```

- [ ] **Step 4: 跑测试确认通过**

```bash
./mvnw -pl menu-api test -Dtest=PantryDeductByIngredientTest
```
Expected: 4 个测试全 PASS。

- [ ] **Step 5: Commit**

```bash
git add menu-api/src/main/java/com/gudu/xsd/modules/pantry/PantryService.java \
        menu-api/src/test/java/com/gudu/xsd/modules/pantry/PantryDeductByIngredientTest.java
git commit -m "feat(pantry): deductByIngredient 按食材FIFO扣减(查批次+扣到0+欠量返回)"
```

---

## Task 5: CookService（编排：聚合→扣减→写 record→标完成）

**Files:**
- Create: `menu-api/src/main/java/com/gudu/xsd/modules/menu/CookResult.java`
- Create: `menu-api/src/main/java/com/gudu/xsd/modules/menu/CookService.java`
- Test: `menu-api/src/test/java/com/gudu/xsd/modules/menu/CookServiceTest.java`

- [ ] **Step 1: 写 CookResult record**

创建 `CookResult.java`：

```java
package com.gudu.xsd.modules.menu;

import com.gudu.xsd.modules.pantry.PantryService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 做菜扣库存结果。
 *
 * @param menuId           整集做=食集 id；单菜直做=null
 * @param deductions       各食材的扣减明细（含实扣/欠量/批次）
 * @param shortages        欠量明细 ingredientId → 欠多少克（家里没有或不够的）
 * @param cookingRecordIds 写入的 cooking_record id 列表（整集做=每菜一条；单菜=一条）
 */
public record CookResult(Long menuId,
                         List<PantryService.DeductResult> deductions,
                         Map<Long, BigDecimal> shortages,
                         List<Long> cookingRecordIds) {}
```

- [ ] **Step 2: 写失败测试**

创建 `CookServiceTest.java`：

```java
package com.gudu.xsd.modules.menu;

import com.gudu.xsd.modules.cookbook.CookingRecord;
import com.gudu.xsd.modules.cookbook.mapper.CookingRecordMapper;
import com.gudu.xsd.modules.dish.DishIngredient;
import com.gudu.xsd.modules.dish.mapper.DishIngredientMapper;
import com.gudu.xsd.modules.menu.mapper.MenuMapper;
import com.gudu.xsd.modules.pantry.PantryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CookServiceTest {

    @Mock MenuMapper menuMapper;
    @Mock MenuDishMapper menuDishMapper;
    @Mock DishIngredientMapper dishIngredientMapper;
    @Mock CookingRecordMapper cookingRecordMapper;
    @Mock PantryService pantryService;

    private CookService cookService;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        cookService = new CookService(menuMapper, menuDishMapper, dishIngredientMapper,
                cookingRecordMapper, pantryService, new NeedAggregator());
    }

    private MenuDish md(long dishId, String factor) {
        MenuDish m = new MenuDish();
        m.setDishId(dishId);
        m.setServingFactor(new BigDecimal(factor));
        return m;
    }

    private DishIngredient di(long dishId, long ingId, String grams) {
        DishIngredient d = new DishIngredient();
        d.setDishId(dishId);
        d.setIngredientId(ingId);
        d.setGrams(new BigDecimal(grams));
        return d;
    }

    @Test
    void cookByMenu_聚合扣减写record并标完成() {
        Menu menu = new Menu();
        menu.setId(7L);
        when(menuMapper.selectById(7L)).thenReturn(menu);
        when(menuDishMapper.selectList(any())).thenReturn(List.of(md(1, "2")));
        when(dishIngredientMapper.selectList(any())).thenReturn(List.of(di(1, 10, "100")));
        // 每食材扣减返回够扣
        when(pantryService.deductByIngredient(eq(10L), eq(new BigDecimal("200"))))
                .thenReturn(new PantryService.DeductResult(10L, new BigDecimal("200"), BigDecimal.ZERO, List.of()));

        CookResult r = cookService.cookByMenu(7L, 99L);

        assertThat(r.menuId()).isEqualTo(7L);
        assertThat(r.shortages()).isEmpty();
        verify(cookingRecordMapper, times(1)).insert(any(CookingRecord.class));   // 每菜一条
        verify(menuMapper).updateById(argThat(m -> "DONE".equals(((Menu) m).getStatus())
                && ((Menu) m).getFinishedAt() != null));
    }

    @Test
    void cookByMenu_欠量写入memo() {
        Menu menu = new Menu();
        menu.setId(7L);
        when(menuMapper.selectById(7L)).thenReturn(menu);
        when(menuDishMapper.selectList(any())).thenReturn(List.of(md(1, "1")));
        when(dishIngredientMapper.selectList(any())).thenReturn(List.of(
                di(1, 10, "100"), di(1, 20, "50")));
        when(pantryService.deductByIngredient(eq(10L), any())).thenReturn(
                new PantryService.DeductResult(10L, new BigDecimal("30"), new BigDecimal("70"), List.of()));
        when(pantryService.deductByIngredient(eq(20L), any())).thenReturn(
                new PantryService.DeductResult(20L, new BigDecimal("50"), BigDecimal.ZERO, List.of()));

        CookResult r = cookService.cookByMenu(7L, 99L);

        assertThat(r.shortages()).containsEntry(10L, new BigDecimal("70"));
        verify(cookingRecordMapper).insert(argThat(rec ->
                rec.getMemo() != null && rec.getMemo().contains("10:70g") && "menu".equals(rec.getSource())));
    }

    @Test
    void cookByDish_单菜直做_source为dish_不更新menu() {
        when(dishIngredientMapper.selectList(any())).thenReturn(List.of(di(3, 10, "100")));
        when(pantryService.deductByIngredient(eq(10L), eq(new BigDecimal("100"))))
                .thenReturn(new PantryService.DeductResult(10L, new BigDecimal("100"), BigDecimal.ZERO, List.of()));

        CookResult r = cookService.cookByDish(3L, BigDecimal.ONE, 99L);

        assertThat(r.menuId()).isNull();
        verify(cookingRecordMapper).insert(argThat(rec ->
                rec.getDishId() == 3L && "dish".equals(rec.getSource()) && rec.getMenuId() == null));
        verify(menuMapper, never()).updateById(any());
    }
}
```

- [ ] **Step 3: 跑测试确认失败（CookService 不存在）**

```bash
./mvnw -pl menu-api test -Dtest=CookServiceTest
```
Expected: 编译错误 `cannot find symbol: class CookService`。

- [ ] **Step 4: 实现 CookService**

创建 `CookService.java`：

```java
package com.gudu.xsd.modules.menu;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gudu.xsd.common.BizException;
import com.gudu.xsd.modules.cookbook.CookingRecord;
import com.gudu.xsd.modules.cookbook.mapper.CookingRecordMapper;
import com.gudu.xsd.modules.dish.DishIngredient;
import com.gudu.xsd.modules.dish.mapper.DishIngredientMapper;
import com.gudu.xsd.modules.menu.mapper.MenuMapper;
import com.gudu.xsd.modules.pantry.PantryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 做菜扣库存编排：聚合用量 → 按 ingredientId FIFO 扣 pantry → 每菜写一条 cooking_record
 * （带 menuId/servingFactor/source/memo）→ 整集做把 menu 标 DONE。
 *
 * 扣减规则（铁律）：扣到 0 为止、pantry 不记负，扣不动的欠量写 cooking_record.memo。
 */
@Service
@RequiredArgsConstructor
public class CookService {

    static final String SOURCE_MENU = "menu";
    static final String SOURCE_DISH = "dish";
    static final String MENU_STATUS_DONE = "DONE";

    private final MenuMapper menuMapper;
    private final MenuDishMapper menuDishMapper;
    private final DishIngredientMapper dishIngredientMapper;
    private final CookingRecordMapper cookingRecordMapper;
    private final PantryService pantryService;
    private final NeedAggregator needAggregator;

    /** 整集做：聚合食集各菜用量 → 扣减 → 每菜写 record → menu 标完成。 */
    @Transactional
    public CookResult cookByMenu(Long menuId, Long memberId) {
        Menu menu = menuMapper.selectById(menuId);
        if (menu == null) {
            throw new BizException("食集不存在");
        }
        List<MenuDish> mds = menuDishMapper.selectList(
                new QueryWrapper<MenuDish>().eq("menu_id", menuId));
        List<Long> dishIds = mds.stream().map(MenuDish::getDishId).filter(Objects::nonNull).distinct()
                .collect(Collectors.toList());
        Map<Long, List<DishIngredient>> byDish = loadDishIngredients(dishIds);
        Map<Long, BigDecimal> needByIng = needAggregator.aggregate(mds, byDish);

        Map<Long, BigDecimal> shortages = new LinkedHashMap<>();
        List<PantryService.DeductResult> deductions = deductAll(needByIng, shortages);

        // 每菜写一条 cooking_record（都带 menuId，source=menu，memo=全量欠量）
        String memo = buildShortageMemo(shortages);
        List<Long> recordIds = new ArrayList<>();
        for (Long dishId : dishIds) {
            BigDecimal dishFactor = mds.stream()
                    .filter(m -> dishId.equals(m.getDishId()))
                    .map(MenuDish::getServingFactor).filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            recordIds.add(insertRecord(memberId, dishId, menuId, dishFactor, SOURCE_MENU, memo));
        }

        menu.setStatus(MENU_STATUS_DONE);
        menu.setFinishedAt(LocalDateTime.now());
        menuMapper.updateById(menu);

        return new CookResult(menuId, deductions, shortages, recordIds);
    }

    /** 单菜直做：不入食集，source=dish。 */
    @Transactional
    public CookResult cookByDish(Long dishId, BigDecimal servings, Long memberId) {
        BigDecimal factor = servings == null || servings.signum() <= 0 ? BigDecimal.ONE : servings;
        Map<Long, List<DishIngredient>> byDish = loadDishIngredients(List.of(dishId));
        Map<Long, BigDecimal> needByIng = new HashMap<>();
        List<DishIngredient> ings = byDish.getOrDefault(dishId, List.of());
        for (DishIngredient di : ings) {
            if (di == null || di.getIngredientId() == null || di.getGrams() == null) continue;
            needByIng.merge(di.getIngredientId(), di.getGrams().multiply(factor), BigDecimal::add);
        }

        Map<Long, BigDecimal> shortages = new LinkedHashMap<>();
        List<PantryService.DeductResult> deductions = deductAll(needByIng, shortages);

        Long recId = insertRecord(memberId, dishId, null, factor, SOURCE_DISH, buildShortageMemo(shortages));
        return new CookResult(null, deductions, shortages, List.of(recId));
    }

    // ===================== 内部辅助 =====================

    private Map<Long, List<DishIngredient>> loadDishIngredients(List<Long> dishIds) {
        if (dishIds.isEmpty()) return Map.of();
        List<DishIngredient> rows = dishIngredientMapper.selectList(
                new QueryWrapper<DishIngredient>().in("dish_id", dishIds));
        return rows.stream().collect(Collectors.groupingBy(DishIngredient::getDishId));
    }

    private List<PantryService.DeductResult> deductAll(Map<Long, BigDecimal> needByIng,
                                                       Map<Long, BigDecimal> shortages) {
        List<PantryService.DeductResult> out = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal> e : needByIng.entrySet()) {
            PantryService.DeductResult r = pantryService.deductByIngredient(e.getKey(), e.getValue());
            out.add(r);
            if (r.shortageGrams() != null && r.shortageGrams().signum() > 0) {
                shortages.put(e.getKey(), r.shortageGrams());
            }
        }
        return out;
    }

    private Long insertRecord(Long memberId, Long dishId, Long menuId,
                              BigDecimal servingFactor, String source, String memo) {
        CookingRecord rec = new CookingRecord();
        rec.setMemberId(memberId);
        rec.setDishId(dishId);
        rec.setMenuId(menuId);
        rec.setServingFactor(servingFactor);
        rec.setSource(source);
        rec.setMemo(memo);
        rec.setCookedAt(LocalDateTime.now());
        cookingRecordMapper.insert(rec);
        return rec.getId();
    }

    /** 欠量 memo："10:70g;20:50g"（ingredientId:克g）。前端翻译食材名。 */
    private String buildShortageMemo(Map<Long, BigDecimal> shortages) {
        if (shortages.isEmpty()) return null;
        return shortages.entrySet().stream()
                .map(e -> e.getKey() + ":" + e.getValue().setScale(0, RoundingMode.HALF_UP) + "g")
                .collect(Collectors.joining(";"));
    }
}
```

- [ ] **Step 5: 跑测试确认通过**

```bash
./mvnw -pl menu-api test -Dtest=CookServiceTest
```
Expected: 3 个测试全 PASS。

- [ ] **Step 6: Commit**

```bash
git add menu-api/src/main/java/com/gudu/xsd/modules/menu/CookService.java \
        menu-api/src/main/java/com/gudu/xsd/modules/menu/CookResult.java \
        menu-api/src/test/java/com/gudu/xsd/modules/menu/CookServiceTest.java
git commit -m "feat(menu): CookService 做菜扣库存编排(cookByMenu/cookByDish+欠量memo+食集DONE)"
```

---

## Task 6: CookController（REST 接口）

**Files:**
- Create: `menu-api/src/main/java/com/gudu/xsd/modules/menu/CookController.java`
- Test: `menu-api/src/test/java/com/gudu/xsd/modules/menu/CookControllerTest.java`

- [ ] **Step 1: 写失败测试（@WebMvcTest，照 IngredientUnitGramControllerTest 范式）**

创建 `CookControllerTest.java`：

```java
package com.gudu.xsd.modules.menu;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.defaults.DefaultSqlSessionFactory;
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** CookController MockMvc 测试：照 IngredientUnitGramControllerTest 范式。 */
@WebMvcTest(
        value = CookController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = com.gudu.xsd.config.SaTokenConfig.class))
@Import(CookControllerTest.TestSqlConfig.class)
class CookControllerTest {

    @TestConfiguration
    static class TestSqlConfig {
        @Bean
        DataSource dataSource() {
            return org.mockito.Mockito.mock(DataSource.class);
        }

        @Bean
        SqlSessionFactory sqlSessionFactory(DataSource ds) {
            TransactionFactory tx = new JdbcTransactionFactory();
            Environment env = new Environment("test", tx, ds);
            return new DefaultSqlSessionFactory(new Configuration(env));
        }
    }

    @Autowired MockMvc mvc;
    @MockBean CookService cookService;
    private final ObjectMapper om = new ObjectMapper();

    @Test
    void POST_整集做菜() throws Exception {
        CookResult stub = new CookResult(7L, List.of(), Map.of(), List.of(1L));
        given(cookService.cookByMenu(eq(7L), any())).willReturn(stub);

        mvc.perform(post("/menu/7/cook"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.menuId").value(7));
        verify(cookService).cookByMenu(eq(7L), any());
    }

    @Test
    void POST_单菜直做_带份数() throws Exception {
        given(cookService.cookByDish(eq(3L), eq(new BigDecimal("2")), any()))
                .willReturn(new CookResult(null, List.of(), Map.of(), List.of(9L)));

        mvc.perform(post("/dish/3/cook-now").param("servings", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cookingRecordIds[0]").value(9));
        verify(cookService).cookByDish(eq(3L), eq(new BigDecimal("2")), any());
    }
}
```

- [ ] **Step 2: 跑测试确认失败（CookController 不存在）**

```bash
./mvnw -pl menu-api test -Dtest=CookControllerTest
```
Expected: 编译错误 `cannot find symbol: class CookController`。

- [ ] **Step 3: 实现 CookController**

创建 `CookController.java`：

```java
package com.gudu.xsd.modules.menu;

import cn.dev33.satoken.stp.StpUtil;
import com.gudu.xsd.common.R;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
@RequiredArgsConstructor
@Tag(name = "做菜")
public class CookController {

    private final CookService cookService;

    /** 整集做：聚合各菜用量 → 扣库存 → 每菜写 cooking_record → 食集标完成。 */
    @PostMapping("/menu/{id}/cook")
    public R<CookResult> cookMenu(@PathVariable Long id) {
        return R.ok(cookService.cookByMenu(id, currentMemberId()));
    }

    /** 单菜直做（轻流程，不入食集）。 */
    @PostMapping("/dish/{id}/cook-now")
    public R<CookResult> cookDish(@PathVariable Long id,
                                  @RequestParam(defaultValue = "1") BigDecimal servings) {
        return R.ok(cookService.cookByDish(id, servings, currentMemberId()));
    }

    private Long currentMemberId() {
        try {
            return StpUtil.getLoginIdAsLong();
        } catch (Exception e) {
            return null;   // 未登录（测试上下文）：service 内 memberId 仅用于写 record，可空
        }
    }
}
```

- [ ] **Step 4: 跑测试确认通过**

```bash
./mvnw -pl menu-api test -Dtest=CookControllerTest
```
Expected: 2 个测试全 PASS。

- [ ] **Step 5: 全量回归**

```bash
./mvnw -pl menu-api test
```
Expected: 全部 PASS（含原有测试 + 本计划新增 4 个测试类）。若 `GuduE2EFlowTest` 因 V36 schema 失败，确认其用 H2/测试库已应用 V36。

- [ ] **Step 6: Commit**

```bash
git add menu-api/src/main/java/com/gudu/xsd/modules/menu/CookController.java \
        menu-api/src/test/java/com/gudu/xsd/modules/menu/CookControllerTest.java
git commit -m "feat(menu): CookController POST /menu/{id}/cook + /dish/{id}/cook-now 做菜扣库存接口"
```

---

## Self-Review（写完后自检）

**1. Spec 覆盖**（对照 spec §4.8、§5 铁律 4/5、§7 gap ②）
- ✅ 按 ingredientId 聚合各菜用量（份数 × DishIngredient.grams）→ Task 2 NeedAggregator
- ✅ FIFO 先扣最早批次（expire_date ASC, nulls last, id ASC）→ Task 3/4
- ✅ 扣到 0 为止、pantry 不记负 → Task 3 PantryDeductionPlanner（shortage 分离）
- ✅ 欠量写 cooking_record.memo → Task 5 buildShortageMemo
- ✅ 写 cooking_record(menuId + servingFactor + source) → Task 5（每菜一条）
- ✅ menu → DONE → Task 5 cookByMenu
- ✅ 整集做 + 单菜直做双流程 → Task 5 cookByMenu / cookByDish + Task 6 两接口
- ⚠️ audit §7「markDone 加份数/防误触」—— **本计划未改 markDone**（属 P1，留 Plan B 或独立小计划）。新流程走 /menu/{id}/cook 与 /dish/{id}/cook-now，旧 markDone 仍只写无份数 record，共存不冲突。

**2. 占位符扫描**：无 TBD/TODO/"实现细节后补"；每个 code step 含完整代码。Task 4 Step 1 测试文件有一处明确标注的修正说明（assertThat 静态导入），实现时按说明处理。

**3. 类型一致性**：
- `NeedAggregator.aggregate` 返回 `Map<Long,BigDecimal>`，CookService 直接传入 deductByIngredient ✓
- `PantryDeductionPlanner.BatchDeduction` / `DeductPlan` 在 Task 3 定义，Task 4 PantryService 引用一致 ✓
- `PantryService.DeductResult` / `BatchOut` 在 Task 4 定义，CookService / CookResult 引用一致 ✓
- `CookResult` 字段名 `menuId/deductions/shortages/cookingRecordIds` 在 Task 5 定义、Task 6 测试 jsonPath 一致 ✓
- 常量 `SOURCE_MENU/SOURCE_DISH/MENU_STATUS_DONE` 在 CookService 定义，测试用字面值 "menu"/"dish"/"DONE" 对齐 ✓

---

## Execution Handoff

**Plan A complete and saved to `docs/superpowers/plans/2026-07-03-cooking-deduction.md`. Two execution options:**

**1. Subagent-Driven（推荐）** — 每个 task 派新 subagent，task 间复核，迭代快

**2. Inline Execution** — 本会话内逐 task 执行，批量 + 检查点

**后续 Plan（待写）：**
- **Plan B**：采购清单全量 + 余色标记（spec §7 gap ③）
- **Plan C**：备菜模块（spec §7 gap ④）
- **Plan D**（P1）：markDone 改造 + sourceMenuId 溯源 + 采购回写 + 分享导出

**选哪种执行方式？**
