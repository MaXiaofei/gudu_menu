import 'package:flutter/material.dart';

/// 主题色与全局 ThemeData：与小程序 menu-mini 配色 1:1 对齐。
/// 主色 #FF8C42 烟火暖橙（导航栏/主按钮/强调）；次级色对应小程序各页硬编码值。
class AppColors {
  AppColors._();

  static const Color primary = Color(0xFFFF8C42); // 烟火暖橙：导航栏/主按钮/强调/选中
  static const Color saveGreen = Color(0xFF2A9D8F); // 青绿：采购"保存"按钮
  static const Color warnRed = Color(0xFFF56C6C); // 红：库存不足色条 / 危险操作
  static const Color warnOrange = Color(0xFFE6A23C); // 橙黄：库存临期色条
  static const Color textSecondary = Color(0xFF999999); // 次要文字（对应 tabBar.color）
  static const Color textPrimary = Color(0xFF2D2A26); // 主文字
  static const Color textHint = Color(0xFF666666);
  static const Color cream = Color(0xFFFAF8F5); // 页面暖底色
  static const Color cardBg = Color(0xFFFFFFFF); // 卡片背景
  static const Color divider = Color(0xFFEEEEEE); // 分隔线/底色
  static const Color rowDivider = Color(0xFFF0F0F0);
}

/// 渐变色集合。
class AppGradients {
  AppGradients._();
  static const LinearGradient primary = LinearGradient(
    colors: [Color(0xFFFF8C42), Color(0xFFE6762A)],
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
  );
}

ThemeData buildAppTheme() {
  return ThemeData(
    useMaterial3: true,
    colorScheme: ColorScheme.fromSeed(
      seedColor: AppColors.primary,
      primary: AppColors.primary,
    ),
    scaffoldBackgroundColor: Colors.white,
    appBarTheme: const AppBarTheme(
      backgroundColor: AppColors.primary,
      foregroundColor: Colors.white,
      centerTitle: true,
      elevation: 0,
      titleTextStyle: TextStyle(
        color: Colors.white,
        fontSize: 18,
        fontWeight: FontWeight.bold,
      ),
    ),
    elevatedButtonTheme: ElevatedButtonThemeData(
      style: ElevatedButton.styleFrom(
        backgroundColor: AppColors.primary,
        foregroundColor: Colors.white,
        minimumSize: const Size(double.infinity, 48),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
      ),
    ),
    inputDecorationTheme: const InputDecorationTheme(
      border: OutlineInputBorder(),
      contentPadding: EdgeInsets.symmetric(horizontal: 12, vertical: 14),
    ),
    dividerTheme: const DividerThemeData(
      color: AppColors.divider,
      thickness: 1,
      space: 1,
    ),
  );
}
