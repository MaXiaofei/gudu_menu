# 咕嘟小食单 · CLAUDE.md

## 自动化测试约定（新功能必读）

开发任何新功能时同步补自动化测试，按「功能代码 + 后端 E2E + Flutter 测试 + 小程序测试」四件套交付，全部跑绿才算完成：

- **后端**：新接口加进 `menu-api` 的 `GuduE2ECoverageTest`（长尾）或 `GuduE2EFlowTest`（核心链路）；自建数据用 `E2E` 前缀命名并在 `e2e-seed.sql` 有兜底清理（唯一约束表必加），保证可重复跑。跑法：SSH 隧道连 staging 后 `DB_HOST=127.0.0.1 REDIS_HOST=127.0.0.1 ./mvnw test -Dtest='GuduE2E*'`
- **Flutter APP**：业务逻辑补 `test/`（widget/service），页面流程补 `integration_test/`（模拟器 `flutter test integration_test/xxx -d emulator-5554`，登录态残留要自适应）
- **小程序 menu-mini**：补 `menu-mini/test/`（unit）与 `e2e/`（playwright），与 APP 同步交付
- **改表结构时**：同步检查 `e2e-seed.sql` 列名（V55 曾因漏改致 E2E 全挂）

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
