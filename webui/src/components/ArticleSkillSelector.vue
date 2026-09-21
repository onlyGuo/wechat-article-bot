<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { Check, ChevronDown, Sparkles, Star } from 'lucide-vue-next'
import { useI18n } from '../i18n'

const props = defineProps({
  modelValue: { type: String, default: '' },
  skills: { type: Array, default: () => [] },
  disabled: Boolean,
})
const emit = defineEmits(['update:modelValue'])
const { t } = useI18n()
const root = ref(), trigger = ref(), list = ref(), open = ref(false)
const optionElements = new Map()
const skillValue = skill => skill.classpathResources ? `builtin:${skill.classpathResources}` : `user:${skill.id}`
const defaultSkill = computed(() => props.skills.find(skill => skill.isDefault))
const missing = computed(() => props.modelValue && !props.skills.some(skill => skillValue(skill) === props.modelValue))
const options = computed(() => {
  const items = [{
    value: '',
    name: t('editor.defaultSkill', { name: defaultSkill.value?.name || t('editor.defaultStyle') }),
    description: t('skillSelector.followDefaultHint'),
    previewHtml: defaultSkill.value?.previewHtml,
    previewStatus: defaultSkill.value?.previewStatus,
    kind: 'default',
  }]
  if (missing.value) items.push({ value: props.modelValue, name: t('editor.deletedSkill'), description: t('skillSelector.deletedHint'), kind: 'missing' })
  return items.concat(props.skills.map(skill => ({ ...skill, value: skillValue(skill), kind: skill.builtIn ? 'builtIn' : 'custom' })))
})
const selected = computed(() => options.value.find(option => option.value === props.modelValue) || options.value[0])

function setOptionElement(value, element) {
  if (element) optionElements.set(value, element)
  else optionElements.delete(value)
}
function centerSelected() {
  const element = optionElements.get(props.modelValue || '')
  if (!element || !list.value) return
  list.value.scrollTop = Math.max(0, element.offsetTop - (list.value.clientHeight - element.offsetHeight) / 2)
}
async function show() {
  if (props.disabled) return
  open.value = true
  await nextTick()
  centerSelected()
  optionElements.get(props.modelValue || '')?.focus({ preventScroll: true })
}
function close(restoreFocus = false) {
  open.value = false
  if (restoreFocus) nextTick(() => trigger.value?.focus())
}
function toggle() { open.value ? close() : show() }
function choose(option) {
  emit('update:modelValue', option.value)
  close(true)
}
function move(event, delta) {
  event.preventDefault()
  const current = options.value.findIndex(option => option.value === event.currentTarget.dataset.value)
  const target = options.value[(current + delta + options.value.length) % options.value.length]
  optionElements.get(target.value)?.focus()
}
function onDocumentPointerDown(event) { if (open.value && !root.value?.contains(event.target)) close() }
function onEscape(event) { if (event.key === 'Escape' && open.value) { event.stopPropagation(); close(true) } }
onMounted(() => { document.addEventListener('pointerdown', onDocumentPointerDown); document.addEventListener('keydown', onEscape) })
onBeforeUnmount(() => { document.removeEventListener('pointerdown', onDocumentPointerDown); document.removeEventListener('keydown', onEscape) })
</script>

<template>
  <div ref="root" class="skill-selector" :class="{open,disabled}">
    <button ref="trigger" type="button" class="skill-selector-trigger" :disabled="disabled" aria-haspopup="listbox" :aria-expanded="open" @click="toggle" @keydown.down.prevent="show">
      <span class="selected-mark"><Star v-if="selected.kind==='default'" :size="14"/><Sparkles v-else :size="14"/></span>
      <span class="selected-copy"><strong>{{selected.name}}</strong><small>{{selected.description||t('skills.noDescription')}}</small></span>
      <ChevronDown :size="16" />
    </button>
    <Transition name="skill-options">
      <div v-if="open" ref="list" class="skill-selector-options" role="listbox" :aria-label="t('editor.articleSkill')">
        <button v-for="option in options" :key="option.value||'__default__'" :ref="element=>setOptionElement(option.value,element)" type="button" role="option" class="skill-option" :class="{selected:option.value===modelValue,missing:option.kind==='missing'}" :aria-selected="option.value===modelValue" :data-value="option.value" @click="choose(option)" @keydown.down="move($event,1)" @keydown.up="move($event,-1)" @keydown.home.prevent="optionElements.get(options[0].value)?.focus()" @keydown.end.prevent="optionElements.get(options.at(-1).value)?.focus()">
          <span class="skill-option-preview">
            <iframe v-if="option.previewHtml" :srcdoc="option.previewHtml" sandbox="" loading="lazy" tabindex="-1" aria-hidden="true"></iframe>
            <span v-else class="skill-option-placeholder"><Sparkles :size="18"/><small>{{option.previewStatus==='GENERATING'?t('skills.generatingPreview'):option.previewStatus==='FAILED'?t('skills.previewFailed'):t('skills.previewPending')}}</small></span>
          </span>
          <span class="skill-option-copy">
            <span class="skill-option-heading"><strong>{{option.name}}</strong><i v-if="option.kind==='default'">{{t('skills.default')}}</i><i v-else-if="option.kind==='builtIn'">{{t('skillSelector.builtIn')}}</i><i v-else-if="option.kind==='custom'">{{t('skillSelector.custom')}}</i></span>
            <small>{{option.description||t('skills.noDescription')}}</small>
          </span>
          <span class="skill-option-check"><Check v-if="option.value===modelValue" :size="15"/></span>
        </button>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
.skill-selector{position:relative;min-width:0}.skill-selector-trigger{width:100%;min-height:52px;display:grid;grid-template-columns:28px minmax(0,1fr) 18px;align-items:center;gap:8px;padding:8px 9px;border:1px solid #dce3de;border-radius:9px;background:#f9faf8;color:#26382f;text-align:left}.skill-selector-trigger:hover,.skill-selector.open .skill-selector-trigger{border-color:#a8bdb2;background:#fff}.skill-selector-trigger:focus-visible{outline:0;border-color:#67a58c;box-shadow:0 0 0 3px rgba(31,122,90,.09)}.selected-mark{width:27px;height:27px;display:grid;place-items:center;border-radius:7px;background:#e7f2ec;color:#28785a}.selected-copy{min-width:0;display:flex;flex-direction:column;gap:3px}.selected-copy strong,.selected-copy small{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.selected-copy strong{font-size:11px}.selected-copy small{font-size:9px;color:#89958e}.skill-selector-trigger>svg{color:#7f8c85;transition:transform .18s}.skill-selector.open .skill-selector-trigger>svg{transform:rotate(180deg)}
.skill-selector-options{position:absolute;z-index:80;top:calc(100% + 8px);right:0;left:0;max-height:min(480px,calc(100vh - 245px));overflow-y:auto;padding:7px;border:1px solid #d9e1dc;border-radius:12px;background:rgba(255,255,255,.98);box-shadow:0 18px 45px rgba(20,38,31,.18);scrollbar-width:thin}.skill-option{width:100%;min-height:92px;display:grid;grid-template-columns:108px minmax(0,1fr) 20px;align-items:center;gap:10px;padding:8px;border:1px solid transparent;border-radius:9px;background:#fff;color:#25372f;text-align:left}.skill-option+.skill-option{margin-top:6px}.skill-option:hover,.skill-option:focus-visible{outline:0;border-color:#cddbd3;background:#f7faf8}.skill-option.selected{border-color:#92bda9;background:#f0f8f4;box-shadow:inset 3px 0 #2d8463}.skill-option.missing{opacity:.68}.skill-option-preview{position:relative;width:108px;height:72px;display:block;overflow:hidden;border:1px solid #e1e6e2;border-radius:7px;background:#f1f4f2}.skill-option-preview iframe{position:absolute;top:0;left:0;width:360px;height:240px;border:0;background:#fff;pointer-events:none;transform:scale(.3);transform-origin:top left}.skill-option-placeholder{width:100%;height:100%;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:5px;color:#829188;text-align:center}.skill-option-placeholder small{max-width:94px;font-size:8px;line-height:1.25}.skill-option-copy{min-width:0;align-self:stretch;display:flex;flex-direction:column;justify-content:center;gap:7px}.skill-option-heading{display:flex;align-items:center;gap:5px;min-width:0}.skill-option-heading strong{min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:11px}.skill-option-heading i{flex:0 0 auto;border-radius:4px;background:#e6f2ec;color:#34765a;padding:2px 4px;font-size:8px;font-style:normal}.skill-option-copy>small{display:-webkit-box;overflow:hidden;color:#7d8a83;font-size:9px;line-height:1.5;-webkit-box-orient:vertical;-webkit-line-clamp:3}.skill-option-check{width:19px;height:19px;display:grid;place-items:center;border-radius:50%;color:#fff;background:#2c8060;opacity:0}.skill-option.selected .skill-option-check{opacity:1}.skill-options-enter-active,.skill-options-leave-active{transition:opacity .14s,transform .14s}.skill-options-enter-from,.skill-options-leave-to{opacity:0;transform:translateY(-5px)}
</style>
