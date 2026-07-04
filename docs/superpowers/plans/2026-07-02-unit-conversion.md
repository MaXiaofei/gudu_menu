# 单位换算服务 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 建立以"克"为内部记账基准的单位换算体系，治好评审 §4/§5/§12（dish_ingredient/pantry/shopping 三方单位口径不一致），让扣减/余色/价格/采购聚合/营养计算都有统一口径。

**Architecture:** 新建换算表 `ingredient_unit_gram` + `UnitConvertService`（克单位直通 + 未配置返回 null 兜底）；`dish_ingredient`/`pantry` 加 `grams` 冗余列，保存路径算换算；所有消费用量的地方（采购聚合、营养计算、价格）改读 `grams`。换算表预置 + 用户可编辑，未配置的食材跳过标灰。

**Tech Stack:** Spring Boot 3 / MyBatis-Plus / Flyway(SQL V 序号) / JUnit5 + MockMvc / Lombok / Sa-Token(@MpPerm)

**Spec:** `docs/superpowers/specs/2026-07-02-unit-conversion-design.md`
**背景:** `docs/redesign-audit.md` §4/§5/§12

---

## File Structure

**新建：**
- `menu-api/sql/V35__unit_conversion.sql` — 建表 + 补字典 + ALTER + 预置 + 回填
- `menu-api/src/main/java/com/gudu/xsd/modules/nutrition/IngredientUnitGram.java` — 换算表实体
- `menu-api/src/main/java/com/gudu/xsd/modules/nutrition/mapper/IngredientUnitGramMapper.java` — Mapper
- `menu-api/src/main/java/com/gudu/xsd/modules/nutrition/UnitConvertService.java` — 换算服务（纯函数 + 查表）
- `menu-api/src/test/java/com/gudu/xsd/modules/nutrition/UnitConvertServiceTest.java` — 纯函数单测
- `menu-api/src/test/java/com/gudu/xsd/modules/nutrition/IngredientUnitGramControllerTest.java` — 换算接口 MockMvc

**修改：**
- `menu-api/src/main/java/com/gudu/xsd/modules/dish/DishIngredient.java` — 加 `unitId`/`grams`
- `menu-api/src/main/java/com/gudu/xsd/modules/pantry/Pantry.java` — 加 `grams`
- `menu-api/src/main/java/com/gudu/xsd/modules/dish/DishService.java` — `saveFull` 算 grams；`computeNutrition` 改读 grams
- `menu-api/src/main/java/com/gudu/xsd/modules/dish/DishQueryService.java:45` — 营养计算改读 grams
- `menu-api/src/main/java/com/gudu/xsd/modules/pantry/PantryService.java` — `saveBatch` 算 grams；新增 `saveWithGrams`
- `menu-api/src/main/java/com/gudu/xsd/modules/pantry/PantryController.java` — `add` 改调 `saveWithGrams`
- `menu-api/src/main/java/com/gudu/xsd/modules/shopping/ShoppingService.java` — `generate` 改读 `di.getGrams()`
- `menu-api/src/main/java/com/gudu/xsd/modules/shopping/ShoppingItemVO.java` — 加 `convertConfigured`（兜底标灰）
- `menu-api/src/main/java/com/gudu/xsd/modules/menu/MenuService.java` — `summary` 价格改按用量算
- `menu-api/src/main/java/com/gudu/xsd/modules/nutrition/IngredientController.java` — 加 GET/PUT 换算接口
- `menu-api/src/main/java/com/gudu/xsd/modules/nutrition/IngredientService.java` — 加换算 CRUD 方法

---

## Task 1: V35 迁移脚本

**Files:**
- Create: `menu-api/sql/V35__unit_conversion.sql`

- [ ] **Step 1: 写迁移脚本**

```sql
-- ============================================================
-- V35 单位换算体系：换算表 + dish_ingredient/pantry 加 grams + 回填
-- 背景：docs/redesign-audit.md §4/§5/§12 三方单位口径不一致
-- 幂等：建表 CREATE IF NOT EXISTS；ALTER 用 information_schema 判列存在
-- ============================================================

-- 1) 补充 unit 字典（V02 仅有 g/ml/个/把，补自然单位用于换算表）
INSERT IGNORE INTO sys_dict(dict_group, name) VALUES
  ('unit','根'),('unit','块'),('unit','头'),('unit','颗'),('unit','条'),
  ('unit','勺'),('unit','斤'),('unit','瓶'),('unit','袋'),('unit','盒'),('unit','杯');

-- 2) 换算表
CREATE TABLE IF NOT EXISTS ingredient_unit_gram (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  ingredient_id   BIGINT NOT NULL,
  unit_id         BIGINT NOT NULL,
  grams_per_unit  DECIMAL(10,2) NOT NULL,
  is_default      TINYINT(1) DEFAULT 0,
  UNIQUE KEY uk_ing_unit (ingredient_id, unit_id),
  KEY idx_ing (ingredient_id)
);

-- 3) 预置高频食材换算（INSERT...SELECT by name，照 V15/V24 范式）
--    覆盖蛋/肉/蔬/调味/主食；is_default=1 标默认单位。其余食材按同范式扩展。
SET @g   := (SELECT id FROM sys_dict WHERE dict_group='unit' AND name='g' LIMIT 1);
SET @ge  := (SELECT id FROM sys_dict WHERE dict_group='unit' AND name='个' LIMIT 1);
SET @gen := (SELECT id FROM sys_dict WHERE dict_group='unit' AND name='根' LIMIT 1);
SET @ba  := (SELECT id FROM sys_dict WHERE dict_group='unit' AND name='把' LIMIT 1);
SET @kuai:= (SELECT id FROM sys_dict WHERE dict_group='unit' AND name='块' LIMIT 1);
SET @jin := (SELECT id FROM sys_dict WHERE dict_group='unit' AND name='斤' LIMIT 1);
SET @shao:= (SELECT id FROM sys_dict WHERE dict_group='unit' AND name='勺' LIMIT 1);
SET @ping:= (SELECT id FROM sys_dict WHERE dict_group='unit' AND name='瓶' LIMIT 1);
SET @dai := (SELECT id FROM sys_dict WHERE dict_group='unit' AND name='袋' LIMIT 1);

INSERT IGNORE INTO ingredient_unit_gram(ingredient_id, unit_id, grams_per_unit, is_default)
  SELECT i.id, @ge, 50, 1 FROM ingredient i WHERE i.name='鸡蛋'
  UNION SELECT i.id, @jin, 500, 1 FROM ingredient i WHERE i.name='五花肉'
  UNION SELECT i.id, @jin, 500, 1 FROM ingredient i WHERE i.name='里脊'
  UNION SELECT i.id, @jin, 500, 1 FROM ingredient i WHERE i.name='牛肉'
  UNION SELECT i.id, @ge, 150, 1 FROM ingredient i WHERE i.name='番茄'
  UNION SELECT i.id, @ge, 150, 1 FROM ingredient i WHERE i.name='土豆'
  UNION SELECT i.id, @gen, 200, 1 FROM ingredient i WHERE i.name='黄瓜'
  UNION SELECT i.id, @gen, 200, 1 FROM ingredient i WHERE i.name='胡萝卜'
  UNION SELECT i.id, @ge, 150, 1 FROM ingredient i WHERE i.name='茄子'
  UNION SELECT i.id, @kuai, 100, 1 FROM ingredient i WHERE i.name='豆腐'
  UNION SELECT i.id, @ba, 100, 1 FROM ingredient i WHERE i.name='大葱'
  UNION SELECT i.id, @kuai, 30, 1 FROM ingredient i WHERE i.name='生姜'
  UNION SELECT i.id, @shao, 5, 1 FROM ingredient i WHERE i.name='盐'
  UNION SELECT i.id, @shao, 5, 1 FROM ingredient i WHERE i.name='冰糖'
  UNION SELECT i.id, @shao, 15, 1 FROM ingredient i WHERE i.name='酱油(生抽)'
  UNION SELECT i.id, @shao, 15, 1 FROM ingredient i WHERE i.name='酱油(老抽)'
  UNION SELECT i.id, @shao, 15, 1 FROM ingredient i WHERE i.name='料酒'
  UNION SELECT i.id, @shao, 15, 1 FROM ingredient i WHERE i.name='食用油'
  UNION SELECT i.id, @dai, 1000, 1 FROM ingredient i WHERE i.name='大米'
  UNION SELECT i.id, @dai, 1000, 1 FROM ingredient i WHERE i.name='面粉';
-- 注：扩展至约 200 种，按 ingredient name 同范式补全（蛋/肉/蔬/调味/主食/水产）

-- 4) dish_ingredient 加 unit_id + grams（幂等 ALTER，照 V30 范式）
SET @c1 := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='dish_ingredient' AND COLUMN_NAME='unit_id');
SET @s1 := IF(@c1=0, 'ALTER TABLE dish_ingredient ADD COLUMN unit_id BIGINT NULL', 'SELECT "exists"');
PREPARE p1 FROM @s1; EXECUTE p1; DEALLOCATE PREPARE p1;

SET @c2 := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='dish_ingredient' AND COLUMN_NAME='grams');
SET @s2 := IF(@c2=0, 'ALTER TABLE dish_ingredient ADD COLUMN grams DECIMAL(12,2) NULL', 'SELECT "exists"');
PREPARE p2 FROM @s2; EXECUTE p2; DEALLOCATE PREPARE p2;

-- 旧数据 amount 是克：unit_id=g, grams=amount（不依赖换算表，100% 回填）
UPDATE dish_ingredient SET unit_id = @g WHERE unit_id IS NULL;
UPDATE dish_ingredient SET grams = amount WHERE grams IS NULL;

-- 5) pantry 加 grams（幂等 ALTER）+ 回填（JOIN 换算表；未覆盖留 NULL=兜底标灰）
SET @c3 := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pantry' AND COLUMN_NAME='grams');
SET @s3 := IF(@c3=0, 'ALTER TABLE pantry ADD COLUMN grams DECIMAL(12,2) NULL', 'SELECT "exists"');
PREPARE p3 FROM @s3; EXECUTE p3; DEALLOCATE PREPARE p3;

UPDATE pantry p
  JOIN ingredient_unit_gram iug ON p.ingredient_id = iug.ingredient_id AND p.unit_id = iug.unit_id
  SET p.grams = p.amount * iug.grams_per_unit
  WHERE p.grams IS NULL;
```

- [ ] **Step 2: 本地跑迁移验证**

在 menu-api 目录连测试库启动（Flyway 自动执行 V35），或手动执行。
Run: `cd menu-api && mvn spring-boot:run`（启动会自动跑迁移；看到 `Migrating V35` 即成功）
Expected: 启动无报错，日志含 `Successfully applied V35__unit_conversion.sql`

- [ ] **Step 3: 验证回填结果**

```bash
# 连测试库校验（按本地实际端口/账号；开发环境 13306）
mysql -h127.0.0.1 -P13306 -uroot -p yanhuo_test -e "
  SELECT COUNT(*) AS total, SUM(grams IS NOT NULL) AS filled FROM dish_ingredient;
  SELECT COUNT(*) AS total, SUM(grams IS NOT NULL) AS filled FROM pantry;
  SELECT COUNT(*) FROM ingredient_unit_gram;"
```
Expected: dish_ingredient filled=total（100%）；pantry filled≤total（仅换算表覆盖的）；ingredient_unit_gram ≥ 20。

- [ ] **Step 4: Commit**

```bash
git add menu-api/sql/V35__unit_conversion.sql
git commit -m "feat(sql): V35 单位换算体系-换算表+grams列+回填"
```

---

## Task 2: 实体 + Mapper（IngredientUnitGram / DishIngredient / Pantry）

**Files:**
- Create: `menu-api/src/main/java/com/gudu/xsd/modules/nutrition/IngredientUnitGram.java`
- Create: `menu-api/src/main/java/com/gudu/xsd/modules/nutrition/mapper/IngredientUnitGramMapper.java`
- Modify: `menu-api/src/main/java/com/gudu/xsd/modules/dish/DishIngredient.java`
- Modify: `menu-api/src/main/java/com/gudu/xsd/modules/pantry/Pantry.java`

- [ ] **Step 1: 新建 IngredientUnitGram 实体**

```java
package com.gudu.xsd.modules.nutrition;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("ingredient_unit_gram")
public class IngredientUnitGram {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long ingredientId;

    /** → sys_dict(group=unit)。 */
    private Long unitId;

    /** 1 个该单位 = 多少克。 */
    private BigDecimal gramsPerUnit;

    /** 是否该食材的默认单位（录入/计价用）。 */
    private Integer isDefault;
}
```

- [ ] **Step 2: 新建 IngredientUnitGramMapper**

```java
package com.gudu.xsd.modules.nutrition.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gudu.xsd.modules.nutrition.IngredientUnitGram;

public interface IngredientUnitGramMapper extends BaseMapper<IngredientUnitGram> {
}
```

- [ ] **Step 3: DishIngredient 加 unitId / grams**

在 `DishIngredient.java` 的 `amount` 字段后追加：

```java
    /** 用量数量（对应 unitId 的个数，如 2 表示 2 个/2 把）。 */
    private BigDecimal amount;   // 原字段，注释从"用量克数"改为"用量数量"

    /** 自然单位 → sys_dict(group=unit)。旧数据 = 'g'。 */
    private Long unitId;

    /** 内部记账基准克数 = amount × grams_per_unit（保存时算，查询零换算）。 */
    private BigDecimal grams;
```
> 即：把原 `/** 用量克数。 */ private BigDecimal amount;` 替换为上面三段。

- [ ] **Step 4: Pantry 加 grams**

在 `Pantry.java` 的 `unitId` 字段后追加：

```java
    private Long unitId;

    /** 内部记账基准克数（盘点用 amount+unitId，扣减/余色用 grams）。 */
    private BigDecimal grams;
```

- [ ] **Step 5: 编译验证**

Run: `cd menu-api && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add menu-api/src/main/java/com/gudu/xsd/modules/nutrition/IngredientUnitGram.java \
        menu-api/src/main/java/com/gudu/xsd/modules/nutrition/mapper/IngredientUnitGramMapper.java \
        menu-api/src/main/java/com/gudu/xsd/modules/dish/DishIngredient.java \
        menu-api/src/main/java/com/gudu/xsd/modules/pantry/Pantry.java
git commit -m "feat: 换算表实体+Mapper, dish_ingredient/pantry 加 grams 字段"
```

---

## Task 3: UnitConvertService（TDD · 纯函数 + 查表封装）

**Files:**
- Create: `menu-api/src/main/java/com/gudu/xsd/modules/nutrition/UnitConvertService.java`
- Test: `menu-api/src/test/java/com/gudu/xsd/modules/nutrition/UnitConvertServiceTest.java`

- [ ] **Step 1: 写失败测试（纯函数 toGrams + isGramUnit）**

```java
package com.gudu.xsd.modules.nutrition;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UnitConvertService 纯函数测试（算法地基，不依赖 Spring）。
 * 参照 PantryServiceTest / ShoppingAggregatorTest 范式：new UnitConvertService(null)。
 */
class UnitConvertServiceTest {

    @Test
    void 克单位直通_无需查表() {
        // unitId=1 假装是 'g'；构造时传入 gramUnitIds=Set.of(1L)
        UnitConvertService s = new UnitConvertService(java.util.Set.of(1L));
        assertThat(s.toGrams(new BigDecimal("300"), 1L, null))
                .isEqualByComparingTo("300");
    }

    @Test
    void 非克单位_按换算系数算() {
        UnitConvertService s = new UnitConvertService(java.util.Set.of(1L));
        // 2 个 × 50g/个 = 100g
        assertThat(s.toGrams(new BigDecimal("2"), 2L, new BigDecimal("50")))
                .isEqualByComparingTo("100");
    }

    @Test
    void 非克单位_未配置换算返回null() {
        UnitConvertService s = new UnitConvertService(java.util.Set.of(1L));
        assertThat(s.toGrams(new BigDecimal("2"), 2L, null)).isNull();
    }

    @Test
    void amount为null返回null() {
        UnitConvertService s = new UnitConvertService(java.util.Set.of(1L));
        assertThat(s.toGrams(null, 2L, new BigDecimal("50"))).isNull();
    }
}
```

- [ ] **Step 2: 跑测试验证失败**

Run: `cd menu-api && mvn test -Dtest=UnitConvertServiceTest -q`
Expected: FAIL（`UnitConvertService` 不存在 / 构造不匹配）

- [ ] **Step 3: 实现 UnitConvertService**

```java
package com.gudu.xsd.modules.nutrition;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gudu.xsd.modules.dict.SysDict;
import com.gudu.xsd.modules.dict.mapper.DictMapper;
import com.gudu.xsd.modules.nutrition.mapper.IngredientUnitGramMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 单位换算服务（评审 B · 最高杠杆地基）。
 *
 * <p>克为内部记账基准。toGrams 是纯函数（可单测）：
 * 克单位直通（1g=1g，无需为每食材建"克=1.0"冗余行）；
 * 非克单位按 gramsPerUnit 算；未配置返回 null（兜底，不硬算）。
 *
 * <p>参照 PantryService 范式：纯函数算法地基 + 显式 Mapper 构造。
 * 测试 new UnitConvertService(null) / (null, gramUnitIds)：故双构造。
 */
@Service
public class UnitConvertService {

    private final IngredientUnitGramMapper unitGramMapper;
    private final DictMapper dictMapper;
    private Set<Long> gramUnitIds = new HashSet<>();

    /** 运行期构造（Spring 注入两个 Mapper）。 */
    @RequiredArgsConstructor
    public UnitConvertService(IngredientUnitGramMapper unitGramMapper, DictMapper dictMapper) {
        this.unitGramMapper = unitGramMapper;
        this.dictMapper = dictMapper;
    }

    /** 测试构造：只传 gramUnitIds（纯函数测试，mapper=null 不触达）。 */
    public UnitConvertService(Set<Long> gramUnitIds) {
        this.unitGramMapper = null;
        this.dictMapper = null;
        this.gramUnitIds = gramUnitIds == null ? new HashSet<>() : new HashSet<>(gramUnitIds);
    }

    /** 启动时缓存 'g'/'克' 的 unit id（非克单位判定用）。 */
    @PostConstruct
    void initGramUnitIds() {
        if (dictMapper == null) return;
        List<SysDict> grams = dictMapper.selectList(
                new QueryWrapper<SysDict>().eq("dict_group", "unit").in("name", List.of("g", "克")));
        gramUnitIds.clear();
        grams.forEach(d -> gramUnitIds.add(d.getId()));
    }

    /** unitId 是否为"克"单位。 */
    public boolean isGramUnit(Long unitId) {
        return unitId != null && gramUnitIds.contains(unitId);
    }

    /**
     * 纯函数：amount + unitId + gramsPerUnit → 克。
     * 克单位直通；非克按系数；未配置(amount/unitId/gramsPerUnit 缺)返回 null。
     */
    public BigDecimal toGrams(BigDecimal amount, Long unitId, BigDecimal gramsPerUnit) {
        if (amount == null) return null;
        if (isGramUnit(unitId)) return amount;
        if (gramsPerUnit == null) return null;
        return amount.multiply(gramsPerUnit);
    }

    /** 查表：「食材 × 单位」每单位克数；未配置返回 null。 */
    public BigDecimal gramsPerUnit(Long ingredientId, Long unitId) {
        if (unitGramMapper == null || ingredientId == null || unitId == null) return null;
        IngredientUnitGram row = unitGramMapper.selectOne(
                new QueryWrapper<IngredientUnitGram>()
                        .eq("ingredient_id", ingredientId)
                        .eq("unit_id", unitId));
        return row == null ? null : row.getGramsPerUnit();
    }

    /** 该食材默认单位（is_default=1）的每单位克数；未配置返回 null。 */
    public BigDecimal defaultGramsPerUnit(Long ingredientId) {
        if (unitGramMapper == null || ingredientId == null) return null;
        IngredientUnitGram row = unitGramMapper.selectOne(
                new QueryWrapper<IngredientUnitGram>()
                        .eq("ingredient_id", ingredientId)
                        .eq("is_default", 1));
        return row == null ? null : row.getGramsPerUnit();
    }

    /** 薄封装：查表 + 纯函数换算。保存路径调用此方法。 */
    public BigDecimal toGramsFor(Long ingredientId, BigDecimal amount, Long unitId) {
        return toGrams(amount, unitId, gramsPerUnit(ingredientId, unitId));
    }
}
```

> 注意 `@RequiredArgsConstructor` 与手写双构造冲突——这里用两个手写构造，去掉类上的 `@RequiredArgsConstructor`。最终类签名：`@Service public class UnitConvertService {`（无 @RequiredArgsConstructor），两个手写构造如上。

- [ ] **Step 4: 跑测试验证通过**

Run: `cd menu-api && mvn test -Dtest=UnitConvertServiceTest -q`
Expected: 4 tests PASS

- [ ] **Step 5: Commit**

```bash
git add menu-api/src/main/java/com/gudu/xsd/modules/nutrition/UnitConvertService.java \
        menu-api/src/test/java/com/gudu/xsd/modules/nutrition/UnitConvertServiceTest.java
git commit -m "feat: UnitConvertService 单位换算(克直通+null兜底)+纯函数单测"
```

---

## Task 4: DishService 保存接入换算 + 营养计算改 grams

**Files:**
- Modify: `menu-api/src/main/java/com/gudu/xsd/modules/dish/DishService.java`（saveFull + computeNutrition）
- Modify: `menu-api/src/main/java/com/gudu/xsd/modules/dish/DishQueryService.java:45`

- [ ] **Step 1: DishService 注入 UnitConvertService**

在 `DishService` 字段区（第 33-39 行 private final 处）追加：

```java
    private final com.gudu.xsd.modules.nutrition.UnitConvertService unitConvert;
```

- [ ] **Step 2: saveFull 保存 dish_ingredient 时算 grams**

把 `DishService.saveFull` 第 66-72 行：

```java
        if (dto.getIngredients() != null) {
            for (DishIngredient ing : dto.getIngredients()) {
                ing.setId(null);
                ing.setDishId(dishId);
                dishIngMapper.insert(ing);
            }
        }
```

替换为：

```java
        if (dto.getIngredients() != null) {
            for (DishIngredient ing : dto.getIngredients()) {
                ing.setId(null);
                ing.setDishId(dishId);
                ing.setGrams(unitConvert.toGramsFor(ing.getIngredientId(), ing.getAmount(), ing.getUnitId()));
                dishIngMapper.insert(ing);
            }
        }
```

- [ ] **Step 3: computeNutrition 改读 grams（DishService.java:243）**

把第 243 行：

```java
                items.add(new NutritionCalcService.Item(n.getMetricId(), n.getValue(), di.getAmount()));
```

替换为：

```java
                BigDecimal qty = di.getGrams() != null ? di.getGrams() : di.getAmount();
                items.add(new NutritionCalcService.Item(n.getMetricId(), n.getValue(), qty));
```

> 说明：优先用 grams（内部克基准，与营养 per 100g 口径一致）；旧数据迁移后 grams 已 = amount，兼容。`BigDecimal qty` 需确保 import（已 import java.math.BigDecimal）。

- [ ] **Step 4: DishQueryService:45 同步改**

`DishQueryService.java:45` 同样把 `di.getAmount()` 换成优先 grams：

```java
                BigDecimal qty = di.getGrams() != null ? di.getGrams() : di.getAmount();
                items.add(new NutritionCalcService.Item(n.getMetricId(), n.getValue(), qty));
```

> 若 DishQueryService 未 import BigDecimal，补 `import java.math.BigDecimal;`。

- [ ] **Step 5: 编译 + 跑既有营养/菜品测试**

Run: `cd menu-api && mvn test -Dtest=DishServiceTest -q`
Expected: PASS（既有测试不回归；营养用克数算，旧数据 grams=amount 结果不变）

- [ ] **Step 6: Commit**

```bash
git add menu-api/src/main/java/com/gudu/xsd/modules/dish/DishService.java \
        menu-api/src/main/java/com/gudu/xsd/modules/dish/DishQueryService.java
git commit -m "feat: dish_ingredient 保存算 grams, 营养计算改读 grams"
```

---

## Task 5: PantryService 保存接入换算 + Controller 改调

**Files:**
- Modify: `menu-api/src/main/java/com/gudu/xsd/modules/pantry/PantryService.java`
- Modify: `menu-api/src/main/java/com/gudu/xsd/modules/pantry/PantryController.java`

- [ ] **Step 1: PantryService 注入 UnitConvertService**

在 `PantryService` 字段区（第 37-38 行）追加：

```java
    private com.gudu.xsd.modules.nutrition.UnitConvertService unitConvert;
```

并加 setter（照 dictMapper 的 setter 范式，第 48-51 行后）：

```java
    @Autowired
    public void setUnitConvert(com.gudu.xsd.modules.nutrition.UnitConvertService unitConvert) {
        this.unitConvert = unitConvert;
    }
```

> 走 setter 注入与 dictMapper 同策略：兼容测试 `new PantryService(null)`。

- [ ] **Step 2: saveBatch 算 grams（第 229-236 行 Pantry 构造处）**

把：

```java
            Pantry p = new Pantry();
            p.setIngredientId(ingredientId);
            p.setAmount(item.getAmount() != null && item.getAmount().compareTo(BigDecimal.ZERO) > 0
                    ? item.getAmount() : BigDecimal.ONE);
            p.setUnitId(unitId);
            p.setExpireDate(item.getExpireDate());
            save(p);
```

替换为：

```java
            Pantry p = new Pantry();
            p.setIngredientId(ingredientId);
            BigDecimal amt = item.getAmount() != null && item.getAmount().compareTo(BigDecimal.ZERO) > 0
                    ? item.getAmount() : BigDecimal.ONE;
            p.setAmount(amt);
            p.setUnitId(unitId);
            p.setExpireDate(item.getExpireDate());
            p.setGrams(unitConvert == null ? null
                    : unitConvert.toGramsFor(ingredientId, amt, unitId));
            save(p);
```

- [ ] **Step 3: 新增 saveWithGrams（单条保存，供 controller add 用）**

在 `PantryService` 的 `deduct` 方法前（第 159 行 `// ===== 扣减 ====` 注释前）插入：

```java
    /** 单条保存库存（含 grams 换算）。供 controller add 调用，确保走换算。 */
    @org.springframework.transaction.annotation.Transactional
    public void saveWithGrams(Pantry pantry) {
        if (pantry != null) {
            pantry.setGrams(unitConvert == null ? null
                    : unitConvert.toGramsFor(pantry.getIngredientId(), pantry.getAmount(), pantry.getUnitId()));
            save(pantry);
        }
    }

```

- [ ] **Step 4: PantryController.add 改调 saveWithGrams**

把 `PantryController.java:67-71`：

```java
    @PostMapping
    public R<Long> add(@RequestBody Pantry pantry) {
        svc.save(pantry);
        return R.ok(pantry.getId());
    }
```

替换为：

```java
    @PostMapping
    public R<Long> add(@RequestBody Pantry pantry) {
        svc.saveWithGrams(pantry);
        return R.ok(pantry.getId());
    }
```

- [ ] **Step 5: 跑 pantry 测试不回归**

Run: `cd menu-api && mvn test -Dtest=PantryServiceTest,PantryControllerTest -q`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add menu-api/src/main/java/com/gudu/xsd/modules/pantry/PantryService.java \
        menu-api/src/main/java/com/gudu/xsd/modules/pantry/PantryController.java
git commit -m "feat: pantry 保存接入换算算 grams, controller add 走 saveWithGrams"
```

---

## Task 6: ShoppingService.generate 改读 grams + 兜底标灰

**Files:**
- Modify: `menu-api/src/main/java/com/gudu/xsd/modules/shopping/ShoppingService.java`（generate）
- Modify: `menu-api/src/main/java/com/gudu/xsd/modules/shopping/ShoppingItemVO.java`

- [ ] **Step 1: ShoppingItemVO 加 convertConfigured 字段**

在 `ShoppingItemVO` 加字段（兜底标灰用）：

```java
    /** 该项用量是否已配置换算（false=未配置，前端标灰提示）。 */
    private Boolean convertConfigured;
```

- [ ] **Step 2: generate 改读 di.getGrams()**

把 `ShoppingService.java:130-136`：

```java
        for (DishIngredient di : dis) {
            Ingredient ing = ingById.get(di.getIngredientId());
            if (ing == null) continue;
            BigDecimal factor = factorByDish.getOrDefault(di.getDishId(), BigDecimal.ONE);
            BigDecimal amount = di.getAmount() == null ? BigDecimal.ZERO : di.getAmount();
            usages.add(new Usage(ing.getId(), amount.multiply(factor), ing.getPurchaseCategoryId()));
        }
```

替换为：

```java
        for (DishIngredient di : dis) {
            Ingredient ing = ingById.get(di.getIngredientId());
            if (ing == null) continue;
            BigDecimal factor = factorByDish.getOrDefault(di.getDishId(), BigDecimal.ONE);
            // 改读 grams（内部克基准）；旧数据迁移后 grams=amount，兼容
            BigDecimal baseGrams = di.getGrams() != null ? di.getGrams()
                    : (di.getAmount() != null ? di.getAmount() : BigDecimal.ZERO);
            usages.add(new Usage(ing.getId(), baseGrams.multiply(factor), ing.getPurchaseCategoryId()));
        }
```

- [ ] **Step 3: fillVoNames 标 convertConfigured**

在 `ShoppingService.fillVoNames` 的 `for (ShoppingItem it : rows)` 循环里（设置 vo 各字段后）追加：

```java
            // 兜底标灰：有 ingredientId 且 referenceGrams 为 null/0 视为未配置换算
            vo.setConvertConfigured(it.getIngredientId() == null
                    || (it.getReferenceGrams() != null && it.getReferenceGrams().compareTo(BigDecimal.ZERO) > 0));
```

> 采购清单未配置换算的项（referenceGrams=0 或空）前端标灰"未配置换算"。手动添加的自定义项（ingredientId=null）不标灰。

- [ ] **Step 4: 跑采购测试不回归**

Run: `cd menu-api && mvn test -Dtest=ShoppingAggregatorTest,ShoppingServiceTest,ShoppingControllerTest -q`
Expected: PASS（ShoppingAggregator 纯函数不变；ShoppingService 输入源变 grams）

- [ ] **Step 5: Commit**

```bash
git add menu-api/src/main/java/com/gudu/xsd/modules/shopping/ShoppingService.java \
        menu-api/src/main/java/com/gudu/xsd/modules/shopping/ShoppingItemVO.java
git commit -m "feat: 采购聚合改读 grams(referenceGrams 名实相符) + 兜底标灰"
```

---

## Task 7: MenuService.summary 价格改按用量算

**Files:**
- Modify: `menu-api/src/main/java/com/gudu/xsd/modules/menu/MenuService.java`

- [ ] **Step 1: MenuService 注入新依赖**

在 `MenuService` 字段区（第 26-29 行）追加：

```java
    private final com.gudu.xsd.modules.dish.mapper.DishIngredientMapper dishIngredientMapper;
    private final com.gudu.xsd.modules.nutrition.mapper.IngredientMapper ingredientMapper;
    private final com.gudu.xsd.modules.nutrition.UnitConvertService unitConvert;
```

> import 补：`com.gudu.xsd.modules.dish.DishIngredient` / `com.gudu.xsd.modules.nutrition.Ingredient`（Dish 已 import）。@RequiredArgsConstructor 会自动生成含新参数的构造。

- [ ] **Step 2: 抽取「按用量算单菜价」私有方法**

在 `MenuService.summary` 方法后追加：

```java
    /** 按用量算单菜 1 份价格 = Σ(dish_ingredient.grams × 每克单价)。
     *  每克单价 = ingredient.price / defaultGramsPerUnit；未配置换算的食材跳过。 */
    private BigDecimal priceByIngredients(Long dishId) {
        List<DishIngredient> dis = dishIngredientMapper.selectList(
                new QueryWrapper<DishIngredient>().eq("dish_id", dishId));
        if (dis.isEmpty()) return BigDecimal.ZERO;
        List<Long> ingIds = dis.stream().map(DishIngredient::getIngredientId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        if (ingIds.isEmpty()) return BigDecimal.ZERO;
        Map<Long, Ingredient> ingById = ingredientMapper.selectBatchIds(ingIds).stream()
                .collect(java.util.stream.Collectors.toMap(Ingredient::getId, i -> i, (a, b) -> a));
        BigDecimal sum = BigDecimal.ZERO;
        for (DishIngredient di : dis) {
            Ingredient ing = ingById.get(di.getIngredientId());
            if (ing == null || ing.getPrice() == null) continue;
            BigDecimal gpu = unitConvert.defaultGramsPerUnit(di.getIngredientId());
            if (gpu == null || gpu.signum() == 0) continue;  // 未配置换算，跳过
            BigDecimal grams = di.getGrams() != null ? di.getGrams() : di.getAmount();
            if (grams == null) continue;
            // 每克单价 × 克数
            sum = sum.add(ing.getPrice().divide(gpu, 6, java.math.RoundingMode.HALF_UP).multiply(grams));
        }
        return sum;
    }
```

- [ ] **Step 3: summary 改用 priceByIngredients**

把 `MenuService.java:64-75` 的 `summary` 方法里第 69 行：

```java
            BigDecimal price = (dish != null && dish.getPrice() != null) ? dish.getPrice() : BigDecimal.ZERO;
```

替换为：

```java
            BigDecimal price = (dish != null) ? priceByIngredients(dish.getId()) : BigDecimal.ZERO;
```

> MenuCalcService.totalPrice 纯函数不变（仍是 Σ price×factor）；变的只是 price 来源：从 dish 整体价 → 按用量算。

- [ ] **Step 4: 跑菜单测试不回归**

Run: `cd menu-api && mvn test -Dtest=*Menu*Test -q`
Expected: PASS（若有 MenuCalcService/MenuController 测试；price 来源变但签名不变）

- [ ] **Step 5: Commit**

```bash
git add menu-api/src/main/java/com/gudu/xsd/modules/menu/MenuService.java
git commit -m "feat: 菜单价格改按食材用量×每克单价计算(评审§12)"
```

---

## Task 8: 换算可编辑接口 GET/PUT + MockMvc（TDD）

**Files:**
- Modify: `menu-api/src/main/java/com/gudu/xsd/modules/nutrition/IngredientService.java`
- Modify: `menu-api/src/main/java/com/gudu/xsd/modules/nutrition/IngredientController.java`
- Test: `menu-api/src/test/java/com/gudu/xsd/modules/nutrition/IngredientUnitGramControllerTest.java`

- [ ] **Step 1: IngredientService 加换算 CRUD 方法**

在 `IngredientService` 注入 mapper（字段区加）：

```java
    private final com.gudu.xsd.modules.nutrition.mapper.IngredientUnitGramMapper unitGramMapper;
```

在类末尾追加方法：

```java
    /** 列出某食材的全部换算（含默认标记）。 */
    public List<com.gudu.xsd.modules.nutrition.IngredientUnitGram> listUnitGrams(Long ingredientId) {
        return unitGramMapper.selectList(new QueryWrapper<com.gudu.xsd.modules.nutrition.IngredientUnitGram>()
                .eq("ingredient_id", ingredientId).orderByDesc("is_default"));
    }

    /** 整体替换某食材的换算（用户编辑入口）：先删后插，保证 is_default 唯一。 */
    @Transactional
    public void replaceUnitGrams(Long ingredientId,
                                 List<com.gudu.xsd.modules.nutrition.IngredientUnitGram> rows) {
        unitGramMapper.delete(new QueryWrapper<com.gudu.xsd.modules.nutrition.IngredientUnitGram>()
                .eq("ingredient_id", ingredientId));
        if (rows == null) return;
        boolean hasDefault = false;
        for (com.gudu.xsd.modules.nutrition.IngredientUnitGram r : rows) {
            r.setId(null);
            r.setIngredientId(ingredientId);
            if (r.getIsDefault() != null && r.getIsDefault() == 1) {
                if (hasDefault) r.setIsDefault(0);  // 只允许一个默认
                else hasDefault = true;
            }
            unitGramMapper.insert(r);
        }
    }
```

> @RequiredArgsConstructor 自动含新 mapper 参数。补 import `com.baomidou.mybatisplus.core.conditions.query.QueryWrapper`（已存在）。

- [ ] **Step 2: IngredientController 加两个端点**

在 `IngredientController` 的 `nutrition` 端点后追加：

```java
    /** 某食材的单位换算列表（用户可编辑）。 */
    @GetMapping("/{id}/unit-grams")
    public R<List<IngredientUnitGram>> unitGrams(@PathVariable Long id) {
        return R.ok(svc.listUnitGrams(id));
    }

    /** 整体替换某食材的单位换算（用户编辑保存）。 */
    @PutMapping("/{id}/unit-grams")
    public R<?> saveUnitGrams(@PathVariable Long id,
                              @RequestBody List<IngredientUnitGram> rows) {
        svc.replaceUnitGrams(id, rows);
        return R.ok(null);
    }
```

> 补 import `com.gudu.xsd.modules.nutrition.IngredientUnitGram`、`java.util.List`（已存在）。

- [ ] **Step 3: 写 MockMvc 测试（先失败）**

```java
package com.gudu.xsd.modules.nutrition;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 换算接口 MockMvc：照 ShoppingControllerTest 范式（排除 SaTokenConfig + mock service）。 */
@WebMvcTest(
        value = IngredientController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = com.gudu.xsd.config.SaTokenConfig.class))
class IngredientUnitGramControllerTest {

    @Autowired MockMvc mvc;
    @MockBean IngredientService svc;
    private final ObjectMapper om = new ObjectMapper();

    @Test
    void GET_食材换算列表() throws Exception {
        IngredientUnitGram g = new IngredientUnitGram();
        g.setIngredientId(1L); g.setUnitId(2L); g.setGramsPerUnit(new BigDecimal("50")); g.setIsDefault(1);
        given(svc.listUnitGrams(1L)).willReturn(List.of(g));

        mvc.perform(get("/ingredient/1/unit-grams"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].gramsPerUnit").value(50));
    }

    @Test
    void PUT_替换食材换算() throws Exception {
        IngredientUnitGram g = new IngredientUnitGram();
        g.setUnitId(2L); g.setGramsPerUnit(new BigDecimal("55")); g.setIsDefault(1);
        mvc.perform(put("/ingredient/1/unit-grams")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(List.of(g))))
                .andExpect(status().isOk());
        verify(svc).replaceUnitGrams(1L, List.of(g));
    }
}
```

- [ ] **Step 4: 跑测试验证**

Run: `cd menu-api && mvn test -Dtest=IngredientUnitGramControllerTest -q`
Expected: 2 tests PASS

- [ ] **Step 5: Commit**

```bash
git add menu-api/src/main/java/com/gudu/xsd/modules/nutrition/IngredientService.java \
        menu-api/src/main/java/com/gudu/xsd/modules/nutrition/IngredientController.java \
        menu-api/src/test/java/com/gudu/xsd/modules/nutrition/IngredientUnitGramControllerTest.java
git commit -m "feat: 食材单位换算可编辑接口 GET/PUT + MockMvc"
```

---

## Task 9: 全量回归

- [ ] **Step 1: analyze 0 error**

Run: `cd menu-flutter && flutter analyze 2>/dev/null; cd ../menu-api && mvn compile -q`
Expected: menu-api BUILD SUCCESS（Flutter 不受影响，跳过即可）

- [ ] **Step 2: 跑全部单测**

Run: `cd menu-api && mvn test -q`
Expected: 全绿（UnitConvertServiceTest 新增 4 + 既有不回归）

- [ ] **Step 3: 跑 E2E**

Run: `cd menu-api && mvn test -Dtest=GuduE2EFlowTest -q`
Expected: 5 场景全绿（排菜去重/采购合并/临期通知/饮食汇总/权限拒绝；采购与营养走 grams 后行为不变）

- [ ] **Step 4: 更新存档 gap 状态**

编辑 `~/.claude/projects/-Users-maxiaofei-mygithub-menu-new/memory/gudu-product-redesign.md`，把「单位换算服务」一条从"方案设计中"改为"已落地，见 docs/superpowers/plans/2026-07-02-unit-conversion.md"。

- [ ] **Step 5: Commit 回归通过**

```bash
git add -A
git commit -m "test: 单位换算体系全量回归通过(单测+E2E)"
```

---

## Self-Review 清单（执行者每 task 末自检）

1. **spec 覆盖**：8 项交付（迁移/服务/保存接入/聚合/价格/接口/兜底/营养连锁）→ Task 1-8 全覆盖，Task 9 回归。
2. **连锁点**：营养计算两处（DishService:243 + DishQueryService:45）→ Task 4 Step 3-4。这是 spec 写作时遗漏、读码时发现的，已补进计划。
3. **类型一致**：`getGrams()`/`setGrams()` 在 DishIngredient/Pantry 统一；`toGrams`(纯) vs `toGramsFor`(查表) 命名贯穿；`IngredientUnitGram.gramsPerUnit` 与 `UnitConvertService.gramsPerUnit()` 一致。
4. **兼容**：旧数据迁移后 `grams=amount`，所有"改读 grams"处都 `di.getGrams() != null ? grams : amount` 兜底，旧库零破坏。
