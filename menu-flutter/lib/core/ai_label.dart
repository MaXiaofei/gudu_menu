/// AI 生成内容标识（隐私政策第三节第 4、7 条；mini-release-compliance A.4）。
/// 文案与小程序 `menu-mini/src/utils/aiLabel.ts` 保持一致。
library;

/// 标识文字。
const String kAiLabel = 'AI 生成';

/// 免责提示（营养报告、特殊人群相关推荐、周餐程）。
const String kAiDisclaimer = 'AI 生成，仅供参考，不能替代医生或营养师建议';

/// 免责提示去掉「AI 生成，」前缀（紧跟在标识徽章后面显示）。
const String kAiDisclaimerTail = '仅供参考，不能替代医生或营养师建议';

/// 咕嘟助手首屏的 AI 身份说明。
const String kAiAssistantIntro = '我是 AI 助手，回答由人工智能生成，可能不准确，仅供参考';

/// AI 内容来源（反馈时带上，便于定位是哪个功能）。
enum AiSource {
  agent('咕嘟助手'),
  scan('拍照识别'),
  nutrition('营养报告'),
  recommend('智荐'),
  weekplan('一周餐程');

  const AiSource(this.label);
  final String label;

  static AiSource? parse(String? v) {
    for (final s in values) {
      if (s.name == v) return s;
    }
    return null;
  }
}

/// AI 内容反馈入口：复用「意见反馈」页，type=ai 并带来源标记（ref 为可选的消息 / 记录 id）。
String aiFeedbackLocation(AiSource source, [Object? ref]) {
  final q = <String, String>{'type': 'ai', 'source': source.name};
  final r = ref?.toString() ?? '';
  if (r.isNotEmpty) q['ref'] = r;
  return Uri(path: '/feedback', queryParameters: q).toString();
}

/// 反馈 clientInfo 里的来源段，如「source=AI/咕嘟助手#m1」。
String aiSourceTag(AiSource source, [String? ref]) =>
    'source=AI/${source.label}${ref == null || ref.isEmpty ? '' : '#$ref'}';
