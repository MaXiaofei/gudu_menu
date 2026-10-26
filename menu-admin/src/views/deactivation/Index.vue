<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import Pagination from '@/components/Pagination.vue'
import { listDeactivations, retryDeactivation, type DeactivationItem } from '@/api/backoffice'
import { DEACTIVATION_STATUS, daysLeft, deactivationOverdue } from '@/utils/backoffice'

/**
 * 注销处理（mini-release-compliance F.2）：用户注销后「账号匿名化与退出家庭」同步完成，
 * 其余个人数据（反馈联系方式等）随后匿名化；任一步失败可重试。隐私政策承诺 15 个工作日内完成，超期高亮。
 */
const pageSize = 15
const pageNum = ref(1)
const total = ref(0)
const overdueTotal = ref(0)
const status = ref('')
const overdueOnly = ref(false)
const list = ref<DeactivationItem[]>([])
const loading = ref(false)
const retryingId = ref<number | null>(null)

const TABS = [
  { value: '', label: '全部' },
  { value: 'FAILED', label: '失败待重试' },
  { value: 'PENDING', label: '处理中' },
  { value: 'DONE', label: '已完成' },
]

async function load() {
  loading.value = true
  try {
    const res = await listDeactivations({ status: status.value || undefined, overdue: overdueOnly.value || undefined, pageNum: pageNum.value, pageSize })
    list.value = Array.isArray(res?.records) ? res.records : []
    total.value = res?.total || 0
    overdueTotal.value = res?.overdueTotal || 0
  } catch {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function onFilter(v: string) {
  status.value = v
  pageNum.value = 1
  load()
}

async function retry(row: DeactivationItem) {
  retryingId.value = row.id
  try {
    const r = await retryDeactivation(row.id)
    ElMessage[r.status === 'DONE' ? 'success' : 'warning'](r.status === 'DONE' ? '已完成匿名化' : `仍有步骤失败：${r.lastError || ''}`)
    await load()
  } finally {
    retryingId.value = null
  }
}

function rowClass({ row }: { row: DeactivationItem }) {
  return deactivationOverdue(row) ? 'is-overdue' : ''
}

function formatTime(s: string | null | undefined) {
  return (s || '').replace('T', ' ').slice(0, 16) || '—'
}

function leftText(row: DeactivationItem) {
  if (row.status === 'DONE') return '—'
  const d = daysLeft(row.deadline)
  if (d === null) return '—'
  return d > 0 ? `剩 ${d} 天` : `已超期 ${-d} 天`
}

onMounted(load)
</script>

<template>
  <div class="yh-page">
    <div class="yh-head">
      <div>
        <h1>注销处理</h1>
        <div class="yh-sub">隐私政策承诺注销后 15 个工作日内删除或匿名化；超期未完成的记录标红</div>
      </div>
      <el-button @click="load">刷新</el-button>
    </div>

    <el-alert v-if="overdueTotal > 0" type="error" show-icon :closable="false" data-testid="deact-overdue-alert"
      :title="`有 ${overdueTotal} 条注销超过 15 个工作日仍未完成，请尽快处理`" />

    <div class="toolbar">
      <div class="yh-seg" role="group" aria-label="处理状态">
        <button v-for="t in TABS" :key="t.value || 'all'" type="button" :class="{ on: status === t.value }" @click="onFilter(t.value)">{{ t.label }}</button>
      </div>
      <el-checkbox v-model="overdueOnly" @change="onFilter(status)">只看超期</el-checkbox>
    </div>

    <section class="yh-card">
      <el-table v-loading="loading" :data="list" :row-class-name="rowClass" data-testid="deact-table">
        <el-table-column prop="accountId" label="账号 ID" width="90" />
        <el-table-column label="申请时间" width="150">
          <template #default="{ row }">{{ formatTime(row.requestedAt) }}</template>
        </el-table-column>
        <el-table-column label="截止" width="160">
          <template #default="{ row }">
            <div>{{ formatTime(row.deadline).slice(0, 10) }}</div>
            <span :class="{ 'txt-danger': deactivationOverdue(row) }">{{ leftText(row) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="DEACTIVATION_STATUS[row.status]?.type || 'info'" size="small">{{ DEACTIVATION_STATUS[row.status]?.label || row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="匿名化结果" min-width="300">
          <template #default="{ row }">
            <div v-for="s in row.steps" :key="s.name" class="step">
              <span :class="s.ok ? 'txt-ok' : 'txt-danger'">{{ s.ok ? '✓' : '✗' }}</span>
              {{ s.name }}<span v-if="s.detail" class="yh-muted">（{{ s.detail }}）</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="尝试" width="70">
          <template #default="{ row }">{{ row.attempts }}</template>
        </el-table-column>
        <el-table-column label="完成时间" width="150">
          <template #default="{ row }">{{ formatTime(row.completedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status !== 'DONE'" link type="primary" :loading="retryingId === row.id" data-testid="deact-retry" @click="retry(row)">重试</el-button>
          </template>
        </el-table-column>
      </el-table>
      <Pagination :total="total" :current-page="pageNum" :page-size="pageSize" @current-change="(p: number) => { pageNum = p; load() }" />
    </section>
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  gap: 16px;
  align-items: center;
}
.step {
  line-height: 1.7;
}
.txt-ok {
  color: var(--el-color-success);
}
.txt-danger {
  color: var(--el-color-danger);
}
:deep(.is-overdue) td {
  background: var(--el-color-danger-light-9) !important;
}
</style>
