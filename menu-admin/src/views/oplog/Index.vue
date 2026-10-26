<script setup lang="ts">
import { onMounted, ref } from 'vue'
import Pagination from '@/components/Pagination.vue'
import { listOperationLogs, type OperationLogItem } from '@/api/backoffice'

/** 运营操作日志：封禁、解封、违规复核、注销重试，谁在何时对什么做了什么、原因。 */
const pageSize = 15
const pageNum = ref(1)
const total = ref(0)
const targetType = ref('')
const list = ref<OperationLogItem[]>([])
const loading = ref(false)

const TYPES = [
  { value: '', label: '全部' },
  { value: 'ACCOUNT', label: '账号' },
  { value: 'MEDIA_CHECK', label: '图片违规' },
  { value: 'TEXT_VIOLATION', label: '文本违规' },
  { value: 'DEACTIVATION', label: '注销' },
]

async function load() {
  loading.value = true
  try {
    const r = await listOperationLogs({ targetType: targetType.value || undefined, pageNum: pageNum.value, pageSize })
    list.value = r?.records || []
    total.value = r?.total || 0
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="yh-page">
    <div class="yh-head">
      <div>
        <h1>操作日志</h1>
        <div class="yh-sub">运营后台的封禁、解封、违规复核、注销重试均留痕，不可修改</div>
      </div>
      <el-button @click="load">刷新</el-button>
    </div>
    <div class="yh-seg" role="group" aria-label="对象类型">
      <button v-for="t in TYPES" :key="t.value || 'all'" type="button" :class="{ on: targetType === t.value }"
        @click="targetType = t.value; pageNum = 1; load()">{{ t.label }}</button>
    </div>
    <section class="yh-card">
      <el-table v-loading="loading" :data="list" data-testid="oplog-table">
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
      </el-table>
      <Pagination :total="total" :current-page="pageNum" :page-size="pageSize" @current-change="(p: number) => { pageNum = p; load() }" />
    </section>
  </div>
</template>

<style scoped>
.yh-seg {
  margin-bottom: 12px;
}
</style>
