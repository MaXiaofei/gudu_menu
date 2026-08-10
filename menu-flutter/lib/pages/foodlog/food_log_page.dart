import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../core/app_theme.dart';
import '../../services/food_log_service.dart';
import '../../widgets/loading_empty.dart';
import '../../widgets/time_select.dart';

/// 食记（做菜日记）主页（对齐 dailylog.html 原型）。
///
/// 视图：日期组件（‹ 2026年7月 › + 月|年范围切换）+ 统计卡（顿饭/道菜/做饭天数/最常）
/// + Tab（时间轴 / 按菜汇总）。
/// 「月|年」= 统计范围切换（月=本月 / 年=全年），视图同构；
/// 选月份在时间控件弹层内（点胶囊展开 年→月 逐级选择）。
/// 单条时间轴点行进详情（评价/再做一次）。
class FoodLogPage extends StatefulWidget {
  const FoodLogPage({super.key});

  @override
  State<FoodLogPage> createState() => _FoodLogPageState();
}

class _FoodLogPageState extends State<FoodLogPage> {
  AppTokens get _t => AppTokens.of(context);

  int _year = DateTime.now().year;
  int _month = DateTime.now().month;
  bool _yearMode = false; // false=月视图 true=年视图

  // Tab：timeline / byDish
  String _tab = 'timeline';

  FoodLogMonth? _monthData;
  FoodLogByDish? _byDishData;
  bool _loading = true;

  // 时间轴分页（§12.2 默认每页 15 条）
  static const _pageSize = 10; // DESIGN.md §12.2（默认 10 条/页）
  final _scroll = ScrollController();
  int _page = 1;
  bool _hasMore = true;
  bool _loadingMore = false;

  @override
  void initState() {
    super.initState();
    _scroll.addListener(_onScroll);
    _load();
  }

  @override
  void dispose() {
    _scroll.dispose();
    super.dispose();
  }

  void _onScroll() {
    if (!_scroll.hasClients || _loadingMore || !_hasMore) return;
    final pos = _scroll.position;
    if (pos.pixels >= pos.maxScrollExtent - 120) _loadMore();
  }

  Future<void> _loadMore() async {
    if (_tab != 'timeline') return;
    setState(() => _loadingMore = true);
    try {
      final range = _yearMode ? 0 : _month;
      final next = await FoodLogService.month(_year, range,
          pageNum: _page + 1, pageSize: _pageSize);
      if (!mounted) return;
      setState(() {
        _monthData = FoodLogMonth(
          summary: next.summary,
          records: [...?_monthData?.records, ...next.records],
          total: next.total,
          size: next.size,
        );
        _page++;
        // §12.3 启发式：本页满页 → 可能还有下一页
        _hasMore = next.records.length == _pageSize;
      });
    } catch (_) {}
    if (mounted) setState(() => _loadingMore = false);
  }

  Future<void> _load() async {
    setState(() => _loading = _monthData == null && _byDishData == null);
    try {
      final range = _yearMode ? 0 : _month; // 年模式=全年范围
      _page = 1;
      _hasMore = true;
      if (_tab == 'byDish') {
        _byDishData = await FoodLogService.byDish(_year, range);
      } else {
        _monthData = await FoodLogService.month(_year, range,
            pageNum: 1, pageSize: _pageSize);
        _hasMore = (_monthData?.records.length ?? 0) == _pageSize;
      }
    } catch (_) {}
    if (mounted) setState(() => _loading = false);
  }

  void _switchMode(bool year) {
    if (_yearMode == year) return;
    setState(() => _yearMode = year);
    _load();
  }

  void _switchTab(String tab) {
    if (_tab == tab) return;
    setState(() => _tab = tab);
    _load();
  }

  void _prev() {
    setState(() {
      if (_yearMode) {
        _year--;
      } else {
        _month--;
        if (_month < 1) {
          _month = 12;
          _year--;
        }
      }
    });
    _load();
  }

  void _next() {
    setState(() {
      if (_yearMode) {
        _year++;
      } else {
        _month++;
        if (_month > 12) {
          _month = 1;
          _year++;
        }
      }
    });
    _load();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        bottom: false,
        child: Column(
          children: [
            // 顶部行：返回箭头（左）+ 日期组件 + 月|年 切换（右）
            // 返回与月份切换箭头分居左右，避免混淆（§13.3 BackHeader 箭头行）
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 0),
              child: Row(
                children: [
                  GestureDetector(
                    onTap: () => context.pop(),
                    child: Text('‹',
                        style: TextStyle(fontSize: 22, color: _t.title, fontWeight: FontWeight.w800)),
                  ),
                  const Spacer(),
                  _dateCapsule(),
                  const SizedBox(width: 8),
                  _modeSwitch(),
                ],
              ),
            ),
            // 统计卡
            _buildSummaryCard(),
            // Tab
            _buildTabBar(),
            // 内容
            Expanded(
              child: _loading ? const LoadingView() : _buildMonthView(),
            ),
          ],
        ),
      ),
    );
  }

  // ===== 顶部组件 =====

  Widget _dateCapsule() {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 3),
      decoration: BoxDecoration(
        color: _t.card,
        border: Border.all(color: _t.border),
        borderRadius: BorderRadius.circular(9),
      ),
      child: Row(mainAxisSize: MainAxisSize.min, children: [
        GestureDetector(
            onTap: _prev,
            child: Text('‹',
                style: TextStyle(color: _t.primary, fontWeight: FontWeight.w800, fontSize: 13))),
        // 统一时间选择胶囊：点击展开 年→月 逐级选择（年模式下选月即切回该月视图）
        TimeSelectCapsule(
          value: DateTime(_year, _month),
          granularity: TimeGranularity.month,
          labelBuilder: (v) => _yearMode ? '${v.year}年' : '${v.year}年${v.month}月',
          onChanged: (picked) {
            setState(() {
              _year = picked.year;
              _month = picked.month;
              _yearMode = false; // 选中具体月份 → 回到月视图
            });
            _load();
          },
        ),
        GestureDetector(
            onTap: _next,
            child: Text('›',
                style: TextStyle(color: _t.primary, fontWeight: FontWeight.w800, fontSize: 13))),
      ]),
    );
  }

  Widget _modeSwitch() {
    return Container(
      padding: const EdgeInsets.all(2),
      decoration: BoxDecoration(color: _t.border, borderRadius: BorderRadius.circular(7)),
      child: Row(mainAxisSize: MainAxisSize.min, children: [
        _modeChip('月', !_yearMode, () => _switchMode(false)),
        _modeChip('年', _yearMode, () => _switchMode(true)),
      ]),
    );
  }

  Widget _modeChip(String label, bool active, VoidCallback onTap) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 2),
        decoration: BoxDecoration(
          color: active ? _t.primaryDeep : null,
          borderRadius: BorderRadius.circular(5),
        ),
        child: Text(label,
            style: _t.textStyles.chip.copyWith(
                color: active ? Colors.white : _t.caption, fontWeight: FontWeight.w800)),
      ),
    );
  }

  Widget _buildSummaryCard() {
    final s = _monthData?.summary;
    final meals = s?.meals ?? 0;
    final dishes = s?.dishes ?? 0;
    final cookDays = s?.cookDays ?? 0;
    final top = s?.topDishes ?? const <String>[];
    return Container(
      margin: const EdgeInsets.fromLTRB(14, 8, 14, 8),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        gradient: LinearGradient(colors: [_t.primary, _t.primaryDeep]),
        borderRadius: BorderRadius.all(Radius.circular(14)),
      ),
      child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
          _stat('$meals', '顿饭'),
          _stat('$dishes', '道菜'),
          _stat('$cookDays', '做饭天数'),
        ]),
        if (top.isNotEmpty)
          Padding(
            padding: const EdgeInsets.only(top: 9),
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 5),
              decoration: BoxDecoration(
                color: Colors.white24,
                borderRadius: BorderRadius.circular(7),
              ),
              child: Text('${_yearMode ? '全年' : '本月'}最常：${top.join(' · ')}',
                  style: _t.textStyles.chip.copyWith(color: Colors.white)),
            ),
          ),
      ]),
    );
  }

  Widget _stat(String value, String label) {
    return Column(children: [
      Text(value, style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w800, color: Colors.white)),
      Text(label, style: TextStyle(fontSize: 9, color: Colors.white.withValues(alpha: .9))),
    ]);
  }

  Widget _buildTabBar() {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 14),
      child: Row(children: [
        _tabChip('时间轴', _tab == 'timeline', () => _switchTab('timeline')),
        const SizedBox(width: 4),
        _tabChip('按菜汇总', _tab == 'byDish', () => _switchTab('byDish')),
      ]),
    );
  }

  Widget _tabChip(String label, bool active, VoidCallback onTap) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
        decoration: BoxDecoration(
          color: active ? _t.title : _t.card,
          border: active ? null : Border.all(color: _t.border),
          borderRadius: BorderRadius.circular(8),
        ),
        child: Text(label,
            style: _t.textStyles.sm.copyWith(
                color: active ? Colors.white : _t.body, fontWeight: FontWeight.w800)),
      ),
    );
  }

  // ===== 月视图 =====

  Widget _buildMonthView() {
    final data = _tab == 'byDish' ? null : _monthData;
    final byDish = _tab == 'byDish' ? _byDishData : null;
    if (_tab == 'byDish') {
      final items = byDish?.items ?? const <FoodLogDishItem>[];
      return ListView(
        padding: const EdgeInsets.fromLTRB(14, 6, 14, 24),
        children: [
          if (byDish != null)
            Container(
              padding: const EdgeInsets.all(10),
              decoration: BoxDecoration(
                color: _t.highlight,
                border: Border.all(color: _t.border),
                borderRadius: BorderRadius.circular(10),
              ),
              child: Text('本月做过 ${byDish.totalKinds} 种菜 · 按次数排序，点一行看全部记录',
                  style: _t.textStyles.sm.copyWith(color: _t.primaryDeep)),
            ),
          const SizedBox(height: 6),
          if (items.isEmpty)
            const Padding(padding: EdgeInsets.only(top: 80), child: EmptyView(text: '本月还没有做菜记录'))
          else
            ...items.map((it) => _byDishRow(it)),
        ],
      );
    }
    final meals = data?.records ?? const <FoodLogMeal>[];
    return RefreshIndicator(
      onRefresh: _load,
      child: ListView(
        controller: _scroll,
        padding: const EdgeInsets.fromLTRB(14, 6, 14, 24),
        children: [
          if (meals.isEmpty)
            const Padding(padding: EdgeInsets.only(top: 80), child: EmptyView(text: '本月还没有做菜记录'))
          else ...[
            ...meals.map((m) => _timelineRow(m)),
            if (_loadingMore)
              const Padding(padding: EdgeInsets.symmetric(vertical: 12), child: LoadingView())
            else if (!_hasMore && meals.length >= _pageSize)
              Padding(
                padding: const EdgeInsets.symmetric(vertical: 10),
                child: Text('共 ${meals.length} 顿',
                    style: _t.textStyles.sm.copyWith(color: _t.caption),
                    textAlign: TextAlign.center),
              ),
          ],
        ],
      ),
    );
  }

  Widget _timelineRow(FoodLogMeal m) {
    final time = m.cookedAt == null
        ? ''
        : '${m.cookedAt!.month}/${m.cookedAt!.day} ${m.cookedAt!.hour.toString().padLeft(2, '0')}:${m.cookedAt!.minute.toString().padLeft(2, '0')}';
    return InkWell(
      onTap: m.menuId == null
          ? null
          : () async {
              await context.push('/food-log/detail?menuId=${m.menuId}');
              _load();
            },
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 9),
        decoration: BoxDecoration(
            border: Border(bottom: BorderSide(color: _t.border))),
        child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
          // 时间轴圆点 + 线
          Padding(
            padding: const EdgeInsets.only(top: 3),
            child: Column(children: [
              Container(
                  width: 8, height: 8,
                  decoration: BoxDecoration(
                      color: _t.primary, shape: BoxShape.circle)),
            ]),
          ),
          const SizedBox(width: 8),
          Expanded(
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Row(children: [
                Expanded(
                  child: Text(m.name,
                      style: _t.textStyles.md.copyWith(color: _t.title, fontWeight: FontWeight.w800)),
                ),
                Text(time, style: _t.textStyles.sm.copyWith(color: _t.caption)),
              ]),
              Text(
                '${m.dishCount} 道菜${m.servingCount != null ? ' · ${m.servingCount} 人份' : ''} · ${m.dishNames.join('/')}',
                style: _t.textStyles.sm.copyWith(color: _t.body),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
              if (m.usedUpCount > 0 || m.partialCount > 0)
                Padding(
                  padding: const EdgeInsets.only(top: 4),
                  child: Row(children: [
                    if (m.usedUpCount > 0)
                      _tag('用完 ${m.usedUpCount} 样', AppTokens.error),
                    if (m.partialCount > 0) ...[
                      const SizedBox(width: 4),
                      _tag('用了一些 ${m.partialCount} 样', AppTokens.warning),
                    ],
                    if (m.reviewed) ...[
                      const SizedBox(width: 4),
                      _tag('已评价', AppTokens.success),
                    ],
                  ]),
                ),
            ]),
          ),
        ]),
      ),
    );
  }

  Widget _tag(String text, Color color) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 1),
      decoration: BoxDecoration(
        color: color.withValues(alpha: .12),
        borderRadius: BorderRadius.circular(5),
      ),
      child: Text(text, style: _t.textStyles.chip.copyWith(color: color)),
    );
  }

  Widget _byDishRow(FoodLogDishItem it) {
    final last = it.lastCookedAt == null
        ? ''
        : '${it.lastCookedAt!.month}/${it.lastCookedAt!.day}';
    return InkWell(
      onTap: () => _openDishRecords(it),
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 10),
        decoration: BoxDecoration(
            border: Border(bottom: BorderSide(color: _t.border))),
        child: Row(children: [
          Expanded(
            child: Text(it.dishName,
                style: _t.textStyles.md.copyWith(color: _t.title, fontWeight: FontWeight.w800)),
          ),
          Text('${it.count} 次', style: _t.textStyles.sm.copyWith(color: _t.primary, fontWeight: FontWeight.w800)),
          const SizedBox(width: 12),
          Text(last, style: _t.textStyles.sm.copyWith(color: _t.caption)),
          if (it.avgStar != null) ...[
            const SizedBox(width: 12),
            Text('★${it.avgStar!.toStringAsFixed(1)}',
                style: _t.textStyles.sm.copyWith(color: _t.primary, fontWeight: FontWeight.w800)),
          ],
        ]),
      ),
    );
  }

  /// 按菜汇总点一行：切回时间轴并提示（原型「点一行看全部记录」，MVP 直接切时间轴）。
  void _openDishRecords(FoodLogDishItem it) {
    setState(() => _tab = 'timeline');
    _load();
  }

}
