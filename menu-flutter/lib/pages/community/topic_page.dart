import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../core/app_theme.dart';
import '../../models/cursor_page.dart';
import '../../services/community_service.dart';
import '../../services/mealplan_service.dart' show md, mondayOf, weekdayNames;
import '../../widgets/dish_image.dart';
import '../../widgets/error_view.dart';
import '../../widgets/hero_gallery.dart';
import '../../widgets/member_avatar.dart';
import '../../widgets/skeleton.dart';
import 'community_widgets.dart';
import 'post_create_page.dart';

/// 话题挑战（原型 Topic.html）：头图拼贴 + 规则与统计 → 我的本周进度（周一至周日）
/// → 发布今天的作品 / 问咕嘟做什么 → 打卡排行 → 最新作品瀑布流。
class TopicPage extends StatefulWidget {
  final int topicId;
  const TopicPage({super.key, required this.topicId});

  @override
  State<TopicPage> createState() => _TopicPageState();
}

class _TopicPageState extends State<TopicPage> {
  TopicDetail? _d;
  Object? _error;
  final _feed = CursorFeed<PostCard>(idOf: (p) => p.postId);

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() => _error = null);
    try {
      final d = await CommunityService.topic(widget.topicId);
      if (mounted) setState(() => _d = d);
    } catch (e) {
      if (mounted) setState(() => _error = e);
      return;
    }
    try {
      _feed.reset();
      await _feed.load((c) => CommunityService.feed(FeedTab.topic, topicId: widget.topicId, cursor: c));
      if (mounted) setState(() {});
    } catch (_) {}
  }

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    final ts = t.textStyles;
    final d = _d;
    if (d == null) {
      return Scaffold(
        backgroundColor: t.bg,
        appBar: AppBar(backgroundColor: t.bg),
        body: _error != null
            ? ErrorView(text: '话题加载失败', onRetry: _load, error: _error)
            : const Padding(padding: EdgeInsets.all(AppTokens.sp16), child: SkeletonBox(height: 260, radius: AppTokens.rLg)),
      );
    }
    final tp = d.topic;
    final left = tp.daysLeft(DateTime.now());
    return Scaffold(
      backgroundColor: t.bg,
      body: ListView(
        padding: EdgeInsets.zero,
        children: [
          SizedBox(
            height: 260,
            child: Stack(
              fit: StackFit.expand,
              children: [
                PhotoCollage(urls: tp.coverUrls, height: 260),
                const DecoratedBox(
                  decoration: BoxDecoration(
                    gradient: LinearGradient(
                      begin: Alignment.topCenter,
                      end: Alignment.bottomCenter,
                      colors: [AppTokens.photoScrim, Colors.transparent, AppTokens.photoScrimStrong],
                      stops: [0, 0.35, 1],
                    ),
                  ),
                ),
                Positioned(
                  left: AppTokens.sp12,
                  top: MediaQuery.of(context).padding.top + AppTokens.sp8,
                  child: HeroIconButton(icon: Icons.arrow_back_rounded, tooltip: '返回', onTap: () => context.pop()),
                ),
                Positioned(
                  left: AppTokens.sp16,
                  right: AppTokens.sp16,
                  bottom: AppTokens.sp16,
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('厨房圈话题挑战${tp.weekNo == null ? '' : ' · 第 ${tp.weekNo} 周'}${tp.startDate != null && tp.endDate != null ? ' · ${md(tp.startDate!)} – ${md(tp.endDate!)}' : ''}',
                          style: ts.xs.copyWith(color: AppTokens.onPhoto.withAlpha(220))),
                      const SizedBox(height: AppTokens.sp4),
                      Text(tp.name, style: ts.h1.copyWith(color: AppTokens.onPhoto)),
                      if (tp.rule.isNotEmpty || tp.intro.isNotEmpty) ...[
                        const SizedBox(height: AppTokens.sp4),
                        Text(tp.rule.isNotEmpty ? tp.rule : tp.intro, maxLines: 2, overflow: TextOverflow.ellipsis,
                            style: ts.caption.copyWith(color: AppTokens.onPhoto)),
                      ],
                      const SizedBox(height: AppTokens.sp8),
                      Wrap(spacing: AppTokens.sp6, children: [
                        _stat(t, Icons.people_outline, '${compactCount(tp.participantCount)} 人参与'),
                        _stat(t, Icons.photo_library_outlined, '${compactCount(tp.postCount)} 作品'),
                        if (left != null) _stat(t, Icons.schedule_rounded, '还剩 $left 天'),
                      ]),
                    ],
                  ),
                ),
              ],
            ),
          ),
          Padding(
            padding: const EdgeInsets.all(AppTokens.sp16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                _progress(t, d),
                const SizedBox(height: AppTokens.sp20),
                if (d.ranking.isNotEmpty) ...[
                  Row(children: [
                    Expanded(child: Text('打卡排行', style: ts.subtitle)),
                    Text('按打卡天数 · 获赞', style: ts.caption),
                  ]),
                  const SizedBox(height: AppTokens.sp8),
                  for (final r in d.ranking.take(5)) _rankRow(t, r),
                  if (d.myRank != null) _myRank(t, d.myRank!, d.doneCount),
                  const SizedBox(height: AppTokens.sp20),
                ],
                Text('最新作品', style: ts.subtitle),
                const SizedBox(height: AppTokens.sp8),
                if (_feed.items.isEmpty)
                  Padding(
                    padding: const EdgeInsets.symmetric(vertical: AppTokens.sp24),
                    child: Center(child: Text('还没有作品，来打第一张卡吧', style: ts.caption)),
                  )
                else
                  MasonryGrid(posts: _feed.items),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _stat(AppTokens t, IconData icon, String text) => Container(
        padding: const EdgeInsets.symmetric(horizontal: AppTokens.sp8, vertical: AppTokens.sp4),
        decoration: BoxDecoration(color: AppTokens.photoScrim, borderRadius: BorderRadius.circular(AppTokens.rPill)),
        child: Row(mainAxisSize: MainAxisSize.min, children: [
          Icon(icon, size: 13, color: AppTokens.onPhoto),
          const SizedBox(width: AppTokens.sp4),
          Text(text, style: t.textStyles.xs.copyWith(color: AppTokens.onPhoto)),
        ]),
      );

  Widget _progress(AppTokens t, TopicDetail d) {
    final ts = t.textStyles;
    final today = DateTime.now();
    final todayKey = DateTime(today.year, today.month, today.day);
    // 后端未给 days 时按自然周（周一至周日）补齐 7 格
    final days = d.days.isNotEmpty
        ? d.days
        : [
            for (var i = 0; i < 7; i++) TopicDay(mondayOf(today).add(Duration(days: i)), false, null),
          ];
    final doneToday = days.any((e) => e.done && DateUtils.isSameDay(e.date, todayKey));
    return Container(
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
          Text('我的进度', style: ts.sectionLabel.copyWith(color: t.body)),
          const SizedBox(height: AppTokens.sp4),
          Row(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              Text('本周已打卡 ', style: ts.body.copyWith(color: t.title)),
              Text('${d.doneCount}', style: ts.h2.copyWith(color: t.primaryDeep)),
              Text(' / ${days.length} 天', style: ts.body.copyWith(color: t.title)),
              const SizedBox(width: AppTokens.sp8),
              Expanded(
                child: Text(
                  d.doneCount >= days.length ? '已全勤' : '再打卡 ${days.length - d.doneCount} 天得徽章',
                  textAlign: TextAlign.right,
                  maxLines: 2,
                  style: ts.caption,
                ),
              ),
            ],
          ),
          const SizedBox(height: AppTokens.sp12),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              for (final day in days)
                Column(children: [
                  _dayDot(t, day, DateUtils.isSameDay(day.date, todayKey)),
                  const SizedBox(height: AppTokens.sp4),
                  Text(DateUtils.isSameDay(day.date, todayKey) ? '今天' : weekdayNames[day.date.weekday - 1],
                      style: ts.meta.copyWith(color: DateUtils.isSameDay(day.date, todayKey) ? t.primaryDeep : t.body)),
                ]),
            ],
          ),
          const SizedBox(height: AppTokens.sp16),
          Row(children: [
            Expanded(
              child: ElevatedButton.icon(
                onPressed: doneToday
                    ? null
                    : () => context.push('/community/create',
                        extra: PostDraftSeed(topicId: d.topic.topicId, topicName: d.topic.name)),
                icon: const Icon(Icons.add_rounded, size: 18),
                label: Text(doneToday ? '今天已打卡' : '发布今天的作品'),
              ),
            ),
            const SizedBox(width: AppTokens.sp10),
            OutlinedButton.icon(
              style: OutlinedButton.styleFrom(minimumSize: const Size(0, 48)),
              onPressed: () => context.push('/agent/chat', extra: {'text': '${d.topic.name} 今天做什么不重样？'}),
              icon: const Icon(Icons.auto_awesome_rounded, size: 16),
              label: const Text('问咕嘟做什么'),
            ),
          ]),
        ],
      ),
    );
  }

  Widget _dayDot(AppTokens t, TopicDay day, bool today) {
    const size = 38.0;
    if (day.done) {
      return Stack(clipBehavior: Clip.none, children: [
        ClipOval(child: DishImage(url: day.coverUrl, name: '打卡', width: size, height: size, radius: 0)),
        Positioned(
          right: -2,
          bottom: -2,
          child: Container(
            width: 16,
            height: 16,
            decoration: BoxDecoration(color: AppTokens.success, shape: BoxShape.circle, border: Border.all(color: t.card, width: 1.5)),
            child: Icon(Icons.check_rounded, size: 10, color: t.card),
          ),
        ),
      ]);
    }
    return Container(
      width: size,
      height: size,
      decoration: BoxDecoration(
        shape: BoxShape.circle,
        color: today ? t.highlight : t.bg,
        border: Border.all(color: today ? t.primaryDeep : t.border, width: today ? 1.5 : 1),
      ),
      child: today ? Icon(Icons.add_rounded, size: 18, color: t.primaryDeep) : null,
    );
  }

  Widget _rankRow(AppTokens t, TopicRank r) {
    final ts = t.textStyles;
    final medal = switch (r.rank) { 1 => AppTokens.warning, 2 => t.border, 3 => t.primary, _ => null };
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: AppTokens.sp6),
      child: Row(children: [
        Container(
          width: 24,
          height: 24,
          alignment: Alignment.center,
          decoration: BoxDecoration(color: medal ?? t.bg, shape: BoxShape.circle),
          child: Text('${r.rank}', style: ts.xs.copyWith(color: medal != null && r.rank != 2 ? t.card : t.title)),
        ),
        const SizedBox(width: AppTokens.sp10),
        MemberAvatar(name: r.user.nickname, avatarUrl: r.user.avatarUrl, size: 34),
        const SizedBox(width: AppTokens.sp10),
        Expanded(
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(r.user.nickname, style: ts.cardTitle.copyWith(color: t.title)),
            Text('打卡 ${r.days} 天 · 获赞 ${compactCount(r.likeCount)}', style: ts.caption),
          ]),
        ),
        if (r.days >= 7) const Icon(Icons.workspace_premium_rounded, color: AppTokens.warning),
      ]),
    );
  }

  Widget _myRank(AppTokens t, int rank, int days) => Container(
        margin: const EdgeInsets.only(top: AppTokens.sp6),
        padding: const EdgeInsets.all(AppTokens.sp10),
        decoration: BoxDecoration(color: t.highlight, borderRadius: BorderRadius.circular(AppTokens.rMd)),
        child: Row(children: [
          Text('我 · 第 $rank 名', style: t.textStyles.cardTitle.copyWith(color: t.title)),
          const Spacer(),
          Text('已打卡 $days 天', style: t.textStyles.caption),
        ]),
      );
}
