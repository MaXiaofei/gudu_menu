import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../core/app_theme.dart';
import '../../models/dish.dart';
import '../../services/pantry_service.dart' show ExpiringItem;
import '../../services/recommend_service.dart';
import '../../widgets/dish_image.dart';
import '../../widgets/error_view.dart';
import '../../widgets/ingredient_icon.dart';
import '../../widgets/skeleton.dart';
import '../../widgets/tone_badge.dart';

/// 智荐首页分区（原型 Smart）：今晚首推大图卡 → 快到期的，先吃掉 → 你家常做 → 应季 → 最近做过。
/// 数据：GET /recommend/home + GET /cookbook/recent。
///
/// - 加载中：骨架；失败：ErrorView + 重试；
/// - 分区无数据隐藏；全部为空整块不渲染（下方语义找菜照常可用）。
class SmartHomeSections extends StatefulWidget {
  const SmartHomeSections({super.key});

  @override
  State<SmartHomeSections> createState() => SmartHomeSectionsState();
}

class SmartHomeSectionsState extends State<SmartHomeSections> {
  RecommendHome? _home;
  List<RecentCooked> _recent = const [];
  bool _loading = true;
  Object? _error;
  int _heroIndex = 0;

  @override
  void initState() {
    super.initState();
    reload();
  }

  /// 重新拉取（下拉刷新 / 重试）。
  Future<void> reload() async {
    setState(() {
      _loading = true;
      _error = null;
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
        _heroIndex = 0;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _error = e;
        _loading = false;
      });
    }
  }

  /// 首推候选：临期主推优先，其次你常做。
  List<DishCard> get _heroPool {
    final h = _home ?? const RecommendHome();
    return h.expiringPicks.isNotEmpty ? h.expiringPicks : h.frequent;
  }

  static String _month() => '${DateTime.now().month}';

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    if (_loading) {
      return const Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          SkeletonBox(height: 300, radius: AppTokens.rXl),
          SizedBox(height: AppTokens.sp16),
          SkeletonBox(width: 160, height: 16),
          SizedBox(height: AppTokens.sp12),
          SkeletonBox(height: 120, radius: AppTokens.rLg),
        ],
      );
    }
    if (_error != null) {
      return ErrorView(text: '智荐加载失败', onRetry: reload, error: _error, compact: true);
    }
    final h = _home ?? const RecommendHome();
    final pool = _heroPool;
    final hero = pool.isEmpty ? null : pool[_heroIndex % pool.length];
    final fromExpiring = h.expiringPicks.isNotEmpty;
    final restPicks = fromExpiring ? h.expiringPicks.where((d) => d != hero).toList() : h.expiringPicks;
    final frequent = fromExpiring ? h.frequent : h.frequent.where((d) => d != hero).toList();
    final children = <Widget>[
      if (hero != null)
        _HeroCard(
          card: hero,
          expiring: fromExpiring && h.expiring.isNotEmpty ? h.expiring.first : null,
          canSwap: pool.length > 1,
          onSwap: () => setState(() => _heroIndex++),
        ),
      if (h.expiring.isNotEmpty || restPicks.isNotEmpty)
        _section(
          t,
          key: 'expiring',
          title: '快到期的，先吃掉',
          action: ('余量', () => context.go('/pantry')),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              if (h.expiring.isNotEmpty)
                SizedBox(
                  height: 44,
                  child: ListView.separated(
                    scrollDirection: Axis.horizontal,
                    itemCount: h.expiring.length,
                    separatorBuilder: (_, __) => const SizedBox(width: AppTokens.sp8),
                    itemBuilder: (_, i) => _ExpiringPill(item: h.expiring[i]),
                  ),
                ),
              if (restPicks.isNotEmpty) ...[
                const SizedBox(height: AppTokens.sp12),
                _DishStrip(cards: restPicks, subtitle: (c) => c.coverage?.label ?? ''),
              ],
            ],
          ),
        ),
      if (frequent.isNotEmpty)
        _section(t,
            key: 'frequent',
            title: '你家常做',
            child: _DishStrip(
                cards: frequent,
                subtitle: (c) => c.cookedCount > 0 ? '做过 ${c.cookedCount} 次' : (c.coverage?.label ?? ''))),
      if (h.seasonal.isNotEmpty)
        _section(t, key: 'seasonal', title: '${_month()}月应季', child: _SeasonStrip(cards: h.seasonal)),
      if (_recent.isNotEmpty)
        _section(
          t,
          key: 'recent',
          title: '最近做过',
          action: ('食记', () => context.push('/food-log')),
          child: Column(children: [for (final r in _recent.take(3)) _RecentRow(r: r)]),
        ),
    ];
    if (children.isEmpty) return const SizedBox.shrink();
    return Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: children);
  }

  Widget _section(AppTokens t,
      {required String key, required String title, (String, VoidCallback)? action, required Widget child}) {
    final ts = t.textStyles;
    return Padding(
      key: ValueKey('smart-section-$key'),
      padding: const EdgeInsets.only(top: AppTokens.sp24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            children: [
              Expanded(child: Text(title, style: ts.subtitle)),
              if (action != null)
                GestureDetector(
                  onTap: action.$2,
                  child: Text('${action.$1} ›', style: ts.caption.copyWith(color: t.primaryDeep, fontWeight: FontWeight.w700)),
                ),
            ],
          ),
          const SizedBox(height: AppTokens.sp12),
          child,
        ],
      ),
    );
  }
}

/// 今晚首推大图卡：满宽照片 + 渐变 + 到期徽章 + 现在就做 / 换一道。
class _HeroCard extends StatelessWidget {
  final DishCard card;
  final ExpiringItem? expiring;
  final bool canSwap;
  final VoidCallback onSwap;
  const _HeroCard({required this.card, this.expiring, required this.canSwap, required this.onSwap});

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    final ts = t.textStyles;
    final e = expiring;
    final cov = card.coverage;
    return GestureDetector(
      onTap: () => context.push('/dish/${card.dishId}'),
      child: Container(
        key: const ValueKey('smart-hero'),
        height: 320,
        decoration: BoxDecoration(borderRadius: BorderRadius.circular(AppTokens.rXl), boxShadow: t.elevationMd),
        child: ClipRRect(
          borderRadius: BorderRadius.circular(AppTokens.rXl),
          child: Stack(
            fit: StackFit.expand,
            children: [
              DishImage(url: card.coverImage, name: card.name, radius: 0, original: true),
              const DecoratedBox(
                decoration: BoxDecoration(
                  gradient: LinearGradient(
                    begin: Alignment.topCenter,
                    end: Alignment.bottomCenter,
                    stops: [0.3, 1],
                    colors: [Colors.transparent, AppTokens.photoScrimStrong],
                  ),
                ),
              ),
              Positioned(
                left: AppTokens.sp16,
                right: AppTokens.sp16,
                top: AppTokens.sp16,
                child: Row(
                  children: [
                    if (e != null)
                      ToneBadge('${e.ingredientName}${e.daysLeft <= 0 ? '今天到期' : e.daysLeft == 1 ? '明天到期' : '剩 ${e.daysLeft} 天'}',
                          tone: BadgeTone.solidError, icon: Icons.schedule_rounded),
                    const Spacer(),
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: AppTokens.sp10, vertical: AppTokens.sp4),
                      decoration: BoxDecoration(color: t.card, borderRadius: BorderRadius.circular(AppTokens.rPill)),
                      child: Text('今晚首推', style: ts.sectionLabel.copyWith(color: t.title)),
                    ),
                  ],
                ),
              ),
              Positioned(
                left: AppTokens.sp16,
                right: AppTokens.sp16,
                bottom: AppTokens.sp16,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(card.name, style: ts.h2.copyWith(color: AppTokens.onPhoto)),
                    const SizedBox(height: AppTokens.sp4),
                    Wrap(
                      spacing: AppTokens.sp8,
                      crossAxisAlignment: WrapCrossAlignment.center,
                      children: [
                        if (e != null) Text('先用掉快到期的${e.ingredientName}', style: ts.caption.copyWith(color: AppTokens.onPhoto)),
                        if (cov != null && cov.label.isNotEmpty)
                          Text(cov.label, style: ts.caption.copyWith(color: AppTokens.onPhoto, fontWeight: FontWeight.w700)),
                        if ((card.cookMinutes ?? 0) > 0)
                          Text('${card.cookMinutes} 分钟', style: ts.caption.copyWith(color: AppTokens.onPhoto)),
                      ],
                    ),
                    const SizedBox(height: AppTokens.sp12),
                    Row(
                      children: [
                        Expanded(
                          child: ElevatedButton(
                            onPressed: () => context.push('/dish/${card.dishId}'),
                            child: const Text('现在就做'),
                          ),
                        ),
                        if (canSwap) ...[
                          const SizedBox(width: AppTokens.sp10),
                          SizedBox(
                            width: 112,
                            child: OutlinedButton.icon(
                              style: OutlinedButton.styleFrom(
                                foregroundColor: AppTokens.onPhoto,
                                backgroundColor: AppTokens.photoScrim,
                                side: const BorderSide(color: AppTokens.onPhoto),
                              ),
                              onPressed: onSwap,
                              icon: const Icon(Icons.refresh_rounded, size: 18),
                              label: const Text('换一道'),
                            ),
                          ),
                        ],
                      ],
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// 临期食材胶囊：插画 + 名称 + 剩余天数。
class _ExpiringPill extends StatelessWidget {
  final ExpiringItem item;
  const _ExpiringPill({required this.item});

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    final badge = ToneBadge.expiry(item.daysLeft) ?? ToneBadge(item.daysText);
    return Container(
      padding: const EdgeInsets.fromLTRB(AppTokens.sp4, AppTokens.sp4, AppTokens.sp8, AppTokens.sp4),
      decoration: BoxDecoration(
        color: t.card,
        border: Border.all(color: t.border),
        borderRadius: BorderRadius.circular(AppTokens.rPill),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          IngredientIcon(name: item.ingredientName, size: 32, background: t.bg),
          const SizedBox(width: AppTokens.sp6),
          Text(item.ingredientName, style: t.textStyles.cardTitle.copyWith(color: t.title)),
          const SizedBox(width: AppTokens.sp6),
          badge,
        ],
      ),
    );
  }
}

/// 横滑菜卡：照片 4:3 + 菜名 + 副信息。
class _DishStrip extends StatelessWidget {
  final List<DishCard> cards;
  final String Function(DishCard) subtitle;
  const _DishStrip({required this.cards, required this.subtitle});

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    final ts = t.textStyles;
    return SizedBox(
      height: 178,
      child: ListView.separated(
        scrollDirection: Axis.horizontal,
        clipBehavior: Clip.none,
        itemCount: cards.length,
        separatorBuilder: (_, __) => const SizedBox(width: AppTokens.sp12),
        itemBuilder: (_, i) {
          final c = cards[i];
          final sub = subtitle(c);
          final cov = ToneBadge.coverage(c.coverage);
          return GestureDetector(
            onTap: () => context.push('/dish/${c.dishId}'),
            child: Container(
              width: 140,
              decoration: BoxDecoration(
                color: t.card,
                borderRadius: BorderRadius.circular(AppTokens.rLg),
                border: Border.all(color: t.border),
                boxShadow: t.elevationSm,
              ),
              clipBehavior: Clip.antiAlias,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  DishImage(url: c.coverImage, name: c.name, width: 140, height: 100, radius: 0),
                  Padding(
                    padding: const EdgeInsets.all(AppTokens.sp10),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(c.name, maxLines: 1, overflow: TextOverflow.ellipsis, style: ts.cardTitle.copyWith(color: t.title)),
                        const SizedBox(height: AppTokens.sp4),
                        if (cov != null && sub == c.coverage?.label)
                          cov
                        else if (sub.isNotEmpty)
                          Text(sub, maxLines: 1, overflow: TextOverflow.ellipsis, style: ts.caption),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          );
        },
      ),
    );
  }
}

/// 应季大图横滑卡：照片 + 底部渐变菜名。
class _SeasonStrip extends StatelessWidget {
  final List<DishCard> cards;
  const _SeasonStrip({required this.cards});

  @override
  Widget build(BuildContext context) {
    final ts = AppTokens.of(context).textStyles;
    return SizedBox(
      height: 150,
      child: ListView.separated(
        scrollDirection: Axis.horizontal,
        itemCount: cards.length,
        separatorBuilder: (_, __) => const SizedBox(width: AppTokens.sp12),
        itemBuilder: (_, i) {
          final c = cards[i];
          return GestureDetector(
            onTap: () => context.push('/dish/${c.dishId}'),
            child: ClipRRect(
              borderRadius: BorderRadius.circular(AppTokens.rLg),
              child: SizedBox(
                width: 220,
                child: Stack(
                  fit: StackFit.expand,
                  children: [
                    DishImage(url: c.coverImage, name: c.name, radius: 0),
                    const DecoratedBox(
                      decoration: BoxDecoration(
                        gradient: LinearGradient(
                          begin: Alignment.topCenter,
                          end: Alignment.bottomCenter,
                          stops: [0.45, 1],
                          colors: [Colors.transparent, AppTokens.photoScrim],
                        ),
                      ),
                    ),
                    Positioned(
                      left: AppTokens.sp12,
                      right: AppTokens.sp12,
                      bottom: AppTokens.sp10,
                      child: Text(c.name, style: ts.lg.copyWith(color: AppTokens.onPhoto, fontWeight: FontWeight.w700)),
                    ),
                  ],
                ),
              ),
            ),
          );
        },
      ),
    );
  }
}

/// 最近做过一行：照片 + 菜名 + 「今天 · 做过 N 次」+ 再做一次。
class _RecentRow extends StatelessWidget {
  final RecentCooked r;
  const _RecentRow({required this.r});

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    final ts = t.textStyles;
    return GestureDetector(
      onTap: () => context.push('/dish/${r.dishId}'),
      child: Container(
        margin: const EdgeInsets.only(bottom: AppTokens.sp10),
        padding: const EdgeInsets.all(AppTokens.sp10),
        decoration: BoxDecoration(
          color: t.card,
          borderRadius: BorderRadius.circular(AppTokens.rLg),
          border: Border.all(color: t.border),
        ),
        child: Row(
          children: [
            DishImage.square(url: r.coverImage, name: r.name, size: 56),
            const SizedBox(width: AppTokens.sp12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(r.name, style: ts.cardTitle.copyWith(color: t.title)),
                  const SizedBox(height: 2),
                  Text('${r.lastCookedLabel()} · 做过 ${r.cookedCount} 次', style: ts.caption),
                ],
              ),
            ),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: AppTokens.sp12, vertical: AppTokens.sp6),
              decoration: BoxDecoration(
                color: t.secondary,
                borderRadius: BorderRadius.circular(AppTokens.rPill),
              ),
              child: Text('再做一次', style: ts.sectionLabel.copyWith(color: t.primaryDeep)),
            ),
          ],
        ),
      ),
    );
  }
}
