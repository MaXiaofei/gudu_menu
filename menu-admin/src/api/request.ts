import axios, { type AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'

const TOKEN_KEY = 'yanhuo-token'

const instance = axios.create({
  baseURL: '/api',
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
        localStorage.removeItem(TOKEN_KEY)
        // 避免在登录页循环
        if (!location.pathname.startsWith('/login')) {
          location.href = '/login'
        }
        return Promise.reject(new Error(body.msg || '未登录'))
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
