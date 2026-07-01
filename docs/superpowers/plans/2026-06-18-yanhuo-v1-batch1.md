# 咕嘟小食单 · V1 第一批 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 打通「小程序可用主线」——家庭里任何人能在小程序上登录、看菜库、看做法（带步骤计时器）、点评打分、标记做过、切换当前就餐成员；后端补齐点评模块 + 通知通道抽象（站内先实现、微信订阅消息留入口）。

**Architecture:** 沿用 MVP 的前后端分离。后端在 `menu-api`（Java Spring Boot）内新增 `review`、`notification` 两个 module，复用既有 Sa-Token / R / MyBatis-Plus 范式。小程序是全新独立工程 `menu-mini`（uniapp + Vue3 + uView Plus），通过同一套 Java RESTful API 消费数据。通知用 **Strategy 抽象**：`NotificationChannel` 接口 + 多通道实现，第一批只启用站内 `in_app`，微信 `wx_subscribe` 通道留空壳，资质到位后填实现、业务零改动。

**Tech Stack:** 后端 Java 17 / Spring Boot 3 / MyBatis-Plus / Sa-Token（新增 module 复用）；小程序 uniapp（Vue3）+ uView Plus + Pinia + uni.request 封装；沿用 MinIO/本地图片上传 `/file/upload`。

**Spec:** `docs/superpowers/specs/2026-06-16-gudu-xiaoshidan-design.md`（V1 范围第 5 节；本计划覆盖 line 86/90/91/93 的「点评 / 通知通道入口 / 权限边界（简化）/ 切换就餐成员」+ 小程序基础浏览，周计划/采购/库存/通知业务触发留第二批）。

**已对齐决策：**
1. 分两批交付，**本计划是第一批**（小程序主线）。
2. 小程序登录用**账号密码复用 Sa-Token**，不走 `wx.login`。
3. 微信订阅消息 V1 **不实现**，但用 `NotificationChannel` Strategy **留入口**，第一批只做站内 `in_app`；微信资质暂不办，小程序先用 H5/开发者工具调试。
4. 小程序 UI 库用 **uView Plus**。

---

## 文件结构

### 后端 `menu-api` 新增

```
menu-api/src/main/java/com/gudu/xsd/modules/
  review/
    Review.java                      实体（点评主表：星级+文字+图片）
    ReviewScore.java                 实体（点评-维度分：review_id+dimension_id+score）
    ReviewSaveDTO.java               入参（点评提交）
    mapper/ReviewMapper.java
    mapper/ReviewScoreMapper.java
    ReviewService.java               保存点评（级联维度分）+ 查菜品点评 + 平均分
    ReviewController.java            提交 / 查菜品点评列表 / 查菜品平均分
  notification/
    Notification.java                实体（站内消息）
    mapper/NotificationMapper.java
    NotificationPayload.java         record（载荷：memberId/type/title/content）
    NotificationChannel.java         接口（Strategy 通道）
    InAppChannel.java                站内通道实现（写 notification 表）
    WxSubscribeChannel.java          微信订阅消息通道（V1 空壳预留）
    NotificationService.java         按 channelKey 分发
    NotificationController.java      列表 / 未读数 / 标记已读
  member/MemberController.java       Modify：加 /member/current 切换就餐成员（Sa-Token session）
menu-api/sql/
  V12__review.sql                    review + review_score 表 + review_dimension 字典种子
  V13__notification.sql              notification 表
```

### 小程序 `menu-mini/`（全新工程）

```
menu-mini/
  package.json vite.config.ts manifest.json pages.json main.ts App.vue
  src/
    main.ts App.vue
    utils/request.ts                 uni.request 封装（baseURL + Authorization + 401 跳登录）
    store/auth.ts store/member.ts    Pinia：登录态 / 当前就餐成员
    api/auth.ts dish.ts review.ts cookbook.ts member.ts
    pages/
      login/Login.vue                账号密码登录
      index/Index.vue                首页（菜库入口 + 切换成员）
      dish/List.vue                  菜库列表 + 搜索筛选
      dish/Detail.vue                菜品详情（步骤图文 + 营养 + 步骤计时器 + 做过标记）
      dish/Review.vue                点评（星级 + 文字 + 图片 + 多维打分）
      dish/Create.vue                录入新菜品（家庭成员贡献菜库）
```

---

## Phase A — 后端点评 + 通知通道 + 就餐成员上下文

### Task A1: 点评维度字典（复用 sys_dict）

> 点评维度（口味/难度/营养均衡/外观）只有 name，复用 MVP 的 `sys_dict`（加 `review_dimension` 组），不新建表 —— 遵循「能配置化就配置化」铁律。

**Files:**
- Create: `menu-api/sql/V12__review.sql`（先只放字典种子，表结构在下个 Task）

- [ ] **Step 1: 写字典种子 SQL**

`menu-api/sql/V12__review.sql`:
```sql
-- 点评维度（复用 sys_dict，group=review_dimension；可在后台配置中心增删）
INSERT INTO sys_dict(dict_group, name, sort) VALUES
  ('review_dimension', '口味', 1),
  ('review_dimension', '难度', 2),
  ('review_dimension', '营养均衡', 3),
  ('review_dimension', '外观', 4);
```

- [ ] **Step 2: 验证**

启动后端连库（开发环境），执行该 SQL（或重启服务由 Flyway/初始化脚本应用）。`GET /dict?group=review_dimension` 返回 4 项。

- [ ] **Step 3: Commit**

```bash
git add menu-api/sql/V12__review.sql
git commit -m "feat(review): 点评维度字典（复用 sys_dict group=review_dimension）"
```

---

### Task A2: 点评模块（review + review_score，TDD 平均分）⭐

> 一条点评 = 星级总评 + 文字 + 多图 + 多维度分（口味/难度/营养均衡/外观，各 1–5）。菜品平均分 = 该菜所有点评星级均值；各维度均分同理。平均分计算是纯函数，TDD。

**Files:**
- Create: `menu-api/sql/V12__review.sql`（追加表结构）
- Create: `modules/review/Review.java`, `ReviewScore.java`, `ReviewSaveDTO.java`, `mapper/ReviewMapper.java`, `mapper/ReviewScoreMapper.java`, `ReviewService.java`, `ReviewController.java`
- Create: `src/test/java/.../review/ReviewServiceTest.java`

- [ ] **Step 1: 写失败测试（平均分纯函数）**

`src/test/java/com/gudu/xsd/modules/review/ReviewServiceTest.java`:
```java
package com.gudu.xsd.modules.review;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewServiceTest {

    private final ReviewService calc = new ReviewService(null, null, null);

    record ScoreRow(Long dimensionId, Integer score) {}

    @Test
    void 菜品总评均分_按星级求平均() {
        var reviews = List.of(5, 4, 3); // 三条点评的星级
        assertThat(calc.averageStar(reviews)).isEqualByComparingTo("4.0");
    }

    @Test
    void 各维度均分_按维度分组求平均() {
        // 两条点评：口味各打 4/5，外观各打 3/4
        var scores = List.of(
            new ScoreRow(1L, 4), new ScoreRow(2L, 3),
            new ScoreRow(1L, 5), new ScoreRow(2L, 4));
        Map<Long, String> avg = calc.averageByDimension(scores);
        assertThat(avg.get(1L)).isEqualTo("4.5");  // 口味 (4+5)/2
        assertThat(avg.get(2L)).isEqualTo("3.5");  // 外观 (3+4)/2
    }
}
```

- [ ] **Step 2: 运行测试，确认失败**

Run: `cd menu-api && ./mvnw test -Dtest=ReviewServiceTest`
Expected: FAIL（`ReviewService` 构造缺参 / 方法不存在）

- [ ] **Step 3: 建表 SQL（追加到 V12）**

`menu-api/sql/V12__review.sql` 末尾追加:
```sql
CREATE TABLE review (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  dish_id BIGINT NOT NULL,
  member_id BIGINT NOT NULL,
  star_rating TINYINT NOT NULL,            -- 总评星级 1-5
  text VARCHAR(1024),
  images VARCHAR(2044),                    -- 多图逗号分隔 URL
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  deleted TINYINT DEFAULT 0
);
CREATE TABLE review_score (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  review_id BIGINT NOT NULL,
  dimension_id BIGINT NOT NULL,            -- 关联 sys_dict(review_dimension)
  score TINYINT NOT NULL,                  -- 1-5
  UNIQUE KEY uk_rev_dim (review_id, dimension_id)
);
CREATE INDEX idx_review_dish ON review(dish_id);
```

- [ ] **Step 4: 实体 + Mapper**

`modules/review/Review.java`:
```java
package com.gudu.xsd.modules.review;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("review")
public class Review {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long dishId;
    private Long memberId;
    private Integer starRating;
    private String text;
    private String images;
    private LocalDateTime createTime;
    @TableLogic
    private Integer deleted;
}
```

`modules/review/ReviewScore.java`:
```java
package com.gudu.xsd.modules.review;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("review_score")
public class ReviewScore {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long reviewId;
    private Long dimensionId;
    private Integer score;
}
```

`modules/review/ReviewSaveDTO.java`:
```java
package com.gudu.xsd.modules.review;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ReviewSaveDTO {
    @NotNull
    private Long dishId;
    @NotNull @Min(1) @Max(5)
    private Integer starRating;
    private String text;
    private List<String> images;                  // 图片 URL 列表
    /** 维度分：dimensionId -> score(1-5) */
    private Map<Long, Integer> dimensionScores;
}
```

`modules/review/mapper/ReviewMapper.java`:
```java
package com.gudu.xsd.modules.review.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gudu.xsd.modules.review.Review;

public interface ReviewMapper extends BaseMapper<Review> {}
```

`modules/review/mapper/ReviewScoreMapper.java`:
```java
package com.gudu.xsd.modules.review.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gudu.xsd.modules.review.ReviewScore;

public interface ReviewScoreMapper extends BaseMapper<ReviewScore> {}
```

- [ ] **Step 5: ReviewService（保存级联 + 纯函数平均分）**

`modules/review/ReviewService.java`:
```java
package com.gudu.xsd.modules.review;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gudu.xsd.modules.review.mapper.ReviewMapper;
import com.gudu.xsd.modules.review.mapper.ReviewScoreMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewMapper reviewMapper;
    private final ReviewScoreMapper reviewScoreMapper;
    // 第三个参数留给 future use（如 DishMapper 校验菜品存在），测试用 null 传入
    private final Object __unused = null;

    /** 提交点评：当前登录的就餐成员 + 级联维度分。 */
    @Transactional
    public Long submit(ReviewSaveDTO dto) {
        Long memberId = StpUtil.getSession().getLong("currentMemberId");
        Review r = new Review();
        r.setDishId(dto.getDishId());
        r.setMemberId(memberId);
        r.setStarRating(dto.getStarRating());
        r.setText(dto.getText());
        r.setImages(dto.getImages() == null ? null : String.join(",", dto.getImages()));
        reviewMapper.insert(r);
        if (dto.getDimensionScores() != null) {
            dto.getDimensionScores().forEach((dimId, score) -> {
                ReviewScore s = new ReviewScore();
                s.setReviewId(r.getId());
                s.setDimensionId(dimId);
                s.setScore(score);
                reviewScoreMapper.insert(s);
            });
        }
        return r.getId();
    }

    /** 某菜的所有点评（最新优先）。 */
    public List<Review> listByDish(Long dishId) {
        return reviewMapper.selectList(
            new QueryWrapper<Review>().eq("dish_id", dishId).orderByDesc("create_time"));
    }

    // ===== 纯函数：平均分（TDD 覆盖） =====

    /** 总评星级均分（保留 1 位）。 */
    public BigDecimal averageStar(List<Integer> stars) {
        if (stars == null || stars.isEmpty()) return BigDecimal.ZERO;
        BigDecimal sum = stars.stream().map(BigDecimal::new)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(new BigDecimal(stars.size()), 1, RoundingMode.HALF_UP);
    }

    /** 各维度均分：dimensionId -> "x.x"。 */
    public Map<Long, String> averageByDimension(List<? extends DimensionScore> rows) {
        Map<Long, List<Integer>> grouped = rows.stream()
            .filter(r -> r.dimensionId() != null && r.score() != null)
            .collect(Collectors.groupingBy(DimensionScore::dimensionId,
                Collectors.mapping(DimensionScore::score, Collectors.toList())));
        return grouped.entrySet().stream().collect(Collectors.toMap(
            Map.Entry::getKey,
            e -> averageStar(e.getValue()).toPlainString()));
    }

    /** 维度分行接口（测试 record 实现它）。 */
    public interface DimensionScore {
        Long dimensionId();
        Integer score();
    }
}
```

> ⚠️ 测试里的 `ScoreRow` record 需实现 `ReviewService.DimensionScore`。更新测试，让 `ScoreRow implements ReviewService.DimensionScore`：
> ```java
> record ScoreRow(Long dimensionId, Integer score) implements ReviewService.DimensionScore {}
> ```
> 并把测试构造改为 `new ReviewService(null, null)`（两参，第三个 `__unused` 给默认值会编译失败 —— 改用双参构造）。**修正**：去掉 `__unused`，构造改为双参：

```java
public ReviewService(ReviewMapper reviewMapper, ReviewScoreMapper reviewScoreMapper) {
    this.reviewMapper = reviewMapper;
    this.reviewScoreMapper = reviewScoreMapper;
}
```
删掉类上的 `@RequiredArgsConstructor` 和 `__unused` 字段。测试 `new ReviewService(null, null)` 即可。

- [ ] **Step 6: ReviewController**

`modules/review/ReviewController.java`:
```java
package com.gudu.xsd.modules.review;

import com.gudu.xsd.common.R;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/review")
@RequiredArgsConstructor
@Tag(name = "点评")
public class ReviewController {

    private final ReviewService svc;

    @PostMapping
    public R<Long> submit(@RequestBody @Valid ReviewSaveDTO dto) {
        return R.ok(svc.submit(dto));
    }

    @GetMapping("/dish/{dishId}")
    public R<List<Review>> listByDish(@PathVariable Long dishId) {
        return R.ok(svc.listByDish(dishId));
    }

    @GetMapping("/dish/{dishId}/avg")
    public R<Map<String, Object>> avg(@PathVariable Long dishId) {
        List<Review> reviews = svc.listByDish(dishId);
        List<Integer> stars = reviews.stream().map(Review::getStarRating).toList();
        Map<String, Object> result = Map.of(
            "star", svc.averageStar(stars).toPlainString(),
            "count", reviews.size());
        return R.ok(result);
    }
}
```

- [ ] **Step 7: 运行测试，确认通过**

Run: `cd menu-api && ./mvnw test -Dtest=ReviewServiceTest`
Expected: PASS（2 个纯函数用例通过）

- [ ] **Step 8: 启动验证**

Run: `cd menu-api && ./mvnw spring-boot:run`。先 `POST /member/current?memberId=1`（Task A4 会建，临时用 `StpUtil.getSession().set` 手动设也可）设就餐成员，再 Knife4j 调 `POST /review` 提交一条点评，`GET /review/dish/1/avg` 返回均分。

- [ ] **Step 9: Commit**

```bash
git add menu-api/src/main/java/com/gudu/xsd/modules/review/ menu-api/src/test/java/com/gudu/xsd/modules/review/ menu-api/sql/V12__review.sql
git commit -m "feat(review): 点评模块（星级+文字+图片+多维打分，平均分 TDD）"
```

---

### Task A3: 通知通道抽象（Strategy，站内先实现，微信留入口）

> 业务侧只调 `NotificationService.send(payload, channelKey)`。V1 只注册 `in_app`（写 notification 表）；`wx_subscribe` 通道注册但为空壳 —— 微信资质到位后填实现，业务零改动，这就是「留入口」。

**Files:**
- Create: `menu-api/sql/V13__notification.sql`
- Create: `modules/notification/Notification.java`, `mapper/NotificationMapper.java`, `NotificationPayload.java`, `NotificationChannel.java`, `InAppChannel.java`, `WxSubscribeChannel.java`, `NotificationService.java`, `NotificationController.java`

- [ ] **Step 1: 建表**

`menu-api/sql/V13__notification.sql`:
```sql
CREATE TABLE notification (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  member_id BIGINT NOT NULL,
  type VARCHAR(32) NOT NULL,          -- expiry(临期)/shopping(采购)/prep(备菜)/...
  channel VARCHAR(32) NOT NULL,       -- in_app / wx_subscribe
  title VARCHAR(128),
  content VARCHAR(1024),
  is_read TINYINT DEFAULT 0,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_notif_member ON notification(member_id, is_read);
```

- [ ] **Step 2: 实体 + Mapper**

`modules/notification/Notification.java`:
```java
package com.gudu.xsd.modules.notification;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("notification")
public class Notification {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long memberId;
    private String type;
    private String channel;
    private String title;
    private String content;
    private Integer isRead;
    private LocalDateTime createTime;
}
```

`modules/notification/mapper/NotificationMapper.java`:
```java
package com.gudu.xsd.modules.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gudu.xsd.modules.notification.Notification;

public interface NotificationMapper extends BaseMapper<Notification> {}
```

- [ ] **Step 3: Strategy 接口 + 载荷**

`modules/notification/NotificationPayload.java`:
```java
package com.gudu.xsd.modules.notification;

/** 通知载荷（与通道实现解耦，业务侧只构造这个）。 */
public record NotificationPayload(Long memberId, String type, String title, String content) {}
```

`modules/notification/NotificationChannel.java`:
```java
package com.gudu.xsd.modules.notification;

/** 通知通道策略。新增通道（如邮件/短信）只需实现并注册为 Bean。 */
public interface NotificationChannel {
    /** 通道标识，业务侧用这个 key 选择通道。 */
    String channelKey();
    /** 实际投递。 */
    void send(NotificationPayload payload);
}
```

- [ ] **Step 4: 站内通道实现**

`modules/notification/InAppChannel.java`:
```java
package com.gudu.xsd.modules.notification;

import com.gudu.xsd.modules.notification.mapper.NotificationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InAppChannel implements NotificationChannel {

    private final NotificationMapper notificationMapper;

    @Override
    public String channelKey() { return "in_app"; }

    @Override
    public void send(NotificationPayload p) {
        Notification n = new Notification();
        n.setMemberId(p.memberId());
        n.setType(p.type());
        n.setChannel("in_app");
        n.setTitle(p.title());
        n.setContent(p.content());
        n.setIsRead(0);
        notificationMapper.insert(n);
    }
}
```

- [ ] **Step 5: 微信订阅消息通道（V1 空壳预留）**

`modules/notification/WxSubscribeChannel.java`:
```java
package com.gudu.xsd.modules.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 微信小程序订阅消息通道。
 * 【V1 留入口】当前不投递：未办微信 appid + 订阅消息模板审核。
 * 微信资质到位后，在此实现：
 *   1. 小程序端 wx.requestSubscribeMessage 拿用户一次性授权
 *   2. 本端调微信 OpenAPI subscribeMessage.send（需 appid/secret/access_token）
 * 业务侧无需改动 —— 仍调 NotificationService.send(payload, "wx_subscribe")。
 */
@Slf4j
@Component
public class WxSubscribeChannel implements NotificationChannel {

    @Override
    public String channelKey() { return "wx_subscribe"; }

    @Override
    public void send(NotificationPayload p) {
        log.debug("[wx_subscribe] 通道未启用（V1 预留），丢弃通知：type={} memberId={}", p.type(), p.memberId());
    }
}
```

- [ ] **Step 6: 分发服务（按 channelKey 路由）**

`modules/notification/NotificationService.java`:
```java
package com.gudu.xsd.modules.notification;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.gudu.xsd.modules.notification.mapper.NotificationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final Map<String, NotificationChannel> channels;
    private final NotificationMapper notificationMapper;

    /** Spring 自动注入所有 NotificationChannel Bean，按 channelKey 索引。 */
    public NotificationService(List<NotificationChannel> channelList, NotificationMapper notificationMapper) {
        this.channels = channelList.stream()
            .collect(Collectors.toMap(NotificationChannel::channelKey, Function.identity()));
        this.notificationMapper = notificationMapper;
    }

    /** 投递到指定通道（通道不存在则忽略）。 */
    public void send(NotificationPayload payload, String channelKey) {
        NotificationChannel ch = channels.get(channelKey);
        if (ch != null) ch.send(payload);
    }

    /** 投递到多个通道（如关键提醒同时站内 + 微信）。 */
    public void sendAll(NotificationPayload payload, Collection<String> channelKeys) {
        channelKeys.forEach(k -> send(payload, k));
    }

    /** 某成员的消息列表（最新优先）。 */
    public List<Notification> list(Long memberId) {
        return notificationMapper.selectList(
            new QueryWrapper<Notification>().eq("member_id", memberId).orderByDesc("create_time"));
    }

    /** 未读数。 */
    public long unreadCount(Long memberId) {
        return notificationMapper.selectCount(new QueryWrapper<Notification>()
            .eq("member_id", memberId).eq("is_read", 0));
    }

    /** 标记单条已读。 */
    public void markRead(Long id) {
        notificationMapper.update(null, new UpdateWrapper<Notification>().eq("id", id).set("is_read", 1));
    }
}
```

> ⚠️ 上面同时写了字段构造（`@RequiredArgsConstructor` 会和手写构造冲突）。**删掉 `@RequiredArgsConstructor` 注解和 `private final Map<String, NotificationChannel> channels;` 字段**，只保留手写的 `NotificationService(List, NotificationMapper)` 构造。`channels` 改为手写构造里赋值的普通 `private final` 字段（构造内赋值即可）。最终类只保留：`private final Map<String, NotificationChannel> channels;` + `private final NotificationMapper notificationMapper;` + 手写构造（去掉类上 `@RequiredArgsConstructor`）。

- [ ] **Step 7: NotificationController（当前就餐成员视角）**

`modules/notification/NotificationController.java`:
```java
package com.gudu.xsd.modules.notification;

import cn.dev33.satoken.stp.StpUtil;
import com.gudu.xsd.common.R;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/notification")
@RequiredArgsConstructor
@Tag(name = "通知中心")
public class NotificationController {

    private final NotificationService svc;

    @GetMapping
    public R<List<Notification>> list() {
        return R.ok(svc.list(currentMemberId()));
    }

    @GetMapping("/unread-count")
    public R<Map<String, Object>> unreadCount() {
        return R.ok(Map.of("count", svc.unreadCount(currentMemberId())));
    }

    @PutMapping("/{id}/read")
    public R<?> markRead(@PathVariable Long id) {
        svc.markRead(id);
        return R.ok(null);
    }

    private Long currentMemberId() {
        return StpUtil.getSession().getLong("currentMemberId");
    }
}
```

- [ ] **Step 8: 验证**

Knife4j 手测：先 `POST /member/current?memberId=1`（下个 Task），再写一段调试 —— 在任意接口或测试里调 `notificationService.send(new NotificationPayload(1L, "expiry", "番茄快过期了", "还剩 1 天"), "in_app")`，然后 `GET /notification` 应看到该消息；`PUT /notification/1/read` 后 `GET /notification/unread-count` 归零。`send(payload, "wx_subscribe")` 不报错、只打 debug 日志（空壳生效）。

- [ ] **Step 9: Commit**

```bash
git add menu-api/src/main/java/com/gudu/xsd/modules/notification/ menu-api/sql/V13__notification.sql
git commit -m "feat(notification): 通知通道 Strategy（站内实现，微信订阅消息留入口）"
```

---

### Task A4: 当前就餐成员上下文（Sa-Token session）

> 小程序登录的是 user（掌勺账号），但点评/记录的是 member（家庭成员）。把「当前就餐 memberId」存进 Sa-Token session，点评/通知接口自动读取，小程序免传。

**Files:**
- Modify: `menu-api/src/main/java/com/gudu/xsd/modules/member/MemberController.java`
- Modify: `menu-api/src/main/java/com/gudu/xsd/modules/member/MemberService.java`（若无 list，补上）

- [ ] **Step 1: 看现有 MemberController**

Run: `cat menu-api/src/main/java/com/gudu/xsd/modules/member/MemberController.java`
确认是否已有 `GET /member`（列表）。MVP Task 6 应已建 CRUD。若无 `list()`，在 `MemberService` 补：
```java
public List<Member> list() { return list(); } // ServiceImpl 已有，直接用 controller 调 svc.list()
```

- [ ] **Step 2: MemberController 加 current 切换/读取接口**

在 `MemberController` 内追加（保持既有 CRUD 不变）：
```java
import cn.dev33.satoken.stp.StpUtil;

/** 列出全部家庭成员（小程序选就餐成员用）。 */
@GetMapping
public R<List<Member>> list() {
    return R.ok(svc.list());
}

/** 切换当前就餐成员（存 Sa-Token session）。 */
@PostMapping("/current")
public R<?> setCurrent(@RequestParam Long memberId) {
    StpUtil.getSession().set("currentMemberId", memberId);
    return R.ok(null);
}

/** 读取当前就餐成员 id。 */
@GetMapping("/current")
public R<Long> getCurrent() {
    return R.ok(StpUtil.getSession().getLong("currentMemberId"));
}
```

> 若 `MemberController` 已有 `@GetMapping`（无 path）做列表，避免重复映射 —— 把新 `list()` 合并到既有的，只追加 `current` 两个接口。

- [ ] **Step 3: 验证**

Knife4j 登录拿 token → `POST /member/current?memberId=2` → `GET /member/current` 返回 `2` → `GET /member` 返回成员列表。

- [ ] **Step 4: Commit**

```bash
git add menu-api/src/main/java/com/gudu/xsd/modules/member/
git commit -m "feat(member): 当前就餐成员上下文（Sa-Token session，点评/通知自动取）"
```

---

## Phase B — 小程序工程（uniapp + uView Plus）

> 工程全新。用 HBuilderX 或 CLI 建都行；本计划用 Vue3 + Vite 的 uniapp CLI 脚手架（`vue3` 分支）。UI 用 uView Plus（`u-*` 组件）。调试先 H5（浏览器）+ 微信开发者工具（不依赖线上资质，本地调试不需要 appid 备案）。

### Task B1: 小程序工程骨架

**Files:**
- Create: `menu-mini/`（package.json、vite.config.ts、manifest.json、pages.json、main.ts、App.vue、utils/request.ts）

- [ ] **Step 1: 初始化工程**

```bash
npx degit dcloudio/uni-preset-vue#vite-ts menu-mini
cd menu-mini
npm install
npm install uview-plus    # uView Plus（Vue3/uniapp 适配）
npm install pinia
```

- [ ] **Step 2: pages.json（页面注册 + tabbar）**

`src/pages.json`:
```json
{
  "pages": [
    { "path": "pages/index/Index", "style": { "navigationBarTitleText": "小食单" } },
    { "path": "pages/dish/List", "style": { "navigationBarTitleText": "菜库" } },
    { "path": "pages/dish/Detail", "style": { "navigationBarTitleText": "菜品详情" } },
    { "path": "pages/dish/Review", "style": { "navigationBarTitleText": "点评" } },
    { "path": "pages/dish/Create", "style": { "navigationBarTitleText": "录入新菜" } },
    { "path": "pages/login/Login", "style": { "navigationBarTitleText": "登录" } }
  ],
  "globalStyle": {
    "navigationBarBackgroundColor": "#FF8C42",
    "navigationBarTextStyle": "white"
  },
  "tabBar": {
    "color": "#999", "selectedColor": "#FF8C42",
    "list": [
      { "pagePath": "pages/index/Index", "text": "首页" },
      { "pagePath": "pages/dish/List", "text": "菜库" }
    ]
  }
}
```

- [ ] **Step 3: request.ts（uni.request 封装，对齐后端 R）**

`src/utils/request.ts`:
```ts
const BASE = '/api' // H5 走 vite proxy；小程序/真机改成 http://<host>:8080

export function getToken(): string {
  return uni.getStorageSync('token') || ''
}

export async function request<T = any>(opt: UniApp.RequestOptions): Promise<T> {
  const res = await uni.request({
    ...opt,
    url: BASE + opt.url,
    header: { Authorization: getToken(), ...opt.header }
  } as any)
  const body = res.data // 后端统一 R{code,msg,data}
  if (body.code === 401) {
    uni.removeStorageSync('token')
    uni.reLaunch({ url: '/pages/login/Login' })
    throw new Error('未登录')
  }
  if (body.code !== 0) {
    uni.showToast({ title: body.msg || '请求失败', icon: 'none' })
    throw new Error(body.msg)
  }
  return body.data as T
}
```

- [ ] **Step 4: Vite proxy（H5 调试转发 /api 到后端）**

`vite.config.ts` 的 server 块加:
```ts
server: {
  proxy: { '/api': { target: 'http://localhost:8080', changeOrigin: true, rewrite: (p) => p.replace(/^\/api/, '') } }
}
```

- [ ] **Step 5: main.ts 引入 uView Plus + Pinia**

`src/main.ts`:
```ts
import { createSSRApp } from 'vue'
import { createPinia } from 'pinia'
import uviewPlus from 'uview-plus'
import App from './App.vue'

export function createApp() {
  const app = createSSRApp(App)
  app.use(createPinia())
  app.use(uviewPlus)
  return { app }
}
```

- [ ] **Step 6: 验证**

Run: `npm run dev:h5` → 浏览器打开，空白首页能加载（uView Plus 无报错）。

- [ ] **Step 7: Commit**

```bash
cd .. && git add menu-mini/ && git commit -m "feat(miniapp): 小程序工程骨架（uniapp+uView Plus+Pinia+请求封装）"
```

---

### Task B2: 登录页 + 登录态 store

**Files:**
- Create: `src/store/auth.ts`, `src/api/auth.ts`, `src/pages/login/Login.vue`

- [ ] **Step 1: auth store（Pinia）**

`src/store/auth.ts`:
```ts
import { defineStore } from 'pinia'
import { request } from '@/utils/request'

export const useAuthStore = defineStore('auth', {
  state: () => ({ token: uni.getStorageSync('token') || '', nickname: '' }),
  actions: {
    async login(username: string, password: string) {
      const r = await request<any>({ url: '/auth/login', method: 'POST', data: { username, password } })
      this.token = r.token
      this.nickname = r.nickname
      uni.setStorageSync('token', r.token)
    },
    logout() {
      this.token = ''
      uni.removeStorageSync('token')
      uni.reLaunch({ url: '/pages/login/Login' })
    }
  }
})
```

- [ ] **Step 2: Login.vue（账号密码表单）**

`src/pages/login/Login.vue`:
```vue
<template>
  <view class="login">
    <u-input v-model="form.username" placeholder="用户名" border="surround" />
    <u-input v-model="form.password" type="password" placeholder="密码" border="surround" />
    <u-button type="primary" @click="onLogin" :loading="loading">登录</u-button>
  </view>
</template>
<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useAuthStore } from '@/store/auth'
const auth = useAuthStore()
const form = reactive({ username: 'admin', password: '' })
const loading = ref(false)
async function onLogin() {
  loading.value = true
  try { await auth.login(form.username, form.password); uni.switchTab({ url: '/pages/index/Index' }) }
  finally { loading.value = false }
}
</script>
```

- [ ] **Step 3: 路由守卫（App.vue onLaunch 检查 token）**

`src/App.vue`:
```vue
<script setup lang="ts">
import { onLaunch } from '@dcloudio/uni-app'
onLaunch(() => {
  const token = uni.getStorageSync('token')
  if (!token) uni.reLaunch({ url: '/pages/login/Login' })
})
</script>
```

- [ ] **Step 4: 验证**

`npm run dev:h5` → 登录页 → admin/admin123 → 跳首页，token 存入 storage。

- [ ] **Step 5: Commit**

```bash
git add menu-mini/src/ && git commit -m "feat(miniapp): 登录页 + 登录态（账号密码复用 Sa-Token）"
```

---

### Task B3: 首页 + 切换当前就餐成员

**Files:**
- Create: `src/store/member.ts`, `src/api/member.ts`, `src/pages/index/Index.vue`

- [ ] **Step 1: member store + api**

`src/api/member.ts`:
```ts
import { request } from '@/utils/request'
export const listMembers = () => request<any[]>({ url: '/member', method: 'GET' })
export const setCurrent = (memberId: number) =>
  request({ url: '/member/current', method: 'POST', data: {}, header: {} } as any)
// uni.request 的 query 传参用 data(GET 也会拼到 query)——若不生效改 url 拼接
export const setCurrentMember = (memberId: number) =>
  request({ url: `/member/current?memberId=${memberId}`, method: 'POST' })
export const getCurrentMember = () =>
  request<number>({ url: '/member/current', method: 'GET' })
```

`src/store/member.ts`:
```ts
import { defineStore } from 'pinia'
import { listMembers, setCurrentMember, getCurrentMember } from '@/api/member'
export const useMemberStore = defineStore('member', {
  state: () => ({ currentId: 0 as number, members: [] as any[] }),
  actions: {
    async load() { this.members = await listMembers(); this.currentId = await getCurrentMember() || 0 },
    async switchTo(id: number) { await setCurrentMember(id); this.currentId = id }
  }
})
```

- [ ] **Step 2: Index.vue（切换成员 + 入口）**

`src/pages/index/Index.vue`:
```vue
<template>
  <view class="index">
    <view class="member-bar">
      <text>当前就餐：</text>
      <u-tag :text="currentName" type="warning" />
      <u-button size="mini" @click="showPicker = true">切换</u-button>
    </view>
    <u-button @click="goList">浏览菜库</u-button>
    <u-button @click="goCreate">录入新菜</u-button>
    <u-picker :show="showPicker" :columns="[members.map(m => m.name)]" @confirm="onPick" @cancel="showPicker=false" />
  </view>
</template>
<script setup lang="ts">
import { ref, computed, onShow } from '@dcloudio/uni-app' as any
import { useMemberStore } from '@/store/member'
const m = useMemberStore()
const showPicker = ref(false)
const currentName = computed(() => m.members.find(x => x.id === m.currentId)?.name || '未选择')
onShow(() => m.load())
function onPick(e: any) { const idx = e.indexs[0]; m.switchTo(m.members[idx].id); showPicker.value = false }
const goList = () => uni.switchTab({ url: '/pages/dish/List' })
const goCreate = () => uni.navigateTo({ url: '/pages/dish/Create' })
</script>
```

- [ ] **Step 3: 验证**

登录 → 首页加载成员 → 选一个 → `GET /member/current` 返回对应 id。

- [ ] **Step 4: Commit**

```bash
git add menu-mini/src/ && git commit -m "feat(miniapp): 首页 + 切换当前就餐成员"
```

---

### Task B4: 菜库列表 + 搜索筛选

> 复用 MVP Task 15 的 `GET /dish/search`（keyword/菜系/标签/分类/耗时/难度）。

**Files:**
- Create: `src/api/dish.ts`, `src/pages/dish/List.vue`

- [ ] **Step 1: dish api（search 分页）**

`src/api/dish.ts`:
```ts
import { request } from '@/utils/request'
export const searchDishes = (params: any) =>
  request<any>({ url: '/dish/search', method: 'GET', data: params })
```

- [ ] **Step 2: List.vue（搜索框 + 列表 + 下拉加载）**

`src/pages/dish/List.vue`:
```vue
<template>
  <view class="list">
    <u-search v-model="keyword" @search="reload" @clear="reload" />
    <u-cell v-for="d in dishes" :key="d.id" :title="d.name" :label="`${d.cookTime||0}分钟 · 难度${d.difficulty||'-'}`" isLink @click="goDetail(d.id)" />
    <u-loadmore :status="status" />
  </view>
</template>
<script setup lang="ts">
import { ref } from 'vue'
import { onReachBottom, onPullDownRefresh } from '@dcloudio/uni-app' as any
import { searchDishes } from '@/api/dish'
const dishes = ref<any[]>([])
const keyword = ref('')
const page = ref(1); const pageSize = 20
const status = ref<'loadmore'|'loading'|'nomore'>('loadmore')
async function reload() { page.value = 1; dishes.value = []; await load() }
async function load() {
  status.value = 'loading'
  const r = await searchDishes({ keyword: keyword.value, pageNum: page.value, pageSize })
  dishes.value.push(...(r.records || r || []))
  status.value = (r.records?.length || 0) < pageSize ? 'nomore' : 'loadmore'
}
onPullDownRefresh(() => reload().then(() => uni.stopPullDownRefresh()))
onReachBottom(() => { if (status.value === 'loadmore') { page.value++; load() } })
reload()
const goDetail = (id: number) => uni.navigateTo({ url: `/pages/dish/Detail?id=${id}` })
</script>
```

> 注：MVP `GET /dish/search` 的返回结构以实际为准（Page 对象有 `records`，或直接数组）。`load()` 里两种都兼容。

- [ ] **Step 3: 验证**

首页「浏览菜库」→ 列表出菜品 → 搜索框输入关键字过滤 → 点进详情。

- [ ] **Step 4: Commit**

```bash
git add menu-mini/src/ && git commit -m "feat(miniapp): 菜库列表 + 搜索筛选"
```

---

### Task B5: 菜品详情（步骤图文 + 营养 + 步骤计时器 + 做过标记）

**Files:**
- Create: `src/pages/dish/Detail.vue`（扩展 dish api 加 detail/nutrition/done）

- [ ] **Step 1: dish api 补 detail/nutrition/done**

`src/api/dish.ts` 追加:
```ts
export const dishDetail = (id: number) => request<any>({ url: `/dish/${id}`, method: 'GET' })
export const dishNutrition = (id: number, serving = 1) =>
  request<any>({ url: `/dish/${id}/nutrition?serving=${serving}`, method: 'GET' })
export const markDone = (dishId: number, note?: string) =>
  request({ url: `/cookbook/done/${dishId}?note=${encodeURIComponent(note || '')}`, method: 'POST' })
```

- [ ] **Step 2: Detail.vue（详情 + 步骤计时器）**

`src/pages/dish/Detail.vue`:
```vue
<template>
  <view class="detail" v-if="dish">
    <u-image :src="imgUrl(dish.coverUrl)" width="100%" height="200" />
    <view class="info">
      <text class="title">{{ dish.name }}</text>
      <text>备料 {{ dish.prepTime }}分 · 烹饪 {{ dish.cookTime }}分 · 难度 {{ dish.difficulty }}/5</text>
    </view>
    <view class="nutrition">
      <text>营养(份数{{serving}})：</text>
      <u-tag v-for="(v,k) in nutrition" :key="k" :text="`${k}:${v}`" type="success" />
    </view>
    <view class="steps" v-for="(s,i) in steps" :key="i">
      <view class="step-head">步骤{{i+1}} <u-button size="mini" @click="toggleTimer(i)">{{timer[i]?'停止':'计时'}}</u-button></view>
      <text>{{ s.text }}</text>
      <u-image v-for="(img,j) in imgs(s.images)" :key="j" :src="imgUrl(img)" width="80" height="80" />
      <view v-if="active===i && timer[i]">⏱ {{elapsed}}s</view>
    </view>
    <u-button type="warning" @click="onMarkDone">标记做过</u-button>
    <u-button @click="goReview">去点评</u-button>
  </view>
</template>
<script setup lang="ts">
import { ref, computed, onUnmounted } from 'vue'
import { onLoad } from '@dcloudio/uni-app' as any
import { dishDetail, dishNutrition, markDone } from '@/api/dish'
const dish = ref<any>(null); const nutrition = ref<any>({}); const serving = ref(1)
const steps = computed(() => dish.value?.steps || [])
const dishId = ref(0)
const active = ref(-1); const timer = ref<Record<number, boolean>>({}); const elapsed = ref(0)
let h: any = null
onLoad(async (q: any) => {
  dishId.value = q.id; dish.value = await dishDetail(q.id); nutrition.value = await dishNutrition(q.id, serving.value)
})
function toggleTimer(i: number) {
  if (timer.value[i]) { timer.value[i] = false; if (active.value === i) { clearInterval(h); active.value = -1 } }
  else { Object.keys(timer.value).forEach((k) => (timer.value[+k] = false)); timer.value[i] = true; active.value = i; elapsed.value = 0; h = setInterval(() => elapsed.value++, 1000) }
}
onUnmounted(() => h && clearInterval(h))
function imgs(s: any) { return s ? String(s).split(',') : [] }
function imgUrl(u: string) { return u ? ('/api' + u) : '' } // 详情图同 BASE
async function onMarkDone() { await markDone(dishId.value); uni.showToast({ title: '已记录' }) }
const goReview = () => uni.navigateTo({ url: `/pages/dish/Review?dishId=${dishId.value}` })
</script>
```

- [ ] **Step 3: 验证**

从列表进详情 → 图文 + 营养标签展示 → 点「计时」倒计时跑、再点停 → 标记做过 → `GET /cookbook/done` 含此菜。

- [ ] **Step 4: Commit**

```bash
git add menu-mini/src/ && git commit -m "feat(miniapp): 菜品详情（步骤图文+营养+步骤计时器+做过标记）"
```

---

### Task B6: 点评页（星级 + 文字 + 图片 + 多维打分）

**Files:**
- Create: `src/api/review.ts`, `src/pages/dish/Review.vue`

- [ ] **Step 1: review api**

`src/api/review.ts`:
```ts
import { request } from '@/utils/request'
import { request as rawUpload } from '@/utils/request' // 图片上传复用 /file/upload
import { getToken } from '@/utils/request'
export const submitReview = (data: any) => request({ url: '/review', method: 'POST', data })
export const listByDish = (dishId: number) => request<any[]>({ url: `/review/dish/${dishId}`, method: 'GET' })
export const reviewAvg = (dishId: number) => request<any>({ url: `/review/dish/${dishId}/avg`, method: 'GET' })
export const dimensions = () => request<any[]>({ url: '/dict?group=review_dimension', method: 'GET' })

// 多图上传：uni.uploadFile 逐张传 /file/upload，收集 url
export async function uploadImages(files: string[]): Promise<string[]> {
  const urls: string[] = []
  for (const f of files) {
    const r: any = await new Promise((res, rej) =>
      uni.uploadFile({
        url: '/api/file/upload', filePath: f, name: 'file',
        header: { Authorization: getToken() },
        success: (x) => res(JSON.parse(x.data).data), fail: rej
      }) as any)
    urls.push(r.url)
  }
  return urls
}
```

- [ ] **Step 2: Review.vue**

`src/pages/dish/Review.vue`:
```vue
<template>
  <view class="review">
    <text>总评星级</text>
    <u-rate v-model="form.starRating" count="5" />
    <text>点评文字</text>
    <u-textarea v-model="form.text" placeholder="说说味道、难度…" />
    <text>图片</text>
    <u-upload :fileList="imgs" @afterRead="onAdd" @delete="onDelete" :maxCount="6" />
    <text>维度打分</text>
    <view v-for="d in dims" :key="d.id">
      <text>{{ d.name }}</text>
      <u-rate v-model="scores[d.id]" count="5" />
    </view>
    <u-button type="primary" @click="onSubmit" :loading="loading">提交</u-button>
  </view>
</template>
<script setup lang="ts">
import { reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app' as any
import { submitReview, dimensions, uploadImages } from '@/api/review'
const dishId = ref(0); const dims = ref<any[]>([]); const imgs = ref<any[]>([])
const form = reactive({ starRating: 5, text: '' })
const scores = reactive<Record<number, number>>({})
const loading = ref(false)
onLoad(async (q: any) => { dishId.value = q.dishId; dims.value = await dimensions() })
function onAdd(e: any) { imgs.value.push(...e.file) }
function onDelete(e: any) { imgs.value.splice(e.index, 1) }
async function onSubmit() {
  loading.value = true
  try {
    const urls = await uploadImages(imgs.value.map((f: any) => f.url))
    await submitReview({ dishId: dishId.value, starRating: form.starRating, text: form.text, images: urls, dimensionScores: scores })
    uni.showToast({ title: '已点评' }); setTimeout(() => uni.navigateBack(), 800)
  } finally { loading.value = false }
}
</script>
```

- [ ] **Step 3: 验证**

详情 → 去点评 → 选星级/打字/选图/各维度打分 → 提交 → `GET /review/dish/{id}` 见新点评、`/avg` 均分变化。

- [ ] **Step 4: Commit**

```bash
git add menu-mini/src/ && git commit -m "feat(miniapp): 点评页（星级+文字+图片+多维打分）"
```

---

### Task B7: 录入新菜品（家庭成员贡献菜库）

> 复用 MVP `POST /dish`（saveWithSteps）。简化：只录基础信息 + 步骤，不挂食材/营养（V1 第二批再强化）。

**Files:**
- Create: `src/pages/dish/Create.vue`（dish api 补 save）

- [ ] **Step 1: dish api 补 save**

`src/api/dish.ts` 追加:
```ts
export const saveDish = (data: any) => request({ url: '/dish', method: 'POST', data })
```

- [ ] **Step 2: Create.vue（基础信息 + 动态步骤）**

`src/pages/dish/Create.vue`:
```vue
<template>
  <view class="create">
    <u-input v-model="form.name" placeholder="菜名" border="surround" />
    <u-input v-model="form.note" placeholder="备注" border="surround" />
    <u-input v-model.number="form.prepTime" type="number" placeholder="备料分钟" border="surround" />
    <u-input v-model.number="form.cookTime" type="number" placeholder="烹饪分钟" border="surround" />
    <u-input v-model.number="form.difficulty" type="number" placeholder="难度1-5" border="surround" />
    <view v-for="(s,i) in steps" :key="i">
      <u-textarea v-model="s.text" :placeholder="`步骤${i+1}`" />
      <u-button size="mini" @click="steps.splice(i,1)">删</u-button>
    </view>
    <u-button @click="steps.push({text:''})">加步骤</u-button>
    <u-button type="primary" @click="onSave" :loading="loading">保存</u-button>
  </view>
</template>
<script setup lang="ts">
import { reactive, ref } from 'vue'
import { saveDish } from '@/api/dish'
const form = reactive({ name: '', note: '', prepTime: 0, cookTime: 0, difficulty: 3, price: 0 })
const steps = reactive<any[]>([{ text: '' }])
const loading = ref(false)
async function onSave() {
  loading.value = true
  try {
    await saveDish({ dish: form, steps: steps.map((s, i) => ({ seq: i + 1, text: s.text, sortOrder: i + 1 })) })
    uni.showToast({ title: '已保存' }); setTimeout(() => uni.navigateBack(), 800)
  } finally { loading.value = false }
}
</script>
```

> 注：`POST /dish` 的入参结构以 `DishSaveDTO` 实际字段为准（MVP Task 9/10）。提交前 `cat` 一下 `DishSaveDTO.java` 对齐字段名。

- [ ] **Step 3: 验证**

首页「录入新菜」→ 填写 → 保存 → 菜库列表出现新菜。

- [ ] **Step 4: Commit**

```bash
git add menu-mini/src/ && git commit -m "feat(miniapp): 录入新菜品（家庭成员贡献菜库）"
```

---

## Phase C — 集成联调

### Task C1: 全流程端到端联调

**Files:** 无新增，验证为主

- [ ] **Step 1: 起 backend + miniapp**

```bash
# 终端1：后端
cd menu-api && ./mvnw spring-boot:run
# 终端2：小程序 H5
cd menu-mini && npm run dev:h5
```

- [ ] **Step 2: 走通主线**

1. 登录 admin/admin123
2. 首页切换当前就餐成员
3. 浏览菜库 → 搜索
4. 进详情 → 步骤计时 → 标记做过
5. 去点评 → 星级+文字+图片+多维打分 → 提交 → 回详情看均分
6. 录入新菜 → 菜库可见
7. （站内通知）后端触发一条 `send(payload,"in_app")` → 小程序可查（通知列表页第二批做，先 Knife4j 验 `/notification`）

- [ ] **Step 3: Commit 收尾**

```bash
git add -A && git commit -m "feat: V1 第一批联调通过（小程序主线跑通）"
```

---

## Self-Review 结果

**1. Spec 覆盖**（对照 spec V1 line 76–93 与模块清单 138–156）：
- 点评（星级+文字+图片+多维度打分）→ Task A1/A2 + B6 ✓
- 通知通道入口（站内实现、微信留 Strategy）→ Task A3 ✓（业务触发临期/采购第二批）
- 切换当前就餐成员 → Task A4 + B3 ✓
- 小程序点菜/烹饪记录/步骤计时器/录入新菜品 → Task B4/B5/B7 ✓
- **本批明确不做（留第二批）**：周计划拖拽排菜、采购清单、库存、按食材反向找菜、菜单时间排程、自定义菜单营养上限精确筛选、网页菜谱导入、PDF/CSV 导出、每日饮食记录、权限矩阵细化、通知业务触发 —— 均在 spec V1 范围内但属第二批。
- **缺口说明**：权限边界（line 91 角色模板 + 功能勾选）本批**简化为「登录即全权」**，未做细粒度权限矩阵 —— 小程序家庭自用场景下风险可控，第二批补。已在「不做」清单标注。

**2. 占位符扫描**：无 TBD/TODO/「稍后实现」。`WxSubscribeChannel` 是**有意留的空壳**（标注了 V1 边界 + 未来实现路径），非占位。小程序 Task 里个别注释提示「字段以实际 DTO 为准」，是因为 MVP 既有 DTO 字段名需对齐 —— 已指示执行者先 `cat` 源文件，属可操作步骤。

**3. 类型一致性**：
- `ReviewService` 测试构造与实现构造一致（双参 `ReviewMapper, ReviewScoreMapper`，去掉 `__unused` 与 `@RequiredArgsConstructor`，Step 5 已显式修正）。
- `NotificationService` 构造与字段一致（手写构造，去掉 `@RequiredArgsConstructor`，Step 6 已显式修正）。
- `NotificationChannel.channelKey()` 的值 `"in_app"` / `"wx_subscribe"` 在 `InAppChannel`/`WxSubscribeChannel`/调用方三处一致。
- `ReviewSaveDTO.dimensionScores` 类型 `Map<Long,Integer>` 与 `ReviewService.submit` 遍历一致。
- 小程序 `request<T>` 返回 `T`，各 api 调用返回类型对齐后端 `R.data`。

---

## 执行交接

Plan complete and saved to `docs/superpowers/plans/2026-06-18-gudu-v1-batch1.md`。两种执行方式：

**1. Subagent-Driven（推荐）** — 每个 Task 派全新 subagent，Task 间审查，上下文干净、迭代快。后端 A1–A4 与小程序 B1–B7 可分两路 subagent 并行（A 路出接口 B 路接）。

**2. Inline Execution** — 当前会话用 executing-plans 批量执行，带检查点审查。

**注意**：小程序工程首次 `npm install` 较慢；H5 调试不依赖微信资质，可立即开干。微信开发者工具真机调试可后置（与本批代码无关，不影响交付）。

**你要哪种？**
