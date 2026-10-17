<template>
  <page-meta :page-style="gdThemeStyle" />
  <view class="page">
    <!-- 顶栏：食材名 + 当前档位副文案 -->
    <ui-back-header :title="detail?.ingredientName" :subtitle="detail ? `当前：${stockLabel(detail.level)}` : undefined" />

    <ui-state v-if="loading" mode="loading" />
    <ui-state v-else-if="!detail" mode="empty" text="食材不存在" />
    <template v-else>
      <scroll-view scroll-y class="body">
        <!-- 食材头：首字块 + 档位徽 -->
        <view class="head">
          <ui-avatar :name="detail.ingredientName" :size="52" :fallback="'食'" />
          <view style="flex: 1" />
          <view class="badge" :style="{ background: stockColor(detail.level) }">
            {{ stockLabel(detail.level) }}
          </view>
        </view>

        <!-- 3 档单选 -->
        <text class="q-line">现在家里是什么情况？</text>
        <view
          v-for="opt in options"
          :key="opt.value"
          class="lcard"
          :class="{ on: selected === opt.value }"
          @click="selected = opt.value"
        >
          <view class="radio" :class="{ on: selected === opt.value }">
            <view v-if="selected === opt.value" class="radio-dot" />
          </view>
          <text class="lcard-title">{{ opt.label }}</text>
          <text class="lcard-desc">{{ opt.desc }}</text>
        </view>

        <!-- 保质期 -->
        <text class="q-line">什么时候前要吃完？</text>
        <picker mode="date" :value="bestBefore || today" class="bb-picker" @change="onBestBefore">
          <view class="bb-field" data-testid="best-before">
            <text v-if="bestBefore" class="bb-val">{{ mdLabel(bestBefore) }} 前吃完</text>
            <text v-else class="bb-ph">不记保质期</text>
            <ui-expiry-badge v-if="bestBefore" :days-left="previewDaysLeft" />
            <text class="bb-arrow">›</text>
          </view>
        </picker>
        <text v-if="suggestedDays && bestBefore === suggestedDate" class="bb-hint">按品类建议 {{ suggestedDays }} 天，已帮你预填</text>

        <!-- 说明条 -->
        <view class="callout">
          选「用完」记一笔用完了，选「不足」记用了一些。昨天用完忘记的，现在补上就行。
        </view>

        <!-- 明细时间线 -->
        <view class="log-head">
          <text class="sec-label">明细</text>
          <text class="log-meta">只展示最近 {{ detail.changes.length }} 条操作</text>
        </view>
        <view class="log-box">
          <view v-if="!detail.changes.length" class="log-none">暂无变动记录</view>
          <view v-for="c in detail.changes" :key="c.id" class="log-row">
            <view class="log-badge" :style="{ background: actionColor(c.action) + '1A', color: actionColor(c.action) }">
              {{ actionLabel(c.action) }}
            </view>
            <text class="log-text">{{ c.note || changeText(c) }}</text>
            <text class="log-time">{{ fmtTime(c.createTime) }}</text>
          </view>
        </view>
        <view style="height: 88px" />
      </scroll-view>

      <view class="bottom">
        <button class="btn-primary wide" :disabled="saving" @click="save">
          {{ saving ? '保存中…' : '保存' }}
        </button>
      </view>
    </template>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { pantryItemDetail, setPantryLevel, shelfLifeDays, type StockLogEntry, type PantryItemDetail } from '@/api/pantry'
import { addDays, mdLabel, ymd } from '@/utils/freshness'
import { stockColor, stockLabel, T } from '@/utils/token'

const loading = ref(true)
const detail = ref<PantryItemDetail | null>(null)
const selected = ref('NONE')
const saving = ref(false)

// 保质期：已有则回显；没有则按品类建议预填（保存时才写入）
const today = ymd(new Date())
const bestBefore = ref('')
const suggestedDays = ref<number | null>(null)
const suggestedDate = ref('')
const previewDaysLeft = computed(() => {
  if (!bestBefore.value) return null
  const [y, m, d] = bestBefore.value.split('-').map(Number)
  const t = new Date()
  const base = new Date(t.getFullYear(), t.getMonth(), t.getDate()).getTime()
  return Math.round((new Date(y, m - 1, d).getTime() - base) / 86400000)
})
function onBestBefore(e: any) {
  bestBefore.value = e?.detail?.value || ''
}

const options = [
  { value: 'ENOUGH', label: '充足', desc: '还有不少' },
  { value: 'LOW', label: '不足', desc: '剩一点点' },
  { value: 'NONE', label: '用完', desc: '用光了' },
]

onLoad(async (options) => {
  const id = Number(options?.id || 0)
  try {
    detail.value = await pantryItemDetail(id)
    selected.value = detail.value.level
    bestBefore.value = detail.value.bestBefore || ''
    if (!bestBefore.value) {
      const d = await shelfLifeDays(id)
      if (d) {
        suggestedDays.value = d
        suggestedDate.value = addDays(d)
        bestBefore.value = suggestedDate.value
      }
    }
  } catch {
    detail.value = null
  }
  loading.value = false
})

function actionLabel(action: string): string {
  switch (action) {
    case 'cook': return '用完了'
    case 'cook_partial': return '用了一些'
    case 'purchase': return '采购'
    case 'undo': return '撤回入库'
    default: return '手动'
  }
}
function actionColor(action: string): string {
  switch (action) {
    case 'cook': return T.error
    case 'cook_partial': return T.warning
    case 'purchase': return T.success
    case 'undo': return T.caption
    default: return T.primary
  }
}
function levelName(l?: string | null): string {
  return l ? stockLabel(l) : '无'
}
function changeText(c: StockLogEntry): string {
  const after = c.afterLevel ? stockLabel(c.afterLevel) : '删除'
  return `${levelName(c.beforeLevel)} → ${after}`
}
function fmtTime(s?: string | null): string {
  if (!s) return ''
  const d = new Date(s.replace(/-/g, '/').replace('T', ' '))
  if (isNaN(d.getTime())) return ''
  const hh = String(d.getHours()).padStart(2, '0')
  const mm = String(d.getMinutes()).padStart(2, '0')
  return `${d.getMonth() + 1}/${d.getDate()} ${hh}:${mm}`
}

async function save() {
  if (!detail.value) return
  saving.value = true
  try {
    await setPantryLevel(detail.value.ingredientId, selected.value, bestBefore.value || undefined)
    uni.showToast({ title: '已保存', icon: 'none' })
    setTimeout(() => uni.navigateBack(), 400)
  } catch (e) {
    uni.showToast({ title: `保存失败：${e}`, icon: 'none' })
  } finally {
    saving.value = false
  }
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
.head {
  display: flex;
  align-items: center;
  padding-top: 12px;
}
.badge {
  padding: 3px 10px;
  border-radius: var(--r-pill);
  color: var(--on-primary);
  font-size: var(--fs-tiny);
  font-weight: 800;
}
.q-line {
  display: block;
  font-size: var(--fs-sm);
  color: var(--body);
  margin: 20px 0 10px;
}
/* 保质期 */
.bb-picker {
  display: block;
}
.bb-field {
  display: flex;
  align-items: center;
  gap: var(--sp-8);
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--r-md);
  padding: var(--sp-10) var(--sp-12);
}
.bb-val {
  flex: 1;
  font-size: var(--fs-md);
  font-weight: 700;
  color: var(--title);
}
.bb-ph {
  flex: 1;
  font-size: var(--fs-md);
  color: var(--caption);
}
.bb-arrow {
  color: var(--caption);
  font-size: var(--fs-lg);
}
.bb-hint {
  display: block;
  margin-top: var(--sp-6);
  font-size: var(--fs-xs);
  color: var(--caption);
}
.lcard {
  display: flex;
  align-items: center;
  gap: 10px;
  border: 1px solid var(--border);
  border-radius: var(--r-md);
  background: var(--bg);
  padding: 12px 14px;
  margin-bottom: 8px;
}
.lcard.on {
  border: 2px solid var(--primary);
}
.radio {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  border: 1.5px solid var(--border);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.radio.on {
  border-color: var(--primary);
}
.radio-dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: var(--primary);
}
.lcard-title {
  font-size: var(--fs-md);
  font-weight: 800;
  color: var(--title);
  margin-right: 8px;
}
.lcard-desc {
  font-size: var(--fs-sm);
  color: var(--caption);
}
.callout {
  background: var(--highlight);
  border: 1px solid var(--border);
  border-radius: var(--r-sm);
  padding: 10px 12px;
  font-size: var(--fs-xs);
  color: var(--body);
  line-height: 1.5;
  margin-top: 4px;
}
.log-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-top: 20px;
}
.sec-label {
  font-size: var(--fs-xs);
  font-weight: 800;
  color: var(--caption);
  letter-spacing: 1px;
  display: block;
}
.log-meta {
  font-size: var(--fs-tiny);
  color: var(--accent);
}
.log-box {
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: var(--r-sm);
  padding: 10px 14px;
  margin-top: 8px;
}
.log-none {
  font-size: var(--fs-xs);
  color: var(--caption);
  padding: 4px 0;
}
.log-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 0;
}
.log-badge {
  flex-shrink: 0;
  padding: 1px 6px;
  border-radius: 4px;
  font-size: var(--fs-tiny);
  font-weight: 800;
}
.log-text {
  flex: 1;
  min-width: 0;
  font-size: var(--fs-xs);
  color: var(--body);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.log-time {
  flex-shrink: 0;
  font-size: var(--fs-tiny);
  color: var(--caption);
}
.bottom {
  padding: 8px 16px calc(12px + env(safe-area-inset-bottom));
  background: var(--card);
  border-top: 1px solid var(--border);
  display: flex;
  justify-content: center;
}
.wide {
  min-width: 200px;
}
</style>
