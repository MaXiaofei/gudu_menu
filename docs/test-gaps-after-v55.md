# V55 端到端验证发现的测试缺口

> 背景：V55「食材库解绑单位」改造后做全链路端到端验证（后端 E2E + APP 模拟器 + admin）过程中暴露的测试覆盖缺口。回头按优先级补上。

## P0 — 直接影响回归可靠性

### 1. e2e-seed.sql 与 schema 迁移无一致性校验
**现象**：V55 删了 `ingredient.unit_id/price` 列，但 `e2e-seed.sql` 仍 INSERT 这两列，导致 13 个 E2E 测试全挂（ScriptStatementFailed）。直到连上 staging DB 跑 E2E 才发现。
**缺口**：没有"迁移改表结构时自动检查种子数据一致性"的机制。
**建议补**：
- 加一个轻量校验：CI 部署后或 PR 检查里，对 `e2e-seed.sql` 做 `EXPLAIN`/dry-run（在 H2/临时库），列不匹配即失败。
- 或在 `V*__*.sql` 改表结构时，维护一个 checklist 提醒同步 `e2e-seed.sql`（CONTRIBUTING 或迁移规范）。

### 2. admin Web UI 测试覆盖（登录 bug 误判已澄清）
**现象（已澄清）**：2026-08-11 端到端验证时，staging admin 登录按钮点击不跳转，当时判断为前端 Vue 路由/token 问题。**后经 curl 诊断确认是误判**——当时正逢 CI 部署 menu-api 的 502 窗口（API 短暂不可用），admin 登录本身正常：API 恢复后 `POST /auth/login` → `GET /auth/me` → 业务接口链路全通。
**缺口**：`menu-admin` 仍没有 `@vue/test-utils` 组件测试，也没有 Playwright E2E（11 页零 UI 测试覆盖）。admin 后端认证链路已被 `GuduE2EFlowTest` 覆盖（loginAdmin + 调各接口），但前端组件/路由/交互无自动化测试。
**建议补**（按需，非紧急——登录 bug 既已澄清，此项降级）：
- 最低限：加 admin 登录 Playwright smoke（登录→跳转 home→断言 URL/token），防认证链路回归。
- 进阶：食材/菜谱/采购页核心 CRUD 加 `@vue/test-utils` 组件测试。

## P1 — V55 新增逻辑的边界覆盖

### 3. CookService / MenuPrepService 的 `formatUsageText` 缺直接单测
**现象**：V55 备菜/做菜确认改用量原文，新增 `formatUsageText(dishName, UsageText)` 拼接逻辑（"番茄炒蛋 2个 ×3"）。目前只在 `CookServiceTest`/`MenuPrepServiceTest` 里间接覆盖一个场景。
**缺口**：`formatUsageText` 的边界没单测：份数 >1 时 "×N" 拼接、`unitName` 为 null（只显数字）、`amount` 为 null（返回 null 被过滤）、dishName 为 null（"菜#id"兜底）。
**建议补**：把 `formatUsageText` 抽成静态纯函数（或独立 helper），加 4-5 个边界单测。

### 4. ShoppingService.generate 落库不写 referenceGrams 未验证
**现象**：V55 后 `generate` 不再写 `referenceGrams`（列保留停用）。`ShoppingServiceTest` 有 `fromPrep` 的 referenceGrams 断言，但 `generate_menu来源`/`generate_dish来源` 没断言"referenceGrams 为 null"。
**建议补**：在现有 generate 测试里加 `assertThat(item.getReferenceGrams()).isNull()` + `totalAmount` 兜底 0 的断言。

### 5. PrepAggregator / NeedAggregator 对 null amount/unitId 边界
**现象**：V55 改聚合读 amount+unitName 后，`MenuPrepService` 出现 NPE（`Map.of().get(null)`），靠 HashMap 兜底修复。说明 null 边界没覆盖。
**缺口**：`PrepAggregatorTest` 没测 amount=null 的行；`NeedAggregatorTest` 没测 unitName=null。
**建议补**：两个聚合测试各加一个"amount/unitId 为 null 不抛 NPE 且正确跳过/兜底"的用例。

## P2 — 防回归的反向断言

### 6. E2E 缺反向断言：已删字段不再返回
**现象**：V55 删了 `ingredient.unitName/unitGramCount/defaultGramSet/stockUnitName`、`MenuSummary.totalPrice`、`dish.price` 等。E2E 改了正向断言（营养/候选数），但没"反向断言"确认这些字段真的从 JSON 消失。
**缺口**：后端如果误回滚或残留字段，E2E 不会报警。
**建议补**：在 `GuduE2EFlowTest` 的食材/菜单/采购场景里，加 `jsonNode.has("unitName")` 为 false 之类的反向断言（挑 2-3 个关键字段）。

### 7. Flutter ShoppingItemVO.fromJson 没测"忽略 legacy referenceGrams"
**现象**：后端 `shopping_item` 表的 `referenceGrams` 列保留（存量数据/legacy），JSON 可能仍返回该字段（null 或旧值）。V55 前端删了 `referenceGrams` 字段解析，但没测试验证"JSON 含该字段时被正确忽略"。
**建议补**：`shopping_service_test.dart` 加一个 fromJson 用例，输入含 `referenceGrams: 500`，断言 `ShoppingItemVO` 不报错且 amountText 不显示"约 500g"。

### 8. Flutter PrepItem.copyWithStatus 保留 usageTexts 未测
**现象**：V55 把 `totalGrams` 改 `usageTexts` 后，`copyWithStatus`（备菜状态切换时复制）也改了字段。目前 `prep.dart` 没有针对 `copyWithStatus` 保留 `usageTexts` 的单测。
**建议补**：加一个 model 单测：构造 PrepItem → copyWithStatus(ready) → 断言 usageTexts/dishNames 等字段原样保留，只 status 变。

## P3 — 环境与工具链（已补）

### 9. APP instrumented test（已补 ✅）
**原现象**：整个 `menu-flutter` 没有 integration_test（instrumented test），启动/导航流程无自动化覆盖。
**已补（2026-08-12）**：加 `integration_test/smoke_test.dart` + `pubspec.yaml` 加 `integration_test` SDK 依赖。smoke 测试启动完整 APP（`app.main()` 含 ApiClient/AuthStore/ThemeController 初始化）→ 等待未登录跳登录页 → 断言登录页标题/输入框/登录按钮可见。模拟器实测通过（`All tests passed!`）。
**后续可扩展**：登录→进首页→点菜谱详情的 happy path；但 smoke 已能捕获入口崩溃/路由错配/持久化异常等启动级回归。

### 10. 本地连 staging DB 的隧道 leftover bug（已修 ✅）
**原现象**：`scripts/db_tunnel.py` 的 HTTP CONNECT 隧道，`recv` 读完响应头后可能多读应用层数据（MySQL greeting），未回传客户端，致 greeting 丢失、pymysql 卡死在握手。本地跑 E2E 走不通，最终靠 SSH 隧道绕过。
**已修（2026-08-12）**：`db_tunnel.py` 与 `run_migrations.py` 的 `make_tunnel` 现在返回 `(socket, leftover_bytes)`，pump/连接前先 `conn.sendall(leftover)` 把响应头后多读的应用层数据回传客户端。Python 语法校验通过。注意：若代理出口 IP 仍被 DB 安全组拦（独立于 leftover），需走 SSH 隧道（`.env` 凭证 + `ssh -L`）。

---

## 全部测试缺口处理状态（2026-08-12 更新）

| 项 | 级别 | 状态 |
|---|---|---|
| #1 e2e-seed.sql 与 schema 一致性校验 | P0 | ✅ 加 `.github/workflows/e2e.yml`（CI 手动跑 E2E，覆盖 seed 一致性 + 回归） |
| #2 admin Web UI 测试 | P0 | ✅ 登录 bug 澄清为误判（CI 502 窗口）；Vue 组件测试降级按需补 |
| #3 formatUsageText 边界 | P1 | ✅ 抽 `UsageTextFormatter` + 9 单测 |
| #4 generate 不写 referenceGrams | P1 | ✅ ShoppingServiceTest 加断言 |
| #5 聚合器 null 边界 | P1 | ✅ MenuPrepServiceTest 加 null unitId / ×N |
| #6 E2E 反向断言 | P2 | ✅ GuduE2EFlowTest 加已删字段不返回 |
| #7 ShoppingItemVO 忽略 legacy | P2 | ✅ Flutter shopping_service_test 加用例 |
| #8 PrepItem.copyWithStatus | P2 | ✅ 新建 prep_test.dart |
| #9 APP integration test | P3 | ✅ integration_test/smoke_test.dart |
| #10 DB 隧道 leftover | P3 | ✅ 修 db_tunnel.py / run_migrations.py |

---

## 优先级建议
- **立刻补**（P0）：#1 防止下次迁移又踩种子数据坑；#2 admin 登录 E2E（解锁 admin 回归）
- **本次改造收尾补**（P1）：#3 #4 #5 是 V55 新逻辑的边界，趁热补成本低
- **后续版本**（P2/P3）：反向断言、model 测试、APP integration test、隧道修复
