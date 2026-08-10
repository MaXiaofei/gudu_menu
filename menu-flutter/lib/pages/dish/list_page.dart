import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../core/image_helper.dart';
import '../../core/app_theme.dart';
import '../../models/dish.dart';
import '../../services/dish_service.dart';
import '../../services/ingredient_service.dart';
import '../../services/menu_service.dart';
import '../../widgets/action_bar.dart';
import '../../widgets/loading_empty.dart';

/// 排序：cooked=做过最多；latest=最新。
enum _SortMode { cooked, latest }

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
  bool _hasText = false;

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

  Future<List<Dish>> _fetch(int pageNum) async {
    try {
      final r = await DishService.search(
        keyword: _keywordCtrl.text.trim().isEmpty ? null : _keywordCtrl.text.trim(),
        tagIds: _selectedTagId == null ? null : [_selectedTagId!],
        cuisineIds: _selectedCuisineId == null ? null : [_selectedCuisineId!],
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

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    return Scaffold(
      backgroundColor: t.bg,
      // DESIGN.md §13：Tab 主页无标题（不放「菜谱」），顶部用 ActionBar。
      // 菜谱页无操作，ActionBar() 不传 action → 返回 SizedBox.shrink。
      body: SafeArea(
        bottom: false,
        child: Column(
          children: [
            // 顶部：picker 模式（食集「+ 加菜」push 进入）显示返回箭头；Tab 主页用 ActionBar
            if (widget.selectForMenuId != null)
              Padding(
                padding: const EdgeInsets.fromLTRB(16, 8, 16, 0),
                child: Row(
                  children: [
                    GestureDetector(
                      onTap: () => context.pop(),
                      child: Text('‹',
                          style: TextStyle(
                              fontSize: 22,
                              color: t.title,
                              fontWeight: FontWeight.w800)),
                    ),
                    const Spacer(),
                  ],
                ),
              )
            else
              // 写菜谱入口已迁到「我的」Tab（§16）；菜谱页无操作
              const ActionBar(),
            if (widget.selectForMenuId != null) _buildSelectHint(t),
            _buildSearchBox(t),
            if (_tags.isNotEmpty) _buildTagBar(t),
            if (_cuisines.isNotEmpty) _buildCuisineBar(t),
            _buildSortBar(t),
            Expanded(
              child: _firstLoading
                  ? const LoadingView()
                  : RefreshIndicator(
                      color: t.primary,
                      onRefresh: _reload,
                      child: _dishes.isEmpty
                          ? ListView(
                              children: const [SizedBox(height: 200), EmptyView(text: '暂无菜品')])
                          : ListView.builder(
                              controller: _scroll,
                              padding: const EdgeInsets.symmetric(horizontal: AppTokens.sp12),
                              itemCount: _dishes.length + 1,
                              itemBuilder: (_, i) {
                                if (i == _dishes.length) {
                                  return Padding(
                                    padding: const EdgeInsets.all(16),
                                    child: Center(
                                      child: Text(
                                        _hasMore ? '上拉加载更多' : '没有更多了',
                                        style: t.textStyles.caption,
                                      ),
                                    ),
                                  );
                                }
                                final dish = _dishes[i];
                                return _DishCard(
                                  dish: dish,
                                  onTapOverride: widget.selectForMenuId != null
                                      ? () => _addToMenu(dish)
                                      : null,
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

  /// 选择模式提示条（从食集详情「+ 加菜」进来时显示）。
  Widget _buildSelectHint(AppTokens t) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(14, AppTokens.sp4, 14, 0),
      child: Container(
        width: double.infinity,
        padding: const EdgeInsets.symmetric(
            horizontal: AppTokens.sp12, vertical: 8),
        decoration: BoxDecoration(
          color: t.highlight,
          borderRadius: BorderRadius.circular(AppTokens.rSm),
          border: Border.all(color: t.primarySoft),
        ),
        child: Text('选择要加入的菜',
            style: t.textStyles.sm.copyWith(color: t.title)),
      ),
    );
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

  /// 搜索框：白底 + 1.5px 主色描边 + 12px圆角。
  /// 搜索中态：深棕文字 12px + 橙色闪烁竖线光标 + 灰色 ✕ 清除。
  /// （原型 cookbook-search.html 行16-23）
  Widget _buildSearchBox(AppTokens t) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(14, AppTokens.sp4, 14, AppTokens.sp8),
      child: Container(
        // 浅色细边框圆角框（柔和输入区域，不是橙色粗边框胶囊）
        decoration: BoxDecoration(
          color: t.card,
          borderRadius: BorderRadius.circular(AppTokens.rMd),
          border: Border.all(color: t.border, width: 1),
        ),
        padding: const EdgeInsets.symmetric(horizontal: AppTokens.sp12, vertical: 9),
        child: Row(
          children: [
            // 搜索文字（深棕 12px）+ 闪烁竖线光标
            Expanded(
              child: Row(
                children: [
                  Flexible(
                    child: TextField(
                      controller: _keywordCtrl,
                      focusNode: _searchFocus,
                      style: t.textStyles.sm.copyWith(color: t.title),
                      // 隐藏系统光标，用自定义闪烁竖线代替
                      showCursor: false,
                      decoration: InputDecoration(
                        isCollapsed: true,
                        border: InputBorder.none,
                        hintText: '搜菜名',
                        hintStyle: t.textStyles.caption,
                        contentPadding: EdgeInsets.zero,
                      ),
                      onChanged: (v) => setState(() => _hasText = v.isNotEmpty),
                      onSubmitted: (_) => _reload(),
                    ),
                  ),
                  // 橙色闪烁竖线光标（原型 gap:8px 后紧跟竖线）
                  if (_searchFocus.hasFocus && _hasText) ...[
                    const SizedBox(width: 8),
                    const _BlinkingCursor(),
                  ],
                ],
              ),
            ),
            // ✕ 清除按钮（原型：11px 文字 ✕）
            if (_hasText)
              GestureDetector(
                onTap: () {
                  _keywordCtrl.clear();
                  setState(() => _hasText = false);
                  _reload();
                },
                child: Padding(
                  padding: const EdgeInsets.only(left: AppTokens.sp8),
                  child: Text('✕', style: t.textStyles.xs),
                ),
              ),
          ],
        ),
      ),
    );
  }

  /// 分类标签行：全部 + tag 横排横向滚动，选中深色高亮（原型行26-32）。
  Widget _buildTagBar(AppTokens t) {
    return _dictBar(t, _tags, _selectedTagId, (id) {
      setState(() => _selectedTagId = id);
      _reload();
    });
  }

  /// 菜系标签行：全部 + cuisine 横排，与分类同款样式（§16，参考下厨房 APP 筛选栏）。
  Widget _buildCuisineBar(AppTokens t) {
    return _dictBar(t, _cuisines, _selectedCuisineId, (id) {
      setState(() => _selectedCuisineId = id);
      _reload();
    });
  }

  /// 筛选标签行共用：首项「全部」+ 字典 chips，横向滚动，选中深色高亮。
  Widget _dictBar(
      AppTokens t, List<DictItem> dict, int? selectedId, ValueChanged<int?> onSelect) {
    return SizedBox(
      height: 36,
      child: ListView(
        scrollDirection: Axis.horizontal,
        padding: const EdgeInsets.fromLTRB(
            AppTokens.sp12, 0, AppTokens.sp12, AppTokens.sp8),
        children: [
          _dictChip(t, null, '全部', selectedId == null, () => onSelect(null)),
          ...dict.map((item) => Padding(
                padding: const EdgeInsets.only(left: 6),
                child: _dictChip(
                    t, item.id, item.name, selectedId == item.id, () => onSelect(item.id)),
              )),
        ],
      ),
    );
  }

  Widget _dictChip(AppTokens t, int? id, String name, bool selected, VoidCallback onTap) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
        decoration: BoxDecoration(
          color: selected ? t.title : t.card,
          borderRadius: BorderRadius.circular(AppTokens.rSm),
          border: selected ? null : Border.all(color: t.border),
        ),
        child: Text(
          name,
          style: t.textStyles.sectionLabel.copyWith(
            color: selected ? Colors.white : t.body,
          ),
        ),
      ),
    );
  }

  Widget _buildSortBar(AppTokens t) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(
          AppTokens.sp12, 0, AppTokens.sp12, AppTokens.sp4),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text('$_total 道', style: t.textStyles.xs),
          // 排序选项（下厨房式：横排可切换，选中深色高亮）
          Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              _sortChip(t, '最新', _SortMode.latest),
              const SizedBox(width: 6),
              _sortChip(t, '做过最多', _SortMode.cooked),
            ],
          ),
        ],
      ),
    );
  }

  Widget _sortChip(AppTokens t, String label, _SortMode mode) {
    final selected = _sort == mode;
    return InkWell(
      onTap: () {
        if (selected) return;
        setState(() => _sort = mode);
        _reload();
      },
      borderRadius: BorderRadius.circular(AppTokens.rPill),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
        decoration: BoxDecoration(
          color: selected ? t.title : Colors.transparent,
          borderRadius: BorderRadius.circular(AppTokens.rPill),
        ),
        child: Text(
          label,
          style: t.textStyles.xs.copyWith(
            color: selected ? t.card : t.caption,
            fontWeight: FontWeight.w800,
          ),
        ),
      ),
    );
  }
}

/// 列表卡片：白底 + 1px描边 + 12px圆角 + margin间距 + 横向布局
/// （44px缩略图 + 菜名 + 做过N次·时间）（原型行40-71）。
class _DishCard extends StatelessWidget {
  final Dish dish;
  /// 选择模式：非空时点击走此回调（加菜），否则默认进菜谱详情。
  final VoidCallback? onTapOverride;
  const _DishCard({required this.dish, this.onTapOverride});

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    final hasCover = dish.coverUrl != null && dish.coverUrl!.isNotEmpty;
    final thumbUrl = hasCover
        ? ImageHelper.toThumbnail(ImageHelper.toAbsolute(dish.coverUrl!))
        : null;

    return GestureDetector(
      onTap: onTapOverride ?? () => context.push('/dish/${dish.id}'),
      child: Container(
      margin: const EdgeInsets.only(bottom: 7),
      padding: const EdgeInsets.all(10),
      decoration: BoxDecoration(
        color: t.card,
        borderRadius: BorderRadius.circular(AppTokens.rMd),
        border: Border.all(color: t.border),
      ),
      child: Row(
        children: [
          // 44px 缩略图，11px 圆角（原型 border-radius:11px）
          ClipRRect(
            borderRadius: BorderRadius.circular(11),
            child: SizedBox(
              width: 44,
              height: 44,
              child: thumbUrl != null
                  ? Image.network(
                      thumbUrl,
                      fit: BoxFit.cover,
                      errorBuilder: (_, __, ___) => _placeholder(t, dish.name),
                      loadingBuilder: (_, child, progress) =>
                          progress == null ? child : _placeholder(t, dish.name),
                    )
                  : _placeholder(t, dish.name),
            ),
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(dish.name, style: t.textStyles.cardTitle),
                const SizedBox(height: 1),
                Text(
                  [
                    dish.cookedCount > 0 ? '做过 ${dish.cookedCount} 次' : '没做过',
                    '${dish.cookTime ?? 0} 分',
                  ].join(' · '),
                  style: t.textStyles.tiny,
                ),
              ],
            ),
          ),
        ],
      ),
      ),
    );
  }

  /// 无封面图时的占位：奶油色底 + 菜名首字（DESIGN.md §10.4，不用 emoji 顶替图片）。
  Widget _placeholder(AppTokens t, String name) {
    final initial = name.trim().isNotEmpty ? name.trim().characters.first : '菜';
    return Container(
      color: t.secondary, // 原型 #FBF0DD，走 token 不裸色值（DESIGN.md §11.2）
      alignment: Alignment.center,
      child: Text(
        initial,
        style: t.textStyles.lg.copyWith(
          color: t.title.withAlpha(115), // ≈ 0.45 透明度
          fontWeight: FontWeight.w600,
        ),
      ),
    );
  }
}

/// 搜索框闪烁竖线光标：宽1 高14 橙色，AnimatedOpacity 周期闪烁。
/// 对应原型 cookbook-search.html 的 `animation:blink 1s infinite` 竖线。
class _BlinkingCursor extends StatefulWidget {
  const _BlinkingCursor();

  @override
  State<_BlinkingCursor> createState() => _BlinkingCursorState();
}

class _BlinkingCursorState extends State<_BlinkingCursor>
    with SingleTickerProviderStateMixin {
  late final AnimationController _ctrl;
  late final Animation<double> _opacity;

  @override
  void initState() {
    super.initState();
    _ctrl = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 500),
    )..repeat(reverse: true);
    _opacity = Tween(begin: 1.0, end: 0.0).animate(_ctrl);
  }

  @override
  void dispose() {
    _ctrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    return AnimatedBuilder(
      animation: _opacity,
      builder: (_, __) => Opacity(
        opacity: _opacity.value,
        child: Container(
          width: 1,
          height: 14,
          color: t.primary, // DESIGN.md §11.2 走 token，不裸色值
        ),
      ),
    );
  }
}
