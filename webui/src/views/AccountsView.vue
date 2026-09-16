<script setup>
import { onMounted, reactive, ref } from 'vue'
import { api } from '../api'
import { useI18n } from '../i18n'
import { Plus, Radio, CheckCircle2, CircleDashed, MoreHorizontal, X, RefreshCw } from 'lucide-vue-next'

const accounts = ref([]), loading = ref(true), error = ref(''), showForm = ref(false), saving = ref(false)
const { t } = useI18n()
const empty = () => ({ id:null, name:'', appId:'', appSecret:'', originalId:'', accountType:'SERVICE', verified:true, defaultAuthor:'', defaultStyle:t('accounts.defaultStyle'), status:'ACTIVE' })
const form = reactive(empty())
async function load() { loading.value=true; try { accounts.value=await api('/api/accounts') } catch(e){error.value=e.message} finally{loading.value=false} }
function open(account) { Object.assign(form, empty(), account || {}); form.appSecret=''; showForm.value=true }
async function save() { saving.value=true; error.value=''; try { const path=form.id?`/api/accounts/${form.id}`:'/api/accounts'; await api(path,{method:form.id?'PUT':'POST',body:JSON.stringify(form)}); showForm.value=false; await load() } catch(e){error.value=e.message} finally{saving.value=false} }
async function test(account) { error.value=''; try { await api(`/api/accounts/${account.id}/test`,{method:'POST'}); await load() } catch(e){error.value=e.message} }
onMounted(load)
</script>

<template>
  <section class="page-content">
    <div class="section-heading"><div><span class="eyebrow">CHANNELS</span><h2>{{ t('accounts.title') }}</h2><p>{{ t('accounts.description') }}</p></div><button class="primary-button" @click="open()"><Plus :size="17" />{{ t('accounts.add') }}</button></div>
    <div v-if="error" class="alert error">{{ error }}</div>
    <div class="account-grid">
      <article v-for="account in accounts" :key="account.id" class="account-card">
        <header><div class="account-avatar">{{ account.name.slice(0,1) }}</div><span class="status-pill" :class="account.connectionStatus.toLowerCase()"><CheckCircle2 v-if="account.connectionStatus==='CONNECTED'" :size="13" /><CircleDashed v-else :size="13" />{{ account.connectionStatus }}</span></header>
        <h3>{{ account.name }}</h3><p>{{ account.accountType === 'SERVICE' ? t('accounts.service') : t('accounts.subscription') }} · {{ account.verified ? t('accounts.verified') : t('accounts.unverified') }}</p>
        <dl><div><dt>AppID</dt><dd>{{ account.appId }}</dd></div><div><dt>{{ t('accounts.defaultAuthor') }}</dt><dd>{{ account.defaultAuthor || t('accounts.notSet') }}</dd></div></dl>
        <div class="capability-row"><span v-for="cap in (account.capabilities||'').split(',')" :key="cap">{{ cap }}</span></div>
        <footer><button class="secondary-button" @click="test(account)"><RefreshCw :size="15" />{{ t('accounts.testConnection') }}</button><button class="icon-button" @click="open(account)"><MoreHorizontal :size="18" /></button></footer>
      </article>
      <button v-if="!accounts.length && !loading" class="add-account-card" @click="open()"><span><Plus /></span><strong>{{ t('accounts.addFirst') }}</strong><p>{{ t('accounts.addFirstDescription') }}</p></button>
    </div>
    <div v-if="showForm" class="modal-backdrop"><form class="modal-card" @submit.prevent="save">
      <header><div><span class="eyebrow">ACCOUNT SETUP</span><h3>{{ form.id ? t('accounts.edit') : t('accounts.add') }}</h3></div><button type="button" class="icon-button" @click="showForm=false"><X :size="19" /></button></header>
      <div class="form-grid"><label>{{ t('accounts.name') }}<input v-model="form.name" required :placeholder="t('accounts.namePlaceholder')"></label><label>{{ t('accounts.type') }}<select v-model="form.accountType"><option value="SERVICE">{{ t('accounts.service') }}</option><option value="SUBSCRIPTION">{{ t('accounts.subscription') }}</option></select></label><label>AppID<input v-model="form.appId" required></label><label>AppSecret<input v-model="form.appSecret" :required="!form.id" type="password" :placeholder="form.id?t('accounts.keepSecret'):t('accounts.appSecretPlaceholder')"></label><label>{{ t('accounts.originalId') }}<input v-model="form.originalId"></label><label>{{ t('accounts.defaultAuthor') }}<input v-model="form.defaultAuthor"></label><label class="full">{{ t('accounts.defaultStyle') }}<input v-model="form.defaultStyle"></label></div>
      <div class="form-actions"><button type="button" class="secondary-button" @click="showForm=false">{{ t('common.cancel') }}</button><button class="primary-button" :disabled="saving">{{ saving?t('accounts.saving'):t('accounts.save') }}</button></div>
    </form></div>
  </section>
</template>
