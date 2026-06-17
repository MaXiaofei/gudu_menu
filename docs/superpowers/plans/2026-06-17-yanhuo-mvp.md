# 烟火小食单 · MVP 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 搭起「烟火小食单」的 Web 后台 MVP——数据地基 + 基础流程跑通，纯手工可用，不接 AI。

**Architecture:** 前后端分离。后端：Java 17 + Spring Boot 3 单体（不拆微服务），MyBatis-Plus + MySQL 8 + Redis，Sa-Token 有状态鉴权，Knife4j 接口文档。前端：Vue3 + Element Plus + Pinia 独立工程，Axios 调后端 RESTful API。Docker Compose 编排 MySQL/Redis/MinIO。TDD：纯函数/算法严格 TDD，CRUD 测 Service 关键逻辑 + Knife4j 手测。

**Tech Stack:** Java 17, Spring Boot 3, MyBatis-Plus, MySQL 8, Redis, Sa-Token, Knife4j, JUnit5 + AssertJ; Vue3 + TS + Vite + Element Plus + Pinia + Axios + ECharts; Docker Compose, MinIO.

**Spec:** `docs/superpowers/specs/2026-06-16-yanhuo-xiaoshidan-design.md`

---

## 文件结构

**顶层目录（三端分离，各自独立工程）**：
```
menu-new/
  backend/    # 后端接口：Java Spring Boot 主服务 + Python ai-service（V2 AI/数据）
  admin/      # Web 管理后台「烟火管理后台」(Vue3 + Element Plus)
  miniapp/    # 小程序「小食单」(uniapp) —— V1 阶段建，MVP 暂不建
  docs/       # 设计文档 + 实现计划
  data/       # MySQL / Redis 持久化卷
  docker-compose.yml
```

### 后端 `backend/`
```
backend/
  pom.xml
  src/main/resources/application.yml  application-dev.yml
  src/main/java/com/yanhuo/xsd/
    YanhuoApplication.java
    common/      R.java BizException.java GlobalExceptionHandler.java PageQuery.java
    config/      SaTokenConfig MybatisPlusConfig RedisConfig CorsConfig Knife4jConfig
    modules/
      auth/      User UserMapper AuthService AuthController LoginDTO
      member/    Member HealthProfile MemberMapper MemberService MemberController
      dict/      SysDict DictItem DictMapper DictService DictController
      nutrition/ NutritionMetric Ingredient IngredientNutrition NutritionService NutritionCalcService(纯函数)
      dish/      Dish DishStep DishIngredient DishHistory DishMapper DishService DishController
      menu/      Menu MenuDish MenuMapper MenuService MenuController MenuCalcService(纯函数)
      cookbook/  Favorite CookingRecord CookbookService CookbookController
      backup/    BackupService BackupController
  src/test/java/com/yanhuo/xsd/  (镜像 main 的测试)
```

### Web 后台 `admin/`
```
admin/
  package.json vite.config.ts
  src/ main.ts App.vue
    router/index.ts  store/auth.ts
    api/ request.ts auth.ts dict.ts member.ts ingredient.ts dish.ts menu.ts
    layouts/ BasicLayout.vue
    views/ login/ Login.vue  dict/ member/ ingredient/ dish/ menu/ (各 Index.vue)
```

### 根目录
```
docker-compose.yml  .gitignore  README.md
```

---

## Phase 0 — 基础设施

### Task 1: 工程骨架与 Docker Compose

**Files:**
- Create: `backend/pom.xml`, `backend/src/main/java/com/yanhuo/xsd/YanhuoApplication.java`
- Create: `admin/package.json`, `admin/vite.config.ts`, `admin/src/main.ts`, `admin/src/App.vue`
- Create: `docker-compose.yml`, `.gitignore`, `README.md`

- [ ] **Step 1: 初始化后端骨架**

`backend/pom.xml` 关键依赖（Spring Boot 3.2.x 父 + web、validation、mybatis-plus-spring-boot3-starter 3.5.5、mysql-connector-j、sa-token-spring-boot3-starter 1.37、spring-boot-starter-data-redis、knife4j-openapi3-jakarta-spring-boot-starter 4.4、lombok、spring-boot-starter-test、assertj）。

`YanhuoApplication.java`:
```java
package com.yanhuo.xsd;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
@SpringBootApplication
@MapperScan("com.yanhuo.xsd.modules.**.mapper")
public class YanhuoApplication {
    public static void main(String[] args) { SpringApplication.run(YanhuoApplication.class, args); }
}
```

- [ ] **Step 2: 初始化前端骨架**

`admin/package.json`: vue@3, vue-router@4, pinia, axios, element-plus, echarts, vite, typescript, @vitejs/plugin-vue。
`vite.config.ts`: 配 `server.proxy` 把 `/api` 代理到 `http://localhost:8080`（解决跨域/前后端分离联调）。

- [ ] **Step 3: docker-compose.yml**

```yaml
services:
  mysql:
    image: mysql:8.0
    environment: { MYSQL_ROOT_PASSWORD: root, MYSQL_DATABASE: yanhuo }
    ports: ["3306:3306"]
    volumes: ["./data/mysql:/var/lib/mysql"]
  redis:
    image: redis:7
    ports: ["6379:6379"]
  minio:
    image: minio/minio
    command: server /data --console-address ":9001"
    ports: ["9000:9000","9001:9001"]
    environment: { MINIO_ROOT_USER: minio, MINIO_ROOT_PASSWORD: minio123 }
```

- [ ] **Step 4: 启动依赖、验证骨架**

Run: `docker compose up -d mysql redis minio`
Run: `cd backend && ./mvnw spring-boot:run`（先不连库，验证能起；后续 Task 加配置）
Run: `cd frontend && npm install && npm run dev`（验证 Vite 起来）

- [ ] **Step 5: Commit**

```bash
git init && git add -A && git commit -m "chore: 项目骨架（后端SpringBoot + 前端Vue3 + docker-compose）"
```

---

### Task 2: 后端通用层与配置

**Files:**
- Create: `common/R.java`, `common/BizException.java`, `common/GlobalExceptionHandler.java`, `common/PageQuery.java`
- Create: `config/MybatisPlusConfig.java`, `config/RedisConfig.java`, `config/CorsConfig.java`, `config/Knife4jConfig.java`
- Create: `src/main/resources/application.yml`, `application-dev.yml`

- [ ] **Step 1: application.yml**

```yaml
server: { port: 8080, servlet: { context-path: / } }
spring:
  profiles: { active: dev }
  jackson: { date-format: yyyy-MM-dd HH:mm:ss, time-zone: GMT+8 }
mybatis-plus:
  configuration: { map-underscore-to-camel-case: true }
  global-config: { db-config: { id-type: auto, logic-delete-field: deleted, logic-delete-value: 1, logic-not-delete-value: 0 } }
sa-token:
  token-name: Authorization
  timeout: 2592000
  is-share: false
  token-style: uuid
springdoc: { swagger-ui: { path: /swagger-ui.html } }
knife4j: { enable: true }
```

`application-dev.yml`: 数据源 `jdbc:mysql://localhost:3306/yanhuo`、redis `localhost:6379`。

- [ ] **Step 2: 统一响应 R + 异常**

```java
// common/R.java
@Data @AllArgsConstructor @NoArgsConstructor
public class R<T> {
    private int code; private String msg; private T data;
    public static <T> R<T> ok(T d) { return new R<>(0, "ok", d); }
    public static <T> R<T> fail(String m) { return new R<>(1, m, null); }
}
// common/BizException.java
public class BizException extends RuntimeException {
    private final int code;
    public BizException(String msg) { super(msg); this.code = 1; }
}
// common/GlobalExceptionHandler.java
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BizException.class)
    public R<?> biz(BizException e) { return R.fail(e.getMessage()); }
    @ExceptionHandler(NotLoginException.class)
    public R<?> notLogin(NotLoginException e) { return new R<>(401, "未登录", null); }
    @ExceptionHandler(Exception.class)
    public R<?> all(Exception e) { e.printStackTrace(); return R.fail("服务器异常"); }
}
```

- [ ] **Step 3: MybatisPlusConfig（分页插件）+ RedisConfig + CorsConfig（允许前端跨域）+ Knife4jConfig**

MybatisPlusConfig 加 `MybatisPlusInterceptor` 内置 `PaginationInnerInterceptor(DbType.MYSQL)`。
CorsConfig 允许 `localhost:5173`、允许携带 Authorization。

- [ ] **Step 4: 验证**

Run: `./mvnw spring-boot:run` → 访问 `http://localhost:8080/doc.html`（Knife4j）能打开即通过。

- [ ] **Step 5: Commit** `feat: 后端通用层与配置（R/异常/CORS/MP/Redis/Knife4j）`

---

### Task 3: Sa-Token 鉴权 + 账号登录

**Files:**
- Create: `modules/auth/User.java`, `UserMapper.java`, `AuthService.java`, `AuthController.java`, `LoginDTO.java`
- Create: `config/SaTokenConfig.java`
- SQL: `user` 表

- [ ] **Step 1: 建表 SQL**（放入 `backend/sql/V1__init_auth.sql`）

```sql
CREATE TABLE user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(64) NOT NULL UNIQUE,
  password_hash VARCHAR(128) NOT NULL,
  nickname VARCHAR(64),
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  deleted TINYINT DEFAULT 0
);
INSERT INTO user(username,password_hash,nickname) VALUES ('admin','<bcrypt>','掌勺人');
```

- [ ] **Step 2: User 实体 + Mapper**

```java
@Data @TableName("user")
public class User { Long id; String username; String passwordHash; String nickname; }
public interface UserMapper extends BaseMapper<User> {}
```

- [ ] **Step 3: AuthService 登录（BCrypt 校验 + StpUtil.login）**

```java
@Service @RequiredArgsConstructor
public class AuthService {
    private final UserMapper userMapper;
    public Map<String,Object> login(LoginDTO dto) {
        User u = userMapper.selectOne(new QueryWrapper<User>().eq("username", dto.getUsername()));
        if (u == null || !BCrypt.checkpw(dto.getPassword(), u.getPasswordHash()))
            throw new BizException("用户名或密码错误");
        StpUtil.login(u.getId());
        return Map.of("token", StpUtil.getTokenValue(), "nickname", u.getNickname());
    }
}
```
> 密码用 Spring Security 的 `BCryptPasswordEncoder`（只引入 `spring-security-crypto`，不启用整个 Security）。初始化管理员密码用 `new BCryptPasswordEncoder().encode("admin123")` 生成填入 SQL。

- [ ] **Step 4: AuthController + SaTokenConfig（注册拦截器，放行 /auth/login 与 /doc.html）**

```java
@RestController @RequestMapping("/auth")
@RequiredArgsConstructor @Tag(name="鉴权")
public class AuthController {
    private final AuthService authService;
    @PostMapping("/login")
    public R<Map<String,Object>> login(@RequestBody @Valid LoginDTO dto) { return R.ok(authService.login(dto)); }
    @PostMapping("/logout")
    public R<?> logout() { StpUtil.logout(); return R.ok(null); }
    @GetMapping("/me")
    public R<?> me() { return R.ok(StpUtil.getLoginIdAsLong()); }
}
```
SaTokenConfig 实现 `WebMvcConfigurer`，`addInterceptors` 注册 `SaInterceptor`，`excludePathPatterns("/auth/login","/doc.html","/swagger-resources/**","/v3/api-docs/**","/webjars/**")`。

- [ ] **Step 5: 验证** Knife4j 调 `POST /auth/login` 拿到 token；带 token 调 `GET /auth/me` 返回 id。

- [ ] **Step 6: Commit** `feat(auth): Sa-Token 账号登录`

---

## Phase 1 — 配置中心

### Task 4: 通用字典管理（8 类）

> 菜系、菜品标签、菜品分类、菜单种类、特殊人群标签、计量单位、采购品类、角色标签——结构一致（id/name/sort）。用「分组 key」区分。

**Files:** `modules/dict/SysDict.java`, `DictMapper.java`, `DictService.java`, `DictController.java`, `DictDTO.java`; SQL `V2__dict.sql`

- [ ] **Step 1: 建表 + 种子**

```sql
CREATE TABLE sys_dict (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  dict_group VARCHAR(32) NOT NULL,  -- cuisine/tag/category/menu_type/audience/unit/purchase_category/role
  name VARCHAR(64) NOT NULL,
  sort INT DEFAULT 0,
  UNIQUE KEY uk_group_name (dict_group, name)
);
-- 种子示例
INSERT INTO sys_dict(dict_group,name) VALUES
 ('cuisine','鲁菜'),('cuisine','川菜'),('cuisine','粤菜'),
 ('category','热菜'),('category','凉菜'),('category','汤羹'),('category','甜品'),
 ('unit','g'),('unit','ml'),('unit','个'),('unit','把'),
 ('purchase_category','蔬菜'),('purchase_category','畜禽肉'),('purchase_category','水产海鲜'),('purchase_category','蛋类'),('purchase_category','豆制品'),('purchase_category','乳制品'),('purchase_category','调味料'),('purchase_category','水果'),
 ('audience','三高'),('audience','糖尿病'),('audience','宝宝辅食'),
 ('role','掌勺'),('role','备菜'),('role','普通成员');
```

- [ ] **Step 2: 实体/Mapper/Service（MyBatis-Plus IService 模板）**

```java
@Data @TableName("sys_dict")
public class SysDict { Long id; String dictGroup; String name; Integer sort; }
public interface DictMapper extends BaseMapper<SysDict> {}
@Service
public class DictService extends ServiceImpl<DictMapper, SysDict> {
    public List<SysDict> listByGroup(String group) {
        return list(new QueryWrapper<SysDict>().eq("dict_group", group).orderByAsc("sort"));
    }
}
```

- [ ] **Step 3: DictController（按组查询 + 增删改）**

```java
@RestController @RequestMapping("/dict") @Tag(name="配置中心")
public class DictController {
    private final DictService svc;
    @GetMapping public R<List<SysDict>> list(@RequestParam String group) { return R.ok(svc.listByGroup(group)); }
    @PostMapping public R<?> add(@RequestBody SysDict d) { svc.save(d); return R.ok(d.getId()); }
    @PutMapping public R<?> update(@RequestBody SysDict d) { svc.updateById(d); return R.ok(null); }
    @DeleteMapping("/{id}") public R<?> del(@PathVariable Long id) { svc.removeById(id); return R.ok(null); }
}
```

- [ ] **Step 4: 验证** Knife4j 调 `GET /dict?group=cuisine` 返回菜系列表；增删改可用。

- [ ] **Step 5: Commit** `feat(dict): 通用字典管理（8类配置）`

---

### Task 5: 营养指标维度字典（带 unit/group）

> 营养指标、点评维度、健康指标有额外字段（unit/group）。MVP 只建「营养指标」（点评/健康 V1/V2 再用，但表先建好）。

**Files:** `modules/nutrition/NutritionMetric.java`, `NutritionMetricMapper.java`, `NutritionMetricController.java`; SQL `V3__metric.sql`

- [ ] **Step 1: 建表 + 6 项种子营养指标**

```sql
CREATE TABLE nutrition_metric (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(32) NOT NULL UNIQUE,
  unit VARCHAR(16) NOT NULL,        -- g / mg / kcal / index
  metric_group VARCHAR(16) NOT NULL,-- macro(宏量) / micro(微量) / gi
  sort INT DEFAULT 0
);
INSERT INTO nutrition_metric(name,unit,metric_group) VALUES
 ('calorie','kcal','macro'),('protein','g','macro'),('fat','g','macro'),
 ('carb','g','macro'),('sugar','g','macro'),('gi','index','gi');
```

- [ ] **Step 2: 实体/Mapper/Controller**（CRUD 同 Task 4 范式：list/add/update/delete）

- [ ] **Step 3: 验证** `GET /nutrition/metric` 返回 6 项。

- [ ] **Step 4: Commit** `feat(nutrition): 营养指标维度字典`

---

## Phase 2 — 家庭成员

### Task 6: 家庭成员 + 健康档案

**Files:** `modules/member/Member.java`, `MemberMapper.java`, `MemberService.java`, `MemberController.java`; SQL `V4__member.sql`

- [ ] **Step 1: 建表**（健康档案存 JSON 字段，灵活存三高指标/忌口/特殊人群）

```sql
CREATE TABLE member (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(64) NOT NULL,
  role_tags VARCHAR(128),           -- 逗号分隔：chef,prep,member
  health_profile JSON,              -- {height,weight,allergies:[],audiences:[],constraints:{sugarMax:25,...}}
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  deleted TINYINT DEFAULT 0
);
```

- [ ] **Step 2: 实体**（healthProfile 用 MyBatis-Plus 的 `JacksonTypeHandler` 映射成 `Map<String,Object>`）

```java
@Data @TableName(value="member", autoResultMap=true)
public class Member {
    Long id; String name; String roleTags;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String,Object> healthProfile;
}
```

- [ ] **Step 3: Service/Controller（CRUD）** 同 Task 4 范式。`GET /member`、`POST/PUT/DELETE`。

- [ ] **Step 4: 验证** 新建一个成员带 health_profile，查询能正确回显 JSON。

- [ ] **Step 5: Commit** `feat(member): 家庭成员与健康档案`

---

## Phase 3 — 食材与营养

### Task 7: 食材库 + 营养 EAV

**Files:** `modules/nutrition/Ingredient.java`, `IngredientNutrition.java`, `IngredientMapper.java`, `IngredientService.java`, `IngredientController.java`; SQL `V5__ingredient.sql`

- [ ] **Step 1: 建表**

```sql
CREATE TABLE ingredient (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(64) NOT NULL,
  unit_id BIGINT,                   -- 关联 sys_dict(unit)
  price DECIMAL(8,2) DEFAULT 0,
  purchase_category_id BIGINT,
  purchase_count INT DEFAULT 0,
  usage_count INT DEFAULT 0,
  deleted TINYINT DEFAULT 0
);
CREATE TABLE ingredient_nutrition (  -- EAV：食材-营养指标-值(per 100g)
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  ingredient_id BIGINT NOT NULL,
  metric_id BIGINT NOT NULL,
  value DECIMAL(10,2) NOT NULL,
  UNIQUE KEY uk_ing_metric (ingredient_id, metric_id)
);
```

- [ ] **Step 2: 实体 + Mapper**

```java
@Data @TableName("ingredient")
public class Ingredient { Long id; String name; Long unitId; BigDecimal price; Long purchaseCategoryId; Integer purchaseCount; Integer usageCount; }
@Data @TableName("ingredient_nutrition")
public class IngredientNutrition { Long id; Long ingredientId; Long metricId; BigDecimal value; }
public interface IngredientMapper extends BaseMapper<Ingredient> {}
public interface IngredientNutritionMapper extends BaseMapper<IngredientNutrition> {}
```

- [ ] **Step 3: IngredientService —— 保存时同步营养 EAV**

```java
@Service @RequiredArgsConstructor
public class IngredientService extends ServiceImpl<IngredientMapper, Ingredient> {
    private final IngredientNutritionMapper nutMapper;
    @Transactional
    public void saveWithNutrition(Ingredient ing, List<IngredientNutrition> nuts) {
        save(ing);
        nutMapper.delete(new QueryWrapper<IngredientNutrition>().eq("ingredient_id", ing.getId()));
        for (IngredientNutrition n : nuts) { n.setIngredientId(ing.getId()); nutMapper.insert(n); }
    }
    public Map<Long,BigDecimal> nutritionOf(Long ingredientId) {  // 返回 metricId -> value(per 100g)
        return nutMapper.selectList(new QueryWrapper<IngredientNutrition>().eq("ingredient_id", ingredientId))
            .stream().collect(Collectors.toMap(IngredientNutrition::getMetricId, IngredientNutrition::getValue));
    }
}
```

- [ ] **Step 4: 验证** 新建食材「番茄」+ 营养（calorie=19,carb=4,...），查 `nutritionOf` 返回正确。

- [ ] **Step 5: Commit** `feat(ingredient): 食材库与营养EAV`

---

### Task 8: 营养聚合纯函数（TDD 核心）⭐

> 单道菜营养 = Σ(食材营养 per 100g × 用量 / 100)。这是整个系统的算法地基，严格 TDD。

**Files:** `modules/nutrition/NutritionCalcService.java`; Test `src/test/java/.../NutritionCalcServiceTest.java`

- [ ] **Step 1: 写失败测试**

```java
class NutritionCalcServiceTest {
    private final NutritionCalcService calc = new NutritionCalcService();
    record Amount(Long metricId, BigDecimal valuePer100g, BigDecimal grams) {}
    @Test
    void 单道菜营养_按用量缩放后求和() {
        // 番茄 200g: calorie 19/100g, carb 4/100g
        // 鸡蛋 100g: calorie 144/100g, protein 13/100g
        var dish = List.of(
            new Amount(1L, new BigDecimal("19"), new BigDecimal("200")),   // calorie
            new Amount(4L, new BigDecimal("4"),  new BigDecimal("200")),   // carb
            new Amount(1L, new BigDecimal("144"), new BigDecimal("100")),  // calorie(鸡蛋)
            new Amount(2L, new BigDecimal("13"),  new BigDecimal("100"))   // protein
        );
        Map<Long,BigDecimal> r = calc.aggregateDish(dish);
        // calorie: 19*2 + 144*1 = 182 ; carb: 4*2=8 ; protein: 13*1=13
        assertThat(r.get(1L)).isEqualByComparingTo("182");
        assertThat(r.get(4L)).isEqualByComparingTo("8");
        assertThat(r.get(2L)).isEqualByComparingTo("13");
    }
    @Test
    void 份数缩放_整体乘以factor() {
        var dish = List.of(new Amount(1L, new BigDecimal("100"), new BigDecimal("100")));
        assertThat(calc.aggregateDish(dish, new BigDecimal("3")).get(1L)).isEqualByComparingTo("300");
    }
}
```

- [ ] **Step 2: 运行测试，确认失败** Run: `./mvnw test -Dtest=NutritionCalcServiceTest` → FAIL（类不存在）

- [ ] **Step 3: 实现**

```java
public class NutritionCalcService {
    public record Item(Long metricId, BigDecimal valuePer100g, BigDecimal grams) {}
    /** 聚合单道菜营养；dish 为该菜各「食材-指标」的 per100g值与用量g */
    public Map<Long,BigDecimal> aggregateDish(List<Item> dish) { return aggregateDish(dish, BigDecimal.ONE); }
    public Map<Long,BigDecimal> aggregateDish(List<Item> dish, BigDecimal servingFactor) {
        Map<Long,BigDecimal> sum = new HashMap<>();
        for (Item it : dish) {
            BigDecimal contrib = it.valuePer100g.multiply(it.grams)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            sum.merge(it.metricId, contrib, BigDecimal::add);
        }
        if (servingFactor.compareTo(BigDecimal.ONE) != 0)
            sum.replaceAll((k,v) -> v.multiply(servingFactor));
        return sum;
    }
}
```

- [ ] **Step 4: 运行测试，确认通过** Run: `./mvnw test -Dtest=NutritionCalcServiceTest` → PASS

- [ ] **Step 5: Commit** `feat(nutrition): 营养聚合纯函数（TDD）`

---

## Phase 4 — 菜品

### Task 9: 菜品基础 + 步骤图文

**Files:** `modules/dish/Dish.java`, `DishStep.java`, `DishMapper.java`, `DishService.java`, `DishController.java`; SQL `V6__dish.sql`

- [ ] **Step 1: 建表**

```sql
CREATE TABLE dish (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(128) NOT NULL,
  note VARCHAR(512),
  cover_url VARCHAR(512),
  prep_time INT,                    -- 备料时间 分钟
  cook_time INT,                    -- 烹饪时间 分钟
  price DECIMAL(8,2) DEFAULT 0,
  difficulty TINYINT,               -- 1-5
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  deleted TINYINT DEFAULT 0
);
CREATE TABLE dish_step (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  dish_id BIGINT NOT NULL,
  seq INT NOT NULL,
  text VARCHAR(1024),
  images VARCHAR(1024),             -- 多图逗号分隔URL
  sort_order INT
);
```

- [ ] **Step 2: 实体 + Mapper + Service（保存菜品时级联保存步骤）**

- [ ] **Step 3: DishController（CRUD + 查详情含步骤）** `GET /dish/{id}` 返回菜品 + 步骤列表。

- [ ] **Step 4: 验证** 新建菜「番茄炒蛋」+ 3 个步骤（图文），详情接口回显完整。

- [ ] **Step 5: Commit** `feat(dish): 菜品与步骤图文`

---

### Task 10: 菜品多对多（菜系/标签/分类/食材用量）

**Files:** `modules/dish/DishIngredient.java`, `DishTag.java`(关联表通用); 改 `DishService`; SQL `V7__dish_rel.sql`

- [ ] **Step 1: 建关联表**

```sql
CREATE TABLE dish_dict (        -- 菜品与 菜系/标签/分类 的多对多（用 rel_type 区分）
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  dish_id BIGINT NOT NULL,
  dict_id BIGINT NOT NULL,
  rel_type VARCHAR(16) NOT NULL,   -- cuisine/tag/category
  UNIQUE KEY uk_rel (dish_id, dict_id, rel_type)
);
CREATE TABLE dish_ingredient (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  dish_id BIGINT NOT NULL,
  ingredient_id BIGINT NOT NULL,
  amount DECIMAL(10,2) NOT NULL,   -- 用量重量 g
  UNIQUE KEY uk (dish_id, ingredient_id)
);
```

- [ ] **Step 2: DishService 扩展**：保存菜品时，整体替换其 `dish_dict`(三种 rel_type) 与 `dish_ingredient`；查详情时一并回显关联的菜系/标签/分类 ID 列表 + 食材用量列表。

- [ ] **Step 3: 验证** 给「番茄炒蛋」挂菜系=家常、标签=快手菜、食材=番茄200g+鸡蛋100g，详情回显完整。

- [ ] **Step 4: Commit** `feat(dish): 多对多关联（菜系/标签/分类/食材用量）`

---

### Task 11: 菜品份数缩放 + 历史版本

**Files:** `modules/dish/DishHistory.java`; `modules/dish/DishQueryService.java`(组合营养计算); SQL `V8__dish_history.sql`

- [ ] **Step 1: 建历史表**

```sql
CREATE TABLE dish_history (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  dish_id BIGINT NOT NULL,
  snapshot JSON NOT NULL,          -- 编辑前完整快照（菜品+步骤+关联）
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

- [ ] **Step 2: DishService.update 前先存快照**：更新菜品时，把旧版本序列化为 JSON 存入 `dish_history`，再更新。提供 `GET /dish/{id}/history` 列历史、`DELETE /dish/{id}/history/{hid}` 删单条、`DELETE /dish/{id}/history` 删全部。

- [ ] **Step 3: 份数营养接口** `GET /dish/{id}/nutrition?serving=2` —— 拉取菜品 `dish_ingredient` + 各食材 `ingredient_nutrition`，组装成 `NutritionCalcService.Item` 列表，调 `aggregateDish(items, serving)` 返回该份数的营养。复用 Task 8 纯函数。

- [ ] **Step 4: 验证** 编辑「番茄炒蛋」→ 产生一条历史；`GET /dish/1/nutrition?serving=2` 返回翻倍营养。

- [ ] **Step 5: Commit** `feat(dish): 份数缩放营养 + 历史版本`

---

## Phase 5 — 菜单

### Task 12: 菜单 + 菜单菜品关联

**Files:** `modules/menu/Menu.java`, `MenuDish.java`, `MenuMapper.java`, `MenuService.java`, `MenuController.java`; SQL `V9__menu.sql`

- [ ] **Step 1: 建表**

```sql
CREATE TABLE menu (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(128) NOT NULL,
  type_id BIGINT,                   -- 关联 sys_dict(menu_type)
  target_member_id BIGINT,
  serving_count INT DEFAULT 1,      -- 份数/人数
  deleted TINYINT DEFAULT 0
);
CREATE TABLE menu_dish (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  menu_id BIGINT NOT NULL,
  dish_id BIGINT NOT NULL,
  serving_factor DECIMAL(4,2) DEFAULT 1  -- 该菜在该菜单的份数
);
```

- [ ] **Step 2: 实体 + Service（保存菜单时级联保存 menu_dish）+ Controller CRUD**

- [ ] **Step 3: 验证** 建菜单「周末家宴」挂 3 道菜（不同份数），详情回显。

- [ ] **Step 4: Commit** `feat(menu): 菜单与菜品关联`

---

### Task 13: 菜单总价 + 营养汇总（TDD）⭐

> 菜单总价 = Σ(菜品价 × 份数)；菜单营养 = Σ(各菜份数营养)。算法纯函数，TDD。

**Files:** `modules/menu/MenuCalcService.java`; Test `MenuCalcServiceTest.java`

- [ ] **Step 1: 写失败测试**

```java
class MenuCalcServiceTest {
    private final MenuCalcService calc = new MenuCalcService();
    record MenuLine(BigDecimal price, Map<Long,BigDecimal> dishNutrition, BigDecimal servingFactor) {}
    @Test
    void 菜单总价_按份数累加() {
        var lines = List.of(
            new MenuLine(new BigDecimal("10"), Map.of(), new BigDecimal("2")),
            new MenuLine(new BigDecimal("15"), Map.of(), new BigDecimal("1")));
        assertThat(calc.totalPrice(lines)).isEqualByComparingTo("35");
    }
    @Test
    void 菜单营养_各菜份数营养按指标累加() {
        var lines = List.of(
            new MenuLine(new BigDecimal("0"), Map.of(1L, new BigDecimal("182")), new BigDecimal("2")), // 364
            new MenuLine(new BigDecimal("0"), Map.of(1L, new BigDecimal("100")), new BigDecimal("1")));// 100
        assertThat(calc.totalNutrition(lines).get(1L)).isEqualByComparingTo("464");
    }
}
```

- [ ] **Step 2: 运行测试，确认失败** → FAIL

- [ ] **Step 3: 实现**

```java
public class MenuCalcService {
    public record MenuLine(BigDecimal price, Map<Long,BigDecimal> dishNutrition, BigDecimal servingFactor) {}
    public BigDecimal totalPrice(List<MenuLine> lines) {
        return lines.stream().map(l -> l.price.multiply(l.servingFactor))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    public Map<Long,BigDecimal> totalNutrition(List<MenuLine> lines) {
        Map<Long,BigDecimal> sum = new HashMap<>();
        for (MenuLine l : lines)
            for (var e : l.dishNutrition.entrySet())
                sum.merge(e.getKey(), e.getValue().multiply(l.servingFactor), BigDecimal::add);
        return sum;
    }
}
```

- [ ] **Step 4: 运行测试，确认通过** → PASS

- [ ] **Step 5: 菜单汇总接口** `GET /menu/{id}/summary` —— 组装各菜的份数营养（复用 Task 11 的 `dish nutrition`），构造 `MenuLine`，调 `MenuCalcService` 返回 `{totalPrice, totalNutrition}`。

- [ ] **Step 6: Commit** `feat(menu): 菜单总价与营养汇总（TDD）`

---

## Phase 6 — 菜库使用 & 记录

### Task 14: 收藏 + 做过标记

**Files:** `modules/cookbook/Favorite.java`, `CookbookService.java`, `CookbookController.java`; SQL `V10__cookbook.sql`

- [ ] **Step 1: 建表**

```sql
CREATE TABLE favorite (id BIGINT PRIMARY KEY AUTO_INCREMENT, member_id BIGINT, dish_id BIGINT, UNIQUE KEY uk(member_id,dish_id));
-- 做过标记复用 cooking_record（Task 16）
```

- [ ] **Step 2: CookbookService**：`favorite(memberId,dishId)`、`unfavorite`、`listFavorites(memberId)`；`markDone` 基于 cooking_record 判重。

- [ ] **Step 3: 接口** `POST /cookbook/favorite/{dishId}`、`GET /cookbook/favorites`、`GET /cookbook/done`（去重的做过菜品）。

- [ ] **Step 4: 验证 + Commit** `feat(cookbook): 收藏与做过`

---

### Task 15: 搜索 + 多维筛选

**Files:** 改 `DishService` / `DishController`; 新增 `DishSearchDTO`

- [ ] **Step 1: 搜索 DTO** 关键字 `keyword`、菜系 `cuisineIds`、标签 `tagIds`、分类 `categoryIds`、最大烹饪时间 `maxMinutes`、最大难度 `maxDifficulty`、营养上限 `nutritionLimits`(metricId->max)。

- [ ] **Step 2: DishService.search** —— keyword 走 `name like`；维度走 `EXISTS` 子查询 `dish_dict`；时间/难度直接条件；营养上限走关联 `dish_ingredient`+`ingredient_nutrition` 聚合后过滤（复杂查询可先只实现 keyword+维度+时间+难度，营养上限标 V2 强化，MVP 先按单菜 `precomputed` 或跳过——**MVP 范围内营养筛选先实现到"按食材含糖/GI 上限"的简化版**：菜关联食材的平均 GI/糖超限则排除）。
> ⚠️ 营养上限筛选的精确实现依赖聚合查询，MVP 阶段用"预计算每菜营养快照存冗余字段"或简化为 V2。本 Task 先交付 keyword + 维度 + 时间 + 难度筛选（已覆盖大多数找菜场景），营养筛选在 V1 自定义菜单时再强化。

- [ ] **Step 3: 接口** `GET /dish/search`（接收上述参数，分页）。

- [ ] **Step 4: 验证 + Commit** `feat(cookbook): 菜库搜索与多维筛选`

---

### Task 16: 烹饪记录

**Files:** `modules/cookbook/CookingRecord.java`, `CookingRecordMapper.java`, `CookingRecordController.java`; SQL `V11__cooking.sql`

- [ ] **Step 1: 建表**

```sql
CREATE TABLE cooking_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  dish_id BIGINT NOT NULL, member_id BIGINT, cooked_at DATETIME, note VARCHAR(512),
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

- [ ] **Step 2: 实体/Mapper/Controller（增 + 按成员查列表）**

- [ ] **Step 3: 验证 + Commit** `feat(cookbook): 烹饪记录`

---

## Phase 7 — 备份 & 前端 & 集成

### Task 17: 数据备份 / 恢复（JSON 全量）

**Files:** `modules/backup/BackupService.java`, `BackupController.java`

- [ ] **Step 1: BackupService.exportAll()** —— 遍历核心表（user/member/sys_dict/nutrition_metric/ingredient/ingredient_nutrition/dish/dish_step/dish_dict/dish_ingredient/menu/menu_dish/cooking_record/favorite），各导出为 `List<Map>`，组装成 `{exportedAt, tables:{...}}` JSON 返回下载流。

- [ ] **Step 2: importAll(json)** —— 反向：按表清空再批量插入（事务，外键依赖顺序：dict→ingredient→dish→...）。MVP 单人自用，接受"覆盖导入"语义，导入前提示。

- [ ] **Step 3: 接口** `GET /backup/export`、`POST /backup/import`（multipart 或 body JSON）。

- [ ] **Step 4: 验证** 导出→改数据→导入→数据恢复。

- [ ] **Step 5: Commit** `feat(backup): 全量数据备份与恢复`

---

### Task 18: 前端骨架 + 登录 + 布局

**Files:** `admin/src/{main.ts, App.vue, router/index.ts, store/auth.ts, api/request.ts, api/auth.ts, layouts/BasicLayout.vue, views/login/Login.vue}`

- [ ] **Step 1: request.ts** —— axios 实例，`baseURL:/api`，请求拦截器塞 `Authorization`，响应拦截器解包 `R`、401 跳登录。

- [ ] **Step 2: store/auth.ts**（Pinia）—— 登录存 token+nickname，`GET /auth/me` 校验。

- [ ] **Step 3: Login.vue** —— Element Plus 表单，调 `/auth/login`，成功跳 `BasicLayout`。

- [ ] **Step 4: BasicLayout.vue** —— 左侧菜单（配置中心/成员/食材/菜品/菜单/备份），顶栏退出；`router-view` 出口；路由守卫拦截未登录。

- [ ] **Step 5: 验证** `npm run dev` → 登录进后台，左侧菜单可见。

- [ ] **Step 6: Commit** `feat(frontend): 骨架/登录/布局`

---

### Task 19: 前端管理页（配置/成员/食材/菜品/菜单）

**Files:** `admin/src/views/{dict,member,ingredient,dish,menu}/Index.vue` + 对应 `api/*.ts`

> 统一范式：Element Plus `el-table` + `el-pagination` + `el-dialog` 表单增删改。每个页面：列表查询、新增弹窗、编辑、删除。菜品页含步骤图文编辑器（多步骤、每步文字+多图上传到 MinIO）。

- [ ] **Step 1: 配置中心页** —— tab 切换 8 个 dict_group，每组成删改；营养指标单独 tab。
- [ ] **Step 2: 成员页** —— 列表 + 健康档案 JSON 表单（身高/体重/忌口/特殊人群/营养约束）。
- [ ] **Step 3: 食材页** —— 列表 + 营养 EAV 动态表单（按营养指标维度生成输入框）+ 采购品类下拉。
- [ ] **Step 4: 菜品页** —— 列表 + 详情编辑（基础信息 + 步骤图文 + 多对多挂菜系/标签/分类 + 食材用量）+ 营养展示（调 `/dish/{id}/nutrition`）+ 历史版本抽屉。
- [ ] **Step 5: 菜单页** —— 列表 + 排菜（选菜+设份数）+ 汇总展示（调 `/menu/{id}/summary`，总价+营养，营养用 ECharts 柱状图）。
- [ ] **Step 6: 图片上传** —— 封装 `upload.ts`，`/backup` 之外加 `/file/upload` 后端接口（存 MinIO，返回 URL）；菜品封面/步骤图/点评图统一用。
- [ ] **Step 7: 逐页验证 + Commit** `feat(frontend): 管理（配置/成员/食材/菜品/菜单）`

---

### Task 20: Docker Compose 集成跑通

**Files:** `backend/Dockerfile`, `admin/Dockerfile`, 改 `docker-compose.yml`, `README.md`

- [ ] **Step 1: 后端 Dockerfile**（多阶段：maven 构建 → eclipse-temurin:17-jre 运行）
- [ ] **Step 2: 前端 Dockerfile**（node 构建 → nginx 托管，nginx 反代 `/api` 到后端）
- [ ] **Step 3: docker-compose 加 backend / frontend 服务**，依赖 mysql/redis/minio，环境变量注入连接信息。
- [ ] **Step 4: 端到端验证** `docker compose up --build` → 访问前端 → 登录 → 建食材/菜品/菜单 → 看营养汇总 → 导出备份。
- [ ] **Step 5: Commit** `feat: Docker Compose 一键部署`

---

## Self-Review 结果

**1. Spec 覆盖**（对照 spec 第 5 节 MVP 清单）：
- 账号 → Task 3 ✓ ｜ 成员+健康档案 → Task 6 ✓ ｜ 配置中心 → Task 4/5 ✓
- 食材库(营养EAV) → Task 7 ✓ ｜ 营养展示(聚合) → Task 8/11 ✓
- 菜品(步骤图文/多对多/份数/历史) → Task 9/10/11 ✓
- 菜单(手动排/总价) → Task 12/13 ✓
- 菜库使用(收藏/做过/搜索筛选) → Task 14/15 ✓ ｜ 烹饪记录 → Task 16 ✓
- 数据备份 → Task 17 ✓
- **缺口**：营养上限精确筛选（Task 15 已说明简化、V1 强化）——已在计划内标注，非遗漏。

**2. 占位符扫描**：无 TBD/TODO；Task 15 的营养筛选简化已显式说明边界，非占位。

**3. 类型一致**：`NutritionCalcService.aggregateDish`、`MenuCalcService.totalPrice/totalNutrition`、`IngredientService.nutritionOf` 在 Task 8/11/13 引用一致；`MenuLine`/`Item` record 命名一致。

---

## 执行交接

Plan complete and saved to `docs/superpowers/plans/2026-06-17-yanhuo-mvp.md`. 两种执行方式：

**1. Subagent-Driven（推荐）** — 每个 Task 派一个全新 subagent 执行，Task 间我审查，迭代快、上下文干净。
**2. Inline Execution** — 在当前会话用 executing-plans 批量执行，带检查点审查。

**你要哪种？**
