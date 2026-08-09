import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../core/app_theme.dart';
import '../../services/food_log_service.dart';
import '../../widgets/loading_empty.dart';

/// 食记（做菜日记）主页（对齐 dailylog.html 原型）。
///
/// 月视图：日期组件（‹ 2026年7月 › + 月|年切换）+ 统计卡（顿饭/道菜/做饭天数/最常）
/// + Tab（时间轴 / 按菜汇总）+ 筛选弹层（餐次/做菜方式/评价状态）。
/// 年视图：12 个月做饭次数地图，点月份切回该月时间轴。
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

  // 筛选：餐次 / 做菜方式 / 评价状态（null=全部）
  String? _meal;
  String? _source;
  bool? _reviewed;

  FoodLogMonth? _monthData;
  FoodLogByDish? _byDishData;
  FoodLogYear? _yearData;
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() => _loading = _monthData == null && _byDishData == null && _yearData == null);
    try {
      if (_yearMode) {
        _yearData = await FoodLogService.year(_year);
      } else if (_tab == 'byDish') {
        _byDishData = await FoodLogService.byDish(_year, _month,
            meal: _meal, source: _source, reviewed: _reviewed);
      } else {
        _monthData = await FoodLogService.month(_year, _month,
            meal: _meal, source: _source, reviewed: _reviewed);
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

  void _jumpToMonth(int m) {
    setState(() {
      _month = m;
      _yearMode = false;
    });
    _load();
  }

  Future<void> _openFilter() async {
    final result = await showModalBottomSheet<_FilterResult>(
      context: context,
      backgroundColor: _t.card,
      shape: const RoundedRectangleBorder(
          borderRadius: BorderRadius.vertical(top: Radius.circular(22))),
      builder: (_) => _FilterSheet(
        meal: _meal,
        source: _source,
        reviewed: _reviewed,
      ),
    );
    if (result == null) return;
    setState(() {
      _meal = result.meal;
      _source = result.source;
      _reviewed = result.reviewed;
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
            // 日期组件行（§13.1 无标题，右对齐）
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 10, 16, 0),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.end,
                children: [
                  _dateCapsule(),
                  const SizedBox(width: 8),
                  _modeSwitch(),
                ],
              ),
            ),
            // 统计卡
            _buildSummaryCard(),
            // Tab + 筛选
            _buildTabBar(),
            // 内容
            Expanded(
              child: _loading
                  ? const LoadingView()
                  : _yearMode ? _buildYearView() : _buildMonthView(),
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
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 6),
          child: Text(_yearMode ? '$_year年' : '$_year年$_month月',
              style: _t.textStyles.sm.copyWith(color: _t.title, fontWeight: FontWeight.w800)),
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
    final s = _yearMode
        ? (_yearData == null ? null : null)
        : _monthData?.summary;
    final meals = _yearMode
        ? (_yearData?.monthCounts.fold<int>(0, (a, b) => a + b) ?? 0)
        : (s?.meals ?? 0);
    // 年视图不显示道菜/天数（原型统计卡随视图切换；年视图接口只给月次数，道菜/天数以 0 兜底显示）
    final dishes = _yearMode ? 0 : (s?.dishes ?? 0);
    final cookDays = _yearMode ? 0 : (s?.cookDays ?? 0);
    final top = _yearMode ? const <String>[] : (s?.topDishes ?? const []);
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
        const Spacer(),
        GestureDetector(
          onTap: _openFilter,
          child: Text('筛选', style: _t.textStyles.sm.copyWith(color: _t.primary, fontWeight: FontWeight.w800)),
        ),
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
    final meals = data?.timeline ?? const <FoodLogMeal>[];
    return RefreshIndicator(
      onRefresh: _load,
      child: ListView(
        padding: const EdgeInsets.fromLTRB(14, 6, 14, 24),
        children: [
          if (meals.isEmpty)
            const Padding(padding: EdgeInsets.only(top: 80), child: EmptyView(text: '本月还没有做菜记录'))
          else
            ...meals.map((m) => _timelineRow(m)),
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

  // ===== 年视图 =====

  Widget _buildYearView() {
    final counts = _yearData?.monthCounts ?? List.filled(12, 0);
    return ListView(
      padding: const EdgeInsets.fromLTRB(14, 8, 14, 24),
      children: [
        Text('$_year · 每月做饭次数',
            style: _t.textStyles.sectionLabel.copyWith(letterSpacing: 1)),
        const SizedBox(height: 8),
        GridView.count(
          crossAxisCount: 3,
          shrinkWrap: true,
          physics: const NeverScrollableScrollPhysics(),
          mainAxisSpacing: 7,
          crossAxisSpacing: 7,
          childAspectRatio: 1.9,
          children: [
            for (int m = 1; m <= 12; m++)
              _monthCell(m, counts[m - 1]),
          ],
        ),
        const SizedBox(height: 12),
        Text('点任一月份，切回该月时间轴',
            style: _t.textStyles.sm.copyWith(color: _t.caption), textAlign: TextAlign.center),
      ],
    );
  }

  Widget _monthCell(int m, int count) {
    final isCurrent = m == DateTime.now().month && _year == DateTime.now().year;
    final hasData = count > 0;
    return GestureDetector(
      onTap: hasData ? () => _jumpToMonth(m) : null,
      child: Container(
        decoration: BoxDecoration(
          gradient: isCurrent
              ? LinearGradient(colors: [_t.primary, _t.primaryDeep])
              : null,
          color: hasData ? _t.card : null,
          border: hasData ? Border.all(color: _t.border) : Border.all(color: _t.border, width: 1),
          borderRadius: BorderRadius.circular(10),
        ),
        child: Column(mainAxisAlignment: MainAxisAlignment.center, children: [
          Text('$m月', style: _t.textStyles.chip.copyWith(
              color: isCurrent ? Colors.white : _t.caption)),
          Text(hasData ? '$count' : '—', style: TextStyle(
              fontSize: 15, fontWeight: FontWeight.w800,
              color: isCurrent ? Colors.white : (hasData ? _t.title : _t.caption.withValues(alpha: .5)))),
        ]),
      ),
    );
  }
}

// ===== 筛选弹层 =====

class _FilterResult {
  final String? meal;
  final String? source;
  final bool? reviewed;
  const _FilterResult({this.meal, this.source, this.reviewed});
}

class _FilterSheet extends StatefulWidget {
  final String? meal;
  final String? source;
  final bool? reviewed;
  const _FilterSheet({required this.meal, required this.source, required this.reviewed});

  @override
  State<_FilterSheet> createState() => _FilterSheetState();
}

class _FilterSheetState extends State<_FilterSheet> {
  String? _meal;
  String? _source;
  bool? _reviewed;

  @override
  void initState() {
    super.initState();
    _meal = widget.meal;
    _source = widget.source;
    _reviewed = widget.reviewed;
  }

  static const _meals = [
    ('全部', null),
    ('早餐', 'breakfast'),
    ('午餐', 'lunch'),
    ('晚餐', 'dinner'),
    ('加餐', 'snack'),
  ];
  static const _sources = [
    ('全部', null),
    ('整集做菜', 'menu'),
    ('单菜直做', 'dish'),
  ];
  static const _revieweds = [
    ('全部', null),
    ('已评价', true),
    ('未评价', false),
  ];

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 12, 16, 16),
        child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          Row(children: [
            Text('筛选', style: t.textStyles.subtitle),
            const Spacer(),
            GestureDetector(
              onTap: () => setState(() {
                _meal = null;
                _source = null;
                _reviewed = null;
              }),
              child: Text('重置', style: t.textStyles.sm.copyWith(color: t.caption)),
            ),
          ]),
          const SizedBox(height: 10),
          _group('餐次', _meals, _meal, (v) => _meal = v),
          _group('做菜方式', _sources, _source, (v) => _source = v),
          _group('评价', _revieweds, _reviewed, (v) => _reviewed = v),
          const SizedBox(height: 12),
          ElevatedButton(
            style: ElevatedButton.styleFrom(
              backgroundColor: t.primary,
              foregroundColor: Colors.white,
              minimumSize: const Size.fromHeight(44),
            ),
            onPressed: () => Navigator.pop(context, _FilterResult(
                meal: _meal, source: _source, reviewed: _reviewed)),
            child: const Text('完成', style: TextStyle(fontWeight: FontWeight.w800)),
          ),
        ]),
      ),
    );
  }

  Widget _group<T>(String title, List<(String, T?)> options, T? selected, ValueChanged<T?> onSelect) {
    final t = AppTokens.of(context);
    return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      Text(title, style: t.textStyles.sectionLabel.copyWith(letterSpacing: 1)),
      const SizedBox(height: 5),
      Wrap(spacing: 6, runSpacing: 6, children: [
        for (final (label, value) in options)
          GestureDetector(
            onTap: () => onSelect(value),
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
              decoration: BoxDecoration(
                color: selected == value ? t.primaryDeep : t.highlight,
                border: Border.all(color: selected == value ? t.primaryDeep : t.border),
                borderRadius: BorderRadius.circular(99),
              ),
              child: Text(label,
                  style: t.textStyles.chip.copyWith(
                      color: selected == value ? Colors.white : t.body,
                      fontWeight: FontWeight.w800)),
            ),
          ),
      ]),
      const SizedBox(height: 10),
    ]);
  }
}
