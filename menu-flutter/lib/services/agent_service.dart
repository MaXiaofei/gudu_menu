import 'dart:async';
import 'dart:convert';

import 'package:dio/dio.dart';

import '../core/api_client.dart';
import '../core/feature_switch.dart';
import '../models/cursor_page.dart';
import '../models/showcase_types.dart';

/// 咕嘟 Agent（contracts.md §1）：会话、流式对话、待确认动作、首屏建议。

/// 一次工具调用（对话气泡上方折叠的「查了余量 · 29 样」）。
class AgentToolCall {
  final String name;
  final String label;
  final String summary;
  final int ms;
  final bool ok;
  final bool running;
  const AgentToolCall({
    required this.name,
    required this.label,
    this.summary = '',
    this.ms = 0,
    this.ok = true,
    this.running = false,
  });

  factory AgentToolCall.fromJson(Map<String, dynamic> j) => AgentToolCall(
        name: Json.s(j['name']),
        label: Json.s(j['label'], Json.s(j['name'])),
        summary: Json.s(j['summary']),
        ms: Json.i(j['ms']),
        ok: j['ok'] != false,
      );

  AgentToolCall finish({required String summary, required int ms, required bool ok}) =>
      AgentToolCall(name: name, label: label, summary: summary, ms: ms, ok: ok);
}

/// 用料去向一行（mark_cooked 预览）。
class CookedLine {
  final String name;
  final String? iconKey;
  final String delta;
  final String after;

  /// USED_UP / LEFT / SKIP
  final String status;
  const CookedLine({required this.name, this.iconKey, this.delta = '', this.after = '', this.status = 'LEFT'});

  factory CookedLine.fromJson(Map<String, dynamic> j) => CookedLine(
        name: Json.s(j['name']),
        iconKey: Json.sOrNull(j['iconKey']),
        delta: Json.s(j['delta']),
        after: Json.s(j['after']),
        status: Json.s(j['status'], 'LEFT'),
      );
}

/// 待确认动作（写操作必须用户确认才执行，5 分钟有效）。
class PendingAction {
  final String actionId;

  /// add_to_menu / add_to_shopping / mark_cooked / add_pantry
  final String tool;
  final String title;
  final Map<String, dynamic> preview;
  final DateTime? expiresAt;

  /// PENDING / DONE / CANCELLED / EXPIRED / SUPERSEDED（被 revise 取代；客户端另有 UNDONE）
  final String status;
  final String? undoToken;

  /// 工具参数（后端可选下发；revise 时在它基础上改）。
  final Map<String, dynamic> args;

  const PendingAction({
    required this.actionId,
    required this.tool,
    required this.title,
    this.preview = const {},
    this.expiresAt,
    this.status = 'PENDING',
    this.undoToken,
    this.args = const {},
  });

  factory PendingAction.fromJson(Map<String, dynamic> j) => PendingAction(
        actionId: Json.s(j['actionId']),
        tool: Json.s(j['tool']),
        title: Json.s(j['title']),
        preview: Json.map(j['preview']),
        expiresAt: Json.date(j['expiresAt']),
        status: Json.s(j['status'], 'PENDING'),
        args: Json.map(j['args']),
      );

  PendingAction copyWith({String? status, String? undoToken}) => PendingAction(
        actionId: actionId,
        tool: tool,
        title: title,
        preview: preview,
        expiresAt: expiresAt,
        status: status ?? this.status,
        undoToken: undoToken ?? this.undoToken,
        args: args,
      );

  bool get isPending => status == 'PENDING';

  /// 是否已过期（以本地时间判断，服务端仍会二次校验）。
  bool expiredAt(DateTime now) => expiresAt != null && now.isAfter(expiresAt!);

  // ---- mark_cooked 预览 ----
  DishCard? get cookedDish {
    final d = preview['dish'];
    return d is Map ? DishCard.fromJson(Map<String, dynamic>.from(d)) : null;
  }

  int get servings => Json.i(preview['servings'], 1);
  List<MemberLite> get eaters => Json.maps(preview['eaters']).map(MemberLite.fromJson).toList();
  List<CookedLine> get lines => Json.maps(preview['lines']).map(CookedLine.fromJson).toList();

  /// 通用预览行（add_to_shopping / add_pantry 的 items、add_to_menu 的 dishes）。
  List<String> get previewNames {
    final out = <String>[];
    for (final key in const ['items', 'dishes']) {
      for (final m in Json.maps(preview[key])) {
        final n = Json.s(m['name']);
        if (n.isNotEmpty) out.add(n);
      }
    }
    return out;
  }
}

/// mark_cooked 的可改参数（revise 的 args）。
///
/// 结构：`{dishId, servings, eaterIds: [memberId], lines: [{name, ingredientId?, status: USED_UP|LEFT|SKIP, amount?}]}`；
/// 后端下发了 [PendingAction.args] 时以它为底，只覆盖这几项。
class CookedRevision {
  int servings;
  final Set<int> eaterIds;
  final List<({String name, int? ingredientId, String? iconKey, String status, String amount})> lines;
  final int? dishId;
  final Map<String, dynamic> base;

  CookedRevision({
    required this.servings,
    required this.eaterIds,
    required this.lines,
    this.dishId,
    this.base = const {},
  });

  factory CookedRevision.from(PendingAction a) {
    final raw = Json.maps(a.preview['lines']);
    return CookedRevision(
      servings: a.servings,
      eaterIds: {for (final e in a.eaters) e.memberId},
      dishId: a.cookedDish?.id ?? Json.iOrNull(a.args['dishId']),
      base: a.args,
      lines: [
        for (final l in raw)
          (
            name: Json.s(l['name']),
            ingredientId: Json.iOrNull(l['ingredientId']),
            iconKey: Json.sOrNull(l['iconKey']),
            status: Json.s(l['status'], 'LEFT'),
            amount: Json.s(l['amount'], Json.s(l['delta']).replaceFirst(RegExp(r'^[-−]\s*'), '')),
          ),
      ],
    );
  }

  Map<String, dynamic> toArgs() => {
        ...base,
        if (dishId != null) 'dishId': dishId,
        'servings': servings,
        'eaterIds': eaterIds.toList()..sort(),
        'lines': [
          for (final l in lines)
            {
              'name': l.name,
              if (l.ingredientId != null) 'ingredientId': l.ingredientId,
              'status': l.status,
              if (l.status != 'SKIP' && l.amount.isNotEmpty) 'amount': l.amount,
            },
        ],
      };
}

/// 对话里的卡片（recipes / menu / pantry / plan）。
class AgentCard {
  final String type;
  final Map<String, dynamic> raw;
  const AgentCard(this.type, this.raw);

  factory AgentCard.fromJson(Map<String, dynamic> j) => AgentCard(Json.s(j['type']), j);

  List<DishCard> get recipes => Json.maps(raw['items']).map(DishCard.fromJson).toList();
  List<Map<String, dynamic>> get pantryItems => Json.maps(raw['items']);
}

class AgentMessage {
  final String messageId;

  /// user / assistant
  final String role;
  final String text;
  final List<AgentToolCall> tools;
  final List<AgentCard> cards;
  final List<PendingAction> actions;
  final String? traceId;
  final DateTime? createdAt;

  /// 客户端状态：流式输出中 / 出错 / 降级 / 已评价
  final bool streaming;
  final String? error;
  final String? errorId;
  final bool degraded;
  final int rating;

  const AgentMessage({
    required this.messageId,
    required this.role,
    this.text = '',
    this.tools = const [],
    this.cards = const [],
    this.actions = const [],
    this.traceId,
    this.createdAt,
    this.streaming = false,
    this.error,
    this.errorId,
    this.degraded = false,
    this.rating = 0,
  });

  bool get isUser => role == 'user';

  factory AgentMessage.fromJson(Map<String, dynamic> j) => AgentMessage(
        messageId: Json.s(j['messageId']),
        role: Json.s(j['role'], 'assistant'),
        text: Json.s(j['text']),
        tools: Json.maps(j['tools']).map(AgentToolCall.fromJson).toList(),
        cards: Json.maps(j['cards']).map(AgentCard.fromJson).toList(),
        actions: Json.maps(j['actions']).map(PendingAction.fromJson).toList(),
        traceId: Json.sOrNull(j['traceId']),
        createdAt: Json.date(j['createdAt']),
      );

  AgentMessage copyWith({
    String? messageId,
    String? text,
    List<AgentToolCall>? tools,
    List<AgentCard>? cards,
    List<PendingAction>? actions,
    String? traceId,
    bool? streaming,
    String? error,
    String? errorId,
    bool? degraded,
    int? rating,
  }) =>
      AgentMessage(
        messageId: messageId ?? this.messageId,
        role: role,
        text: text ?? this.text,
        tools: tools ?? this.tools,
        cards: cards ?? this.cards,
        actions: actions ?? this.actions,
        traceId: traceId ?? this.traceId,
        createdAt: createdAt,
        streaming: streaming ?? this.streaming,
        error: error ?? this.error,
        errorId: errorId ?? this.errorId,
        degraded: degraded ?? this.degraded,
        rating: rating ?? this.rating,
      );

  /// 报错编号展示（traceId 前 8 位 → xxxx-xxxx）。
  String? get shortTraceId {
    final raw = errorId ?? traceId;
    if (raw == null) return null;
    final hex = raw.replaceAll(RegExp(r'[^0-9a-fA-F]'), '').toLowerCase();
    return hex.length >= 8 ? '${hex.substring(0, 4)}-${hex.substring(4, 8)}' : raw;
  }
}

/// SSE 事件（`event:` 名 + `data:` JSON）。
class SseEvent {
  final String event;
  final Map<String, dynamic> data;
  const SseEvent(this.event, this.data);

  @override
  String toString() => 'SseEvent($event, $data)';
}

/// 增量 SSE 解析：按行喂入，空行结束一个事件。
///
/// 规则（W3C EventSource 子集）：`event:` 设事件名（缺省 message）；多行 `data:` 以 \n 拼接；
/// `:` 开头为注释（心跳）忽略；data 非 JSON 时包成 `{text: data}`，保证 token 事件不丢字。
class SseParser {
  String _event = '';
  final List<String> _data = [];

  /// 喂入一行（不含换行符），返回因空行而完成的事件（可能为 null）。
  SseEvent? addLine(String rawLine) {
    final line = rawLine.endsWith('\r') ? rawLine.substring(0, rawLine.length - 1) : rawLine;
    if (line.isEmpty) return _dispatch();
    if (line.startsWith(':')) return null;
    final idx = line.indexOf(':');
    final field = idx < 0 ? line : line.substring(0, idx);
    var value = idx < 0 ? '' : line.substring(idx + 1);
    if (value.startsWith(' ')) value = value.substring(1);
    switch (field) {
      case 'event':
        _event = value;
        break;
      case 'data':
        _data.add(value);
        break;
    }
    return null;
  }

  /// 流结束时把未以空行结束的残留事件吐出。
  SseEvent? close() => _dispatch();

  SseEvent? _dispatch() {
    if (_data.isEmpty && _event.isEmpty) return null;
    final name = _event.isEmpty ? 'message' : _event;
    final raw = _data.join('\n');
    _event = '';
    _data.clear();
    Map<String, dynamic> data;
    try {
      final v = raw.isEmpty ? const <String, dynamic>{} : jsonDecode(raw);
      data = v is Map ? Map<String, dynamic>.from(v) : {'value': v};
    } catch (_) {
      data = {'text': raw};
    }
    return SseEvent(name, data);
  }

  /// 把字节流转成事件流（UTF-8 解码 + 按行切分，跨 chunk 的半行/半个汉字都能正确拼接）。
  static Stream<SseEvent> transform(Stream<List<int>> bytes) async* {
    final parser = SseParser();
    final lines = bytes.cast<List<int>>().transform(utf8.decoder).transform(const LineSplitter());
    await for (final line in lines) {
      final e = parser.addLine(line);
      if (e != null) yield e;
    }
    final tail = parser.close();
    if (tail != null) yield tail;
  }
}

/// 把 SSE 事件逐个折叠进「正在生成的那条助手消息」。
///
/// 页面每收到一个事件就 `msg = AgentReducer.apply(msg, e)` 后 setState，逻辑集中在这里便于单测。
class AgentReducer {
  AgentReducer._();

  static AgentMessage apply(AgentMessage m, SseEvent e) {
    final d = e.data;
    switch (e.event) {
      case 'meta':
        return m.copyWith(messageId: Json.s(d['messageId'], m.messageId), traceId: Json.sOrNull(d['traceId']));
      case 'token':
        return m.copyWith(text: m.text + Json.s(d['text']));
      case 'tool_start':
        return m.copyWith(tools: [
          ...m.tools,
          AgentToolCall(name: Json.s(d['name']), label: Json.s(d['label'], Json.s(d['name'])), running: true),
        ]);
      case 'tool_end':
        final name = Json.s(d['name']);
        final tools = [...m.tools];
        final i = tools.lastIndexWhere((t) => t.name == name && t.running);
        final done = (i >= 0 ? tools[i] : AgentToolCall(name: name, label: name))
            .finish(summary: Json.s(d['summary']), ms: Json.i(d['ms']), ok: d['ok'] != false);
        if (i >= 0) {
          tools[i] = done;
        } else {
          tools.add(done);
        }
        return m.copyWith(tools: tools);
      case 'card':
        return m.copyWith(cards: [...m.cards, AgentCard.fromJson(d)]);
      case 'action':
        return m.copyWith(actions: [...m.actions, PendingAction.fromJson(d)]);
      case 'done':
        return m.copyWith(streaming: false, degraded: d['degraded'] == true);
      case 'error':
        FeatureSwitch.noteCode(Json.i(d['code'])); // 5032 / 5033：紧急关停，页面切暂停提示
        return m.copyWith(
          streaming: false,
          error: Json.s(d['message'], '咕嘟走神了，请再试一次'),
          errorId: Json.sOrNull(d['errorId']),
        );
      default:
        return m;
    }
  }
}

/// 首屏建议（`GET /agent/suggestions`）。
class AgentSuggestions {
  final String greeting;
  final DishCard? tonight;
  final List<({String name, String? iconKey, int daysLeft})> expiring;
  final List<String> prompts;
  const AgentSuggestions({this.greeting = '', this.tonight, this.expiring = const [], this.prompts = const []});

  factory AgentSuggestions.fromJson(Map<String, dynamic> j) {
    final t = j['tonight'];
    return AgentSuggestions(
      greeting: Json.s(j['greeting']),
      tonight: t is Map ? DishCard.fromJson(Map<String, dynamic>.from(t)) : null,
      expiring: [
        for (final e in Json.maps(j['expiring']))
          (name: Json.s(e['name']), iconKey: Json.sOrNull(e['iconKey']), daysLeft: Json.i(e['daysLeft'])),
      ],
      prompts: Json.strs(j['prompts']),
    );
  }

  /// 接口未上线 / 失败时的默认快捷提问。
  static const defaultPrompts = ['冰箱里的能做什么', '给宝宝安排这周辅食', '周六家里来客人怎么安排', '拍冰箱入库'];
}

class AgentSessionItem {
  final String sessionId;
  final String title;
  final String lastMessage;
  final DateTime? updatedAt;
  final String? coverUrl;
  const AgentSessionItem({required this.sessionId, this.title = '', this.lastMessage = '', this.updatedAt, this.coverUrl});

  factory AgentSessionItem.fromJson(Map<String, dynamic> j) => AgentSessionItem(
        sessionId: Json.s(j['sessionId']),
        title: Json.s(j['title']),
        lastMessage: Json.s(j['lastMessage']),
        updatedAt: Json.date(j['updatedAt']),
        coverUrl: Json.sOrNull(j['coverUrl']),
      );
}

/// 发送消息时附带的页面上下文（跟做模式问咕嘟带菜谱与步骤）。
class AgentContext {
  final String page;
  final int? dishId;
  final int? step;
  const AgentContext({required this.page, this.dishId, this.step});
  Map<String, dynamic> toJson() => {'page': page, if (dishId != null) 'dishId': dishId, if (step != null) 'step': step};
}

class AgentService {
  AgentService._();

  static Future<String> createSession({String? title}) async {
    final d = await ApiClient.instance.post('/agent/sessions', body: {if (title != null) 'title': title});
    return Json.s(Json.map(d)['sessionId']);
  }

  static Future<CursorPage<AgentSessionItem>> sessions({String? cursor, int size = 10}) async {
    final d = await ApiClient.instance
        .get('/agent/sessions', query: {if (cursor != null) 'cursor': cursor, 'size': size});
    return CursorPage.fromJson(d, AgentSessionItem.fromJson);
  }

  static Future<CursorPage<AgentMessage>> messages(String sessionId, {String? cursor, int size = 20}) async {
    final d = await ApiClient.instance
        .get('/agent/sessions/$sessionId/messages', query: {if (cursor != null) 'cursor': cursor, 'size': size});
    return CursorPage.fromJson(d, AgentMessage.fromJson);
  }

  /// 发送消息，返回 SSE 事件流。
  ///
  /// 走 dio 的 [ResponseType.stream]：响应体不是 `{code,...}` JSON，ApiClient 拦截器原样放行。
  static Stream<SseEvent> send(
    String sessionId, {
    String? text,
    int? audioFileId,
    AgentContext? context,
    CancelToken? cancelToken,
  }) async* {
    final resp = await ApiClient.instance.dio.post<ResponseBody>(
      '/agent/sessions/$sessionId/messages',
      data: {
        if (text != null) 'text': text,
        if (audioFileId != null) 'audioFileId': audioFileId,
        if (context != null) 'context': context.toJson(),
      },
      cancelToken: cancelToken,
      options: Options(
        responseType: ResponseType.stream,
        headers: {'Accept': 'text/event-stream'},
        receiveTimeout: const Duration(minutes: 2),
      ),
    );
    final body = resp.data;
    if (body == null) return;
    yield* SseParser.transform(body.stream);
  }

  static Future<PendingAction> confirm(PendingAction a) async {
    final d = Json.map(await ApiClient.instance.post('/agent/actions/${a.actionId}/confirm'));
    return a.copyWith(status: Json.s(d['status'], 'DONE'), undoToken: Json.sOrNull(d['undoToken']));
  }

  static Future<PendingAction> cancel(PendingAction a) async {
    final d = Json.map(await ApiClient.instance.post('/agent/actions/${a.actionId}/cancel'));
    return a.copyWith(status: Json.s(d['status'], 'CANCELLED'));
  }

  /// 修改待确认动作的参数（改人份 / 谁吃了 / 实际用量），返回新的 PendingAction（旧的变 SUPERSEDED）。
  static Future<PendingAction> revise(PendingAction a, Map<String, dynamic> args) async {
    final d = await ApiClient.instance.post('/agent/actions/${a.actionId}/revise', body: {'args': args});
    return PendingAction.fromJson(Json.map(d));
  }

  static Future<void> undo(String undoToken) async {
    await ApiClient.instance.post('/agent/actions/undo', body: {'undoToken': undoToken});
  }

  static Future<void> feedback(String messageId, int rating, {String? comment}) async {
    await ApiClient.instance.post('/agent/messages/$messageId/feedback',
        body: {'rating': rating, if (comment != null) 'comment': comment});
  }

  static Future<AgentSuggestions> suggestions() async {
    final d = await ApiClient.instance.get('/agent/suggestions');
    return AgentSuggestions.fromJson(Json.map(d));
  }
}

/// 接口是否未部署（后端还是旧版本）。页面据此显示「功能即将上线」而非报错。
///
/// 两种形态：HTTP 404；或旧版后端把未知路径包成 `{code:1, msg:"…NoResourceFoundException…"}`（HTTP 200）。
bool isNotDeployed(Object e) {
  if (e is! DioException) return false;
  if (e.response?.statusCode == 404) return true;
  final m = e.message ?? '';
  return m.contains('NoResourceFoundException') || m.contains('No static resource');
}
