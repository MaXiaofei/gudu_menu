import '../core/api_client.dart';
import '../models/dish.dart';
import '../models/nutrition_metric.dart';
import '../models/page.dart';

/// 菜品服务（对应 menu-mini/src/api/dish.ts + DishController）。
class DishService {
  /// 多维搜索分页：GET /dish/search。
  /// [ingredientIds] 食材筛选（交集：必须包含所有选中食材）。
  static Future<PageData<Dish>> search({
    String? keyword,
    List<int>? ingredientIds,
    int pageNum = 1,
    int pageSize = 20,
  }) async {
    final data = await ApiClient.instance.get('/dish/search', query: {
      if (keyword != null && keyword.isNotEmpty) 'keyword': keyword,
      if (ingredientIds != null && ingredientIds.isNotEmpty)
        'ingredientIds': ingredientIds.join(','),
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

  /// 单菜直做（不入食集）：POST /dish/{id}/cook-now?servings=N
  /// 扣 pantry + 写 cooking_record，返回 CookResult（含扣减/欠量/记录 id）。
  static Future<CookResult> cookNow(int dishId, {int servings = 1}) async {
    final data = await ApiClient.instance.post(
      '/dish/$dishId/cook-now',
      query: {'servings': servings},
    );
    return CookResult.fromJson(data as Map<String, dynamic>);
  }
}

/// 做菜结果（CookController CookResult）。
///
/// - `menuId`：整集做菜时为食集 id；单菜直做（cook-now）为 null。
/// - `deductions`：各食材扣减明细列表（含实扣/欠量/批次/食材名）。
/// - `shortages`：欠量 Map（ingredientId → 克数），非空表示库存不够。
/// - `cookingRecordIds`：本次生成的 cooking_record id 列表。
class CookResult {
  final int? menuId;
  final List<DeductResult> deductions;
  final Map<int, double> shortages;
  final List<int> cookingRecordIds;

  const CookResult({
    this.menuId,
    required this.deductions,
    required this.shortages,
    required this.cookingRecordIds,
  });

  factory CookResult.fromJson(Map<String, dynamic> j) {
    Map<int, double> parseNumKeyMap(dynamic v) {
      if (v is! Map) return {};
      return Map.fromEntries(
        v.entries.where((e) => e.value != null).map(
              (e) => MapEntry(
                int.parse(e.key.toString()),
                (e.value as num).toDouble(),
              ),
            ),
      );
    }

    return CookResult(
      menuId: (j['menuId'] as num?)?.toInt(),
      deductions: ((j['deductions'] ?? const []) as List)
          .map((e) => DeductResult.fromJson(e as Map<String, dynamic>))
          .toList(),
      shortages: parseNumKeyMap(j['shortages']),
      cookingRecordIds: ((j['cookingRecordIds'] ?? []) as List)
          .map((e) => (e as num).toInt())
          .toList(),
    );
  }

  bool get hasShortage => shortages.isNotEmpty;

  /// 欠量食材名列表（供 UI 展示"缺：番茄 80g、鸡蛋 5g"）。
  List<String> get shortageNames => deductions
      .where((d) => d.shortageGrams > 0)
      .map((d) => d.ingredientName != null && d.ingredientName!.isNotEmpty
          ? '${d.ingredientName} ${d.shortageGrams.toStringAsFixed(0)}g'
          : '食材#${d.ingredientId} ${d.shortageGrams.toStringAsFixed(0)}g')
      .toList();
}

/// 单食材扣减明细（后端 PantryService.DeductResult）。
///
/// - `ingredientId`/`ingredientName`：食材 id 与冗余名（后端批量回填）。
/// - `deductedGrams`：实际从 pantry 扣掉的克数。
/// - `shortageGrams`：欠量克数（>0 表示库存不够）。
/// - `batches`：逐批扣减明细（pantryId/扣量/余量）。
class DeductResult {
  final int ingredientId;
  final String? ingredientName;
  final double deductedGrams;
  final double shortageGrams;
  final List<DeductBatch> batches;

  const DeductResult({
    required this.ingredientId,
    this.ingredientName,
    required this.deductedGrams,
    required this.shortageGrams,
    required this.batches,
  });

  factory DeductResult.fromJson(Map<String, dynamic> j) => DeductResult(
        ingredientId: (j['ingredientId'] as num).toInt(),
        ingredientName: j['ingredientName'] as String?,
        deductedGrams: (j['deductedGrams'] as num? ?? 0).toDouble(),
        shortageGrams: (j['shortageGrams'] as num? ?? 0).toDouble(),
        batches: ((j['batches'] ?? const []) as List)
            .map((e) => DeductBatch.fromJson(e as Map<String, dynamic>))
            .toList(),
      );
}

/// 扣减批次明细（后端 DeductResult.BatchOut）。
class DeductBatch {
  final int pantryId;
  final double deductedGrams;
  final double remainGrams;

  const DeductBatch({
    required this.pantryId,
    required this.deductedGrams,
    required this.remainGrams,
  });

  factory DeductBatch.fromJson(Map<String, dynamic> j) => DeductBatch(
        pantryId: (j['pantryId'] as num).toInt(),
        deductedGrams: (j['deductedGrams'] as num? ?? 0).toDouble(),
        remainGrams: (j['remainGrams'] as num? ?? 0).toDouble(),
      );
}
