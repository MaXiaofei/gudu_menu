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

  /// 菜名占位缓存：menuId → 前 3 个菜名（用于封面缩略堆叠的首字占位）。
  /// 列表接口的 covers 字段在后端未部署时缺失，或菜无封面时为空——
  /// 此时退化为菜名首字色块，保证「几道菜」的视觉提示始终存在。
  final Map<int, List<String>> _dishNames = {};

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
      // 后端旧版本列表接口不返回 dishCount/covers——对缺失项并发拉详情补菜数 + 菜名。
      // 详情接口（GET /menu/{id}）的 dishes 带 dishName，用于首字占位。静默失败，不阻断列表。
      await _enrichDishInfo(r.records);
      return r.records;
    } catch (_) {
      _hasMore = false;
      return [];
    }
  }

  /// 并发拉详情补 dishCount + 菜名（仅对 dishCount == null 的旧接口数据生效）。
  /// 新版后端返回了 dishCount 时直接跳过，无额外请求。
  Future<void> _enrichDishInfo(List<Menu> menus) async {
    final need = menus.where((m) => m.dishCount == null).toList();
    if (need.isEmpty) return;
    await Future.wait(need.map((m) async {
      try {
        final d = await MenuService.detail(m.id);
        // 用详情 dishes 长度回填菜数（拷贝一份避免直接改 const 实体字段）。
        final enriched = Menu(
          id: m.id,
          name: m.name,
          typeId: m.typeId,
          targetMemberId: m.targetMemberId,
          servingCount: m.servingCount,
          status: m.status,
          createTime: m.createTime,
          dishCount: d.dishes.length,
          covers: m.covers,
        );
        final i = menus.indexOf(m);
        if (i >= 0) menus[i] = enriched;
        _dishNames[m.id] = d.dishes
            .map((md) => (md.dishName ?? '').trim())
            .where((n) => n.isNotEmpty)
            .take(3)
            .toList();
      } catch (_) {
        // 单条失败不影响整页，菜数保持 null → UI 兜底显示 0。
      }
    }));
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

  /// 左滑删除二次确认：弹窗 → 确定则调接口删除并从列表移除。
  /// 返回 true 时 Dismissible 才会真正收起卡片。
  Future<bool> _confirmDelete(Menu menu) async {
    final ok = await showDialog<bool>(
          context: context,
          builder: (ctx) => AlertDialog(
            title: const Text('删除食集'),
            content: Text('确认删除食集「${menu.name}」？该操作不可撤销。'),
            actions: [
              TextButton(
                  onPressed: () => Navigator.pop(ctx, false),
                  child: const Text('取消')),
              TextButton(
                  onPressed: () => Navigator.pop(ctx, true),
                  child: const Text('删除')),
            ],
          ),
        ) ??
        false;
    if (!ok) return false;
    try {
      await MenuService.deleteMenu(menu.id);
      if (!mounted) return false;
      setState(() => _menus.removeWhere((m) => m.id == menu.id));
      ScaffoldMessenger.of(context)
          .showSnackBar(const SnackBar(content: Text('已删除')));
      return true;
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(const SnackBar(content: Text('删除失败')));
      }
      return false;
    }
  }

  /// 左滑露出的删除底色：右对齐红色底 + 删除图标。
  Widget _dismissBackground(AppTokens t) {
    return Container(
      alignment: Alignment.centerRight,
      padding: const EdgeInsets.only(right: AppTokens.sp16),
      margin: const EdgeInsets.only(bottom: 7),
      decoration: BoxDecoration(
        color: const Color(0xFFE53935),
        borderRadius: BorderRadius.circular(AppTokens.rMd),
      ),
      child: const Icon(Icons.delete_outline, color: Colors.white),
    );
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
      // DESIGN.md §13：Tab 主页无标题（不放「食集」）。
      // 「新建食集」按钮与状态筛选同一行，放最右侧。
      body: SafeArea(
        bottom: false,
        child: Column(
          children: [
            _buildStatusBar(t),
            Expanded(
              child: _firstLoading
                  ? const LoadingView()
                  : RefreshIndicator(
                      color: t.primary,
                      onRefresh: _reload,
                      child: _menus.isEmpty
                          ? ListView(
                              children: const [
                                // 空态不放 CTA：右上角「新建食集」按钮已在同一屏（§13.1 操作右对齐），避免双入口
                                EmptyView(text: '还没有食集'),
                              ],
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
                                final menu = _menus[i];
                                return Dismissible(
                                  key: ValueKey(menu.id),
                                  direction: DismissDirection.endToStart,
                                  background: _dismissBackground(t),
                                  confirmDismiss: (_) => _confirmDelete(menu),
                                  child: _MenuCard(
                                    menu: menu,
                                    dishNames: _dishNames[menu.id] ?? const [],
                                  ),
                                );
                              },
                            ),
                    ),
            ),
          ],
        ),
      ),
    );
  }

  /// 筛选 + 新建一行：全部/进行中/已完成 筛选条 + 最右侧「新建食集」按钮。
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
          AppTokens.sp16, AppTokens.sp8, AppTokens.sp16, AppTokens.sp8),
      child: Row(
        children: [
          chip(null, '全部', _menus.length),
          chip('ACTIVE', '进行中', _activeCount),
          chip('DONE', '已完成', _doneCount),
          const Spacer(),
          // 新建食集（最右侧，实心胶囊主按钮）
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
}

/// 食集卡片（原型 menu-list-preview.html）。
/// 进行中=高亮卡（highlight 底 + primarySoft 描边）；已完成=普通卡。
class _MenuCard extends StatelessWidget {
  final Menu menu;
  /// 前 3 个菜名（详情接口冗余返回），covers 为空时用首字色块占位。
  final List<String> dishNames;
  const _MenuCard({required this.menu, this.dishNames = const []});

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
                // 有封面图 → 显图；无图但有菜名 → 菜名首字色块占位；都没有 → 不显示堆叠。
                if (menu.covers.isNotEmpty) ...[
                  _CoverStack(covers: menu.covers),
                  const SizedBox(width: AppTokens.sp8),
                ] else if (dishNames.isNotEmpty) ...[
                  _InitialStack(names: dishNames),
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

/// 菜名首字色块堆叠（covers 无图时的降级占位，DESIGN.md §10.4 禁用 emoji 顶图）。
/// 尺寸/叠加规则同 _CoverStack（22×22，后一个左移 8 叠加），最多 3 个。
class _InitialStack extends StatelessWidget {
  final List<String> names;
  const _InitialStack({required this.names});

  static const _size = 22.0;
  static const _overlap = 8.0;
  static const _radius = 6.0;

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    final list = names.take(3).toList();
    if (list.isEmpty) return const SizedBox.shrink();
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
              child: _block(list[i], t),
            ),
        ],
      ),
    );
  }

  Widget _block(String name, AppTokens t) {
    final initial = name.trim().isNotEmpty ? name.trim().characters.first : '菜';
    return Container(
      decoration: BoxDecoration(
        color: t.secondary,
        borderRadius: BorderRadius.circular(_radius),
        border: Border.all(color: t.card, width: 1.5),
      ),
      alignment: Alignment.center,
      width: _size,
      height: _size,
      child: Text(
        initial,
        style: t.textStyles.sm.copyWith(
          fontWeight: FontWeight.w600,
          color: t.title.withAlpha(115), // ≈ 0.45 透明度，同 dish 列表占位
        ),
      ),
    );
  }
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
