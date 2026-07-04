# Plan: 备菜模块（Plan C）

**日期**：2026-07-03 ｜ **状态**：规划中，待 review ｜ **作者**：Claude（资深产品+后端）
**关联**：评审 §1.3 gap ④、原型 spec §4.4、原型 `menu-detail-beicai.html`、[[gudu-product-redesign]] §5

---

## 1. 背景与目标

备菜是产品重做的核心 gap（评审 §1.3 标 ❌ 待建）。原型与 spec 已定，本 plan 把它落成可实现的增量。

**目标**：食集详情页加「备菜 Tab」，回答"洗没洗、切没切、化冻腌制了没"。
**非目标**：不动采购（采购管"买没买"，备菜管"备好没"，同源不同维度，互不串联）；不做餐程（大容器，留后）。

## 2. 核心设计（来自原型 + spec §5）

- **同源全量**：备菜聚合 = Σ 各菜 `DishIngredient.grams × servingFactor`，**不减库存**（与采购同源）。
- **备料状态**：`PENDING`(待备) / `READY`(✓已备) / `THAWING`(化冻中) / `MARINATING`(腌制中)。
- **共用高亮**：被 ≥2 道菜用到的食材 → 🔥 + 列出菜名（"葱 · 番茄炒蛋+清蒸鲈鱼"），一次备够。
- **调料折叠**：`purchase_category_id = 30`（调味料，shopping 已用此 id）的食材归到「🧂 调料 N 样 · 无需备料」折叠组，不进主列表、不计入进度。
- **进度条**：`已备 X / 共 Y 样需备料`（Y = 主列表项数，不含调料）。
- **交互**：点状态 chip → `PENDING ↔ READY` 切换；长按 → 弹「化冻 / 腌制」二选一菜单。

## 3. 数据模型（V37 迁移）

```sql
-- V37__menu_prep_status.sql
CREATE TABLE menu_prep_status (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  menu_id      BIGINT       NOT NULL,
  ingredient_id BIGINT      NOT NULL,
  status       VARCHAR(20)  NOT NULL DEFAULT 'PENDING',  -- PENDING/READY/THAWING/MARINATING
  update_time  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_menu_ingredient (menu_id, ingredient_id),
  KEY idx_menu (menu_id)
) COMMENT='食集备菜备料状态';
```
**约定**：menu+ingredient 无记录即视为 `PENDING`（前端默认待备），不必为每个食材预插。

## 4. 后端布局（仿 shopping 模块）

```
menu-api/src/main/java/com/gudu/xsd/modules/menu/
  ├─ prep/
  │    ├─ MenuPrepStatus.java          @TableName("menu_prep_status") 实体
  │    ├─ PrepStatus.java              enum {PENDING, READY, THAWING, MARINATING}
  │    ├─ PrepAggregator.java          ★纯函数：聚合用量 + 共用计数
  │    ├─ PrepItemVO.java              record（ingredientId/name/totalGrams/dishCount/dishNames/status/shared）
  │    ├─ MenuPrepVO.java              record（items/condiments/readyCount/totalCount）
  │    ├─ MenuPrepService.java         getPrep / updateStatus
  │    ├─ MenuPrepController.java      GET /menu/{id}/prep + PUT /menu/{id}/prep/{ingredientId}
  │    └─ mapper/MenuPrepStatusMapper.java
  └─ sql/V37__menu_prep_status.sql
```

### 4.1 PrepAggregator（纯函数，TDD 核心）

```java
/** 按 ingredientId 聚合各菜用量（×servingFactor），不减库存。 */
public static List<PrepLine> aggregate(List<MenuDishVO> dishes,
                                       Map<Long,List<DishIngredient>> ingByDish);
public record PrepLine(Long ingredientId, BigDecimal totalGrams,
                       int dishCount, List<Long> dishIds) {}
```
**单测用例**（MenuPrepAggregatorTest）：
1. 单菜单料 → 1 条 PrepLine，dishCount=1
2. 多菜共用一料 → 合并 grams，dishCount=2，dishIds 含两菜
3. servingFactor=2 → grams 翻倍
4. 同菜同料多行（理论不该有，防御）→ 累加

### 4.2 MenuPrepService

```java
public MenuPrepVO getPrep(Long menuId) {
    MenuDetail md = menuService.detail(menuId);            // 复用 #2：dishes 带 dishName
    // 1. 批量查各菜 DishIngredient（一次 selectBatchIds(dishIds) + groupBy dishId）
    // 2. List<PrepLine> = PrepAggregator.aggregate(md.dishes(), ingByDish);
    // 3. 批量查 ingredient（name + purchaseCategoryId）→ 判调料折叠
    // 4. 一次查 menu_prep_status where menu_id=? → Map<ingredientId,status>
    // 5. 组装：主列表 items（非调料，带 dishNames 复用 #2 dishName）+ condiments（调料折叠）+ readyCount/totalCount
}
@Transactional
public void updateStatus(Long menuId, Long ingredientId, PrepStatus status) {
    // upsert：存在 uk 则 update，否则 insert（用 INSERT ... ON DUPLICATE KEY UPDATE 或 selectById 判定）
}
```

### 4.3 Controller 契约

| 方法 | 路径 | 返回 | 说明 |
|---|---|---|---|
| GET | `/menu/{id}/prep` | `R<MenuPrepVO>` | 备菜聚合（主料+调料折叠+进度） |
| PUT | `/menu/{id}/prep/{ingredientId}` | `R<?>` | body `{status}`，更新备料状态 |

## 5. 前端

### Flutter（menu-flutter）
- `models/prep.dart`：`PrepItem` / `MenuPrep`
- `services/prep_service.dart`：`getPrep(id)` / `updateStatus(menuId, ingredientId, status)`
- `pages/menu/detail_page.dart`：顶部改四 Tab（菜/备菜/采购/一起吃）；备菜 Tab = 进度条 + 主列表 + 调料折叠
- `_PrepItemRow`：状态 chip（点=PENDING↔READY，长按=showModalSheet 选化冻/腌制）；共用项 🔥 + 边框 #E5A938

### 小程序（menu-mini）
- `api/prep.ts`：`getMenuPrep(id)` / `updatePrepStatus(menuId, ingredientId, status)`
- `pages/menu/Detail.vue`：顶部 Tab 栏（菜/备菜/采购/一起吃），备菜 Tab 复用三色徽章风格的状态 chip
- 配色对齐原型：READY 绿 #4FAE6E / THAWING 蓝 #4FA0D0 / MARINATING 琥珀 #E5A938 / PENDING 白底灰边

## 6. 实现顺序（增量 + TDD）

### Phase 1：后端（可独立验证）
- **Task 1**：V37 迁移 + MenuPrepStatus 实体 + PrepStatus enum + Mapper
- **Task 2**：PrepAggregator 纯函数 + 单测（先红后绿）
- **Task 3**：MenuPrepService（getPrep/updateStatus）+ 单测（mock mapper，照 MenuServiceTest 范式）
- **Task 4**：MenuPrepController + MockMvc 测试（照 MenuControllerTest 范式）
- **Task 5**：staging apply V37 + rsync menu-api + rebuild + curl GET /menu/1/prep 验证聚合

### Phase 2：前端（依赖 Phase 1 接口）
- **Task 6**：Flutter prep model/service + detail_page 改 Tab + 备菜 Tab 页
- **Task 7**：小程序 prep api + Detail.vue 改 Tab + 备菜 Tab
- **Task 8**：staging rebuild 双端 + 浏览器/真机验证状态切换、共用高亮、调料折叠、进度条

## 7. 未决问题（实现时确认）

1. **化冻/腌制时间提示**：原型副标题"建议提前 30 分钟化冻"的数字从哪来？MVP 可硬编码或按 ingredient 名关键词（鱼/肉→化冻提示），无字段则先不做个性化提示。
2. **状态机约束**：READY 能否直接切回 PENDING？（备料返工）建议允许任意切换（无强状态机）。
3. **采购 Tab 与备菜 Tab 的 Tab 栏**：当前 detail_page 是单页无 Tab，本次要引入 Tab 栏——是 PageView 还是 IndexedStack？倾向 IndexedStack（保留各 Tab 状态）。
4. **一起吃 Tab（协同点菜）**：本次不实现，Tab 位占位"即将上线"。

## 8. 验收标准

- 后端：PrepAggregator 纯函数单测全绿；Service/Controller 单测全绿；staging `GET /menu/1/prep` 返回正确聚合（menu 1 实测：主料含番茄/鸡蛋/虾仁等，调味料组含食用油/食盐/白糖）。
- 前端：Flutter analyze 0 error；vue-tsc 0 新增 error；两端备菜 Tab 渲染正确，状态切换可点且持久化（刷新仍在），共用项 🔥 高亮，调料折叠可展开。
- 全量基线：264 tests 仍 0 failures（新增 prep 单测叠加）。
