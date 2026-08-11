import 'package:flutter_test/flutter_test.dart';
import 'package:menu_flutter/models/dish.dart';

/// Dish/DishStep/DishDetail.fromJson 的 null 兜底；
/// 重点 DishStep.imageList：逗号分隔 → List，空串/全空白过滤。
void main() {
  group('Dish.fromJson', () {
    test('完整字段解析', () {
      final d = Dish.fromJson({
        'id': 1,
        'name': '番茄炒蛋',
        'cookTime': 15,
        'prepTime': 5,
        'difficulty': 2,
        'note': '备注',
        'coverUrl': '/img/a.jpg',
        'sourceName': '下厨房',
        'sourceUrl': 'https://www.xiachufang.com/recipe/1/',
        'price': 18.5,
      });
      expect(d.id, 1);
      expect(d.name, '番茄炒蛋');
      expect(d.cookTime, 15);
      expect(d.prepTime, 5);
      expect(d.difficulty, 2);
      expect(d.note, '备注');
      expect(d.sourceName, '下厨房');
      expect(d.sourceUrl, 'https://www.xiachufang.com/recipe/1/');
      expect(d.coverUrl, '/img/a.jpg');
    });

    test('name 缺省兜底空串', () {
      final d = Dish.fromJson({'id': 1});
      expect(d.name, '');
    });

    test('可选字段为 null 时不报错', () {
      final d = Dish.fromJson({'id': 1, 'name': '测试'});
      expect(d.cookTime, isNull);
      expect(d.prepTime, isNull);
      expect(d.difficulty, isNull);
    });
  });

  group('DishStep.imageList', () {
    test('多图逗号分隔 → List', () {
      final s = DishStep.fromJson({'seq': 1, 'text': '步骤1', 'images': 'a.jpg,b.jpg,c.jpg'});
      expect(s.imageList, ['a.jpg', 'b.jpg', 'c.jpg']);
    });

    test('单图', () {
      final s = DishStep.fromJson({'text': '步骤', 'images': 'only.jpg'});
      expect(s.imageList, ['only.jpg']);
    });

    test('images 为 null → 空列表', () {
      final s = DishStep.fromJson({'text': '步骤', 'images': null});
      expect(s.imageList, isEmpty);
    });

    test('images 为空串 → 空列表', () {
      final s = DishStep.fromJson({'text': '步骤', 'images': ''});
      expect(s.imageList, isEmpty);
    });

    test('含空白项过滤：纯空白项被剔除，但有效项保留原值（含首尾空格）', () {
      // 源码逻辑：split(',').where((s) => s.trim().isNotEmpty) → 只剔除纯空白项，
      // 不做 trim，故 ' a.jpg ' 保留首尾空格
      final s = DishStep.fromJson({'text': '步骤', 'images': ' a.jpg , , b.jpg ,'});
      expect(s.imageList, [' a.jpg ', ' b.jpg ']);
    });

    test('全空白 → 空列表', () {
      final s = DishStep.fromJson({'text': '步骤', 'images': '   ,  , '});
      expect(s.imageList, isEmpty);
    });

    test('text 缺省兜底空串', () {
      final s = DishStep.fromJson({'images': null});
      expect(s.text, '');
    });
  });

  group('DishDetail.fromJson', () {
    test('dish + steps 解析', () {
      final d = DishDetail.fromJson({
        'dish': {'id': 1, 'name': '番茄炒蛋'},
        'steps': [
          {'seq': 1, 'text': '切番茄'},
          {'seq': 2, 'text': '炒蛋', 'images': 'a.jpg,b.jpg'},
        ],
      });
      expect(d.dish.id, 1);
      expect(d.dish.name, '番茄炒蛋');
      expect(d.steps.length, 2);
      expect(d.steps[0].text, '切番茄');
      expect(d.steps[1].imageList, ['a.jpg', 'b.jpg']);
    });

    test('steps 缺省兜底空数组', () {
      final d = DishDetail.fromJson({
        'dish': {'id': 1, 'name': '测试'},
      });
      expect(d.steps, isEmpty);
    });
  });
}
