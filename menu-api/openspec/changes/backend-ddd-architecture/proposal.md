# Proposal: 后端严格 DDD 架构重构 + ArchUnit 架构守护体系

**Change ID:** `backend-ddd-architecture`
**Created:** 2026-08-21
**Status:** Draft（Phase 0 已随本提案落地）

---

## Problem Statement

2026-08-21 用 ArchUnit 对 `menu-api` 全量代码（175 个类）做了 DDD 与《阿里巴巴 Java 开发手册》审查，暴露四类结构性问题：

### 1. 无领域层，贫血模型（DDD 缺失）
- 现状为「按功能分包」三层：`modules/<feature>/` 内 Controller + Service + Entity(PO) + Mapper 混放。
- 实体是纯 MyBatis-Plus POJO（`@Data + @TableName`，零业务行为），**所有业务规则堆在 Service**：
  ShoppingService 611 行、RecipeImporter 468 行、FoodLogService 418 行、DishService 404 行——上帝服务，业务规则无法单测复用，只能整段搬。

### 2. 限界上下文边界被打穿（跨模块直接摸表）
- **174 条**「A 模块 Service 直接注入/调用 B 模块 Mapper」违规（ArchUnit 冻结基线在案）。
  重灾区：ShoppingService 引 7 个外部 Mapper（dict/dish/menu/nutrition/pantry）；FoodLogService 引 5 个；PantryService 引 nutrition 的 IngredientMapper。
- 后果：任何一张表结构调整会跨模块炸裂；上下文之间没有防腐层，也无法演进独立数据所有权。

### 3. 事务正确性风险（阿里规约【强制】）
- **30 处** `@Transactional` 未声明 `rollbackFor`（默认仅回滚 RuntimeException，受检异常会漏回滚导致半提交）。→ **Phase 0 已全部修复**。
- `GlobalExceptionHandler` 用 `e.printStackTrace()`。→ **Phase 0 已改 `log.error`**。

### 4. 分层越权
- AiController、NutritionMetricController 直接注入 Mapper 做 CRUD（Controller 越过应用层）。→ **Phase 0 已下沉到 Service**。

谁受影响：所有后端功能迭代（改一处表牵连多模块）、数据一致性（事务漏回滚）、以及后续多端（APP/小程序/后台）并行开发的接口稳定性。

## Proposed Solution

### 严格 DDD 目标架构（详见 [design.md](./design.md)）
1. **四层结构**：每个 `modules/<bc>` 内部拆 `interfaces / application / domain / infrastructure`，依赖单向：interfaces → application → domain ← infrastructure。
2. **充血模型**：聚合根与实体承载业务行为（如 `Menu.confirm()`、`IngredientStock.consume()`），Service 只做用例编排；领域层**零框架依赖**（纯 JDK）。
3. **聚合根**：按聚合一致性边界重建 13 张聚合表（dish/menu/pantry/shopping/... 详见 design.md），跨聚合只通过 ID 引用。
4. **领域事件**：`DomainEvent` 基建 + 进程内事件总线（Spring `ApplicationEventPublisher`），用事件解耦跨上下文协作（DishCookedEvent → pantry 扣库存 / cookbook 记次数），**替代**跨模块摸 Mapper。

### ArchUnit 架构守护（已落地 Phase 0）
- `menu-api/src/test/java/com/gudu/xsd/arch/` 两组守护测试接入 `mvnw test`：
  - `ArchitectureGuardTest`：Controller 不碰 Mapper/ORM、Mapper 收口、实体不依赖上层、公共层不反向依赖、**跨模块 Mapper 冻结基线（174 条，只减不增）**。
  - `AlibabaStandardGuardTest`：禁 printStackTrace/System.out、`@Transactional` 必须 rollbackFor、禁字段注入、禁 java.util.Date/SimpleDateFormat、Web 层命名与统一返回 `R<T>`。
- Phase 1 起随四层结构落地追加：domain 零框架依赖、依赖方向矩阵、领域事件必须实现 `DomainEvent`、聚合根必须继承 `AggregateRoot`、domain.model 禁 `@Data`。

### 落地节奏（绞杀式，不推倒重来）
- **Phase 0（已完成）**：守护基建 + 立即档整改（上述 ✅ 项），全部 362 个单测 + 守护测试绿。
- **Phase 1**：`dish` 菜谱聚合做第一个完整四层样板 + 领域事件基建 + ArchUnit 目标规则激活。
- **Phase 2**：事件打通 2 个高频跨上下文场景，冻结基线削减 ≥30 条。
- **Phase 3/4**：核心上下文复制样板 → 全量收敛 → 跨模块 Mapper 规则转立即生效。

## Scope

### In Scope
- `menu-api` 包结构演进为四层（逐上下文）
- 聚合根/充血模型/领域事件基建与首个样板（dish）
- ArchUnit 守护规则全集与冻结基线收敛流程
- 阿里规约可自动化条款的持续守护

### Out of Scope
- **不改数据库表结构**（聚合边界与现有外键设计已兼容，PO 层保持原表映射）
- **不改 API 契约**（interfaces 层保持现有 URL/请求响应结构，前端三端零感知）
- 不引入微服务拆分/消息队列（进程内事件先行，Outbox/异步后续另立 change）
- 不在本 change 内完成全部 13 个上下文改造（Phase 3/4 只做核心三上下文，其余滚动跟进）
- 小程序/APP/后台前端不涉及

## Impact Analysis

| Component | Change Required | Details |
|-----------|-----------------|---------|
| Database | No | 不改表；PO 仍一一映射现有表 |
| API | No | URL/DTO 契约冻结，由 interfaces 层保证 |
| menu-api 结构 | Yes | modules/<bc> 内拆四层；dish 先行，其余按 Phase 滚动 |
| 领域模型 | Yes | 聚合根/实体/值对象从 PO 分离（充血），PO↔领域对象转换器 |
| 领域事件 | New | DomainEvent 基类 + 事件监听器（pantry/shopping/cookbook/notification） |
| 测试 | Yes | 新增 ArchUnit 守护（已落地）；领域层纯 JUnit 单测（无 Spring 上下文，更快） |
| CI | Yes | `mvnw test` 已含守护；跨模块 Mapper 基线随 PR 只减不增 |

## Risks / Trade-offs

- **迁移期双轨**：试点期间 dish 走四层、其他上下文仍三层，靠 ArchUnit 冻结规则防止旧结构扩散；样板稳定后再批量复制。
- **PO 与领域对象分离的转换成本**：仅对有业务行为的聚合做完整分离；纯字典（SysDict、NutritionMetric）保留简单模式，不教条。
- **事件最终一致性**：进程内事件在同一事务内发布/消费（同步），不引入分布式复杂度；消费失败靠事件监听器内异常回滚同事务。
