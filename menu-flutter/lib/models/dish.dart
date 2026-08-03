/// 菜品（对应后端 Dish）。字段对齐小程序 dish/List、dish/Detail 的用法。
class Dish {
  final int id;
  final String name;
  final int? cookTime;
  final int? prepTime;
  final int? difficulty;
  final String? note;
  final String? coverUrl;
  final num? price;
  /// 菜系/分类/标签名（后端 fillRelNames 回填，列表与详情均返回）。
  final List<String> cuisineNames;
  final List<String> categoryNames;
  final List<String> tagNames;

  const Dish({
    required this.id,
    required this.name,
    this.cookTime,
    this.prepTime,
    this.difficulty,
    this.note,
    this.coverUrl,
    this.price,
    this.cuisineNames = const [],
    this.categoryNames = const [],
    this.tagNames = const [],
  });

  factory Dish.fromJson(Map<String, dynamic> j) => Dish(
        id: (j['id'] as num).toInt(),
        name: (j['name'] ?? '') as String,
        cookTime: (j['cookTime'] as num?)?.toInt(),
        prepTime: (j['prepTime'] as num?)?.toInt(),
        difficulty: (j['difficulty'] as num?)?.toInt(),
        note: j['note'] as String?,
        coverUrl: j['coverUrl'] as String?,
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

/// 菜品详情聚合（后端 DishDetail record：{dish, steps, cuisineIds, ...}）。
class DishDetail {
  final Dish dish;
  final List<DishStep> steps;

  const DishDetail({required this.dish, required this.steps});

  factory DishDetail.fromJson(Map<String, dynamic> j) => DishDetail(
        dish: Dish.fromJson(j['dish'] as Map<String, dynamic>),
        steps: ((j['steps'] ?? const []) as List)
            .map((e) => DishStep.fromJson(e as Map<String, dynamic>))
            .toList(),
      );
}
