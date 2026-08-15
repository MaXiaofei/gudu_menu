<template>
  <!-- 编辑食材：食用属性三选 + 删除 -->
  <view class="page">
    <ui-back-header :title="ing?.name" :subtitle="ing?.categoryName || '未分类'">
      <template #action>
        <text class="del" @click="confirmDelete">✕</text>
      </template>
    </ui-back-header>

    <ui-state v-if="loading" mode="loading" />
    <template v-else>
      <scroll-view scroll-y class="body">
        <text class="q-line">食用属性</text>
        <view class="card" @click="pickEdible">
          <text class="card-label">{{ edibleText(edible) }}</text>
          <text class="card-value">{{ edibleDesc(edible) }}</text>
          <text class="arrow">›</text>
        </view>
        <view style="height: 96px" />
      </scroll-view>

      <view class="bottom">
        <button class="btn-primary" :disabled="saving" @click="save">{{ saving ? '保存中…' : '保存' }}</button>
      </view>
    </template>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { ingredientDetail, updateIngredient, deleteIngredient, type IngredientRow } from '@/api/ingredient'
import { relativeDate } from '@/utils/datetime'

const loading = ref(true)
const ing = ref<IngredientRow | null>(null)
const edible = ref(1)
const saving = ref(false)

onLoad(async (options) => {
  const id = Number(options?.id || 0)
  try {
    ing.value = await ingredientDetail(id)
    edible.value = ing.value.edible ?? 1
  } catch {}
  loading.value = false
})

function edibleText(e: number): string {
  switch (e) {
    case 2: return '饮料零食'
    case 3: return '生活用品'
    default: return '食用'
  }
}
function edibleDesc(e: number): string {
  switch (e) {
    case 2: return '不参与营养统计'
    case 3: return '非食用，仅备料清单用'
    default: return '正常食材，参与营养统计'
  }
}

function pickEdible() {
  uni.showActionSheet({
    itemList: ['食用', '饮料零食', '生活用品'],
    success: ({ tapIndex }) => (edible.value = tapIndex + 1),
  })
}

async function save() {
  if (!ing.value) return
  saving.value = true
  try {
    await updateIngredient(ing.value.id, edible.value)
    uni.showToast({ title: '已保存', icon: 'none' })
    setTimeout(() => uni.navigateBack(), 400)
  } catch {
    uni.showToast({ title: '保存失败', icon: 'none' })
  } finally {
    saving.value = false
  }
}

function confirmDelete() {
  if (!ing.value) return
  uni.showModal({
    title: '删除食材',
    content: `确定删除「${ing.value.name}」？关联的用量记录会保留。`,
    confirmText: '删除',
    confirmColor: '#DB5A4E',
    success: async ({ confirm }) => {
      if (!confirm) return
      try {
        await deleteIngredient(ing.value!.id)
        uni.showToast({ title: '已删除', icon: 'none' })
        setTimeout(() => uni.navigateBack(), 400)
      } catch {
        uni.showToast({ title: '删除失败', icon: 'none' })
      }
    },
  })
}
</script>

<style scoped>
.page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--bg);
}
.body {
  flex: 1;
  min-height: 0;
  padding: 0 16px;
  box-sizing: border-box;
}
.del {
  color: var(--caption);
  font-size: 15px;
  padding: 4px 6px;
}
.q-line {
  display: block;
  font-size: 12px;
  color: var(--body);
  margin: 12px 0 8px;
}
.card {
  display: flex;
  align-items: center;
  gap: 10px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--r-md);
  padding: 13px 14px;
}
.card-label { font-size: 14px; font-weight: 800; color: var(--title); }
.card-value { flex: 1; font-size: 11px; color: var(--caption); }
.arrow { color: var(--caption); font-size: 16px; font-weight: 700; }
.bottom {
  padding: 8px 16px calc(12px + env(safe-area-inset-bottom));
  background: var(--card);
  border-top: 1px solid var(--border);
}
</style>
