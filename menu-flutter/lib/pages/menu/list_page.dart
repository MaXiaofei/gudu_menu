import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../core/app_theme.dart';
import '../../models/menu.dart';
import '../../services/menu_service.dart';
import '../../widgets/loading_empty.dart';

/// 食集列表（对应 menu-mini/src/pages/menu/Home.vue）。
/// 分页（pageSize=20）+ 下拉刷新 + 上拉加载更多。
///
/// 入口：点击列表项跳 `/menu/:id` 详情页（整集做菜落点）。
class MenuListPage extends StatefulWidget {
  const MenuListPage({super.key});
  @override
  State<MenuListPage> createState() => _MenuListPageState();
}

class _MenuListPageState extends State<MenuListPage> {
  final _scroll = ScrollController();
  static const _pageSize = 20;

  List<Menu> _menus = [];
  int _page = 1;
  bool _loading = false;
  bool _hasMore = true;
  bool _firstLoading = true;

  @override
  void initState() {
    super.initState();
    _scroll.addListener(_onScroll);
    _reload();
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

  Future<List<Menu>> _fetch(int pageNum) async {
    try {
      final r = await MenuService.list(pageNum: pageNum, pageSize: _pageSize);
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
    _menus = await _fetch(_page);
    if (mounted) setState(() => _firstLoading = false);
  }

  Future<void> _loadMore() async {
    if (_loading) return;
    setState(() => _loading = true);
    final list = await _fetch(_page);
    _menus.addAll(list);
    if (mounted) setState(() => _loading = false);
  }

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    return Scaffold(
        appBar: AppBar(title: const Text('食集')),
        body: _firstLoading
            ? const LoadingView()
            : RefreshIndicator(
                color: t.primary,
                onRefresh: _reload,
                child: _menus.isEmpty
                    ? const EmptyView(text: '暂无食集')
                    : ListView.builder(
                        controller: _scroll,
                        itemCount: _menus.length + 1,
                        itemBuilder: (_, i) {
                          if (i == _menus.length) {
                            return Padding(
                              padding: const EdgeInsets.all(16),
                              child: Center(
                                child: Text(
                                  _hasMore ? '上拉加载更多' : '没有更多了',
                                  style: TextStyle(
                                      color: t.caption,
                                      fontSize: 13),
                                ),
                              ),
                            );
                          }
                          return _MenuTile(menu: _menus[i]);
                        },
                      ),
              ),
      );
  }
}

class _MenuTile extends StatelessWidget {
  final Menu menu;
  const _MenuTile({required this.menu});

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    return ListTile(
        title: Text(menu.name),
        subtitle: Text(
          '份数 ${menu.servingCount ?? 1}'
          '${menu.isDone ? ' · 已完成' : ''}',
          style: TextStyle(
              color: t.caption, fontSize: 13),
        ),
        trailing: Icon(Icons.chevron_right,
            color: t.caption),
        onTap: () => context.push('/menu/${menu.id}'),
      );
  }
}
