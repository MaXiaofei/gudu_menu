<template>
  <page-meta :page-style="gdThemeStyle" />
  <!-- 识别结果确认（prototype ScanResult；vision-intake spec）：≥0.8 默认勾选、0.5–0.8 标黄「请确认」在前、<0.5 不展示；
       名称可改、可选备选名、数量步进、保质期自动；点「放进余量」前不写库。菜谱截图模式展示识别出的菜谱。 -->
  <view class="page">
    <ui-back-header title="确认识别结果">
      <template #action>
        <view class="again" @click="again"><ui-icon name="camera" :size="14" /><text class="again-txt">再拍一层</text></view>
      </template>
    </ui-back-header>

    <ui-state v-if="loading" mode="loading" />
    <ui-state v-else-if="!job" mode="error" text="识别结果找不到了" :retry="true" @retry="load" />
    <scroll-view v-else scroll-y class="body">
      <view class="pad">
        <view class="summary">
          <ui-dish-image :src="img" name="照" :width="72" :height="72" :radius="12" />
          <view class="sum-main">
            <text class="sum-kicker">{{ MODE_LABEL[job.mode] }} · 识别完成</text>
            <text class="sum-title">识别到 {{ rows.length }} 样</text>
            <text v-if="confirmRows.length" class="sum-warn">{{ confirmRows.length }} 样待确认</text>
          </view>
        </view>
        <view class="privacy"><ui-icon name="shield" :size="14" /><text class="privacy-txt">原图 24 小时后自动删除，只保留识别结果</text></view>

        <!-- 菜谱截图 -->
        <view v-if="job.mode === 'RECIPE' && job.recipe" class="recipe">
          <text class="r-title">{{ job.recipe.title }}</text>
          <text class="lab">用料 {{ job.recipe.ingredients.length }}</text>
          <view class="r-ings">
            <view v-for="ing in job.recipe.ingredients" :key="ing.name" class="r-ing">
              <ui-ing-icon :icon-key="ing.iconKey" :name="ing.name" :size="28" />
              <text class="r-ing-txt">{{ ing.name }} {{ ing.amount }}{{ ing.unit || '' }}</text>
            </view>
          </view>
          <text class="lab">步骤 {{ job.recipe.steps.length }}</text>
          <text v-for="(s, i) in job.recipe.steps" :key="i" class="r-step">{{ i + 1 }}. {{ s }}</text>
        </view>

        <template v-else>
          <view v-if="confirmRows.length" class="group">
            <view class="g-head"><text class="g-title">请确认 · {{ confirmRows.length }}</text><text class="g-hint">看不准的不会自动放进去</text></view>
            <scan-row v-for="r in confirmRows" :key="r.rawName + r.name" :row="r" />
          </view>
          <view class="group">
            <view class="g-head">
              <text class="g-title">已识别 · {{ autoRows.length }}</text>
              <text class="g-link" @click="toggleAll">{{ allAutoOn ? '全不选' : '全选' }}</text>
            </view>
            <scan-row v-for="r in autoRows" :key="r.rawName + r.name" :row="r" />
          </view>
        </template>
        <view style="height: 96px" />
      </view>
    </scroll-view>

    <view v-if="job" class="bar">
      <text class="bar-sub">{{ job.mode === 'RECIPE' ? '会先存为草稿，可以再改' : `已选 ${checked.length} 样 · 放进后可撤销` }}</text>
      <view class="btn" :class="{ off: applying || (job.mode !== 'RECIPE' && !checked.length) }" data-testid="scan-apply" @click="apply">
        <ui-icon name="check" tone="white" :size="18" />
        <text class="btn-txt">{{ job.mode === 'RECIPE' ? '存为我的菜谱草稿' : `放进余量 · ${checked.length} 样` }}</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { applyJob, getJob, toReviewRows, MODE_LABEL, type ReviewRow, type VisionJob } from '@/api/vision'
import { saveDraft } from '@/api/dish'
import { PAGES } from '@/utils/nav'
import ScanRow from './parts/ScanRow.vue'

const job = ref<VisionJob | null>(null)
const rows = ref<ReviewRow[]>([])
const loading = ref(true)
const applying = ref(false)
const jobId = ref(0)
const img = ref('')

onLoad((q: any) => {
  jobId.value = Number(q?.jobId) || 0
  img.value = q?.img ? decodeURIComponent(q.img) : ''
  load()
})

async function load() {
  loading.value = true
  try {
    job.value = await getJob(jobId.value)
    rows.value = toReviewRows(job.value.items || [])
  } catch {
    job.value = null
  } finally {
    loading.value = false
  }
}

const confirmRows = computed(() => rows.value.filter((r) => r.tier === 'confirm'))
const autoRows = computed(() => rows.value.filter((r) => r.tier === 'auto'))
const checked = computed(() => rows.value.filter((r) => r.checked))
const allAutoOn = computed(() => autoRows.value.every((r) => r.checked))
function toggleAll() {
  const v = !allAutoOn.value
  autoRows.value.forEach((r) => (r.checked = v))
}

async function apply() {
  if (applying.value || !job.value) return
  applying.value = true
  try {
    if (job.value.mode === 'RECIPE' && job.value.recipe) {
      await saveDraft({
        dish: { name: job.value.recipe.title },
        steps: job.value.recipe.steps.map((text, i) => ({ sortOrder: i + 1, text })),
        ingredients: [],
        tagIds: [],
        cuisineIds: [],
      } as any)
      uni.showToast({ title: '已存到草稿箱', icon: 'success' })
      setTimeout(() => uni.redirectTo({ url: '/pages/dish/Drafts' }), 500)
      return
    }
    if (!checked.value.length) return
    const r = await applyJob(
      jobId.value,
      checked.value.map((x) => ({ name: x.name, ingredientId: x.ingredientId, amount: x.amount, unit: x.unit, bestBefore: x.suggestedBestBefore })),
    )
    uni.showToast({ title: `放进余量 ${r.added} 样`, icon: 'success' })
    setTimeout(() => uni.switchTab({ url: '/pages/pantry/List' }), 600)
  } finally {
    applying.value = false
  }
}

function again() {
  uni.redirectTo({ url: PAGES.scan })
}
</script>

<style scoped>
.page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--bg);
}
.again {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  border-radius: var(--radius-pill);
  border: 1px solid var(--border);
  background: var(--card);
}
.again-txt {
  font-size: var(--fs-sm);
  color: var(--title);
}
.body {
  flex: 1;
  min-height: 0;
}
.pad {
  padding: 0 var(--space-lg);
}
.summary {
  display: flex;
  align-items: center;
  gap: var(--space-md);
  padding: 12px;
  border-radius: var(--radius-lg);
  background: var(--card);
  border: 1px solid var(--border);
}
.sum-main {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.sum-kicker {
  font-size: var(--fs-xs);
  color: var(--body);
}
.sum-title {
  font-size: var(--fs-h3);
  font-weight: 800;
  color: var(--title);
}
.sum-warn {
  font-size: var(--fs-sm);
  font-weight: 700;
  color: var(--warning-deep);
}
.privacy {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: var(--space-sm) 0 var(--space-lg);
}
.privacy-txt {
  font-size: var(--fs-xs);
  color: var(--body);
}
.group {
  margin-bottom: var(--space-lg);
}
.g-head {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-bottom: var(--space-sm);
}
.g-title {
  font-size: var(--fs-sm);
  font-weight: 700;
  color: var(--body);
  letter-spacing: 1px;
}
.g-hint {
  font-size: var(--fs-xs);
  color: var(--body);
}
.g-link {
  font-size: var(--fs-sm);
  font-weight: 700;
  color: var(--accent);
}
.recipe {
  padding: 14px;
  border-radius: var(--radius-lg);
  background: var(--card);
  border: 1px solid var(--border);
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}
.r-title {
  font-size: var(--fs-subtitle);
  font-weight: 800;
  color: var(--title);
}
.lab {
  margin-top: var(--space-sm);
  font-size: var(--fs-xs);
  font-weight: 700;
  color: var(--body);
}
.r-ings {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-sm);
}
.r-ing {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px 4px 4px;
  border-radius: var(--radius-pill);
  background: var(--secondary);
}
.r-ing-txt {
  font-size: var(--fs-sm);
  color: var(--title);
}
.r-step {
  font-size: var(--fs-md);
  line-height: 1.6;
  color: var(--title);
}
.bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 10px var(--space-lg) calc(12px + env(safe-area-inset-bottom));
  background: rgba(255, 255, 255, 0.97);
  border-top: 1px solid var(--border);
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.bar-sub {
  font-size: var(--fs-xs);
  color: var(--body);
  text-align: center;
}
.btn {
  height: 50px;
  border-radius: var(--radius-md);
  background: var(--primary-deep);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  box-shadow: 0 6px 16px rgba(var(--sh), 0.3);
}
.btn.off {
  opacity: 0.5;
}
.btn-txt {
  font-size: var(--fs-lg);
  font-weight: 700;
  color: var(--on-primary);
}
</style>
