import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../core/app_theme.dart';
import '../../core/feature_switch.dart';
import '../../services/agent_service.dart';
import '../../widgets/ai_badge.dart';
import 'agent_conversation.dart';
import 'agent_widgets.dart';

/// 咕嘟对话页（原型 AgentChat.html）：工具调用链折叠、菜谱卡横滑、确认卡、撤销、评价、流式光标。
///
/// 入口：首屏快捷提问（[initialText]）、最近会话（[sessionId]）、输入栏。
class AgentChatPage extends StatefulWidget {
  final String? initialText;
  final String? sessionId;
  final AgentConversation? conversation; // 测试注入

  const AgentChatPage({super.key, this.initialText, this.sessionId, this.conversation});

  @override
  State<AgentChatPage> createState() => _AgentChatPageState();
}

class _AgentChatPageState extends State<AgentChatPage> {
  late final AgentConversation _c = widget.conversation ?? AgentConversation(sessionId: widget.sessionId);
  final _input = TextEditingController();
  final _scroll = ScrollController();

  @override
  void initState() {
    super.initState();
    _c.addListener(_onChange);
    FeatureSwitch.load();
    if (widget.sessionId != null) _c.loadHistory();
    final q = widget.initialText;
    if (q != null && q.trim().isNotEmpty) {
      WidgetsBinding.instance.addPostFrameCallback((_) => _send(q));
    }
  }

  void _onChange() {
    if (!mounted) return;
    setState(() {});
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (_scroll.hasClients) {
        _scroll.animateTo(_scroll.position.maxScrollExtent,
            duration: const Duration(milliseconds: 200), curve: Curves.easeOut);
      }
    });
  }

  Future<void> _send(String text) async {
    if (text.trim().isEmpty) return;
    _input.clear();
    await _c.send(text);
  }

  void _newSession() {
    if (_c.sending) _c.stop();
    context.pushReplacement('/agent/chat');
  }

  @override
  void dispose() {
    _c.removeListener(_onChange);
    if (widget.conversation == null) _c.dispose();
    _input.dispose();
    _scroll.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    final ts = t.textStyles;
    final follow = followUpsFor(_c.lastAssistant);
    return Scaffold(
      backgroundColor: t.bg,
      appBar: AppBar(
        backgroundColor: t.bg,
        surfaceTintColor: t.bg,
        titleSpacing: 0,
        title: Row(
          children: [
            const GuduAvatar(size: 32),
            const SizedBox(width: AppTokens.sp8),
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('咕嘟', style: ts.pageTitle),
                Row(children: [
                  Container(
                    width: 6,
                    height: 6,
                    decoration: const BoxDecoration(color: AppTokens.success, shape: BoxShape.circle),
                  ),
                  const SizedBox(width: AppTokens.sp4),
                  Text(_c.sending ? '正在回复…' : '已连接你家的厨房', style: ts.meta.copyWith(color: t.body)),
                ]),
              ],
            ),
          ],
        ),
        actions: [
          if (_c.sending)
            TextButton(onPressed: _c.stop, child: const Text('停止'))
          else
            IconButton(tooltip: '新对话', onPressed: _newSession, icon: const Icon(Icons.add_comment_outlined)),
        ],
      ),
      body: Column(
        children: [
          Expanded(
            child: _c.messages.isEmpty
                ? _empty(t)
                : ListView.builder(
                    controller: _scroll,
                    padding: const EdgeInsets.fromLTRB(AppTokens.sp16, AppTokens.sp8, AppTokens.sp16, AppTokens.sp16),
                    // 第 0 项：AI 身份说明（A.4，常驻首屏）
                    itemCount: _c.messages.length + 1,
                    itemBuilder: (_, j) {
                      if (j == 0) {
                        return const Padding(
                          padding: EdgeInsets.only(bottom: AppTokens.sp12),
                          child: AiAssistantIntro(),
                        );
                      }
                      final i = j - 1;
                      final m = _c.messages[i];
                      if (m.isUser) return UserBubble(m.text);
                      final isLast = i == _c.messages.length - 1;
                      return AssistantMessageView(
                        message: m,
                        onActionChanged: _c.updateAction,
                        onRate: m.messageId.startsWith('_') ? null : (r) => _c.rate(m.messageId, r),
                        onRetry: isLast && m.error != null ? () => _c.retryLast() : null,
                      );
                    },
                  ),
          ),
          if (follow.isNotEmpty && !_c.sending)
            SizedBox(
              height: 40,
              child: ListView.separated(
                padding: const EdgeInsets.symmetric(horizontal: AppTokens.sp16),
                scrollDirection: Axis.horizontal,
                itemCount: follow.length,
                separatorBuilder: (_, __) => const SizedBox(width: AppTokens.sp8),
                itemBuilder: (_, i) => ActionChip(
                  label: Text(follow[i], style: ts.caption.copyWith(color: t.title)),
                  backgroundColor: t.card,
                  side: BorderSide(color: t.border),
                  shape: const StadiumBorder(),
                  onPressed: () => _send(follow[i]),
                ),
              ),
            ),
          SafeArea(
            top: false,
            child: Padding(
              padding: const EdgeInsets.fromLTRB(AppTokens.sp12, AppTokens.sp8, AppTokens.sp12, AppTokens.sp8),
              child: AgentInputBar(
                controller: _input,
                hint: '继续问咕嘟…',
                sending: _c.sending,
                onSubmit: _send,
                onCamera: () => context.push('/scan'),
                onVoice: () => context.push('/agent/voice'),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _empty(AppTokens t) {
    final ts = t.textStyles;
    return ListView(
      padding: const EdgeInsets.all(AppTokens.sp24),
      children: [
        const SizedBox(height: AppTokens.sp32),
        const Center(child: GuduAvatar(size: 64)),
        const SizedBox(height: AppTokens.sp16),
        Center(child: Text('我是咕嘟，你家的做饭助手', style: ts.subtitle)),
        const SizedBox(height: AppTokens.sp8),
        const AiAssistantIntro(),
        const SizedBox(height: AppTokens.sp8),
        Center(
          child: Text('我能查余量和菜谱、排食集、加采购、记做完了的菜。\n写操作都会先给你确认。',
              textAlign: TextAlign.center, style: ts.caption.copyWith(height: 1.6)),
        ),
        const SizedBox(height: AppTokens.sp24),
        for (final p in AgentSuggestions.defaultPrompts.take(3))
          Padding(
            padding: const EdgeInsets.only(bottom: AppTokens.sp8),
            child: OutlinedButton(onPressed: () => _send(p), child: Text(p)),
          ),
      ],
    );
  }
}
