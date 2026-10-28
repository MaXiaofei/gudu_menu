<template>
  <page-meta :page-style="gdThemeStyle" />
  <!-- 编辑食材：本家食材可改名 / 分类 / 食用属性、可删除；公共食材只读（平台维护） -->
  <view class="page">
    <ui-back-header :title="ing?.name" :subtitle="ing?.categoryName || '未分类'">
      <template #action>
        <text v-if="canEdit" class="del" data-testid="ing-delete" @click="confirmDelete">✕</text>
      </template>
    </ui-back-header>

    <ui-state v-if="loading" mode="loading" />
    <ui-state v-else-if="!ing" mode="empty" text="食材不存在" />
    <template v-else>
      <scroll-view scroll-y class="body">
        <view class="own-line">
          <ui-own-tag v-if="canEdit" />
          <text v-else class="platform-tag" data-testid="platform-tag">平台食材</text>
        </view>

        <template v-if="canEdit">
          <text class="q-line">食材名</text>
          <input v-model="name" class="field-ipt" maxlength="30" placeholder="食材名" placeholder-class="ph" data-testid="ing-name" />

          <text class="q-line">采购分类</text>
          <view class="cats">
            <view
              v-for="c in categories"
              :key="c.id"
              class="cat"
              :class="{ on: categoryId === c.id }"
              @click="categoryId = c.id"
            >{{ c.name }}</view>
          </view>
        </template>

        <text class="q-line">食用属性</text>
        <view class="card" @click="pickEdible">
          <text class="card-label">{{ edibleText(edible) }}</text>
          <text class="card-value">{{ edibleDesc(edible) }}</text>
          <text v-if="canEdit" class="arrow">›</text>
        </view>
        <text v-if="!canEdit" class="readonly-tip" data-testid="readonly-tip">{{ readonlyTip }}</text>
        <view style="height: 96px" />
      </scroll-view>

      <view v-if="canEdit" class="bottom">
        <button class="btn-primary" :disabled="saving" @click="save">{{ saving ? '保存中…' : '保存' }}</button>
      </view>
    </template>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { ingredientDetail, updateIngredient, deleteIngredient, type IngredientRow } from '@/api/ingredient'
import { listDict, type DictItem } from '@/api/common'
import { ingredientPermission } from '@/utils/ingredientSearch'

const loading = ref(true)
const ing = ref<IngredientRow | null>(null)
const name = ref('')
const categoryId = ref(0)
const categories = ref<DictItem[]>([])
const edible = ref(1)
const saving = ref(false)
/** 本家私有食材可改可删；公共食材由平台维护，只读（后端同步拦截）。 */
const canEdit = computed(() => ingredientPermission(ing.value).canEdit)
const readonlyTip = computed(() => ingredientPermission(ing.value).readonlyTip)

onLoad(async (options) => {
  const id = Number(options?.id || 0)
  try {
    ing.value = await ingredientDetail(id)
    if (ing.value) {
      name.value = ing.value.name
      categoryId.value = ing.value.purchaseCategoryId ?? 0
      edible.value = ing.value.edible ?? 1
      if (ing.value.familyOwned) listDict('purchase_category').then((r) => (categories.value = r)).catch(() => {})
    }
  } catch {}
  loading.value = false
})

function edibleText(e: number): string {
  switch (e) {
    case 2: return '饮料零食'
    case 3: return '生活用品'
    default: return '食用'
  }
}
function edibleDesc(e: number): string {
  switch (e) {
    case 2: return '不参与营养统计'
    case 3: return '非食用，仅备料清单用'
    default: return '正常食材，参与营养统计'
  }
}

function pickEdible() {
  if (!canEdit.value) return
  uni.showActionSheet({
    itemList: ['食用', '饮料零食', '生活用品'],
    success: ({ tapIndex }) => (edible.value = tapIndex + 1),
  })
}

async function save() {
  if (!ing.value || !canEdit.value) return
  const n = name.value.trim()
  if (!n) {
    uni.showToast({ title: '请输入食材名', icon: 'none' })
    return
  }
  saving.value = true
  try {
    await updateIngredient(ing.value.id, {
      name: n,
      purchaseCategoryId: categoryId.value || undefined,
      edible: edible.value,
    })
    uni.showToast({ title: '已保存', icon: 'none' })
    setTimeout(() => uni.navigateBack(), 400)
  } catch {
    // 请求层已提示后端原因（重名、内容违规等）
  } finally {
    saving.value = false
  }
}

function confirmDelete() {
  if (!ing.value || !canEdit.value) return
  uni.showModal({
    title: '删除食材',
    content: `确定删除「${ing.value.name}」？`,
    confirmText: '删除',
    confirmColor: '#DB5A4E',
    success: async ({ confirm }) => {
      if (!confirm) return
      try {
        await deleteIngredient(ing.value!.id)
        uni.showToast({ title: '已删除', icon: 'none' })
        setTimeout(() => uni.navigateBack(), 400)
      } catch {
        // 在用时后端提示「这个食材正在使用（库存/采购/菜谱…），不能删除」，请求层已展示
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
.body {
  flex: 1;
  min-height: 0;
  padding: 0 16px;
  box-sizing: border-box;
}
.del {
  color: var(--caption);
  font-size: var(--fs-lg);
  padding: 4px 6px;
}
.q-line {
  display: block;
  font-size: var(--fs-sm);
  color: var(--body);
  margin: 12px 0 8px;
}
.card {
  display: flex;
  align-items: center;
  gap: 10px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--r-md);
  padding: 13px 14px;
}
.card-label { font-size: var(--fs-md); font-weight: 800; color: var(--title); }
.card-value { flex: 1; font-size: var(--fs-xs); color: var(--caption); }
.own-line { margin: 12px 0 0; display: flex; }
.platform-tag {
  padding: 1px 6px;
  border-radius: var(--r-sm);
  background: var(--bg);
  border: 1px solid var(--border);
  color: var(--caption);
  font-size: var(--fs-tiny);
}
.field-ipt {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--r-sm);
  padding: 10px 12px;
  font-size: var(--fs-md);
  color: var(--title);
}
.ph { color: var(--caption); }
.cats { display: flex; flex-wrap: wrap; gap: 7px; }
.cat {
  padding: 6px 13px;
  border-radius: var(--r-sm);
  background: var(--card);
  border: 1px solid var(--border);
  color: var(--body);
  font-size: var(--fs-xs);
}
.cat.on { background: var(--primary); border-color: var(--primary); color: var(--on-primary); font-weight: 700; }
.readonly-tip {
  display: block;
  margin-top: 10px;
  font-size: var(--fs-xs);
  color: var(--caption);
}
.arrow { color: var(--caption); font-size: var(--fs-lg); font-weight: 700; }
.bottom {
  padding: 8px 16px calc(12px + env(safe-area-inset-bottom));
  background: var(--card);
  border-top: 1px solid var(--border);
}
</style>
