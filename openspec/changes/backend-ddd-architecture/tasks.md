# Implementation Tasks: 后端严格 DDD 架构重构 + ArchUnit 守护

**Change ID:** `backend-ddd-architecture`
**设计依据**：[design.md](./design.md)（聚合表 / 包结构 / 事件清单 / 守护规则全集）

---

## Phase 0: 守护基建 + 立即档整改（✅ 2026-08-21 已完成）

- [x] 0.1 pom 引入 `archunit-junit5:1.3.0`（test scope，纯字节码分析，不依赖 Spring/DB）
- [x] 0.2 `ArchitectureGuardTest`：分层/边界/纯度 9 条规则 + 跨模块 Mapper 冻结基线（174 条）
- [x] 0.3 `AlibabaStandardGuardTest`：阿里规约 8 条强制条款守护
- [x] 0.4 整改：`GlobalExceptionHandler` printStackTrace → `log.error`
- [x] 0.5 整改：30 处 `@Transactional` 补 `rollbackFor = Exception.class`（含 PantryService 全限定名注解 5 处）
- [x] 0.6 整改：`AiController` → 新建 `AiCallLogService` 下沉日志分页/用量聚合；`NutritionMetricController` → 新建 `NutritionMetricService`
- [x] 0.7 `AiControllerTest` 补 `@MockBean AiCallLogService`
- [x] 0.8 验证：`mvnw test`（排除 E2E）362 单测 + 2 守护测试全绿

**Quality Gate:**
- [x] ArchUnit 守护测试全绿（冻结规则除外——由基线吸收）
- [x] 全量单测无回归
- [x] 冻结基线入库 `src/test/resources/archunit_store/`

---

## Phase 1: dish 聚合试点（四层样板 + 事件基建）

- [ ] 1.1 领域事件基建：`common/domain/DomainEvent`（接口：eventId/occurredOn）+ `AbstractDomainEvent` + 聚合根标记 `AggregateRoot<ID>`；`DomainEventPublisher` 封装 `ApplicationEventPublisher`（事务内同步发布）
- [ ] 1.2 dish 四层包结构落地：
  - `domain/model/Dish`（聚合根，充血：`publish()/archive()/substituteIngredient()/changeUsage()`，不变量校验抛领域异常）
  - `DishIngredient/DishStep` 聚合内实体 + `UsageAmount` 值对象（不可变）
  - `infrastructure/po/DishPO`（原 @TableName 实体平移）+ `DishConverter`（PO↔领域对象，含聚合内集合）
  - `application/DishAppService`（编排：加载→行为→存→发 `DishCreatedEvent/DishImportedEvent`；`@Transactional(rollbackFor)`）
  - `interfaces/DishController` + Req/Resp DTO（URL/字段与现契约一一对应）
- [ ] 1.3 `DishDict/DishDraft/DishHistory` 迁入对应层（Draft 独立小聚合，History 只追加）
- [ ] 1.4 RecipeImporter/DishVectorService 适配：改订阅 `DishCreatedEvent/DishImportedEvent` 触发向量索引（替换 dish 模块内直接调用）
- [ ] 1.5 领域单测：`DishTest`（纯 JUnit 无 Spring：不变量/行为/值对象相等性）；`DishAppServiceTest`（Mockito：编排与事件发布断言）
- [ ] 1.6 E2E：`GuduE2ECoverageTest` dish 用例回归（菜谱创建/编辑/搜索契约不变）
- [ ] 1.7 ArchUnit 新规则激活（加入 `ArchitectureGuardTest`）：
  - `..dish.domain..` 零框架依赖
  - dish 包内依赖方向矩阵（interfaces→application→domain←infrastructure）
  - `..domain.model..` 禁 `@Data`；聚合根继承 `AggregateRoot`
  - `..domain.event..` 事件必须实现 `DomainEvent`

**Quality Gate:**
- [ ] `mvnw test`（含守护）全绿
- [ ] dish E2E 回归通过（staging 隧道）
- [ ] API 契约零变化（swagger diff 为空）
- [ ] 新增领域单测不依赖 Spring 上下文（秒级）

---

## Phase 2: 事件驱动解耦跨上下文（基线削减 ≥30 条）

- [ ] 2.1 `DishCookedEvent`：CookService（menu）发布；cookbook 记做过次数、pantry 扣库存、review 评分提醒三处消费——替换对应跨模块 Mapper 直连
- [ ] 2.2 `MenuConfirmedEvent`：shopping 生成采购清单、prep 生成备料单——替换 MenuDishMapper/StockLogMapper 跨模块访问
- [ ] 2.3 `StockChangedEvent` / `ShoppingItemPurchasedEvent`：pantry↔shopping 双向解耦
- [ ] 2.4 每替换一处，同步删 `archunit_store` 基线对应行（禁止整文件重置）
- [ ] 2.5 事件消费方幂等处理（同事务内同步消费，靠事务回滚保证；记录消费日志）
- [ ] 2.6 ArchUnit 规则升级：跨上下文依赖仅允许指向对方 `..application..`（存量冻结）

**Quality Gate:**
- [ ] 冻结基线 ≤144 条（削减 ≥30）
- [ ] 做菜→扣库存→记次数 E2E 链路回归
- [ ] 全量测试绿

---

## Phase 3: 核心上下文复制样板（menu / pantry / shopping）

- [ ] 3.1 menu：`Menu` 聚合根（confirm/reopen 点菜流转行为化）+ 四层迁移 + 单测
- [ ] 3.2 pantry：`IngredientStock` 聚合根（consume/restock/inventory 盘点行为化，StockLog 只追加）+ 四层迁移
- [ ] 3.3 shopping：`ShoppingList` 聚合根（generate/purchase/undo 行为化）+ 四层迁移
- [ ] 3.4 各上下文跨模块依赖改走 AppService / 事件，基线持续削减
- [ ] 3.5 「跨模块 Mapper」ArchUnit 规则对已迁移上下文转立即生效（按模块豁免名单收紧）

**Quality Gate:**
- [ ] 冻结基线 ≤60 条
- [ ] 三上下文 E2E 回归 + 全量测试绿

---

## Phase 4: 收敛收尾

- [ ] 4.1 剩余上下文（nutrition/member/cookbook/dailylog/review）滚动迁移；支撑域（dict/auth/notification/file）评估保留简单模式
- [ ] 4.2 冻结基线清零 → 跨模块 Mapper 规则全局转立即生效，删除 FreezingArchRule 包装
- [ ] 4.3 ShoppingService(611 行) 等 God Service 随迁移拆解为应用服务 + 领域服务（≤200 行/类）
- [ ] 4.4 文档：`docs/design/BACKEND_ARCHITECTURE.md`（四层规范/聚合表/事件清单/守护规则）+ CLAUDE.md 增补「新写后端代码必读」小节
- [ ] 4.5 设计规范同步：本 change spec delta 合入 `openspec/specs/backend-architecture/`

**Quality Gate:**
- [ ] ArchUnit 全部规则立即生效且全绿（无冻结）
- [ ] E2E 全量回归
- [ ] CLAUDE.md / docs 更注入库
