<script setup lang="ts">
import { onMounted, ref, shallowRef } from 'vue'
import { useRouter } from 'vue-router'
import { getOpsStats, type OpsStats } from '@/api/ops'
import { formatCount } from '@/utils/dashboard'
import StatCard from '@/components/StatCard.vue'
import PendingApi from '@/components/PendingApi.vue'

/**
 * 账号与家庭（原型 AdminTenants）。多家庭模型由 multi-tenant-family 落地；
 * 当前只展示 /ops/stats 的账号与成员档案总数，家庭列表、冻结、注销申请待接口（prototype-ui-rollout 2.5 / 3.3）。
 * 后台只看元数据，不展示家庭的余量、食记明细（最小可见原则）。
 */
const router = useRouter()
const stats = shallowRef<OpsStats | null>(null)
const failed = ref(false)

onMounted(async () => {
  try {
    stats.value = await getOpsStats(7)
  } catch {
    failed.value = true
  }
})
</script>

<template>
  <div class="yh-page">
    <div class="yh-head">
      <div>
        <h1>账号与家庭</h1>
        <div class="yh-sub">后台只看元数据，不展示家庭的余量、食记明细（最小可见原则）</div>
      </div>
      <el-button @click="router.push('/member')">查看成员档案</el-button>
    </div>

    <div class="kpis">
      <StatCard
        label="账号"
        :value="formatCount(stats?.totals?.accounts)"
        :hint="stats ? `近 7 天 +${formatCount(stats.last7d?.newAccounts)}` : failed ? '统计加载失败' : ''"
        data-testid="tn-accounts"
      />
      <StatCard label="成员档案" :value="formatCount(stats?.totals?.members)" hint="含无账号的家人" data-testid="tn-members" />
      <StatCard label="家庭" value="—" hint="多家庭模型待上线" data-testid="tn-families" />
      <StatCard label="注销申请" value="—" hint="注销冷静期接口待上线" data-testid="tn-deletion" />
    </div>

    <section class="yh-card">
      <PendingApi
        title="家庭列表待上线"
        needs="家庭（family）与成员角色模型、家庭列表 / 冻结 / 解冻接口、账号注销申请"
        task="multi-tenant-family B–F；prototype-ui-rollout 2.5、3.3"
      >
        <span class="yh-muted">上线后这里按家庭列出主人、成员头像、家庭菜谱数、近 7 天做饭次数与状态</span>
      </PendingApi>
    </section>
  </div>
</template>

<style scoped>
.kpis {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 14px;
}
</style>
