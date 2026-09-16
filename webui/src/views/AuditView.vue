<script setup>
import { onMounted, ref } from 'vue'
import { api } from '../api'
import { useI18n } from '../i18n'
import { ShieldCheck } from 'lucide-vue-next'
const logs=ref([]),error=ref('');onMounted(async()=>{try{logs.value=await api('/api/audit')}catch(e){error.value=e.message}})
const { t, locale } = useI18n()
</script>
<template><section class="page-content"><div class="section-heading"><div><span class="eyebrow">AUDIT TRAIL</span><h2>{{ t('audit.title') }}</h2><p>{{ t('audit.description') }}</p></div></div><div v-if="error" class="alert error">{{error}}</div><div class="timeline panel"><div v-for="log in logs" :key="log.id" class="timeline-row"><span><ShieldCheck :size="16"/></span><div><strong>{{log.username||t('audit.system')}} · {{log.action}} {{log.resourceType}}</strong><p>{{log.resourceId}} · {{log.detail}}</p></div><time>{{new Date(log.createdAt).toLocaleString(locale)}}</time></div><div v-if="!logs.length" class="empty-state"><ShieldCheck/><strong>{{ t('audit.empty') }}</strong><p>{{ t('audit.emptyDescription') }}</p></div></div></section></template>
