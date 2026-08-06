import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../core/app_theme.dart';
import '../../core/image_helper.dart';
import '../../models/menu.dart';
import '../../services/menu_service.dart';
import '../../widgets/loading_empty.dart';

/// 食集列表（对应后端 GET /menu）。
/// 分页（pageSize=20，按创建时间倒序）+ 下拉刷新 + 上拉加载更多。
///
/// 入口：点击列表项跳 `/menu/:id` 详情页。
/// 汇总条切换（全部 / 进行中 / 已完成）走 status 过滤，重置分页。
class MenuListPage extends StatefulWidget {
  const MenuListPage({super.key});
  @override
  State<MenuListPage> createState() => _MenuListPageState();
}

class _MenuListPageState extends State<MenuListPage> {
  final _scroll = ScrollController();
  static const _pageSize = 15; // DESIGN.md §12.2 列表分页约定

  List<Menu> _menus = [];
  int _page = 1;
  bool _loading = false;
  bool _hasMore = true;
  bool _firstLoading = true;

  /// 当前选中的状态过滤：null 全部 / 'ACTIVE' 进行中 / 'DONE' 已完成。
  String? _status;

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
      final r = await MenuService.list(
          pageNum: pageNum, pageSize: _pageSize, status: _status);
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

  /// 切状态过滤：重置分页并按 status 重拉。
  Future<void> _switchStatus(String? status) async {
    if (_status == status) return;
    _status = status;
    await _reload();
  }

  /// 顶栏「新建食集」/ 空态 CTA：弹输入框建空食集。
  Future<void> _createMenu() async {
    final name = await _showCreateDialog();
    if (name == null || name.trim().isEmpty) return;
    try {
      await MenuService.createMenu(name.trim());
      if (!mounted) return;
      ScaffoldMessenger.of(context)
          .showSnackBar(const SnackBar(content: Text('已创建食集')));
      await _reload();
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(const SnackBar(content: Text('创建失败')));
      }
    }
  }

  Future<String?> _showCreateDialog() {
    final ctrl = TextEditingController();
    return showDialog<String>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('新建食集'),
        content: TextField(
          controller: ctrl,
          autofocus: true,
          decoration: const InputDecoration(
            hintText: '食集名（如：今晚的饭）',
            isDense: true,
          ),
          onSubmitted: (v) => Navigator.pop(ctx, v),
        ),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(ctx),
              child: const Text('取消')),
          TextButton(
              onPressed: () => Navigator.pop(ctx, ctrl.text),
              child: const Text('确定')),
        ],
      ),
    );
  }

  /// 汇总条计数：基于当前已加载数据（分页未加载完会随滚动变化，符合列表页轻量定位）。
  int get _activeCount => _menus.where((m) => !m.isDone).length;
  int get _doneCount => _menus.where((m) => m.isDone).length;

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    return Scaffold(
      backgroundColor: t.bg,
      body: SafeArea(
        child: Column(
          children: [
            _buildTopBar(t),
            _buildStatusBar(t),
            Expanded(
              child: _firstLoading
                  ? const LoadingView()
                  : RefreshIndicator(
                      color: t.primary,
                      onRefresh: _reload,
                      child: _menus.isEmpty
                          ? ListView(
                              children: [_EmptyView(onCreate: _createMenu)],
                            )
                          : ListView.builder(
                              controller: _scroll,
                              padding: const EdgeInsets.fromLTRB(AppTokens.sp12,
                                  AppTokens.sp4, AppTokens.sp12, AppTokens.sp16),
                              itemCount: _menus.length + 1,
                              itemBuilder: (_, i) {
                                if (i == _menus.length) {
                                  return Padding(
                                    padding: const EdgeInsets.all(AppTokens.sp16),
                                    child: Center(
                                      child: Text(
                                        _hasMore ? '上拉加载更多' : '没有更多了',
                                        style: t.textStyles.caption,
                                      ),
                                    ),
                                  );
                                }
                                return _MenuCard(menu: _menus[i]);
                              },
                            ),
                    ),
            ),
          ],
        ),
      ),
    );
  }

  /// 浅色顶栏：大标题「食集」 + 右上「新建食集」橙色胶囊按钮。
  Widget _buildTopBar(AppTokens t) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(
          AppTokens.sp16, AppTokens.sp12, AppTokens.sp16, AppTokens.sp4),
      child: Row(
        children: [
          Text('食集', style: t.textStyles.h2),
          const Spacer(),
          GestureDetector(
            onTap: _createMenu,
            child: Container(
              padding:
                  const EdgeInsets.symmetric(horizontal: AppTokens.sp12, vertical: 5),
              decoration: BoxDecoration(
                color: t.primary,
                borderRadius: BorderRadius.circular(AppTokens.rPill),
              ),
              child: Text(
                '新建食集',
                style: t.textStyles.tiny.copyWith(color: Colors.white),
              ),
            ),
          ),
        ],
      ),
    );
  }

  /// 状态汇总条：全部 / 进行中 / 已完成。选中实心深色白字，未选中白底描边。
  Widget _buildStatusBar(AppTokens t) {
    Widget chip(String? status, String label, int count) {
      final selected = _status == status;
      final text = '$label $count';
      return Padding(
        padding: const EdgeInsets.only(right: 6),
        child: GestureDetector(
          onTap: () => _switchStatus(status),
          child: Container(
            padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 4),
            decoration: BoxDecoration(
              color: selected ? t.title : t.card,
              borderRadius: BorderRadius.circular(AppTokens.rSm),
              border: selected ? null : Border.all(color: t.border),
            ),
            child: Text(
              text,
              style: t.textStyles.tiny.copyWith(
                color: selected ? Colors.white : t.body,
              ),
            ),
          ),
        ),
      );
    }

    return Padding(
      padding: const EdgeInsets.fromLTRB(
          AppTokens.sp16, 0, AppTokens.sp16, AppTokens.sp8),
      child: Row(
        children: [
          chip(null, '全部', _menus.length),
          chip('ACTIVE', '进行中', _activeCount),
          chip('DONE', '已完成', _doneCount),
        ],
      ),
    );
  }
}

/// 食集卡片（原型 menu-list-preview.html）。
/// 进行中=高亮卡（highlight 底 + primarySoft 描边）；已完成=普通卡。
class _MenuCard extends StatelessWidget {
  final Menu menu;
  const _MenuCard({required this.menu});

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    final done = menu.isDone;
    final highlight = !done;

    return GestureDetector(
      onTap: () => context.push('/menu/${menu.id}'),
      child: Container(
        margin: const EdgeInsets.only(bottom: 7),
        padding: const EdgeInsets.all(11),
        decoration: BoxDecoration(
          color: highlight ? t.highlight : t.card,
          borderRadius: BorderRadius.circular(AppTokens.rMd),
          border: Border.all(
            color: highlight ? t.primarySoft : t.border,
            width: highlight ? 1.5 : 1,
          ),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // 标题行：菜名 + 状态胶囊
            Row(
              children: [
                Expanded(
                  child: Text(menu.name,
                      style: t.textStyles.cardTitle,
                      overflow: TextOverflow.ellipsis),
                ),
                _StatusPill(done: done),
              ],
            ),
            const SizedBox(height: 5),
            // 第二行：封面缩略堆叠 + 菜数 + 份数 + 相对日期
            Row(
              children: [
                if (menu.covers.isNotEmpty) ...[
                  _CoverStack(covers: menu.covers),
                  const SizedBox(width: AppTokens.sp8),
                ],
                Text('${menu.dishCount ?? 0} 道菜',
                    style: t.textStyles.tiny),
                const Spacer(),
                Text(
                  '${menu.servingCount ?? 1} 人份 · ${_relativeDate(menu.createdAt)}',
                  style: t.textStyles.tiny,
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

/// 状态胶囊：实心底白字（进行中 warning / 已完成 success）。
class _StatusPill extends StatelessWidget {
  final bool done;
  const _StatusPill({required this.done});
  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    final color = done ? AppTokens.success : AppTokens.warning;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: AppTokens.sp8, vertical: 2),
      decoration: BoxDecoration(
        color: color,
        borderRadius: BorderRadius.circular(AppTokens.rPill),
      ),
      child: Text(
        done ? '已完成' : '进行中',
        style: t.textStyles.micro.copyWith(color: Colors.white),
      ),
    );
  }
}

/// 封面缩略堆叠：最多 3 个 22×22 圆角图，后两个左移 -8 叠加（原型 margin-left:-8）。
class _CoverStack extends StatelessWidget {
  final List<String> covers;
  const _CoverStack({required this.covers});

  static const _size = 22.0;
  static const _overlap = 8.0; // 叠加像素
  static const _radius = 6.0;

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    final list = covers.take(3).toList();
    // 总宽 = 第一个 + 后续每个 (size - overlap)
    final width = _size + (list.length - 1) * (_size - _overlap);
    return SizedBox(
      width: width,
      height: _size,
      child: Stack(
        children: [
          for (int i = 0; i < list.length; i++)
            Positioned(
              left: i * (_size - _overlap),
              top: 0,
              child: _thumb(list[i], t, i),
            ),
        ],
      ),
    );
  }

  Widget _thumb(String url, AppTokens t, int index) {
    final abs = ImageHelper.toAbsolute(url);
    final thumb = ImageHelper.toThumbnail(abs);
    return Container(
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(_radius),
        border: Border.all(color: t.card, width: 1.5),
      ),
      clipBehavior: Clip.antiAlias,
      width: _size,
      height: _size,
      child: Image.network(
        thumb,
        fit: BoxFit.cover,
        errorBuilder: (_, __, ___) => _placeholder(t),
        loadingBuilder: (_, child, progress) =>
            progress == null ? child : _placeholder(t),
      ),
    );
  }

  Widget _placeholder(AppTokens t) => Container(color: t.secondary);
}

/// 相对日期：今天 / 昨天 / N 天前 / M/D（不引第三方库）。
String _relativeDate(DateTime? dt) {
  if (dt == null) return '';
  final now = DateTime.now();
  final today = DateTime(now.year, now.month, now.day);
  final that = DateTime(dt.year, dt.month, dt.day);
  final diff = today.difference(that).inDays;
  if (diff <= 0) return '今天';
  if (diff == 1) return '昨天';
  if (diff < 7) return '$diff 天前';
  return '${dt.month}/${dt.day}';
}

/// 空态：占位图 + 文案 + 「建一个食集」CTA（原型右屏）。
class _EmptyView extends StatelessWidget {
  final VoidCallback onCreate;
  const _EmptyView({required this.onCreate});
  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    return Padding(
      padding: const EdgeInsets.only(top: 80),
      child: Center(
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 40),
          child: Column(
            children: [
              Container(
                width: 96,
                height: 96,
                decoration: BoxDecoration(
                  color: t.secondary,
                  borderRadius: BorderRadius.circular(AppTokens.rXl),
                  border: Border.all(color: t.primarySoft, width: 1.5),
                ),
              ),
              const SizedBox(height: AppTokens.sp16),
              Text('还没有食集', style: t.textStyles.subtitle),
              const SizedBox(height: 6),
              Text(
                '把几道菜凑成一顿饭\n备料、采购、做菜一次搞定',
                textAlign: TextAlign.center,
                style: t.textStyles.caption,
              ),
              const SizedBox(height: AppTokens.sp16),
              ElevatedButton(
                onPressed: onCreate,
                style: ElevatedButton.styleFrom(
                  backgroundColor: t.primary,
                  foregroundColor: Colors.white,
                  elevation: 0,
                  padding:
                      const EdgeInsets.symmetric(horizontal: 28, vertical: 11),
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(AppTokens.rMd),
                  ),
                ),
                child: const Text('建一个食集'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
