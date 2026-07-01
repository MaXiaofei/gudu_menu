import 'package:flutter/material.dart';

/// 咕嘟小食单 · 双主题设计系统（与 design/design-themes.html 1:1 对齐）。
///
/// 定稿：原始深色中性色版。
///   · 奶油轻食（暖橙，默认）：primary #E89150 / title #4A382A / body #6E5C49
///   · 抹茶禅意（草绿）   ：primary #7A9A5B / title #2E3520 / body #6B7660
/// 功能色两套共享：success #4FAE6E / warning #E5A938 / error #DB5A4E / info #4FA0D0。
///
/// 新组件取 token：`final t = AppTokens.of(context);` 后用 `t.primary` 等，
/// 切主题时自动跟随。历史页面仍用 [AppColors]（固定为 cream 值）。
class AppTokens extends ThemeExtension<AppTokens> {
  const AppTokens({
    required this.primary,
    required this.primaryDeep,
    required this.primarySoft,
    required this.secondary,
    required this.accent,
    required this.bg,
    required this.card,
    required this.border,
    required this.title,
    required this.body,
    required this.caption,
    required this.shadowBase,
  });

  final Color primary;
  final Color primaryDeep;
  final Color primarySoft;
  final Color secondary;
  final Color accent;
  final Color bg;
  final Color card;
  final Color border;
  final Color title;
  final Color body;
  final Color caption;
  final Color shadowBase; // 阴影基色（对应 CSS 的 --sh RGB）

  // —— 功能色（两套共享，与主题无关）——
  static const Color success = Color(0xFF4FAE6E);
  static const Color warning = Color(0xFFE5A938);
  static const Color error = Color(0xFFDB5A4E);
  static const Color info = Color(0xFF4FA0D0);

  // —— 圆角 token ——
  static const double rSm = 8;
  static const double rMd = 14;
  static const double rLg = 22;
  static const double rPill = 999;

  // —— 间距 token ——
  static const double sp4 = 4;
  static const double sp8 = 8;
  static const double sp12 = 12;
  static const double sp16 = 16;
  static const double sp24 = 24;
  static const double sp32 = 32;
  static const double sp48 = 48;

  // 阴影色（对应 CSS rgba(var(--sh), Α)）
  Color get shadowSm => shadowBase.withAlpha(20); // ≈ .08
  Color get shadowMd => shadowBase.withAlpha(26); // ≈ .10
  Color get shadowLg => shadowBase.withAlpha(36); // ≈ .14

  List<BoxShadow> get elevationSm => [
        BoxShadow(color: shadowSm, offset: const Offset(0, 1), blurRadius: 3),
      ];
  List<BoxShadow> get elevationMd => [
        BoxShadow(color: shadowMd, offset: const Offset(0, 6), blurRadius: 18),
      ];
  List<BoxShadow> get elevationLg => [
        BoxShadow(color: shadowLg, offset: const Offset(0, 14), blurRadius: 36),
      ];

  /// 奶油轻食（暖橙，默认）。
  static const cream = AppTokens(
    primary: Color(0xFFE89150),
    primaryDeep: Color(0xFFD17A3C),
    primarySoft: Color(0xFFF6D9BE),
    secondary: Color(0xFFFBF0DD),
    accent: Color(0xFFB8762E),
    bg: Color(0xFFFDFAF4),
    card: Color(0xFFFFFFFF),
    border: Color(0xFFF0E6D6),
    title: Color(0xFF4A382A),
    body: Color(0xFF6E5C49),
    caption: Color(0xFF9C8C7A),
    shadowBase: Color(0xFFA9651E), // 169,101,30
  );

  /// 抹茶禅意（草绿）。
  static const matcha = AppTokens(
    primary: Color(0xFF7A9A5B),
    primaryDeep: Color(0xFF648449),
    primarySoft: Color(0xFFD8E2C8),
    secondary: Color(0xFFE8E4D5),
    accent: Color(0xFF6B8A4D),
    bg: Color(0xFFF7F5EE),
    card: Color(0xFFFFFFFF),
    border: Color(0xFFE5E2D5),
    title: Color(0xFF2E3520),
    body: Color(0xFF6B7660),
    caption: Color(0xFF9CA58F),
    shadowBase: Color(0xFF7A9A5B), // 122,154,91
  );

  /// 从 BuildContext 取当前主题 token。
  static AppTokens of(BuildContext context) =>
      Theme.of(context).extension<AppTokens>()!;

  @override
  AppTokens copyWith({
    Color? primary,
    Color? primaryDeep,
    Color? primarySoft,
    Color? secondary,
    Color? accent,
    Color? bg,
    Color? card,
    Color? border,
    Color? title,
    Color? body,
    Color? caption,
    Color? shadowBase,
  }) =>
      AppTokens(
        primary: primary ?? this.primary,
        primaryDeep: primaryDeep ?? this.primaryDeep,
        primarySoft: primarySoft ?? this.primarySoft,
        secondary: secondary ?? this.secondary,
        accent: accent ?? this.accent,
        bg: bg ?? this.bg,
        card: card ?? this.card,
        border: border ?? this.border,
        title: title ?? this.title,
        body: body ?? this.body,
        caption: caption ?? this.caption,
        shadowBase: shadowBase ?? this.shadowBase,
      );

  @override
  AppTokens lerp(ThemeExtension<AppTokens>? other, double t) {
    if (other is! AppTokens) return this;
    return AppTokens(
      primary: Color.lerp(primary, other.primary, t)!,
      primaryDeep: Color.lerp(primaryDeep, other.primaryDeep, t)!,
      primarySoft: Color.lerp(primarySoft, other.primarySoft, t)!,
      secondary: Color.lerp(secondary, other.secondary, t)!,
      accent: Color.lerp(accent, other.accent, t)!,
      bg: Color.lerp(bg, other.bg, t)!,
      card: Color.lerp(card, other.card, t)!,
      border: Color.lerp(border, other.border, t)!,
      title: Color.lerp(title, other.title, t)!,
      body: Color.lerp(body, other.body, t)!,
      caption: Color.lerp(caption, other.caption, t)!,
      shadowBase: Color.lerp(shadowBase, other.shadowBase, t)!,
    );
  }
}

/// 由一套 token 构造完整 ThemeData（注入 AppTokens 扩展）。
ThemeData buildBrandTheme(AppTokens t) {
  return ThemeData(
    useMaterial3: true,
    colorScheme: ColorScheme.light(
      primary: t.primary,
      onPrimary: Colors.white,
      primaryContainer: t.primarySoft,
      onPrimaryContainer: t.accent,
      secondary: t.secondary,
      onSecondary: t.accent,
      surface: t.card,
      onSurface: t.title,
      error: AppTokens.error,
      onError: Colors.white,
      outline: t.border,
    ),
    scaffoldBackgroundColor: t.bg,
    canvasColor: t.bg,
    cardColor: t.card,
    dividerColor: t.border,
    extensions: [t],
    appBarTheme: AppBarTheme(
      backgroundColor: t.primary,
      foregroundColor: Colors.white,
      centerTitle: true,
      elevation: 0,
      scrolledUnderElevation: 0,
      titleTextStyle: const TextStyle(
        color: Colors.white,
        fontSize: 18,
        fontWeight: FontWeight.bold,
      ),
    ),
    dividerTheme: DividerThemeData(
      color: t.border,
      thickness: 1,
      space: 1,
    ),
    elevatedButtonTheme: ElevatedButtonThemeData(
      style: ElevatedButton.styleFrom(
        backgroundColor: t.primary,
        foregroundColor: Colors.white,
        disabledBackgroundColor: t.caption,
        disabledForegroundColor: Colors.white,
        minimumSize: const Size(double.infinity, 48),
        elevation: 0,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(AppTokens.rMd),
        ),
      ),
    ),
    inputDecorationTheme: InputDecorationTheme(
      filled: true,
      fillColor: t.bg,
      contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 14),
      border: OutlineInputBorder(
        borderRadius: BorderRadius.circular(AppTokens.rMd),
        borderSide: BorderSide(color: t.border, width: 1.5),
      ),
      enabledBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(AppTokens.rMd),
        borderSide: BorderSide(color: t.border, width: 1.5),
      ),
      focusedBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(AppTokens.rMd),
        borderSide: BorderSide(color: t.primary, width: 1.5),
      ),
    ),
    textTheme: TextTheme(
      headlineLarge: TextStyle(fontSize: 40, fontWeight: FontWeight.w800, color: t.title),
      headlineMedium: TextStyle(fontSize: 32, fontWeight: FontWeight.w800, color: t.title),
      headlineSmall: TextStyle(fontSize: 24, fontWeight: FontWeight.w700, color: t.title),
      titleLarge: TextStyle(fontSize: 20, fontWeight: FontWeight.w600, color: t.title),
      titleMedium: TextStyle(fontSize: 16, fontWeight: FontWeight.w500, color: t.title),
      bodyLarge: TextStyle(fontSize: 16, color: t.body),
      bodyMedium: TextStyle(fontSize: 14, color: t.body),
      bodySmall: TextStyle(fontSize: 12, color: t.caption),
    ),
  );
}

/// 双主题预设入口。
ThemeData get creamTheme => buildBrandTheme(AppTokens.cream);
ThemeData get matchaTheme => buildBrandTheme(AppTokens.matcha);
