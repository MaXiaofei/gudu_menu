import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:menu_flutter/core/app_theme.dart';
import 'package:menu_flutter/pages/dish/detail_page.dart';
import '../helpers/mock_http.dart';

/// 给 widget 测试注入 AppTokens 主题。
Widget _themed(Widget child) => MaterialApp(
      theme: ThemeData(extensions: const [AppTokens.cream]),
      home: child,
    );

/// 详情页"直接做这道菜"端到端（widget 级）：
/// DishDetailPage._load → detail/nutrition/metrics 三个并发请求（mock）→
/// 渲染详情 → 点击"直接做这道菜" → POST /dish/{id}/cook-now →
/// 解析 CookResult.deductions（List<DeductResult>）→ SnackBar 展示缺量食材名。
///
/// 关键断言：修复后 deductions 不再丢失，缺量提示带食材名（"鸡蛋 5g"）。
void main() {
  testWidgets('做菜有欠量 → SnackBar 展示具体缺量食材名', (tester) async {
    bool cookCalled = false;
    installMock((options) {
      switch (options.path) {
        case '/dish/1':
          return okResponse({
            'dish': {
              'id': 1,
              'name': '番茄炒蛋',
              'prepTime': 5,
              'cookTime': 10,
              'difficulty': 2,
              'cuisineNames': ['鲁菜'],
              'categoryNames': ['热菜'],
              'tagNames': ['家常'],
            },
            'steps': [
              {'seq': 1, 'text': '番茄切块'},
            ],
          });
        case '/dish/1/nutrition':
          return okResponse({'1': 200.0});
        case '/nutrition/metric':
          return okResponse([
            {'id': 1, 'name': 'calorie', 'unit': 'kcal'},
          ]);
        case '/dish/1/cook-now':
          cookCalled = true;
          return okResponse({
            'menuId': null,
            'deductions': [
              {
                'ingredientId': 16,
                'ingredientName': '番茄',
                'deductedGrams': 100.0,
                'shortageGrams': 0,
                'batches': [],
              },
              {
                'ingredientId': 17,
                'ingredientName': '鸡蛋',
                'deductedGrams': 0.0,
                'shortageGrams': 5.0,
                'batches': [],
              },
            ],
            'shortages': {'17': 5.0},
            'cookingRecordIds': [6],
          });
        default:
          return okResponse({});
      }
    });

    await tester.pumpWidget(_themed(const DishDetailPage(id: 1)));
    await tester.pumpAndSettle();

    // 详情已渲染
    expect(find.text('番茄炒蛋'), findsOneWidget);
    // Bug B 修复后详情页展示字典名标签
    expect(find.text('鲁菜'), findsOneWidget);
    expect(find.text('家常'), findsOneWidget);

    // 点击"直接做这道菜"
    expect(find.text('直接做这道菜'), findsOneWidget);
    await tester.tap(find.text('直接做这道菜'));
    await tester.pumpAndSettle();

    // cook-now 请求确实发出
    expect(cookCalled, isTrue);
    // SnackBar 展示带食材名的缺量（修复前是"缺量：1 项"，现在是"鸡蛋 5g"）
    expect(find.textContaining('鸡蛋 5g'), findsOneWidget);
  });

  testWidgets('做菜无欠量 → SnackBar 提示库存已扣', (tester) async {
    installMock((options) {
      switch (options.path) {
        case '/dish/1':
          return okResponse({
            'dish': {'id': 1, 'name': '白米饭', 'cookTime': 30},
            'steps': [],
          });
        case '/dish/1/nutrition':
          return okResponse({});
        case '/nutrition/metric':
          return okResponse([]);
        case '/dish/1/cook-now':
          return okResponse({
            'menuId': null,
            'deductions': [
              {
                'ingredientId': 1,
                'ingredientName': '大米',
                'deductedGrams': 200.0,
                'shortageGrams': 0,
                'batches': [],
              },
            ],
            'shortages': {},
            'cookingRecordIds': [8],
          });
        default:
          return okResponse({});
      }
    });

    await tester.pumpWidget(_themed(const DishDetailPage(id: 1)));
    await tester.pumpAndSettle();

    await tester.tap(find.text('直接做这道菜'));
    await tester.pumpAndSettle();

    expect(find.textContaining('库存已扣'), findsOneWidget);
    // 无欠量时不出现"缺量"字样
    expect(find.textContaining('缺量'), findsNothing);
  });
}
