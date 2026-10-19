import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:share_plus/share_plus.dart';

import '../../core/app_theme.dart';
import '../../core/error_text.dart';
import '../../models/cursor_page.dart';
import '../../models/showcase_types.dart';
import '../../services/community_service.dart';
import '../../widgets/dish_image.dart';
import '../../widgets/error_view.dart';
import '../../widgets/hero_gallery.dart';
import '../../widgets/member_avatar.dart';
import '../../widgets/skeleton.dart';
import '../../widgets/tone_badge.dart';

/// 动态详情（原型 Post.html）：大图轮播 → 作者行 → 正文与话题 → 关联菜谱「照着做 · 复制到我家」
/// → 他们照着做了 → 两级评论；底部 评论输入 + 赞 / 收藏 / 分享。
class PostDetailPage extends StatefulWidget {
  final int postId;
  const PostDetailPage({super.key, required this.postId});

  @override
  State<PostDetailPage> createState() => _PostDetailPageState();
}

class _PostDetailPageState extends State<PostDetailPage> {
  PostDetail? _p;
  Object? _error;
  List<({UserLite user, String imageUrl})> _copies = const [];
  final _comments = CursorFeed<PostComment>(idOf: (c) => c.commentId);
  final _input = TextEditingController();
  final _inputFocus = FocusNode();
  PostComment? _replyTo;
  bool _busy = false;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _input.dispose();
    _inputFocus.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    setState(() => _error = null);
    try {
      final p = await CommunityService.detail(widget.postId);
      if (!mounted) return;
      setState(() => _p = p);
    } catch (e) {
      if (mounted) setState(() => _error = e);
      return;
    }
    try {
      final c = await CommunityService.copies(widget.postId);
      if (mounted) setState(() => _copies = c);
    } catch (_) {}
    try {
      await _comments.load((c) => CommunityService.comments(widget.postId, cursor: c));
      if (mounted) setState(() {});
    } catch (_) {}
  }

  Future<void> _react(String type) async {
    final p = _p;
    if (p == null) return;
    final on = type == 'LIKE' ? !p.card.liked : !p.collected;
    try {
      final r = await CommunityService.react(p.card.postId, type, on);
      if (!mounted) return;
      setState(() => _p = p.copyWith(
            card: p.card.withLike(r.liked, r.likeCount),
            collected: r.collected,
            collectCount: r.collectCount,
          ));
    } catch (e) {
      _toast('操作失败：${errorText(e)}');
    }
  }

  Future<void> _follow() async {
    final p = _p;
    if (p == null) return;
    try {
      final f = await CommunityService.follow(p.card.author.userId, !p.following);
      if (mounted) setState(() => _p = p.copyWith(following: f));
    } catch (e) {
      _toast('关注失败：${errorText(e)}');
    }
  }

  Future<void> _copyRecipe() async {
    final p = _p;
    if (p == null || _busy) return;
    setState(() => _busy = true);
    try {
      final dishId = await CommunityService.copyRecipe(p.card.postId);
      if (!mounted) return;
      setState(() => _p = p.copyWith(recipeCopyCount: p.recipeCopyCount + 1));
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(
        content: const Text('已复制到我家，可以按自家口味改'),
        action: SnackBarAction(label: '去看看', onPressed: () => context.push('/dish/$dishId')),
      ));
    } catch (e) {
      _toast('复制失败：${errorText(e)}');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _sendComment() async {
    final text = _input.text.trim();
    if (text.isEmpty) return;
    final parent = _replyTo;
    try {
      final c = await CommunityService.comment(widget.postId, text, parentId: parent?.commentId);
      if (!mounted) return;
      _input.clear();
      setState(() {
        _replyTo = null;
        if (parent == null) {
          _comments.items.insert(0, c);
        } else {
          final i = _comments.items.indexWhere((e) => e.commentId == parent.commentId);
          if (i >= 0) {
            final old = _comments.items[i];
            _comments.items[i] = PostComment(
              commentId: old.commentId,
              author: old.author,
              text: old.text,
              isAuthor: old.isAuthor,
              likeCount: old.likeCount,
              createdAt: old.createdAt,
              replies: [...old.replies, c],
            );
          }
        }
        _p = _p?.copyWith(commentCount: (_p?.commentCount ?? 0) + 1);
      });
      FocusScope.of(context).unfocus();
    } catch (e) {
      _toast('评论失败：${errorText(e)}');
    }
  }

  void _toast(String s) {
    if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(s)));
  }

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    final p = _p;
    if (p == null) {
      return Scaffold(
        backgroundColor: t.bg,
        appBar: AppBar(backgroundColor: t.bg),
        body: _error != null
            ? ErrorView(text: '动态加载失败', onRetry: _load, error: _error)
            : const Padding(
                padding: EdgeInsets.all(AppTokens.sp16),
                child: Column(children: [
                  SkeletonBox(height: 320, radius: AppTokens.rLg),
                  SizedBox(height: AppTokens.sp16),
                  SkeletonBox(height: 20),
                  SizedBox(height: AppTokens.sp8),
                  SkeletonBox(height: 60),
                ]),
              ),
      );
    }
    return Scaffold(
      backgroundColor: t.bg,
      body: Column(
        children: [
          Expanded(
            child: ListView(
              padding: EdgeInsets.zero,
              children: [
                HeroGallery(
                  urls: p.images,
                  name: p.card.title,
                  height: 380,
                  leading: HeroIconButton(icon: Icons.arrow_back_rounded, tooltip: '返回', onTap: () => context.pop()),
                  actions: [
                    HeroIconButton(
                      icon: Icons.ios_share_rounded,
                      tooltip: '分享',
                      onTap: () => Share.share('${p.card.title} —— 来自咕嘟小食单·厨房圈'),
                    ),
                    HeroIconButton(
                      icon: Icons.flag_outlined,
                      tooltip: '举报',
                      onTap: () => _report(p),
                    ),
                  ],
                ),
                Padding(
                  padding: const EdgeInsets.fromLTRB(AppTokens.sp16, AppTokens.sp12, AppTokens.sp16, 0),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      if (p.status != 'PUBLISHED')
                        Padding(
                          padding: const EdgeInsets.only(bottom: AppTokens.sp8),
                          child: ToneBadge(p.status == 'REVIEWING' ? '审核中，仅自己可见' : '已隐藏', tone: BadgeTone.warning),
                        ),
                      _authorRow(t, p),
                      const SizedBox(height: AppTokens.sp12),
                      Text(p.card.title, style: t.textStyles.h3),
                      const SizedBox(height: AppTokens.sp8),
                      Text(p.text, style: t.textStyles.body.copyWith(color: t.title, height: 1.7)),
                      if (p.topics.isNotEmpty) ...[
                        const SizedBox(height: AppTokens.sp8),
                        Wrap(spacing: AppTokens.sp8, children: [
                          for (final tp in p.topics)
                            InkWell(
                              onTap: () => context.push('/community/topic/${tp.topicId}'),
                              child: Text(tp.name.startsWith('#') ? tp.name : '#${tp.name}',
                                  style: t.textStyles.body.copyWith(color: t.primaryDeep, fontWeight: FontWeight.w600)),
                            ),
                        ]),
                      ],
                      const SizedBox(height: AppTokens.sp6),
                      Text(
                        [relativeTime(p.createdAt, DateTime.now()), if (p.city != null) p.city!].where((e) => e.isNotEmpty).join(' · '),
                        style: t.textStyles.caption,
                      ),
                      if (p.recipe != null) ...[const SizedBox(height: AppTokens.sp16), _recipeCard(t, p)],
                      if (_copies.isNotEmpty) ...[const SizedBox(height: AppTokens.sp16), _copiesRow(t)],
                      const SizedBox(height: AppTokens.sp20),
                      Text('评论 ${p.commentCount}', style: t.textStyles.subtitle),
                      const SizedBox(height: AppTokens.sp8),
                      if (_comments.items.isEmpty)
                        Padding(
                          padding: const EdgeInsets.symmetric(vertical: AppTokens.sp16),
                          child: Text('还没有评论，说说你的做法吧', style: t.textStyles.caption),
                        ),
                      for (final c in _comments.items) _commentTile(t, c),
                      if (_comments.hasMore && _comments.items.isNotEmpty)
                        TextButton(
                          onPressed: () async {
                            await _comments.load((c) => CommunityService.comments(widget.postId, cursor: c));
                            if (mounted) setState(() {});
                          },
                          child: const Text('查看更多评论'),
                        ),
                      const SizedBox(height: AppTokens.sp24),
                    ],
                  ),
                ),
              ],
            ),
          ),
          _bottomBar(t, p),
        ],
      ),
    );
  }

  Future<void> _report(PostDetail p) async {
    const reasons = ['广告或引流', '与做饭无关', '图片侵权', '不友善'];
    final r = await showModalBottomSheet<String>(
      context: context,
      builder: (ctx) => SafeArea(
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          for (final r in reasons) ListTile(title: Text(r), onTap: () => Navigator.pop(ctx, r)),
        ]),
      ),
    );
    if (r == null) return;
    try {
      await CommunityService.report(p.card.postId, r);
      _toast('已收到举报，我们会尽快处理');
    } catch (e) {
      _toast('举报失败：${errorText(e)}');
    }
  }

  Widget _authorRow(AppTokens t, PostDetail p) {
    final ts = t.textStyles;
    return Row(
      children: [
        MemberAvatar(name: p.card.author.nickname, avatarUrl: p.card.author.avatarUrl, size: 40),
        const SizedBox(width: AppTokens.sp10),
        Expanded(child: Text(p.card.author.nickname, style: ts.cardTitle.copyWith(color: t.title))),
        p.following
            ? OutlinedButton(
                style: OutlinedButton.styleFrom(minimumSize: const Size(72, 34)),
                onPressed: _follow,
                child: const Text('已关注'),
              )
            : ElevatedButton.icon(
                style: ElevatedButton.styleFrom(minimumSize: const Size(72, 34), shape: const StadiumBorder()),
                onPressed: _follow,
                icon: const Icon(Icons.add_rounded, size: 16),
                label: const Text('关注'),
              ),
      ],
    );
  }

  Widget _recipeCard(AppTokens t, PostDetail p) {
    final ts = t.textStyles;
    final d = p.recipe!;
    final cov = d.coverageText;
    return Container(
      padding: const EdgeInsets.all(AppTokens.sp12),
      decoration: BoxDecoration(
        color: t.card,
        borderRadius: BorderRadius.circular(AppTokens.rLg),
        border: Border.all(color: t.border),
        boxShadow: t.elevationSm,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          InkWell(
            onTap: () => context.push('/dish/${d.id}'),
            child: Row(
              children: [
                DishImage(url: d.coverUrl, name: d.name, width: 64, height: 64, radius: AppTokens.rSm),
                const SizedBox(width: AppTokens.sp12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('关联菜谱', style: ts.sectionLabel.copyWith(color: t.body)),
                      Text(d.name, style: ts.cardTitle.copyWith(color: t.title)),
                      Text([if (d.minutes != null) '${d.minutes} 分钟', if (d.difficulty != null) d.difficulty!].join(' · '),
                          style: ts.caption),
                    ],
                  ),
                ),
                Icon(Icons.chevron_right_rounded, color: t.body),
              ],
            ),
          ),
          if (cov != null) ...[
            const SizedBox(height: AppTokens.sp10),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: AppTokens.sp10, vertical: AppTokens.sp6),
              decoration: BoxDecoration(
                color: d.fullyCovered ? AppTokens.successBg : AppTokens.warningBg,
                borderRadius: BorderRadius.circular(AppTokens.rSm),
              ),
              child: Text(
                '你家够 ${d.have}/${d.total} 样${d.missing.isEmpty ? '' : '，只差${d.missing.take(2).join('、')}'}',
                style: ts.caption.copyWith(color: d.fullyCovered ? AppTokens.successText : AppTokens.warningText),
              ),
            ),
          ],
          const SizedBox(height: AppTokens.sp10),
          ElevatedButton.icon(
            key: const Key('post-copy-recipe'),
            onPressed: _busy ? null : _copyRecipe,
            icon: const Icon(Icons.content_copy_rounded, size: 18),
            label: Text(_busy ? '复制中…' : '照着做 · 复制到我家'),
          ),
          const SizedBox(height: AppTokens.sp6),
          Center(child: Text('已有 ${compactCount(p.recipeCopyCount)} 人照着做', style: ts.caption)),
        ],
      ),
    );
  }

  Widget _copiesRow(AppTokens t) {
    final ts = t.textStyles;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text('他们照着做了', style: ts.subtitle),
        const SizedBox(height: AppTokens.sp8),
        SizedBox(
          height: 112,
          child: ListView.separated(
            scrollDirection: Axis.horizontal,
            itemCount: _copies.length,
            separatorBuilder: (_, __) => const SizedBox(width: AppTokens.sp8),
            itemBuilder: (_, i) {
              final c = _copies[i];
              return SizedBox(
                width: 84,
                child: Column(children: [
                  DishImage(url: c.imageUrl, name: c.user.nickname, width: 84, height: 84, radius: AppTokens.rSm),
                  const SizedBox(height: AppTokens.sp4),
                  Row(children: [
                    MemberAvatar(name: c.user.nickname, avatarUrl: c.user.avatarUrl, size: 16),
                    const SizedBox(width: 2),
                    Expanded(child: Text(c.user.nickname, maxLines: 1, overflow: TextOverflow.ellipsis, style: ts.meta.copyWith(color: t.title))),
                  ]),
                ]),
              );
            },
          ),
        ),
      ],
    );
  }

  Widget _commentTile(AppTokens t, PostComment c, {bool reply = false}) {
    final ts = t.textStyles;
    return Padding(
      padding: EdgeInsets.only(left: reply ? 40 : 0, bottom: AppTokens.sp12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              MemberAvatar(name: c.author.nickname, avatarUrl: c.author.avatarUrl, size: reply ? 24 : 32),
              const SizedBox(width: AppTokens.sp8),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(children: [
                      Text(c.author.nickname, style: ts.caption.copyWith(color: t.body, fontWeight: FontWeight.w600)),
                      if (c.isAuthor) ...[const SizedBox(width: AppTokens.sp4), const ToneBadge('作者', tone: BadgeTone.info)],
                    ]),
                    const SizedBox(height: 2),
                    Text(c.text, style: ts.body.copyWith(color: t.title)),
                    Row(children: [
                      Text(relativeTime(c.createdAt, DateTime.now()), style: ts.meta.copyWith(color: t.body, fontWeight: FontWeight.w500)),
                      if (!reply)
                        TextButton(
                          style: TextButton.styleFrom(minimumSize: const Size(0, 28), padding: const EdgeInsets.symmetric(horizontal: AppTokens.sp8)),
                          onPressed: () {
                            setState(() => _replyTo = c);
                            _inputFocus.requestFocus();
                          },
                          child: Text('回复', style: ts.caption.copyWith(color: t.primaryDeep)),
                        ),
                    ]),
                  ],
                ),
              ),
            ],
          ),
          for (final r in c.replies) _commentTile(t, r, reply: true),
        ],
      ),
    );
  }

  Widget _bottomBar(AppTokens t, PostDetail p) {
    final ts = t.textStyles;
    Widget counter(IconData icon, IconData on, bool active, int n, Color activeColor, VoidCallback onTap, String label) =>
        InkResponse(
          onTap: onTap,
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: AppTokens.sp8),
            child: Column(mainAxisSize: MainAxisSize.min, children: [
              Icon(active ? on : icon, color: active ? activeColor : t.title, size: 22, semanticLabel: label),
              Text(compactCount(n), style: ts.meta.copyWith(color: t.body, fontWeight: FontWeight.w600)),
            ]),
          ),
        );
    return Container(
      padding: EdgeInsets.fromLTRB(AppTokens.sp12, AppTokens.sp8, AppTokens.sp8, AppTokens.sp8 + MediaQuery.of(context).padding.bottom),
      decoration: BoxDecoration(color: t.card, border: Border(top: BorderSide(color: t.border))),
      child: Row(
        children: [
          Expanded(
            child: TextField(
              controller: _input,
              focusNode: _inputFocus,
              textInputAction: TextInputAction.send,
              onSubmitted: (_) => _sendComment(),
              style: ts.input,
              decoration: InputDecoration(
                isDense: true,
                hintText: _replyTo == null ? '说点什么…' : '回复 ${_replyTo!.author.nickname}',
                prefixIcon: Icon(Icons.edit_outlined, size: 18, color: t.body),
                filled: true,
                fillColor: t.bg,
                border: OutlineInputBorder(borderRadius: BorderRadius.circular(AppTokens.rPill), borderSide: BorderSide.none),
                enabledBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(AppTokens.rPill), borderSide: BorderSide.none),
                focusedBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(AppTokens.rPill), borderSide: BorderSide(color: t.primary)),
              ),
            ),
          ),
          counter(Icons.favorite_border_rounded, Icons.favorite_rounded, p.card.liked, p.card.likeCount, AppTokens.error,
              () => _react('LIKE'), '赞'),
          counter(Icons.star_border_rounded, Icons.star_rounded, p.collected, p.collectCount, AppTokens.warning,
              () => _react('COLLECT'), '收藏'),
        ],
      ),
    );
  }
}
