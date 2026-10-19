<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { searchDishes, getDishDetail, type DishDetail, type DishSearchRow } from '@/api/dish'
import { listIngredients, type Ingredient } from '@/api/ingredient'
import { formatCount } from '@/utils/dashboard'
import Pagination from '@/components/Pagination.vue'
import DishThumb from '@/components/DishThumb.vue'
import IngIcon from '@/components/IngIcon.vue'
import PendingApi from '@/components/PendingApi.vue'

/**
 * 公共菜谱库（原型 AdminLibrary）。
 * 多租户落地前，全部菜谱即公共库；「已上架」用 /dish/search 真实数据。
 * 投稿审核（待审核 / 已驳回 / 已下架、自动检查）依赖 dish_submission，接口待上线（prototype-ui-rollout 3.7）。
 */
type Tab = 'pending' | 'online' | 'rejected' | 'offline'
const TABS: { key: Tab; label: string; ready: boolean }[] = [
  { key: 'pending', label: '待审核', ready: false },
  { key: 'online', label: '已上架', ready: true },
  { key: 'rejected', label: '已驳回', ready: false },
  { key: 'offline', label: '已下架', ready: false },
]

const router = useRouter()
const tab = ref<Tab>('online')
const loading = ref(false)
const list = ref<DishSearchRow[]>([])
const total = ref(0)
const query = reactive({ keyword: '', pageNum: 1, pageSize: 15 })

const selectedId = ref<number | null>(null)
const detail = ref<DishDetail | null>(null)
const detailLoading = ref(false)
const ingredientById = ref<Map<number, Ingredient>>(new Map())

async function load() {
  loading.value = true
  try {
    const page = await searchDishes({ keyword: query.keyword || undefined, pageNum: query.pageNum, pageSize: query.pageSize })
    list.value = Array.isArray(page?.records) ? page.records : []
    total.value = page?.total || 0
    if (!list.value.some((d) => d.id === selectedId.value) && list.value[0]) select(list.value[0])
  } finally {
    loading.value = false
  }
}

async function select(row: DishSearchRow) {
  selectedId.value = row.id
  detailLoading.value = true
  try {
    detail.value = await getDishDetail(row.id)
  } catch {
    detail.value = null
  } finally {
    detailLoading.value = false
  }
}

async function loadIngredients() {
  try {
    const all = await listIngredients()
    ingredientById.value = new Map((Array.isArray(all) ? all : []).map((i) => [i.id, i]))
  } catch {
    // 用料名称缺失时只显示用量
  }
}

function onSearch() {
  query.pageNum = 1
  load()
}

function onPage(p: number) {
  query.pageNum = p
  load()
}

const selectedRow = computed(() => list.value.find((d) => d.id === selectedId.value) ?? null)
const ingredients = computed(() =>
  (detail.value?.ingredients ?? []).map((i) => ({
    id: i.ingredientId,
    name: ingredientById.value.get(i.ingredientId)?.name ?? `食材 #${i.ingredientId}`,
    amount: i.amount ? `${i.amount}${i.unitName ?? ''}` : '适量',
  })),
)
const steps = computed(() => (detail.value?.steps ?? []).map((s) => s.text).filter(Boolean))

function sourceText(d: DishSearchRow) {
  if (d.sourceName) return `导入 · ${d.sourceName}`
  if (d.source === 'IMPORT') return '链接导入'
  return '官方 / 家庭录入'
}

function minutes(d: { prepTime?: number; cookTime?: number }) {
  const m = (d.prepTime ?? 0) + (d.cookTime ?? 0)
  return m > 0 ? `${m} 分钟` : '—'
}

function formatDate(s?: string) {
  return (s || '').replace('T', ' ').slice(0, 10) || '—'
}

function rowClass({ row }: { row: DishSearchRow }) {
  return row.id === selectedId.value ? 'is-selected' : ''
}

onMounted(() => {
  load()
  loadIngredients()
})
</script>

<template>
  <div class="yh-page">
    <div class="yh-head">
      <div>
        <h1>公共菜谱库</h1>
        <div class="yh-sub">所有家庭都能看到并加入自家；家庭投稿需审核后上架（审核流程待上线）</div>
      </div>
      <el-button type="primary" @click="router.push('/dish')">去菜谱管理</el-button>
    </div>

    <div class="toolbar">
      <div class="yh-seg" role="group" aria-label="上架状态" data-testid="lib-tabs">
        <button
          v-for="t in TABS"
          :key="t.key"
          type="button"
          :class="{ on: tab === t.key }"
          :aria-pressed="tab === t.key"
          @click="tab = t.key"
        >{{ t.key === 'online' && total ? `${t.label} ${formatCount(total)}` : t.label }}</button>
      </div>
      <template v-if="tab === 'online'">
        <el-input v-model="query.keyword" placeholder="搜菜名" clearable class="kw" @keyup.enter="onSearch" @clear="onSearch" />
        <el-button @click="onSearch">搜索</el-button>
      </template>
    </div>

    <section v-if="tab !== 'online'" class="yh-card">
      <PendingApi
        :title="`「${TABS.find((t) => t.key === tab)?.label}」列表待上线`"
        needs="投稿审核状态（dish_submission）与自动检查：敏感词 / 外链图片 / 名称相似度"
        task="prototype-ui-rollout 3.7"
      />
    </section>

    <div v-else class="split">
      <section class="yh-card table-card">
        <el-table v-loading="loading" :data="list" :row-class-name="rowClass" data-testid="lib-table" @row-click="select">
          <el-table-column label="菜谱" min-width="240">
            <template #default="{ row }">
              <div class="dish-cell">
                <DishThumb :src="row.coverUrl" :name="row.name" :size="48" />
                <span class="dish-txt">
                  <b>{{ row.name }}</b>
                  <span class="yh-muted">{{ sourceText(row) }}</span>
                </span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="时长" width="96">
            <template #default="{ row }">{{ minutes(row) }}</template>
          </el-table-column>
          <el-table-column label="做过" width="80">
            <template #default="{ row }">{{ row.cookedCount ? `${row.cookedCount} 次` : '—' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default>
              <span class="yh-tag ok">已上架</span>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty description="没有找到菜谱" :image-size="72" />
          </template>
        </el-table>
        <Pagination :total="total" :page-size="query.pageSize" :current-page="query.pageNum" @current-change="onPage" />
      </section>

      <section class="yh-card preview" aria-label="菜谱预览" data-testid="lib-preview">
        <template v-if="selectedRow">
          <div class="hero">
            <DishThumb :src="selectedRow.coverUrl" :name="selectedRow.name" ratio="16 / 10" :radius="14" original />
            <div class="cap">
              <b>{{ selectedRow.name }}</b>
              <span>{{ sourceText(selectedRow) }} · {{ minutes(selectedRow) }} · 录入 {{ formatDate(selectedRow.createTime) }}</span>
            </div>
          </div>

          <el-skeleton v-if="detailLoading" :rows="4" animated />
          <template v-else-if="detail">
            <div>
              <div class="label">用料 · {{ ingredients.length }} 样</div>
              <div class="ings">
                <span v-for="i in ingredients" :key="i.id" class="ing-chip">
                  <IngIcon :name="i.name" :size="24" />
                  {{ i.name }} <span class="yh-muted">{{ i.amount }}</span>
                </span>
                <span v-if="!ingredients.length" class="yh-muted">未关联用料</span>
              </div>
            </div>
            <div>
              <div class="label">步骤 · {{ steps.length }} 步</div>
              <ol class="steps">
                <li v-for="(s, i) in steps.slice(0, 4)" :key="i">{{ s }}</li>
              </ol>
              <span v-if="steps.length > 4" class="yh-muted">还有 {{ steps.length - 4 }} 步…</span>
            </div>
          </template>

          <div class="actions">
            <el-tooltip content="下架需审核状态接口（prototype-ui-rollout 3.7）" placement="top">
              <span><el-button disabled>下架</el-button></span>
            </el-tooltip>
            <el-button type="primary" @click="router.push('/dish')">编辑菜谱</el-button>
          </div>
        </template>
        <el-empty v-else description="选择左侧菜谱查看" :image-size="72" />
      </section>
    </div>
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.kw {
  width: 240px;
}
.split {
  display: grid;
  grid-template-columns: minmax(0, 1.5fr) minmax(360px, 1fr);
  gap: 16px;
  align-items: start;
}
@media (max-width: 1100px) {
  .split {
    grid-template-columns: 1fr;
  }
}
.table-card {
  padding: 6px 8px 12px;
}
.table-card :deep(.el-table__row) {
  cursor: pointer;
}
.table-card :deep(.el-table__row.is-selected > td) {
  background: var(--yh-highlight);
}
.dish-cell {
  display: flex;
  align-items: center;
  gap: 12px;
}
.dish-txt {
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.dish-txt b {
  color: var(--yh-title);
}
.preview {
  display: flex;
  flex-direction: column;
  gap: 14px;
  position: sticky;
  top: 0;
}
.hero {
  position: relative;
}
.hero :deep(.dish-thumb) {
  display: flex;
}
.cap {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 28px 16px 14px;
  border-radius: 0 0 14px 14px;
  background: linear-gradient(transparent, rgba(40, 26, 16, 0.72));
  color: #fff;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.cap b {
  font-size: 20px;
}
.cap span {
  font-size: 12px;
  opacity: 0.92;
}
.label {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 1px;
  color: var(--yh-body);
  margin-bottom: 8px;
}
.ings {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.ing-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 3px 10px 3px 3px;
  border-radius: 999px;
  border: 1px solid var(--yh-border);
  background: var(--yh-panel);
  font-size: 13px;
  color: var(--yh-title);
}
.steps {
  margin: 0;
  padding-left: 18px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  color: var(--yh-title);
  font-size: 13px;
  line-height: 1.6;
}
.actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
