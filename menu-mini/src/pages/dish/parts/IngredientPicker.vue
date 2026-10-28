<template>
  <!-- 写菜谱 · 加用料：常用直接点；输入匹配食材库；没有就新建建档。 -->
  <view>
  <view class="mask" @click="$emit('close')">
    <view class="sheet" @click.stop>
      <text class="sheet-title">加用料</text>
      <text class="sheet-sub">常用直接点；其他输入名称匹配，没有就新建</text>
      <input v-model="ingKw" class="sheet-search" placeholder="搜食材库" placeholder-class="ph" :focus="true" />
      <scroll-view scroll-y class="sheet-scroll">
        <view v-if="!ingKw" class="hot-row">
          <view v-for="h in hot" :key="h.id" class="hot-chip" @click="$emit('pick', h.id, h.name)"><ui-ing-icon :name="h.name" :size="22" bare /><text class="hot-name">{{ h.name }}</text><ui-own-tag v-if="h.familyOwned" /></view>
        </view>
        <template v-else>
          <text class="match-label">匹配「{{ ingKw }}」</text>
          <view v-for="i in ingMatches" :key="i.id" class="match-row" @click="$emit('pick', i.id, i.name)">
            <ui-ing-icon :name="i.name" :size="32" />
            <view class="match-main">
              <view class="match-line">
                <text class="match-name">{{ i.name }}</text>
                <ui-own-tag v-if="i.familyOwned" />
              </view>
              <text v-if="aliasHint(i)" class="match-alias">{{ aliasHint(i) }}</text>
            </view>
            <text class="match-pick">选</text>
          </view>
          <view v-if="!ingMatches.length" class="match-none">没有匹配的食材</view>
        </template>
      </scroll-view>
      <view class="sheet-bottom-row">
        <button class="btn-ghost" @click="openCreateIng">＋ 新建食材</button>
      </view>
    </view>
  </view>

  <view v-if="createIngDialog" class="mask" @click="createIngDialog = false">
    <view class="dialog" @click.stop>
      <text class="dialog-title">新建食材</text>
      <input v-model="newIngName" class="dialog-ipt" placeholder="食材名" placeholder-class="ph" />
      <view class="dialog-actions">
        <text class="dialog-btn" @click="createIngDialog = false">取消</text>
        <text class="dialog-btn primary" @click="confirmCreateIng">建档</text>
      </view>
    </view>
  </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { resolveIngredient, searchIngredients, type IngredientItem } from '@/api/ingredient'
import { aliasHint, matchLocal, mergeMatches, resolveTip } from '@/utils/ingredientSearch'

const props = defineProps<{ all: IngredientItem[]; hot: IngredientItem[] }>()
const emit = defineEmits<{ (e: 'close'): void; (e: 'pick', id: number, name: string): void; (e: 'created', it: IngredientItem): void }>()

const ingKw = ref('')
const createIngDialog = ref(false)
const newIngName = ref('')
/** 服务端搜索结果（命中别名，如搜「土豆」出「马铃薯」）。 */
const remote = ref<IngredientItem[]>([])
const ingMatches = computed(() => mergeMatches(matchLocal(props.all, ingKw.value), remote.value))

let timer: ReturnType<typeof setTimeout> | null = null
watch(ingKw, (kw) => {
  if (timer) clearTimeout(timer)
  const q = kw.trim()
  if (!q) {
    remote.value = []
    return
  }
  // 本地没搜到才问服务端（别名只在服务端）；防抖 300ms
  timer = setTimeout(async () => {
    try {
      const r = await searchIngredients(q)
      if (ingKw.value.trim() === q) remote.value = r
    } catch {
      remote.value = []
    }
  }, 300)
})

function openCreateIng() {
  newIngName.value = ingKw.value.trim()
  createIngDialog.value = true
}
async function confirmCreateIng() {
  const n = newIngName.value.trim()
  if (!n) return
  try {
    const r = await resolveIngredient(n)
    createIngDialog.value = false
    // 后端命中已有公共 / 本家食材时不新建：提示「已有：马铃薯」并直接选中它
    emit('created', { id: r.id, name: r.name, familyOwned: r.familyOwned })
    uni.showToast({ title: resolveTip(r), icon: 'none' })
  } catch {
    uni.showToast({ title: '建档失败', icon: 'none' })
  }
}
</script>

<style scoped>
.mask {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  background: var(--mask);
  z-index: 100;
  display: flex;
  align-items: flex-end;
}
.ph {
  color: var(--caption);
}
.hot-chip {
  display: flex;
  align-items: center;
  gap: 4px;
}
.hot-name {
  font-size: var(--fs-sm);
  color: var(--title);
}
.sheet {
  width: 100%;
  height: 70vh;
  background: var(--card);
  border-radius: var(--r-xl) var(--r-xl) 0 0;
  padding: 16px 18px calc(14px + env(safe-area-inset-bottom));
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
}
.sheet-title { font-size: var(--fs-lg); font-weight: 700; color: var(--title); }
.sheet-sub { font-size: var(--fs-xs); color: var(--caption); margin: 2px 0 10px; }
.sheet-search {
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: var(--r-md);
  padding: 9px 12px;
  font-size: var(--fs-md);
  color: var(--title);
}
.sheet-scroll { flex: 1; min-height: 0; margin-top: 10px; }
.hot-row { display: flex; flex-wrap: wrap; gap: 6px; }
.hot-chip {
  padding: 6px 12px;
  border-radius: var(--r-pill);
  background: var(--bg);
  border: 1px solid var(--border);
  font-size: var(--fs-xs);
  color: var(--body);
}
.match-label { display: block; font-size: var(--fs-xs); color: var(--caption); margin-bottom: 6px; }
.match-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 0;
}
.match-main { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px; }
.match-line { display: flex; align-items: center; gap: 6px; }
.match-name { font-size: var(--fs-md); font-weight: 700; color: var(--title); }
.match-alias { font-size: var(--fs-tiny); color: var(--caption); }
.match-pick { font-size: var(--fs-xs); color: var(--primary); font-weight: 700; }
.match-none { font-size: var(--fs-sm); color: var(--caption); padding: 12px 0; }
.sheet-bottom-row { margin-top: 10px; }
.dialog {
  position: fixed;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%);
  width: 78vw;
  background: var(--card);
  border-radius: var(--r-lg);
  padding: 20px 18px 12px;
  z-index: 110;
}
.dialog-title { font-size: var(--fs-lg); font-weight: 700; color: var(--title); }
.dialog-ipt {
  margin-top: 14px;
  border-bottom: 1px solid var(--border);
  padding: 8px 2px;
  font-size: var(--fs-md);
  color: var(--title);
}
.dialog-actions { display: flex; justify-content: flex-end; gap: 18px; margin-top: 10px; }
.dialog-btn { font-size: var(--fs-md); color: var(--caption); padding: 6px 4px; }
.dialog-btn.primary { color: var(--primary); font-weight: 700; }
</style>
