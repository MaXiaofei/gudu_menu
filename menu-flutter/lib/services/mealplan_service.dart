import '../core/api_client.dart';

/// 周计划服务。
class MealPlanService {
  /// 创建周计划：POST /mealplan → 返回 id
  static Future<int> createPlan(String weekStart, {String? name}) async {
    final result = await ApiClient.instance.post('/mealplan', body: {
      'weekStart': weekStart,
      'name': name ?? '本周计划',
    });
    return (result as num).toInt();
  }

  /// 计划详情：GET /mealplan/{id}
  static Future<PlanDetail> getPlan(int planId) async {
    final data = await ApiClient.instance.get('/mealplan/$planId');
    return PlanDetail.fromJson(data as Map<String, dynamic>);
  }

  /// 挂菜：POST /mealplan/{planId}/item → {itemId, duplicates}
  static Future<AddItemResult> addItem(int planId, MealPlanItem item) async {
    final data = await ApiClient.instance.post('/mealplan/$planId/item', body: {
      'date': item.date,
      'meal': item.meal,
      'dishId': item.dishId,
      'servingFactor': item.servingFactor,
    });
    final m = data as Map<String, dynamic>;
    return AddItemResult(
      itemId: (m['itemId'] as num?)?.toInt() ?? 0,
      duplicates: ((m['duplicates'] ?? []) as List)
          .map((e) => e as Map<String, dynamic>)
          .toList(),
    );
  }

  /// 移除：DELETE /mealplan/item/{itemId}
  static Future<void> deleteItem(int itemId) async {
    await ApiClient.instance.delete('/mealplan/item/$itemId');
  }

  /// 复制上周排菜到本周：POST /mealplan/{planId}/copy-from/{srcPlanId}
  static Future<int> copyFrom(int srcPlanId, int planId) async {
    final data = await ApiClient.instance.post('/mealplan/$planId/copy-from/$srcPlanId');
    return (data as num?)?.toInt() ?? 0;
  }

  /// 列表：GET /mealplan?pageNum=1&pageSize=15（DESIGN.md §12.2）。
  static Future<List<MealPlan>> list({int pageSize = 15}) async {
    final data = await ApiClient.instance.get('/mealplan', query: {
      'pageNum': 1, 'pageSize': pageSize,
    });
    if (data is Map && data['records'] is List) {
      return (data['records'] as List)
          .map((e) => MealPlan.fromJson(e as Map<String, dynamic>))
          .toList();
    }
    return [];
  }
}

/// 周计划头。
class MealPlan {
  final int id;
  final String? weekStart;
  final String? name;
  final String? createTime;

  const MealPlan({required this.id, this.weekStart, this.name, this.createTime});

  factory MealPlan.fromJson(Map<String, dynamic> j) => MealPlan(
        id: (j['id'] as num).toInt(),
        weekStart: j['weekStart'] as String?,
        name: j['name'] as String?,
        createTime: j['createTime'] as String?,
      );
}

/// 计划明细项。
class MealPlanItem {
  final int? id;
  final int? planId;
  final String? date;
  final String? meal;
  final int? dishId;
  final double? servingFactor;
  final String? dishName; // 后端 getPlan JOIN 填充

  const MealPlanItem({
    this.id, this.planId, this.date, this.meal,
    this.dishId, this.servingFactor, this.dishName,
  });

  factory MealPlanItem.fromJson(Map<String, dynamic> j) => MealPlanItem(
        id: (j['id'] as num?)?.toInt(),
        planId: (j['planId'] as num?)?.toInt(),
        date: j['date'] as String?,
        meal: j['meal'] as String?,
        dishId: (j['dishId'] as num?)?.toInt(),
        servingFactor: (j['servingFactor'] as num?)?.toDouble(),
        dishName: j['dishName'] as String?,
      );
}

/// 计划详情。
class PlanDetail {
  final MealPlan plan;
  final List<MealPlanItem> items;

  const PlanDetail({required this.plan, required this.items});

  factory PlanDetail.fromJson(Map<String, dynamic> j) => PlanDetail(
        plan: MealPlan.fromJson(j['plan'] as Map<String, dynamic>),
        items: ((j['items'] ?? []) as List)
            .map((e) => MealPlanItem.fromJson(e as Map<String, dynamic>))
            .toList(),
      );

  /// 按日期分组
  Map<String, List<MealPlanItem>> itemsByDate() {
    final m = <String, List<MealPlanItem>>{};
    for (final it in items) {
      final key = it.date ?? '';
      m.putIfAbsent(key, () => []).add(it);
    }
    return m;
  }
}

/// 挂菜结果。
class AddItemResult {
  final int itemId;
  final List<Map<String, dynamic>> duplicates;

  const AddItemResult({required this.itemId, required this.duplicates});
  bool get hasDuplicate => duplicates.isNotEmpty;
}
