import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../core/api_client.dart';
import '../../core/theme.dart';

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

  static const _pageSize = 20;

  @override
  void initState() {
    super.initState();
    _scroll.addListener(_onScroll);
    _reload();
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
    showModalBottomSheet(
      context: context,
      shape: const RoundedRectangleBorder(
          borderRadius: BorderRadius.vertical(top: Radius.circular(16))),
      builder: (ctx) => Padding(
        padding: const EdgeInsets.all(20),
        child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(item.name, style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
          const SizedBox(height: 16),
          ...item.nutritions.entries.map((e) => Padding(
            padding: const EdgeInsets.symmetric(vertical: 4),
            child: Row(children: [
              Expanded(child: Text(e.key, style: const TextStyle(fontSize: 14))),
              Text(e.value, style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w600, color: AppColors.primary)),
            ]),
          )),
          const SizedBox(height: 20),
        ]),
      ),
    );
  }
}

class _IngredientItem {
  final int id;
  final String name;
  final String? unitName;
  final String? purchaseCategoryName;
  final Map<String, String> nutritions; // metricName → value

  const _IngredientItem({
    required this.id,
    required this.name,
    this.unitName,
    this.purchaseCategoryName,
    required this.nutritions,
  });

  factory _IngredientItem.fromJson(Map<String, dynamic> j) {
    // 营养数据在 nutritions 字段（后端 ingredient 列表可能附带）
    final nutritions = <String, String>{};
    if (j['nutritions'] is List) {
      for (final n in (j['nutritions'] as List)) {
        final m = n as Map<String, dynamic>;
        final name = m['metricName'] as String? ?? m['name'] as String? ?? '';
        final value = m['value']?.toString() ?? '';
        if (name.isNotEmpty) nutritions[name] = value;
      }
    }
    return _IngredientItem(
      id: (j['id'] as num).toInt(),
      name: (j['name'] ?? '') as String,
      unitName: j['unitName'] as String?,
      purchaseCategoryName: j['purchaseCategoryName'] as String?,
      nutritions: nutritions,
    );
  }

  String get nutritionSummary {
    if (nutritions.isEmpty) {
      return unitName != null ? '单位: $unitName' : '暂无营养数据';
    }
    final cal = nutritions['热量'] ?? nutritions['calorie'] ?? '';
    final protein = nutritions['蛋白质'] ?? nutritions['protein'] ?? '';
    final parts = <String>[];
    if (cal.isNotEmpty) parts.add('${cal}kcal');
    if (protein.isNotEmpty) parts.add('${protein}g蛋白');
    final base = parts.isEmpty ? '${nutritions.length}项营养' : parts.join(' · ');
    return '每100g · $base';
  }
}
