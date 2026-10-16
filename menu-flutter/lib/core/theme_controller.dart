import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'app_theme.dart';
import 'tokens.g.dart';

/// 主题控制器：持有当前皮肤、持久化偏好、驱动 MaterialApp 重建。
///
/// 皮肤列表来自 `tokens.g.dart` 的 [kBrandSkins]（由 docs/design/tokens.json 生成），
/// 新增皮肤只改 JSON 并重新生成，无需改这里。
///
/// 在 `main` 中 `await ThemeController.load()` 创建后，通过
/// `ChangeNotifierProvider.value` 注入树顶；UI 用 `AnimatedBuilder` 监听它，
/// 或用 `context.watch<ThemeController>()` 取当前皮肤。默认为注册表第一项。
class ThemeController extends ChangeNotifier {
  /// 直接构造（缺省默认皮肤）。生产环境优先用 [load] 恢复持久化偏好。
  ThemeController([String? skinKey]) : _skin = brandSkinOf(skinKey);

  static const prefKey = 'gudu.brandSkin';

  /// 旧版偏好：int 下标（0=cream，1=matcha），仅读取用于迁移。
  static const legacyPrefKey = 'gudu.brandTheme';
  static const _legacyKeys = ['cream', 'matcha'];

  BrandSkin _skin;

  BrandSkin get skin => _skin;
  String get skinKey => _skin.key;
  String get label => _skin.label;
  List<BrandSkin> get skins => kBrandSkins;

  /// 当前皮肤 token（状态栏等 MaterialApp 之外的场景用）。
  AppTokens get tokens => _skin.tokens;

  ThemeData get themeData => buildBrandTheme(_skin.tokens);

  /// 启动时从 SharedPreferences 恢复；兼容旧版 int 偏好；缺省默认皮肤。
  static Future<ThemeController> load() async {
    final prefs = await SharedPreferences.getInstance();
    var key = prefs.getString(prefKey);
    if (key == null) {
      final idx = prefs.getInt(legacyPrefKey);
      if (idx != null && idx >= 0 && idx < _legacyKeys.length) {
        key = _legacyKeys[idx];
      }
    }
    return ThemeController(key);
  }

  Future<void> setSkin(String key) async {
    final next = brandSkinOf(key);
    if (next.key == _skin.key) return;
    _skin = next;
    notifyListeners();
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(prefKey, next.key);
    await prefs.remove(legacyPrefKey);
  }
}
