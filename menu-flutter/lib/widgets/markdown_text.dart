import 'package:flutter/material.dart';

import '../core/app_theme.dart';

/// 极简 Markdown 渲染（用户协议 / 隐私政策静态页用）。
///
/// 只覆盖 `docs/legal/*.md` 实际用到的语法，不引入第三方 markdown 包：
/// `#`/`##`/`###` 标题、段落、`- ` 无序列表、`1. ` 有序列表（原样保留序号）、
/// `| a | b |` 表格（跳过 `|---|` 分隔行）、`---` 分割线、行内 `**粗体**`。
class MarkdownText extends StatelessWidget {
  final String source;
  const MarkdownText(this.source, {super.key});

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    final ts = t.textStyles;
    final blocks = <Widget>[];
    final lines = source.replaceAll('\r\n', '\n').split('\n');
    var i = 0;
    while (i < lines.length) {
      final raw = lines[i];
      final line = raw.trim();
      if (line.isEmpty) {
        i++;
        continue;
      }
      if (line.startsWith('|')) {
        final rows = <List<String>>[];
        while (i < lines.length && lines[i].trim().startsWith('|')) {
          final r = lines[i].trim();
          if (!RegExp(r'^\|[\s:\-|]+\|?$').hasMatch(r)) {
            rows.add(_cells(r));
          }
          i++;
        }
        blocks.add(_table(t, rows));
        continue;
      }
      if (RegExp(r'^-{3,}$').hasMatch(line)) {
        blocks.add(const Padding(
          padding: EdgeInsets.symmetric(vertical: AppTokens.sp8),
          child: Divider(height: 1),
        ));
        i++;
        continue;
      }
      if (line.startsWith('### ')) {
        blocks.add(_pad(_rich(t, line.substring(4), ts.cardTitle.copyWith(color: t.title)),
            top: AppTokens.sp12));
      } else if (line.startsWith('## ')) {
        blocks.add(_pad(_rich(t, line.substring(3), ts.lg.copyWith(fontWeight: FontWeight.w700)),
            top: AppTokens.sp16));
      } else if (line.startsWith('# ')) {
        blocks.add(_pad(_rich(t, line.substring(2), ts.h3), top: AppTokens.sp4));
      } else if (line.startsWith('- ') || line.startsWith('* ')) {
        blocks.add(_pad(
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('·  ', style: ts.body),
              Expanded(child: _rich(t, line.substring(2), ts.body)),
            ],
          ),
          top: AppTokens.sp4,
        ));
      } else {
        // 普通段落 / 有序列表项（「1. xxx」原样保留序号）
        blocks.add(_pad(_rich(t, line, ts.body), top: AppTokens.sp6));
      }
      i++;
    }
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: blocks,
    );
  }

  static List<String> _cells(String row) {
    var r = row;
    if (r.startsWith('|')) r = r.substring(1);
    if (r.endsWith('|')) r = r.substring(0, r.length - 1);
    return r.split('|').map((c) => c.trim()).toList();
  }

  Widget _pad(Widget child, {double top = 0}) =>
      Padding(padding: EdgeInsets.only(top: top), child: child);

  Widget _table(AppTokens t, List<List<String>> rows) {
    if (rows.isEmpty) return const SizedBox.shrink();
    final cols = rows.map((r) => r.length).reduce((a, b) => a > b ? a : b);
    return Padding(
      padding: const EdgeInsets.only(top: AppTokens.sp8),
      child: Table(
        border: TableBorder.all(color: t.border),
        defaultVerticalAlignment: TableCellVerticalAlignment.top,
        children: [
          for (var r = 0; r < rows.length; r++)
            TableRow(
              decoration: BoxDecoration(color: r == 0 ? t.secondary : t.card),
              children: [
                for (var c = 0; c < cols; c++)
                  Padding(
                    padding: const EdgeInsets.all(AppTokens.sp6),
                    child: _rich(
                      t,
                      c < rows[r].length ? rows[r][c] : '',
                      r == 0
                          ? t.textStyles.sectionLabel.copyWith(color: t.title)
                          : t.textStyles.caption,
                    ),
                  ),
              ],
            ),
        ],
      ),
    );
  }

  /// 行内 `**粗体**` → TextSpan。
  Widget _rich(AppTokens t, String text, TextStyle base) {
    final spans = <TextSpan>[];
    final parts = text.split('**');
    for (var k = 0; k < parts.length; k++) {
      if (parts[k].isEmpty) continue;
      spans.add(TextSpan(
        text: parts[k],
        style: k.isOdd
            ? base.copyWith(fontWeight: FontWeight.w700, color: t.title)
            : null,
      ));
    }
    return Text.rich(TextSpan(style: base, children: spans));
  }
}
