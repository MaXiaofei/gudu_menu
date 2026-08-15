<template>
  <view class="page">
    <!-- 顶栏：箭头 + 菜名 + 副信息（§13.2） -->
    <ui-back-header>
      <template #subtitle>
        <view v-if="detail" class="head-sub">
          <text class="sub-line">备料 {{ detail.dish.prepTime ?? '-' }}分 · 烹饪 {{ detail.dish.cookTime ?? '-' }}分 · 难度 {{ detail.dish.difficulty ?? '-' }}/5</text>
          <text v-if="detail.dish.sourceName" class="sub-line">来源：{{ detail.dish.sourceName }}</text>
        </view>
      </template>
    </ui-back-header>

    <ui-state v-if="loading" mode="loading" />
    <ui-state v-else-if="!detail" mode="error" text="加载详情失败" :retry="true" @retry="load" />
    <template v-else>
      <scroll-view scroll-y class="body">
        <!-- 封面（220px；点击全屏；无图首字占位，§10.5） -->
        <view class="cover-wrap" @click="previewCover">
          <image v-if="coverUrl" class="cover" :src="coverUrl" mode="aspectFill" @error="coverFailed = true" />
          <view v-else class="cover cover-ph">
            <text class="cover-ph-txt">{{ initial }}</text>
          </view>
        </view>

        <!-- 标签区 -->
        <view v-if="relNames.length || detail.dish.note" class="tags-wrap">
          <view v-if="relNames.length" class="tags">
            <view v-for="n in relNames" :key="n" class="tag">{{ n }}</view>
          </view>
          <text v-if="detail.dish.note" class="note">{{ detail.dish.note }}</text>
        </view>

        <!-- 用料 -->
        <view v-if="detail.ingredients.length" class="section">
          <view class="sec-head">
            <text class="sec-title">用料</text>
            <text class="sec-meta">份数 1 · 共 {{ detail.ingredients.length }} 样</text>
          </view>
          <view class="ing-card">
            <view v-for="(ing, i) in detail.ingredients" :key="ing.ingredientId">
              <view class="ing-row">
                <view class="ing-initial"><text class="ing-initial-txt">{{ ingInitial(ing) }}</text></view>
                <text class="ing-name">{{ ing.ingredientName || `#${ing.ingredientId}` }}</text>
                <text class="ing-amount">{{ amountText(ing) }}</text>
              </view>
              <view v-if="i < detail.ingredients.length - 1" class="divider" />
            </view>
          </view>
          <text class="ing-note">用量为 1 份基准；做菜时按份数自动放大。</text>
        </view>

        <!-- 做法 -->
        <view v-if="detail.steps.length" class="section">
          <view class="sec-title sec-title-alone">做法</view>
          <view v-for="(s, i) in detail.steps" :key="i" class="step">
            <view class="step-head">
              <view class="step-no"><text class="step-no-txt">{{ i + 1 }}</text></view>
              <text class="step-title">步骤 {{ i + 1 }}</text>
            </view>
            <text v-if="s.text" class="step-text">{{ s.text }}</text>
            <view v-if="stepThumbs(s).length" class="step-imgs">
              <image
                v-for="(img, j) in stepThumbs(s)"
                :key="j"
                class="step-img"
                :src="img"
                mode="aspectFill"
                lazy-load
                @click="previewStep(s, j)"
              />
            </view>
          </view>
        </view>

        <view style="height: 88px" />
      </scroll-view>

      <!-- 底部：加到食集（食集内查看 showActions=0 时隐藏） -->
      <view v-if="showActions" class="bottom">
        <button class="btn-add" :disabled="adding" @click="onAddToMenu">
          {{ adding ? '处理中…' : '加到食集' }}
        </button>
      </view>

      <!-- 加到食集：选择近期食集（半屏弹层） -->
      <view v-if="sheetVisible" class="mask" @click="sheetVisible = false">
        <view class="sheet" @click.stop>
          <view class="sheet-title">加到哪个食集？</view>
          <scroll-view scroll-y class="sheet-list">
            <view v-for="m in recentMenus" :key="m.id" class="sheet-row" @click="pickMenu(m.id)">
              <view class="sheet-row-main">
                <text class="sheet-row-name">{{ m.name }}</text>
                <text class="sheet-row-sub">{{ menuSub(m) }}</text>
              </view>
              <text class="sheet-row-arrow">›</text>
            </view>
            <view class="sheet-row" @click="pickMenu(-1)">
              <view class="sheet-row-main">
                <text class="sheet-row-name new">＋ 新建食集</text>
              </view>
            </view>
          </scroll-view>
        </view>
      </view>

      <!-- 新建食集：输入弹窗（预填菜名） -->
      <view v-if="dialogVisible" class="mask" @click="dialogVisible = false">
        <view class="dialog" @click.stop>
          <text class="dialog-title">新建食集</text>
          <input
            v-model="newMenuName"
            class="dialog-ipt"
            placeholder="食集名（如：今晚的饭）"
            placeholder-class="ph"
            :focus="dialogVisible"
            @confirm="confirmCreateMenu"
          />
          <view class="dialog-actions">
            <text class="dialog-btn" @click="dialogVisible = false">取消</text>
            <text class="dialog-btn primary" @click="confirmCreateMenu">确定</text>
          </view>
        </view>
      </view>
    </template>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { dishDetail, amountText, type DishDetail } from '@/api/dish'
import { listMenus, createMenu, addDishToMenu, type Menu } from '@/api/menu'
import { thumbOf, toAbsolute, thumbList, originalList } from '@/utils/image'
import { isFromToday, mdHm } from '@/utils/datetime'

const loading = ref(true)
const detail = ref<DishDetail | null>(null)
const showActions = ref(true)
const adding = ref(false)
const coverFailed = ref(false)

// 加到食集弹层
const sheetVisible = ref(false)
const recentMenus = ref<Menu[]>([])
const dialogVisible = ref(false)
const newMenuName = ref('')

onLoad((options) => {
  const id = Number(options?.id || 0)
  if (options?.showActions === '0') showActions.value = false
  load(id)
})

async function load(id: number) {
  loading.value = true
  try {
    detail.value = await dishDetail(id)
  } catch {
    detail.value = null
  }
  loading.value = false
}

const initial = computed(() => {
  const n = detail.value?.dish.name || ''
  return [...n][0] || '菜'
})
const relNames = computed(() => {
  const d = detail.value?.dish
  if (!d) return []
  return [...(d.cuisineNames ?? []), ...(d.categoryNames ?? []), ...(d.tagNames ?? [])]
})
const coverUrl = computed(() =>
  coverFailed.value ? '' : thumbOf(detail.value?.dish.coverUrl),
)

function ingInitial(ing: { ingredientName?: string | null; ingredientId: number }): string {
  const n = (ing.ingredientName || '').trim()
  return [...n][0] || '食'
}
function menuSub(m: Menu): string {
  const parts: string[] = []
  if (m.createTime) parts.push(mdHm(m.createTime))
  parts.push(`份数 ${m.servingCount ?? 1}`)
  parts.push(m.status === 'DONE' ? '已完成' : '进行中')
  return parts.join(' · ')
}
function stepThumbs(s: { images?: string | null }): string[] {
  return thumbList(s.images)
}

function previewCover() {
  const url = toAbsolute(detail.value?.dish.coverUrl)
  if (url) uni.previewImage({ urls: [url] })
}
function previewStep(s: { images?: string | null }, index: number) {
  const urls = originalList(s.images)
  if (urls.length) uni.previewImage({ urls, current: urls[index] })
}

// ---- 加到食集（对齐 APP：只列今天 0 点及以后创建的食集） ----
async function onAddToMenu() {
  if (adding.value) return
  adding.value = true
  try {
    const r = await listMenus(1, 50)
    recentMenus.value = r.records.filter((m) => isFromToday(m.createTime))
    if (!recentMenus.value.length) {
      openCreateDialog()
    } else {
      sheetVisible.value = true
    }
  } catch {
    uni.showToast({ title: '加入食集失败', icon: 'none' })
  } finally {
    adding.value = false
  }
}

function openCreateDialog() {
  newMenuName.value = detail.value?.dish.name || ''
  dialogVisible.value = true
}

async function pickMenu(menuId: number) {
  if (menuId === -1) {
    sheetVisible.value = false
    openCreateDialog()
    return
  }
  const d = detail.value?.dish
  if (!d) return
  try {
    await addDishToMenu(menuId, d.id, d.name)
    sheetVisible.value = false
    uni.showToast({ title: `已加入食集「${recentMenus.value.find((m) => m.id === menuId)?.name ?? ''}」`, icon: 'none' })
  } catch {
    uni.showToast({ title: '加入食集失败', icon: 'none' })
  }
}

async function confirmCreateMenu() {
  const d = detail.value?.dish
  if (!d) return
  const name = (newMenuName.value.trim() || d.name)
  try {
    await createMenu(name, [d.id])
    dialogVisible.value = false
    uni.showToast({ title: `已加入新食集「${name}」`, icon: 'none' })
  } catch {
    uni.showToast({ title: '加入食集失败', icon: 'none' })
  }
}
</script>

<style scoped>
.page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--bg);
}
.body {
  flex: 1;
  min-height: 0;
}
.head-sub {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.sub-line {
  font-size: 12px;
  color: var(--caption);
}
/* 封面 */
.cover-wrap {
  width: 100%;
  height: 220px;
  background: var(--secondary);
}
.cover {
  width: 100%;
  height: 100%;
}
.cover-ph {
  display: flex;
  align-items: center;
  justify-content: center;
}
.cover-ph-txt {
  font-size: 64px;
  font-weight: 700;
  color: var(--title);
  opacity: 0.45;
}
/* 标签 */
.tags-wrap {
  padding: 12px 16px 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.tag {
  padding: 3px 10px;
  border-radius: var(--r-pill);
  background: var(--primary-soft);
  color: var(--title);
  font-size: 11px;
}
.note {
  font-size: 12px;
  color: var(--body);
}
/* 分区 */
.section {
  padding: 16px 16px 0;
}
.sec-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 8px;
}
.sec-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--title);
}
.sec-title-alone {
  margin-bottom: 8px;
}
.sec-meta {
  font-size: 11px;
  color: var(--caption);
}
/* 用料 */
.ing-card {
  background: var(--card);
  border-radius: var(--r-md);
  overflow: hidden;
}
.ing-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
}
.ing-initial {
  width: 28px;
  height: 28px;
  border-radius: var(--r-sm);
  background: var(--primary-soft);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.ing-initial-txt {
  font-size: 13px;
  color: var(--primary-deep);
  font-weight: 600;
}
.ing-name {
  flex: 1;
  font-size: 12px;
  font-weight: 700;
  color: var(--title);
}
.ing-amount {
  font-size: 12px;
  font-weight: 800;
  color: var(--title);
}
.divider {
  height: 1px;
  background: var(--border);
  margin-left: 48px;
}
.ing-note {
  display: block;
  padding: 4px 0 0;
  font-size: 10px;
  color: var(--caption);
}
/* 步骤 */
.step {
  border-top: 1px solid var(--border);
  padding: 12px 0;
}
.step:first-of-type {
  border-top: none;
}
.step-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.step-no {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: var(--primary);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.step-no-txt {
  color: #FFFFFF;
  font-size: 11px;
  font-weight: 700;
}
.step-title {
  font-size: 13px;
  font-weight: 700;
  color: var(--title);
}
.step-text {
  font-size: 13px;
  color: var(--body);
  line-height: 1.6;
}
.step-imgs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
}
.step-img {
  width: 80px;
  height: 80px;
  border-radius: var(--r-sm);
  background: var(--secondary);
}
/* 底部按钮 */
.bottom {
  padding: 8px 16px calc(12px + env(safe-area-inset-bottom));
  background: var(--card);
  border-top: 1px solid var(--border);
}
.btn-add {
  background: var(--success);
  color: #FFFFFF;
  border-radius: var(--r-md);
  font-size: 15px;
  font-weight: 700;
  padding: 13px 0;
}
.btn-add[disabled] {
  background: var(--border);
  color: rgba(255, 255, 255, 0.85);
}
/* 弹层 */
.mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  z-index: 100;
  display: flex;
  align-items: flex-end;
}
.sheet {
  width: 100%;
  background: var(--card);
  border-radius: var(--r-xl) var(--r-xl) 0 0;
  padding: 14px 0 calc(12px + env(safe-area-inset-bottom));
}
.sheet-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--title);
  padding: 0 18px 10px;
}
.sheet-list {
  max-height: 50vh;
}
.sheet-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 18px;
}
.sheet-row-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.sheet-row-name {
  font-size: 14px;
  font-weight: 700;
  color: var(--title);
}
.sheet-row-name.new {
  color: var(--primary);
}
.sheet-row-sub {
  font-size: 11px;
  color: var(--caption);
}
.sheet-row-arrow {
  color: var(--caption);
  font-size: 16px;
  font-weight: 700;
}
/* 对话框 */
.dialog-mask-center {
  align-items: center;
  justify-content: center;
}
.dialog {
  position: fixed;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%);
  width: 78vw;
  background: var(--card);
  border-radius: var(--r-lg);
  padding: 20px 18px 12px;
  z-index: 110;
}
.dialog-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--title);
}
.dialog-ipt {
  margin-top: 14px;
  border-bottom: 1px solid var(--border);
  padding: 8px 2px;
  font-size: 14px;
  color: var(--title);
}
.dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 18px;
  margin-top: 10px;
}
.dialog-btn {
  font-size: 14px;
  color: var(--caption);
  padding: 6px 4px;
}
.dialog-btn.primary {
  color: var(--primary);
  font-weight: 700;
}
.ph {
  color: var(--caption);
}
</style>
