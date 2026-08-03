import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../core/api_client.dart';
import '../../core/image_helper.dart';
import '../../core/app_theme.dart';
import '../../models/dish.dart';
import '../../services/dish_service.dart';
import '../../widgets/loading_empty.dart';

/// 搜索模式：按菜名 / 按食材。
enum _SearchMode { name, ingredient }

/// 菜库列表（复刻 menu-mini/src/pages/dish/List.vue）。
/// 搜索 + 分页（pageSize=20）+ 下拉刷新 + 上拉加载更多。
///
/// 搜索支持两种模式（搜索框左侧下拉切换）：
/// - 按菜名：输入菜名关键词，回车搜索。
/// - 按食材：输入食材名实时联想，选中食材后按"含这些食材"搜菜（可多选，交集）。
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
  bool _loading = false;
  bool _hasMore = true;
  bool _firstLoading = true;
  bool _hasText = false;

  _SearchMode _mode = _SearchMode.name;
  // 已选食材（按食材模式）：{id, name}
  final List<_IngredientOption> _selectedIngredients = [];

  @override
  void initState() {
    super.initState();
    _scroll.addListener(_onScroll);
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

  Future<List<Dish>> _fetch(int pageNum) async {
    try {
      final r = await DishService.search(
        keyword: _mode == _SearchMode.name ? _keywordCtrl.text.trim() : null,
        ingredientIds: _selectedIngredients.isEmpty
            ? null
            : _selectedIngredients.map((e) => e.id).toList(),
        pageNum: pageNum,
        pageSize: _pageSize,
      );
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

  /// 按食材模式：实时联想食材（调 /ingredient?keyword=）。
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
        appBar: AppBar(title: const Text('菜库')),
        body: Column(
          children: [
            _buildSearchBar(t),
            // 已选食材标签（按食材模式）
            if (_mode == _SearchMode.ingredient &&
                _selectedIngredients.isNotEmpty)
              _buildSelectedChips(t),
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
                                            color: t.caption,
                                            fontSize: 12),
                                      ),
                                    ),
                                  );
                                }
                                final d = _dishes[i];
                                return _DishTile(dish: d);
                              },
                            ),
                    ),
            ),
          ],
        ),
      );
  }

  /// 搜索框：左侧下拉选模式 + 右侧输入框（按食材模式带联想）。
  Widget _buildSearchBar(AppTokens t) {
    return Padding(
      padding: const EdgeInsets.all(AppTokens.sp12),
      child: Row(
        children: [
          // 模式下拉
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
                // 不刷新列表：切模式只是换搜索方式，列表保持当前结果
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
                PopupMenuItem(
                  value: _SearchMode.name,
                  child: Text('按菜名'),
                ),
                PopupMenuItem(
                  value: _SearchMode.ingredient,
                  child: Text('按食材'),
                ),
              ],
            ),
          ),
          const SizedBox(width: AppTokens.sp8),
          // 输入框
          Expanded(
            child: _mode == _SearchMode.name
                ? TextField(
                    controller: _keywordCtrl,
                    decoration: InputDecoration(
                      hintText: '搜菜名',
                      isDense: true,
                      contentPadding: const EdgeInsets.symmetric(
                          horizontal: AppTokens.sp12, vertical: AppTokens.sp12),
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
                      // 同步外部 _keywordCtrl（用于清空等）
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

  /// 已选食材标签条（按食材模式，点 ✕ 移除并重搜）。
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
                setState(() {
                  _selectedIngredients.clear();
                });
                _reload();
              },
              child: const Text('清除', style: TextStyle(fontSize: 12)),
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

/// 列表项：封面缩略图 + 菜名 + 时间/难度。
class _DishTile extends StatelessWidget {
  final Dish dish;
  const _DishTile({required this.dish});

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    final hasCover =
        dish.coverUrl != null && dish.coverUrl!.isNotEmpty;
    final thumbUrl = hasCover
        ? ImageHelper.toThumbnail(ImageHelper.toAbsolute(dish.coverUrl!))
        : null;

    return ListTile(
      leading: ClipRRect(
        borderRadius: BorderRadius.circular(AppTokens.rSm),
        child: SizedBox(
          width: 56,
          height: 56,
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
      title: Text(dish.name),
      subtitle: Text(
        [
          if (dish.cuisineNames.isNotEmpty) dish.cuisineNames.join('/'),
          '${dish.cookTime ?? 0}分钟',
          '难度${dish.difficulty ?? '-'}',
        ].join(' · '),
        style: TextStyle(
            color: t.caption, fontSize: 12),
      ),
      trailing: Icon(Icons.chevron_right,
          color: t.caption),
      onTap: () => context.push('/dish/${dish.id}'),
    );
  }

  Widget _placeholder(AppTokens t) => Container(
        color: t.bg,
        child: Icon(Icons.restaurant, color: t.border, size: 24),
      );
}
