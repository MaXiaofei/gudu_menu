<script setup lang="ts">
import { onMounted, reactive, ref, shallowRef } from 'vue'
import { ElMessage } from 'element-plus'
import Pagination from '@/components/Pagination.vue'
import StatCard from '@/components/StatCard.vue'
import { getOpsStats, type OpsStats } from '@/api/ops'
import {
  accountConsents, banAccount, listAccounts, unbanAccount, type AccountItem, type ConsentItem,
} from '@/api/backoffice'
import { formatCount } from '@/utils/dashboard'
import {
  ACCOUNT_STATUS, DOC_TYPE, PLATFORM_ROLE, cleanAccountQuery, label, reasonError,
} from '@/utils/backoffice'

/**
 * 账号与家庭（mini-release-compliance F.1 / F.4）：按昵称 / 账号 ID、openid 尾号、家庭 ID 查账号，
 * 看状态、平台角色、所属家庭数；封禁 / 解封带原因（写操作日志）；查看协议同意记录。
 * 最小可见：不展示任何家庭私有数据（菜单 / 余量 / 采购 / 成员档案）。
 */
const pageSize = 15
const pageNum = ref(1)
const total = ref(0)
const list = ref<AccountItem[]>([])
const loading = ref(false)
const loadError = ref('')
const stats = shallowRef<OpsStats | null>(null)
const query = reactive({ keyword: '', openidTail: '', familyId: '', status: '', bannedOnly: false })

const reasonDialog = reactive({ visible: false, mode: 'ban' as 'ban' | 'unban', account: null as AccountItem | null, reason: '', saving: false })
const consentDrawer = reactive({ visible: false, account: null as AccountItem | null, loading: false, records: [] as ConsentItem[] })

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const res = await listAccounts({ ...cleanAccountQuery(query), pageNum: pageNum.value, pageSize })
    list.value = Array.isArray(res?.records) ? res.records : []
    total.value = res?.total || 0
  } catch (e) {
    loadError.value = (e as Error)?.message || '加载失败'
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function search() {
  pageNum.value = 1
  load()
}

function reset() {
  Object.assign(query, { keyword: '', openidTail: '', familyId: '', status: '', bannedOnly: false })
  search()
}

function onPageChange(p: number) {
  pageNum.value = p
  load()
}

function openReason(mode: 'ban' | 'unban', row: AccountItem) {
  Object.assign(reasonDialog, { visible: true, mode, account: row, reason: '', saving: false })
}

async function submitReason() {
  const err = reasonError(reasonDialog.reason)
  if (err) {
    ElMessage.warning(err)
    return
  }
  const acc = reasonDialog.account
  if (!acc) return
  reasonDialog.saving = true
  try {
    if (reasonDialog.mode === 'ban') {
      await banAccount(acc.id, reasonDialog.reason.trim())
      ElMessage.success('已封禁，该账号已被下线')
    } else {
      await unbanAccount(acc.id, reasonDialog.reason.trim())
      ElMessage.success('已解封')
    }
    reasonDialog.visible = false
    await load()
  } finally {
    reasonDialog.saving = false
  }
}

async function openConsents(row: AccountItem) {
  Object.assign(consentDrawer, { visible: true, account: row, loading: true, records: [] })
  try {
    consentDrawer.records = (await accountConsents(row.id)) || []
  } finally {
    consentDrawer.loading = false
  }
}

function formatTime(s: string | null | undefined) {
  return (s || '').replace('T', ' ').slice(0, 16) || '—'
}

onMounted(async () => {
  load()
  try {
    stats.value = await getOpsStats(7)
  } catch {
    stats.value = null
  }
})
</script>

<template>
  <div class="yh-page">
    <div class="yh-head">
      <div>
        <h1>账号与家庭</h1>
        <div class="yh-sub">只看账号元数据与所属家庭数，不展示家庭的菜单、余量、采购、成员档案（最小可见原则）</div>
      </div>
      <el-button @click="load">刷新</el-button>
    </div>

    <div class="kpis">
      <StatCard label="账号" :value="formatCount(stats?.totals?.accounts)" :hint="stats ? `近 7 天 +${formatCount(stats.last7d?.newAccounts)}` : ''" data-testid="tn-accounts" />
      <StatCard label="成员档案" :value="formatCount(stats?.totals?.members)" hint="含无账号的家人" data-testid="tn-members" />
    </div>

    <section class="yh-card">
      <el-form inline class="filters" data-testid="acc-filters" @submit.prevent="search">
        <el-form-item label="昵称 / ID">
          <el-input v-model="query.keyword" placeholder="昵称包含或账号 ID" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="openid 尾号">
          <el-input v-model="query.openidTail" placeholder="至少 4 位" clearable style="width: 130px" />
        </el-form-item>
        <el-form-item label="家庭 ID">
          <el-input v-model="query.familyId" placeholder="家庭 ID" clearable style="width: 110px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 110px">
            <el-option v-for="(v, k) in ACCOUNT_STATUS" :key="k" :label="v" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-checkbox v-model="query.bannedOnly">只看封禁</el-checkbox>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" native-type="submit">查询</el-button>
          <el-button @click="reset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-alert v-if="loadError" :title="loadError" type="error" show-icon :closable="false" />
      <el-table v-loading="loading" :data="list" data-testid="acc-table">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column label="昵称" min-width="140">
          <template #default="{ row }">{{ row.nickname || '—' }}</template>
        </el-table-column>
        <el-table-column label="手机号" width="130">
          <template #default="{ row }">{{ row.maskedPhone || '—' }}</template>
        </el-table-column>
        <el-table-column label="openid 尾号" width="110">
          <template #default="{ row }">{{ row.openidTail || '—' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="150">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">{{ label(ACCOUNT_STATUS, row.status) }}</el-tag>
            <el-tooltip v-if="row.banned" :content="`原因：${row.banReason}（${formatTime(row.bannedAt)}）`">
              <el-tag type="danger" size="small" class="ban-tag" data-testid="acc-banned">封禁中</el-tag>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column label="平台角色" width="110">
          <template #default="{ row }">{{ label(PLATFORM_ROLE, row.platformRole) }}</template>
        </el-table-column>
        <el-table-column label="所属家庭" width="90">
          <template #default="{ row }">{{ row.familyCount }}</template>
        </el-table-column>
        <el-table-column label="注册时间" width="150">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openConsents(row)">同意记录</el-button>
            <template v-if="row.status === 'ACTIVE' && row.platformRole !== 'SUPER_ADMIN'">
              <el-button v-if="row.banned" link type="success" @click="openReason('unban', row)">解封</el-button>
              <el-button v-else link type="danger" data-testid="acc-ban" @click="openReason('ban', row)">封禁</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
      <Pagination :total="total" :current-page="pageNum" :page-size="pageSize" @current-change="onPageChange" />
    </section>

    <el-dialog v-model="reasonDialog.visible" :title="reasonDialog.mode === 'ban' ? '封禁账号' : '解封账号'" width="460px">
      <p class="yh-muted">
        {{ reasonDialog.account?.nickname || `#${reasonDialog.account?.id}` }}：
        {{ reasonDialog.mode === 'ban' ? '封禁后立即下线，登录与提交操作都会被拒绝，小程序会提示封禁原因。' : '解封后可正常登录使用。' }}
      </p>
      <el-input v-model="reasonDialog.reason" type="textarea" :rows="3" maxlength="200" show-word-limit placeholder="原因（必填，会记入操作日志）" data-testid="reason-input" />
      <template #footer>
        <el-button @click="reasonDialog.visible = false">取消</el-button>
        <el-button :type="reasonDialog.mode === 'ban' ? 'danger' : 'primary'" :loading="reasonDialog.saving" data-testid="reason-submit" @click="submitReason">确定</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="consentDrawer.visible" :title="`同意记录 · ${consentDrawer.account?.nickname || '#' + consentDrawer.account?.id}`" size="520px">
      <el-table v-loading="consentDrawer.loading" :data="consentDrawer.records" data-testid="consent-table">
        <el-table-column label="协议" min-width="130">
          <template #default="{ row }">{{ label(DOC_TYPE, row.docType) }}</template>
        </el-table-column>
        <el-table-column prop="docVersion" label="版本" width="110" />
        <el-table-column label="动作" width="70">
          <template #default="{ row }">
            <el-tag :type="row.granted ? 'success' : 'warning'" size="small">{{ row.granted ? '同意' : '撤回' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="时间" width="140">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="端" width="90">
          <template #default="{ row }">{{ row.channel || '—' }}</template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!consentDrawer.loading && !consentDrawer.records.length" description="暂无同意记录" />
    </el-drawer>
  </div>
</template>

<style scoped>
.kpis {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 14px;
}
.filters {
  margin-bottom: 8px;
}
.ban-tag {
  margin-left: 6px;
}
</style>
