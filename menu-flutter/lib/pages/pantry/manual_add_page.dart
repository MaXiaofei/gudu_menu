import 'package:flutter/material.dart';

import '../../core/app_theme.dart';
import '../../services/ingredient_service.dart';
import '../../services/pantry_service.dart';
import '../../widgets/action_bar.dart';
import '../../widgets/initial_avatar.dart';
import '../../widgets/loading_empty.dart';
import '../../widgets/select_chip.dart';
import '../../widgets/search_box.dart';

/// 入库页（V42 档位版，对齐 44829 批次 pantry-manual-add-v2 定稿）。
///
/// ① 选食材（先输入名称：搜库里已有 / 无则新建档）→
/// ② 定档位（充足默认 / 不足）+ 来源备注 → 入库。
/// 不再填数量/单位/保质期——库存是模糊档位，不是账本。
class PantryManualAddPage extends StatefulWidget {
  const PantryManualAddPage({super.key});

  @override
  State<PantryManualAddPage> createState() => _PantryManualAddPageState();
}

class _PantryManualAddPageState extends State<PantryManualAddPage> {
  /// 主题 token 缓存。
  AppTokens get _t => AppTokens.of(context);

  // 步骤：0=选食材，1=定档位+来源
  int _step = 0;

  // 选中的食材（库里已有）
  IngredientItem? _selected;
  // 新建档：搜索框输入的名字，点「新建食材并入库」后生效
  bool _newMode = false;
  String _newName = '';

  // 食材列表 + 搜索 + 家里档位 map
  List<IngredientItem> _ingredients = [];
  Map<int, String> _levelByIng = {}; // ingredientId → 档位（家里状态展示）
  bool _loading = true;
  String _query = '';
  final _searchCtrl = TextEditingController();
  final _searchFocus = FocusNode();

  // 档位 + 来源
  String _level = StockLevel.enough; // 入库默认充足
  String _sourceNote = '朋友送';
  bool _saving = false;

  static const _sourceOptions = ['朋友送', '赠品', '旧库存补登', '其他'];

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _searchCtrl.dispose();
    _searchFocus.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    try {
      final results = await Future.wait([
        IngredientService.listAll(),
        PantryService.listGrouped(),
      ]);
      if (!mounted) return;
      setState(() {
        _ingredients = results[0] as List<IngredientItem>;
        final grouped = results[1] as PantryGrouped;
        _levelByIng = {
          for (final it in grouped.items) it.ingredientId: it.level,
        };
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

  bool get _canNext => _selected != null || _newMode;

  @override
  Widget build(BuildContext context) {
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
    final q = _query.trim();
    final matches = q.isEmpty ? const <IngredientItem>[] : _filtered;
    final exactMatch = q.isNotEmpty && _ingredients.any((i) => i.name == q);
    return Column(
      children: [
        Expanded(
          child: ListView(
            padding: const EdgeInsets.fromLTRB(AppTokens.sp16, AppTokens.sp12, AppTokens.sp16, AppTokens.sp8),
            children: [
              // 头说明（录入页只有返回箭头、无标题，DESIGN.md §13.1；这行是功能说明文案）
              Text('朋友送 / 赠品 / 之前忘记登的旧库存，记一笔进来',
                  style: t.textStyles.sm.copyWith(color: t.caption)),
              const SizedBox(height: 12),

              // 搜索（⌕ + 输入 + ✕ 清除，输入即筛）
              _buildSearchBox(t),
              const SizedBox(height: 16),

              if (q.isEmpty) ...[
                // 未输入：轻提示卡 + 新建入口（一直画着，点它聚焦搜索框）
                _emptyHint(t),
                const SizedBox(height: 20),
                Text('库存里没有？', style: t.textStyles.sectionLabel.copyWith(letterSpacing: 1)),
                const SizedBox(height: 8),
                _newIngredientTile(),
              ] else ...[
                if (matches.isNotEmpty) ...[
                  // 库里已有（输入后才出现）
                  Text('库里已有', style: t.textStyles.sectionLabel.copyWith(letterSpacing: 1)),
                  const SizedBox(height: 8),
                  Container(
                    decoration: BoxDecoration(
                      color: t.card,
                      border: Border.all(color: t.border),
                      borderRadius: BorderRadius.circular(AppTokens.rSm),
                    ),
                    child: Column(
                      children: [
                        for (int i = 0; i < matches.length; i++) ...[
                          if (i > 0) Divider(height: 1, thickness: 1, color: t.border),
                          _ingredientTile(matches[i]),
                        ],
                      ],
                    ),
                  ),
                  const SizedBox(height: 20),
                ],
                // 精确同名时隐藏新建区（上面列表已给答案）
                if (!exactMatch) ...[
                  Text('库存里没有？', style: t.textStyles.sectionLabel.copyWith(letterSpacing: 1)),
                  const SizedBox(height: 8),
                  _newIngredientTile(),
                ],
              ],
            ],
          ),
        ),
        _bottomBar0(),
      ],
    );
  }

  /// 搜索框：⌕ 图标 + 输入 + ✕ 清除（对齐 pantry-page.html 定稿形态）。
  Widget _buildSearchBox(AppTokens t) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(14, AppTokens.sp10, 14, 0),
      child: SearchBox(
        controller: _searchCtrl,
        focusNode: _searchFocus,
        hint: '搜库存',
        onChanged: (v) => setState(() => _query = v),
      ),
    );
  }

  /// 未输入提示卡：聚焦「先想名字」。
  Widget _emptyHint(AppTokens t) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 22),
      decoration: BoxDecoration(
        color: t.card,
        border: Border.all(color: t.border),
        borderRadius: BorderRadius.circular(AppTokens.rSm),
      ),
      child: Column(
        children: [
          Container(
            width: 36,
            height: 36,
            decoration: BoxDecoration(shape: BoxShape.circle, color: t.secondary),
            child: Icon(Icons.search, size: 16, color: t.primaryDeep),
          ),
          const SizedBox(height: 8),
          Text('输入名称，会显示库里已有的食材',
              style: t.textStyles.sm.copyWith(color: t.caption)),
        ],
      ),
    );
  }

  /// 库里已有行：食材名 + 家里档位 + 选/已选（点行选中，已选高亮）。
  Widget _ingredientTile(IngredientItem i) {
    final t = _t;
    final selected = _selected?.id == i.id;
    final levelLabel = StockLevel.label(_levelByIng[i.id]);
    final homeText = levelLabel.isEmpty ? '' : '家里：$levelLabel';
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
                  if (homeText.isNotEmpty)
                    Padding(
                      padding: const EdgeInsets.only(top: 2),
                      child: Text(homeText, style: t.textStyles.tiny.copyWith(color: t.caption)),
                    ),
                ],
              ),
            ),
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

  /// 新建档：虚线框入口。未输入 = 常驻入口，点它聚焦搜索框；
  /// 输入后（无精确同名时才渲染）= 启用，「$q」建档同时入库。
  Widget _newIngredientTile() {
    final t = _t;
    final q = _query.trim();
    final enabled = q.isNotEmpty;
    return GestureDetector(
      onTap: enabled
          ? () => setState(() {
                _newMode = true;
                _selected = null;
                _newName = q;
              })
          : () => _searchFocus.requestFocus(),
      child: DashedBorder(
        color: t.primary,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: AppTokens.sp12, vertical: 11),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('+ 新建食材并入库',
                  style: t.textStyles.sm.copyWith(color: t.primary, fontWeight: FontWeight.w800)),
              if (enabled) ...[
                const SizedBox(height: 2),
                Text('「$q」建档同时入库',
                    style: t.textStyles.tiny.copyWith(color: t.caption)),
              ],
            ],
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
              // 显式有限 minimumSize：全局主题是 Size(inf,48)（全宽 CTA），
              // 本按钮在 Center（宽松约束）里会触发 "infinite width" 布局崩溃
              minimumSize: const Size(200, 48),
              padding: const EdgeInsets.symmetric(horizontal: 56, vertical: 12),
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AppTokens.rMd)),
            ),
            child: const Text('下一步', style: TextStyle(fontWeight: FontWeight.w800)),
          ),
        ),
      ),
    );
  }

  // ===== ② 定档位 + 来源 =====

  Widget _buildStep1() {
    final t = _t;
    return Column(
      children: [
        Expanded(
          child: ListView(
            padding: const EdgeInsets.fromLTRB(AppTokens.sp16, AppTokens.sp12, AppTokens.sp16, AppTokens.sp8),
            children: [
              // 食材头（52px 缩略图 + 名称 + 家里状态）
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

              // 档位选择（入库：充足默认 / 不足）
              Text('补充后，家里有多少？', style: t.textStyles.caption),
              const SizedBox(height: 10),
              Row(children: [
                _levelCard(StockLevel.enough, '充足', '默认'),
                const SizedBox(width: 7),
                _levelCard(StockLevel.low, '不足', '一点点'),
              ]),
              const SizedBox(height: 14),

              // 来源备注
              Text('来源备注', style: t.textStyles.sectionLabel.copyWith(letterSpacing: 1)),
              const SizedBox(height: 8),
              Wrap(
                spacing: 7,
                runSpacing: 7,
                children: _sourceOptions.map((s) {
                  final selected = _sourceNote == s;
                  return SelectChip(
                    label: s,
                    selected: selected,
                    onTap: () => setState(() => _sourceNote = s),
                  );
                }).toList(),
              ),
            ],
          ),
        ),
        _bottomBar1(),
      ],
    );
  }

  /// 食材头副文案：家里当前状态（新建档：新食材，家里还没有）。
  String _stockSub() {
    final levelLabel = _selected == null ? '' : StockLevel.label(_levelByIng[_selected!.id]);
    if (_selected == null) return '新建档 · 家里还没有';
    return '家里：${levelLabel.isEmpty ? '还没有' : levelLabel} · 入库记一笔';
  }

  /// 档位卡片（2 选 1，选中主色描边）。
  Widget _levelCard(String level, String title, String sub) {
    final t = _t;
    final selected = _level == level;
    return Expanded(
      child: GestureDetector(
        onTap: () => setState(() => _level = level),
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 11),
          decoration: BoxDecoration(
            color: t.bg,
            border: Border.all(color: selected ? t.primary : t.border, width: selected ? 2 : 1),
            borderRadius: BorderRadius.circular(AppTokens.rMd),
          ),
          child: Column(children: [
            Text(title,
                style: t.textStyles.md.copyWith(
                  color: t.title,
                  fontWeight: selected ? FontWeight.w800 : FontWeight.w700,
                )),
            const SizedBox(height: 2),
            Text(sub, style: t.textStyles.tiny.copyWith(color: t.caption)),
          ]),
        ),
      ),
    );
  }

  /// ② 底栏：入库。
  Widget _bottomBar1() {
    final t = _t;
    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 8, 16, 12),
        child: Center(
          child: ElevatedButton(
            onPressed: _saving ? null : _submit,
            style: ElevatedButton.styleFrom(
              backgroundColor: t.primary,
              foregroundColor: Colors.white,
              // 显式有限 minimumSize：全局主题是 Size(inf,48)（全宽 CTA），
              // 本按钮在 Center（宽松约束）里会触发 "infinite width" 布局崩溃
              minimumSize: const Size(220, 48),
              padding: const EdgeInsets.symmetric(horizontal: 48, vertical: 12),
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AppTokens.rMd)),
            ),
            child: Text(_saving ? '入库中…' : '入库',
                style: const TextStyle(fontWeight: FontWeight.w800)),
          ),
        ),
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
      await PantryService.manualAdd(
        ingredientId: _selected?.id,
        name: _selected == null ? name : null,
        level: _level,
        sourceNote: _sourceNote,
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
