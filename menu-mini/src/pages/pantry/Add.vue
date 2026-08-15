<template>
  <view class="page">
    <!-- 录入页：只有返回箭头（§13.1） -->
    <view class="top" :style="{ paddingTop: sb + 'px' }">
      <text class="back" @click="goBack">‹</text>
    </view>

    <scroll-view scroll-y class="body">
      <!-- 头说明（功能说明文案，保留） -->
      <text class="hint-line">朋友送 / 赠品 / 之前忘记登的旧库存，记一笔进来</text>

      <!-- 搜索框（⌕ + 搜库存 + ✕） -->
      <view class="search-box">
        <text class="search-ico">⌕</text>
        <input
          v-model="kw"
          class="search-ipt"
          placeholder="搜库存"
          placeholder-class="ph"
          :focus="autoFocus"
          @input="onInput"
        />
        <text v-if="kw" class="search-clear" @click="clearKw">✕</text>
      </view>

      <!-- ===== 步骤① 选食材 ===== -->
      <template v-if="step === 0">
        <!-- 未输入：提示卡 + 新建入口常驻 -->
        <view v-if="!q" class="empty-hint">
          <view class="empty-ico"><text class="empty-ico-txt">⌕</text></view>
          <text class="empty-line">输入名称，会显示库里已有的食材</text>
        </view>

        <template v-else>
          <!-- 库里已有（输入后才出现） -->
          <template v-if="matches.length">
            <text class="sec-label">库里已有</text>
            <view class="sheet">
              <view v-for="i in matches" :key="i.id" class="ing-row" @click="pick(i)">
                <view class="ing-main">
                  <text class="ing-name">{{ i.name }}</text>
                  <text v-if="levelText(i.id)" class="ing-home">{{ levelText(i.id) }}</text>
                </view>
                <text class="pick" :class="{ on: selected?.id === i.id }">{{ selected?.id === i.id ? '已选' : '选' }}</text>
              </view>
            </view>
          </template>

          <!-- 库存里没有？（精确同名时隐藏） -->
          <template v-if="!exactMatch">
            <text class="sec-label">库存里没有？</text>
            <view class="dashed" @click="onNewTile">
              <text class="dashed-title">＋ 新建食材并入库</text>
              <text v-if="q" class="dashed-sub">「{{ q }}」建档同时入库</text>
            </view>
          </template>
        </template>
      </template>

      <!-- ===== 步骤② 定档位 + 来源 ===== -->
      <template v-if="step === 1">
        <view class="food-head">
          <ui-avatar :name="name || '食'" :size="52" :fallback="'食'" />
          <view class="food-info">
            <text class="food-name">{{ name }}</text>
            <text class="food-sub">{{ stockSub }}</text>
          </view>
        </view>

        <text class="q-line">补充后，家里有多少？</text>
        <view class="level-cards">
          <view class="lcard" :class="{ on: level === 'ENOUGH' }" @click="level = 'ENOUGH'">
            <text class="lcard-title">充足</text>
            <text class="lcard-sub">默认</text>
          </view>
          <view class="lcard" :class="{ on: level === 'LOW' }" @click="level = 'LOW'">
            <text class="lcard-title">不足</text>
            <text class="lcard-sub">一点点</text>
          </view>
        </view>

        <text class="sec-label">来源备注</text>
        <view class="src-chips">
          <view
            v-for="s in sourceOptions"
            :key="s"
            class="src-chip"
            :class="{ on: sourceNote === s }"
            @click="sourceNote = s"
          >{{ s }}</view>
        </view>
        <view style="height: 100px" />
      </template>
    </scroll-view>

    <!-- 底部 -->
    <view class="bottom">
      <button v-if="step === 0" class="btn-primary wide" :disabled="!canNext" @click="next">下一步</button>
      <button v-else class="btn-primary wide" :disabled="saving" @click="submit">
        {{ saving ? '入库中…' : '入库' }}
      </button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, nextTick } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { listAllIngredients, type IngredientItem } from '@/api/ingredient'
import { listGroupedAll, pantryManualAdd, type PantryGrouped } from '@/api/pantry'
import { statusBarHeight } from '@/utils/token'

const sb = statusBarHeight()
const autoFocus = ref(false)

const step = ref(0)
const kw = ref('')
const selected = ref<IngredientItem | null>(null)
const newMode = ref(false)
const newName = ref('')

const ingredients = ref<IngredientItem[]>([])
const levelByIng = ref<Record<number, string>>({})
const loading = ref(true)

const level = ref('ENOUGH')
const sourceNote = ref('朋友送')
const sourceOptions = ['朋友送', '赠品', '旧库存补登', '其他']
const saving = ref(false)

onLoad(async () => {
  autoFocus.value = true
  try {
    const [ings, grouped] = await Promise.all([listAllIngredients(), listGroupedAll()])
    ingredients.value = ings
    const map: Record<number, string> = {}
    grouped.items.forEach((it) => (map[it.ingredientId] = it.level))
    levelByIng.value = map
  } catch {}
  loading.value = false
})

const q = computed(() => kw.value.trim())
const matches = computed(() =>
  q.value ? ingredients.value.filter((i) => i.name.includes(q.value)) : [],
)
const exactMatch = computed(
  () => q.value.length > 0 && ingredients.value.some((i) => i.name === q.value),
)
const name = computed(() => selected.value?.name ?? newName.value)
const canNext = computed(() => !!selected.value || newMode.value)
const stockSub = computed(() => {
  if (!selected.value) return '新建档 · 家里还没有'
  const lv = levelByIng.value[selected.value.id]
  const label = lv === 'ENOUGH' ? '充足' : lv === 'LOW' ? '不足' : lv ? '用完' : ''
  return `家里：${label || '还没有'} · 入库记一笔`
})

function onInput() {
  // 选中的食材与新档随输入重置
  selected.value = null
  newMode.value = false
}

function clearKw() {
  kw.value = ''
  selected.value = null
  newMode.value = false
}

function levelText(id: number): string {
  const lv = levelByIng.value[id]
  if (!lv) return ''
  return `家里：${lv === 'ENOUGH' ? '充足' : lv === 'LOW' ? '不足' : '用完'}`
}

function pick(i: IngredientItem) {
  selected.value = i
  newMode.value = false
}

/** 新建入口：已输入=走新建路径；未输入=聚焦搜索框。 */
function onNewTile() {
  if (!q.value) {
    autoFocus.value = false
    nextTick(() => (autoFocus.value = true))
    return
  }
  newMode.value = true
  selected.value = null
  newName.value = q.value
}

function next() {
  step.value = 1
}

function goBack() {
  if (step.value === 1) {
    step.value = 0
    return
  }
  uni.navigateBack()
}

async function submit() {
  if (!name.value) {
    uni.showToast({ title: '请先选食材', icon: 'none' })
    return
  }
  saving.value = true
  try {
    await pantryManualAdd({
      ingredientId: selected.value?.id,
      name: selected.value ? undefined : name.value,
      level: level.value,
      sourceNote: sourceNote.value,
    })
    uni.showToast({ title: '已入库', icon: 'none' })
    setTimeout(() => uni.navigateBack(), 400)
  } catch (e) {
    uni.showToast({ title: `入库失败：${e}`, icon: 'none' })
  } finally {
    saving.value = false
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
.top {
  padding: 0 6px;
}
.back {
  font-size: 26px;
  font-weight: 700;
  color: var(--title);
  padding: 4px 10px;
  line-height: 1.2;
}
.body {
  flex: 1;
  min-height: 0;
  padding: 12px 16px 0;
  box-sizing: border-box;
}
.hint-line {
  display: block;
  font-size: 11px;
  color: var(--caption);
}
.search-box {
  display: flex;
  align-items: center;
  gap: 6px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--r-md);
  padding: 0 10px;
  margin-top: 12px;
}
.search-ico {
  color: var(--caption);
  font-size: 14px;
}
.search-ipt {
  flex: 1;
  font-size: 12px;
  color: var(--title);
  padding: 9px 0;
}
.search-clear {
  color: var(--caption);
  font-size: 13px;
  padding: 4px;
}
.ph { color: var(--caption); }
/* 未输入提示卡 */
.empty-hint {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--r-sm);
  padding: 22px 16px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  margin-top: 16px;
}
.empty-ico {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: var(--secondary);
  display: flex;
  align-items: center;
  justify-content: center;
}
.empty-ico-txt {
  color: var(--primary-deep);
  font-size: 15px;
}
.empty-line {
  font-size: 12px;
  color: var(--caption);
}
.sec-label {
  display: block;
  font-size: 10px;
  font-weight: 800;
  color: var(--caption);
  letter-spacing: 1px;
  margin: 16px 0 8px;
}
/* 库里已有 */
.sheet {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--r-sm);
  overflow: hidden;
}
.ing-row {
  display: flex;
  align-items: center;
  padding: 9px 12px;
}
.ing-row + .ing-row {
  border-top: 1px solid var(--border);
}
.ing-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.ing-name {
  font-size: 13px;
  font-weight: 700;
  color: var(--title);
}
.ing-home {
  font-size: 9px;
  color: var(--caption);
}
.pick {
  padding: 4px 11px;
  border-radius: var(--r-sm);
  border: 1px solid var(--border);
  background: var(--bg);
  color: var(--caption);
  font-size: 10px;
  font-weight: 800;
}
.pick.on {
  background: var(--primary);
  border-color: var(--primary);
  color: #FFFFFF;
}
/* 新建虚线卡 */
.dashed {
  border: 1.5px dashed var(--primary);
  border-radius: 10px;
  padding: 11px 12px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.dashed-title {
  font-size: 12px;
  font-weight: 800;
  color: var(--primary);
}
.dashed-sub {
  font-size: 10px;
  color: var(--caption);
}
/* 步骤② */
.food-head {
  display: flex;
  align-items: center;
  gap: 12px;
}
.food-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.food-name {
  font-size: 18px;
  font-weight: 800;
  color: var(--title);
}
.food-sub {
  font-size: 10px;
  color: var(--caption);
}
.q-line {
  display: block;
  font-size: 11px;
  color: var(--body);
  margin: 16px 0 8px;
}
.level-cards {
  display: flex;
  gap: 7px;
}
.lcard {
  flex: 1;
  border: 1px solid var(--border);
  border-radius: var(--r-md);
  background: var(--bg);
  padding: 11px 8px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}
.lcard.on {
  border: 2px solid var(--primary);
}
.lcard-title {
  font-size: 13px;
  font-weight: 800;
  color: var(--title);
}
.lcard-sub {
  font-size: 9px;
  color: var(--caption);
}
.src-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
}
.src-chip {
  padding: 6px 13px;
  border-radius: var(--r-sm);
  background: var(--card);
  border: 1px solid var(--border);
  color: var(--body);
  font-size: 10px;
  font-weight: 800;
}
.src-chip.on {
  background: var(--primary);
  border-color: var(--primary);
  color: #FFFFFF;
}
/* 底部 */
.bottom {
  padding: 8px 16px calc(12px + env(safe-area-inset-bottom));
  background: var(--card);
  border-top: 1px solid var(--border);
  display: flex;
  justify-content: center;
}
.wide {
  min-width: 200px;
}
</style>
