import 'package:flutter/painting.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:menu_flutter/core/lite_markdown.dart';

void main() {
  String plain(List<({String text, bool bold})> p) => p.map((e) => e.text).join();
  List<String> bolds(List<({String text, bool bold})> p) => [for (final e in p) if (e.bold) e.text];

  test('粗体闭合后加粗，标记不显示', () {
    final p = LiteMarkdown.parse('推荐**清蒸鲈鱼**，少盐');
    expect(plain(p), '推荐清蒸鲈鱼，少盐');
    expect(bolds(p), ['清蒸鲈鱼']);
  });

  test('流式中未闭合的 ** 去掉标记、不加粗', () {
    final p = LiteMarkdown.parse('推荐**清蒸');
    expect(plain(p), '推荐清蒸');
    expect(bolds(p), isEmpty);
    final q = LiteMarkdown.parse('**番茄**和**鸡');
    expect(plain(q), '番茄和鸡');
    expect(bolds(q), ['番茄']);
  });

  test('列表项与标题', () {
    final p = LiteMarkdown.parse('推荐：\n- **清蒸鲈鱼** 30 分钟\n* 凉拌菠菜\n· 菠菜蛋汤\n## 小贴士');
    expect(plain(p), '推荐：\n• 清蒸鲈鱼 30 分钟\n• 凉拌菠菜\n• 菠菜蛋汤\n小贴士');
    expect(bolds(p), ['清蒸鲈鱼', '小贴士']);
  });

  test('乘号与单个星号原样保留', () {
    expect(plain(LiteMarkdown.parse('2*3 个鸡蛋')), '2*3 个鸡蛋');
    expect(plain(LiteMarkdown.parse('-5 度冷冻')), '-5 度冷冻');
  });

  test('spans 粗体带 w700', () {
    final s = LiteMarkdown.spans('a**b**');
    expect(s.length, 2);
    expect((s[1] as TextSpan).style?.fontWeight, FontWeight.w700);
  });
}
