# 咕嘟小食单 · CLAUDE.md

## 设计系统（新 UI 开发必读）

写任何 Flutter UI 前，第一步读 `menu-flutter/lib/core/app_theme.dart`。

### 文字 — VxTextStyles（禁止硬编码 fontSize）

```dart
final ts = AppTokens.of(context).textStyles;
Text('标题', style: ts.pageTitle);          // 16/w700
Text('菜名', style: ts.cardTitle);          // 14/w700
Text('分区标签', style: ts.sectionLabel);    // 11/w700
Text('正文', style: ts.body);               // 14/w400
Text('辅助', style: ts.caption);            // 12/w400
Text('芯片', style: ts.chip);               // 10/w800
// 自定义颜色：ts.sectionLabel.copyWith(color: AppTokens.error)
```

### 颜色 — AppTokens（禁止裸色值）

```dart
final t = AppTokens.of(context);
t.primary / t.title / t.body / t.caption / t.bg / t.card / t.border
AppTokens.success / warning / error / info  // 功能色两套共享
```

### 圆角 / 间距

```dart
AppTokens.rMd(12) / rLg(16) / rSm(8)
AppTokens.sp12 / sp16 / sp24
```

### 已有共享组件（不要重复造轮子）
- `AppCard` — 卡片
- `LoadingView` / `EmptyView` — 骨架屏 / 空态
- `StatusChip` — 状态徽章
- `GradientButton` — 渐变按钮

### 待建（重复模式优先抽组件）
- `SectionLabel` — 分区小标题（12+ 处重复）
- `InfoCallout` — 说明提示条（4 处重复）
- `Counter` — 加减盘（2 处重复）
- `SearchInput` — 搜索框（3 处重复）

### 设计参考
- 原型：`.superpowers/brainstorm/44829-1783002708/content/`
- Token 权威定义：`docs/design/tokens.json`
- 设计规范：`docs/design/DESIGN.md`
