import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../core/app_theme.dart';
import '../../services/dish_service.dart';
import '../../widgets/action_bar.dart';
import '../../widgets/loading_empty.dart';

/// 草稿箱（DESIGN.md §16.4，原型 cookbook-add.html ⑨ 屏）：
/// 入口 = 我的 Tab「草稿箱」；点行继续编辑（回写菜谱页回填）、滑动删除。
class DishDraftListPage extends StatefulWidget {
  const DishDraftListPage({super.key});

  @override
  State<DishDraftListPage> createState() => _DishDraftListPageState();
}

class _DishDraftListPageState extends State<DishDraftListPage> {
  static const _pageSize = 10; // DESIGN.md §12.2
  final _scroll = ScrollController();
  List<DishDraftItem> _items = [];
  int _page = 1;
  bool _loading = true;
  bool _hasMore = true;

  @override
  void initState() {
    super.initState();
    _scroll.addListener(_onScroll);
    _load();
  }

  @override
  void dispose() {
    _scroll.dispose();
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

  Future<void> _load() async {
    try {
      final pg = await DishService.listDrafts(pageNum: _page, pageSize: _pageSize);
      if (mounted) {
        setState(() {
          _items = pg.records;
          _hasMore = pg.records.length >= _pageSize;
          _loading = false;
        });
      }
    } catch (_) {
      if (mounted) {
        setState(() {
          _items = [];
          _loading = false;
        });
      }
    }
  }

  Future<void> _loadMore() async {
    setState(() => _loading = true);
    try {
      final pg = await DishService.listDrafts(pageNum: _page, pageSize: _pageSize);
      if (mounted) {
        setState(() {
          _items.addAll(pg.records);
          _hasMore = pg.records.length >= _pageSize;
          _loading = false;
        });
      }
    } catch (_) {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _remove(DishDraftItem item) async {
    try {
      await DishService.deleteDraft(item.id);
    } catch (_) {}
    if (mounted) {
      setState(() => _items.removeWhere((e) => e.id == item.id));
    }
  }

  String _timeText(DateTime t) {
    final now = DateTime.now();
    final today = DateTime(now.year, now.month, now.day);
    final day = DateTime(t.year, t.month, t.day);
    final diff = today.difference(day).inDays;
    final hm = '${t.hour.toString().padLeft(2, '0')}:${t.minute.toString().padLeft(2, '0')}';
    if (diff == 0) return '今天 $hm';
    if (diff == 1) return '昨天 $hm';
    return '${t.month}/${t.day} $hm';
  }

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    return Scaffold(
      backgroundColor: t.bg,
      body: SafeArea(
        bottom: false,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // 录入/列表页顶栏：只有返回箭头（§13.1）
            const BackHeader(),
            Expanded(
              child: _loading
                  ? const LoadingView()
                  : _items.isEmpty
                      ? const EmptyView(
                          text: '还没有草稿',
                          subtitle: '写菜谱没填完，点「存草稿」就会出现在这里',
                        )
                      : ListView.builder(
                          controller: _scroll,
                          padding: const EdgeInsets.fromLTRB(
                              AppTokens.sp16, AppTokens.sp4, AppTokens.sp16, AppTokens.sp32),
                          itemCount: _items.length + 1,
                          itemBuilder: (_, i) {
                            if (i == _items.length) {
                              return Padding(
                                padding: const EdgeInsets.all(16),
                                child: Center(
                                  child: Text(
                                    _hasMore ? '上拉加载更多' : '没有更多了',
                                    style: t.textStyles.caption,
                                  ),
                                ),
                              );
                            }
                            final item = _items[i];
                            return Dismissible(
                              key: ValueKey(item.id),
                              direction: DismissDirection.endToStart,
                              background: Container(
                                alignment: Alignment.centerRight,
                                padding: const EdgeInsets.only(right: 20),
                                decoration: BoxDecoration(
                                  color: AppTokens.error,
                                  borderRadius: BorderRadius.circular(AppTokens.rMd),
                                ),
                                child: Icon(Icons.delete_outline, color: t.card),
                              ),
                              onDismissed: (_) => _remove(item),
                              child: _buildRow(t, item),
                            );
                          },
                        ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildRow(AppTokens t, DishDraftItem item) {
    final name = item.name.isEmpty ? '未命名草稿' : item.name;
    return InkWell(
      onTap: () => context.push('/create-dish?draftId=${item.id}'),
      borderRadius: BorderRadius.circular(AppTokens.rMd),
      child: Container(
        margin: const EdgeInsets.only(bottom: AppTokens.sp8),
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          color: t.card,
          border: Border.all(color: t.border),
          borderRadius: BorderRadius.circular(AppTokens.rMd),
        ),
        child: Row(
          children: [
            // 封面占位：首字（§10）
            Container(
              width: 42,
              height: 42,
              decoration: BoxDecoration(
                color: t.primarySoft,
                borderRadius: BorderRadius.circular(AppTokens.rSm),
              ),
              alignment: Alignment.center,
              child: Text(
                name.characters.first,
                style: t.textStyles.lg.copyWith(color: t.primaryDeep),
              ),
            ),
            const SizedBox(width: AppTokens.sp12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(name,
                      style: t.textStyles.md.copyWith(
                          fontWeight: FontWeight.w800, color: t.title)),
                  const SizedBox(height: 2),
                  Text(
                    '用料 ${item.ingredientCount} · 步骤 ${item.stepCount} · ${_timeText(item.updateTime)}',
                    style: t.textStyles.xs.copyWith(color: t.caption),
                  ),
                ],
              ),
            ),
            Text('继续 ›',
                style: t.textStyles.sm.copyWith(
                    color: t.accent, fontWeight: FontWeight.w800)),
          ],
        ),
      ),
    );
  }
}
