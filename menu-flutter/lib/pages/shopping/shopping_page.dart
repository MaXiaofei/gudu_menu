import 'package:flutter/material.dart';
import 'package:share_plus/share_plus.dart';

import '../../core/app_theme.dart';
import '../../core/api_client.dart';
import '../../services/shopping_service.dart';
import '../../widgets/loading_empty.dart';

/// 采购清单页。
///
/// 列表页：按时间倒序展示所有采购单，点击进详情，FAB 新建。
/// 详情/生成页：4 个 Tab（周计划/菜品/菜单/自定义文本） + 采购单内容管理。
class ShoppingPage extends StatefulWidget {
  const ShoppingPage({super.key});
  @override
  State<ShoppingPage> createState() => _ShoppingPageState();
}

class _ShoppingPageState extends State<ShoppingPage> {
  // 列表
  List<ShoppingList> _lists = [];
  bool _loading = true;
  // 分页：每页 10 条，滚动到底加载更多
  static const _pageSize = 10;
  final _scroll = ScrollController();
  int _page = 1;
  bool _hasMore = false;
  bool _loadingMore = false;

  // 当前打开的采购单（null=列表视图）
  ShoppingListVO? _detail;
  bool _detailLoading = false;

  // 生成模式
  String _genType = 'plan'; // plan/dish/menu/custom
  final _customTextCtrl = TextEditingController();

  // 生成数据源
  List<Map<String, dynamic>> _plans = [];
  List<Map<String, dynamic>> _dishes = [];
  List<Map<String, dynamic>> _menus = [];
  int? _selectedPlanId;
  List<int> _selectedDishIds = [];
  int? _selectedMenuId;
  bool _genLoading = false;
  bool _genDataLoading = false;
  String _dishSearch = '';

  // 手动添加
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
    _customTextCtrl.dispose();
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

  /// 滚动到底加载下一页。
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
    setState(() => _detailLoading = true);
    try {
      _detail = await ShoppingService.detail(id);
    } catch (_) {
      _snack('加载采购单失败');
    }
    if (mounted) setState(() => _detailLoading = false);
  }

  void _closeDetail() {
    setState(() => _detail = null);
    _loadLists();
  }

  void _snack(String msg) {
    if (!mounted) return;
    ScaffoldMessenger.of(context)
        .showSnackBar(SnackBar(content: Text(msg), duration: const Duration(seconds: 2)));
  }

  // ===== 分享 =====

  String _buildShareText() {
    if (_detail == null) return '';
    final buf = StringBuffer();
    buf.writeln('📋 采购单 #${_detail!.id}  ${_detail!.sourceLabel}');
    if (_detail!.startDate != null) {
      buf.writeln('${_detail!.startDate} ~ ${_detail!.endDate}');
    }
    buf.writeln();
    for (final entry in _detail!.grouped.entries) {
      final catName = _detail!.categoryNames[entry.key] ?? '其他';
      buf.writeln('$catName：');
      for (final item in entry.value) {
        final check = item.isPurchased ? '✓' : '☐';
        buf.writeln('  $check ${item.displayName}  ${item.amountText}');
      }
    }
    buf.writeln();
    buf.writeln('—— 来自：咕嘟小食单');
    return buf.toString();
  }

  void _share() {
    final text = _buildShareText();
    if (text.isNotEmpty) Share.share(text);
  }

  // ===== UI: 列表视图 =====

  @override
  Widget build(BuildContext context) {
    if (_detail != null) return _buildDetailView();
    return _buildListView();
  }

  Widget _buildListView() {
    final t = AppTokens.of(context);
    return Scaffold(
      appBar: AppBar(title: const Text('采购清单')),
      body: _loading
          ? const LoadingView()
          : _lists.isEmpty
              ? Center(child: Text('暂无采购单', style: TextStyle(color: t.caption)))
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
                      final l = _lists[i];
                      return _buildListCard(l);
                    },
                  ),
                ),
      floatingActionButton: FloatingActionButton(
        onPressed: () async {
          try {
            final id = await ShoppingService.createEmpty();
            if (mounted) _openDetail(id);
          } catch (e) {
            _snack('创建失败');
          }
        },
        child: const Icon(Icons.add),
      ),
    );
  }

  Widget _buildListCard(ShoppingList l) {
    final t = AppTokens.of(context);
    final seq = (l.id % 100) + 1;
    final time = l.createdAt ?? '';
    final displayTime = time.length >= 16 ? time.substring(5, 16) : time;
    return Card(
      margin: const EdgeInsets.only(bottom: 8),
      elevation: 0,
      shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(AppTokens.rMd),
          side: BorderSide(color: t.border)),
      child: ListTile(
        leading: CircleAvatar(
          backgroundColor: t.primary.withAlpha(25),
          child: Text('#$seq', style: TextStyle(color: t.primary, fontSize: 12)),
        ),
        title: Text('采购单 · ${l.sourceLabel} · 第$seq 单',
            style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w600)),
        subtitle: Text(l.dateRange.isNotEmpty ? l.dateRange : displayTime,
            style: TextStyle(fontSize: 12, color: t.caption)),
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

  // ===== UI: 详情/生成视图 =====

  Widget _buildDetailView() {
    final t = AppTokens.of(context);
    final d = _detail!;
    return Scaffold(
      appBar: AppBar(
        leading: IconButton(icon: const Icon(Icons.arrow_back), onPressed: _closeDetail),
        title: Text('采购单 #${d.id}'),
        actions: [
          IconButton(icon: const Icon(Icons.share), onPressed: _share),
        ],
      ),
      body: _detailLoading
          ? const LoadingView()
          : Column(
              children: [
                // 生成区
                if (d.items.isEmpty) _buildGenerateSection(),
                // 详情
                Expanded(
                  child: d.items.isEmpty
                      ? Center(child: Text('暂无采购项，上方生成或下方添加',
                          style: TextStyle(color: t.caption)))
                      : ListView(
                          padding: const EdgeInsets.all(12),
                          children: [
                            _buildDetailHeader(d),
                            const SizedBox(height: 8),
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
                ]),
              ),
            )
          : null,
    );
  }

  Widget _buildGenerateSection() {
    final t = AppTokens.of(context);
    return Container(
      margin: const EdgeInsets.all(12),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: t.highlight,
        borderRadius: BorderRadius.circular(AppTokens.rMd),
      ),
      child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        const Text('从哪里生成', style: TextStyle(fontSize: 14, fontWeight: FontWeight.bold)),
        const SizedBox(height: 8),
        Row(children: [
          _genTab('plan', '周计划'),
          _genTab('dish', '菜品'),
          _genTab('menu', '菜单'),
          _genTab('custom', '自定义'),
        ]),
        const SizedBox(height: 8),
        if (_genDataLoading)
          const Padding(padding: EdgeInsets.all(12), child: LoadingView())
        else if (_genType == 'plan')
          _buildPlanPicker()
        else if (_genType == 'dish')
          _buildDishPicker()
        else if (_genType == 'menu')
          _buildMenuPicker()
        else ...[
          TextField(
            controller: _customTextCtrl,
            maxLines: 5,
            decoration: InputDecoration(
              hintText: '输入采购内容，每行一项：\n土豆 3斤\n排骨 2斤\n生抽 1瓶',
              filled: true, fillColor: t.card,
              border: OutlineInputBorder(borderRadius: BorderRadius.circular(AppTokens.rSm)),
            ),
          ),
          const SizedBox(height: 8),
        ],
        SizedBox(
          height: 44,
          child: ElevatedButton(
            onPressed: _genLoading ? null : _doGenerate,
            child: _genLoading
                ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
                : const Text('生成清单', style: TextStyle(fontSize: 14)),
          ),
        ),
      ]),
    );
  }

  void _onGenTypeChange(String type) {
    setState(() { _genType = type; _loadGenData(); });
  }

  Future<void> _loadGenData() async {
    setState(() => _genDataLoading = true);
    try {
      if (_genType == 'plan') {
        final d = await ApiClient.instance.get('/mealplan', query: {'pageNum': 1, 'pageSize': 50});
        _plans = (d is Map && d['records'] is List) ? (d['records'] as List).cast<Map<String, dynamic>>() : [];
      } else if (_genType == 'dish') {
        final d = await ApiClient.instance.get('/dish/search', query: {'pageNum': 1, 'pageSize': 100});
        _dishes = (d is Map && d['records'] is List) ? (d['records'] as List).cast<Map<String, dynamic>>() : [];
      } else if (_genType == 'menu') {
        final d = await ApiClient.instance.get('/menu', query: {'pageNum': 1, 'pageSize': 50});
        _menus = (d is Map && d['records'] is List) ? (d['records'] as List).cast<Map<String, dynamic>>() : [];
      }
    } catch (_) {}
    if (mounted) setState(() => _genDataLoading = false);
  }

  Future<void> _doGenerate() async {
    if (_genLoading) return;
    setState(() => _genLoading = true);
    try {
      int? newId;
      if (_genType == 'plan' && _selectedPlanId != null) {
        newId = await ShoppingService.generateFrom('plan', sourceId: _selectedPlanId);
      } else if (_genType == 'dish' && _selectedDishIds.isNotEmpty) {
        newId = await ShoppingService.generateFrom('dish', sourceIds: _selectedDishIds);
      } else if (_genType == 'menu' && _selectedMenuId != null) {
        newId = await ShoppingService.generateFrom('menu', sourceId: _selectedMenuId);
      } else if (_genType == 'custom') {
        newId = await ShoppingService.generateFromText(_customTextCtrl.text.trim());
      }
      if (newId != null) {
        _snack('已生成');
        _customTextCtrl.clear();
        setState(() { _genLoading = false; });
        _openDetail(newId);
        return;
      }
      _snack('请先选择数据源');
    } catch (e) { _snack('生成失败'); }
    if (mounted) setState(() => _genLoading = false);
  }

  Widget _genTab(String type, String label) {
    final t = AppTokens.of(context);
    final active = _genType == type;
    return Expanded(
      child: GestureDetector(
        onTap: () => _onGenTypeChange(type),
        child: Container(
          padding: const EdgeInsets.symmetric(vertical: 8),
          decoration: BoxDecoration(
            color: active ? t.primary : t.card,
            borderRadius: BorderRadius.circular(AppTokens.rSm),
          ),
          child: Text(label, textAlign: TextAlign.center,
              style: TextStyle(fontSize: 12, color: active ? t.card : t.caption,
                  fontWeight: active ? FontWeight.w600 : FontWeight.normal)),
        ),
      ),
    );
  }

  // ===== 生成 picker =====

  Widget _buildPlanPicker() {
    final t = AppTokens.of(context);
    if (_plans.isEmpty) return _emptyHint('暂无周计划');
    return SizedBox(
      height: 44,
      child: ListView.builder(
        scrollDirection: Axis.horizontal,
        itemCount: _plans.length,
        itemBuilder: (_, i) {
          final p = _plans[i];
          final sel = _selectedPlanId == p['id'];
          return Padding(
            padding: const EdgeInsets.only(right: 8),
            child: GestureDetector(
              onTap: () => setState(() => _selectedPlanId = sel ? null : p['id'] as int),
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                decoration: BoxDecoration(
                  color: sel ? t.primary : t.card,
                  borderRadius: BorderRadius.circular(AppTokens.rMd),
                  border: Border.all(color: sel ? t.primary : t.border),
                ),
                child: Row(mainAxisSize: MainAxisSize.min, children: [
                  Text(p['name'] ?? '${p['weekStart']}起',
                      style: TextStyle(fontSize: 12, fontWeight: FontWeight.w500,
                          color: sel ? t.card : t.title)),
                  if (p['itemCount'] != null) ...[
                    const SizedBox(width: 4),
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 2),
                      decoration: BoxDecoration(
                        color: sel ? Colors.white24 : t.primary.withAlpha(20),
                        borderRadius: BorderRadius.circular(AppTokens.rSm),
                      ),
                      child: Text('${p['itemCount']}菜',
                          style: TextStyle(fontSize: 10, color: sel ? Colors.white70 : t.primary)),
                    ),
                  ],
                ]),
              ),
            ),
          );
        },
      ),
    );
  }

  Widget _buildDishPicker() {
    final t = AppTokens.of(context);
    final filtered = _dishSearch.isEmpty
        ? _dishes
        : _dishes.where((d) => (d['name'] ?? '').toString().contains(_dishSearch)).toList();
    return Column(children: [
      if (_dishes.length > 10)
        Padding(
          padding: const EdgeInsets.only(bottom: 8),
          child: TextField(
            decoration: InputDecoration(
              hintText: '搜索菜品…',
              isDense: true, contentPadding: const EdgeInsets.symmetric(horizontal: 8, vertical: 8),
              filled: true, fillColor: t.card,
              border: OutlineInputBorder(borderRadius: BorderRadius.circular(AppTokens.rSm), borderSide: BorderSide(color: t.border)),
              prefixIcon: const Icon(Icons.search, size: 18),
              suffixIcon: _dishSearch.isNotEmpty
                  ? GestureDetector(onTap: () => setState(() => _dishSearch = ''), child: const Icon(Icons.close, size: 18))
                  : null,
            ),
            onChanged: (v) => setState(() => _dishSearch = v),
          ),
        ),
      if (_selectedDishIds.isNotEmpty)
        Padding(
          padding: const EdgeInsets.only(bottom: 8),
          child: Text('已选 ${_selectedDishIds.length} 道菜',
              style: TextStyle(fontSize: 12, color: t.primary)),
        ),
      SizedBox(
        height: 120,
        child: filtered.isEmpty
            ? Center(child: Text(_dishes.isEmpty ? '暂无菜品' : '无匹配菜品',
                style: TextStyle(color: t.caption, fontSize: 12)))
            : ListView.builder(
                itemCount: filtered.take(50).length,
                itemBuilder: (_, i) {
                  final d = filtered[i];
                  final id = d['id'] as int;
                  final sel = _selectedDishIds.contains(id);
                  return CheckboxListTile(
                    dense: true,
                    contentPadding: EdgeInsets.zero,
                    visualDensity: VisualDensity.compact,
                    title: Text(d['name'] ?? '', style: const TextStyle(fontSize: 12)),
                    value: sel,
                    onChanged: (v) => setState(() {
                      if (v == true) { _selectedDishIds.add(id); } else { _selectedDishIds.remove(id); }
                    }),
                  );
                },
              ),
      ),
    ]);
  }

  Widget _buildMenuPicker() {
    final t = AppTokens.of(context);
    if (_menus.isEmpty) return _emptyHint('暂无菜单');
    return SizedBox(
      height: 44,
      child: ListView.builder(
        scrollDirection: Axis.horizontal,
        itemCount: _menus.length,
        itemBuilder: (_, i) {
          final m = _menus[i];
          final sel = _selectedMenuId == m['id'];
          return Padding(
            padding: const EdgeInsets.only(right: 8),
            child: GestureDetector(
              onTap: () => setState(() => _selectedMenuId = sel ? null : m['id'] as int),
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                decoration: BoxDecoration(
                  color: sel ? t.primary : t.card,
                  borderRadius: BorderRadius.circular(AppTokens.rMd),
                  border: Border.all(color: sel ? t.primary : t.border),
                ),
                child: Text(m['name'] ?? '菜单 #${m['id']}',
                    style: TextStyle(fontSize: 12, fontWeight: FontWeight.w500,
                        color: sel ? t.card : t.title)),
              ),
            ),
          );
        },
      ),
    );
  }

  Widget _emptyHint(String text) {
    final t = AppTokens.of(context);
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8),
      child: Text(text, style: TextStyle(color: t.caption, fontSize: 12)),
    );
  }

  void _showAddSheet() {
    final t = AppTokens.of(context);
    _addNameCtrl.clear();
    _addAmountCtrl.clear();
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(
          borderRadius: BorderRadius.vertical(top: Radius.circular(AppTokens.rLg))),
      builder: (ctx) => Padding(
        padding: EdgeInsets.only(
            left: 16, right: 16, top: 16, bottom: MediaQuery.of(ctx).viewInsets.bottom + 16),
        child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          const Text('手动添加', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
          const SizedBox(height: 12),
          TextField(
            controller: _addNameCtrl,
            decoration: InputDecoration(
                hintText: '食材名', filled: true, fillColor: t.bg,
                border: OutlineInputBorder(borderRadius: BorderRadius.circular(AppTokens.rMd))),
          ),
          const SizedBox(height: 8),
          TextField(
            controller: _addAmountCtrl,
            keyboardType: TextInputType.number,
            decoration: InputDecoration(
                hintText: '数量（可留空）', filled: true, fillColor: t.bg,
                border: OutlineInputBorder(borderRadius: BorderRadius.circular(AppTokens.rMd))),
          ),
          const SizedBox(height: 12),
          SizedBox(
            height: 44,
            child: ElevatedButton(
              onPressed: () async {
                final name = _addNameCtrl.text.trim();
                if (name.isEmpty) {
                  ScaffoldMessenger.of(ctx).showSnackBar(const SnackBar(content: Text('请输入食材名')));
                  return;
                }
                final amt = double.tryParse(_addAmountCtrl.text.trim());
                try {
                  await ShoppingService.addCustomItem(_detail!.id, name, amount: amt);
                  Navigator.pop(ctx);
                  _openDetail(_detail!.id);
                } catch (e) {
                  ScaffoldMessenger.of(ctx).showSnackBar(SnackBar(content: Text('添加失败: $e')));
                }
              },
              child: const Text('添加', style: TextStyle(fontSize: 14)),
            ),
          ),
        ]),
      ),
    );
  }

  Widget _buildDetailHeader(ShoppingListVO d) {
    final t = AppTokens.of(context);
    return Row(children: [
      Container(
        width: 4, height: 18,
        decoration: BoxDecoration(color: t.primary, borderRadius: BorderRadius.circular(2)),
      ),
      const SizedBox(width: 8),
      Text('${d.sourceLabel} · #${d.id}',
          style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold)),
      const Spacer(),
      Text(d.dateRange, style: TextStyle(fontSize: 12, color: t.caption)),
    ]);
  }

  Widget _buildCategorySection(
      String catKey, String catName, List<ShoppingItemVO> items) {
    final t = AppTokens.of(context);
    return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      const SizedBox(height: 12),
      Text(catName, style: TextStyle(fontSize: 14, fontWeight: FontWeight.w600, color: t.primary)),
      const SizedBox(height: 4),
      ...items.map((it) => _buildItemTile(it)),
    ]);
  }

  Widget _buildItemTile(ShoppingItemVO it) {
    final t = AppTokens.of(context);
    return Container(
      padding: const EdgeInsets.symmetric(vertical: 8, horizontal: 4),
      decoration: BoxDecoration(
          border: Border(bottom: BorderSide(color: t.border))),
      child: Row(children: [
        GestureDetector(
          onTap: () async {
            await ShoppingService.togglePurchased(it.id);
            _openDetail(_detail!.id);
          },
          child: Container(
            width: 22, height: 22,
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(AppTokens.rXs),
              border: Border.all(color: it.isPurchased ? t.primary : t.border),
              color: it.isPurchased ? t.primary : null,
            ),
            child: it.isPurchased ? Icon(Icons.check, size: 14, color: t.card) : null,
          ),
        ),
        const SizedBox(width: 8),
        Expanded(
          child: Text(
            it.displayName,
            style: TextStyle(
              fontSize: 14,
              color: it.isPurchased ? t.caption : t.title,
              decoration: it.isPurchased ? TextDecoration.lineThrough : null,
            ),
          ),
        ),
        Text(it.amountText, style: TextStyle(fontSize: 12, color: t.caption)),
        const SizedBox(width: 8),
        _buildStockBadge(it),
        const SizedBox(width: 8),
        GestureDetector(
          onTap: () => _confirmDeleteItem(it),
          child: Icon(Icons.close, size: 16, color: t.caption),
        ),
      ]),
    );
  }

  /// 行尾三色余色徽章（Plan B）。
  ///
  /// - `RED_NONE` 🔴 没有（差 shortageGrams g）
  /// - `YELLOW_SHORT` 🟡 差 shortageGrams g
  /// - `GREEN_ENOUGH` 🟢 够
  /// - stockStatus=null（customName 项或无用量）→ 灰色「手动加」
  Widget _buildStockBadge(ShoppingItemVO it) {
    final t = AppTokens.of(context);
    final status = it.stockStatus;
    if (status == 'RED_NONE') {
      final short = _fmtGrams(it.shortageGrams);
      return _badge('没有${short.isEmpty ? '' : ' 差$short'}', AppTokens.error);
    }
    if (status == 'YELLOW_SHORT') {
      return _badge('差 ${_fmtGrams(it.shortageGrams)}', AppTokens.warning);
    }
    if (status == 'GREEN_ENOUGH') {
      return _badge('够', AppTokens.success);
    }
    // null：customName 手动加项或无用量 → 灰色「手动加」
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
        child: Text(text, style: TextStyle(fontSize: 10, fontWeight: FontWeight.w800, color: t.card)),
      );
  }

  static String _fmtGrams(double? g) {
    if (g == null) return '';
    if (g == g.roundToDouble()) return '${g.toInt()}g';
    return '${g.toStringAsFixed(1)}g';
  }

  Future<void> _confirmDeleteItem(ShoppingItemVO it) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('删除采购项'),
        content: Text('确定删除「${it.displayName}」？'),
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
        await ShoppingService.deleteItem(it.id);
        _openDetail(_detail!.id);
      } catch (_) {
        _snack('删除失败');
      }
    }
  }
}
