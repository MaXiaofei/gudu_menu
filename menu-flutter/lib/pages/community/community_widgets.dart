import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../core/app_theme.dart';
import '../../services/community_service.dart';
import '../../widgets/dish_image.dart';
import '../../widgets/member_avatar.dart';

/// 瀑布流里的一张帖子卡：大图（按封面比例）、角标、标题、作者、赞数。
class PostTile extends StatelessWidget {
  final PostCard post;
  final double width;
  const PostTile({super.key, required this.post, required this.width});

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    final ts = t.textStyles;
    final h = width / post.coverRatio;
    final badge = post.copyCount > 0 ? '照着做 ${compactCount(post.copyCount)}' : (post.tags.isNotEmpty ? post.tags.first : null);
    return Material(
      color: t.card,
      borderRadius: BorderRadius.circular(AppTokens.rMd),
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        key: Key('post-${post.postId}'),
        onTap: () => context.push('/community/post/${post.postId}'),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Stack(
              children: [
                DishImage(url: post.coverUrl, name: post.title, width: width, height: h, radius: 0),
                if (badge != null)
                  Positioned(
                    left: AppTokens.sp6,
                    top: AppTokens.sp6,
                    child: _chip(t, badge, post.copyCount > 0 ? t.primaryDeep : AppTokens.photoScrim),
                  ),
                if (post.imageCount > 1)
                  Positioned(
                    right: AppTokens.sp6,
                    top: AppTokens.sp6,
                    child: _chip(t, '1/${post.imageCount}', AppTokens.photoScrim),
                  ),
              ],
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(AppTokens.sp8, AppTokens.sp8, AppTokens.sp8, AppTokens.sp8),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(post.title, maxLines: 2, overflow: TextOverflow.ellipsis,
                      style: ts.cardTitle.copyWith(color: t.title, height: 1.35)),
                  const SizedBox(height: AppTokens.sp6),
                  Row(
                    children: [
                      MemberAvatar(name: post.author.nickname, avatarUrl: post.author.avatarUrl, size: 18),
                      const SizedBox(width: AppTokens.sp4),
                      Expanded(
                        child: Text(post.author.nickname, maxLines: 1, overflow: TextOverflow.ellipsis, style: ts.caption),
                      ),
                      Icon(post.liked ? Icons.favorite_rounded : Icons.favorite_border_rounded,
                          size: 14, color: post.liked ? AppTokens.error : t.body),
                      const SizedBox(width: 2),
                      Text(compactCount(post.likeCount), style: ts.caption),
                    ],
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _chip(AppTokens t, String text, Color bg) => Container(
        padding: const EdgeInsets.symmetric(horizontal: AppTokens.sp6, vertical: 2),
        decoration: BoxDecoration(color: bg, borderRadius: BorderRadius.circular(AppTokens.rPill)),
        child: Text(text, style: t.textStyles.tiny.copyWith(color: AppTokens.onPhoto)),
      );
}

/// 双列瀑布流（非滚动容器，放进外层 ListView/CustomScrollView）。
class MasonryGrid extends StatelessWidget {
  final List<PostCard> posts;
  final double gap;
  const MasonryGrid({super.key, required this.posts, this.gap = AppTokens.sp10});

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(builder: (_, c) {
      final w = (c.maxWidth - gap) / 2;
      final split = splitMasonry([for (final p in posts) p.coverRatio]);
      Widget col(List<int> idx) => Column(
            children: [
              for (final i in idx) Padding(padding: EdgeInsets.only(bottom: gap), child: PostTile(post: posts[i], width: w)),
            ],
          );
      return Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(width: w, child: col(split.left)),
          SizedBox(width: gap),
          SizedBox(width: w, child: col(split.right)),
        ],
      );
    });
  }
}

/// 2–4 张菜图拼贴（话题横幅、话题头图）。
class PhotoCollage extends StatelessWidget {
  final List<String> urls;
  final double height;
  const PhotoCollage({super.key, required this.urls, required this.height});

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    if (urls.isEmpty) return Container(height: height, color: t.secondary);
    if (urls.length == 1) return DishImage(url: urls.first, name: '', height: height, width: double.infinity, radius: 0);
    final rest = urls.skip(1).take(2).toList();
    return SizedBox(
      height: height,
      child: Row(
        children: [
          Expanded(flex: 3, child: DishImage(url: urls.first, name: '', height: height, radius: 0)),
          const SizedBox(width: 2),
          Expanded(
            flex: 2,
            child: Column(
              children: [
                for (var i = 0; i < rest.length; i++) ...[
                  if (i > 0) const SizedBox(height: 2),
                  Expanded(child: DishImage(url: rest[i], name: '', width: double.infinity, radius: 0)),
                ],
              ],
            ),
          ),
        ],
      ),
    );
  }
}
