<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { ArrowRight, Sparkles, CheckCircle2 } from 'lucide-vue-next'

const auth = useAuthStore()
const router = useRouter()
const username = ref('admin')
const password = ref('Admin@123')
const error = ref('')
async function submit() {
  error.value = ''
  try { await auth.login(username.value, password.value); router.push('/') }
  catch (e) { error.value = e.message }
}
</script>

<template>
  <main class="login-page">
    <section class="login-story">
      <div class="story-brand"><span class="brand-mark"><Sparkles :size="20" /></span><strong>墨舟</strong></div>
      <div class="story-copy">
        <span class="eyebrow">AI WECHAT STUDIO</span>
        <h1>让每一篇好文章，<br><em>都有从容抵达的节奏。</em></h1>
        <p>公众号、编辑、智能体与自动化任务，在一个清晰的工作流中协同。</p>
        <ul>
          <li><CheckCircle2 :size="18" /> AI 与编辑器实时协作</li>
          <li><CheckCircle2 :size="18" /> 多公众号草稿和发布状态统一管理</li>
          <li><CheckCircle2 :size="18" /> 定时研究、自动创作、全程留痕</li>
        </ul>
      </div>
      <div class="story-orbit orbit-one"></div><div class="story-orbit orbit-two"></div>
    </section>
    <section class="login-panel">
      <form class="login-card" @submit.prevent="submit">
        <span class="eyebrow">WELCOME BACK</span>
        <h2>登录内容工作台</h2>
        <p>使用你的系统账号继续。</p>
        <label>用户名<input v-model="username" autocomplete="username" placeholder="请输入用户名"></label>
        <label>密码<input v-model="password" type="password" autocomplete="current-password" placeholder="请输入密码"></label>
        <div v-if="error" class="alert error">{{ error }}</div>
        <button class="primary-button login-button" :disabled="auth.loading">
          {{ auth.loading ? '登录中…' : '进入工作台' }}<ArrowRight :size="18" />
        </button>
        <small class="login-hint">首次启动默认账号 admin / Admin@123</small>
      </form>
    </section>
  </main>
</template>
