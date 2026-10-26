import { defineStore } from 'pinia'
import { myFamilies, switchFamily, createFamily, joinFamily, type SessionFamily } from '@/api/family'
import {
  getFamilies,
  getCurrentFamilyId,
  saveFamilyContext,
  type FamilySummary,
} from '@/utils/family'

/**
 * 家庭上下文 store：状态与 storage（utils/family）双写，页面用响应式，request.ts 用 storage。
 * 切换 / 创建 / 加入都会改服务端会话的当前家庭，成功后广播 `family-changed`，各页按需刷新。
 */
export const useFamilyStore = defineStore('family', {
  state: () => ({
    families: getFamilies() as FamilySummary[],
    currentId: getCurrentFamilyId() as number | null,
  }),
  getters: {
    current(state): FamilySummary | null {
      return state.families.find((f) => f.id === state.currentId) || null
    },
    currentRole(): string {
      return this.current?.role || 'MEMBER'
    },
  },
  actions: {
    /** 从后端同步（服务端会话可能已自动落到别的家庭，以服务端为准）。 */
    async refresh() {
      const r = await myFamilies()
      this.families = r.families || []
      this.currentId = r.currentFamilyId ?? null
      saveFamilyContext({ families: this.families, currentFamilyId: this.currentId })
    },
    async switchTo(familyId: number) {
      const sf = await switchFamily(familyId)
      await this.afterSession(sf)
    },
    async create(name?: string) {
      const sf = await createFamily(name)
      await this.afterSession(sf)
      return sf
    },
    async join(code: string) {
      const sf = await joinFamily(code)
      await this.afterSession(sf)
      return sf
    },
    /** 会话家庭已变：记当前家庭 + 档案，再拉一次列表（成员数 / 新家庭）。 */
    async afterSession(sf: SessionFamily) {
      this.currentId = sf.familyId
      saveFamilyContext({ currentFamilyId: sf.familyId, currentMemberId: sf.profileId })
      try {
        await this.refresh()
      } catch {
        // 列表刷新失败不影响切换结果
      }
      uni.$emit?.('family-changed', sf.familyId)
    },
    /** 本地状态与 storage 对齐（登录 / 合并后由 utils 写入 storage）。 */
    syncFromStorage() {
      this.families = getFamilies()
      this.currentId = getCurrentFamilyId()
    },
  },
})
