import '../core/api_client.dart';
import '../models/dish.dart';
import '../models/nutrition_metric.dart';
import '../models/page.dart';

/// 菜品服务（对应 menu-mini/src/api/dish.ts + DishController）。
class DishService {
  /// 多维搜索分页：GET /dish/search。
  /// [ingredientIds] 食材筛选（交集：必须包含所有选中食材）。
  /// [tagIds] 标签筛选（分类标签条选中）。
  /// [sort] 排序：cooked=做过最多；缺省=最新。
  static Future<PageData<Dish>> search({
    String? keyword,
    List<int>? ingredientIds,
    List<int>? tagIds,
    List<int>? cuisineIds,
    String? sort,
    int pageNum = 1,
    int pageSize = 10, // DESIGN.md §12.2（默认 10 条/页）
  }) async {
    final data = await ApiClient.instance.get('/dish/search', query: {
      if (keyword != null && keyword.isNotEmpty) 'keyword': keyword,
      if (ingredientIds != null && ingredientIds.isNotEmpty)
        'ingredientIds': ingredientIds.join(','),
      if (tagIds != null && tagIds.isNotEmpty) 'tagIds': tagIds.join(','),
      if (cuisineIds != null && cuisineIds.isNotEmpty)
        'cuisineIds': cuisineIds.join(','),
      if (sort != null && sort.isNotEmpty) 'sort': sort,
      'pageNum': pageNum,
      'pageSize': pageSize,
    });
    return PageData<Dish>.fromJson(
      data as Map<String, dynamic>,
      Dish.fromJson,
    );
  }

  /// 详情：GET /dish/{id} → {dish, steps, ...}。
  static Future<DishDetail> detail(int id) async {
    final data = await ApiClient.instance.get('/dish/$id');
    return DishDetail.fromJson(data as Map<String, dynamic>);
  }

  /// 份数营养：GET /dish/{id}/nutrition?serving= → Map<metricId字符串, 值>。
  static Future<Map<String, num>> nutrition(int id, {num serving = 1}) async {
    final data = await ApiClient.instance.get(
      '/dish/$id/nutrition',
      query: {'serving': serving},
    );
    if (data == null) return {};
    return (data as Map).map((k, v) =>
        MapEntry(k.toString(), v == null ? 0 : (v as num)));
  }

  // markDone 已废弃：cookDishNow（cookNow）承担"做菜即标记"，做过列表走 Dish search done。

  /// 营养指标字典：GET /nutrition/metric。
  static Future<List<NutritionMetric>> metrics() async {
    final data = await ApiClient.instance.get('/nutrition/metric');
    return (data as List)
        .map((e) => NutritionMetric.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  /// 写菜谱：POST /dish  → 返回新菜品 id。
  ///
  /// [data] 即 DishSaveDTO：{ dish, steps[, cuisineIds, tagIds, ...] }。
  static Future<int> saveDish(Map<String, dynamic> data) async {
    final result = await ApiClient.instance.post('/dish', body: data);
    return (result as num).toInt();
  }

  /// URL 导入：POST /dish/import-url?url=xxx  → 返回新菜品 id。
  static Future<int> importDishByUrl(String url) async {
    final result = await ApiClient.instance.post(
      '/dish/import-url',
      query: {'url': url},
    );
    return (result as num).toInt();
  }

  // ===== 写菜谱草稿（DESIGN.md §16.4） =====

  /// 保存草稿：POST /dish/draft（body 无 id = 新建，返回草稿 id）。
  static Future<int> saveDraft(Map<String, dynamic> body) async {
    final result = await ApiClient.instance.post('/dish/draft', body: body);
    return (result as num).toInt();
  }

  /// 草稿箱列表：GET /dish/draft/list（本人，按更新时间倒序；分页，DESIGN.md §12）。
  static Future<PageData<DishDraftItem>> listDrafts({
    int pageNum = 1,
    int pageSize = 10,
  }) async {
    final data = await ApiClient.instance.get('/dish/draft/list', query: {
      'pageNum': pageNum,
      'pageSize': pageSize,
    });
    return PageData<DishDraftItem>.fromJson(
      data as Map<String, dynamic>,
      DishDraftItem.fromJson,
    );
  }

  /// 草稿详情（恢复编辑回填）：GET /dish/draft/{id}。
  static Future<DishDraftDetail> draftDetail(int id) async {
    final data = await ApiClient.instance.get('/dish/draft/$id');
    return DishDraftDetail.fromJson(data as Map<String, dynamic>);
  }

  /// 删除草稿（草稿箱滑动删除 / 发布成功后清掉）：DELETE /dish/draft/{id}。
  static Future<void> deleteDraft(int id) async {
    await ApiClient.instance.delete('/dish/draft/$id');
  }

  /// 做菜确认弹窗数据：GET /menu/{id}/cook-materials
  /// （本次用到的食材 + 当前档位 + 是否调料；不落库、不判断够不够）。
  static Future<CookMaterials> cookMaterials(int menuId) async {
    final data = await ApiClient.instance.get('/menu/$menuId/cook-materials');
    return CookMaterials.fromJson(data as Map<String, dynamic>);
  }
}

/// 草稿箱列表项（后端 DishDraftDTO.ListItem，§16.4）。
class DishDraftItem {
  final int id;
  final String name;
  final String? coverUrl;
  final int ingredientCount;
  final int stepCount;
  final DateTime updateTime;

  const DishDraftItem({
    required this.id,
    required this.name,
    this.coverUrl,
    required this.ingredientCount,
    required this.stepCount,
    required this.updateTime,
  });

  factory DishDraftItem.fromJson(Map<String, dynamic> j) => DishDraftItem(
        id: (j['id'] as num).toInt(),
        name: (j['name'] ?? '') as String,
        coverUrl: j['coverUrl'] as String?,
        ingredientCount: (j['ingredientCount'] as num?)?.toInt() ?? 0,
        stepCount: (j['stepCount'] as num?)?.toInt() ?? 0,
        updateTime:
            DateTime.tryParse(j['updateTime']?.toString() ?? '') ?? DateTime.now(),
      );
}

/// 草稿详情（恢复编辑回填；后端 DishDraftDTO.Detail）。
class DishDraftDetail {
  final int id;
  final String name;
  final String? coverUrl;
  final int? prepTime;
  final int? cookTime;
  final int? difficulty;
  final String? note;
  final List<int> tagIds;
  final List<int> cuisineIds;
  final List<DraftIngredientItem> ingredients;
  final List<DraftStepItem> steps;

  const DishDraftDetail({
    required this.id,
    required this.name,
    this.coverUrl,
    this.prepTime,
    this.cookTime,
    this.difficulty,
    this.note,
    this.tagIds = const [],
    this.cuisineIds = const [],
    this.ingredients = const [],
    this.steps = const [],
  });

  factory DishDraftDetail.fromJson(Map<String, dynamic> j) => DishDraftDetail(
        id: (j['id'] as num).toInt(),
        name: (j['name'] ?? '') as String,
        coverUrl: j['coverUrl'] as String?,
        prepTime: (j['prepTime'] as num?)?.toInt(),
        cookTime: (j['cookTime'] as num?)?.toInt(),
        difficulty: (j['difficulty'] as num?)?.toInt(),
        note: j['note'] as String?,
        tagIds: ((j['tagIds'] ?? const []) as List)
            .map((e) => (e as num).toInt())
            .toList(),
        cuisineIds: ((j['cuisineIds'] ?? const []) as List)
            .map((e) => (e as num).toInt())
            .toList(),
        ingredients: ((j['ingredients'] ?? const []) as List)
            .map((e) =>
                DraftIngredientItem.fromJson(e as Map<String, dynamic>))
            .toList(),
        steps: ((j['steps'] ?? const []) as List)
            .map((e) => DraftStepItem.fromJson(e as Map<String, dynamic>))
            .toList(),
      );
}

/// 草稿用料行（数字 + 单位原文分存，恢复时还原两个输入框）。
class DraftIngredientItem {
  final int ingredientId;
  final String? ingredientName;
  final String? amount;
  final String? unitText;

  const DraftIngredientItem({
    required this.ingredientId,
    this.ingredientName,
    this.amount,
    this.unitText,
  });

  factory DraftIngredientItem.fromJson(Map<String, dynamic> j) =>
      DraftIngredientItem(
        ingredientId: (j['ingredientId'] as num?)?.toInt() ?? 0,
        ingredientName: j['ingredientName'] as String?,
        amount: j['amount'] as String?,
        unitText: j['unitText'] as String?,
      );
}

/// 草稿步骤（与 DishStep 同构：seq/text/images）。
class DraftStepItem {
  final int? seq;
  final String text;
  final String? images;

  const DraftStepItem({this.seq, required this.text, this.images});

  factory DraftStepItem.fromJson(Map<String, dynamic> j) => DraftStepItem(
        seq: (j['seq'] as num?)?.toInt(),
        text: (j['text'] ?? '') as String,
        images: j['images'] as String?,
      );

  List<String> get imageList => images == null || images!.isEmpty
      ? const []
      : images!.split(',').where((s) => s.trim().isNotEmpty).toList();
}

/// 做菜确认弹窗数据（后端 CookMaterialsVO）。
///
/// - `items`：本次用到的食材（聚合用量 + 当前档位 + 是否调料，调料默认"用了一些"）。
class CookMaterials {
  final int menuId;
  final List<CookMaterialItem> items;

  const CookMaterials({required this.menuId, required this.items});

  factory CookMaterials.fromJson(Map<String, dynamic> j) => CookMaterials(
        menuId: (j['menuId'] as num).toInt(),
        items: ((j['items'] ?? const []) as List)
            .map((e) => CookMaterialItem.fromJson(e as Map<String, dynamic>))
            .toList(),
      );
}

/// 弹窗里的一项食材。
class CookMaterialItem {
  final int ingredientId;
  final String? ingredientName;
  /// 用量原文（V55 去单位：后端已拼菜名，如「番茄炒蛋 2个」；显示用，不参与任何库存判断）。
  final List<String> usageTexts;
  /// 当前档位 ENOUGH / LOW / NONE。
  final String level;
  /// 是否调料（采购品类=调味料，默认"用了一些"）。
  final bool isCondiment;

  const CookMaterialItem({
    required this.ingredientId,
    this.ingredientName,
    required this.usageTexts,
    required this.level,
    required this.isCondiment,
  });

  factory CookMaterialItem.fromJson(Map<String, dynamic> j) => CookMaterialItem(
        ingredientId: (j['ingredientId'] as num).toInt(),
        ingredientName: j['ingredientName'] as String?,
        usageTexts: ((j['usageTexts'] ?? const []) as List)
            .map((e) => e as String)
            .toList(),
        level: j['level'] as String? ?? 'NONE',
        isCondiment: j['isCondiment'] as bool? ?? false,
      );
}

/// 做菜确认结果（CookController CookResult，V42）。
///
/// - `menuId`：整集做菜的食集 id。
/// - `cookingRecordIds`：本次写入的 cooking_record id 列表。
class CookResult {
  final int? menuId;
  final List<int> cookingRecordIds;

  const CookResult({this.menuId, required this.cookingRecordIds});

  factory CookResult.fromJson(Map<String, dynamic> j) => CookResult(
        menuId: (j['menuId'] as num?)?.toInt(),
        cookingRecordIds: ((j['cookingRecordIds'] ?? const []) as List)
            .map((e) => (e as num).toInt())
            .toList(),
      );
}
