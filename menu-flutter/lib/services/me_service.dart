import '../core/api_client.dart';

/// 「我的」统计（prototype-ui-rollout P0 契约 `GET /me/stats`）。
class MeStats {
  final int cookedThisMonth;
  final int familyDishes;
  /// 临期用掉（原「本月少扔」）：本月在保质期前 3 天内被做菜用掉的库存条数。
  final int savedFromWaste;
  const MeStats({required this.cookedThisMonth, required this.familyDishes, required this.savedFromWaste});

  factory MeStats.fromJson(Map<String, dynamic> j) => MeStats(
        cookedThisMonth: (j['cookedThisMonth'] as num?)?.toInt() ?? 0,
        familyDishes: (j['familyDishes'] as num?)?.toInt() ?? 0,
        savedFromWaste: (j['savedFromWaste'] as num?)?.toInt() ?? 0,
      );
}

class MeService {
  MeService._();

  /// 旧后端无该接口时返回 null（页面隐藏统计格，不报错）。
  static Future<MeStats?> stats() async {
    try {
      final data = await ApiClient.instance.get('/me/stats');
      if (data is Map<String, dynamic> && data.containsKey('cookedThisMonth')) return MeStats.fromJson(data);
    } catch (_) {
      // 降级：隐藏统计
    }
    return null;
  }
}
