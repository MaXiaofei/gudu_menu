<template>
  <!-- 朋友点菜落地页：分享卡片/小程序码进入，免登录（token 凭证） -->
  <view class="page">
    <view class="top" :style="{ paddingTop: sb + 'px' }">
      <text class="title">一起点菜</text>
    </view>

    <ui-state v-if="loading" mode="loading" />
    <view v-else-if="error" class="page-body">
      <ui-state mode="empty" :text="error" hint="请让发起人重新分享邀请" />
    </view>
    <scroll-view v-else scroll-y class="page-body">
      <!-- 我的身份 -->
      <view class="me-row">
        <text class="me-label">我是</text>
        <input v-model="nickname" class="me-ipt" placeholder="输入昵称" placeholder-class="ph" />
      </view>

      <!-- 加菜 -->
      <text class="sec-label">加一道菜</text>
      <view class="add-row">
        <input v-model="kw" class="add-ipt" placeholder="搜菜名，或直接输入自定义菜名" placeholder-class="ph" @confirm="onSearch" />
        <view class="add-btn" @click="quickAddCustom"><text class="add-btn-txt">加</text></view>
      </view>
      <view v-if="results.length" class="results">
        <view v-for="d in results" :key="d.id" class="result-row" @click="addDish(d)">
          <text class="result-name">{{ d.name }}</text>
          <text class="result-add">＋ 加这道</text>
        </view>
      </view>

      <!-- 当前清单 -->
      <text class="sec-label">大家点的（{{ together.dishes.length }} 道）</text>
      <view class="list-card">
        <view v-for="d in together.dishes" :key="d.id" class="dish-row">
          <view class="dish-main">
            <text class="dish-name">{{ d.dishName || '自定义菜' }}</text>
            <text class="dish-by">{{ d.addedByNickname || '朋友' }} 点的</text>
          </view>
          <text class="dish-del" @click="removeDish(d)">✕</text>
        </view>
        <view v-if="!together.dishes.length" class="list-none">还没有人点菜，来第一道</view>
      </view>

      <!-- 成员 -->
      <text class="sec-label">一起吃 · {{ together.members.length }} 人</text>
      <view class="member-chips">
        <view v-for="(m, i) in together.members" :key="i" class="member-chip">
          <view class="member-avatar"><text class="member-avatar-txt">{{ (m.nickname || '友')[0] }}</text></view>
          <text class="member-name">{{ m.nickname || '朋友' }}</text>
        </view>
      </view>
      <view style="height: 24px" />
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onLoad, onShow, onHide } from '@dcloudio/uni-app'
import { getTogether, addTogetherItem, removeTogetherItem, type TogetherVO, type TogetherDish } from '@/api/together'
import { searchDishes, type Dish } from '@/api/dish'
import { statusBarHeight } from '@/utils/token'

const sb = statusBarHeight()

const menuId = ref(0)
const guestKey = ref('')
const nickname = ref('')
const kw = ref('')
const results = ref<Dish[]>([])
const together = ref<TogetherVO>({ members: [], dishes: [], activities: [] })
const loading = ref(true)
const error = ref('')
let timer: ReturnType<typeof setInterval> | null = null

onLoad((options) => {
  menuId.value = Number(options?.menuId || 0)
  guestKey.value = options?.token || ''
  nickname.value = uni.getStorageSync('guest_nickname') || ''
  if (!menuId.value || !guestKey.value) {
    loading.value = false
    error.value = '缺少邀请凭证'
    return
  }
  load()
})

onShow(() => {
  if (menuId.value && guestKey.value && !error.value) startPolling()
})
onHide(stopPolling)

async function load() {
  try {
    together.value = await getTogether(menuId.value, guestKey.value)
    error.value = ''
  } catch (e: any) {
    error.value = e?.message || '凭证失效'
    stopPolling()
  }
  loading.value = false
}

function startPolling() {
  if (timer) return
  timer = setInterval(() => load(), 10000)
}
function stopPolling() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

/** 加菜前确保有昵称（记进本地，动态里显示谁点的）。 */
function ensureNickname(): boolean {
  const n = nickname.value.trim()
  if (!n) {
    uni.showToast({ title: '先输入昵称', icon: 'none' })
    return false
  }
  uni.setStorageSync('guest_nickname', n)
  return true
}

async function onSearch() {
  const q = kw.value.trim()
  if (!q) {
    results.value = []
    return
  }
  try {
    const r = await searchDishes({ keyword: q, pageNum: 1 })
    results.value = r.records.slice(0, 8)
  } catch {}
}

async function addDish(d: Dish) {
  if (!ensureNickname()) return
  try {
    await addTogetherItem(menuId.value, { dishId: d.id, note: nickname.value.trim() }, guestKey.value)
    uni.showToast({ title: `已加「${d.name}」`, icon: 'none' })
    kw.value = ''
    results.value = []
    load()
  } catch (e: any) {
    uni.showToast({ title: e?.message || '加菜失败', icon: 'none' })
  }
}

async function quickAddCustom() {
  const q = kw.value.trim()
  if (!q) return
  if (!ensureNickname()) return
  // 先搜：命中唯一结果直接加菜库里的；否则按自定义名加
  try {
    const r = await searchDishes({ keyword: q, pageNum: 1 })
    if (r.records.length === 1 && r.records[0].name === q) {
      await addDish(r.records[0])
      return
    }
  } catch {}
  try {
    await addTogetherItem(menuId.value, { customName: q, note: nickname.value.trim() }, guestKey.value)
    uni.showToast({ title: `已加「${q}」`, icon: 'none' })
    kw.value = ''
    results.value = []
    load()
  } catch (e: any) {
    uni.showToast({ title: e?.message || '加菜失败', icon: 'none' })
  }
}

async function removeDish(d: TogetherDish) {
  uni.showModal({
    title: '删掉这道菜？',
    content: `「${d.dishName || '自定义菜'}」会从清单移除，大家都能看到是你删的。`,
    confirmText: '删掉',
    confirmColor: '#DB5A4E',
    success: async ({ confirm }) => {
      if (!confirm) return
      try {
        await removeTogetherItem(menuId.value, d.id, guestKey.value)
        load()
      } catch (e: any) {
        uni.showToast({ title: e?.message || '删除失败', icon: 'none' })
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
.top {
  padding: 0 16px;
}
.title {
  font-size: 17px;
  font-weight: 800;
  color: var(--title);
}
.page-body {
  flex: 1;
  min-height: 0;
  padding: 0 16px;
  box-sizing: border-box;
}
.me-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 10px 0 4px;
}
.me-label { font-size: 12px; color: var(--body); }
.me-ipt {
  flex: 1;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--r-sm);
  padding: 8px 12px;
  font-size: 13px;
  color: var(--title);
}
.sec-label {
  display: block;
  font-size: 11px;
  font-weight: 800;
  color: var(--caption);
  letter-spacing: 1px;
  margin: 16px 0 8px;
}
.add-row { display: flex; gap: 8px; }
.add-ipt {
  flex: 1;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--r-md);
  padding: 10px 12px;
  font-size: 13px;
  color: var(--title);
}
.add-btn {
  width: 44px;
  background: var(--primary);
  border-radius: var(--r-md);
  display: flex;
  align-items: center;
  justify-content: center;
}
.add-btn-txt { color: #FFFFFF; font-size: 15px; font-weight: 800; }
.results { margin-top: 8px; }
.result-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--r-sm);
  padding: 9px 12px;
  margin-bottom: 4px;
}
.result-name { font-size: 13px; font-weight: 700; color: var(--title); }
.result-add { font-size: 11px; color: var(--primary); font-weight: 700; }
.list-card {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--r-md);
  padding: 4px 14px;
}
.dish-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 0;
}
.dish-row + .dish-row { border-top: 1px solid var(--bg); }
.dish-main { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px; }
.dish-name { font-size: 13px; font-weight: 700; color: var(--title); }
.dish-by { font-size: 10px; color: var(--caption); }
.dish-del { color: var(--caption); font-size: 13px; padding: 2px 4px; }
.list-none { padding: 14px 0; text-align: center; font-size: 11px; color: var(--caption); }
.member-chips { display: flex; flex-wrap: wrap; gap: 8px; }
.member-chip {
  display: flex;
  align-items: center;
  gap: 6px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--r-pill);
  padding: 5px 12px 5px 5px;
}
.member-avatar {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: var(--secondary);
  display: flex;
  align-items: center;
  justify-content: center;
}
.member-avatar-txt { font-size: 12px; font-weight: 700; color: var(--primary-deep); }
.member-name { font-size: 12px; font-weight: 700; color: var(--title); }
.ph { color: var(--caption); }
</style>
