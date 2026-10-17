<template>
  <page-meta :page-style="gdThemeStyle" />
  <!-- 拍照识别（prototype Scan）：深色取景 → 冰箱 / 小票 / 菜谱截图 → 上传 → 识别任务轮询（已找到 N 样）→ 结果确认。
       小程序用 camera 组件拍照；H5 无 camera 组件，快门改为调起系统相机/相册。 -->
  <view class="page">
    <view class="top" :style="{ paddingTop: inset + 'px' }">
      <view class="circle" @click="goBack"><ui-icon name="x" tone="white" :size="18" /></view>
      <text class="top-title">拍照识别</text>
      <view class="circle" :class="{ on: flash === 'on' }" @click="flash = flash === 'on' ? 'off' : 'on'"><ui-icon name="flash" tone="white" :size="18" /></view>
    </view>

    <view class="finder">
      <image v-if="shot" class="shot" :src="shot" mode="aspectFill" />
      <!-- #ifdef MP-WEIXIN -->
      <camera v-else class="cam" device-position="back" :flash="flash" @error="camError = true" />
      <!-- #endif -->
      <view v-if="!shot && (isH5 || camError)" class="placeholder">
        <ui-icon name="fridge" tone="white" :size="56" />
        <text class="ph-txt">{{ camError ? '没有相机权限，可以从相册选' : '点下方快门拍一张' }}</text>
      </view>
      <view class="corner tl" /><view class="corner tr" /><view class="corner bl" /><view class="corner br" />
      <view class="hint"><ui-icon name="scan" tone="white" :size="14" /><text class="hint-txt">{{ HINT[mode] }}</text></view>
      <view v-if="busy" class="scanline" />

      <!-- 识别中：已找到的食材 -->
      <view v-if="found.length" class="found">
        <view v-for="f in found.slice(0, 8)" :key="f.name" class="tag" :class="{ warn: f.confidence < 0.8 }">
          <text class="tag-txt">{{ f.name }}{{ f.amount ? ' ×' + f.amount : '' }} · {{ Math.round(f.confidence * 100) }}%</text>
        </view>
      </view>
    </view>

    <view v-if="busy || stage" class="progress">
      <view class="prog-head">
        <text class="prog-dot" />
        <text class="prog-txt">{{ stageText }}</text>
        <text v-if="lowCount" class="prog-warn">{{ lowCount }} 样待确认</text>
      </view>
      <view class="prog-bar"><view class="prog-in" :style="{ width: pct + '%' }" /></view>
    </view>

    <view class="modes">
      <text v-for="m in MODES" :key="m" class="mode" :class="{ on: mode === m }" :data-testid="'mode-' + m" @click="!busy && (mode = m)">{{ MODE_LABEL[m] }}</text>
    </view>

    <view class="controls">
      <view class="album" data-testid="scan-album" @click="fromAlbum"><ui-icon name="image" tone="white" :size="22" /></view>
      <view class="shutter" :class="{ busy }" data-testid="scan-shutter" @click="takePhoto"><view class="shutter-in" /></view>
      <view class="done" :class="{ off: !jobId || busy }" data-testid="scan-done" @click="goResult">
        <text class="done-txt">完成{{ found.length ? ' ' + found.length : '' }}</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onUnload } from '@dcloudio/uni-app'
import { createJob, pollJob, MODE_LABEL, type VisionItem, type VisionMode } from '@/api/vision'
import { uploadImage } from '@/api/upload'
import { navInset } from '@/utils/token'
import { PAGES, withQuery } from '@/utils/nav'

const inset = navInset()
const MODES: VisionMode[] = ['FRIDGE', 'RECEIPT', 'RECIPE']
const HINT: Record<VisionMode, string> = {
  FRIDGE: '对准冰箱隔层，一次拍一层',
  RECEIPT: '小票放平，拍全商品和数量',
  RECIPE: '截图或拍下菜谱页面，用料和步骤都要在画面里',
}
const mode = ref<VisionMode>('FRIDGE')
const flash = ref<'on' | 'off'>('off')
const shot = ref('')
const camError = ref(false)
const busy = ref(false)
const stage = ref<'' | 'upload' | 'recognize' | 'done' | 'failed'>('')
const found = ref<VisionItem[]>([])
const jobId = ref(0)
const imageUrl = ref('')
let alive = true
onUnload(() => (alive = false))

let isH5 = false
// #ifdef H5
isH5 = true
// #endif

const lowCount = computed(() => found.value.filter((f) => f.confidence >= 0.5 && f.confidence < 0.8).length)
const stageText = computed(() =>
  stage.value === 'upload'
    ? '上传中…'
    : stage.value === 'recognize'
      ? `识别中 · 已找到 ${found.value.length} 样`
      : stage.value === 'done'
        ? `识别完成 · 共 ${found.value.length} 样`
        : stage.value === 'failed'
          ? '没认出来，换个角度再拍一张'
          : '',
)
const pct = computed(() => (stage.value === 'upload' ? 20 : stage.value === 'recognize' ? 40 + Math.min(50, found.value.length * 6) : 100))

function takePhoto() {
  if (busy.value) return
  // #ifdef MP-WEIXIN
  if (!camError.value) {
    uni.createCameraContext().takePhoto({
      quality: 'high',
      success: (r: any) => handle(r.tempImagePath),
      fail: () => uni.showToast({ title: '拍照失败', icon: 'none' }),
    })
    return
  }
  // #endif
  pickImage(['camera', 'album'])
}
function fromAlbum() {
  if (!busy.value) pickImage(['album'])
}
function pickImage(sourceType: ('album' | 'camera')[]) {
  uni.chooseImage({ count: 1, sourceType, sizeType: ['compressed'], success: (r) => handle(r.tempFilePaths[0]) })
}

async function handle(path: string) {
  shot.value = path
  busy.value = true
  found.value = []
  jobId.value = 0
  try {
    stage.value = 'upload'
    const up = await uploadImage(path)
    imageUrl.value = up.url
    stage.value = 'recognize'
    const { jobId: id } = await createJob({ imageUrl: up.url, mode: mode.value })
    jobId.value = id
    const job = await pollJob(id, { onTick: (j) => alive && (found.value = (j.items || []).filter((x) => x.confidence >= 0.5)) })
    if (!alive) return
    if (job.status === 'FAILED') {
      stage.value = 'failed'
      jobId.value = 0
      return
    }
    found.value = job.items.filter((x) => x.confidence >= 0.5)
    stage.value = 'done'
    setTimeout(goResult, 500)
  } catch {
    stage.value = 'failed'
  } finally {
    busy.value = false
  }
}

function goResult() {
  if (!jobId.value || busy.value) return
  uni.redirectTo({ url: withQuery(PAGES.scanResult, { jobId: jobId.value, img: imageUrl.value }) })
}
function goBack() {
  if (getCurrentPages().length > 1) uni.navigateBack()
  else uni.switchTab({ url: '/pages/pantry/List' })
}
</script>

<style scoped>
.page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #14100c;
  color: var(--on-primary);
}
.top {
  display: flex;
  align-items: center;
  padding: 0 var(--space-lg) var(--space-sm);
}
.top-title {
  flex: 1;
  text-align: center;
  font-size: var(--fs-lg);
  font-weight: 700;
  color: var(--on-primary);
}
.circle {
  width: 36px;
  height: 36px;
  border-radius: var(--radius-pill);
  background: rgba(255, 255, 255, 0.14);
  display: flex;
  align-items: center;
  justify-content: center;
}
.circle.on {
  background: var(--warning);
}
.finder {
  position: relative;
  flex: 1;
  margin: 0 var(--space-lg);
  border-radius: var(--radius-xl);
  overflow: hidden;
  background: #241b14;
}
.cam,
.shot {
  width: 100%;
  height: 100%;
}
.placeholder {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-md);
  opacity: 0.8;
}
.ph-txt {
  font-size: var(--fs-md);
  color: var(--on-primary);
}
.corner {
  position: absolute;
  width: 26px;
  height: 26px;
  border-color: var(--on-primary);
  border-style: solid;
  border-width: 0;
}
.tl {
  left: 12px;
  top: 12px;
  border-left-width: 3px;
  border-top-width: 3px;
  border-top-left-radius: 10px;
}
.tr {
  right: 12px;
  top: 12px;
  border-right-width: 3px;
  border-top-width: 3px;
  border-top-right-radius: 10px;
}
.bl {
  left: 12px;
  bottom: 12px;
  border-left-width: 3px;
  border-bottom-width: 3px;
  border-bottom-left-radius: 10px;
}
.br {
  right: 12px;
  bottom: 12px;
  border-right-width: 3px;
  border-bottom-width: 3px;
  border-bottom-right-radius: 10px;
}
.hint {
  position: absolute;
  left: 50%;
  top: 18px;
  transform: translateX(-50%);
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  border-radius: var(--radius-pill);
  background: rgba(0, 0, 0, 0.5);
  white-space: nowrap;
}
.hint-txt {
  font-size: var(--fs-sm);
  color: var(--on-primary);
}
.scanline {
  position: absolute;
  left: 8%;
  right: 8%;
  height: 2px;
  background: var(--primary);
  box-shadow: 0 0 12px var(--primary);
  animation: scan 2s ease-in-out infinite alternate;
}
@keyframes scan {
  from {
    top: 15%;
  }
  to {
    top: 85%;
  }
}
.found {
  position: absolute;
  left: 12px;
  right: 12px;
  bottom: 16px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.tag {
  padding: 4px 10px;
  border-radius: var(--radius-sm);
  background: rgba(79, 174, 110, 0.92);
}
.tag.warn {
  background: rgba(229, 169, 56, 0.95);
}
.tag-txt {
  font-size: var(--fs-xs);
  font-weight: 700;
  color: var(--on-primary);
}
.progress {
  margin: var(--space-md) var(--space-lg) 0;
  padding: 10px 14px;
  border-radius: var(--radius-md);
  background: rgba(255, 255, 255, 0.1);
}
.prog-head {
  display: flex;
  align-items: center;
  gap: 6px;
}
.prog-dot {
  width: 8px;
  height: 8px;
  border-radius: var(--radius-pill);
  background: var(--primary);
}
.prog-txt {
  flex: 1;
  font-size: var(--fs-sm);
  font-weight: 700;
  color: var(--on-primary);
}
.prog-warn {
  font-size: var(--fs-xs);
  color: var(--warning);
}
.prog-bar {
  margin-top: 8px;
  height: 4px;
  border-radius: 2px;
  background: rgba(255, 255, 255, 0.15);
  overflow: hidden;
}
.prog-in {
  height: 100%;
  background: var(--primary);
  transition: width 0.4s;
}
.modes {
  display: flex;
  justify-content: center;
  gap: var(--space-xl);
  padding: var(--space-md) 0;
}
.mode {
  font-size: var(--fs-md);
  color: rgba(255, 255, 255, 0.6);
  padding-bottom: 4px;
}
.mode.on {
  color: var(--primary);
  font-weight: 700;
  border-bottom: 2px solid var(--primary);
}
.controls {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 var(--space-xl) calc(var(--space-xl) + env(safe-area-inset-bottom));
}
.album {
  width: 48px;
  height: 48px;
  border-radius: var(--radius-md);
  background: rgba(255, 255, 255, 0.14);
  display: flex;
  align-items: center;
  justify-content: center;
}
.shutter {
  width: 76px;
  height: 76px;
  border-radius: var(--radius-pill);
  border: 4px solid var(--on-primary);
  display: flex;
  align-items: center;
  justify-content: center;
}
.shutter.busy {
  opacity: 0.5;
}
.shutter-in {
  width: 60px;
  height: 60px;
  border-radius: var(--radius-pill);
  background: var(--on-primary);
}
.done {
  min-width: 72px;
  height: 40px;
  padding: 0 14px;
  border-radius: var(--radius-pill);
  background: var(--primary-deep);
  display: flex;
  align-items: center;
  justify-content: center;
}
.done.off {
  opacity: 0.4;
}
.done-txt {
  font-size: var(--fs-md);
  font-weight: 700;
  color: var(--on-primary);
}
</style>
