import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../core/api_client.dart';
import '../../core/app_theme.dart';
import '../../services/ingredient_service.dart';
import '../../widgets/loading_empty.dart';

/// 食材库列表页（对齐原型 pantry-ingredient.html）：
/// 顶部「+ 添加」右对齐独占一行（§13.1 管理列表页无大标题）+ 搜索 + 分类筛选。
/// 每项：名称 + 品类标签 +「默认 单位 · ¥价/单位」副标题 + 已设换算/去补徽标；
/// 没设换算的标黄虚线提醒补全（菜价/营养算不准）。
class IngredientListPage extends StatefulWidget {
  const IngredientListPage({super.key});
  @override
  State<IngredientListPage> createState() => _IngredientListPageState();
}

class _IngredientListPageState extends State<IngredientListPage> {
  final _keywordCtrl = TextEditingController();
  final _scroll = ScrollController();

  List<_IngredientItem> _items = [];
  List<DictItem> _categories = [];
  int? _categoryId;
  int _page = 1;
  int _total = 0;
  bool _loading = false;
  bool _hasMore = true;
  bool _firstLoading = true;
  bool _hasText = false;

  static const _pageSize = 15; // DESIGN.md §12.2 列表分页约定

  @override
  void initState() {
    super.initState();
    _scroll.addListener(_onScroll);
    _loadCategories();
    _reload();
  }

  @override
  void dispose() {
    _keywordCtrl.dispose();
    _scroll.dispose();
    super.dispose();
  }

  Future<void> _loadCategories() async {
    try {
      final cats = await IngredientService.listDictByGroup('purchase_category');
      if (mounted) setState(() => _categories = cats);
    } catch (_) {}
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
    final r = await _fetch(_page);
    _items = r.items;
    _total = r.total;
    if (mounted) setState(() => _firstLoading = false);
  }

  Future<void> _loadMore() async {
    if (_loading) return;
    setState(() => _loading = true);
    final r = await _fetch(_page);
    _items.addAll(r.items);
    _total = r.total;
    if (mounted) setState(() => _loading = false);
  }

  Future<({List<_IngredientItem> items, int total})> _fetch(int pageNum) async {
    try {
      final data = await ApiClient.instance.get('/ingredient', query: {
        if (_keywordCtrl.text.trim().isNotEmpty)
          'keyword': _keywordCtrl.text.trim(),
        if (_categoryId != null) 'purchaseCategoryId': _categoryId,
        'pageNum': pageNum,
        'pageSize': _pageSize,
      });
      final records = (data is Map) ? data['records'] : null;
      final total = (data is Map) ? (data['total'] as num?)?.toInt() ?? 0 : 0;
      if (records is List) {
        _hasMore = records.length >= _pageSize;
        return (
          items: records
              .map((e) => _IngredientItem.fromJson(e as Map<String, dynamic>))
              .toList(),
          total: total,
        );
      }
      _hasMore = false;
      return (items: <_IngredientItem>[], total: total);
    } catch (_) {
      _hasMore = false;
      return (items: <_IngredientItem>[], total: 0);
    }
  }

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    return Scaffold(
      body: SafeArea(
        child: Column(children: [
          // 操作行：+ 添加 右对齐独占一行（§13.1 管理列表页无大标题）
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 0),
            child: Row(children: [
              GestureDetector(
                onTap: () => context.pop(),
                child: Text('‹',
                    style: TextStyle(
                        fontSize: 22, color: t.title, fontWeight: FontWeight.w800)),
              ),
              const Spacer(),
              GestureDetector(
                onTap: () async {
                  await context.push('/create-ingredient');
                  _reload();
                },
                child: Container(
                  padding: const EdgeInsets.symmetric(horizontal: 13, vertical: 6),
                  decoration: BoxDecoration(
                    color: t.highlight,
                    borderRadius: BorderRadius.circular(99),
                  ),
                  child: Text('+ 添加',
                      style: t.textStyles.sm.copyWith(
                          color: t.primary, fontWeight: FontWeight.w800)),
                ),
              ),
            ]),
          ),
          // 搜索
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 10, 16, 0),
            child: TextField(
              controller: _keywordCtrl,
              decoration: InputDecoration(
                hintText: '搜食材名',
                prefixIcon: const Icon(Icons.search, size: 18),
                suffixIcon: _hasText
                    ? IconButton(
                        icon: const Icon(Icons.close, size: 16),
                        onPressed: () {
                          _keywordCtrl.clear();
                          setState(() => _hasText = false);
                          _reload();
                        },
                      )
                    : null,
                filled: true,
                fillColor: t.bg,
                isDense: true,
                contentPadding: const EdgeInsets.symmetric(vertical: 8),
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(AppTokens.rMd),
                  borderSide: BorderSide(color: t.border),
                ),
              ),
              onChanged: (v) => setState(() => _hasText = v.isNotEmpty),
              onSubmitted: (_) => _reload(),
            ),
          ),
          // 分类筛选
          SizedBox(
            height: 40,
            child: ListView(
              scrollDirection: Axis.horizontal,
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
              children: [
                _categoryChip('全部 $_total', null),
                for (final c in _categories) _categoryChip(c.name, c.id),
              ],
            ),
          ),
          Expanded(
            child: _firstLoading
                ? const LoadingView()
                : RefreshIndicator(
                    color: t.primary,
                    onRefresh: _reload,
                    child: _items.isEmpty
                        ? ListView(children: [
                            const SizedBox(height: 120),
                            Center(
                                child: Text('暂无食材',
                                    style: TextStyle(color: t.caption))),
                          ])
                        : ListView.builder(
                            controller: _scroll,
                            padding: const EdgeInsets.symmetric(horizontal: 16),
                            itemCount: _items.length + 2,
                            itemBuilder: (_, i) {
                              if (i == _items.length) {
                                return Padding(
                                  padding: const EdgeInsets.all(12),
                                  child: Center(
                                    child: Text(
                                      _hasMore ? '上拉加载更多' : '没有更多了',
                                      style: t.textStyles.sm
                                          .copyWith(color: t.caption),
                                    ),
                                  ),
                                );
                              }
                              if (i == _items.length + 1) {
                                return Padding(
                                  padding: const EdgeInsets.fromLTRB(0, 4, 0, 16),
                                  child: Text(
                                    '食材从「我的」进入；没设换算的标黄，提醒补全',
                                    textAlign: TextAlign.center,
                                    style: t.textStyles.sm
                                        .copyWith(color: t.caption),
                                  ),
                                );
                              }
                              return _buildCard(_items[i]);
                            },
                          ),
                  ),
          ),
        ]),
      ),
    );
  }

  Widget _categoryChip(String name, int? id) {
    final t = AppTokens.of(context);
    final sel = _categoryId == id;
    return GestureDetector(
      onTap: () {
        if (sel) return;
        setState(() => _categoryId = id);
        _reload();
      },
      child: Container(
        margin: const EdgeInsets.only(right: 6),
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
        decoration: BoxDecoration(
          color: sel ? t.title : t.card,
          border: Border.all(color: sel ? t.title : t.border),
          borderRadius: BorderRadius.circular(8),
        ),
        child: Text(name,
            style: t.textStyles.sm.copyWith(
                color: sel ? Colors.white : t.body, fontWeight: FontWeight.w800)),
      ),
    );
  }

  Widget _buildCard(_IngredientItem item) {
    final t = AppTokens.of(context);
    final hasGram = item.unitGramCount > 0;
    return Container(
      margin: const EdgeInsets.only(bottom: 8),
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      decoration: BoxDecoration(
        color: hasGram ? t.card : t.highlight,
        border: Border.all(color: hasGram ? t.border : AppTokens.warning),
        borderRadius: BorderRadius.circular(AppTokens.rMd),
      ),
      child: InkWell(
        borderRadius: BorderRadius.circular(AppTokens.rMd),
        onTap: () async {
          await context.push('/ingredient/${item.id}/edit');
          _reload();
        },
        child: Row(children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(children: [
                  Flexible(
                    child: Text(item.name,
                        style: t.textStyles.md.copyWith(
                            fontWeight: FontWeight.w800, color: t.title)),
                  ),
                  if (item.edible != 1 && item.categoryName != null) ...[
                    const SizedBox(width: 5),
                    Text(item.categoryName!,
                        style: t.textStyles.xs.copyWith(
                            color: t.primaryDeep,
                            backgroundColor: t.bg,
                            fontWeight: FontWeight.w700)),
                  ],
                ]),
                const SizedBox(height: 2),
                Text(
                  hasGram ? item.subtitle : '没设单位换算，菜价/营养算不准',
                  style: t.textStyles.xs.copyWith(
                      color: hasGram ? t.caption : t.primaryDeep),
                ),
              ],
            ),
          ),
          const SizedBox(width: 8),
          if (hasGram)
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
              decoration: BoxDecoration(
                color: AppTokens.success,
                borderRadius: BorderRadius.circular(99),              ),
              child: Text('已设换算',
                  style: t.textStyles.xs.copyWith(
                      color: Colors.white, fontWeight: FontWeight.w800)),
            )
          else
            Text('去补',
                style: t.textStyles.sm.copyWith(
                    color: t.primary, fontWeight: FontWeight.w800)),
        ]),
      ),
    );
  }
}

/// 列表项：名称 + 默认单位/单价副标题 + 品类 + 换算条数 + 食用属性。
class _IngredientItem {
  final int id;
  final String name;

  /// 默认单位名（ingredient.unit_id → 字典）。
  final String? unitName;

  /// 单价（元/默认单位）。
  final double price;

  /// 采购品类名（可空）。
  final String? categoryName;

  /// 单位→克换算条数（0 = 没设换算，标黄去补）。
  final int unitGramCount;

  /// 食用属性：1食用/2饮料零食/3生活用品。
  final int edible;

  const _IngredientItem({
    required this.id,
    required this.name,
    this.unitName,
    this.price = 0,
    this.categoryName,
    this.unitGramCount = 0,
    this.edible = 1,
  });

  factory _IngredientItem.fromJson(Map<String, dynamic> j) => _IngredientItem(
        id: (j['id'] as num).toInt(),
        name: (j['name'] ?? '') as String,
        unitName: (j['unitName'] as String?)?.trim().isNotEmpty == true
            ? j['unitName'] as String
            : null,
        price: (j['price'] as num?)?.toDouble() ?? 0,
        categoryName: (j['categoryName'] as String?)?.trim().isNotEmpty == true
            ? j['categoryName'] as String
            : null,
        unitGramCount: (j['unitGramCount'] as num?)?.toInt() ?? 0,
        edible: (j['edible'] as num?)?.toInt() ?? 1,
      );

  /// 「默认 个 · ¥1/个」（原型）；无价格只显单位；非食用补「非营养/非食用」。
  String get subtitle {
    final parts = <String>['默认 ${unitName ?? '-'}'];
    if (price > 0) parts.add('¥${_fmtPrice(price)}/$unitName');
    if (edible == 2) parts.add('非营养');
    if (edible == 3) parts.add('非食用');
    return parts.join(' · ');
  }

  static String _fmtPrice(double v) =>
      v == v.roundToDouble() ? v.toInt().toString() : v.toStringAsFixed(1);
}
