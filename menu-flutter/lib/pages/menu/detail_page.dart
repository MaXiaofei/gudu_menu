import 'package:flutter/material.dart';

import '../../core/theme.dart';
import '../../models/menu.dart';
import '../../services/menu_service.dart';
import '../../widgets/loading_empty.dart';

/// 食集详情（对应 menu-mini/src/pages/menu/Detail.vue）。
///
/// 展示：食集名 + 份数 + 状态 + 关联菜列表（菜名/份数）+ 底部「整集做菜」按钮。
///
/// 整集做菜（Plan A）：POST /menu/{id}/cook → 聚合各菜用量 → 扣 pantry
/// → 每菜写 cooking_record → 食集标 DONE；欠量时提示缺几项。
///
/// 菜名：后端 `/menu/{id}` 的 dishes 冗余带 dishName/coverUrl，直接渲染，
/// 无需再逐菜 GET /dish/{id} 取名（评审 N+1 gap 已修）。
class MenuDetailPage extends StatefulWidget {
  final int id;
  const MenuDetailPage({super.key, required this.id});
  @override
  State<MenuDetailPage> createState() => _MenuDetailPageState();
}

class _MenuDetailPageState extends State<MenuDetailPage> {
  MenuDetail? _detail;
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      _detail = await MenuService.detail(widget.id);
    } catch (_) {}
    if (mounted) setState(() => _loading = false);
  }

  /// 整集做菜：POST /menu/{id}/cook。
  /// 成功提示「已做菜，库存已扣」；shortages 非空时提示缺量项数。
  bool _cooking = false;
  Future<void> _cookMenu() async {
    if (_cooking) return;
    setState(() => _cooking = true);
    try {
      final result = await MenuService.cookMenu(widget.id);
      if (!mounted) return;
      final msg = result.hasShortage
          ? '已做菜，库存已扣；缺量：${result.shortages.length} 项'
          : '已做菜，库存已扣';
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text(msg)));
      // 做完后食集已标 DONE，刷新状态展示
      await _load();
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(const SnackBar(content: Text('做菜失败')));
      }
    }
    if (mounted) setState(() => _cooking = false);
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(title: const Text('食集详情')),
        body: _loading
            ? const LoadingView()
            : _detail == null
                ? const EmptyView(text: '加载详情失败')
                : _buildBody(),
        bottomNavigationBar: (_loading || _detail == null)
            ? null
            : SafeArea(
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(16, 8, 16, 12),
                  child: ElevatedButton(
                    style: ElevatedButton.styleFrom(
                      backgroundColor: AppColors.success,
                      minimumSize: const Size.fromHeight(48),
                    ),
                    // 食集已完成则禁用
                    onPressed: (_detail!.menu.isDone || _cooking)
                        ? null
                        : _cookMenu,
                    child: _cooking
                        ? const SizedBox(
                            width: 18,
                            height: 18,
                            child: CircularProgressIndicator(
                                strokeWidth: 2, color: Colors.white),
                          )
                        : Text(_detail!.menu.isDone ? '已完成' : '整集做菜'),
                  ),
                ),
              ),
      );

  Widget _buildBody() {
    final m = _detail!.menu;
    return ListView(
      children: [
        Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Expanded(
                    child: Text(m.name,
                        style: const TextStyle(
                            fontSize: 20, fontWeight: FontWeight.bold)),
                  ),
                  if (m.isDone)
                    const _StatusChip('已完成', AppColors.success)
                    else
                    const _StatusChip('进行中', AppColors.warning),
                ],
              ),
              const SizedBox(height: 8),
              Text(
                '份数 ${m.servingCount ?? 1} · 关联 ${_detail!.dishes.length} 道菜',
                style: const TextStyle(
                    fontSize: 12, color: AppColors.textSecondary),
              ),
            ],
          ),
        ),
        const _SectionTitle('包含菜品'),
        ..._detail!.dishes.map((d) => _DishRow(
              dishId: d.dishId,
              dishName: d.dishName,
              servingFactor: d.servingFactor,
            )),
        const SizedBox(height: 24),
      ],
    );
  }
}

/// 菜品行：直接用后端冗余的菜名（dishes 带 dishName）。
class _DishRow extends StatelessWidget {
  final int dishId;
  final String? dishName;
  final double? servingFactor;
  const _DishRow({required this.dishId, this.dishName, this.servingFactor});

  @override
  Widget build(BuildContext context) => Container(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
        decoration: const BoxDecoration(
            border: Border(top: BorderSide(color: AppColors.divider))),
        child: Row(
          children: [
            Expanded(
              child: Text(
                (dishName == null || dishName!.isEmpty)
                    ? '菜 #$dishId'
                    : dishName!,
                style: const TextStyle(fontSize: 14),
              ),
            ),
            Text(
              '× ${servingFactor?.toStringAsFixed(1) ?? '1.0'} 份',
              style: const TextStyle(
                  fontSize: 13, color: AppColors.textSecondary),
            ),
          ],
        ),
      );
}

class _SectionTitle extends StatelessWidget {
  final String text;
  const _SectionTitle(this.text);
  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.all(16),
        child: Text(text,
            style: const TextStyle(
                fontSize: 16, fontWeight: FontWeight.bold)),
      );
}

class _StatusChip extends StatelessWidget {
  final String text;
  final Color color;
  const _StatusChip(this.text, this.color);
  @override
  Widget build(BuildContext context) => Container(
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
        decoration: BoxDecoration(
          color: color.withAlpha(20),
          borderRadius: BorderRadius.circular(12),
        ),
        child: Text(text,
            style: TextStyle(
                fontSize: 12, color: color, fontWeight: FontWeight.w600)),
      );
}
