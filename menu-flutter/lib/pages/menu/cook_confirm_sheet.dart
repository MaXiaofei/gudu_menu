import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../core/app_theme.dart';
import '../../services/dish_service.dart';
import '../../services/pantry_service.dart';

/// 确认弹窗的结果（提交用材）。
class CookConfirmResult {
  /// 用完了的食材 id。
  final List<int> usedUp;
  /// 用了一些的食材 id。
  final List<int> partiallyUsed;
  /// 跳过：本次不更新库存（只写食记 + 食集完成）。
  final bool skipped;

  const CookConfirmResult({
    required this.usedUp,
    required this.partiallyUsed,
    this.skipped = false,
  });
}

/// 做菜确认弹窗（V42，对齐 cooking-deduct-modal ①「这顿饭用了什么」）。
///
/// 每项三态：用完了（→NONE）/ 用了一些（→降一档）/ 这次没用（不动）。
/// 默认值：食材=用完了，调料（isCondiment）=用了一些，用户可自由切换。
/// 底部：「跳过，不更新库存」（继续完成，不改库存）/「确认已做完」。
class CookConfirmSheet extends StatefulWidget {
  final CookMaterials materials;
  const CookConfirmSheet({super.key, required this.materials});

  @override
  State<CookConfirmSheet> createState() => _CookConfirmSheetState();
}

/// 单项选择状态：0=用完了，1=用了一些，2=这次没用。
class _CookConfirmSheetState extends State<CookConfirmSheet> {
  late final Map<int, int> _selection; // ingredientId → 0/1/2

  @override
  void initState() {
    super.initState();
    _selection = {
      for (final it in widget.materials.items)
        it.ingredientId: it.isCondiment ? 1 : 0,
    };
  }

  static const _labels = ['用完了', '用了一些', '这次没用'];
  static const _colors = [AppTokens.error, AppTokens.warning, null];

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    final items = widget.materials.items;
    final usedUpCount = _selection.values.where((v) => v == 0).length;
    return DraggableScrollableSheet(
      initialChildSize: 0.85,
      maxChildSize: 0.95,
      builder: (context, scrollController) => Container(
        decoration: const BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
        ),
        child: Column(
          children: [
            const SizedBox(height: 10),
            Container(width: 36, height: 4, decoration: BoxDecoration(
              color: t.border, borderRadius: BorderRadius.circular(2))),
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 14, 16, 4),
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Text('这顿饭用了什么', style: t.textStyles.pageTitle),
                const SizedBox(height: 4),
                Text(_subtitle(items), style: t.textStyles.sm.copyWith(color: t.caption)),
              ]),
            ),
            Expanded(
              child: ListView(
                controller: scrollController,
                padding: const EdgeInsets.fromLTRB(16, 8, 16, 8),
                children: [
                  Text('每项选一个状态，库存会自动更新',
                      style: t.textStyles.sectionLabel.copyWith(letterSpacing: 1)),
                  const SizedBox(height: 4),
                  for (final it in items) _itemRow(t, it),
                  // 说明
                  Container(
                    margin: const EdgeInsets.only(top: 12),
                    padding: const EdgeInsets.all(10),
                    decoration: BoxDecoration(
                      color: t.highlight,
                      border: Border.all(color: t.border),
                      borderRadius: BorderRadius.circular(AppTokens.rSm),
                    ),
                    child: Text(
                      '食材默认「用完了」，调料默认「用了一些」（降一档）。点一下就能改。没有库存的食材也不拦着你做饭。',
                      style: t.textStyles.sectionLabel.copyWith(color: t.body, height: 1.5),
                    ),
                  ),
                ],
              ),
            ),
            // 底部按钮
            Container(
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 12),
              decoration: BoxDecoration(
                color: t.bg,
                border: Border(top: BorderSide(color: t.border)),
              ),
              child: Row(children: [
                Expanded(
                  child: OutlinedButton(
                    style: OutlinedButton.styleFrom(
                      side: BorderSide(color: t.primary, width: 1.5),
                      foregroundColor: t.primary,
                      minimumSize: const Size.fromHeight(48),
                    ),
                    onPressed: () => Navigator.pop(context,
                        CookConfirmResult(usedUp: const [], partiallyUsed: const [], skipped: true)),
                    child: const Text('跳过，不更新库存'),
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  flex: 1,
                  child: ElevatedButton(
                    style: ElevatedButton.styleFrom(
                      backgroundColor: t.primary,
                      foregroundColor: Colors.white,
                      minimumSize: const Size.fromHeight(48),
                    ),
                    onPressed: () {
                      final usedUp = <int>[];
                      final partiallyUsed = <int>[];
                      _selection.forEach((id, v) {
                        if (v == 0) usedUp.add(id);
                        if (v == 1) partiallyUsed.add(id);
                      });
                      Navigator.pop(context,
                          CookConfirmResult(usedUp: usedUp, partiallyUsed: partiallyUsed));
                    },
                    child: Text('确认已做完 · $usedUpCount 样用完'),
                  ),
                ),
              ]),
            ),
          ],
        ),
      ),
    );
  }

  String _subtitle(List<CookMaterialItem> items) {
    if (items.isEmpty) return '';
    final names = items.take(3).map((it) => it.ingredientName ?? '').join(' + ');
    return items.length > 3 ? '$names 等 ${items.length} 样' : names;
  }

  Widget _itemRow(AppTokens t, CookMaterialItem it) {
    final sel = _selection[it.ingredientId] ?? 0;
    return Container(
      padding: const EdgeInsets.symmetric(vertical: 8),
      decoration: BoxDecoration(border: Border(bottom: BorderSide(color: t.border))),
      child: Row(children: [
        Expanded(
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(it.ingredientName ?? '食材#${it.ingredientId}',
                style: t.textStyles.md.copyWith(color: t.title, fontWeight: FontWeight.w600)),
            const SizedBox(height: 2),
            // V55 去单位：用量原文（如「番茄炒蛋 2个」），不再显示克数
            Text(
              it.usageTexts.isEmpty
                  ? '家里：${StockLevel.label(it.level)}'
                  : '家里：${StockLevel.label(it.level)} · ${it.usageTexts.join(' + ')}',
              style: t.textStyles.sm.copyWith(color: t.caption),
            ),
          ]),
        ),
        // 三态 chips
        for (int i = 0; i < 3; i++)
          Padding(
            padding: const EdgeInsets.only(left: 6),
            child: GestureDetector(
              onTap: () => setState(() => _selection[it.ingredientId] = i),
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 4),
                decoration: BoxDecoration(
                  color: sel == i ? (_colors[i] ?? t.primary) : t.card,
                  border: Border.all(color: sel == i ? (_colors[i] ?? t.primary) : t.border),
                  borderRadius: BorderRadius.circular(AppTokens.rPill),
                ),
                child: Text(
                  _labels[i],
                  style: t.textStyles.sm.copyWith(
                    color: sel == i ? Colors.white : t.caption,
                    fontWeight: sel == i ? FontWeight.w700 : FontWeight.normal,
                  ),
                ),
              ),
            ),
          ),
      ]),
    );
  }
}

/// 做菜完成结果（V42，对齐 cooking-deduct-modal ②）：成功头 + 库存已更新明细 + 返回食集。
/// 完成结果：成功头 + 库存已更新明细 + 统一评价入口（去 /menu/:id/review）+ 返回食集。
class CookResultSheet extends StatelessWidget {
  final int menuId;
  final List<CookMaterialItem> items;
  final List<int> usedUp;
  final List<int> partiallyUsed;

  const CookResultSheet({
    super.key,
    required this.menuId,
    required this.items,
    required this.usedUp,
    required this.partiallyUsed,
  });

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    final byId = {for (final it in items) it.ingredientId: it};
    final usedItems = usedUp.map((id) => byId[id]).whereType<CookMaterialItem>().toList();
    final partialItems =
        partiallyUsed.map((id) => byId[id]).whereType<CookMaterialItem>().toList();
    return Container(
      decoration: const BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const SizedBox(height: 12),
          Container(width: 36, height: 4, decoration: BoxDecoration(
            color: t.border, borderRadius: BorderRadius.circular(2))),
          Padding(
            padding: const EdgeInsets.all(20),
            child: Column(children: [
              // 成功头
              Container(
                width: 56, height: 56,
                decoration: const BoxDecoration(color: AppTokens.success, shape: BoxShape.circle),
                child: const Icon(Icons.check, color: Colors.white, size: 32),
              ),
              const SizedBox(height: 12),
              Text('做好了，库存已更新', style: t.textStyles.subtitle),
              const SizedBox(height: 4),
              Text('食集 → 已完成', style: t.textStyles.sm.copyWith(color: AppTokens.success, fontWeight: FontWeight.w700)),
              const SizedBox(height: 18),

              // 库存已更新
              Row(
                children: [
                  Text('库存已更新', style: t.textStyles.sectionLabel.copyWith(letterSpacing: 1)),
                ],
              ),
              const SizedBox(height: 6),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                decoration: BoxDecoration(
                  color: t.bg,
                  border: Border.all(color: t.border),
                  borderRadius: BorderRadius.circular(AppTokens.rSm),
                ),
                child: Column(children: [
                  for (final it in usedItems)
                    _changeRow(t, it, '用完', AppTokens.error),
                  for (final it in partialItems)
                    _changeRow(t, it, '用了一些', AppTokens.warning),
                  if (usedItems.isEmpty && partialItems.isEmpty)
                    Padding(
                      padding: const EdgeInsets.symmetric(vertical: 8),
                      child: Text('本次没有更新库存',
                          style: t.textStyles.sm.copyWith(color: t.caption)),
                    ),
                ]),
              ),
              const SizedBox(height: 14),

              // 统一评价入口（这顿饭整体 + 每道菜）
              Row(
                children: [
                  Expanded(
                    child: Text('这顿饭的菜 · 吃完别忘了评价',
                        style: t.textStyles.sectionLabel.copyWith(letterSpacing: 1)),
                  ),
                  GestureDetector(
                    onTap: () {
                      Navigator.pop(context);
                      context.push('/menu/$menuId/review');
                    },
                    child: Container(
                      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                      decoration: BoxDecoration(
                        color: t.primary,
                        borderRadius: BorderRadius.circular(AppTokens.rSm),
                      ),
                      child: Text('去评价 ›',
                          style: t.textStyles.sm.copyWith(
                              color: Colors.white, fontWeight: FontWeight.w800)),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 14),

              // 返回食集
              SizedBox(
                width: double.infinity,
                child: ElevatedButton(
                  style: ElevatedButton.styleFrom(
                    backgroundColor: t.primary,
                    foregroundColor: Colors.white,
                    minimumSize: const Size.fromHeight(48),
                  ),
                  onPressed: () => Navigator.pop(context),
                  child: const Text('返回食集'),
                ),
              ),
            ]),
          ),
        ],
      ),
    );
  }

  Widget _changeRow(AppTokens t, CookMaterialItem it, String badge, Color color) {
    return Container(
      padding: const EdgeInsets.symmetric(vertical: 7),
      decoration: BoxDecoration(border: Border(bottom: BorderSide(color: t.border))),
      child: Row(children: [
        Expanded(
          child: Text('${it.ingredientName ?? ''}  ${StockLevel.label(it.level)} →',
              style: t.textStyles.md.copyWith(color: t.title)),
        ),
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
          decoration: BoxDecoration(color: color, borderRadius: BorderRadius.circular(AppTokens.rPill)),
          child: Text(badge, style: t.textStyles.chip.copyWith(color: Colors.white)),
        ),
      ]),
    );
  }
}
