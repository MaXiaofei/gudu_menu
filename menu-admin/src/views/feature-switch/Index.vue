<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listFeatureSwitches, setFeatureSwitch, type FeatureSwitchState } from '@/api/featureSwitch'
import { featureConfirmCopy, featureStatusText, lastOperationText, validateSwitchReason } from '@/utils/featureSwitch'

/**
 * 功能开关：咕嘟助手、问咕嘟的紧急关停。
 * 只用于出现内容风险时一键关闭（默认开启，提审版本即线上版本）；关停必须填原因，切换留痕。
 */
const states = ref<FeatureSwitchState[]>([])
const loadError = ref('')
const loading = ref(false)
const savingKey = ref('')

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

async function toggle(s: FeatureSwitchState) {
  const next = !s.enabled
  const copy = featureConfirmCopy(s.label, next)
  let reason = ''
  try {
    if (next) {
      await ElMessageBox.confirm(copy.message, copy.title, {
        confirmButtonText: copy.confirm,
        cancelButtonText: '取消',
        type: 'info',
      })
    } else {
      const r = await ElMessageBox.prompt(copy.message, copy.title, {
        confirmButtonText: copy.confirm,
        cancelButtonText: '取消',
        type: 'warning',
        inputPlaceholder: '关停原因（必填，例如：出现违规回复）',
        inputValidator: (v: string) => validateSwitchReason(false, v) || true,
      })
      reason = (r.value ?? '').trim()
    }
  } catch {
    return
  }
  savingKey.value = s.key
  try {
    const updated = await setFeatureSwitch({ key: s.key, enabled: next, reason: reason || undefined })
    states.value = states.value.map((x) => (x.key === updated.key ? updated : x))
    ElMessage.success(next ? `${s.label}已恢复` : `${s.label}已暂停，最迟 15 秒全部生效`)
  } finally {
    savingKey.value = ''
  }
}

const fmtAt = (s: string | null) => (s ? s.replace('T', ' ').slice(0, 16) : '—')

onMounted(load)
</script>

<template>
  <div class="page" v-loading="loading">
    <div class="head">
      <h2 class="title">功能开关</h2>
      <p class="desc">出现内容风险时一键关闭。默认开启，关停后用户侧显示「暂停服务」，其他功能不受影响。</p>
    </div>

    <div v-if="loadError" class="yh-card err">
      <span>{{ loadError }}</span>
      <el-button link @click="load">重试</el-button>
    </div>

    <div
      v-for="s in states"
      :key="s.key"
      class="yh-card switch-bar"
      :class="{ off: !s.enabled }"
      :data-testid="`feature-switch-${s.key}`"
    >
      <span class="dot" :class="s.enabled ? 'on' : 'off'" />
      <div class="info">
        <div class="status">{{ featureStatusText(s.label, s.enabled) }}</div>
        <div class="meta">
          {{ lastOperationText(s) }}<template v-if="s.reason"> · {{ s.reason }}</template>
        </div>
      </div>
      <el-popover v-if="s.recentChanges?.length" placement="bottom-end" :width="360" trigger="click">
        <template #reference>
          <el-button link>修改记录</el-button>
        </template>
        <ul class="logs">
          <li v-for="(c, i) in s.recentChanges" :key="i">
            <span :class="c.enabled ? 'tag-on' : 'tag-off'">{{ c.enabled ? '恢复' : '关停' }}</span>
            <span>{{ c.updatedByName || `用户 #${c.updatedBy}` }}</span>
            <span class="at">{{ fmtAt(c.updatedAt) }}</span>
            <span v-if="c.reason" class="reason">{{ c.reason }}</span>
          </li>
        </ul>
      </el-popover>
      <el-button
        :type="s.enabled ? 'danger' : 'primary'"
        :loading="savingKey === s.key"
        @click="toggle(s)"
      >{{ s.enabled ? '紧急关停' : `恢复${s.label}` }}</el-button>
    </div>
  </div>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-width: 880px;
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
.switch-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
}
.switch-bar.off {
  background: var(--yh-error-soft);
  border: 1px solid var(--yh-error);
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
.switch-bar.off .status {
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
