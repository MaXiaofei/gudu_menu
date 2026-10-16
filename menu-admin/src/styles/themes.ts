// 皮肤由 docs/design/tokens.json 生成（tokens.generated.ts）；本文件只做后台视图适配，禁止在此写色值。
// 新增皮肤：改 tokens.json → node scripts/design/gen-tokens.mjs。
import { ADMIN_SEMANTIC, ADMIN_SKINS, adminSkinOf, type AdminSkin } from './tokens.generated'

export type Theme = AdminSkin

/** 全皮肤共享的功能色（tokens.json color.semantic）。 */
export const semantic = ADMIN_SEMANTIC

/** 全部皮肤（第一项为默认）。 */
export const themes: Theme[] = ADMIN_SKINS

/** 按 key 取皮肤，兼容旧版持久化 key（warm/green）；未知回落默认皮肤。 */
export function getTheme(key: string | null | undefined): Theme {
  return adminSkinOf(key)
}
