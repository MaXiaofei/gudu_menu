/// 食集（对应后端 Menu + MenuDish）。
///
/// 后端 `GET /menu/{id}` 返回 `MenuDetail` record：`{ menu, dishes }`：
/// - menu：食集本身（name/servingCount/status/...）。
/// - dishes：关联菜列表（MenuDish：id/menuId/dishId/servingFactor/dishName/coverUrl）。
///
/// dishes 冗余带菜名/封面（后端 detail 批量查），前端无需再逐菜 GET /dish/{id}。
class Menu {
  final int id;
  final String name;
  final int? typeId;
  final int? targetMemberId;
  final int? servingCount;
  /// 状态：ACTIVE 进行中 / DONE 已完成。
  final String? status;
  /// 创建时间（后端 ISO 格式字符串，如 2026-08-06T19:16:12）。
  final String? createTime;

  const Menu({
    required this.id,
    required this.name,
    this.typeId,
    this.targetMemberId,
    this.servingCount,
    this.status,
    this.createTime,
  });

  factory Menu.fromJson(Map<String, dynamic> j) => Menu(
        id: (j['id'] as num).toInt(),
        name: (j['name'] ?? '') as String,
        typeId: (j['typeId'] as num?)?.toInt(),
        targetMemberId: (j['targetMemberId'] as num?)?.toInt(),
        servingCount: (j['servingCount'] as num?)?.toInt(),
        status: j['status'] as String?,
        createTime: j['createTime'] as String?,
      );

  bool get isDone => status == 'DONE';

  /// 创建时间解析为 DateTime（解析失败返回 null）。
  DateTime? get createdAt {
    if (createTime == null || createTime!.isEmpty) return null;
    return DateTime.tryParse(createTime!);
  }
}

/// 食集→菜关联（后端 MenuDish + 冗余菜名/封面）。
class MenuDish {
  final int id;
  final int menuId;
  final int dishId;
  /// 该菜在食集中份数（后端 servingFactor）。
  final double? servingFactor;
  /// 菜名（后端 detail 冗余返回，避免前端逐菜 GET /dish/{id}）。
  final String? dishName;
  /// 菜封面图。
  final String? coverUrl;

  const MenuDish({
    required this.id,
    required this.menuId,
    required this.dishId,
    this.servingFactor,
    this.dishName,
    this.coverUrl,
  });

  factory MenuDish.fromJson(Map<String, dynamic> j) => MenuDish(
        id: (j['id'] as num).toInt(),
        menuId: (j['menuId'] as num).toInt(),
        dishId: (j['dishId'] as num).toInt(),
        servingFactor: (j['servingFactor'] as num?)?.toDouble(),
        dishName: j['dishName'] as String?,
        coverUrl: j['coverUrl'] as String?,
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
