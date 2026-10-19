# Design: 严格 DDD 目标架构与守护规则

**Change ID:** `backend-ddd-architecture`

---

## 1. 限界上下文与聚合划分

保留 `com.gudu.xsd.modules.<bc>` 作为限界上下文（Bounded Context），内部按聚合组织。聚合划分依据现有外键与一致性边界（**不改表**）：

| 上下文 | 聚合根 | 聚合内实体 | 一致性边界说明 |
|--------|--------|-----------|---------------|
| dish 菜谱 | `Dish` | `DishIngredient`（用料）、`DishStep`（步骤） | 菜谱编辑/导入整体保存；DishDict/DishDraft 为独立小聚合 |
| menu 餐程 | `Menu` | `MenuDish` | 点菜→确认→开做整体流转；prep/together 子域并入 menu 上下文 |
| pantry 余量 | `IngredientStock` | `StockLog`（只追加流水） | 双轨三色库存核心；出入库必须过聚合根 |
| shopping 采购 | `ShoppingList` | `ShoppingItem` | 清单生成/勾买/回补整体一致 |
| nutrition 食材库 | `Ingredient` | `IngredientNutrition` | 营养 EAV；NutritionMetric 为支撑字典 |
| member 会员 | `Member` | — | 含营养目标 |
| cookbook 食集 | `CookingRecord`、`Favorite` | — | 做菜历史，DishCooked 事件消费方 |
| dailylog 餐程日志 | `DailyLog` | `DailyLogItem` | 三餐打卡 |
| review 评分 | `Review` | `ReviewScore` | 做完菜后评分 |
| ai 智荐 | —（无核心聚合） | `AiCallLog` 支撑 | 应用服务直接编排 |
| dict / auth / notification / file | 支撑域 | — | 简单模式，不强制四层 |

**跨聚合只通过 ID 引用**（如 MenuDish 存 dishId，不持 Dish 对象引用）。

## 2. 包结构规范（目标态）

```
com.gudu.xsd.modules.dish/
├── interfaces/                      # 接口层（原模块根的 Controller/DTO/VO 迁入）
│   ├── DishController.java
│   ├── DishSaveRequest.java         # 入参 DTO（原 DishSaveDTO）
│   └── DishDetailResponse.java      # 出参 VO
├── application/                     # 应用层
│   └── DishAppService.java          # 用例编排：加载聚合→调领域行为→存→发事件；@Transactional 边界
├── domain/                          # 领域层（纯 JDK，零框架依赖）
│   ├── model/
│   │   ├── Dish.java                # 聚合根（充血）：publish()、archive()、changeIngredient()
│   │   ├── DishIngredient.java      # 聚合内实体
│   │   ├── DishStep.java
│   │   └── UsageAmount.java         # 值对象（数量+单位，不可变）
│   ├── event/
│   │   ├── DishCreatedEvent.java
│   │   └── DishImportedEvent.java
│   └── service/                     # 领域服务（跨聚合纯业务计算）
└── infrastructure/                  # 基础设施层
    ├── DishMapper.java              # 原 mapper 迁入
    ├── po/
    │   └── DishPO.java              # 原 @TableName 实体改为 PO
    └── converter/
        └── DishConverter.java       # PO ↔ 领域对象
```

**依赖方向（单向，ArchUnit 守护）：**

```
interfaces ──► application ──► domain ◄── infrastructure
    │                              ▲
    └────────────(仅 common)───────┘
```

- domain 不依赖任何层、任何框架（Spring/MyBatis/Jackson/Swagger 均禁止）
- 跨上下文调用只能走对方 `application` 层（AppService 公开方法 = 上下文 API）
- 原「跨模块注入他模块 Mapper」逐步替换为：走对方 AppService，或改为订阅领域事件

## 3. 领域事件机制

- **基建位置**：`com.gudu.xsd.common.domain.DomainEvent`（接口：`eventId()`、`occurredOn()`）+ `AbstractDomainEvent`。
- **传输**：进程内 `ApplicationEventPublisher`，**事务内同步发布**（`@TransactionalEventListener(phase = BEFORE_COMMIT)` 或发布于事务内），消费失败同事务回滚，不引入最终一致性与 MQ。
- **命名**：过去时（`DishCookedEvent`、`StockChangedEvent`、`MenuConfirmedEvent`）。
- **初版事件清单**（由跨模块违规热点反推）：

| 事件 | 发布方 | 消费方（替代的跨模块 Mapper 访问） |
|------|--------|-----------------------------------|
| `DishCookedEvent` | menu（CookService） | cookbook 记做过次数、pantry 扣库存、review 提醒 |
| `MenuConfirmedEvent` | menu | shopping 生成采购清单、prep 生成备料单 |
| `StockChangedEvent` | pantry | shopping 缺货自动补项 |
| `ShoppingItemPurchasedEvent` | shopping | pantry 入库回补 |
| `DishCreatedEvent` / `DishImportedEvent` | dish | ai 向量索引构建 |

## 4. 充血模型原则

1. 业务规则写在聚合根/实体方法里，方法名说业务不说技术：`menu.confirm()`、`stock.consume(amount)`、`dish.substituteIngredient(oldId, new)`。
2. 领域对象**不放** `@Data`/public setter——变更通过意图明确的行为方法；值对象（UsageAmount 等）完全不可变。
3. 校验分两层：DTO 入参校验（Bean Validation，interfaces 层）；不变量校验（聚合根构造/行为方法内，抛领域异常）。
4. 应用服务薄：只做「取聚合 → 调行为 → 存 → 发事件」，不写业务 if/else。
5. **不教条**：纯字典/无行为实体（SysDict、NutritionMetric、NotificationChannel）保留 PO 直出，不强造行为方法。

## 5. ArchUnit 守护规则全集

### 5.1 已生效（Phase 0 落地，`src/test/java/com/gudu/xsd/arch/`）

| 规则 | 状态 |
|------|------|
| Controller 不得依赖 Mapper / MyBatis-Plus 数据访问设施 | ✅ 全绿 |
| mapper 包内必须 BaseMapper 接口 + Mapper 后缀；Mapper 后缀收口 mapper 包 | ✅ 全绿 |
| 实体（@TableName）不依赖 Controller/Service/Mapper | ✅ 全绿 |
| common/config 不反向依赖 modules | ✅ 全绿 |
| 禁 printStackTrace / System.out/err | ✅ 全绿 |
| @Transactional（方法/类级）必须显式 rollbackFor | ✅ 全绿（30 处已整改） |
| 禁 @Autowired 字段注入 | ✅ 全绿 |
| 禁 java.util.Date / SimpleDateFormat | ✅ 全绿 |
| @RestController 命名/包位置；Controller 公开方法统一返回 R<T> | ✅ 全绿 |
| **跨模块 Mapper 访问**（冻结基线 174 条，只减不增） | 🧊 基线收敛中 |

### 5.2 随四层落地激活（Phase 1+）

| 规则 | 激活时机 |
|------|----------|
| `..domain..` 零框架依赖（spring/mybatis/jackson/swagger/sa-token） | dish 试点完成 |
| 依赖方向矩阵：interfaces→application→domain←infrastructure，禁止反向 | dish 试点完成 |
| `..domain.event..` 内事件必须实现 `DomainEvent` | 事件基建落地 |
| `..domain.model..` 聚合根必须继承 `AggregateRoot`；禁 `@Data` | dish 试点完成 |
| 跨上下文依赖仅允许指向对方 `..application..` 或 `..interfaces..` DTO | Phase 2 起 |
| 跨模块 Mapper 冻结规则转立即生效 | 基线清零 |

**实现注意（ArchUnit 1.3.0 踩坑记录，避免复踩）：**
- `noClasses().should(自定义条件)` 下事件必须用 `SimpleConditionEvent.satisfied()` 上报「违规属性成立」，用 `violated()` 会被静默吞掉（极性翻转语义）；正向 `classes()/methods().should()` 用 `violated()`。
- `JavaAnnotation.getProperties()` 会并入注解默认值，判断「是否显式声明」必须检查数组长度（如 rollbackFor 空数组 = 未声明），`containsKey` 恒真。
- surefire 对 ArchUnit 自带引擎发现的字段级 `@ArchTest` 计数为 0：规则用静态字段声明 + Jupiter 原生 `@Test` 聚合 `rule.evaluate(classes)` 执行。
- `printStackTrace` 字节码 owner 是静态类型（如 `Exception`），需按 `isAssignableTo(Throwable.class)` 判断。
- 冻结基线目录 `src/test/resources/archunit_store/`（配置见 `archunit.properties`），**整改一条删一行，禁止整文件重置**。

## 6. 迁移策略（绞杀式）

1. **试点样板**（Phase 1，dish）：一个上下文完整走通四层 + 事件 + 守护规则激活，形成可复制模板（含包结构、Converter、AppService、测试范式）。
2. **旧结构冻结**：未迁移上下文继续三层运行，但受「跨模块 Mapper 只减不增」约束，新代码必须走新结构。
3. **按热点排序迁移**（Phase 2/3）：menu → pantry → shopping（跨模块违规与业务复杂度双高优先）。
4. **每期验收**：单测 + ArchUnit 全绿、冻结基线只减、API 契约回归（E2E）不破。
