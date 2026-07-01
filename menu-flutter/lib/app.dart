import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import 'core/api_client.dart';
import 'core/router.dart';
import 'core/theme_controller.dart';
import 'stores/auth_store.dart';
import 'stores/member_store.dart';

/// App 根：注入 stores + 创建路由 + 绑定 401 跳登录。
/// 主题由 [ThemeController] 驱动（双主题：奶油轻食 / 抹茶禅意），见 core/app_theme.dart。
class MenuApp extends StatefulWidget {
  final AuthStore authStore;
  final ThemeController themeController;
  final GlobalKey<ScaffoldMessengerState> scaffoldKey;

  const MenuApp({
    super.key,
    required this.authStore,
    required this.themeController,
    required this.scaffoldKey,
  });

  @override
  State<MenuApp> createState() => _MenuAppState();
}

class _MenuAppState extends State<MenuApp> {
  late final MemberStore _memberStore = MemberStore();
  late final GoRouter _router;

  @override
  void initState() {
    super.initState();
    _router = createRouter(widget.authStore);
    // 401 未登录 → 清栈跳登录页（对应小程序 reLaunch）
    ApiClient.instance.onUnauthorized = () => _router.go('/login');
  }

  @override
  Widget build(BuildContext context) => MultiProvider(
        providers: [
          ChangeNotifierProvider.value(value: widget.authStore),
          ChangeNotifierProvider.value(value: widget.themeController),
          ChangeNotifierProvider.value(value: _memberStore),
        ],
        child: AnimatedBuilder(
          animation: widget.themeController,
          builder: (context, _) => MaterialApp.router(
            scaffoldMessengerKey: widget.scaffoldKey,
            title: '咕嘟小食单',
            theme: widget.themeController.themeData,
            routerConfig: _router,
            debugShowCheckedModeBanner: false,
          ),
        ),
      );
}
