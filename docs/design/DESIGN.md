# Design Constraints · 咕嘟小食单

> **所有 UI 代码必须遵守。违反 = 代码不合格 = 必须返工。**
>
> 单源真相：`docs/design/tokens.json` · 预览：`docs/design/tokens.html` · 旧版双色样板：`docs/design/design-themes.html`

---

> ## 📖 必读约束
>
> 开发任何 UI 组件前必须先读：
> - @docs/design/DESIGN.md（约束清单）
> - @docs/design/tokens.css（颜色 / 间距单一真相源）
>
> **违反约束 = 必须返工。**

---

## 0. 文件地图（Single Source of Truth）

| 文件 | 作用 | 谁消费 |
|---|---|---|
| `docs/design/tokens.css` | **CSS 变量单一真相源**（颜色 / 间距 / 圆角 / 阴影 / 字号 / 动效） | 三端 UI 代码直接引用 |
| `docs/design/tokens.json` | 规范（机器可读） | 三端生成脚本 / 人工对齐 |
| `docs/design/tokens.html` | 可视化预览（含主题切换） | 设计评审 / PM |
| `docs/design/design-themes.html` | 双主题组件样板 | 旧版对照 |
| `docs/design/DESIGN.md` | 约束清单（本文件） | 所有 UI 开发 |
| `menu-flutter/lib/core/app_theme.dart` | Flutter `ThemeData` | Flutter 端 |
| `menu-mini/src/uni.scss` + `src/App.vue` | scss 变量 + `@import` tokens.css | uni-app 小程序 |
| `menu-admin/src/styles/themes.ts` + `global.css` | CSS 变量 | admin 后台 |

> **新增 token 必须先改 `tokens.json` + `tokens.css`**，再同步到三端消费点。任何端"自创"变量即返工。

---

## 1. 硬约束（违反即返工）

### 颜色
- ✅ 颜色只能来自 `tokens.json → color.theme[cream|matcha]` 或 `color.semantic`
- ✅ 业务代码引用 CSS 变量（`--color-primary` / `--color-success-soft` / …），**禁止硬编码 hex**
- ✅ 双主题通过 `data-theme="cream|matcha"` 切换，**禁止写 if-else 分支**
- ❌ 禁止 `#000000` 纯黑 / `#FFFFFF` 纯白（背景用 `--color-bg`，文字用 `--color-title`，卡片用 `--color-card`）
- ❌ 禁止在组件里直接写 `#E89150` —— 必须 `var(--color-primary)`
- ❌ 禁止自创颜色名（如 `--orange-500`）—— 走 primitive palette 或 semantic token

### 间距
- ✅ 只用 `--space-xxs(2) / xs(4) / sm(8) / md(12) / lg(16) / xl(24) / xxl(32) / xxxl(48)`
- ❌ 禁止 `padding: 13px`、`margin: 7px` 等任意数值（原型里 7/9/10 已收敛到最近档）

### 圆角
- ✅ 只用 `--radius-xs(4) / sm(8) / md(12) / lg(16) / xl(22) / pill(999) / circle(50%)`
- ❌ 禁止 `border-radius: 13px`、`border-radius: 99px`（99px 已收敛到 pill/999）

### 阴影
- ✅ 只用 `--shadow-sm / md / lg / fab`，颜色走 `--color-shadow-rgb` rgb 变量（主题感知）
- ❌ 禁止自写 `box-shadow: 0 2px 5px rgba(0,0,0,.1)`

### 动效
- ✅ 过渡时长 `--motion-fast(150ms) / normal(250ms) / slow(400ms)`
- ✅ 所有可交互元素（按钮 / chip / tab）**必须有 hover 态 + 150ms transition**
- ❌ 禁止 `transition: all .3s` 这种模糊声明

### 加载 / 错误
- ✅ 加载态 = **骨架屏**（占位矩形 + 主色浅闪），**禁止 spinner**
- ✅ 错误提示 = **inline error**（`--color-error-soft` 背景 + `--color-error-deep` 文字），**禁止 `alert()` / `confirm()` / `uni.showToast({icon:'error'})`**

### 工具类 / 构建
- ❌ 禁止 tailwind / unocss 任意值 `w-[373px]`、`bg-[#E89150]`
- ❌ 禁止组件内硬编码 API URL（走 `api_client.dart` / `api/` 模块）

---

## 2. 颜色系统

### 2.1 主题色（theme-aware · 双主题）

| Token | 用途 | Cream | Matcha |
|---|---|---|---|
| `--color-primary` | 主按钮 / FAB / 高亮文字 | `#E89150` | `#7A9A5B` |
| `--color-primary-deep` | hover 加深 / 渐变终点 | `#D17A3C` | `#648449` |
| `--color-primary-soft` | 主色浅底 / selected 态 | `#F6D9BE` | `#D8E2C8` |
| `--color-secondary` | 辅色底 / 次级卡 | `#FBF0DD` | `#E8E4D5` |
| `--color-accent` | 强调文字 / 图标 | `#B8762E` | `#6B8A4D` |
| `--color-bg` | 页面底 | `#FDFAF4` | `#F7F5EE` |
| `--color-card` | 卡片 / 浮层 | `#FFFFFF` | `#FFFFFF` |
| `--color-border` | 描边 / 分隔线 | `#F0E6D6` | `#E5E2D5` |
| `--color-title` | 标题 | `#4A382A` | `#2E3520` |
| `--color-body` | 正文 | `#6E5C49` | `#6B7660` |
| `--color-caption` | 辅助 / meta | `#9C8C7A` | `#9CA58F` |
| `--color-highlight` 🆕 | 高亮底（warning callout） | `#FFF7EC` | `#FBF9EC` |
| `--color-shadow-rgb` | 阴影 rgb（不带 rgb()） | `169,101,30` | `122,154,91` |

### 2.2 功能色（两套主题共享）

| Token | 用途 | Hex |
|---|---|---|
| `--color-success` / `--color-success-soft` / `--color-success-deep` | 完成、扣样、余量充足 | `#4FAE6E` / `#F0F8EE` / `#3E8C58` |
| `--color-warning` / `--color-warning-soft` / `--color-warning-deep` | 临期、需注意 | `#E5A938` / `#FFF7EC` / `#B8762E` |
| `--color-error` / `--color-error-soft` / `--color-error-deep` | 过敏、缺料、过期 | `#DB5A4E` / `#FBD9D5` / `#B8382B` |
| `--color-info` / `--color-info-soft` / `--color-info-deep` | 提示、加入食集 | `#4FA0D0` / `#E8F3FA` / `#3E7FA6` |

> 🆕 soft/deep 变体为本次从原型提取：**soft 做 callout 背景 + deep 做文字**，是固定搭配（参考 `tokens.html` Callout 组合示例）。

### 2.3 渐变

| Token | 值 | 用途 |
|---|---|---|
| `--grad-hero` | `linear-gradient(135deg, var(--color-primary), var(--color-primary-deep))` | 主推荐卡 / 大 CTA |

> 仅此一档渐变。其他场景用纯色 `--color-primary` / `--color-primary-soft`。

### 2.4 原始色板（primitive · 仅语义 token 参考）

业务代码**禁止**直接使用 `--orange-400`、`--red-50` 等原始色。原始色板只存在于 `tokens.json → color.primitive`，用于推导语义色。

---

## 3. 字号阶梯（11 档）

原型实测 13 档，收敛为 11 档主阶梯。**9/10/11/18 为本次新增**（原型中 109 / 150 / 119 / 28 次使用）。

| Token | size / weight / lh | 用途 | 示例 |
|---|---|---|---|
| `display` | 40 / 800 / 1.2 | 落地页大标 | 今日咕嘟 |
| `h1` | 32 / 800 / 1.25 | 页面标题 | 我家余量 |
| `h2` | 24 / 700 / 1.3 | 区块标题 | 智荐 |
| `h3` | 20 / 700 / 1.3 | 卡片标题 | 番茄牛腩煲 |
| `subtitle` 🆕 | 18 / 700 / 1.35 | 副标题 | 今天吃点什么？ |
| `lg` | 16 / 600 / 1.4 | 强调正文 | 列表项主文字 |
| `md` | 14 / 400 / 1.5 | 默认正文 | 35 分钟 · 385 kcal |
| `sm` | 12 / 400 / 1.5 | 次级正文 | 食材描述 |
| `xs` 🆕 | 11 / 700 / 1.4 | section label · chip | 找菜 · 低脂 |
| `tiny` 🆕 | 10 / 800 / 1.4 | 徽章 · callout 标题 | ⏰ 临期提醒 |
| `micro` 🆕 | 9 / 800 / 1.4 | tab label · meta | 📖 菜谱 · ✨ 智荐 |

> 字重只用 `400 / 500 / 600 / 700 / 800`。禁止 `font-weight: 300 / 900`。

---

## 4. 圆角阶梯（7 档）

| Token | 值 | 原型使用次数 | 用途 |
|---|---|---|---|
| `--radius-xs` | 4px | 2 | 极小装饰 / 标签 |
| `--radius-sm` | 8px | 44 | chip · 小方块 · 内部装饰 |
| `--radius-md` 🆕 | **12px** | **45** | **卡片 · 输入框（主档）** |
| `--radius-lg` | 16px | 1 | 大卡 |
| `--radius-xl` | 22px | 25 | Hero / 大区块 |
| `--radius-pill` | 999px | 59 | Tab / 按钮 / 圆形条 |
| `--radius-circle` | 50% | — | 头像 / FAB |

> ⚠️ 旧 `design-themes.html` 用 `r-sm:8 / r-md:14 / r-lg:22`。**`r-md` 已从 14 → 12**（原型 45 次使用 12px vs 10 次使用 14px）。历史代码需逐步迁移。

---

## 5. 阴影阶梯（4 档）

| Token | 值 | 用途 |
|---|---|---|
| `--shadow-sm` | `0 1px 3px rgba(var(--color-shadow-rgb), .08)` | 默认卡片 / 输入框 |
| `--shadow-md` | `0 6px 18px rgba(var(--color-shadow-rgb), .10)` | Hero / 弹层 |
| `--shadow-lg` | `0 14px 36px rgba(var(--color-shadow-rgb), .14)` | 模态 / 大浮层 |
| `--shadow-fab` 🆕 | `0 4px 12px rgba(var(--color-shadow-rgb), .40)` | FAB / 凸起按钮 |

> 阴影颜色通过 `--color-shadow-rgb` rgb 变量跟随主题，**禁止写死 rgba(0,0,0,.1)**。

---

## 6. 间距阶梯（8 档 · 4px 基数）

| Token | 值 | 原型高频场景 |
|---|---|---|
| `--space-xxs` | 2px | 极微调 |
| `--space-xs` | 4px | 紧凑元素内 |
| `--space-sm` | 8px | 组件间距 |
| `--space-md` | 12px | 卡片内 padding |
| `--space-lg` | 16px | 区块内 padding |
| `--space-xl` | 24px | section 间距 |
| `--space-xxl` | 32px | 大区块分隔 |
| `--space-xxxl` | 48px | 页面级分隔 |

> 原型实测 gap 高频：9(49) · 8(38) · 10(22) · 7(12) · 6(12)。
> **已收敛**：9/10 → `sm(8)` 或 `md(12)`；7/6 → `xs(4)` 或 `sm(8)`。**禁止自创 7/9/10px 间距**。

---

## 7. 动效

| Token | 值 | 用途 |
|---|---|---|
| `--motion-fast` | 150ms | hover / press / 微交互 |
| `--motion-normal` | 250ms | 展开 / 折叠 / 过渡 |
| `--motion-slow` | 400ms | 模态进出 / 大转场 |
| `--easing-default` | `cubic-bezier(.4,0,.2,1)` | 默认 |
| `--easing-decelerate` | `cubic-bezier(0,0,.2,1)` | 出现 |
| `--easing-accelerate` | `cubic-bezier(.4,0,1,1)` | 消失 |

---

## 8. 布局规则

| 规则 | 值 |
|---|---|
| 主内容区 | max-width `1000px`（预览）/ `340px`（移动原型视口）· 居中 |
| 卡片 | `--radius-md(12px)` · `--space-md(12px)` 内边距 · `--color-card` 背景 · **`shadow` 与 `border` 二选一**（禁止同时用） |
| 列表项 | 高度 **56px**（固定，非 auto） |
| Tab Bar | 高 56px · icon 20px · label 9px · **智荐 FAB 40px 凸起 -16px** |
| 网格 | CSS Grid · 禁止复杂 flexbox 百分比 |
| 卡片图 | 高度 125px · 纯色底（`--color-secondary` / `--color-primary-soft`），**禁止渐变图** |

---

## 9. 组件契约

### Button
```
Props: variant('primary'|'secondary'|'ghost'|'disabled') × size('md'|'sm')
Required: hover 态（primary → primary-deep）· 150ms transition
Forbidden: 自改颜色 / 加 border+shadow+white bg 三件套
```

### Chip（标签 / 状态）
```
Props: variant('solid'|'soft'|'outline') × semantic('success'|'warning'|'error'|'info')
Required: font-size 11px / 12px · --radius-pill · font-weight 700
Example: <span class="chip soft success">扣 8 样</span>
```

### Callout（提示条）
```
Required: soft 背景 + 同色 border + deep 文字（固定搭配）
Example: .callout.warning → background:--color-warning-soft / border:--color-warning / color:--color-warning-deep
Forbidden: 用 alert() / uni.showToast 替代
```

### Card
```
Required: 接受 children · 不接受 style override
Allowed: --color-card 背景 + --shadow-sm OR --color-border（二选一）
Forbidden: border + shadow + white bg 全用
```

### Input
```
Required: Label + Error slot · focus 时 --color-primary border + --color-shadow-rgb 外发光
Forbidden: 无 label 裸输入框
```

### Modal
```
Required: ESC 关闭 · 焦点陷阱 · --shadow-lg · --radius-xl
```

### TabBar
```
Required: 5 项（菜谱 / 食集 / 智荐 / 我家余量 / 我的） · 智荐为凸起 FAB
```

---

## 10. 反例（看到就重做）

| ❌ 反例 | ✅ 正确做法 |
|---|---|
| 一个组件用了 `--color-primary` 又 `--color-accent` 两种主色 | 主色一个，强调色只用于文字/图标 |
| 同一按钮在两个页面颜色不一样 | 走同一 variant，禁止 override |
| 卡片 `border + shadow + white bg` 三件套全上 | 选其一：`shadow`（浮起）或 `border`（贴地） |
| Form 用 `window.alert()` / `uni.showToast` 报错 | inline error · `--color-error-soft` 底 + `--color-error-deep` 文字 |
| `border-radius: 99px`（原型遗留） | 改 `--radius-pill(999)` |
| `border-radius: 14px`（旧 design-themes） | 改 `--radius-md(12)` |
| `font-size: 13px` / `15px` / `17px` | 取最近档 12/14/16 |
| `padding: 13px 15px 17px`（dish-card 原型） | 收敛到 `--space-md(12)` 或 `--space-lg(16)` |
| `box-shadow: 0 2px 5px rgba(0,0,0,.1)` | 用 `--shadow-sm/md/lg` |
| 加载用 spinner | 骨架屏（`--color-primary-soft` 闪烁） |
| `transition: all .3s` | `transition: background .15s var(--easing-default)` 明确属性 |
| 颜色 `#E89150` 硬编码在 .vue 里 | `var(--color-primary)` 或 `Theme.of(ctx).primaryColor` |

---

## 11. 三端落地对照

| 平台 | 消费方式 | 入口文件 |
|---|---|---|
| **uni-app 小程序** | scss 变量（`$primary` 等）+ CSS 变量（`var(--color-primary)`） | `menu-mini/src/uni.scss` · `App.vue` |
| **Flutter** | `ThemeData` + `AppTheme.cream` / `AppTheme.matcha` · 业务用 `Theme.of(ctx).colorScheme.primary` | `menu-flutter/lib/core/app_theme.dart` · `theme_controller.dart` |
| **Admin** | CSS 变量（`:root[data-theme]`） | `menu-admin/src/styles/themes.ts` · `global.css` |

> 任何端新增 token 必须**三端同步**。不允许只在 mini 端加 `--foo` 而 flutter 没对应字段。

---

## 12. 变更流程

1. 在 `docs/design/tokens.json` 增/改 token
2. 在 `docs/design/tokens.html` 预览 + 标注 🆕
3. 三端同步：`uni.scss` / `app_theme.dart` / `themes.ts`
4. PR 描述里列出本次新增 token（ reviewers 对照本文 §2–§7 检查 ）

---

**最后更新**：2026-07-28 · 提取自 18 个 HTML 原型（`44829` 批次 13 屏 + `41263` 批次 5 屏）。
