import type { ModerateAction, Risk } from '@/api/communityAdmin'

// 社区审核：风险分级展示、正文可疑片段高亮、处置表单校验（纯函数）

export const RISK_META: Record<Risk, { label: string; tone: 'err' | 'warn' | 'ok' }> = {
  HIGH: { label: '高风险', tone: 'err' },
  MEDIUM: { label: '中风险', tone: 'warn' },
  LOW: { label: '低风险', tone: 'ok' },
}

export const ACTION_LABEL: Record<ModerateAction, string> = {
  APPROVE: '通过',
  HIDE: '隐藏',
  BAN_7D: '封禁 7 天',
}

export const MODERATE_REASONS = [
  '广告营销 / 导流',
  '健康误导',
  '敏感词',
  '图文不符',
  '抄袭搬运',
  '误判，内容合规',
  '其他',
] as const

export interface ModerationForm {
  action: ModerateAction | ''
  reason: string
  note: string
}

export const NOTE_MAX = 200

/**
 * 处置校验：动作与原因必填；原因选「其他」或动作为封禁时必须写说明（写进审计日志）；
 * 说明不超过 200 字；「误判，内容合规」只能配「通过」。
 */
export function validateModeration(f: ModerationForm): Record<string, string> {
  const errors: Record<string, string> = {}
  const note = (f.note ?? '').trim()
  if (!f.action) errors.action = '请选择处理方式'
  if (!f.reason?.trim()) errors.reason = '请选择处理原因'
  if (!note && (f.reason === '其他' || f.action === 'BAN_7D')) {
    errors.note = f.action === 'BAN_7D' ? '封禁需填写说明，会通知作者并写入审计日志' : '原因为「其他」时需填写说明'
  }
  if (note.length > NOTE_MAX) errors.note = `说明不超过 ${NOTE_MAX} 字`
  if (f.reason === '误判，内容合规' && f.action && f.action !== 'APPROVE') errors.reason = '「误判」只能配合「通过」'
  return errors
}

export type HlKind = 'contact' | 'promo'

// 联系方式：加V/加微/微信/vx/qq + 后续号码，以及掩码手机号；促销：满 N 包邮、领券、返现、秒杀、优惠价
const CONTACT_RE = /(加\s*[vV微]|微信号?|[vV][xX]|[qQ]{2})\s*[:：]?\s*[A-Za-z0-9_*]{0,20}|1\d{2}\*{4}\d{4}/
const PROMO_RE = /满\s*\d+\s*[件包份单]?\s*(包邮|减\s*\d+)|领(券|取|试吃装)|返现|秒杀|优惠价|限时折扣/

/** 把正文切成段，联系方式 / 促销用语标记出来供高亮。 */
export function highlightSegments(text: string): { text: string; kind: HlKind | null }[] {
  const out: { text: string; kind: HlKind | null }[] = []
  let rest = text ?? ''
  while (rest) {
    const c = CONTACT_RE.exec(rest)
    const p = PROMO_RE.exec(rest)
    const hit = [c && { m: c, kind: 'contact' as const }, p && { m: p, kind: 'promo' as const }]
      .filter(Boolean)
      .sort((a, b) => a!.m.index - b!.m.index)[0]
    if (!hit || !hit.m[0]) {
      out.push({ text: rest, kind: null })
      break
    }
    if (hit.m.index > 0) out.push({ text: rest.slice(0, hit.m.index), kind: null })
    out.push({ text: hit.m[0], kind: hit.kind })
    rest = rest.slice(hit.m.index + hit.m[0].length)
  }
  return out
}

/** AI 分项分数条的颜色档：≥0.8 红、≥0.4 黄、其余绿。数量类（unit 存在）只要 >0 即红。 */
export function scoreTone(score: number, unit?: string): 'err' | 'warn' | 'ok' {
  if (unit) return score > 0 ? 'err' : 'ok'
  return score >= 0.8 ? 'err' : score >= 0.4 ? 'warn' : 'ok'
}
