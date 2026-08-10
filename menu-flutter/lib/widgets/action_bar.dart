import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../core/app_theme.dart';

/// Tab 主页顶部操作栏（DESIGN.md §13.1 / §13.3）。
///
/// **无标题**——Tab 主页不放页面名（连「菜谱」「食集」也不放），
/// 当前所在 tab 靠底部 tab bar 选中态提示。
///
/// - 有操作（如「新建食集」、库存「➕」）：操作按钮右对齐独占一行。
/// - 无操作：本组件不渲染任何东西（返回 SizedBox.shrink），
///   页面内容直接顶到状态栏下。
///
/// 内置 [AnnotatedRegion] 自动设置状态栏奶油底 + 深色字（§13.4），
/// 调用方无需手动处理状态栏。
class ActionBar extends StatelessWidget {
  /// 右对齐的操作槽（如「新建食集」胶囊按钮、库存的 ➕ 图标）。为空则本组件不渲染。
  final Widget? action;

  /// 操作行内边距。默认 `EdgeInsets.fromLTRB(sp16, sp8, sp16, sp8)`。
  final EdgeInsets padding;

  const ActionBar({
    super.key,
    this.action,
    this.padding = const EdgeInsets.fromLTRB(
        AppTokens.sp16, AppTokens.sp8, AppTokens.sp16, AppTokens.sp8),
  });

  @override
  Widget build(BuildContext context) {
    // 无操作 → 不渲染，页面内容直接顶到状态栏下（§13.1）。
    if (action == null) return const SizedBox.shrink();

    final t = AppTokens.of(context);
    return AnnotatedRegion<SystemUiOverlayStyle>(
      value: _statusBarStyle(t),
      child: Container(
        color: t.bg,
        padding: padding,
        alignment: Alignment.centerRight,
        child: action,
      ),
    );
  }
}

/// 详情/录入页返回头（DESIGN.md §13.1 / §13.2 / §13.3）。
///
/// **详情页**（菜品/食集/食材详情）：传 [title] = 真实内容名（菜名/食集名/食材名），
/// 渲染「箭头行 + 间距 sp8 + 标题行」。
///
/// **录入/点评页**（写菜谱、写点评、录入食材、手动添加库存、AI 估营养）：
/// 不传 [title]，只渲染返回箭头行（通用标签全删，§13.1）。
///
/// 内置 [AnnotatedRegion] 自动设置状态栏奶油底 + 深色字（§13.4）。
///
/// 结构（详情页）：
/// ```
/// 返回箭头行（独占，高 44 含点击热区）
///       ↕ 间距 AppTokens.sp8（8px）
/// 真实名（h3，18/w700，深色）
/// ```
class BackHeader extends StatelessWidget {
  /// 标题文案。详情页传真实内容名；录入页不传（只渲染箭头行）。
  final String? title;

  /// 标题右侧的可选操作槽（如食集详情头部状态胶囊可放这里，或录入页右上角「保存」）。
  final Widget? action;

  /// 标题下方的副信息（小字，如「备料5分·烹饪5分」「份数4·关联3道菜」）。
  final Widget? subtitle;

  const BackHeader({super.key, this.title, this.action, this.subtitle});

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    return AnnotatedRegion<SystemUiOverlayStyle>(
      value: _statusBarStyle(t),
      child: Material(
        color: t.bg,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: AppTokens.sp4),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              // 箭头行（独占，高 44 含点击热区）。
              const SizedBox(
                height: 44,
                child: Align(
                  alignment: Alignment.centerLeft,
                  child: BackButton(),
                ),
              ),
              // 有标题 → 渲染标题行（与箭头间距 sp8，§13.2 强约束）。
              if (title != null) ...[
                const SizedBox(height: AppTokens.sp8),
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: AppTokens.sp12),
                  child: Row(
                    children: [
                      Expanded(
                        child: Text(
                          title!,
                          style: t.textStyles.h3,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                      if (action != null) action!,
                    ],
                  ),
                ),
                if (subtitle != null)
                  Padding(
                    padding: const EdgeInsets.only(
                      top: AppTokens.sp4,
                      left: AppTokens.sp12,
                      right: AppTokens.sp12,
                    ),
                    child: subtitle!,
                  ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}

/// 状态栏样式：奶油底 + 深色字（§13.4 铁律：状态栏背景 = 顶栏背景）。
SystemUiOverlayStyle _statusBarStyle(AppTokens t) => SystemUiOverlayStyle(
      statusBarColor: t.bg,
      statusBarIconBrightness: Brightness.dark,
      statusBarBrightness: Brightness.light,
      systemNavigationBarColor: t.bg,
      systemNavigationBarIconBrightness: Brightness.dark,
    );
