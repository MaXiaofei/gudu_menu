import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [
    vue(),
    // Element Plus 按需引入：模板里的 <el-*> 与 v-loading 自动 import，不再 app.use(ElementPlus) 全量注册。
    // 样式在 main.ts 整体引入（importStyle:false），避免按组件注入样式与主题变量覆盖的顺序问题。
    Components({
      resolvers: [ElementPlusResolver({ importStyle: false })],
      // 暂不生成 components.d.ts：开启后 el-table 作用域插槽 row 变为 DefaultRow 强类型，存量页面有 ~25 处需逐一标注，另行处理
      dts: false,
    }),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    host: '0.0.0.0',
    port: 5173,
    // 允许读取仓库级设计源（docs/design/ingredient-icons.json，食材插画规则单一来源）
    fs: {
      allow: [fileURLToPath(new URL('.', import.meta.url)), fileURLToPath(new URL('../docs/design', import.meta.url))],
    },
    proxy: {
      '/gudu': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        // 后端 context-path=/gudu，直接透传，无需 rewrite
      },
    },
  },
})
