import 'dart:io';

import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';

import '../../core/app_theme.dart';
import '../../services/review_service.dart';
import '../../services/upload_service.dart';

/// 写点评公共表单（单菜评价 / 食集整体评价共用，V43）。
///
/// 星级 + 文字 + 图片 + 分项打分（口味/难度/营养均衡/外观），提交时 dishId/menuId 二选一。
/// 图片处理复用 UploadService 的压缩 + 上传逻辑（与写菜谱一致）。
class ReviewForm extends StatefulWidget {
  /// 单菜评价目标（与 [menuId] 二选一）。
  final int? dishId;
  /// 食集整体评价目标（与 [dishId] 二选一）。
  final int? menuId;
  /// 评分标题（单菜评价"给这道菜打个分"；食集评价不显示）。
  final String? title;
  /// 提交成功回调（页面层负责返回/刷新）。
  final VoidCallback onSuccess;

  const ReviewForm({
    super.key,
    this.title,
    this.dishId,
    this.menuId,
    required this.onSuccess,
  }) : assert(dishId != null || menuId != null, 'dishId/menuId 至少一个');

  @override
  State<ReviewForm> createState() => _ReviewFormState();
}

class _ReviewFormState extends State<ReviewForm> {
  final _textCtrl = TextEditingController();
  int _starRating = 5;
  final List<File> _imgFiles = []; // 压缩后的本地临时文件
  List<String> _imgUrls = []; // 上传后的服务端 URL

  List<ReviewDimension> _dims = [];
  final Map<int, int> _dimScores = {};

  bool _submitting = false;

  @override
  void initState() {
    super.initState();
    _loadDims();
  }

  @override
  void dispose() {
    _textCtrl.dispose();
    _cleanupFiles();
    super.dispose();
  }

  void _cleanupFiles() {
    for (final f in _imgFiles) {
      try {
        if (f.existsSync()) f.deleteSync();
      } catch (_) {}
    }
  }

  Future<void> _loadDims() async {
    try {
      _dims = await ReviewService.dimensions();
      for (final d in _dims) {
        _dimScores[d.id] = _starRating;
      }
    } catch (_) {
    } finally {
      if (mounted) setState(() {});
    }
  }

  // ===== 图片：复用 UploadService.compress（与写菜谱一致）=====

  Future<void> _pickImages() async {
    final picker = ImagePicker();
    final xfiles = await picker.pickMultiImage(imageQuality: 100);
    if (xfiles.isEmpty) return;

    for (final xf in xfiles) {
      final original = File(xf.path);
      try {
        final compressed = await UploadService.compress(original);
        try {
          if (original.existsSync()) original.deleteSync();
        } catch (_) {}
        setState(() => _imgFiles.add(compressed));
      } catch (_) {
        setState(() => _imgFiles.add(original));
      }
    }
  }

  void _removeImage(int i) {
    setState(() {
      final f = _imgFiles.removeAt(i);
      try {
        if (f.existsSync()) f.deleteSync();
      } catch (_) {}
    });
  }

  Future<void> _uploadAndSubmit() async {
    setState(() => _submitting = true);
    try {
      _imgUrls = [];
      for (final f in _imgFiles) {
        final result = await UploadService.uploadOne(f);
        _imgUrls.add(result.url);
      }

      final dimJson = <String, int>{};
      for (final d in _dims) {
        dimJson[d.id.toString()] = _dimScores[d.id] ?? _starRating;
      }

      await ReviewService.submitReview({
        if (widget.dishId != null) 'dishId': widget.dishId,
        if (widget.menuId != null) 'menuId': widget.menuId,
        'starRating': _starRating,
        'text': _textCtrl.text.trim(),
        'images': _imgUrls,
        'dimensionScores': dimJson,
      });

      _showSnack('已点评');
      widget.onSuccess();
    } catch (e) {
      _showSnack('提交失败: $e');
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  void _showSnack(String msg) {
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(msg), duration: const Duration(seconds: 2)),
    );
  }

  // ========== UI ==========

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          // 总评星级
          Container(
            padding: const EdgeInsets.symmetric(vertical: 24, horizontal: 16),
            decoration: BoxDecoration(
              gradient: LinearGradient(
                colors: [t.primary, const Color(0xFFE6762A)],
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
              ),
              borderRadius: BorderRadius.circular(16),
            ),
            child: Column(
              children: [
                if (widget.title != null)
                  Text(widget.title!,
                      style: t.textStyles.subtitle.copyWith(color: t.card)),
                const SizedBox(height: 12),
                Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: List.generate(5, (i) {
                    return InkWell(
                      onTap: () => setState(() => _starRating = i + 1),
                      borderRadius: BorderRadius.circular(AppTokens.rSm),
                      hoverColor: t.primary.withValues(alpha: 0.08),
                      child: Padding(
                        padding: const EdgeInsets.symmetric(horizontal: 4),
                        child: Icon(
                          i < _starRating ? Icons.star : Icons.star_border,
                          size: 36,
                          color: t.card,
                        ),
                      ),
                    );
                  }),
                ),
                const SizedBox(height: 8),
                Text(_ratingHint,
                    style: t.textStyles.sm.copyWith(color: t.card.withValues(alpha: 0.7))),
              ],
            ),
          ),
          const SizedBox(height: 16),

          // 文字点评
          _sectionLabel('评价内容'),
          const SizedBox(height: 8),
          TextField(
            controller: _textCtrl,
            maxLines: 4,
            style: t.textStyles.md,
            decoration: InputDecoration(
              hintText: '味道如何？难不难？想再做一次吗？',
              filled: true,
              fillColor: t.bg,
              border: OutlineInputBorder(
                borderRadius: BorderRadius.circular(AppTokens.rMd),
                borderSide: BorderSide(color: t.border),
              ),
              focusedBorder: OutlineInputBorder(
                borderRadius: BorderRadius.circular(AppTokens.rMd),
                borderSide: BorderSide(color: t.primary, width: 1.5),
              ),
            ),
          ),
          const SizedBox(height: 16),

          // 图片
          _sectionLabel('添加图片'),
          const SizedBox(height: 8),
          _buildImageSection(),
          const SizedBox(height: 16),

          // 分项打分
          if (_dims.isNotEmpty) ...[
            _sectionLabel('评分'),
            const SizedBox(height: 8),
            _buildDimensionScores(),
            const SizedBox(height: 24),
          ],

          // 提交
          SizedBox(
            height: 48,
            child: ElevatedButton(
              onPressed: _submitting ? null : _uploadAndSubmit,
              child: _submitting
                  ? const SizedBox(
                      width: 20, height: 20,
                      child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                    )
                  : Text('提交点评', style: t.textStyles.lg.copyWith(color: Colors.white)),
            ),
          ),
          const SizedBox(height: 32),
        ],
      ),
    );
  }

  String get _ratingHint {
    const m = {1: '不太行', 2: '一般般', 3: '还可以', 4: '挺不错', 5: '想天天吃！'};
    return m[_starRating] ?? '';
  }

  Widget _sectionLabel(String text) {
    final t = AppTokens.of(context);
    return Row(children: [
      Container(
        width: 4, height: 18,
        decoration: BoxDecoration(
            color: t.primary, borderRadius: BorderRadius.circular(2)),
      ),
      const SizedBox(width: 8),
      Text(text,
          style: t.textStyles.md.copyWith(fontWeight: FontWeight.bold, color: t.title)),
    ]);
  }

  Widget _buildImageSection() {
    final t = AppTokens.of(context);
    return Wrap(
      spacing: 8,
      runSpacing: 8,
      children: [
        ..._imgFiles.asMap().entries.map((e) {
          return Stack(
            clipBehavior: Clip.none,
            children: [
              ClipRRect(
                borderRadius: BorderRadius.circular(AppTokens.rSm),
                child: Image.file(e.value, width: 80, height: 80, fit: BoxFit.cover),
              ),
              Positioned(
                top: -8, right: -8,
                child: InkWell(
                  onTap: () => _removeImage(e.key),
                  borderRadius: BorderRadius.circular(AppTokens.rPill),
                  hoverColor: t.primary.withValues(alpha: 0.08),
                  child: Container(
                    width: 22, height: 22,
                    decoration: BoxDecoration(
                      color: Colors.black54, borderRadius: BorderRadius.circular(AppTokens.rPill),
                    ),
                    child: const Icon(Icons.close, size: 12, color: Colors.white),
                  ),
                ),
              ),
            ],
          );
        }),
        if (_imgFiles.length < 6)
          InkWell(
            onTap: _pickImages,
            borderRadius: BorderRadius.circular(AppTokens.rSm),
            hoverColor: t.primary.withValues(alpha: 0.08),
            child: Container(
              width: 80, height: 80,
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(AppTokens.rSm),
                border: Border.all(color: t.border),
                color: t.bg,
              ),
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Icon(Icons.add_photo_alternate_outlined,
                      size: 24, color: t.caption),
                  Text('${_imgFiles.length}/6',
                      style: t.textStyles.sm.copyWith(color: t.caption)),
                ],
              ),
            ),
          ),
      ],
    );
  }

  Widget _buildDimensionScores() {
    final t = AppTokens.of(context);
    return Container(
      decoration: BoxDecoration(
        color: t.card,
        borderRadius: BorderRadius.circular(AppTokens.rMd),
        border: Border.all(color: t.border),
      ),
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      child: Column(
        children: _dims.map((d) {
          final score = _dimScores[d.id] ?? _starRating;
          return Padding(
            padding: const EdgeInsets.symmetric(vertical: 8),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(d.name, style: t.textStyles.md.copyWith(color: t.title)),
                Row(
                  children: List.generate(5, (i) {
                    return InkWell(
                      onTap: () => setState(() => _dimScores[d.id] = i + 1),
                      borderRadius: BorderRadius.circular(AppTokens.rSm),
                      hoverColor: t.primary.withValues(alpha: 0.08),
                      child: Icon(
                        i < score ? Icons.star : Icons.star_border,
                        size: 24,
                        color: i < score ? t.primary : t.border,
                      ),
                    );
                  }),
                ),
              ],
            ),
          );
        }).toList(),
      ),
    );
  }
}
