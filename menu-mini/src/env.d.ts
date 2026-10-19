/// <reference types="vite/client" />

declare module '*.vue' {
  import { DefineComponent } from 'vue'
  // eslint-disable-next-line @typescript-eslint/no-explicit-any, @typescript-eslint/ban-types
  const component: DefineComponent<{}, {}, any>
  export default component
}

// uview-plus 包未在 package.json 声明 types 入口，这里补默认导出（Vue 插件）的类型
declare module 'uview-plus' {
  import type { Plugin } from 'vue'
  const uviewPlus: Plugin
  export default uviewPlus
}

// uni-app 编译期注入的平台标识（vite define）；单测环境为 undefined
declare const process: { env: { UNI_PLATFORM?: string; [k: string]: string | undefined } }
