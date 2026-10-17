import '../core/api_client.dart';
import '../models/showcase_types.dart';

/// 一周餐程与营养报告（contracts.md §4）。周 = 周一至周日。

/// 默认生成的那一周的周一：今天是周一 → 本周；否则下周一。
DateTime defaultWeekStart(DateTime today) {
  final d = DateTime(today.year, today.month, today.day);
  if (d.weekday == DateTime.monday) return d;
  return d.add(Duration(days: 8 - d.weekday));
}

/// 某天所在周的周一（话题挑战等按自然周统计用）。
DateTime mondayOf(DateTime day) {
  final d = DateTime(day.year, day.month, day.day);
  return d.subtract(Duration(days: d.weekday - 1));
}

String ymd(DateTime d) => '${d.year}-${d.month.toString().padLeft(2, '0')}-${d.day.toString().padLeft(2, '0')}';
String md(DateTime d) => '${d.month}.${d.day.toString().padLeft(2, '0')}';
const weekdayNames = ['周一', '周二', '周三', '周四', '周五', '周六', '周日'];

class PlanSlot {
  final int slotId;

  /// LUNCH / DINNER
  final String meal;
  final DishCard dish;
  final List<String> badges;
  const PlanSlot({required this.slotId, required this.meal, required this.dish, this.badges = const []});

  factory PlanSlot.fromJson(Map<String, dynamic> j) => PlanSlot(
        slotId: Json.i(j['slotId']),
        meal: Json.s(j['meal'], 'DINNER'),
        dish: DishCard.fromJson(Json.map(j['dish'])),
        badges: Json.strs(j['badges']),
      );

  bool get expiring => badges.contains('EXPIRING');
  bool get baby => badges.contains('BABY');
}

class PlanDay {
  final DateTime date;
  final String weekday;
  final String? note;
  final List<PlanSlot> slots;
  const PlanDay({required this.date, required this.weekday, this.note, this.slots = const []});

  factory PlanDay.fromJson(Map<String, dynamic> j) {
    final date = Json.date(j['date']) ?? DateTime(1970);
    return PlanDay(
      date: date,
      weekday: Json.s(j['weekday'], weekdayNames[date.weekday - 1]),
      note: Json.sOrNull(j['note']),
      slots: Json.maps(j['slots']).map(PlanSlot.fromJson).toList(),
    );
  }

  PlanSlot? slot(String meal) {
    for (final s in slots) {
      if (s.meal == meal) return s;
    }
    return null;
  }
}

class MealPlanDraft {
  final int draftId;
  final DateTime weekStart;
  final DateTime weekEnd;
  final List<String> constraints;
  final bool checkPassed;
  final int rounds;
  final List<String> notes;
  final List<PlanDay> days;
  final int estCostFen;
  final int newItems;
  final int usesExpiring;

  const MealPlanDraft({
    required this.draftId,
    required this.weekStart,
    required this.weekEnd,
    this.constraints = const [],
    this.checkPassed = true,
    this.rounds = 1,
    this.notes = const [],
    this.days = const [],
    this.estCostFen = 0,
    this.newItems = 0,
    this.usesExpiring = 0,
  });

  factory MealPlanDraft.fromJson(Map<String, dynamic> j) {
    final check = Json.map(j['check']);
    final sum = Json.map(j['summary']);
    final days = Json.maps(j['days']).map(PlanDay.fromJson).toList()..sort((a, b) => a.date.compareTo(b.date));
    return MealPlanDraft(
      draftId: Json.i(j['draftId']),
      weekStart: Json.date(j['weekStart']) ?? (days.isNotEmpty ? days.first.date : DateTime(1970)),
      weekEnd: Json.date(j['weekEnd']) ?? (days.isNotEmpty ? days.last.date : DateTime(1970)),
      constraints: Json.strs(j['constraints']),
      checkPassed: check['passed'] != false,
      rounds: Json.i(check['rounds'], 1),
      notes: Json.strs(check['notes']),
      days: days,
      estCostFen: Json.i(sum['estCostFen']),
      newItems: Json.i(sum['newItems']),
      usesExpiring: Json.i(sum['usesExpiring']),
    );
  }

  int get dishCount => days.fold(0, (n, d) => n + d.slots.length);

  /// 不重样的菜数。
  int get distinctDishes => {for (final d in days) for (final s in d.slots) s.dish.id}.length;
}

class NutritionAxis {
  final String key;
  final String label;
  final double actual;
  final double target;
  final String unit;
  final double ratio;
  const NutritionAxis({required this.key, required this.label, this.actual = 0, this.target = 0, this.unit = '', this.ratio = 0});

  factory NutritionAxis.fromJson(Map<String, dynamic> j) {
    final actual = Json.d(j['actual']);
    final target = Json.d(j['target']);
    final ratio = j['ratio'] != null ? Json.d(j['ratio']) : (target > 0 ? actual / target : 0.0);
    return NutritionAxis(
      key: Json.s(j['key']),
      label: Json.s(j['label']),
      actual: actual,
      target: target,
      unit: Json.s(j['unit']),
      ratio: ratio,
    );
  }

  /// 钠/脂肪超过 100% 为超标；蛋白质/纤维等低于 80% 为不足。
  bool get over => (key == 'sodium' || key == 'fat') && ratio > 1.0;
}

class NutritionAlert {
  final String key;
  final String label;
  final double dailyAvg;
  final double limit;
  final String unit;
  final List<DishCard> sources;
  final List<DishCard> suggestions;
  const NutritionAlert({
    required this.key,
    required this.label,
    this.dailyAvg = 0,
    this.limit = 0,
    this.unit = '',
    this.sources = const [],
    this.suggestions = const [],
  });

  factory NutritionAlert.fromJson(Map<String, dynamic> j) => NutritionAlert(
        key: Json.s(j['key']),
        label: Json.s(j['label']),
        dailyAvg: Json.d(j['dailyAvg']),
        limit: Json.d(j['limit']),
        unit: Json.s(j['unit']),
        sources: Json.maps(j['sources']).map(DishCard.fromJson).toList(),
        suggestions: Json.maps(j['suggestions']).map(DishCard.fromJson).toList(),
      );
}

class NutritionReport {
  final MemberLite member;
  final String profile;
  final DateTime? from;
  final DateTime? to;
  final int recordCount;
  final List<NutritionAxis> radar;
  final List<NutritionAlert> alerts;
  final String trendKey;
  final List<({DateTime date, double value})> trend;
  final double trendLimit;
  final List<String> goods;

  const NutritionReport({
    required this.member,
    this.profile = '',
    this.from,
    this.to,
    this.recordCount = 0,
    this.radar = const [],
    this.alerts = const [],
    this.trendKey = 'sodium',
    this.trend = const [],
    this.trendLimit = 0,
    this.goods = const [],
  });

  factory NutritionReport.fromJson(Map<String, dynamic> j) {
    final m = Json.map(j['member']);
    final range = Json.map(j['range']);
    final trend = Json.map(j['trend']);
    return NutritionReport(
      member: MemberLite.fromJson(m),
      profile: Json.s(m['profile']),
      from: Json.date(range['from']),
      to: Json.date(range['to']),
      recordCount: Json.i(j['recordCount']),
      radar: Json.maps(j['radar']).map(NutritionAxis.fromJson).toList(),
      alerts: Json.maps(j['alerts']).map(NutritionAlert.fromJson).toList(),
      trendKey: Json.s(trend['key'], 'sodium'),
      trend: [
        for (final p in Json.maps(trend['points'])) (date: Json.date(p['date']) ?? DateTime(1970), value: Json.d(p['value'])),
      ],
      trendLimit: Json.d(trend['limit']),
      goods: Json.strs(j['goods']),
    );
  }
}

/// 生成餐程的约束。
class PlanRequest {
  final DateTime weekStart;
  final List<String> meals;
  final int servings;
  final List<int> memberIds;
  final int? weekdayMaxMinutes;
  final int? budgetFen;
  final bool preferExpiring;

  const PlanRequest({
    required this.weekStart,
    this.meals = const ['LUNCH', 'DINNER'],
    this.servings = 4,
    this.memberIds = const [],
    this.weekdayMaxMinutes = 30,
    this.budgetFen,
    this.preferExpiring = true,
  });

  Map<String, dynamic> toJson() => {
        'weekStart': ymd(weekStart),
        'meals': meals,
        'servings': servings,
        'memberIds': memberIds,
        if (weekdayMaxMinutes != null) 'weekdayMaxMinutes': weekdayMaxMinutes,
        if (budgetFen != null) 'budgetFen': budgetFen,
        'preferExpiring': preferExpiring,
      };
}

class MealPlanService {
  MealPlanService._();

  static Future<MealPlanDraft> create(PlanRequest req) async =>
      MealPlanDraft.fromJson(Json.map(await ApiClient.instance.post('/mealplan/drafts', body: req.toJson())));

  static Future<MealPlanDraft> get(int draftId) async =>
      MealPlanDraft.fromJson(Json.map(await ApiClient.instance.get('/mealplan/drafts/$draftId')));

  /// 「换一道」候选（同样满足约束且本周不重复）。
  static Future<List<({DishCard dish, int sodiumMg})>> alternatives(int draftId, int slotId) async {
    final d = Json.map(await ApiClient.instance.post('/mealplan/drafts/$draftId/slots/$slotId/alternatives'));
    return [for (final m in Json.maps(d['items'])) (dish: DishCard.fromJson(m), sodiumMg: Json.i(m['sodiumMg']))];
  }

  static Future<MealPlanDraft> replace(int draftId, int slotId, int dishId) async => MealPlanDraft.fromJson(
      Json.map(await ApiClient.instance.put('/mealplan/drafts/$draftId/slots/$slotId', body: {'dishId': dishId})));

  /// 生成 7 个食集与合并采购清单。
  static Future<({List<int> menuIds, int shoppingItemCount})> apply(int draftId) async {
    final d = Json.map(await ApiClient.instance.post('/mealplan/drafts/$draftId/apply'));
    return (
      menuIds: (d['menuIds'] as List? ?? const []).map((e) => Json.i(e)).toList(),
      shoppingItemCount: Json.i(d['shoppingItemCount']),
    );
  }

  /// 营养报告（range: week / month）。
  static Future<NutritionReport> report(int memberId, {String range = 'week'}) async => NutritionReport.fromJson(
      Json.map(await ApiClient.instance.get('/nutrition/report', query: {'memberId': memberId, 'range': range})));
}

/// 分 → 「¥326」。
String yuan(int fen) {
  final y = fen / 100;
  return y == y.roundToDouble() ? '¥${y.toInt()}' : '¥${y.toStringAsFixed(1)}';
}
