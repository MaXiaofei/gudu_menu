<template>
  <page-meta :page-style="gdThemeStyle" />
  <!-- 个人资料编辑（录入页：只有返回箭头）：头像 + 昵称，GET/PUT /member/me -->
  <view class="page">
    <ui-back-header />

    <ui-state v-if="loading" mode="loading" />
    <ui-state v-else-if="loadError" mode="error" text="资料加载失败" :retry="true" @retry="load" />
    <template v-else>
      <view class="body">
        <!-- 头像：小程序用微信头像选择（open-type=chooseAvatar），H5 选相册 -->
        <view class="avatar-wrap">
          <!-- #ifdef MP-WEIXIN -->
          <button class="avatar-btn" open-type="chooseAvatar" @chooseavatar="onChooseAvatar">
            <ui-avatar :name="name" :src="avatarSrc" :size="80" :fallback="'掌'" />
          </button>
          <!-- #endif -->
          <!-- #ifndef MP-WEIXIN -->
          <view class="avatar-btn" @click="pickAvatar">
            <ui-avatar :name="name" :src="avatarSrc" :size="80" :fallback="'掌'" />
          </view>
          <!-- #endif -->
          <text class="avatar-tip">{{ uploading ? '上传中…' : '点击更换头像' }}</text>
        </view>

        <view class="card">
          <view class="field">
            <text class="field-label">昵称</text>
            <input
              v-model="name"
              class="field-ipt"
              type="nickname"
              maxlength="20"
              placeholder="1–20 个字"
              placeholder-class="ph"
              data-testid="nickname-input"
            />
          </view>
          <view v-if="me?.phone" class="field">
            <text class="field-label">手机号</text>
            <text class="field-val">{{ me.phone }}</text>
          </view>
        </view>
        <text v-if="nameError" class="err">{{ nameError }}</text>
      </view>

      <view class="bottom">
        <button class="btn-primary" :disabled="saving || uploading" @click="save">
          {{ saving ? '保存中…' : '保存' }}
        </button>
      </view>
    </template>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getMe, updateMe, type MemberMe } from '@/api/member'
import { chooseImages, uploadImage } from '@/api/upload'
import { thumbOf } from '@/utils/image'
import { validateNickname } from '@/utils/validate'
import { useAuthStore } from '@/store/auth'

const auth = useAuthStore()
const loading = ref(true)
const loadError = ref(false)
const me = ref<MemberMe | null>(null)
const name = ref('')
const avatarUrl = ref('') // 服务端相对地址
const uploading = ref(false)
const saving = ref(false)
const touched = ref(false)

const avatarSrc = computed(() => (avatarUrl.value ? thumbOf(avatarUrl.value) : ''))
const nameError = computed(() => (touched.value ? validateNickname(name.value) : ''))

onLoad(() => load())

async function load() {
  loading.value = true
  loadError.value = false
  try {
    me.value = await getMe()
    name.value = me.value.name || ''
    avatarUrl.value = me.value.avatarUrl || ''
  } catch {
    loadError.value = true
  }
  loading.value = false
}

async function upload(path: string) {
  if (!path) return
  uploading.value = true
  try {
    const r = await uploadImage(path, 'avatar')
    avatarUrl.value = r.url
  } catch {
    uni.showToast({ title: '头像上传失败', icon: 'none' })
  } finally {
    uploading.value = false
  }
}

function onChooseAvatar(e: any) {
  upload(e?.detail?.avatarUrl || '')
}

async function pickAvatar() {
  const [path] = await chooseImages(1)
  if (path) upload(path)
}

async function save() {
  touched.value = true
  const err = validateNickname(name.value)
  if (err) {
    uni.showToast({ title: err, icon: 'none' })
    return
  }
  saving.value = true
  try {
    const changedAvatar = avatarUrl.value && avatarUrl.value !== (me.value?.avatarUrl || '')
    await updateMe({ name: name.value, avatarUrl: changedAvatar ? avatarUrl.value : undefined })
    auth.nickname = name.value.trim()
    uni.setStorageSync('nickname', name.value.trim())
    uni.showToast({ title: '已保存', icon: 'none' })
    setTimeout(() => uni.navigateBack(), 400)
  } catch {
    // request.ts 已提示
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--bg);
}
.body {
  flex: 1;
  padding: var(--sp-12) var(--sp-16) 0;
}
.avatar-wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--sp-8);
  margin: var(--sp-12) 0 var(--sp-24);
}
.avatar-btn {
  padding: 0;
  margin: 0;
  background: transparent;
  border-radius: var(--r-lg);
  line-height: 1;
}
.avatar-btn::after {
  border: none;
}
.avatar-tip {
  font-size: var(--fs-sm);
  color: var(--caption);
}
.card {
  background: var(--card);
  border-radius: var(--r-lg);
  overflow: hidden;
}
.field {
  display: flex;
  align-items: center;
  gap: var(--sp-12);
  padding: 13px 14px;
}
.field + .field {
  border-top: 1px solid var(--bg);
}
.field-label {
  width: 56px;
  font-size: var(--fs-md);
  color: var(--body);
}
.field-ipt {
  flex: 1;
  font-size: var(--fs-md);
  color: var(--title);
}
.field-val {
  flex: 1;
  font-size: var(--fs-md);
  color: var(--caption);
}
.ph {
  color: var(--caption);
}
.err {
  display: block;
  margin-top: var(--sp-8);
  font-size: var(--fs-sm);
  color: var(--error);
}
.bottom {
  padding: var(--sp-8) var(--sp-16) calc(12px + env(safe-area-inset-bottom));
}
</style>
