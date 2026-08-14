import 'package:flutter/material.dart';

/// 咕嘟小食单 · 双主题设计系统（颜色 token 权威定义在本文件；
/// 设计约定见 docs/design/DESIGN.md §4 圆角 / §5 阴影 / §6 间距 / §7 交互）。
///
/// 定稿：原始深色中性色版。
///   · 奶油轻食（暖橙，默认）：primary #E89150 / title #4A382A / body #6E5C49
///   · 抹茶禅意（草绿）   ：primary #7A9A5B / title #2E3520 / body #6B7660
/// 功能色两套共享：success #4FAE6E / warning #E5A938 / error #DB5A4E / info #4FA0D0。
///
/// 新组件取 token：`final t = AppTokens.of(context);` 后用 `t.primary` 等，
/// 切主题时自动跟随。
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
    required this.highlight,
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
  final Color highlight; // 高亮底（warning callout 等）
  final Color shadowBase; // 阴影基色（对应 CSS 的 --sh RGB）

  // —— 功能色（两套共享，与主题无关）——
  static const Color success = Color(0xFF4FAE6E);
  static const Color warning = Color(0xFFE5A938);
  static const Color error = Color(0xFFDB5A4E);
  static const Color info = Color(0xFF4FA0D0);

  /// 圆角阶梯（7 档，对齐 DESIGN.md §4）
  /// 实际值：2 / 4 / 8 / 12 / 16 / 22 / 999
  static const double r2 = 2;   // 小色条 / 进度条 / 细分割块
  static const double rXs = 4;
  static const double rSm = 8;
  static const double rMd = 12;  // 修正：14 → 12（原型 45 次使用 12px）
  static const double rLg = 16;
  static const double rXl = 22;
  static const double rPill = 999;

  /// 间距阶梯（10 档，对齐 DESIGN.md §6）
  /// 实际值：2 / 4 / 6 / 8 / 10 / 12 / 16 / 20 / 24 / 32
  static const double sp2 = 2;
  static const double sp4 = 4;
  static const double sp6 = 6;   // 小间距：胶囊内边距 / 胶囊间 gap（页面实际在用 6px 共 35 处）
  static const double sp8 = 8;
  static const double sp10 = 10; // 列表项内边距 / 紧凑卡片 padding（实际在用 10px 共 7 处）
  static const double sp12 = 12;
  static const double sp16 = 16;
  static const double sp20 = 20; // 区块间距 / 较大留白（实际在用 20px 共 3 处）
  static const double sp24 = 24;
  static const double sp32 = 32;

  /// 语义化文字样式（11 档，对齐 tokens.json typography.scale）。
  /// 用法：`final ts = t.textStyles;` → `ts.pageTitle` / `ts.cardTitle` / `ts.body` 等。
  VxTextStyles get textStyles => VxTextStyles.fromTokens(this);

  // 阴影色（对应 CSS rgba(var(--sh), Α)）
  Color get shadowSm => shadowBase.withAlpha(20); // ≈ .08
  Color get shadowMd => shadowBase.withAlpha(26); // ≈ .10
  Color get shadowLg => shadowBase.withAlpha(36); // ≈ .14
  Color get shadowFab => shadowBase.withAlpha(102); // ≈ .40

  List<BoxShadow> get elevationSm => [
        BoxShadow(color: shadowSm, offset: const Offset(0, 1), blurRadius: 3),
      ];
  List<BoxShadow> get elevationMd => [
        BoxShadow(color: shadowMd, offset: const Offset(0, 6), blurRadius: 18),
      ];
  List<BoxShadow> get elevationLg => [
        BoxShadow(color: shadowLg, offset: const Offset(0, 14), blurRadius: 36),
      ];
  /// FAB / 凸起按钮阴影（DESIGN.md §5 --shadow-fab: 0 4px 12px rgba(shadow,.40)）
  List<BoxShadow> get elevationFab => [
        BoxShadow(color: shadowFab, offset: const Offset(0, 4), blurRadius: 12),
      ];

  /// 主色渐变（登录按钮、头像底等用），与 cream/matcha 跟随。
  LinearGradient get primaryGradient => LinearGradient(
        colors: [primary, primaryDeep],
        begin: Alignment.topLeft,
        end: Alignment.bottomRight,
      );

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
    highlight: Color(0xFFFFF7EC),
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
    highlight: Color(0xFFFBF9EC),
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
    Color? highlight,
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
        highlight: highlight ?? this.highlight,
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
      highlight: Color.lerp(highlight, other.highlight, t)!,
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
    // 交互状态（DESIGN.md §7：150ms fast transition）
    hoverColor: t.primary.withValues(alpha: 0.08),
    splashColor: t.primary.withValues(alpha: 0.12),
    highlightColor: t.primary.withValues(alpha: 0.10),
    extensions: [t],
    // DESIGN.md §13：去掉橙色顶栏色块。AppBar 背景改奶油底、深色字，
    // 与 ActionBar/BackHeader 的奶油顶栏统一；残留的 AppBar 用法自动跟随。
    appBarTheme: AppBarTheme(
      backgroundColor: t.bg,
      foregroundColor: t.title,
      centerTitle: true,
      elevation: 0,
      scrolledUnderElevation: 0,
      titleTextStyle: TextStyle(
        color: t.title,
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
        // 按钮文案统一：15/w800 白字（此前各页手写 w800 不一，字号缺省）
        textStyle: const TextStyle(
            fontSize: 15, fontWeight: FontWeight.w800, height: 1.2),
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(AppTokens.rMd),
        ),
      ),
    ),
    // 文字按钮统一：14/w700 主色（危险操作在页面侧 copyWith error 色）
    textButtonTheme: TextButtonThemeData(
      style: TextButton.styleFrom(
        foregroundColor: t.primary,
        textStyle: const TextStyle(
            fontSize: 14, fontWeight: FontWeight.w700, height: 1.2),
      ),
    ),
    // 描边按钮统一：14/w700 主色 + 主色描边 + 48 高（与主按钮同尺寸语言）
    outlinedButtonTheme: OutlinedButtonThemeData(
      style: OutlinedButton.styleFrom(
        foregroundColor: t.primary,
        side: BorderSide(color: t.primary, width: 1.5),
        minimumSize: const Size(double.infinity, 48),
        textStyle: const TextStyle(
            fontSize: 14, fontWeight: FontWeight.w700, height: 1.2),
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(AppTokens.rMd),
        ),
      ),
    ),
    // 错误/成功提示统一：深色底白字 13px，floating + 圆角（此前 SnackBar 为 Material 默认样式）
    snackBarTheme: SnackBarThemeData(
      behavior: SnackBarBehavior.floating,
      backgroundColor: t.title,
      contentTextStyle: TextStyle(
          fontSize: 13, fontWeight: FontWeight.w500, color: t.card, height: 1.4),
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(AppTokens.rMd),
      ),
    ),
    // 弹窗统一：标题 16/w700、正文 14、圆角 16
    dialogTheme: DialogThemeData(
      backgroundColor: t.card,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(AppTokens.rLg),
      ),
      titleTextStyle: TextStyle(
          fontSize: 16, fontWeight: FontWeight.w700, color: t.title, height: 1.3),
      contentTextStyle: TextStyle(
          fontSize: 14, fontWeight: FontWeight.w400, color: t.body, height: 1.5),
    ),
    inputDecorationTheme: InputDecorationTheme(
      filled: true,
      fillColor: t.bg,
      // 输入文字统一 14px（此前各页 12~16 不一）；hint 12 caption 色
      hintStyle: TextStyle(
          fontSize: 12, fontWeight: FontWeight.w400, color: t.caption, height: 1.5),
      labelStyle: TextStyle(
          fontSize: 14, fontWeight: FontWeight.w500, color: t.body, height: 1.5),
      floatingLabelStyle: TextStyle(
          fontSize: 12, fontWeight: FontWeight.w700, color: t.primary, height: 1.4),
      contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 16),
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
      // display → headlineLarge
      headlineLarge: TextStyle(fontSize: 40, fontWeight: FontWeight.w800, color: t.title, height: 1.2),
      // h1
      headlineMedium: TextStyle(fontSize: 32, fontWeight: FontWeight.w800, color: t.title, height: 1.25),
      // h2
      headlineSmall: TextStyle(fontSize: 24, fontWeight: FontWeight.w700, color: t.title, height: 1.3),
      // h3（修正：w600→w700）
      titleLarge: TextStyle(fontSize: 20, fontWeight: FontWeight.w700, color: t.title, height: 1.3),
      // subtitle
      titleMedium: TextStyle(fontSize: 18, fontWeight: FontWeight.w700, color: t.title, height: 1.35),
      // lg（修正：w500→w600）
      titleSmall: TextStyle(fontSize: 16, fontWeight: FontWeight.w600, color: t.title, height: 1.4),
      // md
      bodyLarge: TextStyle(fontSize: 14, fontWeight: FontWeight.w400, color: t.body, height: 1.5),
      // sm
      bodyMedium: TextStyle(fontSize: 12, fontWeight: FontWeight.w400, color: t.body, height: 1.5),
      // xs
      bodySmall: TextStyle(fontSize: 11, fontWeight: FontWeight.w700, color: t.caption, height: 1.4),
      // tiny
      labelSmall: TextStyle(fontSize: 10, fontWeight: FontWeight.w800, color: t.caption, height: 1.4),
      // micro
      labelMedium: TextStyle(fontSize: 9, fontWeight: FontWeight.w800, color: t.caption, height: 1.4),
    ),
  );
}

/// 双主题预设入口。
ThemeData get creamTheme => buildBrandTheme(AppTokens.cream);
ThemeData get matchaTheme => buildBrandTheme(AppTokens.matcha);

/// 语义化文字样式（11 档，对齐 tokens.json typography.scale + 便捷别名）。
///
/// 用法：
/// ```dart
/// final ts = AppTokens.of(context).textStyles;
/// Text('标题', style: ts.pageTitle);
/// Text('正文', style: ts.body);
/// Text('芯片', style: ts.chip);          // tiny
/// Text('标签', style: ts.sectionLabel);  // xs
/// ```
///
/// 需要自定义颜色时用 `.copyWith(color: ...)`：
/// ```dart
/// Text('缺 / 空', style: ts.sectionLabel.copyWith(color: AppTokens.error));
/// ```
class VxTextStyles {
  // —— 基础阶梯（对齐 tokens.json，带默认颜色）——
  final TextStyle display;   // 40/w800 title
  final TextStyle h1;        // 32/w800 title
  final TextStyle h2;        // 24/w700 title
  final TextStyle h3;        // 20/w700 title
  final TextStyle subtitle;  // 18/w700 title
  final TextStyle lg;        // 16/w600 title
  final TextStyle md;        // 14/w400 body
  final TextStyle sm;        // 12/w400 body
  final TextStyle xs;        // 11/w700 caption
  final TextStyle tiny;      // 10/w800 caption
  final TextStyle micro;     //  9/w800 caption

  const VxTextStyles({
    required this.display,
    required this.h1,
    required this.h2,
    required this.h3,
    required this.subtitle,
    required this.lg,
    required this.md,
    required this.sm,
    required this.xs,
    required this.tiny,
    required this.micro,
  });

  factory VxTextStyles.fromTokens(AppTokens t) {
    return VxTextStyles(
      display:  TextStyle(fontSize: 40, fontWeight: FontWeight.w800, color: t.title, height: 1.2),
      h1:       TextStyle(fontSize: 32, fontWeight: FontWeight.w800, color: t.title, height: 1.25),
      h2:       TextStyle(fontSize: 24, fontWeight: FontWeight.w700, color: t.title, height: 1.3),
      h3:       TextStyle(fontSize: 20, fontWeight: FontWeight.w700, color: t.title, height: 1.3),
      subtitle: TextStyle(fontSize: 18, fontWeight: FontWeight.w700, color: t.title, height: 1.35),
      lg:       TextStyle(fontSize: 16, fontWeight: FontWeight.w600, color: t.title, height: 1.4),
      md:       TextStyle(fontSize: 14, fontWeight: FontWeight.w400, color: t.body,  height: 1.5),
      sm:       TextStyle(fontSize: 12, fontWeight: FontWeight.w400, color: t.body,  height: 1.5),
      xs:       TextStyle(fontSize: 11, fontWeight: FontWeight.w700, color: t.caption, height: 1.4),
      tiny:     TextStyle(fontSize: 10, fontWeight: FontWeight.w800, color: t.caption, height: 1.4),
      micro:    TextStyle(fontSize:  9, fontWeight: FontWeight.w800, color: t.caption, height: 1.4),
    );
  }

  // —— 便捷语义别名 ——

  /// 页面主标题（16/w700，居中顶栏用）。
  TextStyle get pageTitle => lg.copyWith(fontWeight: FontWeight.w700);

  /// 卡片/列表项标题（14/w700，比正文稍重）。
  TextStyle get cardTitle => md.copyWith(fontWeight: FontWeight.w700, color: md.color);

  /// 分区标签、section header（11/w700，可 .copyWith(color:) 改语义色）。
  TextStyle get sectionLabel => xs;

  /// chip / badge 内文字（10/w800）。
  TextStyle get chip => tiny;

  /// 正文，14px。
  TextStyle get body => md;

  /// 辅助说明，12px。
  TextStyle get caption => sm;

  /// Tab 栏、页脚元信息，9px。
  TextStyle get meta => micro;

  /// 输入框内文字（14/w500）。
  TextStyle get input => TextStyle(
      fontSize: 14, fontWeight: FontWeight.w500, color: md.color, height: 1.5);

  /// 危险操作按钮文字（删除/移除/撤回，14/w700 error）。
  TextStyle get danger => const TextStyle(
      fontSize: 14, fontWeight: FontWeight.w700, color: AppTokens.error, height: 1.2);
}
