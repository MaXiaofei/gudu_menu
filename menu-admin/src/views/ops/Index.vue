<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { getAiUsage, listAiCallLogs, type AiCallLog } from '@/api/ai'
import { sceneLabel, summarizeUsage, type UsageRow } from '@/utils/aiStats'
import { relativeTime } from '@/utils/feedback'
import HealthCard from '@/components/HealthCard.vue'
import PendingApi from '@/components/PendingApi.vue'

/**
 * 系统健康（原型 AdminOps）。API / MySQL / Redis / 语义搜索 / 迁移一致性、按报错编号聚合、审计日志
 * 都依赖新接口（prototype-ui-rollout 3.9、3.10）；当前只有 AI 服务能用真实数据（近 24 小时失败率 + 最近失败调用）。
 */
const usage = ref<UsageRow[] | null>(null)
const failures = ref<AiCallLog[]>([])
const loaded = ref(false)

const ai = computed(() => {
  if (!usage.value) return { status: 'pending' as const, detail: '读取失败' }
  const s = summarizeUsage(usage.value)
  if (s.totalCalls === 0) return { status: 'ok' as const, detail: '近 24 小时无调用' }
  const failRate = s.failCalls / s.totalCalls
  const status = failRate > 0.2 ? ('err' as const) : failRate > 0.05 ? ('warn' as const) : ('ok' as const)
  return { status, detail: `近 24 小时 ${s.totalCalls} 次 · 失败率 ${(failRate * 100).toFixed(1)}%` }
})

onMounted(async () => {
  const [u, f] = await Promise.allSettled([
    getAiUsage(1),
    listAiCallLogs({ pageNum: 1, pageSize: 5, status: 'fail' }),
  ])
  usage.value = u.status === 'fulfilled' && Array.isArray(u.value) ? u.value : null
  failures.value = f.status === 'fulfilled' && Array.isArray(f.value?.records) ? f.value.records : []
  loaded.value = true
})
</script>

<template>
  <div class="yh-page">
    <div class="yh-head">
      <div>
        <h1>系统健康</h1>
        <div class="yh-sub">服务状态、按报错编号聚合的错误与后台操作审计；健康检查接口上线前，仅 AI 服务有实时数据</div>
      </div>
    </div>

    <div class="health-grid">
      <HealthCard title="API 服务" status="pending" detail="需要健康检查接口（P95 / 错误率）" />
      <HealthCard title="MySQL" status="pending" detail="需要连接数与最近备份时间" />
      <HealthCard title="Redis" status="pending" detail="需要内存与连通性" />
      <HealthCard title="语义搜索" status="pending" detail="需要向量检索 P95" />
      <HealthCard title="AI 服务" :status="ai.status" :detail="ai.detail" data-testid="ops-ai" />
    </div>

    <div class="grid">
      <section class="yh-card errors" aria-label="最近失败的 AI 调用">
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
        <el-empty v-else-if="loaded" description="近期没有失败的 AI 调用" :image-size="64" />
        <el-skeleton v-else :rows="3" animated />
        <PendingApi compact title="全站报错聚合待上线" needs="按报错编号（traceId）聚合的接口错误" task="prototype-ui-rollout 3.10" />
      </section>

      <section class="yh-card" aria-label="审计日志">
        <div class="yh-card-title">审计日志</div>
        <PendingApi compact needs="后台写操作审计记录（audit_log）" task="prototype-ui-rollout 3.9" />
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
