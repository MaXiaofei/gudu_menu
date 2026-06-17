import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import 'element-plus/dist/index.css'
import './styles/global.css'
import App from './App.vue'
import router from './router'
import { useThemeStore } from './store/theme'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(router)
app.use(ElementPlus)

// 注册所有 Element Plus 图标
for (const [key, comp] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, comp as never)
}

// 首屏应用记住的主题
useThemeStore().apply(useThemeStore().current)

app.mount('#app')
