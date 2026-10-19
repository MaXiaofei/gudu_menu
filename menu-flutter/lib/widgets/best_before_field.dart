import 'package:flutter/material.dart';

import '../core/app_theme.dart';
import '../services/pantry_service.dart' show fmtDate, daysLeftLabel;
import 'status_chip.dart';

/// 保质期输入（G2）：点击弹日期选择；可清除；右侧显示「剩 N 天 / 今天到期 / 已过期」。
///
/// 预填由调用方负责（入库时按 GET /pantry/shelf-life 的 suggestedDays 算出 今天+N）。
class BestBeforeField extends StatelessWidget {
  final DateTime? value;
  final ValueChanged<DateTime?> onChanged;
  /// 预填来源提示（如「按品类建议 7 天」），为空不显示。
  final String? hint;

  const BestBeforeField({
    super.key,
    required this.value,
    required this.onChanged,
    this.hint,
  });

  static DateTime _today() {
    final n = DateTime.now();
    return DateTime(n.year, n.month, n.day);
  }

  /// 相对今天的剩余天数（今天=0，过期为负）。
  static int daysFromToday(DateTime d) =>
      DateTime(d.year, d.month, d.day).difference(_today()).inDays;

  Future<void> _pick(BuildContext context) async {
    final today = _today();
    final picked = await showDatePicker(
      context: context,
      initialDate: value ?? today,
      firstDate: today.subtract(const Duration(days: 365)),
      lastDate: today.add(const Duration(days: 365 * 3)),
      helpText: '保质期至',
    );
    if (picked != null) onChanged(picked);
  }

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    final ts = t.textStyles;
    final v = value;
    return InkWell(
      key: const Key('best-before-field'),
      onTap: () => _pick(context),
      borderRadius: BorderRadius.circular(AppTokens.rMd),
      child: Container(
        padding: const EdgeInsets.symmetric(
            horizontal: AppTokens.sp12, vertical: AppTokens.sp10),
        decoration: BoxDecoration(
          color: t.bg,
          border: Border.all(color: t.border, width: 1.5),
          borderRadius: BorderRadius.circular(AppTokens.rMd),
        ),
        child: Row(
          children: [
            Icon(Icons.event_outlined, size: 18, color: t.caption),
            const SizedBox(width: AppTokens.sp8),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(v == null ? '未填保质期（选填）' : '保质期至 ${fmtDate(v)}',
                      style: ts.body.copyWith(color: v == null ? t.caption : t.title)),
                  if (hint != null && hint!.isNotEmpty)
                    Text(hint!, style: ts.sectionLabel),
                ],
              ),
            ),
            if (v != null) ...[
              StatusChip(
                label: daysLeftLabel(daysFromToday(v)),
                color: expiryColor(t, daysFromToday(v)),
              ),
              IconButton(
                key: const Key('best-before-clear'),
                tooltip: '清除保质期',
                visualDensity: VisualDensity.compact,
                icon: Icon(Icons.close, size: 16, color: t.caption),
                onPressed: () => onChanged(null),
              ),
            ],
          ],
        ),
      ),
    );
  }
}
