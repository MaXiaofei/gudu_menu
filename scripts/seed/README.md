# scripts/seed · 测试环境内容填充

OpenSpec：`openspec/changes/recipe-content-seed`。

> **数据来源：下厨房（xiachufang.com）抓取的菜谱图文，版权归原作者。只允许进入 staging / 本地开发库。**
> 脚本对生产硬拒绝（`--env` 只接受 `staging|local`；staging 目标端口必须是 13306，且凭据必须与生产不同）。
> 生产公共库必须为 0 条 `source_name='下厨房'`，见 production-ops-baseline 8A。

## 前置

- 源数据：`xiachufang/scraper/data/progress.json` + `xiachufang/scraper/images/`（git 忽略，不入库）
- 凭据：只读根 `.env`（`STAGING_SSH_HOST/USER/PASS`、`STAGING_MYSQL_ROOT_PASSWORD`、`SEED_IMAGE_BASE`、`STAGING_DEMO_USER/PASSWORD`），不打印、不落盘
- 依赖：`cd scripts/seed && npm install`；图片处理需 Python Pillow；头像素材先跑 `scripts/design/gen-assets.mjs`

## 用法（按顺序）

```bash
cd scripts/seed
node --test                                         # 解析器 / 环境守卫单测
node register-migrations.mjs --env=staging --upto=V67   # 仅当迁移被手工执行过、登记表缺记录时用
python3 images.py                                   # 压缩 + 缩略图 → .cache/images/dish/{original,thumbnail}
node sync-images.mjs --env=staging                  # rsync 增量 → /root/gudu/staging-images/dish/
node recipes.mjs --env=staging --dry-run            # 报告：关联率、未映射分类、Top 未匹配用料
node recipes.mjs --env=staging                      # upsert（source_url 自然键，dish.id 不变）
node demo-family.mjs --env=staging                  # 演示家庭「小王家的厨房」，可重复执行
node demo-family.mjs --env=staging --clean          # 只清理演示数据
node community.mjs --env=staging                    # 厨房圈演示内容（依赖 demo-family 与 V71），可重复执行
node community.mjs --env=staging --clean            # 只清理厨房圈演示内容
```

### 演示数据的日期

- `demo-family.mjs` 全部按**运行机器的本地日期**计算：食集「周六 · 爸妈来吃饭」「朵朵一周辅食」落在运行日起最近的周六 / 周日（客户端按 `menu.create_time` 显示用餐日），余量保质期「明天 / 2 天后」同理。日期过期时重跑即可（成员 id 不变）。
- 不要用 `toISOString()` 取日期：晚上 UTC 已跨日，会把「明天到期」写成后天。

### 厨房圈演示内容（community.mjs）

- 作者 7 位：member 表里 `phone` 前缀 `cm_` 的社区账号（无密码、无健康档案），头像 PNG 在 `assets/community-avatar/`（由原型 `img/avatar/c-*.svg` 光栅化），上传到 `staging-images/avatar/`。`MemberService.household` 排除 `cm_` 账号，不会算进「家里几口人」。
- 帖子 24 条：图片取 staging 已导入菜谱的封面与步骤图，关联对应菜谱；本周（周一起）15 条带「一周不重样」并打卡，形成进度与排行；演示账号 `demo` 有 1 条自己的帖子、关注 5 位作者，并有真实的赞 / 收藏 / 照着做 / 评论行。
- 赞 / 收藏 / 照着做的**计数**含模拟的站外热度基数（让 Feed 像在运营），`liked` / `collected` 只看真实行。
- 关注流是写扩散：脚本经 SSH 在 `gudu-redis-staging` 容器里重建作者与 demo 的收件箱（`gudu:community:inbox:<userId>`）。

报告写在 `.cache/report-*.json`（git 忽略）。

## 规则

| 项 | 规则 |
|---|---|
| 幂等 | `dish` 按 `source_url` upsert；`dish_step`/`dish_ingredient` 按菜重建；`dish_dict` 只 `INSERT IGNORE`，不删已有关联 |
| 分类 | `mappings/category.json` 映射到现有 tag/cuisine；未映射丢弃，**不新建字典词条** |
| 食材 | 原名 → 别名（`mappings/ingredient-alias.json`）→ 去前后缀 → 包含匹配；「x酱/x油」不退化成原料；清水类、小标题行不计入 |
| 补食材 | 只补 `mappings/extra-ingredients.json` 列出的 20 样基础食材（姜、香叶、低筋面粉…，无营养数据） |
| 用量 | 分数、区间（取上限）、中文数字、「个半」、适量/少许（amount=0）；质量单位换算 grams |
| 图片 | URL = `SEED_IMAGE_BASE/dish/original/xc_<id>.jpg`，缩略图同名在 `/thumbnail/`（与客户端替换规则一致） |
| 演示家庭 | 成员 `phone` 以 `STAGING_DEMO_USER`（demo）开头；食集挂在演示成员名下；`e2e-seed.sql` 不清理这些行 |

## 注意

- 写库后**不会**自动重建语义搜索向量。`POST /dish/vector/rebuild` 会逐道先删后写，embedding 失败的菜会丢向量；
  2026-10-16 在 staging 执行时服务器内存打满（无 swap），已中止。重建前先确认内存余量，或改为分批限速。
- 余量 `ingredient_stock` 是全局表（单家庭模型），E2E 会改番茄/鸡蛋档位；需要时重跑 `demo-family.mjs` 复原。
