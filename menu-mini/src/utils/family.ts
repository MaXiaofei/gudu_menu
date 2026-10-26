/**
 * 家庭上下文（multi-tenant-family F.1）：登录 / 切换 / 加入后本地缓存「当前家庭 + 我加入的家庭」。
 *
 * 后端约定：当前家庭存在**登录会话**里（token 维度，见 menu-api TenantInterceptor / FamilyController），
 * 请求不需要额外带家庭头；切换必须调 POST /family/current 由服务端写会话。
 * 本地缓存只用于展示（「我的」页切换器、家庭页角色判断）与 403 FAMILY_ACCESS_DENIED 回退。
 *
 * 纯 storage 读写，不依赖 pinia，request.ts 可直接引用。
 */

export const FAMILY_SWITCH_PAGE = '/pkg-family/Switch'
export const FAMILY_ACCESS_DENIED = 'FAMILY_ACCESS_DENIED'

const K_FAMILIES = 'gd_families'
const K_CURRENT = 'gd_current_family'
const K_ACCOUNT = 'gd_account_id'
const K_MEMBER = 'gd_current_member'
/** 邀请落地页记下的邀请码：未登录 → 登录后回到落地页继续加入。 */
export const PENDING_INVITE_KEY = 'gd_pending_invite'

export type FamilyRole = 'OWNER' | 'ADMIN' | 'MEMBER'

/** GET /family/mine、登录响应 families[] 的元素（后端 FamilySummary）。 */
export interface FamilySummary {
  id: number
  name: string
  role: FamilyRole | string
  memberCount: number
}

/** 登录 / 合并账号响应（AuthService.establish）。 */
export interface LoginResult {
  token: string
  accountId?: number
  nickname?: string
  isAdmin?: boolean
  platformRole?: string
  families?: FamilySummary[]
  currentFamilyId?: number | null
  currentMemberId?: number | null
}

export interface FamilyContext {
  families: FamilySummary[]
  currentFamilyId: number | null
}

export const ROLE_LABEL: Record<string, string> = {
  OWNER: '主理人',
  ADMIN: '管理员',
  MEMBER: '成员',
}

export function roleLabel(role?: string | null): string {
  return (role && ROLE_LABEL[role]) || '成员'
}

/** OWNER / ADMIN 可管理（邀请、改名、移除普通成员、建无账号档案）。 */
export function canManage(role?: string | null): boolean {
  return role === 'OWNER' || role === 'ADMIN'
}

export function getFamilies(): FamilySummary[] {
  const v = uni.getStorageSync(K_FAMILIES)
  return Array.isArray(v) ? v : []
}

export function getCurrentFamilyId(): number | null {
  const v = Number(uni.getStorageSync(K_CURRENT) || 0)
  return v > 0 ? v : null
}

export function getAccountId(): number | null {
  const v = Number(uni.getStorageSync(K_ACCOUNT) || 0)
  return v > 0 ? v : null
}

export function setAccountId(id: number | null | undefined): void {
  if (id) uni.setStorageSync(K_ACCOUNT, id)
}

export function currentFamily(): FamilySummary | null {
  const id = getCurrentFamilyId()
  return getFamilies().find((f) => f.id === id) || null
}

/** 写入家庭上下文；families / currentFamilyId 缺省则保留原值（部分接口只回其一）。 */
export function saveFamilyContext(ctx: Partial<FamilyContext> & { currentMemberId?: number | null }): void {
  if (Array.isArray(ctx.families)) uni.setStorageSync(K_FAMILIES, ctx.families)
  if (ctx.currentFamilyId !== undefined) {
    if (ctx.currentFamilyId) uni.setStorageSync(K_CURRENT, ctx.currentFamilyId)
    else uni.removeStorageSync(K_CURRENT)
  }
  if (ctx.currentMemberId !== undefined) {
    if (ctx.currentMemberId) uni.setStorageSync(K_MEMBER, ctx.currentMemberId)
    else uni.removeStorageSync(K_MEMBER)
  }
}

/** 只清当前家庭（403 FAMILY_ACCESS_DENIED 时：服务端已清会话家庭）。 */
export function clearCurrentFamily(): void {
  uni.removeStorageSync(K_CURRENT)
  uni.removeStorageSync(K_MEMBER)
}

export function clearFamilyContext(): void {
  uni.removeStorageSync(K_FAMILIES)
  uni.removeStorageSync(K_ACCOUNT)
  clearCurrentFamily()
}

/**
 * 登录 / 合并账号成功：写 token、昵称、账号 id 与家庭上下文。
 * 合并账号返回的是**手机号账号的新 token**，旧 token 已被服务端下线，必须整体替换。
 */
export function applyLoginResult(r: LoginResult): void {
  uni.setStorageSync('token', r.token)
  if (r.nickname !== undefined) uni.setStorageSync('nickname', r.nickname || '')
  if (r.accountId) uni.setStorageSync(K_ACCOUNT, r.accountId)
  saveFamilyContext({
    families: r.families ?? [],
    currentFamilyId: r.currentFamilyId ?? null,
    currentMemberId: r.currentMemberId ?? null,
  })
  uni.removeStorageSync('logged_out')
}

/**
 * 清本地登录态（退出登录 / 注销账号）：token、登录方式、家庭上下文、待加入邀请。
 * 协议同意记录不清（同意的是协议版本，与账号无关）。
 */
export function clearLocalSession(): void {
  uni.removeStorageSync('token')
  uni.removeStorageSync('nickname')
  uni.removeStorageSync('login_via')
  uni.removeStorageSync(PENDING_INVITE_KEY)
  clearFamilyContext()
}

/** 后端 R 响应是否为家庭访问被拒（code=403 且 data.reason=FAMILY_ACCESS_DENIED）。 */
export function isFamilyDenied(body: any): boolean {
  return !!body && body.code === 403 && body.data?.reason === FAMILY_ACCESS_DENIED
}

let redirecting = false

/**
 * 处理 FAMILY_ACCESS_DENIED：清当前家庭、提示原因、回到家庭选择页（已在该页则不重复跳转；并发只跳一次）。
 */
export function handleFamilyDenied(msg?: string): void {
  clearCurrentFamily()
  uni.showToast({ title: msg || '你已不在该家庭中，请重新选择家庭', icon: 'none' })
  if (redirecting) return
  const pages = typeof getCurrentPages === 'function' ? getCurrentPages() : []
  const top: any = pages[pages.length - 1]
  if (top && '/' + (top.route || '') === FAMILY_SWITCH_PAGE) return
  redirecting = true
  uni.reLaunch({ url: `${FAMILY_SWITCH_PAGE}?reason=denied` })
  setTimeout(() => (redirecting = false), 1000)
}

/** 邀请分享路径（onShareAppMessage / 复制链接共用）。 */
export function invitePath(code: string): string {
  return `/pkg-family/Invite?code=${encodeURIComponent(code)}`
}

/** 首次登录引导是否已处理（按账号记，跳过也算处理过）。 */
export function onboardKey(accountId: number | null | undefined): string {
  return `gd_onboarded_${accountId || 0}`
}

/** 成员档案是否还没填身体/健康信息（首次登录引导用）。 */
export function profileIsBlank(hp?: Record<string, any> | null): boolean {
  if (!hp) return true
  const has = (k: string) => {
    const v = hp[k]
    return Array.isArray(v) ? v.length > 0 : v !== undefined && v !== null && v !== '' && v !== 0
  }
  return !['height', 'weight', 'age', 'gender', 'audiences', 'allergies'].some(has)
}
