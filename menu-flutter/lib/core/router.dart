import 'package:go_router/go_router.dart';

import '../pages/mealplan/mealplan_page.dart';
import '../pages/pantry/list_page.dart';
import '../pages/shopping/shopping_page.dart';
import '../pages/ai/estimate_page.dart';
import '../pages/ai/recommend_page.dart';
import '../pages/dailylog/daily_log_page.dart';
import '../pages/dish/create_page.dart';
import '../pages/dish/detail_page.dart';
import '../pages/dish/list_page.dart';
import '../pages/dish/review_page.dart';
import '../pages/home_page.dart';
import '../pages/ingredient/create_page.dart';
import '../pages/ingredient/list_page.dart';
import '../pages/login_page.dart';
import '../stores/auth_store.dart';

/// 路由表 + 登录拦截（对应小程序 401/未登录 reLaunch 到登录页）。
/// refreshListenable 绑定 AuthStore：登录态变化自动重定向。
GoRouter createRouter(AuthStore auth) {
  return GoRouter(
    refreshListenable: auth,
    initialLocation: '/',
    redirect: (context, state) {
      final loggedIn = auth.isLoggedIn;
      final atLogin = state.matchedLocation == '/login';
      if (!loggedIn && !atLogin) return '/login';
      if (loggedIn && atLogin) return '/';
      return null;
    },
    routes: [
      GoRoute(path: '/login', builder: (_, __) => const LoginPage()),
      GoRoute(path: '/', builder: (_, __) => const HomePage()),
      GoRoute(path: '/dish', builder: (_, __) => const DishListPage()),
      // 点评
      GoRoute(
        path: '/dish/:id/review',
        builder: (_, s) => ReviewPage(
          dishId: int.parse(s.pathParameters['id']!),
        ),
      ),
      GoRoute(
        path: '/dish/:id',
        builder: (_, s) =>
            DishDetailPage(id: int.parse(s.pathParameters['id']!)),
      ),
      // === 录入新菜（手动 + URL 导入） ===
      GoRoute(
          path: '/create-dish',
          builder: (_, __) => const CreateDishPage()),
      // 以下为 P1/P2 占位，后续替换为真实页面
      // === 食材库 ===
      GoRoute(
          path: '/ingredient',
          builder: (_, __) => const IngredientListPage()),
      // === 录入食材 ===
      GoRoute(
          path: '/create-ingredient',
          builder: (_, __) => const CreateIngredientPage()),
      // 以下仍为占位
      GoRoute(
          path: '/ai-recommend',
          builder: (_, __) => const AiRecommendPage()),
      GoRoute(
          path: '/ai-estimate',
          builder: (_, __) => const AiEstimatePage()),
      GoRoute(
          path: '/mealplan',
          builder: (_, __) => const MealPlanPage()),
      GoRoute(
          path: '/pantry',
          builder: (_, __) => const PantryListPage()),
      GoRoute(
          path: '/shopping',
          builder: (_, __) => const ShoppingPage()),
      GoRoute(
          path: '/dailylog',
          builder: (_, __) => const DailyLogPage()),
    ],
  );
}
