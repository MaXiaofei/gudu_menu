import '../../core/api_client.dart';

/// 食记（做菜日记）服务：统计/时间轴/按菜汇总/年视图/详情/再做一次。
/// 数据源 cooking_record（做菜确认自动写入），无手工录入。
class FoodLogService {
  // ===== 月视图：统计卡 + 时间轴 =====

  /// month=0 表示全年范围（年视图：统计卡+时间轴同构）。
  static Future<FoodLogMonth> month(int year, int month) async {
    final data = await ApiClient.instance.get('/food-log/month', query: {
      'month': month > 0 ? '$year-${month.toString().padLeft(2, '0')}' : '$year',
    });
    return FoodLogMonth.fromJson(data as Map<String, dynamic>);
  }

  /// 按菜汇总（次数降序 + ★均分）。
  /// 按菜汇总（month=0 表示全年范围）。
  static Future<FoodLogByDish> byDish(int year, int month) async {
    final data = await ApiClient.instance.get('/food-log/by-dish', query: {
      'month': month > 0 ? '$year-${month.toString().padLeft(2, '0')}' : '$year',
    });
    return FoodLogByDish.fromJson(data as Map<String, dynamic>);
  }

  /// 年视图：12 个月做饭次数。
  static Future<FoodLogYear> year(int year) async {
    final data = await ApiClient.instance
        .get('/food-log/year', query: {'year': year});
    return FoodLogYear.fromJson(data as Map<String, dynamic>);
  }

  /// 单条详情。
  static Future<FoodLogDetail> detail(int menuId) async {
    final data = await ApiClient.instance
        .get('/food-log/detail', query: {'menuId': menuId});
    return FoodLogDetail.fromJson(data as Map<String, dynamic>);
  }

  /// 再做一次：复制食集 → 返回新食集 id。
  static Future<int> copyMenu(int menuId) async {
    final data = await ApiClient.instance.post('/menu/$menuId/copy');
    return (data as num).toInt();
  }
}

// ===== 模型 =====

class FoodLogMonth {
  final FoodLogSummary summary;
  final List<FoodLogMeal> timeline;

  FoodLogMonth({required this.summary, required this.timeline});

  factory FoodLogMonth.fromJson(Map<String, dynamic> j) => FoodLogMonth(
        summary: FoodLogSummary.fromJson(j['summary'] as Map<String, dynamic>),
        timeline: ((j['timeline'] as List?) ?? const [])
            .map((e) => FoodLogMeal.fromJson(e as Map<String, dynamic>))
            .toList(),
      );
}

class FoodLogSummary {
  final int meals;
  final int dishes;
  final int cookDays;
  final List<String> topDishes;

  FoodLogSummary({
    required this.meals,
    required this.dishes,
    required this.cookDays,
    required this.topDishes,
  });

  factory FoodLogSummary.fromJson(Map<String, dynamic> j) => FoodLogSummary(
        meals: (j['meals'] as num?)?.toInt() ?? 0,
        dishes: (j['dishes'] as num?)?.toInt() ?? 0,
        cookDays: (j['cookDays'] as num?)?.toInt() ?? 0,
        topDishes: ((j['topDishes'] as List?) ?? const []).cast<String>(),
      );
}

class FoodLogMeal {
  final int? menuId;
  final String name;
  final DateTime? cookedAt;
  final int dishCount;
  final int? servingCount;
  final List<String> dishNames;
  final int usedUpCount;
  final int partialCount;
  final bool reviewed;

  FoodLogMeal({
    this.menuId,
    required this.name,
    this.cookedAt,
    required this.dishCount,
    this.servingCount,
    required this.dishNames,
    required this.usedUpCount,
    required this.partialCount,
    required this.reviewed,
  });

  factory FoodLogMeal.fromJson(Map<String, dynamic> j) => FoodLogMeal(
        menuId: (j['menuId'] as num?)?.toInt(),
        name: j['name'] as String? ?? '',
        cookedAt: DateTime.tryParse(j['cookedAt'] as String? ?? ''),
        dishCount: (j['dishCount'] as num?)?.toInt() ?? 0,
        servingCount: (j['servingCount'] as num?)?.toInt(),
        dishNames: ((j['dishNames'] as List?) ?? const []).cast<String>(),
        usedUpCount: (j['usedUpCount'] as num?)?.toInt() ?? 0,
        partialCount: (j['partialCount'] as num?)?.toInt() ?? 0,
        reviewed: j['reviewed'] == true,
      );
}

class FoodLogByDish {
  final int totalKinds;
  final List<FoodLogDishItem> items;

  FoodLogByDish({required this.totalKinds, required this.items});

  factory FoodLogByDish.fromJson(Map<String, dynamic> j) => FoodLogByDish(
        totalKinds: (j['totalKinds'] as num?)?.toInt() ?? 0,
        items: ((j['items'] as List?) ?? const [])
            .map((e) => FoodLogDishItem.fromJson(e as Map<String, dynamic>))
            .toList(),
      );
}

class FoodLogDishItem {
  final int dishId;
  final String dishName;
  final int count;
  final DateTime? lastCookedAt;
  final double? avgStar;

  FoodLogDishItem({
    required this.dishId,
    required this.dishName,
    required this.count,
    this.lastCookedAt,
    this.avgStar,
  });

  factory FoodLogDishItem.fromJson(Map<String, dynamic> j) => FoodLogDishItem(
        dishId: (j['dishId'] as num).toInt(),
        dishName: j['dishName'] as String? ?? '',
        count: (j['count'] as num?)?.toInt() ?? 0,
        lastCookedAt: DateTime.tryParse(j['lastCookedAt'] as String? ?? ''),
        avgStar: (j['avgStar'] as num?)?.toDouble(),
      );
}

class FoodLogYear {
  final int year;
  final List<int> monthCounts;

  FoodLogYear({required this.year, required this.monthCounts});

  factory FoodLogYear.fromJson(Map<String, dynamic> j) => FoodLogYear(
        year: (j['year'] as num).toInt(),
        monthCounts: ((j['monthCounts'] as List?) ?? const [])
            .map((e) => (e as num).toInt())
            .toList(),
      );
}

class FoodLogDetail {
  final int menuId;
  final String name;
  final DateTime? cookedAt;
  final int? servingCount;
  final List<FoodLogDish> dishes;
  final List<String> usedUp;
  final List<String> partial;
  final bool reviewed;

  FoodLogDetail({
    required this.menuId,
    required this.name,
    this.cookedAt,
    this.servingCount,
    required this.dishes,
    required this.usedUp,
    required this.partial,
    required this.reviewed,
  });

  factory FoodLogDetail.fromJson(Map<String, dynamic> j) => FoodLogDetail(
        menuId: (j['menuId'] as num).toInt(),
        name: j['name'] as String? ?? '',
        cookedAt: DateTime.tryParse(j['cookedAt'] as String? ?? ''),
        servingCount: (j['servingCount'] as num?)?.toInt(),
        dishes: ((j['dishes'] as List?) ?? const [])
            .map((e) => FoodLogDish.fromJson(e as Map<String, dynamic>))
            .toList(),
        usedUp: ((j['usedUp'] as List?) ?? const []).cast<String>(),
        partial: ((j['partial'] as List?) ?? const []).cast<String>(),
        reviewed: j['reviewed'] == true,
      );
}

class FoodLogDish {
  final int dishId;
  final String dishName;
  final double? servingFactor;
  final String? note;

  FoodLogDish({
    required this.dishId,
    required this.dishName,
    this.servingFactor,
    this.note,
  });

  factory FoodLogDish.fromJson(Map<String, dynamic> j) => FoodLogDish(
        dishId: (j['dishId'] as num).toInt(),
        dishName: j['dishName'] as String? ?? '',
        servingFactor: (j['servingFactor'] as num?)?.toDouble(),
        note: j['note'] as String?,
      );
}
