<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { ArrowDown } from '@element-plus/icons-vue'
import { useAuthStore } from '@/store/auth'
import { useThemeStore } from '@/store/theme'

const router = useRouter()
const authStore = useAuthStore()
const themeStore = useThemeStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
const form = reactive({
  username: 'admin',
  password: 'admin123',
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function onSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      await authStore.login({ username: form.username, password: form.password })
      ElMessage.success('登录成功')
      router.push('/')
    } catch {
      // 错误信息已在 request 拦截器里 ElMessage
    } finally {
      loading.value = false
    }
  })
}
</script>

<template>
  <div class="login-page">
    <div class="login-card">
      <div class="title">
        <span class="dot"></span>烟火小食单
      </div>
      <div class="subtitle">家庭菜谱 · 菜单 · 营养管理后台</div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        @keyup.enter="onSubmit"
      >
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" clearable />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            show-password
            clearable
          />
        </el-form-item>
        <el-button
          type="primary"
          :loading="loading"
          style="width: 100%; margin-top: 6px"
          @click="onSubmit"
        >
          登 录
        </el-button>
      </el-form>

      <div class="theme-switch">
        <span class="sw-dot" :style="{ background: themeStore.currentTheme().primary }"></span>
        <el-dropdown trigger="click" @command="themeStore.apply">
          <span class="sw-trigger">
            {{ themeStore.currentTheme().name }}
            <el-icon style="margin-left: 2px"><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item
                v-for="t in themeStore.themes"
                :key="t.key"
                :command="t.key"
              >
                <span class="sw-dot" :style="{ background: t.primary }"></span>
                {{ t.name }}
                <span v-if="t.key === themeStore.current" style="color: var(--el-color-primary); margin-left: 8px; font-size: 12px">✓</span>
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, var(--el-color-primary-light-8), var(--yh-bg));
}
.login-card {
  width: 360px;
  background: var(--yh-panel, #fff);
  border-radius: 14px;
  box-shadow: 0 8px 30px rgba(60, 40, 20, 0.12);
  padding: 32px 30px 26px;
}
.title {
  font-size: 22px;
  font-weight: 700;
  color: #333;
  display: flex;
  align-items: center;
  gap: 10px;
}
.title .dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: var(--el-color-primary);
}
.subtitle {
  margin: 6px 0 22px;
  font-size: 13px;
  color: #9a8f80;
}
.theme-switch {
  margin-top: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  color: #7a6f60;
}
.sw-trigger {
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  color: #7a6f60;
  outline: none;
}
</style>
