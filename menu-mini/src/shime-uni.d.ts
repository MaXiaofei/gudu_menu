export {}

declare module "vue" {
  type Hooks = App.AppInstance & Page.PageInstance;
  interface ComponentCustomOptions extends Hooks {}
  /** main.ts 全局 mixin 注入：当前主题 CSS 变量串（page-meta page-style 用）。 */
  interface ComponentCustomProperties {
    gdThemeStyle: string
  }
}
