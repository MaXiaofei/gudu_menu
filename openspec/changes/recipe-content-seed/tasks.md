# Implementation Tasks: 菜谱内容填充

**Change ID:** `recipe-content-seed`

## 0. 前置
- [x] 0.1 staging 补登记迁移：staging 上 V63 登记表此前**从未创建**；V64–V67 表结构逐项核对已生效后，按 deploy.sh「登记表为空 → 一次性登记」语义登记 V01–V67（70 个文件，checksum 与仓库一致；`scripts/seed/register-migrations.mjs`，重复执行新登记 0）。V68/V69 由后续部署/另一 change 登记
- [x] 0.2 盘点（执行前）：staging 已有下厨房菜 308 道（id 1759–2066），被 menu_dish/favorite/cooking_record 引用 0；dish_ingredient 717 行（关联率约 32%）；服务器 `staging-images/dish/` 1892 张原图（71MB，平铺）
- [x] 0.3 根 `.env` 追加 `SEED_IMAGE_BASE`、`STAGING_DEMO_USER`、`STAGING_DEMO_PASSWORD`（随机生成）；`.env.example` 加占位

## 1. 种子脚本
- [x] 1.1 `scripts/seed/recipes.mjs`：source_url 自然键 upsert，dish.id 不变；`--dry-run`、`--limit`、`--only`
- [x] 1.2 环境守卫：只接受 `--env=staging|local`；staging 远端端口必须 13306、凭据须与生产不同（单测覆盖）
- [x] 1.3 凭据只读根 `.env`，不 export、不打印；SSH 密码经 `sshpass -e` 环境变量传入
- [x] 1.4 用量解析器（分数/区间/中文数字/「个半」/适量）+ 单测 `parse.test.mjs`（9 项全绿）
- [x] 1.5 难度/时长推断（步骤时间词归 prep/cook、步骤数、难词、用料数）+ 单测

## 2. 映射
- [x] 2.1 `mappings/category.json`：29 个分类映射到现有 tag；37 个泛词（红烧、下午茶、电饭煲…）丢弃并出现在报告
- [x] 2.2 `mappings/ingredient-alias.json`（238 条别名 + 组合拆分）+ `extra-ingredients.json`（补 20 样缺失基础食材：姜、香叶、桂皮、低筋面粉…）；用料行关联率 **92.5%**（2189/2367，不计清水与小标题行），≥80% 关联的菜 269/308
  - 说明：补建的是 `ingredient` 行（无营养数据），不是 sys_dict 词条
- [x] 2.3 不新建字典词条：执行前后 sys_dict 行数不变（165）

## 3. 图片
- [x] 3.1 `images.py`：original（封面 1080/步骤 720）+ thumbnail（360/240）两级；源图已够小时保留原文件
- [x] 3.2 `sync-images.mjs`：rsync 增量到 `staging-images/dish/{original,thumbnail}/`（各 1893 个；二次同步传输 0）
- [x] 3.3 抽检随机 20 道菜：封面 + 步骤图原图/缩略图 80 个 URL 全部 200

## 4. 执行与收尾
- [x] 4.1 staging dry-run 报告合理后正式执行：新增 0、更新 308（封面/步骤图改指新目录、补用料关联与推断时长）
- [ ] 4.2 向量重建 **未完成**：`POST /dish/vector/rebuild` 触发后 staging Ollama embedding 反复崩溃、宿主机内存打满（7.3/7.7GB、无 swap、负载 105，与生产同机），已重启 menu-api-staging 中止。
  - 现状：pgvector 295 条，其中 1507–1757 指向已不存在的旧菜 id（本 change 之前即存在），当前 309 道菜仅约 40 道有向量；本次重建额外丢失 6 道
  - 待办：清理陈旧向量 + 在内存有余量时分批限速重建（需改 DishVectorService，另立任务）
- [x] 4.3 幂等验证：连续执行两次，第二次 新增 0 / 更新 0 / 未变 308，dish id 集合哈希一致

## 5. 演示家庭
- [x] 5.1 `demo-family.mjs`：账号 `demo` + 成员 林林(高血脂)/妈妈(高血压)/朵朵(宝宝辅食)，头像上传 `staging-images/avatar/`；余量 29 样（鲈鱼明天、菠菜/北豆腐 2 天到期）；食集 5 个（今晚的饭 / 周六 · 爸妈来吃饭 / 朵朵一周辅食 + 2 个已完成）；做饭记录 25 条（近 7 天 11 条）；收藏 6；通知 3
- [x] 5.2 可重复执行（成员按 phone upsert，id 不变，动态数据删后重灌）；`--clean` 一键清理
- [x] 5.3 `e2e-seed.sql`：
  - 动态表清理保留演示成员（phone LIKE 'demo%'）名下的食集/采购单/做饭记录/收藏/通知
  - 修正番茄/鸡蛋自愈块：旧写法每个 @Test 都按名删除**全库**菜谱的番茄/鸡蛋关联（实测导入后被清到只剩 2 行）；改为漂移行改名让位、引用迁到固定 id
  - 全量 GuduE2E：30/38 通过（基线 28/36）；8 个失败均为既有数据漂移（成员 1 名为「管理员」、单位字典「g/个」id 不符、测试 Ollama 地址不可达），与本改动无关；跑完后演示数据与 86 行番茄/鸡蛋关联均保留

## 6. 合规
- [x] 6.1 production-ops-baseline 8A：生产公共库 `source_name='下厨房'` 必须为 0、生产图片目录无 `xc_*`
- [x] 6.2 `scripts/seed/README.md` 写明来源与仅限测试环境

## Quality Gate
- [x] 脚本单测全绿；幂等验证通过；staging 图片抽检通过
- [ ] 各端打开演示账号验收：**staging 当前部署的 menu-api 是旧版本**（`/recommend/home`、`/cookbook/recent` 404，`/pantry/expiring` 仍读旧批次表），需部署 feature-gaps-closure 后端后验收；数据层已核对（3 天内到期 4 样、全部菜有封面、演示成员近 7 天做饭 11 条）
