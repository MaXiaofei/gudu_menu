# 生产端到端测试（prod-e2e）

预发已下线，原来连预发、每用例前清空动态表的端到端测试（`GuduE2E*`）已删除。现在的端到端测试连**生产**，类名 `GuduProdE2E*Test`，位于 `src/test/java/com/gudu/xsd/prode2e/`。

测试在本机起 Spring 应用（RANDOM_PORT 真 HTTP），经 SSH 隧道直连生产 MySQL / Redis；用例自己创建数据（账号、家庭、菜谱……），用完按前缀精确清理，**不依赖也不改动任何已有业务数据**。

## 护栏（为什么不会误跑、不会误伤）

| 护栏 | 做法 |
| --- | --- |
| 默认不运行 | 必须**同时**设置环境变量 `GUDU_PROD_E2E=1` 和 Maven profile `-Pprod-e2e`；缺任何一个，所有 `GuduProdE2E*` 类整体被跳过（不启动 Spring、不连库）。普通 `./mvnw test`、CI 不会误跑 |
| 只走隧道 | `DB_HOST` / `REDIS_HOST` 若设置了非回环地址，测试直接跳过；只允许 127.0.0.1 |
| 数据前缀 | 所有自建数据名称 / 昵称 / 标题 / openid / 变更原因以 `E2E_PROD_` 开头；账号手机号用 1390000xxxx（每次运行随机分配，不固定） |
| 精确清理 | 每个测试类 `@AfterAll` 自动清理并自检残留（有残留则该类失败）；只按前缀匹配，绝不按范围删除；不执行 DDL；Redis 只删这些账号相关的键（`SCAN` 精确匹配，不 `FLUSH`） |
| 兜底清理入口 | `GuduProdE2ECleanupTest`（见下） |
| 全站配置还原 | 改功能开关 `sys_switch`、AI 额度 `ai_guard_config`、模型网关路由等之前先快照，`finally` 里先用 API 改回（让运行中的生产实例刷新缓存 / 收到广播），再 `restoreOrFail()` 校验；还原失败测试失败并打印原值；变更日志的操作人就是 `E2E_PROD_admin` 账号，原因以 `E2E_PROD_` 开头，随账号清理 |
| 不跑定时任务 | 测试进程摘掉了 `@Scheduled` 处理器（全站临期通知、保存期限清理、计数回刷等一律不跑）；保存期限清理 `retention.enabled=false` |
| 不写向量库 / 微信 / 真实模型 | 向量服务为空实现；微信为桩；大模型 provider 为 mock（不配模型密钥就不会真调） |
| 大模型用例单独开关 | 所有会调用大模型的用例集中在 `GuduProdE2EAiTest`，另需 `GUDU_PROD_E2E_AI=1` |

唯一一处提权：夹具里建一个昵称 `E2E_PROD_admin` 的账号并把它的 `platform_role` 置为 `SUPER_ADMIN`（`WHERE id=自己刚建的id AND 昵称带前缀`），用例结束随账号一起删除。

## 跑法

1. 先在**生产备份之后**开隧道（本机端口 → 生产 127.0.0.1:3306 / 6379），例如：
   ```bash
   ssh -N -L 3306:127.0.0.1:3306 -L 6379:127.0.0.1:6379 <生产机>   # 具体主机见仓库根 .env
   ```
2. 连接信息只走环境变量（密码从仓库根 `.env` 取，**不要写进代码或提交**）：
   ```bash
   export DB_HOST=127.0.0.1 DB_PORT=3306 DB_USER=root DB_PASSWORD=...
   export REDIS_HOST=127.0.0.1 REDIS_PORT=6379 REDIS_PASSWORD=...
   # 字段加密密钥必须与生产一致（否则手机号盲索引对不上；缺失则应用启动失败）
   export GUDU_FIELD_CRYPTO_KEYS=... GUDU_FIELD_CRYPTO_HMAC_KEY=... GUDU_FIELD_CRYPTO_ACTIVE=1
   ```
3. 跑非 AI 部分：
   ```bash
   cd menu-api
   GUDU_PROD_E2E=1 ./mvnw test -Pprod-e2e -Dtest='GuduProdE2E*'
   ```
   （不设 `GUDU_PROD_E2E_AI` 时 `GuduProdE2EAiTest` 自动跳过；`GuduProdE2ECleanupTest` 也只在设了 `GUDU_PROD_E2E_CLEANUP=1` 时才跑。）
4. 跑 AI 部分（会改 AI 相关功能开关并还原；默认 provider 为 mock，不配 `DEEPSEEK_API_KEY` / `GLM_API_KEY` 不会产生真实调用与费用；配了才真调，调用次数已压到最少）：
   ```bash
   GUDU_PROD_E2E=1 GUDU_PROD_E2E_AI=1 ./mvnw test -Pprod-e2e -Dtest=GuduProdE2EAiTest
   ```
5. 确认「未设环境变量时全部跳过」：`./mvnw test -Dtest='GuduProdE2E*'` 应显示全部 Skipped。

## 中途失败 / 进程被杀后的兜底清理

```bash
GUDU_PROD_E2E=1 GUDU_PROD_E2E_CLEANUP=1 ./mvnw test -Pprod-e2e -Dtest=GuduProdE2ECleanupTest
```

清理规则（`ProdE2ECleaner`）：
- 账号：昵称或微信 openid 以 `E2E_PROD_` 开头；被并入这些账号的微信账号；本机台账 `menu-api/target/prod-e2e-ledger.txt` 里记过的账号 id（注销用例会把昵称匿名化，靠台账认出）；
- 家庭：主理人是这些账号或名称带前缀，且成员全是 E2E 账号（混入真实用户的家庭**不删**，只在输出里提示人工确认）；
- 档案、菜谱、食材、字典、营养指标、帖子、Agent 会话、网关供应商等：名称 / 标题带前缀，或归属于上述家庭 / 账号 / 档案；
- 个人归属列按「E2E 账号 id ∪ E2E 档案 id」匹配，两类 id 在生产里与存量真实账号 / 档案 id 不会重叠（账号自增起点已抬到一千万以上），所以不会误伤；
- 清理结果会打印每张表删除的行数；清理后自检仍有 `E2E_PROD_` 残留则失败。

全站配置若在中途失败时没来得及还原，失败信息里会打印该表原值（`sys_switch` / `ai_guard_config` / 网关路由），按原值人工改回即可。生产实例的开关缓存 15 秒，网关路由经广播刷新。

## 用例清单

| 类 | 内容 |
| --- | --- |
| `GuduProdE2EFlowTest` | 核心链路：登录、采购、通知、饮食营养、权限、食集、成员、字典、做菜扣库存、单菜直做、家庭主链路、账号合并、注销 |
| `GuduProdE2ECoverageBasicTest` | 长尾接口 A–L：食材 / 菜谱 / 草稿 CRUD、食集备菜、采购、库存档位、评价、收藏、食记、通知、成员 |
| `GuduProdE2ECoverageMoreTest` | 长尾接口 N–W：字典与指标、权限封堵、一起吃、上传、保质期、搜索、智荐、资料、反馈、仪表盘 |
| `GuduProdE2ESafetyTest` | 合规与运营：内容安全、厨房圈 / AI 紧急关停、AI 额度、单独同意与加密、数据导出、协议上报、封禁、注销处理、违规复核 |
| `GuduProdE2ECommunityTest` | 厨房圈发布互动与治理申诉 |
| `GuduProdE2EGatewayTest` | 模型网关：供应商、路由、用量、权限 |
| `GuduProdE2ETenantIsolationTest` | 多家庭越权隔离 |
| `GuduProdE2EDishOfflineTest` | 公共菜谱下架 / 重新上架：搜索隐藏、详情仍可读带标记、不能复制、操作日志 |
| `GuduProdE2EAiTest` | 会调用大模型的用例（Agent、拍照识别、一周餐程、AI 估算 / 补全 / 推荐），需 `GUDU_PROD_E2E_AI=1` |
| `GuduProdE2ECleanupTest` | 兜底清理入口 |

## 写新用例的约定

- 继承 `ProdE2EBase`；所有自建数据名称用 `P + "xxx"`（`P = "E2E_PROD_"`）；账号用 `createAccount` / `registerAndLogin` / `wxLogin`（自动记台账）；手机号用 `phoneFor(key)`。
- 用例之间不得互相依赖，也不要假设「库是空的」；统计类断言用前后差值。
- 不得写非自建 id 的数据；凡改全站配置必须 `snapshot(...)` + `finally` 还原；会调用大模型的放进 `GuduProdE2EAiTest`。
