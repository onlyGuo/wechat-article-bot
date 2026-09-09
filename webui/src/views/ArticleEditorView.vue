<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ArticleHtmlEditor from '../components/ArticleHtmlEditor.vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import { api, stream, uploadAsset } from '../api'
import '../article-agent.css'
import {
  ArrowLeft, Save, Eye, CloudUpload, Send, ImagePlus, Images, Sparkles, Bot, User,
  LoaderCircle, X, Smartphone, Check, PanelRightClose, PanelRightOpen,
} from 'lucide-vue-next'

const route=useRoute(), router=useRouter(), article=ref(null), accounts=ref([]), messages=ref([]), instruction=ref(''), error=ref(''), saving=ref(false), aiBusy=ref(false), preview=ref(false), chatOpen=ref(true), ready=ref(false), dirty=ref(false), savedAt=ref(''), coverInput=ref()
const toolCalls=ref([]), agentSessionId=ref(''), chatMessages=ref(), chatAttachments=ref([]), chatUploading=ref(false), chatImageInput=ref()
const assetPickerOpen=ref(false), assetPickerTarget=ref('inline'), assetPickerItems=ref([]), assetPickerLoading=ref(false), assetPickerError=ref(''), inlineImageInput=ref()
const wechatOperation=reactive({visible:false,status:'idle',type:'',title:'',message:'',percent:0})
const wechatSteps=ref([])
const wechatBusy=computed(()=>wechatOperation.status==='running')
const interactionBusy=computed(()=>aiBusy.value||wechatBusy.value)
const toolRuntime=new Map()
let saveTimer, wechatHideTimer, savePromise=null, applyingServerArticle=false, editSequence=0, contentSequence=0
marked.setOptions({gfm:true,breaks:true})
const editor=ref(), skills=ref([])
function onContentUpdate(){contentSequence+=1;markDirty()}
const wordCount=computed(()=>editor.value?.getText().replace(/\s/g,'').length||0)
const accountName=computed(()=>accounts.value.find(a=>String(a.id)===String(article.value?.accountId))?.name||'未选择公众号')
function markDirty(){if(!ready.value||applyingServerArticle)return;dirty.value=true;editSequence+=1;clearTimeout(saveTimer);if(!interactionBusy.value)saveTimer=setTimeout(()=>save(false),1800)}
watch([
  ()=>article.value?.title,()=>article.value?.digest,()=>article.value?.author,
  ()=>article.value?.skillId,()=>article.value?.accountId,()=>article.value?.sourceUrl,()=>article.value?.coverAssetId,
],markDirty,{flush:'sync'})
function applyServerArticle(updated,replaceContent=false){applyingServerArticle=true;article.value={...article.value,...updated};if(replaceContent)editor.value.setContent(updated.contentHtml||'<p></p>',false);applyingServerArticle=false}
async function load(){try{const [a,acc,msg,sk]=await Promise.all([api(`/api/articles/${route.params.id}`),api('/api/accounts'),api(`/api/articles/${route.params.id}/ai/messages`),api('/api/skills')]);article.value=a;accounts.value=acc;messages.value=msg;skills.value=sk;await nextTick();editor.value.setContent(a.contentHtml||'<p></p>',false);ready.value=true;savedAt.value=formatClock(a.updatedAt)}catch(e){error.value=e.message}}
async function save(showError=true){
  clearTimeout(saveTimer)
  if(savePromise){try{return await savePromise}catch(e){if(showError){error.value=e.message;throw e}return null}}
  if(!article.value||!dirty.value)return article.value
  const sequence=editSequence
  const payload={...article.value,contentHtml:editor.value.getHTML(),revision:article.value.revision}
  saving.value=true
  savePromise=(async()=>{
    const updated=await api(`/api/articles/${article.value.id}`,{method:'PUT',body:JSON.stringify(payload)})
    if(editSequence===sequence){applyServerArticle(updated);dirty.value=false}
    else{applyingServerArticle=true;article.value={...article.value,revision:updated.revision,updatedAt:updated.updatedAt,workflowStatus:updated.workflowStatus};applyingServerArticle=false}
    savedAt.value=formatClock(updated.updatedAt||new Date())
    return updated
  })().finally(()=>{saving.value=false;savePromise=null;if(dirty.value&&!interactionBusy.value){clearTimeout(saveTimer);saveTimer=setTimeout(()=>save(false),1800)}})
  try{return await savePromise}catch(e){if(showError){error.value=e.message;throw e}return null}
}
function stageLabel(stage){return {VALIDATING:'校验配置',COVER:'准备封面',CONTENT:'处理正文',DRAFT:'同步草稿',PUBLISH:'提交发布',SAVING:'保存状态',DONE:'处理完成'}[stage]||stage}
function startWechatOperation(type){
  clearTimeout(wechatHideTimer);wechatSteps.value=[]
  Object.assign(wechatOperation,{visible:true,status:'running',type,title:type==='publish'?'正在发布到微信':'正在同步微信草稿',message:'正在建立安全连接…',percent:1})
}
function handleWechatEvent(name,data){
  if(name==='heartbeat')return
  if(name==='progress'){
    wechatOperation.message=data.message;wechatOperation.percent=Math.max(1,Math.min(100,Number(data.percent)||1))
    const current=wechatSteps.value.at(-1)
    if(current?.stage===data.stage){current.message=data.message;current.percent=wechatOperation.percent}
    else{if(current?.status==='running')current.status='completed';wechatSteps.value.push({stage:data.stage,label:stageLabel(data.stage),message:data.message,percent:wechatOperation.percent,status:'running'})}
  }
  if(name==='completed'){
    const current=wechatSteps.value.at(-1);if(current)current.status='completed'
    Object.assign(wechatOperation,{status:'success',message:data.message||'处理完成',percent:100})
    if(data.article)applyServerArticle(data.article)
  }
  if(name==='error')throw new Error(data.message||'微信接口处理失败')
}
async function action(type){
  if(wechatBusy.value||aiBusy.value)return
  if(type==='publish'&&!confirm('确定将这篇文章提交到微信发布吗？'))return
  error.value=''
  try{
    if(savePromise)await savePromise
    if(dirty.value)await save()
    startWechatOperation(type)
    const path=type==='publish'?`/api/articles/${article.value.id}/publish`:`/api/articles/${article.value.id}/wechat-draft`
    await stream(path,{},handleWechatEvent)
    wechatHideTimer=setTimeout(()=>{wechatOperation.visible=false},4000)
  }catch(e){
    const current=wechatSteps.value.at(-1);if(current?.status==='running')current.status='failed'
    Object.assign(wechatOperation,{visible:true,status:'failed',message:e.message})
    error.value=e.message
  }finally{
    if(dirty.value){clearTimeout(saveTimer);saveTimer=setTimeout(()=>save(false),1800)}
  }
}
async function openAssetPicker(target){
  if(interactionBusy.value)return
  assetPickerTarget.value=target;assetPickerOpen.value=true;assetPickerLoading.value=true;assetPickerError.value=''
  try{assetPickerItems.value=await api(`/api/assets${article.value.accountId?`?accountId=${encodeURIComponent(article.value.accountId)}`:''}`)}catch(e){assetPickerError.value=e.message}finally{assetPickerLoading.value=false}
}
function chooseAsset(asset){
  if(assetPickerTarget.value==='cover'){article.value.coverAssetId=asset.id;article.value.coverUrl=asset.publicUrl}
  else editor.value.insertImage(asset)
  assetPickerOpen.value=false
}
function triggerAssetUpload(){if(assetPickerTarget.value==='cover')coverInput.value?.click();else inlineImageInput.value?.click()}
function editorDocument(){
  const blocks=editor.value.getBlocks()
  return {title:article.value.title||'',digest:article.value.digest||'',contentHtml:editor.value.getHTML(),coverAssetId:article.value.coverAssetId||null,coverUrl:article.value.coverUrl||null,documentVersion:contentSequence,articleRevision:article.value.revision,blocks}
}
function validateDocumentVersion(expected){if(expected===undefined||expected===null)throw new Error('工具缺少 expectedDocumentVersion，请先读取文章');if(Number(expected)!==contentSequence)throw new Error(`正文版本已变化：工具基于 ${expected}，当前为 ${contentSequence}，请重新读取`)}
function validateLines(start,end){const count=editor.value.getBlocks().length;const first=Number(start),last=Number(end);if(!Number.isInteger(first)||!Number.isInteger(last)||first<1||last<first||last>count)throw new Error(`逻辑行范围无效：${start}-${end}，当前共 ${count} 行`);return {first,last}}
function deleteLogicalLines(start,end){const {first,last}=validateLines(start,end);return editor.value.deleteBlocks(first,last)}
function insertAtBlockIndex(index,contentHtml){return editor.value.insertAtBlockIndex(index,contentHtml)}
function toolLabel(name){return {read_article:'读取当前文章',read_blocks:'读取指定内容',delete_blocks:'删除内容块',insert_blocks:'插入新内容',replace_blocks:'改写内容块',update_metadata:'更新标题与摘要',update_cover:'设置文章封面',search_web:'搜索网页',browse_webpage:'浏览网页',search_web_images:'搜索网络图片',list_image_assets:'检索素材库',import_web_image:'导入网络图片',generate_image:'AI 生成图片',edit_image:'AI 编辑图片'}[name]||name}
const immediateEditorTools=new Set(['read_article','read_blocks','delete_blocks','update_metadata','update_cover'])
const streamingEditorTools=new Set(['insert_blocks','replace_blocks'])
function scrollChat(){nextTick(()=>{if(chatMessages.value)chatMessages.value.scrollTop=chatMessages.value.scrollHeight})}
function ensureTool(data){let item=toolCalls.value.find(tool=>tool.callId===data.callId);if(!item){item={type:'TOOL',callId:data.callId,name:data.name,label:toolLabel(data.name),status:'PREPARING',detail:'正在准备…',inserted:0,total:0};toolCalls.value.push(item);messages.value.push(item)}return item}
async function postToolResult(callId,success,result){
  if(!agentSessionId.value)throw new Error('缺少 AI 编辑会话标识')
  await api(`/api/articles/${article.value.id}/ai/sessions/${agentSessionId.value}/tools/${callId}/result`,{method:'POST',body:JSON.stringify({success,result,document:editorDocument()})})
}
async function failTool(item,message){item.status='FAILED';item.detail=message;const runtime=toolRuntime.get(item.callId)||{};if(runtime.reported)return;runtime.reported=true;toolRuntime.set(item.callId,runtime);try{await postToolResult(item.callId,false,message)}catch(e){error.value=e.message}}
async function executeImmediateTool(item,args){
  try{
    let result
    if(item.name==='read_article'){
      const document=editorDocument();result=JSON.stringify({title:document.title,digest:document.digest,coverAssetId:document.coverAssetId,coverUrl:document.coverUrl,documentVersion:document.documentVersion,articleRevision:document.articleRevision,blocks:document.blocks.map(({line,type,html})=>({line,type,html}))});item.detail=`已读取 ${document.blocks.length} 个逻辑块，文档版本 ${document.documentVersion}`
    }else if(item.name==='read_blocks'){
      const document=editorDocument(),{first,last}=validateLines(args.startLine,args.endLine);result=JSON.stringify({title:document.title,digest:document.digest,documentVersion:document.documentVersion,articleRevision:document.articleRevision,blocks:document.blocks.slice(first-1,last).map(({line,type,html})=>({line,type,html}))});item.detail=`已读取第 ${first}～${last} 行`
    }else if(item.name==='delete_blocks'){
      validateDocumentVersion(args.expectedDocumentVersion);const {first,last}=validateLines(args.startLine,args.endLine);deleteLogicalLines(first,last);result=JSON.stringify({message:`已删除第 ${first}～${last} 行`,documentVersion:contentSequence});item.detail=`已删除第 ${first}～${last} 行`
    }else if(item.name==='update_metadata'){
      validateDocumentVersion(args.expectedDocumentVersion);if(args.title!==undefined&&args.title!==null)article.value.title=String(args.title).slice(0,64);if(args.digest!==undefined&&args.digest!==null)article.value.digest=String(args.digest).slice(0,120);result=JSON.stringify({message:'标题和摘要已更新',documentVersion:contentSequence,title:article.value.title,digest:article.value.digest});item.detail='标题和摘要已更新'
    }else if(item.name==='update_cover'){
      validateDocumentVersion(args.expectedDocumentVersion);const assetId=Number(args.assetId);if(!Number.isInteger(assetId)||assetId<1)throw new Error('工具缺少有效的封面素材 assetId');const asset=await api(`/api/assets/${assetId}`);if(article.value.accountId&&asset.accountId&&String(article.value.accountId)!==String(asset.accountId))throw new Error('封面素材不属于当前公众号');article.value.coverAssetId=asset.id;article.value.coverUrl=asset.publicUrl;result=JSON.stringify({message:'文章封面已更新',assetId:asset.id,publicUrl:asset.publicUrl,documentVersion:contentSequence});item.detail='文章封面已更新'
    }else throw new Error(`不支持的编辑工具：${item.name}`)
    item.status='COMPLETED';await postToolResult(item.callId,true,result)
  }catch(e){await failTool(item,e.message)}
}
function prepareStreamingTool(item,args){
  try{
    validateDocumentVersion(args.expectedDocumentVersion)
    let insertIndex
    if(item.name==='replace_blocks'){
      const {first,last}=validateLines(args.startLine,args.endLine);insertIndex=deleteLogicalLines(first,last);item.detail=`已移除第 ${first}～${last} 行，正在写入新内容…`
    }else{
      const count=editor.value.getBlocks().length,line=Number(args.line),position=String(args.position||'AFTER').toUpperCase();if(!Number.isInteger(line)||line<1||line>Math.max(1,count))throw new Error(`插入锚点无效：第 ${args.line} 行`);if(!editor.value.getText().trim()&&editor.value.getBlocks().every(block=>block.type==='p')){if(count)deleteLogicalLines(1,1);insertIndex=0}else insertIndex=position==='BEFORE'?line-1:line;item.detail=`正在第 ${line} 行${position==='BEFORE'?'前':'后'}插入…`
    }
    const runtime={insertIndex,reported:false,failed:false};toolRuntime.set(item.callId,runtime);item.status='CALLING'
  }catch(e){const runtime={reported:false,failed:true};toolRuntime.set(item.callId,runtime);void failTool(item,e.message)}
}
function insertToolDelta(data){const runtime=toolRuntime.get(data.callId),item=ensureTool(data);if(!runtime||runtime.failed)return;try{runtime.insertIndex+=insertAtBlockIndex(runtime.insertIndex,data.contentHtml);item.inserted+=1;item.total=data.total||item.total;item.detail=`正在写入内容 ${item.inserted}/${item.total||'…'}`;item.status='CALLING'}catch(e){runtime.failed=true;void failTool(item,e.message)}}
async function finishStreamingTool(data){const runtime=toolRuntime.get(data.callId),item=ensureTool(data);if(!runtime||runtime.reported)return;if(runtime.failed)return;runtime.reported=true;item.status='COMPLETED';item.detail=`已写入 ${item.inserted} 个内容块`;try{await postToolResult(item.callId,true,JSON.stringify({message:item.detail,documentVersion:contentSequence,blocks:editorDocument().blocks}))}catch(e){item.status='FAILED';item.detail=e.message;error.value=e.message}}
function closeAssistant(turn){if(turn.assistant){turn.assistant.status='COMPLETED';turn.assistant=null}}
function renderMarkdown(content){return DOMPurify.sanitize(marked.parse(String(content||'')))}
function appendAssistantDelta(turn,content){
  if(!content)return
  if(!turn.assistant){turn.assistant=reactive({type:'MESSAGE',role:'ASSISTANT',content:'',status:'RUNNING'});messages.value.push(turn.assistant)}
  turn.assistant.content+=content;turn.receivedDelta=true
}
function handleAgentEvent(name,data,turn){
  if(name==='heartbeat')return
  if(name==='state'&&data.sessionId)agentSessionId.value=data.sessionId
  if(name==='delta')appendAssistantDelta(turn,data.content||'')
  if(name==='tool.call'){
    closeAssistant(turn)
    agentSessionId.value=data.sessionId||agentSessionId.value;const item=ensureTool(data),args=data.arguments||{};item.status='CALLING'
    if(streamingEditorTools.has(item.name))prepareStreamingTool(item,args)
    else if(immediateEditorTools.has(item.name))void executeImmediateTool(item,args)
    else item.detail='正在由服务端执行…'
  }
  if(name==='editor.insert.delta')insertToolDelta(data)
  if(name==='editor.insert.completed')void finishStreamingTool(data)
  if(name==='tool.result'){const item=ensureTool(data);item.status=data.status==='FAILED'?'FAILED':'COMPLETED';if(data.status==='FAILED')item.detail=data.message||'工具执行失败';else if(!item.detail||item.detail==='正在准备…')item.detail='工具执行完成'}
  if(name==='tool.error'){const item=ensureTool(data);item.status='FAILED';item.detail=data.message}
  if(name==='completed'){
    closeAssistant(turn)
    if(!turn.receivedDelta&&data.message)messages.value.push({type:'MESSAGE',role:'ASSISTANT',content:data.message,status:'COMPLETED'})
    if(data.article){applyServerArticle(data.article,true);dirty.value=false;savedAt.value=formatClock(data.article.updatedAt||new Date())}
  }
  if(name==='error')throw new Error(data.message)
  scrollChat()
}
async function askAi(){
  if((!instruction.value.trim()&&!chatAttachments.value.length)||interactionBusy.value||chatUploading.value)return
  error.value='';aiBusy.value=true;clearTimeout(saveTimer);toolCalls.value=[];toolRuntime.clear();agentSessionId.value=''
  try{
    if(savePromise)await savePromise;if(dirty.value)await save()
    const text=instruction.value.trim()||'请查看我上传的图片，并结合当前文章进行处理。',attachments=chatAttachments.value.map(item=>({...item}));instruction.value='';chatAttachments.value=[];messages.value.push({type:'MESSAGE',role:'USER',content:text,status:'COMPLETED',attachments})
    const turn={assistant:null,receivedDelta:false}
    await stream(`/api/articles/${article.value.id}/ai/chat`,{instruction:text,assetIds:attachments.map(item=>item.id)},(name,data)=>handleAgentEvent(name,data,turn))
    closeAssistant(turn)
  }catch(e){error.value=e.message}finally{aiBusy.value=false;if(dirty.value){clearTimeout(saveTimer);saveTimer=setTimeout(()=>save(false),1800)}}
}
async function attachChatImages(event){
  const files=Array.from(event.target.files||[]),available=Math.max(0,4-chatAttachments.value.length);event.target.value=''
  if(!files.length||!available)return
  chatUploading.value=true;error.value=''
  try{for(const file of files.slice(0,available))chatAttachments.value.push(await uploadAsset(file,article.value.accountId))}catch(e){error.value=e.message}finally{chatUploading.value=false}
}
function removeChatAttachment(id){chatAttachments.value=chatAttachments.value.filter(item=>item.id!==id)}
async function uploadInline(event){const file=event.target.files?.[0];if(!file)return;try{const asset=await uploadAsset(file,article.value.accountId);editor.value.insertImage(asset);assetPickerOpen.value=false}catch(e){error.value=e.message}event.target.value=''}
async function uploadCover(event){const file=event.target.files?.[0];if(!file)return;try{const asset=await uploadAsset(file,article.value.accountId);article.value.coverAssetId=asset.id;article.value.coverUrl=asset.publicUrl;assetPickerOpen.value=false}catch(e){error.value=e.message}event.target.value=''}
function formatClock(v){return v?new Date(v).toLocaleTimeString('zh-CN',{hour:'2-digit',minute:'2-digit'}):''}
onMounted(load);onBeforeUnmount(()=>{clearTimeout(saveTimer);clearTimeout(wechatHideTimer)})
</script>

<template>
  <div v-if="article" class="editor-page">
    <header class="editor-header">
      <div class="editor-header-left"><button class="icon-button" @click="router.push('/articles')"><ArrowLeft :size="19" /></button><div class="editor-doc-meta"><strong>{{article.title||'未命名文章'}}</strong><span><i :class="{dirty}"></i>{{saving?'正在保存…':dirty?'有未保存修改':`已于 ${savedAt} 保存`}}</span></div></div>
      <div class="editor-header-center"><span>{{accountName}}</span><span class="status-pill" :class="article.wechatStatus.toLowerCase()">{{article.wechatStatus}}</span></div>
      <div class="editor-header-actions"><button class="secondary-button" @click="preview=true"><Eye :size="16" />预览</button><button class="secondary-button" :disabled="saving||interactionBusy" @click="dirty=true;save()"><Save :size="16" />保存</button><button class="secondary-button" :disabled="interactionBusy" @click="action('draft')"><LoaderCircle v-if="wechatBusy&&wechatOperation.type==='draft'" class="spin" :size="16"/><CloudUpload v-else :size="16" />{{wechatBusy&&wechatOperation.type==='draft'?`同步中 ${wechatOperation.percent}%`:'同步草稿'}}</button><button class="primary-button" :disabled="interactionBusy" @click="action('publish')"><LoaderCircle v-if="wechatBusy&&wechatOperation.type==='publish'" class="spin" :size="16"/><Send v-else :size="16" />{{wechatBusy&&wechatOperation.type==='publish'?`发布中 ${wechatOperation.percent}%`:'发布'}}</button></div>
    </header>
    <div v-if="error" class="editor-alert"><span>{{error}}</span><button @click="error=''"><X :size="16" /></button></div>
    <Transition name="wechat-progress">
      <aside v-if="wechatOperation.visible" class="wechat-progress-card" :class="wechatOperation.status" role="status" aria-live="polite">
        <header>
          <span class="wechat-progress-icon"><LoaderCircle v-if="wechatBusy" class="spin" :size="19"/><Check v-else-if="wechatOperation.status==='success'" :size="19"/><X v-else :size="19"/></span>
          <div><strong>{{wechatOperation.title}}</strong><small>{{wechatOperation.message}}</small></div>
          <button v-if="!wechatBusy" class="wechat-progress-close" title="关闭" @click="wechatOperation.visible=false"><X :size="15"/></button>
        </header>
        <div class="wechat-progress-track"><i :style="{width:`${wechatOperation.percent}%`}"></i></div>
        <div class="wechat-progress-meta"><span>{{wechatOperation.status==='failed'?'处理失败':wechatOperation.status==='success'?'已完成':'实时处理中'}}</span><strong>{{wechatOperation.percent}}%</strong></div>
        <div class="wechat-progress-steps">
          <div v-for="(step,index) in wechatSteps" :key="`${step.stage}-${index}`" :class="step.status">
            <span><Check v-if="step.status==='completed'" :size="12"/><X v-else-if="step.status==='failed'" :size="12"/><LoaderCircle v-else class="spin" :size="12"/></span>
            <div><strong>{{step.label}}</strong><small>{{step.message}}</small></div>
          </div>
        </div>
      </aside>
    </Transition>
    <div class="editor-body" :class="{'chat-closed':!chatOpen}">
      <section class="writing-stage">
        <div class="metadata-strip">
          <select v-model="article.accountId" :disabled="interactionBusy"><option :value="null">选择公众号</option><option v-for="a in accounts" :key="a.id" :value="a.id">{{a.name}}</option></select>
          <input v-model="article.author" :disabled="interactionBusy" placeholder="作者"><input v-model="article.sourceUrl" :disabled="interactionBusy" placeholder="原文链接（可选）">
          <button class="cover-picker" :disabled="interactionBusy" @click="openAssetPicker('cover')"><img v-if="article.coverUrl" :src="article.coverUrl"><Images v-else :size="17" />{{article.coverUrl?'更换封面':'选择封面'}}</button><input ref="coverInput" type="file" accept="image/*" hidden @change="uploadCover">
        </div>
        <label class="article-skill-picker"><Sparkles :size="16" /><span>文章 Skill</span><select v-model="article.skillId" aria-label="文章 Skill" :disabled="interactionBusy"><option :value="null">默认 Skill（{{skills.find(s=>s.isDefault)?.name||'默认风格'}}）</option><option v-if="article.skillId&&!skills.some(s=>s.id===article.skillId)" :value="article.skillId">原 Skill 已删除 · 使用默认</option><option v-for="skill in skills" :key="skill.id" :value="skill.id">{{skill.name}}</option></select><small>用于后续 AI 创作与改写</small></label>
        <div class="paper">
          <input v-model="article.title" class="title-input" :disabled="interactionBusy" maxlength="64" placeholder="输入一个打动人的标题">
          <textarea v-model="article.digest" class="digest-input" :disabled="interactionBusy" maxlength="120" rows="2" placeholder="写一段简洁的摘要，帮助读者快速了解文章…"></textarea>
          <div class="html-asset-tools"><button class="secondary-button" :disabled="interactionBusy" @click="openAssetPicker('inline')"><Images :size="16" />插入素材图片</button><label class="secondary-button"><ImagePlus :size="16" />上传图片<input ref="inlineImageInput" type="file" accept="image/*" hidden :disabled="interactionBusy" @change="uploadInline"></label></div>
          <ArticleHtmlEditor ref="editor" :disabled="interactionBusy" @update="onContentUpdate" />
          <footer class="paper-footer"><span>{{wordCount}} 字</span><span>版本 {{article.revision}}</span></footer>
        </div>
      </section>
      <aside class="ai-panel" v-show="chatOpen">
        <header><div><span class="ai-avatar"><Sparkles :size="17" /></span><div><strong>墨舟智能体</strong><small><i></i> 在线协作</small></div></div><button class="icon-button" @click="chatOpen=false"><PanelRightClose :size="18" /></button></header>
        <div class="ai-context"><Bot :size="15" /><span>{{aiBusy?`智能体正在编辑文章第 ${article.revision} 版`:`智能体上下文：文章第 ${article.revision} 版`}}</span><Check :size="14" /></div>
        <div ref="chatMessages" class="chat-messages">
          <div v-if="!messages.length" class="ai-welcome"><span><Sparkles /></span><strong>想从哪里开始？</strong><p>告诉我主题、目标读者和想要的语气。我会直接在左侧文章中完成修改。</p><button @click="instruction='帮我检查文章结构并给出优化建议'">检查文章结构</button><button @click="instruction='把这篇文章改得更适合微信公众号阅读'">优化微信排版</button></div>
          <template v-for="(item,index) in messages" :key="item.callId||item.id||index">
            <div v-if="item.type!=='TOOL'" class="chat-message" :class="item.role.toLowerCase()"><span class="message-avatar"><User v-if="item.role==='USER'" :size="15" /><Sparkles v-else :size="15" /></span><div><div v-if="item.attachments?.length" class="message-images"><img v-for="image in item.attachments" :key="image.id" :src="image.publicUrl" :alt="image.originalName"></div><div class="message-markdown" v-html="renderMarkdown(item.content)"></div><span v-if="item.status==='RUNNING'" class="typing-caret"></span></div></div>
            <div v-else class="tool-card" :class="item.status.toLowerCase()">
              <span class="tool-state"><Check v-if="item.status==='COMPLETED'" :size="14"/><X v-else-if="item.status==='FAILED'" :size="14"/><LoaderCircle v-else class="spin" :size="14"/></span>
              <div><strong>{{item.label}}</strong><small>{{item.detail}}</small><span v-if="item.total" class="tool-progress"><i :style="{width:`${Math.min(100,(item.inserted/item.total)*100)}%`}"></i></span></div>
            </div>
          </template>
        </div>
        <form class="chat-composer" @submit.prevent="askAi">
          <div v-if="chatAttachments.length" class="chat-attachments"><span v-for="image in chatAttachments" :key="image.id"><img :src="image.publicUrl" :alt="image.originalName"><button type="button" @click="removeChatAttachment(image.id)"><X :size="11"/></button></span></div>
          <textarea v-model="instruction" rows="3" :disabled="interactionBusy" placeholder="告诉智能体如何修改文章，也可以上传参考图片…" @keydown.meta.enter.prevent="askAi"></textarea>
          <div><button type="button" class="attach-button" :disabled="interactionBusy||chatUploading||chatAttachments.length>=4" title="上传图片" @click="chatImageInput.click()"><LoaderCircle v-if="chatUploading" class="spin" :size="16"/><ImagePlus v-else :size="16"/></button><input ref="chatImageInput" type="file" accept="image/jpeg,image/png,image/gif,image/webp" multiple hidden @change="attachChatImages"><span>最多 4 张 · ⌘ ↵ 发送</span><button :disabled="interactionBusy||chatUploading||(!instruction.trim()&&!chatAttachments.length)"><LoaderCircle v-if="aiBusy" class="spin" :size="17"/><Send v-else :size="17" /></button></div>
        </form>
      </aside>
      <button v-if="!chatOpen" class="open-chat" @click="chatOpen=true"><PanelRightOpen :size="19" /><span>打开 AI 助手</span></button>
    </div>
    <div v-if="assetPickerOpen" class="modal-backdrop" @click.self="assetPickerOpen=false"><div class="modal-card wide asset-picker-modal"><header><div><h3>{{assetPickerTarget==='cover'?'选择文章封面':'插入素材图片'}}</h3><p>从当前公众号素材库中选择，也可以上传新图片。</p></div><button class="icon-button" @click="assetPickerOpen=false"><X :size="18"/></button></header><div class="asset-picker-actions"><span>{{assetPickerItems.length}} 个可用素材</span><button class="secondary-button" @click="triggerAssetUpload"><Upload :size="15"/>上传新图片</button></div><div v-if="assetPickerError" class="alert error">{{assetPickerError}}</div><div v-if="assetPickerLoading" class="asset-picker-loading"><LoaderCircle class="spin" :size="20"/>正在加载素材…</div><div v-else-if="assetPickerItems.length" class="asset-picker-grid"><button v-for="asset in assetPickerItems" :key="asset.id" @click="chooseAsset(asset)"><img :src="asset.publicUrl" :alt="asset.originalName"><span>{{asset.originalName}}</span><small>{{asset.sourceType||'素材库'}}</small></button></div><div v-else class="empty-state"><Images :size="32"/><strong>素材库还没有图片</strong><p>上传第一张图片后即可用于封面或正文。</p><button class="primary-button" @click="triggerAssetUpload"><Upload :size="15"/>上传图片</button></div></div></div>
    <div v-if="preview" class="modal-backdrop preview-backdrop" @click.self="preview=false"><div class="phone-preview-modal"><header><div><Smartphone :size="18" /><strong>微信手机预览</strong></div><button class="icon-button" @click="preview=false"><X :size="19" /></button></header><div class="phone-frame"><article><h1>{{article.title}}</h1><div class="wechat-byline">{{article.author||accountName}} · {{accountName}}</div><p class="wechat-digest">{{article.digest}}</p><ArticleHtmlEditor readonly :content="editor.getHTML()" /></article></div></div></div>
  </div>
  <div v-else class="page-loading"><LoaderCircle class="spin" />正在打开文章…</div>
</template>

<style scoped>
.article-skill-picker{display:flex;align-items:center;flex-wrap:wrap;gap:10px;margin:0 auto 16px;max-width:860px;font-size:13px;color:#526159}.article-skill-picker select{border:1px solid #dde2dc;border-radius:7px;padding:7px;background:white;max-width:100%}.article-skill-picker small{color:#89968e}.html-asset-tools{display:flex;gap:8px;margin-bottom:12px;flex-wrap:wrap}
</style>
