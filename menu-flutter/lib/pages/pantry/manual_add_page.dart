import 'package:flutter/material.dart';

import '../../core/app_theme.dart';
import '../../services/ingredient_service.dart';
import '../../services/pantry_service.dart';
import '../../widgets/loading_empty.dart';

/// 手动添加页（别人送/赠品/旧库存补登，对齐 pantry-manual-add-preview.html）。
///
/// 两步：① 选食材（搜库里有 / 新建）→ ② 填数量+来源备注 → 入库。
/// 产生带「手动」来源标签的新记录，区别于详情页盘点（纠偏、不带标签）。
class PantryManualAddPage extends StatefulWidget {
  const PantryManualAddPage({super.key});

  @override
  State<PantryManualAddPage> createState() => _PantryManualAddPageState();
}

class _PantryManualAddPageState extends State<PantryManualAddPage> {
  /// 主题 token 缓存。
  AppTokens get _t => AppTokens.of(context);

  // 步骤：0=选食材，1=填数量+来源
  int _step = 0;

  // 选中的食材
  DictItem? _selected;
  String _newIngredientName = '';

  // 食材字典（搜索用）
  List<DictItem> _ingredients = [];
  bool _loadingDict = true;
  String _query = '';

  // 数量 + 来源
  double _amount = 1;
  String _sourceNote = '朋友送';
  String? _expireDate;

  bool _saving = false;

  static const _sourceOptions = ['朋友送', '赠品', '旧库存补登', '其他'];

  @override
  void initState() {
    super.initState();
    _loadDict();
  }

  Future<void> _loadDict() async {
    try {
      final list = await IngredientService.listAll();
      if (!mounted) return;
      setState(() {
        _ingredients = list;
        _loadingDict = false;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() => _loadingDict = false);
    }
  }

  List<DictItem> get _filtered {
    if (_query.isEmpty) return _ingredients;
    return _ingredients.where((i) => i.name.contains(_query)).toList();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text(_step == 0 ? '添加 · 选食材' : '添加 · 填数量')),
      body: _step == 0 ? _buildStep0() : _buildStep1(),
    );
  }

  // ===== 步骤1：选食材 =====
  Widget _buildStep0() {
    final t = AppTokens.of(context);
    if (_loadingDict) return const LoadingView();
    return Column(
      children: [
        // 搜索框
        Padding(
          padding: const EdgeInsets.all(16),
          child: TextField(
            decoration: InputDecoration(
              hintText: '搜食材名',
              prefixIcon: const Icon(Icons.search, size: 20),
              border: OutlineInputBorder(borderRadius: BorderRadius.circular(AppTokens.rMd)),
              contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
            ),
            onChanged: (v) => setState(() {
              _query = v;
              _newIngredientName = v;
            }),
          ),
        ),
        // 库里已有
        Expanded(
          child: ListView(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            children: [
              if (_filtered.isNotEmpty) ...[
                Text('库里已有', style: t.textStyles.sectionLabel.copyWith(letterSpacing: 1)),
                const SizedBox(height: 8),
                ..._filtered.map((i) => _ingredientTile(i)),
                const SizedBox(height: 16),
              ],
              // 新建档
              Text('库里没有？', style: t.textStyles.sectionLabel.copyWith(letterSpacing: 1)),
              const SizedBox(height: 8),
              _newIngredientTile(),
            ],
          ),
        ),
      ],
    );
  }

  Widget _ingredientTile(DictItem i) {
    final selected = _selected?.id == i.id;
    return Card(
      margin: const EdgeInsets.only(bottom: 4),
      elevation: 0,
      color: selected ? _t.highlight : AppTokens.of(context).card,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(AppTokens.rSm),
        side: BorderSide(color: selected ? _t.primary : AppTokens.of(context).border),
      ),
      child: ListTile(
        title: Text(i.name, style: _t.textStyles.cardTitle),
        trailing: selected
            ? Text('已选', style: _t.textStyles.chip.copyWith(color: _t.primary))
            : Text('选', style: _t.textStyles.chip.copyWith(color: Colors.grey)),
        onTap: () => setState(() {
          _selected = i;
          _newIngredientName = '';
          _step = 1;
        }),
        dense: true,
      ),
    );
  }

  Widget _newIngredientTile() {
    final t = AppTokens.of(context);
    final hasQuery = _query.isNotEmpty && _ingredients.every((i) => i.name != _query);
    return Opacity(
      opacity: hasQuery ? 1 : 0.5,
      child: DashedBorder(
        color: _t.primary,
        child: ListTile(
          title: Text('+ 新建食材并添加', style: _t.textStyles.sm.copyWith(color: _t.primary)),
          subtitle: Text(hasQuery ? '「$_query」建档同时入库' : '输入食材名后点这新建', style: t.textStyles.chip),
          onTap: hasQuery
              ? () => setState(() {
                    _selected = null; // 走新建路径
                    _step = 1;
                  })
              : null,
          dense: true,
        ),
      ),
    );
  }

  // ===== 步骤2：填数量 + 来源 =====
  Widget _buildStep1() {
    final t = AppTokens.of(context);
    final name = _selected?.name ?? _newIngredientName;
    return Column(
      children: [
        Expanded(
          child: ListView(
            padding: const EdgeInsets.all(16),
            children: [
              // 食材头
              Row(children: [
                Container(
                  width: 48, height: 48,
                  decoration: BoxDecoration(color: t.primarySoft, borderRadius: BorderRadius.circular(AppTokens.rMd)),
                  alignment: Alignment.center,
                  child: Text(name.characters.first, style: t.textStyles.h3.copyWith(color: _t.primaryDeep)),
                ),
                const SizedBox(width: 12),
                Expanded(child: Text(name, style: t.textStyles.subtitle)),
              ]),
              const SizedBox(height: 20),

              // 数量加减盘
              Text('这次进来多少？', style: t.textStyles.caption),
              const SizedBox(height: 10),
              Container(
                padding: const EdgeInsets.symmetric(vertical: 20),
                decoration: BoxDecoration(
                  color: t.bg, border: Border.all(color: t.border),
                  borderRadius: BorderRadius.circular(AppTokens.rLg),
                ),
                child: Row(mainAxisAlignment: MainAxisAlignment.center, children: [
                  _circleBtn(Icons.remove, () => setState(() => _amount = (_amount - 1).clamp(0, 999999))),
                  const SizedBox(width: 24),
                  Text(_fmt(_amount), style: t.textStyles.display.copyWith(height: 1)),
                  const SizedBox(width: 24),
                  _circleBtn(Icons.add, () => setState(() => _amount = (_amount + 1).clamp(0, 999999))),
                ]),
              ),
              const SizedBox(height: 20),

              // 来源备注标签
              Text('来源备注', style: t.textStyles.sectionLabel.copyWith(letterSpacing: 1)),
              const SizedBox(height: 8),
              Wrap(
                spacing: 7, runSpacing: 7,
                children: _sourceOptions.map((s) {
                  final selected = _sourceNote == s;
                  return GestureDetector(
                    onTap: () => setState(() => _sourceNote = s),
                    child: Container(
                      padding: const EdgeInsets.symmetric(horizontal: 13, vertical: 6),
                      decoration: BoxDecoration(
                        color: selected ? _t.primary : t.card,
                        border: Border.all(color: selected ? _t.primary : _t.border),
                        borderRadius: BorderRadius.circular(AppTokens.rSm),
                      ),
                      child: Text(s, style: t.textStyles.sectionLabel.copyWith(color: selected ? Colors.white : t.body)),
                    ),
                  );
                }).toList(),
              ),
              const SizedBox(height: 12),

              // 说明条
              Container(
                padding: const EdgeInsets.all(10),
                decoration: BoxDecoration(
                  color: _t.highlight, border: Border.all(color: _t.border),
                  borderRadius: BorderRadius.circular(AppTokens.rSm),
                ),
                child: Text(
                  '这笔记作 手动 +${_fmt(_amount)}，会带「手动」来源标签。区别于盘点：盘点只改已有项的差额、不带标签。',
                  style: t.textStyles.sectionLabel.copyWith(color: t.body, height: 1.5),
                ),
              ),
            ],
          ),
        ),
        // 底栏：入库 + 返回
        SafeArea(
          child: Padding(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 12),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                ElevatedButton(
                  onPressed: _saving ? null : _submit,
                  style: ElevatedButton.styleFrom(
                    backgroundColor: _t.primary,
                    foregroundColor: Colors.white,
                    padding: const EdgeInsets.symmetric(horizontal: 48, vertical: 12),
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AppTokens.rMd)),
                  ),
                  child: Text(_saving ? '入库中…' : '入库 · ${_fmt(_amount)}', style: const TextStyle(fontWeight: FontWeight.w800)),
                ),
                const SizedBox(width: 12),
                OutlinedButton(
                  onPressed: () => _step == 1 ? setState(() => _step = 0) : Navigator.of(context).pop(),
                  style: OutlinedButton.styleFrom(
                    padding: const EdgeInsets.symmetric(horizontal: 32, vertical: 12),
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AppTokens.rMd)),
                  ),
                  child: const Text('返回'),
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }

  Widget _circleBtn(IconData icon, VoidCallback onTap) {
    final t = AppTokens.of(context);
    return GestureDetector(
      onTap: onTap,
      child: Container(
        width: 40, height: 40,
        decoration: BoxDecoration(
          color: t.card, border: Border.all(color: t.border),
          shape: BoxShape.circle,
        ),
        child: Icon(icon, size: 20, color: _t.primary),
      ),
    );
  }

  Future<void> _submit() async {
    final name = _selected?.name ?? _newIngredientName;
    if (name.isEmpty && _selected == null) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('请先选食材')));
      return;
    }
    setState(() => _saving = true);
    try {
      await PantryService.manualAdd(
        ingredientId: _selected?.id,
        name: _selected == null ? name : null,
        amount: _amount,
        sourceNote: _sourceNote,
        expireDate: _expireDate,
      );
      if (!mounted) return;
      Navigator.of(context).pop();
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('入库失败：$e')));
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  static String _fmt(double v) {
    if (v == v.roundToDouble()) return v.toInt().toString();
    return v.toStringAsFixed(1);
  }
}

/// 虚线边框容器（新建食材入口用，对齐原型 dashed border）。
class DashedBorder extends StatelessWidget {
  final Widget child;
  final Color color;
  const DashedBorder({super.key, required this.child, required this.color});

  @override
  Widget build(BuildContext context) {
    return CustomPaint(
      painter: _DashedPainter(color),
      child: child,
    );
  }
}

class _DashedPainter extends CustomPainter {
  final Color color;
  _DashedPainter(this.color);

  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()
      ..color = color
      ..strokeWidth = 1.5
      ..style = PaintingStyle.stroke;
    const dashWidth = 5.0, dashSpace = 4.0;
    final rrect = RRect.fromRectAndRadius(Offset.zero & size, const Radius.circular(10));
    // 简化：画虚线圆角矩形路径
    final path = Path()..addRRect(rrect);
    final metrics = path.computeMetrics();
    for (final metric in metrics) {
      double dist = 0;
      while (dist < metric.length) {
        final next = (dist + dashWidth).clamp(0.0, metric.length);
        canvas.drawPath(metric.extractPath(dist, next), paint);
        dist = next + dashSpace;
      }
    }
  }

  @override
  bool shouldRepaint(covariant _DashedPainter oldDelegate) => oldDelegate.color != color;
}
