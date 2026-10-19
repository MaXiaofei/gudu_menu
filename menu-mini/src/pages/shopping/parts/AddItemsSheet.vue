<template>
  <!-- 采购清单 · 添加采购项（名称 + 数量文本，可连续加多行）。 -->
  <view class="mask" @click="$emit('close')">
    <view class="sheet" @click.stop>
      <text class="sheet-title">添加采购项</text>
      <view class="add-row">
        <input v-model="addName" class="add-ipt" placeholder="名称" placeholder-class="ph" :focus="true" />
        <input v-model="addQty" class="add-qty" placeholder="数量+单位 · 如 2斤" placeholder-class="ph" />
      </view>
      <view class="add-more" @click="pushAddRow">＋ 再加一行</view>
      <view v-if="addedRows.length" class="added-list">
        <text class="added-label">已添加 {{ addedRows.length }} 种</text>
        <view v-for="(r, i) in addedRows" :key="i" class="added-row">
          <text class="added-txt">{{ r.name }} {{ r.qty }}</text>
          <text class="added-del" @click="addedRows.splice(i, 1)">✕</text>
        </view>
      </view>
      <view class="sheet-bottom-row">
        <button class="btn-primary" :disabled="!canSaveAdd" @click="saveAdd">{{ saveAddLabel }}</button>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { addCustomItem } from '@/api/shopping'

const props = defineProps<{ listId: number }>()
const emit = defineEmits<{ (e: 'close'): void; (e: 'saved'): void }>()

const addName = ref('')
const addQty = ref('')
const addedRows = ref<{ name: string; qty: string }[]>([])

const canSaveAdd = computed(
  () => addedRows.value.length > 0 || addName.value.trim().length > 0,
)
const saveAddLabel = computed(() => {
  const n = addedRows.value.length + (addName.value.trim() ? 1 : 0)
  return n > 0 ? `添加 · 保存 ${n} 种` : '保存'
})

function pushAddRow() {
  const name = addName.value.trim()
  if (!name) return
  addedRows.value.push({ name, qty: addQty.value.trim() })
  addName.value = ''
  addQty.value = ''
}

async function saveAdd() {
  const rows = [...addedRows.value]
  if (addName.value.trim()) rows.push({ name: addName.value.trim(), qty: addQty.value.trim() })
  if (!rows.length) return
  try {
    for (const r of rows) {
      const m = r.qty.match(/\d+(\.\d+)?/)
      await addCustomItem({ listId: props.listId, name: r.name, amount: m ? Number(m[0]) : undefined })
    }
    addedRows.value = []
    addName.value = ''
    addQty.value = ''
    emit('saved')
  } catch {
    uni.showToast({ title: '添加失败', icon: 'none' })
  }
}
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
  padding: 16px 18px calc(14px + env(safe-area-inset-bottom));
}
.sheet-title {
  font-size: var(--fs-lg);
  font-weight: 700;
  color: var(--title);
  display: block;
  margin-bottom: 12px;
}
.add-row {
  display: flex;
  gap: 8px;
}
.add-ipt {
  flex: 1;
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: var(--r-sm);
  padding: 9px 10px;
  font-size: var(--fs-md);
  color: var(--title);
}
.add-qty {
  width: 150px;
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: var(--r-sm);
  padding: 9px 10px;
  font-size: var(--fs-md);
  color: var(--title);
}
.add-more {
  margin-top: 8px;
  font-size: var(--fs-sm);
  color: var(--primary);
  font-weight: 700;
}
.added-list {
  margin-top: 10px;
}
.added-label {
  font-size: var(--fs-xs);
  color: var(--caption);
}
.added-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 7px 0;
}
.added-txt {
  font-size: var(--fs-md);
  color: var(--title);
}
.added-del {
  color: var(--caption);
  font-size: var(--fs-sm);
}
.sheet-bottom-row {
  margin-top: 12px;
}
/* 分享视图 */
</style>
