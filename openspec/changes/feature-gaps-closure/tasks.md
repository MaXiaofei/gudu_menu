# Implementation Tasks: 补齐功能缺口

**Change ID:** `feature-gaps-closure`

---

## 1. 库存模型统一 + 保质期（G2/G6）
- [ ] 1.1 迁移：ingredient_stock 加 best_before/opened_at；sys_dict 食材品类增 shelf_life_days；旧 pantry 批次 → ingredient_stock（取最早到期日）
- [ ] 1.2 `IngredientStock` 聚合（四层）：restock / markUsed / isExpiringWithin；`StockLog` 只追加
- [ ] 1.3 `GET /pantry/expiring?days=` 改读三档模型；录入接口支持 bestBefore + 默认保质建议
- [ ] 1.4 旧 pantry 写接口下线（返回 410 + 前端已切换后删除）
- [ ] 1.5 领域单测 + E2E

## 2. 单菜直做（G1）
- [ ] 2.1 `DishCookedEvent`（familyId, dishId, servings, profileIds, stockUsage[]）
- [ ] 2.2 `POST /cook/dish/{dishId}` 应用服务 + 发布事件
- [ ] 2.3 消费方：cookbook 写 cooking_record；pantry 回写三档；cookByMenu 改为同一事件（删除直连 pantry 代码，同步删 archunit 基线行）
- [ ] 2.4 单测 + E2E（单菜做完 → 食记可见 → 库存档位变化）

## 3. 覆盖率 / 最近做过 / 常用食材（G3/G4/G11）
- [ ] 3.1 `StockCoverageCalculator` 领域服务 + 单测
- [ ] 3.2 `/dish/search` 返回 coverage，支持 sort=coverage（无 N+1，压测 P95 增量 < 50ms）
- [ ] 3.3 `GET /cookbook/recent`
- [ ] 3.4 `GET /ingredient/frequent?limit=8`
- [ ] 3.5 E2E

## 4. 首页智荐（G5）
- [ ] 4.1 `GET /recommend/home`（临期主推 Top3 + 你常做 + 季节鲜）
- [ ] 4.2 AI 推荐 prompt 注入临期/覆盖率上下文；AiOutputGuard 回归
- [ ] 4.3 E2E

## 5. 合规与反馈（G14）
- [ ] 5.1 用户协议 / 隐私政策文案（含 SDK 清单、收集信息、注销方式）——**需用户提供主体信息后定稿**
- [ ] 5.2 `POST /feedback` + admin 反馈列表

## 6. menu-mini（G7/G8/G9）
- [ ] 6.1 个人资料编辑、主题外观（持久化）、关于页、协议/隐私/反馈入口
- [ ] 6.2 采购清单 canvas 分享图（保存相册 + 好友分享）
- [ ] 6.3 tabBar 图标与命名「菜谱 / 食集 / 智荐 / 我家余量 / 我的」
- [ ] 6.4 首页智荐、最近做过、菜谱覆盖率、单菜「现在就做」、库存保质期录入
- [ ] 6.5 unit + Playwright

## 7. menu-flutter（G10/G11）
- [ ] 7.1 主题外观页、关于页；删除 MorePage/ComingSoonPage
- [ ] 7.2 常用食材接口、单菜直做、覆盖率、临期、首页智荐
- [ ] 7.3 widget + integration_test

## 8. menu-admin（G12/G13）
- [ ] 8.1 运营仪表盘（统计接口 `GET /ops/stats`，OPERATOR）
- [ ] 8.2 食材营养 AI 补全真实接入 + 审核状态；移除 mock 文案
- [ ] 8.3 反馈管理
- [ ] 8.4 Playwright

## Quality Gate
- [ ] 四件套全绿；ArchUnit 基线较本 change 开始时减少（cook→pantry 事件化）
- [ ] 三端无「建设中 / mock / 敬请期待」字样（grep 守护进 CI）
