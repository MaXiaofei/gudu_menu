<template>
  <!-- 成员档案表单（引导页 / 档案编辑页共用）。只用设计 token。 -->
  <view class="pf">
    <view class="card">
      <text class="group-title">称呼</text>
      <view class="input-wrap">
        <input v-model="form.name" class="ipt" data-testid="pf-name" maxlength="32" placeholder="如：爸爸、小宝" placeholder-class="ipt-ph" />
      </view>
    </view>

    <view class="card">
      <text class="group-title">身体信息（选填，用于算每日营养目标）</text>
      <ui-segment v-model="form.gender" :items="GENDERS" />
      <view class="grid">
        <view class="input-wrap"><input v-model="form.age" class="ipt" type="digit" placeholder="年龄" placeholder-class="ipt-ph" /><text class="unit">岁</text></view>
        <view class="input-wrap"><input v-model="form.height" class="ipt" type="digit" placeholder="身高" placeholder-class="ipt-ph" /><text class="unit">cm</text></view>
        <view class="input-wrap"><input v-model="form.weight" class="ipt" type="digit" placeholder="体重" placeholder-class="ipt-ph" /><text class="unit">kg</text></view>
      </view>
    </view>

    <view class="card">
      <text class="group-title">特殊人群（选填，智荐与备菜会照顾）</text>
      <view class="chips">
        <text
          v-for="a in AUDIENCES"
          :key="a"
          class="chip"
          :class="{ on: form.audiences.includes(a) }"
          :data-testid="'pf-aud-' + a"
          @click="toggle(a)"
        >{{ a }}</text>
      </view>
      <text class="group-title gap">过敏 / 忌口（选填）</text>
      <view class="input-wrap">
        <input v-model="form.allergies" class="ipt" placeholder="如：花生、虾，用顿号分隔" placeholder-class="ipt-ph" />
      </view>
      <text class="hint">健康信息属于敏感个人信息，仅用于家里的菜谱推荐和营养计算，可随时修改或清空。</text>
    </view>

    <view class="card">
      <text class="group-title">饮食目标（选填）</text>
      <ui-segment v-model="form.goal" :items="GOALS" />
      <text class="group-title gap">日常活动量</text>
      <ui-segment v-model="form.activityLevel" :items="ACTIVITY" />
    </view>
  </view>
</template>

<script setup lang="ts">
import { AUDIENCES, type ProfileForm } from '@/utils/memberProfile'

const props = defineProps<{ form: ProfileForm }>()

const GENDERS = [
  { key: '', label: '不填' },
  { key: 'male', label: '男' },
  { key: 'female', label: '女' },
]
const GOALS = [
  { key: '', label: '不填' },
  { key: 'LOSE', label: '减脂' },
  { key: 'MAINTAIN', label: '保持' },
  { key: 'GAIN', label: '增肌' },
]
const ACTIVITY = [
  { key: 'SEDENTARY', label: '久坐' },
  { key: 'LIGHT', label: '轻度' },
  { key: 'MODERATE', label: '中度' },
  { key: 'ACTIVE', label: '高强度' },
]

function toggle(a: string) {
  const list = props.form.audiences
  const i = list.indexOf(a)
  if (i >= 0) list.splice(i, 1)
  else list.push(a)
}
</script>

<style scoped>
@import './family.css';
.pf {
  display: flex;
  flex-direction: column;
  gap: var(--sp-12);
}
.grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--sp-8);
  margin-top: var(--sp-8);
}
.unit {
  font-size: var(--fs-sm);
  color: var(--caption);
}
.chips {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-8);
}
.chip {
  padding: 6px 12px;
  border-radius: var(--r-pill);
  border: 1px solid var(--border);
  background: var(--card);
  font-size: var(--fs-sm);
  color: var(--body);
}
.chip.on {
  background: var(--primary-soft);
  border-color: var(--primary);
  color: var(--primary-deep);
  font-weight: 700;
}
.gap {
  margin-top: var(--sp-12);
}
.hint {
  display: block;
  margin-top: var(--sp-8);
  font-size: var(--fs-xs);
  line-height: 1.6;
  color: var(--caption);
}
</style>
