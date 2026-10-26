<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AvatarStack from '@/components/AvatarStack.vue'
import DishThumb from '@/components/DishThumb.vue'
import StatCard from '@/components/StatCard.vue'
import SwitchBar from './SwitchBar.vue'
import {
  getAdminPost, getCommunityStats, listQueue, moderatePost,
  type CommunityStats, type ModerateAction, type PostDetailLite, type QueueItem, type QueueStatus, type Risk,
} from '@/api/communityAdmin'
import { useBadgeStore } from '@/store/badges'
import {
  ACTION_LABEL, MODERATE_REASONS, NOTE_MAX, RISK_META, highlightSegments, scoreTone, validateModeration,
  type ModerationForm,
} from '@/utils/moderation'
import { traceTime } from '@/utils/agentTrace'

/**
 * 社区审核（原型 AdminCommunity）：发布时同步检查敏感词与外链，AI 预审分级；
 * 高风险进人工队列，举报达阈值自动隐藏。处理动作写审计日志（后端）。
 */
const RISKS: { value: Risk | ''; label: string }[] = [
  { value: '', label: '全部' },
  { value: 'HIGH', label: '高' },
  { value: 'MEDIUM', label: '中' },
  { value: 'LOW', label: '低' },
]

const STATUSES: { value: QueueStatus; label: string }[] = [
  { value: 'PENDING', label: '待处理' },
  { value: 'DONE', label: '已处理' },
  { value: 'APPEALED', label: '申诉' },
]

const badgeStore = useBadgeStore()
const status = ref<QueueStatus>('PENDING')
const stats = ref<CommunityStats | null>(null)
const risk = ref<Risk | ''>('')
const queue = ref<QueueItem[]>([])
const nextCursor = ref<string | null>(null)
const loading = ref(false)
const loadError = ref('')
const selectedId = ref<number | null>(null)
const post = ref<PostDetailLite | null>(null)
const postLoading = ref(false)
const bigIndex = ref(0)
const submitting = ref(false)
const form = reactive<ModerationForm>({ action: '', reason: '', note: '' })
const errors = ref<Record<string, string>>({})

const selected = computed(() => queue.value.find((q) => q.postId === selectedId.value) ?? null)
// 管理员详情里的审核信息优先，缺失时用队列项
const aiScores = computed(() => post.value?.aiScores ?? selected.value?.aiScores ?? [])
const aiSummary = computed(() => post.value?.aiSummary ?? selected.value?.aiSummary ?? null)
const reports = computed(() => post.value?.reports ?? selected.value?.reports ?? [])
const moderation = computed(() => post.value?.moderation ?? selected.value?.moderation ?? null)
const appeal = computed(() => post.value?.appeal ?? selected.value?.appeal ?? null)
const canModerate = computed(() => status.value !== 'DONE')
const statusCount = (v: QueueStatus) =>
  v === 'PENDING' ? stats.value?.pending : v === 'APPEALED' ? stats.value?.appealed : undefined
const images = computed(() => {
  const list = post.value?.images?.filter(Boolean) ?? []
  if (list.length) return list
  return selected.value?.coverUrl ? [selected.value.coverUrl] : []
})
const blockedImage = computed(() => selected.value?.reasons?.some((r) => r.includes('外链')) ?? false)
const segments = computed(() => highlightSegments(post.value?.text ?? ''))

const fmt = (n: number | null | undefined, suffix = '') => (n == null || !Number.isFinite(n) ? '—' : `${n}${suffix}`)
const statCards = computed(() => {
  const s = stats.value ?? {}
  const rate = s.autoPassRate == null ? '—' : `${Math.round(s.autoPassRate <= 1 ? s.autoPassRate * 100 : s.autoPassRate)}%`
  return [
    { label: '今日新帖', value: fmt(s.newPosts), hint: s.fromCookLog != null ? `其中食记分享 ${s.fromCookLog}` : '' },
    { label: '自动通过', value: rate, hint: 'AI 预审低风险直接发布' },
    {
      label: '待人工',
      value: fmt(s.pending),
      hint: s.pendingHigh != null ? `高风险 ${s.pendingHigh} · 中风险 ${s.pendingMedium ?? 0}` : '',
      tone: s.pending ? ('warn' as const) : ('default' as const),
    },
    { label: '今日举报', value: fmt(s.reports), hint: s.autoHidden != null ? `3 次举报自动隐藏 ${s.autoHidden} 条` : '' },
    { label: '平均处理时长', value: s.avgHandleMinutes == null ? '—' : `${s.avgHandleMinutes} 分钟`, hint: '目标 ≤ 2 小时' },
  ]
})

async function loadStats() {
  try {
    stats.value = await getCommunityStats()
    badgeStore.setCommunityPending(stats.value?.pending)
  } catch {
    stats.value = null
  }
}

async function loadQueue(append = false) {
  loading.value = true
  loadError.value = ''
  try {
    const res = await listQueue({ status: status.value, risk: risk.value || undefined, cursor: append ? nextCursor.value : null })
    const items = Array.isArray(res?.items) ? res.items : []
    queue.value = append ? [...queue.value, ...items] : items
    nextCursor.value = res?.nextCursor ?? null
    if (!append && !queue.value.some((q) => q.postId === selectedId.value)) select(queue.value[0]?.postId ?? null)
  } catch (e) {
    loadError.value = (e as Error)?.message || '加载失败'
    if (!append) queue.value = []
  } finally {
    loading.value = false
  }
}

async function select(id: number | null) {
  selectedId.value = id
  post.value = null
  bigIndex.value = 0
  errors.value = {}
  form.action = ''
  form.reason = ''
  form.note = ''
  if (id == null) return
  postLoading.value = true
  try {
    post.value = await getAdminPost(id)
  } catch {
    post.value = null
  } finally {
    postLoading.value = false
  }
}

function onStatus(v: QueueStatus) {
  if (status.value === v) return
  status.value = v
  loadQueue()
}

function onRisk(r: Risk | '') {
  if (risk.value === r) return
  risk.value = r
  loadQueue()
}

async function act(action: ModerateAction) {
  const item = selected.value
  if (!item) return
  form.action = action
  errors.value = validateModeration(form)
  if (Object.keys(errors.value).length) return
  if (action === 'BAN_7D') {
    try {
      await ElMessageBox.confirm(`将封禁「${item.author?.nickname}」7 天并隐藏该帖，作者会收到通知。`, '确认封禁', {
        confirmButtonText: '封禁 7 天',
        cancelButtonText: '取消',
        type: 'warning',
      })
    } catch {
      return
    }
  }
  submitting.value = true
  try {
    await moderatePost(item.postId, { action, reason: form.reason, note: form.note.trim() })
    ElMessage.success(`已${ACTION_LABEL[action]}，已写入审计日志`)
    const idx = queue.value.findIndex((q) => q.postId === item.postId)
    queue.value = queue.value.filter((q) => q.postId !== item.postId)
    if (status.value === 'PENDING') {
      badgeStore.setCommunityPending(badgeStore.communityPending - 1)
      if (stats.value?.pending) stats.value = { ...stats.value, pending: stats.value.pending - 1 }
    } else if (stats.value?.appealed) {
      stats.value = { ...stats.value, appealed: stats.value.appealed - 1 }
    }
    select(queue.value[Math.min(idx, queue.value.length - 1)]?.postId ?? null)
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  loadStats()
  loadQueue()
})
</script>

<template>
  <div class="yh-page">
    <div class="yh-head">
      <div>
        <h1>社区审核</h1>
        <div class="yh-sub">发布时同步检查敏感词与外链；AI 预审分级后，低风险直接发布，中风险先发后审，高风险进人工队列</div>
      </div>
      <div class="head-tools">
        <div class="yh-seg" role="group" aria-label="处理状态" data-testid="status-tabs">
          <button
            v-for="t in STATUSES"
            :key="t.value"
            type="button"
            :class="{ on: status === t.value }"
            :aria-pressed="status === t.value"
            @click="onStatus(t.value)"
          >{{ statusCount(t.value) ? `${t.label} ${statusCount(t.value)}` : t.label }}</button>
        </div>
        <el-button @click="() => { loadStats(); loadQueue() }">刷新</el-button>
      </div>
    </div>

    <!-- A.12 厨房圈紧急关停开关（只用于出现内容风险时一键关闭） -->
    <SwitchBar class="switch" />

    <div class="kpis" data-testid="community-stats">
      <StatCard v-for="c in statCards" :key="c.label" :label="c.label" :value="c.value" :hint="c.hint" :tone="c.tone ?? 'default'" />
    </div>

    <div class="split">
      <section class="yh-card list-card" aria-label="审核队列">
        <div class="card-head">
          <span class="yh-card-title">审核队列</span>
          <div class="yh-seg" role="group" aria-label="风险筛选" data-testid="risk-filter">
            <button
              v-for="r in RISKS"
              :key="r.value || 'all'"
              type="button"
              :class="{ on: risk === r.value }"
              :aria-pressed="risk === r.value"
              @click="onRisk(r.value)"
            >{{ r.label }}</button>
          </div>
        </div>
        <div v-loading="loading" class="queue" data-testid="queue">
          <button
            v-for="q in queue"
            :key="q.postId"
            type="button"
            class="q-row"
            :class="{ on: q.postId === selectedId }"
            :aria-current="q.postId === selectedId ? 'true' : undefined"
            @click="select(q.postId)"
          >
            <DishThumb :src="q.coverUrl" :name="q.title" :size="64" :radius="12" />
            <span class="q-main">
              <b class="q-title">{{ q.title }}</b>
              <span class="q-meta">
                <AvatarStack :people="[{ name: q.author?.nickname, avatarUrl: q.author?.avatarUrl }]" :size="20" />
                {{ q.author?.nickname }}<template v-if="q.reasons?.length"> · {{ q.reasons.join(' · ') }}</template>
              </span>
              <span class="q-tags">
                <span class="yh-tag" :class="RISK_META[q.risk]?.tone">{{ RISK_META[q.risk]?.label || q.risk }}</span>
                <span v-if="q.reportCount" class="yh-tag err">举报 {{ q.reportCount }}</span>
                <span v-if="q.status === 'HIDDEN'" class="yh-tag mute">已隐藏</span>
              </span>
            </span>
          </button>
          <div v-if="!loading && !queue.length" class="empty yh-muted">{{ loadError || (status === 'PENDING' ? '队列已清空' : status === 'DONE' ? '暂无已处理记录' : '暂无申诉') }}</div>
          <el-button v-if="nextCursor" text class="more" :loading="loading" @click="loadQueue(true)">加载更多</el-button>
        </div>
      </section>

      <div v-if="selected" class="right">
        <section v-loading="postLoading" class="yh-card" aria-label="帖子详情" data-testid="post-detail">
          <div class="p-head">
            <AvatarStack :people="[{ name: selected.author?.nickname, avatarUrl: selected.author?.avatarUrl }]" :size="40" />
            <div class="p-who">
              <b>{{ selected.author?.nickname }}</b>
              <span class="yh-muted">{{ traceTime(selected.createdAt) }} 发布<template v-if="post?.city"> · {{ post.city }}</template></span>
            </div>
            <span class="yh-tag" :class="RISK_META[selected.risk]?.tone">
              {{ RISK_META[selected.risk]?.label }} · {{ selected.status === 'PUBLISHED' ? '已公开' : selected.status === 'HIDDEN' ? '已隐藏' : '未公开' }}
            </span>
          </div>
          <div class="p-body">
            <div class="gallery">
              <el-image
                v-if="images.length"
                class="big-img"
                :src="images[bigIndex]"
                fit="cover"
                :preview-src-list="images"
                :initial-index="bigIndex"
                preview-teleported
              />
              <div v-else class="big-img placeholder yh-muted">无图片</div>
              <div class="thumbs">
                <button
                  v-for="(src, i) in images.slice(0, 4)"
                  :key="src + i"
                  type="button"
                  class="thumb"
                  :class="{ on: i === bigIndex }"
                  :aria-label="`第 ${i + 1} 张`"
                  @click="bigIndex = i"
                >
                  <img :src="src" alt="" />
                </button>
                <div v-if="blockedImage" class="thumb blocked" data-testid="blocked-image">外链图片已拦截</div>
              </div>
            </div>
            <div class="p-text">
              <h2>{{ post?.title || selected.title }}</h2>
              <p class="text" data-testid="post-text">
                <template v-for="(s, i) in segments" :key="i">
                  <mark v-if="s.kind" :class="s.kind">{{ s.text }}</mark>
                  <template v-else>{{ s.text }}</template>
                </template>
              </p>
              <div v-if="post?.topics?.length" class="topics">
                <span v-for="t in post.topics" :key="t.topicId" class="yh-tag mute">#{{ t.name }}</span>
              </div>
              <div v-if="post?.recipe" class="yh-muted">关联菜谱：{{ post.recipe.name }}</div>
              <div class="ai" data-testid="ai-judge">
                <div class="ai-title">AI 预审 · 判定依据</div>
                <template v-if="aiScores.length">
                  <div v-for="s in aiScores" :key="s.label" class="score">
                    <span>{{ s.label }}</span>
                    <span class="meter"><i :class="scoreTone(s.score, s.unit)" :style="{ width: `${s.unit ? (s.score > 0 ? 100 : 2) : Math.max(2, s.score * 100)}%` }" /></span>
                    <b>{{ s.unit ? `${s.score} ${s.unit}` : s.score.toFixed(2) }}</b>
                  </div>
                </template>
                <ul v-else class="reasons">
                  <li v-for="r in selected.reasons" :key="r">{{ r }}</li>
                </ul>
                <p v-if="aiSummary" class="yh-muted">{{ aiSummary }}</p>
              </div>
              <div v-if="post?.authorHistory" class="yh-muted hist">作者历史：{{ post.authorHistory }}</div>
              <div v-if="moderation" class="record" data-testid="mod-record">
                <b>上次处理：{{ ACTION_LABEL[moderation.action] }}</b> · {{ moderation.reason }}
                <div class="yh-muted">{{ moderation.operator }} · {{ traceTime(moderation.at) }}<template v-if="moderation.note"> · {{ moderation.note }}</template></div>
              </div>
              <div v-if="appeal" class="record appeal" data-testid="appeal">
                <b>作者申诉</b>
                <div>{{ appeal.text }}</div>
                <div class="yh-muted">{{ traceTime(appeal.createdAt) }}</div>
              </div>
            </div>
          </div>
        </section>

        <div class="grid2">
          <section class="yh-card" aria-label="举报记录" data-testid="reports">
            <div class="yh-card-title">举报记录（{{ selected.reportCount }}）</div>
            <div v-for="(r, i) in reports" :key="i" class="rep">
              <AvatarStack :people="[{ name: r.user?.nickname, avatarUrl: r.user?.avatarUrl }]" :size="28" />
              <div>
                <b>{{ r.user?.nickname }} · {{ r.reason }}</b>
                <div class="yh-muted">{{ r.text ? `「${r.text}」· ` : '' }}{{ traceTime(r.createdAt) }}</div>
              </div>
            </div>
            <div v-if="!reports.length" class="yh-muted rep-empty">
              {{ selected.reportCount ? '举报明细待后端返回' : '暂无举报' }}
            </div>
            <div v-if="selected.reportCount && selected.reportCount < 3" class="yh-muted">再有 {{ 3 - selected.reportCount }} 位不同用户举报将自动隐藏</div>
          </section>

          <section v-if="canModerate" class="yh-card" aria-label="处理" data-testid="moderate">
            <div class="yh-card-title">{{ status === 'APPEALED' ? '复审申诉' : '处理' }}</div>
            <el-form label-position="top" class="mform" @submit.prevent>
              <el-form-item label="原因" :error="errors.reason">
                <el-select v-model="form.reason" placeholder="选择处理原因" data-testid="mod-reason" style="width: 100%">
                  <el-option v-for="r in MODERATE_REASONS" :key="r" :label="r" :value="r" />
                </el-select>
              </el-form-item>
              <el-form-item label="说明（通知作者并写入审计日志）" :error="errors.note">
                <el-input
                  v-model="form.note"
                  type="textarea"
                  :rows="3"
                  :maxlength="NOTE_MAX"
                  show-word-limit
                  placeholder="如：正文含站外联系方式与促销信息，违反《社区规范》第 4 条"
                  data-testid="mod-note"
                />
              </el-form-item>
            </el-form>
            <div v-if="errors.action" class="err-txt">{{ errors.action }}</div>
            <div class="yh-muted audit">写入审计日志：操作人、原因、处理前后状态</div>
            <div class="acts">
              <el-button :loading="submitting && form.action === 'APPROVE'" data-testid="mod-approve" @click="act('APPROVE')">通过</el-button>
              <el-button :loading="submitting && form.action === 'HIDE'" data-testid="mod-hide" @click="act('HIDE')">隐藏</el-button>
              <el-button type="danger" :loading="submitting && form.action === 'BAN_7D'" data-testid="mod-ban" @click="act('BAN_7D')">封禁 7 天</el-button>
            </div>
          </section>
        </div>
      </div>
      <section v-else class="yh-card empty yh-muted">选择左侧帖子查看详情</section>
    </div>
  </div>
</template>

<style scoped>
.switch {
  margin-bottom: 16px;
}
.kpis {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 16px;
}
.split {
  display: grid;
  grid-template-columns: minmax(320px, 380px) minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}
.right {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}
.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
  padding: 0 14px;
}
.list-card {
  padding: 14px 0 6px;
}
.queue {
  display: flex;
  flex-direction: column;
  min-height: 120px;
}
.q-row {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  padding: 12px 14px;
  border: none;
  border-top: 1px solid var(--yh-border);
  border-left: 3px solid transparent;
  background: transparent;
  text-align: left;
  cursor: pointer;
  font-family: inherit;
  color: var(--yh-title);
}
.q-row:hover,
.q-row.on {
  background: var(--yh-highlight);
}
.q-row.on {
  border-left-color: var(--yh-primary-deep);
}
.q-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 5px;
}
.q-title {
  font-size: 14px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.q-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--yh-body);
  overflow: hidden;
  white-space: nowrap;
}
.q-tags {
  display: flex;
  gap: 6px;
}
.empty {
  padding: 28px 16px;
  text-align: center;
}
.more {
  margin: 6px auto;
}
.p-head {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
}
.p-who {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.p-body {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(0, 1fr);
  gap: 18px;
}
.gallery {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.big-img {
  width: 100%;
  aspect-ratio: 4 / 3;
  border-radius: 12px;
  background: var(--yh-secondary);
}
.placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
}
.thumbs {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
}
.thumb {
  aspect-ratio: 4 / 3;
  border-radius: 8px;
  overflow: hidden;
  border: 2px solid transparent;
  padding: 0;
  background: var(--yh-secondary);
  cursor: pointer;
}
.thumb.on {
  border-color: var(--yh-primary-deep);
}
.thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.thumb.blocked {
  cursor: default;
  display: flex;
  align-items: center;
  justify-content: center;
  text-align: center;
  font-size: 12px;
  font-weight: 700;
  color: var(--yh-error-text);
  border: 1.5px dashed var(--yh-error);
  background: repeating-linear-gradient(135deg, var(--yh-error-soft) 0 6px, var(--yh-panel) 6px 12px);
}
.p-text h2 {
  margin: 0 0 8px;
  font-size: 18px;
  color: var(--yh-title);
}
.text {
  margin: 0 0 10px;
  font-size: 14px;
  line-height: 1.75;
  color: var(--yh-title);
  white-space: pre-wrap;
}
mark {
  padding: 0 3px;
  border-radius: 4px;
}
mark.contact {
  background: var(--yh-error-soft);
  color: var(--yh-error-text);
}
mark.promo {
  background: var(--yh-warning-soft);
  color: var(--yh-warning-text);
  border: 1px solid var(--yh-primary-soft);
}
.topics {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 8px;
}
.ai {
  margin-top: 12px;
  padding: 14px;
  border-radius: 12px;
  background: var(--yh-highlight);
  border: 1px solid var(--yh-border);
}
.ai-title {
  font-size: 12px;
  font-weight: 700;
  color: var(--yh-body);
  margin-bottom: 8px;
}
.score {
  display: grid;
  grid-template-columns: 90px 1fr 50px;
  gap: 10px;
  align-items: center;
  font-size: 13px;
  padding: 4px 0;
  color: var(--yh-title);
}
.score b {
  text-align: right;
}
.meter {
  height: 6px;
  border-radius: 3px;
  background: var(--yh-border);
  overflow: hidden;
}
.meter i {
  display: block;
  height: 100%;
  border-radius: 3px;
}
.meter i.err {
  background: var(--yh-error);
}
.meter i.warn {
  background: var(--yh-warning);
}
.meter i.ok {
  background: var(--yh-success);
}
.reasons {
  margin: 0;
  padding-left: 18px;
  font-size: 13px;
  color: var(--yh-title);
  line-height: 1.8;
}
.grid2 {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1.2fr);
  gap: 16px;
}
.rep {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  margin-top: 12px;
  font-size: 13px;
  color: var(--yh-title);
}
.rep-empty {
  margin: 12px 0;
}
.head-tools {
  display: flex;
  gap: 10px;
  align-items: center;
}
.hist {
  margin-top: 10px;
}
.record {
  margin-top: 10px;
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px solid var(--yh-border);
  font-size: 13px;
  color: var(--yh-title);
}
.record.appeal {
  background: var(--yh-info-soft);
  border-color: transparent;
}
.mform {
  margin-top: 12px;
}
.err-txt {
  color: var(--el-color-danger);
  font-size: 12px;
  margin-bottom: 6px;
}
.audit {
  margin-bottom: 12px;
}
.acts {
  display: flex;
  gap: 8px;
}
@media (max-width: 1200px) {
  .kpis {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
  .split,
  .p-body,
  .grid2 {
    grid-template-columns: 1fr;
  }
}
</style>
