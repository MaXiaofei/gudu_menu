<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getCommunitySwitch, setCommunitySwitch, type CommunitySwitchState } from '@/api/communityAdmin'
import { confirmCopy, lastOperationText, switchStatusText, validateSwitchReason } from '@/utils/communitySwitch'

/**
 * 厨房圈紧急关停开关（mini-release-compliance A.12）：只用于出现内容风险时一键关闭。
 * 切换前二次确认（关停必须填原因），展示当前状态、最后操作人与最近修改记录。
 */
const state = ref<CommunitySwitchState | null>(null)
const loadError = ref('')
const saving = ref(false)

async function load() {
  loadError.value = ''
  try {
    state.value = await getCommunitySwitch()
  } catch (e) {
    loadError.value = (e as Error)?.message || '开关状态加载失败'
  }
}

async function toggle() {
  if (!state.value) return
  const next = !state.value.enabled
  const copy = confirmCopy(next)
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
        inputPlaceholder: '关停原因（必填，例如：出现违规图片扩散）',
        inputValidator: (v: string) => validateSwitchReason(false, v) || true,
      })
      reason = (r.value ?? '').trim()
    }
  } catch {
    return
  }
  saving.value = true
  try {
    state.value = await setCommunitySwitch({ enabled: next, reason: reason || undefined })
    ElMessage.success(next ? '厨房圈已恢复' : '厨房圈已暂停，最迟 30 秒全部生效')
  } finally {
    saving.value = false
  }
}

const fmtAt = (s: string | null) => (s ? s.replace('T', ' ').slice(0, 16) : '—')

onMounted(load)
</script>

<template>
  <div class="yh-card switch-bar" :class="{ off: state && !state.enabled }" data-testid="community-switch">
    <template v-if="state">
      <span class="dot" :class="state.enabled ? 'on' : 'off'" />
      <div class="info">
        <div class="status" data-testid="community-switch-status">{{ switchStatusText(state.enabled) }}</div>
        <div class="meta" data-testid="community-switch-last">
          {{ lastOperationText(state) }}<template v-if="state.reason"> · {{ state.reason }}</template>
        </div>
      </div>
      <el-popover v-if="state.recentChanges?.length" placement="bottom-end" :width="360" trigger="click">
        <template #reference>
          <el-button link>修改记录</el-button>
        </template>
        <ul class="logs">
          <li v-for="(c, i) in state.recentChanges" :key="i">
            <span :class="c.enabled ? 'tag-on' : 'tag-off'">{{ c.enabled ? '恢复' : '关停' }}</span>
            <span>{{ c.updatedByName || `用户 #${c.updatedBy}` }}</span>
            <span class="at">{{ fmtAt(c.updatedAt) }}</span>
            <span v-if="c.reason" class="reason">{{ c.reason }}</span>
          </li>
        </ul>
      </el-popover>
      <el-button
        :type="state.enabled ? 'danger' : 'primary'"
        :loading="saving"
        data-testid="community-switch-toggle"
        @click="toggle"
      >{{ state.enabled ? '紧急关停' : '恢复厨房圈' }}</el-button>
    </template>
    <template v-else>
      <span class="meta">{{ loadError || '正在读取厨房圈开关…' }}</span>
      <el-button v-if="loadError" link @click="load">重试</el-button>
    </template>
  </div>
</template>

<style scoped>
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
