<template>
  <page-meta :page-style="gdThemeStyle" />
  <!-- 发布到厨房圈（prototype PostCreate）：从食记带入 → 照片 → 标题/正文（AI 帮我写）→ 关联菜谱 → 话题 → 可见范围。
       隐私：不带家庭成员名与余量（kitchen-community spec「分享食记不泄露家庭信息」由后端保证，前端也不传）。 -->
  <view class="page">
    <view class="top" :style="{ paddingTop: inset + 'px' }">
      <text class="cancel" @click="goBack">取消</text>
      <text class="top-title">发布到厨房圈</text>
      <view class="publish" :class="{ off: publishing }" data-testid="publish" @click="onPublish">
        <text class="publish-txt">{{ publishing ? '发布中' : '发布' }}</text>
      </view>
    </view>

    <scroll-view scroll-y class="body">
      <view class="pad">
        <view v-if="fromLog" class="from">
          <ui-dish-image :src="images[0]" :name="dishName" :width="44" :height="44" :radius="10" />
          <view class="from-main">
            <text class="from-title">从食记带入</text>
            <text class="from-sub">{{ dishName }} · 已带入 {{ images.length }} 张照片</text>
          </view>
        </view>

        <!-- 照片 -->
        <view class="grid">
          <view v-for="(u, i) in images" :key="u" class="cell">
            <image class="cell-img" :src="thumbOf(u)" mode="aspectFill" @click="setCover(i)" />
            <text v-if="i === 0" class="cover-tag">封面</text>
            <view class="del" @click="images.splice(i, 1)"><ui-icon name="x" tone="white" :size="10" /></view>
          </view>
          <view v-if="images.length < 9" class="cell add" data-testid="add-photo" @click="addPhotos">
            <ui-icon name="plus" :size="22" />
            <text class="add-txt">{{ uploading ? '上传中…' : '添加' }}</text>
          </view>
        </view>
        <text class="hint">点照片设为封面 · 最多 9 张</text>

        <!-- 标题 -->
        <view class="field">
          <input v-model="title" class="title-ipt" maxlength="30" placeholder="起个标题，比如：10 分钟的滑蛋虾仁" placeholder-class="ph" data-testid="post-title" />
          <text class="count">{{ [...title].length }}/30</text>
        </view>

        <!-- 正文 + AI 帮写 -->
        <view class="field col">
          <textarea v-model="text" class="text-ipt" maxlength="1000" placeholder="说说怎么做的、哪里容易翻车…" placeholder-class="ph" :auto-height="true" data-testid="post-text" />
          <view v-if="aiDraft || aiLoading" class="ai-box">
            <view class="ai-head"><ui-icon name="sparkle" tone="primary" :size="14" /><text class="ai-kicker">AI 草稿 · 根据你的食记和菜谱生成</text></view>
            <text class="ai-text">{{ aiDraft }}<text v-if="aiLoading" class="cursor">▍</text></text>
            <view v-if="!aiLoading" class="ai-acts">
              <text class="ai-use" data-testid="ai-use" @click="useAi">用这段</text>
              <text class="ai-alt" @click="runAi('default')">换一版</text>
              <text class="ai-alt" @click="runAi('casual')">更口语</text>
              <text class="ai-alt" @click="runAi('short')">更短</text>
            </view>
          </view>
          <view class="text-foot">
            <view class="ai-btn" data-testid="ai-write" @click="runAi('default')"><ui-icon name="sparkle" tone="white" :size="14" /><text class="ai-btn-txt">AI 帮我写</text></view>
            <text class="count">{{ [...text].length }}/1000</text>
          </view>
        </view>

        <!-- 关联菜谱 -->
        <view class="row" @click="pickerOpen = true">
          <text class="row-label">关联菜谱</text>
          <view class="row-val">
            <ui-dish-image v-if="dish" :src="dish.coverUrl" :name="dish.name" :width="28" :height="28" :radius="6" />
            <text class="row-txt">{{ dish ? dish.name : '选一道，别人可以照着做' }}</text>
            <ui-icon name="chevron" :size="14" />
          </view>
        </view>

        <!-- 话题 -->
        <view class="row col">
          <text class="row-label">话题</text>
          <view class="chips">
            <view v-for="t in topicOptions" :key="t.topicId" class="chip" :class="{ on: topicIds.includes(t.topicId) }" @click="toggleTopic(t.topicId)">
              <text class="chip-txt">#{{ t.name }}</text>
            </view>
            <text v-if="!topicOptions.length" class="row-txt">本周暂无话题</text>
          </view>
        </view>

        <!-- 可见范围 -->
        <view class="row col">
          <text class="row-label">谁可以看</text>
          <view class="vis">
            <view class="vis-it" :class="{ on: visibility === 'PUBLIC' }" @click="visibility = 'PUBLIC'"><text class="vis-txt">所有人</text></view>
            <view class="vis-it" :class="{ on: visibility === 'FOLLOWERS' }" @click="visibility = 'FOLLOWERS'"><text class="vis-txt">仅关注我的</text></view>
          </view>
        </view>

        <view class="privacy">
          <ui-icon name="shield" :size="16" />
          <text class="privacy-txt">不会带上家庭成员名和家里的余量，只发菜和照片。</text>
        </view>
        <text class="rule">发布后会先经过 AI 预审，含广告、外链或违规内容会进入人工审核。</text>
      </view>
    </scroll-view>

    <!-- 关联菜谱选择 -->
    <view v-if="pickerOpen" class="mask" @click="pickerOpen = false">
      <view class="sheet" @click.stop>
        <text class="sheet-title">选一道菜谱</text>
        <view class="search">
          <ui-icon name="search" :size="16" />
          <input v-model="kw" class="search-ipt" placeholder="搜菜名" placeholder-class="ph" confirm-type="search" @confirm="search" />
        </view>
        <scroll-view scroll-y class="sheet-list">
          <view v-for="d in results" :key="d.id" class="pick" @click="pick(d)">
            <ui-dish-image :src="d.coverUrl" :name="d.name" :width="44" :height="44" :radius="10" />
            <text class="pick-name">{{ d.name }}</text>
          </view>
          <text v-if="!results.length" class="row-txt">{{ searching ? '搜索中…' : '输入菜名搜索' }}</text>
        </scroll-view>
        <text v-if="dish" class="unlink" @click="pick(null)">不关联菜谱</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { aiText, banner, createPost, validatePost } from '@/api/community'
import { chooseImages, uploadImage } from '@/api/upload'
import { searchDishes, type Dish } from '@/api/dish'
import { navInset } from '@/utils/token'
import { thumbOf } from '@/utils/image'
import { PAGES, withQuery } from '@/utils/nav'

const inset = navInset()
const images = ref<string[]>([])
const title = ref('')
const text = ref('')
const dish = ref<{ id: number; name: string; coverUrl: string | null } | null>(null)
const dishName = ref('')
const fromLog = ref(false)
const cookRecordId = ref<number | undefined>()
const topicIds = ref<number[]>([])
const topicOptions = ref<{ topicId: number; name: string }[]>([])
const visibility = ref<'PUBLIC' | 'FOLLOWERS'>('PUBLIC')
const uploading = ref(false)
const publishing = ref(false)
const aiDraft = ref('')
const aiLoading = ref(false)
const pickerOpen = ref(false)
const kw = ref('')
const results = ref<Dish[]>([])
const searching = ref(false)

onLoad((q: any) => {
  if (q?.fromCookRecordId) {
    fromLog.value = true
    cookRecordId.value = Number(q.fromCookRecordId)
  }
  if (q?.images) images.value = decodeURIComponent(q.images).split(',').filter(Boolean).slice(0, 9)
  if (q?.dishId) {
    dishName.value = decodeURIComponent(q.dishName || '')
    dish.value = { id: Number(q.dishId), name: dishName.value, coverUrl: images.value[0] || null }
  }
  const presetTopic = Number(q?.topicId) || 0
  banner()
    .then((b) => {
      if (b.topic) topicOptions.value = [{ topicId: b.topic.topicId, name: b.topic.name }]
      if (presetTopic && b.topic?.topicId === presetTopic) topicIds.value = [presetTopic]
    })
    .catch(() => {})
  if (presetTopic) topicIds.value = [presetTopic]
})

async function addPhotos() {
  if (uploading.value) return
  const paths = await chooseImages(9 - images.value.length)
  if (!paths.length) return
  uploading.value = true
  try {
    for (const p of paths) images.value.push((await uploadImage(p)).url)
  } catch {
    uni.showToast({ title: '有照片没传上去', icon: 'none' })
  } finally {
    uploading.value = false
  }
}

function setCover(i: number) {
  if (i === 0) return
  const [u] = images.value.splice(i, 1)
  images.value.unshift(u)
}

async function runAi(style: 'default' | 'casual' | 'short') {
  if (aiLoading.value) return
  aiLoading.value = true
  aiDraft.value = ''
  try {
    const r = await aiText({ dishId: dish.value?.id, cookRecordId: cookRecordId.value, imageUrls: images.value.slice(0, 3), style })
    aiDraft.value = r.text
  } catch {
    aiDraft.value = ''
  } finally {
    aiLoading.value = false
  }
}
function useAi() {
  text.value = aiDraft.value
  aiDraft.value = ''
}

function toggleTopic(id: number) {
  topicIds.value = topicIds.value.includes(id) ? topicIds.value.filter((x) => x !== id) : [...topicIds.value, id]
}

async function search() {
  if (!kw.value.trim()) return
  searching.value = true
  try {
    results.value = (await searchDishes({ keyword: kw.value.trim(), pageNum: 1, pageSize: 20 })).records
  } finally {
    searching.value = false
  }
}
function pick(d: Dish | null) {
  dish.value = d ? { id: d.id, name: d.name, coverUrl: d.coverUrl ?? null } : null
  pickerOpen.value = false
}

async function onPublish() {
  if (publishing.value) return
  const err = validatePost({ title: title.value, text: text.value, imageUrls: images.value })
  if (err) return uni.showToast({ title: err, icon: 'none' })
  publishing.value = true
  try {
    const r = await createPost({
      title: title.value,
      text: text.value,
      imageUrls: images.value,
      dishId: dish.value?.id,
      topicIds: topicIds.value,
      visibility: visibility.value,
      fromCookRecordId: cookRecordId.value,
    })
    uni.showToast({ title: r.status === 'PUBLISHED' ? '发布成功' : '已提交，审核后可见', icon: 'none' })
    setTimeout(() => uni.redirectTo({ url: withQuery(PAGES.post, { id: r.postId }) }), 500)
  } finally {
    publishing.value = false
  }
}

function goBack() {
  if (!images.value.length && !title.value && !text.value) return uni.navigateBack()
  uni.showModal({
    title: '放弃发布？',
    content: '已填写的内容不会保存',
    success: ({ confirm }) => confirm && uni.navigateBack(),
  })
}
</script>

<style scoped>
.page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--bg);
}
.top {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  padding: 0 var(--space-lg) var(--space-sm);
}
.cancel {
  font-size: var(--fs-md);
  color: var(--body);
}
.top-title {
  flex: 1;
  text-align: center;
  font-size: var(--fs-lg);
  font-weight: 700;
  color: var(--title);
}
.publish {
  padding: 6px 16px;
  border-radius: var(--radius-pill);
  background: var(--primary-deep);
}
.publish.off {
  opacity: 0.6;
}
.publish-txt {
  font-size: var(--fs-md);
  font-weight: 700;
  color: var(--on-primary);
}
.body {
  flex: 1;
  min-height: 0;
}
.pad {
  padding: var(--space-sm) var(--space-lg) var(--space-xxl);
}
.from {
  display: flex;
  align-items: center;
  gap: var(--space-md);
  padding: 10px 12px;
  border-radius: var(--radius-md);
  background: var(--highlight);
  border: 1px solid var(--primary-soft);
  margin-bottom: var(--space-md);
}
.from-main {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.from-title {
  font-size: var(--fs-md);
  font-weight: 700;
  color: var(--title);
}
.from-sub {
  font-size: var(--fs-sm);
  color: var(--body);
}
.grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--space-sm);
}
.cell {
  position: relative;
  padding-top: 100%;
  border-radius: var(--radius-md);
  overflow: hidden;
  background: var(--secondary);
}
.cell-img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}
.cover-tag {
  position: absolute;
  left: 0;
  bottom: 0;
  right: 0;
  text-align: center;
  background: rgba(0, 0, 0, 0.45);
  color: var(--on-primary);
  font-size: var(--fs-tiny);
  padding: 2px 0;
}
.del {
  position: absolute;
  right: 4px;
  top: 4px;
  width: 18px;
  height: 18px;
  border-radius: var(--radius-pill);
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
}
.cell.add {
  border: 1.5px dashed var(--border);
  background: var(--card);
}
.cell.add > * {
  position: absolute;
}
.cell.add > :first-child {
  left: 50%;
  top: 38%;
  transform: translate(-50%, -50%);
}
.add-txt {
  left: 0;
  right: 0;
  bottom: 14%;
  text-align: center;
  font-size: var(--fs-xs);
  color: var(--body);
}
.hint {
  display: block;
  margin: 6px 0 var(--space-md);
  font-size: var(--fs-xs);
  color: var(--body);
}
.field {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  padding: 12px 0;
  border-bottom: 1px solid var(--border);
}
.field.col {
  flex-direction: column;
  align-items: stretch;
}
.title-ipt {
  flex: 1;
  font-size: var(--fs-lg);
  font-weight: 700;
  color: var(--title);
}
.text-ipt {
  width: 100%;
  min-height: 96px;
  font-size: var(--fs-md);
  line-height: 1.7;
  color: var(--title);
}
.ph {
  color: var(--body);
  font-weight: 400;
}
.count {
  font-size: var(--fs-xs);
  color: var(--body);
}
.ai-box {
  padding: 10px 12px;
  border-radius: var(--radius-md);
  border: 1px dashed var(--primary);
  background: var(--highlight);
}
.ai-head {
  display: flex;
  align-items: center;
  gap: 4px;
}
.ai-kicker {
  font-size: var(--fs-xs);
  font-weight: 700;
  color: var(--accent);
}
.ai-text {
  display: block;
  margin-top: 6px;
  font-size: var(--fs-md);
  line-height: 1.7;
  color: var(--title);
}
.cursor {
  color: var(--primary);
}
.ai-acts {
  display: flex;
  gap: var(--space-sm);
  margin-top: var(--space-sm);
}
.ai-use {
  padding: 4px 12px;
  border-radius: var(--radius-pill);
  background: var(--primary-deep);
  color: var(--on-primary);
  font-size: var(--fs-sm);
  font-weight: 700;
}
.ai-alt {
  padding: 4px 12px;
  border-radius: var(--radius-pill);
  border: 1px solid var(--border);
  background: var(--card);
  color: var(--title);
  font-size: var(--fs-sm);
}
.text-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.ai-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  border-radius: var(--radius-pill);
  background: var(--primary);
}
.ai-btn-txt {
  font-size: var(--fs-sm);
  font-weight: 700;
  color: var(--on-primary);
}
.row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
  padding: 14px 0;
  border-bottom: 1px solid var(--border);
}
.row.col {
  flex-direction: column;
  align-items: stretch;
}
.row-label {
  font-size: var(--fs-md);
  font-weight: 700;
  color: var(--title);
}
.row-val {
  display: flex;
  align-items: center;
  gap: 6px;
}
.row-txt {
  font-size: var(--fs-sm);
  color: var(--body);
}
.chips {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-sm);
}
.chip {
  padding: 5px 12px;
  border-radius: var(--radius-pill);
  border: 1px solid var(--border);
  background: var(--card);
}
.chip.on {
  border-color: var(--primary);
  background: var(--highlight);
}
.chip-txt {
  font-size: var(--fs-sm);
  color: var(--title);
}
.chip.on .chip-txt {
  color: var(--accent);
  font-weight: 700;
}
.vis {
  display: flex;
  gap: var(--space-sm);
}
.vis-it {
  flex: 1;
  height: 40px;
  border-radius: var(--radius-md);
  border: 1px solid var(--border);
  background: var(--card);
  display: flex;
  align-items: center;
  justify-content: center;
}
.vis-it.on {
  border-color: var(--primary-deep);
  background: var(--highlight);
}
.vis-txt {
  font-size: var(--fs-md);
  color: var(--title);
}
.privacy {
  margin-top: var(--space-lg);
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 12px;
  border-radius: var(--radius-md);
  background: var(--success-soft);
}
.privacy-txt {
  font-size: var(--fs-sm);
  color: var(--success-deep);
}
.rule {
  display: block;
  margin-top: var(--space-sm);
  font-size: var(--fs-xs);
  color: var(--body);
  line-height: 1.6;
}
.mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  display: flex;
  align-items: flex-end;
  z-index: 20;
}
.sheet {
  width: 100%;
  max-height: 70vh;
  padding: var(--space-lg);
  border-radius: 20px 20px 0 0;
  background: var(--bg);
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
}
.sheet-title {
  font-size: var(--fs-lg);
  font-weight: 700;
  color: var(--title);
}
.search {
  height: 40px;
  padding: 0 12px;
  border-radius: var(--radius-pill);
  background: var(--card);
  border: 1px solid var(--border);
  display: flex;
  align-items: center;
  gap: 6px;
}
.search-ipt {
  flex: 1;
  font-size: var(--fs-md);
}
.sheet-list {
  max-height: 46vh;
}
.pick {
  display: flex;
  align-items: center;
  gap: var(--space-md);
  padding: 8px 0;
  border-bottom: 1px solid var(--border);
}
.pick-name {
  font-size: var(--fs-md);
  color: var(--title);
}
.unlink {
  text-align: center;
  font-size: var(--fs-sm);
  color: var(--error-deep);
}
</style>
