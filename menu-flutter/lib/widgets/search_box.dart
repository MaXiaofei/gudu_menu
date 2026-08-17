import 'package:flutter/material.dart';

import '../core/app_theme.dart';

/// 统一搜索框（DESIGN §11.1.2，2026-08-17 定稿为推荐输入框样式）：
/// 奶油底填充（t.bg）+ 1.5px 细边框 + rMd 圆角（继承全局 inputDecorationTheme），
/// 框内前置放大镜，输入 14px、hint 12px 灰，有文字时尾部 ✕ 清除。
///
/// [customCursor]（菜谱页原型特色）：隐藏系统光标，由 [cursorWidget] 提供闪烁竖线，
/// 输入框获取焦点且有文字时显示在 ✕ 前。
class SearchBox extends StatefulWidget {
  final TextEditingController? controller;
  final FocusNode? focusNode;
  final String hint;
  final ValueChanged<String>? onChanged;
  final ValueChanged<String>? onSubmitted;
  final bool autoFocus;
  /// 菜谱页：true 时用自定义闪烁光标替代系统光标。
  final bool customCursor;
  final Widget? cursorWidget;

  const SearchBox({
    super.key,
    this.controller,
    this.focusNode,
    this.hint = '搜索',
    this.onChanged,
    this.onSubmitted,
    this.autoFocus = false,
    this.customCursor = false,
    this.cursorWidget,
  });

  @override
  State<SearchBox> createState() => _SearchBoxState();
}

class _SearchBoxState extends State<SearchBox> {
  late final TextEditingController _ctrl =
      widget.controller ?? TextEditingController();
  late final FocusNode _focus = widget.focusNode ?? FocusNode();
  bool _hasText = false;

  @override
  void dispose() {
    if (widget.controller == null) _ctrl.dispose();
    if (widget.focusNode == null) _focus.dispose();
    super.dispose();
  }

  void _onChanged(String v) {
    final has = v.isNotEmpty;
    if (has != _hasText) setState(() => _hasText = has);
    widget.onChanged?.call(v);
  }

  void _clear() {
    _ctrl.clear();
    _onChanged('');
    widget.onSubmitted?.call('');
  }

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    return TextField(
      controller: _ctrl,
      focusNode: _focus,
      style: t.textStyles.input.copyWith(color: t.title),
      // 菜谱页：隐藏系统光标用闪烁竖线；其余用默认光标
      showCursor: !widget.customCursor,
      autofocus: widget.autoFocus,
      // 继承全局输入主题（奶油底填充 + 1.5px 描边 + rMd 圆角，DESIGN §11.1.2）
      decoration: InputDecoration(
        hintText: widget.hint,
        prefixIcon: const Icon(Icons.search, size: 18),
        isDense: true,
        suffixIcon: _hasText
            ? Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  // 自定义闪烁光标（菜谱页原型：聚焦且有文字时显示）
                  if (widget.customCursor && _focus.hasFocus)
                    widget.cursorWidget ?? const _BlinkingCursor(),
                  const SizedBox(width: AppTokens.sp4),
                  GestureDetector(
                    onTap: _clear,
                    child: Padding(
                      padding: const EdgeInsets.only(right: AppTokens.sp8),
                      child: Text('✕', style: t.textStyles.xs),
                    ),
                  ),
                ],
              )
            : null,
      ),
      onChanged: _onChanged,
      onSubmitted: widget.onSubmitted,
    );
  }
}

/// 橙色闪烁竖线光标（菜谱页原型：搜索中态光标）。
class _BlinkingCursor extends StatefulWidget {
  const _BlinkingCursor();

  @override
  State<_BlinkingCursor> createState() => _BlinkingCursorState();
}

class _BlinkingCursorState extends State<_BlinkingCursor>
    with SingleTickerProviderStateMixin {
  late final AnimationController _ac = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 900),
  )..repeat(reverse: true);

  @override
  void dispose() {
    _ac.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return FadeTransition(
      opacity: Tween(begin: 0.15, end: 1.0).animate(_ac),
      child: Container(
        width: 1.5,
        height: 15,
        color: AppTokens.of(context).primary,
      ),
    );
  }
}
