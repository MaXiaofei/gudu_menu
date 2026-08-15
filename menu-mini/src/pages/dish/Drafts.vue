<template>
  <!-- 草稿箱（左滑删除无确认，滑走即删） -->
  <view class="page">
    <ui-back-header />

    <scroll-view scroll-y class="body" @scrolltolower="loadMore">
      <view v-for="d in drafts" :key="d.id" class="swipe-wrap">
        <view class="swipe-del" @click.stop="delDraft(d)">
          <text class="swipe-del-txt">删除</text>
        </view>
        <view
          class="row"
          :style="{ transform: `translateX(${offsets[d.id] || 0}px)` }"
          @touchstart="onTs($event, d.id)"
          @touchmove="onTm($event, d.id)"
          @touchend="onTe($event, d.id)"
          @click="goEdit(d)"
        >
          <ui-avatar :name="d.name || '未'" :size="42" :fallback="'未'" />
          <view class="main">
            <text class="name">{{ d.name || '未命名草稿' }}</text>
            <text class="sub">用料 {{ d.ingredientCount ?? 0 }} · 步骤 {{ d.stepCount ?? 0 }} · {{ smartTime(d.updateTime) }}</text>
          </view>
          <text class="go">继续 ›</text>
        </view>
      </view>
      <ui-state v-if="!loading && !drafts.length" mode="empty" text="还没有草稿" hint="写菜谱没填完，点「存草稿」就会出现在这里" />
      <view v-if="drafts.length && !hasMore" class="footer">没有更多了</view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { listDrafts, deleteDraft, type DishDraftItem } from '@/api/dish'
import { smartTime } from '@/utils/datetime'

const drafts = ref<DishDraftItem[]>([])
const page = ref(1)
const hasMore = ref(true)
const loading = ref(true)

const offsets = reactive<Record<number, number>>({})
let touchStartX = 0
let touchStartY = 0
let touching = false

onShow(() => reload())

async function reload() {
  page.value = 1
  hasMore.value = true
  loading.value = true
  try {
    const r = await listDrafts(1)
    drafts.value = r.records
    hasMore.value = r.records.length >= 10
  } catch {
    drafts.value = []
  }
  loading.value = false
}

async function loadMore() {
  if (loading.value || !hasMore.value) return
  try {
    const r = await listDrafts(page.value + 1)
    page.value += 1
    drafts.value.push(...r.records)
    hasMore.value = r.records.length >= 10
  } catch {}
}

function goEdit(d: DishDraftItem) {
  if ((offsets[d.id] || 0) !== 0) {
    offsets[d.id] = 0
    return
  }
  uni.navigateTo({ url: `/pages/dish/Create?draftId=${d.id}` })
}

async function delDraft(d: DishDraftItem) {
  drafts.value = drafts.value.filter((x) => x.id !== d.id)
  try {
    await deleteDraft(d.id)
  } catch {
    // 失败也照删本地（对齐 APP）
  }
}

function onTs(e: TouchEvent, id: number) {
  touchStartX = e.touches[0].clientX
  touchStartY = e.touches[0].clientY
  touching = true
}
function onTm(e: TouchEvent, id: number) {
  if (!touching) return
  const dx = e.touches[0].clientX - touchStartX
  const dy = Math.abs(e.touches[0].clientY - touchStartY)
  if (dy > 12) return
  const base = offsets[id] || 0
  offsets[id] = Math.min(0, Math.max(-72, base + dx))
}
function onTe(_e: TouchEvent, id: number) {
  touching = false
  const cur = offsets[id] || 0
  offsets[id] = cur < -36 ? -72 : 0
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
  padding: 0 14px;
  box-sizing: border-box;
}
.swipe-wrap {
  position: relative;
  margin-bottom: 7px;
  border-radius: var(--r-md);
  overflow: hidden;
}
.swipe-del {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  width: 72px;
  background: #E53935;
  display: flex;
  align-items: center;
  justify-content: center;
}
.swipe-del-txt { color: #FFFFFF; font-size: 13px; font-weight: 700; }
.row {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--r-md);
  padding: 10px 12px;
  transition: transform 0.15s ease-out;
}
.main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.name {
  font-size: 14px;
  font-weight: 700;
  color: var(--title);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.sub { font-size: 10px; color: var(--caption); }
.go { font-size: 12px; color: var(--primary); font-weight: 700; }
.footer {
  padding: 16px 0;
  text-align: center;
  font-size: 12px;
  color: var(--caption);
}
</style>
