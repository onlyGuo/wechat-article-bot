<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api'
import { FileText, Send, Bot, Users, Plus, ArrowUpRight, Clock3, CircleAlert } from 'lucide-vue-next'

const router = useRouter()
const data = ref({ accounts: 0, drafts: 0, published: 0, failed: 0, activeTasks: 0, followers: 0, aiTokens: 0, recentRuns: [] })
const loading = ref(true)
onMounted(async () => { try { data.value = await api('/api/dashboard') } finally { loading.value = false } })
const formatTime = (value) => value ? new Date(value).toLocaleString('zh-CN', { month:'numeric', day:'numeric', hour:'2-digit', minute:'2-digit' }) : '—'
</script>

<template>
  <section class="page-content dashboard-page">
    <div class="hero-panel">
      <div><span class="eyebrow">CONTENT OVERVIEW</span><h2>从灵感到发布，<br>今天的内容进度一目了然。</h2>
        <p>你有 <strong>{{ data.drafts }}</strong> 篇草稿等待完善，<strong>{{ data.activeTasks }}</strong> 个自动化任务正在工作。</p>
        <button class="primary-button" @click="router.push('/articles?create=1')"><Plus :size="17" />新建文章</button>
      </div>
      <div class="hero-stat"><span>累计发布</span><strong>{{ data.published }}</strong><small>篇公众号文章</small></div>
    </div>
    <div class="metric-grid">
      <article class="metric-card"><span class="metric-icon green"><FileText /></span><div><small>草稿文章</small><strong>{{ data.drafts }}</strong><p>持续打磨中的内容</p></div></article>
      <article class="metric-card"><span class="metric-icon blue"><Send /></span><div><small>已发布</small><strong>{{ data.published }}</strong><p>已成功抵达读者</p></div></article>
      <article class="metric-card"><span class="metric-icon amber"><Bot /></span><div><small>运行中任务</small><strong>{{ data.activeTasks }}</strong><p>{{ data.aiTokens.toLocaleString() }} AI Tokens</p></div></article>
      <article class="metric-card"><span class="metric-icon rose"><Users /></span><div><small>公众号用户</small><strong>{{ data.followers }}</strong><p>来自 {{ data.accounts }} 个公众号</p></div></article>
    </div>
    <div class="dashboard-grid">
      <article class="panel activity-panel">
        <header><div><span class="eyebrow">AUTOMATION</span><h3>最近任务运行</h3></div><button class="text-button" @click="router.push('/tasks')">查看全部<ArrowUpRight :size="16" /></button></header>
        <div v-if="!data.recentRuns.length" class="empty-state"><Bot :size="30" /><strong>还没有任务运行记录</strong><p>创建一个定时创作任务，让智能体按计划完成文章。</p></div>
        <div v-else class="activity-list">
          <div v-for="run in data.recentRuns" :key="run.id" class="activity-row">
            <span class="run-dot" :class="run.status.toLowerCase()"></span>
            <div><strong>任务 #{{ run.taskId }}</strong><p>{{ run.message || '正在执行内容流水线' }}</p></div>
            <span class="status-pill" :class="run.status.toLowerCase()">{{ run.status }}</span>
            <time><Clock3 :size="14" />{{ formatTime(run.startedAt) }}</time>
          </div>
        </div>
      </article>
      <aside class="panel pulse-panel">
        <span class="eyebrow">SYSTEM PULSE</span><h3>工作流健康度</h3>
        <div class="health-ring"><strong>{{ data.failed ? 82 : 100 }}</strong><span>分</span></div>
        <div class="health-item"><span>公众号连接</span><strong>{{ data.accounts }} 个</strong></div>
        <div class="health-item"><span>发布异常</span><strong :class="{ danger: data.failed }">{{ data.failed }} 个</strong></div>
        <p v-if="data.failed" class="health-tip"><CircleAlert :size="16" />有文章需要处理，请检查微信接口记录。</p>
        <p v-else class="health-tip success">所有核心流程运行正常。</p>
      </aside>
    </div>
  </section>
</template>
