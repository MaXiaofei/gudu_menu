import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../core/app_theme.dart';
import '../../core/image_helper.dart';
import '../../models/dish.dart';
import '../../services/recommend_service.dart';
import '../../widgets/error_view.dart';
import '../../widgets/initial_avatar.dart';
import '../../widgets/loading_empty.dart';
import '../../widgets/status_chip.dart';

/// 智荐 tab 首页分区（G4/G5）：临期主推 / 你常做 / 季节鲜（GET /recommend/home）
/// + 最近做过（GET /cookbook/recent）。
///
/// - 加载中：LoadingView 骨架；
/// - 失败：ErrorView + 重试；
/// - 分区无数据：隐藏该分区；全部为空：整块不渲染（下方语义找菜照常可用）。
class SmartHomeSections extends StatefulWidget {
  const SmartHomeSections({super.key});

  @override
  State<SmartHomeSections> createState() => SmartHomeSectionsState();
}

class SmartHomeSectionsState extends State<SmartHomeSections> {
  RecommendHome? _home;
  List<RecentCooked> _recent = const [];
  bool _loading = true;
  bool _failed = false;

  @override
  void initState() {
    super.initState();
    reload();
  }

  /// 重新拉取（下拉刷新 / 重试）。
  Future<void> reload() async {
    setState(() {
      _loading = true;
      _failed = false;
    });
    try {
      final results = await Future.wait([
        RecommendService.home(),
        RecommendService.recent(limit: 10),
      ]);
      if (!mounted) return;
      setState(() {
        _home = results[0] as RecommendHome;
        _recent = results[1] as List<RecentCooked>;
        _loading = false;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _failed = true;
        _loading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    if (_loading) return const LoadingView();
    if (_failed) {
      return ErrorView(text: '智荐加载失败', onRetry: reload);
    }
    final h = _home ?? const RecommendHome();
    final children = <Widget>[
      if (h.expiring.isNotEmpty || h.expiringPicks.isNotEmpty)
        _section(
          t,
          key: 'expiring',
          title: '临期主推',
          sub: '这些快到期了，先吃掉它们',
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              if (h.expiring.isNotEmpty)
                Wrap(
                  spacing: AppTokens.sp6,
                  runSpacing: AppTokens.sp6,
                  children: [
                    for (final e in h.expiring)
                      StatusChip(
                        label: '${e.ingredientName} · ${e.daysText}',
                        color: expiryColor(t, e.daysLeft),
                      ),
                  ],
                ),
              if (h.expiringPicks.isNotEmpty) ...[
                const SizedBox(height: AppTokens.sp8),
                for (final d in h.expiringPicks) _DishRow(card: d),
              ],
            ],
          ),
        ),
      if (h.frequent.isNotEmpty)
        _section(t,
            key: 'frequent',
            title: '你常做',
            child: _DishStrip(cards: h.frequent)),
      if (h.seasonal.isNotEmpty)
        _section(t,
            key: 'seasonal',
            title: '季节鲜',
            sub: '当季食材，正是好吃的时候',
            child: _DishStrip(cards: h.seasonal)),
      if (_recent.isNotEmpty)
        _section(
          t,
          key: 'recent',
          title: '最近做过',
          child: SizedBox(
            height: 112,
            child: ListView.separated(
              scrollDirection: Axis.horizontal,
              itemCount: _recent.length,
              separatorBuilder: (_, __) => const SizedBox(width: AppTokens.sp8),
              itemBuilder: (_, i) {
                final r = _recent[i];
                return _MiniCard(
                  dishId: r.dishId,
                  name: r.name,
                  cover: r.coverImage,
                  meta: '${r.lastCookedLabel()} · 做过 ${r.cookedCount} 次',
                );
              },
            ),
          ),
        ),
    ];
    if (children.isEmpty) return const SizedBox.shrink();
    return Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: children);
  }

  Widget _section(AppTokens t,
      {required String key, required String title, String? sub, required Widget child}) {
    return Padding(
      key: ValueKey('smart-section-$key'),
      padding: const EdgeInsets.only(bottom: AppTokens.sp16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              Text(title, style: t.textStyles.cardTitle.copyWith(color: t.title)),
              if (sub != null) ...[
                const SizedBox(width: AppTokens.sp8),
                Expanded(
                  child: Text(sub,
                      overflow: TextOverflow.ellipsis,
                      style: t.textStyles.sectionLabel),
                ),
              ],
            ],
          ),
          const SizedBox(height: AppTokens.sp8),
          child,
        ],
      ),
    );
  }
}

/// 横向菜卡条（你常做 / 季节鲜）。
class _DishStrip extends StatelessWidget {
  final List<DishCard> cards;
  const _DishStrip({required this.cards});

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: 112,
      child: ListView.separated(
        scrollDirection: Axis.horizontal,
        itemCount: cards.length,
        separatorBuilder: (_, __) => const SizedBox(width: AppTokens.sp8),
        itemBuilder: (_, i) {
          final c = cards[i];
          return _MiniCard(
            dishId: c.dishId,
            name: c.name,
            cover: c.coverImage,
            meta: c.coverage?.label.isNotEmpty == true
                ? c.coverage!.label
                : (c.cookedCount > 0 ? '做过 ${c.cookedCount} 次' : '没做过'),
          );
        },
      ),
    );
  }
}

/// 小方卡：封面（无图首字占位）+ 菜名 + 一行副信息。
class _MiniCard extends StatelessWidget {
  final int dishId;
  final String name;
  final String? cover;
  final String meta;
  const _MiniCard(
      {required this.dishId, required this.name, this.cover, required this.meta});

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    final url = cover;
    return GestureDetector(
      onTap: () => context.push('/dish/$dishId'),
      child: SizedBox(
        width: 96,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            ClipRRect(
              borderRadius: BorderRadius.circular(AppTokens.rMd),
              child: SizedBox(
                width: 96,
                height: 64,
                child: url != null && url.isNotEmpty
                    ? Image.network(
                        ImageHelper.toThumbnail(ImageHelper.toAbsolute(url)),
                        fit: BoxFit.cover,
                        errorBuilder: (_, __, ___) =>
                            InitialAvatar(name: name, size: 96, radius: 0),
                      )
                    : InitialAvatar(name: name, size: 96, radius: 0),
              ),
            ),
            const SizedBox(height: AppTokens.sp4),
            Text(name,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: t.textStyles.caption.copyWith(
                    color: t.title, fontWeight: FontWeight.w700)),
            Text(meta,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: t.textStyles.chip),
          ],
        ),
      ),
    );
  }
}

/// 临期主推里的菜行：菜名 + 家里够 X/Y 样。
class _DishRow extends StatelessWidget {
  final DishCard card;
  const _DishRow({required this.card});

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    final cov = card.coverage;
    return Padding(
      padding: const EdgeInsets.only(bottom: AppTokens.sp6),
      child: InkWell(
        onTap: () => context.push('/dish/${card.dishId}'),
        borderRadius: BorderRadius.circular(AppTokens.rMd),
        child: Container(
          padding: const EdgeInsets.symmetric(
              horizontal: AppTokens.sp12, vertical: AppTokens.sp10),
          decoration: BoxDecoration(
            color: t.card,
            borderRadius: BorderRadius.circular(AppTokens.rMd),
            border: Border.all(color: t.border),
          ),
          child: Row(
            children: [
              Expanded(child: Text(card.name, style: t.textStyles.cardTitle)),
              if (cov != null && cov.label.isNotEmpty)
                StatusChip(label: cov.label, color: coverageColor(t, cov)),
              Icon(Icons.chevron_right, size: 16, color: t.caption),
            ],
          ),
        ),
      ),
    );
  }
}
