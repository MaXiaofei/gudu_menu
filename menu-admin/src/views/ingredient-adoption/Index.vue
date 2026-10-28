<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  addAlias, adoptIngredient, deleteAlias, listAdoptionGroups, listAliases, mergeIngredient,
  type AdoptionGroup, type AliasItem,
} from '@/api/ingredientAdoption'
import { listIngredients, type Ingredient } from '@/api/ingredient'
import { listByGroup, type DictItem } from '@/api/dict'
import { categorySummary, defaultCategoryId, filterGroups, formatTime, resultMessage } from '@/utils/ingredientAdoption'
import Pagination from '@/components/Pagination.vue'

/**
 * 家庭新建食材（运营收编）：
 * 1) 按规整后的名称汇总所有家庭私有食材（家庭数降序），可「收编为公共食材」或「合并到已有公共食材」；
 * 2) 同页「食材别名」维护：列表 + 新增 + 删除。别名只指向公共食材。
 */
type Tab = 'groups' | 'aliases'
const tab = ref<Tab>('groups')

// ---------------------------------------------------------------- 汇总
const groups = ref<AdoptionGroup[]>([])
const keyword = ref('')
const loading = ref(false)
const shown = computed(() => filterGroups(groups.value, keyword.value))

const purchaseOptions = ref<DictItem[]>([])
const publicList = ref<Ingredient[]>([])

async function loadGroups() {
  loading.value = true
  try {
    groups.value = (await listAdoptionGroups()) || []
  } catch {
    groups.value = []
  } finally {
    loading.value = false
  }
}

// 收编弹窗
const adoptVisible = ref(false)
const adoptTarget = ref<AdoptionGroup | null>(null)
const adoptForm = reactive<{ name: string; purchaseCategoryId: number | null; reason: string }>({
  name: '', purchaseCategoryId: null, reason: '',
})
const submitting = ref(false)

function openAdopt(g: AdoptionGroup) {
  adoptTarget.value = g
  adoptForm.name = g.name
  adoptForm.purchaseCategoryId = defaultCategoryId(g)
  adoptForm.reason = ''
  adoptVisible.value = true
}

async function confirmAdopt() {
  if (!adoptTarget.value) return
  if (!adoptForm.name.trim()) {
    ElMessage.warning('请填写公共食材名称')
    return
  }
  submitting.value = true
  try {
    const r = await adoptIngredient({
      groupName: adoptTarget.value.name, name: adoptForm.name.trim(),
      purchaseCategoryId: adoptForm.purchaseCategoryId, reason: adoptForm.reason.trim() || undefined,
    })
    ElMessage.success(resultMessage(r))
    adoptVisible.value = false
    await loadGroups()
  } finally {
    submitting.value = false
  }
}

// 合并弹窗
const mergeVisible = ref(false)
const mergeTarget = ref<AdoptionGroup | null>(null)
const mergeForm = reactive<{ targetId: number | null; reason: string }>({ targetId: null, reason: '' })

function openMerge(g: AdoptionGroup) {
  mergeTarget.value = g
  mergeForm.targetId = null
  mergeForm.reason = ''
  mergeVisible.value = true
}

async function confirmMerge() {
  if (!mergeTarget.value || mergeForm.targetId == null) {
    ElMessage.warning('请选择要合并到的公共食材')
    return
  }
  submitting.value = true
  try {
    const r = await mergeIngredient({
      groupName: mergeTarget.value.name, targetId: mergeForm.targetId, reason: mergeForm.reason.trim() || undefined,
    })
    ElMessage.success(resultMessage(r))
    mergeVisible.value = false
    await loadGroups()
  } finally {
    submitting.value = false
  }
}

// ---------------------------------------------------------------- 别名
const aliasKeyword = ref('')
const aliasList = ref<AliasItem[]>([])
const aliasTotal = ref(0)
const aliasPage = ref(1)
const aliasPageSize = 15
const aliasLoading = ref(false)
const aliasForm = reactive<{ alias: string; ingredientId: number | null }>({ alias: '', ingredientId: null })

async function loadAliases() {
  aliasLoading.value = true
  try {
    const r = await listAliases({ keyword: aliasKeyword.value.trim() || undefined, pageNum: aliasPage.value, pageSize: aliasPageSize })
    aliasList.value = r?.records || []
    aliasTotal.value = r?.total || 0
  } catch {
    aliasList.value = []
  } finally {
    aliasLoading.value = false
  }
}

function onAliasSearch() {
  aliasPage.value = 1
  loadAliases()
}

async function onAddAlias() {
  if (!aliasForm.alias.trim() || aliasForm.ingredientId == null) {
    ElMessage.warning('请填写别名并选择公共食材')
    return
  }
  await addAlias({ alias: aliasForm.alias.trim(), ingredientId: aliasForm.ingredientId })
  ElMessage.success('已添加别名')
  aliasForm.alias = ''
  aliasForm.ingredientId = null
  await loadAliases()
}

async function onDeleteAlias(row: AliasItem) {
  await ElMessageBox.confirm(`删除别名「${row.alias}」？之后搜它将不再命中「${row.ingredientName || ''}」`, '删除别名', { type: 'warning' })
  await deleteAlias(row.id)
  ElMessage.success('已删除')
  await loadAliases()
}

function switchTab(t: Tab) {
  tab.value = t
  if (t === 'aliases' && !aliasList.value.length) loadAliases()
}

onMounted(async () => {
  loadGroups()
  loadAliases()
  purchaseOptions.value = await listByGroup('purchase_category').catch(() => [])
  publicList.value = await listIngredients().catch(() => [])
})
</script>

<template>
  <div class="yh-page">
    <div class="yh-head">
      <div>
        <h1>家庭新建食材</h1>
        <div class="yh-sub">家庭自己新建的食材只在本家可见；这里按名称汇总，高频的收编为公共食材，或合并到已有公共食材（名称记为别名）</div>
      </div>
      <el-button @click="tab === 'groups' ? loadGroups() : loadAliases()">刷新</el-button>
    </div>

    <div class="yh-seg" role="group" aria-label="页签">
      <button type="button" :class="{ on: tab === 'groups' }" data-testid="tab-groups" @click="switchTab('groups')">家庭新建食材</button>
      <button type="button" :class="{ on: tab === 'aliases' }" data-testid="tab-aliases" @click="switchTab('aliases')">食材别名</button>
    </div>

    <section v-if="tab === 'groups'" class="yh-card">
      <div class="toolbar">
        <el-input v-model="keyword" placeholder="按名称筛选" clearable style="width: 220px" data-testid="group-keyword" />
        <span class="muted">共 {{ shown.length }} 个名称</span>
      </div>
      <el-table v-loading="loading" :data="shown" data-testid="group-table">
        <el-table-column prop="name" label="名称" min-width="140" />
        <el-table-column prop="familyCount" label="家庭数" width="90" sortable />
        <el-table-column label="最早创建" width="150">
          <template #default="{ row }">{{ formatTime(row.earliest) }}</template>
        </el-table-column>
        <el-table-column label="各家所选分类" min-width="200">
          <template #default="{ row }">{{ categorySummary(row.categories) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" data-testid="btn-adopt" @click="openAdopt(row)">收编为公共食材</el-button>
            <el-button size="small" data-testid="btn-merge" @click="openMerge(row)">合并到已有</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <section v-else class="yh-card">
      <div class="toolbar">
        <el-input v-model="aliasKeyword" placeholder="搜别名或食材名" clearable style="width: 220px" @keyup.enter="onAliasSearch" @clear="onAliasSearch" />
        <el-button @click="onAliasSearch">搜索</el-button>
        <span class="spacer" />
        <el-input v-model="aliasForm.alias" placeholder="新别名，如 洋芋" style="width: 160px" data-testid="alias-input" />
        <el-select v-model="aliasForm.ingredientId" filterable placeholder="指向的公共食材" style="width: 200px" data-testid="alias-target">
          <el-option v-for="i in publicList" :key="i.id" :label="i.name" :value="i.id" />
        </el-select>
        <el-button type="primary" data-testid="alias-add" @click="onAddAlias">新增</el-button>
      </div>
      <el-table v-loading="aliasLoading" :data="aliasList" data-testid="alias-table">
        <el-table-column prop="alias" label="别名" min-width="140" />
        <el-table-column label="公共食材" min-width="140">
          <template #default="{ row }">{{ row.ingredientName || `#${row.ingredientId}` }}</template>
        </el-table-column>
        <el-table-column label="创建时间" width="150">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button size="small" type="danger" link data-testid="alias-del" @click="onDeleteAlias(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <Pagination :total="aliasTotal" :current-page="aliasPage" :page-size="aliasPageSize" @current-change="(p: number) => { aliasPage = p; loadAliases() }" />
    </section>

    <el-dialog v-model="adoptVisible" title="收编为公共食材" width="460px">
      <p class="hint">将生成一个公共食材，并把「{{ adoptTarget?.name }}」下 {{ adoptTarget?.ingredientCount }} 个家庭食材全部合并过去；各家写法与新名称不同的会记为别名。</p>
      <el-form label-width="90px">
        <el-form-item label="公共名称">
          <el-input v-model="adoptForm.name" data-testid="adopt-name" />
        </el-form-item>
        <el-form-item label="采购分类">
          <el-select v-model="adoptForm.purchaseCategoryId" placeholder="默认取多数家庭所选" clearable data-testid="adopt-category">
            <el-option v-for="p in purchaseOptions" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="adoptForm.reason" placeholder="选填，写入操作日志" maxlength="200" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="adoptVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" data-testid="adopt-confirm" @click="confirmAdopt">确认收编</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="mergeVisible" title="合并到已有公共食材" width="460px">
      <p class="hint">把「{{ mergeTarget?.name }}」下 {{ mergeTarget?.ingredientCount }} 个家庭食材合并到所选公共食材，名称记为它的别名。</p>
      <el-form label-width="90px">
        <el-form-item label="公共食材">
          <el-select v-model="mergeForm.targetId" filterable placeholder="搜索并选择" data-testid="merge-target">
            <el-option v-for="i in publicList" :key="i.id" :label="i.name" :value="i.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="mergeForm.reason" placeholder="选填，写入操作日志" maxlength="200" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="mergeVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" data-testid="merge-confirm" @click="confirmMerge">确认合并</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.yh-seg {
  margin-bottom: 12px;
}
.toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.spacer {
  flex: 1;
}
.muted {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.hint {
  margin: 0 0 12px;
  color: var(--el-text-color-regular);
  font-size: 13px;
}
</style>
