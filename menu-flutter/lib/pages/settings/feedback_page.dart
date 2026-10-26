import 'dart:io';

import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';

import '../../core/ai_label.dart';
import '../../core/api_client.dart';
import '../../core/app_theme.dart';
import '../../core/constants.dart';
import '../../core/error_text.dart';
import '../../services/feedback_service.dart';
import '../../services/upload_service.dart';
import '../../widgets/action_bar.dart';

/// 意见反馈（G14）：内容 5–500 字 + 联系方式（可选）+ 最多 3 张截图 → POST /feedback。
class FeedbackPage extends StatefulWidget {
  const FeedbackPage({super.key, this.aiSource, this.aiRef});

  /// 从 AI 内容旁的「反馈」进入（A.4）：追加并默认选中「AI 内容」类型，来源标记写进 clientInfo。
  final AiSource? aiSource;
  final String? aiRef;

  @override
  State<FeedbackPage> createState() => _FeedbackPageState();
}

class _FeedbackPageState extends State<FeedbackPage> {
  final _content = TextEditingController();
  final _contact = TextEditingController();
  final List<File> _images = [];
  bool _submitting = false;

  /// 反馈类型（原型 Feedback 四宫格；后端 type 字段上线前随 clientInfo 带上）。
  static const _baseTypes = [
    ('出错了', Icons.error_outline_rounded, 'bug'),
    ('想要功能', Icons.lightbulb_outline_rounded, 'feature'),
    ('菜谱有误', Icons.menu_book_outlined, 'recipe'),
    ('其他', Icons.more_horiz_rounded, 'other'),
  ];
  static const _aiType = ('AI 内容', Icons.auto_awesome_outlined, 'ai');
  List<(String, IconData, String)> get _types =>
      widget.aiSource == null ? _baseTypes : [_aiType, ..._baseTypes];
  int _type = 0;

  /// 附带设备型号、App 版本和最近一次报错编号（默认开）。
  bool _attachDiag = true;

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
        type: _types[_type].$3,
        errorId: _attachDiag && widget.aiSource == null ? ApiClient.lastErrorId : null,
        clientInfo: [
          if (widget.aiSource != null) aiSourceTag(widget.aiSource!, widget.aiRef),
          'type=${_types[_type].$3}',
          if (_attachDiag) 'flutter/${Platform.operatingSystem} v${AppConstants.appVersion}',
          if (_attachDiag && widget.aiSource == null && ApiClient.lastErrorId != null)
            'errorId=${ApiClient.lastErrorId}',
        ].join(' · '),
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
                  Text('是关于什么的', style: ts.sectionLabel),
                  const SizedBox(height: AppTokens.sp8),
                  Row(children: [
                    for (var i = 0; i < _types.length; i++) ...[
                      if (i > 0) const SizedBox(width: AppTokens.sp8),
                      Expanded(
                        child: GestureDetector(
                          onTap: () => setState(() => _type = i),
                          child: Container(
                            height: 72,
                            decoration: BoxDecoration(
                              color: _type == i ? t.secondary : t.card,
                              border: Border.all(color: _type == i ? t.primaryDeep : t.border, width: _type == i ? 1.5 : 1),
                              borderRadius: BorderRadius.circular(AppTokens.rMd),
                            ),
                            child: Column(mainAxisAlignment: MainAxisAlignment.center, children: [
                              Icon(_types[i].$2, color: _type == i ? t.primaryDeep : t.body),
                              const SizedBox(height: AppTokens.sp4),
                              Text(_types[i].$1,
                                  style: ts.caption.copyWith(
                                      color: _type == i ? t.title : t.body,
                                      fontWeight: _type == i ? FontWeight.w700 : FontWeight.w400)),
                            ]),
                          ),
                        ),
                      ),
                    ],
                  ]),
                  const SizedBox(height: AppTokens.sp16),
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
                              borderRadius: BorderRadius.circular(AppTokens.rMd),
                            ),
                            child: Icon(Icons.add_photo_alternate_outlined, color: t.body),
                          ),
                        ),
                    ],
                  ),
                  const SizedBox(height: AppTokens.sp16),
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: AppTokens.sp12, vertical: AppTokens.sp4),
                    decoration: BoxDecoration(
                      color: t.card,
                      border: Border.all(color: t.border),
                      borderRadius: BorderRadius.circular(AppTokens.rMd),
                    ),
                    child: Row(children: [
                      Expanded(
                        child: Text(
                          ApiClient.lastErrorId == null
                              ? '附带设备型号和 App 版本，帮我们更快定位'
                              : '附带设备型号、App 版本和报错编号 ${ApiClient.lastErrorId}',
                          style: ts.caption,
                        ),
                      ),
                      Switch(
                        key: const Key('feedback-attach'),
                        value: _attachDiag,
                        activeTrackColor: t.primaryDeep,
                        onChanged: (v) => setState(() => _attachDiag = v),
                      ),
                    ]),
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
