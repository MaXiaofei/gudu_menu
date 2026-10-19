<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import Pagination from '@/components/Pagination.vue'
import AvatarStack from '@/components/AvatarStack.vue'
import { listFeedback, updateFeedbackStatus, type Feedback, type FeedbackStatus } from '@/api/feedback'
import { useBadgeStore } from '@/store/badges'
import { feedbackTitle, platformOf, relativeTime } from '@/utils/feedback'

const pageSize = 15 // DESIGN.md §12.2 列表分页约定
const pageNum = ref(1)
const total = ref(0)
const status = ref<FeedbackStatus | ''>('NEW')
const list = ref<Feedback[]>([])
const loading = ref(false)
const loadError = ref('')
const updatingId = ref<number | null>(null)
const selectedId = ref<number | null>(null)
const badgeStore = useBadgeStore()

const STATUS_TABS: { value: FeedbackStatus | ''; label: string }[] = [
  { value: 'NEW', label: '待处理' },
  { value: 'DONE', label: '已处理' },
  { value: '', label: '全部' },
]

const selected = computed(() => list.value.find((f) => f.id === selectedId.value) ?? null)

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const res = await listFeedback({ status: status.value, pageNum: pageNum.value, pageSize })
    list.value = Array.isArray(res?.records) ? res.records : []
    total.value = res?.total || 0
    if (!list.value.some((f) => f.id === selectedId.value)) selectedId.value = list.value[0]?.id ?? null
  } catch (e) {
    loadError.value = (e as Error)?.message || '加载失败'
    list.value = []
    total.value = 0
    selectedId.value = null
  } finally {
    loading.value = false
  }
}

function onFilter(next: FeedbackStatus | '') {
  if (status.value === next) return
  status.value = next
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
    badgeStore.setFeedbackOpen(badgeStore.feedbackOpen + (next === 'DONE' ? -1 : 1))
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

function who(f: Feedback) {
  return f.memberName || `#${f.memberId}`
}

function images(f: Feedback): string[] {
  return Array.isArray(f.images) ? f.images.filter(Boolean) : []
}

function rowClass({ row }: { row: Feedback }) {
  return row.id === selectedId.value ? 'is-selected' : ''
}

onMounted(load)
</script>

<template>
  <div class="yh-page">
    <div class="yh-head">
      <div>
        <h1>意见反馈</h1>
        <div class="yh-sub">用户在 App / 小程序「意见反馈」提交的内容；处理结果暂不通知用户（消息回复接口待上线）</div>
      </div>
      <el-button @click="load">刷新</el-button>
    </div>

    <div class="toolbar">
      <div class="yh-seg" role="group" aria-label="处理状态" data-testid="fb-status-filter">
        <button
          v-for="t in STATUS_TABS"
          :key="t.value || 'all'"
          type="button"
          :class="{ on: status === t.value }"
          :aria-pressed="status === t.value"
          @click="onFilter(t.value)"
        >{{ t.value === 'NEW' && badgeStore.feedbackOpen ? `${t.label} ${badgeStore.feedbackOpen}` : t.label }}</button>
      </div>
    </div>

    <div class="split">
      <section class="yh-card list-card">
        <el-table
          v-loading="loading"
          :data="list"
          data-testid="fb-table"
          :row-class-name="rowClass"
          highlight-current-row
          @row-click="(row: Feedback) => (selectedId = row.id)"
        >
          <el-table-column label="内容" min-width="300">
            <template #default="{ row }">
              <div class="fb-cell">
                <AvatarStack :people="[{ name: row.memberName }]" :size="36" />
                <div class="fb-main">
                  <b class="fb-title">{{ feedbackTitle(row.content) }}</b>
                  <span class="yh-muted">{{ who(row) }}<template v-if="images(row).length"> · 附 {{ images(row).length }} 张截图</template></span>
                </div>
                <el-image
                  v-if="images(row).length"
                  :src="images(row)[0]"
                  fit="cover"
                  class="fb-shot"
                />
              </div>
            </template>
          </el-table-column>
          <el-table-column label="端" width="80">
            <template #default="{ row }">{{ platformOf(row.clientInfo) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="86">
            <template #default="{ row }">
              <span class="yh-tag" :class="row.status === 'DONE' ? 'ok' : 'warn'">{{ row.status === 'DONE' ? '已处理' : '待处理' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="时间" width="100">
            <template #default="{ row }"><span class="yh-muted" :title="formatTime(row.createTime)">{{ relativeTime(row.createTime) }}</span></template>
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
      </section>

      <section class="yh-card detail" aria-label="反馈详情" data-testid="fb-detail">
        <template v-if="selected">
          <div class="d-head">
            <AvatarStack :people="[{ name: selected.memberName }]" :size="40" />
            <div class="d-title">
              <b>{{ feedbackTitle(selected.content, 40) }}</b>
              <span class="yh-muted">#FB-{{ selected.id }} · {{ who(selected) }} · {{ formatTime(selected.createTime) }}</span>
            </div>
            <span class="yh-tag" :class="selected.status === 'DONE' ? 'ok' : 'warn'">{{ selected.status === 'DONE' ? '已处理' : '待处理' }}</span>
          </div>

          <p class="d-content">{{ selected.content }}</p>

          <div v-if="images(selected).length" class="shots">
            <el-image
              v-for="(src, i) in images(selected)"
              :key="src"
              :src="src"
              :preview-src-list="images(selected)"
              :initial-index="i"
              preview-teleported
              fit="cover"
              class="thumb"
            />
          </div>

          <dl class="meta">
            <div><dt>来源端</dt><dd>{{ platformOf(selected.clientInfo) }}</dd></div>
            <div><dt>设备与版本</dt><dd class="wrap">{{ selected.clientInfo || '-' }}</dd></div>
            <div><dt>联系方式</dt><dd>{{ selected.contact || '未留' }}</dd></div>
            <div><dt>报错编号</dt><dd class="yh-muted">待接入（反馈记录尚未保存报错编号）</dd></div>
          </dl>

          <div class="actions">
            <el-tooltip content="回复并通知用户的接口待上线（prototype-ui-rollout 3.2）" placement="top">
              <span><el-button disabled>回复用户</el-button></span>
            </el-tooltip>
            <el-button
              v-if="selected.status !== 'DONE'"
              type="primary"
              :loading="updatingId === selected.id"
              @click="setStatus(selected, 'DONE')"
            >标记已处理</el-button>
            <el-button
              v-else
              :loading="updatingId === selected.id"
              @click="setStatus(selected, 'NEW')"
            >重新打开</el-button>
          </div>
        </template>
        <el-empty v-else description="选择左侧一条反馈查看详情" :image-size="72" />
      </section>
    </div>
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}
.split {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(360px, 1fr);
  gap: 16px;
  align-items: start;
}
@media (max-width: 1100px) {
  .split {
    grid-template-columns: 1fr;
  }
}
.list-card {
  padding: 6px 8px 12px;
}
.list-card :deep(.el-table__row) {
  cursor: pointer;
}
.list-card :deep(.el-table__row.is-selected > td) {
  background: var(--yh-highlight);
}
.fb-cell {
  display: flex;
  align-items: center;
  gap: 12px;
}
.fb-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.fb-title {
  color: var(--yh-title);
  line-height: 1.45;
}
.fb-shot {
  width: 40px;
  height: 52px;
  border-radius: 6px;
  border: 1px solid var(--yh-border);
  flex-shrink: 0;
}
.detail {
  display: flex;
  flex-direction: column;
  gap: 14px;
  position: sticky;
  top: 0;
}
.d-head {
  display: flex;
  align-items: center;
  gap: 12px;
}
.d-title {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.d-title b {
  font-size: 17px;
  color: var(--yh-title);
}
.d-content {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  color: var(--yh-title);
  line-height: 1.6;
}
.shots {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.thumb {
  width: 96px;
  height: 128px;
  border-radius: 10px;
  border: 1px solid var(--yh-border);
}
.meta {
  margin: 0;
  padding: 12px 14px;
  border-radius: 12px;
  background: var(--yh-bg);
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px 16px;
}
.meta dt {
  font-size: 12px;
  color: var(--yh-body);
}
.meta dd {
  margin: 2px 0 0;
  font-size: 13px;
  color: var(--yh-title);
}
.meta .wrap {
  word-break: break-all;
}
.actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
