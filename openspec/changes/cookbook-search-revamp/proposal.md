# Proposal: 依照原型 cookbook-search.html 重构菜谱页

**Change ID:** `cookbook-search-revamp`
**Created:** 2026-08-04
**Status:** Draft

> **原型文档（交叉引用）**：
> [`../../../.superpowers/brainstorm/44829-1783002708/content/cookbook-search.html`](../../../.superpowers/brainstorm/44829-1783002708/content/cookbook-search.html)
>
> 原型为双屏设计稿：左=菜谱库浏览+搜索，右=按食材找（库存匹配）。
> **⏸ 原型右屏「按食材找」已标注暂停使用，本期只做左屏按菜品搜。**

---

## Problem Statement

当前菜谱页（`menu-flutter/lib/pages/dish/list_page.dart`）功能简陋，与原型设计差距较大：

- **缺少分类标签条**：原型有横向「全部/家常/快手/宝宝」标签筛选，现页面没有。
- **缺少排序**：原型有「做过最多 ▾」排序切换，现页面只按创建时间倒序。
- **列表项信息不足**：原型显示「做过 6 次 · 10 分 · 185 kcal」，现页面只有「菜系 · 时间 · 难度」，没有"做过次数"。
- **代码遗留**：list_page.dart 注释仍写"复刻 menu-mini/src/pages/dish/List.vue"，App 应独立设计，不再参考小程序。

谁受影响：所有用 App 找菜的用户。

## Proposed Solution

### 菜谱库搜索重构（list_page.dart 重写）
按原型左屏结构重做（右屏「按食材找」已暂停）：
1. **AppBar**：title「菜谱」（不设筛选按钮——按食材找暂停，无需入口）
2. **搜索框**：主色描边（`border: BorderSide(color: t.primary)`），保留下拉（菜名/食材）+ ✕ 清除
3. **分类标签条**：横向滚动，`GET /dict?group=tag` 拉标签，首项固定「全部」。选中=深色实心（`t.title` 底白字），未选=描边。点标签→带 tagIds 重新 search
4. **排序行**：左「X 道」结果计数 + 右「做过最多 ▾ / 最新 ▾」PopupMenuButton 切换
5. **列表项**：44px 圆角（rXl=22）缩略图（无图用 `t.secondary` 底+餐厅图标）+ 菜名(13/800) + 「做过 N 次 · X 分 · 难度X」(10/caption)

### 后端：补"做过次数"
原型列表项要显示「做过 N 次」，后端 `GET /dish/search` 当前不返回此数据：
- `Dish.java` 加 `@TableField(exist=false) Integer cookedCount`
- `DishService.search` 新增 `fillCookedCount`，一条 SQL 按 member_id 批量 count cooking_record 回填
- `DishSearchDTO` 加 `String sort`（cooked=做过最多；默认 createTime 倒序）

## Scope

### In Scope
- 菜谱库重构：分类标签条 + 排序 + 做过次数显示
- 后端 `/dish/search` 补 `cookedCount` 字段 + `sort` 参数
- 删除 list_page.dart 里"复刻 menu-mini"等过时注释

### Out of Scope
- **⏸ 按食材找（右屏）暂停使用**：原型已标注暂停，本期不做。含「筛选」入口按钮、by_ingredients_page、cookbook_service 等，待后续恢复时再做。
- **不补"库存够 X/Y 样"**：pantry 匹配计算后续
- **不补列表项热量**：营养批量计算成本高，后续单独做
- **不参考小程序**：App 独立设计，不移植 menu-mini 代码

## Impact Analysis

| Component | Change Required | Details |
|-----------|-----------------|---------|
| Database | No | 不改表结构，cookedCount 从现有 cooking_record 表 GROUP BY 算 |
| API | Yes | `/dish/search` 返回 Dish 加 cookedCount；DishSearchDTO 加 sort/tagIds |
| State | No | 仍用页面级 setState，不引入新状态管理 |
| UI | Yes | list_page.dart 重写（分类标签条 + 排序 + 列表项） |

## Architecture Considerations

- **批量回填模式**：`fillCookedCount` 仿照现有 `fillRelNames`（一次 SQL 批量查，避免 N+1），与项目既有模式一致。
- **tag 筛选**：`GET /dict?group=tag` 拉标签，`DishSearchDTO.tagIds` 已支持（`addRelFilter` 方法现成），前端只需传参。
- **App 设计独立**：删除所有"复刻 menu-mini"注释，避免后续混淆。
- **交叉引用**：本提案通过相对路径引用 superpowers 原型，文档物理位置不动，逻辑关联。

## Success Criteria

- [ ] 菜谱页显示分类标签条，点标签能筛选
- [ ] 菜谱页列表项显示「做过 N 次 · X 分 · 难度X」
- [ ] 排序切换（做过最多/最新）生效
- [ ] 后端 `/dish/search` 返回 cookedCount（按当前成员统计）
- [ ] `flutter test` 全绿；后端测试全绿
- [ ] 真机验证通过

## Risks & Mitigations

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| cookedCount 按 member_id 统计，但当前会话可能无 currentMemberId | Med | Low | 无 member 时回填 0，不报错（现有 done/star 筛选也是同样容错） |
| tag 字典里没有原型展示的「低盐」| Low | Low | 前端只渲染后端返回的 tag，不硬编码标签名 |
