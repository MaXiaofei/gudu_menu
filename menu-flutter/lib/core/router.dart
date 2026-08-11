import 'package:go_router/go_router.dart';

import '../pages/mealplan/mealplan_page.dart';
import '../pages/pantry/list_page.dart';
import '../pages/pantry/detail_page.dart';
import '../pages/pantry/manual_add_page.dart';
import '../pages/review/menu_review_page.dart';
import '../pages/review/menu_review_form_page.dart';
import '../pages/review/my_reviews_page.dart';
import '../pages/shopping/shopping_page.dart';
import '../pages/ai/estimate_page.dart';
import '../pages/ai/recommend_page.dart';
import '../pages/dailylog/daily_log_page.dart';
import '../pages/foodlog/food_log_page.dart';
import '../pages/foodlog/food_log_detail_page.dart';
import '../pages/dish/create_page.dart';
import '../pages/dish/detail_page.dart';
import '../pages/dish/dish_preview_page.dart';
import '../pages/dish/draft_list_page.dart';
import '../pages/dish/list_page.dart';
import '../pages/dish/review_page.dart';
import '../pages/menu/detail_page.dart';
import '../pages/menu/list_page.dart';
import '../pages/ingredient/create_page.dart';
import '../pages/ingredient/edit_page.dart';
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
          // 0. 菜谱（支持 ?selectForMenu=<menuId> 选择模式：从食集详情加菜进来）
          StatefulShellBranch(routes: [
            GoRoute(
              path: '/dish',
              builder: (_, s) => DishListPage(
                selectForMenuId: int.tryParse(
                    s.uri.queryParameters['selectForMenu'] ?? ''),
                sortLatest: s.uri.queryParameters['sort'] == 'latest',
              ),
            ),
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
        builder: (_, s) {
          // extra 形如 {'showActions': false}，从食集详情点进来查看菜谱时传入。
          final extra = s.extra;
          final showActions = (extra is Map && extra['showActions'] is bool)
              ? extra['showActions'] as bool
              : true;
          return DishDetailPage(
            id: int.parse(s.pathParameters['id']!),
            showActions: showActions,
          );
        },
      ),
      // 写菜谱（写菜谱 + 导入链接，DESIGN.md §16；?draftId= 草稿箱继续编辑）
      GoRoute(
          path: '/create-dish',
          builder: (_, s) => CreateDishPage(
                draftId: int.tryParse(
                    s.uri.queryParameters['draftId'] ?? ''),
              )),
      // 草稿箱（我的 Tab「草稿箱」进入，§16.4）
      GoRoute(
        path: '/dish-drafts',
        builder: (_, __) => const DishDraftListPage(),
      ),
      // 写菜谱预览（§16.4：extra 传 DishPreviewData，发布回调复用写菜谱页）
      GoRoute(
        path: '/dish-preview',
        builder: (_, s) => DishPreviewPage(
          data: s.extra as DishPreviewData,
        ),
      ),
      // 菜谱选择页（从食集详情「+ 加菜」进来，?menuId=<id> 选择模式）
      GoRoute(
        path: '/dish-picker',
        builder: (_, s) => DishListPage(
          selectForMenuId: int.tryParse(
              s.uri.queryParameters['menuId'] ?? ''),
        ),
      ),
      // 食集详情（整集做菜落点）
      GoRoute(
        path: '/menu/:id',
        builder: (_, s) => MenuDetailPage(
          id: int.parse(s.pathParameters['id']!),
        ),
      ),
      // 统一评价页（完成结果页/食集完成态/我的评价进入）
      GoRoute(
        path: '/menu/:id/review',
        builder: (_, s) => MenuReviewPage(
          menuId: int.parse(s.pathParameters['id']!),
        ),
      ),
      // 食集整体评价表单（统一评价页点「评价 →」进入）
      GoRoute(
        path: '/menu/:id/review-form',
        builder: (_, s) {
          final extra = s.extra;
          final menuName = (extra is Map && extra['menuName'] is String)
              ? extra['menuName'] as String
              : '';
          return MenuReviewFormPage(
            menuId: int.parse(s.pathParameters['id']!),
            menuName: menuName,
          );
        },
      ),
      // 我的评价（我的 tab 进入）
      GoRoute(
          path: '/my-reviews',
          builder: (_, __) => const MyReviewsPage()),
      // 食材库
      GoRoute(
          path: '/ingredient',
          builder: (_, __) => const IngredientListPage()),
      GoRoute(
          path: '/ingredient/:id/edit',
          builder: (_, s) => IngredientEditPage(
            ingredientId: int.parse(s.pathParameters['id']!),
          )),
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
          builder: (_, state) => ShoppingPage(
              // 备菜一键加购跳转：?listId= 加载完自动打开详情
              initialListId:
                  int.tryParse(state.uri.queryParameters['listId'] ?? ''))),
      GoRoute(
          path: '/dailylog',
          builder: (_, __) => const DailyLogPage()),
      // 食记（做菜日记）：月/年视图 + 按菜汇总 + 筛选 + 单条详情
      GoRoute(
          path: '/food-log',
          builder: (_, __) => const FoodLogPage()),
      GoRoute(
          path: '/food-log/detail',
          builder: (_, s) => FoodLogDetailPage(
            menuId: int.parse(s.uri.queryParameters['menuId'] ?? '0'),
          )),
      // 库存详情（盘点纠偏 + 变动明细）+ 手动添加
      // 注意：/pantry/add 放在 /pantry/:id 前面，避免 add 被当成 :id 匹配
      GoRoute(
          path: '/pantry/add',
          builder: (_, __) => const PantryManualAddPage()),
      GoRoute(
          path: '/pantry/:id',
          builder: (_, s) => PantryDetailPage(
            ingredientId: int.parse(s.pathParameters['id']!),
          )),
    ],
  );
}
