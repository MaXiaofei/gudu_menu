import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'app.dart';
import 'core/api_client.dart';
import 'core/app_theme.dart';
import 'core/theme_controller.dart';
import 'stores/auth_store.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();

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

  // DESIGN.md §13.4：状态栏全局兜底——底色跟随持久化主题（奶油/抹茶）+ 深色字。
  // 运行中切主题由 MenuApp 根部 AnnotatedRegion 跟随；ActionBar/BackHeader 精确控制。
  SystemChrome.setSystemUIOverlayStyle(
      brandOverlayStyle(themeController.tokens));

  runApp(MenuApp(
    authStore: authStore,
    themeController: themeController,
    scaffoldKey: scaffoldKey,
  ));
}
