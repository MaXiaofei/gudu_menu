<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage, type UploadFile } from 'element-plus'
import { request } from '@/api/request'

const importing = ref(false)
const fileRef = ref<File | null>(null)

function onExport() {
  // 直接走浏览器下载：调用 /backup/export，拿到 JSON 文件
  // 通过隐藏 a 标签 + token 放 query 或 header（此处用 fetch 带 header）
  const token = localStorage.getItem('yanhuo-token') || ''
  fetch('/api/backup/export', {
    method: 'GET',
    headers: { Authorization: token },
  })
    .then(async (resp) => {
      if (!resp.ok) throw new Error('导出失败')
      const blob = await resp.blob()
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `yanhuo-backup-${new Date().toISOString().slice(0, 10)}.json`
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
      URL.revokeObjectURL(url)
      ElMessage.success('导出成功')
    })
    .catch(() => {
      ElMessage.error('导出失败，请检查后端是否启动')
    })
}

function onFileChange(file: UploadFile) {
  if (file.raw) fileRef.value = file.raw
}

async function onImport() {
  if (!fileRef.value) {
    ElMessage.warning('请先选择备份文件')
    return
  }
  importing.value = true
  try {
    const form = new FormData()
    form.append('file', fileRef.value)
    await request({
      url: '/backup/import',
      method: 'post',
      data: form,
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    ElMessage.success('导入成功')
  } catch {
    // 错误已由拦截器提示
  } finally {
    importing.value = false
  }
}
</script>

<template>
  <div class="page">
    <el-card shadow="never" header="数据备份">
      <div class="block">
        <h3>导出</h3>
        <p>将全部业务数据导出为 JSON 文件。</p>
        <el-button type="primary" @click="onExport">导出 JSON</el-button>
      </div>
      <el-divider />
      <div class="block">
        <h3>导入</h3>
        <p>选择之前导出的 JSON 文件恢复数据。</p>
        <el-upload
          :auto-upload="false"
          :show-file-list="false"
          accept="application/json"
          :on-change="onFileChange"
        >
          <el-button>选择文件</el-button>
        </el-upload>
        <el-button
          type="success"
          style="margin-left: 12px"
          :loading="importing"
          :disabled="!fileRef"
          @click="onImport"
        >
          开始导入
        </el-button>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.page {
  background: var(--yh-panel, #fff);
  padding: 16px;
  border-radius: 8px;
  max-width: 720px;
}
.block h3 {
  margin: 0 0 6px;
  font-size: 15px;
}
.block p {
  margin: 0 0 12px;
  color: #7a6f60;
  font-size: 13px;
}
</style>
