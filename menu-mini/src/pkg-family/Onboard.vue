<template>
  <page-meta :page-style="gdThemeStyle" />
  <!-- 首次登录 / 刚加入家庭引导：后端已建好「我的家」与本人档案，这里补口味与健康信息（可跳过）。 -->
  <view class="page">
    <view class="intro" :style="{ paddingTop: nav + 'px' }">
      <text class="title" data-testid="onboard-title">{{ fromJoin ? '欢迎加入新家庭' : '欢迎来到咕嘟小食单' }}</text>
      <text class="sub">
        {{ fromJoin ? '' : '已为你建好「' + (familyName || '我的家') + '」。' }}花半分钟说说你的口味和身体情况，智荐和营养计算会更贴合你；不想填也可以跳过，之后在「成员档案」随时补。
      </text>
    </view>
    <ui-state v-if="loading" mode="loading" />
    <view v-else class="body">
      <profile-form :form="form" />
      <text v-if="err" class="err-txt">{{ err }}</text>
      <button class="btn" :disabled="saving" data-testid="onboard-save" @click="onSave">{{ saving ? '保存中…' : '保存，开始做饭' }}</button>
      <button class="btn ghost" data-testid="onboard-skip" @click="finish">跳过</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { listMembers, updateMember, type Member } from '@/api/member'
import { ensureAccountId } from '@/api/account'
import { currentFamily, onboardKey } from '@/utils/family'
import { emptyForm, toForm, toSave, validateForm } from '@/utils/memberProfile'
import { navInset } from '@/utils/token'
import ProfileForm from './parts/ProfileForm.vue'

const nav = navInset()
const loading = ref(true)
const saving = ref(false)
const err = ref('')
const fromJoin = ref(false)
const familyName = ref(currentFamily()?.name || '')
const mine = ref<Member | null>(null)
const accountId = ref<number | null>(null)
const form = reactive(emptyForm(uni.getStorageSync('nickname') || ''))

onLoad(async (o) => {
  fromJoin.value = o?.from === 'join'
  try {
    accountId.value = await ensureAccountId()
    const members = await listMembers()
    mine.value = members.find((m) => m.accountId === accountId.value) || null
    if (mine.value) Object.assign(form, toForm(mine.value))
  } catch {}
  loading.value = false
})

async function onSave() {
  err.value = validateForm(form)
  if (err.value) return
  if (!mine.value) {
    finish()
    return
  }
  saving.value = true
  try {
    await updateMember({ ...toSave(form, mine.value), id: mine.value.id })
    uni.showToast({ title: '已保存', icon: 'none' })
    finish()
  } catch {
    // request.ts 已提示
  } finally {
    saving.value = false
  }
}

function finish() {
  uni.setStorageSync(onboardKey(accountId.value), '1')
  uni.switchTab({ url: '/pages/dish/List' })
}
</script>

<style scoped>
@import './parts/family.css';
.intro {
  padding-left: var(--sp-16);
  padding-right: var(--sp-16);
  padding-bottom: var(--sp-16);
  background: linear-gradient(180deg, var(--primary-soft), var(--bg));
  display: flex;
  flex-direction: column;
  gap: var(--sp-8);
}
.title {
  font-size: var(--fs-h3);
  font-weight: 800;
  color: var(--title);
}
.sub {
  font-size: var(--fs-sm);
  line-height: 1.7;
  color: var(--body);
}
</style>
