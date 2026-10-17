import '../core/api_client.dart';
import '../models/cursor_page.dart';
import '../models/showcase_types.dart';

/// 厨房圈（contracts.md §2）。

class PostCard {
  final int postId;
  final UserLite author;
  final String title;
  final String coverUrl;

  /// 封面宽高比（宽 / 高）。瀑布流按它算卡片高度。
  final double coverRatio;
  final int imageCount;
  final int likeCount;
  final bool liked;
  final int copyCount;
  final List<String> tags;

  const PostCard({
    required this.postId,
    required this.author,
    required this.title,
    required this.coverUrl,
    this.coverRatio = 0.75,
    this.imageCount = 1,
    this.likeCount = 0,
    this.liked = false,
    this.copyCount = 0,
    this.tags = const [],
  });

  factory PostCard.fromJson(Map<String, dynamic> j) {
    final r = Json.d(j['coverRatio'], 0.75);
    return PostCard(
      postId: Json.i(j['postId']),
      author: UserLite.fromJson(j['author']),
      title: Json.s(j['title']),
      coverUrl: Json.s(j['coverUrl']),
      // 极端比例会让瀑布流某一列过长：限定在 3:4.6 ~ 4:3 之间
      coverRatio: r <= 0 ? 0.75 : r.clamp(0.65, 1.34).toDouble(),
      imageCount: Json.i(j['imageCount'], 1),
      likeCount: Json.i(j['likeCount']),
      liked: Json.b(j['liked']),
      copyCount: Json.i(j['copyCount']),
      tags: Json.strs(j['tags']),
    );
  }

  PostCard withLike(bool on, int count) => PostCard(
        postId: postId,
        author: author,
        title: title,
        coverUrl: coverUrl,
        coverRatio: coverRatio,
        imageCount: imageCount,
        likeCount: count,
        liked: on,
        copyCount: copyCount,
        tags: tags,
      );
}

class TopicRef {
  final int topicId;
  final String name;
  const TopicRef(this.topicId, this.name);
  factory TopicRef.fromJson(Map<String, dynamic> j) => TopicRef(Json.i(j['topicId']), Json.s(j['name']));
}

class PostDetail {
  final PostCard card;
  final String text;
  final List<String> images;
  final List<TopicRef> topics;
  final DishCard? recipe;
  final int recipeCopyCount;
  final int commentCount;
  final int collectCount;
  final bool collected;
  final bool following;
  final DateTime? createdAt;
  final String? city;

  /// PUBLISHED / REVIEWING / HIDDEN
  final String status;

  const PostDetail({
    required this.card,
    this.text = '',
    this.images = const [],
    this.topics = const [],
    this.recipe,
    this.recipeCopyCount = 0,
    this.commentCount = 0,
    this.collectCount = 0,
    this.collected = false,
    this.following = false,
    this.createdAt,
    this.city,
    this.status = 'PUBLISHED',
  });

  factory PostDetail.fromJson(Map<String, dynamic> j) {
    final r = j['recipe'];
    final imgs = Json.strs(j['images']);
    final card = PostCard.fromJson(j);
    return PostDetail(
      card: card,
      text: Json.s(j['text']),
      images: imgs.isEmpty && card.coverUrl.isNotEmpty ? [card.coverUrl] : imgs,
      topics: Json.maps(j['topics']).map(TopicRef.fromJson).toList(),
      recipe: r is Map ? DishCard.fromJson(Map<String, dynamic>.from(r)) : null,
      recipeCopyCount: r is Map ? Json.i(r['copyCount']) : 0,
      commentCount: Json.i(j['commentCount']),
      collectCount: Json.i(j['collectCount']),
      collected: Json.b(j['collected']),
      following: Json.b(j['following']),
      createdAt: Json.date(j['createdAt']),
      city: Json.sOrNull(j['city']),
      status: Json.s(j['status'], 'PUBLISHED'),
    );
  }

  PostDetail copyWith({PostCard? card, int? collectCount, bool? collected, bool? following, int? commentCount, int? recipeCopyCount}) =>
      PostDetail(
        card: card ?? this.card,
        text: text,
        images: images,
        topics: topics,
        recipe: recipe,
        recipeCopyCount: recipeCopyCount ?? this.recipeCopyCount,
        commentCount: commentCount ?? this.commentCount,
        collectCount: collectCount ?? this.collectCount,
        collected: collected ?? this.collected,
        following: following ?? this.following,
        createdAt: createdAt,
        city: city,
        status: status,
      );
}

class PostComment {
  final int commentId;
  final UserLite author;
  final String text;
  final bool isAuthor;
  final int likeCount;
  final DateTime? createdAt;
  final List<PostComment> replies;
  const PostComment({
    required this.commentId,
    required this.author,
    required this.text,
    this.isAuthor = false,
    this.likeCount = 0,
    this.createdAt,
    this.replies = const [],
  });

  factory PostComment.fromJson(Map<String, dynamic> j) => PostComment(
        commentId: Json.i(j['commentId']),
        author: UserLite.fromJson(j['author']),
        text: Json.s(j['text']),
        isAuthor: Json.b(j['isAuthor']),
        likeCount: Json.i(j['likeCount']),
        createdAt: Json.date(j['createdAt']),
        replies: Json.maps(j['replies']).map(PostComment.fromJson).toList(),
      );
}

class Topic {
  final int topicId;
  final String name;
  final String intro;
  final String rule;
  final DateTime? startDate;
  final DateTime? endDate;
  final int participantCount;
  final int postCount;
  final List<String> coverUrls;
  const Topic({
    required this.topicId,
    required this.name,
    this.intro = '',
    this.rule = '',
    this.startDate,
    this.endDate,
    this.participantCount = 0,
    this.postCount = 0,
    this.coverUrls = const [],
  });

  factory Topic.fromJson(Map<String, dynamic> j) => Topic(
        topicId: Json.i(j['topicId']),
        name: Json.s(j['name']),
        intro: Json.s(j['intro']),
        rule: Json.s(j['rule']),
        startDate: Json.date(j['startDate']),
        endDate: Json.date(j['endDate']),
        participantCount: Json.i(j['participantCount']),
        postCount: Json.i(j['postCount']),
        coverUrls: Json.strs(j['coverUrls']),
      );

  /// 距结束还剩几天（含当天）；无结束日返回 null。
  int? daysLeft(DateTime today) {
    if (endDate == null) return null;
    final a = DateTime(today.year, today.month, today.day);
    final b = DateTime(endDate!.year, endDate!.month, endDate!.day);
    final d = b.difference(a).inDays + 1;
    return d < 0 ? 0 : d;
  }
}

class TopicDay {
  final DateTime date;
  final bool done;
  final String? coverUrl;
  const TopicDay(this.date, this.done, this.coverUrl);
}

class TopicRank {
  final UserLite user;
  final int days;
  final int likeCount;
  final int rank;
  const TopicRank(this.user, this.days, this.likeCount, this.rank);
}

class TopicDetail {
  final Topic topic;
  final List<TopicDay> days;
  final int doneCount;
  final List<TopicRank> ranking;
  final int? myRank;
  const TopicDetail({required this.topic, this.days = const [], this.doneCount = 0, this.ranking = const [], this.myRank});

  factory TopicDetail.fromJson(Map<String, dynamic> j) {
    final p = Json.map(j['myProgress']);
    return TopicDetail(
      topic: Topic.fromJson(j),
      days: [
        for (final d in Json.maps(p['days']))
          TopicDay(Json.date(d['date']) ?? DateTime(1970), Json.b(d['done']), Json.sOrNull(d['coverUrl'])),
      ],
      doneCount: Json.i(p['doneCount']),
      ranking: [
        for (final r in Json.maps(j['ranking']))
          TopicRank(UserLite.fromJson(r['user']), Json.i(r['days']), Json.i(r['likeCount']), Json.i(r['rank'])),
      ],
      myRank: Json.iOrNull(j['myRank']),
    );
  }
}

class CommunityBanner {
  final Topic? topic;
  final List<UserLite> followingUpdates;
  const CommunityBanner({this.topic, this.followingUpdates = const []});

  factory CommunityBanner.fromJson(Map<String, dynamic> j) {
    final t = j['topic'];
    return CommunityBanner(
      topic: t is Map ? Topic.fromJson(Map<String, dynamic>.from(t)) : null,
      followingUpdates: [for (final u in (j['followingUpdates'] as List? ?? const [])) UserLite.fromJson(u)],
    );
  }
}

/// Feed 分段：推荐 / 关注 / 话题。
enum FeedTab { recommend, follow, topic }

class CommunityService {
  CommunityService._();

  static Future<CursorPage<PostCard>> feed(FeedTab tab, {int? topicId, String? cursor, int size = 20}) async {
    final d = await ApiClient.instance.get('/community/feed', query: {
      'tab': tab.name,
      if (topicId != null) 'topicId': topicId,
      if (cursor != null) 'cursor': cursor,
      'size': size,
    });
    return CursorPage.fromJson(d, PostCard.fromJson);
  }

  static Future<CommunityBanner> banner() async =>
      CommunityBanner.fromJson(Json.map(await ApiClient.instance.get('/community/banner')));

  static Future<PostDetail> detail(int postId) async =>
      PostDetail.fromJson(Json.map(await ApiClient.instance.get('/community/posts/$postId')));

  /// 发布，返回 `(postId, status)`；status 为 REVIEWING 时提示「审核中」。
  static Future<({int postId, String status})> publish({
    required String title,
    required String text,
    required List<String> imageUrls,
    int? dishId,
    List<int> topicIds = const [],
    bool followersOnly = false,
    int? fromCookRecordId,
  }) async {
    final d = Json.map(await ApiClient.instance.post('/community/posts', body: {
      'title': title,
      'text': text,
      'imageUrls': imageUrls,
      if (dishId != null) 'dishId': dishId,
      'topicIds': topicIds,
      'visibility': followersOnly ? 'FOLLOWERS' : 'PUBLIC',
      if (fromCookRecordId != null) 'fromCookRecordId': fromCookRecordId,
    }));
    return (postId: Json.i(d['postId']), status: Json.s(d['status'], 'PUBLISHED'));
  }

  static Future<void> delete(int postId) => ApiClient.instance.delete('/community/posts/$postId');

  /// 赞 / 收藏（幂等），返回最新计数。
  static Future<({int likeCount, int collectCount, bool liked, bool collected})> react(
      int postId, String type, bool on) async {
    final d = Json.map(
        await ApiClient.instance.post('/community/posts/$postId/reactions', body: {'type': type, 'on': on}));
    return (
      likeCount: Json.i(d['likeCount']),
      collectCount: Json.i(d['collectCount']),
      liked: Json.b(d['liked']),
      collected: Json.b(d['collected']),
    );
  }

  /// 照着做：复制为家庭私有菜谱，返回新菜谱 id。
  static Future<int> copyRecipe(int postId) async =>
      Json.i(Json.map(await ApiClient.instance.post('/community/posts/$postId/copy-recipe'))['dishId']);

  static Future<List<({UserLite user, String imageUrl})>> copies(int postId, {int size = 10}) async {
    final d = Json.map(await ApiClient.instance.get('/community/posts/$postId/copies', query: {'size': size}));
    return [
      for (final m in Json.maps(d['items'])) (user: UserLite.fromJson(m['user']), imageUrl: Json.s(m['imageUrl'])),
    ];
  }

  static Future<CursorPage<PostComment>> comments(int postId, {String? cursor, int size = 20}) async {
    final d = await ApiClient.instance
        .get('/community/posts/$postId/comments', query: {if (cursor != null) 'cursor': cursor, 'size': size});
    return CursorPage.fromJson(d, PostComment.fromJson);
  }

  static Future<PostComment> comment(int postId, String text, {int? parentId}) async {
    final d = await ApiClient.instance.post('/community/posts/$postId/comments',
        body: {'text': text, if (parentId != null) 'parentId': parentId});
    return PostComment.fromJson(Json.map(d));
  }

  static Future<bool> follow(int userId, bool on) async =>
      Json.b(Json.map(await ApiClient.instance.post('/community/users/$userId/follow', body: {'on': on}))['following']);

  static Future<TopicDetail> topic(int topicId) async =>
      TopicDetail.fromJson(Json.map(await ApiClient.instance.get('/community/topics/$topicId')));

  /// AI 帮写文案。style: default / casual / short
  static Future<String> aiText({int? dishId, int? cookRecordId, List<String>? imageUrls, String style = 'default'}) async {
    final d = Json.map(await ApiClient.instance.post('/community/drafts/ai-text', body: {
      if (dishId != null) 'dishId': dishId,
      if (cookRecordId != null) 'cookRecordId': cookRecordId,
      if (imageUrls != null) 'imageUrls': imageUrls,
      'style': style,
    }));
    return Json.s(d['text']);
  }

  static Future<void> report(int postId, String reason) =>
      ApiClient.instance.post('/community/reports', body: {'postId': postId, 'reason': reason});
}

/// 双列瀑布流分配：每张卡放进当前更矮的一列（按 1 / coverRatio 近似卡片高度）。
///
/// 返回两列各自的下标列表；纯函数，便于单测「顺序稳定、两列高度差不超过一张卡」。
({List<int> left, List<int> right}) splitMasonry(List<double> ratios, {double extra = 0.42}) {
  final left = <int>[];
  final right = <int>[];
  var hl = 0.0, hr = 0.0;
  for (var i = 0; i < ratios.length; i++) {
    final r = ratios[i] <= 0 ? 0.75 : ratios[i];
    final h = 1 / r + extra; // extra：标题 + 作者行的相对高度
    if (hl <= hr) {
      left.add(i);
      hl += h;
    } else {
      right.add(i);
      hr += h;
    }
  }
  return (left: left, right: right);
}

/// 「2 天前 / 刚刚 / 10-12」相对时间。
String relativeTime(DateTime? t, DateTime now) {
  if (t == null) return '';
  final d = now.difference(t);
  if (d.inMinutes < 1) return '刚刚';
  if (d.inHours < 1) return '${d.inMinutes} 分钟前';
  if (d.inDays < 1) return '${d.inHours} 小时前';
  if (d.inDays < 7) return '${d.inDays} 天前';
  return '${t.month}-${t.day.toString().padLeft(2, '0')}';
}

/// 1234 → 1.2k（社区计数）。
String compactCount(int n) {
  if (n < 1000) return '$n';
  if (n < 10000) {
    final v = (n / 100).round() / 10;
    return '${v == v.roundToDouble() ? v.toInt() : v}k';
  }
  final v = (n / 1000).round() / 10;
  return '${v == v.roundToDouble() ? v.toInt() : v}w';
}
