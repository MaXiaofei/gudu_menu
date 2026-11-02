import { createApp } from 'vue'
import { createPinia } from 'pinia'
// Element Plus 组件/指令由 unplugin-vue-components 按需引入（见 vite.config.ts）；
// 样式仍整体引入一次，保证主题 CSS 变量覆盖与 ElMessage/ElMessageBox 等命令式组件样式完整。
import 'element-plus/dist/index.css'
import './styles/global.css'
import App from './App.vue'
import router from './router'
import { useThemeStore } from './store/theme'
import { reloadOnChunkError } from './utils/chunkReload'

// Vite 预加载 chunk 失败（旧版文件已被新部署删除）：自动刷新一次拿新版
window.addEventListener('vite:preloadError', (e) => {
  if (reloadOnChunkError((e as Event & { payload?: unknown }).payload)) e.preventDefault()
})

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(router)

// 首屏应用记住的主题
useThemeStore().apply(useThemeStore().current)

app.mount('#app')
