import { defineStore } from 'pinia'
import { api } from '../api'

export const useAuthStore = defineStore('auth', {
  state: () => ({ user: null, loading: false }),
  actions: {
    async login(username, password) {
      this.loading = true
      try {
        const result = await api('/api/auth/login', { method: 'POST', body: JSON.stringify({ username, password }) })
        localStorage.setItem('wechat_bot_token', result.token)
        this.user = result.user
      } finally {
        this.loading = false
      }
    },
    async load() {
      if (!localStorage.getItem('wechat_bot_token')) return false
      try {
        this.user = await api('/api/auth/me')
        return true
      } catch {
        return false
      }
    },
    async logout() {
      try { await api('/api/auth/logout', { method: 'POST' }) } catch { /* ignore */ }
      localStorage.removeItem('wechat_bot_token')
      this.user = null
    },
  },
})
