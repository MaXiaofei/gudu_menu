# Implementation Tasks: 三端视觉与设计系统收敛

**Change ID:** `design-system-convergence`

## 1. Token 管线 + 换肤（2026-10-16 提前启动）
- [x] 1.1 tokens.json 皮肤注册表：`color.theme.<key>` 增 label/tagline/legacyKey/onPrimary/admin(sidebar×3)/chart；第一项为默认皮肤
- [x] 1.2 `scripts/design/gen-tokens.mjs`（零依赖）生成 Flutter `lib/core/tokens.g.dart`、mini `src/styles/tokens.generated.{ts,css}`、admin `src/styles/tokens.generated.ts`；`--check` 模式供 CI
- [x] 1.3 皮肤门禁：字段完整、#RRGGBB、WCAG 对比度（正文 ≥4.5、辅助 ≥2.5、主色深底白字 ≥3、主色按钮 <3 告警 <2.2 拒绝）；`node --test scripts/design/gen-tokens.test.mjs` 7/7
  - 门禁发现：抹茶 body `#6B7660`/bg 对比度 4.39 不达标 → 调为 `#68725D`（4.63，仅加深 3%）
- [x] 1.4 接入（三端均已改为读生成文件）：
  - [x] mini：`utils/theme.ts` 的 THEMES/THEME_OPTIONS 由 `SKINS` 派生、`themeCssVars`=`skinCssVars`（新增 `--on-primary`/`--sh` 随皮肤）；App.vue 改 `@import './styles/tokens.generated'`（mp-weixin 构建已验证内联进 app.wxss），顺带修 `.yh-btn-gradient/.yh-btn-ghost` 写死奶油色（抹茶下不变色）；主题页遍历 SKINS；tabBar 图标脚本收进 `scripts/design/gen-tabbar-icons.py`（读 tokens.json，重生成与现有 PNG 逐字节一致）；unit 守护「每套皮肤图标齐全」「theme.ts/App.vue 无皮肤色值」；vitest 114、vue-tsc 0、H5 Playwright 8/8
  - [x] Flutter：`AppTokens.cream/matcha` = `kSkin_*`（删手写色板）；`ThemeController` 改 skin key（新偏好 `gudu.brandSkin` 字符串，兼容读取旧 int `gudu.brandTheme` 并迁移）；主题页遍历 `kBrandSkins`；我的页显示皮肤 label；flutter test 341 全绿、analyze 无新增
  - [x] admin：`themes.ts` 改由 `ADMIN_SKINS` 派生（皮肤 key 统一为 cream/matcha，`adminSkinOf` 兼容旧 key warm/green）；vitest 65、build、Playwright 14 全绿
  - [x] 三端主题模块不再手抄色板（页面内散落的裸色值归 3.1–3.3 清理）
- [ ] 1.5 CI 加 `node scripts/design/gen-tokens.mjs --check` 与生成器单测（随 production-ops-baseline 3.1）

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
