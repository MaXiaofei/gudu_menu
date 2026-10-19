import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';

import 'package:menu_flutter/core/step_timers.dart';
import 'package:menu_flutter/models/cursor_page.dart';
import 'package:menu_flutter/services/agent_service.dart';
import 'package:menu_flutter/services/community_service.dart';
import 'package:menu_flutter/services/mealplan_service.dart';
import 'package:menu_flutter/services/vision_service.dart';

/// showcase-ai-community 纯逻辑：SSE 解析、事件折叠、游标分页、计时解析、周一计算、瀑布流分配。
void main() {
  group('SseParser', () {
    test('event + 多行 data，空行分隔；注释行忽略', () {
      final p = SseParser();
      expect(p.addLine(': ping'), isNull);
      expect(p.addLine('event: tool_start'), isNull);
      expect(p.addLine('data: {"name":"get_pantry",'), isNull);
      expect(p.addLine('data: "label":"查余量"}'), isNull);
      final e = p.addLine('')!;
      expect(e.event, 'tool_start');
      expect(e.data['name'], 'get_pantry');
      expect(e.data['label'], '查余量');
    });

    test('CRLF 行尾、data 非 JSON 包成 text、缺省事件名为 message', () {
      final p = SseParser();
      p.addLine('data: 你好\r');
      final e = p.addLine('\r')!;
      expect(e.event, 'message');
      expect(e.data['text'], '你好');
    });

    test('流结束时吐出未以空行结束的事件', () {
      final p = SseParser();
      p.addLine('event: done');
      p.addLine('data: {"degraded":true}');
      final e = p.close()!;
      expect(e.event, 'done');
      expect(e.data['degraded'], true);
      expect(p.close(), isNull);
    });

    test('transform：跨 chunk 的半行与被切开的汉字 UTF-8 字节都能拼对', () async {
      const raw = 'event: token\ndata: {"text":"清蒸鲈鱼"}\n\nevent: done\ndata: {}\n\n';
      final bytes = utf8.encode(raw);
      // 每 5 字节切一块：必然把某个汉字的 3 字节切开
      final chunks = <List<int>>[for (var i = 0; i < bytes.length; i += 5) bytes.sublist(i, (i + 5).clamp(0, bytes.length))];
      final events = await SseParser.transform(Stream.fromIterable(chunks)).toList();
      expect(events.map((e) => e.event), ['token', 'done']);
      expect(events.first.data['text'], '清蒸鲈鱼');
    });
  });

  group('AgentReducer', () {
    test('按 contracts 事件序列折叠出完整消息', () {
      var m = const AgentMessage(messageId: 'local', role: 'assistant', streaming: true);
      final seq = [
        const SseEvent('meta', {'messageId': 'm1', 'traceId': '7f3a91c2abcd'}),
        const SseEvent('tool_start', {'name': 'get_pantry', 'label': '查了余量'}),
        const SseEvent('tool_end', {'name': 'get_pantry', 'summary': '29 样 · 4 样快到期', 'ms': 45, 'ok': true}),
        const SseEvent('tool_start', {'name': 'search_recipes', 'label': '检索菜谱'}),
        const SseEvent('card', {
          'type': 'recipes',
          'items': [
            {'id': 1, 'name': '清蒸鲈鱼', 'coverage': {'have': 4, 'total': 5, 'missing': ['小葱']}},
          ]
        }),
        const SseEvent('token', {'text': '鲈鱼明天到期，'}),
        const SseEvent('token', {'text': '先做它。'}),
        const SseEvent('action', {
          'actionId': 'a1',
          'tool': 'add_to_shopping',
          'title': '采购 2 样',
          'preview': {'items': [{'name': '小葱'}, {'name': '减盐蒸鱼豉油'}]},
          'expiresAt': '2026-10-16T18:05:00',
          'status': 'PENDING',
        }),
        const SseEvent('done', {'usage': {}, 'degraded': false}),
      ];
      for (final e in seq) {
        m = AgentReducer.apply(m, e);
      }
      expect(m.messageId, 'm1');
      expect(m.shortTraceId, '7f3a-91c2');
      expect(m.text, '鲈鱼明天到期，先做它。');
      expect(m.tools.length, 2);
      expect(m.tools.first.running, isFalse);
      expect(m.tools.first.summary, '29 样 · 4 样快到期');
      expect(m.tools.last.running, isTrue, reason: '检索没收到 tool_end，仍显示进行中');
      expect(m.cards.single.recipes.single.coverageText, '缺 1 样 · 小葱');
      expect(m.actions.single.previewNames, ['小葱', '减盐蒸鱼豉油']);
      expect(m.actions.single.expiredAt(DateTime(2026, 10, 16, 18, 6)), isTrue);
      expect(m.streaming, isFalse);
    });

    test('error 事件：停止流式并带报错编号', () {
      final m = AgentReducer.apply(const AgentMessage(messageId: 'x', role: 'assistant', streaming: true),
          const SseEvent('error', {'code': 500, 'message': '模型超时', 'errorId': 'abcd1234ef'}));
      expect(m.streaming, isFalse);
      expect(m.error, '模型超时');
      expect(m.shortTraceId, 'abcd-1234');
    });

    test('mark_cooked 预览解析', () {
      final a = PendingAction.fromJson({
        'actionId': 'c1',
        'tool': 'mark_cooked',
        'title': '麻婆豆腐做好了',
        'preview': {
          'dish': {'id': 9, 'name': '麻婆豆腐'},
          'servings': 3,
          'eaters': [
            {'memberId': 1, 'name': '小王'},
            {'memberId': 2, 'name': '林林'},
          ],
          'lines': [
            {'name': '北豆腐', 'delta': '-1 块', 'after': '0', 'status': 'USED_UP'},
            {'name': '郫县豆瓣酱', 'delta': '适量', 'status': 'SKIP'},
          ],
        },
      });
      expect(a.cookedDish!.name, '麻婆豆腐');
      expect(a.servings, 3);
      expect(a.eaters.map((e) => e.name), ['小王', '林林']);
      expect(a.lines.first.status, 'USED_UP');
    });
  });

  group('CursorFeed', () {
    test('按游标翻页直到 nextCursor 为空；重复 id 去重', () async {
      final feed = CursorFeed<int>(idOf: (i) => i);
      final calls = <String?>[];
      Future<CursorPage<int>> fetch(String? c) async {
        calls.add(c);
        if (c == null) return const CursorPage(items: [1, 2, 3], nextCursor: 'c1');
        return const CursorPage(items: [3, 4], nextCursor: null);
      }

      expect(await feed.load(fetch), 3);
      expect(feed.hasMore, isTrue);
      expect(await feed.load(fetch), 1, reason: '3 已出现过');
      expect(feed.items, [1, 2, 3, 4]);
      expect(feed.hasMore, isFalse);
      expect(await feed.load(fetch), 0, reason: '没有更多不再请求');
      expect(calls, [null, 'c1']);
      feed.reset();
      expect(feed.items, isEmpty);
      expect(feed.hasMore, isTrue);
    });

    test('CursorPage.fromJson 宽松解析', () {
      final p = CursorPage.fromJson({'items': [{'v': 1}], 'nextCursor': 42}, (m) => m['v'] as int);
      expect(p.items, [1]);
      expect(p.nextCursor, '42');
      expect(CursorPage.fromJson(null, (m) => 0).items, isEmpty);
    });
  });

  group('parseStepTimers', () {
    int secs(String s) => parseStepTimers(s).single.seconds;

    test('分钟 / 秒 / 小时 / 半小时 / 中文数字 / 时钟格式', () {
      expect(secs('大火蒸 8 分钟'), 480);
      expect(secs('焖8分钟'), 480);
      expect(secs('小火煮3:00，让豆腐吃进味道'), 180);
      expect(secs('炖 1 小时'), 3600);
      expect(secs('腌制半小时'), 1800);
      expect(secs('煮半个小时'), 1800);
      expect(secs('炖一个半小时'), 5400);
      expect(secs('焯水 30 秒'), 30);
      expect(secs('煮十五分钟'), 900);
      expect(secs('蒸两分钟'), 120);
    });

    test('区间取上限，「1 小时 20 分钟」合并为一个', () {
      expect(secs('中火煎 5-8 分钟'), 480);
      expect(secs('炖 1 小时 20 分钟'), 4800);
    });

    test('一步多个计时、非时长不误判', () {
      final ts = parseStepTimers('先焯水 2 分钟捞出，再大火蒸 8 分钟');
      expect(ts.map((e) => e.seconds), [120, 480]);
      expect(ts.last.action, '大火蒸');
      expect(parseStepTimers('牛排煎到七分熟'), isEmpty);
      expect(parseStepTimers('三分之一的盐'), isEmpty);
      expect(parseStepTimers('放入豆腐块，轻轻推开'), isEmpty);
    });

    test('动作词作为计时器名', () {
      expect(parseStepTimers('转小火煮 3 分钟').single.label, '转小火煮 3:00');
    });

    test('formatClock / parseChineseNumber', () {
      expect(formatClock(760), '12:40');
      expect(formatClock(45), '0:45');
      expect(formatClock(3900), '1:05:00');
      expect(parseChineseNumber('二十五'), 25);
      expect(parseChineseNumber('十'), 10);
      expect(parseChineseNumber('两'), 2);
      expect(parseChineseNumber('两三'), isNull);
    });
  });

  group('周一至周日', () {
    test('defaultWeekStart：周五 → 下周一；周一 → 本周一；周日 → 次日', () {
      expect(defaultWeekStart(DateTime(2026, 10, 16, 18)), DateTime(2026, 10, 19));
      expect(defaultWeekStart(DateTime(2026, 10, 19, 8)), DateTime(2026, 10, 19));
      expect(defaultWeekStart(DateTime(2026, 10, 18)), DateTime(2026, 10, 19));
      expect(defaultWeekStart(DateTime(2026, 12, 29)), DateTime(2027, 1, 4), reason: '跨年');
    });

    test('mondayOf / ymd / md', () {
      expect(mondayOf(DateTime(2026, 10, 16)), DateTime(2026, 10, 12));
      expect(mondayOf(DateTime(2026, 10, 18)), DateTime(2026, 10, 12));
      expect(ymd(DateTime(2026, 10, 9)), '2026-10-09');
      expect(md(DateTime(2026, 10, 9)), '10.09');
      expect(yuan(32600), '¥326');
      expect(yuan(32650), '¥326.5');
    });
  });

  group('厨房圈工具', () {
    test('splitMasonry：每张进更矮的一列，顺序稳定', () {
      final s = splitMasonry([0.75, 1.33, 0.75, 0.75, 1.0]);
      expect(s.left.first, 0);
      expect(s.right.first, 1);
      expect([...s.left, ...s.right]..sort(), [0, 1, 2, 3, 4]);
      for (final col in [s.left, s.right]) {
        expect(col, List.of(col)..sort(), reason: '列内保持原顺序');
      }
    });

    test('compactCount / relativeTime', () {
      expect(compactCount(856), '856');
      expect(compactCount(1200), '1.2k');
      expect(compactCount(2000), '2k');
      expect(compactCount(16000), '1.6w');
      final now = DateTime(2026, 10, 16, 18);
      expect(relativeTime(now.subtract(const Duration(days: 2)), now), '2 天前');
      expect(relativeTime(now.subtract(const Duration(minutes: 5)), now), '5 分钟前');
      expect(relativeTime(DateTime(2026, 9, 3), now), '9-03');
    });

    test('Topic.daysLeft 含当天', () {
      final t = Topic(topicId: 1, name: '#一周不重样', endDate: DateTime(2026, 10, 18));
      expect(t.daysLeft(DateTime(2026, 10, 16, 20)), 3);
    });
  });

  group('拍照识别分级', () {
    test('≥0.8 AUTO，0.5–0.8 CONFIRM，<0.5 不展示；改名提示', () {
      final job = VisionJob.fromJson({
        'jobId': 3,
        'mode': 'FRIDGE',
        'status': 'DONE',
        'items': [
          {'name': '番茄', 'rawName': '西红柿', 'confidence': 0.94},
          {'name': '菠菜', 'confidence': 0.72},
          {'name': '不明物体', 'confidence': 0.3},
        ],
      });
      expect(job.items.length, 2);
      expect(job.items.first.level, 'AUTO');
      expect(job.items.first.renamed, isTrue);
      expect(job.confirmCount, 1);
    });
  });
}

