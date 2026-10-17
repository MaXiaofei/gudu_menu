import 'dart:async';

import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';

import '../../core/error_text.dart';
import '../../services/agent_service.dart';

/// 一次对话的状态机（对话页、语音页、跟做模式「问咕嘟」共用）。
///
/// 发送：确保会话存在 → 追加用户消息 + 空的助手消息（streaming）→ 逐个 SSE 事件折叠进助手消息。
/// 接口未上线（404）时助手消息显示「咕嘟还没上线」，不抛异常。
class AgentConversation extends ChangeNotifier {
  String? sessionId;
  final List<AgentMessage> messages = [];
  bool sending = false;
  bool loadingHistory = false;
  CancelToken? _cancel;
  StreamSubscription<SseEvent>? _sub;
  int _seq = 0;

  AgentConversation({this.sessionId});

  AgentMessage? get lastAssistant {
    for (var i = messages.length - 1; i >= 0; i--) {
      if (!messages[i].isUser) return messages[i];
    }
    return null;
  }

  Future<void> loadHistory() async {
    final id = sessionId;
    if (id == null) return;
    loadingHistory = true;
    notifyListeners();
    try {
      final page = await AgentService.messages(id);
      messages
        ..clear()
        ..addAll(page.items.reversed); // 接口倒序返回
    } catch (_) {
      // 历史拉不到不影响继续对话
    } finally {
      loadingHistory = false;
      notifyListeners();
    }
  }

  /// 发送一条消息；返回时该轮已结束（done / error / 流断开）。
  Future<void> send(String text, {AgentContext? context}) async {
    final q = text.trim();
    if (q.isEmpty || sending) return;
    sending = true;
    messages.add(AgentMessage(messageId: '_u${_seq++}', role: 'user', text: q, createdAt: DateTime.now()));
    final idx = messages.length;
    messages.add(AgentMessage(messageId: '_a${_seq++}', role: 'assistant', streaming: true, createdAt: DateTime.now()));
    notifyListeners();

    void update(AgentMessage Function(AgentMessage) f) {
      if (idx < messages.length) {
        messages[idx] = f(messages[idx]);
        notifyListeners();
      }
    }

    try {
      sessionId ??= await AgentService.createSession(title: q.length > 16 ? q.substring(0, 16) : q);
      _cancel = CancelToken();
      final done = Completer<void>();
      _sub = AgentService.send(sessionId!, text: q, context: context, cancelToken: _cancel).listen(
        (e) => update((m) => AgentReducer.apply(m, e)),
        onError: (Object e) {
          update((m) => m.copyWith(streaming: false, error: _errorMessage(e), errorId: errorIdOf(e)));
          if (!done.isCompleted) done.complete();
        },
        onDone: () {
          update((m) => m.streaming ? m.copyWith(streaming: false) : m);
          if (!done.isCompleted) done.complete();
        },
        cancelOnError: true,
      );
      await done.future;
    } catch (e) {
      update((m) => m.copyWith(streaming: false, error: _errorMessage(e), errorId: errorIdOf(e)));
    } finally {
      sending = false;
      _sub = null;
      _cancel = null;
      notifyListeners();
    }
  }

  /// 重试最后一条用户消息（删掉失败的那轮）。
  Future<void> retryLast({AgentContext? context}) async {
    if (sending || messages.length < 2) return;
    final last = messages.last;
    if (last.isUser || last.error == null) return;
    final q = messages[messages.length - 2].text;
    messages.removeRange(messages.length - 2, messages.length);
    await send(q, context: context);
  }

  void stop() {
    _cancel?.cancel('用户停止');
    _sub?.cancel();
    sending = false;
    if (messages.isNotEmpty && messages.last.streaming) {
      messages[messages.length - 1] = messages.last.copyWith(streaming: false);
    }
    notifyListeners();
  }

  /// 更新动作卡；[replacing] 为被 revise 取代的旧 actionId（新卡原位替换旧卡）。
  void updateAction(PendingAction a, {String? replacing}) {
    final key = replacing ?? a.actionId;
    for (var i = 0; i < messages.length; i++) {
      final acts = messages[i].actions;
      final j = acts.indexWhere((e) => e.actionId == key);
      if (j >= 0) {
        final next = [...acts]..[j] = a;
        messages[i] = messages[i].copyWith(actions: next);
        notifyListeners();
        return;
      }
    }
  }

  Future<void> rate(String messageId, int rating) async {
    final i = messages.indexWhere((m) => m.messageId == messageId);
    if (i < 0) return;
    messages[i] = messages[i].copyWith(rating: rating);
    notifyListeners();
    if (rating == 0) return;
    try {
      await AgentService.feedback(messageId, rating);
    } catch (_) {
      // 评价失败不打扰用户
    }
  }

  static String _errorMessage(Object e) {
    if (isNotDeployed(e)) return '咕嘟还没上线：后端 Agent 接口未部署，稍后再试';
    if (e is DioException && e.type == DioExceptionType.cancel) return '已停止';
    return errorText(e, '咕嘟走神了，请再试一次');
  }

  @override
  void dispose() {
    _cancel?.cancel();
    _sub?.cancel();
    super.dispose();
  }
}

/// 根据最后一条助手消息给出追问建议。
List<String> followUpsFor(AgentMessage? m) {
  if (m == null || m.streaming) return const [];
  if (m.cards.any((c) => c.type == 'recipes')) {
    return const ['就这几道，加进今晚的食集', '缺的加采购', '换一道清淡点的'];
  }
  if (m.actions.any((a) => a.status == 'DONE')) return const ['提醒我 18:20 蒸鱼', '还缺什么要买？'];
  if (m.actions.any((a) => a.isPending)) return const ['确认', '先不要了'];
  return const ['冰箱里的能做什么', '帮我排下周一周的菜'];
}
