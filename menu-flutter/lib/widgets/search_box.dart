import 'package:flutter/material.dart';

import '../core/app_theme.dart';

/// 统一搜索框（DESIGN.md §11.1.2，2026-08-14 定稿）：
/// 单层白底 + 细边框(1px) + rMd 圆角，框内前置放大镜，输入/hint 均 14px
/// （hint 仅靠灰色区分层次），有文字时尾部 ✕ 清除。
/// 页面禁止再自画搜索框（重复造轮子）。
///
/// [customCursor]（菜谱页原型特色）：隐藏系统光标，由 [cursorWidget] 提供闪烁竖线，
/// 输入框获取焦点且有文字时显示在输入文字后。
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
    return Container(
      decoration: BoxDecoration(
        color: t.card,
        borderRadius: BorderRadius.circular(AppTokens.rMd),
        border: Border.all(color: t.border, width: 1),
      ),
      padding: const EdgeInsets.symmetric(horizontal: AppTokens.sp12, vertical: 8),
      child: Row(
        children: [
          // 放大镜（框内前置）
          Icon(Icons.search, size: 16, color: t.caption),
          const SizedBox(width: AppTokens.sp6),
          Expanded(
            child: Row(
              children: [
                Flexible(
                  child: TextField(
                    controller: _ctrl,
                    focusNode: _focus,
                    style: t.textStyles.input.copyWith(color: t.title),
                    // 菜谱页：隐藏系统光标用闪烁竖线；其余用默认光标
                    showCursor: !widget.customCursor,
                    autofocus: widget.autoFocus,
                    decoration: InputDecoration(
                      isCollapsed: true,
                      border: InputBorder.none,
                      filled: false, // 内嵌搜索框：关掉全局奶油底，单层白底
                      hintText: widget.hint,
                      hintStyle:
                          t.textStyles.input.copyWith(color: t.caption),
                      contentPadding: EdgeInsets.zero,
                    ),
                    onChanged: _onChanged,
                    onSubmitted: widget.onSubmitted,
                  ),
                ),
                // 自定义闪烁光标（菜谱页原型：输入中且有文字时显示）
                if (widget.customCursor &&
                    _focus.hasFocus &&
                    _hasText) ...[
                  const SizedBox(width: AppTokens.sp8),
                  widget.cursorWidget ?? const _BlinkingCursor(),
                ],
              ],
            ),
          ),
          // ✕ 清除（有文字时）
          if (_hasText)
            GestureDetector(
              onTap: _clear,
              child: Padding(
                padding: const EdgeInsets.only(left: AppTokens.sp8),
                child: Text('✕', style: t.textStyles.xs),
              ),
            ),
        ],
      ),
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
