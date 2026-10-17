import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../core/app_theme.dart';
import '../../models/cursor_page.dart';
import '../../services/agent_service.dart' show isNotDeployed;
import '../../services/community_service.dart';
import '../../widgets/error_view.dart';
import '../../widgets/illustration.dart';
import '../../widgets/loading_empty.dart';
import '../../widgets/me_avatar_button.dart';
import '../../widgets/member_avatar.dart';
import '../../widgets/segment_tabs.dart';
import '../../widgets/skeleton.dart';
import 'community_widgets.dart';

/// 厨房圈 Tab（原型 Community.html）：推荐 / 关注 / 话题，话题挑战横幅，双列瀑布流，游标加载。
class CommunityPage extends StatefulWidget {
  const CommunityPage({super.key});

  @override
  State<CommunityPage> createState() => _CommunityPageState();
}

class _CommunityPageState extends State<CommunityPage> {
  static const _tabs = [FeedTab.recommend, FeedTab.follow, FeedTab.topic];
  int _tab = 0;
  final _feed = CursorFeed<PostCard>(idOf: (p) => p.postId);
  final _scroll = ScrollController();
  CommunityBanner? _banner;
  bool _firstLoading = true;
  Object? _error;

  @override
  void initState() {
    super.initState();
    _scroll.addListener(_onScroll);
    _loadBanner();
    _reload();
  }

  @override
  void dispose() {
    _scroll.dispose();
    super.dispose();
  }

  Future<void> _loadBanner() async {
    try {
      final b = await CommunityService.banner();
      if (mounted) setState(() => _banner = b);
    } catch (_) {}
  }

  Future<void> _reload() async {
    _feed.reset();
    setState(() {
      _firstLoading = true;
      _error = null;
    });
    await _more();
    if (mounted) setState(() => _firstLoading = false);
  }

  Future<void> _more() async {
    final tab = _tabs[_tab];
    final topicId = tab == FeedTab.topic ? _banner?.topic?.topicId : null;
    try {
      await _feed.load((c) => CommunityService.feed(tab, topicId: topicId, cursor: c));
      if (mounted) setState(() {});
    } catch (e) {
      if (mounted) setState(() => _error = e);
    }
  }

  void _onScroll() {
    if (_scroll.position.pixels > _scroll.position.maxScrollExtent - 400 && _feed.hasMore && !_feed.loading) {
      _more();
    }
  }

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    final ts = t.textStyles;
    return Scaffold(
      backgroundColor: t.bg,
      body: SafeArea(
        bottom: false,
        child: Column(
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(AppTokens.sp16, AppTokens.sp12, AppTokens.sp16, 0),
              child: Row(
                children: [
                  Expanded(child: Text('厨房圈', style: ts.h2)),
                  ElevatedButton.icon(
                    key: const Key('community-publish'),
                    style: ElevatedButton.styleFrom(
                      minimumSize: const Size(0, 38),
                      padding: const EdgeInsets.symmetric(horizontal: AppTokens.sp12),
                      shape: const StadiumBorder(),
                    ),
                    onPressed: () async {
                      final ok = await context.push<bool>('/community/create');
                      if (ok == true) _reload();
                    },
                    icon: const Icon(Icons.add_rounded, size: 18),
                    label: const Text('发布'),
                  ),
                  const SizedBox(width: AppTokens.sp8),
                  const MeAvatarButton(),
                ],
              ),
            ),
            UnderlineTabs(
              labels: const ['推荐', '关注', '话题'],
              index: _tab,
              onChanged: (i) {
                if (i == _tab) return;
                setState(() => _tab = i);
                _reload();
              },
            ),
            Expanded(
              child: RefreshIndicator(
                color: t.primary,
                onRefresh: () async {
                  await _loadBanner();
                  await _reload();
                },
                child: ListView(
                  key: const Key('community-feed'),
                  controller: _scroll,
                  physics: const AlwaysScrollableScrollPhysics(),
                  padding: const EdgeInsets.fromLTRB(AppTokens.sp16, AppTokens.sp12, AppTokens.sp16, AppTokens.sp24),
                  children: [
                    if (_banner?.topic != null) ...[_topicBanner(t, _banner!.topic!), const SizedBox(height: AppTokens.sp12)],
                    if (_tab == 0 && (_banner?.followingUpdates.isNotEmpty ?? false)) ...[
                      _following(t, _banner!.followingUpdates),
                      const SizedBox(height: AppTokens.sp12),
                    ],
                    ..._body(t),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  List<Widget> _body(AppTokens t) {
    final ts = t.textStyles;
    if (_firstLoading) {
      return [
        const Row(children: [
          Expanded(child: SkeletonBox(height: 220, radius: AppTokens.rMd)),
          SizedBox(width: AppTokens.sp10),
          Expanded(child: SkeletonBox(height: 170, radius: AppTokens.rMd)),
        ]),
      ];
    }
    if (_error != null && _feed.items.isEmpty) {
      if (isNotDeployed(_error!)) {
        return const [
          SizedBox(height: 40),
          EmptyView(
            text: '厨房圈即将开放',
            subtitle: '晒菜、照着做、话题挑战都在路上，先去做一道吧',
            illustration: StateIllustration(keys: ['fish', 'egg', 'broccoli']),
          ),
        ];
      }
      return [ErrorView(text: '厨房圈加载失败', onRetry: _reload, error: _error)];
    }
    if (_feed.items.isEmpty) {
      return [
        const SizedBox(height: 40),
        EmptyView(
          text: _tab == 1 ? '还没关注任何厨友' : '这里还空着',
          subtitle: _tab == 1 ? '去推荐里看看，遇到喜欢的就关注' : '发布第一道菜，成为第一个分享的人',
          illustration: const StateIllustration(keys: ['egg', 'tomato', 'shrimp']),
        ),
      ];
    }
    return [
      MasonryGrid(posts: _feed.items),
      Padding(
        padding: const EdgeInsets.all(AppTokens.sp12),
        child: Center(child: Text(_feed.hasMore ? '加载中…' : '没有更多了', style: ts.caption)),
      ),
    ];
  }

  Widget _topicBanner(AppTokens t, Topic topic) {
    final ts = t.textStyles;
    final left = topic.daysLeft(DateTime.now());
    return Material(
      color: t.title,
      borderRadius: BorderRadius.circular(AppTokens.rLg),
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: () => context.push('/community/topic/${topic.topicId}'),
        child: SizedBox(
          height: 112,
          child: Row(
            children: [
              Expanded(
                child: Padding(
                  padding: const EdgeInsets.all(AppTokens.sp12),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Row(children: [
                        Icon(Icons.emoji_events_outlined, size: 14, color: t.primary),
                        const SizedBox(width: AppTokens.sp4),
                        Text('本周话题挑战', style: ts.xs.copyWith(color: t.primary)),
                      ]),
                      const SizedBox(height: AppTokens.sp4),
                      Text(topic.name, maxLines: 1, overflow: TextOverflow.ellipsis,
                          style: ts.h3.copyWith(color: AppTokens.onPhoto)),
                      const SizedBox(height: AppTokens.sp4),
                      Text(
                        '${compactCount(topic.participantCount)} 人参与${left == null ? '' : ' · 还剩 $left 天'}',
                        style: ts.caption.copyWith(color: AppTokens.onPhoto.withAlpha(200)),
                      ),
                    ],
                  ),
                ),
              ),
              SizedBox(width: 132, child: PhotoCollage(urls: topic.coverUrls, height: 112)),
            ],
          ),
        ),
      ),
    );
  }

  Widget _following(AppTokens t, List users) {
    final ts = t.textStyles;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text('关注的厨友刚更新', style: ts.sectionLabel.copyWith(color: t.body)),
        const SizedBox(height: AppTokens.sp8),
        SizedBox(
          height: 66,
          child: ListView.separated(
            scrollDirection: Axis.horizontal,
            itemCount: users.length,
            separatorBuilder: (_, __) => const SizedBox(width: AppTokens.sp12),
            itemBuilder: (_, i) => Column(
              children: [
                MemberAvatar(name: users[i].nickname, avatarUrl: users[i].avatarUrl, size: 44, ringColor: t.primary),
                const SizedBox(height: AppTokens.sp4),
                SizedBox(
                  width: 52,
                  child: Text(users[i].nickname, maxLines: 1, overflow: TextOverflow.ellipsis,
                      textAlign: TextAlign.center, style: ts.meta.copyWith(color: t.title)),
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }
}
