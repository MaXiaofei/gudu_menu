# 下厨房菜谱用料补全

2026-08 导入的 308 道下厨房菜谱，导入脚本（`xiachufang/scraper/import.js`）只关联了与食材库名称完全一致的食材，
其余食材只拼进了 `dish.note` 的「用料：…」。结果：22 道菜「用料」为空，大部分菜只有 1～3 样用料，备注里是一长串原始用料。

本脚本按 note 里的原始用料补齐 `dish_ingredient`：

| 文件 | 作用 |
|---|---|
| `recipe_fix_map.py` | 映射表：原始名 → 食材库名称（「盐→食盐」「葱姜蒜→小葱/生姜/大蒜」「清水→跳过」）+ 新增食材清单 |
| `gen_recipe_fix.py` | 生成器：读线上导出 → 输出单事务 SQL |
| `rollback_recipe_fix.sql` | 撤回：按 `recipe_fix_log` 还原（新增食材已被用到的保留） |
| `test_recipe_fix.py` | 自检（不连数据库）：`python3 -m unittest test_recipe_fix` |

生成的 SQL 做四件事，全部登记到 `recipe_fix_log`（批次 `xcf-ingredients-20261027`）：
1. 新增食材库里没有的常见食材（生姜、生抽、低筋面粉、香叶……，营养数据留空），插画 key 复用 V68 规则回填；
2. 补用料：只补公共菜谱，同一道菜已有该食材的跳过；「适量/少许」记为数量 0 + 单位；
3. 清掉 note 里的用料清单（来源已单独展示，只剩「来源：下厨房」时置空）；
4. 下架「农家一碗香」（步骤只有图片没有文字、用料只有「肉」），软删除。

## 用法

```bash
# 1) 从目标库导出（制表符分隔，无表头）
#    dish.tsv           select id,source_url,note from dish where deleted=0 and family_id is null and source_name='下厨房';
#    ingredient.tsv     select id,name,deleted from ingredient;
#    dish_ing.tsv       select d.id,d.source_url,g.name from dish d join dish_ingredient i on i.dish_id=d.id join ingredient g on g.id=i.ingredient_id;
#    unit.tsv           select name from sys_dict where dict_group='unit';
# 2) 生成（有无法识别的原始名会列出并退出，补进 recipe_fix_map.py 再跑）
python3 gen_recipe_fix.py --dish dish.tsv --ingredient ingredient.tsv --dish-ingredient dish_ing.tsv \
  --unit unit.tsv --v68 ../../menu-api/sql/V68__ingredient_icon_key.sql --out recipe_fix.sql
# 3) 执行 / 撤回（先在数据副本上演练）
mysql --default-character-set=utf8mb4 <库名> < recipe_fix.sql
mysql --default-character-set=utf8mb4 <库名> < rollback_recipe_fix.sql
```

重复执行补全 SQL 不会重复插入，但会重复登记 note 清理（第二次 note 已无用料清单，不会命中）。
