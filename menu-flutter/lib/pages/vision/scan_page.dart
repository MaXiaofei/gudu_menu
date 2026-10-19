import 'dart:io';

import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:image_picker/image_picker.dart';

import '../../core/app_theme.dart';
import '../../core/error_text.dart';
import '../../services/agent_service.dart' show isNotDeployed;
import '../../services/upload_service.dart';
import '../../services/vision_service.dart';
import '../../widgets/ingredient_icon.dart';

/// 拍照识别（原型 Scan.html）：深色取景界面 + 模式切换（冰箱 / 小票 / 菜谱截图）+ 快门 / 相册。
///
/// 实时取景需要 camera 插件（会引入原生依赖），这里用 image_picker 调系统相机拍照：
/// 取景区显示模式提示与示意，拍完在取景区展示照片并显示识别进度，完成后进入结果确认页。
class ScanPage extends StatefulWidget {
  final VisionMode initialMode;
  const ScanPage({super.key, this.initialMode = VisionMode.fridge});

  @override
  State<ScanPage> createState() => _ScanPageState();
}

class _ScanPageState extends State<ScanPage> {
  late VisionMode _mode = widget.initialMode;
  File? _photo;
  String? _stage; // 上传中 / 识别中
  String? _error;

  Future<void> _capture(ImageSource source) async {
    if (_stage != null) return;
    final x = await ImagePicker().pickImage(source: source, maxWidth: 1600);
    if (x == null || !mounted) return;
    setState(() {
      _photo = File(x.path);
      _error = null;
      _stage = '上传中';
    });
    try {
      final up = await UploadService.uploadOne(File(x.path));
      if (!mounted) return;
      setState(() => _stage = '识别中');
      final job = await VisionService.create(mode: _mode, imageUrl: up.url);
      final done = await VisionService.waitDone(job.jobId);
      if (!mounted) return;
      if (done.failed) {
        setState(() {
          _stage = null;
          _error = done.error ?? '没认出来，换个角度再拍一张';
        });
        return;
      }
      setState(() => _stage = null);
      await context.push('/scan/result', extra: {'job': done, 'photo': _photo!.path});
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _stage = null;
        _error = isNotDeployed(e) ? '拍照识别服务还没上线，先手动入库吧' : '识别失败：${errorText(e)}';
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    final ts = t.textStyles;
    final dark = t.title;
    const onDark = AppTokens.onPhoto;
    return Scaffold(
      backgroundColor: dark,
      body: SafeArea(
        child: Column(
          children: [
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: AppTokens.sp8),
              child: Row(
                children: [
                  IconButton(
                    tooltip: '关闭',
                    onPressed: () => context.pop(),
                    icon: const Icon(Icons.close_rounded, color: onDark),
                  ),
                  Expanded(child: Center(child: Text('拍照识别', style: ts.pageTitle.copyWith(color: onDark)))),
                  const SizedBox(width: 48),
                ],
              ),
            ),
            Expanded(
              child: Padding(
                padding: const EdgeInsets.fromLTRB(AppTokens.sp16, AppTokens.sp8, AppTokens.sp16, AppTokens.sp12),
                child: ClipRRect(
                  borderRadius: BorderRadius.circular(AppTokens.rXl),
                  child: Stack(
                    fit: StackFit.expand,
                    children: [
                      _photo != null ? Image.file(_photo!, fit: BoxFit.cover) : _placeholder(t),
                      const _Corners(),
                      Positioned(
                        top: AppTokens.sp16,
                        left: 0,
                        right: 0,
                        child: Center(
                          child: Container(
                            padding: const EdgeInsets.symmetric(horizontal: AppTokens.sp12, vertical: AppTokens.sp6),
                            decoration: BoxDecoration(color: AppTokens.photoScrim, borderRadius: BorderRadius.circular(AppTokens.rPill)),
                            child: Text(_mode.hint, style: ts.caption.copyWith(color: onDark)),
                          ),
                        ),
                      ),
                      if (_stage != null || _error != null)
                        Positioned(
                          left: AppTokens.sp12,
                          right: AppTokens.sp12,
                          bottom: AppTokens.sp12,
                          child: _statusBar(t),
                        ),
                    ],
                  ),
                ),
              ),
            ),
            _modeSwitch(t),
            const SizedBox(height: AppTokens.sp16),
            Padding(
              padding: const EdgeInsets.fromLTRB(AppTokens.sp32, 0, AppTokens.sp32, AppTokens.sp24),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  _roundAction(t, Icons.photo_library_outlined, '相册', () => _capture(ImageSource.gallery)),
                  Semantics(
                    button: true,
                    label: '拍照',
                    child: InkResponse(
                      key: const Key('scan-shutter'),
                      onTap: () => _capture(ImageSource.camera),
                      child: Container(
                        width: 76,
                        height: 76,
                        decoration: BoxDecoration(
                          shape: BoxShape.circle,
                          border: Border.all(color: onDark, width: 4),
                        ),
                        padding: const EdgeInsets.all(AppTokens.sp4),
                        child: Container(
                          decoration: BoxDecoration(
                            shape: BoxShape.circle,
                            color: _stage != null ? t.primary : onDark,
                          ),
                        ),
                      ),
                    ),
                  ),
                  _roundAction(t, Icons.edit_note_rounded, '手动', () => context.pushReplacement('/pantry/add')),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _placeholder(AppTokens t) {
    final ts = t.textStyles;
    final keys = switch (_mode) {
      VisionMode.fridge => const ['milk', 'egg', 'tomato', 'broccoli', 'carrot', 'tofu'],
      VisionMode.receipt => const ['rice', 'oil', 'sauce', 'egg'],
      VisionMode.recipe => const ['fish', 'ginger', 'scallion', 'sauce'],
    };
    return Container(
      color: t.body,
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Wrap(
            alignment: WrapAlignment.center,
            spacing: AppTokens.sp12,
            runSpacing: AppTokens.sp12,
            children: [
              for (final k in keys)
                Opacity(opacity: 0.85, child: IngredientIcon(name: k, iconKey: k, size: 56, background: t.bg.withAlpha(40))),
            ],
          ),
          const SizedBox(height: AppTokens.sp16),
          Text('点下方快门，用系统相机拍一张', style: ts.caption.copyWith(color: AppTokens.onPhoto.withAlpha(200))),
        ],
      ),
    );
  }

  Widget _statusBar(AppTokens t) {
    final ts = t.textStyles;
    final err = _error != null;
    return Container(
      padding: const EdgeInsets.all(AppTokens.sp12),
      decoration: BoxDecoration(
        color: err ? AppTokens.errorBg : AppTokens.photoScrimStrong,
        borderRadius: BorderRadius.circular(AppTokens.rMd),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(err ? _error! : '$_stage · 一般 3–5 秒',
              style: ts.caption.copyWith(color: err ? AppTokens.errorText : AppTokens.onPhoto, fontWeight: FontWeight.w600)),
          if (!err) ...[
            const SizedBox(height: AppTokens.sp8),
            ClipRRect(
              borderRadius: BorderRadius.circular(AppTokens.r2),
              child: LinearProgressIndicator(minHeight: 4, color: t.primary, backgroundColor: AppTokens.onPhoto.withAlpha(50)),
            ),
          ],
        ],
      ),
    );
  }

  Widget _modeSwitch(AppTokens t) {
    final ts = t.textStyles;
    return Row(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        for (final m in VisionMode.values)
          InkWell(
            key: Key('scan-mode-${m.name}'),
            onTap: _stage != null ? null : () => setState(() => _mode = m),
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: AppTokens.sp12, vertical: AppTokens.sp6),
              child: Column(children: [
                Text(m.label,
                    style: ts.body.copyWith(
                      color: m == _mode ? t.primary : AppTokens.onPhoto.withAlpha(190),
                      fontWeight: m == _mode ? FontWeight.w700 : FontWeight.w400,
                    )),
                const SizedBox(height: AppTokens.sp4),
                Container(
                  width: 5,
                  height: 5,
                  decoration: BoxDecoration(color: m == _mode ? t.primary : Colors.transparent, shape: BoxShape.circle),
                ),
              ]),
            ),
          ),
      ],
    );
  }

  Widget _roundAction(AppTokens t, IconData icon, String label, VoidCallback onTap) => Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          InkResponse(
            onTap: onTap,
            child: Container(
              width: 48,
              height: 48,
              decoration: BoxDecoration(color: AppTokens.onPhoto.withAlpha(36), shape: BoxShape.circle),
              child: Icon(icon, color: AppTokens.onPhoto),
            ),
          ),
          const SizedBox(height: AppTokens.sp4),
          Text(label, style: t.textStyles.caption.copyWith(color: AppTokens.onPhoto.withAlpha(200))),
        ],
      );
}

/// 取景框四角。
class _Corners extends StatelessWidget {
  const _Corners();

  @override
  Widget build(BuildContext context) => IgnorePointer(child: CustomPaint(painter: _CornerPainter(AppTokens.onPhoto)));
}

class _CornerPainter extends CustomPainter {
  final Color color;
  _CornerPainter(this.color);

  @override
  void paint(Canvas canvas, Size size) {
    const inset = 18.0, len = 28.0;
    final p = Paint()
      ..color = color
      ..strokeWidth = 3
      ..style = PaintingStyle.stroke
      ..strokeCap = StrokeCap.round;
    final w = size.width, h = size.height;
    for (final c in [
      [const Offset(inset, inset + len), const Offset(inset, inset), const Offset(inset + len, inset)],
      [Offset(w - inset - len, inset), Offset(w - inset, inset), Offset(w - inset, inset + len)],
      [Offset(inset, h - inset - len), Offset(inset, h - inset), Offset(inset + len, h - inset)],
      [Offset(w - inset - len, h - inset), Offset(w - inset, h - inset), Offset(w - inset, h - inset - len)],
    ]) {
      canvas.drawPath(Path()..moveTo(c[0].dx, c[0].dy)..lineTo(c[1].dx, c[1].dy)..lineTo(c[2].dx, c[2].dy), p);
    }
  }

  @override
  bool shouldRepaint(covariant _CornerPainter old) => old.color != color;
}
