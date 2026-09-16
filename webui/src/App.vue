<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from './stores/auth'
import { useI18n } from './i18n'
import LanguageSwitcher from './components/LanguageSwitcher.vue'
import {
  LayoutDashboard, Radio, FileText, Bot, Users, UserCog, Images, ShieldCheck,
  LogOut, Menu, Search, Sparkles, X, Settings,
} from 'lucide-vue-next'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const { t, locale } = useI18n()
const collapsed = ref(false)
const isPublic = computed(() => route.meta.public)
const isEditor = computed(() => route.meta.editor)
const items = [
  { to: '/', labelKey: 'nav.dashboard', icon: LayoutDashboard },
  { to: '/accounts', labelKey: 'nav.accounts', icon: Radio },
  { to: '/articles', labelKey: 'nav.articles', icon: FileText },
  { to: '/skills', labelKey: 'nav.skills', icon: Sparkles },
  { to: '/tasks', labelKey: 'nav.tasks', icon: Bot },
  { to: '/followers', labelKey: 'nav.followers', icon: Users },
  { to: '/system-users', labelKey: 'nav.users', icon: UserCog },
  { to: '/assets', labelKey: 'nav.assets', icon: Images },
  { to: '/audit', labelKey: 'nav.audit', icon: ShieldCheck },
  { to: '/settings', labelKey: 'nav.settings', icon: Settings },
]

onMounted(() => { if (!isPublic.value) auth.load() })
watch([locale, () => route.meta.titleKey], ([, titleKey]) => { document.title = `${t(titleKey || 'nav.dashboard')} · ${t('common.appName')}` })
async function logout() { await auth.logout(); router.push('/login') }
</script>

<template>
  <RouterView v-if="isPublic" />
  <div v-else class="app-shell" :class="{ 'sidebar-collapsed': collapsed, 'editor-shell': isEditor }">
    <aside class="sidebar">
      <div class="brand">
        <span class="brand-mark"><Sparkles :size="19" /></span>
        <div class="brand-copy"><strong>{{ t('common.appName') }}</strong><small>{{ t('common.appSubtitle') }}</small></div>
        <button class="mobile-close" @click="collapsed = true"><X :size="19" /></button>
      </div>
      <nav>
        <RouterLink v-for="item in items" :key="item.to" :to="item.to" :title="t(item.labelKey)">
          <component :is="item.icon" :size="19" /><span>{{ t(item.labelKey) }}</span>
        </RouterLink>
      </nav>
      <div class="sidebar-footer">
        <div class="avatar">{{ auth.user?.displayName?.slice(0, 1) || t('app.administratorInitial') }}</div>
        <div class="account-copy"><strong>{{ auth.user?.displayName || t('app.systemAdmin') }}</strong><small>{{ auth.user?.role || 'ADMIN' }}</small></div>
        <button class="icon-button inverse" :title="t('app.logout')" @click="logout"><LogOut :size="17" /></button>
      </div>
    </aside>
    <main class="workspace">
      <header v-if="!isEditor" class="topbar">
        <button class="icon-button menu-button" @click="collapsed = !collapsed"><Menu :size="20" /></button>
        <div><h1>{{ t(route.meta.titleKey) }}</h1><p>{{ t('app.greeting') }}</p></div>
        <div class="topbar-actions">
          <button class="search-trigger"><Search :size="17" /><span>{{ t('app.searchPlaceholder') }}</span><kbd>⌘ K</kbd></button>
          <LanguageSwitcher />
        </div>
      </header>
      <RouterView />
    </main>
    <button v-if="collapsed" class="floating-menu" @click="collapsed = false"><Menu :size="20" /></button>
  </div>
</template>
