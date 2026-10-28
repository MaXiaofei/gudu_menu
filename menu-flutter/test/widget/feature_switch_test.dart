import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:menu_flutter/core/api_client.dart';
import 'package:menu_flutter/core/app_theme.dart';
import 'package:menu_flutter/core/feature_switch.dart';
import 'package:menu_flutter/models/dish.dart';
import 'package:menu_flutter/pages/agent/agent_conversation.dart';
import 'package:menu_flutter/pages/cook/cook_mode_page.dart';
import 'package:menu_flutter/services/agent_service.dart';
import 'package:menu_flutter/widgets/agent_paused_gate.dart';
import '../helpers/mock_http.dart';

/// 咕嘟助手 / 问咕嘟紧急关停：5032 / 5033 切暂停状态（不弹报错）；暂停提示盖页；跟做模式隐藏问咕嘟。
void main() {
  setUp(FeatureSwitch.reset);

  test('noteCode：5032 只关咕嘟助手，5033 只关问咕嘟，其他码不识别', () {
    expect(FeatureSwitch.noteCode(5032), isTrue);
    expect(FeatureSwitch.agentChat.value, isFalse);
    expect(FeatureSwitch.cookAsk.value, isTrue);
    expect(FeatureSwitch.noteCode(5033), isTrue);
    expect(FeatureSwitch.cookAsk.value, isFalse);
    expect(FeatureSwitch.noteCode(500), isFalse);
  });

  test('canSend：cook_mode 看问咕嘟，其余看咕嘟助手', () {
    FeatureSwitch.agentChat.value = false;
    expect(FeatureSwitch.canSend('agent'), isFalse);
    expect(FeatureSwitch.canSend(null), isFalse);
    expect(FeatureSwitch.canSend('cook_mode'), isTrue);
    FeatureSwitch.agentChat.value = true;
    FeatureSwitch.cookAsk.value = false;
    expect(FeatureSwitch.canSend('cook_mode'), isFalse);
    expect(FeatureSwitch.canSend('agent'), isTrue);
  });

  test('load：读取状态；失败/缺字段按开处理', () async {
    var body = <String, dynamic>{'agentChat': false, 'cookAsk': true};
    installMock((_) => okResponse(body));
    await FeatureSwitch.load();
    expect(FeatureSwitch.agentChat.value, isFalse);
    expect(FeatureSwitch.cookAsk.value, isTrue);

    body = {'agentChat': true, 'cookAsk': false};
    await FeatureSwitch.load();
    expect(FeatureSwitch.agentChat.value, isTrue);
    expect(FeatureSwitch.cookAsk.value, isFalse);

    body = {};
    await FeatureSwitch.load();
    expect(FeatureSwitch.agentChat.value, isTrue);
    expect(FeatureSwitch.cookAsk.value, isTrue);

    // 接口出错：保持原状，不抛错
    FeatureSwitch.cookAsk.value = false;
    installMock((_) => errResponse(1, '出错了'));
    await FeatureSwitch.load();
    expect(FeatureSwitch.cookAsk.value, isFalse);
  });

  test('ApiClient 收到 5032：置暂停、不弹 toast、抛异常', () async {
    String? toasted;
    installMock((_) => errResponse(5032, '咕嘟助手暂停服务'), onErrorToast: (m) => toasted = m);
    await expectLater(ApiClient.instance.get('/agent/suggestions'), throwsA(isA<DioException>()));
    expect(FeatureSwitch.agentChat.value, isFalse);
    expect(toasted, isNull);
  });

  test('SSE error 事件携带 5032 / 5033：切暂停状态', () {
    final m = AgentReducer.apply(
      AgentMessage(messageId: 'a', role: 'assistant', streaming: true, createdAt: DateTime(2026)),
      const SseEvent('error', {'code': 5033, 'message': '问咕嘟暂停服务', 'errorId': ''}),
    );
    expect(m.error, '问咕嘟暂停服务');
    expect(FeatureSwitch.cookAsk.value, isFalse);
    expect(FeatureSwitch.agentChat.value, isTrue);
  });

  test('AgentConversation.send：暂停时不发送、不追加消息', () async {
    FeatureSwitch.agentChat.value = false;
    final c = AgentConversation();
    await c.send('今晚吃什么');
    expect(c.messages, isEmpty);

    FeatureSwitch.cookAsk.value = false;
    await c.send('没有豆瓣酱怎么办', context: const AgentContext(page: 'cook_mode', dishId: 1, step: 1));
    expect(c.messages, isEmpty);
  });

  testWidgets('暂停时页面换成「咕嘟助手暂停服务」，点「再看看」重新读取开关后回到原页面', (tester) async {
    installMock((_) => okResponse({'agentChat': true, 'cookAsk': true}));
    await tester.pumpWidget(MaterialApp(
      theme: ThemeData(extensions: const [AppTokens.cream]),
      home: const AgentPausedGate(showBack: false, child: Text('咕嘟内容')),
    ));
    expect(find.text('咕嘟内容'), findsOneWidget);

    FeatureSwitch.agentChat.value = false;
    await tester.pump();
    expect(find.byKey(const Key('agent-paused')), findsOneWidget);
    expect(find.text('咕嘟助手暂停服务，其他功能不受影响'), findsOneWidget);
    expect(find.text('咕嘟内容'), findsNothing);

    await tester.runAsync(() async {
      await tester.tap(find.text('再看看'));
      await Future<void>.delayed(const Duration(milliseconds: 100));
    });
    await tester.pump();
    expect(find.text('咕嘟内容'), findsOneWidget);
  });

  testWidgets('跟做模式：问咕嘟关停后隐藏提问区，步骤与导航照常', (tester) async {
    installMock((_) => okResponse({'agentChat': true, 'cookAsk': false}));
    final detail = DishDetail.fromJson({
      'dish': {'id': 9, 'name': '麻婆豆腐'},
      'steps': [
        {'seq': 1, 'text': '豆腐切块'},
        {'seq': 2, 'text': '下锅煮开'},
      ],
      'ingredients': [],
    });
    FeatureSwitch.cookAsk.value = false;
    await tester.pumpWidget(MaterialApp(
      theme: ThemeData(extensions: const [AppTokens.cream]),
      home: CookModePage(dishId: 9, detail: detail),
    ));
    await tester.pumpAndSettle(); // 让 initState 里的开关读取跑完
    expect(find.byKey(const Key('cook-ask')), findsNothing);
    expect(find.textContaining('问咕嘟 ·'), findsNothing);
    expect(find.textContaining('豆腐切块'), findsWidgets);

    FeatureSwitch.cookAsk.value = true;
    await tester.pump();
    expect(find.byKey(const Key('cook-ask')), findsOneWidget);
    await tester.pumpWidget(const SizedBox()); // 卸载页面，释放计时器
  });
}
