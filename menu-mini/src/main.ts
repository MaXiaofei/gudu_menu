import { createSSRApp } from "vue";
import { createPinia } from "pinia";
import uviewPlus from "uview-plus";
import App from "./App.vue";
import { themeState } from "@/utils/theme";

export function createApp() {
  const app = createSSRApp(App);
  app.use(createPinia());
  app.use(uviewPlus);
  // 全局主题：页面模板首节点 <page-meta :page-style="gdThemeStyle" /> 读它（见 utils/theme.ts）
  app.mixin({
    computed: {
      gdThemeStyle(): string {
        return themeState.style;
      },
    },
  });
  return {
    app,
  };
}
