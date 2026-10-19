import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:share_plus/share_plus.dart';

import '../../core/app_theme.dart';
import '../../services/shopping_service.dart';
import '../../widgets/loading_empty.dart';
import '../../widgets/ingredient_icon.dart';

part 'parts/detail_view.dart';
part 'parts/share_view.dart';
part 'parts/sheets.dart';

/// 采购清单页（V42：采购唯一家园）。
///
/// 列表页：所有采购单（食集备菜一键加采购生成 / 自定义新建）+「+ 新建清单」。
/// 详情页：全选 + 勾选=本地选择态（不逐项写库）→ 底部「保存入库 · N 项」批量；
/// 行尾 ✕：未入库=移除确认；已入库=撤回入库确认（恢复入库前档位）。
/// 分享：右上角 → 全屏预览页（所见即所得）→ 复制文字 / 转图片。
/// 自定义采购：空清单「添加」弹窗（名称+数量单位一框、逐条添加、行尾删除）+ 标题旁 ✎ 改名。
class ShoppingPage extends StatefulWidget {
  /// 从备菜「一键加采购」跳转进来时传入：列表加载完自动打开该清单详情。
  final int? initialListId;
  const ShoppingPage({super.key, this.initialListId});
  @override
  State<ShoppingPage> createState() => _ShoppingPageState();
}

class _ShoppingPageState extends State<ShoppingPage> {
  /// 供 part 文件里的扩展方法刷新（setState 是 @protected）。
  void _refresh(VoidCallback fn) => setState(fn);

  // 列表
  List<ShoppingList> _lists = [];
  bool _loading = true;
  static const _pageSize = 10; // DESIGN.md §12.2（默认 10 条/页）
  final _scroll = ScrollController();
  int _page = 1;
  bool _hasMore = false;
  bool _loadingMore = false;

  // 当前打开的采购单（null=列表视图）
  ShoppingListVO? _detail;
  bool _detailLoading = false;

  /// 勾选本地选择态（未入库项 itemId，不逐项写库）。
  final Set<int> _selectedItems = {};

  /// 分享预览视图（true 时替代详情视图）。
  bool _showShare = false;

  /// 分享卡片截图 key（转图片用）。
  final GlobalKey _shareBoundaryKey = GlobalKey();

  // 手动添加（自定义采购）弹窗状态
  final _addNameCtrl = TextEditingController();
  final _addAmountCtrl = TextEditingController();

  // 列表批量删除选择模式（§15：全选 + 批量提交）
  bool _listSelectMode = false;
  final Set<int> _selectedListIds = {};

  @override
  void initState() {
    super.initState();
    _scroll.addListener(_onScroll);
    _loadLists();
  }

  @override
  void dispose() {
    _scroll.dispose();
    _addNameCtrl.dispose();
    _addAmountCtrl.dispose();
    super.dispose();
  }

  Future<void> _loadLists() async {
    setState(() => _loading = true);
    _page = 1;
    _hasMore = false;
    try {
      final pg = await ShoppingService.listPaged(pageNum: 1, pageSize: _pageSize);
      _lists = pg.records;
      _hasMore = pg.records.length >= _pageSize;
    } catch (_) {}
    if (mounted) setState(() => _loading = false);
    // 备菜一键加购跳转进来：列表加载完自动打开该清单详情
    final target = widget.initialListId;
    if (target != null) {
      _openDetail(target);
    }
  }

  Future<void> _loadMore() async {
    if (_loadingMore || !_hasMore) return;
    setState(() => _loadingMore = true);
    try {
      _page++;
      final pg = await ShoppingService.listPaged(pageNum: _page, pageSize: _pageSize);
      _lists.addAll(pg.records);
      _hasMore = pg.records.length >= _pageSize;
    } catch (_) {
      _page--;
      _hasMore = false;
    }
    if (mounted) setState(() => _loadingMore = false);
  }

  void _onScroll() {
    if (!_scroll.hasClients || _loadingMore || !_hasMore) return;
    final pos = _scroll.position;
    if (pos.pixels >= pos.maxScrollExtent - 100) _loadMore();
  }

  Future<void> _openDetail(int id) async {
    setState(() {
      _detailLoading = true;
      _selectedItems.clear();
      _showShare = false;
    });
    try {
      _detail = await ShoppingService.detail(id);
    } catch (_) {
      _snack('加载采购单失败');
    }
    if (mounted) setState(() => _detailLoading = false);
  }

  void _closeDetail() {
    setState(() {
      _detail = null;
      _selectedItems.clear();
      _showShare = false;
    });
    _loadLists();
  }

  void _snack(String msg) {
    if (!mounted) return;
    ScaffoldMessenger.of(context)
        .showSnackBar(SnackBar(content: Text(msg), duration: const Duration(seconds: 2)));
  }

  // ===== 新建清单（自定义采购） =====

  Future<void> _createList() async {
    try {
      final id = await ShoppingService.createEmpty();
      if (!mounted) return;
      _openDetail(id);
    } catch (_) {
      _snack('创建失败');
    }
  }

  // ===== UI: 列表视图 =====

  @override
  Widget build(BuildContext context) {
    if (_showShare && _detail != null) return _buildShareView();
    if (_detail != null) return _buildDetailView();
    return _buildListView();
  }

  Widget _buildListView() {
    final t = AppTokens.of(context);
    return Scaffold(
      appBar: AppBar(
        title: Text(_listSelectMode ? '已选 ${_selectedListIds.length} 个' : '采购清单'),
        actions: [
          if (_listSelectMode)
            TextButton(
              onPressed: _exitListSelectMode,
              child: Text('取消', style: TextStyle(color: t.caption)),
            )
          else ...[
            // 批量删除（§15 批量操作）
            TextButton(
              onPressed: () => setState(() => _listSelectMode = true),
              child: Text('批量删除', style: t.textStyles.danger),
            ),
            // 操作行：新建（自定义采购入口）
            TextButton.icon(
              onPressed: _createList,
              icon: const Icon(Icons.add, size: 18),
              label: const Text('新建清单'),
              style: TextButton.styleFrom(foregroundColor: t.primary),
            ),
            const SizedBox(width: 8),
          ],
        ],
      ),
      body: _loading
          ? const LoadingView()
          : _lists.isEmpty
              ? _buildEmpty(t)
              : RefreshIndicator(
                  onRefresh: _loadLists,
                  child: ListView.builder(
                    controller: _scroll,
                    padding: const EdgeInsets.all(12),
                    itemCount: _lists.length + (_loadingMore ? 1 : 0),
                    itemBuilder: (_, i) {
                      if (i == _lists.length) {
                        return const Padding(
                          padding: EdgeInsets.symmetric(vertical: 16),
                          child: LoadingView(),
                        );
                      }
                      return _buildListCard(_lists[i]);
                    },
                  ),
                ),
      bottomNavigationBar: _listSelectMode ? _buildListSelectBar(t) : null,
    );
  }

  /// 批量删除底部操作条：全选 + 删除 N 个（§15.1 全选铁律）。
  Widget _buildListSelectBar(AppTokens t) {
    final allSelected =
        _lists.isNotEmpty && _selectedListIds.length == _lists.length;
    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 8, 16, 12),
        child: Row(children: [
          TextButton(
            onPressed: () => setState(() {
              if (allSelected) {
                _selectedListIds.clear();
              } else {
                _selectedListIds.addAll(_lists.map((e) => e.id));
              }
            }),
            child: Text(allSelected ? '取消全选' : '全选',
                style: t.textStyles.sm.copyWith(color: t.primary, fontWeight: FontWeight.w800)),
          ),
          const Spacer(),
          OutlinedButton.icon(
            style: OutlinedButton.styleFrom(
              foregroundColor: AppTokens.error,
              side: const BorderSide(color: AppTokens.error),
            ),
            onPressed: _selectedListIds.isEmpty ? null : _confirmDeleteLists,
            icon: const Icon(Icons.delete_outline, size: 18),
            label: Text('删除 ${_selectedListIds.length} 个'),
          ),
        ]),
      ),
    );
  }

  void _exitListSelectMode() {
    setState(() {
      _listSelectMode = false;
      _selectedListIds.clear();
    });
  }

  void _toggleListSelect(int id) {
    setState(() {
      if (!_selectedListIds.remove(id)) _selectedListIds.add(id);
    });
  }

  /// 批量删除：确认后逐个删除（清单数量少，循环调用即可）。
  Future<void> _confirmDeleteLists() async {
    final ids = _selectedListIds.toList();
    if (ids.isEmpty) return;
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('删除采购单'),
        content: Text('确定删除选中的 ${ids.length} 张采购单？'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('取消')),
          TextButton(
              onPressed: () => Navigator.pop(ctx, true),
              child: Text('删除', style: AppTokens.of(ctx).textStyles.danger)),
        ],
      ),
    );
    if (ok != true) return;
    try {
      for (final id in ids) {
        await ShoppingService.deleteList(id);
      }
      _snack('已删除 ${ids.length} 张采购单');
      _exitListSelectMode();
      _loadLists();
    } catch (_) {
      _snack('删除失败');
    }
  }

  /// 批量选择勾选圈：选中=实心勾，未选=空心。
  Widget _selectCircle(bool selected, AppTokens t) {
    return Container(
      width: 22,
      height: 22,
      decoration: BoxDecoration(
        color: selected ? t.primary : null,
        border: Border.all(color: selected ? t.primary : t.border, width: 1.5),
        borderRadius: BorderRadius.circular(6),
      ),
      child: selected
          ? const Icon(Icons.check, size: 14, color: Colors.white)
          : null,
    );
  }

  /// 空态引导：清单来源（备菜一键加采购 / 新建自定义清单）。
  Widget _buildEmpty(AppTokens t) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text('还没有采购清单', style: t.textStyles.md.copyWith(color: t.title)),
            const SizedBox(height: 8),
            Text('清单来自食集：去食集详情 → 备菜 Tab →「一键加采购」；\n或者点右上角「+ 新建清单」自己列',
                textAlign: TextAlign.center,
                style: t.textStyles.sm.copyWith(color: t.caption, height: 1.7)),
          ],
        ),
      ),
    );
  }

  Widget _buildListCard(ShoppingList l) {
    final t = AppTokens.of(context);
    final seq = (l.id % 100) + 1;
    final time = l.createdAt ?? '';
    final displayTime = time.length >= 16 ? time.substring(5, 16) : time;
    final title = l.name != null && l.name!.isNotEmpty
        ? l.name!
        : '采购单 · ${l.sourceLabel} · 第$seq 单';
    return Card(
      margin: const EdgeInsets.only(bottom: 8),
      elevation: 0,
      shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(AppTokens.rMd),
          side: BorderSide(color: t.border)),
      child: ListTile(
        leading: _listSelectMode
            ? _selectCircle(_selectedListIds.contains(l.id), t)
            : CircleAvatar(
                backgroundColor: t.primary.withAlpha(25),
                child: Text('#$seq', style: t.textStyles.sm.copyWith(color: t.primary)),
              ),
        title: Text(title, style: t.textStyles.body.copyWith(fontWeight: FontWeight.w600)),
        subtitle: Text(l.dateRange.isNotEmpty ? l.dateRange : displayTime,
            style: t.textStyles.sm.copyWith(color: t.caption)),
        trailing: _listSelectMode
            ? const SizedBox.shrink()
            : Icon(Icons.chevron_right, color: t.caption),
        onTap: _listSelectMode ? () => _toggleListSelect(l.id) : () => _openDetail(l.id),
        onLongPress: _listSelectMode ? null : () => _confirmDeleteList(l.id),
      ),
    );
  }

  Future<void> _confirmDeleteList(int id) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('删除采购单'),
        content: const Text('确定删除整张采购单？'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('取消')),
          TextButton(
              onPressed: () => Navigator.pop(ctx, true),
              child: Text('删除', style: AppTokens.of(ctx).textStyles.danger)),
        ],
      ),
    );
    if (ok == true) {
      try {
        await ShoppingService.deleteList(id);
        _loadLists();
      } catch (_) {
        _snack('删除失败');
      }
    }
  }

  // ===== UI: 详情视图 =====

}
