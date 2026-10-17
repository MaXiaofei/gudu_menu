<template>
  <page-meta :page-style="gdThemeStyle" />
  <view class="page">
    <!-- 录入页：只有返回箭头（§13.1）；dirty 拦截 -->
    <view class="top" :style="{ paddingTop: sb + 'px' }">
      <text class="back" @click="goBack">‹</text>
    </view>

    <!-- 写菜谱 / 导入链接 分段（奶油底圆角 + 深色选中） -->
    <view class="seg">
      <view class="seg-item" :class="{ on: mode === 'manual' }" @click="mode = 'manual'">写菜谱</view>
      <view class="seg-item" :class="{ on: mode === 'import' }" @click="mode = 'import'">导入链接</view>
    </view>

    <scroll-view scroll-y class="body">
      <!-- ===== 导入链接 Tab ===== -->
      <import-panel v-if="mode === 'import'" />

      <!-- ===== 写菜谱 Tab ===== -->
      <template v-if="mode === 'manual'">
        <!-- 封面 -->
        <view class="cover-box" @click="pickCover">
          <image v-if="coverLocal" class="cover-img" :src="coverLocal" mode="aspectFill" />
          <image v-else-if="coverUrl && !coverFailed" class="cover-img" :src="thumbOf(coverUrl)" mode="aspectFill" @error="coverFailed = true" />
          <view v-else class="cover-ph">
            <ui-icon name="camera" tone="primary" :size="32" />
            <text class="cover-ph-txt">添加封面（可选）</text>
          </view>
          <view v-if="coverLocal || coverUrl" class="cover-ops">
            <view class="cover-op" @click.stop="pickCover"><text class="cover-op-txt">更换</text></view>
            <view class="cover-op" @click.stop="removeCover"><text class="cover-op-txt">删除</text></view>
          </view>
        </view>

        <!-- 菜名 -->
        <view class="field">
          <text class="field-label">菜名 <text class="required">*</text></text>
          <input v-model="name" class="field-ipt" placeholder="如：番茄炒蛋" placeholder-class="ph" />
        </view>

        <!-- 备料/烹饪 -->
        <view class="field-row">
          <view class="field half">
            <text class="field-label">备料(分)</text>
            <input v-model="prepTime" class="field-ipt" type="number" placeholder="分" placeholder-class="ph" />
          </view>
          <view class="field half">
            <text class="field-label">烹饪(分)</text>
            <input v-model="cookTime" class="field-ipt" type="number" placeholder="分" placeholder-class="ph" />
          </view>
        </view>

        <!-- 难度 -->
        <view class="field">
          <text class="field-label">难度</text>
          <view class="stars">
            <text
              v-for="i in 5"
              :key="i"
              class="star"
              :class="{ on: i <= difficulty }"
              @click="difficulty = i"
            >★</text>
            <text class="star-label">{{ difficultyLabels[difficulty - 1] || '' }}</text>
          </view>
        </view>

        <!-- 标签（必选） -->
        <view class="field">
          <text class="field-label">标签 <text class="required">*</text></text>
          <scroll-view scroll-x class="tag-scroll" :show-scrollbar="false">
            <view class="tag-row">
              <view
                v-for="t in tags"
                :key="t.id"
                class="tag"
                :class="{ on: selectedTagIds.includes(t.id) }"
                @click="toggleTag(t.id)"
              >{{ t.name }}</view>
            </view>
          </scroll-view>
        </view>

        <!-- 菜系 -->
        <view class="field">
          <text class="field-label">菜系</text>
          <scroll-view scroll-x class="tag-scroll" :show-scrollbar="false">
            <view class="tag-row">
              <view
                v-for="c in cuisines"
                :key="c.id"
                class="tag"
                :class="{ on: selectedCuisineIds.includes(c.id) }"
                @click="toggleCuisine(c.id)"
              >{{ c.name }}</view>
            </view>
          </scroll-view>
        </view>

        <!-- 介绍 -->
        <view class="field">
          <text class="field-label">菜谱介绍</text>
          <textarea v-model="note" class="field-area" :maxlength="500" placeholder="家常做法、注意事项都写这里" placeholder-class="ph" />
        </view>

        <!-- 用料区 -->
        <view class="sec-bar-row"><view class="sec-bar" /><text class="sec-bar-title">用料</text></view>
        <view v-for="(ing, i) in ingredients" :key="ing.key" class="ing-row">
          <ui-ing-icon :name="ing.name" :size="36" />
          <text class="ing-name">{{ ing.name }}</text>
          <input v-model="ing.amount" class="ing-amount" type="digit" placeholder="用量" placeholder-class="ph" />
          <input
            v-model="ing.unit"
            class="ing-unit"
            :class="{ matched: unitMatched(ing.unit) }"
            placeholder="单位"
            placeholder-class="ph"
          />
          <text class="row-del" @click="ingredients.splice(i, 1)">✕</text>
        </view>
        <view class="add-row-btn" @click="openIngSheet">＋ 加用料</view>

        <!-- 步骤区 -->
        <view class="sec-bar-row"><view class="sec-bar" /><text class="sec-bar-title">做法</text></view>
        <steps-editor :steps="steps" @add="steps.push({ key: ++keySeq, text: '', imageLocal: '', imageUrl: '' })" />

        <view style="height: 96px" />
      </template>
    </scroll-view>

    <!-- 底部三按钮（写菜谱 Tab） -->
    <view v-if="mode === 'manual'" class="bottom">
      <button class="btn-ghost third" :disabled="saving" @click="onSaveDraft">存草稿</button>
      <button class="btn-ghost third" :disabled="saving" @click="onPreview">预览</button>
      <button class="btn-primary third" :disabled="saving" @click="onPublish">
        {{ saving ? '处理中…' : '发布' }}
      </button>
    </view>

    <ingredient-picker
      v-if="ingSheet"
      :all="allIngredients"
      :hot="hotIngredients"
      @close="ingSheet = false"
      @pick="addIngredient"
      @created="onIngCreated"
    />
  </view>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { saveDish, saveDraft, draftDetail, deleteDraft } from '@/api/dish'
import IngredientPicker from './parts/IngredientPicker.vue'
import ImportPanel from './parts/ImportPanel.vue'
import StepsEditor, { type StepRow } from './parts/StepsEditor.vue'
import { listDict, type DictItem } from '@/api/common'
import { listAllIngredients, type IngredientItem } from '@/api/ingredient'
import { chooseImages, uploadImage } from '@/api/upload'
import { thumbOf } from '@/utils/image'
import { navInset } from '@/utils/token'
import { usePreviewStore } from '@/store/preview'

const sb = navInset()
const preview = usePreviewStore()

let keySeq = 0
const mode = ref<'manual' | 'import'>('manual')

// ---- 表单 ----
const name = ref('')
const prepTime = ref('')
const cookTime = ref('')
const difficulty = ref(3)
const difficultyLabels = ['超简单', '简单', '中等', '困难', '炼狱']
const note = ref('')
const coverLocal = ref('')
const coverUrl = ref('')
const coverFailed = ref(false)

const tags = ref<DictItem[]>([])
const cuisines = ref<DictItem[]>([])
const units = ref<DictItem[]>([])
const selectedTagIds = ref<number[]>([])
const selectedCuisineIds = ref<number[]>([])

interface IngRow { key: number; id: number; name: string; amount: string; unit: string }
const ingredients = reactive<IngRow[]>([])
const steps = reactive<StepRow[]>([])

// 草稿
const draftId = ref<number | null>(null)
const initialSig = ref('')

// 弹层
const ingSheet = ref(false)
const hotIngredients = ref<IngredientItem[]>([])
const allIngredients = ref<IngredientItem[]>([])

const saving = ref(false)

onLoad(async (options) => {
  // 预载字典（静默失败）
  listDict('tag').then((r) => (tags.value = r)).catch(() => {})
  listDict('cuisine').then((r) => (cuisines.value = r)).catch(() => {})
  listDict('unit').then((r) => (units.value = r)).catch(() => {})
  listAllIngredients()
    .then((r) => {
      allIngredients.value = r
      hotIngredients.value = r.slice(0, 8)
    })
    .catch(() => {})

  if (options?.draftId) {
    draftId.value = Number(options.draftId)
    try {
      const d = await draftDetail(draftId.value)
      name.value = d.name || ''
      prepTime.value = d.prepTime?.toString() || ''
      cookTime.value = d.cookTime?.toString() || ''
      difficulty.value = d.difficulty ?? 3
      note.value = d.note || ''
      coverUrl.value = d.coverUrl || ''
      selectedTagIds.value = [...(d.tagIds ?? [])]
      selectedCuisineIds.value = [...(d.cuisineIds ?? [])]
      d.ingredients.forEach((ing) =>
        ingredients.push({ key: ++keySeq, id: ing.ingredientId, name: ing.ingredientName || `#${ing.ingredientId}`, amount: ing.amount || '', unit: ing.unitText || '' }),
      )
      d.steps.forEach((s) =>
        steps.push({ key: ++keySeq, text: s.text || '', imageLocal: '', imageUrl: (s.images || '').split(',').filter(Boolean)[0] || '' }),
      )
    } catch {
      uni.showToast({ title: '草稿加载失败', icon: 'none' })
    }
  }
  initialSig.value = signature()
})

// ---- dirty 签名 ----
function signature(): string {
  return JSON.stringify([
    name.value, prepTime.value, cookTime.value, difficulty.value, note.value,
    !!coverLocal.value || !!coverUrl.value,
    selectedTagIds.value, selectedCuisineIds.value,
    ingredients.map((i) => `${i.id}|${i.amount}|${i.unit}`),
    steps.map((s) => `${s.text}|${!!s.imageLocal || !!s.imageUrl}`),
  ])
}
const dirty = computed(() => signature() !== initialSig.value)

function goBack() {
  if (mode.value === 'import' || !dirty.value) {
    uni.navigateBack()
    return
  }
  if (draftId.value) {
    uni.showModal({
      title: '放弃修改？',
      content: '这次改动还没有保存',
      confirmText: '放弃',
      success: ({ confirm }) => confirm && uni.navigateBack(),
    })
  } else {
    uni.showModal({
      title: '先存草稿再走？',
      content: '写了一半的内容，可以先存成草稿',
      confirmText: '存草稿',
      success: async ({ confirm }) => {
        if (!confirm) return
        const ok = await doSaveDraft()
        if (ok) uni.navigateBack()
      },
    })
  }
}

// ---- 图片 ----
async function pickCover() {
  const [p] = await chooseImages(1)
  if (!p) return
  coverLocal.value = p
  coverUrl.value = ''
}
function removeCover() {
  coverLocal.value = ''
  coverUrl.value = ''
}
function toggleTag(id: number) {
  const i = selectedTagIds.value.indexOf(id)
  i >= 0 ? selectedTagIds.value.splice(i, 1) : selectedTagIds.value.push(id)
}
function toggleCuisine(id: number) {
  const i = selectedCuisineIds.value.indexOf(id)
  i >= 0 ? selectedCuisineIds.value.splice(i, 1) : selectedCuisineIds.value.push(id)
}
function unitMatched(text: string): boolean {
  return !!text && units.value.some((u) => u.name === text)
}

// ---- 加用料 ----
function openIngSheet() {
  ingSheet.value = true
}
function addIngredient(id: number, ingName: string) {
  if (ingredients.some((i) => i.id === id)) {
    uni.showToast({ title: `已加过「${ingName}」`, icon: 'none' })
    return
  }
  ingredients.push({ key: ++keySeq, id, name: ingName, amount: '', unit: '' })
  ingSheet.value = false
}
function onIngCreated(it: IngredientItem) {
  allIngredients.value.unshift(it)
  hotIngredients.value = [it, ...hotIngredients.value].slice(0, 8)
  ingSheet.value = false
  ingredients.push({ key: ++keySeq, id: it.id, name: it.name, amount: '', unit: '' })
}

// ---- 上传全部图片（发布/存草稿前） ----
async function uploadAll(): Promise<void> {
  if (coverLocal.value) {
    const r = await uploadImage(coverLocal.value)
    coverUrl.value = r.url
    coverLocal.value = ''
  }
  for (const s of steps) {
    if (s.imageLocal) {
      const r = await uploadImage(s.imageLocal)
      s.imageUrl = r.url
      s.imageLocal = ''
    }
  }
}

/** 有效步骤（过滤空文本）。 */
function validSteps() {
  return steps.filter((s) => s.text.trim()).map((s, i) => ({ seq: i + 1, sortOrder: i + 1, text: s.text.trim(), images: s.imageUrl || undefined }))
}

// ---- 存草稿（不校验必填） ----
async function doSaveDraft(): Promise<boolean> {
  saving.value = true
  try {
    await uploadAll()
    const id = await saveDraft({
      id: draftId.value ?? undefined,
      dish: {
        name: name.value,
        coverUrl: coverUrl.value || undefined,
        note: note.value || undefined,
        prepTime: prepTime.value ? Number(prepTime.value) : undefined,
        cookTime: cookTime.value ? Number(cookTime.value) : undefined,
        difficulty: difficulty.value,
      },
      steps: validSteps(),
      // 草稿：用料按自由文本原文存
      ingredients: ingredients.map((i) => ({ ingredientId: i.id })),
      tagIds: selectedTagIds.value,
      cuisineIds: selectedCuisineIds.value,
    } as any)
    draftId.value = id
    initialSig.value = signature()
    uni.showToast({ title: '已存草稿', icon: 'none' })
    return true
  } catch {
    uni.showToast({ title: '存草稿失败', icon: 'none' })
    return false
  } finally {
    saving.value = false
  }
}
async function onSaveDraft() {
  const ok = await doSaveDraft()
  if (ok && draftId.value && !initialSig.value) uni.navigateBack()
}

// ---- 预览 ----
function onPreview() {
  preview.set({
    name: name.value,
    coverLocal: coverLocal.value,
    coverUrl: coverUrl.value,
    prepTime: prepTime.value,
    cookTime: cookTime.value,
    difficulty: difficulty.value,
    tags: [
      ...cuisines.value.filter((c) => selectedCuisineIds.value.includes(c.id)).map((c) => c.name),
      ...tags.value.filter((t) => selectedTagIds.value.includes(t.id)).map((t) => t.name),
    ],
    note: note.value,
    ingredients: ingredients.map((i) => ({ name: i.name, amount: `${i.amount} ${i.unit}`.trim() })),
    steps: steps.filter((s) => s.text.trim()).map((s) => ({ text: s.text.trim(), image: s.imageLocal || thumbOf(s.imageUrl) })),
  })
  uni.navigateTo({ url: '/pages/dish/Preview' })
}

// ---- 发布 ----
async function onPublish() {
  if (!name.value.trim()) {
    uni.showToast({ title: '请输入菜名', icon: 'none' })
    return
  }
  if (!selectedTagIds.value.length) {
    uni.showToast({ title: '请选择标签', icon: 'none' })
    return
  }
  saving.value = true
  try {
    await uploadAll()
    await saveDish({
      dish: {
        name: name.value.trim(),
        coverUrl: coverUrl.value || undefined,
        note: note.value || undefined,
        prepTime: prepTime.value ? Number(prepTime.value) : undefined,
        cookTime: cookTime.value ? Number(cookTime.value) : undefined,
        difficulty: difficulty.value,
      },
      steps: validSteps(),
      ingredients: ingredients.map((i) => ({
        ingredientId: i.id,
        amount: i.amount ? Number(i.amount) : undefined,
        unitId: unitMatched(i.unit) ? units.value.find((u) => u.name === i.unit)!.id : undefined,
      })),
      tagIds: selectedTagIds.value,
      cuisineIds: selectedCuisineIds.value,
    })
    if (draftId.value) {
      deleteDraft(draftId.value).catch(() => {})
    }
    uni.showToast({ title: '已保存', icon: 'none' })
    uni.setStorageSync('dish_sort_latest', '1')
    setTimeout(() => uni.switchTab({ url: '/pages/dish/List' }), 400)
  } catch (e) {
    uni.showToast({ title: `保存失败：${e}`, icon: 'none' })
  } finally {
    saving.value = false
  }
}

// 预览页发布回调（经页面栈调用）
defineExpose({ onPublishFromPreview: onPublish })

</script>

<style scoped>
.page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--bg);
}
.top {
  padding: 0 6px;
}
.back {
  font-size: var(--fs-h2);
  font-weight: 700;
  color: var(--title);
  padding: 4px 10px;
  line-height: 1.2;
}
.seg {
  margin: 8px 16px;
  background: var(--secondary);
  border-radius: var(--r-md);
  padding: 3px;
  display: flex;
}
.seg-item {
  flex: 1;
  text-align: center;
  padding: 7px 0;
  border-radius: 9px;
  font-size: var(--fs-sm);
  font-weight: 700;
  color: var(--body);
}
.seg-item.on {
  background: var(--title);
  color: var(--on-primary);
  font-weight: 800;
}
.body {
  flex: 1;
  min-height: 0;
  padding: 0 16px;
  box-sizing: border-box;
}
/* 封面 */
.cover-box {
  position: relative;
  height: 96px;
  border-radius: var(--r-md);
  overflow: hidden;
  margin-top: 6px;
}
.cover-img { width: 100%; height: 100%; }
.cover-ph {
  width: 100%;
  height: 100%;
  background: var(--card);
  border: 1px dashed var(--border);
  border-radius: var(--r-md);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
}
.cover-ph-ico { font-size: var(--fs-subtitle); }
.cover-ph-txt { font-size: var(--fs-xs); color: var(--caption); }
.cover-ops {
  position: absolute;
  right: 8px;
  bottom: 8px;
  display: flex;
  gap: 6px;
}
.cover-op {
  background: rgba(0, 0, 0, 0.45);
  border-radius: var(--r-pill);
  padding: 4px 12px;
}
.cover-op-txt { color: var(--on-primary); font-size: var(--fs-tiny); font-weight: 700; }
/* 字段 */
.field { margin-top: 14px; }
.field-row { display: flex; gap: 10px; margin-top: 14px; }
.field.half { flex: 1; }
.field-label {
  display: block;
  font-size: var(--fs-xs);
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
  font-size: var(--fs-md);
  color: var(--title);
}
.field-area {
  width: 100%;
  box-sizing: border-box;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--r-sm);
  padding: 10px 12px;
  font-size: var(--fs-md);
  color: var(--title);
  height: 66px;
}
.stars { display: flex; align-items: center; gap: 6px; }
.star { font-size: var(--fs-h3); color: var(--border); }
.star.on { color: var(--primary); }
.star-label { font-size: var(--fs-xs); color: var(--caption); margin-left: 6px; }
.tag-scroll { white-space: nowrap; }
.tag-row { display: inline-flex; gap: 6px; }
.tag {
  flex-shrink: 0;
  padding: 5px 12px;
  border-radius: var(--r-pill);
  background: var(--card);
  border: 1px solid var(--border);
  color: var(--body);
  font-size: var(--fs-xs);
}
.tag.on { background: var(--primary); border-color: var(--primary); color: var(--on-primary); font-weight: 700; }
/* 用料 */
.sec-bar-row { display: flex; align-items: center; gap: 6px; margin: 18px 0 8px; }
.sec-bar { width: 3px; height: 13px; border-radius: 2px; background: var(--primary); }
.sec-bar-title { font-size: var(--fs-md); font-weight: 800; color: var(--title); }
.ing-row {
  display: flex;
  align-items: center;
  gap: 8px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--r-sm);
  padding: 8px 10px;
  margin-bottom: 6px;
}
.ing-name {
  flex: 1;
  min-width: 0;
  font-size: var(--fs-md);
  font-weight: 700;
  color: var(--title);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ing-amount {
  width: 56px;
  text-align: right;
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: var(--r-xs, 4px);
  padding: 6px 8px;
  font-size: var(--fs-sm);
  color: var(--title);
}
.ing-unit {
  width: 72px;
  text-align: center;
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: 4px;
  padding: 6px 4px;
  font-size: var(--fs-sm);
  color: var(--body);
}
.ing-unit.matched {
  color: var(--primary);
  font-weight: 800;
  border-color: var(--primary);
}
.row-del { color: var(--caption); font-size: var(--fs-md); padding: 2px 4px; }
.add-row-btn {
  border: 1px solid var(--border);
  border-radius: var(--r-md);
  background: var(--card);
  padding: 11px 0;
  text-align: center;
  color: var(--body);
  font-size: var(--fs-md);
  margin-top: 4px;
}
/* 步骤 */
/* 底部 */
.bottom {
  display: flex;
  gap: 8px;
  padding: 8px 16px calc(12px + env(safe-area-inset-bottom));
  background: var(--card);
  border-top: 1px solid var(--border);
}
.third { flex: 1; }
/* 导入 Tab */
/* 弹层 */
.mask {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  background: rgba(0, 0, 0, 0.4);
  z-index: 100;
  display: flex;
  align-items: flex-end;
}
.ph { color: var(--caption); }
</style>
