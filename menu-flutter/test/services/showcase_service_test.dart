import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:menu_flutter/core/api_client.dart';
import 'package:menu_flutter/services/agent_service.dart';
import 'package:menu_flutter/services/community_service.dart';
import 'package:menu_flutter/services/mealplan_service.dart';
import 'package:menu_flutter/services/vision_service.dart';
import '../helpers/mock_http.dart';

/// showcase-ai-community 接口契约（contracts.md）：请求路径 / 参数 / body 与响应解析。
void main() {
  group('Agent', () {
    test('suggestions 解析首推、临期与快捷提问', () async {
      final captor = installMock((_) => okResponse({
            'greeting': '晚上好，小王',
            'tonight': {'id': 1, 'name': '清蒸鲈鱼', 'coverUrl': 'https://x/original/a.jpg', 'minutes': 25},
            'expiring': [
              {'name': '鲈鱼', 'iconKey': 'fish', 'daysLeft': 1},
            ],
            'prompts': ['冰箱里的能做什么'],
          }));
      final s = await AgentService.suggestions();
      expect(captor.last!.path, '/agent/suggestions');
      expect(s.tonight!.name, '清蒸鲈鱼');
      expect(s.expiring.single.daysLeft, 1);
      expect(s.prompts.single, '冰箱里的能做什么');
    });

    test('createSession / confirm / cancel / undo / feedback 路径', () async {
      final captor = installMock((o) {
        if (o.path == '/agent/sessions') return okResponse({'sessionId': 's1'});
        if (o.path.endsWith('/confirm')) return okResponse({'status': 'DONE', 'result': {}, 'undoToken': 'u1'});
        if (o.path.endsWith('/cancel')) return okResponse({'status': 'CANCELLED'});
        return okResponse({});
      });
      expect(await AgentService.createSession(), 's1');
      const a = PendingAction(actionId: 'a1', tool: 'add_to_menu', title: '加入今晚的饭');
      final done = await AgentService.confirm(a);
      expect(captor.last!.path, '/agent/actions/a1/confirm');
      expect(done.status, 'DONE');
      expect(done.undoToken, 'u1');
      expect((await AgentService.cancel(a)).status, 'CANCELLED');
      await AgentService.undo('u1');
      expect(captor.last!.path, '/agent/actions/undo');
      expect(captor.last!.data, {'undoToken': 'u1'});
      await AgentService.feedback('m1', -1);
      expect(captor.last!.path, '/agent/messages/m1/feedback');
      expect(captor.last!.data, {'rating': -1});
    });

    test('send：SSE 流经 dio 解析成事件，带 context 与 Accept 头', () async {
      installMock((_) => okResponse({}));
      RequestOptions? captured;
      ApiClient.instance.dio.httpClientAdapter = _SseAdapter((o) {
        captured = o;
        return 'event: meta\ndata: {"messageId":"m9","traceId":"t1"}\n\n'
            'event: token\ndata: {"text":"好的"}\n\n'
            'event: done\ndata: {"degraded":false}\n\n';
      });
      final events = await AgentService.send('s1',
              text: '没有郫县豆瓣酱用什么代替？',
              context: const AgentContext(page: 'cook_mode', dishId: 9, step: 5))
          .toList();
      expect(captured!.path, '/agent/sessions/s1/messages');
      expect(captured!.headers['Accept'], 'text/event-stream');
      expect(captured!.data['context'], {'page': 'cook_mode', 'dishId': 9, 'step': 5});
      expect(events.map((e) => e.event), ['meta', 'token', 'done']);
    });

    test('isNotDeployed：404 视为接口未上线', () {
      final e = DioException(
        requestOptions: RequestOptions(path: '/agent/suggestions'),
        response: Response(requestOptions: RequestOptions(path: '/'), statusCode: 404),
      );
      expect(isNotDeployed(e), isTrue);
      expect(isNotDeployed(Exception('x')), isFalse);
      // 旧版后端：HTTP 200 + code=1 + NoResourceFoundException
      final old = DioException(
        requestOptions: RequestOptions(path: '/community/feed'),
        message: '服务器开小差了（3796bb22） [NoResourceFoundException: No static resource community/feed.]',
      );
      expect(isNotDeployed(old), isTrue);
      expect(isNotDeployed(DioException(requestOptions: RequestOptions(path: '/x'), message: '余量不足')), isFalse);
    });
  });

  group('厨房圈', () {
    test('feed：tab / cursor / size 参数与游标响应', () async {
      final captor = installMock((_) => okResponse({
            'items': [
              {
                'postId': 1,
                'author': {'userId': 7, 'nickname': '糖糖酱'},
                'title': '第一次做清蒸鲈鱼就成功了',
                'coverUrl': 'u',
                'coverRatio': 0.1,
                'likeCount': 1200,
              },
            ],
            'nextCursor': 'abc',
          }));
      final p = await CommunityService.feed(FeedTab.follow, cursor: 'c0');
      expect(captor.last!.path, '/community/feed');
      expect(captor.last!.queryParameters, {'tab': 'follow', 'cursor': 'c0', 'size': 20});
      expect(p.items.single.author.nickname, '糖糖酱');
      expect(p.items.single.coverRatio, 0.65, reason: '极端比例被限幅');
      expect(p.nextCursor, 'abc');
    });

    test('publish body：可见范围、话题、食记来源', () async {
      final captor = installMock((_) => okResponse({'postId': 5, 'status': 'REVIEWING'}));
      final r = await CommunityService.publish(
        title: '10 分钟的滑蛋虾仁',
        text: '早上赶时间做的',
        imageUrls: ['a', 'b'],
        dishId: 3,
        topicIds: [1],
        followersOnly: true,
        fromCookRecordId: 88,
      );
      expect(captor.last!.path, '/community/posts');
      expect(captor.last!.data['visibility'], 'FOLLOWERS');
      expect(captor.last!.data['fromCookRecordId'], 88);
      expect(r.status, 'REVIEWING');
    });

    test('reactions / copy-recipe / follow / comment', () async {
      final captor = installMock((o) {
        if (o.path.endsWith('/reactions')) {
          return okResponse({'likeCount': 11, 'collectCount': 3, 'liked': true, 'collected': false});
        }
        if (o.path.endsWith('/copy-recipe')) return okResponse({'dishId': 99});
        if (o.path.endsWith('/follow')) return okResponse({'following': true});
        return okResponse({'commentId': 1, 'author': {'userId': 1, 'nickname': '我'}, 'text': '学到了'});
      });
      final r = await CommunityService.react(5, 'LIKE', true);
      expect(captor.last!.data, {'type': 'LIKE', 'on': true});
      expect(r.likeCount, 11);
      expect(await CommunityService.copyRecipe(5), 99);
      expect(captor.last!.path, '/community/posts/5/copy-recipe');
      expect(await CommunityService.follow(7, true), isTrue);
      final c = await CommunityService.comment(5, '学到了', parentId: 2);
      expect(captor.last!.data, {'text': '学到了', 'parentId': 2});
      expect(c.text, '学到了');
    });

    test('topic 详情：我的进度与排行', () async {
      installMock((_) => okResponse({
            'topicId': 1,
            'name': '#一周不重样',
            'endDate': '2026-10-18',
            'myProgress': {
              'days': [
                {'date': '2026-10-12', 'done': true, 'coverUrl': 'u'},
                {'date': '2026-10-13', 'done': false},
              ],
              'doneCount': 1,
            },
            'ranking': [
              {'user': {'userId': 2, 'nickname': '陈师傅'}, 'days': 5, 'likeCount': 3400, 'rank': 1},
            ],
            'myRank': 238,
          }));
      final t = await CommunityService.topic(1);
      expect(t.days.first.done, isTrue);
      expect(t.ranking.single.user.nickname, '陈师傅');
      expect(t.myRank, 238);
    });
  });

  group('拍照识别', () {
    test('create → get → apply', () async {
      final captor = installMock((o) {
        if (o.method == 'POST' && o.path == '/vision/jobs') return okResponse({'jobId': 3, 'status': 'PENDING'});
        if (o.path.endsWith('/apply')) return okResponse({'added': 8});
        return okResponse({'jobId': 3, 'mode': 'FRIDGE', 'status': 'DONE', 'items': []});
      });
      final j = await VisionService.create(mode: VisionMode.receipt, imageUrl: 'u');
      expect(captor.last!.data, {'mode': 'RECEIPT', 'imageUrl': 'u'});
      expect(j.jobId, 3);
      final done = await VisionService.waitDone(3, interval: Duration.zero);
      expect(done.done, isTrue);
      expect(await VisionService.apply(3, [{'name': '番茄', 'amount': 3, 'unit': '个'}]), 8);
    });
  });

  group('餐程与营养', () {
    test('create body 以周一为起点；解析草稿并按日期排序', () async {
      final captor = installMock((_) => okResponse({
            'draftId': 4,
            'weekStart': '2026-10-19',
            'weekEnd': '2026-10-25',
            'check': {'passed': true, 'rounds': 2, 'notes': ['钠日均 1.8g']},
            'days': [
              {
                'date': '2026-10-20',
                'weekday': '周二',
                'slots': [
                  {'slotId': 3, 'meal': 'LUNCH', 'dish': {'id': 1, 'name': '番茄菜花'}, 'badges': ['EXPIRING']},
                ],
              },
              {
                'date': '2026-10-19',
                'weekday': '周一',
                'slots': [
                  {'slotId': 1, 'meal': 'LUNCH', 'dish': {'id': 2, 'name': '香菇鸡肉粥'}, 'badges': ['BABY']},
                  {'slotId': 2, 'meal': 'DINNER', 'dish': {'id': 1, 'name': '番茄菜花'}},
                ],
              },
            ],
            'summary': {'estCostFen': 32600, 'newItems': 18, 'usesExpiring': 3},
          }));
      final d = await MealPlanService.create(PlanRequest(weekStart: defaultWeekStart(DateTime(2026, 10, 16))));
      expect(captor.last!.path, '/mealplan/drafts');
      expect(captor.last!.data['weekStart'], '2026-10-19');
      expect(d.days.first.weekday, '周一');
      expect(d.days.first.slot('LUNCH')!.baby, isTrue);
      expect(d.dishCount, 3);
      expect(d.distinctDishes, 2);
      expect(d.rounds, 2);
    });

    test('alternatives / replace / apply / report', () async {
      final captor = installMock((o) {
        if (o.path.endsWith('/alternatives')) {
          return okResponse({'items': [{'id': 5, 'name': '韭菜炒鸡蛋', 'sodiumMg': 600}]});
        }
        if (o.path.endsWith('/apply')) return okResponse({'menuIds': [1, 2], 'shoppingItemCount': 18});
        if (o.path == '/nutrition/report') {
          return okResponse({
            'member': {'memberId': 3, 'name': '妈妈', 'profile': '高血压'},
            'radar': [
              {'key': 'sodium', 'label': '钠', 'actual': 2.6, 'target': 2.0, 'unit': 'g'},
            ],
            'trend': {'key': 'sodium', 'points': [{'date': '2026-10-12', 'value': 2.4}], 'limit': 2},
          });
        }
        return okResponse({'draftId': 4, 'days': []});
      });
      final alts = await MealPlanService.alternatives(4, 9);
      expect(captor.last!.path, '/mealplan/drafts/4/slots/9/alternatives');
      expect(alts.single.sodiumMg, 600);
      await MealPlanService.replace(4, 9, 5);
      expect(captor.last!.method, 'PUT');
      expect(captor.last!.data, {'dishId': 5});
      expect((await MealPlanService.apply(4)).menuIds, [1, 2]);
      final r = await MealPlanService.report(3);
      expect(captor.last!.queryParameters, {'memberId': 3, 'range': 'week'});
      expect(r.profile, '高血压');
      expect(r.radar.single.ratio, closeTo(1.3, 0.001));
      expect(r.radar.single.over, isTrue);
      expect(r.trendLimit, 2);
    });
  });
}

/// 返回 text/event-stream 的适配器（分块下发，模拟真实流式）。
class _SseAdapter implements HttpClientAdapter {
  final String Function(RequestOptions) body;
  _SseAdapter(this.body);

  @override
  Future<ResponseBody> fetch(RequestOptions options, Stream<Uint8List>? requestStream, Future<void>? cancelFuture) async {
    final bytes = utf8.encode(body(options));
    final chunks = [for (var i = 0; i < bytes.length; i += 7) Uint8List.fromList(bytes.sublist(i, (i + 7).clamp(0, bytes.length)))];
    return ResponseBody(Stream.fromIterable(chunks), 200, headers: {
      Headers.contentTypeHeader: ['text/event-stream'],
    });
  }

  @override
  void close({bool force = false}) {}
}
