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
    // 401 未登录 → 先登出（清 AuthStore 内存态 + 持久化 token，让 router 的
    // redirect 判断 isLoggedIn=false，登录页才不会被弹回主壳）→ 再清栈跳登录页。
    // 只 go('/login') 不登出的话：isLoggedIn 仍为 true，redirect 会把登录页弹回 /dish，
    // 形成"永远到不了登录页、所有页面 401"的死循环。
    ApiClient.instance.onUnauthorized = () {
      widget.authStore.logout();
      _router.go('/login');
    };
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
