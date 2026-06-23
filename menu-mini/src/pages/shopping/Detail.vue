<template>
  <view class="page">
    <!-- 顶栏（自定义） -->
    <view class="topbar">
      <text class="back" @click="goBack">‹</text>
      <text class="top-title">{{ detail ? `采购单 #${detail.id}` : '采购详情' }}</text>
      <view class="back"></view>
    </view>

    <scroll-view scroll-y class="scroll">
      <!-- mode=generate：生成选项 -->
      <view v-if="mode === 'generate'" class="block">
        <view class="block-title">
          <view class="tbar"></view>
          <text>从哪里生成</text>
        </view>
        <view class="yh-card gen">
          <view class="src-tabs">
            <view :class="['tab', sourceType === 'plan' && 'on']" @click="sourceType = 'plan'">周计划</view>
            <view :class="['tab', sourceType === 'dish' && 'on']" @click="sourceType = 'dish'">菜品</view>
            <view :class="['tab', sourceType === 'menu' && 'on']" @click="sourceType = 'menu'">菜单</view>
          </view>

          <!-- 周计划选择 -->
          <picker
            v-if="sourceType === 'plan'"
            mode="selector"
            :range="planNames"
            :value="planIdx"
            @change="(e:any) => onPickPlan(Number(e.detail.value))"
          >
            <view class="gen-input">{{ currentPlan ? (currentPlan.name || weekText(currentPlan.weekStart)) : '选择周计划' }}</view>
          </picker>

          <!-- 菜品多选 -->
          <view v-if="sourceType === 'dish'" class="gen-input" @click="onPickDish">
            {{ pickedDishNames || '选择菜品（可多选，点一次增删）' }}
          </view>

          <!-- 菜单选择 -->
          <picker
            v-if="sourceType === 'menu'"
            mode="selector"
            :range="menuNames"
            :value="menuIdx"
            @change="(e:any) => onPickMenu(Number(e.detail.value))"
          >
            <view class="gen-input">{{ currentMenu ? (currentMenu.name || `菜单 #${currentMenu.id}`) : '选择菜单' }}</view>
          </picker>

          <button
            class="yh-btn-gradient gen-btn"
            :disabled="generating || !canGenerate"
            @click="onGenerate"
          >
            {{ generating ? '生成中…' : '生成清单' }}
          </button>
        </view>
      </view>

      <!-- 详情展示 -->
      <view v-if="loading" class="loading">加载中…</view>

      <template v-else-if="detail">
        <!-- 标题区 -->
        <view class="block">
          <view class="block-title">
            <view class="tbar"></view>
            <text>{{ rangeText(detail) || '采购清单' }}</text>
          </view>
          <view class="yh-card head-card">
            <view class="head-row">
              <text class="head-name">采购单 #{{ detail.id }}</text>
              <text class="head-range">{{ rangeText(detail) }}</text>
            </view>
            <view class="export-bar">
              <button class="yh-btn-ghost half sm" :disabled="exporting" @click="onExportImage">
                {{ exporting ? '生成中…' : '导出图片' }}
              </button>
              <button class="yh-btn-ghost half sm share" open-type="share">分享清单</button>
            </view>
          </view>
        </view>

        <!-- 品类分区 -->
        <view v-if="!detail.items || !detail.items.length" class="empty">该清单暂无采购项，点下方手动添加</view>
        <view v-for="(items, catKey) in detail.grouped" :key="catKey" class="block">
          <view class="block-title">
            <view class="tbar"></view>
            <text>{{ categoryName(catKey) }}</text>
          </view>
          <view class="yh-card cat-card">
            <view
              v-for="it in items"
              :key="it.id"
              :class="['item', it.purchased === 1 && 'done']"
            >
              <view class="check" @click="onToggle(it)">
                <view :class="['box', it.purchased === 1 && 'checked']">✓</view>
              </view>
              <view class="main">
                <view class="row1">
                  <text class="iname">{{ it.ingredientName || it.customName || ('#' + it.ingredientId) }}</text>
                  <text v-if="it.referenceGrams" class="ref-g">约 {{ it.referenceGrams }}g</text>
                  <text v-if="it.purchaseAmount != null" class="ref-g">· {{ it.purchaseAmount }} {{ it.purchaseUnitName || '' }}</text>
                </view>
              </view>
              <view class="item-actions">
                <text class="item-edit" @click.stop="openEdit(it)">编辑</text>
                <text class="item-del" @click.stop="onDelete(it)">删除</text>
              </view>
            </view>
          </view>
        </view>

        <view style="height: 200rpx;"></view>
      </template>

      <view v-else-if="mode !== 'generate'" class="empty">未找到采购单</view>
    </scroll-view>

    <!-- 底部：手动添加（仅详情态） -->
    <view class="bottom-actions" v-if="detail">
      <button class="yh-btn-gradient" @click="openAdd">+ 手动添加</button>
    </view>

    <!-- 手动添加弹层（原生 input/picker） -->
    <view v-if="addOpen" class="mask" @click.self="closeAdd">
      <view class="sheet">
        <view class="sheet-title">手动添加采购项</view>
        <view class="sheet-row">
          <text class="lbl">食材名</text>
          <input class="sheet-input" v-model="form.name" placeholder="如 土豆、老抽" />
        </view>
        <view class="sheet-row">
          <text class="lbl">数量</text>
          <input class="sheet-input" type="digit" v-model="form.amount" placeholder="可留空" />
        </view>
        <view class="sheet-row">
          <text class="lbl">单位</text>
          <picker mode="selector" :range="unitNames" :value="form.unitIdx" @change="(e:any)=>form.unitIdx=Number(e.detail.value)">
            <view class="sheet-picker">{{ form.unitIdx >= 0 ? unitNames[form.unitIdx] : '选单位（可留空）' }}</view>
          </picker>
        </view>
        <view class="sheet-row">
          <text class="lbl">品类</text>
          <picker mode="selector" :range="catNames" :value="form.catIdx" @change="(e:any)=>form.catIdx=Number(e.detail.value)">
            <view class="sheet-picker">{{ form.catIdx >= 0 ? catNames[form.catIdx] : '选品类（可留空）' }}</view>
          </picker>
        </view>
        <view class="sheet-actions">
          <button class="yh-btn-ghost half" @click="closeAdd">取消</button>
          <button class="yh-btn-gradient half" :disabled="adding" @click="onAddCustom">
            {{ adding ? '添加中…' : '添加' }}
          </button>
        </view>
      </view>
    </view>

    <!-- 编辑弹层 -->
    <view v-if="editOpen" class="mask" @click.self="closeEdit">
      <view class="sheet">
        <view class="sheet-title">编辑采购项</view>
        <view class="sheet-row">
          <text class="lbl">食材名</text>
          <input class="sheet-input" v-model="editForm.name" placeholder="食材名" disabled />
        </view>
        <view class="sheet-row">
          <text class="lbl">采购量</text>
          <input class="sheet-input" type="digit" v-model="editForm.amount" placeholder="买多少" />
        </view>
        <view class="sheet-row">
          <text class="lbl">单位</text>
          <picker mode="selector" :range="unitNames" :value="editForm.unitIdx" @change="(e:any)=>editForm.unitIdx=Number(e.detail.value)">
            <view class="sheet-picker">{{ editForm.unitIdx >= 0 ? unitNames[editForm.unitIdx] : '选单位' }}</view>
          </picker>
        </view>
        <view class="sheet-actions">
          <button class="yh-btn-ghost half" @click="closeEdit">取消</button>
          <button class="yh-btn-gradient half" :disabled="saving" @click="onSaveEdit">
            {{ saving ? '保存中…' : '保存' }}
          </button>
        </view>
      </view>
    </view>

    <!-- 离屏画布：导出图片 -->
    <canvas
      canvas-id="shoppingExport"
      id="shoppingExport"
      type="2d"
      class="export-canvas"
      :style="{ width: canvasW + 'px', height: canvasH + 'px' }"
    />
  </view>
</template>

<script setup lang="ts">
import { ref, computed, reactive } from 'vue'
import { onLoad, onShareAppMessage } from '@dcloudio/uni-app'
import {
  generate,
  getDetail,
  togglePurchased,
  addCustomItem,
  updatePurchase,
  deleteItem,
  type ShoppingListVO,
  type ShoppingItemVO,
  type ShoppingSourceType,
} from '@/api/shopping'
import { request } from '@/utils/request'

interface PlanLite { id: number; weekStart: string; name?: string }
interface MenuLite { id: number; name?: string }
interface DishLite { id: number; name: string }

const detail = ref<ShoppingListVO | null>(null)
const loading = ref(false)
const generating = ref(false)
const exporting = ref(false)
const canvasW = ref(320)
const canvasH = ref(480)

// 路由参数
const listId = ref<number>(0)
const mode = ref<string>('')

// 数据源
const sourceType = ref<ShoppingSourceType>('plan')
const plans = ref<PlanLite[]>([])
const menus = ref<MenuLite[]>([])
const dishes = ref<DishLite[]>([])
const currentPlan = ref<PlanLite | null>(null)
const currentMenu = ref<MenuLite | null>(null)
const pickedDishIds = ref<number[]>([])

const planIdx = computed(() => {
  const i = plans.value.findIndex((p) => p.id === currentPlan.value?.id)
  return i >= 0 ? i : 0
})
const menuIdx = computed(() => {
  const i = menus.value.findIndex((mm) => mm.id === currentMenu.value?.id)
  return i >= 0 ? i : 0
})
const planNames = computed(() => plans.value.map((p) => p.name || weekText(p.weekStart)))
const menuNames = computed(() => menus.value.map((mm) => mm.name || `菜单 #${mm.id}`))
const pickedDishNames = computed(() =>
  pickedDishIds.value
    .map((id) => dishes.value.find((d) => d.id === id)?.name)
    .filter(Boolean)
    .join('、')
)

// 字典
const units = ref<{ id: number; name: string }[]>([])
const cats = ref<{ id: number; name: string }[]>([])
const unitNames = computed(() => units.value.map((u) => u.name))
const catNames = computed(() => cats.value.map((c) => c.name))

// 手动添加表单
const addOpen = ref(false)
const adding = ref(false)
const form = reactive({ name: '', amount: '', unitIdx: -1, catIdx: -1 })

// 编辑表单
const editOpen = ref(false)
const saving = ref(false)
const editForm = reactive({ id: 0, name: '', amount: '', unitIdx: -1 })

function openEdit(it: any) {
  editForm.id = it.id
  editForm.name = it.ingredientName || it.customName || ''
  editForm.amount = it.purchaseAmount != null ? String(it.purchaseAmount) : ''
  editForm.unitIdx = it.purchaseUnitId != null ? Math.max(0, units.value.findIndex(u => u.id === it.purchaseUnitId)) : -1
  editOpen.value = true
}
function closeEdit() { editOpen.value = false }

async function onSaveEdit() {
  saving.value = true
  try {
    const unitId = editForm.unitIdx >= 0 ? units.value[editForm.unitIdx]?.id : null
    await updatePurchase(editForm.id, Number(editForm.amount) || 0, unitId as any)
    uni.showToast({ title: '已保存' })
    closeEdit()
    await loadDetail()
  } catch {} finally { saving.value = false }
}

async function onDelete(it: any) {
  uni.showModal({
    title: '删除采购项',
    content: `确定删除「${it.ingredientName || it.customName || '该项'}」？`,
    success: async (r) => {
      if (!r.confirm) return
      try {
        await deleteItem(it.id)
        uni.showToast({ title: '已删除' })
        await loadDetail()
      } catch {}
    },
  })
}

function weekText(weekStart?: string): string {
  if (!weekStart) return '#'
  return weekStart + ' 起'
}
function rangeText(d: ShoppingListVO): string {
  if (d.startDate && d.endDate) return `${d.startDate} ~ ${d.endDate}`
  return d.timeRange || ''
}
function categoryName(catKey: string | number): string {
  const names = detail.value?.categoryNames
  if (names && names[String(catKey)]) return names[String(catKey)]
  return catKey === 'null' || catKey == null ? '其他' : `品类#${catKey}`
}

function onPickPlan(idx: number) { currentPlan.value = plans.value[idx] || null }
function onPickMenu(idx: number) { currentMenu.value = menus.value[idx] || null }
function onPickDish() {
  if (!dishes.value.length) {
    uni.showToast({ title: '暂无菜品', icon: 'none' })
    return
  }
  uni.showActionSheet({
    itemList: dishes.value.map((d) =>
      `${pickedDishIds.value.includes(d.id) ? '✓' : '　'} ${d.name}`
    ),
    success: (r) => {
      const picked = dishes.value[r.tapIndex]
      if (!picked) return
      const i = pickedDishIds.value.indexOf(picked.id)
      if (i >= 0) pickedDishIds.value.splice(i, 1)
      else pickedDishIds.value.push(picked.id)
    },
  })
}

const canGenerate = computed(() => {
  if (sourceType.value === 'plan') return !!currentPlan.value
  if (sourceType.value === 'menu') return !!currentMenu.value
  if (sourceType.value === 'dish') return pickedDishIds.value.length > 0
  return false
})

async function loadRefData() {
  try {
    const [mealPlans, dishRows, menuRows, unitRows, catRows] = await Promise.all([
      request<any>({ url: '/mealplan', method: 'GET', data: { pageNum: 1, pageSize: 100 } }).then((p: any) => p.records || []),
      request<any>({ url: '/dish/search', method: 'GET', data: { pageNum: 1, pageSize: 100 } }).then((p: any) => p.records || []),
      request<any>({ url: '/menu', method: 'GET', data: { pageNum: 1, pageSize: 100 } }).then((p: any) => p.records || []),
      request<any>({ url: '/dict', method: 'GET', data: { group: 'purchase_unit', pageNum: 1, pageSize: 50 } }).then((p: any) => p.records || []),
      request<any>({ url: '/dict', method: 'GET', data: { group: 'purchase_category', pageNum: 1, pageSize: 50 } }).then((p: any) => p.records || []),
    ])
    plans.value = mealPlans
    dishes.value = dishRows
    menus.value = menuRows
    units.value = unitRows
    cats.value = catRows
  } catch { /* 静默 */ }
}

async function loadDetail(id: number) {
  loading.value = true
  try {
    detail.value = await getDetail(id)
  } catch (e: any) {
    uni.showToast({ title: e?.msg || '加载失败', icon: 'none' })
  } finally {
    loading.value = false
  }
}

async function onGenerate() {
  if (!canGenerate.value || generating.value) return
  generating.value = true
  try {
    const req =
      sourceType.value === 'plan'
        ? { sourceType: 'plan', sourceId: currentPlan.value!.id }
        : sourceType.value === 'menu'
          ? { sourceType: 'menu', sourceId: currentMenu.value!.id }
          : { sourceType: 'dish', sourceIds: [...pickedDishIds.value] }
    const newId = await generate(req as any)
    uni.showToast({ title: '生成成功', icon: 'success' })
    mode.value = ''
    listId.value = newId
    await loadDetail(newId)
  } catch (e: any) {
    uni.showToast({ title: e?.msg || '生成失败', icon: 'none' })
  } finally {
    generating.value = false
  }
}

async function onToggle(it: ShoppingItemVO) {
  try {
    await togglePurchased(it.id)
    it.purchased = it.purchased === 1 ? 0 : 1
  } catch (e: any) {
    uni.showToast({ title: e?.msg || '操作失败', icon: 'none' })
  }
}

// ============ 手动添加 ============
function openAdd() {
  if (!detail.value || !detail.value.id) {
    uni.showToast({ title: '清单未加载', icon: 'none' })
    return
  }
  form.name = ''
  form.amount = ''
  form.unitIdx = -1
  form.catIdx = -1
  addOpen.value = true
}
function closeAdd() { addOpen.value = false }

async function onAddCustom() {
  if (adding.value) return
  const name = (form.name || '').trim()
  if (!name) {
    uni.showToast({ title: '请输入食材名', icon: 'none' })
    return
  }
  const amountNum = form.amount === '' ? null : parseFloat(form.amount)
  if (amountNum !== null && isNaN(amountNum)) {
    uni.showToast({ title: '数量格式不对', icon: 'none' })
    return
  }
  const unitId = form.unitIdx >= 0 ? units.value[form.unitIdx]?.id ?? null : null
  const catId = form.catIdx >= 0 ? cats.value[form.catIdx]?.id ?? null : null
  adding.value = true
  try {
    await addCustomItem(detail.value!.id, name, amountNum, unitId, catId)
    uni.showToast({ title: '已添加', icon: 'success' })
    closeAdd()
    await loadDetail(detail.value!.id)
  } catch (e: any) {
    uni.showToast({ title: e?.msg || e?.message || '添加失败', icon: 'none' })
  } finally {
    adding.value = false
  }
}

onLoad(async (q: any) => {
  if (q && q.mode === 'generate') {
    mode.value = 'generate'
    await loadRefData()
    return
  }
  if (q && q.id) {
    const id = Number(q.id)
    if (!isNaN(id)) {
      listId.value = id
      await loadDetail(id)
    }
  }
})

onShareAppMessage(() => {
  const d = detail.value
  const id = d?.id
  const title = d ? `采购单 · ${rangeText(d)}` : '烟火小食单 · 采购单'
  return {
    title,
    path: id ? `/pages/shopping/Detail?id=${id}` : '/pages/shopping/List',
  }
})

function goBack() {
  uni.navigateBack({ fail: () => uni.switchTab({ url: '/pages/misc/Home' }) })
}

// ============ 图片导出（canvas 2d） ============
interface DrawRow { y: number; text: string; sub?: string; done?: boolean; bold?: boolean }

function buildRows(): { rows: DrawRow[]; width: number; height: number } {
  const d = detail.value
  const width = 320
  const pad = 16
  const rows: DrawRow[] = []
  rows.push({ y: 0, text: '采购单', bold: true })
  if (d) rows.push({ y: 0, text: rangeText(d), sub: '' })
  let y = 70
  const grouped = (d?.grouped || {}) as Record<string, ShoppingItemVO[]>
  Object.keys(grouped).forEach((catKey) => {
    const items = grouped[catKey] || []
    if (!items.length) return
    rows.push({ y, text: categoryName(catKey), bold: true })
    y += 34
    items.forEach((it) => {
      const amt = it.purchaseAmount != null
        ? `${it.purchaseAmount} ${it.purchaseUnitName || ''}`
        : (it.referenceGrams ? `约${it.referenceGrams}g` : '')
      rows.push({ y, text: it.ingredientName || it.customName || `#${it.ingredientId}`, sub: amt, done: it.purchased === 1 })
      y += 32
    })
    y += 10
  })
  const height = Math.max(360, y + pad)
  let acc = 70
  for (let i = 2; i < rows.length; i++) {
    const r = rows[i]
    r.y = acc
    acc = r.bold ? acc + 34 : acc + 32
  }
  return { rows, width, height }
}

async function onExportImage() {
  if (exporting.value) return
  const d = detail.value
  if (!d || !d.items || !d.items.length) {
    uni.showToast({ title: '清单为空', icon: 'none' })
    return
  }
  exporting.value = true
  try {
    const { rows, width, height } = buildRows()
    canvasW.value = width
    canvasH.value = height
    await nextFrame()

    const ctx = uni.createCanvasContext('shoppingExport')
    ctx.setFillStyle('#fffaf3')
    ctx.fillRect(0, 0, width, height)
    ctx.setFillStyle('#FF6B35')
    ctx.setFontSize(18)
    ctx.fillText('采购单', 16, 34)
    if (d) {
      ctx.setFillStyle('#999')
      ctx.setFontSize(12)
      ctx.fillText(rangeText(d), 16, 56)
    }
    rows.forEach((r) => {
      if (r.y === 0) return
      if (r.bold) {
        ctx.setFillStyle('#FF6B35')
        ctx.setFontSize(14)
        ctx.fillText(r.text, 16, r.y)
        ctx.setStrokeStyle('#f0e0d0')
        ctx.beginPath()
        ctx.moveTo(16, r.y + 6)
        ctx.lineTo(width - 16, r.y + 6)
        ctx.stroke()
      } else {
        const boxX = 16
        const boxY = r.y - 11
        ctx.setStrokeStyle(r.done ? '#FF6B35' : '#ccc')
        ctx.setLineWidth(1.5)
        ctx.strokeRect(boxX, boxY, 14, 14)
        if (r.done) {
          ctx.setFillStyle('#FF6B35')
          ctx.fillRect(boxX + 2, boxY + 2, 10, 10)
        }
        ctx.setFillStyle(r.done ? '#bbb' : '#333')
        ctx.setFontSize(14)
        ctx.fillText(r.text, 38, r.y)
        if (r.sub) {
          ctx.setFillStyle('#666')
          ctx.setFontSize(12)
          ctx.fillText(r.sub, width - 16 - measureText(ctx, r.sub), r.y)
        }
      }
    })
    ctx.setFillStyle('#bbb')
    ctx.setFontSize(10)
    ctx.fillText('烟火小食单', 16, height - 12)

    await drawSync(ctx)
    const tempPath = await canvasToTemp('shoppingExport')
    await saveOrPreview(tempPath)
  } catch (e: any) {
    uni.showToast({ title: e?.msg || e?.errMsg || '导出失败', icon: 'none' })
  } finally {
    exporting.value = false
  }
}

function measureText(ctx: UniApp.CanvasContext, text: string): number {
  try {
    const m = ctx.measureText(text)
    if (m && typeof m.width === 'number') return m.width
  } catch { /* 降级 */ }
  return text.length * 7
}
function drawSync(ctx: UniApp.CanvasContext): Promise<void> {
  return new Promise((resolve) => {
    ctx.draw(false, () => resolve())
    setTimeout(() => resolve(), 500)
  })
}
function canvasToTemp(canvasId: string): Promise<string> {
  return new Promise((resolve, reject) => {
    uni.canvasToTempFilePath({
      canvasId,
      success: (res) => resolve(res.tempFilePath),
      fail: (err) => reject(err),
    })
  })
}
async function saveOrPreview(tempPath: string) {
  // #ifdef MP-WEIXIN || APP-PLUS
  try {
    await ensureAlbumAuth()
    await new Promise<void>((resolve, reject) => {
      uni.saveImageToPhotosAlbum({
        filePath: tempPath,
        success: () => resolve(),
        fail: (err) => reject(err),
      })
    })
    uni.showToast({ title: '已保存到相册', icon: 'success' })
    return
  } catch {
    /* 权限拒绝 → 降级预览 */
  }
  // #endif
  uni.previewImage({ urls: [tempPath] })
}
function ensureAlbumAuth(): Promise<void> {
  return new Promise((resolve, reject) => {
    uni.getSetting({
      success: (res) => {
        if (res.authSetting['scope.writePhotosAlbum'] === false) {
          uni.openSetting({
            success: (r) => {
              if (r.authSetting['scope.writePhotosAlbum']) resolve()
              else reject(new Error('未授权相册'))
            },
            fail: () => reject(new Error('未授权相册')),
          })
        } else if (res.authSetting['scope.writePhotosAlbum'] === true) {
          resolve()
        } else {
          uni.authorize({
            scope: 'scope.writePhotosAlbum',
            success: () => resolve(),
            fail: () => reject(new Error('未授权相册')),
          })
        }
      },
      fail: () => reject(new Error('读取设置失败')),
    })
  })
}
function nextFrame(): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, 50))
}
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: #FFFBF5;
  display: flex;
  flex-direction: column;
}

/* 顶栏 */
.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: calc(env(safe-area-inset-top) + 16rpx) 24rpx 12rpx;
  background: #FFFBF5;
}
.back {
  width: 60rpx;
  font-size: 48rpx;
  color: #2D2A26;
  text-align: center;
}
.top-title {
  font-size: 32rpx;
  font-weight: 600;
  color: #2D2A26;
}

.scroll { flex: 1; }

/* 块 */
.block { margin: 24rpx 28rpx 0; }
.block-title {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-bottom: 18rpx;
}
.tbar {
  width: 8rpx; height: 32rpx;
  background: #FF8C42; border-radius: 4rpx;
}
.block-title text {
  font-size: 32rpx;
  font-weight: bold;
  color: #2D2A26;
}

/* 生成区 */
.gen { padding: 24rpx; }
.src-tabs { display: flex; gap: 12rpx; margin-bottom: 20rpx; }
.tab {
  flex: 1; text-align: center; font-size: 13px; padding: 14rpx 0;
  border-radius: 12rpx; background: #FFFBF5; color: #9B958C;
}
.tab.on {
  background: linear-gradient(135deg, #FF8C42, #FFA45C);
  color: #fff; font-weight: 600;
}
.gen-input {
  font-size: 14px; color: #333; border: 1px solid #eee;
  border-radius: 12rpx; padding: 18rpx 20rpx; margin-bottom: 20rpx;
}
.gen-btn { width: 100%; }

/* 详情头卡 */
.head-card { padding: 28rpx 32rpx; }
.head-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 20rpx;
}
.head-name {
  font-size: 17px; font-weight: 600; color: #2D2A26;
}
.head-range {
  font-size: 24rpx; color: #9B958C;
}
.export-bar { display: flex; gap: 16rpx; }
.half { flex: 1; }
.sm { font-size: 13px; padding: 16rpx 0; }
.share { color: #FF8C42; border-color: #FF8C42; }

/* 品类分区 */
.cat-card { padding: 8rpx 32rpx; }
.item {
  display: flex;
  padding: 24rpx 0;
  border-bottom: 2rpx solid #F2EDE4;
}
.item:last-child { border-bottom: none; }
.item.done .iname { color: #bbb; text-decoration: line-through; }
.check { padding-right: 20rpx; }
.box {
  width: 40rpx; height: 40rpx;
  border: 3rpx solid #ddd; border-radius: 8rpx;
  display: flex; align-items: center; justify-content: center;
  font-size: 26rpx; color: transparent;
}
.box.checked {
  background: #FF8C42; border-color: #FF8C42; color: #fff;
}
.main { flex: 1; }
.row1 { display: flex; align-items: center; gap: 12rpx; flex-wrap: wrap; }
.iname { font-size: 15px; color: #2D2A26; }
.ref-g { font-size: 24rpx; color: #9B958C; }

/* 空态 */
.empty {
  text-align: center; color: #B8B2A7;
  padding: 80rpx 0; font-size: 13px;
}
.loading {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #B8B2A7;
  font-size: 14px;
  padding: 80rpx 0;
}

/* 底部操作 */
.bottom-actions {
  position: fixed;
  left: 0; right: 0; bottom: 0;
  padding: 24rpx 28rpx calc(env(safe-area-inset-bottom) + 24rpx);
  background: #FFFFFF;
  box-shadow: 0 -4rpx 16rpx rgba(0, 0, 0, 0.06);
  z-index: 10;
}
.bottom-actions .yh-btn-gradient {
  width: 100%;
  height: 88rpx;
  line-height: 88rpx;
  font-size: 30rpx;
  padding: 0;
}

/* 手动添加弹层 */
.mask {
  position: fixed; inset: 0; background: rgba(0,0,0,0.4);
  display: flex; align-items: flex-end; z-index: 999;
}
.sheet {
  width: 100%; background: #fff; border-radius: 24rpx 24rpx 0 0;
  padding: 32rpx 32rpx calc(32rpx + env(safe-area-inset-bottom));
}
.sheet-title {
  font-size: 32rpx; font-weight: 700; color: #2D2A26; margin-bottom: 24rpx;
}
.sheet-row {
  display: flex; align-items: center; gap: 20rpx;
  padding: 20rpx 0; border-bottom: 2rpx solid #F2EDE4;
}
.sheet-row:last-of-type { border-bottom: none; }
.lbl { flex: 0 0 112rpx; font-size: 14px; color: #666; }
.sheet-input {
  flex: 1; border: 1px solid #eee; border-radius: 12rpx;
  padding: 16rpx 20rpx; font-size: 14px;
}
.sheet-picker {
  flex: 1; border: 1px solid #eee; border-radius: 12rpx;
  padding: 16rpx 20rpx; font-size: 14px; color: #333;
}
.sheet-actions { display: flex; gap: 20rpx; margin-top: 28rpx; }

.export-canvas { position: fixed; left: -9999px; top: 0; pointer-events: none; }

button::after { border: none; }
</style>
