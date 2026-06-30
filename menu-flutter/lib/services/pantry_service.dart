import '../core/api_client.dart';
import '../models/page.dart';

/// 食材库存服务。
class PantryService {
  /// 分页库存列表：GET /pantry?pageNum=&pageSize=（"全部" tab 用，每页 10 条）。
  static Future<PageData<PantryVO>> list({
    int pageNum = 1,
    int pageSize = 10,
  }) async {
    final data = await ApiClient.instance.get('/pantry', query: {
      'pageNum': pageNum,
      'pageSize': pageSize,
    });
    return PageData<PantryVO>.fromJson(
      data as Map<String, dynamic>,
      PantryVO.fromJson,
    );
  }

  /// 全量库存列表：GET /pantry?pageSize=1000
  static Future<List<PantryVO>> listAll() async {
    final data = await ApiClient.instance.get('/pantry', query: {
      'pageNum': 1,
      'pageSize': 1000,
    });
    if (data is List) {
      return data.map((e) => PantryVO.fromJson(e as Map<String, dynamic>)).toList();
    }
    if (data is Map && data['records'] is List) {
      return (data['records'] as List)
          .map((e) => PantryVO.fromJson(e as Map<String, dynamic>))
          .toList();
    }
    return [];
  }

  /// 临期库存：GET /pantry/expiring?days=3
  static Future<List<PantryVO>> listExpiring({int days = 3}) async {
    final data = await ApiClient.instance.get(
      '/pantry/expiring',
      query: {'days': days},
    );
    if (data is List) {
      return data.map((e) => PantryVO.fromJson(e as Map<String, dynamic>)).toList();
    }
    return [];
  }

  /// 低库存：GET /pantry/low
  static Future<List<PantryVO>> listLow() async {
    final data = await ApiClient.instance.get('/pantry/low');
    if (data is List) {
      return data.map((e) => PantryVO.fromJson(e as Map<String, dynamic>)).toList();
    }
    return [];
  }

  /// 新增库存项：POST /pantry → 返回 id
  static Future<int> create(Map<String, dynamic> data) async {
    final result = await ApiClient.instance.post('/pantry', body: data);
    return (result as num).toInt();
  }

  /// 更新库存项：PUT /pantry
  static Future<void> update(Map<String, dynamic> data) async {
    await ApiClient.instance.put('/pantry', body: data);
  }

  /// 删除库存项：DELETE /pantry/{id}
  static Future<void> delete(int id) async {
    await ApiClient.instance.delete('/pantry/$id');
  }

  /// 批量添加：POST /pantry/batch → {count: n}
  static Future<int> batchAdd(List<Map<String, dynamic>> items) async {
    final data = await ApiClient.instance.post('/pantry/batch', body: items);
    if (data is Map && data['count'] != null) return (data['count'] as num).toInt();
    return 0;
  }

  /// 手动扣减：POST /pantry/{id}/deduct → {remain}
  static Future<double> deduct(int id, double amount) async {
    final data = await ApiClient.instance.post('/pantry/$id/deduct', body: {'amount': amount});
    if (data is Map && data['remain'] != null) return (data['remain'] as num).toDouble();
    return 0;
  }
}

/// 库存项 VO（对齐后端 PantryVO）。
class PantryVO {
  final int id;
  final int ingredientId;
  final String? ingredientName;
  final double amount;
  final int? unitId;
  final String? unitName;
  final String? expireDate;
  final double? lowThreshold;
  final String? updateTime;

  const PantryVO({
    required this.id,
    required this.ingredientId,
    this.ingredientName,
    required this.amount,
    this.unitId,
    this.unitName,
    this.expireDate,
    this.lowThreshold,
    this.updateTime,
  });

  factory PantryVO.fromJson(Map<String, dynamic> j) => PantryVO(
        id: (j['id'] as num).toInt(),
        ingredientId: (j['ingredientId'] as num).toInt(),
        ingredientName: j['ingredientName'] as String?,
        amount: (j['amount'] as num?)?.toDouble() ?? 0,
        unitId: (j['unitId'] as num?)?.toInt(),
        unitName: j['unitName'] as String?,
        expireDate: j['expireDate'] as String?,
        lowThreshold: (j['lowThreshold'] as num?)?.toDouble(),
        updateTime: j['updateTime'] as String?,
      );

  String get displayName => ingredientName ?? '#$ingredientId';
  String get displayAmount => '${_fmt(amount)} ${unitName ?? ''}';

  /// 是否低于阈值
  bool get isLow =>
      lowThreshold != null && lowThreshold! > 0 && amount < lowThreshold!;

  /// 是否临期（3 天内）
  bool isExpiring({int days = 3}) {
    if (expireDate == null) return false;
    final exp = DateTime.tryParse(expireDate!);
    if (exp == null) return false;
    final diff = exp.difference(DateTime.now()).inDays;
    return diff >= 0 && diff <= days;
  }

  /// 是否已过期
  bool get isExpired {
    if (expireDate == null) return false;
    final exp = DateTime.tryParse(expireDate!);
    if (exp == null) return false;
    return exp.isBefore(DateTime.now());
  }

  /// 过期/临期文案
  String get expireText {
    if (expireDate == null) return '无过期日';
    final exp = DateTime.tryParse(expireDate!);
    if (exp == null) return '';
    final diff = exp.difference(DateTime.now()).inDays;
    if (diff < 0) return '已过期 ${-diff} 天';
    if (diff == 0) return '今天到期';
    return '剩 $diff 天';
  }

  static String _fmt(double v) {
    if (v == v.roundToDouble()) return v.toInt().toString();
    return v.toStringAsFixed(1);
  }
}
