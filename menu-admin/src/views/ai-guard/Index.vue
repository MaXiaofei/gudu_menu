<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getAiGuard, updateAiGuard, type AiGuardOverview } from '@/api/aiGuard'
import {
  CATEGORY_KEYS,
  CATEGORY_LABEL,
  buildUpdate,
  formFromOverview,
  levelColor,
  levelText,
  limitText,
  logText,
  validateForm,
  yuan,
  type AiGuardForm,
} from '@/utils/aiGuard'

/**
 * AI 用量与额度：今日全站已花费 / 上限 / 百分比（≥提醒比例醒目提醒，≥100% 显示已暂停），
 * 今日各类调用次数，可改限额（改动留痕：操作人、时间、新旧值）。次日自动恢复（北京时间）。
 */
const overview = ref<AiGuardOverview | null>(null)
const form = ref<AiGuardForm | null>(null)
const loadError = ref('')
const loading = ref(false)
const saving = ref(false)

const percentBar = computed(() => Math.min(100, overview.value?.percent ?? 0))
const dirty = computed(() => !!(overview.value && form.value && buildUpdate(formFromOverview(overview.value), form.value)))

async function load() {
  loadError.value = ''
  loading.value = true
  try {
    overview.value = await getAiGuard()
    form.value = formFromOverview(overview.value)
  } catch (e) {
    loadError.value = (e as Error)?.message || '用量加载失败'
  } finally {
    loading.value = false
  }
}

async function save() {
  if (!overview.value || !form.value) return
  const err = validateForm(form.value)
  if (err) {
    ElMessage.error(err)
    return
  }
  const body = buildUpdate(formFromOverview(overview.value), form.value)
  if (!body) return
  saving.value = true
  try {
    overview.value = await updateAiGuard(body)
    form.value = formFromOverview(overview.value)
    ElMessage.success('已保存，最迟 60 秒全部实例生效')
  } finally {
    saving.value = false
  }
}

function reset() {
  if (overview.value) form.value = formFromOverview(overview.value)
}

const fmtAt = (s: string) => s.replace('T', ' ').slice(0, 16)

onMounted(load)
</script>

<template>
  <div class="page" v-loading="loading">
    <div class="head">
      <h2 class="title">AI 用量与额度</h2>
      <p class="desc">每日花费 · 次数上限</p>
    </div>

    <div v-if="loadError" class="yh-card err">
      <span>{{ loadError }}</span>
      <el-button link @click="load">重试</el-button>
    </div>

    <template v-if="overview && form">
      <div class="yh-card budget" :class="overview.level.toLowerCase()" data-testid="ai-guard-budget">
        <div class="row">
          <div>
            <div class="label">今日已花费</div>
            <div class="big">
              {{ yuan(overview.spentYuan) }}
              <span class="of">/ {{ overview.limitYuan > 0 ? yuan(overview.limitYuan) : '不限' }}</span>
            </div>
          </div>
          <div class="pct" data-testid="ai-guard-percent">{{ overview.limitYuan > 0 ? `${overview.percent}%` : '—' }}</div>
        </div>
        <el-progress
          :percentage="percentBar"
          :show-text="false"
          :stroke-width="10"
          :color="levelColor(overview.level)"
        />
        <div class="status" :class="overview.level.toLowerCase()" data-testid="ai-guard-status">
          {{ levelText(overview.level, overview.percent) }}
        </div>
      </div>

      <div class="yh-card" data-testid="ai-guard-usage">
        <div class="section">今日各类调用次数</div>
        <el-table :data="overview.categories" row-key="key">
          <el-table-column label="类别" prop="label" min-width="120" />
          <el-table-column label="今日全站次数" min-width="120">
            <template #default="{ row }">{{ overview.countsAvailable ? row.todayCount : '—' }}</template>
          </el-table-column>
          <el-table-column label="每账号每日上限" min-width="140">
            <template #default="{ row }">{{ limitText(row.limitPerAccount, '次') }}</template>
          </el-table-column>
        </el-table>
        <div v-if="!overview.countsAvailable" class="meta">次数统计暂时读不到（计数服务异常），花费不受影响。</div>
      </div>

      <div class="yh-card" data-testid="ai-guard-form">
        <div class="section">额度设置</div>
        <el-form label-width="150px" class="form" @submit.prevent>
          <el-form-item label="每日花费上限（元）">
            <el-input-number v-model="form.dailyBudgetYuan" :min="0" :max="1000000" :precision="2" :step="5" />
            <span class="hint">0 = 不限</span>
          </el-form-item>
          <el-form-item label="提醒比例（%）">
            <el-input-number v-model="form.warnPercent" :min="1" :max="100" :step="5" />
            <span class="hint">达到后本页醒目提醒，后端每天打一次告警日志</span>
          </el-form-item>
          <el-form-item v-for="k in CATEGORY_KEYS" :key="k" :label="`${CATEGORY_LABEL[k]}（次/账号/天）`">
            <el-input-number v-model="form.limits[k]" :min="0" :max="100000" :step="5" :data-testid="`limit-${k}`" />
            <span class="hint">0 = 不限</span>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="saving" :disabled="!dirty" data-testid="ai-guard-save" @click="save">保存</el-button>
            <el-button :disabled="!dirty" @click="reset">还原</el-button>
          </el-form-item>
        </el-form>
      </div>

      <div class="yh-card" data-testid="ai-guard-logs">
        <div class="section">最近修改记录</div>
        <div v-if="!overview.logs.length" class="meta">暂无记录</div>
        <ul v-else class="logs">
          <li v-for="(l, i) in overview.logs" :key="i">
            <span class="at">{{ logText(l) }}</span>
            <span>{{ l.operatorName || `用户 #${l.operatorId}` }}</span>
            <span>{{ fmtAt(l.at) }}</span>
          </li>
        </ul>
      </div>
    </template>
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
.yh-card {
  padding: 12px 16px;
}
.budget {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.budget.warn {
  background: var(--yh-warning-soft);
}
.budget.paused {
  background: var(--yh-error-soft, var(--yh-warning-soft));
}
.row {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
}
.label,
.meta,
.hint {
  font-size: 12px;
  color: var(--yh-body);
}
.hint {
  margin-left: 10px;
}
.big {
  font-size: 28px;
  font-weight: 700;
  color: var(--yh-title);
}
.of {
  font-size: 14px;
  font-weight: 400;
  color: var(--yh-body);
}
.pct {
  font-size: 28px;
  font-weight: 700;
  color: var(--yh-title);
}
.status {
  font-weight: 700;
  color: var(--yh-success);
}
.status.warn {
  color: var(--yh-warning-text);
}
.status.paused {
  color: var(--yh-error-text);
}
.section {
  font-weight: 700;
  color: var(--yh-title);
  margin-bottom: 8px;
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
  gap: 10px;
}
.at {
  color: var(--yh-title);
}
</style>
