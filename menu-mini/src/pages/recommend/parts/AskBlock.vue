<template>
  <!-- 想吃点别的：自然语言语义找菜 + 组合推荐（进页默认推荐静默加载）。 -->
  <view class="ask">
    <text class="sec-title">想吃点别的</text>
    <text class="desc">说说想吃的口味，帮你找菜、搭好一桌</text>
    <view class="search-box">
      <text class="search-ico">⌕</text>
      <input
        v-model="pref"
        class="search-ipt"
        placeholder="想吃什么？如：清淡下饭、酸甜开胃"
        placeholder-class="ph"
        confirm-type="search"
        :cursor="pref.length"
        @confirm="onSemanticSearch"
        @input="onInput"
      />
      <text v-if="pref" class="search-clear" @click="onClear">✕</text>
    </view>
    <view class="chip-wrap">
      <view v-for="s in quickChips" :key="s" class="chip" @click="onQuickChip(s)">{{ s }}</view>
    </view>

    <ui-skeleton v-if="semanticLoading" :count="2" />
    <template v-if="hits && hits.length">
      <text class="label">找菜结果</text>
      <view v-for="h in hits" :key="h.dishId" class="hit-row" @click="goDish(h.dishId)">
        <text class="hit-name">{{ h.name }}</text>
        <text v-if="h.cookTime != null" class="hit-time">{{ h.cookTime }} 分钟</text>
        <text class="arrow">›</text>
      </view>
    </template>

    <template v-if="groups && groups.length">
      <text class="label">{{ isDefault ? '为你搭一桌' : '推荐组合' }}</text>
      <view v-for="(g, i) in groups" :key="i" class="group-card">
        <view class="group-head">
          <text class="group-no">{{ i + 1 }}</text>
          <text class="group-title">推荐组合</text>
        </view>
        <view class="group-chips">
          <view v-for="d in g.dishes || []" :key="d.dishId" class="dish-chip" @click="goDish(d.dishId)">{{ d.name }}</view>
        </view>
        <view v-for="(r, ri) in g.reasons || []" :key="ri" class="reason">
          <text class="dot">·</text>
          <text class="reason-txt">{{ r }}</text>
        </view>
      </view>
    </template>

    <view class="btns">
      <view class="btn outline" :class="{ disabled: semanticLoading }" @click="onSemanticSearch">相似菜</view>
      <view class="btn fill" :class="{ disabled: loading }" @click="onRecommend">
        <text class="btn-fill-txt">{{ loading ? '搭配中…' : '组合推荐' }}</text>
      </view>
    </view>
    <view v-if="error" class="error-box">{{ error }}</view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { semanticSearch, type SemanticHit } from '@/api/dish'
import { recommendDefault, recommendMenu, type RecommendGroup } from '@/api/ai'
import { useMemberStore } from '@/store/member'

const memberStore = useMemberStore()
const pref = ref('')
const hits = ref<SemanticHit[] | null>(null)
const semanticLoading = ref(false)
const loading = ref(false)
const groups = ref<RecommendGroup[] | null>(null)
const error = ref('')
const isDefault = ref(false)
const quickChips = ['清淡下饭', '酸甜开胃', '快手菜', '来点硬菜', '暖暖的汤']

/** 进页默认出菜（对齐 APP）：失败静默。 */
async function loadDefault() {
  try {
    groups.value = await recommendDefault(memberStore.currentId, 3)
    isDefault.value = true
  } catch {
    // 用户可手动触发
  }
}

function onInput() {
  if (hits.value != null || groups.value != null || error.value) {
    hits.value = null
    groups.value = null
    error.value = ''
  }
}
function onClear() {
  pref.value = ''
  onInput()
}
async function onSemanticSearch() {
  const q = pref.value.trim()
  if (!q) return
  semanticLoading.value = true
  try {
    hits.value = await semanticSearch(q)
  } catch {
    hits.value = []
  } finally {
    semanticLoading.value = false
  }
}
function onQuickChip(s: string) {
  pref.value = s
  onSemanticSearch()
}
async function onRecommend() {
  loading.value = true
  error.value = ''
  groups.value = null
  try {
    const list = await recommendMenu(memberStore.currentId, pref.value)
    groups.value = list
    isDefault.value = false
    if (!list.length) error.value = '暂无推荐，菜库菜品较少时建议先录入更多菜品'
  } catch (e: any) {
    error.value = e?.message ? String(e.message).replace(/^Exception: /, '') : '推荐失败，请稍后重试'
  } finally {
    loading.value = false
  }
}
function goDish(id: number) {
  uni.navigateTo({ url: `/pages/dish/Detail?id=${id}` })
}

defineExpose({ loadDefault })
</script>

<style scoped>
.ask {
  display: flex;
  flex-direction: column;
  gap: var(--sp-8);
}
.sec-title {
  font-size: var(--fs-subtitle);
  font-weight: 700;
  color: var(--title);
}
.desc {
  font-size: var(--fs-sm);
  color: var(--body);
}
.search-box {
  height: 44px;
  display: flex;
  align-items: center;
  gap: 6px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--r-md);
  padding: 0 12px;
}
.search-ico {
  color: var(--body);
  font-size: var(--fs-lg);
}
.search-ipt {
  flex: 1;
  font-size: var(--fs-md);
  color: var(--title);
}
.ph {
  color: var(--caption);
}
.search-clear {
  color: var(--body);
  font-size: var(--fs-md);
  padding: 4px;
}
.chip-wrap {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-8);
}
.chip {
  padding: 6px 12px;
  border-radius: var(--r-pill);
  background: var(--secondary);
  border: 1px solid var(--primary-soft);
  color: var(--accent);
  font-size: var(--fs-sm);
  font-weight: 700;
}
.label {
  margin-top: var(--sp-8);
  font-size: var(--fs-xs);
  font-weight: 700;
  color: var(--body);
  letter-spacing: 1px;
}
.hit-row {
  display: flex;
  align-items: center;
  gap: 8px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--r-md);
  padding: 12px;
}
.hit-name {
  flex: 1;
  min-width: 0;
  font-size: var(--fs-md);
  font-weight: 700;
  color: var(--title);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.hit-time {
  font-size: var(--fs-sm);
  color: var(--body);
}
.arrow {
  color: var(--body);
  font-size: var(--fs-lg);
  font-weight: 700;
}
.group-card {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--r-lg);
  padding: 12px;
}
.group-head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.group-no {
  width: 22px;
  height: 22px;
  line-height: 22px;
  text-align: center;
  border-radius: var(--r-pill);
  background: var(--primary-soft);
  font-size: var(--fs-sm);
  font-weight: 800;
  color: var(--title);
}
.group-title {
  font-size: var(--fs-md);
  font-weight: 700;
  color: var(--title);
}
.group-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 6px;
  margin-top: 10px;
}
.dish-chip {
  padding: 4px 12px;
  border-radius: var(--r-pill);
  background: var(--highlight);
  border: 1px solid var(--primary-soft);
  color: var(--primary-deep);
  font-size: var(--fs-sm);
  font-weight: 700;
}
.reason {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-top: 4px;
}
.dot,
.reason-txt {
  font-size: var(--fs-sm);
  color: var(--body);
  line-height: 1.5;
}
.reason-txt {
  flex: 1;
}
.btns {
  display: flex;
  gap: 12px;
  margin-top: var(--sp-4);
}
.btn {
  height: 44px;
  border-radius: var(--r-md);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--fs-md);
  font-weight: 700;
}
.btn.outline {
  flex: 1;
  background: var(--card);
  border: 1px solid var(--border);
  color: var(--title);
}
.btn.fill {
  flex: 2;
  background: var(--primary-deep);
}
.btn-fill-txt {
  color: var(--on-primary);
  font-size: var(--fs-md);
  font-weight: 700;
}
.btn.disabled {
  opacity: 0.6;
}
.error-box {
  padding: 12px;
  background: var(--error-soft);
  border-radius: var(--r-md);
  font-size: var(--fs-sm);
  color: var(--error-deep);
  line-height: 1.5;
}
</style>
