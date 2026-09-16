<script setup>
import { onMounted, ref } from 'vue'
import { api, uploadAsset } from '../api'
import { useI18n } from '../i18n'
import { ImagePlus, Upload, Images } from 'lucide-vue-next'
const assets=ref([]),accounts=ref([]),accountId=ref(''),error=ref(''),uploading=ref(false),input=ref()
const { t } = useI18n()
async function load(){try{assets.value=await api(`/api/assets${accountId.value?`?accountId=${accountId.value}`:''}`)}catch(e){error.value=e.message}}
async function upload(e){const file=e.target.files?.[0];if(!file)return;uploading.value=true;try{await uploadAsset(file,accountId.value||null);load()}catch(err){error.value=err.message}finally{uploading.value=false;e.target.value=''}}
onMounted(async()=>{accounts.value=await api('/api/accounts').catch(()=>[]);load()})
</script>
<template><section class="page-content"><div class="section-heading"><div><span class="eyebrow">MEDIA LIBRARY</span><h2>{{ t('assets.title') }}</h2><p>{{ t('assets.description') }}</p></div><button class="primary-button" :disabled="uploading" @click="input.click()"><Upload :size="17"/>{{uploading?t('assets.uploading'):t('common.upload')}}</button><input ref="input" type="file" accept="image/*" hidden @change="upload"></div><div class="filter-bar"><select v-model="accountId" class="compact-select" @change="load"><option value="">{{ t('assets.all') }}</option><option v-for="a in accounts" :key="a.id" :value="a.id">{{a.name}}</option></select><span class="filter-count">{{ t('assets.count',{count:assets.length}) }}</span></div><div v-if="error" class="alert error">{{error}}</div><div class="asset-grid"><article v-for="asset in assets" :key="asset.id"><div class="asset-image"><img :src="asset.publicUrl" :alt="asset.originalName"><span v-if="asset.wechatMediaId">{{ t('assets.wechat') }}</span></div><strong>{{asset.originalName}}</strong><p>{{(asset.fileSize/1024).toFixed(1)}} KB · {{asset.contentType}}</p></article><button class="asset-upload-card" @click="input.click()"><ImagePlus/><strong>{{ t('assets.add') }}</strong><p>{{ t('assets.addDescription') }}</p></button></div></section></template>
