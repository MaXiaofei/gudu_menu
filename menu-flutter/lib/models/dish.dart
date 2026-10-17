/// 菜品（对应后端 Dish）。字段对齐小程序 dish/List、dish/Detail 的用法。
class Dish {
  final int id;
  final String name;
  final int? cookTime;
  final int? prepTime;
  final int? difficulty;
  final String? note;
  final String? coverUrl;
  /// 来源名（自己创建/下厨房/美食杰/豆果/抖音…，V49）。
  final String? sourceName;
  /// 第三方来源地址（导入时记录，V49）。
  final String? sourceUrl;
  /// 菜系/分类/标签名（后端 fillRelNames 回填，列表与详情均返回）。
  final List<String> cuisineNames;
  final List<String> categoryNames;
  final List<String> tagNames;
  /// 做过次数（按当前就餐成员统计，search 时回填，缺省 0）。
  final int cookedCount;
  /// 本家余量覆盖（「家里够 X/Y 样」，search 登录时回填；未登录/旧接口为 null）。
  final Coverage? coverage;
  /// 最近一次做的时间（prototype-ui-rollout P0 契约；旧接口为 null）。
  final DateTime? lastCookedAt;
  /// 最近一次谁做的（成员名；旧接口为 null）。
  final String? lastCookedBy;
  final String? lastCookedByAvatar;

  const Dish({
    required this.id,
    required this.name,
    this.cookTime,
    this.prepTime,
    this.difficulty,
    this.note,
    this.coverUrl,
    this.sourceName,
    this.sourceUrl,
    this.cuisineNames = const [],
    this.categoryNames = const [],
    this.tagNames = const [],
    this.cookedCount = 0,
    this.coverage,
    this.lastCookedAt,
    this.lastCookedBy,
    this.lastCookedByAvatar,
  });

  factory Dish.fromJson(Map<String, dynamic> j) => Dish(
        id: (j['id'] as num).toInt(),
        name: (j['name'] ?? '') as String,
        cookTime: (j['cookTime'] as num?)?.toInt(),
        prepTime: (j['prepTime'] as num?)?.toInt(),
        difficulty: (j['difficulty'] as num?)?.toInt(),
        note: j['note'] as String?,
        coverUrl: j['coverUrl'] as String?,
        sourceName: j['sourceName'] as String?,
        sourceUrl: j['sourceUrl'] as String?,
        cuisineNames: (j['cuisineNames'] as List?)
                ?.map((e) => e.toString())
                .toList() ??
            const [],
        categoryNames: (j['categoryNames'] as List?)
                ?.map((e) => e.toString())
                .toList() ??
            const [],
        tagNames:
            (j['tagNames'] as List?)?.map((e) => e.toString()).toList() ?? const [],
        cookedCount: (j['cookedCount'] as num?)?.toInt() ?? 0,
        coverage: j['coverage'] is Map
            ? Coverage.fromJson(j['coverage'] as Map<String, dynamic>)
            : null,
        lastCookedAt: j['lastCookedAt'] is String ? DateTime.tryParse(j['lastCookedAt'] as String) : null,
        lastCookedBy: j['lastCookedBy'] is Map ? (j['lastCookedBy']['name'] as String?) : null,
        lastCookedByAvatar: j['lastCookedBy'] is Map ? (j['lastCookedBy']['avatarUrl'] as String?) : null,
      );
}

/// 本家余量对一道菜用料的覆盖（api-contract §0 Coverage；调料不计入）。
class Coverage {
  /// 档位为 ENOUGH 或 LOW 的用料数。
  final int enough;
  /// 非调料用料总数。
  final int total;
  /// 家里没有（档位 NONE 或未建档）的用料名。
  final List<String> missingNames;

  const Coverage({
    required this.enough,
    required this.total,
    this.missingNames = const [],
  });

  factory Coverage.fromJson(Map<String, dynamic> j) => Coverage(
        enough: (j['enough'] as num?)?.toInt() ?? 0,
        total: (j['total'] as num?)?.toInt() ?? 0,
        missingNames: ((j['missing'] ?? const []) as List)
            .whereType<Map>()
            .map((e) => (e['name'] ?? '').toString())
            .where((s) => s.isNotEmpty)
            .toList(),
      );

  /// 全部用料家里都有。
  bool get isFull => total > 0 && enough >= total;

  /// 「家里够 X/Y 样」；无可计用料（全是调料）返回空串。
  String get label => total <= 0 ? '' : '家里够 $enough/$total 样';
}

/// 菜卡（api-contract §0 DishCard：智荐各分区用）。
class DishCard {
  final int dishId;
  final String name;
  final String? coverImage;
  final int? cookMinutes;
  final int? difficulty;
  final int cookedCount;
  final Coverage? coverage;

  const DishCard({
    required this.dishId,
    required this.name,
    this.coverImage,
    this.cookMinutes,
    this.difficulty,
    this.cookedCount = 0,
    this.coverage,
  });

  factory DishCard.fromJson(Map<String, dynamic> j) => DishCard(
        dishId: (j['dishId'] as num).toInt(),
        name: (j['name'] ?? '') as String,
        // P0 契约：coverUrl 优先，兼容旧字段 coverImage
        coverImage: (j['coverUrl'] ?? j['coverImage']) as String?,
        cookMinutes: (j['cookMinutes'] as num?)?.toInt(),
        difficulty: (j['difficulty'] as num?)?.toInt(),
        cookedCount: (j['cookedCount'] as num?)?.toInt() ?? 0,
        coverage: j['coverage'] is Map
            ? Coverage.fromJson(j['coverage'] as Map<String, dynamic>)
            : null,
      );
}

/// 做法步骤（对应后端 DishStep）。
class DishStep {
  final int? seq;
  final String text;
  final String? images; // 逗号分隔的多图相对路径

  const DishStep({this.seq, required this.text, this.images});

  factory DishStep.fromJson(Map<String, dynamic> j) => DishStep(
        seq: (j['seq'] as num?)?.toInt(),
        text: (j['text'] ?? '') as String,
        images: j['images'] as String?,
      );

  /// 步骤图列表（逗号分隔 → List）。
  List<String> get imageList => images == null || images!.isEmpty
      ? const []
      : images!.split(',').where((s) => s.trim().isNotEmpty).toList();
}

/// 菜品详情聚合（后端 DishDetail record：{dish, steps, cuisineIds, ..., ingredients}）。
class DishDetail {
  final Dish dish;
  final List<DishStep> steps;
  /// 用料明细（食材 id/名/用量克数）。详情页「用料」区展示。
  final List<DishIngredient> ingredients;

  const DishDetail({required this.dish, required this.steps, this.ingredients = const []});

  factory DishDetail.fromJson(Map<String, dynamic> j) => DishDetail(
        dish: Dish.fromJson(j['dish'] as Map<String, dynamic>),
        steps: ((j['steps'] ?? const []) as List)
            .map((e) => DishStep.fromJson(e as Map<String, dynamic>))
            .toList(),
        ingredients: ((j['ingredients'] ?? const []) as List)
            .map((e) => DishIngredient.fromJson(e as Map<String, dynamic>))
            .toList(),
      );
}

/// 菜品用料项（对应后端 DishIngredient：食材 id/名/用量原文）。
class DishIngredient {
  final int ingredientId;
  final String? ingredientName;
  /// 用量数量（对应单位个数，如 2 表示 2 个/2 把）。
  final double? amount;
  /// 自然单位名（后端按 unitId 回填；含「适量/少许/一小把」量词单位，§16.3）。
  final String? unitName;
  /// 库存档位 ENOUGH/LOW/NONE（家里：充足/不足/用完；后端批量回填）。
  final String? stockLevel;
  /// 食材插画 key（P0 契约，可空 → 客户端按名称/品类兜底）。
  final String? iconKey;

  const DishIngredient({
    required this.ingredientId,
    this.ingredientName,
    this.amount,
    this.unitName,
    this.stockLevel,
    this.iconKey,
  });

  factory DishIngredient.fromJson(Map<String, dynamic> j) => DishIngredient(
        ingredientId: (j['ingredientId'] as num?)?.toInt() ?? 0,
        ingredientName: j['ingredientName'] as String?,
        amount: (j['amount'] as num?)?.toDouble(),
        unitName: j['unitName'] as String?,
        stockLevel: j['stockLevel'] as String?,
        iconKey: j['iconKey'] as String?,
      );

  String get displayName => ingredientName ?? '#$ingredientId';

  /// 用量文案：自然单位优先（「2 个」「适量」）；无单位信息回落数字（当克理解）。
  String get amountText {
    final un = unitName;
    if (un != null && un.isNotEmpty) {
      final a = amount;
      if (a != null && a > 0) {
        // 整数不带小数
        final n = a == a.roundToDouble() ? '${a.toInt()}' : a.toStringAsFixed(1);
        return '$n $un';
      }
      return un; // 适量 / 少许（amount 为空）
    }
    final a = amount;
    if (a == null) return '';
    final n = a == a.roundToDouble() ? '${a.toInt()}' : a.toStringAsFixed(1);
    return '$n g';
  }
}
