import 'package:flutter/material.dart';

import 'app_theme.dart';

/// 历史颜色快捷常量（向后兼容）。
///
/// 这些值 = 奶油轻食（cream）主题的固定值，供存量页面直接引用。
/// 切到抹茶主题时**不会**跟随——新页面请改用 `AppTokens.of(context)`
/// （主题感知）。详见 [app_theme.dart] 与 design/design-themes.html。
class AppColors {
  AppColors._();

  // —— 品牌（= AppTokens.cream）——
  static const Color primary = Color(0xFFE89150); // 暖橙主色：导航栏/主按钮/选中
  static const Color primaryDeep = Color(0xFFD17A3C); // 加深（按压态/悬浮）
  static const Color primarySoft = Color(0xFFF6D9BE); // 淡色（柔和底/头像底）
  static const Color secondary = Color(0xFFFBF0DD); // 辅色底
  static const Color accent = Color(0xFFB8762E); // 强调文字（柔和底上）

  // —— 中性 ——
  static const Color cream = Color(0xFFFDFAF4); // 页面暖底色
  static const Color cardBg = Color(0xFFFFFFFF); // 卡片背景
  static const Color border = Color(0xFFF0E6D6); // 描边
  static const Color divider = Color(0xFFF0E6D6); // 分隔线（同 border）
  static const Color rowDivider = Color(0xFFF0E6D6);
  static const Color textPrimary = Color(0xFF4A382A); // 标题
  static const Color textHint = Color(0xFF6E5C49); // 正文
  static const Color textSecondary = Color(0xFF9C8C7A); // 辅助/说明

  // —— 功能色（两套共享）——
  static const Color success = Color(0xFF4FAE6E);
  static const Color warning = Color(0xFFE5A938);
  static const Color error = Color(0xFFDB5A4E);
  static const Color info = Color(0xFF4FA0D0);

  // —— 兼容旧名（值已对齐新功能色）——
  static const Color saveGreen = Color(0xFF4FAE6E); // = success
  static const Color warnOrange = Color(0xFFE5A938); // = warning
  static const Color warnRed = Color(0xFFDB5A4E); // = error
}

/// 渐变色集合（历史用）。
///
/// 新设计系统以纯色为主、不强调渐变；新组件请用 [AppTokens] 的纯色 token。
class AppGradients {
  AppGradients._();
  static const LinearGradient primary = LinearGradient(
    colors: [Color(0xFFE89150), Color(0xFFD17A3C)], // cream primary → primaryDeep
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
  );
}

/// 旧入口，等价于奶油轻食主题（[creamTheme]）。保留以兼容存量调用。
ThemeData buildAppTheme() => creamTheme;
