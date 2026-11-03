<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getDishDetail, type DishDetail } from '@/api/dish'
import { listLibrary, offlineDish, onlineDish, type LibraryRow, type LibraryStatus } from '@/api/dishLibrary'
import { LIBRARY_STATUS_TABS, offlineReasonError, REASON_MAX } from '@/utils/dishLibrary'
import { listIngredients, type Ingredient } from '@/api/ingredient'
import { formatCount } from '@/utils/dashboard'
import Pagination from '@/components/Pagination.vue'
import DishThumb from '@/components/DishThumb.vue'
import IngIcon from '@/components/IngIcon.vue'
import LoadError from '@/components/LoadError.vue'

/**
 * 公共菜谱库（原型 AdminLibrary）：只列公共菜谱（/admin/dish-library），可按上架状态筛选，
 * 下架（弹窗填原因 + 二次确认）/ 重新上架。下架后新入口（搜索 / 推荐 / 加入食集 / 餐程 / 咕嘟推荐）隐藏，家庭已用的保留。
 * 投稿审核（待审核 / 已驳回）后端未做，不显示。
 */
const STEP_PREVIEW = 4

const router = useRouter()
const loading = ref(false)
const loadError = ref('')
const detailError = ref(false)
const stepsExpanded = ref(false)
const list = ref<LibraryRow[]>([])
const total = ref(0)
const query = reactive<{ keyword: string; status: LibraryStatus; pageNum: number; pageSize: number }>({
  keyword: '', status: '', pageNum: 1, pageSize: 15,
})

const selectedId = ref<number | null>(null)
const detail = ref<DishDetail | null>(null)
const detailLoading = ref(false)
const ingredientById = ref<Map<number, Ingredient>>(new Map())

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const page = await listLibrary({ keyword: query.keyword || undefined, status: query.status || undefined, pageNum: query.pageNum, pageSize: query.pageSize })
    list.value = Array.isArray(page?.records) ? page.records : []
    total.value = page?.total || 0
    if (!list.value.some((d) => d.id === selectedId.value)) {
      if (list.value[0]) select(list.value[0])
      else clearSelection()
    }
  } catch (e) {
    loadError.value = (e as Error)?.message || '加载失败'
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function clearSelection() {
  selectedId.value = null
  detail.value = null
}

async function select(row: LibraryRow) {
  selectedId.value = row.id
  stepsExpanded.value = false
  detailError.value = false
  detailLoading.value = true
  try {
    detail.value = await getDishDetail(row.id)
  } catch {
    detail.value = null
    detailError.value = true
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

function pickStatus(v: LibraryStatus) {
  query.status = v
  onSearch()
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
const shownSteps = computed(() => (stepsExpanded.value ? steps.value : steps.value.slice(0, STEP_PREVIEW)))

function sourceText(d: LibraryRow) {
  if (d.sourceName) return `导入 · ${d.sourceName}`
  if (d.source === 'IMPORT') return '链接导入'
  return '官方 / 家庭录入'
}

function minutes(d: { prepTime?: number | null; cookTime?: number | null }) {
  const m = (d.prepTime ?? 0) + (d.cookTime ?? 0)
  return m > 0 ? `${m} 分钟` : '—'
}

function formatDate(s?: string | null) {
  return (s || '').replace('T', ' ').slice(0, 10) || '—'
}

function rowClass({ row }: { row: LibraryRow }) {
  return row.id === selectedId.value ? 'is-selected' : ''
}

// ---- 下架 / 重新上架 ----
const offlineVisible = ref(false)
const offlineReason = ref('')
const offlineError = ref('')
const submitting = ref(false)

function openOffline() {
  offlineReason.value = ''
  offlineError.value = ''
  offlineVisible.value = true
}

async function submitOffline() {
  const row = selectedRow.value
  if (!row) return
  offlineError.value = offlineReasonError(offlineReason.value)
  if (offlineError.value) return
  try {
    await ElMessageBox.confirm(
      `确认下架「${row.name}」？下架后用户端搜不到、不能加入食集或照着做；已加入食集 / 做过 / 收藏的家庭不受影响。`,
      '二次确认',
      { type: 'warning', confirmButtonText: '确认下架', cancelButtonText: '再想想' },
    )
  } catch {
    return
  }
  submitting.value = true
  try {
    await offlineDish(row.id, offlineReason.value.trim())
    ElMessage.success('已下架')
    offlineVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error((e as Error)?.message || '下架失败')
  } finally {
    submitting.value = false
  }
}

async function goOnline() {
  const row = selectedRow.value
  if (!row) return
  try {
    await ElMessageBox.confirm(`确认重新上架「${row.name}」？上架后用户端可重新搜到。`, '重新上架', {
      type: 'warning', confirmButtonText: '确认上架', cancelButtonText: '取消',
    })
  } catch {
    return
  }
  submitting.value = true
  try {
    await onlineDish(row.id)
    ElMessage.success('已重新上架')
    await load()
  } catch (e) {
    ElMessage.error((e as Error)?.message || '上架失败')
  } finally {
    submitting.value = false
  }
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
        <div class="yh-sub">公共菜谱 · 来源 · 用料步骤</div>
      </div>
      <el-button type="primary" @click="router.push('/dish')">去菜谱管理</el-button>
    </div>

    <div class="yh-seg" role="group" aria-label="上架状态" data-testid="lib-tabs">
      <button v-for="t in LIBRARY_STATUS_TABS" :key="t.value || 'all'" type="button" :class="{ on: query.status === t.value }"
        :data-testid="`lib-tab-${t.value || 'all'}`" @click="pickStatus(t.value)">{{ t.label }}</button>
    </div>

    <div class="toolbar">
      <span class="count" data-testid="lib-count">共 {{ formatCount(total) }} 道菜谱</span>
      <el-input v-model="query.keyword" placeholder="搜菜名" clearable class="kw" @keyup.enter="onSearch" @clear="onSearch" />
      <el-button @click="onSearch">搜索</el-button>
    </div>

    <div class="split">
      <section class="yh-card table-card">
        <LoadError v-if="loadError" :message="loadError" @retry="load" />
        <el-table v-else v-loading="loading" :data="list" :row-class-name="rowClass" data-testid="lib-table" @row-click="select">
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
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag v-if="row.offline" type="danger" size="small" effect="light" data-testid="lib-status-offline">已下架</el-tag>
              <el-tag v-else type="success" size="small" effect="light">已上架</el-tag>
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

          <div v-if="selectedRow.offline" class="offline-note" data-testid="lib-offline-note">
            已下架 · {{ formatDate(selectedRow.offlineAt) }}<template v-if="selectedRow.offlineReason">　原因：{{ selectedRow.offlineReason }}</template>
          </div>

          <el-skeleton v-if="detailLoading" :rows="4" animated />
          <LoadError v-else-if="detailError" compact title="菜谱详情加载失败" @retry="select(selectedRow)" />
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
                <li v-for="(s, i) in shownSteps" :key="i">{{ s }}</li>
              </ol>
              <el-button v-if="steps.length > STEP_PREVIEW" link type="primary" data-testid="lib-steps-toggle" @click="stepsExpanded = !stepsExpanded">
                {{ stepsExpanded ? '收起' : `展开全部（还有 ${steps.length - STEP_PREVIEW} 步）` }}
              </el-button>
            </div>
          </template>

          <div class="actions">
            <el-button v-if="!selectedRow.offline" type="danger" plain :loading="submitting" data-testid="lib-offline-btn" @click="openOffline">下架</el-button>
            <el-button v-else type="success" plain :loading="submitting" data-testid="lib-online-btn" @click="goOnline">重新上架</el-button>
            <el-button type="primary" @click="router.push('/dish')">编辑菜谱</el-button>
          </div>
        </template>
        <el-empty v-else description="选择左侧菜谱查看" :image-size="72" />
      </section>
    </div>

    <el-dialog v-model="offlineVisible" title="下架公共菜谱" width="460px" data-testid="lib-offline-dialog">
      <p class="dlg-tip">下架后用户端搜索、推荐、加入食集、一周餐程、咕嘟推荐、照着做都不再出现这道菜；已经加入食集 / 餐程 / 做菜记录 / 收藏的家庭照常可用。</p>
      <el-form label-position="top" @submit.prevent>
        <el-form-item label="下架原因（必填，写入操作日志）" :error="offlineError">
          <el-input v-model="offlineReason" type="textarea" :rows="3" :maxlength="REASON_MAX" show-word-limit
            placeholder="例如：图片侵权 / 做法有误" data-testid="lib-offline-reason" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="offlineVisible = false">取消</el-button>
        <el-button type="danger" :loading="submitting" data-testid="lib-offline-submit" @click="submitOffline">下架</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.yh-seg {
  margin-bottom: 12px;
}
.offline-note {
  padding: 8px 12px;
  border-radius: 8px;
  background: var(--el-color-danger-light-9);
  color: var(--el-color-danger);
  font-size: 13px;
}
.dlg-tip {
  margin: 0 0 12px;
  color: var(--yh-body);
  font-size: 13px;
  line-height: 1.6;
}
.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.kw {
  width: 240px;
}
.count {
  color: var(--yh-body);
  font-size: 13px;
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
