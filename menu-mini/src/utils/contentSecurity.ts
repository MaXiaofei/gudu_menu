/**
 * 内容安全（mini-release-compliance A.6）错误码 → 友好提示。
 * 后端：451 = 内容未通过检测；452 = 检测服务暂不可用且该场景失败即拒绝（昵称 / 头像）。
 * 前端按场景给自己的文案，不展示后端原文（避免露出命中词或技术细节）。
 */
export const CONTENT_BLOCKED = 451
export const CONTENT_CHECK_UNAVAILABLE = 452

export type ContentScene = 'nickname' | 'avatar' | 'profile' | 'feedback' | 'agent' | 'default'

const BLOCKED_MSG: Record<ContentScene, string> = {
  nickname: '昵称未通过内容安全检测，换一个试试',
  avatar: '头像未通过内容安全检测，换一张试试',
  profile: '昵称或头像未通过内容安全检测，请修改后再保存',
  feedback: '反馈内容未通过内容安全检测，请修改后再提交',
  agent: '这条消息未通过内容安全检测，换个说法再问吧',
  default: '内容未通过内容安全检测，请修改后再试',
}

export const MSG_CONTENT_UNAVAILABLE = '内容安全检测暂时不可用，请稍后再试'

export function isContentSecurityCode(code: unknown): boolean {
  const n = Number(code)
  return n === CONTENT_BLOCKED || n === CONTENT_CHECK_UNAVAILABLE
}

/** 内容安全错误码对应的友好提示；非内容安全错误码返回 null。 */
export function contentSecurityMessage(code: unknown, scene: ContentScene = 'default'): string | null {
  const n = Number(code)
  if (n === CONTENT_BLOCKED) return BLOCKED_MSG[scene] || BLOCKED_MSG.default
  if (n === CONTENT_CHECK_UNAVAILABLE) return MSG_CONTENT_UNAVAILABLE
  return null
}
