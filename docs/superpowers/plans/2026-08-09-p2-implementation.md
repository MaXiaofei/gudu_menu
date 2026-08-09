# P2 实施计划：库存/采购/做菜/备菜/评价（APP）

> **日期**：2026-08-09 ｜ **设计来源**：`docs/pantry-shopping-redesign.md`（v0.6）、`docs/menu-review-design.md`、原型批次 44829（V42 全套 + menu-review + shopping-restock 撤回）
> **原则**：TDD，后端单测基线 278 不破；Flutter `flutter analyze` 0 error；小程序本轮不动

## 0. 已落盘（勿重复）

- 后端 P1：V42（ingredient_stock + stock_log + 存量映射）、PantryService 重写、CookService（cook-materials + cook 确认）、ShoppingService（from-prep + purchased 带 level）、删单菜直做/扣减链/盘点 —— 278 绿
- 设计文档 v0.6（含一起吃 v0.1，**本轮不做**）；DESIGN.md §15（全选/批量约束）
- 原型全套 V42 + menu-review.html（统一评价）+ dailylog 统一评价入口 + shopping-restock 撤回入库
- 评价方案 docs/menu-review-design.md（已确认）
- 管理后台库存页迁移（pantry 批次表 → ingredient_stock 档位）：后端删旧 admin CRUD 接口、admin 前端改档位管理 —— 已完成（2026-08-09）

## 1. 已确认决策（2026-08-09）

1. **档位文案**：充足 / 不足 / 用完（ENOUGH/LOW/NONE）；已回写 v0.6 文档
2. **撤回入库**：已入库项行尾 ✕ = 撤回入库（恢复入库前档位，依赖 stock_log.before_level）；未入库项 ✕ = 移除；已回写 shopping-restock 原型
3. **食记页（dailylog）评价**：统一评价入口（与做菜结果页同款），已回写 dailylog 原型
4. **一起吃**：稍后再说（v0.6 §8 已定稿，另行排期）

## 2. 后端补丁（阶段 1，TDD）

| # | 任务 | 说明 |
|---|---|---|
| B1 | V43a：stock_log 加 `before_level/after_level`；setLevel/useUp/partialUse 记录前后档位 | 撤回底座 |
| B2 | 批量入库接口：勾选保存一次性入库（`POST /shopping/restock`，body itemIds → 批量 setLevel(ENOUGH) + 记流水） | 现在只有单项 togglePurchased |
| B3 | 撤回入库接口：删采购项 + 恢复 before_level + 记撤回流水（`POST /shopping/item/{id}/undo-restock`） | 依赖 B1 |
| B4 | shopping_list 加 `name` 列 + 改名接口（自定义采购 ✎，`PUT /shopping/{id}/name`） | |
| B5 | 备菜 VO / 菜谱详情接口填 stockLevel（徽标数据源） | |
| B6 | 做菜用材总结落库：cook 时持久化 usedUp/partiallyUsed（cooking_record 新列或 memo 结构化）→ dailylog 显示「用完 N 样：…/用了一些 M 样」 | 任务单第 7 点 |
| B7 | 评价后端：review.menu_id + dish_id 可空 + DTO.menuId + `GET /review/menu-overview/{id}` | menu-review-design.md |

## 3. Flutter（阶段 2-6）

### 阶段 2：库存页（F1-F3）
- `pantry_service.dart` 模型改 3 档（level/lastChange，去克数/批次字段）
- `list_page`：3 档分组（用完/不足/充足）+ 来源标签（cook/cook_partial/purchase/manual）
- `detail_page`：3 档单选（可输入）+「用完了」快捷按钮 + 简版流水（stock_log）
- `manual_add_page` → 单屏入库弹窗：选食材（可新建档，**不用单位**）→ 档位（默认充足）+ 来源标签

### 阶段 3：做菜闭环（F4-F7）
- `menu/detail_page`：「开始做饭」→ 确认弹窗（cook-materials：三态，食材默认用完/调料默认用了一些）→「确认已做完」→ 完成结果页（库存变化 + **统一评价入口「去评价 ›」**）
- 4 Tab → 3 Tab（删采购 Tab）
- 菜谱详情（dish detail）用料行：家里：充足/不足/用完 徽标（B5 数据源）

### 阶段 4：备菜（F8-F9）
- 备料清单库存徽标（B5 数据源）
- 「一键加采购」弹窗：备菜项（食材+用量+档位），默认勾选用完/不足，调 `POST /shopping/from-prep`；完成态隐藏

### 阶段 5：采购页重构（F10-F14，依赖 B2/B3/B4）——最大块
- 列表页（食集/自定义清单）+「+ 新建清单」（自定义采购）
- 清单详情：**全选**（§15）+ 勾选=本地选择态 + 底部「保存入库 · N 项」批量（默认记充足，不弹档位选择，B2）→ 已入库项行尾 ✕=撤回入库（B3）/ 未入库项 ✕=移除
- 分享预览页：右上角 → 全屏所见即所得 → 复制文字 / 转图片
- 自定义采购：「添加」弹窗（名称+数量单位一个框如"2斤"、逐条添加、行尾删除）→ 标题旁 ✎ 改名（B4）→ 匹配食材库的可入库，匹配不到只标已买
- 路由 / 文案统一（整集做菜→开始做饭；档位词=充足/不足/用完）

### 阶段 6：评价功能
- 抽 `ReviewForm` 公共组件（星级+四维度+文字+图片+提交）
- 食集评价表单页 + 统一评价页 `/menu/:id/review`（食集卡片 + 菜品已评/未评列表）
- 入口改造：做菜结果页「去评价 ›」、食集完成态「去评价」直进、食记页（dailylog）同款入口

## 4. 跑通验证（每阶段）

- 后端补丁：`./mvnw -pl menu-api test -Dtest='!GuduE2EFlowTest'` 全绿
- 库存：入库 → 列表刷新 → 详情改档 → 刷新
- 做菜：开始做饭 → 弹窗三态 → 确认已做完 → 结果页 → 去评价 → 统一页
- 备菜：徽标显示 + 一键加采购 → 采购页可见
- 采购：全选 → 保存入库 · N 项 → 已入库 ✕ 撤回恢复档位 → 分享预览
- 评价：食集整体评价 + 每道菜评价 → 状态刷新
