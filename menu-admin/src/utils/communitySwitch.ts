import type { SwitchChange } from '@/api/communityAdmin'

/** 厨房圈紧急关停开关（A.12）的展示与二次确认文案。 */

export const SWITCH_REASON_MAX = 200

export function switchStatusText(enabled: boolean): string {
  return enabled ? '厨房圈运行中' : '厨房圈已暂停'
}

/** 「最后操作：运营小王 · 2026-10-26 18:00」；从未切换过返回「默认开启，尚未操作过」。 */
export function lastOperationText(s: Pick<SwitchChange, 'updatedByName' | 'updatedBy' | 'updatedAt'> | null): string {
  if (!s || (!s.updatedBy && !s.updatedAt)) return '默认开启，尚未操作过'
  const who = s.updatedByName || (s.updatedBy ? `用户 #${s.updatedBy}` : '未知')
  const at = s.updatedAt ? s.updatedAt.replace('T', ' ').slice(0, 16) : '—'
  return `最后操作：${who} · ${at}`
}

/** 二次确认：标题 / 正文 / 按钮。 */
export function confirmCopy(nextEnabled: boolean): { title: string; message: string; confirm: string } {
  return nextEnabled
    ? {
        title: '恢复厨房圈',
        message: '恢复后 App 与网页端的厨房圈读写立即可用（其他服务实例最迟 30 秒生效）。确认风险已处理？',
        confirm: '确认恢复',
      }
    : {
        title: '紧急关停厨房圈',
        message:
          '关停后 App 与网页端的厨房圈浏览、发帖、评论、点赞等全部暂停，用户看到「厨房圈暂停服务」（最迟 30 秒全部生效）。审核后台不受影响。仅用于出现内容风险时，确认关停？',
        confirm: '确认关停',
      }
}

/** 原因：关停时必填（便于事后追溯），恢复时可选；不超过 200 字。 */
export function validateSwitchReason(nextEnabled: boolean, reason: string): string {
  const r = (reason ?? '').trim()
  if (!nextEnabled && !r) return '请填写关停原因'
  if (r.length > SWITCH_REASON_MAX) return `原因不超过 ${SWITCH_REASON_MAX} 字`
  return ''
}
