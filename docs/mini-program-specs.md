# 小程序复刻 · 模块详细规格

> 配合 `docs/mini-program-migration.md`（总览/阶段/映射表）阅读。本文件是**页面级功能规格**，目标：照此在小程序 1:1 复刻 APP 的页面布局、交互与接口调用。
> 信息来源：menu-flutter 源码通读（2026-08-10）。

## 目录

1. [菜谱模块](#一菜谱模块)
2. [食集模块（含新建/备菜/加采购/做菜确认）](#二食集模块)
3. [库存模块](#三库存模块)
4. [采购模块](#四采购模块)
5. [食记模块（做菜日记）](#五食记模块)
6. [每日饮食 dailylog](#六每日饮食-dailylog)
7. [评价模块](#七评价模块)
8. [食材管理](#八食材管理)
9. [成员管理](#九成员管理)
10. [聚餐（一起吃 + 朋友点菜）](#十聚餐一起吃--朋友点菜)
11. [我的 Tab](#十一我的-tab)
12. [修正与裁剪说明](#十二修正与裁剪说明)

---

## 一、菜谱模块

### 1.1 菜谱库 Tab（pages/dish/list_page.dart）

**入口/导航**
- 底部 Tab「菜谱」主页（无标题栏，ActionBar 空占位）。
- 参数：`selectForMenuId`（从食集「+ 加菜」进入的选择模式）；`sort=latest`（发布成功后跳回并强制最新排序）。

**页面结构（自上而下）**
1. 顶栏：Tab 主页模式=ActionBar 空占位；选择模式=左上「‹」+ 提示条「选择要加入的菜」。
2. 搜索框：白底 1.5px 主色描边、12px 圆角，hint「搜菜名」；聚焦橙色光标 + ✕ 清除。
3. 分类标签条（tag 字典）：「全部」+ chips 横向滚动；选中深底白字。
4. 菜系筛选条（cuisine 字典）：同款 chips，与分类**叠加**筛选。
5. 排序行：左「N 道」总数；右「最新 / 做过最多」胶囊。
6. 列表（下拉刷新+上拉加载，10/页，触底前 100px 预载）：卡片=44px 缩略图（thumbnail，失败首字占位）+ 菜名 + 「做过 N 次｜没做过 · X 分」；尾部「上拉加载更多/没有更多了」。

**交互**
- 搜索：**键盘提交触发**（非实时）；chip 点击 setState + reload（页码重置）。
- 点卡：浏览模式→详情；选择模式→`addDishToMenu` 成功 toast「已加入食集」+ pop。
- 左滑删除（浏览模式）：滑出红底→确认弹窗「确定删除「菜名」吗？删除后不可恢复」→ `DELETE /dish/{id}`；失败卡片弹回 + toast。

**接口**：`GET /dish/search`（keyword/tagIds/cuisineIds/sort=cooked 缺省最新/pageNum/pageSize=10）；字典 `GET /dict?group=tag|cuisine`（失败静默不渲染筛选条）；`DELETE /dish/{id}`。

**边界**：空态「暂无菜品」；请求失败静默置空。

**小程序注意**：左滑删除自实现（失败弹回需复位）；触底 `onReachBottom`；下拉 `onPullDownRefresh`；cursor-color 支持橙色光标。

### 1.2 菜谱详情（pages/dish/detail_page.dart）

**入口/导航**：`/dish/{id}`，参数 `showActions`（食集内查看传 false 隐藏「加到食集」）。

**页面结构**
1. BackHeader：箭头 + 菜名（h3）+ 副信息两行：「备料 X分 · 烹饪 Y分 · 难度 Z/5」+ 来源（导入菜谱时「来源：下厨房」等）。
2. 封面：全宽 220px，点击进全屏查看器；无图/失败→奶油底菜名首字大号占位。
3. 标签区：菜系+分类+标签 chips（主色软底）+ 备注 note。
4. 用料：标题行「用料」+「份数 1 · 共 N 样」；行=28px 首字块 + 食材名 + 右对齐用量（自然单位「2 个」「适量」）；底部说明「用量为 1 份基准；做菜时按份数自动放大。」。
5. 做法：每步「步骤 N」+ 文字 + 80×80 步骤图 Wrap（点击全屏）。
6. 底部（showActions）：绿色通栏「加到食集」（48px，提交中转圈禁用）。

**加到食集流程（核心）**
1. 拉食集列表（pageSize 50）→ **过滤出今天 0 点及以后创建的食集**；
2. 无近期食集→弹「新建食集」输入框（**预填菜谱名**，空值回落菜名）→ `createMenu(name, dishIds:[id])` → toast「已加入新食集「name」」；
3. 有→底部选择器「加到哪个食集？」（行=食集名 + `M/D HH:mm · 份数 N · 状态`，末行「新建食集」）→ `addDishToMenu` → toast「已加入食集「name」」；
4. 失败 toast「加入食集失败」；取消复位防重锁。

**接口**：`GET /dish/{id}`；menu list/create/addDish。

**边界**：加载失败 ErrorView + 重试；无图首字占位。

**小程序注意**：全屏查看器用 `wx.previewImage`（传原图数组）；「今天及以后」过滤逻辑保留（本地时区 0 点比较）。

### 1.3 写菜谱（pages/dish/create_page.dart，双 Tab）

**入口/导航**：「我的」→「写菜谱」；草稿箱「继续 ›」→ `?draftId={id}`。发布成功→`go('/dish?sort=latest')`；导入成功→`go('/dish/{newId}')`；预览→push 预览页。

**返回拦截（PopScope + dirty 签名）**
- dirty 判定=表单签名对比（菜名/时间/难度/介绍/封面有无/每条用料/每步文字+图）；
- 编辑草稿模式：「放弃修改？／这次改动还没有保存」→ 取消/放弃；
- 新建模式：「先存草稿再走？／写了一半的内容，可以先存成草稿」→ 取消/**存草稿**（成功再退）/放弃。

**写菜谱 Tab 表单字段（顺序）**
1. 封面（可选）：96px 横幅；空态奶油底+相机图标+「添加封面（可选）」；有图铺满+渐变遮罩+「更换」「删除」白胶囊；URL 图失败→「菜」首字占位。
2. 菜名（必填）：hint「如：番茄炒蛋」。
3. 备料(分)/烹饪(分)：两个数字框并排。
4. 难度：1-5 星点选，默认 3；档位文案「超简单/简单/中等/困难/炼狱」。
5. 标签（**必选**）：tag 字典 chips 多选，一行滚动+▾ 展开收起。
6. 菜系（可选）：cuisine chips 多选。
7. 菜谱介绍：2 行多行输入。
8. 用料区：行卡片=首字色块+食材名+56px 用量数字框+72px 单位框（**实时匹配 unit 字典**：匹配→橙字加粗+主色边框；不匹配提交时 `upsertDict` 自动补字典）+单位▾弹字典 chips+行尾✕删行；
   - 「＋ 加用料」→ 弹层：搜索框实时搜食材库；未搜索显**常用食材 chips（前 8）**；结果行「选」；底部「＋ 新建食材」→ 二级弹层（名称必填预填搜索词+分类 chips 可选）→ 建档成功自动加行 + toast「已建档」；已加过的 toast「已加过」。
9. 步骤区：每步卡片（主色圆号+「步骤 N」+✕删）+3 行文字+步骤图（44px「添加图片（可选）」条 / 100px 图+右上✕）；「添加步骤」追加。
10. 底部三按钮：「存草稿」（描边，**不校验必填**）/「预览」（描边）/「发布」（实底主按钮）；saving 全禁用。

**关键流程**
- 图片：选图→本地压缩暂存；**发布/存草稿时才上传**。
- 存草稿：上传图→用量自由文本原样存（amount/unitText 字符串）→过滤空步骤→`POST /dish/draft`（带 id=更新）→ 存 `_draftId` + 重置 dirty 基准 + toast「已存草稿」→ 从草稿箱进入则 pop。
- 发布：校验菜名「请输入菜名」/标签「请选择标签」（toast）→上传图→`POST /dish`（payload 含 dish/steps/ingredients(ingredientId+amount+unitId)/tagIds/cuisineIds）→成功且有草稿则删草稿（失败忽略）→toast「已保存」→`go('/dish?sort=latest')`。
- 草稿回填：`GET /dish/draft/{id}` 回填全部字段（用料数字/单位两框分开还原）→重置 dirty 基准。

**导入链接 Tab**
- 说明卡（「从其他 App 导入菜谱」+ 平台 chips 下厨房/美食杰/豆果）+ URL 输入框（✕清空）+「开始导入」按钮（导入中「正在解析菜谱…」转圈）。
- 校验：空→「请粘贴菜谱链接」；`Uri.tryParse+hasScheme` 失败→「链接格式不正确」；`POST /dish/import-url?url=` → 新菜 id → toast「导入成功」→ `go('/dish/{id}')`。

**预载字典**：unit / 食材库全量（前 8 常用）/ tag / purchase_category / cuisine，均静默失败。

**小程序注意**：图片 `wx.chooseMedia`+`wx.compressImage`+`wx.uploadFile`（tempFilePath 概念）；dirty 拦截需在自定义导航返回按钮上做 + `enableAlertBeforeUnload` 妥协手势返回；动态行增删用 data-index。

### 1.4 预览页（pages/dish/dish_preview_page.dart）

- 仅写菜谱「预览」进入，**数据对象直传**（无接口）：封面 150px（本地临时图优先）+ 菜名（空「未命名菜谱」）+ meta「备料 X 分 · 烹饪 Y 分 · 难度 ★X」+ 菜系/标签 chips + 用料区 + 介绍 + 做法（圆号+文字）。
- 顶栏「‹」/「编辑」都 pop；底部 Sticky「发布「菜名」」→ 执行写菜谱页 `onPublish` 回调（真正上传在回调里，预览页不重复上传）。
- 小程序：数据用 eventChannel/页面栈传递；tempFilePath 直接 `<image>` 展示。

### 1.5 草稿箱（pages/dish/draft_list_page.dart）

- 「我的」→「草稿箱」；行=42px 首字块（空名「未」）+草稿名+「用料 N · 步骤 N · 时间（今天 HH:mm/昨天/M/D HH:mm）」+ 右侧橙「继续 ›」；分页 10/页。
- 左滑删除（**无确认弹窗**，滑走即删 `DELETE /dish/draft/{id}`，失败静默照删本地）。
- 点行→`/create-dish?draftId=`；空态「还没有草稿」+「写菜谱没填完，点「存草稿」就会出现在这里」。
- 小程序：编辑页返回后 onShow 重拉。

### 1.6 菜谱评价页（pages/dish/review_page.dart）

- `/review?dishId=`，AppBar「写评价」，核心是公共 ReviewForm（dishId 模式，标题「给这道菜打个分」）→ 提交成功 pop。详见 [评价模块](#七评价模块)。

---

## 二、食集模块

### 2.1 食集列表 Tab（pages/menu/list_page.dart）

**页面结构**
1. 筛选行：「全部/进行中/已完成」chips（计数=当前已加载数据）+ 最右「新建食集」实心胶囊。
2. 列表（下拉刷新+分页 15→10/页）：卡片=标题行（食集名+状态胶囊 进行中黄/已完成绿）+ 第二行（封面缩略堆叠 22×22 无图用菜名首字块 +「N 道菜」+「M 人份 · 相对日期」）。
3. 进行中卡片=highlight 底+primarySoft 描边；已完成=白底。
4. 左滑删除：红底→确认弹窗「确认删除食集「name」？该操作不可撤销」→ `DELETE /menu/{id}` → toast「已删除」。
5. 空态：EmptyView「还没有食集」（**无 CTA、无副文案**——右上已有新建按钮）。

**新建食集**
- 弹窗：标题「新建食集」+ 输入 hint「食集名（如：今晚的饭）」+ 取消/确定（回车即提交）；
- `POST /menu` body `{menu:{name, servingCount:1, status:'ACTIVE'}, dishes:[]}`（service 还支持 `dishIds` 带菜建集）→ toast「已创建食集」→ 列表重置刷新。

**后端兼容注意**：列表接口旧版不返回 dishCount/covers 时，APP 并发拉详情补菜数+菜名（`_enrichDishInfo`）——小程序对接新版后端可省略，但建议保留兜底。

### 2.2 食集详情（pages/menu/detail_page.dart）

**页面结构（全局）**
1. BackHeader：食集名（h3）+ 状态胶囊 + 副信息「{相对日期} · 份数 N · 关联 N 道菜 · 约 N 分钟」。
2. TabBar：「菜 · N」/「备菜 · N」/「聚餐 · N」（选中主色+底部 2px 下划线；IndexedStack 保状态，refreshTick 跨 Tab 刷新——小程序直接切 Tab 重拉即可）。
3. 底部操作区**仅菜 Tab 显示**：进行中=绿色「开始做饭」；已完成=「已完成」（禁用）+描边黄「去评价」→ `/menu/{id}/review`。

**菜 Tab（_DishesTab）**
- 「包含菜品」列表：行=38px 缩略图（首字占位）+菜名（空名回退「菜 #id」「自定义菜」）+「{addedByNickname} 点的」小标签（聚餐朋友加的）+「× N 份」+chevron；
- 点行→`/dish/{dishId}?showActions=0`（自定义菜 dishId=null **不可点**）；
- 备注行（虚线上边框）：「备注」+胶囊内容/灰字「加备注/忌口…」+✎ → 弹 255 字输入（预填）→ `PUT /menu/{id}/dish/{dishId}/note`（空串=删）→ 刷新；已完成/自定义菜只读；
- 行尾✕（备注行右端）：确认「移出食集」→ `DELETE /menu/{menuId}/dish/{dishId}` → 刷新；自定义菜不显示✕；
- 「+ 加菜（去菜谱找）」虚线框（主色描边）→ `/dish-picker?menuId=`，返回刷新（未完成态才显示）。

### 2.3 备菜 Tab（_PrepTab）

**数据**：`GET /menu/{id}/prep` → `{items: 主料[], condiments: 调料[], readyCount, totalCount}`；每项：`ingredientId/ingredientName/stockLevel(ENOUGH|LOW|NONE)/usageTexts[]（如「番茄炒蛋 2个」）/dishCount/dishNames（共用高亮）/status`。

**状态 4 档**：`PENDING 待备`（白底描边灰字）/ `READY 已备`（实绿底「✓ 已备」，行名灰色+删除线）/ `THAWING 化冻中`（蓝）/ `MARINATING 腌制中`（橙）。

**页面结构**
1. 进度区：右上「一键加采购」胶囊（完成态隐藏）+「备料进度 · 已备 X / 共 Y 样」+ 绿色进度条；
2. 「备料清单」主料列表；
3. 调料折叠组「调料 N 样」（默认收起；**计入总数与进度**）。

**备菜行**：食材名+共用副信息（≥2 道菜：「N 道菜共用 · 菜名、菜名」淡橙底高亮）+「家里：充足/不足/用完」徽标（绿/橙/红）+用量原文（多条「 + 」拼接）+行尾状态 chip。

**交互**
- **点行：PENDING ↔ READY 循环**；**长按：弹三选**（化冻中/腌制中/重置为待备）；
- **乐观更新+失败回滚**（重算 readyCount）+ toast「更新失败」；
- 已完成食集：chip 全部只读；
- 接口：`PUT /menu/{menuId}/prep/{ingredientId}?status=READY`（query 传状态名，upsert）。

**一键加采购**
- 入口：进度区右上胶囊（完成态隐藏）；
- 弹勾选层：主料+调料全列，**默认勾选家里用完(NONE)/不足(LOW)的**，副文案「默认勾选家里没有/不足的，可改」；行=勾选框+食材名+家里档位徽；
- 确认→`POST /shopping/from-prep {menuId, ingredientIds[]}` → 返回 listId → **直接跳转 `/shopping?listId=`**；
- 边界：备菜空 toast「没有可加入的备菜」；勾选空不提交。

### 2.4 做菜确认弹层（cook_confirm_sheet.dart）

**流程**：「开始做饭」→ `GET /menu/{id}/cook-materials` → 半屏弹层（85% 高）→ 确认 → `POST /menu/{id}/cook` → 结果弹层 → 关闭刷新详情（食集已 DONE）→「去评价 ›」→ `/menu/{id}/review`。

**确认层「这顿饭用了什么」**
- 标题+副标题（前 3 食材名，>3「A + B + C 等 N 样」）；
- 逐项行：食材名 +「家里：{档位} · 用量原文」+ 右侧**三态 chips 单选**：「用完了」红/「用了一些」黄/「这次没用」灰边；选中实底白字；
- **初始值：调料(isCondiment)默认「用了一些」，其余默认「用完了」**；
- 底部说明：「食材默认『用完了』，调料默认『用了一些』（降一档）。点一下就能改。没有库存的食材也不拦着你做饭。」；
- 底栏两按钮：「跳过，不更新库存」（描边，skipped=true 仍完成食集/写食记但不改库存）+「确认已做完 · N 样用完」（主色，N=选用完数）；
- 手势下滑关弹层=**取消**（不做饭）。

**结果层**：绿✓「做好了，库存已更新」+「食集 → 已完成」+ 明细（usedUp 行尾红「用完」/partiallyUsed 黄「用了一些」，皆空「本次没有更新库存」）+「去评价 ›」+「返回食集」。

**接口**：`GET /menu/{id}/cook-materials` → `{items:[{ingredientId, ingredientName, usageTexts[], level, isCondiment}]}`；`POST /menu/{id}/cook` body `{usedUp:[], partiallyUsed:[]}` → 后端扣档位+每菜写 cooking_record+食集标 DONE。

---

## 三、库存模块

### 3.1 库存 Tab（pages/pantry/list_page.dart，2026-08-10 定稿版）

**页面结构**
1. ActionBar：右侧「入库」（描边）+「去采购」（实心）两胶囊。
2. 搜索框：⌕ 图标+输入（hint「搜库存」）+ ✕ 清除（输入时出现）；**输入即搜（300ms 防抖）**。
3. 筛选条（非搜索态）：「全部 N / 用完 N / 不足 N / 充足 N」chips（计数=汇总总数，选中实心各自色）。
4. 列表：
   - **分组态**：三组（用完红/不足黄/充足绿）标题「用完 · N」（计数=汇总总数不随加载变化）+ 行 + 组尾「**加载更多 · 还有 N 项**」胶囊（白底+本组色描边，加载中「加载中…」禁用）；**每组独立分页 10/页**；
   - **搜索态**：隐藏筛选条；「找到 N 个」+平铺结果（服务端按档位排序）+底部加载更多；空结果「搜不到「kw」」。
5. 行：40px 首字头像+食材名+来源副行（「手动 · 朋友送」橙色加粗/「用完了 · 昨晚」/「采购 · 7/2」/「本来就没有」/「无变动记录」）+档位色字+chevron；点行→`/pantry/{ingredientId}`。

**加载策略**：初始/刷新/详情返回=三档第 1 页**并行**拉取；组内独立 `_page/_loadingMore`；空态=汇总三档全 0 →「暂无库存」。

**接口**：`GET /pantry/grouped?level=&keyword=&pageNum=&pageSize=10`（summary 恒为三档总数；items 按 level 过滤+档位排序+名称 Collator 排序+切片）。

### 3.2 入库页（pages/pantry/manual_add_page.dart）

**两步流**：
- **步骤① 选食材**：录入页只有返回箭头（§13.1 无标题）；头说明「朋友送 / 赠品 / 之前忘记登的旧库存，记一笔进来」；搜索框（⌕+「搜库存」+✕）；
  - 未输入：提示卡（「输入名称，会显示库里已有的食材」）+「库存里没有？＋ 新建食材并入库」虚线卡（**常驻**，点它聚焦搜索框）；
  - 输入后：匹配的「库里已有」行（名称+家里档位+「选」/「已选」）；**精确同名时隐藏新建区**；无匹配只留新建区（「「q」建档同时入库」）；
- **步骤② 定档位+来源**：食材头（52px 首字+名称+「家里：xx · 入库记一笔」/「新建档 · 家里还没有」）；「补充后，家里有多少？」两卡：充足（默认）/不足（**一点点**）；「来源备注」chips：朋友送（默认）/赠品/旧库存补登/其他；底部「入库」。

**接口**：`POST /pantry/manual {ingredientId?|name?, level?, sourceNote?}`（选中已有传 ingredientId，新建传 name；默认 ENOUGH）；页面加载需 `GET /ingredient`（全量，建搜索库）+ `GET /pantry/grouped`（全量，建档位 map——全量理由已注释）。

### 3.3 库存详情（pages/pantry/detail_page.dart）

- BackHeader：食材名+档位副文案；食材头（52px 首字+档位徽）；
- 「现在家里是什么情况？」3 档单选卡：充足（还有不少）/不足（剩一点点）/用完（用光了），选中主色描边+实心圆点；
- 说明条：「选「用完」记一笔用完了，选「不足」记用了一些。昨天用完忘记的，现在补上就行。」；
- 「明细」时间线（最近 6 条流水）：来源徽（用完了红/用了一些黄/采购绿/手动主色/撤回入库灰）+备注或「前档位 → 后档位」+时间；
- 底部「保存」→ `PUT /pantry/{ingredientId}/level {level}`（记 manual 流水）→ pop。

---

## 四、采购模块（pages/shopping/shopping_page.dart）

**单页三视图**（同路由 setState 切换）：列表视图→详情视图→分享预览视图。`?listId=` 进来自动打开该清单详情（备菜一键加购跳转用）。

**列表视图**
- AppBar「采购清单」（批量模式「已选 N 个」）；右侧「批量删除」（红字）+「+ 新建清单」（`POST /shopping/create` 建空单直接进详情）；
- 清单卡：序号 `#N`（批量模式=勾选圈）+名称（无自定义名→「采购单 · {来源} · 第N 单」，来源映射 menu=菜单/dish=菜品/plan=周计划/custom=自定义/custom_text=文本录入）+日期区间；
- 批量模式底部「全选/取消全选」+「删除 N 个」（循环逐个 `DELETE /shopping/{id}`）；
- 长按清单→删整单确认；
- 空态：「还没有采购清单」+「清单来自食集：去食集详情 → 备菜 Tab →『一键加采购』；或者点右上角『+ 新建清单』自己列」。

**详情视图**
- AppBar：清单名（自定义名带 ✎ 改名→`PUT /shopping/{id}/name`）+分享图标；
- 全选行（只针对**未入库**项）+「已选 N 项」；
- 按 grouped 分类分节；采购项行：勾选框（**未入库=本地选择态不写库**；已入库=固定绿✓+名称删除线）+数量文本+余量徽章（RED_NONE「家里：用完」红/YELLOW_SHORT「家里：不足」黄/GREEN_ENOUGH「家里：充足」绿/null「手动加」灰）+行尾✕（未入库=移除确认，文案「以后要买，从备菜『一键加采购』或重新生成清单就能加回来」→`DELETE /shopping/item/{id}`；已入库=撤回确认「库存回到入库前状态，这项从清单移除。流水里会记一笔『撤回入库』」→`POST /shopping/item/{id}/undo-restock`）；
- 底栏：「添加」（描边）+「保存入库 · N 项」（N=0 禁用；空清单只有「添加」）；
- **保存入库**：`POST /shopping/restock {itemIds[]}`（批量，默认充足）→ toast「已入库 X 项[，Y 项只标已买]」→ 重开详情清空选择。

**添加弹窗**：名称框+「数量+单位」框（hint「2斤」）+「再加一行」（名称空忽略）；已添加列表✕删；保存=逐条 `POST /shopping/item/custom`（数字正则解析，单位忽略）。

**分享预览视图**：未入库项列表（全入库「清单里的都入库了，没有要分享的项」）；「复制文字」（标题+日期+逐行「名称 数量」）/「转图片分享」（320px 白底卡截图→分享面板）。
- **小程序**：复制用 `wx.setClipboardData`；转图用 canvas 2d 绘制同款卡→`wx.canvasToTempFilePath`→`wx.showShareImageMenu`。

**接口全表**：`GET /shopping`（分页）、`GET /shopping/{id}`（items+grouped+categoryNames）、`POST /shopping/create`、`POST /shopping/restock`、`POST /shopping/item/{id}/undo-restock`、`POST /shopping/item/custom`、`DELETE /shopping/item/{id}`、`DELETE /shopping/{id}`、`PUT /shopping/{id}/name`、`POST /shopping/from-prep`、`GET /shopping/by-menu/{menuId}`。

---

## 五、食记模块（做菜日记）

### 5.1 食记主页（pages/foodlog/food_log_page.dart）

**入口**：「我的」→「食记」。

**页面结构**
1. 顶栏行：「‹」+ TimeSelectCapsule（月粒度）+ 胶囊内 ‹/› 步进 + 最右「月|年」分段切换（primaryDeep 底白字）。
2. 统计卡（渐变 primary→primaryDeep 圆角 14）：横排「顿饭/道菜/做饭天数」+下方 24% 白透明条「（本月|全年）最常：菜名 · 菜名」。
3. Tab 胶囊：「时间轴」「按菜汇总」。
4. 时间轴：下拉刷新+滚动分页（10/页，触底 120px）；行=8px 主色圆点+食集名+`M/D HH:mm`+副行「N 道菜 · M 人份 · 菜名1/菜名2」+标签行（「用完 X 样」红/「用了一些 X 样」黄/「已评价」绿）；menuId=null 禁点。
5. 按菜汇总：提示条「本月做过 N 种菜 · 按次数排序，点一行看全部记录」；行=菜名+「N 次」（主色）+最近做日 M/D+「★X.X」（有均分才显）。

**交互**：月|年切换重载（month 参数=月模式 `yyyy-MM`/年模式 `yyyy`）；步进无边界；胶囊选具体月强制回月视图；按菜行点击=切回时间轴（MVP 未做过滤）；尾部「共 N 顿」。

**接口**：`GET /food-log/month?month=&pageNum=&pageSize=10`（summary+records）；`GET /food-log/by-dish?month=`（全量）。

### 5.2 食记详情（pages/foodlog/food_log_detail_page.dart）

- `?menuId=`；头部食集名+`M/D HH:mm · N 人份`；
- 「这顿饭的菜 · 吃完别忘了评价」区（菜名列表+「去评价 ›」→弹「评价哪道菜？」选择层→`/dish/{dishId}/review`）；
- 「这顿饭用了这些」：用完/用了一些明细卡（皆空「没更新库存（跳过了确认）」）+固定提示「库存档位已自动更新（用完→用完，用了一些→降一档）。去库存页随时可改。」；
- 底部大按钮「再做一次（复制建新食集）」→ `POST /menu/{menuId}/copy` → pop 本页+进新食集详情；下方说明「复制这 N 道菜 + 份数到新食集，重新走一遍流程」。
- 接口：`GET /food-log/detail?menuId=` → {menuId, name, cookedAt, servingCount, dishes[], usedUp[], partial[], reviewed}。

---

## 六、每日饮食 dailylog（pages/dailylog/daily_log_page.dart）

> 注意：这是**营养记录**（入口「更多」页），与「食记」（做菜日记）是两个模块。

- AppBar「每日饮食」+右侧「精准」Switch（成员有营养目标才显示）；
- 日期栏：‹ + TimeSelectCapsule（日粒度「M月d日」）+ ›（今天禁用）；**水平滑动切换日期**；下方「今天 · 左右滑动切换」/周几；
- 摘要（仅今天）：精准模式=热量环形进度（>100% 红/>85% 黄/其他绿；中心百分比）+「热量预算 实际/目标 kcal 剩余/超出」+蛋白/碳水/脂肪三条进度条；轻量模式=「今天 N 项 · 约 X kcal」/「今天还没记录」；
- 当日记录列表（菜=餐厅图标/食材=叶子图标+数量「X 份」「X g」）；FAB「+ 记一餐」仅今天；
- 录入弹层两 Tab：快速记（菜名+热门菜 chips 前 8+「快速记录」提交 `{dishName, amount:1}`）；从菜库选（搜索→点选提交 `{dishId, servingFactor:1}`）；
- **提交语义=整体替换**（现有 items+新项一起 POST）。
- 接口：`GET /dailylog?date=`、`POST /dailylog`、`GET /dailylog/{logId}/nutrition`、`GET /member/{memberId}/nutrition-target`。

---

## 七、评价模块

### 7.1 ReviewForm 公共表单（pages/review/review_form.dart，dish/menu 通用）

- Props：dishId/menuId 二选一 + title（单菜「给这道菜打个分」/食集 null）+ onSuccess；
- 总评星级卡（渐变橙）：5 颗 36px 白星**默认 5 星**；提示文案 {1 不太行/2 一般般/3 还可以/4 挺不错/5 想天天吃！}；
- 评价内容 4 行（hint「味道如何？难不难？想再做一次吗？」，可空）；
- 添加图片：80×80 九宫格，**上限 6 张**（满 6 隐藏添加块），右上黑半透明✕删；选图→压缩暂存→提交时逐张上传；
- 分项评分（维度字典 `GET /dict?group=review_dimension`，失败整块隐藏）：每维度 5 颗 24px 星，初始=当前总评（改总评不联动已改分项）；
- 「提交点评」→ 逐张上传 → `POST /review {dishId|menuId, starRating, text, images[], dimensionScores:{dimId:score}}` → toast「已点评」→ onSuccess pop。

### 7.2 食集统一评价页（pages/review/menu_review_page.dart）

- `/menu/{menuId}/review`；头部首字块+食集名+「N 道菜 · 完成于 M/D」；
- 「这顿饭怎么样？」整体卡：未评「还没有评价」+「评价 →」；已评=星级+「已评 ✓」绿+维度摘要（「口味 ★★★★☆ · 难度 ★★★☆☆」，维度 id 映射 {1 口味,2 难度,3 营养均衡,4 外观}）+「修改」→ `/menu/{id}/review-form`；
- 菜品列表：40px 封面（首字占位）+菜名+已评（星+绿「已评」）/灰「未评」+「评价」按钮→`/dish/{dishId}/review`；整行可点；
- 底部提示条（highlight）：「评过的菜会更新菜谱评分，以后找菜、避雷都用得上。」
- 接口：`GET /review/menu-overview/{menuId}`。

### 7.3 食集评价表单页（menu_review_form_page.dart）

- `/menu/:id/review-form`（传 menuName）；食集头+ReviewForm（menuId 模式，无星级标题）；成功 pop 回评价页。

### 7.4 我的评价（pages/review/my_reviews_page.dart）

- 「我的」→「我的评价」；下拉刷新；
- **待评价区**（有才显示）：食集名+「待评价/部分已评」（黄）+「去评价 →」（primary）→ `/menu/{id}/review`；
- **我的评价区**：类型角标（食集=highlight 底主色字/菜=白底灰字）+名称+时间 M/D+右侧 5 星；点击→对应评价页；
- 空态「还没有评价，做完一顿饭顺手评一下」；
- 接口：`GET /review/mine` → {reviews[], pendingMenus[]}。

### 7.5 单菜均分

`GET /review/dish/{dishId}/avg` → `{star(字符串), count}`（菜谱详情用）。

---

## 八、食材管理

### 8.1 列表页（pages/ingredient/list_page.dart）

- 操作行：返回箭头+右对齐「+ 添加」胶囊（无大标题）；
- 搜索框「搜食材名」（**回车触发**，✕ 清空）+分类 chips（「全部 {total}」+purchase_category 字典，单选）；
- 列表（下拉+上拉分页 15→10/页）：行=食材名+品类标签灰底小字+副行（edible=1「点击编辑」/=2「非营养」/=3「非食用」）；
- 底部注脚「食材从「我的」进入；点击食材可编辑食用属性」；点行→编辑页；
- 接口：`GET /ingredient?keyword=&purchaseCategoryId=&pageNum=&pageSize=`；`GET /dict?group=purchase_category`（**内存缓存 5 分钟**）。

### 8.2 录入食材（create_page.dart）

- 食材名输入+右侧「AI 补全」按钮（橙底；`POST /ai/nutrition/fill {name}` 回填营养→toast「AI 已填充，请核对 (source)」）；
- 「采购分类（可选）」Tag 单选+「+ 自定义」（展开输入，提交时 `POST /dict` upsert 拿 id）；
- 「营养（每 100g，可选）」2 列网格（指标来自 metrics 字典）；
- 「保存」→ `POST /ingredient {ingredient:{name, purchaseCategoryId}, nutritions:[{metricId,value}]}` → toast「已保存」+pop。

### 8.3 编辑页（edit_page.dart）

- BackHeader：食材名+✕删除（确认「关联的用量记录会保留」→`DELETE /ingredient/{id}`）+副信息（首字块+品类）；
- 「食用属性」卡片（点弹三选：食用/饮料零食/生活用品）→「保存」→ `PUT /ingredient {id, edible}` → pop。

---

## 九、成员管理（pages/member/member_list_page.dart）

- 「我的」两处入口；**App 端只读+切换当前就餐成员**（档案编辑在 admin 后台）；
- 成员卡：首字头像+姓名+副信息（特殊人群在前·角色标签在后「高血压 · 掌勺 · 备菜」）+右侧「当前」绿胶囊/「切换」；
- 点行→`POST /member/current?memberId=`（写后端 session，影响点评/营养统计）→ toast「已切换为 {name}」；
- 空态「暂无成员，请先在后台添加」；
- 接口：`GET /member`（**兼容数组或 IPage 两种返回**）、`GET /member/current`、`POST /member/current`；
- 全局状态：MemberStore（members/currentId/currentName），小程序用全局 store，切换后全局生效。

---

## 十、聚餐（一起吃 + 朋友点菜）

> **重要发现**：APP 内只有**发起端**（食集详情「聚餐」Tab）；**朋友点菜页不在 App 内**，是 H5 `together.html`（`{baseUrl}/together.html?token=` 或输口令；访客走 `X-Guest-Key` 请求头免登录）。

### 10.1 聚餐 Tab（发起端，detail_page.dart）

**页面结构**
1. 邀请卡（完成态隐藏）：未生成=「邀请朋友一起点菜」+「生成邀请」；已生成=左侧 88px 二维码（内容 H5 链接带 token）+「邀请朋友 · 口令 {code}」+「扫码 / 点链接 / 输口令，同一邀请三选一即可」+「复制口令」「分享链接」两胶囊；
2. 成员区：「成员 · N」chips（首字圆头像+昵称+相对时间）；空态「还没有人加入，先邀请朋友吧」；
3. 动态区：「{昵称} 点了/删了「{菜名}」」+相对时间；空态「暂无动态」；
4. 底部说明：「朋友扫码 / 点链接 / 输口令都能加入，加菜直接进菜 Tab（标「XX 点的」），谁都能删，会记下谁删的。清单每 10 秒自动刷新。」

**交互**
- 生成邀请：`POST /menu/{id}/invite` → {code, token} → toast「邀请已生成，口令 {code}」；
- 复制口令：剪贴板=口令+不带 token 的入口地址；分享链接=系统分享带 token url；
- **10s 轮询** `GET /menu/{id}/together`（成员轮询即心跳）；失败即停；首次失败=错误页+重试+登录态诊断；
- 完成态：邀请卡隐藏、动态只读。

**接口**：`POST /menu/{menuId}/invite`、`GET /menu/{menuId}/together`（members/dishes/activities/invite）、`POST /menu/{menuId}/together/items`（dishId 或 customName+note，朋友端用）、`DELETE /menu/{menuId}/together/items/{menuDishId}`、`GET /menu/{menuId}/together-count`（Tab 角标）。

### 10.2 朋友端（小程序侧方案）

- **推荐**：小程序码方案——落地页从 scene/query 取 token 免登录加入（对应 H5 的 X-Guest-Key，**需后端补小程序侧凭证支持**）；
- 保留 H5 兼容：分享卡片也可带 H5 链接（域名白名单）；
- 轮询：小程序 `setInterval` 10s，onHide 暂停/onShow 恢复并立即拉一次；
- 分享：`onShareAppMessage` 路径带 token + `wx.setClipboardData` 复制口令。

---

## 十一、我的 Tab（pages/profile_page.dart）

- 用户头部卡（可点→`/members`）：56px 渐变头像图标+昵称（空「掌勺人」）+「当前就餐：xx」（成员名→昵称→「选择就餐成员 ›」兜底）+右箭头；
- 功能卡：家庭成员（value=当前成员名）/食材库/采购清单/食记/写菜谱/草稿箱（**橙色角标=草稿数**，接口失败不显示；小程序建议 onShow 刷新）/我的评价/主题外观（占位）/关于小食单（v1.0.0）；
- 退出登录（红字）→ `auth.logout()`；
- 小程序：Provider→全局 store；登录态/就餐成员全局响应式。

---

## 十二、修正与裁剪说明（对总览文档的更正）

1. **备餐计划（mealplan）不存在**：APP 无 `/mealplan` 路由与页面（mealplan_service.dart 未被前端使用），小程序**不做**该模块；总览文档 3.6 中的备餐计划行删除。
2. **朋友点菜页不在 APP 内**（H5 together.html）：小程序朋友端走小程序码/分享卡片 + 免登录凭证（后端需支持），见 §10.2。
3. **dailylog（每日饮食）入口在「更多」页**，与「食记」（food-log，「我的」入口）是两个模块，均迁移。
4. 分页统一按 DESIGN.md §12.2 **10 条/页**（原 APP 部分页面 15/20，小程序按 10 实现；`docs/design/DESIGN.md` 已定稿）。
