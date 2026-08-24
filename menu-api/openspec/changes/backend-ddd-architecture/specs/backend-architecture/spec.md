# Delta: Backend Architecture（后端架构守护能力）

**Change ID:** `backend-ddd-architecture`
**Affects:** `menu-api` 全部模块（分层结构/聚合设计/事件/编码规约守护）

---

## ADDED

### Requirement: 分层依赖守护（接口层不越权）

Controller（interfaces 层）不得直接依赖 Mapper 或 ORM 数据访问设施；查询与写操作必须经应用层（Service/AppService）。

#### Scenario: 新代码 Controller 注入 Mapper
- GIVEN 开发者在某 Controller 构造器中注入了任意 `modules.<bc>.mapper` 下的 Mapper
- WHEN 运行 `ArchitectureGuardTest`
- THEN 守护失败，违规明细指出该构造参数/字段/方法调用位置

#### Scenario: Controller 使用 MyBatis-Plus QueryWrapper 组装查询
- GIVEN 某 Controller 方法内出现 `com.baomidou.mybatisplus.core.mapper..` 或 `java.sql..` 类型的直接依赖
- WHEN 运行守护
- THEN 守护失败

#### Scenario: 合法分层
- GIVEN Controller 仅依赖本模块 Service 与 common 层
- WHEN 运行守护
- THEN 通过

### Requirement: 模块边界守护（限界上下文数据私有）

`modules.<bc>` 内的 Mapper 是该上下文的私有数据通道，跨上下文不得直接访问；存量违规由冻结基线管理，只减不增。

#### Scenario: 新增跨模块 Mapper 访问
- GIVEN 冻结基线为 174 条，开发者新增一行 shopping 模块访问 dish 模块 Mapper 的代码
- WHEN 运行守护
- THEN 守护失败（基线之外的新违规）

#### Scenario: 整改存量违规
- GIVEN 开发者把 FoodLogService 对 DishMapper 的直接调用改为调用 DishAppService 公开方法
- WHEN 手动删除基线文件中对应行后运行守护
- THEN 守护通过，基线行数减一

#### Scenario: 基线清零后
- GIVEN 全部跨模块 Mapper 访问已整改，基线文件为空
- WHEN 团队把冻结规则改为立即生效（Phase 4）
- THEN 任何新的跨模块 Mapper 依赖直接导致守护失败

### Requirement: 聚合根与充血模型（Phase 1 起）

核心聚合（dish/menu/pantry/shopping…）的领域模型位于 `modules.<bc>.domain.model`，聚合根继承 `AggregateRoot` 标记，业务行为内聚于实体方法，领域层零框架依赖。

#### Scenario: 领域层引入框架依赖
- GIVEN 开发者在 `..dish.domain..` 包的类上添加 `@Service` 或注入 Mapper
- WHEN 运行守护（domain 零框架依赖规则）
- THEN 守护失败

#### Scenario: 聚合根用 @Data 暴露全部 setter
- GIVEN 开发者给 `domain.model.Dish` 加上 `@Data`
- WHEN 运行守护（domain.model 禁 @Data 规则）
- THEN 守护失败；领域对象变更必须走意图明确的行为方法

#### Scenario: 未继承 AggregateRoot 的聚合根
- GIVEN 开发者新增领域实体 `Menu` 但未继承 `AggregateRoot<Long>`
- WHEN 运行守护
- THEN 守护失败

#### Scenario: 业务规则单测不需要 Spring
- GIVEN `Dish` 聚合根实现 `substituteIngredient()` 行为方法
- WHEN 编写 `DishTest`（纯 JUnit）
- THEN 不加载 Spring 上下文即可验证不变量（如替换用料后份数重算）

### Requirement: 领域事件（Phase 1 起）

跨上下文协作通过领域事件解耦：事件位于 `modules.<bc>.domain.event`，实现 `DomainEvent` 接口，事务内同步发布，消费失败同事务回滚。

#### Scenario: 做菜完成触发下游
- GIVEN 用户在 menu 上下文完成一次做菜（CookService）
- WHEN `DishCookedEvent` 发布
- THEN cookbook 记做过次数 + pantry 扣库存 + review 评分提醒依次消费，且不发生任何跨模块 Mapper 直连

#### Scenario: 消费方抛异常
- GIVEN pantry 消费 `DishCookedEvent` 时扣库存校验失败抛领域异常
- WHEN 事件在事务内同步消费
- THEN 整个做菜事务回滚（不出现「记了次数但没扣库存」）

#### Scenario: 新事件未实现 DomainEvent
- GIVEN 开发者在 `domain.event` 包新增 `StockChangedEvent` 但未实现 `DomainEvent`
- WHEN 运行守护
- THEN 守护失败

### Requirement: 事务规约（阿里规约【强制】）

所有 `@Transactional`（方法级与类级）必须显式声明 `rollbackFor = Exception.class`。

#### Scenario: 新增事务方法未声明 rollbackFor
- GIVEN 开发者新增 `@Transactional` 方法（未写 rollbackFor）
- WHEN 运行 `AlibabaStandardGuardTest`
- THEN 守护失败，提示默认仅回滚 RuntimeException

#### Scenario: 已声明
- GIVEN `@Transactional(rollbackFor = Exception.class)`
- WHEN 运行守护
- THEN 通过

### Requirement: 日志与异常规约（阿里规约【强制】）

生产代码禁止 `printStackTrace` 与 `System.out/err` 标准流访问，统一使用日志框架（`@Slf4j` + log.error 等）。

#### Scenario: 异常处理里写 printStackTrace
- GIVEN 开发者在 catch 块中调用 `e.printStackTrace()`
- WHEN 运行守护
- THEN 守护失败（无论 owner 是 Exception 还是 Throwable 等任意可抛出类型）

#### Scenario: 用日志输出
- GIVEN `log.error("xxx", e)`
- WHEN 运行守护
- THEN 通过

### Requirement: 依赖注入与遗留 API 规约（阿里规约【强制】）

统一构造器注入（`@RequiredArgsConstructor`），禁止 `@Autowired` 字段注入；日期时间统一 JSR-310，禁止 `java.util.Date/SimpleDateFormat`。

#### Scenario: 字段注入
- GIVEN 开发者给字段加 `@Autowired`
- WHEN 运行守护
- THEN 守护失败

#### Scenario: 使用 SimpleDateFormat
- GIVEN 新代码 import `java.text.SimpleDateFormat`
- WHEN 运行守护
- THEN 守护失败，应使用 `DateTimeFormatter`

### Requirement: Web 层契约守护

Controller 类名以 Controller 结尾并位于业务模块内；Controller 公开方法统一返回 `R<T>`。

#### Scenario: Controller 返回裸对象
- GIVEN 某 `@RestController` 公开方法直接返回 `List<Dish>` 而非 `R<List<Dish>>`
- WHEN 运行守护
- THEN 守护失败

#### Scenario: 命名不符
- GIVEN 新增 `@RestController` 类 `DishApi`
- WHEN 运行守护
- THEN 守护失败
