<script setup>
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useI18n } from '../i18n'
import { Images, ImagePlus } from 'lucide-vue-next'

const props = defineProps({ content: { type: String, default: '<p></p>' }, readonly: Boolean, disabled: Boolean })
const { t } = useI18n()
const emit = defineEmits(['update', 'insert-asset', 'upload-image'])
const frame = ref(), sourceHost = ref(), sourceMode = ref(false), html = ref(props.content), height = ref(550)
let observer, selection, attachedDocument, monacoApi, sourceEditor, sourceChangeDisposable, syncingSource = false, monacoLoadPromise
let history = [html.value], historyIndex = 0
function record() {
  if (history[historyIndex] === html.value) return
  history = history.slice(0, historyIndex + 1)
  history.push(html.value)
  if (history.length > 100) history.shift()
  historyIndex = history.length - 1
}
function travel(delta) {
  const target = historyIndex + delta
  if (target < 0 || target >= history.length || props.disabled) return
  historyIndex = target; html.value = history[target]; render(); emit('update')
}
const frameDocument = '<!doctype html><html><head><meta charset="utf-8"><meta http-equiv="Content-Security-Policy" content="default-src \'none\'; script-src \'none\'; style-src * \'unsafe-inline\'; img-src * data: blob:; font-src * data:; media-src * blob:; connect-src \'none\'; form-action \'none\'; base-uri \'none\'"><style>html{overflow-wrap:break-word}body{margin:0;color:#333;font:16px/1.8 system-ui,sans-serif;min-height:100px;outline:none}img{max-width:100%}</style></head><body></body></html>'

// Remove executable content only. Layout is not passed through a rich-text schema or CSS allowlist.
function fragment(value) {
  const template = document.createElement('template')
  template.innerHTML = value || ''
  template.content.querySelectorAll('script,iframe,object,embed,base,meta,frame,frameset,applet').forEach(el => el.remove())
  template.content.querySelectorAll('*').forEach(el => {
    const target = (el.getAttribute('attributeName') || el.getAttribute('attributename') || '').toLowerCase()
    if (['animate','set','animatemotion','animatetransform'].includes(el.localName.toLowerCase()) && (['src','href','xlink:href','action','formaction','poster','background','srcdoc'].includes(target) || target.startsWith('on'))) { el.remove(); return }
    for (const attr of Array.from(el.attributes)) {
      const name = attr.name.toLowerCase(), url = attr.value.replace(/[\s\u0000-\u001f\u007f]+/g, '').toLowerCase()
      const urlAttribute = ['src', 'href', 'xlink:href', 'action', 'formaction', 'poster', 'background'].includes(name)
      const badUrl = /^[a-z][a-z0-9+.-]*:/.test(url) && !/^(https?:|mailto:|tel:|data:image\/)/.test(url)
      if (name.startsWith('on') || ['srcdoc','contenteditable'].includes(name) || (urlAttribute && badUrl)) el.removeAttribute(attr.name)
    }
  })
  return template.content
}
function nodes() { return Array.from(fragment(html.value).childNodes).filter(node => node.nodeType !== 3 || node.textContent.trim()) }
function serialize(items) { const box = document.createElement('div'); items.forEach(node => box.append(node)); return box.innerHTML }
function measure() { if (frame.value?.contentDocument?.body) height.value = Math.max(550, Math.ceil(frame.value.contentDocument.body.getBoundingClientRect().height) + 24) }
function render() {
  const doc = frame.value?.contentDocument
  if (!doc?.body) return
  doc.body.replaceChildren(fragment(html.value))
  doc.body.contentEditable = String(!props.readonly && !props.disabled)
  selection = null
  measure()
}
function rememberSelection() {
  const current = frame.value?.contentWindow?.getSelection()
  if (current?.rangeCount) selection = current.getRangeAt(0).cloneRange()
}
function changed() {
  html.value = frame.value.contentDocument.body.innerHTML
  rememberSelection()
  record()
  emit('update')
  measure()
}
function loaded() {
  observer?.disconnect()
  render()
  const doc = frame.value.contentDocument
  attachedDocument = doc
  doc.addEventListener('input', changed)
  doc.addEventListener('selectionchange', rememberSelection)
  // Links and forms remain article content; editing/preview never navigates the frame.
  doc.addEventListener('click', event => { if (event.target.closest?.('a')) event.preventDefault() }, true)
  doc.addEventListener('submit', event => event.preventDefault(), true)
  doc.addEventListener('paste', paste)
  doc.addEventListener('keydown', event => {
    if ((event.ctrlKey || event.metaKey) && ['z','y'].includes(event.key.toLowerCase())) {
      event.preventDefault(); travel(event.shiftKey || event.key.toLowerCase() === 'y' ? 1 : -1)
    }
  })
  observer = new ResizeObserver(measure)
  observer.observe(doc.body)
}
function syncSourceEditor() {
  if (!sourceEditor || sourceEditor.getValue() === html.value) return
  syncingSource = true; sourceEditor.setValue(html.value); syncingSource = false
}
async function loadMonaco() {
  if (monacoApi) return monacoApi
  if (!monacoLoadPromise) monacoLoadPromise = Promise.all([
    import('monaco-editor/esm/vs/editor/editor.api'),
    import('monaco-editor/esm/vs/language/html/monaco.contribution'),
    import('monaco-editor/esm/vs/editor/editor.worker?worker'),
    import('monaco-editor/esm/vs/language/html/html.worker?worker'),
  ]).then(([monaco, _htmlContribution, editorWorkerModule, htmlWorkerModule]) => {
    const EditorWorker = editorWorkerModule.default, HtmlWorker = htmlWorkerModule.default
    globalThis.MonacoEnvironment = { getWorker(_moduleId, label) { return label === 'html' ? new HtmlWorker() : new EditorWorker() } }
    monacoApi = monaco
    return monaco
  })
  return monacoLoadPromise
}
async function initSourceEditor() {
  if (sourceEditor || !sourceHost.value) return
  const monaco = await loadMonaco()
  if (!sourceHost.value || sourceEditor) return
  sourceEditor = monaco.editor.create(sourceHost.value, {
    value: html.value, language: 'html', theme: 'vs-light', readOnly: props.disabled,
    automaticLayout: true, ariaLabel: t('htmlEditor.sourceLabel'), minimap: { enabled: false },
    fontSize: 13, lineHeight: 21, tabSize: 2, insertSpaces: true, wordWrap: 'on',
    scrollBeyondLastLine: false, padding: { top: 14, bottom: 14 },
  })
  sourceChangeDisposable = sourceEditor.onDidChangeModelContent(() => {
    if (syncingSource) return
    html.value = sourceEditor.getValue(); record(); emit('update')
  })
}
function setContent(value, notify = false) { html.value = value ?? '<p></p>'; render(); syncSourceEditor(); if (notify) { record(); emit('update') } else { history = [html.value]; historyIndex = 0 } }
async function setSourceMode(value) { if (sourceMode.value === value) return; sourceMode.value = value; await nextTick(); if (value) await initSourceEditor(); else render() }
function focusSelection() {
  const doc = frame.value.contentDocument
  doc.body.focus()
  const current = frame.value.contentWindow.getSelection()
  if (selection && doc.body.contains(selection.commonAncestorContainer)) { current.removeAllRanges(); current.addRange(selection) }
  return doc
}
function command(name, value = null) {
  if (props.disabled || props.readonly) return
  if (name === 'undo' || name === 'redo') { travel(name === 'undo' ? -1 : 1); return }
  focusSelection().execCommand(name, false, value)
  changed()
}
function insertHtml(value) {
  const safe = serialize(Array.from(fragment(value).childNodes))
  if (sourceMode.value) setContent(html.value + safe, true)
  else command('insertHTML', safe)
}
function insertImage(asset) {
  const image = document.createElement('img'); image.src = asset.publicUrl; image.alt = asset.originalName || t('htmlEditor.imageAlt')
  insertHtml(image.outerHTML)
}
function paste(event) {
  if (props.disabled || props.readonly) return
  const value = event.clipboardData?.getData('text/html')
  if (value) { event.preventDefault(); insertHtml(value) }
}
function link() { const url = prompt(t('htmlEditor.linkPrompt'), 'https://'); if (url && /^(https?:|mailto:|tel:)/i.test(url)) command('createLink', url) }
function blockStyle(property, value) {
  if (!value || props.disabled) return
  const doc = focusSelection()
  const range = doc.getSelection()?.rangeCount ? doc.getSelection().getRangeAt(0) : null
  if (!range) return
  const blocks = Array.from(doc.body.querySelectorAll('p,h1,h2,h3,h4,h5,h6,blockquote,li,td,th,div,section'))
    .filter(el => range.intersectsNode(el) && !Array.from(el.children).some(child => range.intersectsNode(child) && child.matches('p,h1,h2,h3,h4,h5,h6,blockquote,li,div,section')))
  if (blocks.length) blocks.forEach(el => el.style.setProperty(property, value))
  else { const span = doc.createElement('span'); span.style.setProperty(property, value); span.append(range.extractContents()); range.insertNode(span) }
  changed()
}
function getBlocks() { return nodes().map((node, index) => ({ line:index+1, type:node.nodeName.toLowerCase(), text:node.textContent || '', html:serialize([node]) })) }
function deleteBlocks(first, last) { const items = nodes(); items.splice(first - 1, last - first + 1); setContent(serialize(items), true); return first - 1 }
function insertAtBlockIndex(index, value) {
  const items = nodes(), inserted = Array.from(fragment(value).childNodes).filter(node => node.nodeType !== 3 || node.textContent.trim())
  items.splice(index, 0, ...inserted); setContent(serialize(items), true); return inserted.length
}
watch(() => props.content, value => setContent(value))
watch(() => props.disabled, value => { if (frame.value?.contentDocument?.body) frame.value.contentDocument.body.contentEditable = String(!props.readonly && !value); sourceEditor?.updateOptions({ readOnly: value }) })
onBeforeUnmount(() => { observer?.disconnect(); attachedDocument?.removeEventListener('selectionchange', rememberSelection); sourceChangeDisposable?.dispose(); sourceEditor?.dispose() })
function getText() { const content = fragment(html.value); content.querySelectorAll('style').forEach(el=>el.remove()); return content.textContent || '' }
defineExpose({ getHTML:()=>html.value, getText, getBlocks, setContent, deleteBlocks, insertAtBlockIndex, insertImage })
</script>

<template>
  <div class="html-editor">
    <div v-if="!readonly" class="html-editor-controls">
      <div class="html-editor-tabs" role="tablist" :aria-label="t('htmlEditor.modeLabel')"><button id="visual-editor-tab" type="button" role="tab" :aria-selected="!sourceMode" aria-controls="visual-editor-panel" :class="{active:!sourceMode}" @click="setSourceMode(false)">{{ t('htmlEditor.visual') }}</button><button id="source-editor-tab" type="button" role="tab" :aria-selected="sourceMode" aria-controls="source-editor-panel" :class="{active:sourceMode}" @click="setSourceMode(true)">{{ t('htmlEditor.source') }}</button><span>{{ t('htmlEditor.freeLayout') }}</span></div>
      <div v-if="!sourceMode" class="html-format-tools" :inert="disabled">
        <button type="button" class="asset-tool" @click="emit('insert-asset')"><Images :size="14" />{{ t('editor.insertAsset') }}</button><button type="button" class="asset-tool" @click="emit('upload-image')"><ImagePlus :size="14" />{{ t('common.upload') }}</button><i class="tool-divider" aria-hidden="true"></i>
        <button type="button" :title="t('htmlEditor.bold')" @mousedown.prevent @click="command('bold')"><b>B</b></button><button type="button" :title="t('htmlEditor.italic')" @mousedown.prevent @click="command('italic')"><i>I</i></button><button type="button" :title="t('htmlEditor.underline')" @mousedown.prevent @click="command('underline')"><u>U</u></button><button type="button" :title="t('htmlEditor.strike')" @mousedown.prevent @click="command('strikeThrough')"><s>S</s></button>
        <select :aria-label="t('htmlEditor.paragraphType')" @change="command('formatBlock',$event.target.value);$event.target.value='' "><option value="">{{ t('htmlEditor.paragraph') }}</option><option value="p">{{ t('htmlEditor.body') }}</option><option value="h1">{{ t('htmlEditor.headingOne') }}</option><option value="h2">{{ t('htmlEditor.headingTwo') }}</option><option value="h3">{{ t('htmlEditor.headingThree') }}</option><option value="blockquote">{{ t('htmlEditor.quote') }}</option><option value="pre">{{ t('htmlEditor.code') }}</option></select>
        <select :aria-label="t('htmlEditor.fontSize')" @change="blockStyle('font-size',$event.target.value);$event.target.value=''"><option value="">{{ t('htmlEditor.fontSize') }}</option><option v-for="n in [12,14,16,18,20,24,28,32,48]" :value="`${n}px`">{{n}}</option></select>
        <label :title="t('htmlEditor.textColor')">A<input type="color" :aria-label="t('htmlEditor.textColor')" value="#333333" @input="command('foreColor',$event.target.value)"></label><label :title="t('htmlEditor.backgroundColor')">Bg<input type="color" :aria-label="t('htmlEditor.backgroundColor')" value="#fff2a8" @input="command('hiliteColor',$event.target.value)"></label>
        <button v-for="[name,key] in [['justifyLeft','alignLeft'],['justifyCenter','alignCenter'],['justifyRight','alignRight'],['justifyFull','justify'],['insertUnorderedList','list'],['insertOrderedList','numberedList'],['undo','undo'],['redo','redo'],['removeFormat','clearFormat']]" type="button" @mousedown.prevent @click="command(name)">{{t(`htmlEditor.${key}`)}}</button>
        <select :aria-label="t('htmlEditor.lineHeight')" @change="blockStyle('line-height',$event.target.value);$event.target.value=''"><option value="">{{ t('htmlEditor.lineHeight') }}</option><option v-for="n in [1,1.5,1.8,2,2.5]" :value="n">{{n}}</option></select>
        <select :aria-label="t('htmlEditor.spacing')" @change="blockStyle('margin-bottom',$event.target.value);$event.target.value=''"><option value="">{{ t('htmlEditor.spacing') }}</option><option v-for="n in [0,8,16,24,32,48]" :value="`${n}px`">{{n}}px</option></select>
        <button type="button" @mousedown.prevent @click="link">{{ t('htmlEditor.link') }}</button><button type="button" @mousedown.prevent @click="insertHtml('<hr>')">{{ t('htmlEditor.divider') }}</button><button type="button" @mousedown.prevent @click="insertHtml('<table style=&quot;width:100%;border-collapse:collapse&quot;><tbody>'+Array.from({length:3},()=>'<tr>'+Array.from({length:3},()=>`<td style=&quot;border:1px solid #ccc;padding:8px&quot;>${t('htmlEditor.tableContent')}</td>`).join('')+'</tr>').join('')+'</tbody></table>')">{{ t('htmlEditor.table') }}</button>
      </div>
    </div>
    <div v-show="sourceMode" id="source-editor-panel" ref="sourceHost" class="html-source" role="tabpanel" aria-labelledby="source-editor-tab"></div>
    <iframe v-show="!sourceMode" id="visual-editor-panel" ref="frame" :title="readonly?t('htmlEditor.previewTitle'):t('htmlEditor.editTitle')" class="html-canvas" role="tabpanel" aria-labelledby="visual-editor-tab" sandbox="allow-same-origin" :srcdoc="frameDocument" :style="{height:`${height}px`}" @load="loaded"></iframe>
  </div>
</template>

<style scoped>
.html-editor{width:100%;min-width:0}.html-editor-controls{border:1px solid #e2e7e3;border-radius:10px;background:#f8faf8;margin-bottom:24px;padding:0 8px 8px}.html-editor-tabs,.html-format-tools{display:flex;gap:5px;align-items:center}.html-editor-tabs{height:43px;border-bottom:1px solid #e2e7e3;margin:0 -8px 8px;padding:0 8px}.html-editor-tabs span{font-size:11px;color:#8b968f;margin-left:auto}.html-format-tools{flex-wrap:wrap}.html-editor button,.html-editor select{flex:0 0 auto;font:inherit;font-size:11px;border:1px solid #e0e5e1;border-radius:6px;background:white;color:#536159;padding:5px 8px;cursor:pointer}.html-editor button:hover,.html-editor select:hover{border-color:#aebcb4;color:#1f7155}.html-editor button.active{border-color:#c7dfd3;background:#e6f6ee;color:#087d49}.html-editor .html-editor-tabs button{all:unset;box-sizing:border-box;align-self:stretch;display:flex;align-items:center;position:relative;padding:0 13px;color:#77847d;font-size:12px;font-weight:600;cursor:pointer}.html-editor .html-editor-tabs button::after{content:'';position:absolute;right:13px;bottom:-1px;left:13px;height:2px;background:transparent}.html-editor .html-editor-tabs button:hover{color:#246f55}.html-editor .html-editor-tabs button.active{color:#1d684d}.html-editor .html-editor-tabs button.active::after{background:#287b5c}.html-editor .html-editor-tabs button:focus-visible{outline:2px solid rgba(40,123,92,.35);outline-offset:-4px;border-radius:4px}.html-editor button.asset-tool{display:inline-flex;align-items:center;gap:5px;border-color:#cddfd6;color:#256f55}.tool-divider{width:1px;height:22px;background:#dfe5e1;margin:0 2px}.html-format-tools label{flex:0 0 auto;height:28px;border:1px solid #e0e5e1;border-radius:6px;background:#fff;padding:0 5px;font-size:11px;display:flex;align-items:center;gap:3px}.html-format-tools input{width:20px;height:20px;border:0;padding:0}.html-canvas{display:block;width:100%;border:1px solid #e7ebe8;border-radius:8px;background:white}.html-source{width:100%;height:650px;border:1px solid #dfe4e0;border-radius:8px;overflow:hidden}
</style>
