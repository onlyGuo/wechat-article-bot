<script setup>
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '../api'
import { Plus, Search, FileText, Sparkles, Clock3, ChevronRight, Trash2 } from 'lucide-vue-next'

const router=useRouter(), route=useRoute(), items=ref([]), total=ref(0), status=ref('DRAFT'), keyword=ref(''), accounts=ref([]), accountId=ref(''), loading=ref(true), error=ref('')
async function load(){loading.value=true;try{const q=new URLSearchParams({status:status.value,page:'1',pageSize:'50'});if(keyword.value)q.set('keyword',keyword.value);if(accountId.value)q.set('accountId',accountId.value);const data=await api(`/api/articles?${q}`);items.value=data.items;total.value=data.total}catch(e){error.value=e.message}finally{loading.value=false}}
async function create(){try{const article=await api('/api/articles',{method:'POST',body:JSON.stringify({accountId:accountId.value||null,title:'未命名文章',contentHtml:'<p>从这里开始写作…</p>'})});router.push(`/articles/${article.id}`)}catch(e){error.value=e.message}}
async function remove(article){if(!confirm(`确定删除“${article.title}”吗？`))return;try{await api(`/api/articles/${article.id}`,{method:'DELETE'});load()}catch(e){error.value=e.message}}
const fmt=(v)=>v?new Date(v).toLocaleString('zh-CN',{month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit'}):'—'
let timer;watch([status,accountId],load);watch(keyword,()=>{clearTimeout(timer);timer=setTimeout(load,350)})
onMounted(async()=>{accounts.value=await api('/api/accounts').catch(()=>[]);await load();if(route.query.create)create()})
</script>

<template>
  <section class="page-content articles-page">
    <div class="section-heading"><div><span class="eyebrow">EDITORIAL</span><h2>文章工作区</h2><p>手动编辑或邀请智能体共同完成一篇文章。</p></div><button class="primary-button" @click="create"><Plus :size="17" />新建文章</button></div>
    <div class="filter-bar"><div class="tabs"><button :class="{active:status==='DRAFT'}" @click="status='DRAFT'">草稿</button><button :class="{active:status==='PUBLISHED'}" @click="status='PUBLISHED'">已发布</button></div><div class="filter-spacer"></div><label class="search-input"><Search :size="16" /><input v-model="keyword" placeholder="搜索标题或正文"></label><select v-model="accountId" class="compact-select"><option value="">全部公众号</option><option v-for="a in accounts" :key="a.id" :value="a.id">{{a.name}}</option></select></div>
    <div v-if="error" class="alert error">{{error}}</div>
    <div class="article-list panel">
      <div class="list-head"><span>文章</span><span>来源</span><span>状态</span><span>最后更新</span><span></span></div>
      <button v-for="article in items" :key="article.id" class="article-row" @click="router.push(`/articles/${article.id}`)">
        <div class="article-title-cell"><span class="document-icon"><FileText :size="19" /></span><div><strong>{{article.title}}</strong><p>{{article.digest||article.contentText||'暂无摘要'}}</p></div></div>
        <span class="source-badge"><Sparkles v-if="article.sourceType!=='MANUAL'" :size="14" />{{article.sourceType}}</span>
        <div><span class="status-pill" :class="article.workflowStatus.toLowerCase()">{{article.wechatStatus==='NOT_SYNCED'?'本地草稿':article.wechatStatus}}</span></div>
        <time><Clock3 :size="14" />{{fmt(article.updatedAt)}}</time>
        <span class="row-actions"><button class="icon-button danger-ghost" @click.stop="remove(article)"><Trash2 :size="16" /></button><ChevronRight :size="18" /></span>
      </button>
      <div v-if="!items.length&&!loading" class="empty-state"><FileText :size="32" /><strong>这里还没有文章</strong><p>新建一篇文章，或让定时创作 Agent 按计划完成内容。</p><button class="primary-button" @click="create"><Plus :size="16" />新建文章</button></div>
    </div>
    <p class="result-count">共 {{total}} 篇{{status==='DRAFT'?'草稿':'已发布文章'}}</p>
  </section>
</template>
