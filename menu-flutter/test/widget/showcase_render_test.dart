import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import 'package:menu_flutter/core/app_theme.dart';
import 'package:menu_flutter/pages/agent/agent_voice_page.dart';
import 'package:menu_flutter/pages/community/post_create_page.dart';
import 'package:menu_flutter/pages/community/post_detail_page.dart';
import 'package:menu_flutter/pages/community/topic_page.dart';
import 'package:menu_flutter/pages/vision/scan_page.dart';
import 'package:menu_flutter/stores/auth_store.dart';
import 'package:menu_flutter/stores/member_store.dart';
import '../helpers/mock_http.dart';
import '../helpers/showcase_mock.dart';

/// 其余新页面在手机尺寸（360×800 逻辑像素）下渲染无溢出 + 关键内容在位。
void main() {
  Future<void> pump(WidgetTester tester, Widget page) async {
    tester.view.physicalSize = const Size(1080, 2400);
    tester.view.devicePixelRatio = 3;
    addTearDown(tester.view.reset);
    final router = GoRouter(initialLocation: '/t', routes: [
      GoRoute(path: '/t', builder: (_, __) => page),
      GoRoute(path: '/:a/:b', builder: (_, s) => Scaffold(body: Text('ROUTE ${s.uri}'))),
    ]);
    await tester.pumpWidget(MultiProvider(
      providers: [
        ChangeNotifierProvider<AuthStore>(create: (_) => AuthStore()..nickname = '小王'),
        ChangeNotifierProvider<MemberStore>(create: (_) => MemberStore()),
      ],
      child: MaterialApp.router(theme: buildBrandTheme(AppTokens.cream), routerConfig: router),
    ));
    await tester.pumpAndSettle();
  }

  testWidgets('动态详情：作者、照着做、两级评论、底部赞藏', (tester) async {
    installShowcase((o) {
      if (o.path == '/community/posts/5') {
        return okResponse({
          'postId': 5,
          'author': {'userId': 2, 'nickname': '糖糖酱'},
          'title': '第一次做清蒸鲈鱼就成功了！火候和时间都记下来了',
          'text': '一斤二两的鲈鱼，背上划刀摆成孔雀开屏，水开后大火蒸 8 分钟，关火再焖 2 分钟。',
          'images': ['a', 'b', 'c'],
          'topics': [{'topicId': 9, 'name': '#一周不重样'}],
          'recipe': {'id': 1, 'name': '清蒸鲈鱼', 'minutes': 25, 'copyCount': 128, 'coverage': {'have': 5, 'total': 6, 'missing': ['小葱']}},
          'likeCount': 1200,
          'collectCount': 386,
          'commentCount': 2,
          'createdAt': DateTime.now().subtract(const Duration(days: 2)).toIso8601String(),
          'city': '上海',
        });
      }
      if (o.path.endsWith('/copies')) return okResponse({'items': [{'user': {'userId': 3, 'nickname': '米米妈'}, 'imageUrl': 'x'}]});
      if (o.path.endsWith('/comments')) {
        return okResponse({
          'items': [
            {
              'commentId': 1,
              'author': {'userId': 4, 'nickname': '阿May'},
              'text': '鱼要冷水下锅吗？',
              'replies': [
                {'commentId': 2, 'author': {'userId': 2, 'nickname': '糖糖酱'}, 'text': '水开了再放', 'isAuthor': true},
              ],
            },
          ],
          'nextCursor': null,
        });
      }
      return okResponse(null);
    });
    await pump(tester, const PostDetailPage(postId: 5));
    expect(find.text('糖糖酱'), findsWidgets);
    expect(find.text('2 天前 · 上海'), findsOneWidget);
    expect(find.text('你家够 5/6 样，只差小葱'), findsOneWidget);
    expect(find.text('照着做 · 复制到我家'), findsOneWidget);
    expect(find.text('作者'), findsOneWidget);
    expect(find.text('1.2k'), findsOneWidget);
  });

  testWidgets('发布：从食记带入 + 话题 + 隐私提示；AI 帮写出草稿', (tester) async {
    installShowcase((o) {
      if (o.path == '/community/banner') return okResponse({'topic': {'topicId': 9, 'name': '#一周不重样'}});
      if (o.path == '/community/drafts/ai-text') return okResponse({'text': '早上赶时间做的，十分钟上桌。'});
      return okResponse(null);
    });
    await pump(
      tester,
      const PostCreatePage(
        seed: PostDraftSeed(dishId: 3, dishName: '滑蛋虾仁', fromText: '今天 18:40 做了 滑蛋虾仁', imageUrls: ['a', 'b', 'c'], topicId: 9, topicName: '#一周不重样'),
      ),
    );
    expect(find.text('从食记带入'), findsOneWidget);
    expect(find.text('今天 18:40 做了 滑蛋虾仁 · 已带入 3 张照片'), findsOneWidget);
    expect(find.text('封面'), findsOneWidget);
    await tester.tap(find.byKey(const Key('post-ai')));
    await tester.pumpAndSettle();
    expect(find.text('早上赶时间做的，十分钟上桌。'), findsOneWidget);
    await tester.tap(find.text('用这段'));
    await tester.pumpAndSettle();
    await tester.scrollUntilVisible(find.textContaining('不会带上家庭成员名'), 200, scrollable: find.byType(Scrollable).first);
    expect(find.text('#一周不重样'), findsOneWidget);
  });

  testWidgets('话题挑战：头图、本周进度 7 格（周一起）、排行', (tester) async {
    final monday = DateTime.now().subtract(Duration(days: DateTime.now().weekday - 1));
    String d(int i) => monday.add(Duration(days: i)).toIso8601String().substring(0, 10);
    installShowcase((o) {
      if (o.path == '/community/topics/9') {
        return okResponse({
          'topicId': 9,
          'name': '#一周不重样',
          'rule': '连续 7 天，每天做一道不重样的家常菜并发布',
          'participantCount': 2100,
          'postCount': 16000,
          'startDate': d(0),
          'endDate': d(6),
          'myProgress': {
            'days': [for (var i = 0; i < 7; i++) {'date': d(i), 'done': i < 2, 'coverUrl': 'x'}],
            'doneCount': 2,
          },
          'ranking': [
            {'user': {'userId': 1, 'nickname': '陈师傅'}, 'days': 7, 'likeCount': 3400, 'rank': 1},
          ],
          'myRank': 238,
        });
      }
      return okResponse({'items': [], 'nextCursor': null});
    });
    await pump(tester, const TopicPage(topicId: 9));
    expect(find.text('#一周不重样'), findsOneWidget);
    expect(find.text('2.1k 人参与'), findsOneWidget);
    expect(find.text('周一'), findsOneWidget);
    expect(find.text('我 · 第 238 名'), findsOneWidget);
  });

  testWidgets('拍照识别取景页：三种模式切换提示', (tester) async {
    installShowcase((_) => okResponse(null));
    await pump(tester, const ScanPage());
    expect(find.text('对准冰箱隔层，一次拍一层'), findsOneWidget);
    await tester.tap(find.byKey(const Key('scan-mode-receipt')));
    await tester.pump();
    expect(find.text('把小票摊平，拍全商品明细'), findsOneWidget);
  });

  testWidgets('语音页：文字降级 → 生成做完回写确认卡', (tester) async {
    installShowcase((o) {
      if (o.path == '/agent/sessions') return okResponse({'sessionId': 's9'});
      return SseBody(sse('action', {
            'actionId': 'c1',
            'tool': 'mark_cooked',
            'title': '麻婆豆腐做好了',
            'preview': {
              'dish': {'id': 9, 'name': '麻婆豆腐'},
              'servings': 3,
              'eaters': [{'memberId': 1, 'name': '小王'}, {'memberId': 2, 'name': '林林'}, {'memberId': 3, 'name': '妈妈'}],
              'lines': [
                {'name': '北豆腐', 'delta': '-1 块', 'after': '0', 'status': 'USED_UP'},
                {'name': '郫县豆瓣酱', 'delta': '适量', 'status': 'SKIP'},
              ],
            },
            'status': 'PENDING',
          }) +
          sse('done', {}));
    });
    await pump(tester, const AgentVoicePage());
    await tester.enterText(find.byKey(const Key('voice-text')), '麻婆豆腐做完了，我们三个人吃的');
    await tester.tap(find.byKey(const Key('voice-send')));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 300));
    await tester.pump(const Duration(milliseconds: 300));
    expect(find.text('麻婆豆腐做好了'), findsOneWidget);
    expect(find.text('3 人份'), findsOneWidget);
    expect(find.text('用完了'), findsOneWidget);
    expect(find.text('不扣'), findsOneWidget);
    expect(find.text('林林'), findsOneWidget);
  });

  testWidgets('语音页：改一下（人份 / 谁吃了 / 用量）→ revise → 新卡原位替换', (tester) async {
    final a = installShowcase((o) {
      if (o.path == '/agent/sessions') return okResponse({'sessionId': 's9'});
      if (o.path == '/agent/actions/c1/revise') {
        return okResponse({
          'actionId': 'c2',
          'tool': 'mark_cooked',
          'title': '麻婆豆腐做好了 · 已修改',
          'preview': {
            'dish': {'id': 9, 'name': '麻婆豆腐'},
            'servings': 4,
            'eaters': [{'memberId': 1, 'name': '小王'}],
            'lines': [{'name': '北豆腐', 'delta': '-半块', 'after': '半块', 'status': 'LEFT'}],
          },
          'status': 'PENDING',
        });
      }
      return SseBody(sse('action', {
            'actionId': 'c1',
            'tool': 'mark_cooked',
            'title': '麻婆豆腐做好了',
            'preview': {
              'dish': {'id': 9, 'name': '麻婆豆腐'},
              'servings': 3,
              'eaters': [{'memberId': 1, 'name': '小王'}, {'memberId': 2, 'name': '林林'}],
              'lines': [{'name': '北豆腐', 'ingredientId': 5, 'delta': '-1 块', 'after': '0', 'status': 'USED_UP'}],
            },
            'status': 'PENDING',
          }) +
          sse('done', {}));
    });
    await pump(tester, const AgentVoicePage());
    await tester.enterText(find.byKey(const Key('voice-text')), '麻婆豆腐做完了');
    await tester.tap(find.byKey(const Key('voice-send')));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 300));

    await tester.ensureVisible(find.byKey(const Key('action-revise-c1')));
    await tester.tap(find.byKey(const Key('action-revise-c1')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('revise-servings-plus')));
    await tester.tap(find.byKey(const Key('revise-eater-2'))); // 林林没吃
    await tester.tap(find.byKey(const Key('revise-line-0-LEFT')));
    await tester.pump();
    await tester.enterText(find.byKey(const Key('revise-amount-0')), '半块');
    await tester.tap(find.byKey(const Key('revise-save')));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 300));

    final body = a.requests.firstWhere((r) => r.path == '/agent/actions/c1/revise').data as Map;
    final args = body['args'] as Map;
    expect(args['servings'], 4);
    expect(args['eaterIds'], [1]);
    expect(args['lines'], [
      {'name': '北豆腐', 'ingredientId': 5, 'status': 'LEFT', 'amount': '半块'},
    ]);
    expect(find.text('麻婆豆腐做好了 · 已修改'), findsOneWidget);
    expect(find.text('麻婆豆腐做好了'), findsNothing, reason: '旧卡被新卡原位替换');
    expect(find.text('4 人份'), findsOneWidget);
    expect(find.byKey(const Key('action-confirm-c2')), findsOneWidget);
  });

  testWidgets('从食记发布：话题来自 topics?status=ACTIVE，提交带 fromFoodLogId', (tester) async {
    final a = installShowcase((o) {
      if (o.path == '/community/topics') {
        return okResponse([
          {'topicId': 9, 'name': '#一周不重样', 'weekNo': 3},
          {'topicId': 10, 'name': '#宝宝辅食'},
        ]);
      }
      if (o.path == '/community/posts') return okResponse({'postId': 1, 'status': 'PUBLISHED'});
      return okResponse(null);
    });
    await pump(tester, const PostCreatePage(seed: PostDraftSeed(foodLogId: 77, dishId: 3, dishName: '滑蛋虾仁', fromText: '10/16 18:40 做了 滑蛋虾仁')));
    expect(a.requests.any((r) => r.path == '/community/topics' && r.queryParameters['status'] == 'ACTIVE'), isTrue);
    expect(find.text('#宝宝辅食'), findsOneWidget);
    expect(find.textContaining('发布时自动带入这顿饭的照片'), findsOneWidget);

    await tester.enterText(find.byKey(const Key('post-title')), '10 分钟的滑蛋虾仁');
    await tester.tap(find.byKey(const Key('post-publish')));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 300));
    final body = a.requests.firstWhere((r) => r.path == '/community/posts').data as Map;
    expect(body['fromFoodLogId'], 77);
    expect(body.containsKey('fromCookRecordId'), isFalse);
    expect(body.containsKey('menuId'), isFalse);
  });
}
