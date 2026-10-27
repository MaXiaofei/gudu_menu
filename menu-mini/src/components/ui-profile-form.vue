<template>
  <!-- 头像 + 昵称表单（资料编辑页、首次登录完善资料弹窗共用）：
       小程序用微信官方头像选择（open-type=chooseAvatar）+ 昵称输入（type=nickname），H5 选相册。
       上传走 uploadImage(…, 'avatar')（后端异步内容安全）；默认插槽放卡片内的额外字段。 -->
  <view class="pf">
    <view class="avatar-wrap">
      <!-- #ifdef MP-WEIXIN -->
      <button class="avatar-btn" open-type="chooseAvatar" data-testid="choose-avatar" @chooseavatar="onChooseAvatar">
        <ui-avatar :name="name" :src="avatarSrc" :size="avatarSize" :fallback="fallback" />
      </button>
      <!-- #endif -->
      <!-- #ifndef MP-WEIXIN -->
      <view class="avatar-btn" data-testid="choose-avatar" @click="pickAvatar">
        <ui-avatar :name="name" :src="avatarSrc" :size="avatarSize" :fallback="fallback" />
      </view>
      <!-- #endif -->
      <text class="avatar-tip">{{ uploading ? '上传中…' : avatarTip }}</text>
    </view>

    <view class="card">
      <view class="field">
        <text class="field-label">昵称</text>
        <input
          :value="name"
          class="field-ipt"
          type="nickname"
          maxlength="20"
          :placeholder="placeholder"
          placeholder-class="ph"
          data-testid="nickname-input"
          @input="onNameInput"
          @blur="onNameInput"
        />
      </view>
      <slot />
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { chooseImages, uploadImage } from '@/api/upload'
import { chooseAvatarFailHint } from '@/utils/chooseAvatar'
import { thumbOf } from '@/utils/image'

const props = withDefaults(
  defineProps<{
    name: string
    /** 服务端相对地址（/uploads/…） */
    avatarUrl: string
    uploading?: boolean
    avatarSize?: number
    avatarTip?: string
    placeholder?: string
    fallback?: string
  }>(),
  { uploading: false, avatarSize: 80, avatarTip: '点击更换头像', placeholder: '1–20 个字', fallback: '掌' },
)

const emit = defineEmits<{
  (e: 'update:name', v: string): void
  (e: 'update:avatarUrl', v: string): void
  (e: 'update:uploading', v: boolean): void
}>()

const avatarSrc = computed(() => (props.avatarUrl ? thumbOf(props.avatarUrl) : ''))

function onNameInput(e: any) {
  // type=nickname：微信昵称建议在 blur 时才回填，input/blur 都同步
  const v = e?.detail?.value ?? e?.target?.value ?? ''
  emit('update:name', String(v))
}

async function upload(path: string) {
  if (!path) return
  emit('update:uploading', true)
  try {
    const r = await uploadImage(path, 'avatar')
    emit('update:avatarUrl', r.url)
  } catch {
    uni.showToast({ title: '头像上传失败', icon: 'none' })
  } finally {
    emit('update:uploading', false)
  }
}

function onChooseAvatar(e: any) {
  const hint = chooseAvatarFailHint(e?.detail)
  if (hint) {
    console.warn('[chooseAvatar]', e?.detail?.errMsg)
    uni.showToast({ title: hint, icon: 'none' })
    return
  }
  upload(e?.detail?.avatarUrl || '')
}

async function pickAvatar() {
  const [path] = await chooseImages(1)
  if (path) upload(path)
}
</script>

<style scoped>
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
.ph {
  color: var(--caption);
}
</style>
