# 小程序复刻 APP 迁移方案（页面级映射文档）

> 2026-08-10 定稿。目标：小程序（menu-mini，uni-app + Vue3 + 微信小程序）成为 APP（menu-flutter）的完整复刻——页面布局、视觉样式、功能交互全部对齐 APP。
> 共用同一后端（menu-api），接口全部现成；两端共同设计源 = 44829 原型批次（`.superpowers/brainstorm/44829-1783002708/`）。

## 1. 范围

| 范围 | 说明 |
|---|---|
| **做** | 除下列外 APP 全部功能：菜谱/食集/库存/我的 4 个 Tab 全部页面、写菜谱、评价、食记、采购、食材管理、成员、备餐计划、**聚餐/一起吃 + 朋友扫码点菜**、**推荐 Tab（2026-08-17 随 APP 向量化版同步迁移）** |
| **暂不做** | AI 估营养（APP「我的」下的录入工具，等 APP 端稳定后补） |
| **保留** | 小程序现有登录授权（微信授权 → token），request 封装、auth store |

## 2. 总体技术方案

1. **设计 token 迁移**：`AppTokens.cream`（menu-flutter/lib/core/app_theme.dart）→ 小程序全局 CSS 变量（App.vue / uni.scss），色值/圆角/字号/间距逐项对齐，两端同源。
   - 主色 `#E89150` / deep `#D17A3C` / bg `#FDFAF4` / card `#FFF` / border `#F0E6D6` / title `#4A382A` / body `#6E5C49` / caption `#9C8C7A` / highlight `#FFF7EC` / secondary `#FBF0DD` / success `#4FAE6E` / warning `#E5A938` / error `#DB5A4E`
   - 圆角：rSm 8 / rMd 12 / rLg 16 / rXl 22 / 胶囊 999；字号体系对齐 `textStyles`（micro 9 / tiny 10 / sm 12 / md 13 / cardTitle 14 / subtitle 15 / h3 18 / pageTitle 16w700…）
2. **公共组件一次做好**（见 §4 组件映射表），全部页面复用，禁止各页手写顶栏/空态（对齐 DESIGN.md §13/§14 铁律）。
3. **API 层**：按 APP service 层（menu-flutter/lib/services/*.dart）逐方法对齐重写 menu-mini/src/api/*.ts；列表接口一律分页（`pageNum/pageSize`，每页 10 条，DESIGN.md §12.2）。
4. **设计约束沿用** `docs/design/DESIGN.md` 全部条款（§2 文案规范、§12 分页、§13 顶栏、§14 空态/错误态、§16 写菜谱…），小程序不得自成一套。

## 3. 页面映射总表

### 3.1 底部 Tab（5 个，自定义 TabBar）

| # | APP 路由/页面 | 功能要点 | 依赖接口 | 小程序页面 | 阶段 |
|---|---|---|---|---|---|
| 1 | `/dish` 菜谱库 | 搜索框、分类标签条、排序（最新/做过最多）、卡片列表、分页（10/页）、?sort=latest 跳转支持 | dish/search、dict(tag) | pages/dish/List | 2 |
| 2 | `/menu` 食集 | 全部/进行中/已完成筛选 + 右侧「新建食集」、卡片（封面堆叠/菜数/份数/状态胶囊）、左滑删、下拉刷新+分页、空态（无 CTA 无副文案） | menu CRUD | pages/menu/List | 3 |
| 3 | `/ai-recommend` 推荐 | 语义输入（自然语言/快捷口味 chips）→ 「相似菜」Top8 列表（行可点详情）+「组合推荐」（画像+偏好向量召回、卡片含菜品 chips + 理由）；输入变化即清空旧结果 | dish/semantic-search、ai/menu/recommend | pages/recommend/Index | 9 |
| 4 | `/pantry` 库存 | 搜索框（输入即搜、300ms 防抖、✕清空、结果平铺按档排序分页）、四档筛选 chip、用完/不足/充足三组分页（10/页、组尾加载更多胶囊）、入库/去采购顶按钮 | pantry/grouped(level,keyword,pageNum,pageSize) | pages/pantry/List | 4 |
| 5 | `/profile` 我的 | 成员切换栏、我的评价/食材管理/成员管理/备餐计划/写菜谱入口、草稿箱、设置 | member、统计 | pages/profile/Profile | 1(骨架)/7(完整) |

### 3.2 菜谱模块

| APP 路由/页面 | 功能要点 | 依赖接口 | 小程序页面 | 阶段 |
|---|---|---|---|---|
| `/dish/:id` 菜谱详情 | 封面（无图首字占位）、用料（家里有/没有徽标，读库存 levelMap）、步骤图文、评价摘要/入口 | dish 详情、pantry levelMap、review avg | pages/dish/Detail | 2 |
| `/dish-picker` 选菜 | 食集「+加菜」进来的选择模式，点卡加入返回 | dish/search、menu 加菜 | pages/dish/Picker | 3 |
| `/create-dish` 写菜谱 | 写菜谱/导入链接分段（注意切换刷新！）、封面图上传、菜名/时间/难度/标签/介绍/用料/步骤（图文）、草稿保存、预览发布；?draftId= 继续编辑 | dish/draft 系列、上传、导入解析 | pages/dish/Create | 6 |
| `/dish-drafts` 草稿箱 | 列表 + 继续编辑 + 删 | dish/draft/list | pages/dish/Drafts | 6 |
| `/dish-preview` 预览 | 发布前预览（extra 传数据） | 无（本地数据） | pages/dish/Preview | 6 |
| `/dish/:id/review` 菜谱评价 | 好不好吃（星级+标签+评语） | review | pages/dish/Review | 6 |

### 3.3 食集 + 做菜

| APP 路由/页面 | 功能要点 | 依赖接口 | 小程序页面 | 阶段 |
|---|---|---|---|---|
| `/menu/:id` 食集详情 | BackHeader（食集名+副信息+状态徽）、菜/备菜/一起吃 3 Tab（IndexedStack 保状态）、备注编辑、加菜/删菜、整集做（做菜确认弹层：每样料 用完了/用了一些/没用，扣库存）、去评价 | menu 详情、prep、pantry、cook | pages/menu/Detail | 3 |
| 做菜确认弹层 | 「这顿饭用了什么」三态逐项确认 → 回写库存 + 记食记 | menu/cook、pantry level | （Detail 页内组件） | 3 |
| `/menu/:id/review` 统一评价页 | 结果页统一入口（逐道评价+整集评价） | review/menu-overview | pages/menu/Review | 6 |
| `/menu/:id/review-form` 评价表单 | 星级/标签/评语提交 | review | pages/menu/ReviewForm | 6 |

#### 3.3.1 食集功能详细规格（新建 / 备菜状态 / 加采购）

**A. 新建食集**
- 入口：食集列表筛选行最右侧「新建食集」实心胶囊按钮（空态不放 CTA，避免双入口）；
- 交互：弹窗（标题「新建食集」+ 输入框 hint「食集名（如：今晚的饭）」+ 取消/确定，键盘回车即提交）；
- 接口：`POST /menu`，body `{menu: {name, servingCount: 1, status: 'ACTIVE'}, dishes: []}`（service 层还支持 `dishIds` 带菜建集）；
- 成功：toast「已创建食集」→ 列表刷新（重置第 1 页）；
- 小程序实现：`uni.showModal` 不够自定义样式，用 popup 弹层组件对齐 APP 的 AlertDialog 视觉。

**B. 备菜状态变更（待备 / 已备）**
- 数据：`GET /menu/{menuId}/prep` → `{items: 主料[], condiments: 调料[], readyCount, totalCount}`；每项 PrepItem 含：`ingredientId / ingredientName / stockLevel（家里档位 ENOUGH/LOW/NONE）/ usageTexts（用量明细，如「番茄炒蛋 2个」）/ dishCount / dishNames（共用高亮）/ status`；
- 状态 4 档（枚举 PrepStatus）：`PENDING 待备`（灰 caption 色）/ `READY 已备`（绿 success）/ `THAWING 化冻中`（蓝 info）/ `MARINATING 腌制中`（黄 warning）；
- 交互规则：
  - **点行**：`PENDING ↔ READY` 循环切换（最常用路径一键已备）；
  - **长按行**：弹三选一（化冻中 / 腌制中 / 重置为待备）；
  - **乐观更新**：先改本地 + 重算 readyCount，`PUT /menu/{menuId}/prep/{ingredientId}?status=READY` 失败回滚 + toast「更新失败」；
  - **已完成食集**（status=DONE）：chip 只读不可点；
- 进度区：「备料进度」+「已备 X / 共 Y 样」+ 进度条（readyCount/totalCount）；
- 结构：主料「备料清单」+ 调料**折叠组**（默认收起，同样计入总数与进度）；
- 小程序注意：APP 用 IndexedStack+refreshTick 保 Tab 状态；小程序 Tab 切换/加菜返回后用 `onShow` 重拉 prep 即可，无需复刻 IndexedStack。

**C. 一键加采购**
- 入口：备菜 Tab 进度区右上「一键加采购」实心胶囊（食集已完成时隐藏）；
- 交互：弹勾选层（主料+调料全部列出）：
  - **默认勾选家里用完(NONE)/不足(LOW)的项**，副文案「默认勾选家里没有/不足的，可改」；
  - 行 = 勾选框 + 食材名 + 家里档位徽；
  - 底部确认 → `POST /shopping/from-prep {menuId, ingredientIds[]}` → 返回 listId；
  - **成功后直接跳转该采购清单**（`/shopping?listId=xxx`，小程序 `navigateTo shopping?listId=`）；
- 后端语义：送进该食集关联的采购清单，没有则新建一张；
- 边界：备菜为空时 toast「没有可加入的备菜」；勾选为空不提交。

### 3.4 库存 + 采购

| APP 路由/页面 | 功能要点 | 依赖接口 | 小程序页面 | 阶段 |
|---|---|---|---|---|
| `/pantry/add` 入库 | 录入页只有返回箭头；头说明文案；搜索框（⌕+✕）；未输入=提示卡+「库存里没有？＋新建」入口（点它聚焦搜索）；输入后显示库里已有（家里档位+选）；精确同名隐藏新建区；第二步档位（补充后，家里有多少？充足默认/不足=一点点）+来源备注（朋友送/赠品/旧库存补登/其他） | pantry/manual、ingredient 列表 | pages/pantry/Add | 4 |
| `/pantry/:id` 库存详情 | 食材头+档位徽、3 档单选（现在家里是什么情况？）、最近 6 条流水、保存 | pantry/item、PUT level | pages/pantry/Detail | 4 |
| `/shopping` 采购页 | 清单分区（品类）、行（家里档位徽、勾选）、全选、手动添加、保存入库（批量设档位，充足默认）、清空已完成 | shopping 系列 | pages/shopping/List | 4 |
| 采购入库弹层 | 勾选项设档位入库 → 回写库存 | shopping/restock | （Shopping 页内组件） | 4 |

### 3.5 食记

| APP 路由/页面 | 功能要点 | 依赖接口 | 小程序页面 | 阶段 |
|---|---|---|---|---|
| `/dailylog` 食记日记 | 快速记/从菜库选、精准模式、翻日 | dailylog | pages/dailylog/Index | 5 |
| `/food-log` 食记统计 | 月|年统计范围切换、TimeSelectCapsule 时间胶囊、‹›步进、时间轴/按菜汇总 2 Tab、分页 | food-log/month、year、by-dish | pages/foodlog/Index | 5 |
| `/food-log/detail` 食记详情 | 单日记详情+营养 | food-log/detail | pages/foodlog/Detail | 5 |

### 3.6 食材 / 成员 / 备餐 / 我的评价

| APP 路由/页面 | 功能要点 | 依赖接口 | 小程序页面 | 阶段 |
|---|---|---|---|---|
| `/ingredient` 食材管理 | 分类筛选、搜索、列表、库存徽 | ingredient、dict | pages/ingredient/List | 7 |
| `/ingredient/:id/edit` 编辑 | 名称/品类/食用属性 | ingredient PUT | pages/ingredient/Edit | 7 |
| `/create-ingredient` 新建 | 同上（含 AI 补全营养） | ingredient POST、ai | pages/ingredient/Create | 7 |
| `/members` 成员管理 | 成员卡、切换、健康档案（只读+切换） | member | pages/member/Index | 7 |
| `/my-reviews` 我的评价 | 评价列表 | review/mine | pages/review/Mine | 6 |

> 注：**备餐计划（mealplan）不存在**——APP 无该路由/页面（service 未被前端使用），小程序不做。各页面的**详细功能规格**（交互流程/接口/边界/小程序注意点）见 `docs/mini-program-specs.md`。

### 3.7 聚餐（一起吃 + 扫码点菜）

| APP 路由/页面 | 功能要点 | 依赖接口 | 小程序页面 | 阶段 |
|---|---|---|---|---|
| 食集详情「一起吃」Tab | 邀请入口（生成分享/小程序码）、协同点菜列表、实时汇总 | menu/together、invite | pages/menu/Detail 内 Tab | 8 |
| 朋友点菜页 | 免登录视角（凭证进）、浏览菜+点菜+提交 | together/items | pages/together/Pick | 8 |

## 4. 公共组件映射（Flutter → Vue）

| Flutter 组件 | 职责 | 小程序组件 |
|---|---|---|
| ActionBar | Tab 主页顶栏（无标题，action 右对齐，状态栏融合） | components/ActionBar.vue |
| BackHeader | 详情/录入页顶栏（箭头行在上、title 可选） | components/BackHeader.vue |
| LoadingView / EmptyView / ErrorView | 骨架闪烁 / 空态（可带 CTA）/ 错误重试 | components/StateViews.vue |
| StatusChip / stockColor | 档位胶囊与三色 | components/StatusChip.vue |
| InitialAvatar | 首字色块占位（§10.4/§10.5） | components/InitialAvatar.vue |
| TimeSelectCapsule | 时间胶囊（年→月→日→时→分逐级弹层） | components/TimeSelect.vue |
| 加载更多胶囊 | 「加载更多 · 还有 N 项」 | components/LoadMore.vue |
| CustomTabBar（已有） | 底部 5 Tab，状态保持 | 保留改造为 5 Tab |

## 5. 分阶段实施（每阶段独立可验收）

| 阶段 | 内容 | 验收 |
|---|---|---|
| 1 地基 | 删旧页面（保留 login/request/auth store）；token CSS 变量；公共组件；5 Tab 骨架 + 推荐「建设中」占位；登录联调 | 登录后进 5 Tab 壳，视觉对齐 APP |
| 2 菜谱链路 | 菜谱 Tab（搜索/标签/排序/分页）→ 菜谱详情（用料徽标/评价摘要） | 找菜→看菜全通 |
| 3 食集+做菜 | 食集 Tab → 详情（菜/备菜）→ 加菜/删菜/备注 → 整集做（做菜确认扣库存） | 建食集→加菜→做完扣库存 |
| 4 库存+采购 | 库存 Tab（搜索+三组分页）→ 入库 → 详情改档位 → 采购 → 勾选入库 | 库存增删改查+采购闭环 |
| 5 食记 | 日记 + 统计（月/年、时间轴/按菜） | 做菜后自动+手动记食记 |
| 6 写菜谱+评价 | 写菜谱（上传/草稿/预览/导入链接）→ 菜谱评价 → 食集评价 → 我的评价 | 全评价链路 |
| 7 我的+管理 | 食材管理/编辑/新建、成员、我的页完整 | 管理功能齐 |
| 8 聚餐 | 一起吃 Tab、邀请（小程序码/分享卡片）、朋友点菜（免登录凭证） | 扫码点菜闭环 |
| 9 推荐 | 推荐 Tab 复刻 APP（语义找菜 + 组合推荐，2026-08 向量化版） | 找菜→看菜、组合推荐全通 |

## 6. 小程序特有差异与风险

1. **登录**：微信授权（wx.login → code2session → 后端换 token），后端已有对接（保留现有 auth 流程）；朋友点菜页需免登录凭证路由。
2. **自定义导航栏**：全部页面 `navigationStyle: custom`，对齐 BackHeader/ActionBar；注意各机型安全区（胶囊按钮位置）。
3. **图片上传**：wx.chooseMedia + uploadFile（写菜谱封面/步骤图、头像）。
4. **页面栈 10 层上限**：写菜谱→预览→发布等深链路用 redirectTo/navigateBack 控制。
5. **扫码/分享**：聚餐邀请用小程序码参数或分享卡片 path 带凭证。
6. **双端维护约定**：APP 改版后小程序按本表同步（44829 原型批次 + DESIGN.md 是共同权威，改动先回写原型再两端实施）。

## 7. 待办边界（明确不做，防蔓延）

- AI 估营养
- APP 端尚未定稿的功能不预做，等定稿后按第 6.6 条同步
