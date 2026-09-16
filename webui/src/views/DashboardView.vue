<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api'
import { useI18n } from '../i18n'
import { FileText, Send, Bot, Users, Plus, ArrowUpRight, Clock3, CircleAlert } from 'lucide-vue-next'

const router = useRouter()
const data = ref({ accounts: 0, drafts: 0, published: 0, failed: 0, activeTasks: 0, followers: 0, aiTokens: 0, recentRuns: [] })
const loading = ref(true)
const { t, locale } = useI18n()
onMounted(async () => { try { data.value = await api('/api/dashboard') } finally { loading.value = false } })
const formatTime = (value) => value ? new Date(value).toLocaleString(locale.value, { month:'numeric', day:'numeric', hour:'2-digit', minute:'2-digit' }) : t('common.none')
</script>

<template>
  <section class="page-content dashboard-page">
    <div class="hero-panel">
      <div><span class="eyebrow">CONTENT OVERVIEW</span><h2>{{ t('dashboard.title') }}</h2>
        <p>{{ t('dashboard.summary',{drafts:data.drafts,tasks:data.activeTasks}) }}</p>
        <button class="primary-button" @click="router.push('/articles?create=1')"><Plus :size="17" />{{ t('dashboard.newArticle') }}</button>
      </div>
      <div class="hero-stat"><span>{{ t('dashboard.totalPublished') }}</span><strong>{{ data.published }}</strong><small>{{ t('dashboard.wechatArticles') }}</small></div>
    </div>
    <div class="metric-grid">
      <article class="metric-card"><span class="metric-icon green"><FileText /></span><div><small>{{ t('dashboard.draftArticles') }}</small><strong>{{ data.drafts }}</strong><p>{{ t('dashboard.draftDescription') }}</p></div></article>
      <article class="metric-card"><span class="metric-icon blue"><Send /></span><div><small>{{ t('dashboard.published') }}</small><strong>{{ data.published }}</strong><p>{{ t('dashboard.publishedDescription') }}</p></div></article>
      <article class="metric-card"><span class="metric-icon amber"><Bot /></span><div><small>{{ t('dashboard.activeTasks') }}</small><strong>{{ data.activeTasks }}</strong><p>{{ data.aiTokens.toLocaleString(locale) }} AI Tokens</p></div></article>
      <article class="metric-card"><span class="metric-icon rose"><Users /></span><div><small>{{ t('dashboard.followers') }}</small><strong>{{ data.followers }}</strong><p>{{ t('dashboard.fromAccounts',{count:data.accounts}) }}</p></div></article>
    </div>
    <div class="dashboard-grid">
      <article class="panel activity-panel">
        <header><div><span class="eyebrow">AUTOMATION</span><h3>{{ t('dashboard.recentRuns') }}</h3></div><button class="text-button" @click="router.push('/tasks')">{{ t('dashboard.viewAll') }}<ArrowUpRight :size="16" /></button></header>
        <div v-if="!data.recentRuns.length" class="empty-state"><Bot :size="30" /><strong>{{ t('dashboard.noRuns') }}</strong><p>{{ t('dashboard.noRunsDescription') }}</p></div>
        <div v-else class="activity-list">
          <div v-for="run in data.recentRuns" :key="run.id" class="activity-row">
            <span class="run-dot" :class="run.status.toLowerCase()"></span>
            <div><strong>{{ t('dashboard.taskNumber',{id:run.taskId}) }}</strong><p>{{ run.message || t('dashboard.runningPipeline') }}</p></div>
            <span class="status-pill" :class="run.status.toLowerCase()">{{ run.status }}</span>
            <time><Clock3 :size="14" />{{ formatTime(run.startedAt) }}</time>
          </div>
        </div>
      </article>
      <aside class="panel pulse-panel">
        <span class="eyebrow">SYSTEM PULSE</span><h3>{{ t('dashboard.health') }}</h3>
        <div class="health-ring"><strong>{{ data.failed ? 82 : 100 }}</strong><span>{{ t('dashboard.points') }}</span></div>
        <div class="health-item"><span>{{ t('dashboard.connections') }}</span><strong>{{ t('dashboard.connectionCount',{count:data.accounts}) }}</strong></div>
        <div class="health-item"><span>{{ t('dashboard.publishFailures') }}</span><strong :class="{ danger: data.failed }">{{ data.failed }}</strong></div>
        <p v-if="data.failed" class="health-tip"><CircleAlert :size="16" />{{ t('dashboard.needsAttention') }}</p>
        <p v-else class="health-tip success">{{ t('dashboard.healthy') }}</p>
      </aside>
    </div>
  </section>
</template>
