<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { api } from '../api'
import { Plus, Sparkles, Pencil, Trash2, X, Star, Search } from 'lucide-vue-next'
const skills=ref([]), query=ref(''), error=ref(''), showForm=ref(false), saving=ref(false), viewing=ref(false)
const form=reactive({id:null,name:'',description:'',content:''})
const filtered=computed(()=>skills.value.filter(skill=>`${skill.name} ${skill.description} ${skill.content}`.toLowerCase().includes(query.value.toLowerCase())))
async function load(){try{skills.value=await api('/api/skills')}catch(e){error.value=e.message}}
function open(skill,readOnly=false){Object.assign(form,{id:null,name:'',description:'',content:''},skill||{});viewing.value=readOnly;showForm.value=true;error.value=''}
async function save(){if(saving.value)return;saving.value=true;try{await api(form.id?`/api/skills/${form.id}`:'/api/skills',{method:form.id?'PUT':'POST',body:JSON.stringify(form)});showForm.value=false;await load()}catch(e){error.value=e.message}finally{saving.value=false}}
async function makeDefault(skill){try{await api(`/api/skills/${skill.id}/default`,{method:'POST'});await load()}catch(e){error.value=e.message}}
async function remove(skill){if(!confirm(`删除“${skill.name}”？引用它的文章和定时任务将使用当前默认 Skill，已有正文不受影响。`))return;try{await api(`/api/skills/${skill.id}`,{method:'DELETE'});await load()}catch(e){error.value=e.message}}
onMounted(load)
</script>

<template>
  <section class="page-content">
    <div class="section-heading"><div><span class="eyebrow">ARTICLE SKILLS</span><h2>定义每一种文章的表达与样式</h2><p>把写作风格、内容结构、排版和视觉规范保存为 Skill，在文章与定时任务中使用。</p></div><button class="primary-button" @click="open()"><Plus :size="17"/>新建 Skill</button></div>
    <div v-if="error&&!showForm" class="alert error">{{error}}</div>
    <label class="skill-search"><Search :size="17"/><input v-model="query" placeholder="搜索 Skill 名称或内容" aria-label="搜索 Skill"></label>
    <div class="skill-grid">
      <article v-for="skill in filtered" :key="skill.id" class="skill-card">
        <header><span class="skill-icon"><Sparkles :size="20"/></span><span v-if="skill.isDefault" class="skill-default"><Star :size="13"/>默认</span></header>
        <button class="skill-title" @click="open(skill,true)">{{skill.name}}</button><p>{{skill.description||'暂无说明'}}</p><pre>{{skill.content}}</pre>
        <footer><button class="text-button" @click="open(skill,true)">查看</button><button class="text-button" @click="open(skill)"><Pencil :size="14"/>编辑</button><button v-if="!skill.isDefault" class="text-button" @click="makeDefault(skill)">设为默认</button><button class="icon-button danger-ghost" :disabled="skill.isDefault" :title="skill.isDefault?'请先设置其他默认 Skill':'删除 Skill'" @click="remove(skill)"><Trash2 :size="16"/></button></footer>
      </article>
    </div>
    <div v-if="!filtered.length" class="empty-state">没有匹配的 Skill</div>
    <div v-if="showForm" class="modal-backdrop" @click.self="showForm=false"><form class="modal-card wide skill-modal" @submit.prevent="save"><header><h3>{{viewing?'查看 Skill':form.id?'编辑 Skill':'新建 Skill'}}</h3><button type="button" class="icon-button" @click="showForm=false"><X :size="19"/></button></header><div v-if="error" class="alert error">{{error}}</div><div class="form-grid">
      <label class="full">名称<input v-model="form.name" required maxlength="255" :readonly="viewing" placeholder="例如：极简科技、杂志专栏、故事随笔"></label>
      <label class="full">说明<input v-model="form.description" maxlength="1000" :readonly="viewing" placeholder="适用主题、读者或使用场景"></label>
      <label class="full">Skill 内容<textarea v-model="form.content" required maxlength="60000" rows="18" :readonly="viewing" placeholder="自由描述语气、写作风格、内容组织、排版、颜色、字体与样式；可直接加入 HTML/CSS 示例。"></textarea><small>支持自由文本、Markdown 和 HTML/CSS 示例。修改后用于后续 AI 创作；已有正文不会自动重排。</small></label>
    </div><div class="form-actions"><button type="button" class="secondary-button" @click="showForm=false">关闭</button><button v-if="viewing" type="button" class="primary-button" @click="viewing=false">编辑 Skill</button><button v-else class="primary-button" :disabled="saving">{{saving?'保存中…':'保存 Skill'}}</button></div></form></div>
  </section>
</template>

<style scoped>
.skill-search{display:flex;align-items:center;gap:10px;background:white;border:1px solid #e5e7eb;border-radius:9px;padding:8px 12px;max-width:450px;margin-bottom:24px;color:#888}.skill-search input{border:0;outline:none;width:100%;padding:4px}.skill-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(300px,1fr));gap:20px}.skill-card{background:white;border:1px solid #e6e9e7;border-radius:14px;padding:24px}.skill-card header,.skill-card footer{display:flex;align-items:center;gap:12px}.skill-card header{justify-content:space-between;margin-bottom:18px}.skill-icon{background:#edf8f1;color:#259565;padding:10px;border-radius:12px}.skill-default{display:flex;align-items:center;gap:5px;color:#168455;font-size:12px}.skill-title{border:0;background:none;font:inherit;font-size:18px;font-weight:600;text-align:left;cursor:pointer;padding:0}.skill-card p{font-size:13px;color:#888;min-height:20px}.skill-card pre{white-space:pre-wrap;font:12px/1.8 inherit;display:-webkit-box;-webkit-line-clamp:5;-webkit-box-orient:vertical;overflow:hidden;height:110px;color:#777;background:#f8faf9;padding:12px;border-radius:8px}.skill-card footer{margin-top:18px;flex-wrap:wrap}.skill-card footer .icon-button{margin-left:auto}.skill-modal textarea{font:13px/1.7 ui-monospace,monospace;resize:vertical}
</style>
