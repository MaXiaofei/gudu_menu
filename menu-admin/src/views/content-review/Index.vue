<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import Pagination from '@/components/Pagination.vue'
import {
  confirmMedia, confirmText, listMediaViolations, listTextViolations, mediaViolationImage, restoreMedia,
  type MediaViolation, type TextViolation,
} from '@/api/backoffice'
import { PURPOSE, label, mediaActions, reasonError, reviewLabel } from '@/utils/backoffice'

/**
 * 内容安全违规复核（mini-release-compliance F.3）：文本 / 图片违规记录（来源、用途、结论、时间、关联账号）。
 * 图片可「恢复」（误判：还原隔离文件、恢复头像 / 帖子）或「确认违规」；文本只记摘录，可「确认违规」。
 * 所有复核动作写操作日志。
 */
const pageSize = 15
const tab = ref<'media' | 'text'>('media')

const media = reactive({ review: 'PENDING', pageNum: 1, total: 0, list: [] as MediaViolation[], loading: false })
const text = reactive({ confirmed: 'false' as '' | 'true' | 'false', pageNum: 1, total: 0, list: [] as TextViolation[], loading: false })
const preview = reactive({ visible: false, src: '' as string | null, loading: false })
const dialog = reactive({ visible: false, kind: 'restore' as 'restore' | 'confirm-media' | 'confirm-text', id: 0, reason: '', saving: false })

const MEDIA_TABS = [
  { value: 'PENDING', label: '待复核' },
  { value: 'CONFIRMED', label: '已确认违规' },
  { value: 'RESTORED', label: '已恢复' },
  { value: '', label: '全部' },
]

async function loadMedia() {
  media.loading = true
  try {
    const r = await listMediaViolations({ review: media.review || undefined, pageNum: media.pageNum, pageSize })
    media.list = r?.records || []
    media.total = r?.total || 0
  } catch {
    media.list = []
  } finally {
    media.loading = false
  }
}

async function loadText() {
  text.loading = true
  try {
    const r = await listTextViolations({ confirmed: text.confirmed === '' ? undefined : text.confirmed === 'true', pageNum: text.pageNum, pageSize })
    text.list = r?.records || []
    text.total = r?.total || 0
  } catch {
    text.list = []
  } finally {
    text.loading = false
  }
}

async function showImage(row: MediaViolation) {
  Object.assign(preview, { visible: true, src: '', loading: true })
  try {
    preview.src = await mediaViolationImage(row.id)
  } finally {
    preview.loading = false
  }
}

function open(kind: typeof dialog.kind, id: number) {
  Object.assign(dialog, { visible: true, kind, id, reason: '', saving: false })
}

async function submit() {
  if (dialog.kind === 'restore') {
    const err = reasonError(dialog.reason)
    if (err) {
      ElMessage.warning(err)
      return
    }
  }
  dialog.saving = true
  try {
    if (dialog.kind === 'restore') {
      const r = await restoreMedia(dialog.id, dialog.reason.trim())
      const base = r.filesRestored ? '已恢复：原图已还原，头像 / 帖子已恢复' : '已改判恢复（隔离区无文件，原图未还原）'
      if (r.notices?.length) ElMessage.warning(`${base}；${r.notices.join('；')}`)
      else ElMessage.success(base)
      await loadMedia()
    } else if (dialog.kind === 'confirm-media') {
      await confirmMedia(dialog.id, dialog.reason.trim() || undefined)
      ElMessage.success('已确认违规')
      await loadMedia()
    } else {
      await confirmText(dialog.id, dialog.reason.trim() || undefined)
      ElMessage.success('已确认违规')
      await loadText()
    }
    dialog.visible = false
  } finally {
    dialog.saving = false
  }
}

function formatTime(s: string | null | undefined) {
  return (s || '').replace('T', ' ').slice(0, 16) || '—'
}

onMounted(() => {
  loadMedia()
  loadText()
})
</script>

<template>
  <div class="yh-page">
    <div class="yh-head">
      <div>
        <h1>内容安全复核</h1>
        <div class="yh-sub">微信内容安全 / 规则拦下的文本与图片；违规图片已隔离（原地址显示占位图），误判可恢复</div>
      </div>
      <el-button @click="tab === 'media' ? loadMedia() : loadText()">刷新</el-button>
    </div>

    <el-tabs v-model="tab" data-testid="cr-tabs">
      <el-tab-pane label="图片" name="media">
        <div class="yh-seg" role="group" aria-label="复核状态">
          <button v-for="t in MEDIA_TABS" :key="t.value || 'all'" type="button" :class="{ on: media.review === t.value }"
            @click="media.review = t.value; media.pageNum = 1; loadMedia()">{{ t.label }}</button>
        </div>
        <section class="yh-card">
          <el-table v-loading="media.loading" :data="media.list" data-testid="cr-media-table">
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column label="用途" width="110">
              <template #default="{ row }">{{ label(PURPOSE, row.purpose) }}</template>
            </el-table-column>
            <el-table-column label="来源 / 标签" width="130">
              <template #default="{ row }">微信 · {{ row.label || '—' }}</template>
            </el-table-column>
            <el-table-column label="关联账号" width="100">
              <template #default="{ row }">{{ row.accountId ? `#${row.accountId}` : '—' }}</template>
            </el-table-column>
            <el-table-column label="检测时间" width="150">
              <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
            </el-table-column>
            <el-table-column label="复核结论" width="140">
              <template #default="{ row }">
                <el-tag :type="row.reviewStatus === 'RESTORED' ? 'success' : row.reviewStatus === 'CONFIRMED' ? 'danger' : 'warning'" size="small">{{ reviewLabel(row.reviewStatus) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" min-width="220" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="showImage(row)">查看原图</el-button>
                <el-button v-if="mediaActions(row.reviewStatus).restore" link type="success" data-testid="cr-restore" @click="open('restore', row.id)">恢复</el-button>
                <el-button v-if="mediaActions(row.reviewStatus).confirm" link type="danger" @click="open('confirm-media', row.id)">确认违规</el-button>
              </template>
            </el-table-column>
          </el-table>
          <Pagination :total="media.total" :current-page="media.pageNum" :page-size="pageSize" @current-change="(p: number) => { media.pageNum = p; loadMedia() }" />
        </section>
      </el-tab-pane>

      <el-tab-pane label="文本" name="text">
        <el-radio-group v-model="text.confirmed" size="small" @change="text.pageNum = 1; loadText()">
          <el-radio-button value="false">待复核</el-radio-button>
          <el-radio-button value="true">已确认</el-radio-button>
          <el-radio-button value="">全部</el-radio-button>
        </el-radio-group>
        <section class="yh-card">
          <el-table v-loading="text.loading" :data="text.list" data-testid="cr-text-table">
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column label="用途" width="110">
              <template #default="{ row }">{{ label(PURPOSE, row.purpose) }}</template>
            </el-table-column>
            <el-table-column label="内容摘录" min-width="260">
              <template #default="{ row }">{{ row.excerpt || '—' }}</template>
            </el-table-column>
            <el-table-column label="来源 / 结论" width="150">
              <template #default="{ row }">{{ row.source === 'WX' ? '微信' : '规则' }} · {{ row.suggest }}</template>
            </el-table-column>
            <el-table-column label="关联账号" width="100">
              <template #default="{ row }">{{ row.accountId ? `#${row.accountId}` : '—' }}</template>
            </el-table-column>
            <el-table-column label="时间" width="150">
              <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="110" fixed="right">
              <template #default="{ row }">
                <el-button v-if="!row.confirmed" link type="danger" @click="open('confirm-text', row.id)">确认违规</el-button>
                <span v-else class="yh-muted">已确认</span>
              </template>
            </el-table-column>
          </el-table>
          <Pagination :total="text.total" :current-page="text.pageNum" :page-size="pageSize" @current-change="(p: number) => { text.pageNum = p; loadText() }" />
        </section>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="preview.visible" title="隔离区原图" width="520px">
      <div v-loading="preview.loading" class="preview">
        <img v-if="preview.src" :src="preview.src" alt="违规图片原图" />
        <el-empty v-else-if="!preview.loading" description="隔离区没有该文件（可能已恢复或已清理）" />
      </div>
    </el-dialog>

    <el-dialog v-model="dialog.visible" :title="dialog.kind === 'restore' ? '恢复图片（误判）' : '确认违规'" width="460px">
      <p class="yh-muted">{{ dialog.kind === 'restore' ? '恢复后原图还原、头像与帖子恢复可见，用户可再次使用该图片。' : '确认后保持隔离 / 拦截状态。' }}</p>
      <el-input v-model="dialog.reason" type="textarea" :rows="3" maxlength="200" show-word-limit
        :placeholder="dialog.kind === 'restore' ? '原因（必填，记入操作日志）' : '备注（可选，记入操作日志）'" />
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button :type="dialog.kind === 'restore' ? 'primary' : 'danger'" :loading="dialog.saving" @click="submit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.yh-seg {
  margin-bottom: 12px;
}
.preview {
  min-height: 160px;
  text-align: center;
}
.preview img {
  max-width: 100%;
  max-height: 60vh;
}
</style>
