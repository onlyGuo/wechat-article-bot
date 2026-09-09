import { createRouter, createWebHistory } from 'vue-router'
import LoginView from './views/LoginView.vue'

const routes = [
  { path: '/login', name: 'login', component: LoginView, meta: { public: true, title: '登录' } },
  { path: '/', name: 'dashboard', component: () => import('./views/DashboardView.vue'), meta: { title: '工作台' } },
  { path: '/accounts', name: 'accounts', component: () => import('./views/AccountsView.vue'), meta: { title: '公众号管理' } },
  { path: '/articles', name: 'articles', component: () => import('./views/ArticlesView.vue'), meta: { title: '文章管理' } },
  { path: '/articles/:id', name: 'editor', component: () => import('./views/ArticleEditorView.vue'), meta: { title: '智能文章编辑器', editor: true } },
  { path: '/skills', name: 'skills', component: () => import('./views/SkillsView.vue'), meta: { title: 'Skill 管理' } },
  { path: '/tasks', name: 'tasks', component: () => import('./views/TasksView.vue'), meta: { title: '定时任务' } },
  { path: '/followers', name: 'followers', component: () => import('./views/FollowersView.vue'), meta: { title: '公众号用户' } },
  { path: '/system-users', name: 'system-users', component: () => import('./views/SystemUsersView.vue'), meta: { title: '系统用户' } },
  { path: '/assets', name: 'assets', component: () => import('./views/AssetsView.vue'), meta: { title: '素材库' } },
  { path: '/audit', name: 'audit', component: () => import('./views/AuditView.vue'), meta: { title: '操作审计' } },
  { path: '/settings', name: 'settings', component: () => import('./views/SettingsView.vue'), meta: { title: '系统设置' } },
]

const router = createRouter({ history: createWebHistory(), routes })
router.beforeEach((to) => {
  document.title = `${to.meta.title || '工作台'} · 墨舟`
  if (!to.meta.public && !localStorage.getItem('wechat_bot_token')) return '/login'
  if (to.name === 'login' && localStorage.getItem('wechat_bot_token')) return '/'
})
export default router
