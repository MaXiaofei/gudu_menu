import { request, BASE } from '@/utils/request'

/**
 * 个人信息保护（mini-release-compliance）：
 * - B.2 敏感信息单独同意：健康档案 sensitive_health、不满 14 周岁儿童信息 child_info（监护人同意）
 * - C.2 导出我的数据：GET /account/me/export、POST /account/me/export/link（每天合计 3 次）
 */

export type SensitiveDocType = 'sensitive_health' | 'child_info'

/** 单独同意弹窗文案版本（与后端 ConsentAppService.SENSITIVE_DOC_VERSION 一致，文案改版时同步升级）。 */
export const SENSITIVE_DOC_VERSION = '2026-10-26'

export interface ConsentItem {
  granted: boolean
  version?: string | null
  at?: string | null
}

export type ConsentStatus = Record<SensitiveDocType, ConsentItem>

function normalize(data: any): ConsentStatus {
  const items = data?.items ?? {}
  const pick = (k: SensitiveDocType): ConsentItem => ({
    granted: !!items[k]?.granted,
    version: items[k]?.version ?? null,
    at: items[k]?.at ?? null,
  })
  return { sensitive_health: pick('sensitive_health'), child_info: pick('child_info') }
}

/** 当前账号的单独同意状态。 */
export async function getSensitiveConsent(): Promise<ConsentStatus> {
  return normalize(await request<any>({ url: '/consent/sensitive', method: 'GET' }))
}

/** 同意（可一次同意多项，如儿童档案同时同意健康档案 + 监护人同意）。 */
export async function grantSensitiveConsent(docTypes: SensitiveDocType[]): Promise<ConsentStatus> {
  const data = await request<any>({
    url: '/consent/sensitive',
    method: 'POST',
    data: { docTypes: Array.from(new Set(docTypes)), docVersion: SENSITIVE_DOC_VERSION, channel: 'mp-weixin' },
  })
  return normalize(data)
}

/** 撤回同意：后端同时删除本账号本人档案及其填写的档案中的对应信息。 */
export async function withdrawSensitiveConsent(docType: SensitiveDocType): Promise<ConsentStatus> {
  const data = await request<any>({
    url: '/consent/sensitive/withdraw',
    method: 'POST',
    data: { docType, channel: 'mp-weixin' },
  })
  return normalize(data)
}

// ---------------------------------------------------------------- 导出我的数据

/** 导出内容（只含本人信息；其他家庭成员的档案不在其中）。 */
export interface MyDataExport {
  formatVersion: number
  exportedAt: string
  accountId: number
  note?: string
  account?: Record<string, any> | null
  families?: any[]
  dishes?: any[]
  cookingRecords?: any[]
  dailyLogs?: any[]
  reviews?: any[]
  aiConversations?: any[]
  feedback?: any[]
  consents?: any[]
  [key: string]: any
}

/** 直接取个人数据 JSON（每天 3 次，超限后端 429）。小程序内只适合展示摘要，保存文件请用 {@link createExportLink}。 */
export function exportMyData(): Promise<MyDataExport> {
  return request<MyDataExport>({ url: '/account/me/export', method: 'GET', timeout: 30000 })
}

export interface ExportLink {
  /** 完整下载地址（BASE + 后端返回的相对路径），30 分钟内在浏览器打开即下载 JSON 文件 */
  url: string
  expiresAt: string
}

/**
 * 生成个人数据临时下载链接（计入每天 3 次）。
 * 小程序无法直接把文件存到手机：页面拿到 url 后用 {@link copyExportLink} 复制，提示用户粘贴到手机/电脑浏览器下载。
 */
export async function createExportLink(): Promise<ExportLink> {
  const r = await request<{ path: string; expiresAt: string }>({ url: '/account/me/export/link', method: 'POST' })
  return { url: BASE + r.path, expiresAt: r.expiresAt }
}

/** 复制下载链接到剪贴板（30 分钟有效）。 */
export function copyExportLink(link: ExportLink): Promise<void> {
  return new Promise((resolve, reject) => {
    uni.setClipboardData({
      data: link.url,
      success: () => {
        uni.showToast({ title: '链接已复制，30 分钟内在浏览器打开下载', icon: 'none' })
        resolve()
      },
      fail: (e: any) => reject(e),
    })
  })
}
