# 咕嘟小食单 · 重设计流程评审报告

> **评审日期**：2026-07-02
> **评审动因**：UI 改版收尾、进入产品重做主线后，串联核心流程发现多处断链
> **评审范围**：`pantry` / `shopping` / `cookbook` / `menu` 四域的自动扣减、采购生成、份数缩放、单位语义
> **结论先行**：当前代码与产品重做存档决策存在系统性偏差——**三条核心流程"存档说做了、代码没做"**，且"自动计算"的地基（单位模型）自相矛盾。本报告固化结论，避免下次被代码注释里的 "redesign" 字样再次误导。

---

## 一、P0 流程断链（存档决策 ≠ 代码现状）

### 1. 「做菜自动扣减库存」—— 未实现

| 项 | 内容 |
|---|---|
| 用户原话 | `docs/咕嘟小食单改版文档.tx:13`「不知道该怎么设计**消耗的流程**才能让家里的食材余量准确些」 |
| 存档决策 | `gudu-product-redesign.md:27`「双轨三色库存：**auto扣减(做菜按 DishIngredient 扣 pantry)** + 手动盘点」 |
| 代码现状 | `CookbookService.markDone()`(`cookbook/CookbookService.java:61-68`) 只 `cookingRecordMapper.insert(r)` 写一条记录，**无任何 pantry 扣减调用**；`PantryService.deduct(id, amount)`(`pantry/PantryService.java:161`) 只支持手动单条扣减；全局搜不到 `cooking_record → pantry` 的事件/AOP/Service 互调 |
| 后果 | 用户做一万次菜，库存余量纹丝不动。双轨三色目前只有"手动盘点"一轨在跑，**直接打掉用户写在改版文档里的第一痛点** |

### 2. 「采购清单全量 + 余量红黄绿标记」—— 未实现

| 项 | 内容 |
|---|---|
| 存档决策 | `gudu-product-redesign.md:32`「从食集派生拉**全量**用料，每项标记余量(**家里够=绿/有但差X=黄/没有=红**)，用户自己删家里够的」 |
| 代码现状 | `ShoppingService.generate()`(`shopping/ShoppingService.java:106-159`) 全程**不读 pantry**，只算 `referenceGrams` 就落库；`ShoppingItemVO` 无余量状态字段；`fillVoNames()` 不填任何 stock 状态；`ShoppingAggregator` 注释挂着 "redesign"，算法仍是 V20 旧合并逻辑 |
| 澄清 | `gudu-product-redesign.md:41` 说「~~ShoppingAggregator 加 pantry 减法~~(取消)」——取消的是**减库存**，**余量标记仍要做**。这两件事被搞混，结果余量标记一起被省了 |
| 后果 | 用户看不到"家里够不够"，全量采购单 = 一份和库存无关的用料清单，差异化体验为零 |

### 3. 「备菜模块」—— 不存在

| 项 | 内容 |
|---|---|
| 存档决策 | `gudu-product-redesign.md:31`「食集详情页独立兄弟 Tab=菜/备菜/采购/协同；新增 `menu_prep_status` 表；`GET /menu/{id}/prep` + `PUT /prep/{ingredientId}`」 |
| 代码现状 | 全代码库无 prep 相关文件、表、接口 |
| 后果 | 食集详情页四 Tab 里只有"菜"是活的，"备菜/采购/协同"三 Tab 都是空壳 |

---

## 二、P0 计算根基：单位语义自相矛盾

最隐蔽、最致命的一类——**不是少写了接口，而是字段命名/注释骗了调用方**，所有"计算"都建立在错误前提上。

### 4. `dish_ingredient.amount` 到底是不是克？三方口径不一致

| 文件 | 字段 | 实际含义 |
|---|---|---|
| `DishIngredient.java:25` | `amount` 注释 | 「用量**克数**」 |
| `Pantry.java:28` | `unitId` | 指向 `sys_dict(group=unit)` |
| `UnitMatcher.java:21-57` | 返回值 | 「个/把/斤/瓶/盒/颗」——**没有一个是克** |
| `ShoppingService.java:135` | `amount.multiply(factor)` | 直接把 amount 当克累加成 `referenceGrams` |

**矛盾**：
- 若菜谱真的统一录克数 → pantry 走 `UnitMatcher` 推断出"个/把/斤"，**两边单位对不上**，自动扣减/余量标记做不了（必须换算）；
- 若菜谱实际录"个/把/斤"（更可能，因为 `UnitMatcher` 就是这么推的）→ `DishIngredient` 注释撒谎、`referenceGrams` 命名撒谎、采购清单"合计克数"毫无意义（2 个鸡蛋 + 1 斤肉 = "3"？）。

存档把"单位换算服务"列为 gap（`gudu-product-redesign.md:44`），但 **`referenceGrams` 已经在生成并展示给前端**——换算没做，意味着**现在每一个采购行的参考克数都是错的**。

### 5. pantry「多批次」/「单条扣减」/「食材聚合判定」三套语义打架

- pantry 按**入库批次**存（一笔一行，各有 `expireDate`/`lowThreshold`），同一食材多笔很常见（今天买一盒鸡蛋，明天又买一盒）。
- `PantryService.deduct(id, amount)` 是**按 pantry.id 扣单条**。
- 但「做菜扣减」必然按 `ingredientId` 反查多条 → **多条之间怎么扣？FIFO 按过期日？平均？合并？** ——没有任何逻辑。
- 余量标记同样要按 `ingredientId` 汇总多条 → `listLow()`(`PantryService.java:107`) 现在按**单条 pantry** 判 `amount < low_threshold`，同食材多条时阈值判定也是糊的。

**结论**：库存模型是批次制、扣减接口是单条制、需求是食材聚合制——三套语义打架，auto 扣减就算挂上钩也跑不对。

---

## 三、P1 具体漏洞

### 6. `servingFactor` 累加 → 用户误操作就翻倍采购

`ShoppingService.java:117-119`：
```java
factorByDish.merge(du.dishId, du.servingFactor, BigDecimal::add);
```
同一 `dishId` 在 `menu_dish` 出现 N 次，factor 相加。设计意图是"午餐做一次+晚餐做一次=2 份"，但**用户把同一道菜误加入食集两次，采购量直接 ×2 且无任何提示**。建议：menu 数据源下先按 dishId 聚合并提示"该菜已有，是否增加份数"。

### 7. `markDone` 不接收份数 → 将来挂钩扣减也扣不对

`CookbookService.markDone(memberId, dishId, note)`(`CookbookService.java:61`) 无 `servingFactor` 参数；`cooking_record` 表也无该字段。即使现在补上 auto 扣减钩子，**做 1 人份还是 4 人份无从得知**，只能默认扣 1 份。

### 8. `markDone` 无来源 / 无防误触 / 无幂等

- 每点一次"做完了"写一条 cooking_record，没确认/去重。存档说"做菜次数 = count(*)"（`gudu-product-redesign.md:38`），误点会污染次数。
- cooking_record 不关联 `menu_id`/`menu_dish_id`。存档（`gudu-product-redesign.md:28`）说"单菜直做 vs 食集双流程都写共享 cooking_record 表"，**但没字段区分来源**，将来"这顿饭做了哪几道"无法回溯。

### 9. 采购清单丢了 menu/dish 的溯源（违反自己定的铁律）

`ShoppingService.newList`(`ShoppingService.java:222`)：
```java
list.setSourcePlanId("plan".equals(sourceType) ? sourceId : null);
```
铁律「采购清单从**食集/餐程**派生，记溯源」(`gudu-product-redesign.md:30`)，但 menu 来源反而把 sourceId 丢了。`ShoppingList` 只有 `sourcePlanId`，**没有 `sourceMenuId`**——从食集生成的清单查不回来源。

### 10. 改版文档明确要的「复制文字/转图片分享」未实现

`docs/咕嘟小食单改版文档.tx:18`「还能让我复制文字或者变成图片分享给别人让他们买菜」。`ShoppingController` 无导出/分享接口。

### 11. `MenuDish` 语义歧义（影响协同点菜去重）

一条 `MenuDish` = 一次加入，`servingFactor` 在同一行。"加两次"和"加一次但份数=2"语义等价但数据形态不同（一条 vs 两条）。第 6 条的 factor 相加把它们强行统一，**但协同点菜场景下朋友各自点同一道菜，到底合并一行还是多行**——没规则。直接影响协同去重逻辑。

### 12. 价格计算的单位换算同样缺失

`MenuCalcService.totalPrice`(`MenuCalcService.java:21`) 用 `price × servingFactor`。但 `ingredient.price` 的计价单位（500g？1 个？1 斤？）与 `dish_ingredient.amount`（号称克）关系未定义。改版文档 line 24「按用量计算价格」——当前只能算对，前提是录入时人肉保证单位一致，**无任何校验或换算兜底**。

---

## 四、修复优先级（地基优先，自下而上）

```
①单位换算服务（地基，阻塞 ②③④）
    ├─ ②auto 扣减触发链 + 多批次 FIFO 策略（依赖 ①）
    ├─ ③采购余量红黄绿标记（依赖 ①，按 ingredientId 聚合 pantry）
    └─ ④价格按用量计算（依赖 ①）
⑤补齐：备菜模块、采购溯源(sourceMenuId)、分享/导出、cooking_record 来源字段
⑥精细原型：建立在 ①②③ 之上（单位怎么录、扣减弹什么 modal、余色怎么标，都由 ①②③ 决定）
```

**最高杠杆点 = ①单位换算服务**：它同时阻塞扣减、余色、价格三件事，且决定了原型的录入交互。先做 ① 的方案设计，再推其余。

---

## 五、与存档的对齐动作

本报告写完后，同步更新 `gudu-product-redesign.md`：
- 决策 1（双轨三色）、5（备菜）、6（采购全量+余色）三条加「⚠️ 2026-07-02 评审：未落地」标注，指向本报告；
- 「后端已实现约 70%」段重新校准：70% 指旧 MVP，新重做的三条核心流程**均未落地**；
- 「待补 gap」段补入：auto 扣减钩子、采购余色标记、备菜模块、单位换算服务、cooking_record 来源字段、sourceMenuId。
