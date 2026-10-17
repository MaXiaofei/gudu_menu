/// 食材插画解析（规则单一来源：仓库根 docs/design/ingredient-icons.json，
/// 素材 key 清单：docs/design/assets.json；改规则后同步本文件，
/// test/core/ingredient_icons_test.dart 会校验两边一致）。
///
/// 解析顺序：后端 iconKey → 名称关键词 → 采购品类兜底 → null（调用方退首字）。
class IngredientIcons {
  IngredientIcons._();

  /// 已打包的插画 key（assets/ing/<key>.png）。
  static const Set<String> keys = {
    'beef', 'broccoli', 'cabbage', 'carrot', 'crab', 'cucumber', 'egg',
    'fish', 'garlic', 'ginger', 'lotus', 'milk', 'mushroom', 'noodle',
    'oil', 'pepper', 'pork', 'potato', 'pumpkin', 'ribs', 'rice', 'sauce',
    'scallion', 'shrimp', 'spinach', 'tissue', 'tofu', 'tomato', 'wing',
  };

  /// [key, 关键词...]，按数组顺序；取命中关键词最长的一项。
  static const List<(String, List<String>)> keywords = [
    ('tomato', ['番茄', '西红柿', '圣女果']),
    ('egg', ['鸡蛋', '鸭蛋', '鹌鹑蛋', '蛋']),
    ('scallion', ['葱', '香葱', '小葱', '韭菜', '蒜苗']),
    ('potato', ['土豆', '马铃薯', '红薯', '山药', '芋头']),
    ('broccoli', ['西兰花', '西蓝花', '菜花', '花菜']),
    ('fish', ['鱼', '鲈鱼', '鲫鱼', '带鱼', '三文鱼', '黑鱼']),
    ('spinach', ['菠菜', '油麦菜', '空心菜', '芹菜', '生菜']),
    ('tofu', ['豆腐', '香干', '腐竹', '豆皮']),
    ('pork', ['五花肉', '猪肉', '肉末', '里脊', '培根', '火腿', '腊肠']),
    ('milk', ['牛奶', '酸奶', '奶油', '淡奶', '奶粉']),
    ('ginger', ['姜']),
    ('garlic', ['蒜', '大蒜', '蒜瓣']),
    ('shrimp', ['虾', '虾仁', '大虾', '龙虾']),
    ('wing', ['鸡翅', '翅中', '翅根', '鸡腿', '鸡肉', '鸡胸', '鸡']),
    ('ribs', ['排骨', '肋排', '猪蹄', '棒骨', '羊排']),
    ('lotus', ['莲藕', '藕']),
    ('pumpkin', ['南瓜']),
    ('rice', ['大米', '米饭', '小米', '糯米', '米', '面粉', '燕麦']),
    ('sauce', ['酱油', '生抽', '老抽', '豉油', '醋', '料酒', '蚝油', '酱', '盐', '糖', '鸡精', '味精', '胡椒']),
    ('oil', ['油', '食用油', '香油', '麻油', '橄榄油']),
    ('carrot', ['胡萝卜', '萝卜']),
    ('pepper', ['青椒', '辣椒', '彩椒', '尖椒']),
    ('mushroom', ['蘑菇', '香菇', '金针菇', '杏鲍菇', '木耳', '平菇']),
    ('cabbage', ['白菜', '包菜', '卷心菜', '娃娃菜', '小白菜', '青菜']),
    ('noodle', ['面条', '挂面', '意面', '粉丝', '米粉', '年糕']),
    ('crab', ['蟹', '螃蟹', '梭子蟹']),
    ('beef', ['牛肉', '牛腩', '肥牛', '羊肉']),
    ('cucumber', ['黄瓜', '丝瓜', '苦瓜', '西葫芦', '冬瓜']),
    ('tissue', ['纸巾', '抽纸', '保鲜膜', '洗洁精']),
  ];

  /// 采购品类名 → key。
  static const Map<String, String> categoryFallback = {
    '蔬菜': 'cabbage',
    '水果': 'tomato',
    '畜禽肉': 'pork',
    '水产海鲜': 'fish',
    '蛋类': 'egg',
    '乳制品': 'milk',
    '调味料': 'sauce',
    '谷物主食': 'rice',
    '坚果': 'rice',
    '干菌藻': 'mushroom',
    '速冻': 'noodle',
    '饮料': 'milk',
    '肉禽蛋': 'pork',
    '肉类': 'pork',
    '禽蛋': 'egg',
    '水产': 'fish',
    '调料': 'sauce',
    '调味品': 'sauce',
    '粮油': 'rice',
    '主食': 'rice',
    '米面粮油': 'rice',
    '乳品': 'milk',
    '奶制品': 'milk',
    '豆制品': 'tofu',
    '菌菇': 'mushroom',
    '日用': 'tissue',
    '其他': 'tissue',
  };

  /// 名称关键词匹配（与后端 V68 回填规则相同）。
  static String? matchName(String name) {
    final n = name.trim();
    if (n.isEmpty) return null;
    String? best;
    var bestLen = 0;
    for (final (key, words) in keywords) {
      for (final w in words) {
        if (w.length > bestLen && n.contains(w)) {
          best = key;
          bestLen = w.length;
        }
      }
    }
    return best;
  }

  /// 解析最终 key；返回 null 表示无插画（退首字）。
  static String? resolve({String? iconKey, String? name, String? category}) {
    if (iconKey != null && keys.contains(iconKey)) return iconKey;
    final byName = name == null ? null : matchName(name);
    if (byName != null) return byName;
    if (category != null) return categoryFallback[category.trim()];
    return null;
  }

  static String asset(String key) => 'assets/ing/$key.png';
}
