import '../core/api_client.dart';
import '../models/dish.dart';
import 'pantry_service.dart';

/// 智荐首页（api-contract §4/§5，G4/G5）。
class RecommendService {
  RecommendService._();

  /// 首页智荐聚合：GET /recommend/home → 临期主推 / 你常做 / 季节鲜。
  static Future<RecommendHome> home() async {
    final data = await ApiClient.instance.get('/recommend/home');
    return RecommendHome.fromJson((data as Map?)?.cast<String, dynamic>() ?? {});
  }

  /// 最近做过：GET /cookbook/recent?limit=（按菜去重，最近优先；limit ≤ 30）。
  static Future<List<RecentCooked>> recent({int limit = 10}) async {
    final data = await ApiClient.instance
        .get('/cookbook/recent', query: {'limit': limit.clamp(1, 30)});
    if (data is List) {
      return data
          .map((e) => RecentCooked.fromJson(e as Map<String, dynamic>))
          .toList();
    }
    return [];
  }
}

/// GET /recommend/home 返回体。任一分区无数据为空数组，前端隐藏该分区。
class RecommendHome {
  final List<ExpiringItem> expiring;
  final List<DishCard> expiringPicks;
  final List<DishCard> frequent;
  final List<DishCard> seasonal;

  const RecommendHome({
    this.expiring = const [],
    this.expiringPicks = const [],
    this.frequent = const [],
    this.seasonal = const [],
  });

  static List<DishCard> _cards(dynamic v) => v is List
      ? v.map((e) => DishCard.fromJson(e as Map<String, dynamic>)).toList()
      : const [];

  factory RecommendHome.fromJson(Map<String, dynamic> j) => RecommendHome(
        expiring: j['expiring'] is List
            ? (j['expiring'] as List)
                .map((e) => ExpiringItem.fromJson(e as Map<String, dynamic>))
                .toList()
            : const [],
        expiringPicks: _cards(j['expiringPicks']),
        frequent: _cards(j['frequent']),
        seasonal: _cards(j['seasonal']),
      );

  bool get isEmpty =>
      expiring.isEmpty &&
      expiringPicks.isEmpty &&
      frequent.isEmpty &&
      seasonal.isEmpty;
}

/// 最近做过的一道菜（GET /cookbook/recent）。
class RecentCooked {
  final int dishId;
  final String name;
  final String? coverImage;
  final DateTime? lastCookedAt;
  final int cookedCount;

  const RecentCooked({
    required this.dishId,
    required this.name,
    this.coverImage,
    this.lastCookedAt,
    this.cookedCount = 0,
  });

  factory RecentCooked.fromJson(Map<String, dynamic> j) => RecentCooked(
        dishId: (j['dishId'] as num).toInt(),
        name: (j['name'] ?? '') as String,
        coverImage: (j['coverUrl'] ?? j['coverImage']) as String?,
        lastCookedAt: DateTime.tryParse(j['lastCookedAt']?.toString() ?? ''),
        cookedCount: (j['cookedCount'] as num?)?.toInt() ?? 0,
      );

  /// 「今天 / 昨天 / N 天前 / M/D」。
  String lastCookedLabel([DateTime? now]) {
    final at = lastCookedAt;
    if (at == null) return '';
    final n = now ?? DateTime.now();
    final days = DateTime(n.year, n.month, n.day)
        .difference(DateTime(at.year, at.month, at.day))
        .inDays;
    if (days <= 0) return '今天';
    if (days == 1) return '昨天';
    if (days < 7) return '$days 天前';
    return '${at.month}/${at.day}';
  }
}
