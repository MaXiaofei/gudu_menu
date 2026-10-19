import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../core/api_client.dart';
import '../core/constants.dart';
import '../services/auth_service.dart';

/// 鉴权状态（对应 menu-mini/src/store/auth.ts）。
/// token 持久化到 SharedPreferences；同时同步给 ApiClient 供请求头使用。
class AuthStore extends ChangeNotifier {
  String _token = '';
  String nickname = '';
  bool _loading = false;

  String get token => _token;
  bool get isLoggedIn => _token.isNotEmpty;
  bool get loading => _loading;

  /// 启动时从持久化恢复 token。
  Future<void> init() async {
    final sp = await SharedPreferences.getInstance();
    _token = sp.getString(AppConstants.tokenKey) ?? '';
    ApiClient.instance.token = _token.isEmpty ? null : _token;
  }

  /// 登录成功返回 true；失败返回 false（错误 toast 已由拦截器弹出）。
  Future<bool> login(String username, String password) async {
    _loading = true;
    notifyListeners();
    try {
      final r = await AuthService.login(username, password);
      _token = r.token;
      nickname = r.nickname;
      ApiClient.instance.token = _token;
      final sp = await SharedPreferences.getInstance();
      await sp.setString(AppConstants.tokenKey, _token);
      return true;
    } catch (_) {
      return false;
    } finally {
      _loading = false;
      notifyListeners();
    }
  }

  /// 个人资料改昵称后同步（我的页头卡即时刷新）。
  void updateNickname(String name) {
    if (nickname == name) return;
    nickname = name;
    notifyListeners();
  }

  Future<void> logout() async {
    _token = '';
    nickname = '';
    ApiClient.instance.token = null;
    final sp = await SharedPreferences.getInstance();
    await sp.remove(AppConstants.tokenKey);
    notifyListeners();
  }
}
