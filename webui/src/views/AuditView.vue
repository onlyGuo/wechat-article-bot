<script setup>
import { onMounted, ref } from 'vue'
import { api } from '../api'
import { ShieldCheck } from 'lucide-vue-next'
const logs=ref([]),error=ref('');onMounted(async()=>{try{logs.value=await api('/api/audit')}catch(e){error.value=e.message}})
</script>
<template><section class="page-content"><div class="section-heading"><div><span class="eyebrow">AUDIT TRAIL</span><h2>每一次变更都有迹可循</h2><p>记录文章、任务、公众号和用户的关键操作。</p></div></div><div v-if="error" class="alert error">{{error}}</div><div class="timeline panel"><div v-for="log in logs" :key="log.id" class="timeline-row"><span><ShieldCheck :size="16"/></span><div><strong>{{log.username||'系统'}} · {{log.action}} {{log.resourceType}}</strong><p>{{log.resourceId}} · {{log.detail}}</p></div><time>{{new Date(log.createdAt).toLocaleString('zh-CN')}}</time></div><div v-if="!logs.length" class="empty-state"><ShieldCheck/><strong>暂无审计记录</strong><p>后续关键操作会自动出现在这里。</p></div></div></section></template>
