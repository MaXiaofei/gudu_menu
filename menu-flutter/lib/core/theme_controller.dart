import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'app_theme.dart';

/// 品牌主题枚举：奶油轻食（暖橙）/ 抹茶禅意（草绿）。
enum BrandThemeMode { cream, matcha }

/// 主题控制器：持有当前品牌主题、持久化偏好、驱动 MaterialApp 重建。
///
/// 在 `main` 中 `await ThemeController.load()` 创建后，通过
/// `ChangeNotifierProvider.value` 注入树顶；UI 用 `AnimatedBuilder` 监听它，
/// 或用 `context.watch<ThemeController>()` 取当前主题。默认奶油轻食。
class ThemeController extends ChangeNotifier {
  /// 直接构造（默认奶油轻食）。生产环境优先用 [load] 恢复持久化偏好。
  ThemeController([this._mode = BrandThemeMode.cream]);

  static const _prefKey = 'gudu.brandTheme';

  BrandThemeMode _mode;

  BrandThemeMode get mode => _mode;
  bool get isCream => _mode == BrandThemeMode.cream;
  String get label => isCream ? '奶油轻食 · 暖橙' : '抹茶禅意 · 草绿';

  ThemeData get themeData => isCream ? creamTheme : matchaTheme;

  /// 启动时从 SharedPreferences 恢复，缺省奶油轻食。
  static Future<ThemeController> load() async {
    final prefs = await SharedPreferences.getInstance();
    final idx = prefs.getInt(_prefKey) ?? 0;
    final mode = BrandThemeMode.values[
        idx.clamp(0, BrandThemeMode.values.length - 1)];
    return ThemeController(mode);
  }

  Future<void> setMode(BrandThemeMode mode) async {
    if (_mode == mode) return;
    _mode = mode;
    notifyListeners();
    final prefs = await SharedPreferences.getInstance();
    await prefs.setInt(_prefKey, mode.index);
  }

  Future<void> toggle() =>
      setMode(isCream ? BrandThemeMode.matcha : BrandThemeMode.cream);
}
