import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../../core/app_theme.dart';
import '../../core/error_text.dart';
import '../../models/member.dart';
import '../../services/agent_service.dart' show isNotDeployed;
import '../../services/mealplan_service.dart';
import '../../stores/member_store.dart';
import '../../widgets/dish_image.dart';
import '../../widgets/error_view.dart';
import '../../widgets/illustration.dart';
import '../../widgets/loading_empty.dart';
import '../../widgets/member_avatar.dart';
import '../../widgets/skeleton.dart';
import '../../widgets/tone_badge.dart';

/// AI 一周餐程（原型 WeekPlan.html）：周一至周日 × 午 / 晚；约束 chips → 校验结果 →
/// 7 天网格（点格子「换一道」）→ 预计采购汇总 →「生成 7 个食集和采购清单」。
class WeekPlanPage extends StatefulWidget {
  final int? draftId;
  const WeekPlanPage({super.key, this.draftId});

  @override
  State<WeekPlanPage> createState() => _WeekPlanPageState();
}

class _WeekPlanPageState extends State<WeekPlanPage> {
  MealPlanDraft? _d;
  Object? _error;
  bool _loading = true;
  bool _applying = false;
  int? _swapping;
  bool _preferExpiring = true;
  bool _weekdayQuick = true;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _generate(reuse: widget.draftId));
  }

  List<Member> _members() {
    try {
      return context.read<MemberStore>().members;
    } catch (_) {
      return const [];
    }
  }

  Future<void> _generate({int? reuse}) async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final members = _members();
      final d = reuse != null
          ? await MealPlanService.get(reuse)
          : await MealPlanService.create(PlanRequest(
              weekStart: defaultWeekStart(DateTime.now()),
              servings: members.isEmpty ? 2 : members.length,
              memberIds: [for (final m in members) m.id],
              weekdayMaxMinutes: _weekdayQuick ? 30 : null,
              preferExpiring: _preferExpiring,
            ));
      if (mounted) setState(() => _d = d);
    } catch (e) {
      if (mounted) setState(() => _error = e);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _swap(PlanSlot slot) async {
    final d = _d;
    if (d == null) return;
    setState(() => _swapping = slot.slotId);
    try {
      final alts = await MealPlanService.alternatives(d.draftId, slot.slotId);
      if (!mounted) return;
      setState(() => _swapping = null);
      final picked = await showModalBottomSheet<int>(
        context: context,
        showDragHandle: true,
        builder: (ctx) {
          final t = AppTokens.of(ctx);
          final ts = t.textStyles;
          return SafeArea(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Padding(
                  padding: const EdgeInsets.fromLTRB(AppTokens.sp16, 0, AppTokens.sp16, AppTokens.sp8),
                  child: Row(children: [
                    Expanded(child: Text('换掉 ${slot.dish.name}', style: ts.subtitle)),
                    Text('同样满足约束 · 本周不重样', style: ts.caption),
                  ]),
                ),
                if (alts.isEmpty)
                  Padding(padding: const EdgeInsets.all(AppTokens.sp16), child: Text('暂时没有更合适的替换', style: ts.body)),
                for (final a in alts)
                  ListTile(
                    leading: DishImage(url: a.dish.coverUrl, name: a.dish.name, width: 48, height: 48, radius: AppTokens.rSm),
                    title: Text(a.dish.name, style: ts.cardTitle.copyWith(color: t.title)),
                    subtitle: Text(
                        [if (a.dish.minutes != null) '${a.dish.minutes} 分钟', if (a.sodiumMg > 0) '钠 ${(a.sodiumMg / 1000).toStringAsFixed(1)}g'].join(' · '),
                        style: ts.caption),
                    trailing: Icon(Icons.chevron_right_rounded, color: t.body),
                    onTap: () => Navigator.pop(ctx, a.dish.id),
                  ),
                const SizedBox(height: AppTokens.sp8),
              ],
            ),
          );
        },
      );
      if (picked == null) return;
      final next = await MealPlanService.replace(d.draftId, slot.slotId, picked);
      if (mounted) setState(() => _d = next);
    } catch (e) {
      if (mounted) {
        setState(() => _swapping = null);
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('换菜失败：${errorText(e)}')));
      }
    }
  }

  Future<void> _apply() async {
    final d = _d;
    if (d == null || _applying) return;
    setState(() => _applying = true);
    try {
      final r = await MealPlanService.apply(d.draftId);
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('已生成 ${r.menuIds.length} 个食集，采购清单合并了 ${r.shoppingItemCount} 样')));
      context.go('/menu');
    } catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('生成失败：${errorText(e)}')));
    } finally {
      if (mounted) setState(() => _applying = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    final ts = t.textStyles;
    final d = _d;
    final start = d?.weekStart ?? defaultWeekStart(DateTime.now());
    final end = d?.weekEnd ?? start.add(const Duration(days: 6));
    return Scaffold(
      backgroundColor: t.bg,
      appBar: AppBar(
        backgroundColor: t.bg,
        surfaceTintColor: t.bg,
        titleSpacing: 0,
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('AI 一周餐程', style: ts.pageTitle),
            Text('${md(start)} ${weekdayNames[start.weekday - 1]} – ${md(end)} ${weekdayNames[end.weekday - 1]} · 午晚两餐',
                style: ts.caption),
          ],
        ),
        actions: [
          Padding(
            padding: const EdgeInsets.only(right: AppTokens.sp12),
            child: OutlinedButton.icon(
              style: OutlinedButton.styleFrom(minimumSize: const Size(0, 34), padding: const EdgeInsets.symmetric(horizontal: AppTokens.sp10)),
              onPressed: _loading ? null : () => _generate(),
              icon: const Icon(Icons.refresh_rounded, size: 16),
              label: const Text('重新排'),
            ),
          ),
        ],
      ),
      body: _body(t),
      bottomNavigationBar: d == null ? null : _footer(t, d),
    );
  }

  Widget _body(AppTokens t) {
    final ts = t.textStyles;
    final members = _members();
    final chips = <Widget>[
      if (members.isNotEmpty)
        _chip(t,
            leading: AvatarStack(size: 18, max: 4, people: [for (final m in members) (name: m.name, avatarUrl: m.avatarUrl)]),
            label: '${members.length} 口人'),
      for (final m in members)
        for (final a in m.audiences) _chip(t, label: '${m.name}${a.contains('宝宝') ? '辅食版' : a.contains('高血压') ? '少盐' : a}', key: true),
      _toggle(t, '工作日 ≤30 分钟', _weekdayQuick, (v) {
        setState(() => _weekdayQuick = v);
        _generate();
      }),
      _toggle(t, '优先用快到期', _preferExpiring, (v) {
        setState(() => _preferExpiring = v);
        _generate();
      }),
    ];
    return ListView(
      padding: const EdgeInsets.fromLTRB(AppTokens.sp16, AppTokens.sp4, AppTokens.sp16, AppTokens.sp24),
      children: [
        SizedBox(
          height: 36,
          child: ListView.separated(
            scrollDirection: Axis.horizontal,
            itemCount: chips.length,
            separatorBuilder: (_, __) => const SizedBox(width: AppTokens.sp6),
            itemBuilder: (_, i) => chips[i],
          ),
        ),
        const SizedBox(height: AppTokens.sp12),
        if (_loading) ...[
          Container(
            padding: const EdgeInsets.all(AppTokens.sp12),
            decoration: BoxDecoration(color: t.highlight, borderRadius: BorderRadius.circular(AppTokens.rMd)),
            child: Row(children: [
              Icon(Icons.auto_awesome_rounded, color: t.primaryDeep),
              const SizedBox(width: AppTokens.sp8),
              Expanded(child: Text('咕嘟正在排：先按档案过滤，再排荤素搭配，最后校验营养…', style: ts.caption.copyWith(color: t.title))),
            ]),
          ),
          const SizedBox(height: AppTokens.sp12),
          for (var i = 0; i < 4; i++)
            const Padding(padding: EdgeInsets.only(bottom: AppTokens.sp8), child: SkeletonBox(height: 64, radius: AppTokens.rMd)),
        ] else if (_error != null && _d == null)
          isNotDeployed(_error!)
              ? const EmptyView(
                  text: 'AI 排餐即将上线',
                  subtitle: '先从模板建食集，上线后一键排好一周',
                  illustration: StateIllustration(keys: ['rice', 'fish', 'broccoli']),
                )
              : ErrorView(text: '餐程生成失败', onRetry: () => _generate(), error: _error)
        else if (_d != null) ...[
          _check(t, _d!),
          const SizedBox(height: AppTokens.sp12),
          _grid(t, _d!),
        ],
      ],
    );
  }

  Widget _chip(AppTokens t, {Widget? leading, required String label, bool key = false}) => Container(
        padding: const EdgeInsets.symmetric(horizontal: AppTokens.sp10),
        alignment: Alignment.center,
        decoration: BoxDecoration(
          color: key ? AppTokens.warningBg : t.card,
          borderRadius: BorderRadius.circular(AppTokens.rPill),
          border: Border.all(color: key ? AppTokens.warning.withAlpha(110) : t.border),
        ),
        child: Row(mainAxisSize: MainAxisSize.min, children: [
          if (leading != null) ...[leading, const SizedBox(width: AppTokens.sp4)],
          Text(label, style: t.textStyles.caption.copyWith(color: key ? AppTokens.warningText : t.title, fontWeight: FontWeight.w600)),
        ]),
      );

  Widget _toggle(AppTokens t, String label, bool on, ValueChanged<bool> onChanged) => FilterChip(
        label: Text(label),
        selected: on,
        onSelected: _loading ? null : onChanged,
        selectedColor: t.primarySoft,
        checkmarkColor: t.primaryDeep,
        side: BorderSide(color: t.border),
        shape: const StadiumBorder(),
        visualDensity: VisualDensity.compact,
      );

  Widget _check(AppTokens t, MealPlanDraft d) {
    final ts = t.textStyles;
    final ok = d.checkPassed;
    return Container(
      padding: const EdgeInsets.all(AppTokens.sp12),
      decoration: BoxDecoration(
        color: ok ? AppTokens.successBg : AppTokens.warningBg,
        borderRadius: BorderRadius.circular(AppTokens.rMd),
        border: Border.all(color: (ok ? AppTokens.success : AppTokens.warning).withAlpha(90)),
      ),
      child: Row(
        children: [
          Container(
            width: 34,
            height: 34,
            decoration: BoxDecoration(color: ok ? AppTokens.success : AppTokens.warning, borderRadius: BorderRadius.circular(AppTokens.rSm)),
            child: Icon(ok ? Icons.check_rounded : Icons.priority_high_rounded, color: t.card),
          ),
          const SizedBox(width: AppTokens.sp10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(ok ? '已排好 ${d.days.length} 天 · 校验通过' : '已排好 · 有 ${d.notes.length} 处需留意',
                    style: ts.cardTitle.copyWith(color: ok ? AppTokens.successText : AppTokens.warningText)),
                Text(
                  [...d.notes, '${d.distinctDishes} 道不重样', '第 ${d.rounds} 轮通过'].join(' · '),
                  style: ts.caption.copyWith(color: ok ? AppTokens.successText : AppTokens.warningText),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _grid(AppTokens t, MealPlanDraft d) {
    final ts = t.textStyles;
    return Column(
      children: [
        Row(children: [
          const SizedBox(width: 52),
          Expanded(child: Text('午餐', style: ts.sectionLabel.copyWith(color: t.body))),
          const SizedBox(width: AppTokens.sp6),
          Expanded(child: Text('晚餐', style: ts.sectionLabel.copyWith(color: t.body))),
        ]),
        const SizedBox(height: AppTokens.sp6),
        for (final day in d.days)
          Padding(
            padding: const EdgeInsets.only(bottom: AppTokens.sp6),
            child: IntrinsicHeight(
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  SizedBox(
                    width: 52,
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Text(day.weekday, style: ts.cardTitle.copyWith(color: t.title)),
                        Text(md(day.date), style: ts.meta.copyWith(color: t.body, fontWeight: FontWeight.w500)),
                        if (day.note != null) ...[
                          const SizedBox(height: 2),
                          ToneBadge(day.note!, tone: BadgeTone.warning),
                        ],
                      ],
                    ),
                  ),
                  Expanded(child: _cell(t, day.slot('LUNCH'))),
                  const SizedBox(width: AppTokens.sp6),
                  Expanded(child: _cell(t, day.slot('DINNER'))),
                ],
              ),
            ),
          ),
      ],
    );
  }

  Widget _cell(AppTokens t, PlanSlot? s) {
    final ts = t.textStyles;
    if (s == null) {
      return Container(
        height: 56,
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(AppTokens.rMd),
          border: Border.all(color: t.border),
        ),
        alignment: Alignment.center,
        child: Text('不做', style: ts.caption),
      );
    }
    final busy = _swapping == s.slotId;
    return Material(
      color: t.card,
      borderRadius: BorderRadius.circular(AppTokens.rMd),
      child: InkWell(
        key: Key('plan-slot-${s.slotId}'),
        borderRadius: BorderRadius.circular(AppTokens.rMd),
        onTap: busy ? null : () => _swap(s),
        onLongPress: () => context.push('/dish/${s.dish.id}'),
        child: Container(
          padding: const EdgeInsets.all(AppTokens.sp6),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(AppTokens.rMd),
            border: Border.all(color: busy ? t.primaryDeep : t.border),
          ),
          child: Row(
            children: [
              DishImage(url: s.dish.coverUrl, name: s.dish.name, width: 40, height: 40, radius: AppTokens.rSm),
              const SizedBox(width: AppTokens.sp6),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Text(s.dish.name, maxLines: 1, overflow: TextOverflow.ellipsis, style: ts.caption.copyWith(color: t.title, fontWeight: FontWeight.w700)),
                    if (busy)
                      Text('找替换中…', style: ts.meta.copyWith(color: t.primaryDeep))
                    else if (s.baby)
                      const ToneBadge('宝宝版', tone: BadgeTone.info)
                    else if (s.expiring)
                      const ToneBadge('快到期', tone: BadgeTone.warning),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _footer(AppTokens t, MealPlanDraft d) {
    final ts = t.textStyles;
    Widget stat(String v, String l, {Color? c}) => Expanded(
          child: Column(children: [
            Text(v, style: ts.subtitle.copyWith(color: c ?? t.title)),
            Text(l, style: ts.caption),
          ]),
        );
    return Container(
      padding: EdgeInsets.fromLTRB(AppTokens.sp16, AppTokens.sp10, AppTokens.sp16, AppTokens.sp10 + MediaQuery.of(context).padding.bottom),
      decoration: BoxDecoration(color: t.card, border: Border(top: BorderSide(color: t.border))),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Row(children: [
            stat(yuan(d.estCostFen), '预计采购'),
            stat('${d.newItems} 样', '需要新买'),
            stat('${d.usesExpiring} 样', '用掉快到期', c: AppTokens.successText),
          ]),
          const SizedBox(height: AppTokens.sp10),
          SizedBox(
            width: double.infinity,
            child: ElevatedButton(
              key: const Key('plan-apply'),
              onPressed: _applying ? null : _apply,
              child: Text(_applying ? '生成中…' : '生成 ${d.days.length} 个食集和采购清单'),
            ),
          ),
        ],
      ),
    );
  }
}
