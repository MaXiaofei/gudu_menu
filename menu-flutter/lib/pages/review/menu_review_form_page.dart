import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../core/app_theme.dart';
import 'review_form.dart';

/// 食集整体评价表单页（V43：复用公共 ReviewForm，提交带 menuId）。
class MenuReviewFormPage extends StatefulWidget {
  final int menuId;
  final String menuName;

  const MenuReviewFormPage({
    super.key,
    required this.menuId,
    required this.menuName,
  });

  @override
  State<MenuReviewFormPage> createState() => _MenuReviewFormPageState();
}

class _MenuReviewFormPageState extends State<MenuReviewFormPage> {
  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    return Scaffold(
      appBar: AppBar(title: const Text('评价这顿饭')),
      body: Column(
        children: [
          // 食集头（名称 + 菜数副信息，对齐统一评价页）
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 12, 16, 0),
            child: Row(
              children: [
                Container(
                  width: 40,
                  height: 40,
                  decoration: BoxDecoration(
                    color: t.secondary,
                    borderRadius: BorderRadius.circular(AppTokens.rMd),
                  ),
                  alignment: Alignment.center,
                  child: Text(
                    widget.menuName.isNotEmpty ? widget.menuName.characters.first : '饭',
                    style: t.textStyles.md.copyWith(color: t.primary),
                  ),
                ),
                const SizedBox(width: AppTokens.sp12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(widget.menuName,
                          style: t.textStyles.cardTitle.copyWith(color: t.title)),
                      Text('这顿饭整体 · 口味/难度/营养均衡/外观',
                          style: t.textStyles.sm.copyWith(color: t.caption)),
                    ],
                  ),
                ),
              ],
            ),
          ),
          Expanded(
            child: ReviewForm(
              menuId: widget.menuId,
              title: '这顿饭整体怎么样？',
              onSuccess: () {
                if (mounted) context.pop();
              },
            ),
          ),
        ],
      ),
    );
  }
}
