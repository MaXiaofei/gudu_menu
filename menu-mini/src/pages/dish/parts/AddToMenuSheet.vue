<template>
  <!-- 加到食集：近期食集半屏弹层 + 新建食集输入框（菜谱详情用）。 -->
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
          <view class="sheet-row-main"><text class="sheet-row-name new">＋ 新建食集</text></view>
        </view>
      </scroll-view>
    </view>
  </view>

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

<script setup lang="ts">
import { ref } from 'vue'
import { listMenus, createMenu, addDishToMenu, type Menu } from '@/api/menu'
import { isFromToday, mdHm } from '@/utils/datetime'

const props = defineProps<{ dishId: number; dishName: string }>()

const sheetVisible = ref(false)
const dialogVisible = ref(false)
const recentMenus = ref<Menu[]>([])
const newMenuName = ref('')
const busy = ref(false)

function menuSub(m: Menu): string {
  const parts: string[] = []
  if (m.createTime) parts.push(mdHm(m.createTime))
  parts.push(`份数 ${m.servingCount ?? 1}`)
  parts.push(m.status === 'DONE' ? '已完成' : '进行中')
  return parts.join(' · ')
}

/** 打开：只列今天 0 点及以后创建的食集（对齐 APP）；没有就直接新建。 */
async function open() {
  if (busy.value) return
  busy.value = true
  try {
    const r = await listMenus(1, 50)
    recentMenus.value = r.records.filter((m) => isFromToday(m.createTime))
    if (!recentMenus.value.length) openCreateDialog()
    else sheetVisible.value = true
  } catch {
    uni.showToast({ title: '加入食集失败', icon: 'none' })
  } finally {
    busy.value = false
  }
}

function openCreateDialog() {
  newMenuName.value = props.dishName
  dialogVisible.value = true
}

async function pickMenu(menuId: number) {
  if (menuId === -1) {
    sheetVisible.value = false
    openCreateDialog()
    return
  }
  try {
    await addDishToMenu(menuId, props.dishId, props.dishName)
    sheetVisible.value = false
    uni.showToast({ title: `已加入食集「${recentMenus.value.find((m) => m.id === menuId)?.name ?? ''}」`, icon: 'none' })
  } catch {
    uni.showToast({ title: '加入食集失败', icon: 'none' })
  }
}

async function confirmCreateMenu() {
  const name = newMenuName.value.trim() || props.dishName
  try {
    await createMenu(name, [props.dishId])
    dialogVisible.value = false
    uni.showToast({ title: `已加入新食集「${name}」`, icon: 'none' })
  } catch {
    uni.showToast({ title: '加入食集失败', icon: 'none' })
  }
}

defineExpose({ open, busy })
</script>

<style scoped>
.mask {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  background: var(--mask);
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
  font-size: var(--fs-lg);
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
  font-size: var(--fs-md);
  font-weight: 700;
  color: var(--title);
}
.sheet-row-name.new {
  color: var(--primary-deep);
}
.sheet-row-sub {
  font-size: var(--fs-sm);
  color: var(--body);
}
.sheet-row-arrow {
  color: var(--body);
  font-size: var(--fs-lg);
  font-weight: 700;
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
  font-size: var(--fs-lg);
  font-weight: 700;
  color: var(--title);
}
.dialog-ipt {
  margin-top: 14px;
  border-bottom: 1px solid var(--border);
  padding: 8px 2px;
  font-size: var(--fs-md);
  color: var(--title);
}
.dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 18px;
  margin-top: 10px;
}
.dialog-btn {
  font-size: var(--fs-md);
  color: var(--body);
  padding: 6px 4px;
}
.dialog-btn.primary {
  color: var(--primary-deep);
  font-weight: 700;
}
.ph {
  color: var(--caption);
}
</style>
