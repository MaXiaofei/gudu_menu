import 'dart:async';
import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../core/app_theme.dart';
import '../../core/feature_switch.dart';
import '../../core/step_timers.dart';
import '../../models/dish.dart';
import '../../services/agent_service.dart';
import '../../services/dish_service.dart';
import '../../widgets/dish_image.dart';
import '../../widgets/error_view.dart';
import '../agent/agent_conversation.dart';
import '../agent/agent_widgets.dart';

/// 一个正在跑的计时器（多个并行）。
class RunningTimer {
  final String id;
  final String label;
  final int total;
  int left;
  bool paused = false;
  RunningTimer({required this.id, required this.label, required this.total}) : left = total;
  bool get done => left <= 0;
}

/// 跟做模式（原型 CookMode.html）：沉浸式大字步骤 + 步骤图；步骤里的时长自动变计时器，
/// 多个计时器并行（底部条），到点震动 + 提示音；可在当前步骤里「问咕嘟」（带菜谱与步骤上下文）。
/// 最后一步「做好了」返回 true，由菜谱详情接着打开做完确认（回写余量）。
///
/// 屏幕常亮需要 wakelock_plus 插件，未引入；语音「下一步」需要 speech_to_text 插件，未引入。
class CookModePage extends StatefulWidget {
  final int dishId;
  final DishDetail? detail; // 测试注入
  const CookModePage({super.key, required this.dishId, this.detail});

  @override
  State<CookModePage> createState() => _CookModePageState();
}

class _CookModePageState extends State<CookModePage> {
  DishDetail? _d;
  Object? _error;
  int _step = 0;
  final List<RunningTimer> _timers = [];
  Timer? _ticker;
  final _ask = TextEditingController();
  AgentConversation? _conv;

  @override
  void initState() {
    super.initState();
    _d = widget.detail;
    FeatureSwitch.load();
    if (_d == null) _load();
  }

  Future<void> _load() async {
    try {
      final d = await DishService.detail(widget.dishId);
      if (mounted) setState(() => _d = d);
    } catch (e) {
      if (mounted) setState(() => _error = e);
    }
  }

  @override
  void dispose() {
    _ticker?.cancel();
    _conv?.dispose();
    _ask.dispose();
    super.dispose();
  }

  void _startTimer(StepTimer st) {
    final id = '$_step-${st.text}';
    if (_timers.any((e) => e.id == id && !e.done)) return;
    setState(() => _timers.add(RunningTimer(id: id, label: st.action.isEmpty ? '第 ${_step + 1} 步' : st.action, total: st.seconds)));
    _ticker ??= Timer.periodic(const Duration(seconds: 1), (_) => _tick());
  }

  void _tick() {
    if (!mounted) return;
    var anyRunning = false;
    final finished = <RunningTimer>[];
    for (final t in _timers) {
      if (t.done || t.paused) continue;
      t.left--;
      if (t.done) {
        finished.add(t);
      } else {
        anyRunning = true;
      }
    }
    for (final f in finished) {
      HapticFeedback.heavyImpact();
      SystemSound.play(SystemSoundType.alert);
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('${f.label} 到点了')));
    }
    if (!anyRunning) {
      _ticker?.cancel();
      _ticker = null;
    }
    setState(() {});
  }

  void _go(int delta) {
    final n = _d?.steps.length ?? 0;
    final next = _step + delta;
    if (next < 0) return;
    if (next >= n) {
      Navigator.of(context).pop(true); // 做好了 → 详情页打开做完确认
      return;
    }
    setState(() => _step = next);
  }

  Future<void> _askGudu() async {
    final q = _ask.text.trim();
    if (q.isEmpty) return;
    _ask.clear();
    FocusScope.of(context).unfocus();
    var c = _conv;
    if (c == null) {
      c = _conv = AgentConversation();
      c.addListener(() {
        if (mounted) setState(() {});
      });
    }
    await c.send(q, context: AgentContext(page: 'cook_mode', dishId: widget.dishId, step: _step + 1));
  }

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    final d = _d;
    return AnnotatedRegion<SystemUiOverlayStyle>(
      value: SystemUiOverlayStyle.light,
      child: Scaffold(
        backgroundColor: t.title,
        body: SafeArea(
          child: d == null
              ? (_error != null
                  ? ErrorView(text: '菜谱加载失败', onRetry: _load, error: _error)
                  : const Center(child: SizedBox.shrink()))
              : d.steps.isEmpty
                  ? _noSteps(t)
                  : _content(t, d),
        ),
      ),
    );
  }

  Widget _noSteps(AppTokens t) => Center(
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          Text('这道菜还没有步骤', style: t.textStyles.h3.copyWith(color: AppTokens.onPhoto)),
          const SizedBox(height: AppTokens.sp12),
          OutlinedButton(onPressed: () => Navigator.of(context).maybePop(), child: const Text('返回')),
        ]),
      );

  Widget _content(AppTokens t, DishDetail d) {
    final ts = t.textStyles;
    final steps = d.steps;
    final st = steps[_step];
    final timers = parseStepTimers(st.text);
    final img = st.imageList.isNotEmpty ? st.imageList.first : null;
    final remainMin = steps.skip(_step).fold<int>(0, (n, s) => n + parseStepTimers(s.text).fold<int>(0, (a, b) => a + b.seconds)) ~/ 60;
    const onDark = AppTokens.onPhoto;
    final last = _step == steps.length - 1;
    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(AppTokens.sp8, AppTokens.sp8, AppTokens.sp16, 0),
          child: Row(
            children: [
              IconButton(
                tooltip: '退出跟做',
                onPressed: () => Navigator.of(context).maybePop(),
                icon: const Icon(Icons.close_rounded, color: onDark),
              ),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(d.dish.name, style: ts.lg.copyWith(color: onDark)),
                    Text('第 ${_step + 1} / ${steps.length} 步${remainMin > 0 ? ' · 还剩约 $remainMin 分钟' : ''}',
                        style: ts.caption.copyWith(color: onDark.withAlpha(190))),
                  ],
                ),
              ),
            ],
          ),
        ),
        Padding(
          padding: const EdgeInsets.fromLTRB(AppTokens.sp16, AppTokens.sp10, AppTokens.sp16, 0),
          child: Row(children: [
            for (var i = 0; i < steps.length; i++)
              Expanded(
                child: Container(
                  height: 4,
                  margin: EdgeInsets.only(right: i == steps.length - 1 ? 0 : 4),
                  decoration: BoxDecoration(
                    color: i <= _step ? t.primary : onDark.withAlpha(40),
                    borderRadius: BorderRadius.circular(AppTokens.r2),
                  ),
                ),
              ),
          ]),
        ),
        Expanded(
          child: ListView(
            padding: const EdgeInsets.fromLTRB(AppTokens.sp16, AppTokens.sp12, AppTokens.sp16, AppTokens.sp16),
            children: [
              if (img != null)
                Stack(children: [
                  DishImage(url: img, name: d.dish.name, width: double.infinity, height: 170, radius: AppTokens.rLg, original: true),
                  Positioned(
                    left: AppTokens.sp10,
                    top: AppTokens.sp10,
                    child: Container(
                      padding: const EdgeInsets.symmetric(horizontal: AppTokens.sp8, vertical: AppTokens.sp4),
                      decoration: BoxDecoration(color: AppTokens.photoScrim, borderRadius: BorderRadius.circular(AppTokens.rSm)),
                      child: Text('STEP ${_step + 1}', style: ts.xs.copyWith(color: onDark)),
                    ),
                  ),
                ]),
              const SizedBox(height: AppTokens.sp16),
              _stepText(t, st.text, timers),
              const SizedBox(height: AppTokens.sp12),
              Wrap(
                spacing: AppTokens.sp10,
                runSpacing: AppTokens.sp8,
                crossAxisAlignment: WrapCrossAlignment.center,
                children: [
                  for (final tm in timers)
                    ElevatedButton.icon(
                      key: Key('cook-timer-${tm.seconds}'),
                      style: ElevatedButton.styleFrom(minimumSize: const Size(0, 44), shape: const StadiumBorder()),
                      onPressed: () => _startTimer(tm),
                      icon: const Icon(Icons.play_arrow_rounded),
                      label: Text(tm.label),
                    ),
                  if (!last)
                    Text('下一步：${_short(steps[_step + 1].text)}', style: ts.caption.copyWith(color: onDark.withAlpha(190))),
                ],
              ),
              const SizedBox(height: AppTokens.sp16),
              // 问咕嘟紧急关停时隐藏提问区，跟做模式其余功能照常
              ValueListenableBuilder<bool>(
                valueListenable: FeatureSwitch.cookAsk,
                builder: (_, on, __) => on ? _askPanel(t) : const SizedBox.shrink(),
              ),
            ],
          ),
        ),
        if (_timers.isNotEmpty) _timerBar(t),
        Padding(
          padding: const EdgeInsets.fromLTRB(AppTokens.sp16, AppTokens.sp8, AppTokens.sp16, AppTokens.sp12),
          child: Row(
            children: [
              Expanded(
                flex: 2,
                child: OutlinedButton.icon(
                  key: const Key('cook-prev'),
                  style: OutlinedButton.styleFrom(
                    foregroundColor: onDark,
                    side: BorderSide(color: onDark.withAlpha(80)),
                    minimumSize: const Size(0, 52),
                  ),
                  onPressed: _step == 0 ? null : () => _go(-1),
                  icon: const Icon(Icons.chevron_left_rounded),
                  label: const Text('上一步'),
                ),
              ),
              const SizedBox(width: AppTokens.sp12),
              Expanded(
                flex: 3,
                child: ElevatedButton.icon(
                  key: const Key('cook-next'),
                  style: ElevatedButton.styleFrom(minimumSize: const Size(0, 52)),
                  onPressed: () => _go(1),
                  icon: Icon(last ? Icons.check_rounded : Icons.chevron_right_rounded),
                  label: Text(last ? '做好了' : '下一步'),
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }

  String _short(String s) => s.length > 16 ? '${s.substring(0, 16)}…' : s;

  /// 大字步骤，时长片段高亮。
  Widget _stepText(AppTokens t, String text, List<StepTimer> timers) {
    final style = t.textStyles.h3.copyWith(color: AppTokens.onPhoto, height: 1.5);
    final spans = <TextSpan>[];
    var i = 0;
    for (final tm in timers) {
      final at = text.indexOf(tm.text, i);
      if (at < 0) continue;
      if (at > i) spans.add(TextSpan(text: text.substring(i, at)));
      spans.add(TextSpan(text: tm.text, style: TextStyle(color: t.primary)));
      i = at + tm.text.length;
    }
    if (i < text.length) spans.add(TextSpan(text: text.substring(i)));
    return Text.rich(TextSpan(children: spans), style: style);
  }

  Widget _askPanel(AppTokens t) {
    final ts = t.textStyles;
    const onDark = AppTokens.onPhoto;
    final msgs = _conv?.messages ?? const <AgentMessage>[];
    return Container(
      padding: const EdgeInsets.all(AppTokens.sp12),
      decoration: BoxDecoration(
        color: onDark.withAlpha(18),
        borderRadius: BorderRadius.circular(AppTokens.rLg),
        border: Border.all(color: onDark.withAlpha(30)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text('问咕嘟 · 带着这一步的上下文', style: ts.xs.copyWith(color: onDark.withAlpha(190))),
          for (final m in msgs.length > 2 ? msgs.sublist(msgs.length - 2) : msgs) ...[
            const SizedBox(height: AppTokens.sp8),
            if (m.isUser)
              Align(
                alignment: Alignment.centerRight,
                child: Container(
                  padding: const EdgeInsets.symmetric(horizontal: AppTokens.sp10, vertical: AppTokens.sp6),
                  decoration: BoxDecoration(color: t.secondary, borderRadius: BorderRadius.circular(AppTokens.rMd)),
                  child: Text(m.text, style: ts.body.copyWith(color: t.title)),
                ),
              )
            else
              Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                const GuduAvatar(size: 26),
                const SizedBox(width: AppTokens.sp8),
                Expanded(
                  child: Container(
                    padding: const EdgeInsets.symmetric(horizontal: AppTokens.sp10, vertical: AppTokens.sp6),
                    decoration: BoxDecoration(color: onDark.withAlpha(26), borderRadius: BorderRadius.circular(AppTokens.rMd)),
                    child: Text(
                      m.error ?? (m.text.isEmpty && m.streaming ? '咕嘟正在想…' : m.text),
                      style: ts.body.copyWith(color: m.error != null ? AppTokens.errorBg : onDark, height: 1.5),
                    ),
                  ),
                ),
              ]),
          ],
          const SizedBox(height: AppTokens.sp8),
          TextField(
            key: const Key('cook-ask'),
            controller: _ask,
            enabled: !(_conv?.sending ?? false),
            textInputAction: TextInputAction.send,
            onSubmitted: (_) => _askGudu(),
            style: ts.body.copyWith(color: onDark),
            cursorColor: t.primary,
            decoration: InputDecoration(
              isDense: true,
              hintText: '比如：没有郫县豆瓣酱用什么代替？',
              hintStyle: ts.caption.copyWith(color: onDark.withAlpha(140)),
              filled: true,
              fillColor: onDark.withAlpha(20),
              border: OutlineInputBorder(borderRadius: BorderRadius.circular(AppTokens.rPill), borderSide: BorderSide.none),
              enabledBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(AppTokens.rPill), borderSide: BorderSide.none),
              focusedBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(AppTokens.rPill), borderSide: BorderSide(color: t.primary)),
              suffixIcon: IconButton(
                tooltip: '发送',
                onPressed: _askGudu,
                icon: Icon(Icons.arrow_upward_rounded, color: t.primary),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _timerBar(AppTokens t) {
    final ts = t.textStyles;
    const onDark = AppTokens.onPhoto;
    return SizedBox(
      height: 64,
      child: ListView.separated(
        padding: const EdgeInsets.symmetric(horizontal: AppTokens.sp16),
        scrollDirection: Axis.horizontal,
        itemCount: _timers.length,
        separatorBuilder: (_, __) => const SizedBox(width: AppTokens.sp8),
        itemBuilder: (_, i) {
          final tm = _timers[i];
          final hot = !tm.done && tm.left <= 60;
          final color = tm.done ? AppTokens.success : hot ? AppTokens.warning : t.primary;
          return InkWell(
            borderRadius: BorderRadius.circular(AppTokens.rMd),
            onTap: () => setState(() {
              if (tm.done) {
                _timers.removeAt(i);
              } else {
                tm.paused = !tm.paused;
                if (!tm.paused) _ticker ??= Timer.periodic(const Duration(seconds: 1), (_) => _tick());
              }
            }),
            child: Container(
              width: 160,
              padding: const EdgeInsets.symmetric(horizontal: AppTokens.sp10),
              decoration: BoxDecoration(
                color: hot ? AppTokens.warning.withAlpha(40) : onDark.withAlpha(18),
                borderRadius: BorderRadius.circular(AppTokens.rMd),
                border: Border.all(color: hot ? AppTokens.warning : onDark.withAlpha(25)),
              ),
              child: Row(children: [
                SizedBox(
                  width: 32,
                  height: 32,
                  child: CustomPaint(painter: _RingPainter(1 - tm.left / tm.total, color, onDark.withAlpha(40))),
                ),
                const SizedBox(width: AppTokens.sp8),
                Expanded(
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(tm.done ? '到点了' : formatClock(tm.left),
                          style: ts.lg.copyWith(color: hot ? AppTokens.warning : onDark, fontFeatures: const [FontFeature.tabularFigures()])),
                      Text('${tm.label}${tm.paused ? ' · 暂停' : tm.done ? ' · 点一下移除' : ''}',
                          maxLines: 1, overflow: TextOverflow.ellipsis, style: ts.meta.copyWith(color: onDark.withAlpha(190))),
                    ],
                  ),
                ),
              ]),
            ),
          );
        },
      ),
    );
  }
}

class _RingPainter extends CustomPainter {
  final double progress;
  final Color color;
  final Color track;
  _RingPainter(this.progress, this.color, this.track);

  @override
  void paint(Canvas canvas, Size size) {
    final r = Rect.fromLTWH(2, 2, size.width - 4, size.height - 4);
    final base = Paint()
      ..color = track
      ..style = PaintingStyle.stroke
      ..strokeWidth = 4;
    canvas.drawArc(r, 0, 2 * math.pi, false, base);
    canvas.drawArc(r, -math.pi / 2, 2 * math.pi * progress.clamp(0.0, 1.0), false,
        base..color = color..strokeCap = StrokeCap.round);
  }

  @override
  bool shouldRepaint(covariant _RingPainter old) => old.progress != progress || old.color != color;
}
