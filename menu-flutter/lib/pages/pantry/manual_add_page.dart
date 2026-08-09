import 'package:flutter/material.dart';

import '../../core/app_theme.dart';
import '../../services/ingredient_service.dart';
import '../../services/pantry_service.dart';
import '../../widgets/action_bar.dart';
import '../../widgets/initial_avatar.dart';
import '../../widgets/loading_empty.dart';

/// 入库页（V42 档位版，对齐 pantry-manual-add.html 两屏）。
///
/// ① 选食材（搜库里已有 / 新建档，填名字就行不用单位）→
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
  // 新建档：搜索框输入的名字，勾选「新建食材并入库」后生效
  bool _newMode = false;
  String _newName = '';

  // 食材列表 + 搜索 + 家里档位 map
  List<IngredientItem> _ingredients = [];
  Map<int, String> _levelByIng = {}; // ingredientId → 档位（家里状态展示）
  bool _loading = true;
  String _query = '';

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
    return Column(
      children: [
        Expanded(
          child: ListView(
            padding: const EdgeInsets.fromLTRB(AppTokens.sp16, AppTokens.sp12, AppTokens.sp16, AppTokens.sp8),
            children: [
              Text('入库', style: t.textStyles.h3),
              const SizedBox(height: 4),
              Text('朋友送 / 赠品 / 之前忘记登的旧库存，记一笔进来',
                  style: t.textStyles.sm.copyWith(color: t.caption)),
              const SizedBox(height: 16),

              // 搜索
              TextField(
                onChanged: (v) => setState(() => _query = v),
                decoration: InputDecoration(
                  hintText: '搜食材名',
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
                  child: Text('搜不到，试试下面「新建食材并入库」',
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
          color: t.primary,
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: AppTokens.sp12, vertical: 11),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('+ 新建食材并入库',
                    style: t.textStyles.sm.copyWith(color: t.primary, fontWeight: FontWeight.w800)),
                const SizedBox(height: 2),
                Text(
                  enabled ? '「$q」建档同时入库' : '填个名字就行，不用填单位',
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
              Text('这次进来，家里算哪档？', style: t.textStyles.caption),
              const SizedBox(height: 10),
              Row(children: [
                _levelCard(StockLevel.enough, '充足', '默认'),
                const SizedBox(width: 7),
                _levelCard(StockLevel.low, '不足', '只买了一点'),
              ]),
              const SizedBox(height: 14),

              // 来源备注
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

              // 说明条
              Container(
                padding: const EdgeInsets.all(10),
                decoration: BoxDecoration(
                  color: t.highlight,
                  border: Border.all(color: t.border),
                  borderRadius: BorderRadius.circular(AppTokens.rSm),
                ),
                child: Text(
                  '这笔记作 手动 · $_sourceNote，$_name 变为「${StockLevel.label(_level)}」。不用填买了多少、不用填单位——库存是档位，不是账本。',
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
