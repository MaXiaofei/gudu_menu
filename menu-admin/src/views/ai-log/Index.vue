<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { request } from '@/api/request'
import { ElMessage } from 'element-plus'

// ===== 汇总 =====
interface UsageRow {
  scene: string
  totalCalls: number
  failCalls: number
  tokensIn: number
  tokensOut: number
  latencyAvgMs: number
}
const usage = ref<UsageRow[]>([])
const usageDays = ref(7)

const sceneLabel = (s: string) => ({
  nutrition_fill: '营养补全',
  menu_recommend: '菜单推荐',
  dish_estimate: '菜品估营养',
}[s] || s)

const totalTokensIn = computed(() => usage.value.reduce((s, r) => s + r.tokensIn, 0))
const totalTokensOut = computed(() => usage.value.reduce((s, r) => s + r.tokensOut, 0))
const totalCalls = computed(() => usage.value.reduce((s, r) => s + r.totalCalls, 0))
const totalFails = computed(() => usage.value.reduce((s, r) => s + r.failCalls, 0))

async function loadUsage() {
  try {
    usage.value = await request({ url: '/ai/usage', method: 'GET', data: { days: usageDays.value } })
  } catch (e: any) {
    ElMessage.error('加载用量失败')
  }
}

// ===== 明细列表 =====
interface CallLog {
  id: number
  scene: string
  memberId: number
  request: string
  response: string
  tokensIn: number
  tokensOut: number
  cost: number
  provider: string
  latencyMs: number
  status: string
  errorMsg: string | null
  createTime: string
}

const logs = ref<CallLog[]>([])
const total = ref(0)
const loading = ref(false)
const query = ref({
  pageNum: 1,
  pageSize: 15,
  scene: '' as string,
  status: '' as string,
})

async function loadLogs() {
  loading.value = true
  try {
    const params: any = { pageNum: query.value.pageNum, pageSize: query.value.pageSize }
    if (query.value.scene) params.scene = query.value.scene
    if (query.value.status) params.status = query.value.status
    const res = await request<{ records: CallLog[]; total: number }>({
      url: '/ai/call-log', method: 'GET', data: params,
    })
    logs.value = res.records || []
    total.value = res.total || 0
  } catch (e: any) {
    ElMessage.error('加载日志失败')
  } finally {
    loading.value = false
  }
}

// ===== 详情弹窗 =====
const detailVisible = ref(false)
const detailLog = ref<CallLog | null>(null)

function showDetail(row: CallLog) {
  detailLog.value = row
  detailVisible.value = true
}

function formatJson(str: string | null) {
  if (!str) return '(空)'
  try {
    return JSON.stringify(JSON.parse(str), null, 2)
  } catch {
    return str
  }
}

function statusType(s: string) {
  return s === 'ok' ? 'success' : 'danger'
}

onMounted(() => {
  loadUsage()
  loadLogs()
})
</script>

<template>
  <div class="ai-log">
    <!-- 汇总卡片 -->
    <el-row :gutter="12" class="summary-row">
      <el-col :span="6">
        <el-card shadow="never">
          <div class="stat-card">
            <div class="stat-value">{{ totalCalls }}</div>
            <div class="stat-label">总调用</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="stat-card">
            <div class="stat-value" style="color: #f56c6c">{{ totalFails }}</div>
            <div class="stat-label">失败次数</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="stat-card">
            <div class="stat-value" style="color: #e6a23c">{{ totalTokensIn.toLocaleString() }}</div>
            <div class="stat-label">输入 Token</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="stat-card">
            <div class="stat-value" style="color: #67c23a">{{ totalTokensOut.toLocaleString() }}</div>
            <div class="stat-label">输出 Token</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 按场景汇总 -->
    <el-card shadow="never" class="section">
      <template #header>
        <div class="card-header">
          <span>按场景汇总</span>
          <el-select v-model="usageDays" size="small" style="width: 100px" @change="loadUsage">
            <el-option :value="1" label="近 1 天" />
            <el-option :value="7" label="近 7 天" />
            <el-option :value="30" label="近 30 天" />
          </el-select>
        </div>
      </template>
      <el-table :data="usage" size="small">
        <el-table-column label="场景" width="120">
          <template #default="{ row }">{{ sceneLabel(row.scene) }}</template>
        </el-table-column>
        <el-table-column prop="totalCalls" label="调用次数" width="90" />
        <el-table-column label="失败" width="70">
          <template #default="{ row }">
            <span :style="{ color: row.failCalls > 0 ? '#f56c6c' : '' }">{{ row.failCalls }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="tokensIn" label="输入 Token" width="110" />
        <el-table-column prop="tokensOut" label="输出 Token" width="110" />
        <el-table-column prop="latencyAvgMs" label="均延迟" width="90">
          <template #default="{ row }">{{ row.latencyAvgMs }}ms</template>
        </el-table-column>
        <el-table-column label="成功率">
          <template #default="{ row }">
            <el-progress
              :percentage="row.totalCalls > 0 ? Math.round((1 - row.failCalls / row.totalCalls) * 100) : 0"
              :stroke-width="8"
              :status="row.failCalls === 0 ? 'success' : ''"
            />
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 明细列表 -->
    <el-card shadow="never" class="section">
      <template #header>
        <div class="card-header">
          <span>调用明细</span>
          <div class="filters">
            <el-select v-model="query.scene" placeholder="全部场景" clearable size="small" style="width: 130px" @change="loadLogs">
              <el-option value="nutrition_fill" label="营养补全" />
              <el-option value="menu_recommend" label="菜单推荐" />
              <el-option value="dish_estimate" label="菜品估营养" />
            </el-select>
            <el-select v-model="query.status" placeholder="全部状态" clearable size="small" style="width: 110px; margin-left: 8px" @change="loadLogs">
              <el-option value="ok" label="成功" />
              <el-option value="fail" label="失败" />
            </el-select>
          </div>
        </div>
      </template>
      <el-table :data="logs" v-loading="loading" size="small" @row-click="showDetail" highlight-current-row>
        <el-table-column prop="createTime" label="时间" width="160" />
        <el-table-column label="场景" width="110">
          <template #default="{ row }">{{ sceneLabel(row.scene) }}</template>
        </el-table-column>
        <el-table-column prop="provider" label="模型" width="90" />
        <el-table-column label="状态" width="70">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ row.status === 'ok' ? '成功' : '失败' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="tokensIn" label="入Token" width="80" />
        <el-table-column prop="tokensOut" label="出Token" width="80" />
        <el-table-column prop="latencyMs" label="延迟" width="70">
          <template #default="{ row }">{{ row.latencyMs }}ms</template>
        </el-table-column>
        <el-table-column label="请求摘要" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span :style="{ color: row.status === 'fail' ? '#f56c6c' : '' }">
              {{ row.errorMsg ? row.errorMsg : (row.request || '').substring(0, 80) }}
            </span>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        :total="total"
        :page-sizes="[20, 50, 100]"
        layout="total, sizes, prev, pager, next"
        small
        style="margin-top: 12px; justify-content: flex-end"
        @current-change="loadLogs"
        @size-change="loadLogs"
      />
    </el-card>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" title="调用详情" width="700px" top="5vh">
      <template v-if="detailLog">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="时间">{{ detailLog.createTime }}</el-descriptions-item>
          <el-descriptions-item label="场景">{{ sceneLabel(detailLog.scene) }}</el-descriptions-item>
          <el-descriptions-item label="模型">{{ detailLog.provider }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusType(detailLog.status)" size="small">{{ detailLog.status === 'ok' ? '成功' : '失败' }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="输入 Token">{{ detailLog.tokensIn }}</el-descriptions-item>
          <el-descriptions-item label="输出 Token">{{ detailLog.tokensOut }}</el-descriptions-item>
          <el-descriptions-item label="延迟">{{ detailLog.latencyMs }}ms</el-descriptions-item>
          <el-descriptions-item label="成员 ID">{{ detailLog.memberId }}</el-descriptions-item>
          <el-descriptions-item v-if="detailLog.errorMsg" label="错误信息" :span="2">
            <span style="color: #f56c6c">{{ detailLog.errorMsg }}</span>
          </el-descriptions-item>
        </el-descriptions>

        <el-divider content-position="left">请求内容（发送给模型的）</el-divider>
        <el-input type="textarea" :model-value="formatJson(detailLog.request)" readonly :rows="5" />

        <el-divider content-position="left">响应内容（模型返回的）</el-divider>
        <el-input type="textarea" :model-value="formatJson(detailLog.response)" readonly :rows="5" />
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.summary-row { margin-bottom: 16px; }
.stat-card { text-align: center; padding: 8px 0; }
.stat-value { font-size: 28px; font-weight: bold; color: #409eff; }
.stat-label { font-size: 13px; color: #909399; margin-top: 4px; }
.section { margin-bottom: 16px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.filters { display: flex; }
</style>
