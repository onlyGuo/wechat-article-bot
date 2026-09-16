<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { api } from '../api'
import { useI18n } from '../i18n'
import { Plus, Sparkles, Pencil, Trash2, X, Star, Search } from 'lucide-vue-next'
const skills=ref([]), query=ref(''), error=ref(''), showForm=ref(false), saving=ref(false), viewing=ref(false)
const { t } = useI18n()
const form=reactive({id:null,name:'',description:'',content:''})
const filtered=computed(()=>skills.value.filter(skill=>`${skill.name} ${skill.description} ${skill.content}`.toLowerCase().includes(query.value.toLowerCase())))
async function load(){try{skills.value=await api('/api/skills')}catch(e){error.value=e.message}}
function open(skill,readOnly=false){Object.assign(form,{id:null,name:'',description:'',content:''},skill||{});viewing.value=readOnly;showForm.value=true;error.value=''}
async function save(){if(saving.value)return;saving.value=true;try{await api(form.id?`/api/skills/${form.id}`:'/api/skills',{method:form.id?'PUT':'POST',body:JSON.stringify(form)});showForm.value=false;await load()}catch(e){error.value=e.message}finally{saving.value=false}}
async function makeDefault(skill){try{await api(`/api/skills/${skill.id}/default`,{method:'POST'});await load()}catch(e){error.value=e.message}}
async function remove(skill){if(!confirm(t('skills.deleteConfirm',{name:skill.name})))return;try{await api(`/api/skills/${skill.id}`,{method:'DELETE'});await load()}catch(e){error.value=e.message}}
onMounted(load)
</script>

<template>
  <section class="page-content">
    <div class="section-heading"><div><span class="eyebrow">ARTICLE SKILLS</span><h2>{{ t('skills.title') }}</h2><p>{{ t('skills.description') }}</p></div><button class="primary-button" @click="open()"><Plus :size="17"/>{{ t('skills.new') }}</button></div>
    <div v-if="error&&!showForm" class="alert error">{{error}}</div>
    <label class="skill-search"><Search :size="17"/><input v-model="query" :placeholder="t('skills.searchPlaceholder')" :aria-label="t('skills.searchPlaceholder')"></label>
    <div class="skill-grid">
      <article v-for="skill in filtered" :key="skill.id" class="skill-card">
        <header><span class="skill-icon"><Sparkles :size="20"/></span><span v-if="skill.isDefault" class="skill-default"><Star :size="13"/>{{ t('skills.default') }}</span></header>
        <button class="skill-title" @click="open(skill,true)">{{skill.name}}</button><p>{{skill.description||t('skills.noDescription')}}</p><pre>{{skill.content}}</pre>
        <footer><button class="text-button" @click="open(skill,true)">{{ t('common.view') }}</button><button class="text-button" @click="open(skill)"><Pencil :size="14"/>{{ t('common.edit') }}</button><button v-if="!skill.isDefault" class="text-button" @click="makeDefault(skill)">{{ t('skills.setDefault') }}</button><button class="icon-button danger-ghost" :disabled="skill.isDefault" :title="skill.isDefault?t('skills.defaultDeleteHint'):t('skills.removeTitle')" @click="remove(skill)"><Trash2 :size="16"/></button></footer>
      </article>
    </div>
    <div v-if="!filtered.length" class="empty-state">{{ t('skills.empty') }}</div>
    <div v-if="showForm" class="modal-backdrop"><form class="modal-card wide skill-modal" @submit.prevent="save"><header><h3>{{viewing?t('skills.view'):form.id?t('skills.edit'):t('skills.new')}}</h3><button type="button" class="icon-button" @click="showForm=false"><X :size="19"/></button></header><div v-if="error" class="alert error">{{error}}</div><div class="form-grid">
      <label class="full">{{ t('skills.name') }}<input v-model="form.name" required maxlength="255" :readonly="viewing" :placeholder="t('skills.namePlaceholder')"></label>
      <label class="full">{{ t('skills.descriptionLabel') }}<input v-model="form.description" maxlength="1000" :readonly="viewing" :placeholder="t('skills.descriptionPlaceholder')"></label>
      <label class="full">{{ t('skills.content') }}<textarea v-model="form.content" required maxlength="60000" rows="18" :readonly="viewing" :placeholder="t('skills.contentPlaceholder')"></textarea><small>{{ t('skills.contentHint') }}</small></label>
    </div><div class="form-actions"><button type="button" class="secondary-button" @click="showForm=false">{{ t('common.close') }}</button><button v-if="viewing" type="button" class="primary-button" @click="viewing=false">{{ t('skills.edit') }}</button><button v-else class="primary-button" :disabled="saving">{{saving?t('common.saving'):t('common.save')}} Skill</button></div></form></div>
  </section>
</template>

<style scoped>
.skill-search{display:flex;align-items:center;gap:10px;background:white;border:1px solid #e5e7eb;border-radius:9px;padding:8px 12px;max-width:450px;margin-bottom:24px;color:#888}.skill-search input{border:0;outline:none;width:100%;padding:4px}.skill-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(300px,1fr));gap:20px}.skill-card{background:white;border:1px solid #e6e9e7;border-radius:14px;padding:24px}.skill-card header,.skill-card footer{display:flex;align-items:center;gap:12px}.skill-card header{justify-content:space-between;margin-bottom:18px}.skill-icon{background:#edf8f1;color:#259565;padding:10px;border-radius:12px}.skill-default{display:flex;align-items:center;gap:5px;color:#168455;font-size:12px}.skill-title{border:0;background:none;font:inherit;font-size:18px;font-weight:600;text-align:left;cursor:pointer;padding:0}.skill-card p{font-size:13px;color:#888;min-height:20px}.skill-card pre{white-space:pre-wrap;font:12px/1.8 inherit;display:-webkit-box;-webkit-line-clamp:5;-webkit-box-orient:vertical;overflow:hidden;height:110px;color:#777;background:#f8faf9;padding:12px;border-radius:8px}.skill-card footer{margin-top:18px;flex-wrap:wrap}.skill-card footer .icon-button{margin-left:auto}.skill-modal textarea{font:13px/1.7 ui-monospace,monospace;resize:vertical}
</style>
