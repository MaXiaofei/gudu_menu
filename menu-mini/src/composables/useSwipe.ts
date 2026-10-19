import { reactive } from 'vue'

/**
 * 左滑露出操作（删除）手势：72px 露出 / 过半吸附 / 纵向位移 >12px 让路滚动。
 * 列表页共用（菜谱、食集、草稿）。
 */
export function useSwipe(width = 72) {
  const offsets = reactive<Record<number, number>>({})
  let startX = 0
  let startY = 0
  let touching = false

  function onTs(e: TouchEvent, _id: number) {
    startX = e.touches[0].clientX
    startY = e.touches[0].clientY
    touching = true
  }
  function onTm(e: TouchEvent, id: number) {
    if (!touching) return
    const dx = e.touches[0].clientX - startX
    const dy = Math.abs(e.touches[0].clientY - startY)
    if (dy > 12) return
    const base = offsets[id] || 0
    offsets[id] = Math.min(0, Math.max(-width, base + dx))
  }
  function onTe(_e: TouchEvent, id: number) {
    touching = false
    const cur = offsets[id] || 0
    offsets[id] = cur < -width / 2 ? -width : 0
  }
  function reset() {
    Object.keys(offsets).forEach((k) => delete offsets[Number(k)])
  }
  return { offsets, onTs, onTm, onTe, reset }
}
