<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listAiCallLogs, type AiCallLog } from '@/api/ai'
import { getOpsHealth, rebuildDishVectors, type HealthItem } from '@/api/ops'
import { sceneLabel } from '@/utils/aiStats'
import { relativeTime } from '@/utils/feedback'
import { isSuperAdminStored } from '@/utils/auth-storage'
import HealthCard from '@/components/HealthCard.vue'

/**
 * 系统健康：服务探测来自运营可读的 /ops/health（应用 / MySQL / Redis / 语义搜索 / AI）。
 * 「最近失败的 AI 调用」「重建语义检索」仅超管可见，运营不发这些请求。
 */
const router = useRouter()
const isSuper = isSuperAdminStored()

const items = ref<HealthItem[]>([])
const healthLoaded = ref(false)
const healthFailed = ref(false)
const failures = ref<AiCallLog[]>([])
const failuresLoaded = ref(false)
const rebuilding = ref(false)

const cards = computed(() =>
  items.value.map((i) => ({
    key: i.key,
    title: i.label,
    status: i.status,
    detail:
      i.status === 'ok'
        ? [i.detail, `${i.latencyMs} 毫秒`].filter(Boolean).join(' · ')
        : `${i.error || '探测失败'} · ${i.latencyMs} 毫秒`,
  })),
)

async function loadHealth() {
  try {
    const r = await getOpsHealth()
    items.value = Array.isArray(r?.items) ? r.items : []
    healthFailed.value = false
  } catch {
    healthFailed.value = true
  } finally {
    healthLoaded.value = true
  }
}

async function loadFailures() {
  try {
    const f = await listAiCallLogs({ pageNum: 1, pageSize: 5, status: 'fail' })
    failures.value = Array.isArray(f?.records) ? f.records : []
  } catch {
    failures.value = []
  } finally {
    failuresLoaded.value = true
  }
}

async function onRebuild() {
  try {
    await ElMessageBox.confirm(
      '将对全部菜谱重新生成语义检索向量，耗时较长且会调用嵌入模型，确定执行吗？',
      '重建语义检索',
      { type: 'warning', confirmButtonText: '确定重建', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  rebuilding.value = true
  try {
    const r = await rebuildDishVectors()
    if (r.error) ElMessage.warning(`已重建 ${r.ok}/${r.total} 道，首个失败原因：${r.error}`)
    else ElMessage.success(`重建完成，共 ${r.total} 道，成功 ${r.ok} 道`)
    await loadHealth()
  } catch {
    // 失败提示由请求拦截器统一弹出
  } finally {
    rebuilding.value = false
  }
}

onMounted(() => {
  loadHealth()
  if (isSuper) loadFailures()
})
</script>

<template>
  <div class="yh-page">
    <div class="yh-head">
      <div>
        <h1>系统健康</h1>
        <div class="yh-sub">服务探测 · 操作日志</div>
      </div>
      <el-button v-if="isSuper" :loading="rebuilding" data-testid="btn-rebuild" @click="onRebuild">重建语义检索</el-button>
    </div>

    <el-alert v-if="healthFailed" type="error" :closable="false" title="健康摘要读取失败，请稍后重试" data-testid="ops-health-failed" />
    <div v-else class="health-grid" data-testid="ops-cards">
      <HealthCard v-for="c in cards" :key="c.key" :title="c.title" :status="c.status" :detail="c.detail" :data-testid="`ops-${c.key}`" />
      <el-skeleton v-if="!healthLoaded" :rows="2" animated />
    </div>

    <div class="grid">
      <section v-if="isSuper" class="yh-card errors" aria-label="最近失败的 AI 调用">
        <div class="yh-card-title">最近失败的 AI 调用</div>
        <div v-if="failures.length" class="fail-list" data-testid="ops-failures">
          <div v-for="f in failures" :key="f.id" class="fail-row">
            <span class="yh-tag err">失败</span>
            <span class="f-main">
              <b>{{ sceneLabel(f.scene) }}</b>
              <span class="yh-muted">{{ f.errorMsg || '未记录错误信息' }}</span>
            </span>
            <span class="yh-muted">{{ relativeTime(f.createTime) }}</span>
          </div>
        </div>
        <el-empty v-else-if="failuresLoaded" description="近期没有失败的 AI 调用" :image-size="64" />
        <el-skeleton v-else :rows="3" animated />
      </section>

      <section class="yh-card" aria-label="操作日志">
        <div class="yh-card-title">操作日志</div>
        <p class="yh-muted">后台写操作的审计记录在操作日志页查看。</p>
        <el-button type="primary" plain data-testid="goto-oplog" @click="router.push('/oplog')">前往操作日志</el-button>
      </section>
    </div>
  </div>
</template>

<style scoped>
.health-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 14px;
}
.grid {
  display: grid;
  grid-template-columns: minmax(0, 1.5fr) minmax(320px, 1fr);
  gap: 16px;
  align-items: start;
}
@media (max-width: 1100px) {
  .grid {
    grid-template-columns: 1fr;
  }
}
.errors {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.fail-list {
  display: flex;
  flex-direction: column;
}
.fail-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px solid var(--yh-border);
}
.fail-row:last-child {
  border-bottom: none;
}
.f-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.f-main b {
  color: var(--yh-title);
  font-size: 14px;
}
.f-main .yh-muted {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
