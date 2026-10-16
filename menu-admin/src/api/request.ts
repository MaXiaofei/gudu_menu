import axios, { type AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import { TOKEN_KEY, clearAuth } from '@/utils/auth-storage'

const instance = axios.create({
  baseURL: '/gudu',
  timeout: 15000,
})

// 请求拦截器：自动塞 Authorization
instance.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem(TOKEN_KEY)
    if (token) {
      config.headers = config.headers || {}
      config.headers.Authorization = token
    }
    return config
  },
  (error) => Promise.reject(error),
)

// 响应拦截器：拆包统一响应 R {code,msg,data}
instance.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code === 0) {
        return body.data
      }
      if (body.code === 401) {
        clearAuth()
        // 避免在登录页循环
        if (!location.pathname.startsWith('/login')) {
          location.href = '/login'
        }
        return Promise.reject(new Error(body.msg || '未登录'))
      }
      if (body.code === 403) {
        // 无权限（如非超管访问后台接口 / 登录 scope=admin 被拒）：提示服务端原因，不清登录态
        const msg = body.msg || '无权限访问'
        ElMessage.error(msg)
        return Promise.reject(new Error(msg))
      }
      ElMessage.error(body.msg || '请求失败')
      return Promise.reject(new Error(body.msg || '请求失败'))
    }
    return body
  },
  (error) => {
    const msg = error?.response?.data?.msg || error.message || '网络错误'
    ElMessage.error(msg)
    return Promise.reject(error)
  },
)

export function request<T = unknown>(config: AxiosRequestConfig): Promise<T> {
  return instance.request<unknown, T>(config)
}

export default instance
