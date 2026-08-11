import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../core/api_client.dart';
import '../../core/constants.dart';
import '../../core/app_theme.dart';
import '../../services/ingredient_service.dart';
import '../../widgets/loading_empty.dart';

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

  static const _pageSize = 15; // DESIGN.md §12.2 列表分页约定

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
    final t = AppTokens.of(context);
    return Scaffold(
      appBar: AppBar(title: const Text('食材库')),
      body: Column(children: [
        Padding(
          padding: const EdgeInsets.all(AppTokens.sp12),
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
              filled: true, fillColor: t.bg,
              border: OutlineInputBorder(borderRadius: BorderRadius.circular(AppTokens.rMd)),
            ),
            onChanged: (v) => setState(() => _hasText = v.isNotEmpty),
            onSubmitted: (_) => _reload(),
          ),
        ),
        Expanded(
          child: _firstLoading
              ? const LoadingView()
              : RefreshIndicator(
                  color: t.primary,
                  onRefresh: _reload,
                  child: _items.isEmpty
                      ? Center(child: Text('暂无食材', style: TextStyle(color: t.caption)))
                      : ListView.builder(
                          controller: _scroll,
                          padding: const EdgeInsets.symmetric(horizontal: AppTokens.sp12),
                          itemCount: _items.length + 1,
                          itemBuilder: (_, i) {
                            if (i == _items.length) {
                              return Padding(
                                padding: const EdgeInsets.all(AppTokens.sp16),
                                child: Center(child: Text(
                                  _hasMore ? '上拉加载更多' : '没有更多了',
                                  style: t.textStyles.sm.copyWith(color: t.caption),
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
    final t = AppTokens.of(context);
    return Card(
      margin: const EdgeInsets.only(bottom: AppTokens.sp8),
      elevation: 0,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AppTokens.rMd),
          side: BorderSide(color: t.border)),
      child: ListTile(
        leading: CircleAvatar(
          radius: 18,
          backgroundColor: t.primary.withAlpha(20),
          child: Icon(Icons.eco_outlined, size: 18, color: t.primary),
        ),
        title: Text(item.name, style: t.textStyles.md.copyWith(fontWeight: FontWeight.w600, color: t.title)),
        subtitle: Text(
          item.nutritionSummary,
          style: t.textStyles.sm.copyWith(color: t.caption),
        ),
        trailing: Icon(Icons.chevron_right, size: 18, color: t.caption),
        onTap: () async {
          await context.push('/ingredient/${item.id}/edit');
          _reload();
        },
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
