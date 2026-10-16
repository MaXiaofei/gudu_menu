# Implementation Tasks: 补齐功能缺口

**Change ID:** `feature-gaps-closure`

---

## 1. 库存模型统一 + 保质期（G2/G6）
- [x] 1.1 迁移：ingredient_stock 加 best_before/opened_at；sys_dict 食材品类增 shelf_life_days；旧 pantry 批次 → ingredient_stock（取最早到期日）——`V64__stock_best_before.sql`（每食材最早的未过期日，只回填已建档且非 NONE 的行）
- [x] 1.2 `IngredientStock` 聚合（四层）：restock / markUsed / isExpiringWithin；`StockLog` 只追加——聚合名 `StockItem`（pantry/domain），仓储 `appendLog` 只追加
- [x] 1.3 `GET /pantry/expiring?days=` 改读三档模型；录入接口支持 bestBefore + 默认保质建议（`GET /pantry/shelf-life`）；临期通知调度同步改读三档
- [x] 1.4 旧 pantry 写接口下线（返回 410 + 前端已切换后删除）——核查：批次表已无任何写接口（POST /pantry 于 V42 删除），V64 起读路径也全部切走；`pantry` 表下一版本删
- [x] 1.5 领域单测 + E2E

## 2. 单菜直做（G1）
- [x] 2.1 `DishCookedEvent`（familyId, dishId, servings, profileIds, stockUsage[]）——多租户未落地，暂携带 memberId/source/menuId/dishes[(dishId,servings)]/usedUp/partiallyUsed/note
- [x] 2.2 `POST /cook/dish/{dishId}` 应用服务 + 发布事件（另含 `GET /cook/dish/{dishId}/materials`）
- [x] 2.3 消费方：cookbook 写 cooking_record；pantry 回写三档；cookByMenu 改为同一事件（删除直连 pantry 代码，同步删 archunit 基线行）——CookService 跨模块 Mapper 清零，基线 174→155
- [x] 2.4 单测 + E2E（单菜做完 → 食记可见 → 库存档位变化）

## 3. 覆盖率 / 最近做过 / 常用食材（G3/G4/G11）
- [x] 3.1 `StockCoverageCalculator` 领域服务 + 单测
- [ ] 3.2 `/dish/search` 返回 coverage，支持 sort=coverage（无 N+1，压测 P95 增量 < 50ms）——功能 + 批量无 N+1 已完成（后端 ✅）；P95 压测未做
- [x] 3.3 `GET /cookbook/recent`
- [x] 3.4 `GET /ingredient/frequent?limit=8`
- [x] 3.5 E2E

## 4. 首页智荐（G5）
- [x] 4.1 `GET /recommend/home`（临期主推 Top3 + 你常做 + 季节鲜）
- [ ] 4.2 AI 推荐 prompt 注入临期/覆盖率上下文；AiOutputGuard 回归——未做（不在本轮契约 §1–8 范围）
- [x] 4.3 E2E

## 5. 合规与反馈（G14）
- [ ] 5.1 用户协议 / 隐私政策文案（含 SDK 清单、收集信息、注销方式）——**需用户提供主体信息后定稿**
- [ ] 5.2 `POST /feedback` + admin 反馈列表——后端 ✅（POST/GET/PUT status，`V67__feedback.sql`）；admin 页面待前端

## 6. menu-mini（G7/G8/G9）
- [x] 6.1 个人资料编辑、主题外观（持久化）、关于页、协议/隐私/反馈入口
- [x] 6.2 采购清单 canvas 分享图（保存相册 + 好友分享）
- [x] 6.3 tabBar 图标与命名「菜谱 / 食集 / 智荐 / 我家余量 / 我的」
- [x] 6.4 首页智荐、最近做过、菜谱覆盖率、单菜「现在就做」、库存保质期录入
- [x] 6.5 unit + Playwright

## 7. menu-flutter（G10/G11）
- [x] 7.1 主题外观页、关于页；删除 MorePage/ComingSoonPage（另含：个人资料编辑、意见反馈、用户协议/隐私政策页、登录页协议勾选、状态栏跟随主题、Tab 文案改 智荐/我家余量）
- [x] 7.2 常用食材接口、单菜直做、覆盖率、临期、首页智荐
- [x] 7.3 widget + integration_test（widget/service 测试已补齐；integration_test 已适配新登录与 Tab 命名、可编译，未在设备上跑）

## 8. menu-admin（G12/G13）
- [x] 8.1 运营仪表盘（统计接口 `GET /ops/stats`，OPERATOR）— admin 端完成（KPI + 14 天趋势，echarts 按需引入，主题 token 配色）
- [ ] 8.2 食材营养 AI 补全真实接入 + 审核状态；移除 mock 文案 — admin 端已移除 mock 文案、标注「AI 估算，请核对后保存」且保存前可编辑；**审核状态待后端字段**
- [x] 8.3 反馈管理（列表/状态筛选/缩略图预览/标记 DONE·NEW/分页）
- [x] 8.4 Playwright（dashboard / feedback 新增，dish.spec 侧栏「菜谱」修复；14/14 本地通过）

## Quality Gate
- [ ] 四件套全绿；ArchUnit 基线较本 change 开始时减少（cook→pantry 事件化）——ArchUnit 部分 ✅：174→155；四件套待前端
- [ ] 三端无「建设中 / mock / 敬请期待」字样（grep 守护进 CI）
