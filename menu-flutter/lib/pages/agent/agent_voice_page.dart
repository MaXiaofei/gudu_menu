import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../core/app_theme.dart';
import 'agent_conversation.dart';
import 'agent_widgets.dart';

/// 咕嘟 · 语音（原型 AgentVoice.html）：上半部分聆听面板，下半部分为本轮生成的确认卡（如做完回写）。
///
/// 当前未接入端侧语音识别插件（需 speech_to_text / record），降级为「按住说」位置的文字输入：
/// 面板照常显示，转写区可直接打字，发送后走同一条 Agent 流式链路。
class AgentVoicePage extends StatefulWidget {
  final AgentConversation? conversation; // 测试注入
  const AgentVoicePage({super.key, this.conversation});

  @override
  State<AgentVoicePage> createState() => _AgentVoicePageState();
}

class _AgentVoicePageState extends State<AgentVoicePage> with SingleTickerProviderStateMixin {
  late final AgentConversation _c = widget.conversation ?? AgentConversation();
  final _text = TextEditingController();
  late final AnimationController _wave =
      AnimationController(vsync: this, duration: const Duration(milliseconds: 1200));

  @override
  void initState() {
    super.initState();
    _c.addListener(_onChange);
  }

  void _onChange() {
    if (!mounted) return;
    // 只在发送时跑声波动画（避免常驻动画拖累测试与电量）
    if (_c.sending && !_wave.isAnimating) _wave.repeat();
    if (!_c.sending && _wave.isAnimating) _wave.stop();
    setState(() {});
  }

  Future<void> _send() async {
    final q = _text.text.trim();
    if (q.isEmpty) return;
    FocusScope.of(context).unfocus();
    await _c.send(q);
  }

  @override
  void dispose() {
    _c.removeListener(_onChange);
    if (widget.conversation == null) _c.dispose();
    _text.dispose();
    _wave.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    final ts = t.textStyles;
    final reply = _c.lastAssistant;
    return Scaffold(
      backgroundColor: t.bg,
      appBar: AppBar(
        backgroundColor: t.bg,
        surfaceTintColor: t.bg,
        title: Text('咕嘟 · 语音', style: ts.pageTitle),
      ),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(AppTokens.sp16, AppTokens.sp4, AppTokens.sp16, AppTokens.sp24),
        children: [
          _panel(t),
          const SizedBox(height: AppTokens.sp16),
          if (reply != null)
            AssistantMessageView(
              message: reply,
              onActionChanged: _c.updateAction,
              onRetry: reply.error != null ? () => _c.retryLast() : null,
            )
          else
            _tips(t),
          // 做完回写卡自带「改一下」（人份 / 谁吃了 / 实际用量 → revise）；其他情况继续对话修正
          if (_c.sessionId != null && reply != null && !_c.sending && !reply.actions.any((a) => a.isPending))
            OutlinedButton.icon(
              onPressed: () => context.pushReplacement('/agent/chat', extra: {'sessionId': _c.sessionId}),
              icon: const Icon(Icons.chat_bubble_outline_rounded, size: 18),
              label: const Text('继续对话'),
            ),
        ],
      ),
    );
  }

  /// 深色聆听面板：状态 → 转写 → 声波 + 麦克风/发送。
  Widget _panel(AppTokens t) {
    final ts = t.textStyles;
    final listening = _c.sending;
    return Container(
      padding: const EdgeInsets.all(AppTokens.sp16),
      decoration: BoxDecoration(
        color: t.title,
        borderRadius: BorderRadius.circular(AppTokens.rXl),
        boxShadow: t.elevationLg,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(
                width: 8,
                height: 8,
                decoration: BoxDecoration(color: listening ? AppTokens.error : t.primary, shape: BoxShape.circle),
              ),
              const SizedBox(width: AppTokens.sp6),
              Text(listening ? '咕嘟在听…' : '说一句话，或直接打字',
                  style: ts.caption.copyWith(color: AppTokens.onPhoto.withAlpha(210))),
              const Spacer(),
              Text('语音识别插件未接入 · 先用文字',
                  style: ts.meta.copyWith(color: AppTokens.onPhoto.withAlpha(170), fontWeight: FontWeight.w500)),
            ],
          ),
          const SizedBox(height: AppTokens.sp12),
          TextField(
            key: const Key('voice-text'),
            controller: _text,
            minLines: 2,
            maxLines: 4,
            enabled: !listening,
            style: ts.h3.copyWith(color: AppTokens.onPhoto),
            cursorColor: t.primary,
            textInputAction: TextInputAction.send,
            onSubmitted: (_) => _send(),
            decoration: InputDecoration(
              hintText: '例如：麻婆豆腐做完了，我们三个人吃的',
              hintStyle: ts.subtitle.copyWith(color: AppTokens.onPhoto.withAlpha(120), fontWeight: FontWeight.w500),
              border: InputBorder.none,
              enabledBorder: InputBorder.none,
              focusedBorder: InputBorder.none,
              disabledBorder: InputBorder.none,
              filled: false,
            ),
          ),
          const SizedBox(height: AppTokens.sp8),
          Row(
            children: [
              Expanded(child: SizedBox(height: 36, child: _Waveform(animation: _wave, color: t.primary))),
              const SizedBox(width: AppTokens.sp12),
              Semantics(
                button: true,
                label: '发送',
                child: InkResponse(
                  key: const Key('voice-send'),
                  onTap: listening ? null : _send,
                  child: Container(
                    width: 60,
                    height: 60,
                    decoration: BoxDecoration(
                      gradient: LinearGradient(colors: [t.primary, t.primaryDeep]),
                      shape: BoxShape.circle,
                      boxShadow: t.elevationFab,
                    ),
                    child: Icon(listening ? Icons.graphic_eq_rounded : Icons.mic_rounded, color: t.card, size: 28),
                  ),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _tips(AppTokens t) {
    final ts = t.textStyles;
    const tips = ['麻婆豆腐做完了，三个人吃的', '把鲈鱼加进今晚的食集', '鸡蛋和牛奶买回来了'];
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text('可以这样说', style: ts.sectionLabel.copyWith(color: t.body)),
        const SizedBox(height: AppTokens.sp8),
        for (final s in tips)
          Padding(
            padding: const EdgeInsets.only(bottom: AppTokens.sp8),
            child: InkWell(
              onTap: () => setState(() => _text.text = s),
              borderRadius: BorderRadius.circular(AppTokens.rMd),
              child: Container(
                padding: const EdgeInsets.all(AppTokens.sp12),
                decoration: BoxDecoration(
                  color: t.card,
                  borderRadius: BorderRadius.circular(AppTokens.rMd),
                  border: Border.all(color: t.border),
                ),
                child: Row(children: [
                  Icon(Icons.format_quote_rounded, color: t.primaryDeep, size: 18),
                  const SizedBox(width: AppTokens.sp8),
                  Expanded(child: Text(s, style: ts.body.copyWith(color: t.title))),
                ]),
              ),
            ),
          ),
        const SizedBox(height: AppTokens.sp4),
        Text('写操作（回写余量、加食集、加采购）都会先出确认卡，确认后 5 分钟内可撤销。', style: ts.caption),
      ],
    );
  }
}

/// 声波条（静止时为一排高低错落的短条）。
class _Waveform extends StatelessWidget {
  final Animation<double> animation;
  final Color color;
  const _Waveform({required this.animation, required this.color});

  @override
  Widget build(BuildContext context) => AnimatedBuilder(
        animation: animation,
        builder: (_, __) => CustomPaint(painter: _WavePainter(animation.value, color)),
      );
}

class _WavePainter extends CustomPainter {
  final double phase;
  final Color color;
  _WavePainter(this.phase, this.color);

  @override
  void paint(Canvas canvas, Size size) {
    const n = 26;
    final w = size.width / n;
    final p = Paint()
      ..color = color
      ..strokeCap = StrokeCap.round
      ..strokeWidth = math.max(2, w * 0.45);
    for (var i = 0; i < n; i++) {
      final base = 0.25 + 0.55 * (0.5 + 0.5 * math.sin(i * 1.3));
      final live = 0.5 + 0.5 * math.sin(phase * 2 * math.pi + i * 0.7);
      final h = size.height * (phase == 0 ? base * 0.6 : base * (0.4 + 0.6 * live));
      final x = w * (i + 0.5);
      canvas.drawLine(Offset(x, (size.height - h) / 2), Offset(x, (size.height + h) / 2), p);
    }
  }

  @override
  bool shouldRepaint(covariant _WavePainter old) => old.phase != phase || old.color != color;
}
