# Implementation Tasks: 按高保真原型改版四端

**Change ID:** `prototype-ui-rollout`
每项按四件套交付（功能 + 后端 E2E + Flutter 测试 + 小程序测试），附截图验收。

## P0 基础
- [x] 0.1 `scripts/design/gen-assets.mjs`：SVG → PNG（144px）输出到 Flutter/小程序，admin 用 SVG；App 图标 `brand/logo.png`；`--check`
- [x] 0.2 后端 V68 `ingredient.icon_key` + 回填规则；ingredient/pantry/dish_ingredient/shopping VO 带 `iconKey`；e2e-seed 检查
  - `IngredientIconPolicy`（nutrition/domain，纯函数）与 `docs/design/ingredient-icons.json`、V68 CASE 顺序由单测逐项校验；**后端已含品类兜底**（关键词不中再按采购品类），客户端只需在 null 时退首字
  - 写入：`IngredientService.save/updateById` 自动算（改名/改品类重算）；pantry 手动入库新建食材按名称算
  - 透出：/ingredient、/dish/{id} 用料、/pantry/grouped|item|expiring、/recommend/home.expiring、/cook/dish/{id}/materials、/menu/{id}/cook-materials、备菜项、/shopping 项（手动项按名称推断）
  - staging 已执行并登记 schema_migration_log：356/356 食材有 key；e2e-seed 番茄/鸡蛋带 icon_key
- [ ] 0.3 推荐接口补 `coverUrl`；客户端统一读 `coverUrl`（mini✓ `cardCover()` 优先 coverUrl 回退 coverImage） Flutter✓ DishCard/RecentCooked 优先 coverUrl
  - [x] 后端：`/recommend/home` 菜卡 `coverUrl`=`coverImage`（待客户端切换后删 coverImage）
- [x] 0.4 Flutter 共享组件：DishImage、IngredientIcon、AvatarStack、HeroGallery、StepCarousel、Skeleton、OfflineBanner、SegmentTabs、StatGrid；合并 4 处自写首字占位；widget 测试 × 两皮肤
  - Flutter✓ `lib/widgets/`：dish_image（DishImage/DishCollage/DishCircleStack，失败回退首字）、ingredient_icon（`core/ingredient_icons.dart` 规则副本，单测校验与 docs/design JSON 一致）、member_avatar（MemberAvatar/AvatarStack，默认插画头像）、hero_gallery、step_carousel、skeleton、offline_banner（`core/connectivity.dart`，ApiClient 连接错误置离线）、segment_tabs、stat_grid、tone_badge（覆盖率/到期/语气徽章）、illustration（空态/错误插画）；ErrorView 升级为插画 + 重试 + 报错编号
  - 未引入 cached_network_image（避免新增原生依赖），沿用 Image.network 内存缓存
  - 主题：主按钮 primaryDeep 底、小字默认 body 色；`test/widget/shared_components_test.dart` 两皮肤 20 例
- [x] 0.5 小程序共享组件：ui-dish-image、ui-ing-icon、ui-avatar-stack、ui-hero-gallery、ui-step-carousel、ui-skeleton、ui-offline-bar、ui-segment、ui-stat-grid；unit mini✓
  - 另增 ui-member-avatar（默认插画头像）、ui-dish-row / ui-dish-card / ui-dish-collage / ui-thumb-stack、ui-tab-header、ui-icon（线性图标 PNG，`menu-mini/scripts/gen-icons.cjs` 生成）；ui-state 升级为骨架 / 插画空态 / 错误+报错编号；ui-cook-sheet 按原型重做（人份、食记、插画行）
  - `utils/ingIcon.ts` 解析 iconKey→名称→品类兜底；规则/素材清单副本由单测守护与 docs/design 一致；`request.ts` 记录 X-Trace-Id（报错编号）+ `silent` 探测请求
  - unit：test/components/ui-kit.spec.ts 等 15 例
- [x] 0.6 admin 组件：DishThumb、IngIcon、AvatarStack、StatCard、HealthCard（+ PendingApi「接口待上线」空态；IngIcon 直接读 `docs/design/ingredient-icons.json`；侧栏按原型分组 + 反馈角标；主按钮改 primaryDeep 底）
- [x] 0.7 后端 `GET /pantry/summary`、`GET /me/stats`、菜谱列表项 `cookedCount/lastCookedAt/lastCookedBy`；E2E
  - `/pantry/summary`：三档含「用完」、`expiring3d` 与 `/pantry/expiring?days=3` 同口径（含已过期、不含用完），byCategory 按数量降序、未分类叫「其他」
  - `/me/stats`：cookedThisMonth=本月做菜事件数（同一 eventId 算一顿）；familyDishes=非「下厨房」导入的菜谱数（多租户后改 family_id）；savedFromWaste 依赖 **V69 `stock_log.best_before` 快照**（做菜用掉时记录），上线后开始累计
  - `/dish/search`：cookedCount 沿用「当前就餐成员」口径；lastCookedAt/lastCookedBy 为全家最近一次（多租户后按家庭）
  - 单测 561→586；E2E `W1/W2` + 相关 P/Q/R/S/E1 共 8 例连 staging 通过

## P1 主链路页面（Flutter + 小程序同步）
- [ ] 1.1 菜谱列表 mini✓（首推大图卡、84px 封面行、覆盖率+缺什么、上次谁做的、写菜谱 FAB；「我家的/公共库/收藏」范围待 P2 后端） Flutter✓（首推大图卡、84px 封面行、覆盖率徽章、上次谁做的、写菜谱 FAB、筛选弹层；范围 Tab 待 P2）
- [ ] 1.2 菜谱详情（HeroGallery + 用料在家状态 + StepCarousel） mini✓（缺的料一键加入采购；收藏按钮待收藏接口） Flutter✓（大图轮播=封面+步骤图、用料插画+在家状态+加入采购、横滑步骤卡、底栏 加入食集/现在就做；收藏/分享待接口）
- [ ] 1.3 做完确认 mini✓（「谁吃了」无后端字段，未做） Flutter✓（成品图头、插画行、三态分段；谁吃了/人份待后端字段）
- [ ] 1.4 智荐 mini✓ Flutter✓（问候+家人头像、今晚首推大图卡+换一道、临期胶囊、横滑卡、应季大图、最近做过、消息铃铛）
- [ ] 1.5 我家余量 mini✓（四格统计、先吃这些+关联菜、行内三档；按品类筛选待 grouped 支持 categoryId） Flutter✓（四格统计用 /pantry/summary 降级本地聚合、先吃这些、行内三档、入库 FAB；按品类筛选同 mini 待后端）
- [ ] 1.6 入库 mini✓（你家常买插画宫格；批量篮子「放进余量 · N 样」未做，仍一次一样） Flutter✓（你家常买插画宫格、插画食材头；批量篮子未做）
- [ ] 1.7 食集列表 mini✓（进行中拼图主卡 + 备菜/一起吃进度；模板入口未做） Flutter✓（进行中拼图大卡、日期块+叠放菜图行；进度条与模板未做）
- [ ] 1.8 食集详情 4 Tab（采购并入）+ 备菜；拆分大文件 ≤500 行 mini✓（menu/Detail 1365→516 行，拆 4 个 Tab 组件；采购 Tab 由备菜聚合按家里没有/不够/够分组生成清单） Flutter✓（SegmentTabs 菜/备菜/采购/一起吃；新增采购 Tab；detail_page 1586→拆为 detail_page + detail/ 5 个 part，均 ≤400 行）
- [ ] 1.9 采购清单转图片预览 mini✓（预览卡插画横幅+分组插画；导出图仍为 canvas 文字版；shopping/List 1130→718 行） Flutter✓（分享卡品牌头+按品类分组插画；shopping_page 1073→392 行 + parts/）
- [ ] 1.10 食记 mini✓（食记接口无封面，用「最近做过」按菜名补图） Flutter✓（统计格 + 卡片行 64px 菜图；接口无封面时退首字，兼容 covers 字段）
- [ ] 1.11 写菜谱（拆分 create_page 2056 行） mini✓（Create.vue 918→655 行，拆导入/加用料/步骤；可见范围与投稿开关待 3.7） Flutter✓ 仅拆分（create_page 2056→475 行 + create/ 5 个 part，扩展方法），视觉未改
- [ ] 1.12 一起吃（Flutter 补独立页） mini✓（together/Pick 照片卡+头像+隐私说明） Flutter：一起吃 Tab 换头像；好友点菜页为小程序 H5 场景，App 未补独立页
- [ ] 1.13 我的 mini✓ Flutter✓（渐变头+大头像、/me/stats 三格（404 隐藏）、最近做的照片条、分组彩色图标）
- [ ] 1.14 主题 / 反馈 / 关于 mini✓（主题实时预览；反馈类型宫格 + 报错编号，type/errorId 已随请求发送并写入 clientInfo） Flutter✓（主题预览卡；反馈类型宫格 + type/errorId 字段与 mini 同契约，ApiClient.lastErrorId 记录最近报错编号）
- [ ] 1.15 状态规范落地到全部数据页（骨架/空态插画/错误+报错编号/离线条） mini✓（离线条接入 4 个 Tab 页；离线写操作排队未做） Flutter✓ 部分（骨架/插画空态/错误+编号接入本次改版页面；离线条挂在 MainShell 全局；其余旧页面仍为旧 LoadingView/EmptyView）
- [ ] 1.16 登录页视觉；小程序勾选协议前不做静默登录（合规） mini✓（`utils/consent.ts` 带协议版本；App 启动、silentLogin、401 自愈三处均拦截；单测覆盖） Flutter✓（插画头图 + App 图标、协议提示条；App 无静默登录）
- [ ] 1.17 消息中心（先接现有 expiry/shopping 两类） mini✓（新页 pages/notice/Index；无「全部已读」接口，前端逐条 PUT） Flutter✓（`/notices` 新页 + 智荐铃铛未读点；全部已读为逐条 PUT）

## P1 后台
- [x] 1.20 仪表盘：待办卡（待审核/待核对/待处理反馈）+ 本周最常做菜图卡
  - 已接真实数据：5 张 KPI、7/14/30 天趋势、新反馈（头像）、缺营养数据、最常做的菜（`/dish/search?sort=cooked` 封面图）
  - 待接口：投稿待审核、系统告警显示「待接入」；「最常做」为全量做过次数（非「本周」），平台级本周热度需后端聚合接口
- [x] 1.21 营养：食材插画、统计、被引用菜谱（核对状态见 3.6）——右侧编辑面板 + AI 补全；被引用用 `/dish/search?ingredientIds=`
- [ ] 1.22 反馈：处理中状态、类型/端筛选、截图预览
  - 已完成：列表 + 详情双栏、截图预览、来源端（由 clientInfo 推断）、标记已处理/重新打开、侧栏角标联动
  - 待后端：「处理中」状态、type 字段与类型/端服务端筛选、报错编号、回复用户（3.2）
- [ ] 1.23 AI 用量：按功能拆分、P95、限额触发统计（预算配置见 3.8）
  - 已完成：今天/7/30 天、按功能条形图、成本与 P95（基于最近 500 条明细前端计算并标注）、当前模型、调用明细
  - 待后端：限额触发统计（429 计数）；成本/P95 改服务端聚合以覆盖完整时间窗口
- [x] 1.24 后台骨架页：公共菜谱库（已上架用真实数据 + 大图预览；待审核/驳回/下架待 3.7）、账号与家庭（账号/成员数；家庭列表待 2.5）、系统健康（AI 服务失败率 + 最近失败调用；其余待 3.9/3.10）；菜谱管理表格加封面列

## P2 家庭（依赖 multi-tenant-family B/E/F 完成）
- [ ] 2.1 创建 / 加入家庭（引导 3 步、6 位邀请码）
- [ ] 2.2 切换家庭底部弹层；全局 currentFamily store + 请求头
- [ ] 2.3 成员与角色、成员档案、退出家庭
- [ ] 2.4 邀请消息（加入 / 暂不）
- [ ] 2.5 后台 账号与家庭（只读元数据 + 冻结）

## P3 生产级能力（后端 DDD 新上下文 + 四端）
- [ ] 3.1 消息类型扩展 invite/feedback/library + 全部已读
- [ ] 3.2 反馈 type/errorId；后台回复并发消息
- [ ] 3.3 注销 15 天冷静期（申请/撤回/到期匿名化，主人需先转让）
- [ ] 3.4 数据导出（异步 + 消息通知）
- [ ] 3.5 登录设备列表与下线
- [ ] 3.6 营养核对状态（AI 估算 → 人工核对）
- [ ] 3.7 投稿公共库 + 后台审核页（自动检查：敏感词/外链图/相似度）
- [ ] 3.8 AI 预算与后台改限额（80% 告警、超额降级）
- [ ] 3.9 审计日志 + 后台页
- [ ] 3.10 系统健康 + 报错聚合 + 后台页

## P3-待定（需用户决策供应商与费用，决策前不开工）
- [ ] 3.20 短信验证码登录
- [ ] 3.21 拍小票入库
- [ ] 3.22 拍照识别菜谱

## Quality Gate
- [ ] 每页 before/after 截图 × 两皮肤，用户验收
- [ ] 四端测试全绿；ArchUnit 基线只减不增
- [ ] pages 目录 0 裸色值 / 裸字号
