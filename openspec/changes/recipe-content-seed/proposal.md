# Proposal: 菜谱内容填充（下厨房导入数据 · 仅测试环境）

**Change ID:** `recipe-content-seed`
**Created:** 2026-10-16
**Status:** Draft
**Roadmap:** 与 `prototype-ui-rollout` 并行，先行落地（界面开发需要真实内容）

---

## Problem Statement

- staging 公共菜谱只有早期一次性导入的约 251 道（08-14 跑过 `xiachufang/scraper/import.js`），本地已抓到 308 道详情、1892 张图，未全部入库。
- 现有导入脚本不可重复、不安全：
  - 每次「按 source_name 全删再插」，dish.id 变化，菜单/收藏/食记/点评引用全部孤儿化；
  - SSH 与 MySQL root 密码**明文写在 import.js**；
  - 分类直接新建 66 个泛词（与 V57–V62 字典清洗冲突）；食材只精确同名匹配，覆盖率偏低；分数/区间用量解析错误（「1/2 小匙」→ unit=`/2小匙`）；
  - difficulty / prep_time / cook_time 全空；不触发向量库重建，语义搜索搜不到。
- 新原型（`docs/design/prototype/`）的界面强依赖：菜品封面、步骤图、用料与家庭余量的关联（「家里够 X/Y 样」）、做过次数、食记、成员头像。staging 没有一个「看起来像原型」的完整演示家庭。

## 决策（2026-10-16 用户确认）

- 数据来源：**直接使用下厨房导入数据**（图文版权归原作者）。
  - **只允许进入 staging / 本地开发库**；脚本对生产目标硬拒绝；生产公共库不含 `source_name='下厨房'` 数据。
  - 正式上线前公共库内容须替换为自有或已授权内容（列入 production-ops-baseline 上架检查项）。
- 写入方式：**可重复执行的种子脚本 + 在 staging 执行并登记**。

## Proposed Solution

1. **种子脚本 `scripts/seed/`**（Node，零新增依赖之外只用 mysql2）
   - 源数据：`xiachufang/scraper/data/progress.json` + `images/`（目录保持 git 忽略，不入库）。
   - **upsert 以 `source_url` 为自然键**，dish.id 稳定；子表（dish_step / dish_ingredient / dish_dict）按 dish 局部重建。
   - 凭据只从根 `.env` 读（`STAGING_SSH_*`、`STAGING_MYSQL_ROOT_PASSWORD`），连接沿用 `scripts/db_tunnel.py` 的隧道方式；**目标非 staging/local 时退出**。
   - `--dry-run` 输出 SQL 与统计报告（匹配率、未映射词条），不落库。
2. **映射表（入库，`scripts/seed/mappings/*.json`）**
   - 分类映射：下厨房分类 → 现有 tag/cuisine/category 字典 id；无映射则丢弃，**不新建字典词条**。
   - 食材别名：「香葱→葱」「细盐→食盐」等，提高 `dish_ingredient` 关联率；未匹配用料保留在 note。
   - 用量解析：支持分数、区间（取上限）、「适量/少许」（amount=0）、中文数字；单位走 V54 字典。
3. **字段补全**：按步骤数与关键词推断 difficulty；按步骤文本时间词估 prep/cook_time；无法推断留空。
4. **图片**：压缩到最长边 1080（封面）/ 720（步骤），JPEG q80，另生成 `thumbnail/` 版本；rsync 增量同步到 staging `/root/gudu/staging-images/dish/`；URL 写绝对地址，前缀按环境配置（`SEED_IMAGE_BASE`）。
5. **收尾**：调用 `POST /dish/vector/rebuild`；输出报告（新增/更新/跳过、覆盖率分布）。
6. **演示家庭（staging）**：「小王家的厨房」演示账号，含 4 位成员（含 1 个宝宝档案、头像）、余量（含临期）、2 个食集（进行中 + 周末）、近 30 天食记、收藏，使各端打开即与原型一致。
   - 账号密码写入根 `.env`（`STAGING_DEMO_USER` / `STAGING_DEMO_PASSWORD`），不入库、不写进文档。
   - 数据带 `DEMO` 标记，可一键清理；不与 E2E 前缀数据冲突，`e2e-seed.sql` 不清理演示数据。
7. **成员头像与食材插画**：原型自绘 SVG（`docs/design/prototype/img/{avatar,ing}`）光栅化后作为默认头像 / 食材图标随客户端打包（见 `prototype-ui-rollout`），种子脚本只写头像 URL。

## Out of Scope

- 生产公共库内容建设（需授权内容，另立 change）。
- 继续爬取（`scrape.js` 已因 429 停止，本 change 只用已抓到的数据）。
- 多租户字段（`dish.family_id`）——由 `multi-tenant-family` 落地；公共库数据 `family_id` 保持 NULL，脚本届时自动兼容。

## Risks

| 风险 | 应对 |
|---|---|
| 版权 | 仅 staging；脚本硬拒绝生产；上架检查项 |
| E2E 每次清空 menu/favorite 等表，演示家庭的食集/收藏会被清 | 脚本提供 `--demo-only` 快速重建；后续 E2E 改为只清 E2E 前缀数据（列为任务） |
| 映射不全导致覆盖率低 | dry-run 报告列出 Top 未匹配用料，迭代补别名 |
| staging V64–V67 曾直接执行未登记 `schema_migration_log` | 执行种子前先补登记（任务 0.1） |
