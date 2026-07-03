import '../core/api_client.dart';
import '../models/menu.dart';
import '../models/page.dart';
import 'dish_service.dart' show CookResult;

/// 食集服务（对应后端 MenuController + CookController.cookMenu）。
///
/// - `GET /menu`：后台分页（按创建时间倒序）。
/// - `GET /menu/{id}`：食集详情（menu + 关联菜列表 MenuDish）。
/// - `POST /menu/{id}/cook`：整集做菜（Plan A）→ 聚合用量→扣库存→每菜写
///   cooking_record→食集标 DONE，返回 CookResult。
class MenuService {
  /// 分页查食集：GET /menu?pageNum=&pageSize=。
  static Future<PageData<Menu>> list({
    int pageNum = 1,
    int pageSize = 20,
  }) async {
    final data = await ApiClient.instance.get('/menu', query: {
      'pageNum': pageNum,
      'pageSize': pageSize,
    });
    return PageData<Menu>.fromJson(
      data as Map<String, dynamic>,
      Menu.fromJson,
    );
  }

  /// 详情：GET /menu/{id} → { menu, dishes:[{dishId, servingFactor, ...}] }。
  static Future<MenuDetail> detail(int id) async {
    final data = await ApiClient.instance.get('/menu/$id');
    return MenuDetail.fromJson(data as Map<String, dynamic>);
  }

  /// 整集做菜：POST /menu/{id}/cook。
  /// 扣 pantry + 每菜写 cooking_record + 食集标 DONE，返回 CookResult
  /// （含扣减/欠量/记录 id）。
  static Future<CookResult> cookMenu(int menuId) async {
    final data = await ApiClient.instance.post('/menu/$menuId/cook');
    return CookResult.fromJson(data as Map<String, dynamic>);
  }
}
