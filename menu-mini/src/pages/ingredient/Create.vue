<template>
  <!-- 录入食材：名称 + 采购分类（AI 补全属 AI 估营养范畴，暂不做） -->
  <view class="page">
    <view class="top" :style="{ paddingTop: sb + 'px' }">
      <text class="back" @click="goBack">‹</text>
      <text class="title">录入食材</text>
      <view style="width: 30px" />
    </view>

    <scroll-view scroll-y class="body">
      <view class="field">
        <text class="field-label">食材名 <text class="required">*</text></text>
        <input v-model="name" class="field-ipt" placeholder="如：番茄" placeholder-class="ph" />
      </view>

      <text class="sec-label">采购分类（可选）</text>
      <view class="cats">
        <view
          v-for="c in categories"
          :key="c.id"
          class="cat"
          :class="{ on: categoryId === c.id }"
          @click="pickCategory(c.id)"
        >{{ c.name }}</view>
        <view class="cat custom" :class="{ on: customMode }" @click="openCustom">＋ 自定义</view>
      </view>
      <input v-if="customMode" v-model="customName" class="field-ipt custom-ipt" placeholder="自定义分类名" placeholder-class="ph" :focus="customMode" />

      <view style="height: 96px" />
    </scroll-view>

    <view class="bottom">
      <button class="btn-primary" :disabled="saving" @click="save">{{ saving ? '保存中…' : '保存' }}</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { listDict, type DictItem } from '@/api/common'
import { createIngredient, upsertDict } from '@/api/ingredient'
import { statusBarHeight } from '@/utils/token'

const sb = statusBarHeight()
const name = ref('')
const categories = ref<DictItem[]>([])
const categoryId = ref(0)
const customMode = ref(false)
const customName = ref('')
const saving = ref(false)

onLoad(() => {
  listDict('purchase_category').then((r) => (categories.value = r)).catch(() => {})
})

function pickCategory(id: number) {
  categoryId.value = id
  customMode.value = false
}
function openCustom() {
  customMode.value = true
  categoryId.value = 0
}

async function save() {
  const n = name.value.trim()
  if (!n) {
    uni.showToast({ title: '请输入食材名', icon: 'none' })
    return
  }
  saving.value = true
  try {
    let cid = categoryId.value || undefined
    if (customMode.value && customName.value.trim()) {
      cid = await upsertDict(customName.value.trim(), 'purchase_category')
    }
    await createIngredientFull(n, cid)
    uni.showToast({ title: '已保存', icon: 'none' })
    setTimeout(() => uni.navigateBack(), 400)
  } catch {
    uni.showToast({ title: '保存失败', icon: 'none' })
  } finally {
    saving.value = false
  }
}

/** 带分类建档。 */
async function createIngredientFull(n: string, purchaseCategoryId?: number): Promise<void> {
  const { request } = await import('@/utils/request')
  await request({
    url: '/ingredient',
    method: 'POST',
    data: { ingredient: { name: n, purchaseCategoryId }, nutritions: [] },
  })
}

function goBack() {
  uni.navigateBack()
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
  display: flex;
  align-items: center;
  padding: 0 14px;
}
.back {
  font-size: 26px;
  font-weight: 700;
  color: var(--title);
  line-height: 1;
  padding: 4px 8px;
}
.title {
  flex: 1;
  text-align: center;
  font-size: 15px;
  font-weight: 700;
  color: var(--title);
}
.body {
  flex: 1;
  min-height: 0;
  padding: 0 16px;
  box-sizing: border-box;
}
.field { margin-top: 14px; }
.field-label {
  display: block;
  font-size: 11px;
  font-weight: 800;
  color: var(--body);
  margin-bottom: 6px;
}
.required { color: var(--error); }
.field-ipt {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--r-sm);
  padding: 10px 12px;
  font-size: 14px;
  color: var(--title);
}
.custom-ipt { margin-top: 8px; }
.sec-label {
  display: block;
  font-size: 11px;
  font-weight: 800;
  color: var(--body);
  margin: 16px 0 8px;
}
.cats { display: flex; flex-wrap: wrap; gap: 7px; }
.cat {
  padding: 6px 13px;
  border-radius: var(--r-sm);
  background: var(--card);
  border: 1px solid var(--border);
  color: var(--body);
  font-size: 11px;
}
.cat.on { background: var(--primary); border-color: var(--primary); color: #FFFFFF; font-weight: 700; }
.bottom {
  padding: 8px 16px calc(12px + env(safe-area-inset-bottom));
  background: var(--card);
  border-top: 1px solid var(--border);
}
.ph { color: var(--caption); }
</style>
