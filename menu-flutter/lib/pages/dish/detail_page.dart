import 'dart:async';

import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../core/image_helper.dart';
import '../../core/app_theme.dart';
import '../../models/dish.dart';
import '../../models/nutrition_metric.dart';
import '../../services/dish_service.dart';
import '../../widgets/image_viewer.dart';
import '../../widgets/loading_empty.dart';
import '../../widgets/nutrition_grid.dart';

/// 菜品详情（复刻 menu-mini/src/pages/dish/Detail.vue）。
/// 封面 + 营养区 + 做法步骤（**步骤计时器**）+ 直接做这道菜/去点评。
///
/// 图片策略：
/// - 列表/详情默认加载缩略图（/thumbnail/xxx.jpg），节省流量 + 加载快。
/// - 点击图片弹出全屏可查看器，加载原图（/original/xxx.jpg），支持双指缩放。
class DishDetailPage extends StatefulWidget {
  final int id;
  const DishDetailPage({super.key, required this.id});
  @override
  State<DishDetailPage> createState() => _DishDetailPageState();
}

class _DishDetailPageState extends State<DishDetailPage> {
  DishDetail? _detail;
  List<NutritionMetric> _metrics = [];
  Map<String, num> _nutrition = {};
  final int _serving = 1;
  bool _loading = true;

  int _activeStep = -1;
  int _elapsed = 0;
  Timer? _timer;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  Future<void> _load() async {
    try {
      _detail = await DishService.detail(widget.id);
    } catch (_) {}
    try {
      _nutrition = await DishService.nutrition(widget.id, serving: _serving);
    } catch (_) {}
    try {
      _metrics = await DishService.metrics();
    } catch (_) {}
    if (mounted) setState(() => _loading = false);
  }

  void _toggleTimer(int i) {
    if (_activeStep == i && _timer != null) {
      _timer!.cancel();
      _timer = null;
      setState(() => _activeStep = -1);
      return;
    }
    _timer?.cancel();
    setState(() {
      _activeStep = i;
      _elapsed = 0;
    });
    _timer = Timer.periodic(const Duration(seconds: 1), (_) {
      setState(() => _elapsed++);
    });
  }

  /// 打开全屏图片查看器（加载原图）。
  void _openViewer(String url) {
    final urls = ImageHelper.resolve(url);
    Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => ImageViewer(
          thumbnailUrl: urls.thumbnail,
          originalUrl: urls.original,
        ),
      ),
    );
  }

  /// 单菜直做：POST /dish/{id}/cook-now?servings=N。
  /// 扣 pantry + 写 cooking_record；库存不够时提示缺哪些食材（shortages）。
  bool _cooking = false;
  Future<void> _cookNow() async {
    if (_cooking) return;
    setState(() => _cooking = true);
    try {
      final result = await DishService.cookNow(widget.id, servings: _serving);
      if (!mounted) return;
      final msg = result.hasShortage
          ? '已做菜，库存已扣；缺量：${result.shortages.length} 项'
          : '已做菜，库存已扣';
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text(msg)));
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(const SnackBar(content: Text('做菜失败')));
      }
    }
    if (mounted) setState(() => _cooking = false);
  }

  /// 构建可点击的缩略图（点一下弹全屏原图）。
  Widget _thumbnailImage(String url,
      {double? width, double? height, BoxFit fit = BoxFit.cover}) {
    final t = AppTokens.of(context);
    final urls = ImageHelper.resolve(url);
    return GestureDetector(
      onTap: () => _openViewer(url),
      child: Image.network(
        urls.thumbnail,
        width: width,
        height: height,
        fit: fit,
        errorBuilder: (_, __, ___) => const SizedBox.shrink(),
        loadingBuilder: (_, child, progress) {
          if (progress == null) return child;
          return Container(
            width: width,
            height: height,
            color: t.bg,
            child: const Center(
              child: SizedBox(
                width: 16,
                height: 16,
                child: CircularProgressIndicator(strokeWidth: 2),
              ),
            ),
          );
        },
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    return Scaffold(
        appBar: AppBar(title: const Text('菜品详情')),
        body: _loading
            ? const LoadingView()
            : _detail == null
                ? const EmptyView(text: '加载详情失败')
                : ListView(
                    children: [
                      if (_detail!.dish.coverUrl != null &&
                          _detail!.dish.coverUrl!.isNotEmpty)
                        _thumbnailImage(
                          _detail!.dish.coverUrl!,
                          width: double.infinity,
                          height: 220,
                        ),
                      Padding(
                        padding: const EdgeInsets.all(AppTokens.sp16),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(_detail!.dish.name,
                                style: TextStyle(
                                    fontSize: 24,
                                    fontWeight: FontWeight.bold,
                                    color: t.title)),
                            const SizedBox(height: AppTokens.sp8),
                            Text(
                              '备料 ${_detail!.dish.prepTime ?? 0}分 · 烹饪 ${_detail!.dish.cookTime ?? 0}分 · 难度 ${_detail!.dish.difficulty ?? '-'}/5',
                              style: TextStyle(
                                  fontSize: 12, color: t.caption),
                            ),
                            if ((_detail!.dish.note ?? '').isNotEmpty) ...[
                              const SizedBox(height: AppTokens.sp8),
                              Text(_detail!.dish.note!,
                                  style: TextStyle(
                                      fontSize: 12,
                                      color: t.body)),
                            ],
                          ],
                        ),
                      ),
                      if (_metrics.isNotEmpty && _nutrition.isNotEmpty) ...[
                        const _SectionTitle('营养（份数 1）'),
                        NutritionGrid(metrics: _metrics, values: _nutrition),
                      ],
                      const _SectionTitle('做法'),
                      ..._detail!.steps.asMap().entries.map((entry) {
                        final i = entry.key;
                        final s = entry.value;
                        final active = _activeStep == i;
                        return Container(
                          padding: const EdgeInsets.symmetric(
                              horizontal: AppTokens.sp16, vertical: AppTokens.sp12),
                          decoration: BoxDecoration(
                              border: Border(
                                  top: BorderSide(
                                      color: t.border))),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Row(
                                children: [
                                  Text('步骤 ${i + 1}', style: TextStyle(color: t.title)),
                                  const Spacer(),
                                  ElevatedButton(
                                    style: ElevatedButton.styleFrom(
                                      backgroundColor: active
                                          ? AppTokens.error
                                          : t.primary,
                                      minimumSize: const Size(64, 32),
                                    ),
                                    onPressed: () => _toggleTimer(i),
                                    child: Text(active ? '停止' : '计时'),
                                  ),
                                ],
                              ),
                              const SizedBox(height: AppTokens.sp8),
                              Text(s.text, style: TextStyle(color: t.body)),
                              if (s.imageList.isNotEmpty) ...[
                                const SizedBox(height: AppTokens.sp8),
                                Wrap(
                                  spacing: AppTokens.sp8,
                                  runSpacing: AppTokens.sp8,
                                  children: s.imageList
                                      .map((img) => _thumbnailImage(
                                            img,
                                            width: 80,
                                            height: 80,
                                          ))
                                      .toList(),
                                ),
                              ],
                              if (active) ...[
                                const SizedBox(height: AppTokens.sp8),
                                Text('⏱ ${_elapsed}s',
                                    style: TextStyle(
                                        color: t.primary,
                                        fontSize: 18,
                                        fontWeight: FontWeight.bold)),
                              ],
                            ],
                          ),
                        );
                      }),
                      const SizedBox(height: AppTokens.sp16),
                      Padding(
                        padding: const EdgeInsets.symmetric(
                            horizontal: AppTokens.sp16, vertical: AppTokens.sp12),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.stretch,
                          children: [
                            ElevatedButton(
                              style: ElevatedButton.styleFrom(
                                  backgroundColor: AppTokens.success),
                              onPressed: _cooking ? null : _cookNow,
                              child: _cooking
                                  ? const SizedBox(
                                      width: 18,
                                      height: 18,
                                      child: CircularProgressIndicator(
                                          strokeWidth: 2, color: Colors.white))
                                  : const Text('直接做这道菜'),
                            ),
                            const SizedBox(height: AppTokens.sp12),
                            OutlinedButton(
                              onPressed: () =>
                                  context.push('/dish/${widget.id}/review'),
                              child: const Text('去点评'),
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(height: AppTokens.sp24),
                    ],
                  ),
      );
  }
}

class _SectionTitle extends StatelessWidget {
  final String text;
  const _SectionTitle(this.text);
  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    return Padding(
        padding: const EdgeInsets.all(AppTokens.sp16),
        child: Text(text,
            style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: t.title)),
      );
  }
}
