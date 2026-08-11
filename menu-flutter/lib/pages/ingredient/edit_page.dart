import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../core/app_theme.dart';
import '../../core/api_client.dart';
import '../../services/ingredient_service.dart';
import '../../widgets/loading_empty.dart';

/// 食材编辑页（对齐原型 pantry-ingredient.html 右侧卡片）。
///
/// 只读头部（名称 + 品类，食材本身来自食材库不可改名）+ 默认单位「改」+
/// 只读换算表「+ 加一个换算」+ 单价/食用属性并排卡片 + ✕ 删除食材。
/// 保存：updateIngredient(默认单位/单价/食用属性) + saveUnitGrams(换算表)。
class IngredientEditPage extends StatefulWidget {
  final int ingredientId;
  const IngredientEditPage({super.key, required this.ingredientId});

  @override
  State<IngredientEditPage> createState() => _IngredientEditPageState();
}

class _IngredientEditPageState extends State<IngredientEditPage> {
  AppTokens get _t => AppTokens.of(context);

  bool _loading = true;
  bool _saving = false;

  // 食材信息（只读展示 + 可编辑项）
  String _name = '';
  String? _categoryName;
  double _price = 0;
  int _edible = 1; // 1食用/2饮料零食/3生活用品
  int? _unitId;
  String? _unitName;
  List<DictItem> _units = [];

  // 换算表（只读展示，行尾 ✕ 可删，+ 加一个换算）
  final List<UnitGram> _rows = [];

  static const _edibleNames = {1: '食用', 2: '饮料零食', 3: '生活用品'};

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final ing = await ApiClient.instance.get('/ingredient/${widget.ingredientId}');
      final grams = await IngredientService.fetchUnitGrams(widget.ingredientId);
      final units = await IngredientService.listDictByGroup('unit');
      if (mounted) {
        setState(() {
          final m = ing is Map ? ing : const <String, dynamic>{};
          _name = (m['name'] ?? '') as String;
          _categoryName = m['categoryName'] as String?;
          _price = (m['price'] as num?)?.toDouble() ?? 0;
          _edible = (m['edible'] as num?)?.toInt() ?? 1;
          _unitId = (m['unitId'] as num?)?.toInt();
          _unitName = (m['unitName'] as String?)?.trim().isNotEmpty == true
              ? m['unitName'] as String
              : null;
          _units = units;
          _rows
            ..clear()
            ..addAll(grams);
          _loading = false;
        });
      }
    } catch (_) {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _save() async {
    if (_saving) return;
    setState(() => _saving = true);
    try {
      // 1. 默认单位 / 单价 / 食用属性
      await IngredientService.updateIngredient({
        'id': widget.ingredientId,
        'unitId': _unitId,
        'price': _price,
        'edible': _edible,
      });
      // 2. 换算表（整体替换）
      await IngredientService.saveUnitGrams(
          widget.ingredientId, _rows.map((r) => r.toJson()).toList());
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(const SnackBar(content: Text('已保存')));
        context.pop();
      }
    } catch (e) {
      if (mounted) _snack('保存失败: $e');
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  /// 默认单位「改」：弹底部单位列表。
  Future<void> _pickDefaultUnit() async {
    final id = await showModalBottomSheet<int>(
      context: context,
      backgroundColor: _t.card,
      builder: (ctx) => SafeArea(
        child: ListView(
          shrinkWrap: true,
          children: [
            Padding(
              padding: const EdgeInsets.all(16),
              child: Text('默认单位', style: _t.textStyles.md.copyWith(
                  fontWeight: FontWeight.w800, color: _t.title)),
            ),
            for (final u in _units)
              ListTile(
                title: Text(u.name,
                    style: _t.textStyles.sm.copyWith(color: _t.body)),
                trailing: u.id == _unitId
                    ? Icon(Icons.check, size: 18, color: _t.primary)
                    : null,
                onTap: () => Navigator.pop(ctx, u.id),
              ),
          ],
        ),
      ),
    );
    if (id != null && mounted) {
      setState(() {
        _unitId = id;
        _unitName = _units.where((u) => u.id == id).firstOrNull?.name;
      });
    }
  }

  /// 单价卡片：弹数字输入。
  Future<void> _editPrice() async {
    final ctrl = TextEditingController(
        text: _price == 0 ? '' : _price.toStringAsFixed(2));
    final v = await showDialog<double>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('单价'),
        content: TextField(
          controller: ctrl,
          autofocus: true,
          keyboardType: const TextInputType.numberWithOptions(decimal: true),
          decoration: const InputDecoration(hintText: '如 1.5'),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('取消')),
          TextButton(
            onPressed: () => Navigator.pop(ctx, double.tryParse(ctrl.text)),
            child: const Text('确定'),
          ),
        ],
      ),
    );
    if (v != null && mounted) setState(() => _price = v);
  }

  /// 食用属性卡片：弹三选。
  Future<void> _editEdible() async {
    final v = await showModalBottomSheet<int>(
      context: context,
      backgroundColor: _t.card,
      builder: (ctx) => SafeArea(
        child: ListView(
          shrinkWrap: true,
          children: [
            Padding(
              padding: const EdgeInsets.all(16),
              child: Text('食用属性', style: _t.textStyles.md.copyWith(
                  fontWeight: FontWeight.w800, color: _t.title)),
            ),
            for (final e in _edibleNames.entries)
              ListTile(
                title: Text(e.value,
                    style: _t.textStyles.sm.copyWith(color: _t.body)),
                trailing: e.key == _edible
                    ? Icon(Icons.check, size: 18, color: _t.primary)
                    : null,
                onTap: () => Navigator.pop(ctx, e.key),
              ),
          ],
        ),
      ),
    );
    if (v != null && mounted) setState(() => _edible = v);
  }

  /// 「+ 加一个换算」：弹单位选择 + 克数输入。
  Future<void> _addUnitGram() async {
    int? unitId;
    final gramCtrl = TextEditingController();
    final ok = await showModalBottomSheet<bool>(
      context: context,
      backgroundColor: _t.card,
      isScrollControlled: true,
      builder: (ctx) {
        final t = _t;
        return StatefulBuilder(
          builder: (ctx, setSheet) => SafeArea(
            child: Padding(
              padding: EdgeInsets.fromLTRB(
                  16, 16, 16, 16 + MediaQuery.of(ctx).viewInsets.bottom),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('加一个换算', style: t.textStyles.md.copyWith(
                      fontWeight: FontWeight.w800, color: t.title)),
                  const SizedBox(height: 10),
                  Wrap(
                    spacing: 6,
                    runSpacing: 6,
                    children: _units.map((u) {
                      final sel = u.id == unitId;
                      return GestureDetector(
                        onTap: () => setSheet(() => unitId = u.id),
                        child: Container(
                          padding: const EdgeInsets.symmetric(
                              horizontal: 10, vertical: 5),
                          decoration: BoxDecoration(
                            color: sel ? t.primary : t.highlight,
                            border: Border.all(
                                color: sel ? t.primary : t.border),
                            borderRadius: BorderRadius.circular(99),
                          ),
                          child: Text(u.name,
                              style: t.textStyles.chip.copyWith(
                                  color: sel ? Colors.white : t.body,
                                  fontWeight: FontWeight.w800)),
                        ),
                      );
                    }).toList(),
                  ),
                  const SizedBox(height: 12),
                  TextField(
                    controller: gramCtrl,
                    autofocus: true,
                    keyboardType:
                        const TextInputType.numberWithOptions(decimal: true),
                    decoration: InputDecoration(
                      hintText: '1 个单位 = 多少克',
                      suffixText: 'g',
                      filled: true,
                      fillColor: t.bg,
                      isDense: true,
                      border: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(AppTokens.rMd)),
                    ),
                  ),
                  const SizedBox(height: 14),
                  SizedBox(
                    width: double.infinity,
                    child: ElevatedButton(
                      style: ElevatedButton.styleFrom(
                        backgroundColor: t.primary,
                        foregroundColor: Colors.white,
                        minimumSize: const Size(0, 44),
                      ),
                      onPressed: unitId == null
                          ? null
                          : () {
                              final grams = double.tryParse(gramCtrl.text);
                              if (grams == null || grams <= 0) return;
                              final u = _units.firstWhere(
                                  (x) => x.id == unitId);
                              setState(() {
                                _rows.add(UnitGram(
                                    unitId: u.id,
                                    unitName: u.name,
                                    gramsPerUnit: grams,
                                    isDefault: false));
                              });
                              Navigator.pop(ctx, true);
                            },
                      child: const Text('确认添加',
                          style: TextStyle(fontWeight: FontWeight.w800)),
                    ),
                  ),
                ],
              ),
            ),
          ),
        );
      },
    );
    if (ok != true) return;
  }

  /// 删除食材确认。
  Future<void> _confirmDelete() async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('删除食材'),
        content: Text('确定删除「$_name」？关联的换算和用量记录会保留。'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('取消')),
          TextButton(
              onPressed: () => Navigator.pop(ctx, true),
              child: const Text('删除', style: TextStyle(color: AppTokens.error))),
        ],
      ),
    );
    if (ok != true) return;
    try {
      await IngredientService.deleteIngredient(widget.ingredientId);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('已删除')));
        context.pop();
      }
    } catch (_) {
      if (mounted) _snack('删除失败');
    }
  }

  void _snack(String msg) {
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(msg)));
  }

  @override
  Widget build(BuildContext context) {
    final t = _t;
    return Scaffold(
      body: SafeArea(
        bottom: false,
        child: Column(children: [
          // 顶栏：‹ 返回 + ✕ 删除（§13.3）
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 0),
            child: Row(children: [
              GestureDetector(
                onTap: () => context.pop(),
                child: Text('‹',
                    style: TextStyle(
                        fontSize: 22, color: t.title, fontWeight: FontWeight.w800)),
              ),
              const Spacer(),
              GestureDetector(
                onTap: _confirmDelete,
                child: Text('✕', style: TextStyle(fontSize: 14, color: t.caption)),
              ),
            ]),
          ),
          Expanded(
            child: _loading ? const LoadingView() : _buildBody(t),
          ),
          // 底部保存
          SafeArea(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 12),
              child: ElevatedButton(
                style: ElevatedButton.styleFrom(
                  backgroundColor: t.primary,
                  foregroundColor: Colors.white,
                  minimumSize: const Size.fromHeight(48),
                ),
                onPressed: _saving ? null : _save,
                child: _saving
                    ? const SizedBox(
                        width: 18,
                        height: 18,
                        child: CircularProgressIndicator(
                            strokeWidth: 2, color: Colors.white))
                    : const Text('保存', style: TextStyle(fontWeight: FontWeight.w800)),
              ),
            ),
          ),
        ]),
      ),
    );
  }

  Widget _buildBody(AppTokens t) {
    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 10, 16, 24),
      children: [
        // 只读头部：图标 + 名称 + 品类副标题（食材来自食材库，不可改名）
        Row(children: [
          Container(
            width: 46,
            height: 46,
            decoration: BoxDecoration(
              color: t.highlight,
              borderRadius: BorderRadius.circular(AppTokens.rSm),
            ),
            child: Icon(Icons.eco_outlined, size: 24, color: t.primary),
          ),
          const SizedBox(width: 10),
          Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(_name,
                style: t.textStyles.md.copyWith(
                    fontWeight: FontWeight.w800,
                    fontSize: 16,
                    color: t.title)),
            if (_categoryName != null && _categoryName!.isNotEmpty)
              Text(_categoryName!,
                  style: t.textStyles.xs.copyWith(color: t.caption)),
          ]),
        ]),
        const SizedBox(height: 14),

        // 默认单位（卡片行 + 改）
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
          decoration: BoxDecoration(
            color: t.card,
            border: Border.all(color: t.border),
            borderRadius: BorderRadius.circular(AppTokens.rMd),
          ),
          child: Row(children: [
            Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text('默认单位', style: t.textStyles.xs.copyWith(color: t.caption)),
              const SizedBox(height: 2),
              Text(_unitName ?? '未设',
                  style: t.textStyles.sm.copyWith(
                      fontWeight: FontWeight.w800, color: t.title)),
            ]),
            const Spacer(),
            GestureDetector(
              onTap: _pickDefaultUnit,
              child: Text('改',
                  style: t.textStyles.sm.copyWith(
                      color: t.primary, fontWeight: FontWeight.w800)),
            ),
          ]),
        ),
        const SizedBox(height: 12),

        // 单位 → 克换算（只读行 + 加一个换算）
        Text('单位 → 克换算',
            style: t.textStyles.sectionLabel.copyWith(letterSpacing: 1)),
        const SizedBox(height: 5),
        Container(
          decoration: BoxDecoration(
            color: t.card,
            border: Border.all(color: t.border),
            borderRadius: BorderRadius.circular(AppTokens.rMd),
          ),
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
          child: _rows.isEmpty
              ? Padding(
                  padding: const EdgeInsets.symmetric(vertical: 12),
                  child: Center(
                      child: Text('没设换算，菜价/营养算不准',
                          style: t.textStyles.sm.copyWith(color: t.caption))),
                )
              : Column(
                  children: [
                    for (int i = 0; i < _rows.length; i++) _gramRow(t, i),
                  ],
                ),
        ),
        const SizedBox(height: 6),
        GestureDetector(
          onTap: _addUnitGram,
          child: Container(
            padding: const EdgeInsets.symmetric(vertical: 8),
            decoration: BoxDecoration(
              border: Border.all(color: t.primary, style: BorderStyle.solid),
              borderRadius: BorderRadius.circular(AppTokens.rSm),
            ),
            child: Center(
              child: Text('+ 加一个换算',
                  style: t.textStyles.sm.copyWith(
                      color: t.primary, fontWeight: FontWeight.w800)),
            ),
          ),
        ),
        const SizedBox(height: 12),

        // 单价 + 食用属性（并排卡片，点卡片编辑）
        Row(children: [
          Expanded(
            child: GestureDetector(
              onTap: _editPrice,
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                decoration: BoxDecoration(
                  color: t.card,
                  border: Border.all(color: t.border),
                  borderRadius: BorderRadius.circular(AppTokens.rMd),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('单价', style: t.textStyles.xs.copyWith(color: t.caption)),
                    const SizedBox(height: 2),
                    Text(_price > 0
                        ? '¥${_fmtPrice(_price)} / ${_unitName ?? '-'}'
                        : '未设',
                        style: t.textStyles.sm.copyWith(
                            fontWeight: FontWeight.w800, color: t.title)),
                  ],
                ),
              ),
            ),
          ),
          const SizedBox(width: 8),
          Expanded(
            child: GestureDetector(
              onTap: _editEdible,
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                decoration: BoxDecoration(
                  color: t.card,
                  border: Border.all(color: t.border),
                  borderRadius: BorderRadius.circular(AppTokens.rMd),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('食用属性', style: t.textStyles.xs.copyWith(color: t.caption)),
                    const SizedBox(height: 2),
                    Text(_edibleNames[_edible] ?? '食用',
                        style: t.textStyles.sm.copyWith(
                            fontWeight: FontWeight.w800, color: t.title)),
                  ],
                ),
              ),
            ),
          ),
        ]),
        const SizedBox(height: 14),

        // 提示：换算影响菜价/营养；库存是档位不依赖换算
        Container(
          padding: const EdgeInsets.all(10),
          decoration: BoxDecoration(
            color: t.highlight,
            border: Border.all(color: t.border),
            borderRadius: BorderRadius.circular(AppTokens.rSm),
          ),
          child: Text(
            '换算影响：菜价/营养计算。库存是档位，不依赖换算。',
            style: t.textStyles.sm.copyWith(color: t.primaryDeep, height: 1.5),
          ),
        ),
      ],
    );
  }

  /// 换算表行：只读「1 个 = 50 g」+ 行尾 ✕ 删除。
  Widget _gramRow(AppTokens t, int i) {
    final row = _rows[i];
    final unitName = row.unitName ??
        _units.where((u) => u.id == row.unitId).firstOrNull?.name ??
        '?';
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8),
      child: Row(children: [
        Expanded(
          child: Text('1 $unitName',
              style: t.textStyles.sm.copyWith(color: t.body)),
        ),
        Text('= ${_fmtPrice(row.gramsPerUnit)} g',
            style: t.textStyles.sm.copyWith(
                fontWeight: FontWeight.w700, color: t.body)),
        const SizedBox(width: 8),
        GestureDetector(
          onTap: () => setState(() => _rows.removeAt(i)),
          child: Text('✕', style: TextStyle(fontSize: 12, color: t.caption)),
        ),
      ]),
    );
  }

  static String _fmtPrice(double v) =>
      v == v.roundToDouble() ? v.toInt().toString() : v.toStringAsFixed(1);
}
