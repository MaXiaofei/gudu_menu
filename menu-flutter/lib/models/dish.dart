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
  final num? price;
  /// 菜系/分类/标签名（后端 fillRelNames 回填，列表与详情均返回）。
  final List<String> cuisineNames;
  final List<String> categoryNames;
  final List<String> tagNames;
  /// 做过次数（按当前就餐成员统计，search 时回填，缺省 0）。
  final int cookedCount;

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
    this.price,
    this.cuisineNames = const [],
    this.categoryNames = const [],
    this.tagNames = const [],
    this.cookedCount = 0,
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
        price: j['price'] as num?,
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

/// 菜品用料项（对应后端 DishIngredient：食材 id/名/用量克数）。
class DishIngredient {
  final int ingredientId;
  final String? ingredientName;
  /// 用量数量（对应单位个数，如 2 表示 2 个/2 把）。
  final double? amount;
  /// 自然单位名（后端按 unitId 回填；含「适量/少许/一小把」量词单位，§16.3）。
  final String? unitName;
  final double grams;
  /// 库存档位 ENOUGH/LOW/NONE（家里：充足/不足/用完；后端批量回填）。
  final String? stockLevel;

  const DishIngredient({
    required this.ingredientId,
    this.ingredientName,
    this.amount,
    this.unitName,
    this.grams = 0,
    this.stockLevel,
  });

  factory DishIngredient.fromJson(Map<String, dynamic> j) => DishIngredient(
        ingredientId: (j['ingredientId'] as num?)?.toInt() ?? 0,
        ingredientName: j['ingredientName'] as String?,
        amount: (j['amount'] as num?)?.toDouble(),
        unitName: j['unitName'] as String?,
        grams: (j['grams'] as num?)?.toDouble() ?? 0,
        stockLevel: j['stockLevel'] as String?,
      );

  String get displayName => ingredientName ?? '#$ingredientId';

  /// 用量文案：自然单位优先（「2 个」「适量」）；无单位信息回落克数。
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
    final g = grams;
    if (g == g.roundToDouble()) return '${g.toInt()} g';
    return '${g.toStringAsFixed(1)} g';
  }
}
