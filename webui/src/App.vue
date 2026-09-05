<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from './stores/auth'
import {
  LayoutDashboard, Radio, FileText, Bot, Users, UserCog, Images, ShieldCheck,
  LogOut, Menu, Search, Sparkles, X, Settings,
} from 'lucide-vue-next'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const collapsed = ref(false)
const isPublic = computed(() => route.meta.public)
const isEditor = computed(() => route.meta.editor)
const items = [
  { to: '/', label: '工作台', icon: LayoutDashboard },
  { to: '/accounts', label: '公众号管理', icon: Radio },
  { to: '/articles', label: '文章管理', icon: FileText },
  { to: '/tasks', label: '定时任务', icon: Bot },
  { to: '/followers', label: '公众号用户', icon: Users },
  { to: '/system-users', label: '系统用户', icon: UserCog },
  { to: '/assets', label: '素材库', icon: Images },
  { to: '/audit', label: '操作审计', icon: ShieldCheck },
  { to: '/settings', label: '系统设置', icon: Settings },
]

onMounted(() => { if (!isPublic.value) auth.load() })
async function logout() { await auth.logout(); router.push('/login') }
</script>

<template>
  <RouterView v-if="isPublic" />
  <div v-else class="app-shell" :class="{ 'sidebar-collapsed': collapsed, 'editor-shell': isEditor }">
    <aside class="sidebar">
      <div class="brand">
        <span class="brand-mark"><Sparkles :size="19" /></span>
        <div class="brand-copy"><strong>墨舟</strong><small>AI 内容工作台</small></div>
        <button class="mobile-close" @click="collapsed = true"><X :size="19" /></button>
      </div>
      <nav>
        <RouterLink v-for="item in items" :key="item.to" :to="item.to" :title="item.label">
          <component :is="item.icon" :size="19" /><span>{{ item.label }}</span>
        </RouterLink>
      </nav>
      <div class="sidebar-footer">
        <div class="avatar">{{ auth.user?.displayName?.slice(0, 1) || '管' }}</div>
        <div class="account-copy"><strong>{{ auth.user?.displayName || '系统管理员' }}</strong><small>{{ auth.user?.role || 'ADMIN' }}</small></div>
        <button class="icon-button inverse" title="退出登录" @click="logout"><LogOut :size="17" /></button>
      </div>
    </aside>
    <main class="workspace">
      <header v-if="!isEditor" class="topbar">
        <button class="icon-button menu-button" @click="collapsed = !collapsed"><Menu :size="20" /></button>
        <div><h1>{{ route.meta.title }}</h1><p>今天也让好内容更快抵达读者。</p></div>
        <div class="topbar-actions">
          <button class="search-trigger"><Search :size="17" /><span>搜索文章、任务…</span><kbd>⌘ K</kbd></button>
        </div>
      </header>
      <RouterView />
    </main>
    <button v-if="collapsed" class="floating-menu" @click="collapsed = false"><Menu :size="20" /></button>
  </div>
</template>
