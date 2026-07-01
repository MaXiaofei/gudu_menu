import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../core/api_client.dart';
import '../../core/constants.dart';
import '../../core/theme.dart';
import '../../services/ingredient_service.dart';

/// 食材列表页：搜索 + 分页 + 每项显示名称/单位/营养概览。
/// 点 + 进入录入新食材。
class IngredientListPage extends StatefulWidget {
  const IngredientListPage({super.key});
  @override
  State<IngredientListPage> createState() => _IngredientListPageState();
}

class _IngredientListPageState extends State<IngredientListPage> {
  final _keywordCtrl = TextEditingController();
  final _scroll = ScrollController();

  List<_IngredientItem> _items = [];
  int _page = 1;
  bool _loading = false;
  bool _hasMore = true;
  bool _firstLoading = true;
  bool _hasText = false;

  // 单位 / 采购品类 字典(id → name)，详情弹窗用
  final Map<int, String> _unitNames = {};
  final Map<int, String> _catNames = {};

  static const _pageSize = 20;

  @override
  void initState() {
    super.initState();
    _scroll.addListener(_onScroll);
    _reload();
    _loadDicts();
  }

  Future<void> _loadDicts() async {
    try {
      final results = await Future.wait([
        IngredientService.listDictByGroup('unit'),
        IngredientService.listDictByGroup('purchase_category'),
      ]);
      _unitNames.addEntries(results[0].map((d) => MapEntry(d.id, d.name)));
      _catNames.addEntries(results[1].map((d) => MapEntry(d.id, d.name)));
      if (mounted) setState(() {});
    } catch (_) {}
  }

  @override
  void dispose() {
    _keywordCtrl.dispose();
    _scroll.dispose();
    super.dispose();
  }

  void _onScroll() {
    if (!_scroll.hasClients) return;
    if (_scroll.position.pixels >= _scroll.position.maxScrollExtent - 100 &&
        !_loading && _hasMore) {
      _page++;
      _loadMore();
    }
  }

  Future<void> _reload() async {
    _page = 1;
    _hasMore = true;
    setState(() => _firstLoading = true);
    final list = await _fetch(_page);
    _items = list;
    if (mounted) setState(() => _firstLoading = false);
  }

  Future<void> _loadMore() async {
    if (_loading) return;
    setState(() => _loading = true);
    final list = await _fetch(_page);
    _items.addAll(list);
    if (mounted) setState(() => _loading = false);
  }

  Future<List<_IngredientItem>> _fetch(int pageNum) async {
    try {
      final data = await ApiClient.instance.get('/ingredient', query: {
        if (_keywordCtrl.text.trim().isNotEmpty) 'keyword': _keywordCtrl.text.trim(),
        'pageNum': pageNum,
        'pageSize': _pageSize,
      });
      final records = (data is Map) ? data['records'] : null;
      if (records is List) {
        _hasMore = records.length >= _pageSize;
        return records.map((e) => _IngredientItem.fromJson(e as Map<String, dynamic>)).toList();
      }
      _hasMore = false;
      return [];
    } catch (_) {
      _hasMore = false;
      return [];
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('食材库')),
      body: Column(children: [
        Padding(
          padding: const EdgeInsets.all(12),
          child: TextField(
            controller: _keywordCtrl,
            decoration: InputDecoration(
              hintText: '搜食材名',
              prefixIcon: const Icon(Icons.search),
              suffixIcon: _hasText
                  ? IconButton(
                      icon: const Icon(Icons.close),
                      onPressed: () {
                        _keywordCtrl.clear();
                        setState(() => _hasText = false);
                        _reload();
                      },
                    )
                  : null,
              filled: true, fillColor: const Color(0xFFFAFAFA),
              border: OutlineInputBorder(borderRadius: BorderRadius.circular(10)),
            ),
            onChanged: (v) => setState(() => _hasText = v.isNotEmpty),
            onSubmitted: (_) => _reload(),
          ),
        ),
        Expanded(
          child: _firstLoading
              ? const Center(child: CircularProgressIndicator())
              : RefreshIndicator(
                  color: AppColors.primary,
                  onRefresh: _reload,
                  child: _items.isEmpty
                      ? const Center(child: Text('暂无食材', style: TextStyle(color: AppColors.textSecondary)))
                      : ListView.builder(
                          controller: _scroll,
                          padding: const EdgeInsets.symmetric(horizontal: 12),
                          itemCount: _items.length + 1,
                          itemBuilder: (_, i) {
                            if (i == _items.length) {
                              return Padding(
                                padding: const EdgeInsets.all(16),
                                child: Center(child: Text(
                                  _hasMore ? '上拉加载更多' : '没有更多了',
                                  style: const TextStyle(color: AppColors.textSecondary, fontSize: 13),
                                )),
                              );
                            }
                            return _buildCard(_items[i]);
                          },
                        ),
                ),
        ),
      ]),
      floatingActionButton: FloatingActionButton(
        onPressed: () async {
          await context.push('/create-ingredient');
          _reload();
        },
        child: const Icon(Icons.add),
      ),
    );
  }

  Widget _buildCard(_IngredientItem item) {
    return Card(
      margin: const EdgeInsets.only(bottom: 8),
      elevation: 0,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10),
          side: const BorderSide(color: Color(0xFFEEEEEE))),
      child: ListTile(
        leading: CircleAvatar(
          radius: 18,
          backgroundColor: const Color(0xFF8B5E3C).withAlpha(20),
          child: const Icon(Icons.eco_outlined, size: 18, color: Color(0xFF8B5E3C)),
        ),
        title: Text(item.name, style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w600)),
        subtitle: Text(
          item.nutritionSummary,
          style: const TextStyle(fontSize: 12, color: AppColors.textSecondary),
        ),
        trailing: const Icon(Icons.chevron_right, size: 18, color: AppColors.textSecondary),
        onTap: () {
          // 后续可跳食材详情，暂时只显示营养
          _showNutritionSheet(item);
        },
      ),
    );
  }

  void _showNutritionSheet(_IngredientItem item) {
    final unit = item.unitId == null ? null : _unitNames[item.unitId];
    final cat = item.purchaseCategoryId == null ? null : _catNames[item.purchaseCategoryId];
    showModalBottomSheet(
      context: context,
      shape: const RoundedRectangleBorder(
          borderRadius: BorderRadius.vertical(top: Radius.circular(16))),
      builder: (ctx) => Padding(
        padding: const EdgeInsets.fromLTRB(20, 20, 20, 28),
        child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(item.name, style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
          if (unit != null || cat != null) ...[
            const SizedBox(height: 6),
            Text(
              [
                if (unit != null) '单位：$unit',
                if (cat != null) '品类：$cat',
              ].join('  ·  '),
              style: const TextStyle(fontSize: 12, color: AppColors.textSecondary),
            ),
          ],
          const SizedBox(height: 16),
          if (item.nutritions.isEmpty)
            const Padding(
              padding: EdgeInsets.symmetric(vertical: 12),
              child: Text('暂无营养数据', style: TextStyle(color: AppColors.textSecondary)),
            )
          else ...[
            const Text('每 100g 营养',
                style: TextStyle(fontSize: 13, color: AppColors.textSecondary, fontWeight: FontWeight.w600)),
            const SizedBox(height: 8),
            ...item.nutritions.entries.map((e) => Padding(
                  padding: const EdgeInsets.symmetric(vertical: 5),
                  child: Row(children: [
                    Expanded(
                        child: Text(e.key, style: const TextStyle(fontSize: 14))),
                    Text(
                      '${e.value} ${_IngredientItem.unitByCn[e.key] ?? ''}'.trim(),
                      style: const TextStyle(
                          fontSize: 14, fontWeight: FontWeight.w600, color: AppColors.primary),
                    ),
                  ]),
                )),
          ],
          const SizedBox(height: 20),
        ]),
      ),
    );
  }
}

class _IngredientItem {
  final int id;
  final String name;
  final int? unitId;
  final int? purchaseCategoryId;
  final Map<String, String> nutritions; // 中文指标名 → 值（按固定顺序）

  const _IngredientItem({
    required this.id,
    required this.name,
    this.unitId,
    this.purchaseCategoryId,
    required this.nutritions,
  });

  /// 营养指标后端字段名（英文）→ 固定展示顺序。
  static const _metricOrder = ['calorie', 'protein', 'fat', 'carb', 'sugar', 'gi'];

  /// 中文指标名 → 单位（每100g）。
  static const unitByCn = {
    '热量': 'kcal',
    '蛋白质': 'g',
    '脂肪': 'g',
    '碳水': 'g',
    '糖': 'g',
    '升糖指数': '',
  };

  factory _IngredientItem.fromJson(Map<String, dynamic> j) {
    // 后端列表返回 nutrition（Map<英文指标名, 值>，每100g），非 nutritions 数组。
    final raw = j['nutrition'];
    final nutritions = <String, String>{};
    void addCn(String enKey, dynamic v) {
      if (v == null) return;
      nutritions[AppConstants.metricNameCn(enKey)] = _fmtNum(v);
    }

    if (raw is Map) {
      // 先按固定顺序插入，保证展示稳定
      for (final en in _metricOrder) {
        addCn(en, raw[en]);
      }
      // 再补 _metricOrder 之外可能的指标
      raw.forEach((k, v) {
        final key = k.toString();
        if (!_metricOrder.contains(key)) addCn(key, v);
      });
    }
    return _IngredientItem(
      id: (j['id'] as num).toInt(),
      name: (j['name'] ?? '') as String,
      unitId: (j['unitId'] as num?)?.toInt(),
      purchaseCategoryId: (j['purchaseCategoryId'] as num?)?.toInt(),
      nutritions: nutritions,
    );
  }

  String get nutritionSummary {
    if (nutritions.isEmpty) return '暂无营养数据';
    final cal = nutritions['热量'] ?? '';
    final protein = nutritions['蛋白质'] ?? '';
    final parts = <String>[];
    if (cal.isNotEmpty) parts.add('${cal}kcal');
    if (protein.isNotEmpty) parts.add('${protein}g蛋白');
    final base = parts.isEmpty ? '${nutritions.length}项营养' : parts.join(' · ');
    return '每100g · $base';
  }

  static String _fmtNum(dynamic v) {
    final d = v is num ? v.toDouble() : double.tryParse(v.toString());
    if (d == null) return v.toString();
    return d == d.roundToDouble() ? d.toInt().toString() : d.toStringAsFixed(1);
  }
}
