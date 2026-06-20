<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listMembers,
  createMember,
  updateMember,
  deleteMember,
  listPermKeys,
  type Member,
  type HealthProfile,
} from '@/api/member'
import { listByGroup, type DictItem } from '@/api/dict'
import Pagination from '@/components/Pagination.vue'

const loading = ref(false)
const list = ref<Member[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = 10
const audienceOptions = ref<DictItem[]>([])
const genderOptions = ref<DictItem[]>([])
const roleOptions = ref<DictItem[]>([])
/** 全量功能权限 key -> 中文映射（来自后端 /member/permissions/keys）。 */
const permOptions = ref<Record<string, string>>({})

async function load() {
  loading.value = true
  try {
    const page = await listMembers({ pageNum: pageNum.value, pageSize })
    list.value = page.records || []
    total.value = page.total || 0
  } finally {
    loading.value = false
  }
}

function onPageChange(p: number) {
  pageNum.value = p
  load()
}

async function loadDicts() {
  const [a, g, r, p] = await Promise.all([
    listByGroup('audience'),
    listByGroup('gender'),
    listByGroup('role'),
    listPermKeys(),
  ])
  audienceOptions.value = a
  genderOptions.value = g
  roleOptions.value = r
  permOptions.value = p || {}
}

onMounted(() => {
  load()
  loadDicts()
})

const dialogVisible = ref(false)
const editing = ref<Member | null>(null)

/** 把后端 roleTags（逗号分隔 id 串，如 "32,34"）拆成 number[]。 */
function parseRoleIds(s: string | undefined | null): number[] {
  if (!s) return []
  return String(s)
    .split(',')
    .map((x) => Number(x.trim()))
    .filter((n) => !Number.isNaN(n) && n > 0)
}

/** 把 role 字典 id 映射成中文名，逗号拼接。 */
function roleTagsText(s: string | undefined | null): string {
  const ids = parseRoleIds(s)
  if (!ids.length) return '-'
  return ids
    .map((id) => roleOptions.value.find((r) => r.id === id)?.name ?? `#${id}`)
    .join('、')
}

/** 健康档案适用人群：种子已是中文字符串数组（如 ["高血压"]），直接 join 展示。 */
function audienceText(arr: unknown): string {
  if (!Array.isArray(arr) || !arr.length) return ''
  return arr.map((x) => String(x)).join('、')
}

function blankForm() {
  return {
    name: '',
    // 表单内部用 number[]（role 字典 id）承载多选
    roleTags: [] as number[],
    // 小程序功能权限个人勾选（key 数组，null 走角色默认模板）
    mpPermissions: [] as string[],
    healthProfile: {
      height: undefined as number | undefined,
      weight: undefined as number | undefined,
      age: undefined as number | undefined,
      gender: '',
      audiences: [] as string[],
      allergies: [] as string[],
      sugarMax: undefined as number | undefined,
      saltMax: undefined as number | undefined,
    } as HealthProfile,
  }
}

const form = reactive(blankForm())

function resetForm() {
  Object.assign(form, blankForm())
}

function openCreate() {
  editing.value = null
  resetForm()
  dialogVisible.value = true
}

function openEdit(row: Member) {
  editing.value = row
  resetForm()
  form.name = row.name
  form.roleTags = parseRoleIds(row.roleTags)
  form.mpPermissions = Array.isArray(row.mpPermissions) ? [...row.mpPermissions] : []
  form.healthProfile = { ...(row.healthProfile || {}) } as HealthProfile
  if (!form.healthProfile.audiences) form.healthProfile.audiences = []
  if (!form.healthProfile.allergies) form.healthProfile.allergies = []
  dialogVisible.value = true
}

async function onSubmit() {
  if (!form.name.trim()) {
    ElMessage.warning('请填写成员姓名')
    return
  }
  // roleTags 表单内为 number[]，后端存逗号分隔 id 串
  const roleTagsStr = form.roleTags.map(String).join(',')
  // mpPermissions：空数组时传 null，让后端走角色默认模板
  const mpPermissions = form.mpPermissions.length ? [...form.mpPermissions] : null
  if (editing.value) {
    await updateMember({
      id: editing.value.id,
      name: form.name.trim(),
      roleTags: roleTagsStr,
      healthProfile: form.healthProfile,
      mpPermissions,
    })
    ElMessage.success('已更新')
  } else {
    await createMember({
      name: form.name.trim(),
      roleTags: roleTagsStr,
      healthProfile: form.healthProfile,
      mpPermissions,
    })
    ElMessage.success('已新增')
  }
  dialogVisible.value = false
  await load()
}

async function onDelete(row: Member) {
  await ElMessageBox.confirm(`确定删除成员「${row.name}」？`, '提示', { type: 'warning' })
  await deleteMember(row.id)
  ElMessage.success('已删除')
  await load()
}
</script>

<template>
  <div class="page">
    <div class="toolbar">
      <el-button type="primary" @click="openCreate">新增成员</el-button>
    </div>
    <el-table v-loading="loading" :data="list" border>
      <el-table-column label="姓名" prop="name" width="160" />
      <el-table-column label="角色标签" min-width="200">
        <template #default="{ row }">
          <span class="mini">{{ roleTagsText(row.roleTags) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="健康档案" min-width="280">
        <template #default="{ row }">
          <span class="mini">
            身高 {{ row.healthProfile?.height ?? '-' }} / 体重 {{ row.healthProfile?.weight ?? '-' }}
            <template v-if="audienceText(row.healthProfile?.audiences)"> · {{ audienceText(row.healthProfile?.audiences) }}</template>
          </span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <Pagination
      :total="total"
      :page-size="pageSize"
      :current-page="pageNum"
      @current-change="onPageChange"
    />

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑成员' : '新增成员'" width="560px">
      <el-form label-width="100px">
        <el-form-item label="姓名">
          <el-input v-model="form.name" placeholder="请输入姓名" />
        </el-form-item>
        <el-form-item label="角色标签">
          <el-select
            v-model="form.roleTags"
            multiple
            placeholder="选择角色"
            style="width: 100%"
          >
            <el-option
              v-for="r in roleOptions"
              :key="r.id"
              :label="r.name"
              :value="r.id"
            />
          </el-select>
        </el-form-item>
        <el-divider content-position="left">健康档案</el-divider>
        <el-form-item label="身高(cm)">
          <el-input-number v-model="form.healthProfile.height" :min="0" />
        </el-form-item>
        <el-form-item label="体重(kg)">
          <el-input-number v-model="form.healthProfile.weight" :min="0" />
        </el-form-item>
        <el-form-item label="年龄">
          <el-input-number v-model="form.healthProfile.age" :min="0" />
        </el-form-item>
        <el-form-item label="性别">
          <el-radio-group v-model="form.healthProfile.gender">
            <el-radio
              v-for="g in genderOptions"
              :key="g.id"
              :value="g.name"
            >
              {{ g.name }}
            </el-radio>
            <el-radio value="">未知</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="适用人群">
          <el-select
            v-model="form.healthProfile.audiences"
            multiple
            placeholder="选择适用人群"
            style="width: 100%"
          >
            <el-option
              v-for="a in audienceOptions"
              :key="a.id"
              :label="a.name"
              :value="a.name"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="过敏源">
          <el-select
            v-model="form.healthProfile.allergies"
            multiple
            filterable
            allow-create
            default-first-option
            placeholder="输入过敏源"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="糖上限(g)">
          <el-input-number v-model="form.healthProfile.sugarMax" :min="0" />
        </el-form-item>
        <el-form-item label="盐上限(g)">
          <el-input-number v-model="form.healthProfile.saltMax" :min="0" />
        </el-form-item>
        <el-divider content-position="left">小程序功能权限</el-divider>
        <el-form-item label="功能权限">
          <div class="perm-hint mini">
            不勾选时走角色默认模板（掌勺全权 / 备菜备菜相关 / 普通成员只读点评）。
            勾选为「放宽」：个人勾选与角色默认取并集，只能增不能减。
          </div>
          <el-checkbox-group v-model="form.mpPermissions">
            <el-checkbox
              v-for="(label, key) in permOptions"
              :key="key"
              :value="key"
              :label="label"
            >
              {{ label }}
            </el-checkbox>
          </el-checkbox-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="onSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page {
  background: var(--yh-panel, #fff);
  padding: 16px;
  border-radius: 8px;
}
.toolbar {
  margin-bottom: 12px;
}
.mini {
  font-size: 12px;
  color: #7a6f60;
}
.perm-hint {
  margin-bottom: 8px;
  line-height: 1.5;
}
</style>
