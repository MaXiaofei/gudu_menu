import 'package:flutter_test/flutter_test.dart';

import 'package:menu_flutter/core/app_theme.dart';

void main() {
  test('smoke: AppTokens cream/matcha 均可构建且关键字段已定义', () {
    expect(AppTokens.cream.primary, isNotNull);
    expect(AppTokens.cream.bg, isNotNull);
    expect(AppTokens.matcha.primary, isNotNull);
    expect(AppTokens.matcha.bg, isNotNull);
    // 静态功能色
    expect(AppTokens.success, isNotNull);
    expect(AppTokens.warning, isNotNull);
    expect(AppTokens.error, isNotNull);
    expect(AppTokens.info, isNotNull);
  });
}
