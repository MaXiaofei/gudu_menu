import '../core/api_client.dart';
import '../models/page.dart';

/// 食材库存服务（V42 手动 3 档版）。
///
/// 库存不做自动扣减、不用克数：每食材一行档位 ENOUGH(充足)/LOW(不足)/NONE(用完)。
class PantryService {
  // ===================== APP 核心 =====================

  /// 三色分组列表（全量）：GET /pantry/grouped（手动入库页建「食材→档位」map 用。
  /// 全量拉取：需一次性拿全部档位，按 DESIGN.md §12 注释说明理由）。
  static Future<PantryGrouped> listGrouped() async {
    final data = await ApiClient.instance.get('/pantry/grouped');
    return PantryGrouped.fromJson(data as Map<String, dynamic>);
  }

  /// 分页三色分组列表：GET /pantry/grouped?level=&keyword=&pageNum=&pageSize=
  /// summary 恒为 keyword 范围内三档总数（chips 计数 / 搜索「找到 N 个」），
  /// items 按 level 过滤 + 排序 + 切片。每页 10 条（DESIGN.md §12.2）。
  static Future<PantryGrouped> listGroupedPage({
    String? level,
    String? keyword,
    int pageNum = 1,
    int pageSize = 10,
  }) async {
    final data = await ApiClient.instance.get('/pantry/grouped', query: {
      if (level != null) 'level': level,
      if (keyword != null && keyword.isNotEmpty) 'keyword': keyword,
      'pageNum': pageNum,
      'pageSize': pageSize,
    });
    return PantryGrouped.fromJson(data as Map<String, dynamic>);
  }

  /// 食材详情：GET /pantry/item?ingredientId=（档位 + 最近流水）。
  static Future<PantryItemDetail> itemDetail(int ingredientId) async {
    final data = await ApiClient.instance.get('/pantry/item', query: {'ingredientId': ingredientId});
    return PantryItemDetail.fromJson(data as Map<String, dynamic>);
  }

  /// 设档位：PUT /pantry/{ingredientId}/level（手动修正，记 manual 流水）。
  static Future<void> setLevel(int ingredientId, String level, {String? note}) async {
    await ApiClient.instance.put('/pantry/$ingredientId/level', body: {
      'level': level,
      if (note != null) 'note': note,
    });
  }

  /// 手动入库：POST /pantry/manual（选食材 → 档位默认充足 + 来源标签；新建档不用单位）。
  static Future<void> manualAdd({
    int? ingredientId,
    String? name,
    String? level,
    String? sourceNote,
  }) async {
    await ApiClient.instance.post('/pantry/manual', body: {
      if (ingredientId != null) 'ingredientId': ingredientId,
      if (name != null) 'name': name,
      if (level != null) 'level': level,
      if (sourceNote != null) 'sourceNote': sourceNote,
    });
  }

  // ===================== admin 兼容（pantry 批次表，P5 迁移后清理） =====================

  /// 分页库存列表：GET /pantry?pageNum=&pageSize=（后台管理用）。
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

  /// 临期库存：GET /pantry/expiring?days=3（通知调度用）。
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

  /// 不足库存：GET /pantry/low（档位 LOW 的食材，兼容旧接口）。
  static Future<List<PantryGroupedItem>> listLow() async {
    final data = await ApiClient.instance.get('/pantry/low');
    if (data is List) {
      return data.map((e) => PantryGroupedItem.fromJson(e as Map<String, dynamic>)).toList();
    }
    return [];
  }

  /// 新增库存项：POST /pantry → 返回 id（写 pantry 批次表，admin 用）。
  static Future<int> create(Map<String, dynamic> data) async {
    final result = await ApiClient.instance.post('/pantry', body: data);
    return (result as num).toInt();
  }

  /// 更新库存项：PUT /pantry（admin 用）。
  static Future<void> update(Map<String, dynamic> data) async {
    await ApiClient.instance.put('/pantry', body: data);
  }

  /// 删除库存项：DELETE /pantry/{id}（admin 用）。
  static Future<void> delete(int id) async {
    await ApiClient.instance.delete('/pantry/$id');
  }

  /// 批量添加：POST /pantry/batch → {count: n}（admin 用）。
  static Future<int> batchAdd(List<Map<String, dynamic>> items) async {
    final data = await ApiClient.instance.post('/pantry/batch', body: items);
    if (data is Map && data['count'] != null) return (data['count'] as num).toInt();
    return 0;
  }
}

/// 库存档位常量与文案（充足/不足/用完，V42）。
class StockLevel {
  static const enough = 'ENOUGH';
  static const low = 'LOW';
  static const none = 'NONE';

  /// 档位 → 中文文案。
  static String label(String? level) => switch (level) {
        enough => '充足',
        low => '不足',
        none => '用完',
        _ => '',
      };

  /// 动作词：用完了/用了一些/这次没用（做菜确认弹窗三态，action 常量）。
  static String actionLabel(String action) => switch (action) {
        'cook' => '用完了',
        'cook_partial' => '用了一些',
        'purchase' => '采购',
        'manual' => '手动',
        'undo' => '撤回入库',
        _ => action,
      };
}

/// 库存项 VO（对齐后端 PantryVO，pantry 批次表 admin 用）。
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

  /// 是否低于阈值（admin 兼容，pantry 批次表语义）。
  bool get isLow =>
      lowThreshold != null && lowThreshold! > 0 && amount < lowThreshold!;

  /// 是否临期（3 天内，admin 兼容）。
  bool isExpiring({int days = 3}) {
    if (expireDate == null) return false;
    final exp = DateTime.tryParse(expireDate!);
    if (exp == null) return false;
    final diff = exp.difference(DateTime.now()).inDays;
    return diff >= 0 && diff <= days;
  }

  /// 是否已过期（admin 兼容）。
  bool get isExpired {
    if (expireDate == null) return false;
    final exp = DateTime.tryParse(expireDate!);
    if (exp == null) return false;
    return exp.isBefore(DateTime.now());
  }

  /// 过期/临期文案（admin 兼容）。
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

// ===================== 三色分组模型（V42 档位版） =====================

/// GET /pantry/grouped 返回：汇总 + 每食材一行档位。
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

/// 分组列表里的一项（一个食材一行，V42 档位版）。
class PantryGroupedItem {
  final int ingredientId;
  final String? ingredientName;
  final String level; // ENOUGH / LOW / NONE
  final LastChange? lastChange;

  const PantryGroupedItem({
    required this.ingredientId,
    this.ingredientName,
    required this.level,
    this.lastChange,
  });

  factory PantryGroupedItem.fromJson(Map<String, dynamic> j) => PantryGroupedItem(
        ingredientId: (j['ingredientId'] as num).toInt(),
        ingredientName: j['ingredientName'] as String?,
        level: j['level'] as String? ?? StockLevel.none,
        lastChange: j['lastChange'] == null
            ? null
            : LastChange.fromJson(j['lastChange'] as Map<String, dynamic>),
      );

  String get displayName => ingredientName ?? '#$ingredientId';

  /// 档位文案：充足/不足/用完。
  String get levelLabel => StockLevel.label(level);

  /// 来源标签文案（用完了/用了一些/采购/手动；无变动返回空）。
  String get sourceLabel {
    final s = lastChange?.source;
    return switch (s) {
      'cook' => '用完了',
      'cook_partial' => '用了一些',
      'purchase' => '采购',
      'manual' => '手动',
      'undo' => '撤回入库',
      _ => '',
    };
  }

  /// 来源副文案（备注或时间）。
  String get sourceSub {
    final lc = lastChange;
    if (lc == null) return '';
    final note = lc.sourceNote;
    if (note != null && note.isNotEmpty) return note;
    return _fmtTime(lc.createTime);
  }

  static String _fmtTime(String? iso) {
    if (iso == null || iso.isEmpty) return '';
    final dt = DateTime.tryParse(iso);
    if (dt == null) return '';
    return '${dt.month}/${dt.day}';
  }
}

/// 最近一次变动（V42：无克数，只有来源/备注/时间）。
class LastChange {
  final String source;
  final String? sourceNote;
  final String? createTime;

  const LastChange({required this.source, this.sourceNote, this.createTime});

  factory LastChange.fromJson(Map<String, dynamic> j) => LastChange(
        source: j['source'] as String? ?? '',
        sourceNote: j['sourceNote'] as String?,
        createTime: j['createTime'] as String?,
      );
}

// ===================== 食材详情模型（V42 档位版） =====================

/// GET /pantry/item 返回：档位 + 最近 N 条流水（stock_log）。
class PantryItemDetail {
  final int ingredientId;
  final String? ingredientName;
  final String level; // ENOUGH / LOW / NONE
  final List<StockLogEntry> changes;

  const PantryItemDetail({
    required this.ingredientId,
    this.ingredientName,
    required this.level,
    required this.changes,
  });

  factory PantryItemDetail.fromJson(Map<String, dynamic> j) {
    final list = (j['changes'] ?? []) as List;
    return PantryItemDetail(
      ingredientId: (j['ingredientId'] as num).toInt(),
      ingredientName: j['ingredientName'] as String?,
      level: j['level'] as String? ?? StockLevel.none,
      changes: list.map((e) => StockLogEntry.fromJson(e as Map<String, dynamic>)).toList(),
    );
  }

  String get displayName => ingredientName ?? '#$ingredientId';
  String get levelLabel => StockLevel.label(level);
}

/// 一条档位变动流水（stock_log，V42）。
class StockLogEntry {
  final int id;
  final int ingredientId;
  final String action; // cook / cook_partial / purchase / manual / undo
  final String? beforeLevel;
  final String? afterLevel;
  final String? note;
  final String? createTime;

  const StockLogEntry({
    required this.id,
    required this.ingredientId,
    required this.action,
    this.beforeLevel,
    this.afterLevel,
    this.note,
    this.createTime,
  });

  factory StockLogEntry.fromJson(Map<String, dynamic> j) => StockLogEntry(
        id: (j['id'] as num).toInt(),
        ingredientId: (j['ingredientId'] as num).toInt(),
        action: j['action'] as String? ?? '',
        beforeLevel: j['beforeLevel'] as String?,
        afterLevel: j['afterLevel'] as String?,
        note: j['note'] as String?,
        createTime: j['createTime'] as String?,
      );

  /// 动作文案（用完了/用了一些/采购/手动/撤回入库）。
  String get actionLabel => StockLevel.actionLabel(action);

  /// 变动描述：前档位 → 后档位（如「不足 → 用完」；新建档「无 → 充足」）。
  String get changeText {
    final before = beforeLevel == null ? '无' : StockLevel.label(beforeLevel);
    final after = afterLevel == null ? '删除' : StockLevel.label(afterLevel);
    return '$before → $after';
  }
}
