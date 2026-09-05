<script setup>
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api'
import { Plus, Bot, Play, Clock3, MoreHorizontal, X, CheckCircle2, AlertCircle, Trash2, LoaderCircle, FileText } from 'lucide-vue-next'
import CronBuilder from '../components/CronBuilder.vue'
import { describeQuartzCron } from '../utils/cron'

const router=useRouter()
const tasks=ref([]),accounts=ref([]),assets=ref([]),error=ref(''),notice=ref(''),showForm=ref(false),showRuns=ref(false),runs=ref([]),selected=ref(null),running=ref(null)
const defaultInstruction=`每天检索并浏览过去24小时内值得关注的 AI 产品与行业动态，优先阅读官方公告和可靠媒体来源。选择一个最适合公众号读者的主题，核实关键事实后，整理成一篇观点清晰、结构完整、适合手机阅读的原创文章。正文末尾列出主要参考来源；需要时从素材库选择或生成合适的封面和正文配图。`
const blank=()=>({id:null,name:'',accountId:null,coverAssetId:null,cronExpression:'0 0 9 * * ?',timezone:'Asia/Shanghai',aiPrompt:defaultInstruction,outputMode:'LOCAL_DRAFT',enabled:true})
const form=reactive(blank())
let runPoller=null
const outputLabel=value=>({LOCAL_DRAFT:'保存本地草稿',WECHAT_DRAFT:'同步微信草稿',AUTO_PUBLISH:'自动发布公众号'}[value]||value)
const fmt=value=>value?new Date(value).toLocaleString('zh-CN'):'尚未运行'

async function load(){try{[tasks.value,accounts.value,assets.value]=await Promise.all([api('/api/tasks'),api('/api/accounts'),api('/api/assets')])}catch(e){error.value=e.message}}
function open(task){Object.assign(form,blank(),task||{});showForm.value=true;error.value=''}
async function save(){try{await api(form.id?`/api/tasks/${form.id}`:'/api/tasks',{method:form.id?'PUT':'POST',body:JSON.stringify(form)});showForm.value=false;notice.value='定时创作任务已保存';await load()}catch(e){error.value=e.message}}
async function run(task){if(running.value)return;running.value=task.id;error.value='';notice.value='';try{const result=await api(`/api/tasks/${task.id}/run`,{method:'POST'});notice.value=`任务已在后台启动，运行记录 #${result.id}`;await load()}catch(e){error.value=e.message}finally{running.value=null}}
function stopRunPolling(){if(runPoller){clearInterval(runPoller);runPoller=null}}
async function refreshRuns(){
  if(!selected.value)return
  try{runs.value=await api(`/api/tasks/${selected.value.id}/runs`);if(!runs.value.some(item=>item.status==='RUNNING'))stopRunPolling()}
  catch(e){error.value=e.message;stopRunPolling()}
}
async function history(task){stopRunPolling();selected.value=task;showRuns.value=true;await refreshRuns();if(runs.value.some(item=>item.status==='RUNNING'))runPoller=setInterval(refreshRuns,3000)}
function closeHistory(){showRuns.value=false;stopRunPolling()}
async function remove(task){if(!confirm(`确定删除任务“${task.name}”吗？`))return;try{await api(`/api/tasks/${task.id}`,{method:'DELETE'});await load()}catch(e){error.value=e.message}}
onMounted(load)
onBeforeUnmount(stopRunPolling)
</script>

<template>
  <section class="page-content">
    <div class="section-heading"><div><span class="eyebrow">AUTONOMOUS AGENTS</span><h2>让智能体按时完成一篇文章</h2><p>按计划启动完整创作 Agent，自主检索、阅读、整理、配图并交付文章。</p></div><button class="primary-button" @click="open()"><Plus :size="17" />新建任务</button></div>
    <div v-if="error" class="alert error">{{error}}</div><div v-if="notice" class="alert task-notice">{{notice}}</div>
    <div class="task-grid">
      <article v-for="task in tasks" :key="task.id" class="task-card">
        <header><span class="task-icon"><Bot :size="20" /></span><div class="task-toggle" :class="{on:task.enabled}"><i></i>{{task.enabled?'运行中':'已停用'}}</div></header>
        <h3>{{task.name}}</h3><p class="task-source">{{task.aiPrompt||'尚未填写创作要求'}}</p>
        <div class="task-schedule"><Clock3 :size="16" /><div><small>{{task.timezone}}</small><strong>{{describeQuartzCron(task.cronExpression)}}</strong><code>{{task.cronExpression}}</code></div></div>
        <dl><div><dt>目标公众号</dt><dd>{{task.accountName||'不指定公众号'}}</dd></div><div><dt>完成动作</dt><dd>{{outputLabel(task.outputMode)}}</dd></div><div><dt>下次执行</dt><dd>{{fmt(task.nextRunAt)}}</dd></div></dl>
        <footer><button class="secondary-button" :disabled="running===task.id" @click="run(task)"><Play :size="15" />{{running===task.id?'启动中…':'立即执行'}}</button><button class="text-button" @click="history(task)">运行记录</button><button class="icon-button" @click="open(task)"><MoreHorizontal :size="17" /></button><button class="icon-button danger-ghost" @click="remove(task)"><Trash2 :size="16" /></button></footer>
      </article>
      <button v-if="!tasks.length" class="add-account-card" @click="open()"><span><Plus /></span><strong>创建第一个定时创作 Agent</strong><p>告诉它何时运行，以及每次需要完成什么文章</p></button>
    </div>

    <div v-if="showForm" class="modal-backdrop" @click.self="showForm=false"><form class="modal-card schedule-modal" @submit.prevent="save"><header><div><span class="eyebrow">SCHEDULED AGENT</span><h3>{{form.id?'编辑定时创作任务':'新建定时创作任务'}}</h3></div><button type="button" class="icon-button" @click="showForm=false"><X :size="19" /></button></header><div class="form-grid">
      <label>任务名称<input v-model="form.name" required placeholder="例如：每日 AI 行业头条"></label><label>目标公众号<select v-model="form.accountId"><option :value="null">不指定，仅保存本地</option><option v-for="account in accounts" :key="account.id" :value="account.id">{{account.name}}</option></select></label>
      <label>执行时区<input v-model="form.timezone" required placeholder="Asia/Shanghai"><small>所有日期和时间均按此时区解释。</small></label><span></span>
      <div class="full schedule-builder-field"><span>执行计划</span><CronBuilder v-model="form.cronExpression" /></div>
      <label>完成后的动作<select v-model="form.outputMode"><option value="LOCAL_DRAFT">保存为本地草稿</option><option value="WECHAT_DRAFT">同步到微信公众号草稿箱</option><option value="AUTO_PUBLISH">自动发布到公众号</option></select></label>
      <label>默认封面（可选）<select v-model="form.coverAssetId"><option :value="null">由 Agent 自行选择或生成</option><option v-for="asset in assets" :key="asset.id" :value="asset.id">{{asset.originalName}}</option></select></label>
      <label class="full">每次执行的完整要求<textarea v-model="form.aiPrompt" required rows="10" placeholder="描述要关注的领域、时间范围、资料要求、读者、文章风格、结构、配图和事实核验要求。"></textarea><small>Agent 会据此自主使用网页搜索、内容浏览、素材库和图片工具，并且每次只创作一篇新文章。</small></label>
      <label class="checkbox full"><input v-model="form.enabled" type="checkbox">保存后启用 Quartz 调度</label>
    </div><div class="form-actions"><button type="button" class="secondary-button" @click="showForm=false">取消</button><button class="primary-button">保存任务</button></div></form></div>

    <div v-if="showRuns" class="modal-backdrop" @click.self="closeHistory"><div class="modal-card wide"><header><div><span class="eyebrow">AGENT RUNS</span><h3>{{selected?.name}}</h3></div><button class="icon-button" @click="closeHistory"><X :size="19" /></button></header><div class="run-list"><div v-for="run in runs" :key="run.id"><CheckCircle2 v-if="run.status==='SUCCESS'" class="success-text"/><LoaderCircle v-else-if="run.status==='RUNNING'" class="spin"/><AlertCircle v-else class="danger-text"/><div><strong>{{run.status}} · {{run.triggerType}}</strong><p>{{run.message||'智能体正在执行研究与创作…'}}</p><small>工具调用 {{run.toolCallCount||0}} 次<span v-if="run.articleId"> · 文章 #{{run.articleId}}</span></small><details v-if="run.executionLog" class="run-log"><summary>查看工具执行日志</summary><pre>{{run.executionLog}}</pre></details></div><div class="run-actions"><button v-if="run.articleId" class="text-button" @click="router.push(`/articles/${run.articleId}`)"><FileText :size="14"/>查看文章</button><time>{{fmt(run.startedAt)}}</time></div></div><div v-if="!runs.length" class="empty-state">暂无运行记录</div></div></div></div>
  </section>
</template>
