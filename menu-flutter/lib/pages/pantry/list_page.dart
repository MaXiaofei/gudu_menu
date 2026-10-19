import 'dart:async';

import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../core/app_theme.dart';
import '../../services/pantry_service.dart';
import '../../widgets/error_view.dart';
import '../../widgets/illustration.dart';
import '../../widgets/ingredient_icon.dart';
import '../../widgets/loading_empty.dart';
import '../../widgets/search_box.dart';
import '../../widgets/select_chip.dart';
import '../../widgets/skeleton.dart';
import '../../widgets/stat_grid.dart';
import '../../widgets/tone_badge.dart';
import '../../widgets/me_avatar_button.dart';
import 'pantry_widgets.dart';

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

  /// 先吃这些（3 天内到期，原型 Pantry）。
  List<ExpiringItem> _expiring = const [];

  /// 顶部四格统计（/pantry/summary，旧后端本地聚合降级）。
  PantrySummary? _summary;

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
        // 统计与临期各自失败不影响列表
        final extra = await Future.wait<Object?>([
          PantryService.summary().then<Object?>((v) => v, onError: (_) => null),
          PantryService.listExpiring(days: 3).then<Object?>((v) => v, onError: (_) => null),
        ]);
        if (!mounted) return;
        setState(() {
          _summary = extra[0] as PantrySummary?;
          _expiring = ((extra[1] as List<ExpiringItem>?) ?? const [])
              .where((e) => e.level != StockLevel.none)
              .toList();
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
      if (!mounted) return;
      setState(() => _loadingMore[level] = false);
      ScaffoldMessenger.of(context)
          .showSnackBar(const SnackBar(content: Text('加载更多失败，请重试')));
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
      if (!mounted) return;
      setState(() => _searchLoading = false);
      ScaffoldMessenger.of(context)
          .showSnackBar(const SnackBar(content: Text('加载更多失败，请重试')));
    }
  }

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    final ts = t.textStyles;
    return Scaffold(
      floatingActionButton: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          FloatingActionButton.extended(
            key: const Key('pantry-scan'),
            heroTag: 'pantry-scan',
            backgroundColor: t.card,
            foregroundColor: t.primaryDeep,
            shape: StadiumBorder(side: BorderSide(color: t.border)),
            onPressed: () async {
              await context.push('/scan');
              _load();
            },
            icon: const Icon(Icons.photo_camera_outlined),
            label: Text('拍照', style: ts.cardTitle.copyWith(color: t.primaryDeep)),
          ),
          const SizedBox(width: AppTokens.sp10),
          FloatingActionButton.extended(
        heroTag: 'pantry-add',
        backgroundColor: t.primaryDeep,
        foregroundColor: t.card,
        shape: const StadiumBorder(),
        onPressed: () async {
          await context.push('/pantry/add');
          _load();
        },
        icon: const Icon(Icons.add_rounded),
        label: Text('入库', style: ts.cardTitle.copyWith(color: t.card)),
      ),
        ],
      ),
      body: SafeArea(
        bottom: false,
        child: Column(
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(AppTokens.sp16, AppTokens.sp12, AppTokens.sp16, 0),
              child: Row(
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text('我家余量', style: ts.h2),
                        if (_grouped != null) Text('共 ${_statTotal()} 样', style: ts.caption),
                      ],
                    ),
                  ),
                  OutlinedButton.icon(
                    style: OutlinedButton.styleFrom(
                      minimumSize: const Size(0, 40),
                      padding: const EdgeInsets.symmetric(horizontal: AppTokens.sp12),
                      shape: const StadiumBorder(),
                    ),
                    onPressed: () async {
                      await context.push('/shopping');
                      _load(); // 采购勾选会回写库存，返回后刷新
                    },
                    icon: const Icon(Icons.shopping_cart_outlined, size: 18),
                    label: const Text('去采购'),
                  ),
                  const SizedBox(width: AppTokens.sp8),
                  const MeAvatarButton(),
                ],
              ),
            ),
            _buildSearchBox(t),
            Expanded(
              child: _loading
                  ? const SkeletonList(count: 6, thumb: 44)
                  : _error != null
                      ? ErrorView(text: '加载失败', onRetry: _load)
                      : RefreshIndicator(
                          onRefresh: _load,
                          child: _searching
                              ? _buildSearchBody(t)
                              : _totalCount == 0
                                  ? ListView(children: [
                                      if (_grouped != null)
                                        Padding(
                                          padding: const EdgeInsets.all(AppTokens.sp16),
                                          child: _buildFilterChips(t, _grouped!),
                                        ),
                                      const SizedBox(height: 40),
                                      EmptyView(
                                        text: '冰箱里还是空的',
                                        subtitle: '记下家里有什么，智荐才知道先帮你用掉哪些',
                                        illustration: const StateIllustration.fridge(),
                                        actionLabel: '手动添加',
                                        onAction: () async {
                                          await context.push('/pantry/add');
                                          _load();
                                        },
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

  int _statTotal() {
    final s = _summary;
    return s != null && s.total > 0 ? s.total : _totalCount;
  }

  /// 顶部四格：充足 / 不足 / 用完 / 3 天内到期（点前三格切筛选）。
  Widget _buildStats(AppTokens t) {
    final g = _grouped!;
    final s = _summary;
    final useSummary = s != null && s.total > 0;
    return StatGrid(items: [
      StatItem('${useSummary ? s.enough : g.enough}', '充足',
          tone: StatTone.success, onTap: () => setState(() => _filter = 'enough')),
      StatItem('${useSummary ? s.low : g.low}', '不足',
          tone: StatTone.warning, onTap: () => setState(() => _filter = 'low')),
      StatItem('${useSummary ? s.out : g.none}', '用完',
          tone: StatTone.error, onTap: () => setState(() => _filter = 'none')),
      StatItem('${useSummary ? s.expiring3d : _expiring.length}', '3 天内到期', tone: StatTone.dark),
    ]);
  }

  /// 先吃这些：临期食材行（红/黄到期徽章）。
  Widget _buildExpiring(AppTokens t) {
    final ts = t.textStyles;
    return Container(
      margin: const EdgeInsets.only(top: AppTokens.sp16),
      padding: const EdgeInsets.all(AppTokens.sp12),
      decoration: BoxDecoration(
        color: t.card,
        borderRadius: BorderRadius.circular(AppTokens.rLg),
        border: Border.all(color: AppTokens.errorBg),
        boxShadow: t.elevationSm,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(children: [
            Container(width: 6, height: 6, decoration: const BoxDecoration(color: AppTokens.error, shape: BoxShape.circle)),
            const SizedBox(width: AppTokens.sp6),
            Text('先吃这些', style: ts.sectionLabel.copyWith(color: AppTokens.errorText)),
            const Spacer(),
            GestureDetector(
              onTap: () => context.push('/ai-recommend'),
              child: Text('看看能做什么 ›', style: ts.caption.copyWith(color: t.primaryDeep, fontWeight: FontWeight.w700)),
            ),
          ]),
          const SizedBox(height: AppTokens.sp4),
          for (final e in _expiring.take(4))
            InkWell(
              onTap: () async {
                await context.push('/pantry/${e.ingredientId}');
                _load();
              },
              child: Padding(
                padding: const EdgeInsets.symmetric(vertical: AppTokens.sp6),
                child: Row(children: [
                  IngredientIcon(name: e.ingredientName, size: 40),
                  const SizedBox(width: AppTokens.sp12),
                  Expanded(child: Text(e.ingredientName, style: ts.cardTitle.copyWith(color: t.title))),
                  ToneBadge.expiry(e.daysLeft) ?? ToneBadge(e.daysText),
                ]),
              ),
            ),
        ],
      ),
    );
  }

  /// 搜索框：统一 SearchBox（⌕ 放大镜 + 输入 + ✕ 清除，输入即搜 300ms 防抖，§11.1.2）。
  Widget _buildSearchBox(AppTokens t) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(AppTokens.sp16, AppTokens.sp12, AppTokens.sp16, 0),
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
      padding: const EdgeInsets.fromLTRB(AppTokens.sp16, AppTokens.sp12, AppTokens.sp16, 96),
      children: [
        _buildStats(t),
        if (_expiring.isNotEmpty && f == 'all') _buildExpiring(t),
        const SizedBox(height: AppTokens.sp16),
        // 筛选条（全部 + 用完/不足/充足 三档）
        _buildFilterChips(t, g),
        const SizedBox(height: AppTokens.sp4),
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
    return Wrap(
      spacing: 0,
      children: [
        _chip(t, 'all', '全部', _totalCount, null),
        const SizedBox(width: 6),
        _chip(t, 'none', '用完', g.none, AppTokens.errorText),
        const SizedBox(width: 6),
        _chip(t, 'low', '不足', g.low, AppTokens.warningText),
        const SizedBox(width: 6),
        _chip(t, 'enough', '充足', g.enough, AppTokens.successText),
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
      padding: const EdgeInsets.only(top: AppTokens.sp16, bottom: AppTokens.sp8),
      child: Row(children: [
        Container(width: 6, height: 6, decoration: BoxDecoration(color: color, shape: BoxShape.circle)),
        const SizedBox(width: AppTokens.sp6),
        Text(text, style: t.textStyles.sectionLabel.copyWith(letterSpacing: 1)),
      ]),
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

  Widget _buildLoadMore(AppTokens t, String level, Color color, int remain) => LoadMorePill(
        remain: remain,
        loading: _loadingMore[level]!,
        color: color,
        onTap: () => _loadMore(level),
      );

  Widget _buildSearchLoadMore(AppTokens t, int remain) =>
      LoadMorePill(remain: remain, loading: _searchLoading, onTap: _loadMoreSearch);

  Widget _buildRow(AppTokens t, PantryGroupedItem item) => PantryRow(
        item: item,
        onTap: () async {
          await context.push('/pantry/${item.ingredientId}');
          _load(); // 详情页改档位后刷新
        },
        onLevel: (lv) async {
          try {
            await PantryService.setLevel(item.ingredientId, lv);
            _load();
          } catch (_) {
            // request 已 toast
          }
        },
      );
}
