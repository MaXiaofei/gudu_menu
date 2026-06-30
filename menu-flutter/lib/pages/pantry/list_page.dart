import 'package:flutter/material.dart';

import '../../core/theme.dart';
import '../../services/ingredient_service.dart';
import '../../services/pantry_service.dart';

/// 批量行数据
class _BatchRow {
  final TextEditingController name = TextEditingController();
  final TextEditingController qty = TextEditingController();
  final TextEditingController expire = TextEditingController();
  String unit = '';

  void dispose() { name.dispose(); qty.dispose(); expire.dispose(); }
  String get nameText => name.text.trim();
  String get qtyText => qty.text.trim();
  String get expireText => expire.text.trim();

  /// 根据食材名自动推断单位
  void suggestUnit() {
    final n = nameText;
    if (n.isEmpty) { unit = ''; return; }
    unit = _matchUnit(n);
  }

  // 客户端单位匹配表（与后端 UnitMatcher 保持一致）
  static String _matchUnit(String name) {
    const map = {
      '牛奶': '盒', '酸奶': '盒', '豆浆': '杯', '油': '瓶', '酱油': '瓶', '醋': '瓶',
      '料酒': '瓶', '盐': '袋', '糖': '袋', '生抽': '瓶', '老抽': '瓶', '蚝油': '瓶',
      '排骨': '斤', '牛肉': '斤', '羊肉': '斤', '猪肉': '斤', '鸡肉': '斤', '鸭肉': '斤',
      '鸡腿': '斤', '鸡翅': '斤', '鸡胸': '斤', '鱼': '条', '虾': '斤', '蟹': '只',
      '蛋': '个', '鸡蛋': '个', '鸭蛋': '个', '白菜': '颗', '生菜': '把',
      '土豆': '个', '番茄': '个', '西红柿': '个', '黄瓜': '根', '胡萝卜': '根',
      '茄子': '个', '玉米': '根', '红薯': '个', '葱': '把', '姜': '块', '蒜': '头',
      '辣椒': '个', '青椒': '个', '蘑菇': '盒', '金针菇': '盒', '苹果': '个',
      '香蕉': '根', '梨': '个', '西瓜': '个', '葡萄': '串', '草莓': '盒',
      '米': '袋', '面': '袋', '面条': '把', '面包': '个', '馒头': '个',
      '豆腐': '块', '肉': '斤', '菜': '把', '果': '个', '奶': '盒',
    };
    for (final e in map.entries) {
      if (name.contains(e.key)) return e.value;
    }
    return '';
  }
}

/// 食材库存管理页。
///
/// 3 个 Tab：全部 / 临期 / 不足。
/// 每项卡片显示食材名、数量、过期日、阈值标记。
/// FAB 新增库存项 → 底部 Sheet 选食材 + 填数量/单位/过期日/阈值。
/// 点击卡片 → 编辑弹窗。
/// 长按 → 删除确认。
class PantryListPage extends StatefulWidget {
  const PantryListPage({super.key});

  @override
  State<PantryListPage> createState() => _PantryListPageState();
}

class _PantryListPageState extends State<PantryListPage>
    with SingleTickerProviderStateMixin {
  late final TabController _tabCtrl;

  List<PantryVO> _items = [];
  List<DictItem> _ingredients = [];
  bool _loading = true;

  // 分页（仅"全部"tab）：每页 10 条，滚动到底加载更多
  static const _pageSize = 10;
  final _scroll = ScrollController();
  int _page = 1;
  bool _hasMore = false;
  bool _loadingMore = false;
  bool get _paginated => _tabCtrl.index == 0;

  // 批量模式
  final List<_BatchRow> _batchRows = [];
  bool _batchSaving = false;

  // 表单
  final _amountCtrl = TextEditingController();
  final _expireCtrl = TextEditingController();
  final _thresholdCtrl = TextEditingController();
  final _nameCtrl = TextEditingController(); // 单品添加的食材名
  List<DictItem> _matchedIngredients = []; // 输入匹配结果

  @override
  void initState() {
    super.initState();
    _tabCtrl = TabController(length: 3, vsync: this);
    _tabCtrl.addListener(() {
      if (!_tabCtrl.indexIsChanging) _load();
    });
    _scroll.addListener(_onScroll);
    _load();
  }

  @override
  void dispose() {
    _scroll.dispose();
    _tabCtrl.dispose();
    _amountCtrl.dispose();
    _expireCtrl.dispose();
    _thresholdCtrl.dispose();
    _nameCtrl.dispose();
    super.dispose();
  }

  Future<void> _loadOptions() async {
    try {
      _ingredients = await IngredientService.listAll();
      if (mounted) setState(() {});
    } catch (_) {}
  }

  Future<void> _load() async {
    setState(() => _loading = true);
    _page = 1;
    _hasMore = false;
    try {
      switch (_tabCtrl.index) {
        case 0:
          // "全部" tab 走分页，首屏 10 条
          final pg = await PantryService.list(pageNum: 1, pageSize: _pageSize);
          _items = pg.records;
          _hasMore = pg.records.length >= _pageSize;
          break;
        case 1:
          _items = await PantryService.listExpiring();
          break;
        case 2:
          _items = await PantryService.listLow();
          break;
      }
    } catch (_) {
      _items = [];
    }
    _loadOptions();
    if (mounted) setState(() => _loading = false);
  }

  /// 滚动到底加载下一页（仅"全部"tab）。
  Future<void> _loadMore() async {
    if (!_paginated || _loadingMore || !_hasMore) return;
    setState(() => _loadingMore = true);
    try {
      _page++;
      final pg = await PantryService.list(pageNum: _page, pageSize: _pageSize);
      _items.addAll(pg.records);
      _hasMore = pg.records.length >= _pageSize;
    } catch (_) {
      _page--;
      _hasMore = false;
    }
    if (mounted) setState(() => _loadingMore = false);
  }

  void _onScroll() {
    if (!_scroll.hasClients || !_paginated || _loadingMore || !_hasMore) return;
    final pos = _scroll.position;
    if (pos.pixels >= pos.maxScrollExtent - 100) _loadMore();
  }

  // ===== 新增 / 编辑 =====

  void _showAddSheet() {
    _showFormSheet(isEdit: false, item: null);
  }

  void _showEditSheet(PantryVO item) {
    _showFormSheet(isEdit: true, item: item);
  }

  void _showFormSheet({required bool isEdit, PantryVO? item}) {
    _nameCtrl.text = item?.ingredientName ?? item?.displayName ?? '';
    _amountCtrl.text = item != null ? item.amount.toString() : '';
    _expireCtrl.text = item?.expireDate ?? '';
    _thresholdCtrl.text = item?.lowThreshold?.toString() ?? '';
    _matchedIngredients = [];

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
      ),
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setSheetState) => Padding(
          padding: EdgeInsets.only(
            left: 16, right: 16, top: 20,
            bottom: MediaQuery.of(ctx).viewInsets.bottom + 16,
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text(isEdit ? '编辑库存' : '添加库存',
                  style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
              const SizedBox(height: 14),
              // 食材输入框（替代 chip 列表）
              TextField(
                controller: _nameCtrl,
                autofocus: true,
                style: const TextStyle(fontSize: 15),
                decoration: InputDecoration(
                  labelText: isEdit ? '食材名' : '食材名（输入或选择）',
                  hintText: '如：牛奶、排骨',
                  prefixIcon: const Icon(Icons.search, size: 20),
                  filled: true, fillColor: const Color(0xFFFAFAFA),
                  border: OutlineInputBorder(borderRadius: BorderRadius.circular(10)),
                ),
                onChanged: (v) {
                  setSheetState(() {
                    final q = v.trim().toLowerCase();
                    if (q.isEmpty) {
                      _matchedIngredients = [];
                    } else {
                      _matchedIngredients = _ingredients
                          .where((i) => i.name.toLowerCase().contains(q))
                          .take(5)
                          .toList();
                    }
                  });
                },
              ),
              // 匹配结果下拉提示
              if (_matchedIngredients.isNotEmpty)
                Container(
                  margin: const EdgeInsets.only(top: 4),
                  decoration: BoxDecoration(
                    color: Colors.white,
                    borderRadius: BorderRadius.circular(8),
                    border: Border.all(color: const Color(0xFFE0E0E0)),
                  ),
                  child: Column(children: _matchedIngredients.map((ing) {
                    return ListTile(
                      dense: true,
                      title: Text(ing.name, style: const TextStyle(fontSize: 14)),
                      onTap: () {
                        _nameCtrl.text = ing.name;
                        setSheetState(() {
                          _matchedIngredients = [];
                        });
                      },
                    );
                  }).toList()),
                ),
              if (_nameCtrl.text.isNotEmpty && _matchedIngredients.isEmpty)
                Padding(
                  padding: const EdgeInsets.only(top: 4, left: 4),
                  child: Text('未匹配到已有食材，将创建新食材"${_nameCtrl.text.trim()}"',
                      style: const TextStyle(fontSize: 11, color: AppColors.textSecondary)),
                ),
              const SizedBox(height: 12),
              // 数量
              TextField(
                controller: _amountCtrl,
                keyboardType: TextInputType.number,
                decoration: InputDecoration(
                  labelText: '数量',
                  hintText: '如 500',
                  filled: true, fillColor: const Color(0xFFFAFAFA),
                  border: OutlineInputBorder(borderRadius: BorderRadius.circular(10)),
                ),
              ),
              const SizedBox(height: 8),
              // 过期日
              TextField(
                controller: _expireCtrl,
                decoration: InputDecoration(
                  labelText: '过期日（可选）',
                  hintText: 'yyyy-MM-dd',
                  suffixIcon: IconButton(
                    icon: const Icon(Icons.calendar_today, size: 18),
                    onPressed: () async {
                      final picked = await showDatePicker(
                        context: ctx,
                        initialDate: DateTime.now().add(const Duration(days: 7)),
                        firstDate: DateTime.now(),
                        lastDate: DateTime.now().add(const Duration(days: 365 * 2)),
                      );
                      if (picked != null) {
                        setSheetState(() {
                          _expireCtrl.text =
                              '${picked.year}-${picked.month.toString().padLeft(2, '0')}-${picked.day.toString().padLeft(2, '0')}';
                        });
                      }
                    },
                  ),
                  filled: true, fillColor: const Color(0xFFFAFAFA),
                  border: OutlineInputBorder(borderRadius: BorderRadius.circular(10)),
                ),
              ),
              const SizedBox(height: 8),
              // 低库存阈值
              TextField(
                controller: _thresholdCtrl,
                keyboardType: TextInputType.number,
                decoration: InputDecoration(
                  labelText: '低库存阈值（可选）',
                  hintText: '低于此量显示红色警告',
                  filled: true, fillColor: const Color(0xFFFAFAFA),
                  border: OutlineInputBorder(borderRadius: BorderRadius.circular(10)),
                ),
              ),
              const SizedBox(height: 16),
              SizedBox(
                height: 44,
                child: ElevatedButton(
                  onPressed: () async {
                    final name = _nameCtrl.text.trim();
                    if (name.isEmpty) {
                      _snack(ctx, '请输入食材名');
                      return;
                    }
                    final amt = double.tryParse(_amountCtrl.text.trim());
                    if (amt == null || amt <= 0) {
                      _snack(ctx, '请输入有效数量');
                      return;
                    }
                    final expire = _expireCtrl.text.trim().isNotEmpty ? _expireCtrl.text.trim() : null;
                    final threshold = _thresholdCtrl.text.trim().isNotEmpty
                        ? double.tryParse(_thresholdCtrl.text.trim())
                        : null;

                    try {
                      if (isEdit && item != null) {
                        await PantryService.update({
                          'id': item.id,
                          'amount': amt,
                          'expireDate': expire,
                          'lowThreshold': threshold,
                        });
                      } else {
                        // 用批量接口（后端按 name 匹配/创建 ingredient）
                        await PantryService.batchAdd([{
                          'name': name,
                          'amount': amt,
                          'expireDate': expire,
                        }]);
                      }
                      Navigator.pop(ctx);
                      _load();
                    } catch (e) {
                      _snack(ctx, '保存失败');
                    }
                  },
                  child: Text(isEdit ? '保存修改' : '添加', style: const TextStyle(fontSize: 15)),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  // ===== 批量添加 =====

  void _showBatchSheet() {
    if (_batchRows.isEmpty) {
      _batchRows.add(_BatchRow());
    }
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
      ),
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setSheetState) => Padding(
          padding: EdgeInsets.only(
            left: 12, right: 12, top: 16,
            bottom: MediaQuery.of(ctx).viewInsets.bottom + 16,
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Row(children: [
                const Text('批量添加', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
                const Spacer(),
                TextButton(
                  onPressed: () {
                    _batchRows.clear();
                    setSheetState(() {});
                  },
                  child: const Text('清空', style: TextStyle(color: AppColors.warnRed)),
                ),
              ]),
              const SizedBox(height: 4),
              // 表头
              Row(children: [
                const Expanded(flex: 3, child: Text('名称', style: TextStyle(fontSize: 12, color: AppColors.textSecondary))),
                const Expanded(flex: 2, child: Text('数量', style: TextStyle(fontSize: 12, color: AppColors.textSecondary))),
                const Expanded(flex: 1, child: Text('单位', style: TextStyle(fontSize: 12, color: AppColors.textSecondary))),
                const Expanded(flex: 2, child: Text('过期日', style: TextStyle(fontSize: 12, color: AppColors.textSecondary))),
                const SizedBox(width: 32),
              ]),
              const SizedBox(height: 4),
              SizedBox(
                height: 260,
                child: ListView.builder(
                  itemCount: _batchRows.length,
                  itemBuilder: (_, i) {
                    final r = _batchRows[i];
                    return Padding(
                      padding: const EdgeInsets.only(bottom: 6),
                      child: Row(children: [
                        Expanded(
                          flex: 3,
                          child: _batchField(r.name, '食材名', Icons.eco_outlined,
                            onChanged: (_) => setSheetState(() => r.suggestUnit()),
                          ),
                        ),
                        const SizedBox(width: 6),
                        Expanded(
                          flex: 2,
                          child: _batchField(r.qty, '500', null),
                        ),
                        const SizedBox(width: 6),
                        Expanded(
                          flex: 1,
                          child: _batchUnitChip(r, () => setSheetState(() {})),
                        ),
                        const SizedBox(width: 6),
                        Expanded(
                          flex: 2,
                          child: _batchField(r.expire, '7/5', Icons.calendar_today),
                        ),
                        GestureDetector(
                          onTap: () {
                            r.dispose();
                            _batchRows.removeAt(i);
                            setSheetState(() {});
                          },
                          child: const Icon(Icons.close, size: 18, color: Colors.grey),
                        ),
                      ]),
                    );
                  },
                ),
              ),
              const SizedBox(height: 8),
              OutlinedButton.icon(
                onPressed: () => setSheetState(() => _batchRows.add(_BatchRow())),
                icon: const Icon(Icons.add, size: 16),
                label: const Text('添加一行'),
              ),
              const SizedBox(height: 12),
              SizedBox(
                height: 44,
                child: ElevatedButton(
                  onPressed: _batchSaving ? null : () => _submitBatch(ctx),
                  child: _batchSaving
                      ? const SizedBox(width: 18, height: 18,
                          child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
                      : const Text('一键入库', style: TextStyle(fontSize: 15)),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _batchUnitChip(_BatchRow r, VoidCallback onRefresh) {
    return GestureDetector(
      onTap: () {
        final ctrl = TextEditingController(text: r.unit);
        showDialog(
          context: context,
          builder: (ctx) => AlertDialog(
            title: const Text('修改单位'),
            content: TextField(controller: ctrl, autofocus: true,
                decoration: const InputDecoration(hintText: '斤/盒/个/...')),
            actions: [
              TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('取消')),
              TextButton(onPressed: () {
                r.unit = ctrl.text.trim();
                Navigator.pop(ctx);
                onRefresh();
              }, child: const Text('确定')),
            ],
          ),
        );
      },
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 10),
        decoration: BoxDecoration(
          color: r.unit.isNotEmpty ? AppColors.primary.withAlpha(15) : const Color(0xFFF8F8F8),
          borderRadius: BorderRadius.circular(6),
          border: Border.all(color: r.unit.isNotEmpty ? AppColors.primary.withAlpha(60) : const Color(0xFFE0E0E0)),
        ),
        child: Text(
          r.unit.isNotEmpty ? r.unit : '?',
          textAlign: TextAlign.center,
          style: TextStyle(
            fontSize: 12,
            color: r.unit.isNotEmpty ? AppColors.primary : Colors.grey.shade400,
            fontWeight: r.unit.isNotEmpty ? FontWeight.w600 : FontWeight.normal,
          ),
        ),
      ),
    );
  }

  Widget _batchField(TextEditingController ctrl, String hint, IconData? icon,
      {ValueChanged<String>? onChanged}) {
    return TextField(
      controller: ctrl,
      onChanged: onChanged,
      style: const TextStyle(fontSize: 13),
      decoration: InputDecoration(
        hintText: hint,
        isDense: true,
        contentPadding: const EdgeInsets.symmetric(horizontal: 8, vertical: 10),
        filled: true, fillColor: const Color(0xFFF8F8F8),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(6),
          borderSide: const BorderSide(color: Color(0xFFE0E0E0)),
        ),
        prefixIcon: icon != null ? Icon(icon, size: 16) : null,
      ),
    );
  }

  Future<void> _submitBatch(BuildContext ctx) async {
    final valid = _batchRows.where((r) => r.nameText.isNotEmpty).toList();
    if (valid.isEmpty) { _snack(ctx, '请至少输入一个食材名'); return; }

    setState(() => _batchSaving = true);
    try {
      final items = valid.map((r) {
        final expire = r.expireText.isNotEmpty ? _normalizeDate(r.expireText) : null;
        return {
          'name': r.nameText,
          'amount': _parseQty(r.qtyText),
          if (r.unit.isNotEmpty) 'unit': r.unit,
          if (expire != null) 'expireDate': expire,
        };
      }).toList();

      final count = await PantryService.batchAdd(items);
      Navigator.pop(ctx);
      _batchRows.clear();
      if (mounted) _snack(context, '已添加 $count 项');
      _load();
    } catch (_) {
      _snack(ctx, '添加失败');
    } finally {
      setState(() => _batchSaving = false);
    }
  }

  /// 解析数量字符串：500g→500, 2斤→1000, 3盒→3, 空→1
  double _parseQty(String s) {
    if (s.isEmpty) return 1;
    final numMatch = RegExp(r'^(\d+\.?\d*)\s*(.*)').firstMatch(s.trim());
    if (numMatch == null) return 1;
    final v = double.tryParse(numMatch.group(1)!) ?? 1;
    final unit = numMatch.group(2)?.trim() ?? '';
    if (unit == '斤') return v * 500;
    if (unit == '公斤' || unit == 'kg') return v * 1000;
    if (unit == '两') return v * 50;
    return v;
  }

  /// 日期标准化：7/5 → 2026-07-05, 7-5 → 2026-07-05
  String? _normalizeDate(String s) {
    final m = RegExp(r'^(\d{1,2})[/-](\d{1,2})$').firstMatch(s.trim());
    if (m == null) return null;
    final month = int.parse(m.group(1)!).toString().padLeft(2, '0');
    final day = int.parse(m.group(2)!).toString().padLeft(2, '0');
    final year = DateTime.now().year;
    return '$year-$month-$day';
  }

  void _snack(BuildContext ctx, String msg) {
    ScaffoldMessenger.of(ctx)
        .showSnackBar(SnackBar(content: Text(msg), duration: const Duration(seconds: 2)));
  }

  // ===== 删除 =====

  Future<void> _deleteItem(PantryVO item) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('删除库存项'),
        content: Text('确定删除「${item.displayName}」？'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('取消')),
          TextButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('删除', style: TextStyle(color: AppColors.warnRed)),
          ),
        ],
      ),
    );
    if (ok == true) {
      try {
        await PantryService.delete(item.id);
        _load();
      } catch (_) {
        _snack(context, '删除失败');
      }
    }
  }

  // ===== UI =====

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('食材库存'),
        actions: [
          IconButton(
            icon: const Icon(Icons.playlist_add),
            tooltip: '批量添加',
            onPressed: _showBatchSheet,
          ),
        ],
        bottom: TabBar(
          controller: _tabCtrl,
          indicatorColor: Colors.white,
          labelColor: Colors.white,
          unselectedLabelColor: Colors.white60,
          tabs: const [
            Tab(text: '全部'),
            Tab(text: '临期'),
            Tab(text: '不足'),
          ],
        ),
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : _items.isEmpty
              ? Center(
                  child: Text(
                    _emptyText,
                    style: const TextStyle(color: AppColors.textSecondary, fontSize: 14),
                  ),
                )
              : RefreshIndicator(
                  onRefresh: _load,
                  child: ListView.builder(
                    controller: _scroll,
                    padding: const EdgeInsets.all(12),
                    itemCount: _items.length + (_loadingMore ? 1 : 0),
                    itemBuilder: (_, i) {
                      if (i == _items.length) {
                        return const Padding(
                          padding: EdgeInsets.symmetric(vertical: 16),
                          child: Center(
                            child: SizedBox(
                              width: 20, height: 20,
                              child: CircularProgressIndicator(strokeWidth: 2),
                            ),
                          ),
                        );
                      }
                      final item = _items[i];
                      return _buildCard(item);
                    },
                  ),
                ),
      floatingActionButton: FloatingActionButton(
        onPressed: _showAddSheet,
        child: const Icon(Icons.add),
      ),
    );
  }

  String get _emptyText {
    switch (_tabCtrl.index) {
      case 0: return '暂无库存';
      case 1: return '没有临期食材 ✨';
      case 2: return '库存充足 ✅';
      default: return '';
    }
  }

  // ===== 扣减 =====

  void _quickDeduct(PantryVO item) async {
    // 默认步长：1（个/盒/袋等离散单位）或 50（g/ml 等连续单位）
    final unit = item.unitName ?? '';
    final step = ['个', '盒', '袋', '瓶', '罐', '颗', '只', '条', '块', '把', '根']
            .any((u) => unit.contains(u)) ? 1.0 : 50.0;
    try {
      await PantryService.deduct(item.id, step);
      _load();
    } catch (_) { _snack(context, '扣减失败'); }
  }

  void _showDeductSheet(PantryVO item) {
    final ctrl = TextEditingController(text: '1');
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(
          borderRadius: BorderRadius.vertical(top: Radius.circular(16))),
      builder: (ctx) => Padding(
        padding: EdgeInsets.only(left: 16, right: 16, top: 20,
            bottom: MediaQuery.of(ctx).viewInsets.bottom + 16),
        child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          Text('使用 ${item.displayName}', style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
          const SizedBox(height: 4),
          Text('当前库存：${item.displayAmount}',
              style: const TextStyle(fontSize: 13, color: AppColors.textSecondary)),
          const SizedBox(height: 14),
          TextField(
            controller: ctrl,
            autofocus: true,
            keyboardType: TextInputType.number,
            decoration: InputDecoration(
              hintText: '用了多少？',
              suffixText: item.unitName ?? '',
              filled: true, fillColor: const Color(0xFFFAFAFA),
              border: OutlineInputBorder(borderRadius: BorderRadius.circular(10)),
            ),
          ),
          const SizedBox(height: 14),
          SizedBox(
            height: 44,
            child: ElevatedButton(
              onPressed: () async {
                final v = double.tryParse(ctrl.text.trim());
                if (v == null || v <= 0) { _snack(ctx, '请输入有效数量'); return; }
                try {
                  await PantryService.deduct(item.id, v);
                  Navigator.pop(ctx);
                  _load();
                } catch (_) { _snack(ctx, '扣减失败'); }
              },
              child: const Text('确认使用', style: TextStyle(fontSize: 15)),
            ),
          ),
        ]),
      ),
    );
  }

  Widget _miniBtn(String label, Color color, VoidCallback onTap) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
        decoration: BoxDecoration(
          color: color.withAlpha(15),
          borderRadius: BorderRadius.circular(12),
          border: Border.all(color: color.withAlpha(60)),
        ),
        child: Text(label, style: TextStyle(fontSize: 12, color: color, fontWeight: FontWeight.w600)),
      ),
    );
  }

  Widget _buildCard(PantryVO item) {
    final low = item.isLow;
    final expiring = item.isExpiring();
    final expired = item.isExpired;
    Color? borderColor;
    if (low) {
      borderColor = AppColors.warnRed;
    } else if (expiring || expired) {
      borderColor = AppColors.warnOrange;
    }

    return Card(
      margin: const EdgeInsets.only(bottom: 8),
      elevation: 0,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(10),
        side: borderColor != null
            ? BorderSide(color: borderColor, width: 1.5)
            : const BorderSide(color: Color(0xFFEEEEEE)),
      ),
      child: InkWell(
        borderRadius: BorderRadius.circular(10),
        onTap: () => _showEditSheet(item),
        onLongPress: () => _deleteItem(item),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Expanded(
                    child: Text(item.displayName,
                        style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w600)),
                  ),
                  Text(item.displayAmount,
                      style: const TextStyle(
                          fontSize: 14, fontWeight: FontWeight.w600, color: AppColors.primary)),
                  const SizedBox(width: 8),
                  _miniBtn('−', AppColors.warnRed, () => _quickDeduct(item)),
                  const SizedBox(width: 4),
                  _miniBtn('使用', AppColors.primary, () => _showDeductSheet(item)),
                ],
              ),
              const SizedBox(height: 4),
              Row(
                children: [
                  // 阈值
                  Text(
                    item.lowThreshold != null && item.lowThreshold! > 0
                        ? '阈值 ${_fmt(item.lowThreshold!)}'
                        : '无阈值',
                    style: const TextStyle(fontSize: 11, color: AppColors.textSecondary),
                  ),
                  const SizedBox(width: 12),
                  // 过期日
                  Text(
                    item.expireText,
                    style: TextStyle(
                      fontSize: 11,
                      color: expired
                          ? AppColors.warnRed
                          : expiring
                              ? AppColors.warnOrange
                              : AppColors.textSecondary,
                      fontWeight: (expired || expiring) ? FontWeight.w600 : FontWeight.normal,
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }

  String _fmt(double v) {
    if (v == v.roundToDouble()) return v.toInt().toString();
    return v.toStringAsFixed(1);
  }
}
