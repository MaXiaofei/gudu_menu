import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:menu_flutter/core/app_theme.dart';
import 'package:menu_flutter/pages/shopping/shopping_page.dart';
import '../helpers/mock_http.dart';

/// 给 widget 测试注入 AppTokens 主题。
Widget _themed(Widget child) => MaterialApp(
      theme: ThemeData(extensions: const [AppTokens.cream]),
      home: child,
    );

/// 采购页 widget 测试（V42 采购唯一家园）：
/// 列表/新建 → 详情（全选+勾选本地态+保存入库·N 项）→ 行尾 ✕（移除/撤回入库）
/// → 自定义添加/改名 → 分享预览。
void main() {
  Map<String, dynamic> detailJson() => {
        'id': 1,
        'name': '周末采购',
        'timeRange': 'custom',
        'startDate': '2026-08-09',
        'endDate': '2026-08-09',
        'sourceLabel': '自定义',
        'dateRange': '8/9',
        'items': [
          // 未入库：番茄（家里：用完）
          {
            'id': 101,
            'ingredientId': 10,
            'ingredientName': '番茄',
            'referenceGrams': 200,
            'purchased': 0,
            'stockStatus': 'RED_NONE',
          },
          // 未入库：鸡蛋（家里：不足）
          {
            'id': 102,
            'ingredientId': 20,
            'ingredientName': '鸡蛋',
            'referenceGrams': 300,
            'purchased': 0,
            'stockStatus': 'YELLOW_SHORT',
          },
          // 已入库：鲈鱼
          {
            'id': 103,
            'ingredientId': 30,
            'ingredientName': '鲈鱼',
            'referenceGrams': 500,
            'purchased': 1,
            'stockStatus': 'GREEN_ENOUGH',
          },
          // 手动加项（未入库，无食材关联）
          {'id': 104, 'customName': '洗洁精', 'purchased': 0},
        ],
        'grouped': {
          '1': [
            {
              'id': 101,
              'ingredientId': 10,
              'ingredientName': '番茄',
              'referenceGrams': 200,
              'purchased': 0,
              'stockStatus': 'RED_NONE',
            },
            {
              'id': 102,
              'ingredientId': 20,
              'ingredientName': '鸡蛋',
              'referenceGrams': 300,
              'purchased': 0,
              'stockStatus': 'YELLOW_SHORT',
            },
          ],
          '2': [
            {
              'id': 103,
              'ingredientId': 30,
              'ingredientName': '鲈鱼',
              'referenceGrams': 500,
              'purchased': 1,
              'stockStatus': 'GREEN_ENOUGH',
            },
            {'id': 104, 'customName': '洗洁精', 'purchased': 0},
          ],
        },
        'categoryNames': {'1': '蔬菜蛋奶', '2': '其他'},
      };

  Map<String, dynamic> listJson() => {
        'records': [
          {'id': 1, 'name': '周末采购', 'timeRange': 'custom', 'createdAt': '2026-08-09T10:00:00'},
          {'id': 2, 'timeRange': 'menu', 'sourceMenuId': 3, 'createdAt': '2026-08-08T10:00:00'},
        ],
        'total': 2,
      };

  Future<void> pumpPage(WidgetTester tester, List<String> paths) async {
    installMock((options) {
      if (options.path == '/shopping' && options.method == 'GET') {
        return okResponse(listJson());
      }
      if (options.path == '/shopping/1' && options.method == 'GET') {
        return okResponse(detailJson());
      }
      if (options.path == '/shopping/restock') {
        return okResponse({'restocked': 2, 'markedOnly': 0});
      }
      if (options.path == '/shopping/item/103/undo-restock') {
        return okResponse(null);
      }
      if (options.path == '/shopping/item/101') {
        return okResponse(null);
      }
      if (options.path == '/shopping/item/custom') {
        return okResponse(200);
      }
      if (options.path == '/shopping/1/name') {
        return okResponse(null);
      }
      if (options.path == '/shopping/create') {
        return okResponse(1);
      }
      return okResponse({});
    });
    await tester.pumpWidget(_themed(const ShoppingPage()));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));
  }

  testWidgets('列表渲染 + 自定义清单显示名称 + 食集清单显示来源', (tester) async {
    await pumpPage(tester, []);

    expect(find.text('采购清单'), findsOneWidget);
    expect(find.text('周末采购'), findsOneWidget);
    expect(find.textContaining('采购单 ·'), findsOneWidget); // 食集清单无 name
  });

  testWidgets('详情：全选 → 保存入库 · N 项 → POST /shopping/restock', (tester) async {
    final captor = installMock((options) {
      if (options.path == '/shopping' && options.method == 'GET') return okResponse(listJson());
      if (options.path == '/shopping/1' && options.method == 'GET') return okResponse(detailJson());
      if (options.path == '/shopping/restock') return okResponse({'restocked': 3, 'markedOnly': 1});
      return okResponse({});
    });
    await tester.pumpWidget(_themed(const ShoppingPage()));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));

    // 打开清单 1（周末采购）
    await tester.tap(find.text('周末采购'));
    await tester.pumpAndSettle();

    // 全选行 + 已选计数
    expect(find.text('全选'), findsOneWidget);
    expect(find.text('已选 0 项'), findsOneWidget);
    expect(find.text('保存入库 · 0 项'), findsOneWidget);

    // 全选 → 未入库 3 项（番茄/鸡蛋/洗洁精）被选，已入库鲈鱼不算
    await tester.tap(find.text('全选'));
    await tester.pump();
    expect(find.text('已选 3 项'), findsOneWidget);
    expect(find.text('保存入库 · 3 项'), findsOneWidget);

    // 保存入库 → restock body = 3 个未入库 itemId
    await tester.tap(find.text('保存入库 · 3 项'));
    await tester.pumpAndSettle();
    final req = captor.all.firstWhere((r) => r.path == '/shopping/restock');
    final ids = (req.data as Map<String, dynamic>)['itemIds'] as List;
    expect(ids.toSet(), {101, 102, 104});
  });

  testWidgets('勾选单项 → 保存入库 · 1 项', (tester) async {
    final captor = installMock((options) {
      if (options.path == '/shopping' && options.method == 'GET') return okResponse(listJson());
      if (options.path == '/shopping/1' && options.method == 'GET') return okResponse(detailJson());
      if (options.path == '/shopping/restock') return okResponse({'restocked': 1, 'markedOnly': 0});
      return okResponse({});
    });
    await tester.pumpWidget(_themed(const ShoppingPage()));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));
    await tester.tap(find.text('周末采购'));
    await tester.pumpAndSettle();

    // 点番茄行勾选
    await tester.tap(find.text('番茄'));
    await tester.pump();
    expect(find.text('保存入库 · 1 项'), findsOneWidget);

    await tester.tap(find.text('保存入库 · 1 项'));
    await tester.pumpAndSettle();
    final req = captor.all.firstWhere((r) => r.path == '/shopping/restock');
    final ids = (req.data as Map<String, dynamic>)['itemIds'] as List;
    expect(ids, [101]);
  });

  testWidgets('未入库项 ✕ → 移除确认 → DELETE /shopping/item/{id}', (tester) async {
    final captor = installMock((options) {
      if (options.path == '/shopping' && options.method == 'GET') return okResponse(listJson());
      if (options.path == '/shopping/1' && options.method == 'GET') return okResponse(detailJson());
      if (options.path == '/shopping/item/101' && options.method == 'DELETE') return okResponse(null);
      return okResponse({});
    });
    await tester.pumpWidget(_themed(const ShoppingPage()));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));
    await tester.tap(find.text('周末采购'));
    await tester.pumpAndSettle();

    // 点番茄行尾 ✕ → 确认弹窗 → 移除
    await tester.tap(find.byIcon(Icons.close).first);
    await tester.pumpAndSettle();
    expect(find.text('移除采购项'), findsOneWidget);
    await tester.tap(find.text('移除'));
    await tester.pumpAndSettle();

    final req = captor.all.firstWhere((r) => r.path == '/shopping/item/101');
    expect(req.method, 'DELETE');
  });

  testWidgets('已入库项 ✕ → 撤回入库确认 → POST undo-restock', (tester) async {
    final captor = installMock((options) {
      if (options.path == '/shopping' && options.method == 'GET') return okResponse(listJson());
      if (options.path == '/shopping/1' && options.method == 'GET') return okResponse(detailJson());
      if (options.path == '/shopping/item/103/undo-restock') return okResponse(null);
      return okResponse({});
    });
    await tester.pumpWidget(_themed(const ShoppingPage()));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));
    await tester.tap(find.text('周末采购'));
    await tester.pumpAndSettle();

    // 鲈鱼（已入库）行尾 ✕ → 撤回确认
    // 行尾 ✕ 顺序：番茄/鸡蛋（未入库）→ 鲈鱼（已入库）→ 洗洁精。找鲈鱼行的 ✕：
    // 简化：点第 3 个 ✕（鲈鱼行）
    await tester.tap(find.byIcon(Icons.close).at(2));
    await tester.pumpAndSettle();
    expect(find.textContaining('撤回「鲈鱼」的入库'), findsOneWidget);
    await tester.tap(find.text('撤回入库'));
    await tester.pumpAndSettle();

    final req = captor.all.firstWhere((r) => r.path == '/shopping/item/103/undo-restock');
    expect(req.method, 'POST');
  });

  testWidgets('自定义添加：逐条添加 → 保存 → POST /shopping/item/custom', (tester) async {
    final captor = installMock((options) {
      if (options.path == '/shopping' && options.method == 'GET') return okResponse(listJson());
      if (options.path == '/shopping/1' && options.method == 'GET') return okResponse(detailJson());
      if (options.path == '/shopping/item/custom') return okResponse(200);
      return okResponse({});
    });
    await tester.pumpWidget(_themed(const ShoppingPage()));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));
    await tester.tap(find.text('周末采购'));
    await tester.pumpAndSettle();

    // 打开添加弹窗
    await tester.tap(find.text('手动添加'));
    await tester.pumpAndSettle();
    expect(find.text('添加采购项'), findsOneWidget);

    // 输入名称 + 数量单位一个框 → 添加
    await tester.enterText(find.byType(TextField).at(0), '山竹');
    await tester.enterText(find.byType(TextField).at(1), '2斤');
    await tester.tap(find.text('添加'));
    await tester.pump();
    expect(find.textContaining('山竹'), findsOneWidget);
    expect(find.textContaining('已添加 1 种'), findsOneWidget);

    // 保存 → addCustomItem body：name + 解析出的数字 amount
    await tester.tap(find.text('添加 · 保存 1 种'));
    await tester.pumpAndSettle();
    final req = captor.all.firstWhere((r) => r.path == '/shopping/item/custom');
    expect(req.data['listId'], 1);
    expect(req.data['name'], '山竹');
    expect(req.data['amount'], 2.0);
  });

  testWidgets('改名：点 ✎ → 输入新名 → 保存 → PUT /shopping/{id}/name', (tester) async {
    final captor = installMock((options) {
      if (options.path == '/shopping' && options.method == 'GET') return okResponse(listJson());
      if (options.path == '/shopping/1' && options.method == 'GET') return okResponse(detailJson());
      if (options.path == '/shopping/1/name') return okResponse(null);
      return okResponse({});
    });
    await tester.pumpWidget(_themed(const ShoppingPage()));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));
    await tester.tap(find.text('周末采购'));
    await tester.pumpAndSettle();

    // 点标题（AppBar 里的，含 ✎）→ 改名弹窗
    await tester.tap(find.descendant(
      of: find.byType(AppBar),
      matching: find.text('周末采购'),
    ));
    await tester.pumpAndSettle();
    expect(find.text('修改清单名称'), findsOneWidget);

    await tester.enterText(find.byType(TextField), '下周末采购');
    await tester.tap(find.text('保存'));
    await tester.pumpAndSettle();

    final req = captor.all.firstWhere((r) => r.path == '/shopping/1/name');
    expect(req.method, 'PUT');
    expect(req.data, {'name': '下周末采购'});
  });

  testWidgets('分享预览：右上角分享 → 预览未入库项 → 复制文字', (tester) async {
    installMock((options) {
      if (options.path == '/shopping' && options.method == 'GET') return okResponse(listJson());
      if (options.path == '/shopping/1' && options.method == 'GET') return okResponse(detailJson());
      return okResponse({});
    });
    await tester.pumpWidget(_themed(const ShoppingPage()));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));
    await tester.tap(find.text('周末采购'));
    await tester.pumpAndSettle();

    // 右上角分享 → 预览页
    await tester.tap(find.byIcon(Icons.share));
    await tester.pumpAndSettle();
    expect(find.text('分享采购清单'), findsOneWidget);
    expect(find.text('复制文字'), findsOneWidget);
    expect(find.text('转图片分享'), findsOneWidget);

    // 复制文字：拦截 Clipboard.setData 断言内容
    String? copiedText;
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
      SystemChannels.platform,
      (call) async {
        if (call.method == 'Clipboard.setData') {
          copiedText = (call.arguments as Map)['text'] as String?;
        }
        return null;
      },
    );
    await tester.tap(find.text('复制文字'));
    await tester.pump();
    expect(copiedText, contains('周末采购'));
    expect(copiedText, contains('番茄'));
    expect(copiedText, contains('鸡蛋'));
    expect(copiedText, contains('洗洁精'));
    expect(copiedText, isNot(contains('鲈鱼'))); // 已入库不出现在分享内容
  });
}
