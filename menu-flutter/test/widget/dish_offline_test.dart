import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:menu_flutter/core/app_theme.dart';
import 'package:menu_flutter/models/dish.dart';
import 'package:menu_flutter/pages/dish/detail_page.dart';
import '../helpers/mock_http.dart';

Widget _themed(Widget child) => MaterialApp(
      theme: ThemeData(extensions: const [AppTokens.cream]),
      home: child,
    );

void _mockDish(Map<String, dynamic> dish) {
  installMock((options) {
    if (options.path == '/dish/1') {
      return okResponse({
        'dish': dish,
        'steps': [
          {'seq': 1, 'text': '番茄切块'}
        ],
      });
    }
    if (options.path == '/dish/1/nutrition') return okResponse({});
    if (options.path == '/nutrition/metric') return okResponse([]);
    return okResponse({});
  });
}

/// 公共菜谱下架（V101）：详情页显示「已下架」，不再提供「加入食集」；做菜 / 跟做照常。
void main() {
  group('Dish.fromJson offline', () {
    test('offline=true 解析为已下架', () {
      expect(Dish.fromJson({'id': 1, 'name': '菜', 'offline': true}).offline, isTrue);
    });

    test('缺省 / false / 非布尔值 都按未下架', () {
      expect(Dish.fromJson({'id': 1, 'name': '菜'}).offline, isFalse);
      expect(Dish.fromJson({'id': 1, 'name': '菜', 'offline': false}).offline, isFalse);
      expect(Dish.fromJson({'id': 1, 'name': '菜', 'offline': 'yes'}).offline, isFalse);
    });
  });

  testWidgets('已下架：显示已下架标记与说明，隐藏「加入食集」，现在就做 / 跟做仍在', (tester) async {
    _mockDish({'id': 1, 'name': '番茄炒蛋', 'cookTime': 10, 'offline': true});

    await tester.pumpWidget(_themed(const DishDetailPage(id: 1)));
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('dish-offline-tag')), findsOneWidget);
    expect(find.text('已下架'), findsOneWidget);
    expect(find.byKey(const Key('dish-offline-tip')), findsOneWidget);
    expect(find.text('加入食集'), findsNothing);
    expect(find.byKey(const Key('dish-cook-now')), findsOneWidget);
    expect(find.byKey(const Key('dish-cook-mode')), findsOneWidget);
  });

  testWidgets('未下架：没有下架标记，「加入食集」照常', (tester) async {
    _mockDish({'id': 1, 'name': '番茄炒蛋', 'cookTime': 10});

    await tester.pumpWidget(_themed(const DishDetailPage(id: 1)));
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('dish-offline-tag')), findsNothing);
    expect(find.byKey(const Key('dish-offline-tip')), findsNothing);
    expect(find.text('加入食集'), findsOneWidget);
  });
}
