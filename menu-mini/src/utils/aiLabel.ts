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

/**
 * 健康类免责提示（不带「AI 生成」）：营养报告（规则计算）、智荐（本地向量召回）不是大模型生成，
 * 只提示仅供参考，不标 AI。
 */
export const HEALTH_DISCLAIMER = '仅供参考，不能替代医生或营养师建议'

/** 咕嘟助手首屏的 AI 身份说明。 */
export const AI_ASSISTANT_INTRO = '我是 AI 助手，回答由人工智能生成，可能不准确，仅供参考'

/** AI 内容来源（反馈时带上，便于定位是哪个功能）。只列真正由大模型生成的功能。 */
export type AiSource = 'agent' | 'scan' | 'weekplan'

export const AI_SOURCE_LABEL: Record<AiSource, string> = {
  agent: '咕嘟助手',
  scan: '拍照识别',
  weekplan: '一周餐程',
}

/** AI 内容反馈入口：复用「意见反馈」页，type=ai 并带来源标记（ref 为可选的消息 / 记录 id）。 */
export function aiFeedbackUrl(source: AiSource, ref?: string | number): string {
  let url = `/pages/profile/Feedback?type=ai&source=${source}`
  if (ref !== undefined && ref !== null && String(ref) !== '') url += `&ref=${encodeURIComponent(String(ref))}`
  return url
}

/**
 * AI 内容反馈的来源引用（后端 feedback.source_ref，A.11）：「agent:m123」/「weekplan」。
 * 只接受已知来源，其他返回空串（避免 URL 参数被随意塞内容）。
 */
export function aiSourceRef(source?: string, ref?: string): string {
  if (!source || !(source in AI_SOURCE_LABEL)) return ''
  const r = (ref ?? '').trim()
  return (r ? `${source}:${r}` : source).slice(0, 128)
}
