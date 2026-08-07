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
- 用户可见的**任何文案与图标位置**一律不带 emoji 字符，包括但不限于：页面标题、Tab、按钮、提示条、空态文案，以及**底部功能栏（底部 Tab 栏）的图标**（如 `📋 ⚠️ ✨ 🏆 🍱 ♨️ 🛒`）。
- 警示/强调靠颜色 + 边框 + 图标组件（`Icon`），不靠 emoji 字符。
- 空态文案举例：`没有临期食材`（不带 `✨`）、`库存充足`（不带 `✅`）。
- 食材/菜品缩略图属于「需要图片的位置」，见 §10。

### 2.3 图标语义（含底部功能栏）
- 底部功能栏（底部 Tab 栏）、工具卡等**一律用 Material 矢量图标（`Icons.*`）**，不用 emoji 字符。
- 图标选中性功能语义（如 `Icons.restaurant` 表「荐菜」），避免营销味图标（`Icons.auto_awesome` 闪光）。
- 凸起 FAB 是否放图标由设计决定；不放时圆心留空，靠下方文字标识。

---

## §4 圆角阶梯（7 档）

| token | 值 | 用途 |
|---|---|---|
| `r2` | 2 | 小色条、进度条、细分割块 |
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

## §6 间距阶梯（10 档）

| token | 值 | 用途 |
|---|---|---|
| `sp2` | 2 | 微调间隙（图标内、细分割线旁） |
| `sp4` | 4 | 紧凑间距 |
| `sp6` | 6 | 小间距：胶囊内边距、胶囊间 gap |
| `sp8` | 8 | 行内间距 |
| `sp10` | 10 | 列表项内边距、紧凑卡片 padding |
| `sp12` | 12 | 卡片内边距、列表水平边距 |
| `sp16` | 16 | 区块间距、表单垂直间距 |
| `sp20` | 20 | 区块间距、较大留白 |
| `sp24` | 24 | 大区块间距 |
| `sp32` | 32 | 页面级间距 |

基准 2px 起步（2/4/6 偏紧凑档，8+ 为常规档）。**token 阶梯是封闭集合——禁止使用 13/14 等 token 外非标间距值；若确需新档位，先在 `app_theme.dart` 补 token 再用。** 具体以 `app_theme.dart` 为准。

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

## §12 列表分页约定（跨端）

> 所有端、所有列表接口与列表页**必须分页**，不允许一次性全量拉取（统计数据导出、下拉选择项等确需全量的场景除外，且须在代码注释中说明理由）。

### 12.1 适用范围

| 端 | 位置 | 约束 |
|---|---|---|
| 后台管理 | `menu-admin/` 列表页 + `menu-api/` 列表接口 | 列表分页 |
| 小程序 | `menu-mini/` 列表页 | 列表分页 |
| App | `menu-flutter/` 列表页 | 列表分页 |
| 后端接口 | `menu-api/` 所有 `GET ...列表` | 必须收 `pageNum`/`pageSize`、返回分页结构 |

### 12.2 默认每页 15 条

- **默认 `pageSize = 15`**（用户未指定分页大小时）。
- 后端分页基类 `common/PageQuery.java` 的 `pageSize` 默认值、前端各列表页的 `_pageSize`/默认请求参数，统一对齐到 **15**。
- 分页参数名固定：`pageNum`（页码，从 1 开始）/ `pageSize`（每页条数）。

### 12.3 分页响应结构

- 后端统一返回 `R<IPage<T>>`，前端按 `{ records, total, current, size }` 消费。
- 「是否还有下一页」由调用方按 `records.length < pageSize` 启发式判断（项目无 `hasMore` 字段，保持现状）。

### 12.4 现状待对齐（设计债）

本约束为新增（2026-08-06）。现有代码存在偏差，需在后续迭代中逐步对齐到 `pageSize=15`，新代码立即遵循：
- 后端 `PageQuery.pageSize` 默认值为 10（应对齐到 15）。
- 前端各列表页默认 `pageSize` 不一致（食集/菜品=20、采购=10、库存/食材全量拉取），应统一到 15；全量拉取场景若确需保留，须注释说明。

---

## §13 顶栏与详情页结构约定 ⭐（新增 2026-08-07）

**顶栏改造的核心：去掉橙色顶栏色块、去掉「XX详情」废话标签、状态栏与顶栏融合。**

### 13.1 三类页面顶栏分工

| 类 | 页面 | 顶部形态 | 文案 |
|---|---|---|---|
| **详情页** | 菜品详情、食集详情、食材详情 | 奶油底，箭头行在上、文案行在下（间距 `sp8`） | **有**（真实菜名/食集名/食材名） |
| **录入页** | 录入新菜、写点评、录入食材、手动添加库存、AI 估营养 | 奶油底，**只有返回箭头** | **无**（通用标签全删） |
| **Tab 主页** | 菜谱/食集/库存/推荐/我的（5 个 Tab） | 奶油底，**无小标题**；有操作的页面操作按钮右对齐独占一行，无操作的页面内容直接顶到状态栏下 | **无**（页面名也不放，靠底部 tab bar 选中态提示当前 tab） |

- **核心原则**：文案只在「有真实内容名」的页面留（菜名/食集名/食材名）。通用标签（「菜品详情」「录入新菜」这种废话）**一律删除**。
- 录入页用户点「新建」进来，本来就知道在干嘛，不需要标签提醒。
- **Tab 主页不放页面名**：顶部无文字（连「菜谱」「食集」也不放），当前所在 tab 靠底部 tab bar 的选中态提示。这是对"每页都带主标题很 low"反馈的回应——Tab 主页和详情/录入页统一遵循"无废话标题"。

### 13.2 详情页统一结构（强约束）⭐

菜品详情、食集详情、食材详情**视觉结构完全统一**，自上而下：

```
返回箭头行（独占，高 32px）
      ↕ 间距 AppTokens.sp8（8px）
真实名（h3，18/w700，深色）
副信息行（小字，如「备料5分·烹饪5分」「份数4·关联3道菜」）
```

- **箭头行独占第一行**：返回箭头 `‹` 不与任何文字同行。
- **真实名独立成行**：菜名/食集名/食材名放在箭头下方，用 `h3`（18/w700），不与首字头像、状态胶囊等元素挤在一行。
- **首字头像、状态胶囊等下沉为副信息**：食材的首字色块、食集的状态胶囊，放在副信息行（小字区），**不抢占标题行**。
- **箭头与真实名的间距固定 `AppTokens.sp8`（8px）**，三个详情页必须一致，不得各自调参。
- **禁止「XX详情」标签**：不得在顶部写「菜品详情」「食集详情」「食材详情」这种通用标签，标题就是真实内容名。

### 13.3 组件契约

- **`ActionBar(action?)`** → Tab 主页用。奶油底，**无标题**，只有一个可选的操作槽（右对齐）。无操作时不渲染（内容直接顶到状态栏下）。状态栏由内置 `AnnotatedRegion` 控制。
- **`BackHeader(title?, action?)`** → 详情页（传 title）+ 录入页（不传 title）共用。奶油底，箭头行在上、标题行在下、间距 `sp8`。title 不传时只渲染箭头行。
- **禁止重复造轮子**：详情页/录入页不得各自手写顶栏，必须用 `BackHeader`；Tab 页必须用 `PageHeader`。

### 13.4 状态栏与顶栏融合（铁律）

- **状态栏背景 = 顶栏背景**。任何页面都不允许出现状态栏与顶栏的色差断层。
- **实现方式**：用 `AnnotatedRegion<SystemUiOverlayStyle>` **封装进 `PageHeader` / `BackHeader` 组件内部**，组件 build 时自动包一层，调用方无感知。
  - 背景色：`AppTokens.bg`（奶油底 `#FDFAF4`）。
  - 文字/图标色：深色（`SystemUiOverlayStyle.dark`）。
- **全局兜底**：`main.dart` 启动时调 `SystemChrome.setSystemUIOverlayStyle(奶油底 + 深色字)`，防止未用 Header 组件的漏网页面状态栏失控。
- 本项目三类页面顶栏均为奶油底，统一深色字，无需动态切换。

---

## §14 空态 / 错误态 / 占位 统一约束 ⭐（新增 2026-08-07）

**三类异常态必须用公共组件，禁止各自手写或语义错位。**

### 14.1 空态 vs 错误态（铁律：空 ≠ 错）

| 状态 | 组件 | 触发场景 | 文案 |
|---|---|---|---|
| **加载中** | `LoadingView` | 数据未到 | 骨架闪烁（禁 spinner，见 §1） |
| **空态** | `EmptyView(text, [subtitle, actionLabel, onAction])` | 数据到了但为空（如"暂无菜品"） | 描述性（"暂无XX""把几道菜凑成一顿饭"） |
| **错误态** | `ErrorView(text, onRetry)` | 请求失败/异常 | 错误描述 + **重试按钮** |

- **禁止用 `EmptyView` 顶替错误态**。"暂无库存"和"加载失败"语义不同，错误必须有重试入口。
- **禁止手写灰字空态**（如 `Center(child: Text('暂无食材'))`）。所有空态必须走 `EmptyView`。
- `EmptyView` 增强态（带 subtitle + CTA）用于引导性空态（如食集列表空时"建一个食集"）；纯描述空态用最简形态。

### 14.2 图片占位统一（首字色块）

- 列表/卡片缩略图占位**一律用首字色块**（菜名/食系/食材名首字 + `secondary` 底 + `title.withAlpha(115)`），由公共组件 `InitialAvatar` 提供。
- **禁用 Material 图标顶图**（如 `Icons.image_outlined`、`Icons.eco_outlined` 当缩略图）。图标只用于功能图标，不替代图片位（§10）。
- **现状需整改**：ingredient 列表用 `CircleAvatar + eco_outlined` 叶子图标 → 改 `InitialAvatar`；menu `_CoverStack` 加载失败占位是纯色块 → 改首字。
- 同一"首字占位"逻辑（`characters.first + secondary + title alpha115`）当前在 4 处重复实现（dish 列表、menu `_InitialStack`、dish 详情用料行、pantry 详情头）→ 抽 `InitialAvatar` 后必须复用，禁止继续复制。

### 14.3 组件契约（禁止重复造轮子）

以下组件必须用公共版，禁止在页面内私有重造同名组件：

| 组件 | 用途 | 禁止 |
|---|---|---|
| `LoadingView` | 加载态 | 自写骨架/spinner |
| `EmptyView` | 空态 | 手写 `Center+Text` |
| `ErrorView` | 错误态 | 用 EmptyView 顶替、自造 `_RetryView` |
| `InitialAvatar` | 首字色块占位 | 复制首字逻辑、用图标顶图 |
| `SectionTitle` | 分区标题 | 私有 `_SectionTitle`（dish/menu 详情已各写一份，样式不一） |
| `StatusChip` | 状态/标签胶囊 | 私有 `_StatusPill`/`_StatusChip` |
| `AppCard` / `AppCard.outlined` | 卡片容器 | 手写 Container/Card 各定边框阴影 |
| `ActionBar` | Tab 主页顶栏 | 手写浅色栏、用橙色 AppBar |
| `BackHeader` | 详情/录入页顶栏 | 手写返回行、用橙色 AppBar |

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
