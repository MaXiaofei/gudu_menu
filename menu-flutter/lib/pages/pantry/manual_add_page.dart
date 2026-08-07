import 'package:flutter/material.dart';

import '../../core/app_theme.dart';
import '../../services/ingredient_service.dart';
import '../../services/pantry_service.dart';
import '../../widgets/action_bar.dart';
import '../../widgets/initial_avatar.dart';
import '../../widgets/loading_empty.dart';

/// 手动添加页（别人送/赠品/旧库存补登，对齐 pantry-manual-add.html 两屏）。
///
/// ① 选食材（搜库里有 / 新建档）→ ② 填数量 + 批次属性 + 来源备注 → 入库。
/// 产生带「手动」来源标签的新记录，区别于详情页盘点（纠偏、不带标签）。
/// V41：批次属性「存放」存 pantry.storage，「保质期」按天数折成过期日 expireDate。
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

  // 选中的食材（库里已有）
  IngredientItem? _selected;
  // 新建档：搜索框输入的名字，勾选「新建食材并添加」后生效
  bool _newMode = false;
  String _newName = '';

  // 食材列表 + 搜索
  List<IngredientItem> _ingredients = [];
  bool _loading = true;
  String _query = '';

  // 数量 + 批次属性 + 来源
  double _amount = 1;
  String _sourceNote = '朋友送';
  String? _storage; // 常温/冷藏/冷冻
  int? _shelfDays; // 保质期天数 → expireDate = 今天 + N
  bool _saving = false;

  static const _sourceOptions = ['朋友送', '赠品', '旧库存补登', '其他'];
  static const _storageOptions = ['常温', '冷藏', '冷冻'];
  static const _shelfOptions = [3, 7, 15, 30, 60, 90];

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final list = await IngredientService.listAll();
      if (!mounted) return;
      setState(() {
        _ingredients = list;
        _loading = false;
      });
    } catch (_) {
      // 静默，request 已 toast
      if (!mounted) return;
      setState(() => _loading = false);
    }
  }

  List<IngredientItem> get _filtered {
    final q = _query.trim();
    if (q.isEmpty) return _ingredients;
    return _ingredients.where((i) => i.name.contains(q)).toList();
  }

  /// 已选食材名（库里已有 / 新建档）。
  String get _name => _selected?.name ?? _newName;

  /// 当前余量（库里已有有值，新建档 0）。
  double get _stock => _selected?.stockAmount ?? 0;

  String? get _unitName => _selected?.unitName;

  bool get _canNext => _selected != null || _newMode;

  @override
  Widget build(BuildContext context) {
    // 顶部 ‹：① 返回退出；② 返回选食材
    return PopScope(
      canPop: _step == 0,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop && _step == 1) setState(() => _step = 0);
      },
      child: Scaffold(
        backgroundColor: _t.bg,
        body: SafeArea(
          bottom: false,
          child: Column(
            children: [
              // DESIGN.md §13.1：录入页 BackHeader 只渲染返回箭头行
              const BackHeader(),
              Expanded(
                child: _loading && _step == 0
                    ? const LoadingView()
                    : (_step == 0 ? _buildStep0() : _buildStep1()),
              ),
            ],
          ),
        ),
      ),
    );
  }

  // ===== ① 选食材 =====

  Widget _buildStep0() {
    final t = _t;
    return Column(
      children: [
        Expanded(
          child: ListView(
            padding: const EdgeInsets.fromLTRB(AppTokens.sp16, AppTokens.sp12, AppTokens.sp16, AppTokens.sp8),
            children: [
              // 头说明（对齐原型：标题 + 一句场景说明）
              Text('选食材', style: t.textStyles.h3),
              const SizedBox(height: 4),
              Text('朋友送 / 赠品 / 之前忘记登的旧库存，记一笔进来',
                  style: t.textStyles.sm.copyWith(color: t.caption)),
              const SizedBox(height: 16),

              // 搜索
              TextField(
                onChanged: (v) => setState(() => _query = v),
                decoration: InputDecoration(
                  hintText: '搜食材名 / 扫码',
                  hintStyle: t.textStyles.sm.copyWith(color: t.caption),
                  filled: true,
                  fillColor: t.card,
                  contentPadding:
                      const EdgeInsets.symmetric(horizontal: AppTokens.sp12, vertical: 12),
                  enabledBorder: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(AppTokens.rMd),
                    borderSide: BorderSide(color: t.border),
                  ),
                  focusedBorder: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(AppTokens.rMd),
                    borderSide: BorderSide(color: t.primary),
                  ),
                ),
              ),
              const SizedBox(height: 20),

              // 库里已有
              Text('库里已有', style: t.textStyles.sectionLabel.copyWith(letterSpacing: 1)),
              const SizedBox(height: 8),
              if (_filtered.isEmpty)
                Container(
                  width: double.infinity,
                  padding: const EdgeInsets.symmetric(vertical: 18),
                  decoration: BoxDecoration(
                    color: t.card,
                    border: Border.all(color: t.border),
                    borderRadius: BorderRadius.circular(AppTokens.rSm),
                  ),
                  child: Text('搜不到，试试下面「新建食材并添加」',
                      textAlign: TextAlign.center,
                      style: t.textStyles.sm.copyWith(color: t.caption)),
                )
              else
                Container(
                  decoration: BoxDecoration(
                    color: t.card,
                    border: Border.all(color: t.border),
                    borderRadius: BorderRadius.circular(AppTokens.rSm),
                  ),
                  child: Column(
                    children: [
                      for (int i = 0; i < _filtered.length; i++) ...[
                        if (i > 0) Divider(height: 1, thickness: 1, color: t.border),
                        _ingredientTile(_filtered[i]),
                      ],
                    ],
                  ),
                ),
              const SizedBox(height: 20),

              // 库里没有？→ 新建档
              Text('库里没有？', style: t.textStyles.sectionLabel.copyWith(letterSpacing: 1)),
              const SizedBox(height: 8),
              _newIngredientTile(),
            ],
          ),
        ),
        _bottomBar0(),
      ],
    );
  }

  /// 库里已有行：食材名 + 现有 X 个 · 单位 + 选/已选（点行选中，已选高亮）。
  Widget _ingredientTile(IngredientItem i) {
    final t = _t;
    final selected = _selected?.id == i.id;
    final unit = (i.unitName == null || i.unitName!.isEmpty) ? '' : ' ${i.unitName}';
    final stockTxt = '现有 ${_fmt(i.stockAmount)}$unit';
    return InkWell(
      onTap: () => setState(() {
        _selected = i;
        _newMode = false;
      }),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: AppTokens.sp12, vertical: 9),
        child: Row(
          children: [
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(i.name, style: t.textStyles.cardTitle),
                  const SizedBox(height: 2),
                  Text(
                    unit.isEmpty ? stockTxt : '$stockTxt · ${i.unitName}',
                    style: t.textStyles.tiny.copyWith(color: t.caption),
                  ),
                ],
              ),
            ),
            // 选/已选（原型：已选 = 橙底白字胶囊）
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 11, vertical: 4),
              decoration: BoxDecoration(
                color: selected ? t.primary : null,
                borderRadius: BorderRadius.circular(AppTokens.rSm),
              ),
              child: Text(
                selected ? '已选' : '选',
                style: t.textStyles.chip.copyWith(
                  color: selected ? Colors.white : t.caption,
                  fontWeight: FontWeight.w800,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  /// 新建档：虚线框入口（输入食材名后可用，点选后走新建路径）。
  Widget _newIngredientTile() {
    final t = _t;
    final q = _query.trim();
    final enabled = q.isNotEmpty && _ingredients.every((i) => i.name != q);
    return Opacity(
      opacity: enabled ? 1 : 0.5,
      child: GestureDetector(
        onTap: enabled
            ? () => setState(() {
                  _newMode = true;
                  _selected = null;
                  _newName = q;
                })
            : null,
        child: DashedBorder(
          color: _newMode ? t.primary : t.primary,
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: AppTokens.sp12, vertical: 11),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('+ 新建食材并添加',
                    style: t.textStyles.sm.copyWith(color: t.primary, fontWeight: FontWeight.w800)),
                const SizedBox(height: 2),
                Text(
                  enabled ? '「$q」建档同时入库' : '填名字 + 单位，建档同时入库',
                  style: t.textStyles.tiny.copyWith(color: t.caption),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  /// ① 底栏：下一步（未选禁用）。
  Widget _bottomBar0() {
    final t = _t;
    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 8, 16, 12),
        child: Center(
          child: ElevatedButton(
            onPressed: _canNext ? () => setState(() => _step = 1) : null,
            style: ElevatedButton.styleFrom(
              backgroundColor: t.primary,
              foregroundColor: Colors.white,
              disabledBackgroundColor: t.border,
              disabledForegroundColor: Colors.white,
              padding: const EdgeInsets.symmetric(horizontal: 56, vertical: 12),
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AppTokens.rMd)),
            ),
            child: const Text('下一步', style: TextStyle(fontWeight: FontWeight.w800)),
          ),
        ),
      ),
    );
  }

  // ===== ② 填数量 + 批次属性 + 来源 =====

  Widget _buildStep1() {
    final t = _t;
    return Column(
      children: [
        Expanded(
          child: ListView(
            padding: const EdgeInsets.fromLTRB(AppTokens.sp16, AppTokens.sp12, AppTokens.sp16, AppTokens.sp8),
            children: [
              // 食材头（对齐详情页：52px 缩略图 + 名称 + 现有量）
              Row(
                children: [
                  InitialAvatar(name: _name.isEmpty ? '食' : _name, size: 52),
                  const SizedBox(width: AppTokens.sp12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(_name, style: t.textStyles.h3),
                        const SizedBox(height: 2),
                        Text(_stockSub(), style: t.textStyles.sm.copyWith(color: t.caption)),
                      ],
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 20),

              // 加减盘
              Text('这次进来多少？', style: t.textStyles.caption),
              const SizedBox(height: 10),
              Container(
                padding: const EdgeInsets.symmetric(vertical: 22),
                decoration: BoxDecoration(
                  color: t.card,
                  border: Border.all(color: t.border),
                  borderRadius: BorderRadius.circular(AppTokens.rLg),
                ),
                child: Row(mainAxisAlignment: MainAxisAlignment.center, children: [
                  _circleBtn(Icons.remove, () => setState(() => _amount = (_amount - 1).clamp(0, 999999))),
                  const SizedBox(width: 26),
                  Column(children: [
                    Text(_fmt(_amount), style: t.textStyles.display.copyWith(height: 1)),
                    const SizedBox(height: 4),
                    Text(_unitName ?? '', style: t.textStyles.tiny.copyWith(color: t.caption)),
                  ]),
                  const SizedBox(width: 26),
                  _circleBtn(Icons.add, () => setState(() => _amount = (_amount + 1).clamp(0, 999999))),
                ]),
              ),
              const SizedBox(height: 14),

              // 批次属性：日期（今天）/ 存放 / 保质期
              Row(
                children: [
                  _attrCard('日期', '今天 ${_fmtMonthDay(DateTime.now())}', onTap: null),
                  const SizedBox(width: 7),
                  _attrCard('存放', _storage ?? '未设', onTap: _pickStorage),
                  const SizedBox(width: 7),
                  _attrCard('保质期', _shelfDays != null ? '$_shelfDays 天' : '未设', onTap: _pickShelf),
                ],
              ),
              const SizedBox(height: 14),

              // 来源备注：手动标签下挂什么说明（会显示在这条记录上）
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text('来源备注', style: t.textStyles.sectionLabel.copyWith(letterSpacing: 1)),
                  Text('会显示在这条记录上',
                      style: t.textStyles.tiny.copyWith(color: _t.primaryDeep)),
                ],
              ),
              const SizedBox(height: 8),
              Wrap(
                spacing: 7,
                runSpacing: 7,
                children: _sourceOptions.map((s) {
                  final selected = _sourceNote == s;
                  return GestureDetector(
                    onTap: () => setState(() => _sourceNote = s),
                    child: Container(
                      padding: const EdgeInsets.symmetric(horizontal: 13, vertical: 6),
                      decoration: BoxDecoration(
                        color: selected ? t.primary : t.card,
                        border: Border.all(color: selected ? t.primary : t.border),
                        borderRadius: BorderRadius.circular(AppTokens.rSm),
                      ),
                      child: Text(s,
                          style: t.textStyles.sectionLabel.copyWith(
                            color: selected ? Colors.white : t.body,
                            fontWeight: FontWeight.w800,
                          )),
                    ),
                  );
                }).toList(),
              ),
              const SizedBox(height: 12),

              // 说明条：和盘点的边界
              Container(
                padding: const EdgeInsets.all(10),
                decoration: BoxDecoration(
                  color: t.highlight,
                  border: Border.all(color: t.border),
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
        _bottomBar1(),
      ],
    );
  }

  /// 食材头副文案：现有 X 个 · 手动新增一笔（新建档：现有 0 · 手动新增一笔）。
  String _stockSub() {
    final unit = (_unitName == null || _unitName!.isEmpty) ? '' : ' $_unitName';
    return '现有 ${_fmt(_stock)}$unit · 手动新增一笔';
  }

  /// 批次属性卡：小标签 + 值（可点选；日期卡为静态「今天」）。
  Widget _attrCard(String label, String value, {VoidCallback? onTap}) {
    final t = _t;
    final card = Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 9),
      decoration: BoxDecoration(
        color: t.card,
        border: Border.all(color: t.border),
        borderRadius: BorderRadius.circular(AppTokens.rSm),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(label, style: t.textStyles.tiny.copyWith(color: t.caption)),
          const SizedBox(height: 3),
          Text(value,
              style: t.textStyles.sectionLabel.copyWith(
                color: value == '未设' ? t.caption : t.title,
                fontWeight: FontWeight.w700,
              )),
        ],
      ),
    );
    if (onTap == null) return card;
    return Expanded(child: GestureDetector(onTap: onTap, child: card));
  }

  /// 存放选择：常温/冷藏/冷冻（弹层单选，存 pantry.storage）。
  Future<void> _pickStorage() async {
    final v = await showModalBottomSheet<String>(
      context: context,
      backgroundColor: _t.card,
      builder: (ctx) => SafeArea(
        child: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              for (final s in _storageOptions)
                ListTile(
                  title: Text(s, textAlign: TextAlign.center),
                  onTap: () => Navigator.pop(ctx, s),
                ),
              ListTile(
                title: Text('不设', textAlign: TextAlign.center,
                    style: _t.textStyles.sectionLabel.copyWith(color: _t.caption)),
                onTap: () => Navigator.pop(ctx, ''),
              ),
            ],
          ),
        ),
      ),
    );
    if (v == null) return;
    setState(() => _storage = v.isEmpty ? null : v);
  }

  /// 保质期选择：3/7/15/30/60/90 天（折成过期日 expireDate 入库）。
  Future<void> _pickShelf() async {
    final v = await showModalBottomSheet<int>(
      context: context,
      backgroundColor: _t.card,
      builder: (ctx) => SafeArea(
        child: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              for (final d in _shelfOptions)
                ListTile(
                  title: Text('$d 天', textAlign: TextAlign.center),
                  onTap: () => Navigator.pop(ctx, d),
                ),
              ListTile(
                title: Text('不设', textAlign: TextAlign.center,
                    style: _t.textStyles.sectionLabel.copyWith(color: _t.caption)),
                onTap: () => Navigator.pop(ctx, 0),
              ),
            ],
          ),
        ),
      ),
    );
    if (v == null) return;
    setState(() => _shelfDays = v == 0 ? null : v);
  }

  /// ② 底栏：入库 · N 个（带单位）。
  Widget _bottomBar1() {
    final t = _t;
    final unit = (_unitName == null || _unitName!.isEmpty) ? '' : ' $_unitName';
    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 8, 16, 12),
        child: Center(
          child: ElevatedButton(
            onPressed: _saving ? null : _submit,
            style: ElevatedButton.styleFrom(
              backgroundColor: t.primary,
              foregroundColor: Colors.white,
              padding: const EdgeInsets.symmetric(horizontal: 48, vertical: 12),
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AppTokens.rMd)),
            ),
            child: Text(_saving ? '入库中…' : '入库 · ${_fmt(_amount)}$unit',
                style: const TextStyle(fontWeight: FontWeight.w800)),
          ),
        ),
      ),
    );
  }

  Widget _circleBtn(IconData icon, VoidCallback onTap) {
    final t = AppTokens.of(context);
    return GestureDetector(
      onTap: onTap,
      child: Container(
        width: 40,
        height: 40,
        decoration: BoxDecoration(
          color: t.card,
          border: Border.all(color: t.border),
          shape: BoxShape.circle,
        ),
        child: Icon(icon, size: 20, color: t.primary),
      ),
    );
  }

  Future<void> _submit() async {
    final name = _name;
    if (name.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('请先选食材')));
      return;
    }
    setState(() => _saving = true);
    try {
      // 保质期 → 过期日：今天 + N 天（yyyy-MM-dd）
      final expire = _shelfDays == null
          ? null
          : _dateStr(DateTime.now().add(Duration(days: _shelfDays!)));
      await PantryService.manualAdd(
        ingredientId: _selected?.id,
        name: _selected == null ? name : null,
        amount: _amount,
        sourceNote: _sourceNote,
        expireDate: expire,
        storage: _storage,
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

  static String _fmtMonthDay(DateTime d) => '${d.month}/${d.day}';

  static String _dateStr(DateTime d) =>
      '${d.year}-${d.month.toString().padLeft(2, '0')}-${d.day.toString().padLeft(2, '0')}';
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
