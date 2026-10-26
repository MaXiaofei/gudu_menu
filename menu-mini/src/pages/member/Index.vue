<template>
  <page-meta :page-style="gdThemeStyle" />
  <!-- 成员管理：只读展示 + 切换当前就餐成员（档案编辑在后台） -->
  <view class="page">
    <ui-back-header title="家庭成员" subtitle="点一下切换当前就餐成员；忌口与份量会用于智荐和备菜" />
    <view v-if="store.members.length" class="fam-card">
      <ui-avatar-stack :items="store.members.map((m) => ({ name: m.name, tags: m.audienceTags }))" :size="36" :max="5" />
      <view class="fam-main">
        <text class="fam-name">我家的厨房</text>
        <text class="fam-sub">{{ store.members.length }} 位成员 · 邀请家人加入将在多家庭版本上线</text>
      </view>
    </view>

    <ui-state v-if="loading" mode="loading" />
    <scroll-view v-else scroll-y class="body">
      <view
        v-for="m in store.members"
        :key="m.id"
        class="row"
        @click="switchTo(m)"
      >
        <ui-member-avatar :name="m.name" :tags="m.audienceTags" :size="44" />
        <view class="main">
          <text class="name">{{ m.name }}</text>
          <text v-if="tagsOf(m).length" class="tags">{{ tagsOf(m).join(' · ') }}</text>
        </view>
        <view v-if="store.currentId === m.id" class="cur"><text class="cur-txt">当前</text></view>
        <text v-else class="switch">切换</text>
      </view>
      <ui-state v-if="!store.members.length" mode="empty" text="还没有成员" hint="请先在后台添加家庭成员" :illus="['rice', 'egg']" />

      <!-- 敏感信息单独同意（B.2）：可随时撤回，撤回即删除对应信息 -->
      <view v-if="consent" class="consent">
        <text class="consent-title">敏感信息授权</text>
        <text class="consent-hint">健康档案与儿童信息仅用于营养计算、菜品过滤与 AI 回答，加密存储；撤回后删除对应信息</text>
        <view v-for="c in consentRows" :key="c.type" class="consent-row">
          <view class="main">
            <text class="name">{{ c.label }}</text>
            <text class="tags">{{ c.granted ? `已同意${c.at ? ' · ' + c.at.slice(0, 10) : ''}` : '未同意（填写时会单独询问）' }}</text>
          </view>
          <text v-if="c.granted" class="withdraw" @click="withdraw(c.type)">撤回</text>
        </view>
      </view>
      <view style="height: 24px" />
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { useMemberStore } from '@/store/member'
import type { Member } from '@/api/member'
import {
  getSensitiveConsent,
  withdrawSensitiveConsent,
  type ConsentStatus,
  type SensitiveDocType,
} from '@/api/privacy'
import { askModal, withdrawDialog } from '@/utils/sensitiveConsent'

const store = useMemberStore()
const loading = ref(true)
const consent = ref<ConsentStatus | null>(null)

const consentRows = computed(() => {
  const c = consent.value
  if (!c) return []
  return [
    { type: 'sensitive_health' as SensitiveDocType, label: '健康信息（身高体重、过敏、特殊人群）', ...c.sensitive_health },
    { type: 'child_info' as SensitiveDocType, label: '不满 14 周岁儿童信息（监护人同意）', ...c.child_info },
  ]
})

async function loadConsent() {
  try {
    consent.value = await getSensitiveConsent()
  } catch {
    consent.value = null
  }
}

onShow(async () => {
  loading.value = true
  try {
    await store.load()
  } catch {}
  loading.value = false
  loadConsent()
})

async function withdraw(type: SensitiveDocType) {
  const { title, content } = withdrawDialog(type)
  if (!(await askModal(title, content, '撤回并删除', '取消'))) return
  try {
    consent.value = await withdrawSensitiveConsent(type)
    await store.load().catch(() => {})
    uni.showToast({ title: '已撤回，相关信息已删除', icon: 'none' })
  } catch {}
}

function tagsOf(m: any): string[] {
  return [...(m.audienceTags ?? []), ...(m.roleTags ?? [])]
}

async function switchTo(m: Member) {
  if (store.currentId === m.id) return
  try {
    await store.switchTo(m.id)
    uni.showToast({ title: `已切换为 ${m.name}`, icon: 'none' })
  } catch {
    uni.showToast({ title: '切换失败', icon: 'none' })
  }
}
</script>

<style scoped>
.page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--bg);
}
.body {
  flex: 1;
  min-height: 0;
  padding: 0 16px;
  box-sizing: border-box;
}
.row {
  display: flex;
  align-items: center;
  gap: 12px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--r-md);
  padding: 12px 14px;
  margin-bottom: 8px;
}
.main { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 3px; }
.name { font-size: var(--fs-md); font-weight: 700; color: var(--title); }
.tags { font-size: var(--fs-tiny); color: var(--caption); }
.cur {
  background: var(--success);
  border-radius: var(--r-pill);
  padding: 3px 10px;
}
.cur-txt { color: var(--on-primary); font-size: var(--fs-tiny); font-weight: 700; }
.switch { font-size: var(--fs-sm); color: var(--primary); }
.consent {
  margin-top: var(--sp-16);
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--r-md);
  padding: 12px 14px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.consent-title { font-size: var(--fs-md); font-weight: 700; color: var(--title); }
.consent-hint { font-size: var(--fs-tiny); color: var(--caption); }
.consent-row { display: flex; align-items: center; gap: 12px; padding-top: 8px; border-top: 1px solid var(--border); }
.withdraw { font-size: var(--fs-sm); color: var(--error); }
.fam-card {
  margin: var(--sp-8) var(--sp-16) var(--sp-12);
  padding: var(--sp-16);
  border-radius: var(--r-xl);
  background: linear-gradient(135deg, var(--primary-soft), var(--secondary));
  display: flex;
  align-items: center;
  gap: var(--sp-12);
}
.fam-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.fam-name {
  font-size: var(--fs-lg);
  font-weight: 700;
  color: var(--title);
}
.fam-sub {
  font-size: var(--fs-sm);
  color: var(--body);
}
</style>
