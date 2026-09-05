<script setup>
import { onMounted, ref } from 'vue'
import { api, uploadAsset } from '../api'
import { ImagePlus, Upload, Images } from 'lucide-vue-next'
const assets=ref([]),accounts=ref([]),accountId=ref(''),error=ref(''),uploading=ref(false),input=ref()
async function load(){try{assets.value=await api(`/api/assets${accountId.value?`?accountId=${accountId.value}`:''}`)}catch(e){error.value=e.message}}
async function upload(e){const file=e.target.files?.[0];if(!file)return;uploading.value=true;try{await uploadAsset(file,accountId.value||null);load()}catch(err){error.value=err.message}finally{uploading.value=false;e.target.value=''}}
onMounted(async()=>{accounts.value=await api('/api/accounts').catch(()=>[]);load()})
</script>
<template><section class="page-content"><div class="section-heading"><div><span class="eyebrow">MEDIA LIBRARY</span><h2>文章素材库</h2><p>集中管理封面和正文图片，发布时自动转换为微信素材。</p></div><button class="primary-button" :disabled="uploading" @click="input.click()"><Upload :size="17"/>{{uploading?'上传中…':'上传图片'}}</button><input ref="input" type="file" accept="image/*" hidden @change="upload"></div><div class="filter-bar"><select v-model="accountId" class="compact-select" @change="load"><option value="">全部素材</option><option v-for="a in accounts" :key="a.id" :value="a.id">{{a.name}}</option></select><span class="filter-count">{{assets.length}} 个素材</span></div><div v-if="error" class="alert error">{{error}}</div><div class="asset-grid"><article v-for="asset in assets" :key="asset.id"><div class="asset-image"><img :src="asset.publicUrl" :alt="asset.originalName"><span v-if="asset.wechatMediaId">微信素材</span></div><strong>{{asset.originalName}}</strong><p>{{(asset.fileSize/1024).toFixed(1)}} KB · {{asset.contentType}}</p></article><button class="asset-upload-card" @click="input.click()"><ImagePlus/><strong>添加图片素材</strong><p>支持 JPG、PNG、GIF、WebP</p></button></div></section></template>
