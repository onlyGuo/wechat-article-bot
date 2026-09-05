<script setup>
import { onMounted, reactive, ref } from 'vue'
import { api } from '../api'
import { Plus, Radio, CheckCircle2, CircleDashed, MoreHorizontal, X, RefreshCw } from 'lucide-vue-next'

const accounts = ref([]), loading = ref(true), error = ref(''), showForm = ref(false), saving = ref(false)
const empty = () => ({ id:null, name:'', appId:'', appSecret:'', originalId:'', accountType:'SERVICE', verified:true, defaultAuthor:'', defaultStyle:'清爽专业', status:'ACTIVE' })
const form = reactive(empty())
async function load() { loading.value=true; try { accounts.value=await api('/api/accounts') } catch(e){error.value=e.message} finally{loading.value=false} }
function open(account) { Object.assign(form, empty(), account || {}); form.appSecret=''; showForm.value=true }
async function save() { saving.value=true; error.value=''; try { const path=form.id?`/api/accounts/${form.id}`:'/api/accounts'; await api(path,{method:form.id?'PUT':'POST',body:JSON.stringify(form)}); showForm.value=false; await load() } catch(e){error.value=e.message} finally{saving.value=false} }
async function test(account) { error.value=''; try { await api(`/api/accounts/${account.id}/test`,{method:'POST'}); await load() } catch(e){error.value=e.message} }
onMounted(load)
</script>

<template>
  <section class="page-content">
    <div class="section-heading"><div><span class="eyebrow">CHANNELS</span><h2>连接你的内容阵地</h2><p>统一管理公众号凭据、接口能力和默认创作偏好。</p></div><button class="primary-button" @click="open()"><Plus :size="17" />添加公众号</button></div>
    <div v-if="error" class="alert error">{{ error }}</div>
    <div class="account-grid">
      <article v-for="account in accounts" :key="account.id" class="account-card">
        <header><div class="account-avatar">{{ account.name.slice(0,1) }}</div><span class="status-pill" :class="account.connectionStatus.toLowerCase()"><CheckCircle2 v-if="account.connectionStatus==='CONNECTED'" :size="13" /><CircleDashed v-else :size="13" />{{ account.connectionStatus }}</span></header>
        <h3>{{ account.name }}</h3><p>{{ account.accountType === 'SERVICE' ? '服务号' : '订阅号' }} · {{ account.verified ? '已认证' : '未认证' }}</p>
        <dl><div><dt>AppID</dt><dd>{{ account.appId }}</dd></div><div><dt>默认作者</dt><dd>{{ account.defaultAuthor || '未设置' }}</dd></div></dl>
        <div class="capability-row"><span v-for="cap in (account.capabilities||'').split(',')" :key="cap">{{ cap }}</span></div>
        <footer><button class="secondary-button" @click="test(account)"><RefreshCw :size="15" />检测连接</button><button class="icon-button" @click="open(account)"><MoreHorizontal :size="18" /></button></footer>
      </article>
      <button v-if="!accounts.length && !loading" class="add-account-card" @click="open()"><span><Plus /></span><strong>添加第一个公众号</strong><p>填写 AppID 和 AppSecret 开始连接</p></button>
    </div>
    <div v-if="showForm" class="modal-backdrop" @click.self="showForm=false"><form class="modal-card" @submit.prevent="save">
      <header><div><span class="eyebrow">ACCOUNT SETUP</span><h3>{{ form.id ? '编辑公众号' : '添加公众号' }}</h3></div><button type="button" class="icon-button" @click="showForm=false"><X :size="19" /></button></header>
      <div class="form-grid"><label>公众号名称<input v-model="form.name" required placeholder="例如：产品手记"></label><label>账号类型<select v-model="form.accountType"><option value="SERVICE">服务号</option><option value="SUBSCRIPTION">订阅号</option></select></label><label>AppID<input v-model="form.appId" required></label><label>AppSecret<input v-model="form.appSecret" :required="!form.id" type="password" :placeholder="form.id?'留空表示不修改':'请输入 AppSecret'"></label><label>原始 ID<input v-model="form.originalId"></label><label>默认作者<input v-model="form.defaultAuthor"></label><label class="full">默认风格<input v-model="form.defaultStyle"></label></div>
      <div class="form-actions"><button type="button" class="secondary-button" @click="showForm=false">取消</button><button class="primary-button" :disabled="saving">{{ saving?'保存中…':'保存公众号' }}</button></div>
    </form></div>
  </section>
</template>
