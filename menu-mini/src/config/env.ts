/**
 * ============================================================
 * 环境切换唯一入口（预发 / 生产）
 * ============================================================
 *
 * 用法：把下面的 ENV 改成 'staging' 或 'prod'，重新编译即可。
 * 全站所有出网地址（API 请求 / 图片 / 上传 / 分享链接）自动跟随切换，
 * 其他任何文件不需要动。
 *
 *  ENV = 'staging' → 预发环境（feat/mvp 分支自动部署的后端）
 *  ENV = 'prod'    → 生产环境（main 分支自动部署的后端）
 *
 * 发布正式版小程序前：确认 ENV = 'prod'。
 * ============================================================
 */
export type EnvName = 'staging' | 'prod'

export const ENV: EnvName = 'staging'

/** 各环境 API 基址（含 /gudu context-path）。 */
const ENDPOINTS: Record<EnvName, string> = {
  staging: 'https://staging.imxf.cloud/gudu',
  prod: 'https://imxf.cloud/gudu',
}

/** API 基址：请求（request.ts）、图片（image.ts）、上传（upload.ts）统一引用。 */
export const BASE = ENDPOINTS[ENV]

/** 环境中文名（错误提示/日志用）。 */
export const ENV_LABEL = ENV === 'prod' ? '生产' : '预发'
