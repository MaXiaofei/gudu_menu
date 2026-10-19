/// 家庭成员（对应后端 Member）。
/// 列表页用：id/name + roleTags（角色标签）+ healthProfile.audiences（特殊人群）。
class Member {
  final int id;
  final String name;

  /// 角色标签，逗号分隔（掌勺,备菜）。
  final String? roleTags;

  /// 健康档案特殊人群标签（如 高血压/高血糖）。
  final List<String> audiences;

  /// 头像（V66 member.avatar_url；空 → 默认插画头像）。
  final String? avatarUrl;

  const Member({
    required this.id,
    required this.name,
    this.roleTags,
    this.audiences = const [],
    this.avatarUrl,
  });

  /// 宝宝档案（特殊人群含宝宝辅食）→ 默认头像用宝宝插画。
  bool get isBaby => audiences.any((a) => a.contains('宝宝'));

  factory Member.fromJson(Map<String, dynamic> j) {
    final hp = j['healthProfile'];
    final audiences = <String>[];
    if (hp is Map) {
      final a = hp['audiences'];
      if (a is List) audiences.addAll(a.whereType<String>());
    }
    return Member(
      id: (j['id'] as num).toInt(),
      name: (j['name'] ?? '') as String,
      roleTags: (j['roleTags'] as String?)?.trim().isNotEmpty == true
          ? j['roleTags'] as String
          : null,
      audiences: audiences,
      avatarUrl: (j['avatarUrl'] as String?)?.trim().isNotEmpty == true ? j['avatarUrl'] as String : null,
    );
  }

  /// 列表副信息：特殊人群在前，角色标签在后（「高血压 · 掌勺 · 备菜」）。
  String get subtitle {
    final parts = <String>[
      if (audiences.isNotEmpty) audiences.join('、'),
      if (roleTags != null)
        roleTags!
            .split(',')
            .map((s) => s.trim())
            .where((s) => s.isNotEmpty)
            .join(' · '),
    ];
    return parts.join(' · ');
  }
}
