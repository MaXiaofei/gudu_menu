<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listDictPaged,
  createDict,
  updateDict,
  deleteDict,
  listNutritionMetrics,
  type DictItem,
  type DictSaveDTO,
  type NutritionMetric,
} from '@/api/dict'

type GroupKey =
  | 'cuisine'
  | 'tag'
  | 'category'
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
  { key: 'category', title: '菜品分类' },
  { key: 'menu_type', title: '菜单类型' },
  { key: 'audience', title: '适用人群' },
  { key: 'unit', title: '计量单位' },
  { key: 'purchase_category', title: '采购分类' },
  { key: 'role', title: '成员角色' },
]

const activeTab = ref<string>('cuisine')
const loading = ref(false)
const pageSize = 10

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
// 营养指标数据
const metrics = ref<NutritionMetric[]>([])

async function loadGroup(group: string) {
  loading.value = true
  try {
    const st = dictMap[group] || { records: [], total: 0, pageNum: 1 }
    const page = await listDictPaged(group, { pageNum: st.pageNum, pageSize })
    dictMap[group] = { records: page.records || [], total: page.total || 0, pageNum: st.pageNum }
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
  metrics.value = await listNutritionMetrics()
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

async function onSubmit() {
  if (!form.name.trim()) {
    ElMessage.warning('请填写名称')
    return
  }
  const group = activeTab.value as GroupKey
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
  await ElMessageBox.confirm(`确定删除「${row.name}」？`, '提示', { type: 'warning' })
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
        <div class="tab-toolbar">
          <el-button type="primary" @click="openCreate">新增{{ g.title }}</el-button>
        </div>
        <el-table v-loading="loading" :data="(dictMap[g.key] && dictMap[g.key].records) || []" border size="default">
          <el-table-column label="名称" prop="name" min-width="180" />
          <el-table-column label="排序" prop="sort" width="100" />
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button link type="danger" @click="onDelete(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          background
          layout="total, prev, pager, next, jumper"
          :total="(dictMap[g.key] && dictMap[g.key].total) || 0"
          :page-size="pageSize"
          :current-page="(dictMap[g.key] && dictMap[g.key].pageNum) || 1"
          @current-change="(p: number) => onPageChange(g.key, p)"
          style="margin-top: 16px; justify-content: flex-end; display: flex"
        />
      </el-tab-pane>

      <el-tab-pane label="营养指标" name="nutrition">
        <el-table v-loading="loading" :data="metrics" border size="default">
          <el-table-column label="指标名称" min-width="160">
            <template #default="{ row }">{{ metricDisplayName(row.name) }}</template>
          </el-table-column>
          <el-table-column label="单位" prop="unit" width="120" />
          <el-table-column label="分组" prop="metricGroup" width="160" />
        </el-table>
        <div class="hint">营养指标由后端维护，此处仅查看。</div>
      </el-tab-pane>
    </el-tabs>

    <el-dialog
      v-model="dialogVisible"
      :title="editing ? '编辑' : '新增'"
      width="420px"
    >
      <el-form label-width="80px">
        <el-form-item label="名称">
          <el-input v-model="form.name" placeholder="请输入名称" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="onSubmit">保存</el-button>
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
.tab-toolbar {
  margin-bottom: 12px;
  display: flex;
  justify-content: flex-end;
}
.hint {
  margin-top: 10px;
  font-size: 12px;
  color: #9a8f80;
}
</style>
