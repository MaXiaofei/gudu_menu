import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../core/constants.dart';
import '../../core/app_theme.dart';
import '../../models/nutrition_metric.dart';
import '../../services/ai_service.dart';
import '../../services/dish_service.dart';
import '../../services/ingredient_service.dart';
import '../../widgets/loading_empty.dart';

/// 录入食材：
/// - 食材名 + AI 补全营养按钮
/// - 计量单位（从字典加载 Tag 列表，点选，也可输入自定义）
/// - 采购分类（从字典加载 Tag 列表，点选，也可输入自定义）
/// - 6 项营养指标（热量/蛋白/脂肪/碳水/糖/GI），AI 可自动填充
/// - 保存（自定义单位/分类自动补入 dict）
class CreateIngredientPage extends StatefulWidget {
  const CreateIngredientPage({super.key});

  @override
  State<CreateIngredientPage> createState() => _CreateIngredientPageState();
}

class _CreateIngredientPageState extends State<CreateIngredientPage> {
  final _nameCtrl = TextEditingController();
  final _nutritionMap = <int, TextEditingController>{};
  final _customUnitCtrl = TextEditingController();
  final _customCatCtrl = TextEditingController();

  List<DictItem> _units = [];
  List<DictItem> _purchases = [];
  List<NutritionMetric> _metrics = [];

  int? _unitId;
  int? _purchaseCategoryId;
  bool _showCustomUnit = false;
  bool _showCustomCat = false;

  bool _aiLoading = false;
  bool _saving = false;

  @override
  void initState() {
    super.initState();
    _loadDicts();
  }

  @override
  void dispose() {
    _nameCtrl.dispose();
    for (final c in _nutritionMap.values) { c.dispose(); }
    _customUnitCtrl.dispose();
    _customCatCtrl.dispose();
    super.dispose();
  }

  Future<void> _loadDicts() async {
    final results = await Future.wait([
      IngredientService.listDictByGroup('unit'),
      IngredientService.listDictByGroup('purchase_category'),
      DishService.metrics(),
    ]);
    setState(() {
      _units = results[0] as List<DictItem>;
      _purchases = results[1] as List<DictItem>;
      _metrics = results[2] as List<NutritionMetric>;
      for (final m in _metrics) {
        _nutritionMap.putIfAbsent(m.id, () => TextEditingController());
      }
    });
  }

  Future<void> _onAiFill() async {
    final name = _nameCtrl.text.trim();
    if (name.isEmpty) {
      _showSnack('请先输入食材名');
      return;
    }
    setState(() => _aiLoading = true);
    try {
      final result = await AiService.aiFillNutrition(name);
      final vm = result.valueMap;
      for (final m in _metrics) {
        if (vm.containsKey(m.id)) {
          _nutritionMap[m.id]?.text = vm[m.id]!.toString();
        }
      }
      _showSnack('AI 已填充，请核对 ($result.source)');
    } catch (e) {
      _showSnack('AI 补全失败: $e');
    } finally {
      if (mounted) setState(() => _aiLoading = false);
    }
  }

  Future<void> _onSave() async {
    final name = _nameCtrl.text.trim();
    if (name.isEmpty) {
      _showSnack('请输入食材名');
      return;
    }
    if (_unitId == null && !_showCustomUnit) {
      _showSnack('请选择计量单位');
      return;
    }
    if (_purchaseCategoryId == null && !_showCustomCat) {
      _showSnack('请选择采购分类');
      return;
    }
    final nutritions = <Map<String, dynamic>>[];
    for (final m in _metrics) {
      final raw = _nutritionMap[m.id]?.text.trim() ?? '';
      if (raw.isNotEmpty) {
        final v = double.tryParse(raw);
        if (v != null) {
          nutritions.add({'metricId': m.id, 'value': v});
        }
      }
    }
    if (nutritions.isEmpty) {
      _showSnack('请填写或 AI 补全营养指标');
      return;
    }

    setState(() => _saving = true);
    try {
      // 处理自定义单位/分类：先 upsert dict 获取 id
      int? unitId = _unitId;
      if (unitId == null && _showCustomUnit) {
        final customName = _customUnitCtrl.text.trim();
        if (customName.isNotEmpty) {
          unitId = await IngredientService.upsertDict(customName, 'unit');
        }
      }
      int? catId = _purchaseCategoryId;
      if (catId == null && _showCustomCat) {
        final customName = _customCatCtrl.text.trim();
        if (customName.isNotEmpty) {
          catId = await IngredientService.upsertDict(customName, 'purchase_category');
        }
      }

      await IngredientService.createIngredient({
        'ingredient': {
          'name': name,
          'unitId': unitId,
          'purchaseCategoryId': catId,
        },
        'nutritions': nutritions,
      });
      _showSnack('已保存');
      if (mounted) context.pop();
    } catch (e) {
      _showSnack('保存失败: $e');
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  void _showSnack(String msg) {
    if (!mounted) return;
    ScaffoldMessenger.of(context)
        .showSnackBar(SnackBar(content: Text(msg), duration: const Duration(seconds: 2)));
  }

  // ============ UI ============

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    return Scaffold(
      appBar: AppBar(title: const Text('录入食材')),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // 食材名 + AI 按钮
            Row(
              children: [
                Expanded(
                  child: TextField(
                    controller: _nameCtrl,
                    style: const TextStyle(fontSize: 16),
                    decoration: InputDecoration(
                      labelText: '食材名',
                      hintText: '如：番茄',
                      prefixIcon: const Icon(Icons.eco_outlined),
                      filled: true,
                      fillColor: t.bg,
                      border: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(AppTokens.rMd),
                        borderSide: BorderSide(color: t.border),
                      ),
                      focusedBorder: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(AppTokens.rMd),
                        borderSide: BorderSide(color: t.primary, width: 1.5),
                      ),
                    ),
                  ),
                ),
                const SizedBox(width: 8),
                SizedBox(
                  height: 56,
                  child: ElevatedButton(
                    onPressed: _aiLoading ? null : _onAiFill,
                    style: ElevatedButton.styleFrom(
                      backgroundColor: AppTokens.warning,
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(AppTokens.rMd),
                      ),
                    ),
                    child: _aiLoading
                        ? const SizedBox(
                            width: 18,
                            height: 18,
                            child: CircularProgressIndicator(
                              strokeWidth: 2,
                              color: Colors.white,
                            ),
                          )
                        : const Text('AI\n补全', textAlign: TextAlign.center,
                            style: TextStyle(fontSize: 12)),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 16),

            // 计量单位（Tag 列表点选）
            _sectionLabel('计量单位'),
            const SizedBox(height: 8),
            _buildChipSelector(
              items: _units,
              selectedId: _unitId,
              onSelected: (id) => setState(() { _unitId = id; _showCustomUnit = false; }),
              showCustom: _showCustomUnit,
              onCustomTap: () => setState(() { _unitId = null; _showCustomUnit = !_showCustomUnit; _customUnitCtrl.clear(); }),
              emptyText: '单位字典加载中…',
            ),
            if (_showCustomUnit)
              Padding(
                padding: const EdgeInsets.only(top: 8),
                child: TextField(
                  controller: _customUnitCtrl,
                  autofocus: true,
                  decoration: InputDecoration(
                    hintText: '输入自定义单位，如：扎、捆',
                    filled: true, fillColor: t.bg,
                    border: OutlineInputBorder(borderRadius: BorderRadius.circular(AppTokens.rSm)),
                    contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                    isDense: true,
                  ),
                ),
              ),
            const SizedBox(height: 16),

            // 采购分类（Tag 列表点选）
            _sectionLabel('采购分类'),
            const SizedBox(height: 8),
            _buildChipSelector(
              items: _purchases,
              selectedId: _purchaseCategoryId,
              onSelected: (id) => setState(() { _purchaseCategoryId = id; _showCustomCat = false; }),
              showCustom: _showCustomCat,
              onCustomTap: () => setState(() { _purchaseCategoryId = null; _showCustomCat = !_showCustomCat; _customCatCtrl.clear(); }),
              emptyText: '采购分类加载中…',
            ),
            if (_showCustomCat)
              Padding(
                padding: const EdgeInsets.only(top: 8),
                child: TextField(
                  controller: _customCatCtrl,
                  autofocus: true,
                  decoration: InputDecoration(
                    hintText: '输入自定义分类',
                    filled: true, fillColor: t.bg,
                    border: OutlineInputBorder(borderRadius: BorderRadius.circular(AppTokens.rSm)),
                    contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                    isDense: true,
                  ),
                ),
              ),
            const SizedBox(height: 24),

            // 营养指标
            _sectionLabel('营养（每 100g）'),
            const SizedBox(height: 8),
            if (_metrics.isEmpty)
              const Padding(
                padding: EdgeInsets.symmetric(vertical: 16),
                child: LoadingView(),
              )
            else
              _buildNutritionGrid(),

            const SizedBox(height: 32),

            // 保存
            SizedBox(
              height: 48,
              child: ElevatedButton(
                onPressed: _saving ? null : _onSave,
                child: _saving
                    ? const SizedBox(
                        width: 20,
                        height: 20,
                        child: CircularProgressIndicator(
                            strokeWidth: 2, color: Colors.white),
                      )
                    : const Text('保存', style: TextStyle(fontSize: 16)),
              ),
            ),
            const SizedBox(height: 32),
          ],
        ),
      ),
    );
  }

  Widget _sectionLabel(String text) {
    final t = AppTokens.of(context);
    return Row(
      children: [
        Container(
          width: 4, height: 18,
          decoration: BoxDecoration(
            color: t.primary, borderRadius: BorderRadius.circular(2),
          ),
        ),
        const SizedBox(width: 8),
        Text(text,
            style: TextStyle(
                fontSize: 14, fontWeight: FontWeight.bold, color: t.title)),
      ],
    );
  }

  /// Tag 点选器：单选，选中高亮。末尾带"+自定义"选项。
  Widget _buildChipSelector({
    required List<DictItem> items,
    required int? selectedId,
    required ValueChanged<int> onSelected,
    required bool showCustom,
    required VoidCallback onCustomTap,
    required String emptyText,
  }) {
    if (items.isEmpty) {
      final t = AppTokens.of(context);
      return Padding(
        padding: const EdgeInsets.symmetric(vertical: 8),
        child: Text(emptyText,
            style: TextStyle(color: t.caption, fontSize: 12)),
      );
    }
    return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      Wrap(spacing: 8, runSpacing: 8, children: [
        ...items.map((item) {
          final t = AppTokens.of(context);
          final selected = selectedId == item.id;
          return GestureDetector(
            onTap: () { onSelected(item.id); onCustomTap(); }, // 选已有项时关闭自定义
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
              decoration: BoxDecoration(
                color: selected ? t.primary.withAlpha(25) : t.bg,
                borderRadius: BorderRadius.circular(AppTokens.rPill),
                border: Border.all(
                    color: selected ? t.primary : t.border, width: 1.5),
              ),
              child: Text(item.name, style: TextStyle(fontSize: 12,
                  fontWeight: selected ? FontWeight.w600 : FontWeight.normal,
                  color: selected ? t.primary : t.title)),
            ),
          );
        }),
        // +自定义
        GestureDetector(
          onTap: onCustomTap,
          child: Builder(builder: (context) {
            final t = AppTokens.of(context);
            return Container(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
              decoration: BoxDecoration(
                color: showCustom ? t.primary.withAlpha(15) : t.bg,
                borderRadius: BorderRadius.circular(AppTokens.rPill),
                border: Border.all(
                    color: showCustom ? t.primary : t.border, width: 1.2),
              ),
              child: Text(showCustom ? '自定义' : '+ 自定义', style: TextStyle(fontSize: 12,
                  color: showCustom ? t.primary : t.caption)),
            );
          }),
        ),
      ]),
    ]);
  }

  /// 营养指标网格（2 列，每项英文名 → 中文映射）。
  Widget _buildNutritionGrid() {
    return LayoutBuilder(
      builder: (context, constraints) {
        final t = AppTokens.of(context);
        return Wrap(
          spacing: 12,
          runSpacing: 12,
          children: _metrics.map((m) {
            final ctrl = _nutritionMap[m.id]!;
            final label = AppConstants.metricNameCn(m.name);
            final unit = m.unit;
            final itemWidth = (constraints.maxWidth - 12) / 2;
            return SizedBox(
              width: itemWidth,
              child: TextField(
                controller: ctrl,
                keyboardType: TextInputType.number,
                style: const TextStyle(fontSize: 14),
                decoration: InputDecoration(
                  labelText: label,
                  hintText: unit.isNotEmpty ? '单位: $unit' : '',
                  filled: true,
                  fillColor: t.bg,
                  border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(AppTokens.rSm),
                    borderSide: BorderSide(color: t.border),
                  ),
                  contentPadding:
                      const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                  isDense: true,
                ),
              ),
            );
          }).toList(),
        );
      },
    );
  }
}
