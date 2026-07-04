# 单位换算服务设计（评审 B · 最高杠杆地基）

> **日期**：2026-07-02
> **状态**：待用户 review
> **背景**：`docs/redesign-audit.md` §4/§5/§12 的根因——`dish_ingredient` / `pantry` / `shopping` 三方单位口径不一致且无换算，所有"自动计算"（扣减/余色/价格/采购聚合）建立在错误前提上。本 spec 是修复这些的**地基**，阻塞扣减链、余色标记、价格计算三件事。
> **关联**：`gudu-product-redesign.md` 待补 gap「单位换算服务」；下游依赖 spec（auto 扣减触发链、采购余色标记）将引用本 spec 的 `grams` 契约。

---

## 一、已确认决策（2026-07-02）

| # | 决策 | 选择 |
|---|---|---|
| 1 | 录入模型 | **自然单位录入 + 后端换算表 + 克为内部基准**（非强制克、非双轨必填） |
| 2 | 换算表可编辑 | ✅ 用户可在食材详情页改"1 个 = ? g" |
| 3 | pantry 冗余 grams | ✅ 存储 `grams` 列，避免运行时多批次实时换算 |
| 4 | 兜底（未配置换算） | **跳过 + 标灰提示**，不硬算默认值 |
| 5 | 价格基准 | **每「默认单位」计价**，复用换算表转克 |
| 6 | 旧数据 | 种子确认是克（番茄炒蛋番茄 300/鸡蛋 180、红烧肉五花 350/冰糖 15…），迁移 straightforward |

---

## 二、数据模型变更

### 2.1 新表 `ingredient_unit_gram`（换算表，核心）

```sql
CREATE TABLE ingredient_unit_gram (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  ingredient_id   BIGINT NOT NULL,
  unit_id         BIGINT NOT NULL,          -- → sys_dict(group=unit)
  grams_per_unit  DECIMAL(10,2) NOT NULL,   -- 1 个该单位 = 多少克
  is_default      TINYINT(1) DEFAULT 0,     -- 该食材的默认录入/计价单位
  UNIQUE KEY uk_ing_unit (ingredient_id, unit_id),
  KEY idx_ing (ingredient_id)
);
```

- 一个食材可有多条（鸡蛋：个=50g 默认、盒=300g；葱：把=100g 默认、根=15g）。
- `is_default=1` 的行决定该食材录入时的默认单位 + 价格计价单位。每食材至多一条 `is_default=1`（应用层约束）。

### 2.2 改 `dish_ingredient`（评审 §4 根因）

| 字段 | 现在 | 变更后 |
|---|---|---|
| `amount` | DECIMAL(10,2) "用量克数"（语义错） | **数量**（如 2），语义 = 该 `unit_id` 的个数 |
| `unit_id` | ❌ 无 | ✅ 新增（自然单位；旧数据 = "克"单位） |
| `grams` | ❌ 无 | ✅ 新增（冗余 = `amount × grams_per_unit`，**内部记账基准**） |

```sql
ALTER TABLE dish_ingredient ADD COLUMN unit_id BIGINT NULL;
ALTER TABLE dish_ingredient ADD COLUMN grams  DECIMAL(12,2) NULL;
```

### 2.3 改 `pantry`（评审 §5 根因）

保持 `amount + unit_id`（用户盘点用自然单位），**新增 `grams` 冗余**（当前余量总克数）。

```sql
ALTER TABLE pantry ADD COLUMN grams DECIMAL(12,2) NULL;
```

> 这一步治好 §5："批次制 / 单条扣减 / 食材聚合"三套语义打架——扣减与余色判定一律按 `ingredient_id` 汇总各批次 `grams` 比较，展示时换回 `amount + unit_id`。

### 2.4 `ingredient.price` 语义明确化

`ingredient.price` = **每「默认单位」价格（元）**。若表已存在 `price` 列则沿用并修正语义；不存在则新增 `price DECIMAL(10,2) NULL`。

> 例：鸡蛋默认单位"个" → price=1.0（元/个）；猪肉默认单位"斤" → price=15（元/斤）。

---

## 三、`UnitConvertService` 设计

纯函数 + 换算表 Mapper，参照 `PantryService.isLow` / `MenuCalcService` 范式（算法地基，可单测）。

```java
@Service
@RequiredArgsConstructor
public class UnitConvertService {
    private final IngredientUnitGramMapper mapper;

    /** 查「食材 × 单位」每单位克数；未配置返回 null（调用方按兜底处理）。 */
    public BigDecimal gramsPerUnit(Long ingredientId, Long unitId) { ... }

    /** 该食材的默认单位克数（is_default=1 的行）。 */
    public BigDecimal defaultGramsPerUnit(Long ingredientId) { ... }

    /** 把 amount + unit 换算成克。
     *  克单位直通（1 克 = 1 克，无需为每食材建"克=1.0"冗余行）；
     *  非克单位查换算表，未配置 → 返回 null（不硬算）。 */
    public BigDecimal toGrams(Long ingredientId, BigDecimal amount, Long unitId) {
        if (amount == null) return null;
        if (isGramUnit(unitId)) return amount;            // 克是基础单位
        BigDecimal gpu = gramsPerUnit(ingredientId, unitId);
        return gpu == null ? null : amount.multiply(gpu);
    }

    /** unitId 是否为"克"单位（sys_dict name in {'克','g'}，启动时缓存 id）。 */
    boolean isGramUnit(Long unitId) { ... }
}
```

**调用点**（保存路径算 `grams` 冗余，查询/计算零运行时换算）：
- `dish_ingredient` 保存（菜谱创建/编辑）→ `grams = toGrams(ingId, amount, unitId)`
- `pantry` 保存（入库/盘点/批量添加）→ `grams = toGrams(ingId, amount, unitId)`
- `pantry` 扣减 → 直接加减 `grams`（不再触换算），同时按 `grams / grams_per_unit` 反推 `amount` 展示值（反推可能成小数，如 3.5 个；展示精度与四舍五入策略属扣减链 spec 细节）

---

## 四、换算表预置数据策略

- **预置范围**：常见食材约 200 种的标准换算，随种子 SQL 下发。覆盖：
  - 蛋白质：鸡蛋（个 50）、五花肉/里脊（斤 500）、鱼（条 500）、虾（斤 500）…
  - 蔬果：番茄/土豆（个 150）、黄瓜/胡萝卜（根 200）、葱（把 100 / 根 15）…
  - 调味（液体）：生抽/老抽/醋/料酒/蚝油（勺 15 / 瓶 500）…
  - 调味（固体）：盐/糖/味精（勺 5 / 袋 400）…
  - 主食：米/面（袋 1000）、面条（把 100）…
- **单位字典对齐**：`sys_dict(group=unit)` 需含克(g)、个、根、把、块、头、颗、条、勺、斤、瓶、袋、盒、杯、串。迁移前先核 V02/V27 字典，缺则补。
- **用户可编辑**：食材详情页可改"1 个 = ? g"（增/改 `ingredient_unit_gram` 行），换算与价格共用此表，改一处两处同步。
- **兜底**：`toGrams` 返回 null 的食材，在扣减/余色/价格/采购聚合处**跳过该项并标灰提示「未配置换算」**，不参与计算（比硬算 100g 诚实，避免隐性错误累积）。

---

## 五、价格模型（决策 5 落地）

```
每克单价 = ingredient.price / defaultGramsPerUnit(ingredient)
菜品价格 = Σ( dish_ingredient.grams × 每克单价 )
菜单总价 = Σ( 菜品价格 × servingFactor )   ← MenuCalcService 现有逻辑保留
```

- 录入：用户填 `ingredient.price`（按默认单位的直觉价，如鸡蛋 1 元/个）。
- 计算：内部走"每克单价"，与克基准一致。
- 展示：菜总价 + 食材价回显（"鸡蛋 1 元/个 ≈ 5 元/斤"，用换算表反算）。
- **兜底**：食材未配置默认单位（`defaultGramsPerUnit` 为 null）→ 每克单价无法算 → 该食材在菜价/采购估价处**跳过 + 标灰**，与决策 4 一致。

---

## 六、三端消费改造（统一用 grams · 接口契约）

下游 P0 spec（扣减链 / 余色标记）将依赖本节契约。

| 消费方 | 现状 | 改造后（都用 grams） | 依赖的下游 spec |
|---|---|---|---|
| 做菜扣减 | 未实现 | `dish_ingredient.grams × servingFactor` 扣 `pantry.grams`（按 ingredientId 聚合多批次 FIFO） | auto 扣减链 spec（待写） |
| 采购余色 | 不读 pantry | 聚合菜品 `grams` vs 按 ingredientId 汇总 `pantry.grams` → 红(无/0)/黄(有但<用量)/绿(≥用量) | 采购余色 spec（待写） |
| 采购聚合 | `amount` 当克累加 | `grams` 累加 → `referenceGrams` 名实相符 | 本 spec 覆盖（ShoppingAggregator 改用 grams） |
| 价格 | `price × factor` 单位不明 | 见第五节 | 本 spec 覆盖 |
| 营养计算 | `di.getAmount()` 恰为克（`DishService:243` + `DishQueryService:45` 两处） | 改读 `di.getGrams()`——amount 语义从"克"变"数量"后必须改，否则营养全错 | 本 spec 覆盖 |

> 本 spec 交付"换算能力 + grams 契约 + 采购聚合/价格/营养计算三处直接改造"；扣减链与余色标记是独立 P0 spec，引用本 spec 的 `grams` 字段。

---

## 七、迁移方案（V35）

新迁移编号 **V35**（V31–V34 已占用：dish_source / member_nutrition_target / meal_dict_expand / shopping_item_custom_nullable）。

```sql
-- V35__unit_conversion.sql

-- 1) 换算表
CREATE TABLE ingredient_unit_gram ( ... );   -- 见 2.1

-- 2) 预置 ~200 种常见食材换算（INSERT ... SELECT by name 从 ingredient 取 id）
INSERT INTO ingredient_unit_gram(ingredient_id, unit_id, grams_per_unit, is_default) ...;

-- 3) dish_ingredient 加列 + 回填（旧 amount 是克）
ALTER TABLE dish_ingredient ADD COLUMN unit_id BIGINT NULL;
ALTER TABLE dish_ingredient ADD COLUMN grams  DECIMAL(12,2) NULL;
-- 3a) "克"单位 id（无则在 V35 头部补字典）
SET @g_unit = (SELECT id FROM sys_dict WHERE dict_group='unit' AND name IN ('克','g') LIMIT 1);
UPDATE dish_ingredient SET unit_id = @g_unit, grams = amount;

-- 4) pantry 加列 + 回填（按现有 unit × 换算表算 grams，没配置的留 null）
ALTER TABLE pantry ADD COLUMN grams DECIMAL(12,2) NULL;
UPDATE pantry p
  JOIN ingredient_unit_gram iug ON p.ingredient_id = iug.ingredient_id AND p.unit_id = iug.unit_id
  SET p.grams = p.amount * iug.grams_per_unit;

-- 5) ingredient.price 语义保留（若缺列则 ADD COLUMN price DECIMAL(10,2) NULL）
```

**回填约定**：
- `dish_ingredient`：100% 回填（旧数据全是克）。
- `pantry`：仅回填"换算表已覆盖"的食材；未覆盖的 `grams=NULL`，应用层按兜底标灰。

---

## 八、测试策略

- **UnitConvertService** 纯函数单测：`toGrams` 正常 / null 兜底 / 默认单位。
- **保存路径**：`dish_ingredient` / `pantry` 保存时 `grams` 冗余正确（MockMvc）。
- **ShoppingAggregator**：改用 `grams` 后，`referenceGrams` = Σ grams（回归 + 新 case）。
- **价格**：`MenuCalcService.totalPrice` 按"每克单价"计算的单测。
- **迁移 V35**：在测试库跑一遍，校验 dish_ingredient 100% 回填、pantry 回填比例。

---

## 九、不做（YAGNI）

- ❌ AI 识别食材单位（纯规则换算表够用）
- ❌ 跨食材的单位归类（如"所有叶菜统一按把"）
- ❌ 实时换算查询（全冗余 `grams`，查/算零换算）
- ❌ 营养字段也走换算（营养表 `ingredient_nutrition` 已按每 100g 录，本 spec 不动）
- ❌ 复合单位（如"2 个又 50g"）——MVP 一个食材一行一个单位

---

## 十、交付清单

- [ ] V35 迁移（建表 + 预置 + 回填）
- [ ] `IngredientUnitGram` 实体 + Mapper
- [ ] `UnitConvertService`（+ 单测）
- [ ] `DishIngredient` / `Pantry` 实体加字段
- [ ] 保存路径接入换算（菜谱编辑 `DishService.saveFull`、库存入库 `PantryService.saveBatch`/`saveWithGrams`）
- [ ] `ShoppingService.generate` 改读 `di.getGrams()`（`ShoppingAggregator` 纯函数不变）
- [ ] `MenuService.summary` 价格改按用量算（`MenuCalcService` 纯函数签名不变）
- [ ] 营养计算改读 grams：`DishService.computeNutrition:243` + `DishQueryService:45` 两处
- [ ] 食材详情页「换算可编辑」接口（GET/PUT `/ingredient/{id}/unit-grams`）
- [ ] 兜底标灰：未配置换算的项在采购/余色/扣减处跳过 + 提示
```
