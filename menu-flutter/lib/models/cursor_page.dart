/// 游标分页（contracts.md：请求 `cursor,size`，响应 `{items, nextCursor}`）。
///
/// 与 MyBatis-Plus 的 [PageData] 不同：厨房圈 Feed、Agent 会话等按 `(score,id)` 游标翻页，
/// 翻页期间有新内容也不会重复或遗漏。`nextCursor == null` 表示没有更多。
class CursorPage<T> {
  final List<T> items;
  final String? nextCursor;

  const CursorPage({required this.items, this.nextCursor});

  bool get hasMore => nextCursor != null && nextCursor!.isNotEmpty;

  factory CursorPage.fromJson(dynamic json, T Function(Map<String, dynamic>) fromJsonT) {
    if (json is! Map) return CursorPage<T>(items: const []);
    final raw = json['items'];
    final items = raw is List
        ? raw.whereType<Map>().map((e) => fromJsonT(Map<String, dynamic>.from(e))).toList()
        : <T>[];
    final next = json['nextCursor'];
    return CursorPage<T>(items: items, nextCursor: next == null ? null : '$next');
  }

}

/// 游标列表的累积状态：负责「首屏 / 上拉加载更多」的去重与终止判断。
///
/// 页面持有一个 [CursorFeed]，调用 [load] 时传入取页函数；并发调用会被忽略，
/// 已出现过的 id 不会重复追加（服务端游标出错时的兜底）。
class CursorFeed<T> {
  final Object Function(T item) idOf;
  final List<T> items = [];
  String? _cursor;
  bool _done = false;
  bool _loading = false;
  final Set<Object> _seen = {};

  CursorFeed({required this.idOf});

  bool get hasMore => !_done;
  bool get loading => _loading;

  void reset() {
    items.clear();
    _seen.clear();
    _cursor = null;
    _done = false;
    _loading = false;
  }

  /// 取下一页并追加；返回本次新增条数。没有更多或正在加载时返回 0。
  Future<int> load(Future<CursorPage<T>> Function(String? cursor) fetch) async {
    if (_done || _loading) return 0;
    _loading = true;
    try {
      final page = await fetch(_cursor);
      var added = 0;
      for (final it in page.items) {
        if (_seen.add(idOf(it))) {
          items.add(it);
          added++;
        }
      }
      _cursor = page.nextCursor;
      _done = !page.hasMore;
      return added;
    } finally {
      _loading = false;
    }
  }
}
