/// showcase-ai-community 共用类型（contracts.md「共用类型」）。
library;

int _int(dynamic v, [int d = 0]) => v is num ? v.toInt() : int.tryParse('${v ?? ''}') ?? d;
int? _intOrNull(dynamic v) => v is num ? v.toInt() : int.tryParse('${v ?? ''}');
String? _strOrNull(dynamic v) => v == null || '$v'.isEmpty ? null : '$v';
List<String> _strList(dynamic v) => v is List ? v.map((e) => '$e').toList() : const [];

/// 菜谱卡（Agent 推荐、餐程格子、营养建议、帖子关联菜谱共用）。
class DishCard {
  final int id;
  final String name;
  final String? coverUrl;
  final int? minutes;
  final String? difficulty;
  final int have;
  final int total;
  final List<String> missing;
  final List<String> tags;

  const DishCard({
    required this.id,
    required this.name,
    this.coverUrl,
    this.minutes,
    this.difficulty,
    this.have = 0,
    this.total = 0,
    this.missing = const [],
    this.tags = const [],
  });

  factory DishCard.fromJson(Map<String, dynamic> j) {
    final cov = j['coverage'];
    final c = cov is Map ? cov : const {};
    return DishCard(
      id: _int(j['id'] ?? j['dishId']),
      name: '${j['name'] ?? ''}',
      coverUrl: _strOrNull(j['coverUrl']),
      minutes: _intOrNull(j['minutes']),
      difficulty: _strOrNull(j['difficulty']),
      have: _int(c['have']),
      total: _int(c['total']),
      missing: _strList(c['missing']),
      tags: _strList(j['tags']),
    );
  }

  /// 「家里够」/「缺 1 样 · 小葱」。total 为 0（未知覆盖率）时返回 null。
  String? get coverageText {
    if (total <= 0) return null;
    if (missing.isEmpty && have >= total) return '家里够';
    final n = missing.isNotEmpty ? missing.length : (total - have);
    return missing.isEmpty ? '缺 $n 样' : '缺 $n 样 · ${missing.take(2).join('、')}';
  }

  bool get fullyCovered => total > 0 && have >= total;
}

class MemberLite {
  final int memberId;
  final String name;
  final String? avatarUrl;
  const MemberLite({required this.memberId, required this.name, this.avatarUrl});

  factory MemberLite.fromJson(Map<String, dynamic> j) => MemberLite(
        memberId: _int(j['memberId'] ?? j['id']),
        name: '${j['name'] ?? ''}',
        avatarUrl: _strOrNull(j['avatarUrl']),
      );

  Map<String, dynamic> toJson() => {'memberId': memberId, 'name': name, 'avatarUrl': avatarUrl};
}

class UserLite {
  final int userId;
  final String nickname;
  final String? avatarUrl;
  const UserLite({required this.userId, required this.nickname, this.avatarUrl});

  factory UserLite.fromJson(dynamic j) {
    if (j is! Map) return const UserLite(userId: 0, nickname: '');
    return UserLite(
      userId: _int(j['userId'] ?? j['id']),
      nickname: '${j['nickname'] ?? j['name'] ?? ''}',
      avatarUrl: _strOrNull(j['avatarUrl']),
    );
  }
}

class IngredientLine {
  final int? ingredientId;
  final String name;
  final String? iconKey;
  final String amount;
  final String? unit;
  const IngredientLine({this.ingredientId, required this.name, this.iconKey, this.amount = '', this.unit});

  factory IngredientLine.fromJson(Map<String, dynamic> j) => IngredientLine(
        ingredientId: _intOrNull(j['ingredientId']),
        name: '${j['name'] ?? ''}',
        iconKey: _strOrNull(j['iconKey']),
        amount: '${j['amount'] ?? ''}',
        unit: _strOrNull(j['unit']),
      );
}

/// 解析辅助（供各 service 复用，保持宽松：缺字段给默认值不抛异常）。
class Json {
  Json._();
  static int i(dynamic v, [int d = 0]) => _int(v, d);
  static int? iOrNull(dynamic v) => _intOrNull(v);
  static double d(dynamic v, [double def = 0]) => v is num ? v.toDouble() : double.tryParse('${v ?? ''}') ?? def;
  static String s(dynamic v, [String d = '']) => v == null ? d : '$v';
  static String? sOrNull(dynamic v) => _strOrNull(v);
  static bool b(dynamic v) => v == true || v == 1 || v == 'true';
  static List<String> strs(dynamic v) => _strList(v);
  static Map<String, dynamic> map(dynamic v) => v is Map ? Map<String, dynamic>.from(v) : <String, dynamic>{};
  static List<Map<String, dynamic>> maps(dynamic v) =>
      v is List ? v.whereType<Map>().map((e) => Map<String, dynamic>.from(e)).toList() : const [];
  static DateTime? date(dynamic v) => v == null ? null : DateTime.tryParse('$v');
}
