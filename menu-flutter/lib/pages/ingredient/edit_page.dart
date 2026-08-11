import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../core/app_theme.dart';
import '../../core/api_client.dart';
import '../../services/ingredient_service.dart';
import '../../widgets/loading_empty.dart';

/// 食材编辑页（对齐 ingredient-manage.html 编辑卡）。
///
/// 默认单位 + 单位换算表（+ 加一个换算 / ✕ 删除）+ 单价 + 采购品类 + ✕ 删除食材。
/// 保存：updateIngredient(默认单位/单价/品类) + saveUnitGrams(换算表)。
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

  // 食材基本信息
  String _name = '';
  double _price = 0;
  int? _unitId;
  int? _categoryId;
  List<DictItem> _units = [];
  List<DictItem> _categories = [];

  // 换算表
  final List<UnitGram> _rows = [];

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      // 食材详情（GET /ingredient/{id}）
      final ing = await ApiClient.instance
          .get('/ingredient/${widget.ingredientId}');
      // 单位换算（GET /ingredient/{id}/unit-grams）
      final grams = await IngredientService.fetchUnitGrams(widget.ingredientId);
      // 字典
      _units = await IngredientService.listDictByGroup('unit');
      _categories = await IngredientService.listDictByGroup('purchase_category');

      if (mounted) {
        setState(() {
          _name = (ing is Map) ? (ing['name'] ?? '') as String : '';
          _price = (ing is Map)
              ? (ing['price'] as num?)?.toDouble() ?? 0
              : 0;
          _unitId = (ing is Map) ? (ing['unitId'] as num?)?.toInt() : null;
          _categoryId = (ing is Map)
              ? (ing['purchaseCategoryId'] as num?)?.toInt()
              : null;
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
      // 1. 更新食材基本信息
      await IngredientService.updateIngredient({
        'id': widget.ingredientId,
        'name': _name,
        'unitId': _unitId,
        'price': _price,
        'purchaseCategoryId': _categoryId,
      });
      // 2. 保存换算表（整体替换）
      await IngredientService.saveUnitGrams(
          widget.ingredientId, _rows.map((r) => r.toJson()).toList());
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(const SnackBar(content: Text('已保存')));
        context.pop();
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text('保存失败: $e')));
      }
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final t = _t;
    return Scaffold(
      body: SafeArea(
        bottom: false,
        child: Column(
          children: [
            // BackHeader 箭头行（§13.3）
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 0),
              child: Row(children: [
                GestureDetector(
                  onTap: () => context.pop(),
                  child: Text('‹',
                      style:
                          TextStyle(fontSize: 22, color: t.title, fontWeight: FontWeight.w800)),
                ),
                const Spacer(),
                // 删除食材
                GestureDetector(
                  onTap: _confirmDelete,
                  child: Text('✕', style: TextStyle(fontSize: 14, color: t.caption)),
                ),
              ]),
            ),
            Expanded(
              child: _loading
                  ? const LoadingView()
                  : _buildBody(t),
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
                      ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
                      : const Text('保存', style: TextStyle(fontWeight: FontWeight.w800)),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildBody(AppTokens t) {
    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
      children: [
        // 食材名
        Text('食材名', style: t.textStyles.sectionLabel.copyWith(letterSpacing: 1)),
        const SizedBox(height: 6),
        TextField(
          controller: TextEditingController(text: _name),
          onChanged: (v) => _name = v,
          decoration: InputDecoration(
            hintText: '食材名',
            filled: true,
            fillColor: t.bg,
            border: OutlineInputBorder(borderRadius: BorderRadius.circular(AppTokens.rMd)),
          ),
        ),
        const SizedBox(height: 14),

        // 默认单位
        Text('默认单位', style: t.textStyles.sectionLabel.copyWith(letterSpacing: 1)),
        const SizedBox(height: 6),
        _buildTagRow(t, _units, _unitId, (id) => setState(() => _unitId = id)),
        const SizedBox(height: 14),

        // 单价
        Text('单价（元）', style: t.textStyles.sectionLabel.copyWith(letterSpacing: 1)),
        const SizedBox(height: 6),
        TextField(
          controller: TextEditingController(text: _price == 0 ? '' : _price.toStringAsFixed(2)),
          onChanged: (v) => _price = double.tryParse(v) ?? 0,
          keyboardType: TextInputType.number,
          decoration: InputDecoration(
            hintText: '如 3.50',
            filled: true,
            fillColor: t.bg,
            border: OutlineInputBorder(borderRadius: BorderRadius.circular(AppTokens.rMd)),
          ),
        ),
        const SizedBox(height: 14),

        // 采购品类
        Text('采购品类', style: t.textStyles.sectionLabel.copyWith(letterSpacing: 1)),
        const SizedBox(height: 6),
        _buildTagRow(t, _categories, _categoryId, (id) => setState(() => _categoryId = id)),
        const SizedBox(height: 14),

        // 单位换算表
        Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
          Text('单位 → 克换算', style: t.textStyles.sectionLabel.copyWith(letterSpacing: 1)),
          GestureDetector(
            onTap: _addUnitGram,
            child: Text('+ 换算', style: t.textStyles.sm.copyWith(color: t.primary, fontWeight: FontWeight.w800)),
          ),
        ]),
        const SizedBox(height: 6),
        Container(
          decoration: BoxDecoration(
            color: t.card,
            border: Border.all(color: t.border),
            borderRadius: BorderRadius.circular(AppTokens.rSm),
          ),
          child: _rows.isEmpty
              ? Padding(
                  padding: const EdgeInsets.symmetric(vertical: 14),
                  child: Center(child: Text('没有换算，库存按 1:1 计', style: t.textStyles.sm.copyWith(color: t.caption))),
                )
              : Column(
                  children: [
                    for (int i = 0; i < _rows.length; i++)
                      _unitGramRow(t, i),
                  ],
                ),
        ),
        const SizedBox(height: 14),

        // 库存档位
        Container(
          padding: const EdgeInsets.all(10),
          decoration: BoxDecoration(
            color: t.highlight,
            border: Border.all(color: t.border),
            borderRadius: BorderRadius.circular(AppTokens.rSm),
          ),
          child: Text(
            '库存是 3 档（充足/不足/用完），不依赖换算。换算只影响菜价和营养计算。',
            style: t.textStyles.sm.copyWith(color: t.primaryDeep, height: 1.5),
          ),
        ),
      ],
    );
  }

  /// Tag 选择行（单位/品类）
  Widget _buildTagRow(AppTokens t, List<DictItem> items, int? selected, ValueChanged<int> onSelect) {
    return Wrap(
      spacing: 6,
      runSpacing: 6,
      children: items.map((d) {
        final sel = d.id == selected;
        return GestureDetector(
          onTap: () => onSelect(d.id),
          child: Container(
            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
            decoration: BoxDecoration(
              color: sel ? t.primary : t.highlight,
              border: Border.all(color: sel ? t.primary : t.border),
              borderRadius: BorderRadius.circular(99),
            ),
            child: Text(d.name,
                style: t.textStyles.chip.copyWith(
                    color: sel ? Colors.white : t.body,
                    fontWeight: FontWeight.w800)),
          ),
        );
      }).toList(),
    );
  }

  /// 换算表行：单位名 + 克/单位输入 + 默认 ✓ + ✕ 删除
  Widget _unitGramRow(AppTokens t, int i) {
    final row = _rows[i];
    final unitName = row.unitName ?? _units.where((u) => u.id == row.unitId).firstOrNull?.name ?? '';
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 7),
      child: Row(children: [
        Expanded(flex: 2, child: Text(unitName, style: t.textStyles.sm.copyWith(color: t.title))),
        const SizedBox(width: 6),
        SizedBox(
          width: 70,
          child: TextField(
            controller: TextEditingController(text: row.gramsPerUnit.toStringAsFixed(0)),
            onChanged: (v) => _rows[i] = UnitGram(
              id: row.id, unitId: row.unitId, unitName: row.unitName,
              gramsPerUnit: double.tryParse(v) ?? 0,
              isDefault: row.isDefault,
            ),
            keyboardType: TextInputType.number,
            decoration: InputDecoration(
              hintText: 'g',
              isDense: true,
              filled: true,
              fillColor: t.bg,
              border: OutlineInputBorder(borderRadius: BorderRadius.circular(AppTokens.rXs)),
            ),
          ),
        ),
        Text(' g', style: t.textStyles.sm.copyWith(color: t.caption)),
        const SizedBox(width: 6),
        // 默认单位（单选）
        GestureDetector(
          onTap: () => setState(() {
            for (int j = 0; j < _rows.length; j++) {
              _rows[j] = UnitGram(
                id: _rows[j].id, unitId: _rows[j].unitId, unitName: _rows[j].unitName,
                gramsPerUnit: _rows[j].gramsPerUnit,
                isDefault: j == i,
              );
            }
          }),
          child: Container(
            width: 20, height: 20,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              border: Border.all(color: row.isDefault ? t.primary : t.border, width: 1.5),
              color: row.isDefault ? t.primary : null,
            ),
            child: row.isDefault
                ? const Icon(Icons.check, size: 12, color: Colors.white)
                : null,
          ),
        ),
        const SizedBox(width: 6),
        // 删除
        GestureDetector(
          onTap: () => setState(() => _rows.removeAt(i)),
          child: const Icon(Icons.close, size: 16, color: AppTokens.error),
        ),
      ]),
    );
  }

  /// 加一个换算行：选单位 + 填克数
  void _addUnitGram() {
    // 弹选单位
    showModalBottomSheet<int>(
      context: context,
      backgroundColor: _t.card,
      builder: (ctx) => SafeArea(
        child: ListView(
          shrinkWrap: true,
          children: [
            const Padding(padding: EdgeInsets.all(16), child: Text('选单位')),
            for (final u in _units)
              ListTile(
                title: Text(u.name),
                onTap: () => Navigator.pop(ctx, u.id),
              ),
          ],
        ),
      ),
    ).then((unitId) {
      if (unitId != null) {
        final unitName = _units.where((u) => u.id == unitId).firstOrNull?.name;
        setState(() {
          _rows.add(UnitGram(
            unitId: unitId,
            unitName: unitName,
            gramsPerUnit: 0,
            isDefault: _rows.isEmpty,
          ));
        });
      }
    });
  }

  /// 删除食材确认
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
}
