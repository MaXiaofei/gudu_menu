import 'package:flutter_test/flutter_test.dart';
import 'package:menu_flutter/core/constants.dart';

/// metricNameCn：6 个已知指标英文→中文映射 + 未知 key 原样返回兜底。
void main() {
  group('AppConstants.metricNameCn', () {
    test('6 个已知指标都有中文映射', () {
      const cases = {
        'calorie': '热量',
        'protein': '蛋白质',
        'fat': '脂肪',
        'carb': '碳水',
        'sugar': '糖',
        'gi': '升糖指数',
      };
      cases.forEach((en, cn) {
        expect(AppConstants.metricNameCn(en), cn, reason: '$en 应映射为 $cn');
      });
    });

    test('未知指标名原样返回', () {
      expect(AppConstants.metricNameCn('fiber'), 'fiber');
      expect(AppConstants.metricNameCn('sodium'), 'sodium');
      expect(AppConstants.metricNameCn('custom_metric'), 'custom_metric');
    });

    test('空字符串原样返回', () {
      expect(AppConstants.metricNameCn(''), '');
    });
  });

  group('AppConstants 常量', () {
    test('tokenKey 已定义', () {
      expect(AppConstants.tokenKey, 'token');
    });

    test('baseUrl 非空', () {
      expect(AppConstants.baseUrl, isNotEmpty);
    });
  });
}
