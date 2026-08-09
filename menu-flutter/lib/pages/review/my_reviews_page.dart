import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../core/app_theme.dart';
import '../../services/review_service.dart';
import '../../widgets/loading_empty.dart';

/// 我的评价（V43）：待评价食集 + 我评过的（食集/菜品历史）。
///
/// 入口：我的 tab「我的评价」。待评价 → 统一评价页；已评 → 可点进修改。
class MyReviewsPage extends StatefulWidget {
  const MyReviewsPage({super.key});

  @override
  State<MyReviewsPage> createState() => _MyReviewsPageState();
}

class _MyReviewsPageState extends State<MyReviewsPage> {
  MyReviews? _data;
  bool _loading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() {
      _loading = _data == null;
      _error = null;
    });
    try {
      final d = await ReviewService.myReviews();
      if (!mounted) return;
      setState(() {
        _data = d;
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
      appBar: AppBar(title: const Text('我的评价')),
      body: _loading
          ? const LoadingView()
          : _error != null
              ? EmptyView(text: '加载失败：$_error')
              : RefreshIndicator(
                  onRefresh: _load,
                  child: _buildBody(t),
                ),
    );
  }

  Widget _buildBody(AppTokens t) {
    final d = _data!;
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        // 待评价
        if (d.pendingMenus.isNotEmpty) ...[
          _sectionTitle(t, '待评价'),
          const SizedBox(height: 8),
          ...d.pendingMenus.map((m) => _pendingTile(t, m)),
          const SizedBox(height: 20),
        ],
        // 我评过的
        _sectionTitle(t, '我评过的'),
        const SizedBox(height: 8),
        if (d.reviews.isEmpty)
          Container(
            padding: const EdgeInsets.all(20),
            decoration: BoxDecoration(
              color: t.card,
              borderRadius: BorderRadius.circular(AppTokens.rMd),
              border: Border.all(color: t.border),
            ),
            child: Center(
              child: Text('还没有评价，做完一顿饭顺手评一下',
                  style: t.textStyles.sm.copyWith(color: t.caption)),
            ),
          )
        else
          ...d.reviews.map((r) => _reviewTile(t, r)),
      ],
    );
  }

  Widget _sectionTitle(AppTokens t, String text) {
    return Text(text, style: t.textStyles.sectionLabel.copyWith(letterSpacing: 1));
  }

  Widget _pendingTile(AppTokens t, PendingMenu m) {
    final remaining = m.remaining;
    return InkWell(
      onTap: () async {
        await context.push('/menu/${m.menuId}/review');
        _load();
      },
      child: Container(
        margin: const EdgeInsets.only(bottom: 8),
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
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
                  Text(m.menuName, style: t.textStyles.md.copyWith(color: t.title)),
                  const SizedBox(height: 2),
                  Text(
                    m.menuReviewed
                        ? '还差 $remaining 道菜没评'
                        : '食集整体没评${m.reviewedDishCount > 0 ? '，还差 $remaining 项' : ''}',
                    style: t.textStyles.sm.copyWith(color: AppTokens.warning),
                  ),
                ],
              ),
            ),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 13, vertical: 6),
              decoration: BoxDecoration(
                color: t.primary,
                borderRadius: BorderRadius.circular(AppTokens.rSm),
              ),
              child: Text('去评价 →',
                  style: t.textStyles.sm.copyWith(color: t.card, fontWeight: FontWeight.w800)),
            ),
          ],
        ),
      ),
    );
  }

  Widget _reviewTile(AppTokens t, ReviewEntry r) {
    return InkWell(
      onTap: () async {
        if (r.isMenu) {
          await context.push('/menu/${r.menuId}/review');
        } else {
          await context.push('/dish/${r.dishId}/review');
        }
        _load();
      },
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 10),
        decoration: BoxDecoration(
          border: Border(bottom: BorderSide(color: t.border)),
        ),
        child: Row(
          children: [
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
              decoration: BoxDecoration(
                color: r.isMenu ? t.highlight : t.card,
                borderRadius: BorderRadius.circular(AppTokens.rXs),
                border: Border.all(color: r.isMenu ? t.primary : t.border),
              ),
              child: Text(r.isMenu ? '食集' : '菜',
                  style: t.textStyles.chip.copyWith(
                      color: r.isMenu ? t.primary : t.caption)),
            ),
            const SizedBox(width: AppTokens.sp10),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(r.name ?? '', style: t.textStyles.md.copyWith(color: t.title)),
                  const SizedBox(height: 2),
                  Text(_fmtTime(r.createTime),
                      style: t.textStyles.sm.copyWith(color: t.caption)),
                ],
              ),
            ),
            Row(
              mainAxisSize: MainAxisSize.min,
              children: List.generate(5, (i) => Icon(
                i < r.starRating ? Icons.star : Icons.star_border,
                size: 16,
                color: t.primary,
              )),
            ),
          ],
        ),
      ),
    );
  }

  static String _fmtTime(String? iso) {
    if (iso == null || iso.isEmpty) return '';
    final dt = DateTime.tryParse(iso);
    if (dt == null) return '';
    return '${dt.month}/${dt.day}';
  }
}
