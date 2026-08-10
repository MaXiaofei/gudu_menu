import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:share_plus/share_plus.dart';

import '../../core/app_theme.dart';
import '../../services/shopping_service.dart';
import '../../widgets/loading_empty.dart';

/// 采购清单页（V42：采购唯一家园）。
///
/// 列表页：所有采购单（食集备菜一键加采购生成 / 自定义新建）+「+ 新建清单」。
/// 详情页：全选 + 勾选=本地选择态（不逐项写库）→ 底部「保存入库 · N 项」批量；
/// 行尾 ✕：未入库=移除确认；已入库=撤回入库确认（恢复入库前档位）。
/// 分享：右上角 → 全屏预览页（所见即所得）→ 复制文字 / 转图片。
/// 自定义采购：空清单「添加」弹窗（名称+数量单位一框、逐条添加、行尾删除）+ 标题旁 ✎ 改名。
class ShoppingPage extends StatefulWidget {
  const ShoppingPage({super.key});
  @override
  State<ShoppingPage> createState() => _ShoppingPageState();
}

class _ShoppingPageState extends State<ShoppingPage> {
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
        title: const Text('采购清单'),
        actions: [
          // 操作行：新建（自定义采购入口）
          TextButton.icon(
            onPressed: _createList,
            icon: const Icon(Icons.add, size: 18),
            label: const Text('新建清单'),
            style: TextButton.styleFrom(foregroundColor: t.primary),
          ),
          const SizedBox(width: 8),
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
        leading: CircleAvatar(
          backgroundColor: t.primary.withAlpha(25),
          child: Text('#$seq', style: t.textStyles.sm.copyWith(color: t.primary)),
        ),
        title: Text(title, style: t.textStyles.body.copyWith(fontWeight: FontWeight.w600)),
        subtitle: Text(l.dateRange.isNotEmpty ? l.dateRange : displayTime,
            style: t.textStyles.sm.copyWith(color: t.caption)),
        trailing: Icon(Icons.chevron_right, color: t.caption),
        onTap: () => _openDetail(l.id),
        onLongPress: () => _confirmDeleteList(l.id),
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
              child: const Text('删除', style: TextStyle(color: AppTokens.error))),
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

  Widget _buildDetailView() {
    final t = AppTokens.of(context);
    final d = _detail!;
    return Scaffold(
      appBar: AppBar(
        leading: IconButton(icon: const Icon(Icons.arrow_back), onPressed: _closeDetail),
        title: _buildTitle(t, d),
        actions: [
          IconButton(icon: const Icon(Icons.share), onPressed: _openShare),
        ],
      ),
      body: _detailLoading
          ? const LoadingView()
          : Column(
              children: [
                // 全选行（§15：有勾选必须提供全选）
                if (d.items.isNotEmpty) _buildSelectAllRow(t),
                Expanded(
                  child: d.items.isEmpty
                      ? _buildEmptyDetail(t)
                      : ListView(
                          padding: const EdgeInsets.all(12),
                          children: [
                            _buildDetailHeader(d),
                            const SizedBox(height: 4),
                            for (final entry in d.grouped.entries)
                              _buildCategorySection(
                                  entry.key, d.categoryNames[entry.key] ?? '其他', entry.value),
                            const SizedBox(height: 60),
                          ],
                        ),
                ),
              ],
            ),
      bottomNavigationBar: d.items.isNotEmpty
          ? SafeArea(
              child: Padding(
                padding: const EdgeInsets.fromLTRB(16, 8, 16, 8),
                child: Row(children: [
                  Expanded(
                    child: OutlinedButton.icon(
                      onPressed: _showAddSheet,
                      icon: const Icon(Icons.add, size: 18),
                      label: const Text('手动添加'),
                    ),
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    flex: 2,
                    child: ElevatedButton(
                      style: ElevatedButton.styleFrom(
                        backgroundColor: t.primary,
                        foregroundColor: Colors.white,
                        minimumSize: const Size.fromHeight(44),
                      ),
                      onPressed: _selectedItems.isEmpty ? null : _saveRestock,
                      child: Text('保存入库 · ${_selectedItems.length} 项'),
                    ),
                  ),
                ]),
              ),
            )
          : null,
    );
  }

  /// 标题：自定义清单显示 name + ✎ 改名；食集清单显示「采购单 #N」。
  Widget _buildTitle(AppTokens t, ShoppingListVO d) {
    final hasName = d.name != null && d.name!.isNotEmpty;
    return GestureDetector(
      onTap: hasName ? _showRenameSheet : null,
      child: Row(mainAxisSize: MainAxisSize.min, children: [
        Text(hasName ? d.name! : '采购单 #${d.id}',
            style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w700)),
        if (hasName) ...[
          const SizedBox(width: 4),
          Icon(Icons.edit_outlined, size: 16, color: t.primary),
        ],
      ]),
    );
  }

  /// 全选行：勾选/取消勾选全部未入库项。
  Widget _buildSelectAllRow(AppTokens t) {
    final unpurchased = _detail!.items.where((it) => it.purchased == 0).toList();
    final allSelected =
        unpurchased.isNotEmpty && unpurchased.every((it) => _selectedItems.contains(it.id));
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      decoration: BoxDecoration(
        color: t.card,
        border: Border(bottom: BorderSide(color: t.border)),
      ),
      child: Row(children: [
        GestureDetector(
          onTap: () => setState(() {
            if (allSelected) {
              _selectedItems.removeAll(unpurchased.map((it) => it.id));
            } else {
              _selectedItems.addAll(unpurchased.map((it) => it.id));
            }
          }),
          child: Row(children: [
            Icon(
              allSelected ? Icons.check_box : Icons.check_box_outline_blank,
              color: allSelected ? t.primary : t.caption,
              size: 22,
            ),
            const SizedBox(width: 6),
            Text('全选', style: t.textStyles.md.copyWith(color: t.title, fontWeight: FontWeight.w600)),
          ]),
        ),
        const Spacer(),
        Text('已选 ${_selectedItems.length} 项',
            style: t.textStyles.sm.copyWith(color: _selectedItems.isEmpty ? t.caption : t.primary)),
      ]),
    );
  }

  /// 空清单（自定义采购）：提示 + 底栏「添加」由 bottomNavigationBar 提供。
  Widget _buildEmptyDetail(AppTokens t) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text('还没有采购项', style: t.textStyles.md.copyWith(color: t.title)),
            const SizedBox(height: 8),
            Text('点下方「添加」列要买的——食材、生活用品都行，当备忘单用。\n匹配到食材库的勾选后可入库，匹配不到的照常列、只标已买',
                textAlign: TextAlign.center,
                style: t.textStyles.sm.copyWith(color: t.caption, height: 1.7)),
          ],
        ),
      ),
    );
  }

  Widget _buildDetailHeader(ShoppingListVO d) {
    final t = AppTokens.of(context);
    final hasName = d.name != null && d.name!.isNotEmpty;
    return Row(children: [
      Container(
        width: 4, height: 18,
        decoration: BoxDecoration(color: t.primary, borderRadius: BorderRadius.circular(2)),
      ),
      const SizedBox(width: 8),
      Expanded(
        child: Text(hasName ? d.name! : '${d.sourceLabel} · #${d.id}',
            style: t.textStyles.pageTitle),
      ),
      Text(d.dateRange, style: t.textStyles.sm.copyWith(color: t.caption)),
    ]);
  }

  Widget _buildCategorySection(
      String catKey, String catName, List<ShoppingItemVO> items) {
    final t = AppTokens.of(context);
    return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      const SizedBox(height: 12),
      Text(catName, style: t.textStyles.md.copyWith(fontWeight: FontWeight.w600, color: t.primary)),
      const SizedBox(height: 4),
      ...items.map((it) => _buildItemTile(it)),
    ]);
  }

  /// 单项：
  /// - 未入库：勾选框=本地选择态；行尾 ✕ = 移除确认
  /// - 已入库：✓ 固定 + 删除线 + 已入库徽标；行尾 ✕ = 撤回入库确认
  Widget _buildItemTile(ShoppingItemVO it) {
    final t = AppTokens.of(context);
    final bought = it.purchased == 1;
    final selected = _selectedItems.contains(it.id);
    return InkWell(
      // 点整行切换勾选（未入库项；已入库项固定不可点）
      onTap: bought
          ? null
          : () => setState(() {
                if (!_selectedItems.remove(it.id)) {
                  _selectedItems.add(it.id);
                }
              }),
      child: Container(
      padding: const EdgeInsets.symmetric(vertical: 8, horizontal: 4),
      decoration: BoxDecoration(
          border: Border(bottom: BorderSide(color: t.border))),
      child: Row(children: [
        // 勾选：未入库项本地选择态；已入库项固定 ✓
        Container(
          width: 22, height: 22,
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(AppTokens.rXs),
            border: Border.all(
                color: bought
                    ? AppTokens.success
                    : (selected ? t.primary : t.border),
                width: bought || selected ? 1.5 : 1),
            color: bought ? AppTokens.success : (selected ? t.primary : null),
          ),
          child: bought || selected
              ? Icon(Icons.check, size: 14, color: t.card)
              : null,
        ),
        const SizedBox(width: 8),
        Expanded(
          child: Text(
            it.displayName,
            style: t.textStyles.md.copyWith(
              color: bought ? t.caption : (selected ? t.title : t.title),
              decoration: bought ? TextDecoration.lineThrough : null,
            ),
          ),
        ),
        Text(it.amountText, style: t.textStyles.sm.copyWith(color: t.caption)),
        const SizedBox(width: 8),
        _buildStockBadge(it),
        const SizedBox(width: 8),
        // 行尾 ✕：未入库=移除；已入库=撤回入库
        GestureDetector(
          onTap: bought ? () => _confirmUndoRestock(it) : () => _confirmRemove(it),
          child: Padding(
            padding: const EdgeInsets.all(4),
            child: Icon(Icons.close, size: 16, color: t.caption),
          ),
        ),
      ]),
      ),
    );
  }

  /// 行尾三色余量徽章（V42 档位版：家里：用完/不足/充足）。
  Widget _buildStockBadge(ShoppingItemVO it) {
    final t = AppTokens.of(context);
    final status = it.stockStatus;
    if (status == 'RED_NONE') return _badge('家里：用完', AppTokens.error);
    if (status == 'YELLOW_SHORT') return _badge('家里：不足', AppTokens.warning);
    if (status == 'GREEN_ENOUGH') return _badge('家里：充足', AppTokens.success);
    // null：customName 手动加项或无食材关联 → 灰
    return _badge('手动加', t.caption);
  }

  Widget _badge(String text, Color color) {
    final t = AppTokens.of(context);
    return Container(
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
        decoration: BoxDecoration(
          color: color,
          borderRadius: BorderRadius.circular(AppTokens.rPill),
        ),
        child: Text(text, style: t.textStyles.chip.copyWith(color: t.card)),
      );
  }

  // ===== 批量保存入库 / 移除 / 撤回 =====

  /// 保存入库：勾选的项一次性入库（默认记充足），手动项只标已买。
  Future<void> _saveRestock() async {
    if (_selectedItems.isEmpty) return;
    try {
      final r = await ShoppingService.restock(_selectedItems.toList());
      if (!mounted) return;
      final msg = r.markedOnly > 0
          ? '已入库 ${r.restocked} 项，${r.markedOnly} 项只标已买'
          : '已入库 ${r.restocked} 项';
      _snack(msg);
      _openDetail(_detail!.id); // 刷新详情（清空选择态）
    } catch (e) {
      _snack('保存入库失败：$e');
    }
  }

  /// 未入库项 ✕：移除确认（不会丢，可从备菜一键加采购/重新生成加回）。
  Future<void> _confirmRemove(ShoppingItemVO it) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('移除采购项'),
        content: Text('移除「${it.displayName}」？\n以后要买，从备菜「一键加采购」或重新生成清单就能加回来。'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('取消')),
          TextButton(
              onPressed: () => Navigator.pop(ctx, true),
              child: const Text('移除', style: TextStyle(color: AppTokens.error))),
        ],
      ),
    );
    if (ok == true) {
      try {
        await ShoppingService.deleteItem(it.id);
        _openDetail(_detail!.id);
      } catch (_) {
        _snack('移除失败');
      }
    }
  }

  /// 已入库项 ✕：撤回入库确认（恢复入库前档位，流水记「撤回入库」）。
  Future<void> _confirmUndoRestock(ShoppingItemVO it) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text('撤回「${it.displayName}」的入库？'),
        content: const Text('撤回后库存回到入库前的状态，这项从清单移除。流水里会记一笔「撤回入库」。'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('留着')),
          TextButton(
              onPressed: () => Navigator.pop(ctx, true),
              child: const Text('撤回入库', style: TextStyle(color: AppTokens.error))),
        ],
      ),
    );
    if (ok == true) {
      try {
        await ShoppingService.undoRestock(it.id);
        _snack('已撤回入库');
        _openDetail(_detail!.id);
      } catch (_) {
        _snack('撤回失败');
      }
    }
  }

  // ===== 分享预览视图 =====

  void _openShare() => setState(() => _showShare = true);

  Widget _buildShareView() {
    final t = AppTokens.of(context);
    final d = _detail!;
    // 未入库项（要买的）= 分享内容
    final unpurchased = d.items.where((it) => it.purchased == 0).toList();
    return Scaffold(
      appBar: AppBar(
        leading: IconButton(
            icon: const Icon(Icons.arrow_back), onPressed: () => setState(() => _showShare = false)),
        title: const Text('分享采购清单'),
      ),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 10, 16, 4),
            child: Text('预览与导出的内容、格式完全一致',
                style: t.textStyles.sm.copyWith(color: t.caption)),
          ),
          Expanded(
            child: Stack(
              children: [
                ListView(
                  padding: const EdgeInsets.all(16),
                  children: [
                    if (unpurchased.isEmpty)
                      Text('清单里的都入库了，没有要分享的项',
                          style: t.textStyles.sm.copyWith(color: t.caption)),
                  ],
                ),
                // 屏幕外分享卡片：仅用于截图（RepaintBoundary.toImage），不参与显示
                Positioned(
                  left: -10000,
                  top: 0,
                  child: RepaintBoundary(
                    key: _shareBoundaryKey,
                    child: _ShareCard(listName: _shareTitle(d), items: unpurchased),
                  ),
                ),
              ],
            ),
          ),
          Container(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 12),
            decoration: BoxDecoration(
              color: t.card,
              border: Border(top: BorderSide(color: t.border)),
            ),
            child: Column(children: [
              _shareAction(t, '文', '复制文字', '粘贴到微信/备忘录，别人照着买', _copyShare),
              const SizedBox(height: 8),
              _shareAction(t, '图', '转图片分享', '生成一张清单图，发群里/相册', _shareImage),
            ]),
          ),
        ],
      ),
    );
  }

  Widget _shareAction(AppTokens t, String icon, String title, String desc, VoidCallback onTap) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 11),
        decoration: BoxDecoration(
          border: Border.all(color: t.border, width: 1.5),
          borderRadius: BorderRadius.circular(AppTokens.rMd),
        ),
        child: Row(children: [
          Container(
            width: 34, height: 34,
            decoration: BoxDecoration(color: t.secondary, borderRadius: BorderRadius.circular(10)),
            alignment: Alignment.center,
            child: Text(icon, style: t.textStyles.sm.copyWith(color: t.primaryDeep)),
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(title, style: t.textStyles.md.copyWith(color: t.title, fontWeight: FontWeight.w800)),
              Text(desc, style: t.textStyles.sm.copyWith(color: t.caption)),
            ]),
          ),
        ]),
      ),
    );
  }

  /// 分享标题：自定义清单名 或 食集清单「采购单 #N」。
  String _shareTitle(ShoppingListVO d) {
    final hasName = d.name != null && d.name!.isNotEmpty;
    return hasName ? d.name! : '采购单 #${d.id}';
  }

  /// 分享文字：标题 + 日期 + 未入库项逐行「名称 用量」。
  Future<void> _copyShare() async {
    final d = _detail!;
    final now = DateTime.now();
    final lines = d.items
        .where((it) => it.purchased == 0)
        .map((it) => it.amountText.isEmpty
            ? it.displayName
            : '${it.displayName} ${it.amountText}')
        .toList();
    final text = [
      '${_shareTitle(d)} · ${now.month} 月 ${now.day} 日',
      ...lines,
    ].join('\n');
    await Clipboard.setData(ClipboardData(text: text));
    if (mounted) _snack('采购清单已复制');
  }

  /// 转图片分享：截图屏幕外分享卡片 → PNG → 系统分享面板（share_plus）。
  Future<void> _shareImage() async {
    try {
      await WidgetsBinding.instance.endOfFrame;
      final boundary = _shareBoundaryKey.currentContext?.findRenderObject()
          as RenderRepaintBoundary?;
      if (boundary == null) return;
      final image = await boundary.toImage(pixelRatio: 3);
      final bytes = (await image.toByteData(format: ui.ImageByteFormat.png))!
          .buffer
          .asUint8List();
      await Share.shareXFiles(
        [XFile.fromData(bytes, mimeType: 'image/png', name: 'shopping_list.png')],
        text: '采购清单',
      );
    } catch (_) {
      if (mounted) _snack('生成分享图片失败');
    }
  }

  // ===== 手动添加（自定义采购） =====

  /// 添加弹窗：名称 + 数量单位一个框（如 2斤），逐条添加、行尾删除、最后保存。
  void _showAddSheet() {
    final t = AppTokens.of(context);
    final rows = <(String, String)>[]; // (名称, 数量文本)
    final nameCtrl = TextEditingController();
    final amountCtrl = TextEditingController();
    showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(
          borderRadius: BorderRadius.vertical(top: Radius.circular(AppTokens.rLg))),
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setSheetState) => Padding(
          padding: EdgeInsets.only(
              left: 16, right: 16, top: 16, bottom: MediaQuery.of(ctx).viewInsets.bottom + 16),
          child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.stretch, children: [
            Text('添加采购项', style: AppTokens.of(ctx).textStyles.subtitle),
            const SizedBox(height: 4),
            Text('食材、生活用品都可以记（当备忘单用）',
                style: t.textStyles.sm.copyWith(color: t.caption)),
            const SizedBox(height: 12),
            // 输入行：名称 + 数量单位一个框 + 添加
            Row(children: [
              Expanded(
                flex: 2,
                child: TextField(
                  controller: nameCtrl,
                  decoration: InputDecoration(
                      hintText: '名称',
                      isDense: true,
                      filled: true,
                      fillColor: t.bg,
                      border: OutlineInputBorder(borderRadius: BorderRadius.circular(AppTokens.rMd))),
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: TextField(
                  controller: amountCtrl,
                  decoration: InputDecoration(
                      hintText: '数量+单位 · 如 2斤',
                      isDense: true,
                      filled: true,
                      fillColor: t.bg,
                      border: OutlineInputBorder(borderRadius: BorderRadius.circular(AppTokens.rMd))),
                ),
              ),
              const SizedBox(width: 8),
              ElevatedButton(
                style: ElevatedButton.styleFrom(backgroundColor: t.primary, foregroundColor: Colors.white),
                onPressed: () {
                  final name = nameCtrl.text.trim();
                  if (name.isEmpty) return;
                  setSheetState(() {
                    rows.add((name, amountCtrl.text.trim()));
                    nameCtrl.clear();
                    amountCtrl.clear();
                  });
                },
                child: const Text('添加'),
              ),
            ]),
            const SizedBox(height: 12),
            // 已添加列表：行尾 ✕ 删除
            if (rows.isNotEmpty) ...[
              Text('已添加 ${rows.length} 种', style: t.textStyles.sectionLabel.copyWith(letterSpacing: 1)),
              const SizedBox(height: 4),
              for (int i = 0; i < rows.length; i++)
                Padding(
                  padding: const EdgeInsets.symmetric(vertical: 5),
                  child: Row(children: [
                    Expanded(
                      child: Text(
                        '${rows[i].$1}${rows[i].$2.isEmpty ? '' : '  ${rows[i].$2}'}',
                        style: t.textStyles.md.copyWith(color: t.title),
                      ),
                    ),
                    GestureDetector(
                      onTap: () => setSheetState(() => rows.removeAt(i)),
                      child: const Padding(
                        padding: EdgeInsets.all(4),
                        child: Icon(Icons.close, size: 16),
                      ),
                    ),
                  ]),
                ),
            ],
            const SizedBox(height: 12),
            SizedBox(
              height: 44,
              child: ElevatedButton(
                onPressed: () async {
                  if (rows.isEmpty) {
                    ScaffoldMessenger.of(ctx)
                        .showSnackBar(const SnackBar(content: Text('先添加至少一项')));
                    return;
                  }
                  try {
                    for (final (name, amountText) in rows) {
                      // 数量单位一个框：解析数字部分，单位忽略（备忘单场景够用）
                      final amount = double.tryParse(RegExp(r'\d+(\.\d+)?').firstMatch(amountText)?.group(0) ?? '');
                      await ShoppingService.addCustomItem(_detail!.id, name, amount: amount);
                    }
                    if (ctx.mounted) Navigator.pop(ctx);
                    _openDetail(_detail!.id);
                  } catch (e) {
                    if (ctx.mounted) {
                      ScaffoldMessenger.of(ctx).showSnackBar(SnackBar(content: Text('添加失败: $e')));
                    }
                  }
                },
                child: Text(rows.isEmpty ? '保存' : '添加 · 保存 ${rows.length} 种'),
              ),
            ),
          ]),
        ),
      ),
    );
  }

  /// 改名弹窗（自定义采购 ✎）。
  Future<void> _showRenameSheet() async {
    final t = AppTokens.of(context);
    final d = _detail!;
    final ctrl = TextEditingController(text: d.name ?? '');
    final result = await showDialog<String>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('修改清单名称'),
        content: Column(mainAxisSize: MainAxisSize.min, children: [
          Text('列表页和分享内容会同步显示新名字',
              style: t.textStyles.sm.copyWith(color: t.caption)),
          const SizedBox(height: 10),
          TextField(
            controller: ctrl,
            autofocus: true,
            decoration: InputDecoration(
              hintText: '清单名称',
              isDense: true,
              filled: true,
              fillColor: t.bg,
              border: OutlineInputBorder(borderRadius: BorderRadius.circular(AppTokens.rMd)),
            ),
          ),
        ]),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('取消')),
          TextButton(
              onPressed: () => Navigator.pop(ctx, ctrl.text.trim()),
              child: const Text('保存')),
        ],
      ),
    );
    if (result == null || result.isEmpty) return;
    try {
      await ShoppingService.renameList(d.id, result);
      _openDetail(d.id);
    } catch (_) {
      _snack('改名失败');
    }
  }
}

/// 分享卡片（仅截图用，置于屏幕外）：白底 + 清单名·日期 + 未入库项列表。
class _ShareCard extends StatelessWidget {
  final String listName;
  final List<ShoppingItemVO> items;
  const _ShareCard({required this.listName, required this.items});

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    final now = DateTime.now();
    return Container(
      width: 320,
      padding: const EdgeInsets.all(20),
      color: Colors.white,
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(listName, style: t.textStyles.pageTitle.copyWith(color: t.title)),
          Text('${now.month} 月 ${now.day} 日',
              style: t.textStyles.caption.copyWith(color: t.caption)),
          const SizedBox(height: 12),
          for (final it in items)
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 6),
              child: Row(
                children: [
                  Expanded(
                    child: Text(it.displayName,
                        style: t.textStyles.md.copyWith(color: t.title)),
                  ),
                  if (it.amountText.isNotEmpty)
                    Text(it.amountText,
                        style: t.textStyles.sm.copyWith(color: t.caption)),
                ],
              ),
            ),
        ],
      ),
    );
  }
}
