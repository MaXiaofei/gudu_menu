<template>
  <view class="create">
    <view class="block">
      <text class="label">菜名 *</text>
      <u-input v-model="form.name" placeholder="如：番茄炒蛋" border="surround" />
    </view>

    <view class="block import-block">
      <text class="label">URL 导入（下厨房/美食杰/豆果）</text>
      <view class="import-row">
        <u-input v-model="importUrl" placeholder="粘贴菜谱链接" border="surround" />
        <u-button size="mini" type="success" :loading="importing" @click="onImportUrl">导入</u-button>
      </view>
    </view>
    <view class="block">
      <text class="label">备注</text>
      <u-input v-model="form.note" placeholder="可选" border="surround" />
    </view>
    <view class="row">
      <view class="col">
        <text class="label">备料(分)</text>
        <u-input v-model="form.prepTime" type="number" border="surround" />
      </view>
      <view class="col">
        <text class="label">烹饪(分)</text>
        <u-input v-model="form.cookTime" type="number" border="surround" />
      </view>
      <view class="col">
        <text class="label">难度1-5</text>
        <u-input v-model="form.difficulty" type="number" border="surround" />
      </view>
    </view>

    <text class="section">做法步骤</text>
    <view class="step" v-for="(s, i) in steps" :key="i">
      <view class="step-head">
        <text>步骤 {{ i + 1 }}</text>
        <u-button size="mini" type="error" @click="steps.splice(i, 1)">删除</u-button>
      </view>
      <u-textarea v-model="s.text" :placeholder="`步骤 ${i + 1} 描述`" />
    </view>
    <u-button @click="addStep">+ 添加步骤</u-button>

    <u-button type="primary" :loading="loading" @click="onSave" class="save-btn">保存</u-button>
  </view>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { saveDish, importDishByUrl } from '@/api/dish'

// dish 字段对齐后端 Dish 实体（name/note/prepTime/cookTime/difficulty/price）
const form = reactive({ name: '', note: '', prepTime: '', cookTime: '', difficulty: '3', price: '' })
// steps 字段对齐后端 DishStep 实体（seq/text/sortOrder/images）
const steps = reactive<any[]>([{ text: '' }])
const loading = ref(false)
const importUrl = ref('')
const importing = ref(false)

async function onImportUrl() {
  if (!importUrl.value.trim()) {
    uni.showToast({ title: '请粘贴链接', icon: 'none' })
    return
  }
  importing.value = true
  try {
    const id = await importDishByUrl(importUrl.value.trim())
    uni.showToast({ title: '导入成功', icon: 'success' })
    setTimeout(() => uni.redirectTo({ url: `/pages/dish/Detail?id=${id}` }), 800)
  } catch {
    // request.ts 已弹 toast
  } finally {
    importing.value = false
  }
}

function addStep() { steps.push({ text: '' }) }

async function onSave() {
  if (!form.name.trim()) {
    uni.showToast({ title: '请输入菜名', icon: 'none' })
    return
  }
  loading.value = true
  try {
    // 对齐 DishSaveDTO: { dish, steps }（cuisineIds/tagIds/categoryIds/ingredients YAGNI 留第二批）
    const payload = {
      dish: {
        name: form.name,
        note: form.note || null,
        prepTime: form.prepTime ? Number(form.prepTime) : null,
        cookTime: form.cookTime ? Number(form.cookTime) : null,
        difficulty: form.difficulty ? Number(form.difficulty) : null,
        price: form.price ? Number(form.price) : null
      },
      steps: steps
        .filter(s => s.text && s.text.trim())
        .map((s, i) => ({ seq: i + 1, text: s.text, sortOrder: i + 1 }))
    }
    await saveDish(payload)
    uni.showToast({ title: '已保存' })
    setTimeout(() => uni.navigateBack(), 800)
  } catch {
    // request.ts 已弹 toast
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.create { padding: 30rpx; }
.block { margin-bottom: 24rpx; }
.import-block { background: #faf6f0; padding: 16rpx; border-radius: 12rpx; }
.import-row { display: flex; gap: 12rpx; align-items: center; }
.label { display: block; font-size: 26rpx; color: #666; margin-bottom: 12rpx; }
.row { display: flex; gap: 16rpx; }
.col { flex: 1; }
.section { display: block; font-size: 30rpx; font-weight: bold; margin: 30rpx 0 16rpx; }
.step { border: 1rpx solid #eee; padding: 16rpx; border-radius: 12rpx; margin-bottom: 16rpx; }
.step-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12rpx; }
.save-btn { margin-top: 40rpx; }
</style>
