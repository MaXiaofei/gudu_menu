# Implementation Tasks: 三端视觉与设计系统收敛

**Change ID:** `design-system-convergence`

## 1. Token 管线
- [ ] 1.1 tokens.json 补齐（字号 7 档、间距、圆角、动效、语义色、数据色板 × 两主题）
- [ ] 1.2 `scripts/design/gen-tokens.mjs` 生成三端产物 + CI 一致性校验

## 2. 共享组件
- [ ] 2.1 Flutter：SectionLabel / InfoCallout / Counter / SearchInput / InitialThumb / ErrorView(重试)；golden tests × 两主题
- [ ] 2.2 mini：ui-card / ui-section-label / ui-callout / ui-counter / ui-search / ui-thumb / ui-state；unit
- [ ] 2.3 admin：PageCard / StatusTag / ErrorState；Element Plus 主题变量映射

## 3. 替换与守护
- [ ] 3.1 mini：12 页顶栏换共享组件；404 处字号、475 处间距、87 处色值 → 变量；stylelint 守护
- [ ] 3.2 Flutter：BoxDecoration 卡片 → AppCard；Colors.white → token；8 处 fontSize、4 处 Color(0x 清零；27 处吞错改 ErrorView；grep 守护
- [ ] 3.3 admin：51 处 hex、27 处字号、49 处内联 style 清理；ai-log 图表改品牌色板
- [ ] 3.4 状态栏随主题（Flutter）；tabBar 图标（mini）

## 4. 无障碍与动效
- [ ] 4.1 点击区域、Semantics/aria-label、对比度校验脚本（两主题）
- [ ] 4.2 大字体 1.3 无溢出（Flutter integration 截图）
- [ ] 4.3 动效规范落地（时长/曲线/骨架 shimmer）

## 5. 页面打磨（逐页 before/after 截图，用户验收）
- [ ] 5.1 首页智荐 5.2 菜谱 5.3 食集详情 4-Tab 5.4 我家余量 5.5 采购 5.6 食记 5.7 我的/家庭 5.8 登录与引导 5.9 admin 仪表盘

## 6. 大文件拆分
- [ ] 6.1 Flutter 三个大页面拆分 ≤ 500 行
- [ ] 6.2 mini 三个大页面拆分 ≤ 500 行

## 7. 视觉回归
- [ ] 7.1 Playwright 截图基线（mini H5、admin × 两主题）
- [ ] 7.2 Flutter golden 基线

## Quality Gate
- [ ] 守护规则 0 违规（pages 目录）
- [ ] 视觉回归基线入库；每页用户验收通过
