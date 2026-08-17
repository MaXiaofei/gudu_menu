import 'dart:async';

import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../core/app_theme.dart';
import '../../services/pantry_service.dart';
import '../../widgets/action_bar.dart';
import '../../widgets/error_view.dart';
import '../../widgets/initial_avatar.dart';
import '../../widgets/loading_empty.dart';
import '../../widgets/select_chip.dart';
import '../../widgets/search_box.dart';
import '../../widgets/status_chip.dart';

/// 库存主页（分页版，对齐 44829 批次 pantry-page.html 定稿）。
///
/// 结构：顶部搜索框（⌕ 输入即搜，300ms 防抖，结果平铺按档位排序 + 分页）→
/// 筛选条（全部/用完/不足/充足，带计数）→ 按 用完→不足→充足 三组，每组独立分页
/// （每页 10 条，组尾「加载更多 · 还有 N 项」）→ 每行点行进食材详情页盘点 →
/// 右上角「入库」（手动入库）+「去采购」（采购闭环）。
class PantryListPage extends StatefulWidget {
  const PantryListPage({super.key});

  @override
  State<PantryListPage> createState() => _PantryListPageState();
}

class _PantryListPageState extends State<PantryListPage> {
  /// 主题 token 缓存。
  AppTokens get _t => AppTokens.of(context);

  static const _pageSize = 10; // DESIGN.md §12.2：列表分页统一 10 条/页

  final _searchCtrl = TextEditingController();
  final _searchFocus = FocusNode();
  Timer? _debounce;

  /// 筛选档：all / none / low / enough（缺省「全部」）。
  /// nullable 兜底：热重载时旧 State 实例没有本字段，避免 null 崩溃。
  String? _filter = 'all';

  /// 生效中的筛选档（null 兜底「全部」）。
  String get _activeFilter => _filter ?? 'all';

  /// 汇总（三档总数：chips 计数 / 分组标题 / 「找到 N 个」，不随分页变化）。
  PantryGrouped? _grouped;
  bool _loading = true;
  String? _error;

  /// 三档各自已加载的列表 / 页码 / 加载中（「全部」页三组独立分页，单档 tab 共用同路径）。
  final Map<String, List<PantryGroupedItem>> _items = {
    StockLevel.none: [],
    StockLevel.low: [],
    StockLevel.enough: [],
  };
  final Map<String, int> _page = {
    StockLevel.none: 1,
    StockLevel.low: 1,
    StockLevel.enough: 1,
  };
  final Map<String, bool> _loadingMore = {
    StockLevel.none: false,
    StockLevel.low: false,
    StockLevel.enough: false,
  };

  /// 搜索：关键词 + 平铺结果（服务端已按 用完→不足→充足 排序）+ 分页。
  String _query = '';
  List<PantryGroupedItem> _searchItems = [];
  int _searchPage = 1;
  bool _searchLoading = false;

  bool get _searching => _query.isNotEmpty;

  /// 库存总数（三档相加）。
  int get _totalCount =>
      (_grouped?.none ?? 0) + (_grouped?.low ?? 0) + (_grouped?.enough ?? 0);

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _debounce?.cancel();
    _searchCtrl.dispose();
    _searchFocus.dispose();
    super.dispose();
  }

  /// 初始 / 下拉刷新 / 详情返回 / 搜索词变化：重置拉第 1 页。
  /// 分组态三档第 1 页并行拉取（tab 切换即时）；搜索态按关键词拉第 1 页。
  Future<void> _load() async {
    setState(() {
      _loading = _grouped == null;
      _error = null;
    });
    try {
      if (_searching) {
        final r = await PantryService.listGroupedPage(
          keyword: _query,
          pageNum: 1,
          pageSize: _pageSize,
        );
        if (!mounted) return;
        setState(() {
          _grouped = r;
          _searchItems = r.items;
          _searchPage = 1;
          _loading = false;
        });
      } else {
        final results = await Future.wait([
          PantryService.listGroupedPage(level: StockLevel.none, pageNum: 1, pageSize: _pageSize),
          PantryService.listGroupedPage(level: StockLevel.low, pageNum: 1, pageSize: _pageSize),
          PantryService.listGroupedPage(level: StockLevel.enough, pageNum: 1, pageSize: _pageSize),
        ]);
        if (!mounted) return;
        setState(() {
          _grouped = results[0];
          _items[StockLevel.none]!..clear()..addAll(results[0].items);
          _items[StockLevel.low]!..clear()..addAll(results[1].items);
          _items[StockLevel.enough]!..clear()..addAll(results[2].items);
          _page.updateAll((key, value) => 1);
          _loading = false;
        });
      }
    } catch (e) {
      // 静默，request 已 toast
      if (!mounted) return;
      setState(() {
        _error = '$e';
        _loading = false;
      });
    }
  }

  /// 输入即搜：300ms 防抖后拉第 1 页（对齐原型交互）。
  void _onQueryChanged(String v) {
    final q = v.trim();
    setState(() => _query = q);
    _debounce?.cancel();
    _debounce = Timer(const Duration(milliseconds: 300), _load);
  }

  /// ✕ 清空搜索，恢复三组分页视图。

  /// 某档加载下一页（组尾「加载更多」）。
  Future<void> _loadMore(String level) async {
    if (_loadingMore[level]!) return;
    setState(() => _loadingMore[level] = true);
    try {
      final r = await PantryService.listGroupedPage(
        level: level,
        pageNum: _page[level]! + 1,
        pageSize: _pageSize,
      );
      if (!mounted) return;
      setState(() {
        _page[level] = _page[level]! + 1;
        _items[level]!.addAll(r.items);
        _loadingMore[level] = false;
      });
    } catch (_) {
      if (mounted) setState(() => _loadingMore[level] = false);
    }
  }

  /// 搜索结果加载下一页。
  Future<void> _loadMoreSearch() async {
    if (_searchLoading) return;
    setState(() => _searchLoading = true);
    try {
      final r = await PantryService.listGroupedPage(
        keyword: _query,
        pageNum: _searchPage + 1,
        pageSize: _pageSize,
      );
      if (!mounted) return;
      setState(() {
        _searchPage++;
        _searchItems.addAll(r.items);
        _searchLoading = false;
      });
    } catch (_) {
      if (mounted) setState(() => _searchLoading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    return Scaffold(
      // DESIGN.md §13：Tab 主页无标题，顶部用 ActionBar 放操作（批量添加图标右对齐）。
      // 状态栏由 ActionBar 内置 AnnotatedRegion 控制（奶油底+深色字）。
      body: SafeArea(
        bottom: false,
        child: Column(
          children: [
            ActionBar(
              action: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  // 「入库」：统一入库弹窗（采购/朋友送/旧库存补登），放「去采购」左侧
                  _topButton(
                    label: '入库',
                    filled: false,
                    onTap: () async {
                      await context.push('/pantry/add');
                      _load(); // 手动添加后刷新
                    },
                  ),
                  const SizedBox(width: AppTokens.sp8),
                  // 「去采购」：主操作（采购闭环），右对齐独占一行（原型定稿）
                  _topButton(
                    label: '去采购',
                    filled: true,
                    onTap: () async {
                      await context.push('/shopping');
                      _load(); // 采购勾选会回写库存，返回后刷新
                    },
                  ),
                ],
              ),
            ),
            _buildSearchBox(t),
            Expanded(
              child: _loading
                  ? const LoadingView()
                  : _error != null
                      ? ErrorView(text: '加载失败', onRetry: _load) // §14.1：错误态用 ErrorView
                      : RefreshIndicator(
                          onRefresh: _load,
                          child: _searching
                              ? _buildSearchBody(t)
                              : _totalCount == 0
                                  ? Column(children: [
                                      // 空数据也展示档位筛选条（计数 0，结构可见）
                                      if (_grouped != null) _buildFilterChips(t, _grouped!),
                                      Expanded(
                                        child: ListView(
                                          children: const [
                                            SizedBox(height: 120),
                                            Center(child: EmptyView(text: '暂无库存')),
                                          ],
                                        ),
                                      ),
                                    ])
                                  : _buildBody(t),
                        ),
            ),
          ],
        ),
      ),
    );
  }

  /// 顶栏按钮（2026-08-17 与筛选 chip 同款语言）：次按钮白底细边框深棕字、
  /// 主按钮深棕实底白字，8px 圆角，11px/w700。
  Widget _topButton({
    required String label,
    required bool filled,
    required VoidCallback onTap,
  }) {
    final t = AppTokens.of(context);
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: AppTokens.sp12, vertical: 5),
        decoration: BoxDecoration(
          color: filled ? t.title : t.card,
          border: filled ? null : Border.all(color: t.border),
          borderRadius: BorderRadius.circular(AppTokens.rSm),
        ),
        child: Text(
          label,
          style: t.textStyles.sectionLabel.copyWith(
            color: filled ? Colors.white : t.body,
          ),
        ),
      ),
    );
  }

  /// 搜索框：统一 SearchBox（⌕ 放大镜 + 输入 + ✕ 清除，输入即搜 300ms 防抖，§11.1.2）。
  Widget _buildSearchBox(AppTokens t) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(14, AppTokens.sp10, 14, 0),
      child: SearchBox(
        controller: _searchCtrl,
        focusNode: _searchFocus,
        hint: '搜库存',
        onChanged: _onQueryChanged,
      ),
    );
  }

  /// 分组视图：筛选条 + 三组（按档筛选后只显对应组），组内独立分页。
  Widget _buildBody(AppTokens t) {
    final g = _grouped!;
    final f = _activeFilter;
    final showNone = f == 'all' || f == 'none';
    final showLow = f == 'all' || f == 'low';
    final showEnough = f == 'all' || f == 'enough';
    return ListView(
      padding: const EdgeInsets.fromLTRB(12, 8, 12, 80),
      children: [
        // 筛选条（全部 + 用完/不足/充足 三档，替代三色汇总条，原型 pantry-page 定稿）
        _buildFilterChips(t, g),
        const SizedBox(height: 4),
        // 分组列表：标题计数用汇总总数（不随加载变化），组尾「加载更多」
        if (showNone && g.none > 0)
          _buildSection(t, StockLevel.none, '用完', AppTokens.error, g.none),
        if (showLow && g.low > 0)
          _buildSection(t, StockLevel.low, '不足', AppTokens.warning, g.low),
        if (showEnough && g.enough > 0)
          _buildSection(t, StockLevel.enough, '充足', AppTokens.success, g.enough),
      ],
    );
  }

  /// 搜索视图：结果平铺（服务端按 用完→不足→充足 排序）+「找到 N 个」+ 底部加载更多。
  Widget _buildSearchBody(AppTokens t) {
    final g = _grouped!;
    final total = g.enough + g.low + g.none;
    final remain = total - _searchItems.length;
    return ListView(
      padding: const EdgeInsets.fromLTRB(12, 8, 12, 80),
      children: [
        Text(
          '找到 $total 个',
          style: t.textStyles.tiny.copyWith(
            color: t.caption,
            fontWeight: FontWeight.w800,
            letterSpacing: 1,
          ),
        ),
        const SizedBox(height: 8),
        if (_searchItems.isEmpty)
          Container(
            padding: const EdgeInsets.all(18),
            decoration: BoxDecoration(
              color: t.card,
              border: Border.all(color: t.border),
              borderRadius: BorderRadius.circular(AppTokens.rSm),
            ),
            child: Text('搜不到「$_query」',
                textAlign: TextAlign.center,
                style: t.textStyles.sm.copyWith(color: t.caption)),
          )
        else ...[
          ..._searchItems.map((i) => _buildRow(t, i)),
          if (remain > 0) _buildSearchLoadMore(t, remain),
        ],
      ],
    );
  }

  /// 筛选条：全部 / 缺 / 低 / 够（带计数）。选中实心，未选白底描边。
  Widget _buildFilterChips(AppTokens t, PantryGrouped g) {
    return Row(
      children: [
        _chip(t, 'all', '全部', _totalCount, null),
        const SizedBox(width: 6),
        _chip(t, 'none', '用完', g.none, AppTokens.error),
        const SizedBox(width: 6),
        _chip(t, 'low', '不足', g.low, AppTokens.warning),
        const SizedBox(width: 6),
        _chip(t, 'enough', '充足', g.enough, AppTokens.success),
      ],
    );
  }

  /// 单个筛选 chip（SelectChip 统一样式）：选中深棕实底白字，未选白底描边、
  /// 文字保留三色语义（缺=红/低=黄/够=绿），2026-08-17 由胶囊三色选中改为统一筛选样式。
  Widget _chip(AppTokens t, String key, String label, int count, Color? color) {
    final selected = _activeFilter == key;
    return SelectChip(
      label: '$label $count',
      selected: selected,
      semanticColor: color,
      onTap: () => setState(() => _filter = key),
    );
  }

  Widget _buildSectionTitle(AppTokens t, String text, Color color) {
    return Padding(
      padding: const EdgeInsets.only(top: 10, bottom: 4),
      child: Text(text,
          style: t.textStyles.micro.copyWith(color: color, letterSpacing: 1)),
    );
  }

  /// 单档分组：标题（汇总总数）+ 已加载行 + 组尾「加载更多 · 还有 N 项」。
  Widget _buildSection(AppTokens t, String level, String label, Color color, int total) {
    final list = _items[level]!;
    final remain = total - list.length;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        _buildSectionTitle(t, '$label · $total', color),
        ...list.map((i) => _buildRow(t, i)),
        if (remain > 0) _buildLoadMore(t, level, color, remain),
      ],
    );
  }

  /// 组尾加载更多胶囊：白底 + 本组色描边/文字，加载中禁用。
  Widget _buildLoadMore(AppTokens t, String level, Color color, int remain) {
    final loading = _loadingMore[level]!;
    return Center(
      child: GestureDetector(
        onTap: loading ? null : () => _loadMore(level),
        child: Container(
          margin: const EdgeInsets.symmetric(vertical: 8),
          padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 6),
          decoration: BoxDecoration(
            color: t.card,
            border: Border.all(color: color.withAlpha(60)),
            borderRadius: BorderRadius.circular(AppTokens.rPill),
          ),
          child: Text(
            loading ? '加载中…' : '加载更多 · 还有 $remain 项',
            style: t.textStyles.tiny.copyWith(
              color: loading ? t.caption : color,
              fontWeight: FontWeight.w800,
            ),
          ),
        ),
      ),
    );
  }

  /// 搜索底部加载更多（与分组胶囊同款，灰描边）。
  Widget _buildSearchLoadMore(AppTokens t, int remain) {
    return Center(
      child: GestureDetector(
        onTap: _searchLoading ? null : _loadMoreSearch,
        child: Container(
          margin: const EdgeInsets.symmetric(vertical: 8),
          padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 6),
          decoration: BoxDecoration(
            color: t.card,
            border: Border.all(color: t.border),
            borderRadius: BorderRadius.circular(AppTokens.rPill),
          ),
          child: Text(
            _searchLoading ? '加载中…' : '加载更多 · 还有 $remain 项',
            style: t.textStyles.tiny.copyWith(
              color: _searchLoading ? t.caption : t.caption,
              fontWeight: FontWeight.w800,
            ),
          ),
        ),
      ),
    );
  }

  /// 单行：食材名 + 上次变动 + 档位文字 + 进入箭头（点行进详情改档位）。
  Widget _buildRow(AppTokens t, PantryGroupedItem item) {
    final color = stockColor(item.level);
    final hasSource = item.sourceLabel.isNotEmpty;
    return Card(
      margin: const EdgeInsets.only(bottom: 4),
      elevation: 0,
      color: t.card,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(AppTokens.rMd),
        side: BorderSide(color: color.withAlpha(40)),
      ),
      child: InkWell(
        borderRadius: BorderRadius.circular(AppTokens.rMd),
        onTap: () async {
          await context.push('/pantry/${item.ingredientId}');
          _load(); // 详情页改档位后刷新
        },
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
          child: Row(
            children: [
              // 食材前置缩略图：无图走首字色块占位（DESIGN.md §10.4）
              InitialAvatar(name: item.displayName, size: 40),
              const SizedBox(width: AppTokens.sp12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(item.displayName,
                        style: t.textStyles.cardTitle.copyWith(color: t.title)),
                    const SizedBox(height: 2),
                    Text(
                      hasSource
                          ? '${item.sourceLabel} ${item.sourceSub}'
                          : (item.level == 'NONE' ? '本来就没有' : '无变动记录'),
                      style: t.textStyles.tiny.copyWith(
                        color: hasSource && item.sourceLabel == '手动' ? _t.primary : t.caption,
                        fontWeight: hasSource && item.sourceLabel == '手动' ? FontWeight.w700 : FontWeight.normal,
                      ),
                    ),
                  ],
                ),
              ),
              Text(item.levelLabel,
                  style: t.textStyles.sm.copyWith(color: color, fontWeight: FontWeight.w800)),
              const SizedBox(width: 6),
              Icon(Icons.chevron_right, size: 18, color: t.caption),
            ],
          ),
        ),
      ),
    );
  }
}
