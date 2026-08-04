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
import '../pages/menu/detail_page.dart';
import '../pages/menu/list_page.dart';
import '../pages/ingredient/create_page.dart';
import '../pages/ingredient/list_page.dart';
import '../pages/login_page.dart';
import '../pages/profile_page.dart';
import '../stores/auth_store.dart';
import '../widgets/main_shell.dart';

/// 路由表 + 登录拦截（对应小程序 401/未登录 reLaunch 到登录页）。
///
/// 结构：5 个 tab（菜谱/食集/[推荐]/库存/我的）包在 StatefulShellRoute 里，
/// 由 [MainShell] 注入底部 Tab Bar；非 tab 页（详情/录入/点评等）为顶层 GoRoute，
/// 由 tab 页 push 进入，不显示底部 bar。
///
/// refreshListenable 绑定 AuthStore：登录态变化自动重定向。
GoRouter createRouter(AuthStore auth) {
  return GoRouter(
    refreshListenable: auth,
    initialLocation: '/dish',
    redirect: (context, state) {
      final loggedIn = auth.isLoggedIn;
      final atLogin = state.matchedLocation == '/login';
      if (!loggedIn && !atLogin) return '/login';
      if (loggedIn && atLogin) return '/dish';
      return null;
    },
    routes: [
      GoRoute(path: '/login', builder: (_, __) => const LoginPage()),

      // ===== 5-Tab 主壳：菜谱 / 食集 / 推荐 / 库存 / 我的 =====
      StatefulShellRoute.indexedStack(
        builder: (context, state, navigationShell) =>
            MainShell(navigationShell: navigationShell),
        branches: [
          // 0. 菜谱
          StatefulShellBranch(routes: [
            GoRoute(path: '/dish', builder: (_, __) => const DishListPage()),
          ]),
          // 1. 食集
          StatefulShellBranch(routes: [
            GoRoute(path: '/menu', builder: (_, __) => const MenuListPage()),
          ]),
          // 2. 推荐（凸起 FAB）
          StatefulShellBranch(routes: [
            GoRoute(path: '/ai-recommend', builder: (_, __) => const AiRecommendPage()),
          ]),
          // 3. 库存
          StatefulShellBranch(routes: [
            GoRoute(path: '/pantry', builder: (_, __) => const PantryListPage()),
          ]),
          // 4. 我的
          StatefulShellBranch(routes: [
            GoRoute(path: '/profile', builder: (_, __) => const ProfilePage()),
          ]),
        ],
      ),

      // ===== 非 tab 页（push 进入，无底部 bar） =====
      // 菜品详情 + 点评
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
      // 录入新菜（手动 + URL 导入）
      GoRoute(
          path: '/create-dish',
          builder: (_, __) => const CreateDishPage()),
      // 食集详情（整集做菜落点）
      GoRoute(
        path: '/menu/:id',
        builder: (_, s) => MenuDetailPage(
          id: int.parse(s.pathParameters['id']!),
        ),
      ),
      // 食材库
      GoRoute(
          path: '/ingredient',
          builder: (_, __) => const IngredientListPage()),
      GoRoute(
          path: '/create-ingredient',
          builder: (_, __) => const CreateIngredientPage()),
      // 以下为 P1/P2 占位，后续替换为真实页面
      GoRoute(
          path: '/ai-estimate',
          builder: (_, __) => const AiEstimatePage()),
      GoRoute(
          path: '/mealplan',
          builder: (_, __) => const MealPlanPage()),
      GoRoute(
          path: '/shopping',
          builder: (_, __) => const ShoppingPage()),
      GoRoute(
          path: '/dailylog',
          builder: (_, __) => const DailyLogPage()),
    ],
  );
}
