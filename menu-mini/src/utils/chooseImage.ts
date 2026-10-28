/**
 * 选图（chooseMedia / chooseImage）失败时给用户的提示。
 * 隐私指引未声明、隐私授权被拒、相机相册权限被拒时微信只回 errMsg，
 * 之前静默返回空，用户看到的是「点了没反应」。用户主动取消不提示。
 */
export function chooseImageFailHint(errMsg?: string): string | null {
  const msg = String(errMsg || '')
  if (/cancel/i.test(msg)) return null
  if (/privacy|scope is not declared/i.test(msg)) return '需要先同意隐私授权才能选择图片'
  if (/auth|deny|denied|permission/i.test(msg)) return '没有相机或相册权限，请在设置里打开后重试'
  return '没能选择图片，请重试'
}

/** 有提示时弹出 toast。 */
export function toastChooseImageFail(errMsg?: string): void {
  const hint = chooseImageFailHint(errMsg)
  if (hint) uni.showToast({ title: hint, icon: 'none' })
}
