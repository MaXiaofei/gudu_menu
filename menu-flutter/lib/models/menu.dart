/// 食集（对应后端 Menu + MenuDish）。
///
/// 后端 `GET /menu/{id}` 返回 `MenuDetail` record：`{ menu, dishes }`：
/// - menu：食集本身（name/servingCount/status/...）。
/// - dishes：关联菜列表（MenuDish：id/menuId/dishId/servingFactor）。
///
/// 注意：dishes 只带 dishId，不带菜名/封面；详情页需再拉一次 `GET /dish/{dishId}`
/// 拿菜名（菜单规模小，逐个拉可接受；后端暂无批量接口）。
class Menu {
  final int id;
  final String name;
  final int? typeId;
  final int? targetMemberId;
  final int? servingCount;
  /// 状态：ACTIVE 进行中 / DONE 已完成。
  final String? status;

  const Menu({
    required this.id,
    required this.name,
    this.typeId,
    this.targetMemberId,
    this.servingCount,
    this.status,
  });

  factory Menu.fromJson(Map<String, dynamic> j) => Menu(
        id: (j['id'] as num).toInt(),
        name: (j['name'] ?? '') as String,
        typeId: (j['typeId'] as num?)?.toInt(),
        targetMemberId: (j['targetMemberId'] as num?)?.toInt(),
        servingCount: (j['servingCount'] as num?)?.toInt(),
        status: j['status'] as String?,
      );

  bool get isDone => status == 'DONE';
}

/// 食集→菜关联（后端 MenuDish）。
class MenuDish {
  final int id;
  final int menuId;
  final int dishId;
  /// 该菜在食集中的份数（后端 servingFactor）。
  final double? servingFactor;

  const MenuDish({
    required this.id,
    required this.menuId,
    required this.dishId,
    this.servingFactor,
  });

  factory MenuDish.fromJson(Map<String, dynamic> j) => MenuDish(
        id: (j['id'] as num).toInt(),
        menuId: (j['menuId'] as num).toInt(),
        dishId: (j['dishId'] as num).toInt(),
        servingFactor: (j['servingFactor'] as num?)?.toDouble(),
      );
}

/// 食集详情聚合（后端 MenuService.MenuDetail record：`{ menu, dishes }`）。
class MenuDetail {
  final Menu menu;
  final List<MenuDish> dishes;

  const MenuDetail({required this.menu, required this.dishes});

  factory MenuDetail.fromJson(Map<String, dynamic> j) => MenuDetail(
        menu: Menu.fromJson(j['menu'] as Map<String, dynamic>),
        dishes: ((j['dishes'] ?? const []) as List)
            .map((e) => MenuDish.fromJson(e as Map<String, dynamic>))
            .toList(),
      );
}
