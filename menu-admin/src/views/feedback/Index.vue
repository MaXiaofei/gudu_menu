<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import Pagination from '@/components/Pagination.vue'
import AvatarStack from '@/components/AvatarStack.vue'
import {
  listFeedback, replyFeedback, updateFeedbackStatus, type Feedback, type FeedbackStatus, type FeedbackType,
} from '@/api/feedback'
import { useBadgeStore } from '@/store/badges'
import {
  REPLY_MAX, feedbackStatusLabel, feedbackStatusTone, feedbackTitle, feedbackTypeLabel, feedbackTypeTone,
  platformOf, relativeTime, sourceRefLabel, validateReply,
} from '@/utils/feedback'

const pageSize = 15 // DESIGN.md §12.2 列表分页约定
const pageNum = ref(1)
const total = ref(0)
const status = ref<FeedbackStatus | ''>('NEW')
const type = ref<FeedbackType | ''>('')
const list = ref<Feedback[]>([])
const loading = ref(false)
const loadError = ref('')
const updatingId = ref<number | null>(null)
const selectedId = ref<number | null>(null)
const replyText = ref('')
const replyError = ref('')
const replying = ref(false)
const badgeStore = useBadgeStore()

const STATUS_TABS: { value: FeedbackStatus | ''; label: string }[] = [
  { value: 'NEW', label: '待处理' },
  { value: 'REPLIED', label: '已回复' },
  { value: 'DONE', label: '已处理' },
  { value: '', label: '全部' },
]

const TYPE_TABS: { value: FeedbackType | ''; label: string }[] = [
  { value: '', label: '全部类型' },
  { value: 'general', label: '一般反馈' },
  { value: 'ai', label: 'AI 内容' },
  { value: 'complaint', label: '投诉举报' },
]

const selected = computed(() => list.value.find((f) => f.id === selectedId.value) ?? null)

// 切换选中条目：回复框带出已有回复（便于修改），清空错误
watch(selectedId, () => {
  replyText.value = selected.value?.reply || ''
  replyError.value = ''
})

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const res = await listFeedback({ type: type.value, status: status.value, pageNum: pageNum.value, pageSize })
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

function onTypeFilter(next: FeedbackType | '') {
  if (type.value === next) return
  type.value = next
  pageNum.value = 1
  load()
}

function onPageChange(p: number) {
  pageNum.value = p
  load()
}

/** 侧栏「待处理」角标只数 NEW：离开 NEW −1，回到 NEW +1。 */
function syncBadge(prev: FeedbackStatus, next: FeedbackStatus) {
  const delta = (next === 'NEW' ? 1 : 0) - (prev === 'NEW' ? 1 : 0)
  if (delta) badgeStore.setFeedbackOpen(Math.max(0, badgeStore.feedbackOpen + delta))
}

/** 当前筛选下该条不再属于此列表时重新拉取；否则就地更新。 */
async function applyLocal(row: Feedback, patch: Partial<Feedback>) {
  const next = patch.status ?? row.status
  if (status.value && status.value !== next) {
    if (list.value.length === 1 && pageNum.value > 1) pageNum.value -= 1
    await load()
  } else {
    Object.assign(row, patch)
  }
}

async function setStatus(row: Feedback, next: FeedbackStatus) {
  updatingId.value = row.id
  try {
    await updateFeedbackStatus(row.id, next)
    ElMessage.success(next === 'DONE' ? '已标记为已处理' : '已重新打开')
    syncBadge(row.status, next)
    await applyLocal(row, { status: next })
  } finally {
    updatingId.value = null
  }
}

async function sendReply(row: Feedback, markDone: boolean) {
  replyError.value = validateReply(replyText.value)
  if (replyError.value) return
  replying.value = true
  try {
    const text = replyText.value.trim()
    await replyFeedback(row.id, text, markDone)
    // 与后端一致：NEW → REPLIED；已处理保持；勾选「并标记已处理」→ DONE
    const next: FeedbackStatus = markDone ? 'DONE' : row.status === 'NEW' ? 'REPLIED' : row.status
    ElMessage.success('已回复，用户会在「消息与提醒」收到通知')
    syncBadge(row.status, next)
    await applyLocal(row, { status: next, reply: text, repliedAt: nowLocal() })
  } catch (e) {
    replyError.value = (e as Error)?.message || '回复失败'
  } finally {
    replying.value = false
  }
}

/** 本地时间 yyyy-MM-ddTHH:mm:ss（与后端 LocalDateTime 序列化同格式）。 */
function nowLocal() {
  const d = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

function formatTime(s: string | null | undefined) {
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
        <div class="yh-sub">用户在小程序「意见反馈」与 AI 内容旁「反馈」入口提交的内容；回复会以站内消息通知用户（隐私政策承诺 15 个工作日内答复）</div>
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
      <div class="yh-seg" role="group" aria-label="反馈类型" data-testid="fb-type-filter">
        <button
          v-for="t in TYPE_TABS"
          :key="t.value || 'all'"
          type="button"
          :class="{ on: type === t.value }"
          :aria-pressed="type === t.value"
          @click="onTypeFilter(t.value)"
        >{{ t.label }}</button>
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
          <el-table-column label="类型" width="96">
            <template #default="{ row }">
              <span class="yh-tag" :class="feedbackTypeTone(row.type)">{{ feedbackTypeLabel(row.type) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="端" width="80">
            <template #default="{ row }">{{ platformOf(row.clientInfo) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="86">
            <template #default="{ row }">
              <span class="yh-tag" :class="feedbackStatusTone(row.status)">{{ feedbackStatusLabel(row.status) }}</span>
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
            <span class="yh-tag" :class="feedbackTypeTone(selected.type)">{{ feedbackTypeLabel(selected.type) }}</span>
            <span class="yh-tag" :class="feedbackStatusTone(selected.status)" data-testid="fb-detail-status">{{ feedbackStatusLabel(selected.status) }}</span>
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
            <div><dt>来源内容</dt><dd class="wrap" data-testid="fb-source">{{ sourceRefLabel(selected.sourceRef) || '-' }}</dd></div>
            <div><dt>设备与版本</dt><dd class="wrap">{{ selected.clientInfo || '-' }}</dd></div>
            <div><dt>联系方式</dt><dd>{{ selected.contact || '未留' }}</dd></div>
          </dl>

          <div v-if="selected.reply" class="replied" data-testid="fb-replied">
            <span class="yh-muted">已回复 · {{ formatTime(selected.repliedAt) }}</span>
            <p>{{ selected.reply }}</p>
          </div>

          <div class="reply-box">
            <el-input
              v-model="replyText"
              type="textarea"
              :rows="4"
              :maxlength="REPLY_MAX"
              show-word-limit
              :placeholder="selected.reply ? '修改回复（会再通知用户一次）' : '写给用户的回复，会出现在小程序「消息与提醒」和「我的反馈」里'"
              data-testid="fb-reply-input"
            />
            <span v-if="replyError" class="reply-err" data-testid="fb-reply-error">{{ replyError }}</span>
          </div>

          <div class="actions">
            <el-button
              :loading="replying"
              data-testid="fb-reply-send"
              @click="sendReply(selected, false)"
            >{{ selected.reply ? '更新回复' : '回复用户' }}</el-button>
            <el-button
              v-if="selected.status !== 'DONE'"
              type="primary"
              :loading="replying"
              data-testid="fb-reply-done"
              @click="sendReply(selected, true)"
            >回复并标记已处理</el-button>
          </div>
          <div class="actions">
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
.replied {
  padding: 10px 14px;
  border-radius: 12px;
  border-left: 3px solid var(--yh-primary-deep);
  background: var(--yh-highlight);
}
.replied p {
  margin: 4px 0 0;
  white-space: pre-wrap;
  word-break: break-word;
  color: var(--yh-title);
  line-height: 1.6;
}
.reply-box {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.reply-err {
  font-size: 12px;
  color: var(--yh-error-text);
}
.actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
