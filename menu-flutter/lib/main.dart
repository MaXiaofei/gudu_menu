import 'package:flutter/material.dart';

import 'app.dart';
import 'core/api_client.dart';
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

  runApp(MenuApp(
    authStore: authStore,
    themeController: themeController,
    scaffoldKey: scaffoldKey,
  ));
}
