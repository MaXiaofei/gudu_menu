import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../core/api_client.dart';
import '../../core/image_helper.dart';
import '../../core/app_theme.dart';
import '../../models/dish.dart';
import '../../services/dish_service.dart';
import '../../services/ingredient_service.dart';
import '../../widgets/loading_empty.dart';

/// 搜索模式：按菜名 / 按食材。
enum _SearchMode { name, ingredient }

/// 排序：cooked=做过最多；latest=最新。
enum _SortMode { cooked, latest }

/// 菜库列表（按原型 cookbook-search.html 左屏）。
/// 搜索框 + 分类标签条 + 排序 + 分页 + 下拉刷新 + 上拉加载更多。
///
/// 搜索支持两种模式（搜索框左侧下拉切换）：
/// - 按菜名：输入菜名关键词，回车搜索。
/// - 按食材：输入食材名实时联想，选中后按"含这些食材"搜菜（可多选，交集）。
class DishListPage extends StatefulWidget {
  const DishListPage({super.key});
  @override
  State<DishListPage> createState() => _DishListPageState();
}

class _DishListPageState extends State<DishListPage> {
  final _scroll = ScrollController();
  final _keywordCtrl = TextEditingController();
  static const _pageSize = 20;

  List<Dish> _dishes = [];
  int _page = 1;
  int _total = 0;
  bool _loading = false;
  bool _hasMore = true;
  bool _firstLoading = true;
  bool _hasText = false;

  _SearchMode _mode = _SearchMode.name;
  _SortMode _sort = _SortMode.latest;
  final List<_IngredientOption> _selectedIngredients = [];

  // 分类标签条
  List<DictItem> _tags = [];
  int? _selectedTagId; // null = 全部

  @override
  void initState() {
    super.initState();
    _scroll.addListener(_onScroll);
    _loadTags();
    _reload();
  }

  @override
  void dispose() {
    _scroll.dispose();
    _keywordCtrl.dispose();
    super.dispose();
  }

  void _onScroll() {
    if (!_scroll.hasClients) return;
    if (_scroll.position.pixels >= _scroll.position.maxScrollExtent - 100 &&
        !_loading &&
        _hasMore) {
      _page++;
      _loadMore();
    }
  }

  Future<void> _loadTags() async {
    try {
      final tags = await IngredientService.listDictByGroup('tag');
      if (mounted) setState(() => _tags = tags);
    } catch (_) {}
  }

  Future<List<Dish>> _fetch(int pageNum) async {
    try {
      final r = await DishService.search(
        keyword: _mode == _SearchMode.name ? _keywordCtrl.text.trim() : null,
        ingredientIds: _selectedIngredients.isEmpty
            ? null
            : _selectedIngredients.map((e) => e.id).toList(),
        tagIds: _selectedTagId == null ? null : [_selectedTagId!],
        sort: _sort == _SortMode.cooked ? 'cooked' : null,
        pageNum: pageNum,
        pageSize: _pageSize,
      );
      _total = r.total;
      _hasMore = r.records.length >= _pageSize;
      return r.records;
    } catch (_) {
      _hasMore = false;
      return [];
    }
  }

  Future<void> _reload() async {
    _page = 1;
    _hasMore = true;
    setState(() => _firstLoading = true);
    final list = await _fetch(_page);
    _dishes = list;
    if (mounted) setState(() => _firstLoading = false);
  }

  Future<void> _loadMore() async {
    if (_loading) return;
    setState(() => _loading = true);
    final list = await _fetch(_page);
    _dishes.addAll(list);
    if (mounted) setState(() => _loading = false);
  }

  Future<List<_IngredientOption>> _suggestIngredients(String keyword) async {
    if (keyword.trim().isEmpty) return [];
    try {
      final data = await ApiClient.instance.get('/ingredient', query: {
        'keyword': keyword.trim(),
        'pageNum': 1,
        'pageSize': 20,
      });
      if (data is Map && data['records'] is List) {
        return (data['records'] as List)
            .map((e) => _IngredientOption.fromJson(e as Map<String, dynamic>))
            .where((e) => e.name.isNotEmpty)
            .toList();
      }
    } catch (_) {}
    return [];
  }

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    return Scaffold(
        appBar: AppBar(title: const Text('菜谱')),
        body: Column(
          children: [
            _buildSearchBar(t),
            if (_mode == _SearchMode.ingredient &&
                _selectedIngredients.isNotEmpty)
              _buildSelectedChips(t),
            if (_tags.isNotEmpty) _buildTagBar(t),
            _buildSortBar(t),
            Expanded(
              child: _firstLoading
                  ? const LoadingView()
                  : RefreshIndicator(
                      color: t.primary,
                      onRefresh: _reload,
                      child: _dishes.isEmpty
                          ? const EmptyView(text: '暂无菜品')
                          : ListView.builder(
                              controller: _scroll,
                              itemCount: _dishes.length + 1,
                              itemBuilder: (_, i) {
                                if (i == _dishes.length) {
                                  return Padding(
                                    padding: const EdgeInsets.all(16),
                                    child: Center(
                                      child: Text(
                                        _hasMore ? '上拉加载更多' : '没有更多了',
                                        style: TextStyle(
                                            color: t.caption, fontSize: 12),
                                      ),
                                    ),
                                  );
                                }
                                return _DishTile(dish: _dishes[i]);
                              },
                            ),
                    ),
            ),
          ],
        ),
      );
  }

  /// 搜索框：主色描边 + 左侧下拉选模式 + ✕ 清除。
  Widget _buildSearchBar(AppTokens t) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(
          AppTokens.sp12, AppTokens.sp12, AppTokens.sp12, AppTokens.sp8),
      child: Row(
        children: [
          Container(
            decoration: BoxDecoration(
              color: t.secondary,
              borderRadius: BorderRadius.circular(AppTokens.rMd),
            ),
            child: PopupMenuButton<_SearchMode>(
              initialValue: _mode,
              onSelected: (m) {
                setState(() {
                  _mode = m;
                  _keywordCtrl.clear();
                  _hasText = false;
                });
              },
              child: Padding(
                padding: const EdgeInsets.symmetric(
                    horizontal: AppTokens.sp12, vertical: AppTokens.sp16),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Text(
                      _mode == _SearchMode.name ? '菜名' : '食材',
                      style: TextStyle(
                          fontSize: 13,
                          fontWeight: FontWeight.w700,
                          color: t.accent),
                    ),
                    Icon(Icons.arrow_drop_down, size: 18, color: t.accent),
                  ],
                ),
              ),
              itemBuilder: (_) => const [
                PopupMenuItem(value: _SearchMode.name, child: Text('按菜名')),
                PopupMenuItem(value: _SearchMode.ingredient, child: Text('按食材')),
              ],
            ),
          ),
          const SizedBox(width: AppTokens.sp8),
          Expanded(
            child: _mode == _SearchMode.name
                ? TextField(
                    controller: _keywordCtrl,
                    decoration: InputDecoration(
                      hintText: '搜菜名',
                      isDense: true,
                      contentPadding: const EdgeInsets.symmetric(
                          horizontal: AppTokens.sp12, vertical: AppTokens.sp12),
                      enabledBorder: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(AppTokens.rMd),
                        borderSide: BorderSide(color: t.primary, width: 1.5),
                      ),
                      focusedBorder: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(AppTokens.rMd),
                        borderSide: BorderSide(color: t.primary, width: 1.5),
                      ),
                      suffixIcon: _hasText
                          ? IconButton(
                              icon: const Icon(Icons.close, size: 20),
                              onPressed: () {
                                _keywordCtrl.clear();
                                setState(() => _hasText = false);
                                _reload();
                              },
                            )
                          : null,
                    ),
                    onChanged: (v) => setState(() => _hasText = v.isNotEmpty),
                    onSubmitted: (_) => _reload(),
                  )
                : Autocomplete<_IngredientOption>(
                    optionsBuilder: (textEditingValue) =>
                        _suggestIngredients(textEditingValue.text),
                    displayStringForOption: (o) => o.name,
                    fieldViewBuilder:
                        (ctx, controller, focusNode, onFieldSubmitted) {
                      if (controller.text != _keywordCtrl.text) {
                        _keywordCtrl.text = controller.text;
                      }
                      return TextField(
                        controller: controller,
                        focusNode: focusNode,
                        decoration: InputDecoration(
                          hintText: '输食材名，如番茄',
                          isDense: true,
                          contentPadding: const EdgeInsets.symmetric(
                              horizontal: AppTokens.sp12,
                              vertical: AppTokens.sp12),
                          enabledBorder: OutlineInputBorder(
                            borderRadius: BorderRadius.circular(AppTokens.rMd),
                            borderSide: BorderSide(color: t.primary, width: 1.5),
                          ),
                          focusedBorder: OutlineInputBorder(
                            borderRadius: BorderRadius.circular(AppTokens.rMd),
                            borderSide: BorderSide(color: t.primary, width: 1.5),
                          ),
                        ),
                        onSubmitted: (_) => onFieldSubmitted(),
                      );
                    },
                    onSelected: (option) {
                      if (!_selectedIngredients.any((e) => e.id == option.id)) {
                        setState(() {
                          _selectedIngredients.add(option);
                          _keywordCtrl.clear();
                        });
                        _reload();
                      }
                    },
                    optionsViewBuilder: (ctx, onSelected, options) {
                      return Align(
                        alignment: Alignment.topLeft,
                        child: Material(
                          elevation: 4,
                          borderRadius: BorderRadius.circular(AppTokens.rMd),
                          child: ConstrainedBox(
                            constraints: BoxConstraints(
                              maxWidth: MediaQuery.of(ctx).size.width - 120,
                              maxHeight: 200,
                            ),
                            child: ListView.builder(
                              shrinkWrap: true,
                              itemCount: options.length,
                              itemBuilder: (_, i) {
                                final o = options.elementAt(i);
                                return ListTile(
                                  dense: true,
                                  title: Text(o.name,
                                      style: const TextStyle(fontSize: 13)),
                                  onTap: () => onSelected(o),
                                );
                              },
                            ),
                          ),
                        ),
                      );
                    },
                  ),
          ),
        ],
      ),
    );
  }

  /// 已选食材标签条（按食材模式）。
  Widget _buildSelectedChips(AppTokens t) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(
          horizontal: AppTokens.sp12, vertical: AppTokens.sp4),
      child: Wrap(
        spacing: AppTokens.sp8,
        runSpacing: AppTokens.sp8,
        children: [
          ..._selectedIngredients.map((ing) => Chip(
                materialTapTargetSize: MaterialTapTargetSize.shrinkWrap,
                visualDensity: const VisualDensity(horizontal: -4, vertical: -4),
                label: Text(ing.name, style: const TextStyle(fontSize: 12)),
                backgroundColor: t.primarySoft,
                labelStyle: TextStyle(color: t.accent),
                deleteIconColor: t.accent,
                onDeleted: () {
                  setState(() {
                    _selectedIngredients.removeWhere((e) => e.id == ing.id);
                  });
                  _reload();
                },
              )),
          if (_selectedIngredients.isNotEmpty)
            TextButton(
              onPressed: () {
                setState(() => _selectedIngredients.clear());
                _reload();
              },
              child: const Text('清除', style: TextStyle(fontSize: 12)),
            ),
        ],
      ),
    );
  }

  /// 分类标签条：横向滚动，全部 + 各 tag。
  Widget _buildTagBar(AppTokens t) {
    return SizedBox(
      height: 38,
      child: ListView(
        scrollDirection: Axis.horizontal,
        padding: const EdgeInsets.symmetric(horizontal: AppTokens.sp12),
        children: [
          _tagChip(t, null, '全部'),
          ..._tags.map((tag) => _tagChip(t, tag.id, tag.name)),
        ],
      ),
    );
  }

  Widget _tagChip(AppTokens t, int? id, String name) {
    final selected = _selectedTagId == id;
    return Padding(
      padding: const EdgeInsets.only(right: AppTokens.sp8),
      child: GestureDetector(
        onTap: () {
          setState(() => _selectedTagId = id);
          _reload();
        },
        child: Container(
          padding: const EdgeInsets.symmetric(
              horizontal: AppTokens.sp12, vertical: 5),
          decoration: BoxDecoration(
            color: selected ? t.title : t.card,
            borderRadius: BorderRadius.circular(AppTokens.rSm),
            border: selected ? null : Border.all(color: t.border),
          ),
          child: Text(
            name,
            style: TextStyle(
              fontSize: 10,
              fontWeight: FontWeight.w800,
              color: selected ? Colors.white : t.body,
            ),
          ),
        ),
      ),
    );
  }

  /// 排序行：左结果计数 + 右排序切换。
  Widget _buildSortBar(AppTokens t) {
    return Padding(
      padding: const EdgeInsets.symmetric(
          horizontal: AppTokens.sp12, vertical: AppTokens.sp4),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text('$_total 道', style: TextStyle(fontSize: 10, color: t.caption)),
          PopupMenuButton<_SortMode>(
            initialValue: _sort,
            onSelected: (s) {
              setState(() => _sort = s);
              _reload();
            },
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(
                  _sort == _SortMode.cooked ? '做过最多' : '最新',
                  style: TextStyle(
                      fontSize: 10, fontWeight: FontWeight.w800, color: t.accent),
                ),
                Icon(Icons.arrow_drop_down, size: 16, color: t.accent),
              ],
            ),
            itemBuilder: (_) => const [
              PopupMenuItem(value: _SortMode.cooked, child: Text('做过最多')),
              PopupMenuItem(value: _SortMode.latest, child: Text('最新')),
            ],
          ),
        ],
      ),
    );
  }
}

/// 食材联想选项。
class _IngredientOption {
  final int id;
  final String name;
  const _IngredientOption({required this.id, required this.name});

  factory _IngredientOption.fromJson(Map<String, dynamic> j) =>
      _IngredientOption(
        id: (j['id'] as num).toInt(),
        name: (j['name'] ?? '') as String,
      );

  @override
  bool operator ==(Object other) => other is _IngredientOption && id == other.id;

  @override
  int get hashCode => id;
}

/// 列表项：44px 圆角缩略图 + 菜名 + 做过N次/时间/难度。
class _DishTile extends StatelessWidget {
  final Dish dish;
  const _DishTile({required this.dish});

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    final hasCover = dish.coverUrl != null && dish.coverUrl!.isNotEmpty;
    final thumbUrl = hasCover
        ? ImageHelper.toThumbnail(ImageHelper.toAbsolute(dish.coverUrl!))
        : null;

    return ListTile(
      contentPadding: const EdgeInsets.symmetric(
          horizontal: AppTokens.sp12, vertical: AppTokens.sp4),
      leading: ClipRRect(
        borderRadius: BorderRadius.circular(AppTokens.rXl),
        child: SizedBox(
          width: 44,
          height: 44,
          child: thumbUrl != null
              ? Image.network(
                  thumbUrl,
                  fit: BoxFit.cover,
                  errorBuilder: (_, __, ___) => _placeholder(t),
                  loadingBuilder: (_, child, progress) {
                    if (progress == null) return child;
                    return _placeholder(t);
                  },
                )
              : _placeholder(t),
        ),
      ),
      title: Text(dish.name,
          style: TextStyle(
              fontSize: 13, fontWeight: FontWeight.w800, color: t.title)),
      subtitle: Text(
        [
          dish.cookedCount > 0 ? '做过 ${dish.cookedCount} 次' : '没做过',
          '${dish.cookTime ?? 0} 分',
          '难度${dish.difficulty ?? '-'}',
        ].join(' · '),
        style: TextStyle(fontSize: 10, color: t.caption),
      ),
      onTap: () => context.push('/dish/${dish.id}'),
    );
  }

  Widget _placeholder(AppTokens t) => Container(
        color: t.secondary,
        child: Icon(Icons.restaurant, color: t.border, size: 20),
      );
}
