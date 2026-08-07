import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'app.dart';
import 'core/api_client.dart';
import 'core/theme_controller.dart';
import 'stores/auth_store.dart';

/// 状态栏全局兜底色 = AppTokens.bg（cream 默认主题值 #FDFAF4）。
/// 顶栏改造后所有页面顶栏都是奶油底，状态栏与其融合，无色差断层。
/// ActionBar/BackHeader 内置 AnnotatedRegion 是精确控制（主），此处是兜底（辅）。
const Color _kStatusBarBg = Color(0xFFFDFAF4);

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();

  // DESIGN.md §13.4：状态栏全局兜底——奶油底 + 深色字。
  SystemChrome.setSystemUIOverlayStyle(const SystemUiOverlayStyle(
    statusBarColor: _kStatusBarBg,
    statusBarIconBrightness: Brightness.dark, // Android 深色字/图标
    statusBarBrightness: Brightness.light, // iOS：light 表示状态栏内容为深色
    systemNavigationBarColor: _kStatusBarBg,
    systemNavigationBarIconBrightness: Brightness.dark,
  ));

  // 全局 SnackBar key，供 ApiClient 的错误提示使用。
  final scaffoldKey = GlobalKey<ScaffoldMessengerState>();
  ApiClient.instance.init(
    onErrorToast: (msg) {
      scaffoldKey.currentState?.showSnackBar(
        SnackBar(content: Text(msg), duration: const Duration(seconds: 2)),
      );
    },
  );

  // 启动时从持久化恢复 token / 主题偏好，并同步给 ApiClient。
  final authStore = AuthStore();
  await authStore.init();
  final themeController = await ThemeController.load();

  runApp(MenuApp(
    authStore: authStore,
    themeController: themeController,
    scaffoldKey: scaffoldKey,
  ));
}
