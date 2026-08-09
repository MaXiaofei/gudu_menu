import '../core/api_client.dart';
import '../core/constants.dart';

// ===================== 模型 =====================

/// 邀请凭证：口令 + token（url = {base}/together.html?token=，二维码内容=url）。
class TogetherInvite {
  final String code;
  final String token;
  String get url => '${AppConstants.baseUrl}/together.html?token=$token';

  TogetherInvite({required this.code, required this.token});

  factory TogetherInvite.fromJson(Map<String, dynamic> j) =>
      TogetherInvite(code: j['code'] as String, token: j['token'] as String);
}

class TogetherMember {
  final int? memberId;
  final String nickname;
  final String? lastActiveAt;
  TogetherMember({this.memberId, required this.nickname, this.lastActiveAt});

  factory TogetherMember.fromJson(Map<String, dynamic> j) => TogetherMember(
      memberId: j['memberId'] as int?,
      nickname: j['nickname'] as String? ?? '',
      lastActiveAt: j['lastActiveAt'] as String?);
}

class TogetherDish {
  final int id;
  final int? dishId;
  final String dishName;
  final String? note;
  final String? addedByNickname;
  TogetherDish({
    required this.id,
    this.dishId,
    required this.dishName,
    this.note,
    this.addedByNickname,
  });

  factory TogetherDish.fromJson(Map<String, dynamic> j) => TogetherDish(
      id: (j['id'] as num).toInt(),
      dishId: j['dishId'] as int?,
      dishName: j['dishName'] as String? ?? '',
      note: j['note'] as String?,
      addedByNickname: j['addedByNickname'] as String?);
}

class TogetherActivity {
  final String nickname;
  final String action; // add / remove
  final String dishName;
  final String? createTime;
  TogetherActivity({
    required this.nickname,
    required this.action,
    required this.dishName,
    this.createTime,
  });

  factory TogetherActivity.fromJson(Map<String, dynamic> j) =>
      TogetherActivity(
          nickname: j['nickname'] as String? ?? '',
          action: j['action'] as String? ?? '',
          dishName: j['dishName'] as String? ?? '',
          createTime: j['createTime'] as String?);
}

class TogetherVO {
  final List<TogetherMember> members;
  final List<TogetherDish> dishes;
  final List<TogetherActivity> activities;
  final TogetherInvite? invite;
  TogetherVO({
    required this.members,
    required this.dishes,
    required this.activities,
    this.invite,
  });

  factory TogetherVO.fromJson(Map<String, dynamic> j) => TogetherVO(
      members: (j['members'] as List? ?? [])
          .map((e) => TogetherMember.fromJson(e as Map<String, dynamic>))
          .toList(),
      dishes: (j['dishes'] as List? ?? [])
          .map((e) => TogetherDish.fromJson(e as Map<String, dynamic>))
          .toList(),
      activities: (j['activities'] as List? ?? [])
          .map((e) => TogetherActivity.fromJson(e as Map<String, dynamic>))
          .toList(),
      invite: j['invite'] == null
          ? null
          : TogetherInvite.fromJson(j['invite'] as Map<String, dynamic>));
}

/// 聚餐（一起点菜）服务：邀请生成 / 清单轮询 / 加菜 / 删菜。
/// 身份：APP 用户走登录态（Sa-Token）；H5 访客走 X-Guest-Key（本端不涉及）。
class TogetherService {
  /// 生成/刷新邀请（登录用户）：返回口令 + token。
  static Future<TogetherInvite> invite(int menuId) async {
    final d = await ApiClient.instance.post('/menu/$menuId/invite');
    return TogetherInvite.fromJson(d as Map<String, dynamic>);
  }

  /// 聚餐清单（轮询）。
  static Future<TogetherVO> together(int menuId) async {
    final d = await ApiClient.instance.get('/menu/$menuId/together');
    return TogetherVO.fromJson(d as Map<String, dynamic>);
  }

  /// 加菜：dishId（菜谱）或 customName（自由输入）二选一，可带备注。
  static Future<void> addItem(int menuId,
      {int? dishId, String? customName, String? note}) async {
    await ApiClient.instance.post('/menu/$menuId/together/items', body: {
      if (dishId != null) 'dishId': dishId,
      if (customName != null) 'customName': customName,
      if (note != null && note.isNotEmpty) 'note': note,
    });
  }

  /// 删菜（已加入成员可删任意菜）。
  static Future<void> removeItem(int menuId, int menuDishId) async {
    await ApiClient.instance
        .delete('/menu/$menuId/together/items/$menuDishId');
  }
}
