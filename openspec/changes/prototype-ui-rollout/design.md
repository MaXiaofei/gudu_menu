# Design: 按高保真原型改版四端

**Change ID:** `prototype-ui-rollout`

## 1. 画面 → 页面对照（2026-10-16 盘点）

| 原型 | Flutter | 小程序 | 现状 | 本 change 要做的 |
|---|---|---|---|---|
| Login | `login_page.dart` | `login/Login.vue` | 布局不同 | 菜品拼贴头图、品牌图标；协议勾选前不发起任何登录（小程序含静默登录） |
| FamilyOnboard | — | — | 缺 | P2 新建 |
| FamilySwitch | — | — | 缺 | P2 底部弹层 |
| FamilyMembers | `member/member_list_page.dart` | `member/Index.vue` | 部分 | P2：邀请码、角色、档案分组、退出家庭 |
| Recipes | `dish/list_page.dart` | `dish/List.vue` | 部分 | 首推大图卡、84px 封面行、全部/我家的/公共库/收藏、排序 chips、临期提示、做过次数 |
| RecipeDetail | `dish/detail_page.dart` | `dish/Detail.vue` | 布局不同 | HeroGallery（叠返回/分享/收藏）、用料行在家状态 + 加入采购、StepCarousel、底部 加入食集 / 现在就做 |
| CookSheet | `menu/cook_confirm_sheet.dart` | `components/ui-cook-sheet.vue` | 布局不同 | 成品缩略图、人份步进、谁吃了（头像多选）、用料行插画 |
| Smart | `ai/recommend_page.dart` + `smart_home_sections.dart` | `recommend/Index.vue` | 部分 | 今晚首推大图卡（换一道）、快到期横滑、你家常做、应季、最近做过 |
| Pantry | `pantry/list_page.dart` | `pantry/List.vue` | 部分 | 顶部四格统计、分类带计数、先吃这些 + 关联菜、行内三档、采购角标、食材插画 |
| PantryAdd | `pantry/manual_add_page.dart` | `pantry/Add.vue` | 布局不同 | 你家常买插画宫格、批量篮子「放进余量 · N 样」、保质期自动填 + 可清空 |
| Menus | `menu/list_page.dart` | `menu/List.vue` | 部分 | 进行中主卡（拼图封面 + 三条进度）、接下来日期卡、模板横滑、已完成计数 |
| MenuDetail/Prep/Shop | `menu/detail_page.dart`(1586 行) + `shopping/shopping_page.dart` | `menu/Detail.vue`(1365 行) + `shopping/List.vue` | 部分 | 4 Tab（菜/备菜/采购/一起吃）；采购从独立页并入 Tab（独立页保留为全局清单入口）；拆分大文件 ≤500 行 |
| ShopShare | 嵌在 shopping_page | `utils/shareImage.ts` | 部分 | 独立预览：拼图横幅、分组插画、家庭头像 |
| CookLog | `foodlog/*` | `foodlog/*` | 布局不同 | 日历缩略图、今天大图组、余量变化 chip + 撤销 |
| RecipeEdit | `dish/create_page.dart`(2056 行) | `dish/Create.vue` | 布局不同 | 封面大图、参数 chips、你家常用插画标签、可见范围（投稿开关 P3）；拆分 |
| FriendPick | `_TogetherTab` | `together/Pick.vue` | Flutter 缺 | 两列照片卡可勾选、忌口 chips、头像 |
| Me | `profile_page.dart` | `profile/Profile.vue` | 部分 | 渐变头 + 大头像、三格统计、最近食记照片条、彩色图标设置列表 |
| Theme / Feedback | 有 | 有 | 布局不同 | 预览用真图；反馈类型宫格 + 截图 + 报错编号 |
| Privacy | `legal_page` + `about_page` | `Legal.vue` + `About.vue` | 部分 | P1 视觉 + 开关；P3 导出/设备/注销 |
| Notices | — | — | 缺 | P3（P1 先接现有 expiry/shopping 两类） |
| States | `loading_empty.dart`、`error_view.dart` | `ui-state.vue` | 部分 | Skeleton、空态插画、报错编号、OfflineBanner |

后台：Dashboard(部分) / Library(缺) / Nutrition(部分) / Feedback(部分) / Tenants(缺) / Ai(部分) / Ops(缺)。

## 2. 素材管线

- 源：`docs/design/prototype/img/{ing,avatar,extra}/*.svg`（自绘，可商用）。
- `scripts/design/gen-assets.mjs`：Playwright 光栅化为 PNG（@2x/@3x），输出：
  - Flutter `menu-flutter/assets/ing/`、`assets/avatar/`（pubspec 声明；不引入 flutter_svg，避免新增依赖）
  - 小程序 `menu-mini/src/static/ing/`、`static/avatar/`（注意主包 2MB 限制：食材图 96px 单张 < 4KB，超限改分包或 CDN）
  - admin 直接用 SVG（`src/assets/ing/`）
- `--check` 模式供 CI。
- **食材 → 插画映射**：后端新增 `ingredient.icon_key VARCHAR(32) NULL`（V68），回填规则：名称关键词 → 采购品类兜底（蔬菜→cabbage、肉→pork…）；客户端 key 缺失时用品类兜底再退首字。接口在 ingredient / pantry / dish_ingredient / shopping 各 VO 带 `iconKey`。
- **头像**：`member.avatar_url`（V66 已有）为空时按成员档案类型给默认插画头像（宝宝/成人），不再首字。

## 3. 共享组件（三端同名）

| 组件 | Flutter (`lib/widgets/`) | 小程序 (`components/`) | 说明 |
|---|---|---|---|
| DishImage | `dish_image.dart` | `ui-dish-image` | 比例(1:1/4:3/16:9) + 圆角 + 缩略图规则 + 失败回退首字；Flutter 引入 `cached_network_image`（需验证不触发 AGP 冲突，否则自写内存缓存） |
| IngredientIcon | `ingredient_icon.dart` | `ui-ing-icon` | iconKey → 资产；secondary 浅底圆角块；「用完」去色 |
| AvatarStack | `avatar_stack.dart` | `ui-avatar-stack` | 最多 4 个 + 「+N」，白描边叠放 |
| HeroGallery | `hero_gallery.dart` | `ui-hero-gallery` | 满宽轮播 + 页码 + 顶部渐变 + 浮动按钮 |
| StepCarousel | `step_carousel.dart` | `ui-step-carousel` | 横滑步骤卡（图 + STEP n + 文本），无图时纯文本卡 |
| Skeleton | `skeleton.dart` | `ui-skeleton` | 呼吸动画 1.2s；10s 超时转错误态 |
| OfflineBanner | `offline_banner.dart` | `ui-offline-bar` | 连接监听 + 待同步计数 |
| SegmentTabs | `segment_tabs.dart` | `ui-segment` | 食集 4 Tab、菜谱范围切换 |
| StatGrid | `stat_grid.dart` | `ui-stat-grid` | 余量四格、我的三格 |

后台补：`DishThumb`、`IngIcon`、`AvatarStack`、`StatCard`、`HealthCard`。

## 4. 接口调整（P0/P1，向后兼容）

**P0 契约（前后端并行开发按此对齐）：**
- `iconKey: string | null`（后端已按「名称关键词 → 采购品类」兜底，null 时客户端退首字）出现在：`/dish/{id}` 用料项、`/pantry`（列表与分组）项、`/shopping` 清单项、`/menu/{id}/cook-materials` 项、备菜项、`/ingredient` 列表项。规则见 `docs/design/ingredient-icons.json`；素材 key 见 `docs/design/assets.json`。
- `GET /pantry/summary` → `{ enough: number, low: number, out: number, expiring3d: number, byCategory: [{ categoryId, name, count }] }`
- `GET /me/stats` → `{ cookedThisMonth: number, familyDishes: number, savedFromWaste: number }`
- `/dish/search` 列表项新增 `cookedCount: number`、`lastCookedAt: string | null`（ISO 日期时间）、`lastCookedBy: { memberId, name, avatarUrl } | null`
- `/recommend/home` 各菜品项新增 `coverUrl`（与 `coverImage` 同值）

- 推荐接口 `coverImage` 增加 `coverUrl` 同值字段；客户端改读 `coverUrl`，一个版本后删 `coverImage`。
- 余量统计：`GET /pantry/summary` → `{enough, low, out, expiring3d, byCategory[]}`（现由客户端聚合，改为服务端，避免分页导致不准）。
- 「我的」统计：`GET /me/stats` → `{cookedThisMonth, familyDishes, savedFromWaste}`（「本月少扔」= 临期前被做菜用掉的库存条数）。
- 菜谱列表项补 `cookedCount`、`lastCookedAt`、`lastCookedBy`（cookbook-search-revamp 已规划，合并到此处实现）。

## 5. P3 后端新增（DDD 四层，新上下文原生）

| 能力 | 接口（草案） | 表 |
|---|---|---|
| 消息中心扩展 | `GET /notification?type=` 增 invite / feedback / library；`PUT /notification/read-all` | notification.type 扩枚举 |
| 反馈 | `POST /feedback` 增 `type`、`errorId`；后台 `POST /feedback/{id}/reply`（同时发消息）、状态 处理中 | feedback 加列 |
| 账号注销冷静期 | `POST /account/deletion`、`DELETE /account/deletion`（撤回）；定时任务到期匿名化 | account_deletion |
| 数据导出 | `POST /account/export` → 异步生成 JSON/CSV 压缩包，消息通知下载（24h 有效） | export_job |
| 登录设备 | `GET /account/devices`、`DELETE /account/devices/{id}` | auth_session（refresh token 维度） |
| 投稿公共库 | `POST /dish/{id}/submit`；后台 `GET /admin/library?status=`、`POST /admin/library/{id}/approve|reject|offline`；自动检查（敏感词、外链图片、名称相似度） | dish_submission |
| 营养核对 | ingredient 加 `nutrition_status`(AI_ESTIMATED/VERIFIED/MISSING)、`verified_by`、`verified_at` | ingredient 加列 |
| 审计日志 | 后台写操作 AOP 记录；`GET /admin/audit` | audit_log |
| AI 预算 | `GET/PUT /admin/ai/quota`（每人每日、周预算、80% 告警、超额降级到规则推荐）；用量按功能聚合 | ai_quota_config，ai_call_log 加 cost |
| 系统健康 | `GET /admin/ops/health`（API P95/错误率、MySQL、Redis、语义搜索、迁移一致性）、`GET /admin/ops/errors`（按 traceId 前缀聚合） | 读 actuator + ai_call_log + 错误表 |
| 后台家庭 | `GET /admin/families`、冻结/解冻（P2 后） | 依赖 multi-tenant-family |

## 6. 验收方式

- 每页交付附 before/after 截图（Flutter 模拟器、小程序 H5 Playwright、admin Playwright），与原型同尺寸并排；两套皮肤。
- 截图基线进入 design-system-convergence §7 视觉回归。
