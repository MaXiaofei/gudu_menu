import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:dio/dio.dart';

import 'package:menu_flutter/core/app_theme.dart';
import 'package:menu_flutter/pages/pantry/list_page.dart';
import 'package:menu_flutter/widgets/initial_avatar.dart';
import '../helpers/mock_http.dart';

/// 给 widget 测试注入 AppTokens 主题。
Widget _themed(Widget child) => MaterialApp(
      theme: ThemeData(extensions: const [AppTokens.cream]),
      home: child,
    );

/// 库存主页 widget 测试（分页 + 搜索版，对齐 44829 批次 pantry-page.html 定稿）：
/// 搜索框 + 筛选条（全部/缺/低/够）替代三色汇总条 + 点选筛档 + 三组独立分页 + 搜索平铺。
void main() {
  // 三档各 2/1/2 条，覆盖 缺/低/够
  // ignore: prefer_final_locals
  List<Map<String, dynamic>> allItems = [
    {
      'ingredientId': 1,
      'ingredientName': '鸡蛋', 'level': 'NONE',
      'lastChange': {
        'source': 'cook', 'sourceNote': null,
        'createTime': '2026-08-07T19:00:00',
      },
    },
    {
      'ingredientId': 2,
      'ingredientName': '鲈鱼', 'level': 'NONE',
      'lastChange': null,
    },
    {
      'ingredientId': 3,
      'ingredientName': '牛奶', 'level': 'LOW',
      'lastChange': null,
    },
    {
      'ingredientId': 4,
      'ingredientName': '大米', 'level': 'ENOUGH',
      'lastChange': {
        'source': 'purchase', 'sourceNote': null,
        'createTime': '2026-07-02T10:00:00',
      },
    },
    {
      'ingredientId': 5,
      'ingredientName': '苹果', 'level': 'ENOUGH',
      'lastChange': {
        'source': 'manual', 'sourceNote': '朋友送',
        'createTime': '2026-07-02T11:00:00',
      },
    },
  ];

  Map<String, dynamic> summaryOf(List<Map<String, dynamic>> list) {
    var enough = 0, low = 0, none = 0;
    for (final it in list) {
      switch (it['level']) {
        case 'ENOUGH': enough++; break;
        case 'LOW': low++; break;
        default: none++;
      }
    }
    return {'enough': enough, 'low': low, 'none': none};
  }

  /// 按请求参数（level/keyword/pageNum/pageSize）切片，模拟后端分页。
  /// 注：dio 的 queryParameters 值可能是 int（pageNum/pageSize），统一按字符串解析。
  Map<String, dynamic> groupedFor(RequestOptions options) {
    final qp = options.queryParameters;
    final kw = (qp['keyword'] as String?) ?? '';
    final level = qp['level'] as String?;
    final pageNum = int.parse('${qp['pageNum'] ?? '1'}');
    final pageSize = int.parse('${qp['pageSize'] ?? '10'}');
    final scoped = allItems
        .where((it) =>
            (it['ingredientName'] as String).contains(kw) &&
            (level == null || it['level'] == level))
        .toList();
    final start = (pageNum - 1) * pageSize;
    return {
      'summary': summaryOf(allItems.where((it) =>
          (it['ingredientName'] as String).contains(kw)).toList()),
      'items': scoped.skip(start).take(pageSize).toList(),
    };
  }

  Future<void> pumpList(WidgetTester tester) async {
    installMock((options) {
      if (options.path == '/pantry/grouped') return okResponse(groupedFor(options));
      return okResponse({});
    });
    await tester.pumpWidget(_themed(const PantryListPage()));
    await tester.pump(); // 加载中 → 数据
    await tester.pump(const Duration(milliseconds: 50));
  }

  testWidgets('搜索框 + 筛选条替代汇总条 + 缩略图 + 右上角按钮（无 FAB）', (tester) async {
    await pumpList(tester);

    // 搜索框（⌕ + 搜库存）
    expect(find.byType(TextField), findsOneWidget);
    expect(find.text('搜库存'), findsOneWidget);

    // 筛选条带计数：全部/用完/不足/充足
    expect(find.text('全部 5'), findsOneWidget);
    expect(find.text('用完 2'), findsOneWidget);
    expect(find.text('不足 1'), findsOneWidget);
    expect(find.text('充足 2'), findsOneWidget);

    // 分组标题 + 全部行
    expect(find.text('用完 · 2'), findsOneWidget);
    expect(find.text('不足 · 1'), findsOneWidget);
    expect(find.text('充足 · 2'), findsOneWidget);
    expect(find.text('鸡蛋'), findsOneWidget);
    expect(find.text('鲈鱼'), findsOneWidget);
    expect(find.text('牛奶'), findsOneWidget);
    expect(find.text('大米'), findsOneWidget);
    expect(find.text('苹果'), findsOneWidget);

    // 行前置缩略图（首字占位）
    expect(find.byType(InitialAvatar), findsNWidgets(5));

    // 右上角「入库」「去采购」，无右下 FAB
    expect(find.text('入库'), findsOneWidget);
    expect(find.text('去采购'), findsOneWidget);
    expect(find.byType(FloatingActionButton), findsNothing);

    // 手动来源标签
    expect(find.textContaining('手动'), findsOneWidget);
  });

  testWidgets('点「缺」只显示缺组；点「够」只显示够组；回「全部」恢复', (tester) async {
    await pumpList(tester);

    // 用完：只留 鸡蛋/鲈鱼
    await tester.tap(find.text('用完 2'));
    await tester.pump();
    expect(find.text('用完 · 2'), findsOneWidget);
    expect(find.text('鸡蛋'), findsOneWidget);
    expect(find.text('鲈鱼'), findsOneWidget);
    expect(find.text('牛奶'), findsNothing);
    expect(find.text('大米'), findsNothing);

    // 充足：只留 大米/苹果
    await tester.tap(find.text('充足 2'));
    await tester.pump();
    expect(find.text('充足 · 2'), findsOneWidget);
    expect(find.text('大米'), findsOneWidget);
    expect(find.text('苹果'), findsOneWidget);
    expect(find.text('鸡蛋'), findsNothing);
    expect(find.text('牛奶'), findsNothing);

    // 全部：恢复
    await tester.tap(find.text('全部 5'));
    await tester.pump();
    expect(find.text('鸡蛋'), findsOneWidget);
    expect(find.text('牛奶'), findsOneWidget);
    expect(find.text('苹果'), findsOneWidget);
  });

  testWidgets('分页：超过 10 条时组尾「加载更多 · 还有 N 项」，点击补齐', (tester) async {
    // 用完 18 条（名称已按后端排序），其他档不变
    final saved = allItems;
    allItems = [
      ...saved,
      ...List.generate(16, (i) => {
            'ingredientId': 100 + i,
            'ingredientName': '食材${i + 1}', 'level': 'NONE',
            'lastChange': null,
          }),
    ];
    await pumpList(tester);

    // 首屏只显示 10 条（鸡蛋/鲈鱼 + 食材1-8）+ 剩余 8 条
    expect(find.text('用完 · 18'), findsOneWidget);
    expect(find.text('加载更多 · 还有 8 项'), findsOneWidget);
    expect(find.text('食材1'), findsOneWidget);
    expect(find.text('食材8'), findsOneWidget);
    expect(find.text('食材9'), findsNothing); // 未加载
    expect(find.text('食材16'), findsNothing);

    // 点击加载（按钮在首屏下方，先滚动到可见）→ 补齐 18 条，按钮消失
    final moreBtn = find.text('加载更多 · 还有 8 项');
    await tester.ensureVisible(moreBtn);
    await tester.pump();
    await tester.tap(moreBtn);
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));
    expect(find.text('食材10'), findsOneWidget);
    expect(find.text('食材16'), findsOneWidget);
    expect(find.text('加载更多'), findsNothing);

    allItems = saved;
  });

  testWidgets('搜索：输入即搜，结果平铺 + 隐藏筛选条 + 无结果空态', (tester) async {
    await pumpList(tester);

    // 输入「米」→ 防抖后搜索
    await tester.enterText(find.byType(TextField), '米');
    await tester.pump(const Duration(milliseconds: 350)); // 防抖
    await tester.pump();
    expect(find.text('找到 1 个'), findsOneWidget);
    expect(find.text('大米'), findsOneWidget);
    expect(find.text('全部 5'), findsNothing); // 筛选条隐藏
    expect(find.text('鸡蛋'), findsNothing); // 只显示命中

    // 无结果空态
    await tester.enterText(find.byType(TextField), '橙子');
    await tester.pump(const Duration(milliseconds: 350));
    await tester.pump();
    expect(find.text('找到 0 个'), findsOneWidget);
    expect(find.text('搜不到「橙子」'), findsOneWidget);

    // ✕ 清空 → 恢复分组视图（统一 SearchBox 的 ✕ 文字清除按钮）
    await tester.tap(find.text('✕'));
    await tester.pump(const Duration(milliseconds: 350));
    await tester.pump();
    expect(find.text('全部 5'), findsOneWidget);
    expect(find.text('鸡蛋'), findsOneWidget);
  });
}
