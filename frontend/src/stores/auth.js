import { defineStore } from 'pinia'
import api from '../api'

const STORAGE_KEY = 'sg_token'
const USER_KEY = 'sg_user'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem(STORAGE_KEY) || '',
    user: JSON.parse(localStorage.getItem(USER_KEY) || 'null'),
  }),
  getters: {
    isLoggedIn: (s) => !!s.token,
    username: (s) => s.user?.username || '',
    nickname: (s) => s.user?.nickname || s.user?.username || '',
    role: (s) => s.user?.role || 'USER',
    gardenSize: (s) => s.user?.gardenSize || 6,
    consecutiveDays: (s) => s.user?.consecutiveDays || 0,
    totalPlants: (s) => s.user?.totalPlants || 0,
  },
  actions: {
    /** 登录：后端签发 JWT 并返回用户基础信息 */
    async login(username, password) {
      const data = await api.post('/api/auth/login', { username, password })
      this.token = data.token
      this.user = data.user
      localStorage.setItem(STORAGE_KEY, data.token)
      localStorage.setItem(USER_KEY, JSON.stringify(data.user))
      return data
    },
    /** 注册并直接登录 */
    async register(form) {
      const data = await api.post('/api/auth/register', form)
      this.token = data.token
      this.user = data.user
      localStorage.setItem(STORAGE_KEY, data.token)
      localStorage.setItem(USER_KEY, JSON.stringify(data.user))
      return data
    },
    /** 拉取最新用户资料（含 gardenSize/consecutiveDays/totalPlants 变化） */
    async refreshProfile() {
      const data = await api.get('/api/user/profile')
      this.user = data.user
      localStorage.setItem(USER_KEY, JSON.stringify(data.user))
      return data.user
    },
    /** 退出登录 */
    logout() {
      this.token = ''
      this.user = null
      localStorage.removeItem(STORAGE_KEY)
      localStorage.removeItem(USER_KEY)
    },
  },
})
