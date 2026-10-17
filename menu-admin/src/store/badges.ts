import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getOpsStats } from '@/api/ops'

/**
 * 侧栏角标：目前只有「待处理反馈」有真实数据（/ops/stats totals.feedbackOpen）。
 * 投稿待审核、营养待核对等角标等对应接口上线后再加，不显示假数字。
 */
export const useBadgeStore = defineStore('badges', () => {
  const feedbackOpen = ref(0)

  function setFeedbackOpen(n: number | null | undefined) {
    feedbackOpen.value = Math.max(0, Number(n) || 0)
  }

  async function refresh() {
    try {
      const s = await getOpsStats(7)
      setFeedbackOpen(s?.totals?.feedbackOpen)
    } catch {
      // 角标是辅助信息，拉取失败不打扰（request 层已统一提示）
    }
  }

  return { feedbackOpen, setFeedbackOpen, refresh }
})
