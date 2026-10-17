<template>
  <!-- 写菜谱 · 做法步骤编辑（每步文字 + 一张可选步骤图）。steps 为父组件 reactive 数组，就地修改。 -->
  <view>
    <view v-for="(s, i) in steps" :key="s.key" class="step-card">
      <view class="step-head">
        <view class="step-no"><text class="step-no-txt">{{ i + 1 }}</text></view>
        <text class="step-title">步骤 {{ i + 1 }}</text>
        <text class="row-del" @click="steps.splice(i, 1)">✕</text>
      </view>
      <textarea v-model="s.text" class="step-area" :maxlength="1000" placeholder="这一步怎么做（可选）" placeholder-class="ph" />
      <image v-if="s.imageLocal || s.imageUrl" class="step-img" :src="s.imageLocal || thumbOf(s.imageUrl)" mode="aspectFill" />
      <view v-else class="step-add-img" @click="pickStepImage(i)"><ui-icon name="camera" :size="18" /><text class="step-add-txt">添加步骤图（可选）</text></view>
      <view v-if="s.imageLocal || s.imageUrl" class="step-img-del" @click="removeStepImage(i)">✕</view>
    </view>
    <view class="add-row-btn" @click="$emit('add')">＋ 添加步骤</view>
  </view>
</template>

<script setup lang="ts">
import { chooseImages } from '@/api/upload'
import { thumbOf } from '@/utils/image'

export interface StepRow {
  key: number
  text: string
  imageLocal: string
  imageUrl: string
}

const props = defineProps<{ steps: StepRow[] }>()
defineEmits<{ (e: 'add'): void }>()

async function pickStepImage(i: number) {
  const [p] = await chooseImages(1)
  if (!p) return
  props.steps[i].imageLocal = p
  props.steps[i].imageUrl = ''
}
function removeStepImage(i: number) {
  props.steps[i].imageLocal = ''
  props.steps[i].imageUrl = ''
}

// ---- chips ----
</script>

<style scoped>
.ph {
  color: var(--caption);
}
.row-del {
  color: var(--body);
  font-size: var(--fs-md);
  padding: 4px;
}
.add-row-btn {
  height: 44px;
  line-height: 44px;
  text-align: center;
  border-radius: var(--r-md);
  border: 1px dashed var(--primary-soft);
  background: var(--highlight);
  color: var(--primary-deep);
  font-size: var(--fs-md);
  font-weight: 700;
}
.step-add-txt {
  font-size: var(--fs-sm);
  color: var(--body);
}
.step-card {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--r-md);
  padding: 12px;
  margin-bottom: 8px;
  position: relative;
}
.step-head { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.step-no {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: var(--primary);
  display: flex;
  align-items: center;
  justify-content: center;
}
.step-no-txt { color: var(--on-primary); font-size: var(--fs-xs); font-weight: 700; }
.step-title { flex: 1; font-size: var(--fs-md); font-weight: 700; color: var(--title); }
.step-area {
  width: 100%;
  box-sizing: border-box;
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: var(--r-sm);
  padding: 8px 10px;
  font-size: var(--fs-md);
  color: var(--title);
  height: 66px;
}
.step-img { width: 100%; height: 100px; border-radius: var(--r-sm); margin-top: 8px; }
.step-add-img {
  margin-top: 8px;
  border: 1px dashed var(--border);
  border-radius: var(--r-sm);
  padding: 11px 0;
  text-align: center;
  font-size: var(--fs-xs);
  color: var(--caption);
}
.step-img-del {
  position: absolute;
  right: 20px;
  bottom: 118px;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.54);
  color: var(--on-primary);
  font-size: var(--fs-sm);
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
