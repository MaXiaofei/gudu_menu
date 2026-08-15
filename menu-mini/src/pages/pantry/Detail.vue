<template>
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
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { pantryItemDetail, setPantryLevel, type StockLogEntry, type PantryItemDetail } from '@/api/pantry'
import { stockColor, stockLabel, T } from '@/utils/token'

const loading = ref(true)
const detail = ref<PantryItemDetail | null>(null)
const selected = ref('NONE')
const saving = ref(false)

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
    await setPantryLevel(detail.value.ingredientId, selected.value)
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
  color: #FFFFFF;
  font-size: 10px;
  font-weight: 800;
}
.q-line {
  display: block;
  font-size: 12px;
  color: var(--body);
  margin: 20px 0 10px;
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
  font-size: 13px;
  font-weight: 800;
  color: var(--title);
  margin-right: 8px;
}
.lcard-desc {
  font-size: 12px;
  color: var(--caption);
}
.callout {
  background: var(--highlight);
  border: 1px solid var(--border);
  border-radius: var(--r-sm);
  padding: 10px 12px;
  font-size: 11px;
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
  font-size: 11px;
  font-weight: 800;
  color: var(--caption);
  letter-spacing: 1px;
  display: block;
}
.log-meta {
  font-size: 9px;
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
  font-size: 11px;
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
  font-size: 9px;
  font-weight: 800;
}
.log-text {
  flex: 1;
  min-width: 0;
  font-size: 11px;
  color: var(--body);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.log-time {
  flex-shrink: 0;
  font-size: 9px;
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
