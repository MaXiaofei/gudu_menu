import '../core/api_client.dart';
import '../models/prep.dart';

/// 备菜服务（对应后端 MenuPrepController）。
///
/// - `GET /menu/{id}/prep`：备菜聚合（主料 items + 调料 condiments + 进度）。
/// - `PUT /menu/{id}/prep/{ingredientId}?status=`：更新备料状态（upsert）。
class PrepService {
  /// 备菜聚合：GET /menu/{id}/prep → MenuPrep。
  static Future<MenuPrep> getPrep(int menuId) async {
    final data = await ApiClient.instance.get('/menu/$menuId/prep');
    return MenuPrep.fromJson(data as Map<String, dynamic>);
  }

  /// 更新备料状态：PUT /menu/{id}/prep/{ingredientId}?status=READY。
  static Future<void> updateStatus(
    int menuId,
    int ingredientId,
    PrepStatus status,
  ) async {
    await ApiClient.instance.put(
      '/menu/$menuId/prep/$ingredientId',
      query: {'status': status.name},
    );
  }
}
