import '../core/api_client.dart';
import '../models/page.dart';

/// 采购清单服务。
class ShoppingService {
  /// 分页列表：GET /shopping?pageNum=&pageSize=（每页 10 条）。
  static Future<PageData<ShoppingList>> listPaged({
    int pageNum = 1,
    int pageSize = 10,
  }) async {
    final data = await ApiClient.instance.get('/shopping', query: {
      'pageNum': pageNum,
      'pageSize': pageSize,
    });
    return PageData<ShoppingList>.fromJson(
      data as Map<String, dynamic>,
      ShoppingList.fromJson,
    );
  }

  /// 列表（按创建时间倒序）：GET /shopping?pageSize=100
  static Future<List<ShoppingList>> list() async {
    final data = await ApiClient.instance.get('/shopping', query: {
      'pageNum': 1,
      'pageSize': 100,
    });
    if (data is Map && data['records'] is List) {
      return (data['records'] as List)
          .map((e) => ShoppingList.fromJson(e as Map<String, dynamic>))
          .toList();
    }
    return [];
  }

  /// 详情：GET /shopping/{id}
  static Future<ShoppingListVO> detail(int id) async {
    final data = await ApiClient.instance.get('/shopping/$id');
    return ShoppingListVO.fromJson(data as Map<String, dynamic>);
  }

  /// 生成（自定义文本）：POST /shopping/generate → id
  static Future<int> generate(Map<String, dynamic> req) async {
    final result = await ApiClient.instance.post('/shopping/generate', body: req);
    return (result as num).toInt();
  }

  /// 生成（菜单/菜品/周计划）
  static Future<int> generateFrom(String type, {int? sourceId, List<int>? sourceIds}) async {
    return generate({
      'sourceType': type,
      if (sourceId != null) 'sourceId': sourceId,
      if (sourceIds != null) 'sourceIds': sourceIds,
    });
  }

  /// 自定义文本生成
  static Future<int> generateFromText(String text) async {
    return generate({'sourceType': 'custom', 'customText': text});
  }

  /// 建空采购单：POST /shopping/create → id
  static Future<int> createEmpty() async {
    final result = await ApiClient.instance.post('/shopping/create');
    return (result as num).toInt();
  }

  /// 切换已购：PUT /shopping/item/{id}/purchased
  static Future<void> togglePurchased(int itemId) async {
    await ApiClient.instance.put('/shopping/item/$itemId/purchased');
  }

  /// 更新采购量：PUT /shopping/item/{id}
  static Future<void> updatePurchase(int itemId, double amount, int? unitId) async {
    await ApiClient.instance.put('/shopping/item/$itemId', body: {
      'purchaseAmount': amount,
      if (unitId != null) 'purchaseUnitId': unitId,
    });
  }

  /// 手动添加自定义项：POST /shopping/item/custom
  static Future<int> addCustomItem(int listId, String name,
      {double? amount, int? unitId, int? categoryId}) async {
    final result = await ApiClient.instance.post('/shopping/item/custom', body: {
      'listId': listId,
      'name': name,
      'amount': amount,
      'unitId': unitId,
      'purchaseCategoryId': categoryId,
    });
    return (result as num).toInt();
  }

  /// 删除采购项：DELETE /shopping/item/{id}
  static Future<void> deleteItem(int itemId) async {
    await ApiClient.instance.delete('/shopping/item/$itemId');
  }

  /// 删除整张清单：DELETE /shopping/{id}
  static Future<void> deleteList(int listId) async {
    await ApiClient.instance.delete('/shopping/$listId');
  }
}

/// 采购单摘要（列表用）。
class ShoppingList {
  final int id;
  final int? sourcePlanId;
  final String? timeRange;
  final String? startDate;
  final String? endDate;
  final String? createdAt;

  const ShoppingList({
    required this.id,
    this.sourcePlanId,
    this.timeRange,
    this.startDate,
    this.endDate,
    this.createdAt,
  });

  factory ShoppingList.fromJson(Map<String, dynamic> j) => ShoppingList(
        id: (j['id'] as num).toInt(),
        sourcePlanId: (j['sourcePlanId'] as num?)?.toInt(),
        timeRange: j['timeRange'] as String?,
        startDate: j['startDate'] as String?,
        endDate: j['endDate'] as String?,
        createdAt: j['createdAt'] as String?,
      );

  String get sourceLabel {
    switch (timeRange) {
      case 'menu': return '菜单';
      case 'dish': return '菜品';
      case 'plan': return '周计划';
      case 'custom': return '自定义';
      case 'custom_text': return '文本录入';
      default: return timeRange ?? '';
    }
  }

  String get dateRange {
    if (startDate != null && endDate != null) return '$startDate ~ $endDate';
    return '';
  }
}

/// 采购单详情 VO。
class ShoppingListVO {
  final int id;
  final String? timeRange;
  final String? startDate;
  final String? endDate;
  final List<ShoppingItemVO> items;
  final Map<String, List<ShoppingItemVO>> grouped;
  final Map<String, String> categoryNames;

  const ShoppingListVO({
    required this.id,
    this.timeRange,
    this.startDate,
    this.endDate,
    required this.items,
    required this.grouped,
    required this.categoryNames,
  });

  factory ShoppingListVO.fromJson(Map<String, dynamic> j) {
    final items = ((j['items'] ?? []) as List)
        .map((e) => ShoppingItemVO.fromJson(e as Map<String, dynamic>))
        .toList();
    final grouped = <String, List<ShoppingItemVO>>{};
    final catNames = <String, String>{};
    if (j['grouped'] is Map) {
      (j['grouped'] as Map).forEach((k, v) {
        grouped[k.toString()] = ((v as List)
            .map((e) => ShoppingItemVO.fromJson(e as Map<String, dynamic>))
            .toList());
      });
    }
    if (j['categoryNames'] is Map) {
      (j['categoryNames'] as Map).forEach((k, v) {
        catNames[k.toString()] = v.toString();
      });
    }
    return ShoppingListVO(
      id: (j['id'] as num).toInt(),
      timeRange: j['timeRange'] as String?,
      startDate: j['startDate'] as String?,
      endDate: j['endDate'] as String?,
      items: items,
      grouped: grouped,
      categoryNames: catNames,
    );
  }

  String get sourceLabel {
    switch (timeRange) {
      case 'menu': return '菜单';
      case 'dish': return '菜品';
      case 'plan': return '周计划';
      case 'custom': return '自定义';
      case 'custom_text': return '文本录入';
      default: return timeRange ?? '采购单';
    }
  }

  String get dateRange {
    if (startDate != null && endDate != null) return '$startDate ~ $endDate';
    return '';
  }
}

/// 采购项 VO。
class ShoppingItemVO {
  final int id;
  final int? ingredientId;
  final String? ingredientName;
  final String? customName;
  final double? referenceGrams;
  final double? purchaseAmount;
  final String? purchaseUnitName;
  final int? purchaseCategoryId;
  final String? purchaseCategoryName;
  final int purchased;

  const ShoppingItemVO({
    required this.id,
    this.ingredientId,
    this.ingredientName,
    this.customName,
    this.referenceGrams,
    this.purchaseAmount,
    this.purchaseUnitName,
    this.purchaseCategoryId,
    this.purchaseCategoryName,
    required this.purchased,
  });

  factory ShoppingItemVO.fromJson(Map<String, dynamic> j) => ShoppingItemVO(
        id: (j['id'] as num).toInt(),
        ingredientId: (j['ingredientId'] as num?)?.toInt(),
        ingredientName: j['ingredientName'] as String?,
        customName: j['customName'] as String?,
        referenceGrams: (j['referenceGrams'] as num?)?.toDouble(),
        purchaseAmount: (j['purchaseAmount'] as num?)?.toDouble(),
        purchaseUnitName: j['purchaseUnitName'] as String?,
        purchaseCategoryId: (j['purchaseCategoryId'] as num?)?.toInt(),
        purchaseCategoryName: j['purchaseCategoryName'] as String?,
        purchased: (j['purchased'] as num?)?.toInt() ?? 0,
      );

  String get displayName => ingredientName ?? customName ?? '#$ingredientId';
  bool get isPurchased => purchased == 1;
  String get amountText {
    if (purchaseAmount != null) return '${_fmt(purchaseAmount!)} ${purchaseUnitName ?? ''}';
    if (referenceGrams != null) return '约 ${referenceGrams!.toInt()}g';
    return '';
  }

  static String _fmt(double v) {
    if (v == v.roundToDouble()) return v.toInt().toString();
    return v.toStringAsFixed(1);
  }
}
