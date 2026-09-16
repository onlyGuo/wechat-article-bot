<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { useI18n } from '../i18n'
import LanguageSwitcher from '../components/LanguageSwitcher.vue'
import { ArrowRight, Sparkles, CheckCircle2 } from 'lucide-vue-next'

const auth = useAuthStore()
const router = useRouter()
const username = ref('admin')
const password = ref('Admin@123')
const error = ref('')
const { t } = useI18n()
async function submit() {
  error.value = ''
  try { await auth.login(username.value, password.value); router.push('/') }
  catch (e) { error.value = e.message }
}
</script>

<template>
  <main class="login-page">
    <section class="login-story">
      <div class="story-brand"><span class="brand-mark"><Sparkles :size="20" /></span><strong>{{ t('common.appName') }}</strong></div>
      <div class="story-copy">
        <span class="eyebrow">AI WECHAT STUDIO</span>
        <h1>{{ t('login.headline') }}<br><em>{{ t('login.headlineEmphasis') }}</em></h1>
        <p>{{ t('login.description') }}</p>
        <ul>
          <li><CheckCircle2 :size="18" /> {{ t('login.featureOne') }}</li>
          <li><CheckCircle2 :size="18" /> {{ t('login.featureTwo') }}</li>
          <li><CheckCircle2 :size="18" /> {{ t('login.featureThree') }}</li>
        </ul>
      </div>
      <div class="story-orbit orbit-one"></div><div class="story-orbit orbit-two"></div>
    </section>
    <section class="login-panel">
      <form class="login-card" @submit.prevent="submit">
        <div class="login-language"><LanguageSwitcher /></div>
        <span class="eyebrow">WELCOME BACK</span>
        <h2>{{ t('login.title') }}</h2>
        <p>{{ t('login.subtitle') }}</p>
        <label>{{ t('login.username') }}<input v-model="username" autocomplete="username" :placeholder="t('login.usernamePlaceholder')"></label>
        <label>{{ t('login.password') }}<input v-model="password" type="password" autocomplete="current-password" :placeholder="t('login.passwordPlaceholder')"></label>
        <div v-if="error" class="alert error">{{ error }}</div>
        <button class="primary-button login-button" :disabled="auth.loading">
          {{ auth.loading ? t('login.signingIn') : t('login.enter') }}<ArrowRight :size="18" />
        </button>
        <small class="login-hint">{{ t('login.hint') }}</small>
      </form>
    </section>
  </main>
</template>
