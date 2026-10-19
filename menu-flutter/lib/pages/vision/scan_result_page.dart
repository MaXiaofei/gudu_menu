import 'dart:io';

import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../core/app_theme.dart';
import '../../core/error_text.dart';
import '../../services/vision_service.dart';
import '../../widgets/ingredient_icon.dart';
import '../../widgets/tone_badge.dart';

/// 识别结果确认（原型 ScanResult.html）：置信度 ≥0.8 默认勾选、0.5–0.8 标黄「请确认」；
/// 可改名（或点备选）、调数量、看保质期；「放进余量 · N 样」一次写入。
class ScanResultPage extends StatefulWidget {
  final VisionJob job;
  final String? photoPath;
  const ScanResultPage({super.key, required this.job, this.photoPath});

  @override
  State<ScanResultPage> createState() => _ScanResultPageState();
}

class _Row {
  String name;
  double amount;
  bool checked;
  final VisionItem src;
  _Row(this.src)
      : name = src.name,
        amount = src.amount,
        checked = !src.needsConfirm;
}

class _ScanResultPageState extends State<ScanResultPage> {
  late final List<_Row> _rows = [for (final it in widget.job.items) _Row(it)];
  bool _saving = false;

  int get _checked => _rows.where((r) => r.checked).length;

  Future<void> _save() async {
    final items = [
      for (final r in _rows.where((r) => r.checked))
        {
          'name': r.name,
          if (r.name == r.src.name && r.src.ingredientId != null) 'ingredientId': r.src.ingredientId,
          'amount': r.amount,
          'unit': r.src.unit,
          if (r.src.suggestedBestBefore != null)
            'bestBefore': r.src.suggestedBestBefore!.toIso8601String().substring(0, 10),
        },
    ];
    if (items.isEmpty) return;
    setState(() => _saving = true);
    try {
      final n = await VisionService.apply(widget.job.jobId, items);
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('已放进余量 $n 样，可在余量页撤销')));
      context.go('/pantry');
    } catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('入库失败：${errorText(e)}')));
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  Future<void> _rename(_Row r) async {
    final c = TextEditingController(text: r.name);
    final name = await showDialog<String>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('改成'),
        content: TextField(controller: c, autofocus: true),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('取消')),
          TextButton(onPressed: () => Navigator.pop(ctx, c.text.trim()), child: const Text('确定')),
        ],
      ),
    );
    c.dispose();
    if (name != null && name.isNotEmpty) setState(() => r.name = name);
  }

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    final ts = t.textStyles;
    final job = widget.job;
    final confirm = _rows.where((r) => r.src.needsConfirm).toList();
    final auto = _rows.where((r) => !r.src.needsConfirm).toList();
    final recipe = job.recipe;
    return Scaffold(
      backgroundColor: t.bg,
      appBar: AppBar(
        backgroundColor: t.bg,
        surfaceTintColor: t.bg,
        title: Text('确认识别结果', style: ts.pageTitle),
        actions: [
          TextButton.icon(
            onPressed: () => context.pop(),
            icon: const Icon(Icons.photo_camera_outlined, size: 18),
            label: const Text('再拍一张'),
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(AppTokens.sp16, AppTokens.sp4, AppTokens.sp16, AppTokens.sp24),
        children: [
          _summary(t, job),
          if (recipe != null) ...[
            const SizedBox(height: AppTokens.sp16),
            _recipe(t, recipe),
          ],
          if (confirm.isNotEmpty) ...[
            const SizedBox(height: AppTokens.sp16),
            Row(children: [
              Text('请确认 · ${confirm.length}', style: ts.sectionLabel.copyWith(color: AppTokens.warningText)),
              const Spacer(),
              Text('看不准的不会自动放进去', style: ts.caption),
            ]),
            const SizedBox(height: AppTokens.sp8),
            for (final r in confirm) _rowTile(t, r),
          ],
          if (auto.isNotEmpty) ...[
            const SizedBox(height: AppTokens.sp16),
            Row(children: [
              Text('已识别 · ${auto.length}', style: ts.sectionLabel.copyWith(color: t.body)),
              const Spacer(),
              InkWell(
                onTap: () => setState(() {
                  final all = auto.every((r) => r.checked);
                  for (final r in auto) {
                    r.checked = !all;
                  }
                }),
                child: Text(auto.every((r) => r.checked) ? '全不选' : '全选',
                    style: ts.caption.copyWith(color: t.primaryDeep, fontWeight: FontWeight.w700)),
              ),
            ]),
            const SizedBox(height: AppTokens.sp8),
            for (final r in auto) _rowTile(t, r),
          ],
          if (_rows.isEmpty && recipe == null)
            Padding(
              padding: const EdgeInsets.symmetric(vertical: AppTokens.sp32),
              child: Center(child: Text('这张照片里没认出食材，换个角度再拍一张', style: ts.body)),
            ),
        ],
      ),
      bottomNavigationBar: _rows.isEmpty
          ? null
          : Container(
              padding: EdgeInsets.fromLTRB(AppTokens.sp16, AppTokens.sp10, AppTokens.sp16, AppTokens.sp10 + MediaQuery.of(context).padding.bottom),
              decoration: BoxDecoration(color: t.card, border: Border(top: BorderSide(color: t.border))),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(
                    '已选 $_checked 样${confirm.isEmpty ? '' : ' · 其中 ${confirm.where((r) => r.checked).length} 样是你确认过的'}',
                    style: ts.caption,
                  ),
                  const SizedBox(height: AppTokens.sp8),
                  SizedBox(
                    width: double.infinity,
                    child: ElevatedButton.icon(
                      key: const Key('scan-apply'),
                      onPressed: _saving || _checked == 0 ? null : _save,
                      icon: const Icon(Icons.check_rounded),
                      label: Text(_saving ? '放进去…' : '放进余量 · $_checked 样'),
                    ),
                  ),
                ],
              ),
            ),
    );
  }

  Widget _summary(AppTokens t, VisionJob job) {
    final ts = t.textStyles;
    final mode = VisionMode.values.firstWhere((m) => m.code == job.mode, orElse: () => VisionMode.fridge);
    return Container(
      padding: const EdgeInsets.all(AppTokens.sp12),
      decoration: BoxDecoration(
        color: t.card,
        borderRadius: BorderRadius.circular(AppTokens.rLg),
        border: Border.all(color: t.border),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              ClipRRect(
                borderRadius: BorderRadius.circular(AppTokens.rSm),
                child: widget.photoPath != null && File(widget.photoPath!).existsSync()
                    ? Image.file(File(widget.photoPath!), width: 72, height: 72, fit: BoxFit.cover)
                    : Container(
                        width: 72,
                        height: 72,
                        color: t.secondary,
                        child: Icon(Icons.photo_outlined, color: t.body),
                      ),
              ),
              const SizedBox(width: AppTokens.sp12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(mode.label, style: ts.caption),
                    Text('识别到 ${_rows.length} 样', style: ts.h3),
                    if (job.confirmCount > 0)
                      Text('${job.confirmCount} 样待确认', style: ts.cardTitle.copyWith(color: AppTokens.warningText)),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: AppTokens.sp10),
          Row(children: [
            Icon(Icons.lock_clock_outlined, size: 14, color: t.body),
            const SizedBox(width: AppTokens.sp4),
            Expanded(child: Text('原图 24 小时后自动删除，只保留识别结果', style: ts.caption)),
          ]),
        ],
      ),
    );
  }

  Widget _recipe(AppTokens t, ({String title, List ingredients, List<String> steps}) r) {
    final ts = t.textStyles;
    return Container(
      padding: const EdgeInsets.all(AppTokens.sp12),
      decoration: BoxDecoration(
        color: t.card,
        borderRadius: BorderRadius.circular(AppTokens.rLg),
        border: Border.all(color: t.border),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(r.title, style: ts.subtitle),
          const SizedBox(height: AppTokens.sp6),
          Text('用料 ${r.ingredients.length} 样 · 步骤 ${r.steps.length} 步', style: ts.caption),
          const SizedBox(height: AppTokens.sp8),
          for (var i = 0; i < r.steps.length && i < 3; i++)
            Padding(
              padding: const EdgeInsets.only(bottom: AppTokens.sp4),
              child: Text('${i + 1}. ${r.steps[i]}', maxLines: 2, overflow: TextOverflow.ellipsis, style: ts.body),
            ),
          const SizedBox(height: AppTokens.sp6),
          OutlinedButton(onPressed: () => context.push('/create-dish'), child: const Text('去写菜谱')),
        ],
      ),
    );
  }

  Widget _rowTile(AppTokens t, _Row r) {
    final ts = t.textStyles;
    final it = r.src;
    final pct = '${(it.confidence * 100).round()}%';
    final days = it.suggestedBestBefore?.difference(DateTime.now()).inDays;
    return Container(
      margin: const EdgeInsets.only(bottom: AppTokens.sp8),
      padding: const EdgeInsets.all(AppTokens.sp10),
      decoration: BoxDecoration(
        color: it.needsConfirm ? AppTokens.warningBg : t.card,
        borderRadius: BorderRadius.circular(AppTokens.rMd),
        border: Border.all(color: it.needsConfirm ? AppTokens.warning.withAlpha(110) : t.border),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Checkbox(
                value: r.checked,
                onChanged: (v) => setState(() => r.checked = v ?? false),
                activeColor: AppTokens.success,
                shape: const CircleBorder(),
              ),
              IngredientIcon(name: r.name, iconKey: r.name == it.name ? it.iconKey : null, size: 40),
              const SizedBox(width: AppTokens.sp10),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(children: [
                      Flexible(
                        child: InkWell(
                          onTap: () => _rename(r),
                          child: Row(mainAxisSize: MainAxisSize.min, children: [
                            Flexible(child: Text(r.name, overflow: TextOverflow.ellipsis, style: ts.cardTitle.copyWith(color: t.title))),
                            const SizedBox(width: 2),
                            Icon(Icons.edit_outlined, size: 13, color: t.body),
                          ]),
                        ),
                      ),
                      const SizedBox(width: AppTokens.sp6),
                      ToneBadge(pct, tone: it.needsConfirm ? BadgeTone.warning : BadgeTone.success),
                    ]),
                    const SizedBox(height: 2),
                    if (it.renamed && r.name == it.name)
                      Text('${it.rawName} → 已匹配为 ${it.name}', style: ts.caption.copyWith(color: AppTokens.infoText))
                    else if (days != null)
                      Text('$days 天后到期', style: ts.caption),
                  ],
                ),
              ),
              _stepper(t, r),
            ],
          ),
          if (it.needsConfirm && it.alternatives.isNotEmpty)
            Padding(
              padding: const EdgeInsets.only(left: 48, top: AppTokens.sp4),
              child: Wrap(
                spacing: AppTokens.sp6,
                crossAxisAlignment: WrapCrossAlignment.center,
                children: [
                  Text('也可能是', style: ts.caption),
                  for (final a in it.alternatives)
                    ActionChip(
                      label: Text(a, style: ts.caption.copyWith(color: t.title)),
                      visualDensity: VisualDensity.compact,
                      backgroundColor: t.card,
                      side: BorderSide(color: t.border),
                      onPressed: () => setState(() {
                        r.name = a;
                        r.checked = true;
                      }),
                    ),
                ],
              ),
            ),
        ],
      ),
    );
  }

  Widget _stepper(AppTokens t, _Row r) {
    final ts = t.textStyles;
    String fmt(double v) => v == v.roundToDouble() ? '${v.toInt()}' : v.toStringAsFixed(1);
    final step = r.src.unit == 'g' || r.src.unit == 'ml' ? 50.0 : 1.0;
    Widget btn(IconData icon, VoidCallback? onTap) => InkResponse(
          onTap: onTap,
          radius: 16,
          child: Container(
            width: 26,
            height: 26,
            decoration: BoxDecoration(shape: BoxShape.circle, border: Border.all(color: t.border), color: t.card),
            child: Icon(icon, size: 14, color: onTap == null ? t.border : t.title),
          ),
        );
    return Row(mainAxisSize: MainAxisSize.min, children: [
      btn(Icons.remove_rounded, r.amount > step ? () => setState(() => r.amount -= step) : null),
      SizedBox(
        width: 44,
        child: Text('${fmt(r.amount)}${r.src.unit}', textAlign: TextAlign.center, style: ts.caption.copyWith(color: t.title)),
      ),
      btn(Icons.add_rounded, () => setState(() => r.amount += step)),
    ]);
  }
}
