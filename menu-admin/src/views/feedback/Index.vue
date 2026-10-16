<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import Pagination from '@/components/Pagination.vue'
import { listFeedback, updateFeedbackStatus, type Feedback, type FeedbackStatus } from '@/api/feedback'

const pageSize = 15 // DESIGN.md §12.2 列表分页约定
const pageNum = ref(1)
const total = ref(0)
const status = ref<FeedbackStatus | ''>('NEW')
const list = ref<Feedback[]>([])
const loading = ref(false)
const loadError = ref('')
const updatingId = ref<number | null>(null)

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const res = await listFeedback({ status: status.value, pageNum: pageNum.value, pageSize })
    list.value = res?.records || []
    total.value = res?.total || 0
  } catch (e) {
    loadError.value = (e as Error)?.message || '加载失败'
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function onFilter() {
  pageNum.value = 1
  load()
}

function onPageChange(p: number) {
  pageNum.value = p
  load()
}

async function setStatus(row: Feedback, next: FeedbackStatus) {
  updatingId.value = row.id
  try {
    await updateFeedbackStatus(row.id, next)
    ElMessage.success(next === 'DONE' ? '已标记为已处理' : '已重新打开')
    // 当前筛选下该条不再属于此列表时重新拉取；否则就地更新
    if (status.value && status.value !== next) {
      if (list.value.length === 1 && pageNum.value > 1) pageNum.value -= 1
      await load()
    } else {
      row.status = next
    }
  } finally {
    updatingId.value = null
  }
}

function formatTime(s: string) {
  return (s || '').replace('T', ' ').slice(0, 16)
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="toolbar">
      <el-radio-group v-model="status" data-testid="fb-status-filter" @change="onFilter">
        <el-radio-button value="NEW">待处理</el-radio-button>
        <el-radio-button value="DONE">已处理</el-radio-button>
        <el-radio-button value="">全部</el-radio-button>
      </el-radio-group>
      <div class="spacer"></div>
      <el-button @click="load">刷新</el-button>
    </div>

    <el-table v-loading="loading" :data="list" border data-testid="fb-table">
      <el-table-column label="时间" width="150">
        <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="成员" width="120">
        <template #default="{ row }">{{ row.memberName || `#${row.memberId}` }}</template>
      </el-table-column>
      <el-table-column label="内容" min-width="260">
        <template #default="{ row }">
          <div class="content">{{ row.content }}</div>
        </template>
      </el-table-column>
      <el-table-column label="联系方式" width="140">
        <template #default="{ row }">{{ row.contact || '-' }}</template>
      </el-table-column>
      <el-table-column label="图片" width="200">
        <template #default="{ row }">
          <div v-if="row.images && row.images.length" class="thumbs">
            <el-image
              v-for="(src, i) in row.images"
              :key="src"
              :src="src"
              :preview-src-list="row.images"
              :initial-index="i"
              preview-teleported
              fit="cover"
              class="thumb"
            />
          </div>
          <span v-else class="muted">-</span>
        </template>
      </el-table-column>
      <el-table-column label="客户端" width="180" show-overflow-tooltip>
        <template #default="{ row }">{{ row.clientInfo || '-' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <span class="status" :class="row.status === 'DONE' ? 'done' : 'new'">
            {{ row.status === 'DONE' ? '已处理' : '待处理' }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.status !== 'DONE'"
            link
            type="primary"
            :loading="updatingId === row.id"
            @click="setStatus(row, 'DONE')"
          >标记已处理</el-button>
          <el-button
            v-else
            link
            :loading="updatingId === row.id"
            @click="setStatus(row, 'NEW')"
          >重新打开</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty
          :description="loadError ? `加载失败：${loadError}` : status === 'NEW' ? '没有待处理的反馈' : '暂无反馈'"
          :image-size="80"
        >
          <el-button v-if="loadError" type="primary" @click="load">重试</el-button>
        </el-empty>
      </template>
    </el-table>

    <Pagination :total="total" :page-size="pageSize" :current-page="pageNum" @current-change="onPageChange" />
  </div>
</template>

<style scoped>
.page {
  background: var(--yh-panel);
  border-radius: 12px;
  padding: 16px;
}
.toolbar {
  display: flex;
  align-items: center;
  margin-bottom: 12px;
}
.toolbar .spacer {
  flex: 1;
}
.content {
  white-space: pre-wrap;
  word-break: break-word;
  color: var(--yh-title);
  line-height: 1.5;
}
.thumbs {
  display: flex;
  gap: 6px;
}
.thumb {
  width: 48px;
  height: 48px;
  border-radius: 6px;
  border: 1px solid var(--yh-border);
}
.muted {
  color: var(--yh-caption);
}
.status {
  display: inline-block;
  font-size: 12px;
  font-weight: 700;
  padding: 2px 8px;
  border-radius: 999px;
}
.status.new {
  color: var(--yh-warning-deep);
  background: color-mix(in srgb, var(--yh-warning) 16%, transparent);
}
.status.done {
  color: var(--yh-success);
  background: color-mix(in srgb, var(--yh-success) 14%, transparent);
}
</style>
