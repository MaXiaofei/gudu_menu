<template>
  <!-- 首次登录完善资料（A.7）：昵称仍是默认「微信用户」时弹一次，可跳过；跳过或完成后本机不再弹。
       表单复用 ui-profile-form（chooseAvatar + type=nickname）；保存走 PUT /member/me（后端内容安全，451/452 由 request.ts 按场景提示）。 -->
  <view v-if="visible" class="ps-mask" data-testid="profile-setup">
    <view class="ps-sheet">
      <text class="ps-title">设置头像和昵称</text>
      <text class="ps-sub">让家人和一起点菜的朋友认出你</text>
      <ui-profile-form
        v-model:name="name"
        v-model:avatar-url="avatarUrl"
        v-model:uploading="uploading"
        :avatar-size="72"
        avatar-tip="点击选择头像"
        placeholder="点这里用微信昵称或自己起一个"
      />
      <text v-if="error" class="ps-err">{{ error }}</text>
      <button class="btn-primary ps-save" :disabled="saving || uploading" data-testid="profile-setup-save" @click="save">
        {{ saving ? '保存中…' : '完成' }}
      </button>
      <view class="ps-skip" data-testid="profile-setup-skip" @click="skip"><text class="ps-skip-txt">跳过，以后在「我的」里改</text></view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { updateMe } from '@/api/member'
import { launchAuthDone, markOnboarded } from '@/utils/loginRedirect'
import { markProfilePrompted, shouldPromptProfile, validateSetupNickname } from '@/utils/profileSetup'

const emit = defineEmits<{ (e: 'done', v: { saved: boolean; name?: string }): void }>()

const visible = ref(false)
const name = ref('')
const avatarUrl = ref('')
const uploading = ref(false)
const saving = ref(false)
const error = ref('')

function setTabBar(show: boolean) {
  // 原生 tabBar 在页面之上，弹窗期间藏起来；非 Tab 页调用失败静默
  try {
    const opt = { animation: false, fail: () => {} } as any
    if (show) uni.showTabBar(opt)
    else uni.hideTabBar(opt)
  } catch {
    // 测试环境无 tabBar
  }
}

/** 页面在拿到登录态后调用（也会在挂载时自动检查一次）。 */
function check() {
  if (visible.value || !shouldPromptProfile()) return
  visible.value = true
  setTabBar(false)
}

// 启动页可能在静默登录完成前就挂载：等启动登录流程结束再判断
onMounted(() => launchAuthDone().then(check))
defineExpose({ check })

function close(saved: boolean, n?: string) {
  markProfilePrompted()
  // 跳过 = 先别打扰：成员档案引导也记为已处理，不连着弹第二次（multi-tenant-family 首次引导）
  if (!saved) markOnboarded()
  visible.value = false
  setTabBar(true)
  emit('done', { saved, name: n })
}

function skip() {
  close(false)
}

async function save() {
  const err = validateSetupNickname(name.value)
  error.value = err
  if (err) return
  saving.value = true
  try {
    const n = name.value.trim()
    await updateMe({ name: n, avatarUrl: avatarUrl.value || undefined })
    uni.setStorageSync('nickname', n)
    uni.showToast({ title: '已保存', icon: 'none' })
    close(true, n)
  } catch {
    // request.ts 已提示（含内容安全 451/452 友好文案），弹窗保持打开可修改
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.ps-mask {
  position: fixed;
  left: 0;
  right: 0;
  top: 0;
  bottom: 0;
  z-index: 999;
  background: var(--mask);
  display: flex;
  align-items: flex-end;
}
.ps-sheet {
  width: 100%;
  box-sizing: border-box;
  background: var(--bg);
  border-radius: var(--r-xl) var(--r-xl) 0 0;
  padding: var(--sp-24) var(--sp-16) calc(var(--sp-16) + env(safe-area-inset-bottom));
  display: flex;
  flex-direction: column;
}
.ps-title {
  font-size: var(--fs-subtitle);
  font-weight: 800;
  color: var(--title);
  text-align: center;
}
.ps-sub {
  margin-top: var(--sp-4);
  font-size: var(--fs-sm);
  color: var(--caption);
  text-align: center;
}
.ps-err {
  margin-top: var(--sp-8);
  font-size: var(--fs-sm);
  color: var(--error);
}
.ps-save {
  margin-top: var(--sp-16);
}
.ps-skip {
  display: flex;
  justify-content: center;
  padding: var(--sp-12) 0 0;
}
.ps-skip-txt {
  font-size: var(--fs-md);
  color: var(--body);
}
</style>
