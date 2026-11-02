/**
 * request 注入点（修复小程序动态 import 失效）：
 * featureSwitch / consent 需要发请求，但 request.ts 又静态依赖它们（循环依赖）。
 * 原先用 `await import('./request')` 规避，uni-app 编译微信小程序时会把它变成字符串 "./request.js"，
 * 解构出 undefined，调用即抛错。改为：request.ts 在模块初始化时 bindRequest(request) 注册自己，
 * 这里只放一个无任何依赖的叶子模块，彻底不引入循环。禁止再写动态 import()（见 test/utils/no-dynamic-import.spec.ts）。
 */
type RequestFn = <T = any>(opts: any) => Promise<T>

let impl: RequestFn | null = null

/** 仅 request.ts 调用：注册真实的 request。 */
export function bindRequest(fn: RequestFn): void {
  impl = fn
}

/** 经注入的 request 发请求；尚未注册（request.ts 未加载）时按失败处理，调用方本就 try/catch。 */
export function bridgedRequest<T = any>(opts: any): Promise<T> {
  if (!impl) return Promise.reject(new Error('request 尚未就绪'))
  return impl<T>(opts)
}

type PromptLoginFn = (opts?: { target?: string | null; onCancel?: () => void }) => Promise<boolean>
let promptLoginImpl: PromptLoginFn | null = null

/** 仅 guest.ts 调用：注册 promptLogin（loginRedirect.ensureLogin 要用，二者互相依赖，同样不能动态 import）。 */
export function bindPromptLogin(fn: PromptLoginFn): void {
  promptLoginImpl = fn
}

/** 经注入的 promptLogin 弹登录引导；尚未注册时什么也不做。 */
export function bridgedPromptLogin(opts?: Parameters<PromptLoginFn>[0]): Promise<boolean> {
  return promptLoginImpl ? promptLoginImpl(opts) : Promise.resolve(false)
}
