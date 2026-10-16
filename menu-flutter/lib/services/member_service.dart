import '../core/api_client.dart';
import '../models/member.dart';

/// 成员服务（对应 menu-mini/src/api/member.ts + MemberController）。
///
/// 注意契约不严谨：后端 /member 返回 IPage<Member>，小程序当数组用。
/// 这里统一兼容 List 与 IPage，正确解包成 List<Member>。
class MemberService {
  static Future<List<Member>> list() async {
    final data = await ApiClient.instance.get('/member');
    if (data is List) {
      return data
          .map((e) => Member.fromJson(e as Map<String, dynamic>))
          .toList();
    }
    if (data is Map && data['records'] is List) {
      return (data['records'] as List)
          .map((e) => Member.fromJson(e as Map<String, dynamic>))
          .toList();
    }
    return [];
  }

  /// 切换当前就餐成员：POST /member/current?memberId=
  static Future<void> setCurrent(int memberId) async {
    await ApiClient.instance.post('/member/current', query: {'memberId': memberId});
  }

  /// 取当前成员：GET /member/current → Long（无则 0）。
  static Future<int> getCurrent() async {
    final data = await ApiClient.instance.get('/member/current');
    if (data == null) return 0;
    return (data as num).toInt();
  }

  /// 当前登录成员资料：GET /member/me（api-contract §6）。
  static Future<MemberProfile> me() async {
    final data = await ApiClient.instance.get('/member/me');
    return MemberProfile.fromJson(data as Map<String, dynamic>);
  }

  /// 修改个人资料：PUT /member/me（name 1–20 字；avatarUrl 须为本站 /uploads/ 地址）。
  static Future<void> updateMe({String? name, String? avatarUrl}) async {
    await ApiClient.instance.put('/member/me', body: {
      if (name != null) 'name': name,
      if (avatarUrl != null) 'avatarUrl': avatarUrl,
    });
  }
}

/// 当前登录成员资料（GET /member/me）。
class MemberProfile {
  final int id;
  final String name;
  final String? avatarUrl;
  /// 脱敏手机号（138****0001），未绑定为 null。
  final String? phone;
  final bool isAdmin;

  const MemberProfile({
    required this.id,
    required this.name,
    this.avatarUrl,
    this.phone,
    this.isAdmin = false,
  });

  factory MemberProfile.fromJson(Map<String, dynamic> j) => MemberProfile(
        id: (j['id'] as num?)?.toInt() ?? 0,
        name: (j['name'] ?? '') as String,
        avatarUrl: j['avatarUrl'] as String?,
        phone: j['phone'] as String?,
        isAdmin: j['isAdmin'] as bool? ?? false,
      );
}
