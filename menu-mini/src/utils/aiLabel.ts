/**
 * AI 生成内容标识（隐私政策第三节第 4、7 条；mini-release-compliance A.4）。
 * 文案与 Flutter `lib/core/ai_label.dart` 保持一致。
 */

/** 标识文字。 */
export const AI_LABEL = 'AI 生成'

/** 免责提示（营养报告、特殊人群相关推荐、周餐程）。 */
export const AI_DISCLAIMER = 'AI 生成，仅供参考，不能替代医生或营养师建议'

/** 免责提示去掉「AI 生成，」前缀的部分（紧跟在标识胶囊后面显示）。 */
export const AI_DISCLAIMER_TAIL = AI_DISCLAIMER.replace(`${AI_LABEL}，`, '')

/** 咕嘟助手首屏的 AI 身份说明。 */
export const AI_ASSISTANT_INTRO = '我是 AI 助手，回答由人工智能生成，可能不准确，仅供参考'

/** AI 内容来源（反馈时带上，便于定位是哪个功能）。 */
export type AiSource = 'agent' | 'scan' | 'nutrition' | 'recommend' | 'weekplan'

export const AI_SOURCE_LABEL: Record<AiSource, string> = {
  agent: '咕嘟助手',
  scan: '拍照识别',
  nutrition: '营养报告',
  recommend: '智荐',
  weekplan: '一周餐程',
}

/** AI 内容反馈入口：复用「意见反馈」页，type=ai 并带来源标记（ref 为可选的消息 / 记录 id）。 */
export function aiFeedbackUrl(source: AiSource, ref?: string | number): string {
  let url = `/pages/profile/Feedback?type=ai&source=${source}`
  if (ref !== undefined && ref !== null && String(ref) !== '') url += `&ref=${encodeURIComponent(String(ref))}`
  return url
}

/** 反馈页 clientInfo 里的来源段，如「来源:AI/咕嘟助手#m123」。 */
export function aiSourceTag(source?: string, ref?: string): string {
  if (!source) return ''
  const label = (AI_SOURCE_LABEL as Record<string, string>)[source] || source
  return `来源:AI/${label}${ref ? '#' + ref : ''}`
}
