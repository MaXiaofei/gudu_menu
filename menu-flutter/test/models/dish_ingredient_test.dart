import 'package:flutter_test/flutter_test.dart';
import 'package:menu_flutter/models/dish.dart';

void main() {
  group('DishIngredient.amountText', () {
    test('数量 + 自然单位', () {
      expect(const DishIngredient(ingredientId: 1, amount: 2, unitName: '个').amountText, '2 个');
    });

    test('数量为 0（导入的「适量/少许」）只显示单位', () {
      expect(const DishIngredient(ingredientId: 1, amount: 0, unitName: '适量').amountText, '适量');
    });

    test('数量为 0 且无单位：不显示「0 g」', () {
      expect(const DishIngredient(ingredientId: 1, amount: 0).amountText, '');
    });
  });
}
