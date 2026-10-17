import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import 'package:menu_flutter/core/app_theme.dart';
import 'package:menu_flutter/models/dish.dart';
import 'package:menu_flutter/models/member.dart';
import 'package:menu_flutter/pages/agent/agent_chat_page.dart';
import 'package:menu_flutter/pages/agent/agent_home_page.dart';
import 'package:menu_flutter/pages/community/community_page.dart';
import 'package:menu_flutter/pages/cook/cook_mode_page.dart';
import 'package:menu_flutter/pages/mealplan/nutrition_report_page.dart';
import 'package:menu_flutter/pages/mealplan/week_plan_page.dart';
import 'package:menu_flutter/pages/vision/scan_result_page.dart';
import 'package:menu_flutter/services/vision_service.dart';
import 'package:menu_flutter/stores/auth_store.dart';
import 'package:menu_flutter/stores/member_store.dart';
import 'package:menu_flutter/widgets/me_avatar_button.dart';
import '../helpers/mock_http.dart';
import '../helpers/showcase_mock.dart';

/// showcase-ai-community 关键页面的 widget 流程。
void main() {
  late MemberStore members;

  setUp(() {
    members = MemberStore()
      ..members = const [
        Member(id: 1, name: '小王'),
        Member(id: 3, name: '妈妈', audiences: ['高血压']),
        Member(id: 4, name: '朵朵', audiences: ['宝宝辅食']),
      ]
      ..currentId = 1;
  });

  /// 用 GoRouter 承载待测页（页面里用 context.push / pop），所有子路由落到占位页。
  Future<GoRouter> pumpPage(WidgetTester tester, Widget page, {Size size = const Size(1080, 2400)}) async {
    tester.view.physicalSize = size;
    tester.view.devicePixelRatio = 3;
    addTearDown(tester.view.reset);
    final router = GoRouter(initialLocation: '/t', routes: [
      GoRoute(path: '/t', builder: (_, __) => page),
      GoRoute(path: '/:a', builder: (_, s) => Scaffold(body: Text('ROUTE ${s.uri}'))),
      GoRoute(path: '/:a/:b', builder: (_, s) => Scaffold(body: Text('ROUTE ${s.uri}'))),
      GoRoute(path: '/:a/:b/:c', builder: (_, s) => Scaffold(body: Text('ROUTE ${s.uri}'))),
    ]);
    await tester.pumpWidget(MultiProvider(
      providers: [
        ChangeNotifierProvider<AuthStore>(create: (_) => AuthStore()..nickname = '小王'),
        ChangeNotifierProvider<MemberStore>.value(value: members),
      ],
      child: MaterialApp.router(
        theme: buildBrandTheme(AppTokens.cream),
        routerConfig: router,
      ),
    ));
    return router;
  }

  group('咕嘟 Agent', () {
    testWidgets('首屏：今晚首推 + 快到期 + 快捷提问；右上角头像进「我的」', (tester) async {
      installShowcase((o) {
        if (o.path == '/agent/suggestions') {
          return okResponse({
            'greeting': '我看了下家里：29 样食材，鲈鱼明天到期。',
            'tonight': {'id': 7, 'name': '清蒸鲈鱼', 'minutes': 25, 'coverage': {'have': 4, 'total': 5, 'missing': ['小葱']}},
            'expiring': [
              {'name': '鲈鱼', 'iconKey': 'fish', 'daysLeft': 1},
              {'name': '菠菜', 'iconKey': 'spinach', 'daysLeft': 2},
            ],
            'prompts': ['冰箱里的能做什么', '拍冰箱入库'],
          });
        }
        return okResponse({'items': [], 'nextCursor': null});
      });
      await pumpPage(tester, const AgentHomePage());
      await tester.pumpAndSettle();

      expect(find.text('清蒸鲈鱼'), findsOneWidget);
      expect(find.textContaining('鲈鱼明天到期'), findsWidgets);
      expect(find.text('还有 1 样快到期'), findsOneWidget);
      expect(find.text('冰箱里的能做什么'), findsOneWidget);
      expect(find.text('今晚 3 口人'), findsOneWidget);

      await tester.tap(find.byType(MeAvatarButton));
      await tester.pumpAndSettle();
      expect(find.text('ROUTE /profile'), findsOneWidget);
    });

    testWidgets('首屏：接口未部署（404）显示部署中提示与默认快捷提问', (tester) async {
      installShowcase((_) => const HttpError(404));
      await pumpPage(tester, const AgentHomePage());
      await tester.pumpAndSettle();
      expect(find.textContaining('还在部署中'), findsOneWidget);
      expect(find.text('冰箱里的能做什么'), findsOneWidget);
    });

    testWidgets('对话：流式 → 工具链 + 菜谱卡 + 确认卡；确认后可撤销', (tester) async {
      final a = installShowcase((o) {
        if (o.path == '/agent/sessions') return okResponse({'sessionId': 's1'});
        if (o.path == '/agent/sessions/s1/messages') {
          return SseBody(sse('meta', {'messageId': 'm1', 'traceId': '7f3a91c2'}) +
              sse('tool_start', {'name': 'get_pantry', 'label': '查了余量'}) +
              sse('tool_end', {'name': 'get_pantry', 'summary': '29 样，4 样快到期', 'ms': 300, 'ok': true}) +
              sse('card', {
                'type': 'recipes',
                'items': [
                  {'id': 1, 'name': '清蒸鲈鱼', 'coverage': {'have': 4, 'total': 5, 'missing': ['小葱']}},
                  {'id': 2, 'name': '凉拌菠菜', 'coverage': {'have': 3, 'total': 3}},
                ],
              }) +
              sse('token', {'text': '鲈鱼明天到期，先做它。'}) +
              sse('action', {
                'actionId': 'act1',
                'tool': 'add_to_shopping',
                'title': '采购 2 样',
                'preview': {'items': [{'name': '小葱'}, {'name': '减盐蒸鱼豉油'}]},
                'expiresAt': DateTime.now().add(const Duration(minutes: 5)).toIso8601String(),
                'status': 'PENDING',
              }) +
              sse('done', {'degraded': false}));
        }
        if (o.path == '/agent/actions/act1/confirm') return okResponse({'status': 'DONE', 'undoToken': 'u1'});
        if (o.path == '/agent/actions/undo') return okResponse({'status': 'UNDONE'});
        return okResponse({});
      });
      await pumpPage(tester, const AgentChatPage(initialText: '冰箱里的东西今晚能做什么？妈妈要少盐'));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 300));
      await tester.pump(const Duration(milliseconds: 300));

      expect(find.text('冰箱里的东西今晚能做什么？妈妈要少盐'), findsOneWidget);
      // 一轮结束且有卡片 → 工具链折叠为摘要，点开看每一步
      expect(find.text('查了 1 项 · 用时 0.3 秒'), findsOneWidget);
      await tester.tap(find.text('查了 1 项 · 用时 0.3 秒'));
      await tester.pump();
      expect(find.text('查了余量 · 29 样，4 样快到期'), findsOneWidget);
      expect(find.text('get_pantry'), findsOneWidget);
      expect(find.text('清蒸鲈鱼'), findsOneWidget);
      expect(find.text('缺 1 样 · 小葱'), findsOneWidget);
      expect(find.text('家里够'), findsOneWidget);
      expect(find.textContaining('鲈鱼明天到期，先做它。'), findsOneWidget);
      expect(find.text('采购 2 样'), findsOneWidget);
      expect(find.textContaining('报错编号 7f3a-91c2'), findsOneWidget);
      final sent = a.requests.firstWhere((r) => r.path == '/agent/sessions/s1/messages');
      expect(sent.data['text'], '冰箱里的东西今晚能做什么？妈妈要少盐');

      await tester.ensureVisible(find.byKey(const Key('action-confirm-act1')));
      await tester.tap(find.byKey(const Key('action-confirm-act1')));
      await tester.pump(const Duration(milliseconds: 300));
      expect(find.text('已执行'), findsOneWidget);
      expect(find.byKey(const Key('action-undo-act1')), findsOneWidget);

      await tester.tap(find.byKey(const Key('action-undo-act1')));
      await tester.pump(const Duration(milliseconds: 300));
      expect(find.text('已撤销'), findsOneWidget);
      expect(a.requests.any((r) => r.path == '/agent/actions/undo' && r.data['undoToken'] == 'u1'), isTrue);
    });

    testWidgets('对话：Agent 接口 404 → 助手消息提示未上线，可重试', (tester) async {
      installShowcase((_) => const HttpError(404));
      await pumpPage(tester, const AgentChatPage(initialText: '你好'));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 300));
      expect(find.textContaining('咕嘟还没上线'), findsOneWidget);
      expect(find.text('重试'), findsOneWidget);
    });
  });

  group('厨房圈', () {
    testWidgets('Feed：话题横幅 + 双列瀑布流；切到关注带 tab=follow', (tester) async {
      final a = installShowcase((o) {
        if (o.path == '/community/banner') {
          return okResponse({
            'topic': {'topicId': 9, 'name': '#一周不重样', 'participantCount': 2100, 'endDate': '2099-01-01'},
            'followingUpdates': [{'userId': 2, 'nickname': '陈师傅'}],
          });
        }
        if (o.path == '/community/feed') {
          return okResponse({
            'items': [
              for (var i = 1; i <= 4; i++)
                {
                  'postId': i,
                  'author': {'userId': i, 'nickname': '厨友$i'},
                  'title': '第 $i 道菜',
                  'coverUrl': '',
                  'coverRatio': i.isEven ? 1.0 : 0.75,
                  'likeCount': 1200,
                  'copyCount': i == 1 ? 128 : 0,
                },
            ],
            'nextCursor': null,
          });
        }
        return okResponse(null);
      });
      await pumpPage(tester, const CommunityPage());
      await tester.pumpAndSettle();

      expect(find.text('#一周不重样'), findsOneWidget);
      expect(find.text('第 1 道菜'), findsOneWidget);
      expect(find.text('第 4 道菜'), findsOneWidget);
      expect(find.text('照着做 128'), findsOneWidget);
      expect(find.text('1.2k'), findsNWidgets(4));
      await tester.scrollUntilVisible(find.text('没有更多了'), 300, scrollable: find.descendant(of: find.byKey(const Key('community-feed')), matching: find.byType(Scrollable)).first);
      expect(find.text('没有更多了'), findsOneWidget);

      await tester.tap(find.text('关注'));
      await tester.pumpAndSettle();
      expect(a.requests.last.queryParameters['tab'], 'follow');
    });

    testWidgets('Feed：接口未部署 → 即将开放空态', (tester) async {
      installShowcase((_) => const HttpError(404));
      await pumpPage(tester, const CommunityPage());
      await tester.pumpAndSettle();
      expect(find.text('厨房圈即将开放'), findsOneWidget);
    });
  });

  group('一周餐程 / 营养报告', () {
    Map<String, dynamic> draft({String dinnerMon = '蒜蓉西兰花'}) => {
          'draftId': 5,
          'weekStart': '2026-10-19',
          'weekEnd': '2026-10-25',
          'check': {'passed': true, 'rounds': 2, 'notes': ['钠日均 1.8g']},
          'days': [
            for (var i = 0; i < 7; i++)
              {
                'date': '2026-10-${19 + i}',
                'slots': [
                  {'slotId': i * 2 + 1, 'meal': 'LUNCH', 'dish': {'id': 100 + i, 'name': '午$i'}, 'badges': i == 0 ? ['BABY'] : []},
                  {'slotId': i * 2 + 2, 'meal': 'DINNER', 'dish': {'id': 200 + i, 'name': i == 0 ? dinnerMon : '晚$i'}},
                ],
              },
          ],
          'summary': {'estCostFen': 32600, 'newItems': 18, 'usesExpiring': 3},
        };

    testWidgets('从周一排起；点格子换一道 → 替换；生成食集', (tester) async {
      final a = installShowcase((o) {
        if (o.path == '/mealplan/drafts') return okResponse(draft());
        if (o.path.endsWith('/alternatives')) {
          return okResponse({'items': [{'id': 300, 'name': '韭菜炒鸡蛋', 'sodiumMg': 600}]});
        }
        if (o.method == 'PUT') return okResponse(draft(dinnerMon: '韭菜炒鸡蛋'));
        if (o.path.endsWith('/apply')) return okResponse({'menuIds': [1, 2, 3, 4, 5, 6, 7], 'shoppingItemCount': 18});
        return okResponse(null);
      });
      await pumpPage(tester, const WeekPlanPage());
      await tester.pumpAndSettle();

      final req = a.requests.firstWhere((r) => r.path == '/mealplan/drafts');
      expect(DateTime.parse(req.data['weekStart']).weekday, DateTime.monday);
      expect(req.data['memberIds'], [1, 3, 4]);
      expect(find.text('周一'), findsOneWidget);
      expect(find.text('周日'), findsOneWidget);
      expect(find.text('已排好 7 天 · 校验通过'), findsOneWidget);
      expect(find.text('¥326'), findsOneWidget);
      expect(find.text('宝宝版'), findsOneWidget);
      expect(find.text('妈妈少盐'), findsOneWidget);

      await tester.tap(find.byKey(const Key('plan-slot-2')));
      await tester.pumpAndSettle();
      expect(find.text('换掉 蒜蓉西兰花'), findsOneWidget);
      await tester.tap(find.text('韭菜炒鸡蛋'));
      await tester.pumpAndSettle();
      expect(a.requests.last.data, {'dishId': 300});
      expect(find.text('韭菜炒鸡蛋'), findsOneWidget);

      await tester.tap(find.byKey(const Key('plan-apply')));
      await tester.pumpAndSettle();
      expect(find.text('ROUTE /menu'), findsOneWidget);
    });

    testWidgets('营养报告：默认选有档案的成员，雷达 + 超标卡', (tester) async {
      final a = installShowcase((o) => okResponse({
            'member': {'memberId': 3, 'name': '妈妈', 'profile': '高血压'},
            'recordCount': 11,
            'radar': [
              {'key': 'energy', 'label': '能量', 'actual': 1800, 'target': 1950},
              {'key': 'protein', 'label': '蛋白质', 'actual': 63, 'target': 60},
              {'key': 'fat', 'label': '脂肪', 'actual': 54, 'target': 50},
              {'key': 'sodium', 'label': '钠', 'actual': 2.6, 'target': 2.0},
              {'key': 'fiber', 'label': '膳食纤维', 'actual': 26, 'target': 25},
            ],
            'alerts': [
              {
                'key': 'sodium',
                'label': '钠',
                'dailyAvg': 2.6,
                'limit': 2,
                'unit': 'g',
                'sources': [{'id': 1, 'name': '红烧肉'}],
                'suggestions': [{'id': 2, 'name': '清蒸鲈鱼'}],
              },
            ],
            'trend': {'key': 'sodium', 'points': [{'date': '2026-10-12', 'value': 2.4}, {'date': '2026-10-13', 'value': 1.6}], 'limit': 2},
            'goods': ['膳食纤维达标'],
          }));
      await pumpPage(tester, const NutritionReportPage());
      await tester.pumpAndSettle();
      expect(a.requests.single.queryParameters, {'memberId': 3, 'range': 'week'});
      expect(find.text('钠 日均 2.6g'), findsOneWidget);
      expect(find.text('红烧肉'), findsOneWidget);
      expect(find.text('换进餐程'), findsOneWidget);
      await tester.scrollUntilVisible(find.text('膳食纤维达标'), 300,
          scrollable: find.descendant(of: find.byKey(const Key('nutrition-list')), matching: find.byType(Scrollable)).first);
      expect(find.text('膳食纤维达标'), findsOneWidget);
    });
  });

  group('拍照识别结果', () {
    testWidgets('待确认项默认不勾选；勾上后放进余量提交', (tester) async {
      final a = installShowcase((o) => okResponse({'added': 2}));
      final job = VisionJob.fromJson({
        'jobId': 8,
        'mode': 'FRIDGE',
        'status': 'DONE',
        'items': [
          {'name': '番茄', 'rawName': '西红柿', 'confidence': 0.94, 'amount': 3, 'unit': '个', 'ingredientId': 11},
          {'name': '菠菜', 'confidence': 0.72, 'amount': 1, 'unit': '把', 'alternatives': ['油麦菜']},
        ],
      });
      await pumpPage(tester, ScanResultPage(job: job));
      await tester.pumpAndSettle();

      expect(find.text('识别到 2 样'), findsOneWidget);
      expect(find.text('西红柿 → 已匹配为 番茄'), findsOneWidget);
      expect(find.text('放进余量 · 1 样'), findsOneWidget);

      await tester.tap(find.text('油麦菜'));
      await tester.pumpAndSettle();
      expect(find.text('放进余量 · 2 样'), findsOneWidget);

      await tester.tap(find.byKey(const Key('scan-apply')));
      await tester.pumpAndSettle();
      final items = a.requests.single.data['items'] as List;
      expect(items.map((e) => e['name']), ['番茄', '油麦菜']);
      expect(items.first['ingredientId'], 11);
      expect(items.last.containsKey('ingredientId'), isFalse, reason: '改名后不再沿用识别出的食材 id');
    });
  });

  group('跟做模式', () {
    testWidgets('时长生成计时器并倒计时；最后一步「做好了」返回 true', (tester) async {
      installShowcase((_) => okResponse(null));
      final detail = DishDetail.fromJson({
        'dish': {'id': 9, 'name': '麻婆豆腐'},
        'steps': [
          {'seq': 1, 'text': '豆腐切块，焯水 1 分钟'},
          {'seq': 2, 'text': '放入豆腐块，转小火煮 3 分钟，让豆腐吃进味道'},
        ],
        'ingredients': [],
      });
      bool? result;
      await pumpPage(
        tester,
        Builder(
          builder: (ctx) => Scaffold(
            body: Center(
              child: TextButton(
                onPressed: () async {
                  result = await Navigator.of(ctx).push<bool>(
                    MaterialPageRoute(builder: (_) => CookModePage(dishId: 9, detail: detail)),
                  );
                },
                child: const Text('OPEN'),
              ),
            ),
          ),
        ),
      );
      await tester.tap(find.text('OPEN'));
      await tester.pumpAndSettle();

      expect(find.text('第 1 / 2 步 · 还剩约 4 分钟'), findsOneWidget);
      await tester.tap(find.byKey(const Key('cook-timer-60')));
      await tester.pump();
      expect(find.text('1:00'), findsOneWidget);
      await tester.pump(const Duration(seconds: 3));
      expect(find.text('0:57'), findsOneWidget);

      await tester.tap(find.byKey(const Key('cook-next')));
      await tester.pump();
      expect(find.text('第 2 / 2 步 · 还剩约 3 分钟'), findsOneWidget);
      expect(find.text('转小火煮 3:00'), findsOneWidget);
      expect(find.text('做好了'), findsOneWidget);

      await tester.tap(find.byKey(const Key('cook-next')));
      await tester.pumpAndSettle();
      expect(result, isTrue);
    });
  });
}
