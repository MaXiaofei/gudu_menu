<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listMembers,
  createMember,
  updateMember,
  deleteMember,
  type Member,
  type HealthProfile,
} from '@/api/member'
import { listByGroup, type DictItem } from '@/api/dict'

const loading = ref(false)
const list = ref<Member[]>([])
const audienceOptions = ref<DictItem[]>([])
const roleOptions = ref<DictItem[]>([])

async function load() {
  loading.value = true
  try {
    list.value = await listMembers()
  } finally {
    loading.value = false
  }
}

async function loadDicts() {
  const [a, r] = await Promise.all([listByGroup('audience'), listByGroup('role')])
  audienceOptions.value = a
  roleOptions.value = r
}

onMounted(() => {
  load()
  loadDicts()
})

const dialogVisible = ref(false)
const editing = ref<Member | null>(null)

function blankForm() {
  return {
    name: '',
    roleTags: [] as string[],
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
  form.roleTags = [...(row.roleTags || [])]
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
  if (editing.value) {
    await updateMember({
      id: editing.value.id,
      name: form.name.trim(),
      roleTags: form.roleTags,
      healthProfile: form.healthProfile,
    })
    ElMessage.success('已更新')
  } else {
    await createMember({
      name: form.name.trim(),
      roleTags: form.roleTags,
      healthProfile: form.healthProfile,
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
          <el-tag
            v-for="t in row.roleTags || []"
            :key="t"
            style="margin-right: 6px"
            type="info"
          >
            {{ t }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="健康档案" min-width="280">
        <template #default="{ row }">
          <span class="mini">
            身高 {{ row.healthProfile?.height ?? '-' }} / 体重 {{ row.healthProfile?.weight ?? '-' }}
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

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑成员' : '新增成员'" width="560px">
      <el-form label-width="100px">
        <el-form-item label="姓名">
          <el-input v-model="form.name" placeholder="请输入姓名" />
        </el-form-item>
        <el-form-item label="角色标签">
          <el-select
            v-model="form.roleTags"
            multiple
            filterable
            allow-create
            default-first-option
            placeholder="选择或输入角色"
            style="width: 100%"
          >
            <el-option
              v-for="r in roleOptions"
              :key="r.id"
              :label="r.name"
              :value="r.name"
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
          <el-input v-model="form.healthProfile.gender" placeholder="男/女" />
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
</style>
