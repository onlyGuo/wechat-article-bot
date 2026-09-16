<script setup>
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useI18n } from '../i18n'

const props = defineProps({ content: { type: String, default: '<p></p>' }, readonly: Boolean, disabled: Boolean })
const { t } = useI18n()
const emit = defineEmits(['update'])
const frame = ref(), sourceMode = ref(false), html = ref(props.content), height = ref(550)
let observer, selection, attachedDocument
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
function setContent(value, notify = false) { html.value = value ?? '<p></p>'; render(); if (notify) { record(); emit('update') } else { history = [html.value]; historyIndex = 0 } }
function commitSource(event) { html.value = event.target.value; record(); emit('update') }
async function toggleSource() { sourceMode.value = !sourceMode.value; await nextTick(); if (!sourceMode.value) render() }
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
watch(() => props.disabled, value => { if (frame.value?.contentDocument?.body) frame.value.contentDocument.body.contentEditable = String(!props.readonly && !value) })
onBeforeUnmount(() => { observer?.disconnect(); attachedDocument?.removeEventListener('selectionchange', rememberSelection) })
function getText() { const content = fragment(html.value); content.querySelectorAll('style').forEach(el=>el.remove()); return content.textContent || '' }
defineExpose({ getHTML:()=>html.value, getText, getBlocks, setContent, deleteBlocks, insertAtBlockIndex, insertImage })
</script>

<template>
  <div class="html-editor">
    <div v-if="!readonly" class="html-editor-controls">
      <div class="html-editor-modes"><button type="button" :class="{active:!sourceMode}" @click="sourceMode&&toggleSource()">{{ t('htmlEditor.visual') }}</button><button type="button" :class="{active:sourceMode}" @click="!sourceMode&&toggleSource()">{{ t('htmlEditor.source') }}</button><span>{{ t('htmlEditor.freeLayout') }}</span></div>
      <div v-if="!sourceMode" class="html-format-tools" :inert="disabled">
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
    <textarea v-if="sourceMode" class="html-source" :aria-label="t('htmlEditor.sourceLabel')" :value="html" :disabled="disabled" spellcheck="false" @input="commitSource"></textarea>
    <iframe v-show="!sourceMode" ref="frame" :title="readonly?t('htmlEditor.previewTitle'):t('htmlEditor.editTitle')" class="html-canvas" sandbox="allow-same-origin" :srcdoc="frameDocument" :style="{height:`${height}px`}" @load="loaded"></iframe>
  </div>
</template>

<style scoped>
.html-editor{width:100%;min-width:0}.html-editor-controls{border-block:1px solid #eee;margin-bottom:24px;padding:10px 0}.html-editor-modes,.html-format-tools{display:flex;gap:6px;align-items:center;flex-wrap:wrap}.html-editor-modes{margin-bottom:8px}.html-editor-modes span{font-size:11px;color:#999;margin-left:auto}.html-editor button,.html-editor select{font:inherit;font-size:12px;border:1px solid #e5e7eb;border-radius:5px;background:white;padding:5px 8px;cursor:pointer}.html-editor button.active{background:#e6f6ee;color:#087d49}.html-format-tools label{font-size:12px;display:flex;align-items:center;gap:3px}.html-format-tools input{width:24px;height:24px;border:0;padding:0}.html-canvas{display:block;width:100%;border:0;background:white}.html-source{width:100%;min-height:650px;resize:vertical;font:13px/1.7 ui-monospace,monospace;padding:16px;border:1px solid #ddd;border-radius:8px;tab-size:2}
</style>
