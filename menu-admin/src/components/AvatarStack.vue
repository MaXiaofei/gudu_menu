<script setup lang="ts">
import { computed } from 'vue'
import { initialOf } from '@/utils/image'

export interface AvatarPerson {
  name?: string | null
  avatarUrl?: string | null
}

/** 头像叠放（原型 .avs）：最多 max 个，超出显示 +N；无头像退首字圆。单人时就是一个头像。 */
const props = withDefaults(
  defineProps<{
    people: AvatarPerson[]
    max?: number
    size?: number
  }>(),
  { max: 4, size: 28 },
)

const shown = computed(() => props.people.slice(0, props.max))
const more = computed(() => Math.max(0, props.people.length - props.max))
const style = computed(() => ({
  width: `${props.size}px`,
  height: `${props.size}px`,
  fontSize: `${Math.round(props.size * 0.42)}px`,
}))
</script>

<template>
  <span class="avs" :style="{ '--ov': `${Math.round(size * 0.28)}px` }">
    <span
      v-for="(p, i) in shown"
      :key="i"
      class="av"
      :style="style"
      :title="p.name || undefined"
    >
      <img v-if="p.avatarUrl" :src="p.avatarUrl" :alt="p.name || '头像'" />
      <template v-else>{{ initialOf(p.name, '用') }}</template>
    </span>
    <span v-if="more" class="av more" :style="style">+{{ more }}</span>
  </span>
</template>

<style scoped>
.avs {
  display: inline-flex;
  align-items: center;
  vertical-align: middle;
}
.av {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  border: 2px solid var(--yh-panel);
  background: var(--yh-secondary);
  color: var(--yh-title);
  font-weight: 700;
  overflow: hidden;
  flex-shrink: 0;
  box-sizing: border-box;
  margin-left: calc(-1 * var(--ov));
}
.av:first-child {
  margin-left: 0;
}
.av img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.av.more {
  color: var(--yh-body);
  font-size: 11px;
}
</style>
