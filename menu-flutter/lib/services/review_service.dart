import '../core/api_client.dart';

/// 点评服务（V43：食集评价 + 我的评价 + 单菜均分）。
class ReviewService {
  /// 点评维度字典：GET /dict?group=review_dimension
  static Future<List<ReviewDimension>> dimensions() async {
    final data = await ApiClient.instance.get(
      '/dict',
      query: {'group': 'review_dimension'},
    );
    if (data is List) {
      return data
          .map((e) => ReviewDimension.fromJson(e as Map<String, dynamic>))
          .toList();
    }
    if (data is Map && data['records'] is List) {
      return (data['records'] as List)
          .map((e) => ReviewDimension.fromJson(e as Map<String, dynamic>))
          .toList();
    }
    return [];
  }

  /// 提交点评（dishId/menuId 二选一）：POST /review
  static Future<void> submitReview(Map<String, dynamic> data) async {
    await ApiClient.instance.post('/review', body: data);
  }

  /// 统一评价页数据：GET /review/menu-overview/{menuId}
  static Future<MenuReviewOverview> menuOverview(int menuId) async {
    final data = await ApiClient.instance.get('/review/menu-overview/$menuId');
    return MenuReviewOverview.fromJson(data as Map<String, dynamic>);
  }

  /// 我的评价：GET /review/mine
  static Future<MyReviews> myReviews() async {
    final data = await ApiClient.instance.get('/review/mine');
    return MyReviews.fromJson(data as Map<String, dynamic>);
  }

  /// 单菜均分 + 评价数（菜谱详情展示）：GET /review/dish/{dishId}/avg
  static Future<(double?, int)> dishAvg(int dishId) async {
    final data = await ApiClient.instance.get('/review/dish/$dishId/avg');
    if (data is Map && data['star'] is String) {
      return (double.tryParse(data['star'] as String), (data['count'] as num?)?.toInt() ?? 0);
    }
    return (null, 0);
  }
}

/// 点评维度（如"味道""口感""外观"）。
class ReviewDimension {
  final int id;
  final String name;

  const ReviewDimension({required this.id, required this.name});

  factory ReviewDimension.fromJson(Map<String, dynamic> j) => ReviewDimension(
        id: (j['id'] as num).toInt(),
        name: (j['name'] ?? '') as String,
      );
}

/// 统一评价页数据（GET /review/menu-overview/{menuId}）。
class MenuReviewOverview {
  final int menuId;
  final String menuName;
  final String? finishedAt;
  final int dishCount;
  final MenuReviewStatus? menuReview;
  final List<DishReviewStatus> dishes;

  const MenuReviewOverview({
    required this.menuId,
    required this.menuName,
    this.finishedAt,
    required this.dishCount,
    this.menuReview,
    required this.dishes,
  });

  factory MenuReviewOverview.fromJson(Map<String, dynamic> j) =>
      MenuReviewOverview(
        menuId: (j['menuId'] as num).toInt(),
        menuName: (j['menuName'] ?? '') as String,
        finishedAt: j['finishedAt'] as String?,
        dishCount: (j['dishCount'] as num?)?.toInt() ?? 0,
        menuReview: j['menuReview'] == null
            ? null
            : MenuReviewStatus.fromJson(j['menuReview'] as Map<String, dynamic>),
        dishes: ((j['dishes'] as List?) ?? [])
            .map((e) => DishReviewStatus.fromJson(e as Map<String, dynamic>))
            .toList(),
      );
}

/// 食集整体评价状态（我的最近一条）。
class MenuReviewStatus {
  final bool reviewed;
  final int? starRating;
  final Map<int, int> dimensionScores;
  final String? createTime;

  const MenuReviewStatus({
    required this.reviewed,
    this.starRating,
    required this.dimensionScores,
    this.createTime,
  });

  factory MenuReviewStatus.fromJson(Map<String, dynamic> j) => MenuReviewStatus(
        reviewed: (j['reviewed'] as bool?) ?? false,
        starRating: (j['starRating'] as num?)?.toInt(),
        dimensionScores: ((j['dimensionScores'] as Map?) ?? {})
            .map((k, v) => MapEntry(int.parse(k as String), (v as num).toInt())),
        createTime: j['createTime'] as String?,
      );
}

/// 单道菜评价状态（我的最近一条）。
class DishReviewStatus {
  final int dishId;
  final String dishName;
  final String? coverUrl;
  final int? starRating;

  const DishReviewStatus({
    required this.dishId,
    required this.dishName,
    this.coverUrl,
    this.starRating,
  });

  factory DishReviewStatus.fromJson(Map<String, dynamic> j) => DishReviewStatus(
        dishId: (j['dishId'] as num).toInt(),
        dishName: (j['dishName'] ?? '') as String,
        coverUrl: j['coverUrl'] as String?,
        starRating: (j['starRating'] as num?)?.toInt(),
      );
}

/// 我的评价（GET /review/mine）。
class MyReviews {
  final List<ReviewEntry> reviews;
  final List<PendingMenu> pendingMenus;

  const MyReviews({required this.reviews, required this.pendingMenus});

  factory MyReviews.fromJson(Map<String, dynamic> j) => MyReviews(
        reviews: ((j['reviews'] as List?) ?? [])
            .map((e) => ReviewEntry.fromJson(e as Map<String, dynamic>))
            .toList(),
        pendingMenus: ((j['pendingMenus'] as List?) ?? [])
            .map((e) => PendingMenu.fromJson(e as Map<String, dynamic>))
            .toList(),
      );
}

/// 评价历史条目（食集或菜品）。
class ReviewEntry {
  final int id;
  final int? dishId;
  final int? menuId;
  final String? name;
  final int starRating;
  final String? createTime;

  const ReviewEntry({
    required this.id,
    this.dishId,
    this.menuId,
    this.name,
    required this.starRating,
    this.createTime,
  });

  bool get isMenu => dishId == null && menuId != null;

  factory ReviewEntry.fromJson(Map<String, dynamic> j) => ReviewEntry(
        id: (j['id'] as num).toInt(),
        dishId: (j['dishId'] as num?)?.toInt(),
        menuId: (j['menuId'] as num?)?.toInt(),
        name: j['name'] as String?,
        starRating: (j['starRating'] as num).toInt(),
        createTime: j['createTime'] as String?,
      );
}

/// 待评价食集。
class PendingMenu {
  final int menuId;
  final String menuName;
  final String? finishedAt;
  final int dishCount;
  final int reviewedDishCount;
  final bool menuReviewed;

  const PendingMenu({
    required this.menuId,
    required this.menuName,
    this.finishedAt,
    required this.dishCount,
    required this.reviewedDishCount,
    required this.menuReviewed,
  });

  /// 还差几道菜没评（食集整体没评也算一道）。
  int get remaining {
    var r = dishCount - reviewedDishCount;
    if (!menuReviewed) r += 1;
    return r;
  }

  factory PendingMenu.fromJson(Map<String, dynamic> j) => PendingMenu(
        menuId: (j['menuId'] as num).toInt(),
        menuName: (j['menuName'] ?? '') as String,
        finishedAt: j['finishedAt'] as String?,
        dishCount: (j['dishCount'] as num?)?.toInt() ?? 0,
        reviewedDishCount: (j['reviewedDishCount'] as num?)?.toInt() ?? 0,
        menuReviewed: (j['menuReviewed'] as bool?) ?? false,
      );
}
