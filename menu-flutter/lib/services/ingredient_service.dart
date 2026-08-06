import '../core/api_client.dart';

/// 食材服务（字典 / 创建 / AI 补全营养）。
class IngredientService {
  /// 字典项（单位 / 采购分类）：GET /dict?group=xxx
  ///
  /// 例外于 DESIGN.md §12.1（列表须分页）：字典项用于下拉选择，需全量。
  static Future<List<DictItem>> listDictByGroup(String group) async {
    final data = await ApiClient.instance.get('/dict', query: {
      'group': group,
      'pageNum': 1,
      'pageSize': 1000,
    });
    final records = (data is Map) ? data['records'] : null;
    if (records is List) {
      return records
          .map((e) => DictItem.fromJson(e as Map<String, dynamic>))
          .toList();
    }
    return [];
  }

  /// 新建食材：POST /ingredient → 返回 id。
  static Future<int> createIngredient(Map<String, dynamic> data) async {
    final result = await ApiClient.instance.post('/ingredient', body: data);
    return (result as num).toInt();
  }

  /// 全部食材列表（id + name）：GET /ingredient?pageSize=1000
  ///
  /// 例外于 DESIGN.md §12.1（列表须分页）：用于建菜/采购选食材下拉，需全量。
  static Future<List<DictItem>> listAll() async {
    final data = await ApiClient.instance.get('/ingredient', query: {
      'pageNum': 1,
      'pageSize': 1000,
    });
    final records = (data is Map) ? data['records'] : null;
    if (records is List) {
      return records
          .map((e) => DictItem.fromJson(e as Map<String, dynamic>))
          .toList();
    }
    return [];
  }

  /// 新增字典项（自定义单位/分类），返回新 id。
  static Future<int> upsertDict(String name, String group) async {
    final result = await ApiClient.instance.post('/dict', body: {
      'name': name,
      'dictGroup': group,
    });
    return (result as num).toInt();
  }
}

/// 字典项（单位 / 采购分类）。
class DictItem {
  final int id;
  final String name;

  const DictItem({required this.id, required this.name});

  factory DictItem.fromJson(Map<String, dynamic> j) => DictItem(
        id: (j['id'] as num).toInt(),
        name: (j['name'] ?? '') as String,
      );
}
