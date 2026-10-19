import 'package:flutter/material.dart';

import '../core/app_theme.dart';

/// 分段 Tab（原型：食集详情 菜/备菜/采购/一起吃）。secondary 底槽 + 白色选中块。
class SegmentTabs extends StatelessWidget {
  final List<String> labels;
  final int index;
  final ValueChanged<int> onChanged;
  const SegmentTabs({super.key, required this.labels, required this.index, required this.onChanged});

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    final ts = t.textStyles;
    return Container(
      padding: const EdgeInsets.all(AppTokens.sp4),
      decoration: BoxDecoration(
        color: t.secondary,
        borderRadius: BorderRadius.circular(AppTokens.rMd),
      ),
      child: Row(
        children: [
          for (var i = 0; i < labels.length; i++)
            Expanded(
              child: GestureDetector(
                behavior: HitTestBehavior.opaque,
                onTap: () => onChanged(i),
                child: AnimatedContainer(
                  duration: const Duration(milliseconds: 150),
                  height: 36,
                  alignment: Alignment.center,
                  decoration: BoxDecoration(
                    color: i == index ? t.card : null,
                    borderRadius: BorderRadius.circular(9),
                    boxShadow: i == index ? t.elevationSm : null,
                  ),
                  child: Text(
                    labels[i],
                    style: ts.body.copyWith(
                      color: i == index ? t.title : t.body,
                      fontWeight: i == index ? FontWeight.w700 : FontWeight.w400,
                    ),
                  ),
                ),
              ),
            ),
        ],
      ),
    );
  }
}

/// 下划线 Tab（菜谱「全部/我家的/公共库/收藏」）。
class UnderlineTabs extends StatelessWidget {
  final List<String> labels;
  final int index;
  final ValueChanged<int> onChanged;
  const UnderlineTabs({super.key, required this.labels, required this.index, required this.onChanged});

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    final ts = t.textStyles;
    return Container(
      decoration: BoxDecoration(border: Border(bottom: BorderSide(color: t.border))),
      child: Row(
        children: [
          for (var i = 0; i < labels.length; i++)
            GestureDetector(
              behavior: HitTestBehavior.opaque,
              onTap: () => onChanged(i),
              child: Container(
                margin: const EdgeInsets.only(right: AppTokens.sp20),
                padding: const EdgeInsets.symmetric(vertical: AppTokens.sp8),
                decoration: BoxDecoration(
                  border: Border(
                    bottom: BorderSide(color: i == index ? t.primaryDeep : Colors.transparent, width: 3),
                  ),
                ),
                child: Text(
                  labels[i],
                  style: ts.lg.copyWith(
                    color: i == index ? t.title : t.body,
                    fontWeight: i == index ? FontWeight.w700 : FontWeight.w400,
                  ),
                ),
              ),
            ),
        ],
      ),
    );
  }
}
