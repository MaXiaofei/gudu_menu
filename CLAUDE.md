# 咕嘟小食单 · CLAUDE.md

## 账号密码与配置（最高优先级，先读这里）

**所有服务器、数据库、预发/生产环境、Nacos、Redis、微信小程序的配置和账号密码，全部在仓库根目录的 `.env` 文件里。**
在 menu-new 目录下任何涉及使用账号密码 / 连接地址 / 密钥的场景，必须**先查这个文件**，不要向用户索要，也不要臆造。模板见 `.env.example`。

## Git 推送前必须确认账户（每次 push 前必做）

执行 `git push` 之前，必须先在**对话里**向用户确认本次使用的 GitHub 账户，禁止未经确认直接推送：

1. 在仓库目录下执行 `git config credential.https://github.com.username` 读取实际生效的账户（目录分流规则：`~/codes/**` → xiaoshuai1024，其他 → MaXiaofei）；
2. 在对话中列出：仓库名、要推送的分支、remote 地址、将使用的账户名，请用户确认；
3. 用户明确确认后才可执行 push；换账户等异议按用户指示处理后再推。

## 自动化测试约定（新功能必读）

**按改动范围测试：只跑本次改动了的项目，不要求四件套全上，节省 token 与时间。**

- 改了 `menu-api` → 跑相关后端单测（`./mvnw test -Dtest=...`）；涉及生产 E2E 时再按下方说明跑
- 改了 `menu-mini` → 跑 `menu-mini/test/` 相关单测（必要时 `e2e/`）
- 改了 `menu-admin` → 跑对应 vitest
- 改了 `menu-flutter` → 跑 `test/` 或 `integration_test/`
- 未改动的项目不补测试、不跑测试；改表结构时仍要同步检查 `e2e-seed.sql` 列名（V55 曾因漏改致 E2E 全挂）

**后端生产 E2E（预发已下线，端到端连生产）**：`menu-api/src/test/java/com/gudu/xsd/prode2e/` 下的 `GuduProdE2E*Test`。新接口加进 `GuduProdE2ECoverage*Test`（长尾）或 `GuduProdE2EFlowTest`（核心链路），会调大模型的放 `GuduProdE2EAiTest`；自建数据一律用 `E2E_PROD_` 前缀，类结束按前缀精确清理，改全站配置必须先快照、`finally` 还原。跑法（**务必在生产备份之后**，连接信息见 `menu-api/docs/prod-e2e.md`）：`GUDU_PROD_E2E=1 ./mvnw test -Pprod-e2e -Dtest='GuduProdE2E*'`；缺环境变量或 profile 时全部跳过；AI 用例另需 `GUDU_PROD_E2E_AI=1`；中途失败用 `GUDU_PROD_E2E=1 GUDU_PROD_E2E_CLEANUP=1 ./mvnw test -Pprod-e2e -Dtest=GuduProdE2ECleanupTest` 兜底清理。

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
