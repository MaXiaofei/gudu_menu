<script setup lang="ts">
import { computed } from 'vue'

/**
 * 通用分页组件：在 el-pagination 基础上增加「首页 / 尾页」按钮。
 * el-pagination 无原生 first/last，此处用两个 el-button 包裹实现。
 */
const props = withDefaults(
  defineProps<{
    total: number
    currentPage: number
    pageSize?: number
  }>(),
  {
    pageSize: 10,
  },
)

const emit = defineEmits<{
  (e: 'current-change', page: number): void
}>()

// 总页数：total 为 0 时仍算 1 页，避免尾页按钮恒 disabled
const pageCount = computed(() => {
  if (props.total <= 0) return 1
  return Math.ceil(props.total / props.pageSize) || 1
})

const isFirst = computed(() => props.currentPage <= 1)
const isLast = computed(() => props.currentPage >= pageCount.value)

function goFirst() {
  if (isFirst.value) return
  emit('current-change', 1)
}

function goLast() {
  if (isLast.value) return
  emit('current-change', pageCount.value)
}

function onPageChange(p: number) {
  emit('current-change', p)
}
</script>

<template>
  <div class="yh-pagination">
    <el-button
      class="yh-pagination__edge"
      :disabled="isFirst"
      @click="goFirst"
    >首页</el-button>
    <el-pagination
      background
      layout="total, prev, pager, next, jumper"
      :total="total"
      :page-size="pageSize"
      :current-page="currentPage"
      @current-change="onPageChange"
    />
    <el-button
      class="yh-pagination__edge"
      :disabled="isLast"
      @click="goLast"
    >尾页</el-button>
  </div>
</template>

<style scoped>
.yh-pagination {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 16px;
}
/* 让首尾页按钮高度与 el-pagination 背景分页按钮视觉对齐 */
.yh-pagination__edge {
  height: 28px;
  padding: 0 12px;
}
</style>
