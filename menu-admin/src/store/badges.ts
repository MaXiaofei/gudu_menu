import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getOpsStats } from '@/api/ops'
import { getCommunityStats } from '@/api/communityAdmin'

/**
 * 侧栏角标：「待处理反馈」（/ops/stats totals.feedbackOpen）与「社区待人工审核」（/admin/community/stats pending）。
 * 投稿待审核、营养待核对等角标等对应接口上线后再加，不显示假数字。
 */
export const useBadgeStore = defineStore('badges', () => {
  const feedbackOpen = ref(0)
  const communityPending = ref(0)

  function setFeedbackOpen(n: number | null | undefined) {
    feedbackOpen.value = Math.max(0, Number(n) || 0)
  }

  function setCommunityPending(n: number | null | undefined) {
    communityPending.value = Math.max(0, Number(n) || 0)
  }

  async function refresh() {
    // 两个来源互不影响；角标是辅助信息，拉取失败不打扰（request 层已统一提示）
    await Promise.allSettled([
      getOpsStats(7).then((s) => setFeedbackOpen(s?.totals?.feedbackOpen)),
      getCommunityStats().then((s) => setCommunityPending(s?.pending)),
    ])
  }

  return { feedbackOpen, communityPending, setFeedbackOpen, setCommunityPending, refresh }
})
