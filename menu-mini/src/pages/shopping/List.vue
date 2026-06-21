<template>
  <view class="page">
    <!-- 生成入口：数据源切换 -->
    <view class="card gen">
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

      <!-- 菜品多选（tap 弹 ActionSheet 增删） -->
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
        class="btn-primary gen-btn"
        :disabled="generating || !canGenerate"
        @click="onGenerate"
      >
        {{ generating ? '生成中…' : '生成清单' }}
      </button>
    </view>

    <!-- 生成出的清单详情 -->
    <view v-if="loading" class="empty">加载中…</view>
    <view v-else-if="!detail" class="empty">暂无采购清单（请先生成，或下方手动加）</view>
    <view v-else class="detail">
      <view class="detail-head">
        <text class="title">采购单</text>
        <text class="range">{{ rangeText(detail) }}</text>
      </view>

      <view class="export-bar">
        <button class="btn-ghost half sm" :disabled="exporting" @click="onExportImage">
          {{ exporting ? '生成中…' : '导出图片' }}
        </button>
        <button class="btn-ghost half sm share" open-type="share">分享清单</button>
      </view>

      <view v-if="!detail.items || !detail.items.length" class="empty small">该清单暂无采购项，可手动加</view>

      <!-- 按品类分区展示 -->
      <view v-for="(items, catKey) in detail.grouped" :key="catKey" class="card category">
        <view class="cat-title">{{ categoryName(catKey) }}</view>
        <view v-for="it in items" :key="it.id" :class="['item', it.purchased === 1 && 'done']">
          <view class="check" @click="onToggle(it)">
            <view :class="['box', it.purchased === 1 && 'checked']">✓</view>
          </view>
          <view class="main">
            <view class="row1">
              <text class="iname">{{ it.ingredientName || it.customName || ('#' + it.ingredientId) }}</text>
              <text v-if="it.referenceGrams" class="ref-g">约 {{ it.referenceGrams }}g</text>
            </view>
            <view class="row2">
              <input
                class="amt"
                type="digit"
                v-model="ensureDraft(it).amount"
                placeholder="买多少"
                @click.stop
              />
              <picker
                class="unit-pk"
                mode="selector"
                :range="unitNames"
                :value="ensureDraft(it).unitIdx"
                @change="(e: any) => onPickUnit(it.id, Number(e.detail.value))"
                @click.stop
              >
                <view class="unit-txt">
                  {{ ensureDraft(it).unitIdx >= 0 ? unitNames[ensureDraft(it).unitIdx] : '单位' }}
                </view>
              </picker>
              <view class="save" @click.stop="onSavePurchase(it)">保存</view>
            </view>
            <view v-if="it.purchaseAmount != null && it.purchaseUnitName" class="cur">
              已填：{{ it.purchaseAmount }} {{ it.purchaseUnitName }}
            </view>
          </view>
        </view>
      </view>
    </view>

    <!-- 底部：手动添加自定义采购项 -->
    <button class="btn-primary add-btn" @click="openAdd">+ 手动添加</button>

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
          <button class="btn-ghost half" @click="closeAdd">取消</button>
          <button class="btn-primary half" :disabled="adding" @click="onAddCustom">
            {{ adding ? '添加中…' : '添加' }}
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
import { onShow, onLoad, onShareAppMessage } from '@dcloudio/uni-app'
import {
  generate,
  getDetail,
  togglePurchased,
  updatePurchase,
  listShopping,
  addCustomItem,
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
const dishNames = computed(() => dishes.value.map((d) => d.name))
const pickedDishNames = computed(() =>
  pickedDishIds.value
    .map((id) => dishes.value.find((d) => d.id === id)?.name)
    .filter(Boolean)
    .join('、')
)

// 采购单位字典（中文）+ 采购品类字典（用于手动添加 picker）
const units = ref<{ id: number; name: string }[]>([])
const cats = ref<{ id: number; name: string }[]>([])
const unitNames = computed(() => units.value.map((u) => u.name))
const catNames = computed(() => cats.value.map((c) => c.name))

// 草稿：每行采购量+单位序号
const draft = reactive<Record<number, { amount: string; unitIdx: number }>>({})

// 手动添加表单
const addOpen = ref(false)
const adding = ref(false)
const form = reactive({ name: '', amount: '', unitIdx: -1, catIdx: -1 })

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

function ensureDraft(it: ShoppingItemVO) {
  if (!draft[it.id]) {
    const idx = it.purchaseUnitId != null ? units.value.findIndex((u) => u.id === it.purchaseUnitId) : -1
    draft[it.id] = {
      amount: it.purchaseAmount != null ? String(it.purchaseAmount) : '',
      unitIdx: idx >= 0 ? idx : -1,
    }
  }
  return draft[it.id]
}

function onPickUnit(id: number, idx: number) {
  if (draft[id]) draft[id].unitIdx = idx
}

function onPickPlan(idx: number) {
  currentPlan.value = plans.value[idx] || null
}
function onPickMenu(idx: number) {
  currentMenu.value = menus.value[idx] || null
}
/** 菜品多选：ActionSheet 点一次增/删。 */
function onPickDish() {
  if (!dishes.value.length) {
    uni.showToast({ title: '暂无菜品', icon: 'none' })
    return
  }
  uni.showActionSheet({
    itemList: dishes.value.map((d, i) =>
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
      request<any>({ url: '/mealplan', method: 'GET', data: { pageNum: 1, pageSize: 100 } })
        .then((p: any) => p.records || []),
      request<any>({ url: '/dish/search', method: 'GET', data: { pageNum: 1, pageSize: 100 } })
        .then((p: any) => p.records || []),
      request<any>({ url: '/menu', method: 'GET', data: { pageNum: 1, pageSize: 100 } })
        .then((p: any) => p.records || []),
      request<any>({ url: '/dict', method: 'GET', data: { group: 'purchase_unit', pageNum: 1, pageSize: 50 } })
        .then((p: any) => p.records || []),
      request<any>({ url: '/dict', method: 'GET', data: { group: 'purchase_category', pageNum: 1, pageSize: 50 } })
        .then((p: any) => p.records || []),
    ])
    plans.value = mealPlans
    dishes.value = dishRows
    menus.value = menuRows
    units.value = unitRows
    cats.value = catRows
  } catch {
    /* 静默 */
  }
}

async function loadPlans() {
  await loadRefData()
  try {
    const records: any[] = await listShopping()
    if (records.length && !detail.value) {
      await loadDetail(records[0].id)
    }
  } catch {
    /* 静默 */
  }
}

async function loadDetail(listId: number) {
  loading.value = true
  try {
    detail.value = await getDetail(listId)
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
    const listId = await generate(req as any)
    uni.showToast({ title: '生成成功', icon: 'success' })
    await loadDetail(listId)
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

async function onSavePurchase(it: ShoppingItemVO) {
  const d = ensureDraft(it)
  const amt = parseFloat(d.amount)
  if (d.amount === '' || isNaN(amt)) {
    uni.showToast({ title: '请输入采购量', icon: 'none' })
    return
  }
  if (d.unitIdx < 0 || !units.value[d.unitIdx]) {
    uni.showToast({ title: '请选择单位', icon: 'none' })
    return
  }
  try {
    await updatePurchase(it.id, amt, units.value[d.unitIdx].id)
    it.purchaseAmount = amt
    it.purchaseUnitId = units.value[d.unitIdx].id
    it.purchaseUnitName = units.value[d.unitIdx].name
    uni.showToast({ title: '已保存', icon: 'success' })
  } catch (e: any) {
    uni.showToast({ title: e?.msg || '保存失败', icon: 'none' })
  }
}

// ============ 手动添加自定义采购项（V30） ============

function openAdd() {
  if (!detail.value || !detail.value.id) {
    uni.showToast({ title: '请先生成或选择一张清单', icon: 'none' })
    return
  }
  form.name = ''
  form.amount = ''
  form.unitIdx = -1
  form.catIdx = -1
  addOpen.value = true
}
function closeAdd() {
  addOpen.value = false
}

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

onShow(() => {
  loadPlans()
})

onLoad((q: any) => {
  if (q && q.id) {
    const id = Number(q.id)
    if (!isNaN(id)) loadDetail(id)
  }
})

onShareAppMessage(() => {
  const d = detail.value
  const id = d?.id
  const title = d ? `采购单 · ${rangeText(d)}` : '烟火小食单 · 采购单'
  return {
    title,
    path: id ? `/pages/shopping/List?id=${id}` : '/pages/shopping/List',
  }
})

// ============ 图片导出（canvas 2d，保留原实现） ============

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
      const amt = it.purchaseAmount != null ? `${it.purchaseAmount} ${it.purchaseUnitName || ''}` : (it.referenceGrams ? `约${it.referenceGrams}g` : '')
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
    // 权限拒绝 → 降级预览
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
.page { padding: 10px 12px 24px; }

/* 生成区 */
.gen { padding: 12px; }
.src-tabs { display: flex; gap: 8px; margin-bottom: 10px; }
.tab { flex: 1; text-align: center; font-size: 13px; padding: 7px 0; border-radius: 6px; background: #f3f3f3; color: #666; }
.tab.on { background: #FF6B35; color: #fff; font-weight: 600; }
.gen-input {
  font-size: 14px; color: #333; border: 1px solid #eee;
  border-radius: 6px; padding: 9px 10px; margin-bottom: 10px;
}
.gen-btn { width: 100%; }

.empty { text-align: center; color: #aaa; padding: 40px 0; font-size: 13px; }
.empty.small { padding: 18px 0; }

.detail { display: flex; flex-direction: column; gap: 10px; margin-top: 4px; }
.detail-head { display: flex; justify-content: space-between; align-items: baseline; padding: 4px 2px; }
.title { font-size: 17px; font-weight: 700; color: #222; }
.range { font-size: 12px; color: #999; }

/* 品类分区 */
.category { padding: 10px 12px; }
.cat-title { font-size: 13px; font-weight: 600; color: #FF6B35; padding-bottom: 6px; border-bottom: 1px dashed #f0e0d0; margin-bottom: 6px; }
.item { display: flex; padding: 10px 0; border-bottom: 1px solid #f5f5f5; }
.item:last-child { border-bottom: none; }
.item.done .iname { color: #bbb; text-decoration: line-through; }
.check { padding: 0 10px 0 0; }
.box { width: 20px; height: 20px; border: 2px solid #ddd; border-radius: 4px; display: flex; align-items: center; justify-content: center; font-size: 13px; color: transparent; }
.box.checked { background: #FF6B35; border-color: #FF6B35; color: #fff; }
.main { flex: 1; display: flex; flex-direction: column; gap: 6px; }
.row1 { display: flex; align-items: center; gap: 8px; }
.iname { font-size: 15px; color: #333; }
.ref-g { font-size: 11px; color: #999; }
.row2 { display: flex; align-items: center; gap: 8px; }
.amt { flex: 1; border: 1px solid #eee; border-radius: 6px; padding: 6px 8px; font-size: 14px; }
.unit-pk { min-width: 64px; }
.unit-txt { border: 1px solid #eee; border-radius: 6px; padding: 6px 10px; font-size: 14px; color: #333; }
.save { font-size: 13px; color: #fff; background: #2a9d8f; padding: 7px 12px; border-radius: 6px; }
.cur { font-size: 11px; color: #2a9d8f; }

/* 导出工具条 */
.export-bar { display: flex; gap: 8px; margin: 4px 0; }
.half { flex: 1; }
.sm { font-size: 13px; padding: 8px 0; }
.share { color: #2a9d8f; border-color: #2a9d8f; }

/* 手动添加 */
.add-btn { margin-top: 14px; width: 100%; }

.mask {
  position: fixed; inset: 0; background: rgba(0,0,0,0.4);
  display: flex; align-items: flex-end; z-index: 999;
}
.sheet {
  width: 100%; background: #fff; border-radius: 12px 12px 0 0;
  padding: 16px 16px calc(16px + env(safe-area-inset-bottom));
}
.sheet-title { font-size: 16px; font-weight: 700; color: #222; margin-bottom: 12px; }
.sheet-row { display: flex; align-items: center; gap: 10px; padding: 10px 0; border-bottom: 1px solid #f5f5f5; }
.sheet-row:last-of-type { border-bottom: none; }
.lbl { flex: 0 0 56px; font-size: 14px; color: #666; }
.sheet-input { flex: 1; border: 1px solid #eee; border-radius: 6px; padding: 8px 10px; font-size: 14px; }
.sheet-picker { flex: 1; border: 1px solid #eee; border-radius: 6px; padding: 8px 10px; font-size: 14px; color: #333; }
.sheet-actions { display: flex; gap: 10px; margin-top: 14px; }

.export-canvas { position: fixed; left: -9999px; top: 0; pointer-events: none; }

button::after { border: none; }
</style>
