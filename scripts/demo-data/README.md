# 演示数据造数脚本

把一个已有家庭做成「看起来用了一个月」（2026-09-27 ~ 2026-10-27）的样子：我（妻子，OWNER）、老公（加入我们家，MEMBER）、
另一家的朋友小林（独立账号 + 独立家庭「小林家」，只能被邀请凭证访问我们的聚餐，不可登录）。

| 文件 | 作用 |
|---|---|
| `gen_demo_seed.py` | 生成器：读参考数据 + 账号参数，输出 `seed_demo.sql`（固定种子，可复现） |
| `demo_content.py` | 剧本：每顿饭、点评文案、采购行程、库存等（纯数据，改文案/日子改这里） |
| `cleanup_demo_seed.sql` | 清除：按登记表逆序删除所有演示行并还原被改过的已有行 |
| `test_demo_seed.py` | 对生成 SQL 的静态自检（不连数据库） |

## 用法

```bash
cd scripts/demo-data
# 1) 生成（健康档案加密需要 cryptography 和与线上一致的密钥；不设密钥则健康档案与单独同意记录一律留空）
export GUDU_FIELD_CRYPTO_KEYS='1:<base64 32字节>'     # 与线上 GUDU_FIELD_CRYPTO_KEYS 相同；GUDU_FIELD_CRYPTO_ACTIVE 默认 1
python3 gen_demo_seed.py --ref-dir <含 ref-dish.tsv / ref-ingredient.tsv / ref-dish_ingredient.tsv 的目录> \
    --me-account-id <ME_ACCOUNT_ID> --me-family-id <ME_FAMILY_ID> --me-profile-id <ME_PROFILE_ID> \
    --husband-account-id <HUSBAND_ACCOUNT_ID> --out seed_demo.sql
# 可选：--no-health 不写健康档案；--no-backdate 不回溯已有账号/家庭/档案的创建时间

# 2) 造数（必须用 mysql 命令行客户端，文件含 DELIMITER；单事务，任何校验/插入失败整体回滚）
mysql --default-character-set=utf8mb4 --max_allowed_packet=64M <库名> < seed_demo.sql

# 3) 清除（可重复执行）
mysql --default-character-set=utf8mb4 <库名> < cleanup_demo_seed.sql
```

- 已造过（登记表非空）再执行 seed 会 SIGNAL 拒绝，须先清除。
- seed 开头校验：两个账号 ACTIVE、家庭 ACTIVE、`ME_ACCOUNT_ID` 是该家庭 OWNER、`ME_PROFILE_ID` 是她在该家庭的档案、
  老公不在该家庭、公共菜谱/食材/点评维度/采购单位/菜单类型字典齐全、我们家没有库存与收藏（有冲突就拒绝，避免唯一键撞车）。
- 输出末尾打印各表插入行数。

## 登记与清除

- `demo_seed_registry(seq, table_name, row_id, created_at)`：**每插入一行登记一条**，所有表都是单列自增主键 `id`，没有联合主键表。
- `demo_seed_restore`：对**已有行**的 UPDATE 的原值（清除时仅当当前值仍是脚本写入的值才还原）：
  - 老公账号 `last_family_id` → 我们家（让他登录后默认进入我们家；清除时还原成他自己那个空家庭）；
  - 回溯创建时间（仅当现有值晚于目标值才改）：我 / 老公的 `account.created_at`、我们家 `family.created_at`、我的 `family_membership.joined_at` 与 `member.create_time`，
    让「父记录早于子记录」成立。不想动用 `--no-backdate`。
- 清除后：老公的账号本身和他自建的家庭不动；朋友账号/家庭/档案、老公在我们家的 membership 与档案全部删除；两张登记表被 DROP。
  清除脚本末尾有自检查询（登记过的行是否残留，应无结果行）。AUTO_INCREMENT 计数不回退（无影响）。

## 造了什么（表名以代码实际为准）

家庭：`account`(仅朋友) `family` `family_membership` `member`；单独同意 `consent_record`（仅 sensitive_health，早于健康档案写入）；
食集：`menu` `menu_dish` `menu_prep_status` `menu_cook_usage` `menu_activity` `menu_invite` `menu_join`（含 10 月 10 日周末聚餐：邀请口令/token、小林通过邀请加入并点菜）；
做饭：`cooking_record`（食记由它生成，整集做 + 3 条单菜直做）；`favorite` `review` `review_score`；
采购：`shopping_list` `shopping_item`（含勾选/未勾选）；库存：`ingredient_stock` `stock_log`（采购入库→做饭消耗→过期扔掉可对上）；
饮食记录：`daily_log` `daily_log_item`；一周餐程：`meal_plan_draft` `meal_plan_slot`（10.19~10.25 已应用，生成对应食集，排了没做的两天保持进行中）；
通知：`notification`（`expiry` 临期、`shopping` 采购清单，收件人按 V91 拆列：档案级写 `recipient_profile_id`）。

**不造**：AI 对话/生成、厨房圈、意见反馈、内容安全、操作日志；`pantry` / `pantry_change_log`（V42 起已冻结不再读写）、
`menu_template`、`dish_draft`/`dish_history`（自建菜谱）、`user_preference`、旧 `meal_plan`/`meal_plan_item`（无代码使用）。
库里没有 `foodlog` 表：食记 = `cooking_record`。

## 数据口径要点

- 所有时间在区间内，最晚 2026-10-27 07:40（「今天」早晨），均带分秒、不整点；工作日晚饭为主，周末早午餐 + 大餐，10/1~10/4 回老家无做饭，
  10/5~10/7 假期在家，另有几天点外卖/没做饭（只有饮食记录备注）。
- id 规则：`cooking_record/review/favorite/daily_log/meal_plan_draft/menu_dish`（家庭内点菜）用**成员档案 id**；
  聚餐（`menu_join` / `menu_invite.created_by` / 朋友点菜的 `menu_dish.added_by_member_id` / `menu_activity.member_id`）用**账号 id**，与 `TogetherService` 一致。
- 朋友家的数据 `family_id` 全是她家，我们家的全是 `@ME_FAMILY_ID`；她点的菜写在我们家的食集里（`added_by_member_id` = 她的账号）。
- 邀请凭证有效期 7 天（10-08 创建，10-15 到期），所以现在已失效——这是真实状态；要演示「可加入」请在 App 里重新生成邀请。
- 健康档案：AES-256-GCM，格式 `ENC:v<版本>:base64(12字节IV‖密文‖16字节tag)`，与 `FieldCipher` 完全一致；IV 由 HMAC 确定性派生以便脚本可复现。
  只给老公和小林写（含 `health_consent_account_id`）；**我的档案不动**（已有行，请本人在 App 里填）。`member.phone` 一律不写。
- 单独同意 `doc_version` 取 `ConsentAppService.SENSITIVE_DOC_VERSION`（2026-10-26）。

- 余量模型：采购入库 → 做饭确认「用完了 / 用了一些」（未勾选的食材档位不变）→ 过期扔掉（多数直接删除余量行）；周末/假期大采购会顺手补齐常用的耐放品。
  生成器会打印按 `/pantry/summary` 口径算出的今日 ENOUGH / LOW / OUT / 3 天内到期数（我们家目前 31/9/6，到期 6）。旋钮在 `gen_demo_seed.py` 顶部。
- 用户动作（造食集、采购、评价、记饮食、库存变动）不会落在 00:30~06:30；食集在做饭前一晚 20~23 点、前一天或当天下午 15~18:30（晚饭）/ 当天 8~10:30（早午餐）创建。

## 自检

```bash
# Python（可选装 cryptography 以覆盖加密相关用例）
DEMO_REF_DIR=<ref 目录> DEMO_PROD_SCHEMA=<prod-schema.sql> python3 -m unittest -v test_demo_seed
# Java：密文 fixture 能被 FieldCipher 解出
cd menu-api && ./mvnw -q test -Dtest=DemoSeedCipherFixtureTest
```

fixture 在 `menu-api/src/test/resources/crypto/demo-seed-health-cipher.json`（测试密钥，非线上密钥）；改动加密实现后用
`gen_demo_seed.py --crypto-fixture` 重新生成。
