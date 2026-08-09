<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listGrouped,
  setLevel,
  deleteLevel,
  levelLabel,
  levelTagType,
  type PantryGroupedItem,
} from '@/api/pantry'
import { listIngredients } from '@/api/ingredient'

const loading = ref(false)
const allList = ref<PantryGroupedItem[]>([])
const keyword = ref('')
const pageNum = ref(1)
const pageSize = 15

// 食材名称过滤（前端本地，数据量小）
const filteredList = computed<PantryGroupedItem[]>(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return allList.value
  return allList.value.filter((p) => (p.ingredientName || `#${p.ingredientId}`).toLowerCase().includes(kw))
})

const list = computed<PantryGroupedItem[]>(() => {
  const start = (pageNum.value - 1) * pageSize
  return filteredList.value.slice(start, start + pageSize)
})

const total = computed(() => filteredList.value.length)
watch(keyword, () => {
  pageNum.value = 1
})

async function load() {
  loading.value = true
  try {
    const vo = await listGrouped()
    allList.value = vo.items || []
    pageNum.value = 1
  } finally {
    loading.value = false
  }
}

function onSearch() {
  pageNum.value = 1
}

function onPageChange(p: number) {
  pageNum.value = p
}

// 档位选择（V42：充足/不足/用完，不再填数量/单位/过期日）
const LEVEL_OPTIONS = [
  { value: 'ENOUGH', label: '充足', desc: '还有不少' },
  { value: 'LOW', label: '不足', desc: '剩一点点' },
  { value: 'NONE', label: '用完', desc: '用光了' },
]

const ingredients = ref<{ id: number; name: string }[]>([])

async function loadOptions() {
  const ings = await listIngredients()
  ingredients.value = ings.map((x) => ({ id: x.id, name: x.name }))
}

onMounted(() => {
  load()
  loadOptions()
})

// ============ 新增/编辑（设档位） ============
const dialogVisible = ref(false)
const editing = ref<PantryGroupedItem | null>(null)
const form = reactive({ ingredientId: undefined as number | undefined, level: 'ENOUGH' })

function openCreate() {
  editing.value = null
  form.ingredientId = undefined
  form.level = 'ENOUGH'
  dialogVisible.value = true
}

function openEdit(row: PantryGroupedItem) {
  editing.value = row
  form.ingredientId = row.ingredientId
  form.level = row.level || 'ENOUGH'
  dialogVisible.value = true
}

async function onSubmit() {
  if (!form.ingredientId) {
    ElMessage.warning('请选择食材')
    return
  }
  try {
    await setLevel(form.ingredientId, form.level, editing.value ? '管理后台修改' : '管理后台建档')
    ElMessage.success(editing.value ? '已更新档位' : '已建档')
    dialogVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error(`保存失败：${(e as Error).message || e}`)
  }
}

async function onDelete(row: PantryGroupedItem) {
  await ElMessageBox.confirm(
    `确定删除「${row.ingredientName || `#${row.ingredientId}`}」的库存档位？删除后该食材回到「没建档」。`,
    '提示',
    { type: 'warning' },
  )
  await deleteLevel(row.ingredientId)
  ElMessage.success('已删除')
  await load()
}

// 上次变动文案（来源 + 时间）
function lastChangeText(row: PantryGroupedItem): string {
  const lc = row.lastChange
  if (!lc) return '无变动记录'
  const sourceMap: Record<string, string> = {
    cook: '做菜·用完了',
    cook_partial: '做菜·用了一些',
    purchase: '采购入库',
    manual: '手动',
    undo: '撤回入库',
  }
  const source = sourceMap[lc.source || ''] || lc.source || ''
  const note = lc.sourceNote ? ` · ${lc.sourceNote}` : ''
  const time = lc.createTime ? ` · ${lc.createTime.slice(0, 16).replace('T', ' ')}` : ''
  return `${source}${note}${time}`
}
</script>

<template>
  <div class="page">
    <div class="toolbar">
      <el-input
        v-model="keyword"
        placeholder="食材名称搜索"
        clearable
        class="filter-input"
        @keyup.enter="onSearch"
      />
      <el-button @click="onSearch">搜索</el-button>
      <div class="spacer" />
      <el-button type="primary" @click="openCreate">新增库存档位</el-button>
    </div>
    <el-table v-loading="loading" :data="list" border>
      <el-table-column label="食材" min-width="160">
        <template #default="{ row }">
          {{ row.ingredientName || `#${row.ingredientId}` }}
        </template>
      </el-table-column>
      <el-table-column label="档位" width="120">
        <template #default="{ row }">
          <el-tag :type="levelTagType(row.level)" effect="light">{{ levelLabel(row.level) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="上次变动" min-width="240">
        <template #default="{ row }">
          <span class="mini">{{ lastChangeText(row) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">改档位</el-button>
          <el-button link type="danger" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <Pagination
      :total="total"
      :page-size="pageSize"
      :current-page="pageNum"
      @current-change="onPageChange"
    />

    <el-dialog v-model="dialogVisible" :title="editing ? '改档位' : '新增库存档位'" width="420px">
      <el-form label-width="90px">
        <el-form-item label="食材">
          <el-select
            v-model="form.ingredientId"
            filterable
            placeholder="选择食材"
            style="width: 100%"
            :disabled="!!editing"
          >
            <el-option
              v-for="i in ingredients"
              :key="i.id"
              :label="i.name"
              :value="i.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="档位">
          <el-radio-group v-model="form.level">
            <el-radio-button v-for="o in LEVEL_OPTIONS" :key="o.value" :value="o.value">
              {{ o.label }}
            </el-radio-button>
          </el-radio-group>
          <div class="mini" style="margin-top: 6px">
            {{
              LEVEL_OPTIONS.find((o) => o.value === form.level)?.desc
            }}（库存是模糊档位，不填数量/单位/过期日）
          </div>
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
.mini {
  font-size: 12px;
  color: #7a6f60;
}
</style>
