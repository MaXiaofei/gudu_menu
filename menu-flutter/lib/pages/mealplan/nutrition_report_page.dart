import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../../core/ai_label.dart';
import '../../core/app_theme.dart';
import '../../models/member.dart';
import '../../services/agent_service.dart' show isNotDeployed;
import '../../services/mealplan_service.dart';
import '../../stores/member_store.dart';
import '../../widgets/ai_badge.dart';
import '../../widgets/dish_image.dart';
import '../../widgets/error_view.dart';
import '../../widgets/illustration.dart';
import '../../widgets/loading_empty.dart';
import '../../widgets/member_avatar.dart';
import '../../widgets/nutrition_charts.dart';
import '../../widgets/segment_tabs.dart';
import '../../widgets/skeleton.dart';

/// 营养报告（原型 Nutrition.html）：成员切换 → 五维雷达（实际 vs 建议）→ 超标卡（来源菜 + 替换建议）
/// → 7 日钠趋势 → 做得好的项。
class NutritionReportPage extends StatefulWidget {
  final int? memberId;
  const NutritionReportPage({super.key, this.memberId});

  @override
  State<NutritionReportPage> createState() => _NutritionReportPageState();
}

class _NutritionReportPageState extends State<NutritionReportPage> {
  int? _memberId;
  int _range = 0; // 0 本周 / 1 本月
  NutritionReport? _r;
  Object? _error;
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _memberId = widget.memberId;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (_memberId == null) {
        final ms = _members();
        // 默认先看有特殊人群档案的成员（最需要关注的人）
        final pick = ms.where((m) => m.audiences.isNotEmpty).toList();
        _memberId = (pick.isNotEmpty ? pick.first : (ms.isNotEmpty ? ms.first : null))?.id;
      }
      _load();
    });
  }

  List<Member> _members() {
    try {
      return context.read<MemberStore>().members;
    } catch (_) {
      return const [];
    }
  }

  Future<void> _load() async {
    final id = _memberId;
    if (id == null) {
      setState(() => _loading = false);
      return;
    }
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final r = await MealPlanService.report(id, range: _range == 0 ? 'week' : 'month');
      if (mounted) setState(() => _r = r);
    } catch (e) {
      if (mounted) setState(() => _error = e);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    final ts = t.textStyles;
    List<Member> members = const [];
    try {
      members = context.watch<MemberStore>().members;
    } catch (_) {}
    return Scaffold(
      backgroundColor: t.bg,
      appBar: AppBar(
        backgroundColor: t.bg,
        surfaceTintColor: t.bg,
        title: Text('营养报告', style: ts.pageTitle),
        actions: [
          SizedBox(
            width: 132,
            child: Padding(
              padding: const EdgeInsets.only(right: AppTokens.sp12),
              child: SegmentTabs(
                labels: const ['本周', '本月'],
                index: _range,
                onChanged: (i) {
                  setState(() => _range = i);
                  _load();
                },
              ),
            ),
          ),
        ],
      ),
      body: ListView(
        key: const Key('nutrition-list'),
        padding: const EdgeInsets.fromLTRB(AppTokens.sp16, AppTokens.sp4, AppTokens.sp16, AppTokens.sp32),
        children: [
          if (members.isNotEmpty) _memberChips(t, members),
          const SizedBox(height: AppTokens.sp12),
          // AI 标识 + 免责提示（A.4）
          const AiBadge(disclaimer: true, source: AiSource.nutrition),
          const SizedBox(height: AppTokens.sp12),
          if (_loading)
            const SkeletonBox(height: 280, radius: AppTokens.rLg)
          else if (_error != null)
            isNotDeployed(_error!)
                ? const EmptyView(
                    text: '营养报告即将上线',
                    subtitle: '会根据食记按成员档案算每周摄入，超标给替换建议',
                    illustration: StateIllustration(keys: ['broccoli', 'fish', 'milk']),
                  )
                : ErrorView(text: '报告加载失败', onRetry: _load, error: _error)
          else if (_r == null)
            const EmptyView(text: '还没有家庭成员', subtitle: '先在「家庭成员」里添加档案')
          else
            ..._report(t, _r!),
        ],
      ),
    );
  }

  Widget _memberChips(AppTokens t, List<Member> members) {
    final ts = t.textStyles;
    return SizedBox(
      height: 48,
      child: ListView.separated(
        scrollDirection: Axis.horizontal,
        itemCount: members.length,
        separatorBuilder: (_, __) => const SizedBox(width: AppTokens.sp8),
        itemBuilder: (_, i) {
          final m = members[i];
          final on = m.id == _memberId;
          return InkWell(
            borderRadius: BorderRadius.circular(AppTokens.rPill),
            onTap: () {
              setState(() => _memberId = m.id);
              _load();
            },
            child: Container(
              padding: const EdgeInsets.fromLTRB(AppTokens.sp4, AppTokens.sp4, AppTokens.sp12, AppTokens.sp4),
              decoration: BoxDecoration(
                color: on ? t.highlight : t.card,
                borderRadius: BorderRadius.circular(AppTokens.rPill),
                border: Border.all(color: on ? t.primaryDeep : t.border, width: on ? 1.5 : 1),
              ),
              child: Row(children: [
                MemberAvatar(name: m.name, avatarUrl: m.avatarUrl, size: 36, baby: m.isBaby),
                const SizedBox(width: AppTokens.sp6),
                Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Text(m.name, style: ts.caption.copyWith(color: t.title, fontWeight: FontWeight.w700)),
                    if (m.audiences.isNotEmpty) Text(m.audiences.first, style: ts.meta.copyWith(color: AppTokens.errorText)),
                  ],
                ),
              ]),
            ),
          );
        },
      ),
    );
  }

  List<Widget> _report(AppTokens t, NutritionReport r) {
    final ts = t.textStyles;
    final overCount = r.radar.where((a) => a.over).length;
    final good = r.radar.length - overCount;
    return [
      Container(
        padding: const EdgeInsets.all(AppTokens.sp16),
        decoration: BoxDecoration(
          color: t.card,
          borderRadius: BorderRadius.circular(AppTokens.rLg),
          border: Border.all(color: t.border),
          boxShadow: t.elevationSm,
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text.rich(TextSpan(children: [
              TextSpan(text: '${r.member.name}${_range == 0 ? '本周' : '本月'} $good 项达标'),
              if (overCount > 0)
                TextSpan(
                  text: ' · ${r.radar.where((a) => a.over).map((a) => a.label).join('、')}超标',
                  style: const TextStyle(color: AppTokens.errorText),
                ),
            ]), style: ts.subtitle),
            Text(
              '根据 ${r.recordCount} 条食记按每人份估算${r.from != null && r.to != null ? ' · ${md(r.from!)}–${md(r.to!)}' : ''}',
              style: ts.caption,
            ),
            const SizedBox(height: AppTokens.sp8),
            if (r.radar.length >= 3)
              NutritionRadar(
                labels: [for (final a in r.radar) a.label],
                ratios: [for (final a in r.radar) a.ratio],
                over: [for (final a in r.radar) a.over],
              ),
            Row(mainAxisAlignment: MainAxisAlignment.center, children: [
              Container(width: 14, height: 3, color: t.primary),
              const SizedBox(width: AppTokens.sp4),
              Text('实际摄入', style: ts.caption),
              const SizedBox(width: AppTokens.sp16),
              Container(width: 14, height: 1, color: t.border),
              const SizedBox(width: AppTokens.sp4),
              Text('${r.profile.isEmpty ? '' : r.profile}建议量', style: ts.caption),
            ]),
          ],
        ),
      ),
      for (final a in r.alerts) ...[const SizedBox(height: AppTokens.sp12), _alert(t, a)],
      if (r.trend.isNotEmpty) ...[
        const SizedBox(height: AppTokens.sp12),
        Container(
          padding: const EdgeInsets.all(AppTokens.sp16),
          decoration: BoxDecoration(
            color: t.card,
            borderRadius: BorderRadius.circular(AppTokens.rLg),
            border: Border.all(color: t.border),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(children: [
                Expanded(child: Text('近 ${r.trend.length} 天${_label(r.trendKey)}摄入', style: ts.cardTitle.copyWith(color: t.title))),
                Text('${r.trend.where((p) => r.trendLimit > 0 && p.value > r.trendLimit).length} 天超过建议', style: ts.caption),
              ]),
              const SizedBox(height: AppTokens.sp8),
              TrendLineChart(
                values: [for (final p in r.trend) p.value],
                xLabels: [for (final p in r.trend) weekdayNames[p.date.weekday - 1].substring(1)],
                limit: r.trendLimit,
                unit: 'g',
              ),
            ],
          ),
        ),
      ],
      if (r.goods.isNotEmpty) ...[
        const SizedBox(height: AppTokens.sp12),
        for (final g in r.goods)
          Container(
            margin: const EdgeInsets.only(bottom: AppTokens.sp8),
            padding: const EdgeInsets.all(AppTokens.sp12),
            decoration: BoxDecoration(color: AppTokens.successBg, borderRadius: BorderRadius.circular(AppTokens.rMd)),
            child: Row(children: [
              const Icon(Icons.thumb_up_alt_outlined, size: 18, color: AppTokens.successText),
              const SizedBox(width: AppTokens.sp8),
              Expanded(child: Text(g, style: ts.body.copyWith(color: AppTokens.successText))),
            ]),
          ),
      ],
    ];
  }

  String _label(String key) => switch (key) { 'sodium' => '钠', 'fat' => '脂肪', 'energy' => '能量', 'protein' => '蛋白质', 'fiber' => '膳食纤维', _ => key };

  Widget _alert(AppTokens t, NutritionAlert a) {
    final ts = t.textStyles;
    final ratio = a.limit > 0 ? (a.dailyAvg / a.limit) : 1.0;
    String fmt(double v) => v == v.roundToDouble() ? '${v.toInt()}' : v.toStringAsFixed(1);
    return Container(
      padding: const EdgeInsets.all(AppTokens.sp16),
      decoration: BoxDecoration(
        color: t.card,
        borderRadius: BorderRadius.circular(AppTokens.rLg),
        border: Border.all(color: AppTokens.error.withAlpha(90)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(children: [
            Container(
              padding: const EdgeInsets.symmetric(horizontal: AppTokens.sp6, vertical: 2),
              decoration: BoxDecoration(color: AppTokens.error, borderRadius: BorderRadius.circular(AppTokens.rXs)),
              child: Text('超标', style: ts.tiny.copyWith(color: AppTokens.onPhoto)),
            ),
            const SizedBox(width: AppTokens.sp8),
            Expanded(child: Text('${a.label} 日均 ${fmt(a.dailyAvg)}${a.unit}', style: ts.cardTitle.copyWith(color: t.title))),
            Text('建议 ≤${fmt(a.limit)}${a.unit}', style: ts.caption),
          ]),
          const SizedBox(height: AppTokens.sp10),
          LayoutBuilder(builder: (_, c) {
            final w = c.maxWidth;
            final fill = (ratio / 1.5).clamp(0.0, 1.0) * w;
            final mark = (1 / 1.5) * w;
            return SizedBox(
              height: 12,
              child: Stack(children: [
                Container(decoration: BoxDecoration(color: t.secondary, borderRadius: BorderRadius.circular(AppTokens.rPill))),
                Container(
                  width: fill,
                  decoration: BoxDecoration(
                    gradient: const LinearGradient(colors: [AppTokens.warning, AppTokens.error]),
                    borderRadius: BorderRadius.circular(AppTokens.rPill),
                  ),
                ),
                Positioned(left: mark - 1, top: 0, bottom: 0, child: Container(width: 2, color: t.title)),
              ]),
            );
          }),
          if (a.sources.isNotEmpty) ...[
            const SizedBox(height: AppTokens.sp12),
            Text('主要来自', style: ts.sectionLabel.copyWith(color: t.body)),
            const SizedBox(height: AppTokens.sp6),
            Wrap(spacing: AppTokens.sp8, runSpacing: AppTokens.sp8, children: [for (final d in a.sources) _dishChip(t, d.coverUrl, d.name)]),
          ],
          if (a.suggestions.isNotEmpty) ...[
            const SizedBox(height: AppTokens.sp12),
            Container(
              padding: const EdgeInsets.all(AppTokens.sp10),
              decoration: BoxDecoration(color: AppTokens.successBg, borderRadius: BorderRadius.circular(AppTokens.rMd)),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('下周换成 ${a.suggestions.map((d) => d.name).join('、')}', style: ts.caption.copyWith(color: AppTokens.successText, fontWeight: FontWeight.w700)),
                  const SizedBox(height: AppTokens.sp8),
                  Row(children: [
                    Expanded(
                      child: Wrap(spacing: AppTokens.sp8, runSpacing: AppTokens.sp8, children: [
                        for (final d in a.suggestions) _dishChip(t, d.coverUrl, d.name),
                      ]),
                    ),
                    ElevatedButton(
                      style: ElevatedButton.styleFrom(minimumSize: const Size(0, 36), padding: const EdgeInsets.symmetric(horizontal: AppTokens.sp10)),
                      onPressed: () => context.push('/mealplan'),
                      child: const Text('换进餐程'),
                    ),
                  ]),
                ],
              ),
            ),
          ],
        ],
      ),
    );
  }

  Widget _dishChip(AppTokens t, String? url, String name) => Container(
        padding: const EdgeInsets.fromLTRB(AppTokens.sp4, AppTokens.sp4, AppTokens.sp10, AppTokens.sp4),
        decoration: BoxDecoration(
          color: t.card,
          borderRadius: BorderRadius.circular(AppTokens.rPill),
          border: Border.all(color: t.border),
        ),
        child: Row(mainAxisSize: MainAxisSize.min, children: [
          ClipOval(child: DishImage(url: url, name: name, width: 26, height: 26, radius: 0)),
          const SizedBox(width: AppTokens.sp6),
          Text(name, style: t.textStyles.caption.copyWith(color: t.title)),
        ]),
      );
}
