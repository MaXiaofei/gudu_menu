// 备菜（对应后端 prep 包：PrepItemVO / MenuPrepVO / PrepStatus）。
//
// 后端 `GET /menu/{id}/prep` 返回 `MenuPrep`：
// `{ items:[PrepItem], condiments:[PrepItem], readyCount, totalCount }`
// - items：主料（purchaseCategoryId != 调味料），需备料、计入进度。
// - condiments：调料折叠组（purchaseCategoryId = 调味料），无需备料、不计进度。
// - readyCount/totalCount：进度（已备 / 共需备料，不含调料）。

/// 备料状态（后端 PrepStatus enum 名）。
enum PrepStatus {
  pending, // PENDING 待备
  ready, // READY 已备
  thawing, // THAWING 化冻中
  marinating; // MARINATING 腌制中

  /// 后端返回的大写名 → enum（未知/空 → pending）。
  static PrepStatus fromName(String? name) {
    switch (name?.toUpperCase()) {
      case 'READY':
        return PrepStatus.ready;
      case 'THAWING':
        return PrepStatus.thawing;
      case 'MARINATING':
        return PrepStatus.marinating;
      default:
        return PrepStatus.pending;
    }
  }

  /// 提交后端的大写名（PUT ?status=）。
  String get name => switch (this) {
        PrepStatus.pending => 'PENDING',
        PrepStatus.ready => 'READY',
        PrepStatus.thawing => 'THAWING',
        PrepStatus.marinating => 'MARINATING',
      };

  /// 中文标签。
  String get label => switch (this) {
        PrepStatus.pending => '待备',
        PrepStatus.ready => '已备',
        PrepStatus.thawing => '化冻中',
        PrepStatus.marinating => '腌制中',
      };
}

/// 备菜列表一行（按食材聚合）：后端 PrepItemVO record。
class PrepItem {
  final int ingredientId;
  final String ingredientName;
  /// 聚合总克数（已 ×servingFactor，不减库存）。
  final double totalGrams;
  /// 被几道菜用到。
  final int dishCount;
  /// 用到该食材的菜名列表（共用高亮用）。
  final List<String> dishNames;
  final PrepStatus status;
  /// 是否共用项（dishCount >= 2），前端 🔥 高亮便利字段。
  final bool shared;

  const PrepItem({
    required this.ingredientId,
    required this.ingredientName,
    required this.totalGrams,
    required this.dishCount,
    required this.dishNames,
    required this.status,
    required this.shared,
  });

  factory PrepItem.fromJson(Map<String, dynamic> j) => PrepItem(
        ingredientId: (j['ingredientId'] as num).toInt(),
        ingredientName: (j['ingredientName'] ?? '') as String,
        totalGrams: (j['totalGrams'] as num?)?.toDouble() ?? 0,
        dishCount: (j['dishCount'] as num?)?.toInt() ?? 0,
        dishNames: ((j['dishNames'] ?? const []) as List)
            .map((e) => e as String)
            .toList(),
        status: PrepStatus.fromName(j['status'] as String?),
        shared: (j['shared'] as bool?) ?? false,
      );

  PrepItem copyWithStatus(PrepStatus s) => PrepItem(
        ingredientId: ingredientId,
        ingredientName: ingredientName,
        totalGrams: totalGrams,
        dishCount: dishCount,
        dishNames: dishNames,
        status: s,
        shared: shared,
      );
}

/// 备菜聚合（后端 MenuPrepVO record）。
class MenuPrep {
  final List<PrepItem> items;
  final List<PrepItem> condiments;
  final int readyCount;
  final int totalCount;

  const MenuPrep({
    required this.items,
    required this.condiments,
    required this.readyCount,
    required this.totalCount,
  });

  factory MenuPrep.fromJson(Map<String, dynamic> j) => MenuPrep(
        items: ((j['items'] ?? const []) as List)
            .map((e) => PrepItem.fromJson(e as Map<String, dynamic>))
            .toList(),
        condiments: ((j['condiments'] ?? const []) as List)
            .map((e) => PrepItem.fromJson(e as Map<String, dynamic>))
            .toList(),
        readyCount: (j['readyCount'] as num?)?.toInt() ?? 0,
        totalCount: (j['totalCount'] as num?)?.toInt() ?? 0,
      );
}
