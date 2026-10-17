<template>
  <!-- 写菜谱 · 粘贴链接导入：自动解析菜名、步骤和图片。 -->
  <view>
    <view class="imp-card">
      <ui-icon name="link" tone="primary" :size="28" />
      <text class="imp-title">从其他 App 导入菜谱</text>
      <text class="imp-sub">粘贴下厨房、美食杰、豆果的菜谱链接，自动解析菜名、步骤和图片</text>
      <view class="imp-chips">
        <text v-for="p in ['下厨房', '美食杰', '豆果']" :key="p" class="imp-chip">{{ p }}</text>
      </view>
    </view>
    <view class="imp-input-row">
      <ui-icon name="link" :size="18" />
      <input v-model="importUrl" class="imp-input" placeholder="粘贴菜谱链接" placeholder-class="ph" />
      <text v-if="importUrl" class="imp-clear" @click="importUrl = ''">✕</text>
    </view>
    <button class="btn-primary imp-btn" :disabled="importing" @click="doImport">
      {{ importing ? '正在解析菜谱…' : '开始导入' }}
    </button>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { importDishByUrl } from '@/api/dish'

const importUrl = ref('')
const importing = ref(false)

async function doImport() {
  const url = importUrl.value.trim()
  if (!url) {
    uni.showToast({ title: '请粘贴菜谱链接', icon: 'none' })
    return
  }
  if (!/^https?:\/\//.test(url)) {
    uni.showToast({ title: '链接格式不正确', icon: 'none' })
    return
  }
  importing.value = true
  try {
    const id = await importDishByUrl(url)
    uni.showToast({ title: '导入成功', icon: 'none' })
    setTimeout(() => uni.redirectTo({ url: `/pages/dish/Detail?id=${id}` }), 400)
  } catch (e) {
    uni.showToast({ title: `导入失败：${e}`, icon: 'none' })
  } finally {
    importing.value = false
  }
}
</script>

<style scoped>
.ph {
  color: var(--caption);
}
.imp-btn {
  background: var(--primary-deep);
}
.imp-card {
  background: var(--highlight);
  border: 1px solid var(--border);
  border-radius: var(--r-md);
  padding: 14px;
  margin-top: 6px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.imp-title { font-size: var(--fs-md); font-weight: 800; color: var(--title); }
.imp-sub { font-size: var(--fs-xs); color: var(--body); line-height: 1.5; }
.imp-chips { display: flex; gap: 6px; margin-top: 2px; }
.imp-chip {
  padding: 3px 10px;
  border-radius: var(--r-pill);
  background: var(--card);
  color: var(--body);
  font-size: var(--fs-tiny);
}
.imp-input-row {
  display: flex;
  align-items: center;
  gap: 6px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--r-md);
  padding: 0 12px;
  margin: 14px 0;
}
.imp-ico { font-size: var(--fs-md); }
.imp-input { flex: 1; padding: 11px 0; font-size: var(--fs-md); color: var(--title); }
.imp-clear { color: var(--caption); font-size: var(--fs-md); padding: 4px; }
</style>
