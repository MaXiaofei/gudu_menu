import '../core/api_client.dart';
import '../models/page.dart';

/// 食材库存服务。
class PantryService {
  /// 分页库存列表：GET /pantry?pageNum=&pageSize=（"全部" tab 用，每页 15 条，DESIGN.md §12.2）。
  static Future<PageData<PantryVO>> list({
    int pageNum = 1,
    int pageSize = 15,
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
  ///
  /// 例外于 DESIGN.md §12.1（列表须分页）：库存需在本地分组/筛选/聚合展示，
  /// 一次性拉全量；库存量级有限（家庭库存通常百条以内）。
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

  // ===================== 库存页主页（三色分组，V39） =====================

  /// 三色分组列表：GET /pantry/grouped
  static Future<PantryGrouped> listGrouped() async {
    final data = await ApiClient.instance.get('/pantry/grouped');
    return PantryGrouped.fromJson(data as Map<String, dynamic>);
  }

  /// 食材详情：GET /pantry/item?ingredientId=
  static Future<PantryItemDetail> itemDetail(int ingredientId) async {
    final data = await ApiClient.instance.get('/pantry/item', query: {'ingredientId': ingredientId});
    return PantryItemDetail.fromJson(data as Map<String, dynamic>);
  }

  /// 盘点：POST /pantry/adjust {ingredientId, newAmount, sourceNote?}
  static Future<void> adjust(int ingredientId, double newAmount, {String? sourceNote}) async {
    await ApiClient.instance.post('/pantry/adjust', body: {
      'ingredientId': ingredientId,
      'newAmount': newAmount,
      if (sourceNote != null) 'sourceNote': sourceNote,
    });
  }

  /// 手动添加：POST /pantry/manual
  static Future<void> manualAdd({
    int? ingredientId,
    String? name,
    required double amount,
    int? unitId,
    required String sourceNote,
    String? expireDate,
  }) async {
    await ApiClient.instance.post('/pantry/manual', body: {
      if (ingredientId != null) 'ingredientId': ingredientId,
      if (name != null) 'name': name,
      'amount': amount,
      if (unitId != null) 'unitId': unitId,
      'sourceNote': sourceNote,
      if (expireDate != null) 'expireDate': expireDate,
    });
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

// ===================== 三色分组模型（V39） =====================

/// GET /pantry/grouped 返回：汇总 + 按食材聚合的项列表。
class PantryGrouped {
  final int enough;
  final int low;
  final int none;
  final List<PantryGroupedItem> items;

  const PantryGrouped({required this.enough, required this.low, required this.none, required this.items});

  factory PantryGrouped.fromJson(Map<String, dynamic> j) {
    final s = (j['summary'] ?? {}) as Map<String, dynamic>;
    final list = (j['items'] ?? []) as List;
    return PantryGrouped(
      enough: (s['enough'] as num?)?.toInt() ?? 0,
      low: (s['low'] as num?)?.toInt() ?? 0,
      none: (s['none'] as num?)?.toInt() ?? 0,
      items: list.map((e) => PantryGroupedItem.fromJson(e as Map<String, dynamic>)).toList(),
    );
  }
}

/// 分组列表里的一项（一个食材聚合后）。
class PantryGroupedItem {
  final int ingredientId;
  final String? ingredientName;
  final int? unitId;
  final String? unitName;
  final double? lowThreshold;
  final double totalAmount;
  final double totalGrams;
  final String status; // ENOUGH / LOW / NONE
  final LastChange? lastChange;

  const PantryGroupedItem({
    required this.ingredientId,
    this.ingredientName,
    this.unitId,
    this.unitName,
    this.lowThreshold,
    required this.totalAmount,
    required this.totalGrams,
    required this.status,
    this.lastChange,
  });

  factory PantryGroupedItem.fromJson(Map<String, dynamic> j) => PantryGroupedItem(
        ingredientId: (j['ingredientId'] as num).toInt(),
        ingredientName: j['ingredientName'] as String?,
        unitId: (j['unitId'] as num?)?.toInt(),
        unitName: j['unitName'] as String?,
        lowThreshold: (j['lowThreshold'] as num?)?.toDouble(),
        totalAmount: (j['totalAmount'] as num?)?.toDouble() ?? 0,
        totalGrams: (j['totalGrams'] as num?)?.toDouble() ?? 0,
        status: j['status'] as String? ?? 'NONE',
        lastChange: j['lastChange'] == null
            ? null
            : LastChange.fromJson(j['lastChange'] as Map<String, dynamic>),
      );

  String get displayName => ingredientName ?? '#$ingredientId';
  String get displayAmount => '${_fmtAmount(totalAmount)} ${unitName ?? ''}';

  /// 来源标签文案（做菜/采购/盘点/手动）。
  String get sourceLabel {
    switch (lastChange?.source) {
      case 'cook': return '做菜';
      case 'purchase': return '采购';
      case 'inventory': return '盘点';
      case 'manual': return '手动';
      default: return '';
    }
  }

  /// 来源副文案（如「朋友送」「-6」）。
  String get sourceSub {
    final lc = lastChange;
    if (lc == null) return '';
    final delta = lc.delta;
    final sign = delta >= 0 ? '+' : '';
    final note = lc.sourceNote;
    if (note != null && note.isNotEmpty) {
      return '$sign${_fmtAmount(delta)} · $note';
    }
    return '$sign${_fmtAmount(delta)}';
  }

  static String _fmtAmount(double v) {
    if (v == v.roundToDouble()) return v.toInt().toString();
    return v.toStringAsFixed(1);
  }
}

/// 最近一次变动。
class LastChange {
  final String source;
  final String? sourceNote;
  final String? createTime;
  final double delta;

  const LastChange({required this.source, this.sourceNote, this.createTime, required this.delta});

  factory LastChange.fromJson(Map<String, dynamic> j) => LastChange(
        source: j['source'] as String? ?? '',
        sourceNote: j['sourceNote'] as String?,
        createTime: j['createTime'] as String?,
        delta: (j['delta'] as num?)?.toDouble() ?? 0,
      );
}

// ===================== 食材详情模型（V39） =====================

/// GET /pantry/item 返回：食材合计 + 阈值克数 + 最近 N 条流水。
class PantryItemDetail {
  final int ingredientId;
  final String? ingredientName;
  final int? unitId;
  final String? unitName;
  final double? lowThreshold;
  final double totalAmount;
  final double totalGrams;
  final double thresholdGrams;
  final String status;
  final List<PantryChangeLog> changes;

  const PantryItemDetail({
    required this.ingredientId,
    this.ingredientName,
    this.unitId,
    this.unitName,
    this.lowThreshold,
    required this.totalAmount,
    required this.totalGrams,
    required this.thresholdGrams,
    required this.status,
    required this.changes,
  });

  factory PantryItemDetail.fromJson(Map<String, dynamic> j) {
    final list = (j['changes'] ?? []) as List;
    return PantryItemDetail(
      ingredientId: (j['ingredientId'] as num).toInt(),
      ingredientName: j['ingredientName'] as String?,
      unitId: (j['unitId'] as num?)?.toInt(),
      unitName: j['unitName'] as String?,
      lowThreshold: (j['lowThreshold'] as num?)?.toDouble(),
      totalAmount: (j['totalAmount'] as num?)?.toDouble() ?? 0,
      totalGrams: (j['totalGrams'] as num?)?.toDouble() ?? 0,
      thresholdGrams: (j['thresholdGrams'] as num?)?.toDouble() ?? 0,
      status: j['status'] as String? ?? 'NONE',
      changes: list.map((e) => PantryChangeLog.fromJson(e as Map<String, dynamic>)).toList(),
    );
  }

  String get displayName => ingredientName ?? '#$ingredientId';
}

/// 一条变动流水。
class PantryChangeLog {
  final int id;
  final int ingredientId;
  final String source;
  final double delta;
  final double? amountAfter;
  final String? sourceNote;
  final String? createTime;

  const PantryChangeLog({
    required this.id,
    required this.ingredientId,
    required this.source,
    required this.delta,
    this.amountAfter,
    this.sourceNote,
    this.createTime,
  });

  factory PantryChangeLog.fromJson(Map<String, dynamic> j) => PantryChangeLog(
        id: (j['id'] as num).toInt(),
        ingredientId: (j['ingredientId'] as num).toInt(),
        source: j['source'] as String? ?? '',
        delta: (j['delta'] as num?)?.toDouble() ?? 0,
        amountAfter: (j['amountAfter'] as num?)?.toDouble(),
        sourceNote: j['sourceNote'] as String?,
        createTime: j['createTime'] as String?,
      );

  String get sourceLabel {
    switch (source) {
      case 'cook': return '做菜';
      case 'purchase': return '采购';
      case 'inventory': return '盘点';
      case 'manual': return '手动';
      default: return source;
    }
  }

  String get deltaText {
    final sign = delta >= 0 ? '+' : '';
    final v = delta == delta.roundToDouble() ? delta.toInt().toString() : delta.toStringAsFixed(1);
    return '$sign$v g';
  }
}
