import axios, { type AxiosInstance, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'

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
      window.location.href = '/login'
      return Promise.reject(new Error(data.msg))
    }
    ElMessage.error(data.msg || '请求失败')
    return Promise.reject(new Error(data.msg))
  },
  (error) => {
    if (error.response?.status === 422) {
      ElMessage.error('请检查输入内容')
    } else {
      ElMessage.error('网络错误，请稍后重试')
    }
    return Promise.reject(error)
  }
)

export default request
