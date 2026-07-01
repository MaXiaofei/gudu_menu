<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { UploadRequestOptions } from 'element-plus'
import {
  searchDishes,
  getDishDetail,
  createDish,
  updateDish,
  deleteDish,
  importDishByUrl,
  getDishNutrition,
  getDishHistory,
  type Dish,
  type DishStep,
  type DishIngredient,
  type DishHistory,
  type DishSearchRow,
} from '@/api/dish'
import { listByGroup, listNutritionMetrics, type DictItem, type NutritionMetric } from '@/api/dict'
import { listIngredients, type Ingredient } from '@/api/ingredient'
import { upload } from '@/api/upload'
import type { UploadAjaxError } from 'element-plus/es/components/upload/src/ajax'
import Pagination from '@/components/Pagination.vue'

// ===== 字典/选项 =====
const cuisineOptions = ref<DictItem[]>([])
const tagOptions = ref<DictItem[]>([])
const categoryOptions = ref<DictItem[]>([])
const metrics = ref<NutritionMetric[]>([])
const ingredients = ref<Ingredient[]>([])

async function loadDicts() {
  const [c, t, cat, m, ing] = await Promise.all([
    listByGroup('cuisine'),
    listByGroup('tag'),
    listByGroup('category'),
    listNutritionMetrics(),
    listIngredients(),
  ])
  cuisineOptions.value = c
  tagOptions.value = t
  categoryOptions.value = cat
  metrics.value = m
  ingredients.value = ing
}

// ===== 列表（分页搜索）=====
const loading = ref(false)
const list = ref<DishSearchRow[]>([])
const total = ref(0)
const query = reactive({
  keyword: '',
  pageNum: 1,
  pageSize: 20,
})

// ===== URL 导入 =====
const importDialogVisible = ref(false)
const importUrl = ref('')
const importing = ref(false)

function openImport() {
  importUrl.value = ''
  importDialogVisible.value = true
}

async function doImport() {
  if (!importUrl.value.trim()) {
    ElMessage.warning('请粘贴菜谱链接')
    return
  }
  importing.value = true
  try {
    const id = await importDishByUrl(importUrl.value.trim())
    ElMessage.success(`导入成功，新菜品 ID: ${id}`)
    importDialogVisible.value = false
    await load()
  } catch {
    // request 拦截器已弹错误
  } finally {
    importing.value = false
  }
}

async function load() {
  loading.value = true
  try {
    const page = await searchDishes({
      keyword: query.keyword || undefined,
      pageNum: query.pageNum,
      pageSize: query.pageSize,
    })
    list.value = page.records || []
    total.value = page.total || 0
  } finally {
    loading.value = false
  }
}

function onSearch() {
  query.pageNum = 1
  load()
}

onMounted(() => {
  loadDicts()
  load()
})

// ===== 新增/编辑对话框 =====
const dialogVisible = ref(false)
const editing = ref<Dish | null>(null)

const baseForm = reactive<{
  id?: number
  name: string
  prepTime?: number
  cookTime?: number
  difficulty?: number
  coverUrl?: string
}>({
  name: '',
  prepTime: undefined,
  cookTime: undefined,
  difficulty: undefined,
  coverUrl: '',
})

const steps = ref<DishStep[]>([{ text: '', images: [] }])
const cuisineIds = ref<number[]>([])
const tagIds = ref<number[]>([])
const categoryIds = ref<number[]>([])
const dishIngredients = ref<DishIngredient[]>([{ ingredientId: undefined as unknown as number, amount: 1 }])

function resetForm() {
  editing.value = null
  baseForm.id = undefined
  baseForm.name = ''
  baseForm.prepTime = undefined
  baseForm.cookTime = undefined
  baseForm.difficulty = undefined
  baseForm.coverUrl = ''
  steps.value = [{ text: '', images: [] }]
  cuisineIds.value = []
  tagIds.value = []
  categoryIds.value = []
  dishIngredients.value = [{ ingredientId: undefined as unknown as number, amount: 1 }]
}

function openCreate() {
  resetForm()
  dialogVisible.value = true
}

async function openEdit(row: DishSearchRow) {
  resetForm()
  try {
    const detail = await getDishDetail(row.id)
    editing.value = detail.dish
    baseForm.id = detail.dish.id
    baseForm.name = detail.dish.name
    baseForm.prepTime = detail.dish.prepTime as number | undefined
    baseForm.cookTime = detail.dish.cookTime as number | undefined
    baseForm.difficulty = detail.dish.difficulty as number | undefined
    baseForm.coverUrl = detail.dish.coverUrl as string | undefined
    steps.value = (detail.steps && detail.steps.length ? detail.steps : [{ text: '', images: [] }]).map((s) => ({
      text: s.text,
      images: s.images ? [...s.images] : [],
    }))
    cuisineIds.value = [...(detail.cuisineIds || [])]
    tagIds.value = [...(detail.tagIds || [])]
    categoryIds.value = [...(detail.categoryIds || [])]
    dishIngredients.value = (detail.ingredients && detail.ingredients.length
      ? detail.ingredients
      : [{ ingredientId: undefined as unknown as number, amount: 1 }]
    ).map((d) => ({ ingredientId: d.ingredientId, amount: d.amount }))
  } catch {
    // 忽略
  }
  dialogVisible.value = true
}

function addStep() {
  steps.value.push({ text: '', images: [] })
}
function removeStep(i: number) {
  if (steps.value.length <= 1) return
  steps.value.splice(i, 1)
}
function addStepImage(s: DishStep) {
  if (!s.images) s.images = []
  s.images.push('')
}
function removeStepImage(s: DishStep, i: number) {
  s.images?.splice(i, 1)
}

function addIngredient() {
  dishIngredients.value.push({ ingredientId: undefined as unknown as number, amount: 1 })
}
function removeIngredient(i: number) {
  if (dishIngredients.value.length <= 1) return
  dishIngredients.value.splice(i, 1)
}

async function onSubmit() {
  if (!baseForm.name.trim()) {
    ElMessage.warning('请填写菜品名称')
    return
  }
  const payloadSteps = steps.value
    .filter((s) => (s.text && s.text.trim()) || (s.images && s.images.length))
    .map((s) => ({ text: s.text || '', images: (s.images || []).filter(Boolean) }))
  const payloadIngredients = dishIngredients.value
    .filter((d) => d.ingredientId !== undefined && d.ingredientId !== null)
    .map((d) => ({ ingredientId: Number(d.ingredientId), amount: Number(d.amount) }))

  const dish = {
    name: baseForm.name.trim(),
    prepTime: baseForm.prepTime,
    cookTime: baseForm.cookTime,
    difficulty: baseForm.difficulty,
    coverUrl: baseForm.coverUrl,
  }
  const common = {
    steps: payloadSteps,
    cuisineIds: cuisineIds.value,
    tagIds: tagIds.value,
    categoryIds: categoryIds.value,
    ingredients: payloadIngredients,
  }
  if (editing.value && baseForm.id) {
    await updateDish({ dish: { id: baseForm.id, ...dish }, ...common })
    ElMessage.success('已更新')
  } else {
    await createDish({ dish, ...common })
    ElMessage.success('已新增')
  }
  dialogVisible.value = false
  await load()
}

async function onDelete(row: DishSearchRow) {
  await ElMessageBox.confirm(`确定删除菜品「${row.name}」？`, '提示', { type: 'warning' })
  await deleteDish(row.id)
  ElMessage.success('已删除')
  await load()
}

// ===== 图片上传 =====
async function customUpload(opts: UploadRequestOptions) {
  const file = opts.file as File
  try {
    const r = await upload(file)
    opts.onSuccess(r)
  } catch (e) {
    // Element Plus 的 onError 要求 UploadAjaxError(带 status/method/url)；
    // 这里把任意异常包装成符合签名的对象，保留原始 message 供界面展示。
    const raw = e instanceof Error ? e : new Error(String(e))
    const ajaxErr = Object.assign(new Error(raw.message), {
      status: 0,
      method: 'POST',
      url: '',
    }) as UploadAjaxError
    opts.onError(ajaxErr)
  }
}

/** 步骤图上传成功后，把 URL push 进指定步骤 */
function makeStepUploadSuccess(step: DishStep) {
  return (resp: { url: string }) => {
    if (!step.images) step.images = []
    step.images.push(resp.url)
  }
}

/** 封面上传成功，回填 coverUrl */
function onCoverSuccess(resp: { url: string }) {
  baseForm.coverUrl = resp.url
}

// ===== 查看营养（列表）=====
const nutritionDialogVisible = ref(false)
const nutritionTitle = ref('')
const nutritionData = ref<{ name: string; value: number; unit: string }[]>([])

// 后端 nutrition_metric 字典 name 为英文，转中文便于家庭阅读
const METRIC_CN: Record<string, string> = {
  calorie: '热量',
  protein: '蛋白质',
  fat: '脂肪',
  carb: '碳水',
  sugar: '糖',
  gi: '升糖指数',
}

async function showNutrition(row: DishSearchRow) {
  nutritionTitle.value = `「${row.name}」营养成分`
  nutritionDialogVisible.value = true
  try {
    const raw = await getDishNutrition(row.id, 1)
    nutritionData.value = metrics.value
      .filter((m) => raw[String(m.id)] !== undefined && raw[String(m.id)] !== null)
      .map((m) => ({ name: METRIC_CN[m.name] || m.name, value: Number(raw[String(m.id)]), unit: m.unit }))
  } catch {
    nutritionData.value = []
  }
}

// ===== 历史抽屉 =====
const historyVisible = ref(false)
const historyList = ref<DishHistory[]>([])
const historyTitle = ref('')

async function showHistory(row: DishSearchRow) {
  historyTitle.value = `「${row.name}」历史版本`
  historyVisible.value = true
  try {
    historyList.value = await getDishHistory(row.id)
  } catch {
    historyList.value = []
  }
}
</script>

<template>
  <div class="page">
    <div class="toolbar">
      <el-input
        v-model="query.keyword"
        placeholder="菜品名称搜索"
        clearable
        class="filter-input"
        @keyup.enter="onSearch"
      />
      <el-button type="primary" @click="onSearch">搜索</el-button>
      <div class="spacer" />
      <el-button type="primary" @click="openCreate">新增菜品</el-button>
      <el-button @click="openImport">URL 导入</el-button>
    </div>

    <el-table v-loading="loading" :data="list" border>
      <el-table-column label="名称" prop="name" min-width="160" />
      <el-table-column label="准备(分)" prop="prepTime" width="100" />
      <el-table-column label="烹饪(分)" prop="cookTime" width="100" />
      <el-table-column label="难度" prop="difficulty" width="80" />
      <el-table-column label="操作" width="320" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="success" @click="showNutrition(row)">营养</el-button>
          <el-button link type="info" @click="showHistory(row)">历史</el-button>
          <el-button link type="danger" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <Pagination
      :total="total"
      :page-size="query.pageSize"
      :current-page="query.pageNum"
      @current-change="(p: number) => { query.pageNum = p; load() }"
    />

    <!-- 新增/编辑大表单 -->
    <el-dialog v-model="dialogVisible" :title="editing ? '编辑菜品' : '新增菜品'" width="820px" top="5vh">
      <el-form label-width="100px">
        <el-divider content-position="left">基础信息</el-divider>
        <el-form-item label="菜品名称">
          <el-input v-model="baseForm.name" placeholder="菜品名" style="width: 320px" />
        </el-form-item>
        <el-form-item label="准备时间(分)">
          <el-input-number v-model="baseForm.prepTime" :min="0" />
        </el-form-item>
        <el-form-item label="烹饪时间(分)">
          <el-input-number v-model="baseForm.cookTime" :min="0" />
        </el-form-item>
        <el-form-item label="难度">
          <el-input-number v-model="baseForm.difficulty" :min="0" :max="5" />
        </el-form-item>
        <el-form-item label="封面图">
          <el-upload
            :http-request="customUpload"
            :on-success="onCoverSuccess"
            :show-file-list="false"
            accept="image/*"
          >
            <el-button>上传图片</el-button>
          </el-upload>
          <el-input v-model="baseForm.coverUrl" placeholder="或直接填写图片URL" style="margin-top: 6px" />
          <el-image
            v-if="baseForm.coverUrl"
            :src="baseForm.coverUrl"
            style="width: 80px; height: 60px; margin-top: 6px; border-radius: 4px"
            fit="cover"
          />
        </el-form-item>

        <el-divider content-position="left">分类（菜系/标签/分类）</el-divider>
        <el-form-item label="菜系">
          <el-select v-model="cuisineIds" multiple placeholder="选择菜系" style="width: 100%">
            <el-option v-for="c in cuisineOptions" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="标签">
          <el-select v-model="tagIds" multiple placeholder="选择标签" style="width: 100%">
            <el-option v-for="t in tagOptions" :key="t.id" :label="t.name" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="菜品分类">
          <el-select v-model="categoryIds" multiple placeholder="选择分类" style="width: 100%">
            <el-option v-for="c in categoryOptions" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>

        <el-divider content-position="left">食材用量</el-divider>
        <div v-for="(d, i) in dishIngredients" :key="i" class="inline-row">
          <el-select v-model="d.ingredientId" placeholder="选择食材" filterable style="width: 240px">
            <el-option v-for="ing in ingredients" :key="ing.id" :label="ing.name" :value="ing.id" />
          </el-select>
          <el-input-number v-model="d.amount" :min="0" :precision="2" style="margin-left: 8px" />
          <el-button link type="danger" style="margin-left: 8px" @click="removeIngredient(i)">删除</el-button>
        </div>
        <el-button text type="primary" @click="addIngredient">+ 添加食材</el-button>

        <el-divider content-position="left">步骤</el-divider>
        <div v-for="(s, i) in steps" :key="i" class="step-block">
          <div class="step-head">
            <b>步骤 {{ i + 1 }}</b>
            <el-button link type="danger" @click="removeStep(i)">删除步骤</el-button>
          </div>
          <el-input v-model="s.text" type="textarea" :rows="2" placeholder="步骤描述" />
          <div class="step-imgs">
            <div v-for="(_img, j) in s.images || []" :key="j" class="step-img-row">
              <el-input v-model="s.images![j]" placeholder="图片URL" style="width: 320px" />
              <el-button link type="danger" @click="removeStepImage(s, j)">移除</el-button>
            </div>
            <div class="step-img-actions">
              <el-upload
                :http-request="customUpload"
                :show-file-list="false"
                :on-success="makeStepUploadSuccess(s)"
                accept="image/*"
              >
                <el-button size="small">上传步骤图</el-button>
              </el-upload>
              <el-button size="small" text type="primary" @click="addStepImage(s)">+ 填写URL</el-button>
            </div>
          </div>
        </div>
        <el-button text type="primary" @click="addStep">+ 添加步骤</el-button>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="onSubmit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 营养成分列表 -->
    <el-dialog v-model="nutritionDialogVisible" :title="nutritionTitle" width="480px">
      <el-table :data="nutritionData" border v-if="nutritionData.length">
        <el-table-column label="指标" prop="name" min-width="120" />
        <el-table-column label="数值" prop="value" width="140" align="right">
          <template #default="{ row }">
            <span class="nut-value">{{ row.value }}</span>
          </template>
        </el-table-column>
        <el-table-column label="单位" prop="unit" width="100" align="left" />
      </el-table>
      <div v-if="!nutritionData.length" class="empty">暂无营养数据</div>
    </el-dialog>

    <!-- 历史 -->
    <el-drawer v-model="historyVisible" :title="historyTitle" size="40%">
      <el-table :data="historyList" border>
        <el-table-column label="时间" prop="createTime" width="180" />
        <el-table-column label="版本ID" prop="id" width="100" />
        <el-table-column label="快照">
          <template #default="{ row }">
            <pre class="snap">{{ JSON.stringify(row.snapshot, null, 2) }}</pre>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="!historyList.length" class="empty">暂无历史版本</div>
    </el-drawer>

    <!-- URL 导入 -->
    <el-dialog v-model="importDialogVisible" title="URL 导入菜谱" width="520px">
      <el-alert
        type="info"
        :closable="false"
        title="支持 下厨房 / 美食杰 / 豆果美食；其他站点尽力提取。"
        style="margin-bottom: 12px"
      />
      <el-input v-model="importUrl" placeholder="粘贴菜谱网页链接" clearable />
      <template #footer>
        <el-button @click="importDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="importing" @click="doImport">导入</el-button>
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
  margin-bottom: 16px;
  display: flex;
  align-items: center;
  gap: 12px;
}
.filter-input {
  width: 240px;
}
.spacer {
  flex: 1;
}
.inline-row {
  margin-bottom: 8px;
}
.step-block {
  border: 1px dashed #e6d9cc;
  border-radius: 6px;
  padding: 10px 12px;
  margin-bottom: 10px;
}
.step-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}
.step-imgs {
  margin-top: 8px;
}
.step-img-row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
}
.step-img-actions {
  display: flex;
  gap: 8px;
}
.empty {
  text-align: center;
  color: #9a8f80;
  padding: 24px;
}
.nut-value {
  font-weight: bold;
  color: #E89150;
}
.snap {
  max-height: 200px;
  overflow: auto;
  font-size: 11px;
  margin: 0;
  white-space: pre-wrap;
}
</style>
