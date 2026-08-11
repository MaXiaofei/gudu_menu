import '../core/api_client.dart';

/// 食材服务（字典 / 创建 / AI 补全营养）。
class IngredientService {
  /// 字典缓存：写菜谱/采购等页面频繁进页会反复拉 /dict，5 分钟内命中缓存不再请求。
  /// 字典数据量小且低频变化；upsertDict 新增后主动失效，保证新单位立即可见。
  static final Map<String, List<DictItem>> _dictCache = {};
  static DateTime? _dictCacheTime;
  static const _dictCacheTtl = Duration(minutes: 5);

  /// 字典项（单位 / 采购分类）：GET /dict?group=xxx
  ///
  /// 例外于 DESIGN.md §12.1（列表须分页）：字典项用于下拉选择，需全量。
  /// 内存缓存 5 分钟（TTL），避免进页面就重复请求后端。
  static Future<List<DictItem>> listDictByGroup(String group) async {
    final now = DateTime.now();
    if (_dictCacheTime != null &&
        now.difference(_dictCacheTime!) < _dictCacheTtl) {
      final cached = _dictCache[group];
      if (cached != null) return cached;
    }
    final data = await ApiClient.instance.get('/dict', query: {
      'group': group,
      'pageNum': 1,
      'pageSize': 1000,
    });
    final records = (data is Map) ? data['records'] : null;
    final items = records is List
        ? records
            .map((e) => DictItem.fromJson(e as Map<String, dynamic>))
            .toList()
        : <DictItem>[];
    _dictCache[group] = items;
    _dictCacheTime = now;
    return items;
  }

  /// 清空字典缓存（测试用；upsertDict 新增单位后也会自动失效对应分组）。
  static void clearDictCache() {
    _dictCache.clear();
    _dictCacheTime = null;
  }

  /// 新建食材：POST /ingredient → 返回 id。
  static Future<int> createIngredient(Map<String, dynamic> data) async {
    final result = await ApiClient.instance.post('/ingredient', body: data);
    return (result as num).toInt();
  }

  /// 全部食材列表（id + name + 现有库存 + 主单位）：GET /ingredient?pageSize=1000
  ///
  /// 例外于 DESIGN.md §12.1（列表须分页）：用于建菜/采购选食材下拉，需全量。
  /// V41：后端挂 stockAmount/stockUnitName（现有 X 个 · 单位，手动添加页用）；
  /// 旧后端无此字段时兜底 0 / null。
  static Future<List<IngredientItem>> listAll() async {
    final data = await ApiClient.instance.get('/ingredient', query: {
      'pageNum': 1,
      'pageSize': 1000,
    });
    final records = (data is Map) ? data['records'] : null;
    if (records is List) {
      return records
          .map((e) => IngredientItem.fromJson(e as Map<String, dynamic>))
          .toList();
    }
    return [];
  }

  /// 按名称模糊搜索食材库（写菜谱「加用料」弹层用）：GET /ingredient?keyword=xx
  static Future<List<IngredientItem>> search(String keyword) async {
    final data = await ApiClient.instance.get('/ingredient', query: {
      'keyword': keyword,
      'pageNum': 1,
      'pageSize': 20,
    });
    final records = (data is Map) ? data['records'] : null;
    if (records is List) {
      return records
          .map((e) => IngredientItem.fromJson(e as Map<String, dynamic>))
          .toList();
    }
    return [];
  }

  /// 新增字典项（自定义单位/分类），返回新 id。新增后失效字典缓存（新单位立即可见）。
  static Future<int> upsertDict(String name, String group) async {
    final result = await ApiClient.instance.post('/dict', body: {
      'name': name,
      'dictGroup': group,
    });
    _dictCache.remove(group);
    return (result as num).toInt();
  }

  /// 保存食材克换算（新建食材共用弹层「克换算」用）：
  /// PUT /ingredient/{id}/unit-grams，rows = [{unitId, gramsPerUnit, isDefault}]。
  static Future<void> saveUnitGrams(
      int ingredientId, List<Map<String, dynamic>> rows) async {
    await ApiClient.instance.put('/ingredient/$ingredientId/unit-grams',
        body: rows);
  }

  /// 查食材的单位换算：GET /ingredient/{id}/unit-grams（编辑页加载）。
  static Future<List<UnitGram>> fetchUnitGrams(int ingredientId) async {
    final data = await ApiClient.instance.get('/ingredient/$ingredientId/unit-grams');
    final list = data is List ? data : const [];
    return list.map((e) => UnitGram.fromJson(e as Map<String, dynamic>)).toList();
  }

  /// 更新食材信息（名称/默认单位/单价/采购品类）：PUT /ingredient。
  static Future<void> updateIngredient(Map<String, dynamic> data) async {
    await ApiClient.instance.put('/ingredient', body: data);
  }

  /// 删除食材：DELETE /ingredient/{id}。
  static Future<void> deleteIngredient(int id) async {
    await ApiClient.instance.delete('/ingredient/$id');
  }
}

/// 食材单位换算行（编辑页用）。
class UnitGram {
  final int? id;
  final int? unitId;
  final String? unitName;
  final double gramsPerUnit;
  final bool isDefault;

  const UnitGram({
    this.id,
    this.unitId,
    this.unitName,
    required this.gramsPerUnit,
    required this.isDefault,
  });

  factory UnitGram.fromJson(Map<String, dynamic> j) => UnitGram(
        id: (j['id'] as num?)?.toInt(),
        unitId: (j['unitId'] as num?)?.toInt(),
        unitName: j['unitName'] as String?,
        gramsPerUnit: (j['gramsPerUnit'] as num?)?.toDouble() ?? 0,
        isDefault: (j['isDefault'] as num?)?.toInt() == 1,
      );

  Map<String, dynamic> toJson() => {
        'id': id,
        'unitId': unitId,
        'unitName': unitName,
        'gramsPerUnit': gramsPerUnit,
        'isDefault': isDefault ? 1 : 0,
      };
}

/// 用量自由文本解析（写菜谱用料，§16.3）：
/// 「2 个」→ (2, 个id)；「500 g」→ (500, g id)；「2」→ (2, g 默认)；
/// 「适量 / 少许 / 一小把」→ (null, 量词单位id)（量词作为单位在字典维护，V46）；
/// 字典外的自由文本 → (null, null)。单位名只在 [units] 字典存在时匹配。
(double?, int?) parseAmountText(String text, List<DictItem> units) {
  if (text.isEmpty) return (null, null);
  final m = RegExp(r'^(\d+(?:\.\d+)?)\s*([a-zA-Z\u4e00-\u9fa5]+)?$')
      .firstMatch(text);
  if (m != null) {
    final amount = double.tryParse(m.group(1)!);
    if (amount != null) {
      final unitName = m.group(2)?.toLowerCase();
      if (unitName == null || unitName.isEmpty) {
        // 纯数字 → 默认 g（与后端旧数据一致）
        final g = units.isEmpty
            ? null
            : units.firstWhere((u) => u.name == 'g', orElse: () => units.first);
        return (amount, g?.id);
      }
      if (units.isEmpty) return (amount, null);
      final unit = units.firstWhere((u) => u.name.toLowerCase() == unitName,
          orElse: () => units.first);
      return (amount, unit.name.toLowerCase() == unitName ? unit.id : null);
    }
  }
  // 非数字：「适量 / 少许 / 一小把」等量词按字典精确匹配 → (null, 量词id)
  if (units.isEmpty) return (null, null);
  final fuzzy =
      units.where((u) => u.name == text.trim()).toList();
  return fuzzy.isEmpty ? (null, null) : (null, fuzzy.first.id);
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

/// 食材库项（手动添加「库里已有」用）：id + name + 当前库存余量 + 主单位。
/// V41：stockAmount/stockUnitName 来自后端；旧后端缺字段时兜底 0 / null。
class IngredientItem {
  final int id;
  final String name;

  /// 当前库存余量（pantry SUM(amount)，主单位）。
  final double stockAmount;

  /// 主单位名（可空）。
  final String? unitName;

  const IngredientItem({
    required this.id,
    required this.name,
    this.stockAmount = 0,
    this.unitName,
  });

  factory IngredientItem.fromJson(Map<String, dynamic> j) => IngredientItem(
        id: (j['id'] as num).toInt(),
        name: (j['name'] ?? '') as String,
        stockAmount: (j['stockAmount'] as num?)?.toDouble() ?? 0,
        unitName: (j['stockUnitName'] as String?)?.trim().isNotEmpty == true
            ? j['stockUnitName'] as String
            : null,
      );
}
