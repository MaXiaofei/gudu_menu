/**
 * 敏感个人信息单独同意（mini-release-compliance B.2，隐私政策 §二「敏感个人信息」、§四「儿童个人信息」）。
 *
 * 规则与后端 SensitiveInfoClassifier 一致（后端为准、强制执行）：
 * - 健康档案（healthProfile 任一非空值 / 体控目标 / 活动水平）→ sensitive_health
 * - 不满 14 周岁（age ∈ (0,14) / ageMonth ∈ (0,168) / 特殊人群含「宝宝辅食」）→ 另需监护人 child_info
 *
 * 保存前先查同意状态，缺哪项弹哪项的单独同意窗（说明用途、可撤回；儿童场景明确由监护人同意），同意后才保存。
 */
import {
  getSensitiveConsent,
  grantSensitiveConsent,
  type SensitiveDocType,
} from '@/api/privacy'

export interface SensitivePayload {
  healthProfile?: Record<string, any> | null
  goal?: string | null
  activityLevel?: string | null
}

export const BABY_AUDIENCE = '宝宝辅食'

function present(v: any): boolean {
  if (v === null || v === undefined) return false
  if (typeof v === 'string') return v.trim() !== ''
  if (Array.isArray(v)) return v.some(present)
  if (typeof v === 'object') return Object.values(v).some(present)
  return true
}

function num(v: any): number {
  const n = typeof v === 'number' ? v : parseFloat(String(v ?? ''))
  return Number.isFinite(n) ? n : 0
}

export function hasHealthData(h?: Record<string, any> | null): boolean {
  return !!h && Object.values(h).some(present)
}

export function isChild(h?: Record<string, any> | null): boolean {
  if (!h) return false
  const age = num(h.age)
  if (age > 0 && age < 14) return true
  const months = num(h.ageMonth)
  if (months > 0 && months < 168) return true
  const aud = h.audiences
  if (Array.isArray(aud)) return aud.some((a) => String(a).trim() === BABY_AUDIENCE)
  return typeof aud === 'string' && aud.includes(BABY_AUDIENCE)
}

/** 本次保存需要的单独同意类型。 */
export function requiredConsents(p: SensitivePayload): SensitiveDocType[] {
  const out: SensitiveDocType[] = []
  if (hasHealthData(p.healthProfile) || present(p.goal) || present(p.activityLevel)) out.push('sensitive_health')
  if (isChild(p.healthProfile)) out.push('child_info')
  return out
}

/** 单独同意弹窗文案（uni.showModal：系统原生弹窗，单独展示、需主动点「同意」）。 */
export function consentDialog(types: SensitiveDocType[]): { title: string; content: string } {
  if (types.includes('child_info')) {
    return {
      title: '监护人单独同意',
      content:
        '你正在录入不满 14 周岁儿童的信息（如月龄、过敏、宝宝辅食）。请确认你是该儿童的父母或其他监护人，并同意我们仅为辅食推荐、过敏过滤与营养计算使用这些信息（加密存储，不用于其他目的）。你可随时在「家庭成员」页撤回同意，撤回后我们会删除这些信息。',
    }
  }
  return {
    title: '健康信息单独同意',
    content:
      '身高体重、过敏忌口、特殊饮食人群等健康档案属于敏感个人信息。我们仅用于营养计算、过滤不适合的菜品和 AI 回答相关问题（加密存储，不用于其他目的）。不填写不影响其他功能。你可随时在「家庭成员」页撤回同意，撤回后我们会删除这些信息。',
  }
}

export function askModal(title: string, content: string, confirmText = '同意', cancelText = '不同意'): Promise<boolean> {
  return new Promise((resolve) => {
    uni.showModal({
      title,
      content,
      confirmText,
      cancelText,
      success: (r: any) => resolve(!!r?.confirm),
      fail: () => resolve(false),
    })
  })
}

/**
 * 弹窗并记录同意。known 为已知缺少的类型（如后端 428 返回的 docTypes），不传则按 payload 判定后查询后端状态。
 * 返回 true = 可以保存；false = 用户不同意（不保存）。
 */
export async function ensureSensitiveConsent(
  p: SensitivePayload,
  known?: SensitiveDocType[],
): Promise<boolean> {
  let missing: SensitiveDocType[]
  if (known && known.length) {
    missing = known
  } else {
    const need = requiredConsents(p)
    if (!need.length) return true
    const status = await getSensitiveConsent()
    missing = need.filter((t) => !status[t].granted)
  }
  if (!missing.length) return true
  // 儿童场景：同一个弹窗里由监护人一并同意健康档案与儿童信息
  const toGrant: SensitiveDocType[] = missing.includes('child_info')
    ? Array.from(new Set<SensitiveDocType>([...missing, ...requiredConsents(p)]))
    : missing
  const { title, content } = consentDialog(toGrant)
  if (!(await askModal(title, content))) return false
  await grantSensitiveConsent(toGrant)
  return true
}

/** 撤回确认文案。 */
export function withdrawDialog(type: SensitiveDocType): { title: string; content: string } {
  return type === 'child_info'
    ? {
        title: '撤回儿童信息同意',
        content: '撤回后，你录入的不满 14 周岁儿童的健康信息（月龄、过敏、宝宝辅食等）将被删除，成员称呼保留。确定撤回？',
      }
    : {
        title: '撤回健康信息同意',
        content: '撤回后，你本人及你填写的家庭成员健康档案（身高体重、过敏、特殊人群、体控目标等）将被删除，成员称呼保留。确定撤回？',
      }
}
