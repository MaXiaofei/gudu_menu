import 'dart:io';

import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';

import '../../core/app_theme.dart';
import '../../core/constants.dart';
import '../../core/error_text.dart';
import '../../services/feedback_service.dart';
import '../../services/upload_service.dart';
import '../../widgets/action_bar.dart';

/// 意见反馈（G14）：内容 5–500 字 + 联系方式（可选）+ 最多 3 张截图 → POST /feedback。
class FeedbackPage extends StatefulWidget {
  const FeedbackPage({super.key});

  @override
  State<FeedbackPage> createState() => _FeedbackPageState();
}

class _FeedbackPageState extends State<FeedbackPage> {
  final _content = TextEditingController();
  final _contact = TextEditingController();
  final List<File> _images = [];
  bool _submitting = false;

  @override
  void dispose() {
    _content.dispose();
    _contact.dispose();
    super.dispose();
  }

  void _snack(String msg) {
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(msg)));
  }

  Future<void> _pickImages() async {
    final remain = FeedbackService.maxImages - _images.length;
    if (remain <= 0) return;
    try {
      final xfiles = await ImagePicker().pickMultiImage(imageQuality: 100);
      if (!mounted || xfiles.isEmpty) return;
      setState(() => _images.addAll(xfiles.take(remain).map((x) => File(x.path))));
      if (xfiles.length > remain) _snack('最多 ${FeedbackService.maxImages} 张截图');
    } catch (e) {
      if (mounted) _snack('选图失败：${errorText(e, '请检查相册权限')}');
    }
  }

  Future<void> _submit() async {
    final content = _content.text.trim();
    if (content.length < FeedbackService.minContent) {
      _snack('请至少写 ${FeedbackService.minContent} 个字，方便我们定位问题');
      return;
    }
    setState(() => _submitting = true);
    try {
      final urls = <String>[];
      for (final f in _images) {
        final r = await UploadService.uploadOne(f);
        urls.add(r.url);
      }
      await FeedbackService.submit(
        content: content,
        contact: _contact.text.trim(),
        images: urls,
        clientInfo: 'flutter/${Platform.operatingSystem} v${AppConstants.appVersion}',
      );
      if (!mounted) return;
      _snack('感谢反馈，我们会尽快处理');
      Navigator.of(context).pop();
    } catch (e) {
      if (mounted) _snack('提交失败：${errorText(e)}');
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    final ts = t.textStyles;
    return Scaffold(
      backgroundColor: t.bg,
      body: SafeArea(
        bottom: false,
        child: Column(
          children: [
            const BackHeader(title: '意见反馈'),
            Expanded(
              child: ListView(
                padding: const EdgeInsets.all(AppTokens.sp16),
                children: [
                  Text('遇到问题或有想法，告诉我们', style: ts.caption.copyWith(color: t.caption)),
                  const SizedBox(height: AppTokens.sp12),
                  TextField(
                    key: const Key('feedback-content'),
                    controller: _content,
                    maxLines: 6,
                    maxLength: FeedbackService.maxContent,
                    style: ts.input,
                    decoration: const InputDecoration(hintText: '描述一下问题或建议（至少 5 个字）'),
                  ),
                  const SizedBox(height: AppTokens.sp8),
                  TextField(
                    key: const Key('feedback-contact'),
                    controller: _contact,
                    maxLength: 50,
                    style: ts.input,
                    decoration: const InputDecoration(hintText: '联系方式（选填：手机号 / 微信 / 邮箱）'),
                  ),
                  const SizedBox(height: AppTokens.sp8),
                  Text('截图（选填，最多 ${FeedbackService.maxImages} 张）', style: ts.sectionLabel),
                  const SizedBox(height: AppTokens.sp8),
                  Wrap(
                    spacing: AppTokens.sp8,
                    runSpacing: AppTokens.sp8,
                    children: [
                      for (var i = 0; i < _images.length; i++) _thumb(t, i),
                      if (_images.length < FeedbackService.maxImages)
                        GestureDetector(
                          onTap: _submitting ? null : _pickImages,
                          child: Container(
                            width: 80,
                            height: 80,
                            decoration: BoxDecoration(
                              color: t.card,
                              border: Border.all(color: t.border),
                              borderRadius: BorderRadius.circular(AppTokens.rSm),
                            ),
                            child: Icon(Icons.add_photo_alternate_outlined, color: t.caption),
                          ),
                        ),
                    ],
                  ),
                ],
              ),
            ),
            SafeArea(
              child: Padding(
                padding: const EdgeInsets.fromLTRB(
                    AppTokens.sp16, AppTokens.sp8, AppTokens.sp16, AppTokens.sp12),
                child: ElevatedButton(
                  onPressed: _submitting ? null : _submit,
                  child: Text(_submitting ? '提交中…' : '提交'),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _thumb(AppTokens t, int i) {
    return Stack(
      children: [
        ClipRRect(
          borderRadius: BorderRadius.circular(AppTokens.rSm),
          child: Image.file(_images[i], width: 80, height: 80, fit: BoxFit.cover),
        ),
        Positioned(
          right: 0,
          top: 0,
          child: GestureDetector(
            onTap: () => setState(() => _images.removeAt(i)),
            child: Container(
              decoration: BoxDecoration(color: t.title.withAlpha(150), shape: BoxShape.circle),
              child: Icon(Icons.close, size: 16, color: t.card),
            ),
          ),
        ),
      ],
    );
  }
}
