import axios, { type AxiosInstance, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'

let isRefreshing = false
let failedQueue: Array<{
  resolve: (value: unknown) => void
  reject: (reason?: unknown) => void
  config: InternalAxiosRequestConfig
}> = []

function processFailedQueue(error: unknown) {
  failedQueue.forEach((item) => {
    item.reject(error)
  })
  failedQueue = []
}

function pushToLogin() {
  // Lazy import router to avoid "useRouter() must be called from setup" error
  import('@/router').then(({ default: router }) => {
    router.push('/login')
  }).catch(() => {
    // Router not ready, fall back to direct assignment
    window.location.hash = '/login'
  })
}

const request: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 10000,
  headers: { 'Content-Type': 'application/json' },
})

// Request interceptor — attach token
request.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = localStorage.getItem('access_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// Response interceptor — unwrap Result envelope
request.interceptors.response.use(
  (response: AxiosResponse) => {
    const data = response.data
    // Pass through non-JSON or binary responses
    if (!data || typeof data !== 'object' || !('code' in data)) {
      return response
    }
    if (data.code === 0) {
      return data.data
    }
    if (data.code === 401) {
      localStorage.removeItem('access_token')
      pushToLogin()
      return Promise.reject(new Error(data.msg))
    }
    ElMessage.error(data.msg || '请求失败')
    return Promise.reject(new Error(data.msg))
  },
  async (error) => {
    // Handle 401 from token expiry — try refresh
    if (error.response?.status === 401 && !error.config._retried) {
      if (!isRefreshing) {
        isRefreshing = true
        try {
          const { authApi } = await import('@/api/auth')
          await authApi.refresh()
          // Refresh succeeded — reissue queued requests
          const newToken = localStorage.getItem('access_token')
          if (newToken) {
            const originalRequests = [...failedQueue]
            failedQueue = []
            originalRequests.forEach((item) => {
              item.config.headers.Authorization = `Bearer ${newToken}`
              item.resolve(request(item.config))
            })
          }
          // Retry the original request
          error.config._retried = true
          return request(error.config)
        } catch (refreshError) {
          // Refresh failed — clear token and go to login
          localStorage.removeItem('access_token')
          pushToLogin()
          processFailedQueue(refreshError)
          return Promise.reject(refreshError)
        } finally {
          isRefreshing = false
        }
      }
      // Another request is already refreshing — queue this request
      return new Promise((resolve, reject) => {
        failedQueue.push({ resolve, reject, config: error.config })
      })
    }

    // Handle HTTP 422 (CORS preflight failure)
    if (error.response?.status === 422) {
      ElMessage.error('网络请求被拒绝，请检查网络或跨域设置')
    } else if (error.response?.status === 413) {
      ElMessage.error('文件过大被服务器拒绝，请压缩后重试')
    } else if (error.response) {
      ElMessage.error(`服务器错误 (${error.response.status})`)
    } else if (error.request) {
      ElMessage.error('网络连接失败，请检查网络')
    } else {
      ElMessage.error(error.message || '请求失败')
    }
    return Promise.reject(error)
  }
)

export default request
