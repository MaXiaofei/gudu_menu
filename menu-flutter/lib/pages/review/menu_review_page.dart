import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../core/app_theme.dart';
import '../../core/image_helper.dart';
import '../../services/review_service.dart';
import '../../widgets/loading_empty.dart';

/// 统一评价页（V43）：食集整体评价 + 每道菜评价，各自已评/未评状态。
///
/// 入口：做菜完成结果页「去评价」、食集完成态「去评价」、「我的评价」待评价列表。
class MenuReviewPage extends StatefulWidget {
  final int menuId;

  const MenuReviewPage({super.key, required this.menuId});

  @override
  State<MenuReviewPage> createState() => _MenuReviewPageState();
}

class _MenuReviewPageState extends State<MenuReviewPage> {
  MenuReviewOverview? _vo;
  bool _loading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() {
      _loading = _vo == null;
      _error = null;
    });
    try {
      final vo = await ReviewService.menuOverview(widget.menuId);
      if (!mounted) return;
      setState(() {
        _vo = vo;
        _loading = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _error = '$e';
        _loading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    return Scaffold(
      appBar: AppBar(title: const Text('评价')),
      body: _loading
          ? const LoadingView()
          : _error != null
              ? EmptyView(text: '加载失败：$_error')
              : RefreshIndicator(
                  onRefresh: _load,
                  child: ListView(
                    padding: const EdgeInsets.all(16),
                    children: [
                      _buildHeader(t),
                      const SizedBox(height: 16),
                      _buildMenuReviewCard(t),
                      const SizedBox(height: 20),
                      _buildDishList(t),
                      const SizedBox(height: 12),
                      Container(
                        padding: const EdgeInsets.all(10),
                        decoration: BoxDecoration(
                          color: t.highlight,
                          borderRadius: BorderRadius.circular(AppTokens.rSm),
                        ),
                        child: Text(
                          '评过的菜会更新菜谱评分，以后找菜、避雷都用得上。',
                          style: t.textStyles.sm.copyWith(color: t.caption),
                        ),
                      ),
                    ],
                  ),
                ),
    );
  }

  Widget _buildHeader(AppTokens t) {
    final vo = _vo!;
    return Row(
      children: [
        Container(
          width: 44,
          height: 44,
          decoration: BoxDecoration(
            color: t.secondary,
            borderRadius: BorderRadius.circular(AppTokens.rMd),
          ),
          alignment: Alignment.center,
          child: Text(
            vo.menuName.isNotEmpty ? vo.menuName.characters.first : '饭',
            style: t.textStyles.md.copyWith(color: t.primary),
          ),
        ),
        const SizedBox(width: AppTokens.sp12),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(vo.menuName, style: t.textStyles.pageTitle.copyWith(color: t.title)),
              Text('${vo.dishCount} 道菜 · 完成于 ${_fmtTime(vo.finishedAt)}',
                  style: t.textStyles.sm.copyWith(color: t.caption)),
            ],
          ),
        ),
      ],
    );
  }

  /// 食集整体评价卡片：未评 → 「评价 →」；已评 → 星级 + 四维度 + 已评 ✓（可修改）。
  Widget _buildMenuReviewCard(AppTokens t) {
    final vo = _vo!;
    final mr = vo.menuReview;
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: t.card,
        borderRadius: BorderRadius.circular(AppTokens.rMd),
        border: Border.all(color: t.border),
      ),
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('这顿饭怎么样？', style: t.textStyles.sectionLabel.copyWith(letterSpacing: 1)),
                const SizedBox(height: 8),
                if (mr == null) ...[
                  Text('还没有评价', style: t.textStyles.md.copyWith(color: t.title)),
                ] else ...[
                  Row(children: [
                    _stars(mr.starRating ?? 0, t.primary, 20),
                    const SizedBox(width: 6),
                    Text('已评 ✓',
                        style: t.textStyles.sm.copyWith(color: AppTokens.success, fontWeight: FontWeight.w600)),
                  ]),
                  const SizedBox(height: 6),
                  if (mr.dimensionScores.isNotEmpty)
                    Text(_dimSummary(mr.dimensionScores),
                        style: t.textStyles.sm.copyWith(color: t.caption)),
                ],
              ],
            ),
          ),
          GestureDetector(
            onTap: _openMenuForm,
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
              decoration: BoxDecoration(
                color: t.primary,
                borderRadius: BorderRadius.circular(AppTokens.rSm),
              ),
              child: Text(mr == null ? '评价 →' : '修改',
                  style: t.textStyles.sm.copyWith(color: t.card, fontWeight: FontWeight.w800)),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildDishList(AppTokens t) {
    final vo = _vo!;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text('菜品', style: t.textStyles.sectionLabel.copyWith(letterSpacing: 1)),
        const SizedBox(height: 8),
        ...vo.dishes.map((d) => _dishRow(t, d)),
      ],
    );
  }

  Widget _dishRow(AppTokens t, DishReviewStatus d) {
    final reviewed = d.starRating != null;
    return InkWell(
      onTap: () async {
        await context.push('/dish/${d.dishId}/review');
        _load(); // 评完返回刷新状态
      },
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 10),
        decoration: BoxDecoration(
          border: Border(bottom: BorderSide(color: t.border)),
        ),
        child: Row(
          children: [
            ClipRRect(
              borderRadius: BorderRadius.circular(AppTokens.rSm),
              child: SizedBox(
                width: 40,
                height: 40,
                child: d.coverUrl != null && d.coverUrl!.isNotEmpty
                    ? Image.network(
                        ImageHelper.toThumbnail(ImageHelper.toAbsolute(d.coverUrl!)),
                        fit: BoxFit.cover,
                        errorBuilder: (_, __, ___) => _placeholder(t, d.dishName),
                      )
                    : _placeholder(t, d.dishName),
              ),
            ),
            const SizedBox(width: AppTokens.sp12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(d.dishName, style: t.textStyles.md.copyWith(color: t.title)),
                  const SizedBox(height: 2),
                  reviewed
                      ? Row(children: [
                          _stars(d.starRating!, t.primary, 16),
                          const SizedBox(width: 6),
                          Text('已评',
                              style: t.textStyles.sm.copyWith(
                                  color: AppTokens.success, fontWeight: FontWeight.w600)),
                        ])
                      : Text('未评', style: t.textStyles.sm.copyWith(color: t.caption)),
                ],
              ),
            ),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 13, vertical: 6),
              decoration: BoxDecoration(
                color: t.card,
                borderRadius: BorderRadius.circular(AppTokens.rSm),
                border: Border.all(color: t.primary, width: 1.5),
              ),
              child: Text('评价',
                  style: t.textStyles.sm.copyWith(color: t.primary, fontWeight: FontWeight.w800)),
            ),
          ],
        ),
      ),
    );
  }

  Widget _placeholder(AppTokens t, String name) {
    return Container(
      color: t.secondary,
      alignment: Alignment.center,
      child: Text(name.isNotEmpty ? name.characters.first : '菜',
          style: t.textStyles.sm.copyWith(color: t.caption)),
    );
  }

  Widget _stars(int n, Color color, double size) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: List.generate(5, (i) => Icon(
        i < n ? Icons.star : Icons.star_border,
        size: size,
        color: color,
      )),
    );
  }

  /// 四维度摘要："口味 ★★★★☆ · 难度 ★★★☆☆"
  String _dimSummary(Map<int, int> scores) {
    const labels = {
      1: '口味', 2: '难度', 3: '营养均衡', 4: '外观',
    };
    return scores.entries.map((e) {
      final label = labels[e.key] ?? '维度';
      return '$label ${'★' * e.value}${'☆' * (5 - e.value)}';
    }).join(' · ');
  }

  Future<void> _openMenuForm() async {
    final vo = _vo;
    if (vo == null) return;
    await context.push('/menu/${widget.menuId}/review-form',
        extra: {'menuName': vo.menuName});
    _load();
  }

  static String _fmtTime(String? iso) {
    if (iso == null || iso.isEmpty) return '';
    final dt = DateTime.tryParse(iso);
    if (dt == null) return '';
    return '${dt.month}/${dt.day}';
  }
}
