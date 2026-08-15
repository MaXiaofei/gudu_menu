<template>
  <!-- 食材库列表：搜索（回车）+ 分类筛选 + 分页 -->
  <view class="page">
    <ui-back-header>
      <template #action>
        <view class="add-btn" @click="goCreate">＋ 添加</view>
      </template>
    </ui-back-header>

    <!-- 搜索框（回车触发） -->
    <view class="search-row">
      <view class="search-box">
        <text class="search-ico">⌕</text>
        <input v-model="kwInput" class="search-ipt" placeholder="搜食材名" placeholder-class="ph" confirm-type="search" @confirm="onSearch" />
        <text v-if="kwInput" class="search-clear" @click="onClear">✕</text>
      </view>
    </view>

    <!-- 分类 chips -->
    <scroll-view v-if="categories.length" scroll-x class="chip-bar" :show-scrollbar="false">
      <view class="chip-row">
        <view
          v-for="c in [{ id: 0, name: `全部 ${total}` }, ...categories]"
          :key="c.id"
          class="chip"
          :class="{ on: categoryId === c.id }"
          @click="onCategory(c.id)"
        >{{ c.name }}</view>
      </view>
    </scroll-view>

    <scroll-view scroll-y class="body" @scrolltolower="loadMore">
      <view v-for="i in list" :key="i.id" class="row" @click="goEdit(i)">
        <view class="row-main">
          <view class="row-name-line">
            <text class="row-name">{{ i.name }}</text>
            <text v-if="i.categoryName" class="row-cat">{{ i.categoryName }}</text>
          </view>
          <text class="row-sub">{{ edibleText(i.edible) }}</text>
        </view>
        <text class="arrow">›</text>
      </view>
      <ui-state v-if="!firstLoading && !list.length" mode="empty" text="暂无食材" />
      <view v-if="list.length && !hasMore" class="footer">没有更多了</view>
      <view style="height: 40px" />
    </scroll-view>

    <view class="foot-note">食材从「我的」进入；点击食材可编辑食用属性</view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { listIngredients, type IngredientRow } from '@/api/ingredient'
import { listDict, type DictItem } from '@/api/common'

const kwInput = ref('')
const keyword = ref('')
const categories = ref<DictItem[]>([])
const categoryId = ref(0)

const list = ref<IngredientRow[]>([])
const total = ref(0)
const page = ref(1)
const hasMore = ref(true)
const firstLoading = ref(true)

onShow(() => {
  listDict('purchase_category').then((r) => (categories.value = r)).catch(() => {})
  reload()
})

async function fetch(p: number): Promise<IngredientRow[]> {
  try {
    const r = await listIngredients({
      keyword: keyword.value || undefined,
      purchaseCategoryId: categoryId.value || undefined,
      pageNum: p,
    })
    total.value = r.total
    hasMore.value = r.records.length >= 10
    return r.records
  } catch {
    hasMore.value = false
    return []
  }
}

async function reload() {
  page.value = 1
  hasMore.value = true
  firstLoading.value = true
  list.value = await fetch(1)
  firstLoading.value = false
}

async function loadMore() {
  if (firstLoading.value || !hasMore.value) return
  const rows = await fetch(page.value + 1)
  page.value += 1
  list.value.push(...rows)
}

function onSearch() {
  keyword.value = kwInput.value.trim()
  reload()
}
function onClear() {
  kwInput.value = ''
  keyword.value = ''
  reload()
}
function onCategory(id: number) {
  if (categoryId.value === id) return
  categoryId.value = id
  reload()
}

function edibleText(edible?: number | null): string {
  switch (edible) {
    case 2: return '非营养'
    case 3: return '非食用'
    default: return '点击编辑'
  }
}

function goCreate() {
  uni.navigateTo({ url: '/pages/ingredient/Create' })
}
function goEdit(i: IngredientRow) {
  uni.navigateTo({ url: `/pages/ingredient/Edit?id=${i.id}` })
}
</script>

<style scoped>
.page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--bg);
}
.add-btn {
  padding: 5px 12px;
  border-radius: var(--r-pill);
  background: var(--primary);
  color: #FFFFFF;
  font-size: 10px;
  font-weight: 700;
}
.search-row { padding: 4px 14px 8px; }
.search-box {
  display: flex;
  align-items: center;
  gap: 6px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--r-md);
  padding: 0 12px;
}
.search-ico { color: var(--caption); font-size: 14px; }
.search-ipt { flex: 1; font-size: 12px; color: var(--title); padding: 9px 0; }
.search-clear { color: var(--caption); font-size: 13px; padding: 4px; }
.ph { color: var(--caption); }
.chip-bar { white-space: nowrap; }
.chip-row { display: inline-flex; gap: 6px; padding: 0 14px 8px; }
.chip {
  flex-shrink: 0;
  padding: 5px 12px;
  border-radius: var(--r-sm);
  background: var(--card);
  border: 1px solid var(--border);
  color: var(--body);
  font-size: 11px;
}
.chip.on { background: var(--title); border-color: var(--title); color: #FFFFFF; }
.body { flex: 1; min-height: 0; padding: 0 14px; box-sizing: border-box; }
.row {
  display: flex;
  align-items: center;
  gap: 10px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--r-md);
  padding: 10px 12px;
  margin-bottom: 6px;
}
.row-main { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px; }
.row-name-line { display: flex; align-items: center; gap: 6px; }
.row-name { font-size: 13px; font-weight: 700; color: var(--title); }
.row-cat {
  padding: 1px 6px;
  border-radius: var(--r-sm);
  background: var(--bg);
  color: var(--caption);
  font-size: 9px;
}
.row-sub { font-size: 10px; color: var(--caption); }
.arrow { color: var(--caption); font-size: 16px; font-weight: 700; }
.footer { padding: 16px 0; text-align: center; font-size: 12px; color: var(--caption); }
.foot-note {
  padding: 8px 0 calc(10px + env(safe-area-inset-bottom));
  text-align: center;
  font-size: 10px;
  color: var(--caption);
}
</style>
