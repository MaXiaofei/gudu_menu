/**
 * 构建期功能开关（mini-release-compliance A.1 / A.9）。
 *
 * 按构建平台决定，编译时 process.env.UNI_PLATFORM 被替换为常量：
 *  - community：厨房圈（社区）。微信小程序正式版不开放，H5 保持现状。
 *  - voice：咕嘟语音（后端语音识别仍是 Mock）。微信小程序隐藏，只保留文字对话。
 *
 * 关闭某功能时：入口用 FEATURES.xxx 隐藏；对应页面/分包在 pages.json 用
 * `// #ifndef MP-WEIXIN` 条件编译剔除，不打进小程序包（test/utils/features.spec.ts 守护两边一致）。
 */
export interface Features {
  community: boolean
  voice: boolean
}

export function resolveFeatures(platform?: string): Features {
  const mpWeixin = platform === 'mp-weixin'
  return {
    community: !mpWeixin,
    voice: !mpWeixin,
  }
}

export const FEATURES: Readonly<Features> = Object.freeze(resolveFeatures(process.env.UNI_PLATFORM))
