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
    String? sort,
    int pageNum = 1,
    int pageSize = 15, // DESIGN.md §12.2
  }) async {
    final data = await ApiClient.instance.get('/dish/search', query: {
      if (keyword != null && keyword.isNotEmpty) 'keyword': keyword,
      if (ingredientIds != null && ingredientIds.isNotEmpty)
        'ingredientIds': ingredientIds.join(','),
      if (tagIds != null && tagIds.isNotEmpty) 'tagIds': tagIds.join(','),
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

  /// 录入新菜：POST /dish  → 返回新菜品 id。
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

  /// 做菜确认弹窗数据：GET /menu/{id}/cook-materials
  /// （本次用到的食材 + 当前档位 + 是否调料；不落库、不判断够不够）。
  static Future<CookMaterials> cookMaterials(int menuId) async {
    final data = await ApiClient.instance.get('/menu/$menuId/cook-materials');
    return CookMaterials.fromJson(data as Map<String, dynamic>);
  }
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
  /// 聚合用量（显示用，不参与任何库存判断）。
  final double needGrams;
  /// 当前档位 ENOUGH / LOW / NONE。
  final String level;
  /// 是否调料（采购品类=调味料，默认"用了一些"）。
  final bool isCondiment;

  const CookMaterialItem({
    required this.ingredientId,
    this.ingredientName,
    required this.needGrams,
    required this.level,
    required this.isCondiment,
  });

  factory CookMaterialItem.fromJson(Map<String, dynamic> j) => CookMaterialItem(
        ingredientId: (j['ingredientId'] as num).toInt(),
        ingredientName: j['ingredientName'] as String?,
        needGrams: (j['needGrams'] as num?)?.toDouble() ?? 0,
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
