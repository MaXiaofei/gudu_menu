<template>
  <!-- 采购清单分享预览（prototype ShopShare）：深色底预览卡（插画横幅 + 分组食材插画 + 家庭署名）→ 复制文字 / 转图片分享。 -->
  <view class="sv">
    <view class="top" :style="{ paddingTop: sb + 'px' }">
      <view class="back" aria-label="返回" @click="$emit('back')"><ui-icon name="back" tone="white" :size="22" /></view>
      <text class="title">分享采购清单</text>
      <view style="width: 40px" />
    </view>
    <scroll-view scroll-y class="body">
      <view class="share-card">
        <view class="banner">
          <ui-ing-icon v-for="(it, i) in bannerItems" :key="i" class="b-ic" :icon-key="it.iconKey" :name="itemName(it)" :size="44" bare />
          <view class="banner-txt">
            <text class="share-title">{{ title }}</text>
            <text class="share-sub">{{ subtitle }}</text>
          </view>
        </view>
        <view class="rows">
          <template v-for="g in groups" :key="g.name">
            <text class="g-name">{{ g.name }}</text>
            <view v-for="it in g.items" :key="it.id" class="share-row">
              <ui-ing-icon :icon-key="it.iconKey" :name="itemName(it)" :size="32" />
              <text class="share-name">{{ itemName(it) }}</text>
              <text class="share-qty">{{ amountText(it) }}</text>
            </view>
          </template>
          <text v-if="!items.length" class="share-none">清单里的都入库了，没有要分享的项</text>
        </view>
        <view class="foot">
          <image class="logo" src="/static/logo-gudu.png" mode="aspectFit" />
          <text class="foot-txt">咕嘟小食单 · 一家人的采购清单</text>
        </view>
      </view>
      <view class="share-actions">
        <view class="share-btn" @click="copyText"><text class="share-btn-txt">复制文字</text></view>
        <view class="share-btn fill" :class="{ disabled: generating }" @click="saveImage">
          <text class="share-btn-txt on">{{ generating ? '生成中…' : '转图片分享' }}</text>
        </view>
      </view>
      <text class="share-tip">没开相册权限时，保存会引导你去设置里打开</text>
      <view style="height: 40px" />
    </scroll-view>
    <!-- #ifndef H5 -->
    <canvas type="2d" id="shareCanvas" class="share-canvas" />
    <!-- #endif -->

    <view v-if="shareImg" class="mask-center" @click="shareImg = ''">
      <view class="img-dialog" @click.stop>
        <scroll-view scroll-y class="img-scroll">
          <image class="img-preview" :src="shareImg" mode="widthFix" :show-menu-by-longpress="true" @click="previewShareImg" />
        </scroll-view>
        <!-- #ifdef H5 -->
        <text class="img-tip">长按图片保存，或点「下载图片」</text>
        <view class="img-actions">
          <view class="img-btn ghost" @click="shareImg = ''">关闭</view>
          <view class="img-btn fill" @click="downloadH5">下载图片</view>
        </view>
        <!-- #endif -->
        <!-- #ifndef H5 -->
        <view class="img-actions">
          <view class="img-btn ghost" @click="saveShareImg">保存到相册</view>
          <view class="img-btn fill" @click="shareToFriend">发给好友</view>
        </view>
        <!-- #endif -->
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, getCurrentInstance, ref } from 'vue'
import { amountText, type ShoppingDetail, type ShoppingItem } from '@/api/shopping'
import { navInset } from '@/utils/token'
import { palette } from '@/utils/theme'
import { drawShareImage, shareLayout, type ShareContent } from '@/utils/shareImage'
import { saveToAlbum } from '@/utils/album'

const props = defineProps<{ detail: ShoppingDetail; items: ShoppingItem[] }>()
defineEmits<{ (e: 'back'): void }>()

const sb = navInset()
const instance = getCurrentInstance()

function itemName(it: ShoppingItem): string {
  return it.ingredientName || it.customName || `#${it.id}`
}
const title = computed(() => props.detail.name || `采购单 #${props.detail.id}`)
const subtitle = computed(() => {
  const now = new Date()
  return `${now.getMonth() + 1}月${now.getDate()}日 · ${props.items.length} 样`
})
const bannerItems = computed(() => props.items.slice(0, 4))
/** 按清单分区（grouped + categoryNames）归组，只留未买的。 */
const groups = computed(() => {
  const ids = new Set(props.items.map((i) => i.id))
  const g = props.detail.grouped
  if (!g) return [{ name: '要买的', items: props.items }]
  return Object.entries(g)
    .map(([k, list]) => ({ name: props.detail.categoryNames?.[k] || '其他', items: list.filter((i) => ids.has(i.id)) }))
    .filter((x) => x.items.length)
})

function copyText() {
  const lines = props.items.map((it) => `${itemName(it)} ${amountText(it)}`.trim())
  uni.setClipboardData({
    data: [`${title.value} · ${subtitle.value}`, ...lines].join('\n'),
    success: () => uni.showToast({ title: '采购清单已复制', icon: 'none' }),
  })
}

// ---- 转图片分享（canvas 绘制 → 预览 → 保存相册 / 发给好友；H5 下载/长按） ----
const generating = ref(false)
const shareImg = ref('')

function shareContent(): ShareContent {
  return {
    title: title.value,
    subtitle: `${subtitle.value.split(' · ')[0]} · 待买 ${props.items.length} 项`,
    rows: props.items.map((it) => ({ name: itemName(it), qty: amountText(it) })),
  }
}

async function saveImage() {
  if (generating.value) return
  if (!props.items.length) {
    uni.showToast({ title: '没有要分享的采购项', icon: 'none' })
    return
  }
  generating.value = true
  try {
    shareImg.value = await renderShareImage(shareContent())
  } catch {
    uni.showToast({ title: '生成图片失败，可先复制文字', icon: 'none' })
  } finally {
    generating.value = false
  }
}

function renderShareImage(c: ShareContent): Promise<string> {
  const L = shareLayout(c.rows.length)
  // #ifdef H5
  return new Promise((resolve, reject) => {
    try {
      const canvas = document.createElement('canvas')
      canvas.width = L.width
      canvas.height = L.height
      const ctx = canvas.getContext('2d')
      if (!ctx) throw new Error('no ctx')
      drawShareImage(ctx as any, c, palette())
      resolve(canvas.toDataURL('image/png'))
    } catch (e) {
      reject(e)
    }
  })
  // #endif
  // #ifndef H5
  return new Promise((resolve, reject) => {
    uni
      .createSelectorQuery()
      .in(instance?.proxy)
      .select('#shareCanvas')
      .fields({ node: true, size: true } as any, () => {})
      .exec((res: any[]) => {
        const canvas = res?.[0]?.node
        if (!canvas) {
          reject(new Error('canvas not ready'))
          return
        }
        canvas.width = L.width
        canvas.height = L.height
        const ctx = canvas.getContext('2d')
        drawShareImage(ctx, c, palette())
        uni.canvasToTempFilePath({
          canvas,
          fileType: 'png',
          success: (r: any) => resolve(r.tempFilePath),
          fail: (e: any) => reject(e),
        } as any)
      })
  })
  // #endif
}

function previewShareImg() {
  if (shareImg.value) uni.previewImage({ urls: [shareImg.value] })
}
async function saveShareImg() {
  if (shareImg.value) await saveToAlbum(shareImg.value)
}
/** 小程序：调起微信图片分享菜单（发给朋友 / 保存 / 收藏）。 */
function shareToFriend() {
  if (!shareImg.value) return
  const api = (uni as any).showShareImageMenu
  if (typeof api !== 'function') {
    uni.showToast({ title: '当前微信版本不支持，请先保存到相册', icon: 'none' })
    return
  }
  api({
    path: shareImg.value,
    fail: (e: any) => {
      if (!/cancel/i.test(e?.errMsg || '')) uni.showToast({ title: '分享失败，请先保存到相册', icon: 'none' })
    },
  })
}
/** H5：触发浏览器下载。 */
function downloadH5() {
  // #ifdef H5
  const a = document.createElement('a')
  a.href = shareImg.value
  a.download = `${shareContent().title}.png`
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  // #endif
}
</script>

<style scoped>
.sv {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--title);
}
.top {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 14px 8px;
}
.back {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.title {
  flex: 1;
  text-align: center;
  font-size: var(--fs-lg);
  font-weight: 700;
  color: var(--on-primary);
}
.body {
  flex: 1;
  min-height: 0;
}
.share-card {
  margin: var(--sp-8) var(--sp-24) 0;
  border-radius: var(--r-xl);
  background: var(--card);
  overflow: hidden;
  box-shadow: 0 20px 48px rgba(0, 0, 0, 0.35);
}
.banner {
  position: relative;
  height: 128px;
  background: var(--secondary);
  overflow: hidden;
}
.b-ic {
  position: absolute;
  opacity: 0.9;
}
.b-ic:nth-child(1) { right: 18px; top: 14px; transform: rotate(-8deg); }
.b-ic:nth-child(2) { right: 70px; top: 36px; transform: rotate(10deg); }
.b-ic:nth-child(3) { right: 26px; top: 66px; transform: rotate(6deg); }
.b-ic:nth-child(4) { right: 112px; top: 10px; transform: rotate(-12deg); }
.banner-txt {
  position: absolute;
  left: var(--sp-20);
  bottom: var(--sp-16);
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.share-title {
  font-size: var(--fs-h3);
  font-weight: 800;
  color: var(--title);
}
.share-sub {
  font-size: var(--fs-sm);
  color: var(--body);
}
.rows {
  padding: var(--sp-8) var(--sp-20) var(--sp-12);
}
.g-name {
  display: block;
  margin-top: var(--sp-10);
  font-size: var(--fs-xs);
  font-weight: 700;
  color: var(--body);
  letter-spacing: 1px;
}
.share-row {
  display: flex;
  align-items: center;
  gap: var(--sp-10);
  padding: var(--sp-8) 0;
  border-bottom: 1px dashed var(--border);
}
.share-name {
  flex: 1;
  font-size: var(--fs-md);
  font-weight: 700;
  color: var(--title);
}
.share-qty {
  font-size: var(--fs-md);
  color: var(--body);
}
.share-none {
  display: block;
  padding: var(--sp-20) 0;
  text-align: center;
  font-size: var(--fs-md);
  color: var(--body);
}
.foot {
  display: flex;
  align-items: center;
  gap: var(--sp-8);
  padding: var(--sp-12) var(--sp-20) var(--sp-16);
  background: var(--bg);
}
.logo {
  width: 28px;
  height: 28px;
}
.foot-txt {
  font-size: var(--fs-sm);
  color: var(--body);
}
.share-actions {
  display: flex;
  gap: var(--sp-12);
  margin: var(--sp-24) var(--sp-24) 0;
}
.share-btn {
  flex: 1;
  height: 48px;
  border-radius: var(--r-md);
  background: rgba(255, 255, 255, 0.12);
  border: 1px solid rgba(255, 255, 255, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
}
.share-btn.fill {
  flex: 1.4;
  background: var(--primary-deep);
  border-color: var(--primary-deep);
}
.share-btn.disabled {
  opacity: 0.6;
}
.share-btn-txt {
  font-size: var(--fs-md);
  font-weight: 700;
  color: var(--on-primary);
}
.share-tip {
  display: block;
  margin-top: var(--sp-12);
  text-align: center;
  font-size: var(--fs-sm);
  color: rgba(255, 255, 255, 0.7);
}
.share-canvas {
  position: fixed;
  left: -9999px;
  top: 0;
  width: 375px;
  height: 375px;
}
.mask-center {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 120;
  background: var(--mask);
  display: flex;
  align-items: center;
  justify-content: center;
}
.img-dialog {
  width: 84vw;
  max-height: 84vh;
  background: var(--card);
  border-radius: var(--r-lg);
  padding: var(--sp-12);
  display: flex;
  flex-direction: column;
  gap: var(--sp-10);
}
.img-scroll {
  max-height: 64vh;
}
.img-preview {
  width: 100%;
  border-radius: var(--r-sm);
}
.img-tip {
  text-align: center;
  font-size: var(--fs-sm);
  color: var(--body);
}
.img-actions {
  display: flex;
  gap: var(--sp-10);
}
.img-btn {
  flex: 1;
  height: 44px;
  line-height: 44px;
  text-align: center;
  border-radius: var(--r-md);
  font-size: var(--fs-md);
  font-weight: 700;
}
.img-btn.ghost {
  border: 1px solid var(--border);
  color: var(--title);
}
.img-btn.fill {
  background: var(--primary-deep);
  color: var(--on-primary);
}
</style>
