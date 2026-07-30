import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../core/image_helper.dart';
import '../../core/app_theme.dart';
import '../../models/dish.dart';
import '../../services/dish_service.dart';
import '../../widgets/loading_empty.dart';

/// 菜库列表（复刻 menu-mini/src/pages/dish/List.vue）。
/// 搜索 + 分页（pageSize=20）+ 下拉刷新 + 上拉加载更多。
///
/// 封面图片使用缩略图（/thumbnail/xxx.jpg），节省列表滚动时的流量和内存。
class DishListPage extends StatefulWidget {
  const DishListPage({super.key});
  @override
  State<DishListPage> createState() => _DishListPageState();
}

class _DishListPageState extends State<DishListPage> {
  final _scroll = ScrollController();
  final _keywordCtrl = TextEditingController();
  static const _pageSize = 20;

  List<Dish> _dishes = [];
  int _page = 1;
  bool _loading = false;
  bool _hasMore = true;
  bool _firstLoading = true;
  bool _hasText = false;

  @override
  void initState() {
    super.initState();
    _scroll.addListener(_onScroll);
    _reload();
  }

  @override
  void dispose() {
    _scroll.dispose();
    _keywordCtrl.dispose();
    super.dispose();
  }

  void _onScroll() {
    if (!_scroll.hasClients) return;
    if (_scroll.position.pixels >= _scroll.position.maxScrollExtent - 100 &&
        !_loading &&
        _hasMore) {
      _page++;
      _loadMore();
    }
  }

  Future<List<Dish>> _fetch(int pageNum) async {
    try {
      final r = await DishService.search(
        keyword: _keywordCtrl.text.trim(),
        pageNum: pageNum,
        pageSize: _pageSize,
      );
      _hasMore = r.records.length >= _pageSize;
      return r.records;
    } catch (_) {
      _hasMore = false;
      return [];
    }
  }

  Future<void> _reload() async {
    _page = 1;
    _hasMore = true;
    setState(() => _firstLoading = true);
    final list = await _fetch(_page);
    _dishes = list;
    if (mounted) setState(() => _firstLoading = false);
  }

  Future<void> _loadMore() async {
    if (_loading) return;
    setState(() => _loading = true);
    final list = await _fetch(_page);
    _dishes.addAll(list);
    if (mounted) setState(() => _loading = false);
  }

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    return Scaffold(
        appBar: AppBar(title: const Text('菜库')),
        body: Column(
          children: [
            Padding(
              padding: const EdgeInsets.all(12),
              child: TextField(
                controller: _keywordCtrl,
                decoration: InputDecoration(
                  hintText: '搜菜名',
                  prefixIcon: const Icon(Icons.search),
                  suffixIcon: _hasText
                      ? IconButton(
                          icon: const Icon(Icons.close),
                          onPressed: () {
                            _keywordCtrl.clear();
                            setState(() => _hasText = false);
                            _reload();
                          },
                        )
                      : null,
                ),
                onChanged: (v) => setState(() => _hasText = v.isNotEmpty),
                onSubmitted: (_) => _reload(),
              ),
            ),
            Expanded(
              child: _firstLoading
                  ? const LoadingView()
                  : RefreshIndicator(
                      color: t.primary,
                      onRefresh: _reload,
                      child: _dishes.isEmpty
                          ? const EmptyView(text: '暂无菜品')
                          : ListView.builder(
                              controller: _scroll,
                              itemCount: _dishes.length + 1,
                              itemBuilder: (_, i) {
                                if (i == _dishes.length) {
                                  return Padding(
                                    padding: const EdgeInsets.all(16),
                                    child: Center(
                                      child: Text(
                                        _hasMore ? '上拉加载更多' : '没有更多了',
                                        style: TextStyle(
                                            color: t.caption,
                                            fontSize: 12),
                                      ),
                                    ),
                                  );
                                }
                                final d = _dishes[i];
                                return _DishTile(dish: d);
                              },
                            ),
                    ),
            ),
          ],
        ),
      );
  }
}

/// 列表项：封面缩略图 + 菜名 + 时间/难度。
class _DishTile extends StatelessWidget {
  final Dish dish;
  const _DishTile({required this.dish});

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    final hasCover =
        dish.coverUrl != null && dish.coverUrl!.isNotEmpty;
    final thumbUrl = hasCover
        ? ImageHelper.toThumbnail(ImageHelper.toAbsolute(dish.coverUrl!))
        : null;

    return ListTile(
      leading: ClipRRect(
        borderRadius: BorderRadius.circular(AppTokens.rSm),
        child: SizedBox(
          width: 56,
          height: 56,
          child: thumbUrl != null
              ? Image.network(
                  thumbUrl,
                  fit: BoxFit.cover,
                  errorBuilder: (_, __, ___) => _placeholder(t),
                  loadingBuilder: (_, child, progress) {
                    if (progress == null) return child;
                    return _placeholder(t);
                  },
                )
              : _placeholder(t),
        ),
      ),
      title: Text(dish.name),
      subtitle: Text(
        '${dish.cookTime ?? 0}分钟 · 难度${dish.difficulty ?? '-'}',
        style: TextStyle(
            color: t.caption, fontSize: 12),
      ),
      trailing: Icon(Icons.chevron_right,
          color: t.caption),
      onTap: () => context.push('/dish/${dish.id}'),
    );
  }

  Widget _placeholder(AppTokens t) => Container(
        color: t.bg,
        child: Icon(Icons.restaurant, color: t.border, size: 24),
      );
}
