<script setup lang="ts">
import { computed } from 'vue'
import { iconUrl, resolveIconKey } from '@/utils/ingredientIcon'
import { initialOf } from '@/utils/image'

/** 食材插画（原型 .ing）：secondary 浅底圆角块；无插画时退首字。muted=用完/非食用，去色显示。 */
const props = withDefaults(
  defineProps<{
    name?: string | null
    iconKey?: string | null
    categoryName?: string | null
    size?: number
    muted?: boolean
  }>(),
  { name: '', iconKey: null, categoryName: null, size: 40, muted: false },
)

const key = computed(() => resolveIconKey({ iconKey: props.iconKey, name: props.name, categoryName: props.categoryName }))
const src = computed(() => iconUrl(key.value))
const box = computed(() => ({
  width: `${props.size}px`,
  height: `${props.size}px`,
  borderRadius: `${Math.round(props.size * 0.3)}px`,
}))
const img = computed(() => ({ width: `${Math.round(props.size * 0.75)}px`, height: `${Math.round(props.size * 0.75)}px` }))
</script>

<template>
  <span class="ing-icon" :class="{ muted }" :style="box" :data-icon="key || 'initial'">
    <img v-if="src" :src="src" :style="img" :alt="name || '食材'" />
    <span v-else class="initial" :style="{ fontSize: `${Math.round(size * 0.42)}px` }">{{ initialOf(name, '材') }}</span>
  </span>
</template>

<style scoped>
.ing-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  background: var(--yh-secondary);
  vertical-align: middle;
}
.ing-icon.muted img {
  filter: grayscale(1);
  opacity: 0.5;
}
.initial {
  font-weight: 700;
  color: var(--yh-title);
  opacity: 0.45;
}
</style>
