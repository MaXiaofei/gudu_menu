import 'dart:io';

import 'package:flutter/material.dart';

import '../../core/app_theme.dart';

/// 预览数据（写菜谱页「预览」组装，随路由 extra 传入）。
class DishPreviewData {
  final String name;
  final String? coverUrl; // 已上传 URL（草稿回填）
  final File? coverFile; // 本地新选图
  final String prepText;
  final String cookText;
  final int difficulty;
  final List<String> tags; // 标签名（必选，原型 ⑦ 屏 chips）
  final List<String> cuisines; // 菜系名（可选，§16.2）
  final String? note;
  final List<(String, String)> ingredients; // (食材名, 用量自由文本)
  final List<DishPreviewStep> steps;
  final Future<void> Function() onPublish; // 发布回调（复用写菜谱页发布逻辑）

  const DishPreviewData({
    required this.name,
    this.coverUrl,
    this.coverFile,
    this.prepText = '',
    this.cookText = '',
    this.difficulty = 3,
    this.tags = const [],
    this.cuisines = const [],
    this.note,
    this.ingredients = const [],
    this.steps = const [],
    required this.onPublish,
  });
}

class DishPreviewStep {
  final String text;
  final String? imageUrl;

  const DishPreviewStep({required this.text, this.imageUrl});
}

/// 写菜谱预览页（DESIGN.md §16.4 / 原型 cookbook-add.html ⑦ 屏）：
/// 发布前看完整详情 = 菜谱详情所见即所得（封面/菜名/meta/用料/介绍/步骤）。
/// 顶部「编辑」返回写菜谱页；底部「发布」回调 create_page 的发布逻辑。
class DishPreviewPage extends StatefulWidget {
  final DishPreviewData data;
  const DishPreviewPage({super.key, required this.data});

  @override
  State<DishPreviewPage> createState() => _DishPreviewPageState();
}

class _DishPreviewPageState extends State<DishPreviewPage> {
  bool _publishing = false;

  Future<void> _onPublish() async {
    if (_publishing) return;
    setState(() => _publishing = true);
    try {
      await widget.data.onPublish();
      // 发布成功：create_page 内部 context.go 已跳新菜详情，本页无需再处理
    } catch (_) {
      if (mounted) setState(() => _publishing = false);
    }
  }

  String get _difficultyStars {
    final d = widget.data.difficulty.clamp(0, 5);
    return '★' * d + '☆' * (5 - d);
  }

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    final d = widget.data;
    return Scaffold(
      backgroundColor: t.bg,
      body: SafeArea(
        bottom: false,
        child: Column(
          children: [
            // 预览页顶栏：返回编辑 + 右侧「编辑」（原型 ⑦ 屏）
            Padding(
              padding: const EdgeInsets.fromLTRB(
                  AppTokens.sp16, AppTokens.sp12, AppTokens.sp16, AppTokens.sp4),
              child: Row(
                children: [
                  GestureDetector(
                    onTap: () => Navigator.of(context).pop(),
                    child: Text('‹',
                        style: TextStyle(
                            fontSize: 24,
                            color: t.title,
                            fontWeight: FontWeight.w800)),
                  ),
                  const Spacer(),
                  GestureDetector(
                    onTap: () => Navigator.of(context).pop(),
                    child: Text('编辑',
                        style: t.textStyles.md.copyWith(
                            color: t.accent, fontWeight: FontWeight.w800)),
                  ),
                ],
              ),
            ),
            Expanded(
              child: ListView(
                padding: const EdgeInsets.fromLTRB(
                    AppTokens.sp16, 0, AppTokens.sp16, AppTokens.sp24),
                children: [
                  // Hero 封面（无图 → 首字占位 §10）
                  ClipRRect(
                    borderRadius: BorderRadius.circular(AppTokens.rMd),
                    child: SizedBox(
                      height: 150,
                      child: d.coverFile != null
                          ? Image.file(d.coverFile!, fit: BoxFit.cover)
                          : d.coverUrl != null && d.coverUrl!.isNotEmpty
                              ? Image.network(
                                  d.coverUrl!,
                                  fit: BoxFit.cover,
                                  errorBuilder: (_, __, ___) => _coverFallback(t),
                                )
                              : _coverFallback(t),
                    ),
                  ),
                  const SizedBox(height: AppTokens.sp16),
                  // 菜名 + meta
                  Text(d.name.isEmpty ? '未命名菜谱' : d.name,
                      style: t.textStyles.h2.copyWith(color: t.title)),
                  const SizedBox(height: AppTokens.sp4),
                  Text(
                    [
                      if (d.prepText.isNotEmpty) '备料 ${d.prepText} 分',
                      if (d.cookText.isNotEmpty) '烹饪 ${d.cookText} 分',
                      '难度 $_difficultyStars',
                    ].join(' · '),
                    style: t.textStyles.xs.copyWith(color: t.caption),
                  ),
                  // 菜系 + 标签 chips（原型 ⑦ 屏；菜系在前，标签必选）
                  if (d.cuisines.isNotEmpty || d.tags.isNotEmpty) ...[
                    const SizedBox(height: AppTokens.sp10),
                    Wrap(
                      spacing: 6,
                      children: [
                        ...d.cuisines.map((c) => Container(
                              padding: const EdgeInsets.symmetric(
                                  horizontal: 9, vertical: 3),
                              decoration: BoxDecoration(
                                color: t.primarySoft,
                                borderRadius:
                                    BorderRadius.circular(AppTokens.rPill),
                              ),
                              child: Text(c,
                                  style: t.textStyles.xs.copyWith(
                                      color: t.accent,
                                      fontWeight: FontWeight.w700)),
                            )),
                        ...d.tags.map((tag) => Container(
                              padding: const EdgeInsets.symmetric(
                                  horizontal: 9, vertical: 3),
                              decoration: BoxDecoration(
                                color: t.primarySoft,
                                borderRadius:
                                    BorderRadius.circular(AppTokens.rPill),
                              ),
                              child: Text(tag,
                                  style: t.textStyles.xs.copyWith(
                                      color: t.accent,
                                      fontWeight: FontWeight.w700)),
                            )),
                      ],
                    ),
                  ],
                  const SizedBox(height: AppTokens.sp16),
                  // 用料（与库存解耦，只列食材和用量，§16.3）
                  if (d.ingredients.isNotEmpty) ...[
                    _sectionLabel(t, '用料'),
                    const SizedBox(height: AppTokens.sp8),
                    Container(
                      decoration: BoxDecoration(
                        color: t.card,
                        border: Border.all(color: t.border),
                        borderRadius: BorderRadius.circular(AppTokens.rMd),
                      ),
                      child: Column(
                        children: [
                          for (var i = 0; i < d.ingredients.length; i++) ...[
                            Padding(
                              padding: const EdgeInsets.symmetric(
                                  horizontal: AppTokens.sp12, vertical: 10),
                              child: Row(
                                children: [
                                  _avatar(t, d.ingredients[i].$1),
                                  const SizedBox(width: AppTokens.sp10),
                                  Expanded(
                                    child: Text(d.ingredients[i].$1,
                                        style: t.textStyles.md.copyWith(
                                            fontWeight: FontWeight.w700,
                                            color: t.title)),
                                  ),
                                  if (d.ingredients[i].$2.isNotEmpty)
                                    Text(d.ingredients[i].$2,
                                        style: t.textStyles.sm.copyWith(
                                            fontWeight: FontWeight.w800,
                                            color: t.title)),
                                ],
                              ),
                            ),
                            if (i != d.ingredients.length - 1)
                              Divider(height: 1, thickness: 1, color: t.border),
                          ],
                        ],
                      ),
                    ),
                  ],
                  // 菜谱介绍
                  if (d.note != null && d.note!.isNotEmpty) ...[
                    const SizedBox(height: AppTokens.sp16),
                    _sectionLabel(t, '介绍'),
                    const SizedBox(height: AppTokens.sp6),
                    Text(d.note!,
                        style: t.textStyles.sm.copyWith(color: t.body)),
                  ],
                  // 做法步骤
                  if (d.steps.isNotEmpty) ...[
                    const SizedBox(height: AppTokens.sp16),
                    _sectionLabel(t, '做法'),
                    const SizedBox(height: AppTokens.sp6),
                    for (var i = 0; i < d.steps.length; i++) ...[
                      Padding(
                        padding: const EdgeInsets.symmetric(vertical: 5),
                        child: Row(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Container(
                              width: 22,
                              height: 22,
                              decoration: BoxDecoration(
                                color: t.primary,
                                shape: BoxShape.circle,
                              ),
                              alignment: Alignment.center,
                              child: Text('${i + 1}',
                                  style: t.textStyles.xs.copyWith(
                                      color: t.card,
                                      fontWeight: FontWeight.w800)),
                            ),
                            const SizedBox(width: AppTokens.sp10),
                            Expanded(
                              child: Text(d.steps[i].text,
                                  style: t.textStyles.sm.copyWith(
                                      color: t.body, height: 1.6)),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ],
                ],
              ),
            ),
            // Sticky 发布
            Container(
              padding: const EdgeInsets.fromLTRB(
                  AppTokens.sp16, AppTokens.sp10, AppTokens.sp16, AppTokens.sp12),
              decoration: BoxDecoration(
                color: t.card,
                border: Border(top: BorderSide(color: t.border)),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  SizedBox(
                    height: 48,
                    child: ElevatedButton(
                      onPressed: _publishing ? null : _onPublish,
                      child: _publishing
                          ? const SizedBox(
                              width: 20,
                              height: 20,
                              child: CircularProgressIndicator(
                                  strokeWidth: 2, color: Colors.white),
                            )
                          : Text('发布「${d.name}」',
                              style: t.textStyles.lg.copyWith(color: Colors.white)),
                    ),
                  ),
                  const SizedBox(height: AppTokens.sp6),
                  Text('发布后进菜谱库 · 不满意点左上角返回改',
                      textAlign: TextAlign.center,
                      style: t.textStyles.xs.copyWith(color: t.caption)),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _sectionLabel(AppTokens t, String text) => Text(text,
      style: t.textStyles.xs.copyWith(
          color: t.caption, fontWeight: FontWeight.w800, letterSpacing: 1));

  Widget _avatar(AppTokens t, String name) => Container(
        width: 26,
        height: 26,
        decoration: BoxDecoration(
          color: t.primarySoft,
          borderRadius: BorderRadius.circular(AppTokens.rSm),
        ),
        alignment: Alignment.center,
        child: Text(name.isEmpty ? '食' : name.characters.first,
            style: t.textStyles.sm.copyWith(
                color: t.accent, fontWeight: FontWeight.w700)),
      );

  Widget _coverFallback(AppTokens t) => Container(
        color: t.primarySoft,
        alignment: Alignment.center,
        child: Text(
          widget.data.name.isEmpty ? '菜' : widget.data.name.characters.first,
          style: t.textStyles.h1.copyWith(color: t.primary.withValues(alpha: 0.4)),
        ),
      );
}
