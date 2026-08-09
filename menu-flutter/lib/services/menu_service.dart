import '../core/api_client.dart';
import '../models/menu.dart';
import '../models/page.dart';
import 'dish_service.dart' show CookResult;

/// 食集服务（对应后端 MenuController + CookController.cookMenu）。
///
/// - `GET /menu`：后台分页（按创建时间倒序）。
/// - `GET /menu/{id}`：食集详情（menu + 关联菜列表 MenuDish）。
/// - `POST /menu`：新建食集（MenuSaveDTO），返回新 id。
/// - `PUT /menu`：更新食集（整体替换关联菜），返回新 id。
/// - `POST /menu/{id}/cook`：整集做菜 → 返回 CookResult。
class MenuService {
  /// 分页查食集：GET /menu?pageNum=&pageSize=&status=。
  /// [status] 可选过滤：'ACTIVE' 进行中 / 'DONE' 已完成；null = 全部。
  static Future<PageData<Menu>> list({
    int pageNum = 1,
    int pageSize = 15, // DESIGN.md §12.2
    String? status,
  }) async {
    final query = <String, dynamic>{
      'pageNum': pageNum,
      'pageSize': pageSize,
    };
    if (status != null && status.isNotEmpty) query['status'] = status;
    final data = await ApiClient.instance.get('/menu', query: query);
    return PageData<Menu>.fromJson(
      data as Map<String, dynamic>,
      Menu.fromJson,
    );
  }

  /// 详情：GET /menu/{id} → { menu, dishes:[{dishId, servingFactor, ...}], totalMinutes }。
  static Future<MenuDetail> detail(int id) async {
    final data = await ApiClient.instance.get('/menu/$id');
    return MenuDetail.fromJson(data as Map<String, dynamic>);
  }

  /// 修改/删除食集中某道菜的备注：PUT /menu/{menuId}/dish/{dishId}/note。
  /// [note] 空串 = 删除备注（后端置 null，前端回显「加备注/忌口…」占位）。
  static Future<void> updateDishNote(int menuId, int dishId, String note) async {
    await ApiClient.instance.put('/menu/$menuId/dish/$dishId/note',
        body: {'note': note});
  }

  /// 从食集中移除某道菜：DELETE /menu/{menuId}/dish/{dishId}（原型菜行 ✕）。
  static Future<void> removeDishFromMenu(int menuId, int dishId) async {
    await ApiClient.instance.delete('/menu/$menuId/dish/$dishId');
  }

  /// 聚餐 tab 汇总数量：GET /menu/{id}/together-count（占位，协同点菜待建返回 0）。
  static Future<int> getTogetherCount(int menuId) async {
    final data = await ApiClient.instance.get('/menu/$menuId/together-count');
    return (data as num?)?.toInt() ?? 0;
  }

  /// 删除食集：DELETE /menu/{id}。
  static Future<void> deleteMenu(int id) async {
    await ApiClient.instance.delete('/menu/$id');
  }

  /// 新建食集：POST /menu（MenuSaveDTO），返回新食集 id。
  /// [name] 食集名，[dishIds] 初始菜品 id 列表。
  static Future<int> createMenu(String name, {List<int>? dishIds}) async {
    final body = {
      'menu': {
        'name': name,
        'servingCount': 1,
        'status': 'ACTIVE',
      },
      'dishes': (dishIds ?? [])
          .map((id) => {'dishId': id, 'servingFactor': 1})
          .toList(),
    };
    final result = await ApiClient.instance.post('/menu', body: body);
    return (result as num).toInt();
  }

  /// 加菜到已有食集：拉详情 → dishes 追加 → PUT /menu 整体更新。
  ///
  /// 食集名拼接规则：加菜后，若原食集名等于"已有菜名拼接"（说明是自动拼接名、非用户自定义），
  /// 则更新为含新菜的新拼接名；否则保留用户自定义名。
  ///
  /// [dishName] 新加入菜品的名字（用于拼接）。
  static Future<void> addDishToMenu(int menuId, int dishId, {String? dishName}) async {
    final detail = await MenuService.detail(menuId);
    // 检查是否已在食集中（避免重复加）
    final exists = detail.dishes.any((d) => d.dishId == dishId);
    if (exists) return;

    // 判断当前食集名是否是"菜名拼接"格式（等于已有菜名的「_」拼接）
    final existingNames = detail.dishes
        .map((d) => d.dishName ?? '')
        .where((n) => n.isNotEmpty)
        .toList();
    final autoName = existingNames.join('_');
    // 是自动拼接名（或空名）→ 更新为含新菜的新拼接
    final shouldRename = detail.menu.name == autoName ||
        detail.menu.name.isEmpty;
    final newName = shouldRename && dishName != null && dishName.isNotEmpty
        ? [...existingNames, dishName].join('_')
        : detail.menu.name;

    final body = {
      'menu': {
        'id': detail.menu.id,
        'name': newName,
        'servingCount': detail.menu.servingCount ?? 1,
        'status': detail.menu.status ?? 'ACTIVE',
      },
      'dishes': [
        ...detail.dishes.map((d) => {
              'dishId': d.dishId,
              'servingFactor': d.servingFactor ?? 1,
            }),
        {'dishId': dishId, 'servingFactor': 1},
      ],
    };
    await ApiClient.instance.put('/menu', body: body);
  }

  /// 整集做菜确认：POST /menu/{id}/cook，body 带 usedUp/partiallyUsed。
  /// 按用户确认更新档位 + 写 cooking_record + 食集标 DONE。
  static Future<CookResult> cookMenu(
    int menuId, {
    List<int> usedUp = const [],
    List<int> partiallyUsed = const [],
  }) async {
    final data = await ApiClient.instance.post('/menu/$menuId/cook', body: {
      'usedUp': usedUp,
      'partiallyUsed': partiallyUsed,
    });
    return CookResult.fromJson(data as Map<String, dynamic>);
  }
}
