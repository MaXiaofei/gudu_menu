/** 表单校验（个人资料 / 意见反馈）。返回错误文案，空串=通过。 */

/** 字符数（按 Unicode 码点，中文/emoji 各算 1）。 */
export function charLen(s: string): number {
  return [...s].length
}

/** 昵称：1–20 字，去首尾空格后非空。 */
export function validateNickname(name: string): string {
  const v = (name ?? '').trim()
  if (!v) return '昵称不能为空'
  if (charLen(v) > 20) return '昵称最多 20 个字'
  return ''
}

export const FEEDBACK_MIN = 5
export const FEEDBACK_MAX = 500
export const FEEDBACK_CONTACT_MAX = 50
export const FEEDBACK_IMAGES_MAX = 3

/** 意见反馈：内容 5–500 字，联系方式 ≤ 50 字，图片 ≤ 3 张。 */
export function validateFeedback(p: { content: string; contact?: string; images?: string[] }): string {
  const c = (p.content ?? '').trim()
  if (charLen(c) < FEEDBACK_MIN) return `请至少写 ${FEEDBACK_MIN} 个字`
  if (charLen(c) > FEEDBACK_MAX) return `最多 ${FEEDBACK_MAX} 个字`
  if (p.contact && charLen(p.contact.trim()) > FEEDBACK_CONTACT_MAX) return `联系方式最多 ${FEEDBACK_CONTACT_MAX} 个字`
  if ((p.images?.length ?? 0) > FEEDBACK_IMAGES_MAX) return `最多 ${FEEDBACK_IMAGES_MAX} 张图片`
  return ''
}

/** 登录前协议勾选（微信审核要求）：未勾选返回提示文案。 */
export function agreementError(agreed: boolean): string {
  return agreed ? '' : '请先阅读并同意《用户协议》和《隐私政策》'
}

/** 上传接口返回的相对地址是否为本站 uploads（PUT /member/me、POST /feedback 要求）。 */
export function isUploadUrl(url?: string | null): boolean {
  return !!url && /\/uploads\//.test(url)
}
