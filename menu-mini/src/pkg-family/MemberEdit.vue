<template>
  <page-meta :page-style="gdThemeStyle" />
  <!-- 本家成员档案编辑 / 新建：有账号的家人（本人或主理人/管理员可改）与无账号成员（宝宝、老人，主理人/管理员代建）。 -->
  <view class="page">
    <ui-back-header :title="isNew ? '添加成员档案' : '成员档案'" :subtitle="subtitle" />
    <ui-state v-if="loading" mode="loading" />
    <ui-state v-else-if="!isNew && !member" mode="error" text="档案不存在或不属于当前家庭" />
    <view v-else class="body">
      <view v-if="readonly" class="callout">
        <text class="callout-txt">只有本人或家里的主理人、管理员可以修改这份档案。</text>
      </view>
      <profile-form :form="form" />
      <text v-if="err" class="err-txt" data-testid="member-err">{{ err }}</text>
      <template v-if="!readonly">
        <button class="btn" :disabled="saving" data-testid="member-save" @click="onSave">{{ saving ? '保存中…' : '保存' }}</button>
        <button v-if="deletable" class="btn danger" data-testid="member-delete" @click="onDelete">删除这份档案</button>
      </template>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { addMember, deleteMember, listMembers, updateMember, type Member } from '@/api/member'
import { ensureAccountId } from '@/api/account'
import { useFamilyStore } from '@/store/family'
import { canManage } from '@/utils/family'
import { emptyForm, toForm, toSave, validateForm } from '@/utils/memberProfile'
import ProfileForm from './parts/ProfileForm.vue'
import { confirm } from './parts/dialog'

const store = useFamilyStore()
const loading = ref(true)
const saving = ref(false)
const err = ref('')
const isNew = ref(false)
const member = ref<Member | null>(null)
const me = ref<number | null>(null)
const form = reactive(emptyForm())

const manage = computed(() => canManage(store.currentRole))
const isMine = computed(() => !!member.value?.accountId && member.value.accountId === me.value)
const readonly = computed(() => !isNew.value && !manage.value && !isMine.value)
const deletable = computed(() => !isNew.value && manage.value && !!member.value && !member.value.accountId)
const subtitle = computed(() => {
  if (isNew.value) return '没有账号的家人（如宝宝、老人）也能建档，用于智荐和营养计算'
  return member.value?.accountId ? '已注册的家人' : '无账号成员 · 由主理人或管理员维护'
})

onLoad(async (o) => {
  const id = Number(o?.id || 0)
  isNew.value = !id
  try {
    me.value = await ensureAccountId()
    if (!store.families.length) await store.refresh().catch(() => {})
    if (id) {
      member.value = (await listMembers()).find((m) => m.id === id) || null
      if (member.value) Object.assign(form, toForm(member.value))
    }
  } catch {}
  loading.value = false
})

async function onSave() {
  err.value = validateForm(form)
  if (err.value) return
  saving.value = true
  try {
    if (isNew.value) await addMember(toSave(form))
    else await updateMember({ ...toSave(form, member.value), id: member.value!.id })
    uni.showToast({ title: '已保存', icon: 'none' })
    setTimeout(() => uni.navigateBack(), 300)
  } catch {
    // request.ts 已提示
  } finally {
    saving.value = false
  }
}

async function onDelete() {
  const m = member.value
  if (!m) return
  if (!(await confirm('删除档案', `删除「${m.name}」的档案后，TA 的口味与健康信息将一并删除，不能恢复。`))) return
  await deleteMember(m.id)
  uni.showToast({ title: '已删除', icon: 'none' })
  setTimeout(() => uni.navigateBack(), 300)
}
</script>

<style scoped>
@import './parts/family.css';
</style>
