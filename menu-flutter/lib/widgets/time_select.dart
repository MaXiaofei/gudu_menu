import 'package:flutter/material.dart';

import '../core/app_theme.dart';

/// 时间选择粒度：year=只选年 / month=年月 / day=年月日 / time=年月日+时分
enum TimeGranularity { year, month, day, time }

/// 统一时间选择胶囊（点击弹出逐级选择器：年 → 月 → 日 → 时间）。
///
/// 对齐原型日期组件样式：白底圆角胶囊 + 当前值 + ▾。
/// 调用方自行组合 ‹ › 步进按钮；本组件只负责"展示 + 点击展开选择"。
class TimeSelectCapsule extends StatelessWidget {
  final DateTime value;
  final TimeGranularity granularity;
  final ValueChanged<DateTime> onChanged;
  final String Function(DateTime)? labelBuilder;

  const TimeSelectCapsule({
    super.key,
    required this.value,
    required this.granularity,
    required this.onChanged,
    this.labelBuilder,
  });

  /// 胶囊文案（按粒度截断）。
  static String labelOf(DateTime v, TimeGranularity g) {
    switch (g) {
      case TimeGranularity.year:
        return '${v.year}年';
      case TimeGranularity.month:
        return '${v.year}年${v.month}月';
      case TimeGranularity.day:
        return '${v.year}年${v.month}月${v.day}日';
      case TimeGranularity.time:
        final hh = v.hour.toString().padLeft(2, '0');
        final mm = v.minute.toString().padLeft(2, '0');
        return '${v.year}年${v.month}月${v.day}日 $hh:$mm';
    }
  }

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    final label = labelBuilder?.call(value) ?? labelOf(value, granularity);
    return GestureDetector(
      onTap: () => showModalBottomSheet<DateTime>(
        context: context,
        backgroundColor: t.card,
        shape: const RoundedRectangleBorder(
            borderRadius: BorderRadius.vertical(top: Radius.circular(22))),
        builder: (_) => _TimeSelectSheet(
          initial: value,
          granularity: granularity,
        ),
      ).then((picked) {
        if (picked != null) onChanged(picked);
      }),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 3),
        decoration: BoxDecoration(
          color: t.card,
          border: Border.all(color: t.border),
          borderRadius: BorderRadius.circular(9),
        ),
        child: Row(mainAxisSize: MainAxisSize.min, children: [
          Text(label,
              style: t.textStyles.sm.copyWith(color: t.title, fontWeight: FontWeight.w800)),
          const SizedBox(width: 3),
          Icon(Icons.arrow_drop_down, size: 14, color: t.caption),
        ]),
      ),
    );
  }
}

// ===================== 逐级选择弹层 =====================

class _TimeSelectSheet extends StatefulWidget {
  final DateTime initial;
  final TimeGranularity granularity;
  const _TimeSelectSheet({required this.initial, required this.granularity});

  @override
  State<_TimeSelectSheet> createState() => _TimeSelectSheetState();
}

class _TimeSelectSheetState extends State<_TimeSelectSheet> {
  late int _year = widget.initial.year;
  late int _month = widget.initial.month;
  late int _day = widget.initial.day;
  late int _hour = widget.initial.hour;
  late int _minute = (widget.initial.minute ~/ 5) * 5;
  late int _step = 0; // 0=年 1=月 2=日 3=时间

  int get _maxStep => switch (widget.granularity) {
        TimeGranularity.year => 0,
        TimeGranularity.month => 1,
        TimeGranularity.day => 2,
        TimeGranularity.time => 3,
      };

  DateTime get _result => DateTime(_year, _month, _day, _hour, _minute);

  void _select(int value) {
    setState(() {
      switch (_step) {
        case 0:
          _year = value;
          break;
        case 1:
          _month = value;
          _day = _day.clamp(1, daysInMonth(_year, _month));
          break;
        case 2:
          _day = value;
          break;
        case 3:
          if (_hour == -1) {
            _hour = value;
          } else {
            _minute = value;
          }
          break;
      }
      if (_step < _maxStep) {
        _step++;
        if (_step == 3) _hour = -1; // 时间级先选小时再选分钟
      } else {
        Navigator.pop(context, _result);
      }
    });
  }

  static int daysInMonth(int year, int month) =>
      DateTime(year, month + 1, 0).day;

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    // 顶部路径：按已确认到的层级展示
    final path = [
      '$_year年',
      if (_step >= 1) '$_month月',
      if (_step >= 2) '$_day日',
      if (_step >= 3)
        '${_hour < 0 ? '--' : _hour.toString().padLeft(2, '0')}:${_minute.toString().padLeft(2, '0')}',
    ].join('');
    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 14, 16, 12),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Row(children: [
              Text(path, style: t.textStyles.subtitle.copyWith(color: t.title)),
              const Spacer(),
              GestureDetector(
                onTap: () => Navigator.pop(context, _result),
                child: Text('完成',
                    style: t.textStyles.sm.copyWith(
                        color: t.primary, fontWeight: FontWeight.w800)),
              ),
            ]),
            const SizedBox(height: 10),
            SizedBox(height: 300, child: _buildStep(t)),
          ],
        ),
      ),
    );
  }

  Widget _buildStep(AppTokens t) {
    switch (_step) {
      case 0:
        return _grid(t, '年', [for (int y = _year - 10; y <= _year + 10; y++) '$y'],
            _year, (i) => _year - 10 + i);
      case 1:
        return _grid(t, '月', [for (int m = 1; m <= 12; m++) '$m月'],
            _month, (i) => i + 1);
      case 2:
        return _dayGrid(t);
      default:
        // 时间：先选小时（24 格），再选分钟（00/05..55 12 格）
        if (_hour < 0) {
          return _grid(t, '时', [for (int h = 0; h < 24; h++) h.toString().padLeft(2, '0')],
              _hour, (i) => i);
        }
        return _grid(t, '分', [for (int m = 0; m < 60; m += 5) m.toString().padLeft(2, '0')],
            _minute, (i) => i * 5);
    }
  }

  /// 网格：label 前缀标题 + 3 列按钮网格。
  Widget _grid(AppTokens t, String title, List<String> items,
      int selected, int Function(int) valueOf) {
    return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      Text('选择$title', style: t.textStyles.sectionLabel.copyWith(letterSpacing: 1)),
      const SizedBox(height: 8),
      Expanded(
        child: GridView.count(
          crossAxisCount: 3,
          mainAxisSpacing: 6,
          crossAxisSpacing: 6,
          childAspectRatio: 2.4,
          children: [
            for (int i = 0; i < items.length; i++)
              _cell(t, items[i], valueOf(i) == selected, () => _select(valueOf(i))),
          ],
        ),
      ),
    ]);
  }

  /// 日历：7 列，首行 一二三四五六日，当月天数 + 前后空位。
  Widget _dayGrid(AppTokens t) {
    final first = DateTime(_year, _month, 1);
    final leading = first.weekday % 7; // 周一=1 → 周日=0
    final days = daysInMonth(_year, _month);
    return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      Text('选择日', style: t.textStyles.sectionLabel.copyWith(letterSpacing: 1)),
      const SizedBox(height: 6),
      Row(children: [
        for (final w in const ['一', '二', '三', '四', '五', '六', '日'])
          Expanded(
              child: Text(w,
                  textAlign: TextAlign.center,
                  style: t.textStyles.chip.copyWith(color: t.caption))),
      ]),
      const SizedBox(height: 4),
      Expanded(
        child: GridView.count(
          crossAxisCount: 7,
          mainAxisSpacing: 4,
          crossAxisSpacing: 4,
          children: [
            for (int i = 0; i < leading; i++) const SizedBox(),
            for (int d = 1; d <= days; d++)
              _cell(t, '$d', d == _day, () => _select(d)),
          ],
        ),
      ),
    ]);
  }

  Widget _cell(AppTokens t, String label, bool selected, VoidCallback onTap) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        alignment: Alignment.center,
        decoration: BoxDecoration(
          color: selected ? t.primaryDeep : t.bg,
          border: Border.all(color: selected ? t.primaryDeep : t.border),
          borderRadius: BorderRadius.circular(8),
        ),
        child: Text(label,
            style: t.textStyles.sm.copyWith(
                color: selected ? Colors.white : t.body,
                fontWeight: selected ? FontWeight.w800 : FontWeight.normal)),
      ),
    );
  }
}
