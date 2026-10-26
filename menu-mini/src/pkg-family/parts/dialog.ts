/** 家庭 / 账号分包的对话框小工具（Promise 化 uni.showModal）。 */

export function confirm(title: string, content: string, confirmText = '确定'): Promise<boolean> {
  return new Promise((resolve) => {
    uni.showModal({
      title,
      content,
      confirmText,
      success: (r) => resolve(!!r.confirm),
      fail: () => resolve(false),
    })
  })
}

/** 可输入的确认框；取消返回 null。 */
export function inputModal(title: string, value = '', placeholder = ''): Promise<string | null> {
  return new Promise((resolve) => {
    uni.showModal({
      title,
      editable: true,
      content: value,
      placeholderText: placeholder,
      success: (r: any) => resolve(r.confirm ? String(r.content ?? '') : null),
      fail: () => resolve(null),
    } as any)
  })
}

/** 后端 LocalDateTime（2026-10-26T09:00:00）→ 10-26 09:00。 */
export function fmt(s?: string | null): string {
  if (!s) return ''
  const t = s.replace('T', ' ')
  return t.length >= 16 ? t.slice(5, 16) : t
}
