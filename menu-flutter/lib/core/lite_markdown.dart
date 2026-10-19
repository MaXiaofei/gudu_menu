import 'package:flutter/painting.dart';

/// 咕嘟回复的轻量 Markdown 兜底（后端约定输出纯文本，模型偶尔仍带标记）。
///
/// 只认三种写法，其余字符原样：
/// - `**粗体**` → 加粗；流式中尚未闭合的 `**` 去掉标记按普通文字显示，闭合后再变粗，避免闪出星号。
/// - 行首 `- ` / `* ` / `· ` → 「• 」列表项。
/// - 行首 `#`～`###` + 空格 → 整行加粗。
class LiteMarkdown {
  LiteMarkdown._();

  static final _bullet = RegExp(r'^(\s*)[-*·]\s+');
  static final _heading = RegExp(r'^#{1,3}\s+');

  /// 解析为片段：每段文字 + 是否加粗。换行以 `\n` 文字片段保留。
  static List<({String text, bool bold})> parse(String src) {
    final out = <({String text, bool bold})>[];
    final lines = src.split('\n');
    for (var i = 0; i < lines.length; i++) {
      var line = lines[i];
      var lineBold = false;
      final h = _heading.firstMatch(line);
      if (h != null) {
        line = line.substring(h.end);
        lineBold = true;
      } else {
        final b = _bullet.firstMatch(line);
        if (b != null) line = '${b.group(1)}• ${line.substring(b.end)}';
      }
      _inline(line, lineBold, out);
      if (i < lines.length - 1) out.add((text: '\n', bold: false));
    }
    return _merge(out);
  }

  static void _inline(String line, bool forceBold, List<({String text, bool bold})> out) {
    var bold = false;
    var rest = line;
    while (true) {
      final at = rest.indexOf('**');
      if (at < 0) {
        if (rest.isNotEmpty) out.add((text: rest, bold: forceBold || bold));
        return;
      }
      // 当前不在粗体中且后面没有闭合标记：未闭合（流式中途），去掉标记按普通文字输出
      if (!bold && rest.indexOf('**', at + 2) < 0) {
        final plain = rest.substring(0, at) + rest.substring(at + 2);
        if (plain.isNotEmpty) out.add((text: plain, bold: forceBold));
        return;
      }
      if (at > 0) out.add((text: rest.substring(0, at), bold: forceBold || bold));
      bold = !bold;
      rest = rest.substring(at + 2);
    }
  }

  static List<({String text, bool bold})> _merge(List<({String text, bool bold})> parts) {
    final out = <({String text, bool bold})>[];
    for (final p in parts) {
      if (out.isNotEmpty && out.last.bold == p.bold) {
        out[out.length - 1] = (text: out.last.text + p.text, bold: p.bold);
      } else {
        out.add(p);
      }
    }
    return out;
  }

  /// 转为 TextSpan 列表；粗体用 [boldWeight]，其余继承父样式。
  static List<InlineSpan> spans(String src, {FontWeight boldWeight = FontWeight.w700}) => [
        for (final p in parse(src))
          TextSpan(text: p.text, style: p.bold ? TextStyle(fontWeight: boldWeight) : null),
      ];
}
