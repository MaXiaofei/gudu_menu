import { defineStore } from 'pinia'

/** 写菜谱「预览」页数据（页面间传递，替代 Flutter 路由 extra）。 */
export interface PreviewData {
  name: string
  coverLocal: string
  coverUrl: string
  prepTime: string
  cookTime: string
  difficulty: number
  tags: string[]
  note: string
  ingredients: { name: string; amount: string }[]
  steps: { text: string; image: string }[]
}

export const usePreviewStore = defineStore('preview', {
  state: () => ({ data: null as PreviewData | null }),
  actions: {
    set(d: PreviewData) {
      this.data = d
    },
    clear() {
      this.data = null
    },
  },
})
