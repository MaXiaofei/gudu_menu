<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listByGroup,
  createNutritionMetric,
  updateNutritionMetric,
  listDictPaged,
  createDict,
  updateDict,
  deleteDict,
  listNutritionMetrics,
  type DictItem,
  type DictSaveDTO,
  type NutritionMetric,
} from '@/api/dict'
import Pagination from '@/components/Pagination.vue'
import LoadError from '@/components/LoadError.vue'
import { useAuthStore } from '@/store/auth'
import {
  METRIC_GROUPS, METRIC_UNITS, dictDeleteWarning, validateDictName, validateMetric,
} from '@/utils/dict'

const auth = useAuthStore()
/** 营养指标写接口后端仅超管可调：运营只读。 */
const canEditMetric = computed(() => auth.isSuperAdmin)

type GroupKey =
  | 'cuisine'
  | 'tag'
  | 'menu_type'
  | 'audience'
  | 'unit'
  | 'purchase_category'
  | 'role'

interface GroupTab {
  key: GroupKey
  title: string
}

const groupTabs: GroupTab[] = [
  { key: 'cuisine', title: '菜系' },
  { key: 'tag', title: '标签' },
  { key: 'menu_type', title: '菜单类型' },
  { key: 'audience', title: '适用人群' },
  { key: 'unit', title: '计量单位' },
  { key: 'purchase_category', title: '采购分类' },
  { key: 'role', title: '成员角色' },
]

const activeTab = ref<string>('cuisine')
const loading = ref(false)
const groupError = reactive<Record<string, string>>({})
const metricError = ref('')
const pageSize = 15

// 营养指标 name(英文, DB/营养 EAV 兼容 key) → 中文展示名
// DB 仍存英文，前端展示时映射；未命中时回退为原 name。
const METRIC_CN: Record<string, string> = {
  calorie: '热量',
  protein: '蛋白质',
  fat: '脂肪',
  carb: '碳水',
  sugar: '糖',
  gi: '升糖指数',
}

function metricDisplayName(name: string): string {
  return METRIC_CN[name] ?? name
}

interface GroupState {
  records: DictItem[]
  total: number
  pageNum: number
}

// 各 group 的分页状态
const dictMap = reactive<Record<string, GroupState>>({})
// 各 group 的搜索关键词（前端本地过滤，数据量小）
const groupKeyword = reactive<Record<string, string>>({})
// 营养指标数据
const metrics = ref<NutritionMetric[]>([])

// 按 group 取过滤后的列表（名称 includes，不区分大小写）
function filteredRecords(group: string): DictItem[] {
  const st = dictMap[group]
  if (!st) return []
  const kw = (groupKeyword[group] || '').trim().toLowerCase()
  if (!kw) return st.records
  return st.records.filter((r) => (r.name || '').toLowerCase().includes(kw))
}

// 过滤后用于表格展示的当前页数据
function pagedRecords(group: string): DictItem[] {
  const rows = filteredRecords(group)
  const st = dictMap[group]
  const start = ((st?.pageNum || 1) - 1) * pageSize
  return rows.slice(start, start + pageSize)
}

// 过滤后总数
function filteredTotal(group: string): number {
  return filteredRecords(group).length
}

function onSearch(group: string) {
  if (!dictMap[group]) dictMap[group] = { records: [], total: 0, pageNum: 1 }
  dictMap[group].pageNum = 1
}

async function loadGroup(group: string) {
  loading.value = true
  groupError[group] = ''
  const st = dictMap[group] || { records: [], total: 0, pageNum: 1 }
  try {
    const page = await listDictPaged(group, { pageNum: st.pageNum, pageSize })
    dictMap[group] = { records: page.records || [], total: page.total || 0, pageNum: st.pageNum }
  } catch (e) {
    groupError[group] = (e as Error)?.message || '加载失败'
    dictMap[group] = { records: [], total: 0, pageNum: st.pageNum }
  } finally {
    loading.value = false
  }
}

function onPageChange(group: string, p: number) {
  if (!dictMap[group]) dictMap[group] = { records: [], total: 0, pageNum: 1 }
  dictMap[group].pageNum = p
  loadGroup(group)
}

async function loadMetrics() {
  metricError.value = ''
  try {
    metrics.value = await listNutritionMetrics()
  } catch (e) {
    metricError.value = (e as Error)?.message || '加载失败'
    metrics.value = []
  }
}

async function onTabChange(tab: string) {
  if (tab === 'nutrition') {
    await loadMetrics()
  } else if (!dictMap[tab]) {
    await loadGroup(tab)
  }
}

onMounted(() => {
  loadGroup(activeTab.value)
  loadMetrics()
})

// ===== 营养指标新增/编辑（仅超管）=====
const metricDialog = reactive({ visible: false, editing: null as NutritionMetric | null, saving: false })
const metricForm = reactive({ name: '', unit: 'g', metricGroup: 'macro', sort: 0 })

function openMetric(row: NutritionMetric | null) {
  metricDialog.editing = row
  metricDialog.saving = false
  metricForm.name = row?.name ?? ''
  metricForm.unit = row?.unit ?? 'g'
  metricForm.metricGroup = row?.metricGroup ?? 'macro'
  metricForm.sort = row?.sort ?? 0
  metricDialog.visible = true
}

async function onSubmitMetric() {
  if (!canEditMetric.value) return
  const err = validateMetric(metricForm, metrics.value.map((m) => m.name), metricDialog.editing?.name)
  if (err) {
    ElMessage.warning(err)
    return
  }
  metricDialog.saving = true
  try {
    const dto = { name: metricForm.name.trim(), unit: metricForm.unit.trim(), metricGroup: metricForm.metricGroup, sort: metricForm.sort }
    if (metricDialog.editing) await updateNutritionMetric({ ...dto, id: metricDialog.editing.id })
    else await createNutritionMetric(dto)
    ElMessage.success('已保存')
    metricDialog.visible = false
    await loadMetrics()
  } finally {
    metricDialog.saving = false
  }
}

// ===== 字典新增/编辑对话框 =====
const dialogVisible = ref(false)
const editing = ref<DictItem | null>(null)
const form = reactive<{ name: string; sort: number }>({ name: '', sort: 0 })

function openCreate() {
  editing.value = null
  form.name = ''
  form.sort = 0
  dialogVisible.value = true
}

function openEdit(row: DictItem) {
  editing.value = row
  form.name = row.name
  form.sort = row.sort
  dialogVisible.value = true
}

const submitting = ref(false)

async function onSubmit() {
  const group = activeTab.value as GroupKey
  // 重名校验取同分组全量（列表只是当前页），取不到时仍交给后端唯一约束兜底
  let existing: string[] = (dictMap[group]?.records || []).map((r) => r.name)
  try {
    existing = (await listByGroup(group)).map((r) => r.name)
  } catch {
    // 用当前页名称做校验
  }
  const err = validateDictName(form.name, existing, editing.value?.name)
  if (err) {
    ElMessage.warning(err)
    return
  }
  submitting.value = true
  try {
    await save(group)
  } finally {
    submitting.value = false
  }
}

async function save(group: GroupKey) {
  if (editing.value) {
    const dto: DictSaveDTO = {
      id: editing.value.id,
      dictGroup: group,
      name: form.name.trim(),
      sort: form.sort,
    }
    await updateDict(dto)
    ElMessage.success('已更新')
  } else {
    const dto: DictSaveDTO = { dictGroup: group, name: form.name.trim(), sort: form.sort }
    await createDict(dto)
    ElMessage.success('已新增')
  }
  dialogVisible.value = false
  await loadGroup(group)
}

async function onDelete(row: DictItem) {
  await ElMessageBox.confirm(dictDeleteWarning(activeTab.value, row.name), '删除确认', { type: 'warning', confirmButtonText: '确认删除' })
  await deleteDict(row.id)
  ElMessage.success('已删除')
  await loadGroup(activeTab.value as GroupKey)
}
</script>

<template>
  <div class="page">
    <el-tabs v-model="activeTab" type="card" @tab-change="onTabChange">
      <el-tab-pane
        v-for="g in groupTabs"
        :key="g.key"
        :label="g.title"
        :name="g.key"
      >
        <div class="toolbar">
          <el-input
            v-model="groupKeyword[g.key]"
            placeholder="名称搜索"
            clearable
            class="filter-input"
            @keyup.enter="onSearch(g.key)"
          />
          <el-button type="primary" @click="onSearch(g.key)">搜索</el-button>
          <div class="spacer" />
          <el-button type="primary" @click="openCreate">新增{{ g.title }}</el-button>
        </div>
        <LoadError v-if="groupError[g.key]" :message="groupError[g.key]" @retry="loadGroup(g.key)" />
        <el-table v-else v-loading="loading" :data="pagedRecords(g.key)" border size="default">
          <el-table-column label="名称" prop="name" min-width="180" />
          <el-table-column label="排序" prop="sort" width="100" />
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button link type="danger" @click="onDelete(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <Pagination
          :total="filteredTotal(g.key)"
          :page-size="pageSize"
          :current-page="(dictMap[g.key] && dictMap[g.key].pageNum) || 1"
          @current-change="(p: number) => onPageChange(g.key, p)"
        />
      </el-tab-pane>

      <el-tab-pane label="营养指标" name="nutrition">
        <div v-if="canEditMetric" class="toolbar">
          <div class="spacer" />
          <el-button type="primary" data-testid="metric-add" @click="openMetric(null)">新增指标</el-button>
        </div>
        <LoadError v-if="metricError" :message="metricError" @retry="loadMetrics" />
        <el-table v-else v-loading="loading" :data="metrics" border size="default">
          <el-table-column label="指标名称" min-width="160">
            <template #default="{ row }">{{ metricDisplayName(row.name) }}</template>
          </el-table-column>
          <el-table-column label="单位" prop="unit" width="120" />
          <el-table-column label="分组" prop="metricGroup" width="160" />
          <el-table-column v-if="canEditMetric" label="操作" width="100" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" data-testid="metric-edit" @click="openMetric(row)">编辑</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="hint">{{ canEditMetric ? '营养指标仅超级管理员可新增 / 修改；指标标识创建后不可改，也不提供删除（已有食材营养数据按它存储）。' : '营养指标仅超级管理员可修改，此处仅查看。' }}</div>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="metricDialog.visible" :title="metricDialog.editing ? '编辑营养指标' : '新增营养指标'" width="420px">
      <el-form label-width="80px">
        <el-form-item label="标识">
          <el-input v-model="metricForm.name" :disabled="!!metricDialog.editing" placeholder="英文，如 fiber" maxlength="32" data-testid="metric-name" />
        </el-form-item>
        <el-form-item label="单位">
          <el-select v-model="metricForm.unit" filterable allow-create default-first-option placeholder="选择或输入" data-testid="metric-unit">
            <el-option v-for="u in METRIC_UNITS" :key="u" :label="u" :value="u" />
          </el-select>
        </el-form-item>
        <el-form-item label="分组">
          <el-select v-model="metricForm.metricGroup" placeholder="选择分组" data-testid="metric-group">
            <el-option v-for="g in METRIC_GROUPS" :key="g.value" :label="g.label" :value="g.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="metricForm.sort" :min="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="metricDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="metricDialog.saving" data-testid="metric-save" @click="onSubmitMetric">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="dialogVisible"
      :title="editing ? '编辑' : '新增'"
      width="420px"
    >
      <el-form label-width="80px">
        <el-form-item label="名称">
          <el-input v-model="form.name" placeholder="请输入名称" maxlength="64" show-word-limit data-testid="dict-name" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" data-testid="dict-save" @click="onSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page {
  background: var(--yh-panel, #fff);
  padding: 16px;
  border-radius: 8px;
}
.toolbar {
  margin-bottom: 16px;
  display: flex;
  align-items: center;
  gap: 12px;
}
.filter-input {
  width: 240px;
}
.spacer {
  flex: 1;
}
.hint {
  margin-top: 10px;
  font-size: 12px;
  color: #9a8f80;
}
</style>
