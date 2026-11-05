<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listFeatureSwitches,
  setFeatureSwitch,
  setPlatformSwitches,
  type FeaturePlatform,
  type FeatureSwitchState,
} from '@/api/featureSwitch'
import {
  ENTRY_CATEGORY_ORDER,
  PLATFORM_LABEL,
  buildMatrix,
  featureConfirmCopy,
  lastOperationText,
  platformBatchCopy,
  recentChangeRows,
  switchName,
  validateSwitchReason,
} from '@/utils/featureSwitch'

/**
 * 功能开关：6 类生成式 AI（咕嘟助手、问咕嘟、拍照识别、一周餐程、营养估算、AI 帮写）按「小程序 / App」分别紧急关停。
 * 另有与 AI 独立的 3 个页面入口（写菜谱、草稿箱、采购清单分享），同样按端关停。
 * AI 小程序默认开启、App 默认关闭；页面入口两端默认开启。关停必须填原因，切换留痕。每端可一键关闭 / 恢复全部 AI。
 */
const states = ref<FeatureSwitchState[]>([])
const loadError = ref('')
const loading = ref(false)
const savingKey = ref('')
const batchSaving = ref('')

const rows = computed(() => buildMatrix(states.value))
const entryRows = computed(() => buildMatrix(states.value, ENTRY_CATEGORY_ORDER))
const recent = computed(() => recentChangeRows(states.value, 20))
const platforms: FeaturePlatform[] = ['mp', 'app']

async function load() {
  loadError.value = ''
  loading.value = true
  try {
    states.value = await listFeatureSwitches()
  } catch (e) {
    loadError.value = (e as Error)?.message || '开关状态加载失败'
  } finally {
    loading.value = false
  }
}

/** 弹确认（恢复）或填原因（关停），返回 null 表示取消。 */
async function askReason(copy: { title: string; message: string; confirm: string }, next: boolean): Promise<string | null> {
  try {
    if (next) {
      await ElMessageBox.confirm(copy.message, copy.title, {
        confirmButtonText: copy.confirm,
        cancelButtonText: '取消',
        type: 'info',
      })
      return ''
    }
    const r = await ElMessageBox.prompt(copy.message, copy.title, {
      confirmButtonText: copy.confirm,
      cancelButtonText: '取消',
      type: 'warning',
      inputPlaceholder: '关停原因（必填，例如：提审要求 / 出现违规内容）',
      inputValidator: (v: string) => validateSwitchReason(false, v) || true,
    })
    return (r.value ?? '').trim()
  } catch {
    return null
  }
}

async function toggle(s: FeatureSwitchState) {
  const next = !s.enabled
  const name = switchName(s.platform, s.label)
  const reason = await askReason(featureConfirmCopy(name, next, s.label), next)
  if (reason === null) return
  savingKey.value = s.key
  try {
    const updated = await setFeatureSwitch({ key: s.key, enabled: next, reason: reason || undefined })
    states.value = states.value.map((x) => (x.key === updated.key ? updated : x))
    ElMessage.success(next ? `${name}已恢复` : `${name}已暂停，最迟 15 秒全部生效`)
  } finally {
    savingKey.value = ''
  }
}

/** 某端一键关闭 / 恢复全部 AI。 */
async function batch(platform: FeaturePlatform, next: boolean) {
  const reason = await askReason(platformBatchCopy(platform, next), next)
  if (reason === null) return
  batchSaving.value = `${platform}:${next}`
  try {
    const updated = await setPlatformSwitches({ platform, enabled: next, reason: reason || undefined })
    const byKey = new Map(updated.map((x) => [x.key, x]))
    states.value = states.value.map((x) => byKey.get(x.key) ?? x)
    ElMessage.success(next ? `${PLATFORM_LABEL[platform]}全部 AI 已恢复` : `${PLATFORM_LABEL[platform]}全部 AI 已暂停，最迟 15 秒生效`)
  } finally {
    batchSaving.value = ''
  }
}

const fmtAt = (s: string | null) => (s ? s.replace('T', ' ').slice(0, 16) : '—')

onMounted(load)
</script>

<template>
  <div class="page" v-loading="loading">
    <div class="head">
      <h2 class="title">功能开关</h2>
      <p class="desc">一键关停 · 小程序 · App · AI 小程序默认开启，App 默认关闭；页面入口两端默认开启</p>
    </div>

    <div v-if="loadError" class="yh-card err">
      <span>{{ loadError }}</span>
      <el-button link @click="load">重试</el-button>
    </div>

    <div class="yh-card batch" data-testid="feature-switch-batch">
      <span class="batch-title">一键操作</span>
      <template v-for="p in platforms" :key="p">
        <el-button
          type="danger"
          plain
          :loading="batchSaving === `${p}:false`"
          :data-testid="`batch-off-${p}`"
          @click="batch(p, false)"
        >{{ PLATFORM_LABEL[p] }}一键关闭全部 AI</el-button>
        <el-button
          link
          :loading="batchSaving === `${p}:true`"
          :data-testid="`batch-on-${p}`"
          @click="batch(p, true)"
        >恢复{{ PLATFORM_LABEL[p] }}全部</el-button>
      </template>
    </div>

    <div class="yh-card table-card">
      <el-table :data="rows" row-key="category" data-testid="feature-switch-table">
        <el-table-column label="AI 功能" prop="label" min-width="120" />
        <el-table-column v-for="p in platforms" :key="p" :label="PLATFORM_LABEL[p]" min-width="240">
          <template #default="{ row }">
            <div v-if="row[p]" class="cell" :class="{ off: !row[p].enabled }" :data-testid="`feature-switch-${row[p].key}`">
              <span class="dot" :class="row[p].enabled ? 'on' : 'off'" />
              <div class="info">
                <div class="status">{{ row[p].enabled ? '运行中' : '已暂停' }}</div>
                <div class="meta">
                  {{ lastOperationText(row[p], p, row.category) }}<template v-if="row[p].reason"> · {{ row[p].reason }}</template>
                </div>
              </div>
              <el-button
                size="small"
                :type="row[p].enabled ? 'danger' : 'primary'"
                :loading="savingKey === row[p].key"
                @click="toggle(row[p])"
              >{{ row[p].enabled ? '关停' : '恢复' }}</el-button>
            </div>
            <span v-else class="meta">—</span>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div class="yh-card table-card" data-testid="entry-switch-card">
      <div class="status">页面入口</div>
      <p class="meta entry-note">写菜谱、草稿箱、采购清单分享的紧急关停，与上方 AI 开关互不影响。</p>
      <el-table :data="entryRows" row-key="category" data-testid="entry-switch-table">
        <el-table-column label="页面入口" prop="label" min-width="120" />
        <el-table-column v-for="p in platforms" :key="p" :label="PLATFORM_LABEL[p]" min-width="240">
          <template #default="{ row }">
            <div v-if="row[p]" class="cell" :class="{ off: !row[p].enabled }" :data-testid="`entry-switch-${row[p].key}`">
              <span class="dot" :class="row[p].enabled ? 'on' : 'off'" />
              <div class="info">
                <div class="status">{{ row[p].enabled ? '运行中' : '已暂停' }}</div>
                <div class="meta">
                  {{ lastOperationText(row[p], p, row.category) }}<template v-if="row[p].reason"> · {{ row[p].reason }}</template>
                </div>
              </div>
              <el-button
                size="small"
                :type="row[p].enabled ? 'danger' : 'primary'"
                :loading="savingKey === row[p].key"
                @click="toggle(row[p])"
              >{{ row[p].enabled ? '关停' : '恢复' }}</el-button>
            </div>
            <span v-else class="meta">—</span>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div class="yh-card recent" data-testid="feature-switch-recent">
      <div class="status">最近修改记录</div>
      <div v-if="!recent.length" class="meta">暂无记录</div>
      <ul v-else class="logs">
        <li v-for="(c, i) in recent" :key="i">
          <span :class="c.enabled ? 'tag-on' : 'tag-off'">{{ c.enabled ? '恢复' : '关停' }}</span>
          <span class="at">{{ PLATFORM_LABEL[c.platform] }}{{ c.label }}</span>
          <span>{{ c.updatedByName || `用户 #${c.updatedBy}` }}</span>
          <span class="at">{{ fmtAt(c.updatedAt) }}</span>
          <span v-if="c.reason" class="reason">{{ c.reason }}</span>
        </li>
      </ul>
    </div>
  </div>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-width: 960px;
}
.title {
  margin: 0;
  color: var(--yh-title);
}
.desc {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--yh-body);
}
.err {
  padding: 12px 16px;
  color: var(--yh-error-text);
}
.batch {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  padding: 12px 16px;
}
.batch-title {
  font-weight: 700;
  color: var(--yh-title);
  margin-right: 8px;
}
.table-card,
.recent {
  padding: 12px 16px;
}
.entry-note {
  margin: 4px 0 12px;
}
.cell {
  display: flex;
  align-items: center;
  gap: 10px;
}
.dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex: none;
}
.dot.on {
  background: var(--yh-success);
}
.dot.off {
  background: var(--yh-error);
}
.info {
  flex: 1;
  min-width: 0;
}
.status {
  font-weight: 700;
  color: var(--yh-title);
}
.cell.off .status {
  color: var(--yh-error-text);
}
.meta {
  font-size: 12px;
  color: var(--yh-body);
}
.logs {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
  font-size: 12px;
  color: var(--yh-body);
}
.logs li {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.tag-on {
  color: var(--yh-success);
  font-weight: 700;
}
.tag-off {
  color: var(--yh-error-text);
  font-weight: 700;
}
.at {
  color: var(--yh-title);
}
.reason {
  flex-basis: 100%;
}
</style>
