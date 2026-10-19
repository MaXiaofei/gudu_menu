import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import 'package:provider/provider.dart';

import '../../core/app_theme.dart';
import '../../models/dish.dart';
import '../../models/member.dart';
import '../../services/dish_service.dart';
import '../../services/ingredient_service.dart';
import '../../services/menu_service.dart';
import '../../stores/member_store.dart';
import '../../widgets/me_avatar_button.dart';
import '../../widgets/action_bar.dart';
import '../../widgets/error_view.dart';
import '../../widgets/loading_empty.dart';
import '../../widgets/member_avatar.dart';
import '../../widgets/search_box.dart';
import '../../widgets/select_chip.dart';
import '../../widgets/skeleton.dart';
import 'dish_cards.dart';

/// 排序：cooked=做过最多；latest=最新；coverage=家里够的优先（G3）。
enum _SortMode { cooked, latest, coverage }

/// 菜库列表（按原型 cookbook-search.html 左屏）。
/// 浅色顶栏 + 纯搜索框 + 分类标签条 + 排序 + 卡片式列表。
///
/// [selectForMenuId] 非空 = 选择模式：从食集详情「+ 加菜」进来，
/// 点菜卡直接加入该食集并返回（原型「加菜（去菜谱找）」）。
///
class DishListPage extends StatefulWidget {
  final int? selectForMenuId;

  /// 外部跳转要求按「最新」排序（写菜谱发布成功后 ?sort=latest，§16）。
  final bool sortLatest;
  const DishListPage(
      {super.key, this.selectForMenuId, this.sortLatest = false});
  @override
  State<DishListPage> createState() => _DishListPageState();
}

class _DishListPageState extends State<DishListPage> {
  final _scroll = ScrollController();
  final _keywordCtrl = TextEditingController();
  final _searchFocus = FocusNode();
  static const _pageSize = 10; // DESIGN.md §12.2（默认 10 条/页）

  List<Dish> _dishes = [];
  int _page = 1;
  int _total = 0;
  bool _loading = false;
  bool _hasMore = true;
  bool _firstLoading = true;
  /// 首屏加载失败（显示错误态 + 重试，不再静默成「暂无菜品」）。
  bool _loadFailed = false;

  _SortMode _sort = _SortMode.latest;

  // 分类标签条
  List<DictItem> _tags = [];
  int? _selectedTagId; // null = 全部

  // 菜系筛选条（§16：与分类同款样式，可叠加）
  List<DictItem> _cuisines = [];
  int? _selectedCuisineId; // null = 全部

  @override
  void initState() {
    super.initState();
    _scroll.addListener(_onScroll);
    if (widget.sortLatest) _sort = _SortMode.latest;
    _searchFocus.addListener(() => setState(() {}));
    _loadTags();
    _reload();
  }

  @override
  void didUpdateWidget(DishListPage oldWidget) {
    super.didUpdateWidget(oldWidget);
    // 发布成功跳转 ?sort=latest：页面已存在（IndexedStack 状态保持）时强制切回最新
    if (widget.sortLatest && _sort != _SortMode.latest) {
      setState(() => _sort = _SortMode.latest);
      _reload();
    }
  }

  @override
  void dispose() {
    _scroll.dispose();
    _keywordCtrl.dispose();
    _searchFocus.dispose();
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
      final cuisines = await IngredientService.listDictByGroup('cuisine');
      if (mounted) setState(() => _cuisines = cuisines);
    } catch (_) {}
  }

  Future<List<Dish>?> _fetch(int pageNum) async {
    try {
      final r = await DishService.search(
        keyword: _keywordCtrl.text.trim().isEmpty ? null : _keywordCtrl.text.trim(),
        tagIds: _selectedTagId == null ? null : [_selectedTagId!],
        cuisineIds: _selectedCuisineId == null ? null : [_selectedCuisineId!],
        sort: switch (_sort) {
          _SortMode.cooked => 'cooked',
          _SortMode.coverage => 'coverage',
          _SortMode.latest => null,
        },
        pageNum: pageNum,
        pageSize: _pageSize,
      );
      _total = r.total;
      _hasMore = r.records.length >= _pageSize;
      return r.records;
    } catch (_) {
      _hasMore = false;
      return null; // 调用方区分失败（null）与空页（[]）
    }
  }

  Future<void> _reload() async {
    _page = 1;
    _hasMore = true;
    setState(() {
      _firstLoading = true;
      _loadFailed = false;
    });
    final list = await _fetch(_page);
    _dishes = list ?? [];
    if (mounted) {
      setState(() {
        _firstLoading = false;
        _loadFailed = list == null;
      });
    }
  }

  Future<void> _loadMore() async {
    if (_loading) return;
    setState(() => _loading = true);
    final list = await _fetch(_page);
    if (list == null) {
      _page--; // 回退页码，下次上拉重试
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('加载更多失败，下拉刷新重试')));
      }
    } else {
      _dishes.addAll(list);
    }
    if (mounted) setState(() => _loading = false);
  }

  /// 当前家里的成员（无 Provider 时——如单测直接 pump 本页——返回空）。
  List<Member> _members(BuildContext context) {
    try {
      return Provider.of<MemberStore>(context).members;
    } catch (_) {
      return const [];
    }
  }

  bool get _browsing => widget.selectForMenuId == null;

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    final ts = t.textStyles;
    final members = _members(context);
    return Scaffold(
      backgroundColor: t.bg,
      floatingActionButton: _browsing
          ? FloatingActionButton.extended(
              heroTag: 'dish-create',
              backgroundColor: t.title,
              foregroundColor: t.card,
              elevation: 2,
              shape: const StadiumBorder(),
              onPressed: () => context.push('/create-dish'),
              icon: const Icon(Icons.add_rounded),
              label: Text('写菜谱', style: ts.cardTitle.copyWith(color: t.card)),
            )
          : null,
      body: SafeArea(
        bottom: false,
        child: Column(
          children: [
            if (!_browsing)
              const BackHeader(title: '选择要加入的菜')
            else
              Padding(
                padding: const EdgeInsets.fromLTRB(AppTokens.sp16, AppTokens.sp12, AppTokens.sp16, AppTokens.sp4),
                child: Row(
                  children: [
                    Expanded(child: Text('菜谱', style: ts.h2)),
                    if (members.isNotEmpty)
                      InkWell(
                        borderRadius: BorderRadius.circular(AppTokens.rPill),
                        onTap: () => context.push('/members'),
                        child: Container(
                          padding: const EdgeInsets.fromLTRB(AppTokens.sp4, AppTokens.sp4, AppTokens.sp10, AppTokens.sp4),
                          decoration: BoxDecoration(
                            color: t.card,
                            border: Border.all(color: t.border),
                            borderRadius: BorderRadius.circular(AppTokens.rPill),
                          ),
                          child: Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              AvatarStack(
                                size: 24,
                                max: 3,
                                people: [for (final m in members) (name: m.name, avatarUrl: m.avatarUrl)],
                              ),
                              const SizedBox(width: AppTokens.sp6),
                              Text('我的家 · ${members.length} 人', style: ts.caption.copyWith(color: t.title)),
                              Icon(Icons.expand_more_rounded, size: 16, color: t.body),
                            ],
                          ),
                        ),
                      ),
                    const SizedBox(width: AppTokens.sp8),
                    const MeAvatarButton(),
                  ],
                ),
              ),
            _buildSearchBox(t),
            _buildSortBar(t),
            Expanded(
              child: _firstLoading
                  ? const SkeletonList(count: 5, thumb: 84)
                  : _loadFailed
                      ? ErrorView(text: '菜谱加载失败', onRetry: _reload)
                      : RefreshIndicator(
                          color: t.primary,
                          onRefresh: _reload,
                          child: _dishes.isEmpty
                              ? ListView(children: [
                                  const SizedBox(height: 80),
                                  EmptyView(
                                    text: '暂无菜品',
                                    subtitle: '写下家里常做的菜，或从链接导入',
                                    actionLabel: _browsing ? '写菜谱' : null,
                                    onAction: _browsing ? () => context.push('/create-dish') : null,
                                  ),
                                ])
                              : ListView.builder(
                                  controller: _scroll,
                                  padding: const EdgeInsets.fromLTRB(AppTokens.sp16, AppTokens.sp4, AppTokens.sp16, 96),
                                  itemCount: _dishes.length + 1,
                                  itemBuilder: (_, i) {
                                    if (i == _dishes.length) {
                                      return Padding(
                                        padding: const EdgeInsets.all(AppTokens.sp16),
                                        child: Center(
                                          child: Text(_hasMore ? '上拉加载更多' : '没有更多了', style: ts.caption),
                                        ),
                                      );
                                    }
                                    final dish = _dishes[i];
                                    final featured = _browsing &&
                                        i == 0 &&
                                        (dish.coverUrl ?? '').isNotEmpty &&
                                        _keywordCtrl.text.trim().isEmpty;
                                    final card = featured
                                        ? FeaturedDishCard(dish: dish)
                                        : DishRowCard(
                                            dish: dish,
                                            onTapOverride: _browsing ? null : () => _addToMenu(dish),
                                          );
                                    if (!_browsing) return card;
                                    return Dismissible(
                                      key: ValueKey('dish-${dish.id}'),
                                      direction: DismissDirection.endToStart,
                                      background: Container(
                                        margin: const EdgeInsets.only(bottom: AppTokens.sp10),
                                        alignment: Alignment.centerRight,
                                        padding: const EdgeInsets.only(right: AppTokens.sp20),
                                        decoration: BoxDecoration(
                                          color: AppTokens.error,
                                          borderRadius: BorderRadius.circular(AppTokens.rLg),
                                        ),
                                        child: Icon(Icons.delete_outline, color: t.card),
                                      ),
                                      confirmDismiss: (_) => _confirmDelete(dish),
                                      onDismissed: (_) {
                                        setState(() => _dishes.removeWhere((d) => d.id == dish.id));
                                        ScaffoldMessenger.of(context)
                                            .showSnackBar(SnackBar(content: Text('已删除「${dish.name}」')));
                                      },
                                      child: card,
                                    );
                                  },
                                ),
                        ),
            ),
          ],
        ),
      ),
    );
  }

  /// 筛选弹层：分类 + 菜系（原型「筛选」芯片）。
  Future<void> _openFilter() async {
    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      builder: (ctx) {
        final t = AppTokens.of(ctx);
        final ts = t.textStyles;
        return StatefulBuilder(builder: (ctx, setSheet) {
          Widget group(String title, List<DictItem> dict, int? sel, ValueChanged<int?> onSel) => Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(title, style: ts.lg),
                  const SizedBox(height: AppTokens.sp8),
                  Wrap(spacing: AppTokens.sp8, runSpacing: AppTokens.sp8, children: [
                    SelectChip(label: '全部', selected: sel == null, onTap: () => onSel(null)),
                    for (final d in dict) SelectChip(label: d.name, selected: sel == d.id, onTap: () => onSel(d.id)),
                  ]),
                  const SizedBox(height: AppTokens.sp16),
                ],
              );
          return SafeArea(
            child: Padding(
              padding: const EdgeInsets.all(AppTokens.sp16),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  if (_tags.isNotEmpty)
                    group('分类', _tags, _selectedTagId, (id) => setSheet(() => _selectedTagId = id)),
                  if (_cuisines.isNotEmpty)
                    group('菜系', _cuisines, _selectedCuisineId, (id) => setSheet(() => _selectedCuisineId = id)),
                  if (_tags.isEmpty && _cuisines.isEmpty) Text('暂无可用筛选', style: ts.body),
                  const SizedBox(height: AppTokens.sp8),
                  ElevatedButton(onPressed: () => Navigator.pop(ctx), child: const Text('看结果')),
                ],
              ),
            ),
          );
        });
      },
    );
    if (mounted) {
      setState(() {});
      _reload();
    }
  }

  /// 选择模式：把菜加入目标食集，提示后返回。
  Future<void> _addToMenu(Dish dish) async {
    final menuId = widget.selectForMenuId!;
    try {
      await MenuService.addDishToMenu(menuId, dish.id, dishName: dish.name);
      if (!mounted) return;
      ScaffoldMessenger.of(context)
          .showSnackBar(const SnackBar(content: Text('已加入食集')));
      context.pop();
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(const SnackBar(content: Text('加入失败')));
      }
    }
  }

  /// 左滑删除确认：弹窗确认 → 调删除接口；成功返回 true（卡片滑走），
  /// 失败提示并返回 false（卡片弹回）。
  Future<bool> _confirmDelete(Dish dish) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('删除菜谱'),
        content: Text('确定删除「${dish.name}」吗？\n删除后不可恢复。'),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(ctx, false),
              child: const Text('取消')),
          TextButton(
              onPressed: () => Navigator.pop(ctx, true),
              child: const Text('删除',
                  style: TextStyle(
                      color: AppTokens.error, fontWeight: FontWeight.w700))),
        ],
      ),
    );
    if (ok != true) return false;
    try {
      await DishService.deleteDish(dish.id);
      return true;
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(const SnackBar(content: Text('删除失败，请稍后重试')));
      }
      return false;
    }
  }

  /// 搜索框：白底 + 1.5px 主色描边 + 12px圆角。
  /// 搜索中态：深棕文字 12px + 橙色闪烁竖线光标 + 灰色 ✕ 清除。
  /// （原型 cookbook-search.html 行16-23）
  Widget _buildSearchBox(AppTokens t) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(AppTokens.sp16, AppTokens.sp8, AppTokens.sp16, AppTokens.sp4),
      // 统一搜索框（DESIGN.md §11.1.2）：白底细边框单层 + 框内放大镜 + 14px hint
      child: SearchBox(
        controller: _keywordCtrl,
        focusNode: _searchFocus,
        hint: '搜菜名、食材',
        customCursor: true,
        onSubmitted: (_) => _reload(),
      ),
    );
  }

  Widget _buildSortBar(AppTokens t) {
    final filtered = _selectedTagId != null || _selectedCuisineId != null;
    return SizedBox(
      height: 44,
      child: ListView(
        scrollDirection: Axis.horizontal,
        padding: const EdgeInsets.symmetric(horizontal: AppTokens.sp16, vertical: AppTokens.sp6),
        children: [
          _sortChip(t, '家里够的优先', _SortMode.coverage),
          const SizedBox(width: AppTokens.sp8),
          _sortChip(t, '做得最多', _SortMode.cooked),
          const SizedBox(width: AppTokens.sp8),
          _sortChip(t, '最新', _SortMode.latest),
          const SizedBox(width: AppTokens.sp8),
          SelectChip(label: filtered ? '筛选 · 已选' : '筛选', selected: filtered, onTap: _openFilter),
          const SizedBox(width: AppTokens.sp12),
          Center(child: Text('$_total 道', style: t.textStyles.caption)),
        ],
      ),
    );
  }

  Widget _sortChip(AppTokens t, String label, _SortMode mode) {
    final selected = _sort == mode;
    return SelectChip(
      label: label,
      selected: selected,
      onTap: () {
        if (selected) return;
        setState(() => _sort = mode);
        _reload();
      },
    );
  }
}
