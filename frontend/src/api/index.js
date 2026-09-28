import axios from 'axios'
import { useAuthStore } from '../stores/auth'
import { ElMessage } from 'element-plus'
import router from '../router'

const api = axios.create({
  baseURL: '', // 相对路径，走 vite dev proxy；生产时后端与前端同域部署，JWT 由 Authorization 头传递
  timeout: 15000,
})

// 请求拦截器：自动注入 JWT
api.interceptors.request.use((config) => {
  const auth = useAuthStore()
  if (auth.token) {
    config.headers.Authorization = 'Bearer ' + auth.token
  }
  return config
})

/** 401 全局登出提示的防抖标志——同一 token 只提示一次，避免并发请求 401 时连弹 */
let loggedOutToastShown = false

// 响应拦截器：统一处理错误 + 401 踢回登录页
api.interceptors.response.use(
  (resp) => {
    const data = resp.data || {}
    // 后端统一 {success, ...} 包装
    if (data.success === false) {
      // 401 走 status 分支，这里不弹
      if (resp.status !== 401 && data.msg) ElMessage.error(data.msg)
      return Promise.reject(new Error(data.msg || '请求失败'))
    }
    return data
  },
  (err) => {
    const status = err?.response?.status
    const msg = err?.response?.data?.msg || err.message
    if (status === 401) {
      const auth = useAuthStore()
      const isLoginPage = router.currentRoute.value?.name === 'Login'

      // 登录页自身 401 只是登录失败，不要登出或跳走
      if (isLoginPage) {
        ElMessage.error(msg || '用户名或密码错误')
        return Promise.reject(err)
      }

      // 非登录页 401：token 真的过期了 → 清 token + 跳登录（防抖只弹一次）
      if (!loggedOutToastShown) {
        loggedOutToastShown = true
        ElMessage.error('登录已过期，请重新登录')
      }
      auth.logout()
      router.push({ name: 'Login' }).finally(() => {
        // 等 toast 显示完再允许下一次提示
        setTimeout(() => { loggedOutToastShown = false }, 5000)
      })
    } else {
      ElMessage.error(msg || '网络异常')
    }
    return Promise.reject(err)
  }
)

export default api
