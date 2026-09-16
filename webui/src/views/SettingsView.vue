<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api'
import { useI18n } from '../i18n'
import { BrainCircuit, KeyRound, Save, ShieldCheck } from 'lucide-vue-next'

const router = useRouter()
const { t } = useI18n()
const loading=ref(true),saving=ref(false),message=ref(''),error=ref('')
const llm=reactive({provider:'OPENAI_COMPATIBLE',baseUrl:'https://api.openai.com',modelName:'gpt-4.1-mini',apiKey:'',hasApiKey:false,apiKeyMasked:t('settings.notConfigured'),enabled:false,temperature:0.7,maxTokens:4096,clearApiKey:false,imageBaseUrl:'',imageModelName:'',imageApiKey:'',hasImageApiKey:false,imageApiKeyMasked:t('settings.reuseLlm'),clearImageApiKey:false})
const password=reactive({currentPassword:'',newPassword:'',confirmPassword:''})

async function load(){try{Object.assign(llm,await api('/api/settings/llm'))}catch(e){error.value=e.message}finally{loading.value=false}}
async function saveLlm(){saving.value=true;error.value='';message.value='';try{const result=await api('/api/settings/llm',{method:'PUT',body:JSON.stringify(llm)});Object.assign(llm,result,{apiKey:'',clearApiKey:false,imageApiKey:'',clearImageApiKey:false});message.value=t('settings.saved')}catch(e){error.value=e.message}finally{saving.value=false}}
async function changePassword(){error.value='';message.value='';if(password.newPassword!==password.confirmPassword){error.value=t('settings.passwordMismatch');return}try{await api('/api/auth/password',{method:'PUT',body:JSON.stringify(password)});localStorage.removeItem('wechat_bot_token');message.value=t('settings.passwordChanged');setTimeout(()=>router.push('/login'),800)}catch(e){error.value=e.message}}
onMounted(load)
</script>

<template>
  <section class="page-content settings-page">
    <div class="section-heading"><div><span class="eyebrow">SYSTEM SETTINGS</span><h2>{{ t('settings.title') }}</h2><p>{{ t('settings.description') }}</p></div></div>
    <div v-if="error" class="alert error">{{error}}</div><div v-if="message" class="alert success-alert">{{message}}</div>
    <div v-if="loading" class="panel empty-state">{{ t('settings.loading') }}</div>
    <div v-else class="settings-grid">
      <form class="panel settings-card" @submit.prevent="saveLlm">
        <header><span class="settings-icon"><BrainCircuit/></span><div><h3>{{ t('settings.llm') }}</h3><p>{{ t('settings.llmDescription') }}</p></div></header>
        <div class="form-grid">
          <label>{{ t('settings.provider') }}<select v-model="llm.provider"><option value="OPENAI_COMPATIBLE">Chat Completions</option><option value="OPENAI_RESPONSES">Responses / Codex</option><option value="ANTHROPIC">Anthropic Messages</option></select><small>{{ t('settings.providerHint') }}</small></label>
          <label>{{ t('settings.model') }}<input v-model="llm.modelName" required placeholder="gpt-4.1-mini"></label>
          <label class="full">Base URL<input v-model="llm.baseUrl" required placeholder="https://api.openai.com"><small>{{ t('settings.baseUrlHint') }}</small></label>
          <label class="full">API Key<input v-model="llm.apiKey" type="password" :placeholder="llm.hasApiKey?t('settings.apiKeySaved'):t('settings.apiKeyPlaceholder')"><small>{{ t('settings.currentStatus',{status:llm.apiKeyMasked}) }}</small></label>
          <label>Temperature<input v-model.number="llm.temperature" type="number" min="0" max="2" step="0.1"></label>
          <label>{{ t('settings.maxTokens') }}<input v-model.number="llm.maxTokens" type="number" min="256" max="32768"></label>
          <label>{{ t('settings.imageModel') }}<input v-model="llm.imageModelName" placeholder="gpt-image-1"><small>{{ t('settings.imageModelHint') }}</small></label>
          <label>{{ t('settings.imageBaseUrl') }}<input v-model="llm.imageBaseUrl" :placeholder="t('settings.reuseBaseUrl')"></label>
          <label class="full">{{ t('settings.imageApiKey') }}<input v-model="llm.imageApiKey" type="password" :placeholder="llm.hasImageApiKey?t('settings.imageKeySaved'):t('settings.reuseApiKey')"><small>{{ t('settings.currentStatus',{status:llm.imageApiKeyMasked}) }}</small></label>
          <label class="checkbox full"><input v-model="llm.enabled" type="checkbox">{{ t('settings.enableAi') }}</label>
          <label v-if="llm.hasApiKey" class="checkbox full danger"><input v-model="llm.clearApiKey" type="checkbox">{{ t('settings.clearApiKey') }}</label>
          <label v-if="llm.hasImageApiKey" class="checkbox full danger"><input v-model="llm.clearImageApiKey" type="checkbox">{{ t('settings.clearImageApiKey') }}</label>
        </div>
        <footer><span><ShieldCheck :size="15"/>{{ t('settings.encryption') }}</span><button class="primary-button" :disabled="saving"><Save :size="16"/>{{saving?t('settings.saving'):t('settings.save')}}</button></footer>
      </form>
      <form class="panel settings-card password-card" @submit.prevent="changePassword">
        <header><span class="settings-icon amber"><KeyRound/></span><div><h3>{{ t('settings.passwordTitle') }}</h3><p>{{ t('settings.passwordDescription') }}</p></div></header>
        <div class="form-grid one-column">
          <label>{{ t('settings.currentPassword') }}<input v-model="password.currentPassword" type="password" required autocomplete="current-password"></label>
          <label>{{ t('settings.newPassword') }}<input v-model="password.newPassword" type="password" required minlength="8" autocomplete="new-password"></label>
          <label>{{ t('settings.confirmPassword') }}<input v-model="password.confirmPassword" type="password" required minlength="8" autocomplete="new-password"></label>
        </div>
        <footer><span>{{ t('settings.passwordHint') }}</span><button class="secondary-button">{{ t('settings.changePassword') }}</button></footer>
      </form>
    </div>
  </section>
</template>
