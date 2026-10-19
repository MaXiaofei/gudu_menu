/// 跟做模式：从步骤文字里识别时长，生成计时器（cook-mode spec：「大火蒸 8 分钟」→ 8:00）。
///
/// 支持：「8 分钟」「8分钟」「3:00」「1 小时」「半小时」「一个半小时」「30 秒」「十五分钟」
/// 「5-8 分钟」（取上限）「1 小时 20 分钟」（合并）。不认「8 分熟」「七分饱」这类非时长。
class StepTimer {
  /// 原文片段，如「8 分钟」。
  final String text;
  final int seconds;

  /// 片段前的动作词（「大火蒸」「小火煮」），用作计时器名；没有时为空。
  final String action;

  const StepTimer({required this.text, required this.seconds, this.action = ''});

  String get label => action.isEmpty ? text : '$action ${formatClock(seconds)}';

  @override
  bool operator ==(Object other) => other is StepTimer && other.seconds == seconds && other.text == text;

  @override
  int get hashCode => Object.hash(text, seconds);

  @override
  String toString() => 'StepTimer($text, $seconds s, $action)';
}

const _cnDigits = {'零': 0, '〇': 0, '一': 1, '二': 2, '两': 2, '三': 3, '四': 4, '五': 5, '六': 6, '七': 7, '八': 8, '九': 9};

/// 中文数字（≤ 99）→ int；「十五」「二十」「三十五」「两」。无法识别返回 null。
int? parseChineseNumber(String s) {
  if (s.isEmpty) return null;
  final n = int.tryParse(s);
  if (n != null) return n;
  if (s == '十') return 10;
  final i = s.indexOf('十');
  if (i >= 0) {
    final hi = i == 0 ? 1 : _cnDigits[s.substring(0, i)];
    final rest = s.substring(i + 1);
    final lo = rest.isEmpty ? 0 : _cnDigits[rest];
    if (hi == null || lo == null) return null;
    return hi * 10 + lo;
  }
  if (s.length == 1) return _cnDigits[s];
  return null;
}

const _num = r'(\d+(?:\.\d+)?|[零〇一二两三四五六七八九十]+)';
final _range = RegExp('$_num\\s*[-~～到至]\\s*');
final _clock = RegExp(r'(?<![\d:])(\d{1,2}):([0-5]\d)(?![\d:])');
final _unit = RegExp('$_num\\s*(个)?\\s*(半)?\\s*(小时|分钟|分(?![熟饱之])|秒钟?)');

double? _toNum(String s) => double.tryParse(s) ?? parseChineseNumber(s)?.toDouble();

/// 识别一段步骤文字里的所有时长（按出现顺序；同一位置的「1 小时 20 分钟」合并为一个）。
List<StepTimer> parseStepTimers(String text) {
  final found = <({int start, int end, int seconds})>[];

  for (final m in _unit.allMatches(text)) {
    final v = _toNum(m.group(1)!);
    if (v == null) continue;
    final unit = m.group(4)!;
    final half = m.group(3) != null;
    // 区间「5-8 分钟」：正则只匹配到「8 分钟」，取上限即可（更安全，不夹生）
    var secs = unit.startsWith('小时')
        ? (v * 3600).round()
        : unit.startsWith('秒')
            ? v.round()
            : (v * 60).round();
    if (half) secs += unit.startsWith('小时') ? 1800 : 30;
    if (secs <= 0) continue;
    found.add((start: m.start, end: m.end, seconds: secs));
  }
  for (final m in _clock.allMatches(text)) {
    final mm = int.parse(m.group(1)!);
    final ss = int.parse(m.group(2)!);
    if (mm == 0 && ss == 0) continue;
    found.add((start: m.start, end: m.end, seconds: mm * 60 + ss));
  }
  for (final m in RegExp(r'半个?小时').allMatches(text)) {
    // 「一个半小时」已由 _unit 处理；这里只认独立的「半小时」
    if (m.start > 0 && '个一两'.contains(text[m.start - 1])) continue;
    found.add((start: m.start, end: m.end, seconds: 1800));
  }
  if (found.isEmpty) return const [];
  found.sort((a, b) => a.start.compareTo(b.start));

  // 合并紧挨着的「1 小时 20 分钟」
  final merged = <({int start, int end, int seconds})>[];
  for (final f in found) {
    if (merged.isNotEmpty) {
      final last = merged.last;
      if (f.start < last.end) continue; // 重叠（同一片段被两条规则命中）
      final gap = text.substring(last.end, f.start).trim();
      if (gap.isEmpty || gap == '零') {
        merged[merged.length - 1] = (start: last.start, end: f.end, seconds: last.seconds + f.seconds);
        continue;
      }
    }
    merged.add(f);
  }

  return [
    for (final f in merged)
      StepTimer(
        text: text.substring(f.start, f.end).trim(),
        seconds: f.seconds,
        action: _actionBefore(text, _rangeStart(text, f.start)),
      ),
  ];
}

/// 若前面是「5-」这种区间起点，把动作词的截取位置前移到区间开头。
int _rangeStart(String text, int start) {
  for (final m in _range.allMatches(text)) {
    if (m.end == start) return m.start;
  }
  return start;
}

/// 时长前最近的动作短语（2–4 个汉字，如「大火蒸」「小火煮」「焖」）。
String _actionBefore(String text, int start) {
  var i = start;
  while (i > 0 && text[i - 1] == ' ') {
    i--;
  }
  final buf = StringBuffer();
  while (i > 0) {
    final ch = text[i - 1];
    if (RegExp(r'[，。,.;；、！!\s（(]').hasMatch(ch)) break;
    buf.write(ch);
    i--;
    if (buf.length >= 6) break;
  }
  var s = buf.toString().split('').reversed.join();
  s = s.replaceAll(RegExp(r'(约|大约|再|继续|需要|左右|后)$'), '');
  s = s.replaceAll(RegExp(r'^(然后|再|先|接着|最后|盖上锅盖|盖盖)'), '');
  return s.length > 4 ? s.substring(s.length - 4) : s;
}

/// 秒 → 「3:00」「12:40」「1:05:00」。
String formatClock(int seconds) {
  final s = seconds < 0 ? 0 : seconds;
  final h = s ~/ 3600;
  final m = (s % 3600) ~/ 60;
  final sec = s % 60;
  final ss = sec.toString().padLeft(2, '0');
  if (h > 0) return '$h:${m.toString().padLeft(2, '0')}:$ss';
  return '$m:$ss';
}
