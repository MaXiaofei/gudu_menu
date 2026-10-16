import 'package:flutter_test/flutter_test.dart';

import 'package:menu_flutter/core/app_theme.dart';
import 'package:menu_flutter/core/theme_controller.dart';
import 'package:menu_flutter/core/tokens.g.dart';

/// 状态栏底色跟随主题（main.dart 不再写死奶油色）。
void main() {
  test('brandOverlayStyle 取当前主题 bg', () {
    expect(brandOverlayStyle(AppTokens.cream).statusBarColor, AppTokens.cream.bg);
    expect(brandOverlayStyle(AppTokens.matcha).statusBarColor, AppTokens.matcha.bg);
    expect(brandOverlayStyle(AppTokens.matcha).systemNavigationBarColor,
        AppTokens.matcha.bg);
  });

  test('AppTokens.cream/matcha 即生成的皮肤常量', () {
    expect(identical(AppTokens.cream, kSkin_cream), isTrue);
    expect(identical(AppTokens.matcha, kSkin_matcha), isTrue);
    expect(kBrandSkins.first.key, 'cream');
  });

  test('未知皮肤 key 回落默认皮肤', () {
    expect(ThemeController('not-exist').skinKey, kBrandSkins.first.key);
  });

  test('ThemeController.tokens 随模式切换', () {
    expect(ThemeController().tokens, AppTokens.cream);
    expect(ThemeController('matcha').tokens, AppTokens.matcha);
  });
}
