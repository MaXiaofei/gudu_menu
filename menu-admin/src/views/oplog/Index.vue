<script setup lang="ts">
import { onMounted, ref } from 'vue'
import Pagination from '@/components/Pagination.vue'
import LoadError from '@/components/LoadError.vue'
import { listOperationLogs, type OperationLogItem } from '@/api/backoffice'
import { cleanOpLogQuery, operatorIdError } from '@/utils/backoffice'

/** 运营操作日志：封禁、解封、违规复核、注销重试，谁在何时对什么做了什么、原因。可按对象类型、操作人、时间范围筛选。 */
const pageSize = 15
const pageNum = ref(1)
const total = ref(0)
const targetType = ref('')
const operatorId = ref('')
const range = ref<[string, string] | null>(null)
const operatorErr = ref('')
const list = ref<OperationLogItem[]>([])
const loading = ref(false)
const loadError = ref('')

const TYPES = [
  { value: '', label: '全部' },
  { value: 'ACCOUNT', label: '账号' },
  { value: 'MEDIA_CHECK', label: '图片违规' },
  { value: 'TEXT_VIOLATION', label: '文本违规' },
  { value: 'DEACTIVATION', label: '注销' },
  { value: 'INGREDIENT', label: '食材收编' },
]

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const r = await listOperationLogs({
      ...cleanOpLogQuery({ targetType: targetType.value, operatorId: operatorId.value, range: range.value }),
      pageNum: pageNum.value,
      pageSize,
    })
    list.value = r?.records || []
    total.value = r?.total || 0
  } catch (e) {
    loadError.value = (e as Error)?.message || '加载失败'
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function search() {
  operatorErr.value = operatorIdError(operatorId.value) || ''
  if (operatorErr.value) return
  pageNum.value = 1
  load()
}

function reset() {
  operatorId.value = ''
  range.value = null
  targetType.value = ''
  search()
}

function pickType(v: string) {
  targetType.value = v
  search()
}

onMounted(load)
</script>

<template>
  <div class="yh-page">
    <div class="yh-head">
      <div>
        <h1>操作日志</h1>
        <div class="yh-sub">操作 · 留痕 · 只读</div>
      </div>
      <el-button @click="load">刷新</el-button>
    </div>
    <div class="yh-seg" role="group" aria-label="对象类型">
      <button v-for="t in TYPES" :key="t.value || 'all'" type="button" :class="{ on: targetType === t.value }"
        @click="pickType(t.value)">{{ t.label }}</button>
    </div>
    <el-form inline class="filters" data-testid="oplog-filters" @submit.prevent="search">
      <el-form-item label="操作人" :error="operatorErr">
        <el-input v-model="operatorId" placeholder="操作人账号 ID" clearable style="width: 150px" data-testid="oplog-operator" />
      </el-form-item>
      <el-form-item label="时间范围">
        <el-date-picker v-model="range" type="daterange" value-format="YYYY-MM-DD" range-separator="至"
          start-placeholder="开始日期" end-placeholder="结束日期" style="width: 260px" data-testid="oplog-range" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" native-type="submit">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </el-form-item>
    </el-form>
    <section class="yh-card">
      <LoadError v-if="loadError" :message="loadError" @retry="load" />
      <el-table v-else v-loading="loading" :data="list" data-testid="oplog-table">
        <el-table-column label="时间" width="150">
          <template #default="{ row }">{{ (row.createdAt || '').replace('T', ' ').slice(0, 16) }}</template>
        </el-table-column>
        <el-table-column label="操作人" width="130">
          <template #default="{ row }">{{ row.operatorName || `#${row.operatorId}` }}</template>
        </el-table-column>
        <el-table-column prop="actionLabel" label="操作" width="130" />
        <el-table-column label="对象" width="150">
          <template #default="{ row }">{{ row.targetType }} #{{ row.targetId }}</template>
        </el-table-column>
        <el-table-column label="原因" min-width="180">
          <template #default="{ row }">{{ row.reason || '—' }}</template>
        </el-table-column>
        <el-table-column label="详情" min-width="200">
          <template #default="{ row }">{{ row.detail || '—' }}</template>
        </el-table-column>
        <template #empty>
          <el-empty description="没有符合条件的操作记录" :image-size="72" />
        </template>
      </el-table>
      <Pagination :total="total" :current-page="pageNum" :page-size="pageSize" @current-change="(p: number) => { pageNum = p; load() }" />
    </section>
  </div>
</template>

<style scoped>
.yh-seg {
  margin-bottom: 12px;
}
.filters {
  margin-bottom: 4px;
}
</style>
