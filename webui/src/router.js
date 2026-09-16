import { createRouter, createWebHistory } from 'vue-router'
import LoginView from './views/LoginView.vue'
import { t } from './i18n'

const routes = [
  { path: '/login', name: 'login', component: LoginView, meta: { public: true, titleKey: 'login.title' } },
  { path: '/', name: 'dashboard', component: () => import('./views/DashboardView.vue'), meta: { titleKey: 'nav.dashboard' } },
  { path: '/accounts', name: 'accounts', component: () => import('./views/AccountsView.vue'), meta: { titleKey: 'nav.accounts' } },
  { path: '/articles', name: 'articles', component: () => import('./views/ArticlesView.vue'), meta: { titleKey: 'nav.articles' } },
  { path: '/articles/:id', name: 'editor', component: () => import('./views/ArticleEditorView.vue'), meta: { titleKey: 'nav.articles', editor: true } },
  { path: '/skills', name: 'skills', component: () => import('./views/SkillsView.vue'), meta: { titleKey: 'nav.skills' } },
  { path: '/tasks', name: 'tasks', component: () => import('./views/TasksView.vue'), meta: { titleKey: 'nav.tasks' } },
  { path: '/followers', name: 'followers', component: () => import('./views/FollowersView.vue'), meta: { titleKey: 'nav.followers' } },
  { path: '/system-users', name: 'system-users', component: () => import('./views/SystemUsersView.vue'), meta: { titleKey: 'nav.users' } },
  { path: '/assets', name: 'assets', component: () => import('./views/AssetsView.vue'), meta: { titleKey: 'nav.assets' } },
  { path: '/audit', name: 'audit', component: () => import('./views/AuditView.vue'), meta: { titleKey: 'nav.audit' } },
  { path: '/settings', name: 'settings', component: () => import('./views/SettingsView.vue'), meta: { titleKey: 'nav.settings' } },
]

const router = createRouter({ history: createWebHistory(), routes })
router.beforeEach((to) => {
  document.title = `${t(to.meta.titleKey || 'nav.dashboard')} · ${t('common.appName')}`
  if (!to.meta.public && !localStorage.getItem('wechat_bot_token')) return '/login'
  if (to.name === 'login' && localStorage.getItem('wechat_bot_token')) return '/'
})
export default router
