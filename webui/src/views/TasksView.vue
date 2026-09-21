<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api'
import { Plus, Bot, Play, Clock3, MoreHorizontal, X, CheckCircle2, AlertCircle, Trash2, LoaderCircle, FileText } from 'lucide-vue-next'
import CronBuilder from '../components/CronBuilder.vue'
import { describeQuartzCron } from '../utils/cron'
import { useI18n } from '../i18n'

const router=useRouter()
const skills=ref([]),tasks=ref([]),accounts=ref([]),assets=ref([]),error=ref(''),notice=ref(''),showForm=ref(false),showRuns=ref(false),runs=ref([]),selected=ref(null),running=ref(null)
const { t, locale } = useI18n()
const blank=()=>({id:null,name:'',accountId:null,skillId:null,classpathResources:null,coverAssetId:null,cronExpression:'0 0 9 * * ?',timezone:'Asia/Shanghai',aiPrompt:t('tasks.defaultInstruction'),outputMode:'LOCAL_DRAFT',enabled:true})
const form=reactive(blank())
const skillOptionValue=skill=>skill.classpathResources?`builtin:${skill.classpathResources}`:`user:${skill.id}`
const skillSelection=computed({
  get(){if(form.classpathResources)return `builtin:${form.classpathResources}`;if(form.skillId!=null)return `user:${form.skillId}`;return ''},
  set(value){form.skillId=value.startsWith('user:')?Number(value.slice(5)):null;form.classpathResources=value.startsWith('builtin:')?value.slice(8):null}
})
const selectedSkillMissing=computed(()=>skillSelection.value&&!skills.value.some(skill=>skillOptionValue(skill)===skillSelection.value))
let runPoller=null
const outputLabel=value=>({LOCAL_DRAFT:t('tasks.localDraft'),WECHAT_DRAFT:t('tasks.wechatDraft'),AUTO_PUBLISH:t('tasks.autoPublish')}[value]||value)
const fmt=value=>value?new Date(value).toLocaleString(locale.value):t('tasks.notRunYet')

async function load(){try{[tasks.value,accounts.value,assets.value,skills.value]=await Promise.all([api('/api/tasks'),api('/api/accounts'),api('/api/assets'),api('/api/skills')])}catch(e){error.value=e.message}}
function open(task){Object.assign(form,blank(),task||{});showForm.value=true;error.value=''}
async function save(){try{await api(form.id?`/api/tasks/${form.id}`:'/api/tasks',{method:form.id?'PUT':'POST',body:JSON.stringify(form)});showForm.value=false;notice.value=t('tasks.saved');await load()}catch(e){error.value=e.message}}
async function run(task){if(running.value)return;running.value=task.id;error.value='';notice.value='';try{const result=await api(`/api/tasks/${task.id}/run`,{method:'POST'});notice.value=t('tasks.started',{id:result.id});await load()}catch(e){error.value=e.message}finally{running.value=null}}
function stopRunPolling(){if(runPoller){clearInterval(runPoller);runPoller=null}}
async function refreshRuns(){
  if(!selected.value)return
  try{runs.value=await api(`/api/tasks/${selected.value.id}/runs`);if(!runs.value.some(item=>item.status==='RUNNING'))stopRunPolling()}
  catch(e){error.value=e.message;stopRunPolling()}
}
async function history(task){stopRunPolling();selected.value=task;showRuns.value=true;await refreshRuns();if(runs.value.some(item=>item.status==='RUNNING'))runPoller=setInterval(refreshRuns,3000)}
function closeHistory(){showRuns.value=false;stopRunPolling()}
async function remove(task){if(!confirm(t('tasks.deleteConfirm',{name:task.name})))return;try{await api(`/api/tasks/${task.id}`,{method:'DELETE'});await load()}catch(e){error.value=e.message}}
onMounted(load)
onBeforeUnmount(stopRunPolling)
</script>

<template>
  <section class="page-content">
    <div class="section-heading"><div><span class="eyebrow">AUTONOMOUS AGENTS</span><h2>{{ t('tasks.title') }}</h2><p>{{ t('tasks.description') }}</p></div><button class="primary-button" @click="open()"><Plus :size="17" />{{ t('tasks.new') }}</button></div>
    <div v-if="error" class="alert error">{{error}}</div><div v-if="notice" class="alert task-notice">{{notice}}</div>
    <div class="task-grid">
      <article v-for="task in tasks" :key="task.id" class="task-card">
        <header><span class="task-icon"><Bot :size="20" /></span><div class="task-toggle" :class="{on:task.enabled}"><i></i>{{task.enabled?t('tasks.running'):t('tasks.stopped')}}</div></header>
        <h3>{{task.name}}</h3><p class="task-source">{{task.aiPrompt||t('tasks.noInstruction')}}</p>
        <div class="task-schedule"><Clock3 :size="16" /><div><small>{{task.timezone}}</small><strong>{{describeQuartzCron(task.cronExpression)}}</strong><code>{{task.cronExpression}}</code></div></div>
        <dl><div><dt>{{ t('tasks.targetAccount') }}</dt><dd>{{task.accountName||t('tasks.noTarget')}}</dd></div><div><dt>{{ t('tasks.output') }}</dt><dd>{{outputLabel(task.outputMode)}}</dd></div><div><dt>{{ t('tasks.nextRun') }}</dt><dd>{{fmt(task.nextRunAt)}}</dd></div></dl>
        <footer><button class="secondary-button" :disabled="running===task.id" @click="run(task)"><Play :size="15" />{{running===task.id?t('tasks.starting'):t('tasks.runNow')}}</button><button class="text-button" @click="history(task)">{{ t('tasks.history') }}</button><button class="icon-button" @click="open(task)"><MoreHorizontal :size="17" /></button><button class="icon-button danger-ghost" @click="remove(task)"><Trash2 :size="16" /></button></footer>
      </article>
      <button v-if="!tasks.length" class="add-account-card" @click="open()"><span><Plus /></span><strong>{{ t('tasks.addFirst') }}</strong><p>{{ t('tasks.addFirstDescription') }}</p></button>
    </div>

    <div v-if="showForm" class="modal-backdrop"><form class="modal-card schedule-modal" @submit.prevent="save"><header><div><span class="eyebrow">SCHEDULED AGENT</span><h3>{{form.id?t('tasks.edit'):t('tasks.new')}}</h3></div><button type="button" class="icon-button" @click="showForm=false"><X :size="19" /></button></header><div class="form-grid">
      <label>{{ t('tasks.name') }}<input v-model="form.name" required :placeholder="t('tasks.namePlaceholder')"></label><label>{{ t('tasks.targetAccount') }}<select v-model="form.accountId"><option :value="null">{{ t('tasks.localOnly') }}</option><option v-for="account in accounts" :key="account.id" :value="account.id">{{account.name}}</option></select></label>
      <label>{{ t('tasks.timezone') }}<input v-model="form.timezone" required placeholder="Asia/Shanghai"><small>{{ t('tasks.timezoneHint') }}</small></label><span></span>
      <div class="full schedule-builder-field"><span>{{ t('tasks.schedule') }}</span><CronBuilder v-model="form.cronExpression" /></div>
      <label>{{ t('tasks.outputAfter') }}<select v-model="form.outputMode"><option value="LOCAL_DRAFT">{{ t('tasks.localDraft') }}</option><option value="WECHAT_DRAFT">{{ t('tasks.wechatDraft') }}</option><option value="AUTO_PUBLISH">{{ t('tasks.autoPublish') }}</option></select></label>
      <label>{{ t('tasks.cover') }}<select v-model="form.coverAssetId"><option :value="null">{{ t('tasks.agentSelectsCover') }}</option><option v-for="asset in assets" :key="asset.id" :value="asset.id">{{asset.originalName}}</option></select></label>
      <label class="full">{{ t('tasks.articleSkill') }}<select v-model="skillSelection"><option value="">{{ t('tasks.followDefaultSkill',{name:skills.find(s=>s.isDefault)?.name||t('common.default')}) }}</option><option v-if="selectedSkillMissing" :value="skillSelection">{{ t('tasks.deletedSkill') }}</option><option v-for="skill in skills" :key="skill.classpathResources||skill.id" :value="skillOptionValue(skill)">{{skill.name}}</option></select><small>{{ t('tasks.skillHint') }}</small></label>
      <label class="full">{{ t('tasks.instruction') }}<textarea v-model="form.aiPrompt" required rows="10" :placeholder="t('tasks.instructionPlaceholder')"></textarea><small>{{ t('tasks.instructionHint') }}</small></label>
      <label class="checkbox full"><input v-model="form.enabled" type="checkbox">{{ t('tasks.enableAfterSave') }}</label>
    </div><div class="form-actions"><button type="button" class="secondary-button" @click="showForm=false">{{ t('common.cancel') }}</button><button class="primary-button">{{ t('tasks.save') }}</button></div></form></div>

    <div v-if="showRuns" class="modal-backdrop"><div class="modal-card wide"><header><div><span class="eyebrow">AGENT RUNS</span><h3>{{selected?.name}}</h3></div><button class="icon-button" @click="closeHistory"><X :size="19" /></button></header><div class="run-list"><div v-for="run in runs" :key="run.id"><CheckCircle2 v-if="run.status==='SUCCESS'" class="success-text"/><LoaderCircle v-else-if="run.status==='RUNNING'" class="spin"/><AlertCircle v-else class="danger-text"/><div><strong>{{run.status}} · {{run.triggerType}}</strong><p>{{run.message||t('tasks.agentRunning')}}</p><small>{{ t('tasks.toolCalls',{count:run.toolCallCount||0}) }}<span v-if="run.articleId">{{ t('tasks.articleNumber',{id:run.articleId}) }}</span></small><details v-if="run.executionLog" class="run-log"><summary>{{ t('tasks.viewLog') }}</summary><pre>{{run.executionLog}}</pre></details></div><div class="run-actions"><button v-if="run.articleId" class="text-button" @click="router.push(`/articles/${run.articleId}`)"><FileText :size="14"/>{{ t('tasks.viewArticle') }}</button><time>{{fmt(run.startedAt)}}</time></div></div><div v-if="!runs.length" class="empty-state">{{ t('tasks.noHistory') }}</div></div></div></div>
  </section>
</template>
