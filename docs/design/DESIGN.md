# 咕嘟小食单 · 设计规范 (DESIGN.md)

> 颜色/圆角/间距 token 的权威定义在 `lib/core/app_theme.dart`。
> 代码注释中以 `DESIGN.md §N` 形式引用本文档对应章节。

---

## §0 设计资料层级（原型为准）

> **背景**：当前 Flutter 代码流程不完整、排版较乱，原型承载了完整的设计意图。
> 因此**以原型为设计准则，代码为辅**，开发中双向同步。

| 层级 | 资料 | 位置 | 说明 |
|---|---|---|---|
| ① **设计准则** | 44829 批次原型 | `.superpowers/brainstorm/44829-1783002708/` | 完整设计意图的权威来源，**开发以此为准**。其中 `home-aisho.html` 已暂停（智荐首页概念待定） |
| ② **参考实现** | Flutter 代码 | `menu-flutter/` | 当前实现，流程不完整，排版待优化。开发中会调整；**代码改动后需回写更新原型**，保持双向同步 |
| ③ **历史归档** | 早期原型批次 | `26374` / `36853` / `41263` / `41398` | 早期设计探讨产物，已被 44829 取代，**不作参考** |

### 同步规则
- 新功能/改版：**先改原型（44829），再改代码**
- 代码修复 bug：改动确认后，**回写原型**保持一致
- 原型与代码冲突时：
  - **Token 值（字号/颜色/圆角/间距）→ 以本文档约束为准**（代码尚未统一处理，原型可能使用随手填的近似值）
  - **布局/交互/流程 → 以原型为准**（原型承载完整设计意图，代码是参考实现）
  - 如果原型本身有错，双方确认后同步修改

---

## §1 加载态：禁止 spinner

- 列表/详情加载时**不使用圆形 spinner**，统一用骨架屏（skeleton）占位。
- 骨架屏 = 主色浅底矩形 + 透明度闪烁动画（`TweenAnimationBuilder` 0.3↔0.7/0.8，1200ms）。
- 实现：`lib/widgets/loading_empty.dart` 的 `LoadingView`，`lib/widgets/image_viewer.dart` 的 `_ImageViewSkeleton`。

---

## §2 文案：去 AI 味、去 emoji

### 2.1 禁止 AI 营销口吻
- 禁用「智荐」「AI 帮我」「为你推荐」「系统帮你」等拟人/算法营销词，改用中性功能名（「推荐」「推荐菜」）。
- 禁止把推荐算法的内部逻辑（临期/忌口/历史/季节）写进用户可见的副标题。
- PM 黑话（动线闭环/总枢纽/铁律/双轨库存）只允许出现在代码注释和本文档，不进 UI 文案。

### 2.2 禁止 emoji 进 UI 文案
- **页面标题、Tab、按钮、提示条、空态文案**一律不带 emoji 字符（如 `📋 ⚠️ ✨ 🏆 🍱 ♨️ 🛒`）。
- 警示/强调靠颜色 + 边框 + 图标组件（`Icon`），不靠 emoji 字符。
- 空态文案举例：`没有临期食材`（不带 `✨`）、`库存充足`（不带 `✅`）。
- 食材/菜品缩略图属于「需要图片的位置」，见 §10。

### 2.3 图标语义
- 底部 Tab、工具卡等**用 Material 矢量图标（`Icons.*`）**，不用 emoji 字符。
- 图标选中性功能语义（如 `Icons.restaurant` 表「荐菜」），避免营销味图标（`Icons.auto_awesome` 闪光）。
- 凸起 FAB 是否放图标由设计决定；不放时圆心留空，靠下方文字标识。

---

## §4 圆角阶梯（6 档）

| token | 值 | 用途 |
|---|---|---|
| `rXs` | 4 | 小标签、chip |
| `rSm` | 8 | 小按钮、骨架行 |
| `rMd` | 12 | 卡片、输入框、按钮标准 |
| `rLg` | 16 | 大卡片、头部容器 |
| `rXl` | 22 | 模态、大容器 |
| `rPill` | 999 | 圆形、胶囊 |

（具体以 `app_theme.dart` 为准）

---

## §5 阴影

- FAB / 凸起按钮：`--shadow-fab: 0 4px 12px rgba(shadow,.40)`（token `elevationFab`）。

---

## §6 间距阶梯（8 档）

| token | 值 | 用途 |
|---|---|---|
| `sp2` | 2 | 微调间隙 |
| `sp4` | 4 | 紧凑间距 |
| `sp8` | 8 | 行内间距 |
| `sp12` | 12 | 卡片内边距、列表水平边距 |
| `sp16` | 16 | 区块间距、表单垂直间距 |
| `sp24` | 24 | 大区块间距 |
| `sp32` | 32 | 页面级间距 |
| `sp48` | 48 | 超大间距 |

基准 4px 倍数。具体以 `app_theme.dart` 为准。

---

## §7 交互状态

- 过渡时长：150ms（fast transition）。
- 选中态用 `primary`，未选中用 `caption`。

---

## §8 卡片 / TabBar

- 卡片标准：圆角 `rMd(12)`，1px 描边，柔和阴影。
- TabBar：高 56px · icon 20px · label 9px。

---

## §9 契约（TabBar / Card）

- **TabBar 契约**：5 个 Tab（菜谱 / 食集 / [推荐] / 我家库存 / 我的）；推荐为凸起 FAB（直径 40px · 上凸 16px），其余为平铺 nav item；状态独立保持（IndexedStack）。
- **Card 契约**：通用卡片用 `AppCard`，统一圆角/描边/阴影，不自造样式。

---

## §10 图片占位规则 ⭐（新增）

**凡是需要图片的位置，前端必须能优雅降级——不加载图片也要正常显示，不允许出现空白破图或布局塌陷。**

### 10.1 占位策略
所有图片位置默认渲染一个**占位容器**（尺寸/圆角与目标图片一致），图片加载失败或 url 为空时停留在占位态，不报错、不留白。

### 10.2 占位样式
- 背景：`primarySoft`（主色浅底）或 `card.withValues(alpha: 0.3)`。
- 前景：居中 `Icon(Icons.image_outlined)` 或食材/菜品名首字，透明度 0.4~0.5。
- 圆角、尺寸与该位置正常图片一致（不因占位改变布局）。

### 10.3 实现要求
- 用 `Image.network(..., errorBuilder: ...)` 兜底占位 widget。
- 用 `Image.network(..., loadingBuilder: ...)` 在加载中也显示占位（禁止 spinner，见 §1）。
- 缩略图 + 原图双链路：先展示缩略图，原图后台加载完无缝切换；任一失败都回退到占位（参考 `lib/widgets/image_viewer.dart`）。

### 10.4 「需要图片的标题」约定
指**页面/卡片标题旁本应配图的位置**（如菜品卡缩略图、食材图标、分类头图）。
- 即使后端没返回图片 url，或图片 404，标题文字 + 占位图必须完整渲染。
- **绝不允许因为图片缺失导致标题区域空白、错位或整张卡片不显示。**
- emoji 不作为图片的替代品（见 §2.2）；图片缺失时走占位，不用 `🍅🐟` 顶替。

---

## §11 Flutter 代码实现约束 ⭐（新增 2026-08-05）

> **写任何新 UI 前第一步：读 `lib/core/app_theme.dart`，确认可用 token 和组件。**

### 11.1 文字：必须用 VxTextStyles，禁止硬编码 fontSize

语义化文字体系定义在 `lib/core/app_theme.dart` → `VxTextStyles`。对齐 `tokens.json` 的 11 档阶梯。

**用法：** `final ts = AppTokens.of(context).textStyles;`

| 别名 | 字号/字重 | 用途 |
|---|---|---|
| `ts.pageTitle` | 16/w700 | 页面主标题 |
| `ts.cardTitle` | 14/w700 | 卡片/列表项标题 |
| `ts.sectionLabel` | 11/w700 | 分区标签，`.copyWith(color:)` 改语义色 |
| `ts.body` | 14/w400 | 正文 |
| `ts.caption` | 12/w400 | 辅助说明 |
| `ts.chip` | 10/w800 | Chip/Badge 文字 |
| `ts.meta` | 9/w800 | Tab 文字/元信息 |

完整阶梯（display→micro 共 11 档）见 `VxTextStyles` 类定义。

**禁止：**
- ❌ 手写 `fontSize: N` — 一律走 `ts.xxx` 或 `ts.xxx.copyWith()`
- ❌ `fontWeight: FontWeight.w800` 滥用 — w800 仅限 display/h1/h2/tiny/micro 五档

### 11.2 颜色：必须用 AppTokens，禁止裸色值

```dart
final t = AppTokens.of(context);
t.primary / t.title / t.body / t.caption / t.bg / t.card / t.border
AppTokens.success / warning / error / info  // 功能色两套共享
```

**禁止：** `Color(0xFF...)` 裸色值 — 主题切换时不会跟随。

### 11.3 圆角 / 阴影 / 间距

```dart
AppTokens.rXs(4) / rSm(8) / rMd(12) / rLg(16) / rXl(22) / rPill(999)
AppTokens.sp4 / sp8 / sp12 / sp16 / sp24 / sp32 / sp48
t.elevationSm / t.elevationMd / t.elevationLg / t.elevationFab
```

### 11.4 已有共享组件（禁止重复造轮子）

| 组件 | 文件 | 用途 |
|---|---|---|
| `AppCard` | `lib/widgets/app_card.dart` | 通用卡片 |
| `LoadingView` / `EmptyView` | `lib/widgets/loading_empty.dart` | 骨架屏 / 空态 |
| `StatusChip` | `lib/widgets/status_chip.dart` | 状态徽章 |
| `GradientButton` | `lib/widgets/gradient_button.dart` | 渐变主按钮 |

### 11.5 重复 UI 模式（做新功能优先抽组件，不复制代码）

以下模式在当前代码中出现 ≥2 次，属于设计债。遇到时先抽组件再使用：

| 组件 | 重复次数 | 说明 |
|---|---|---|
| `SectionLabel` | 12+ | 分区小标题（`ts.sectionLabel` + `letterSpacing: 1`） |
| `InfoCallout` | 4 | 说明提示条（`t.highlight` bg + `t.border` 描边 + `AppTokens.rSm` 圆角） |
| `Counter` | 2 | 加减盘（± 圆形按钮 + `ts.display` 大数字 + 下方差值文字） |
| `SearchInput` | 3 | 搜索框（白底 + 1.5px 主色描边 + `rMd` 圆角 + 搜索图标 + ✕ 清除） |

---

## 附：本文档维护约定

- 新增视觉/交互约定时，同步在对应代码注释里写 `DESIGN.md §N` 引用。
- token 权威定义始终在 `app_theme.dart`；本文件只做语义说明，不重复抄数值。
- 引用过本文档的代码文件（需保持引用有效）：
  - `lib/core/app_theme.dart`（§4/§5/§6/§7/**§11**）
  - `lib/widgets/loading_empty.dart`（§1）
  - `lib/widgets/image_viewer.dart`（§1）
  - `lib/widgets/app_card.dart`（§8/§9）
  - `lib/widgets/main_shell.dart`（§8/§9）
  - `lib/pages/dish/list_page.dart`（§11 — VxTextStyles 参考实现）
