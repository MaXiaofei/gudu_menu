# Implementation Tasks: 菜谱页按原型重构（只做按菜品搜）

**Change ID:** `cookbook-search-revamp`
**原型**：[`../../../.superpowers/brainstorm/44829-1783002708/content/cookbook-search.html`](../../../.superpowers/brainstorm/44829-1783002708/content/cookbook-search.html)
**范围说明**：原型右屏「按食材找」已标注暂停使用，本期只做左屏按菜品搜。

---

## Phase 1: 后端 - 补做过次数 + 排序

- [ ] 1.1 `Dish.java` 加 `@TableField(exist=false) private Integer cookedCount;`
- [ ] 1.2 `DishSearchDTO.java` 加 `private String sort;`（可选：cooked / 默认 createTime 倒序）
- [ ] 1.3 `DishService.search` 新增 `fillCookedCount(List<Dish>, Long memberId)`：
  - 一条 SQL：`SELECT dish_id, COUNT(*) c FROM cooking_record WHERE member_id=? AND dish_id IN(...) GROUP BY dish_id`
  - 回填每条 Dish.cookedCount（没做过=0）
  - 在 search 两个返回点（fillRelNames 之后）调用
- [ ] 1.4 `DishService.search` 按 `sort=cooked` 时调整排序
- [ ] 1.5 后端测试：`DishServiceTest` 补 cookedCount 回填断言 + sort 排序断言

**Quality Gate:**
- [ ] `mvn test` 全绿
- [ ] cookedCount 无 member 时容错为 0

---

## Phase 2: 前端 - 数据层（model + service）

- [ ] 2.1 `models/dish.dart`：Dish 加 `final int cookedCount` + fromJson 解析（默认 0）
- [ ] 2.2 `services/dish_service.dart`：`search` 加 `tagIds`、`sort` 参数，透传 query
- [ ] 2.3 单元测试：Dish.fromJson 含 cookedCount

**Quality Gate:**
- [ ] `flutter test` 现有用例不回归
- [ ] model 解析容错（cookedCount 缺省=0）

---

## Phase 3: 前端 - 菜谱库重构

- [ ] 3.1 `list_page.dart` AppBar：title「菜谱」（**不放筛选按钮**——按食材找暂停）
- [ ] 3.2 搜索框：主色描边（InputBorder borderSide: t.primary），保留下拉（菜名/食材）+ ✕
- [ ] 3.3 分类标签条：横向滚动，`GET /dict?group=tag` 拉标签，首项「全部」固定。选中=`t.title`底白字，未选=描边。点标签→tagIds 重新 search
- [ ] 3.4 排序行：左「X 道」计数 + 右 PopupMenuButton（做过最多/最新），切排序重新 search
- [ ] 3.5 列表项 `_DishTile` 重做：44px rXl 圆角缩略图（无图用 t.secondary 底+Icons.restaurant）+ 菜名(13/800) + 「做过 N 次 · X 分 · 难度X」(10/caption)
- [ ] 3.6 删除"复刻 menu-mini"等过时注释
- [ ] 3.7 widget 测试更新：browsing_flow_test 断言新列表项文案、分类标签条渲染

**Quality Gate:**
- [ ] `flutter analyze` 无 error
- [ ] 分类标签点击筛选生效
- [ ] 排序切换生效

---

## Phase 4: 部署 + 真机验证

- [ ] 4.1 后端同步到远程 staging 9090，重建容器（rsync src + docker compose up -d --build）
- [ ] 4.2 curl 验证 `/dish/search` 返回 cookedCount
- [ ] 4.3 Flutter 重新构建安装到模拟器
- [ ] 4.4 真机验证：分类标签筛选、做过次数、排序

**Quality Gate:**
- [ ] 后端 deployed，新字段可验证
- [ ] 模拟器真机点一遍全流程通过

---

## Completion Checklist

- [ ] 所有 Phase 完成
- [ ] flutter test + 后端 mvn test 全绿
- [ ] 真机验证通过
- [ ] 删除过时注释（不参考小程序）
- [ ] Ready for `/openspec-archive`

---

> **⏸ 暂停项（不在本期范围）**：原型右屏「按食材找」（by_ingredients_page、cookbook_service、dish_match model、「筛选」入口按钮、库存匹配）。待后续恢复时另起 change。
