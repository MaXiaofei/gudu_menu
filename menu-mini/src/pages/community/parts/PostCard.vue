<template>
  <!-- 厨房圈瀑布流卡片：大图（按 coverRatio 定高）+ 角标 + 标题 + 作者 + 赞。 -->
  <view class="pc" :data-testid="'post-' + post.postId" @click="$emit('open', post)">
    <view class="cover" :style="{ paddingTop: ratioPct }">
      <image class="img" :src="cover" mode="aspectFill" lazy-load />
      <text v-if="post.copyCount > 0" class="badge hot">照着做 {{ countText(post.copyCount) }}</text>
      <text v-else-if="post.tags[0]" class="badge">{{ post.tags[0] }}</text>
      <text v-if="post.imageCount > 1" class="pages">1/{{ post.imageCount }}</text>
    </view>
    <view class="body">
      <text class="title">{{ post.title }}</text>
      <view class="meta">
        <ui-avatar :name="post.author.nickname" :src="avatar" :size="20" />
        <text class="nick">{{ post.author.nickname }}</text>
        <view class="like" :data-testid="'like-' + post.postId" @click.stop="$emit('like', post)">
          <ui-icon name="heart" :tone="post.liked ? 'primary' : 'dark'" :size="14" />
          <text class="like-n" :class="{ on: post.liked }">{{ countText(post.likeCount) }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { countText, coverHeightRatio, type PostCard } from '@/api/community'
import { thumbOf } from '@/utils/image'

const props = defineProps<{ post: PostCard }>()
defineEmits<{ (e: 'open', p: PostCard): void; (e: 'like', p: PostCard): void }>()

/** 卡片图高 = 宽 × 高宽比（coverRatio 后端约定 0.5–2）。 */
const ratioPct = computed(() => `${coverHeightRatio(props.post.coverRatio) * 100}%`)
const cover = computed(() => thumbOf(props.post.coverUrl))
const avatar = computed(() => (props.post.author.avatarUrl ? thumbOf(props.post.author.avatarUrl) : null))
</script>

<style scoped>
.pc {
  border-radius: var(--radius-lg);
  overflow: hidden;
  background: var(--card);
  border: 1px solid var(--border);
  margin-bottom: var(--space-sm);
}
.cover {
  position: relative;
  height: 0;
  background: var(--secondary);
}
.img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}
.badge {
  position: absolute;
  left: 8px;
  top: 8px;
  padding: 2px 8px;
  border-radius: var(--radius-pill);
  background: rgba(255, 255, 255, 0.92);
  color: var(--title);
  font-size: var(--fs-xs);
  font-weight: 700;
}
.badge.hot {
  background: var(--primary-deep);
  color: var(--on-primary);
}
.pages {
  position: absolute;
  right: 8px;
  top: 8px;
  padding: 1px 6px;
  border-radius: var(--radius-pill);
  background: rgba(0, 0, 0, 0.45);
  color: var(--on-primary);
  font-size: var(--fs-tiny);
}
.body {
  padding: 8px 10px 10px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.title {
  font-size: var(--fs-md);
  font-weight: 700;
  line-height: 1.4;
  color: var(--title);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.meta {
  display: flex;
  align-items: center;
  gap: 6px;
}
.nick {
  flex: 1;
  min-width: 0;
  font-size: var(--fs-xs);
  color: var(--body);
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.like {
  display: flex;
  align-items: center;
  gap: 2px;
  padding: 2px 0 2px 6px;
}
.like-n {
  font-size: var(--fs-xs);
  color: var(--body);
}
.like-n.on {
  color: var(--primary-deep);
  font-weight: 700;
}
</style>
