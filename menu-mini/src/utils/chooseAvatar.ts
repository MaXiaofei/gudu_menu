/**
 * open-type=chooseAvatar 回调没有拿到头像时给用户的提示。
 * 微信在「用户隐私保护指引」未声明头像、隐私授权被拒等情况下只回 errMsg、不给 avatarUrl，
 * 之前静默 return，用户看到的是「点了没反应」。用户主动取消不提示。
 */
export function chooseAvatarFailHint(detail: { avatarUrl?: string; errMsg?: string } | undefined): string | null {
  if (detail?.avatarUrl) return null
  const msg = String(detail?.errMsg || '')
  if (/cancel/i.test(msg)) return null
  if (/privacy|scope is not declared|auth deny|authorize/i.test(msg)) return '需要先同意隐私授权才能使用微信头像'
  return '没有拿到头像，请重试'
}
