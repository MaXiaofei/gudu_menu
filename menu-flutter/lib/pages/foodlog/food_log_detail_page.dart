import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../core/app_theme.dart';
import '../../services/food_log_service.dart';
import '../../widgets/loading_empty.dart';

/// 食记单条详情（对齐 dailylog.html 单条详情）。
///
/// 这顿饭的菜（去评价统一入口）→ 这顿饭用了这些（用完/用了一些）→ 再做一次。
/// 再做一次：POST /menu/{id}/copy → 复制建新食集（ACTIVE）。
class FoodLogDetailPage extends StatefulWidget {
  final int menuId;
  const FoodLogDetailPage({super.key, required this.menuId});

  @override
  State<FoodLogDetailPage> createState() => _FoodLogDetailPageState();
}

class _FoodLogDetailPageState extends State<FoodLogDetailPage> {
  AppTokens get _t => AppTokens.of(context);

  FoodLogDetail? _detail;
  bool _loading = true;
  bool _copying = false;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final d = await FoodLogService.detail(widget.menuId);
      if (!mounted) return;
      setState(() {
        _detail = d;
        _loading = false;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() => _loading = false);
    }
  }

  /// 再做一次：复制食集 → 跳食集详情。
  Future<void> _copyAndGo() async {
    if (_copying) return;
    setState(() => _copying = true);
    try {
      final newId = await FoodLogService.copyMenu(widget.menuId);
      if (!mounted) return;
      context.pop();
      await context.push('/menu/$newId');
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(const SnackBar(content: Text('复制失败')));
      }
    } finally {
      if (mounted) setState(() => _copying = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final t = _t;
    final d = _detail;
    return Scaffold(
      body: SafeArea(
        bottom: false,
        child: Column(
          children: [
            // 顶栏：返回 + ⋯
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 12, 16, 0),
              child: Row(children: [
                GestureDetector(
                  onTap: () => Navigator.of(context).pop(),
                  child: Text('‹',
                      style: TextStyle(fontSize: 20, color: _t.title, fontWeight: FontWeight.w800)),
                ),
                const Spacer(),
                Text('⋯',
                    style: TextStyle(fontSize: 14, color: _t.title)),
              ]),
            ),
            Expanded(
              child: _loading
                  ? const LoadingView()
                  : d == null
                      ? const EmptyView(text: '加载失败')
                      : _buildBody(t, d),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildBody(AppTokens t, FoodLogDetail d) {
    final time = d.cookedAt == null
        ? ''
        : '${d.cookedAt!.month}/${d.cookedAt!.day} ${d.cookedAt!.hour.toString().padLeft(2, '0')}:${d.cookedAt!.minute.toString().padLeft(2, '0')}';
    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 6, 16, 24),
      children: [
        // 头
        Text(d.name, style: t.textStyles.h3.copyWith(color: t.title)),
        Text(
          '${time}${d.servingCount != null ? ' · ${d.servingCount} 人份' : ''}',
          style: t.textStyles.sm.copyWith(color: t.caption),
        ),
        const SizedBox(height: 14),

        // 这顿饭的菜：去评价统一入口
        Text('这顿饭的菜 · 吃完别忘了评价',
            style: t.textStyles.sectionLabel.copyWith(letterSpacing: 1)),
        const SizedBox(height: 6),
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 11),
          decoration: BoxDecoration(
            color: t.bg,
            border: Border.all(color: t.border),
            borderRadius: BorderRadius.circular(12),
          ),
          child: Row(children: [
            Expanded(
              child: Text(
                d.dishes.map((x) => x.dishName).join(' · '),
                style: t.textStyles.sm.copyWith(color: t.body),
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
              ),
            ),
            const SizedBox(width: 8),
            GestureDetector(
              onTap: () => _goReview(d),
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 13, vertical: 6),
                decoration: BoxDecoration(
                  color: t.primary,
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Text('去评价 ›',
                    style: t.textStyles.chip.copyWith(color: Colors.white, fontWeight: FontWeight.w800)),
              ),
            ),
          ]),
        ),
        const SizedBox(height: 14),

        // 这顿饭用了这些
        Text('这顿饭用了这些', style: t.textStyles.sectionLabel.copyWith(letterSpacing: 1)),
        const SizedBox(height: 6),
        Container(
          width: double.infinity,
          padding: const EdgeInsets.all(10),
          decoration: BoxDecoration(
            color: t.card,
            border: Border.all(color: t.border),
            borderRadius: BorderRadius.circular(10),
          ),
          child: Text(
            d.usedUp.isEmpty && d.partial.isEmpty
                ? '没更新库存（跳过了确认）'
                : [
                    if (d.usedUp.isNotEmpty)
                      '用完 ${d.usedUp.length} 样：${d.usedUp.join(' · ')}',
                    if (d.partial.isNotEmpty)
                      '用了一些 ${d.partial.length} 样：${d.partial.join(' · ')}',
                  ].join('\n'),
            style: t.textStyles.sm.copyWith(color: t.body, height: 1.9),
          ),
        ),
        const SizedBox(height: 8),
        Container(
          padding: const EdgeInsets.all(10),
          decoration: BoxDecoration(
            color: t.highlight,
            border: Border.all(color: t.border),
            borderRadius: BorderRadius.circular(10),
          ),
          child: Text('库存档位已自动更新（用完→用完，用了一些→降一档）。去库存页随时可改。',
              style: t.textStyles.sm.copyWith(color: t.primaryDeep, height: 1.5)),
        ),
        const SizedBox(height: 16),

        // 再做一次
        ElevatedButton(
          style: ElevatedButton.styleFrom(
            backgroundColor: t.primary,
            foregroundColor: Colors.white,
            minimumSize: const Size.fromHeight(48),
          ),
          onPressed: _copying ? null : _copyAndGo,
          child: _copying
              ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
              : const Text('再做一次（复制建新食集）', style: TextStyle(fontWeight: FontWeight.w800)),
        ),
        const SizedBox(height: 6),
        Text('复制这 ${d.dishes.length} 道菜 + 份数到新食集，重新走一遍流程',
            style: t.textStyles.sm.copyWith(color: t.caption), textAlign: TextAlign.center),
      ],
    );
  }

  /// 去评价：复用统一评价页（menu review 表单，V43 已有页面）。
  Future<void> _goReview(FoodLogDetail d) async {
    final picked = await showModalBottomSheet<int>(
      context: context,
      backgroundColor: _t.card,
      builder: (ctx) => SafeArea(
        child: ListView(
          shrinkWrap: true,
          children: [
            Padding(
              padding: const EdgeInsets.all(16),
              child: Text('评价哪道菜？', style: _t.textStyles.cardTitle.copyWith(color: _t.title)),
            ),
            for (final dish in d.dishes)
              ListTile(
                title: Text(dish.dishName,
                    style: _t.textStyles.md.copyWith(color: _t.title)),
                trailing: Text('★', style: TextStyle(color: _t.primary)),
                onTap: () => Navigator.pop(ctx, dish.dishId),
              ),
          ],
        ),
      ),
    );
    if (picked == null || !mounted) return;
    await context.push('/dish/$picked/review');
    _load();
  }
}
