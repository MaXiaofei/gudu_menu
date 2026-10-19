# Proposal: 补齐功能缺口

**Change ID:** `feature-gaps-closure`
**Created:** 2026-10-16
**Status:** Draft
**Roadmap:** #4（依赖 `multi-tenant-family`：所有新接口原生带家庭隔离、按四层实现）

---

## Problem Statement

2026-10-16 审计核对了 `docs/redesign-audit.md` 与历史交接点列出的缺口。三条 P0 断链（做菜回写库存、采购三色、备菜）**已修复**，剩余缺口：

### 后端
| # | 缺口 | 证据 |
|---|------|------|
| G1 | **单菜直做**不存在：单做一道菜不写 cooking_record、不回写库存、不进食记 | `CookbookController.java:36` 注释引用的 `cookDishNow` 全库不存在；`CookService.cookByMenu` 只覆盖食集 |
| G2 | **临期主推**做不了：三档库存 `ingredient_stock` 只有 `level`，无保质期；`/pantry/expiring` 读的是旧批次表 `pantry` | `IngredientStock.java:25-32`、`PantryController.java:85` |
| G3 | **菜谱余量覆盖率**（「家里够 X/Y 样」）缺失：仅详情页逐项 `stockLevel` | `DishService.java:204-208` |
| G4 | **最近做过**无专门接口 | 仅 foodlog month/by-dish/year |
| G5 | AI 智荐不参考临期与家里余量 | `MenuRecommender` |
| G6 | 两套库存模型并存（旧 `pantry` 批次表 + 新 `ingredient_stock` 三档）语义冲突 | `PantryService` 同时操作两表 |

### 前端占位 / 半成品
| # | 端 | 缺口 | 证据 |
|---|----|------|------|
| G7 | mini | 用户头卡、「主题外观」、「关于小食单」只 toast「建设中」 | `profile/Profile.vue:7,61-66` |
| G8 | mini | 「转图片分享」实为复制文字 | `shopping/List.vue:150,478-483` |
| G9 | mini | tabBar 无图标；文案「推荐/库存」与定稿命名「智荐/我家余量」不符 | `pages.json` |
| G10 | Flutter | 「主题外观」「关于」点击为空函数；唯一可切主题的 `MorePage` 无入口（死代码），`ComingSoonPage` 亦为死代码 | `profile_page.dart:138,145`、`more_page.dart:45` |
| G11 | Flutter | 「常用食材」取食材库前 8 个而非按使用频率 | `dish/create_page.dart:200` |
| G12 | admin | 首页仅欢迎语 | `views/Home.vue` |
| G13 | admin | 食材页面向用户展示「mock 待接 GLM，请核对」 | `views/ingredient/Index.vue:173,329` |
| G14 | 三端 | 缺《用户协议》《隐私政策》页面与入口、意见反馈入口 | 登录页/我的页 |

## Proposed Solution

### 后端（新功能一律四层 + 领域事件）
1. **单菜直做 G1**：`POST /cook/dish/{dishId}`（份数、实际用料可调）。`CookSession` 领域行为 → 发布 `DishCookedEvent`；消费方：cookbook 写 `cooking_record`、pantry 依据用户确认的「用完/用了一部分」回写三档、foodlog 可见。与 `cookByMenu` 共用同一事件（同时把 cookByMenu 里直连 pantry 的代码改为事件消费，削减跨模块 Mapper 基线）。
2. **库存模型统一 + 保质期 G2/G6**：`ingredient_stock` 增加 `best_before DATE NULL`、`opened_at`；三档库存成为**唯一库存模型**，旧 `pantry` 批次表数据迁移（取最近到期日）后只读、下一版本删除。`IngredientStock` 聚合行为：`restock(level, bestBefore)`、`markUsed(partially|up)`、`isExpiringWithin(days)`。新接口 `GET /pantry/expiring?days=3`。录入时按食材品类给出**默认保质天数建议**（字典 `shelf_life_days`）。
3. **覆盖率 G3**：领域服务 `StockCoverageCalculator`（菜谱用料 × 本家库存 → `{enough, total, missing[]}`）；`/dish/search` 返回 `coverage` 字段（批量计算，单 SQL 拉库存，避免 N+1）；新增排序 `sort=coverage`（「家里够的优先」）。
4. **最近做过 G4**：`GET /cookbook/recent?limit=10`（按菜去重，含最近日期与次数）。
5. **智荐增强 G5**：`GET /recommend/home` 聚合：临期主推（临期食材 → 可见菜谱按覆盖率排序 Top3）+ 你常做 + 季节鲜；AI 推荐 prompt 注入临期与覆盖率上下文。

### 前端
6. **mini**：个人资料编辑页（头像/昵称）、主题外观页（奶油轻食/抹茶禅意，持久化）、关于页（版本号、协议、隐私、反馈）、采购清单 canvas 生成分享图（保存相册 + 分享给好友）、tabBar 图标与定稿命名、首页智荐接入 `/recommend/home` 与 `/cookbook/recent`、菜谱列表展示「家里够 X/Y 样」、单菜「现在就做」入口、库存录入保质期。
7. **Flutter**：主题外观页接入 `ThemeController`、关于页、删除 `MorePage`/`ComingSoonPage` 死代码、常用食材改用 `GET /ingredient/frequent`（按本家 dish_ingredient + cooking_record 频次）、单菜直做、覆盖率、临期、首页智荐同 mini。
8. **admin**：运营首页仪表盘（用户数/家庭数/DAU/新增菜谱/AI 调用与费用趋势/公共库规模）；食材 AI 营养补全改为真实调用并移除 mock 文案（补全结果标注「AI 生成，待审核」+ 审核状态）。
9. **合规页面 G14**：用户协议、隐私政策（含收集信息清单、第三方 SDK 清单、注销方式）作为静态页，三端统一入口；意见反馈 `POST /feedback`。

## Scope

### In Scope
后端 G1–G6，前端 G7–G14，以及对应四件套测试。

### Out of Scope
- UGC 社区、营养报告周报（另立 change）
- 推送渠道扩展（短信/APP Push）

## Impact Analysis

| Component | Change | Details |
|-----------|--------|---------|
| Database | Yes | ingredient_stock 加 best_before/opened_at；dict 增 shelf_life_days；feedback 表；pantry 旧表迁移后冻结 |
| API | Yes | 新增 `/cook/dish/{id}`、`/pantry/expiring`(语义变更)、`/cookbook/recent`、`/recommend/home`、`/ingredient/frequent`、`/feedback`；`/dish/search` 增 `coverage` 字段与排序 |
| 三端 | Yes | 见上 |
| 架构 | 正向 | cook→pantry/cookbook 改事件驱动，冻结基线削减 |

## Risks / Trade-offs
- 旧 `pantry` 表迁移：存量批次合并为单条三档（取最早到期日），可能丢失多批次细节；产品已确认三档模型为主线，可接受。
- 覆盖率计算在搜索高频路径：一次查询本家全部库存（通常 < 200 条）内存计算，P95 目标 < 50ms 增量。
