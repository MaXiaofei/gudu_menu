// 截图用 mock 数据（与原型 docs/design/prototype 的演示家庭一致）。
import 'harness.dart';

Map<String, dynamic> dishJson(int id, String name, String slug,
        {int cook = 20, int cooked = 0, Map<String, dynamic>? coverage, List<String> tags = const []}) =>
    {
      'id': id,
      'name': name,
      'cookTime': cook,
      'difficulty': 2,
      'coverUrl': img('dish/$slug.jpg'),
      'cookedCount': cooked,
      'tagNames': tags,
      if (coverage != null) 'coverage': coverage,
    };

Map<String, dynamic> cov(int enough, int total, [List<String> missing = const []]) => {
      'enough': enough,
      'total': total,
      'missing': [for (final m in missing) {'name': m}],
    };

final luyuDetail = {
  'dish': {
    ...dishJson(2, '清蒸鲈鱼', 'qingzheng-luyu', cook: 25, cooked: 4, tags: ['适合高血压', '少油', '清淡']),
    'sourceName': '我家的',
    'note': '鲈鱼要新鲜，蒸的时间宁短勿长。妈妈的做法：出锅后淋热油激香葱丝。',
  },
  'steps': [
    for (var i = 1; i <= 5; i++)
      {
        'seq': i,
        'text': [
          '鲈鱼洗净，两面各划三刀，铺姜片腌 10 分钟。',
          '鱼身切片摆成开屏状，鱼头立在盘首。',
          '水开后上锅，大火蒸 8 分钟，关火焖 2 分钟。',
          '倒掉盘中蒸出的汤水，淋蒸鱼豉油。',
          '铺葱丝，浇一勺热油激香即可。',
        ][i - 1],
        'images': img('step/qingzheng-luyu-${i + 2}.jpg'),
      }
  ],
  'ingredients': [
    {'ingredientId': 11, 'ingredientName': '鲈鱼', 'amount': 1, 'unitName': '条（约 500g）', 'stockLevel': 'ENOUGH'},
    {'ingredientId': 12, 'ingredientName': '姜', 'amount': 1, 'unitName': '块', 'stockLevel': 'ENOUGH'},
    {'ingredientId': 13, 'ingredientName': '蒸鱼豉油', 'amount': 2, 'unitName': '勺', 'stockLevel': 'LOW'},
    {'ingredientId': 14, 'ingredientName': '食用油', 'amount': 1, 'unitName': '勺', 'stockLevel': 'ENOUGH'},
    {'ingredientId': 15, 'ingredientName': '小葱', 'amount': 3, 'unitName': '根', 'stockLevel': 'NONE'},
  ],
};

final recipeList = {
  'records': [
    {...dishJson(1, '滑蛋虾仁', 'huadan-xiaren', cook: 10, cooked: 12, coverage: cov(3, 3), tags: ['宝宝可吃']),
      'lastCookedAt': DateTime.now().subtract(const Duration(days: 3)).toIso8601String(), 'lastCookedBy': {'name': '妈妈'}},
    {...dishJson(2, '清蒸鲈鱼', 'qingzheng-luyu', cook: 25, cooked: 4, coverage: cov(4, 5, ['小葱']), tags: ['适合高血压'])},
    {...dishJson(3, '莲藕排骨汤', 'lianou-paigu', cook: 90, coverage: cov(2, 4, ['莲藕', '玉米'])), 'sourceName': '公共库'},
    {...dishJson(4, '红烧肉', 'hongshao-rou', cook: 60, cooked: 2, coverage: cov(1, 5, ['五花肉', '冰糖', '八角', '小葱']))},
    {...dishJson(5, '蒜蓉西兰花', 'suanrong-xilanhua', cook: 8, cooked: 9, coverage: cov(2, 2))},
  ],
  'total': 32,
};

final members = [
  {'id': 1, 'name': '小王'},
  {'id': 2, 'name': '林林', 'healthProfile': {'audiences': ['高血脂']}},
  {'id': 3, 'name': '妈妈', 'healthProfile': {'audiences': ['高血压']}},
  {'id': 4, 'name': '朵朵', 'healthProfile': {'audiences': ['宝宝辅食']}},
];

Map<String, dynamic> card(int id, String name, String slug, {int min = 20, int cooked = 0, Map<String, dynamic>? coverage}) => {
      'dishId': id,
      'name': name,
      'coverUrl': img('dish/$slug.jpg'),
      'cookMinutes': min,
      'cookedCount': cooked,
      if (coverage != null) 'coverage': coverage,
    };

String _d(int days) => DateTime.now().add(Duration(days: days)).toIso8601String().substring(0, 10);

final recommendHome = {
  'expiring': [
    {'ingredientId': 11, 'ingredientName': '鲈鱼', 'level': 'ENOUGH', 'bestBefore': _d(1), 'daysLeft': 1},
    {'ingredientId': 21, 'ingredientName': '菠菜', 'level': 'LOW', 'bestBefore': _d(2), 'daysLeft': 2},
    {'ingredientId': 22, 'ingredientName': '北豆腐', 'level': 'ENOUGH', 'bestBefore': _d(2), 'daysLeft': 2},
  ],
  'expiringPicks': [
    card(2, '清蒸鲈鱼', 'qingzheng-luyu', min: 25, coverage: cov(4, 5, ['小葱'])),
    card(6, '凉拌菠菜', 'liangban-bocai', min: 10, coverage: cov(4, 4)),
    card(7, '麻婆豆腐', 'mapo-doufu', min: 20, coverage: cov(3, 4, ['肉末'])),
  ],
  'frequent': [
    card(1, '滑蛋虾仁', 'huadan-xiaren', min: 10, cooked: 12),
    card(5, '蒜蓉西兰花', 'suanrong-xilanhua', min: 8, cooked: 9),
    card(8, '可乐鸡翅', 'kele-jichi', min: 30, cooked: 6),
  ],
  'seasonal': [
    card(9, '小鸡炖蘑菇', 'xiaoji-mogu', min: 60),
    card(10, '梭子蟹炒年糕', 'xiezi-niangao', min: 25),
    card(11, '蓝莓山药泥', 'shanyao-ni', min: 20),
  ],
};

final recentCooked = [
  {'dishId': 12, 'name': '土豆炖牛肉', 'coverUrl': img('dish/tudou-niurou.jpg'),
    'lastCookedAt': DateTime.now().subtract(const Duration(days: 1)).toIso8601String(), 'cookedCount': 5},
  {'dishId': 13, 'name': '牛奶南瓜羹', 'coverUrl': img('dish/nangua-geng.jpg'),
    'lastCookedAt': DateTime.now().subtract(const Duration(days: 2)).toIso8601String(), 'cookedCount': 8},
];

Map<String, dynamic> stock(int id, String name, String level, {int? days, String? src, String? cat}) => {
      'ingredientId': id,
      'ingredientName': name,
      'level': level,
      if (days != null) 'daysLeft': days,
      if (days != null) 'bestBefore': _d(days),
      if (cat != null) 'categoryName': cat,
      if (src != null) 'lastChange': {'source': src, 'createTime': DateTime.now().subtract(const Duration(days: 1)).toIso8601String()},
    };

final pantryAll = [
  stock(11, '鲈鱼', 'ENOUGH', days: 1, src: 'purchase'),
  stock(21, '菠菜', 'LOW', days: 2, src: 'cook_partial'),
  stock(22, '北豆腐', 'ENOUGH', days: 2, src: 'purchase'),
  stock(31, '番茄', 'ENOUGH', days: 6, src: 'purchase'),
  stock(32, '鸡蛋', 'LOW', days: 20, src: 'cook_partial'),
  stock(33, '土豆', 'ENOUGH', days: 12, src: 'manual'),
  stock(15, '小葱', 'NONE', src: 'cook'),
  stock(34, '牛奶', 'NONE', src: 'cook'),
  stock(35, '秋葵', 'ENOUGH', cat: '蔬菜', src: 'purchase'),
];

Object pantryResponder(String path, Map<String, dynamic> q) {
  if (path == '/pantry/grouped') {
    final level = q['level'] as String?;
    return {
      'summary': {'enough': 5, 'low': 2, 'none': 2},
      'items': pantryAll.where((it) => level == null || it['level'] == level).toList(),
    };
  }
  if (path == '/pantry/summary') return {'enough': 24, 'low': 6, 'out': 3, 'expiring3d': 3};
  if (path == '/pantry/expiring') return pantryAll.where((e) => (e['daysLeft'] as int? ?? 99) <= 3).toList();
  return {};
}

final menuDetail = {
  'menu': {'id': 5, 'name': '今晚的饭', 'servingCount': 4, 'status': 'ACTIVE', 'createTime': DateTime.now().toIso8601String()},
  'totalMinutes': 58,
  'dishes': [
    {'id': 1, 'menuId': 5, 'dishId': 2, 'servingFactor': 1.0, 'dishName': '清蒸鲈鱼', 'coverUrl': img('dish/qingzheng-luyu.jpg'), 'note': '妈妈那份少盐'},
    {'id': 2, 'menuId': 5, 'dishId': 1, 'servingFactor': 1.0, 'dishName': '滑蛋虾仁', 'coverUrl': img('dish/huadan-xiaren.jpg'), 'addedByNickname': '朵朵'},
    {'id': 3, 'menuId': 5, 'dishId': 5, 'servingFactor': 0.5, 'dishName': '蒜蓉西兰花', 'coverUrl': img('dish/suanrong-xilanhua.jpg'), 'addedByNickname': '林林', 'note': '少油'},
    {'id': 4, 'menuId': 5, 'dishId': 14, 'servingFactor': 1.0, 'dishName': '西红柿鸡蛋汤', 'coverUrl': img('dish/xihongshi-jidan-tang.jpg'), 'addedByNickname': '妈妈'},
  ],
};

final menuShop = {
  'id': 9,
  'items': [
    {'id': 1, 'ingredientName': '西兰花', 'purchaseAmount': 1, 'purchaseUnitName': '颗', 'purchased': 0, 'stockStatus': 'NONE'},
    {'id': 2, 'ingredientName': '小葱', 'purchaseAmount': 3, 'purchaseUnitName': '根', 'purchased': 0, 'stockStatus': 'NONE'},
    {'id': 3, 'ingredientName': '虾仁', 'purchaseAmount': 1, 'purchaseUnitName': '盒', 'purchased': 1, 'stockStatus': 'NONE'},
    {'id': 4, 'ingredientName': '鸡蛋', 'purchaseAmount': 2, 'purchaseUnitName': '个', 'purchased': 0, 'stockStatus': 'LOW'},
    {'id': 5, 'ingredientName': '番茄', 'purchaseAmount': 2, 'purchaseUnitName': '个', 'purchased': 0, 'stockStatus': 'ENOUGH'},
    {'id': 6, 'ingredientName': '鲈鱼', 'purchaseAmount': 1, 'purchaseUnitName': '条', 'purchased': 0, 'stockStatus': 'ENOUGH'},
  ],
};

String _ago(int d) => DateTime.now().add(Duration(days: d)).toIso8601String();

final menuList = {
  'records': [
    {'id': 5, 'name': '今晚的饭', 'servingCount': 4, 'status': 'ACTIVE', 'createTime': _ago(0), 'dishCount': 4,
      'covers': [img('dish/qingzheng-luyu.jpg'), img('dish/huadan-xiaren.jpg'), img('dish/suanrong-xilanhua.jpg')]},
    {'id': 6, 'name': '周六 · 爸妈来吃饭', 'servingCount': 6, 'status': 'ACTIVE', 'createTime': _ago(2), 'dishCount': 6,
      'covers': [img('dish/tangcu-paigu.jpg'), img('dish/youmen-daxia.jpg'), img('dish/hongshao-rou.jpg')]},
    {'id': 7, 'name': '朵朵一周辅食', 'servingCount': 1, 'status': 'ACTIVE', 'createTime': _ago(3), 'dishCount': 5,
      'covers': [img('dish/nangua-geng.jpg'), img('dish/zhengshuidan.jpg'), img('dish/xiaomi-zhou.jpg')]},
    {'id': 8, 'name': '工作日两菜一汤', 'servingCount': 3, 'status': 'DONE', 'createTime': _ago(-2), 'dishCount': 3,
      'covers': [img('dish/mapo-doufu.jpg'), img('dish/shousi-baocai.jpg'), img('dish/dongua-paigu.jpg')]},
  ],
  'total': 4,
};
