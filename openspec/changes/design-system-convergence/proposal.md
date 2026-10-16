# Proposal: 三端视觉与设计系统收敛

**Change ID:** `design-system-convergence`
**Created:** 2026-10-16
**Status:** Draft
**Roadmap:** #6（在 `feature-gaps-closure` 页面稳定后批量收敛；新页面从现在起即遵循）

---

## Problem Statement

设计规范已定稿（`docs/design/DESIGN.md`、`tokens.json`，双主题奶油轻食 / 抹茶禅意），但三端执行度差异大：

| 端 | 评分 | 主要问题（审计证据） |
|----|------|---------------------|
| Flutter | 7/10 | textStyles 403 处✅；但 `BoxDecoration(` 直写 154 处 vs `AppCard` 4 处；`GradientButton` 0 处使用；`Colors.white` 72 处（抹茶主题下不跟随）；`SectionLabel/InfoCallout/Counter/SearchInput` 未建；27 处 `catch (_) {}` 吞错→空态冒充；`ErrorView` 仅 2 处、裸 `CircularProgressIndicator` 17 处；`Semantics` 0 处；状态栏色写死；首字母占位图 3 处重复 |
| menu-mini | 4/10 | **404 处硬编码 font-size、0 处 var()**，9–64px 十余档、9px 17 次；475 处硬编码间距、`--sp-*` 0 使用；87 处裸 hex；12 页自写顶栏；`ui-status-chip` 0 引用；tabBar 无图标；recommend/Preview/ingredient/profile 缺空/加载态 |
| menu-admin | 5/10 | 51 处裸 hex（暖色在抹茶主题不变色）；`themes.ts` 仅主色/侧栏/背景，无文字/边框/语义色 token；ai-log 用 Element 默认蓝红绿；首页空壳；27 处 font-size、49 处内联 style |

## Proposed Solution

### 1. 单一 token 源，三端生成
`docs/design/tokens.json` 为唯一权威 → 脚本 `scripts/design/gen-tokens.mjs` 生成：
- Flutter `lib/core/generated_tokens.dart`（AppTokens 读取）
- mini `src/styles/tokens.scss` + CSS 变量（`--c-*`、`--fs-*`、`--sp-*`、`--r-*`，两套主题 class）
- admin `src/styles/tokens.css`（同名变量）+ Element Plus 主题变量映射
CI 校验生成物与 tokens.json 一致。

### 2. 字号 / 间距 / 圆角阶梯收敛
- 字号 7 档：caption 12 · chip 10(仅徽章) · sectionLabel 11 · body 14 · cardTitle 14/700 · pageTitle 16/700 · display 22/800（mini 以 rpx 等比）；**禁用 < 10px**。
- 间距 4 基：4/8/12/16/24/32；圆角 sm8/md12/lg16/xl22/pill。

### 3. 共享组件补齐并替换
| 组件 | Flutter | mini | admin |
|------|---------|------|-------|
| 卡片 | AppCard（替换 154 处 BoxDecoration 中卡片类） | ui-card | PageCard |
| 分区标题 | SectionLabel（新建） | ui-section-label | — |
| 说明提示条 | InfoCallout（新建） | ui-callout | — |
| 加减盘 | Counter（新建） | ui-counter | — |
| 搜索框 | SearchInput（SearchBox 改名统一） | ui-search | — |
| 状态徽章 | StatusChip | ui-status-chip（推广） | StatusTag |
| 顶栏 | AppBar 主题 | ui-back-header / ui-action-bar（12 页替换） | — |
| 首字母占位图 | InitialThumb（合并 3 处） | ui-thumb | — |
| 三态 | LoadingView / EmptyView / ErrorView(带重试) | ui-state(loading/empty/error) | v-loading + el-empty + ErrorState |

### 4. 状态与反馈标准
每个数据页必须有 **加载（骨架屏）/ 空态（插画 + 引导动作）/ 错误（文案 + 重试）** 三态；禁止 `catch (_) {}` 静默吞错（lint 规则）；操作反馈统一 toast/snackbar 文案规范（成功简短、失败说原因 + 下一步）。

### 5. 主题与无障碍
- 双主题全量生效：所有颜色经 token；Flutter `onPrimary`/`card` 替代 `Colors.white`；状态栏随主题；admin 补全语义 token 后 ai-log 图表色改用品牌数据色板。
- 暗色模式：**V1 不做系统暗色**（品牌双主题已覆盖场景），记录决策。
- 无障碍：点击区域 ≥ 44pt/48dp；图标按钮补 `Semantics`/`aria-label`；正文对比度 ≥ 4.5:1（两套主题校验）；支持系统大字体（Flutter textScaler 至 1.3 不溢出）。

### 6. 动效与质感
统一时长（120/200/300ms）与曲线；列表进入淡入上移、底部弹层、按钮按压反馈；骨架屏 shimmer；避免布局抖动动画（只动 transform/opacity）。

### 7. 守护
- mini/admin：stylelint 规则禁止 `.vue` 中裸 hex、px/rpx 字号（必须 var）；
- Flutter：custom_lint 或 grep 守护禁止 `fontSize:` / `Color(0x` / `Colors.`（白名单 transparent）出现在 `lib/pages/**`；
- 视觉回归：Playwright 截图基线（mini H5 / admin 关键页 × 两主题），Flutter golden tests（关键组件 × 两主题）。

### 8. 页面级打磨与原型对齐
以 `.superpowers/brainstorm/*/content/` 原型为基准逐页验收（首页智荐、菜谱、食集详情 4-Tab、我家余量、采购、食记、我的、家庭页），每页出 before/after 截图对比，用户验收后关闭。

### 9. 大文件拆分（随收敛进行）
Flutter `dish/create_page.dart`(2043)、`menu/detail_page.dart`(1586)、`shopping_page.dart`(1073)；mini `menu/Detail.vue`(1364)、`shopping/List.vue`(933)、`dish/Create.vue`(917) 拆为页面壳 + 区块组件，单文件 ≤ 500 行。

## Scope
In：三端全部页面与共享组件。Out：品牌 VI 重做、插画体系外包（空态插画先用 emoji/线性图标，后续可替换）。

## Impact Analysis
纯前端；不改 API。风险在大面积替换引入视觉回归 → 截图基线 + 分页验收。
