<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { CalendarClock, Check, Copy } from 'lucide-vue-next'
import { describeQuartzCron } from '../utils/cron'
import { useI18n } from '../i18n'

const props=defineProps({modelValue:{type:String,default:''}})
const emit=defineEmits(['update:modelValue'])
const { t } = useI18n()
const modes=computed(()=>[
  ['once',t('cron.once')],['interval',t('cron.interval')],['daily',t('cron.daily')],['weekly',t('cron.weekly')],
  ['monthly',t('cron.monthly')],['yearly',t('cron.yearly')],['advanced',t('cron.advanced')]
])
const weekOptions=computed(()=>[['MON',t('cron.weekNames.mon')],['TUE',t('cron.weekNames.tue')],['WED',t('cron.weekNames.wed')],['THU',t('cron.weekNames.thu')],['FRI',t('cron.weekNames.fri')],['SAT',t('cron.weekNames.sat')],['SUN',t('cron.weekNames.sun')]])
const monthOptions=Array.from({length:12},(_,index)=>index+1)
const dayOptions=Array.from({length:31},(_,index)=>index+1)
const pad=value=>String(value).padStart(2,'0')
const clamp=(value,min,max)=>Math.max(min,Math.min(max,Number(value)||0))
const tomorrow=()=>{
  const value=new Date(Date.now()+24*60*60*1000)
  value.setHours(9,0,0,0)
  return `${value.getFullYear()}-${pad(value.getMonth()+1)}-${pad(value.getDate())}T${pad(value.getHours())}:${pad(value.getMinutes())}`
}

const mode=ref('daily')
const onceAt=ref(tomorrow())
const time=reactive({hour:9,minute:0,second:0})
const interval=reactive({unit:'MINUTES',step:30,minute:0,second:0})
const weekDays=ref(['MON'])
const monthDays=ref(['1'])
const yearly=reactive({month:1,day:1})
const advanced=reactive({second:'0',minute:'0',hour:'9',day:'*',month:'*',week:'?',year:''})
const copied=ref(false)
let hydrating=true

const expression=computed(()=>{
  const second=clamp(time.second,0,59),minute=clamp(time.minute,0,59),hour=clamp(time.hour,0,23)
  if(mode.value==='once'){
    if(!onceAt.value)return ''
    const match=onceAt.value.match(/^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/)
    return match?`0 ${Number(match[5])} ${Number(match[4])} ${Number(match[3])} ${Number(match[2])} ? ${match[1]}`:''
  }
  if(mode.value==='interval'){
    const step=interval.unit==='HOURS'?clamp(interval.step,1,23):clamp(interval.step,1,59)
    if(interval.unit==='SECONDS')return `0/${step} * * * * ?`
    if(interval.unit==='MINUTES')return `${clamp(interval.second,0,59)} 0/${step} * * * ?`
    return `${clamp(interval.second,0,59)} ${clamp(interval.minute,0,59)} 0/${step} * * ?`
  }
  if(mode.value==='daily')return `${second} ${minute} ${hour} * * ?`
  if(mode.value==='weekly')return `${second} ${minute} ${hour} ? * ${weekDays.value.length?weekDays.value.join(','):'MON'}`
  if(mode.value==='monthly')return `${second} ${minute} ${hour} ${monthDays.value.length?monthDays.value.join(','):'1'} * ?`
  if(mode.value==='yearly')return `${second} ${minute} ${hour} ${clamp(yearly.day,1,31)} ${clamp(yearly.month,1,12)} ?`
  return [advanced.second,advanced.minute,advanced.hour,advanced.day,advanced.month,advanced.week,advanced.year]
    .map(value=>String(value??'').trim()).filter((value,index)=>index<6||value).join(' ')
})

const summary=computed(()=>describeQuartzCron(expression.value))
const highFrequency=computed(()=>mode.value==='interval'&&(
  interval.unit==='SECONDS'||(interval.unit==='MINUTES'&&Number(interval.step)<5)))

function setClock(hour,minute,second){time.hour=Number(hour);time.minute=Number(minute);time.second=Number(second)}
function toggle(list,value){const index=list.indexOf(value);if(index>=0){if(list.length>1)list.splice(index,1)}else list.push(value)}
function toggleMonthDay(value){
  if(value==='L'){monthDays.value=['L'];return}
  if(monthDays.value.includes('L'))monthDays.value=[]
  toggle(monthDays.value,value)
}
function load(value){
  const parts=(value||'').trim().split(/\s+/)
  if(parts.length<6){mode.value='daily';return}
  const [second,minute,hour,day,month,week,year]=parts
  if(year&&/^\d{4}$/.test(year)&&[second,minute,hour,day,month].every(item=>/^\d+$/.test(item))&&week==='?'){
    mode.value='once';onceAt.value=`${year}-${pad(month)}-${pad(day)}T${pad(hour)}:${pad(minute)}`;return
  }
  let match=second.match(/^0\/(\d+)$/)
  if(match){mode.value='interval';interval.unit='SECONDS';interval.step=Number(match[1]);return}
  match=minute.match(/^0\/(\d+)$/)
  if(match&&hour==='*'){mode.value='interval';interval.unit='MINUTES';interval.step=Number(match[1]);interval.second=Number(second)||0;return}
  match=hour.match(/^0\/(\d+)$/)
  if(match){mode.value='interval';interval.unit='HOURS';interval.step=Number(match[1]);interval.minute=Number(minute)||0;interval.second=Number(second)||0;return}
  if([second,minute,hour].every(item=>/^\d+$/.test(item))&&day==='*'&&month==='*'&&week==='?'){
    mode.value='daily';setClock(hour,minute,second);return
  }
  if([second,minute,hour].every(item=>/^\d+$/.test(item))&&day==='?'&&month==='*'&&week!=='?'){
    mode.value='weekly';setClock(hour,minute,second);weekDays.value=week.split(',');return
  }
  if([second,minute,hour].every(item=>/^\d+$/.test(item))&&month==='*'&&week==='?'&&/^([1-9]|[12]\d|3[01]|L)(,([1-9]|[12]\d|3[01]|L))*$/.test(day)){
    mode.value='monthly';setClock(hour,minute,second);monthDays.value=day.split(',');return
  }
  if([second,minute,hour,day,month].every(item=>/^\d+$/.test(item))&&week==='?'){
    mode.value='yearly';setClock(hour,minute,second);yearly.day=Number(day);yearly.month=Number(month);return
  }
  mode.value='advanced';Object.assign(advanced,{second,minute,hour,day,month,week,year:year||''})
}

async function copyExpression(){
  try{await navigator.clipboard.writeText(expression.value);copied.value=true;setTimeout(()=>copied.value=false,1500)}catch{copied.value=false}
}

onMounted(()=>{load(props.modelValue);queueMicrotask(()=>{hydrating=false;if(expression.value!==props.modelValue)emit('update:modelValue',expression.value)})})
watch(expression,value=>{if(!hydrating)emit('update:modelValue',value)})
</script>

<template>
  <div class="cron-builder">
    <div class="cron-mode-tabs">
      <button v-for="item in modes" :key="item[0]" type="button" :class="{active:mode===item[0]}" @click="mode=item[0]">{{item[1]}}</button>
    </div>

    <div class="cron-config">
      <div v-if="mode==='once'" class="cron-row">
        <label>{{ t('cron.dateTime') }}<input v-model="onceAt" type="datetime-local" required></label>
        <p>{{ t('cron.onceHint') }}</p>
      </div>

      <div v-else-if="mode==='interval'" class="cron-row interval-row">
        <label>{{ t('cron.intervalUnit') }}<select v-model="interval.unit"><option value="SECONDS">{{ t('cron.seconds') }}</option><option value="MINUTES">{{ t('cron.minutes') }}</option><option value="HOURS">{{ t('cron.hours') }}</option></select></label>
        <label>{{ t('cron.every') }}<input v-model.number="interval.step" type="number" min="1" :max="interval.unit==='HOURS'?23:59"><span>{{interval.unit==='SECONDS'?t('cron.seconds'):interval.unit==='MINUTES'?t('cron.minutes'):t('cron.hours')}}</span></label>
        <label v-if="interval.unit==='HOURS'">{{ t('cron.minuteOfHour') }}<input v-model.number="interval.minute" type="number" min="0" max="59"></label>
        <label v-if="interval.unit!=='SECONDS'">{{ t('cron.seconds') }}<input v-model.number="interval.second" type="number" min="0" max="59"></label>
        <p v-if="highFrequency" class="cron-warning">{{ t('cron.highFrequency') }}</p>
      </div>

      <div v-else-if="mode==='daily'" class="cron-row"><div class="cron-time"><label>{{ t('cron.hour') }}<input v-model.number="time.hour" type="number" min="0" max="23"></label><b>:</b><label>{{ t('cron.minute') }}<input v-model.number="time.minute" type="number" min="0" max="59"></label><b>:</b><label>{{ t('cron.second') }}<input v-model.number="time.second" type="number" min="0" max="59"></label></div><p>{{ t('cron.dailyHint') }}</p></div>

      <div v-else-if="mode==='weekly'" class="cron-stack">
        <span class="cron-label">{{ t('cron.weekdays') }}</span><div class="cron-chips week"><button v-for="item in weekOptions" :key="item[0]" type="button" :class="{active:weekDays.includes(item[0])}" @click="toggle(weekDays,item[0])">{{item[1]}}</button></div>
        <div class="cron-time"><label>{{ t('cron.hour') }}<input v-model.number="time.hour" type="number" min="0" max="23"></label><b>:</b><label>{{ t('cron.minute') }}<input v-model.number="time.minute" type="number" min="0" max="59"></label><b>:</b><label>{{ t('cron.second') }}<input v-model.number="time.second" type="number" min="0" max="59"></label></div>
      </div>

      <div v-else-if="mode==='monthly'" class="cron-stack">
        <span class="cron-label">{{ t('cron.days') }}</span><div class="cron-chips days"><button v-for="day in dayOptions" :key="day" type="button" :class="{active:monthDays.includes(String(day))}" @click="toggleMonthDay(String(day))">{{day}}</button><button type="button" :class="{active:monthDays.includes('L')}" @click="toggleMonthDay('L')">{{ t('cron.last') }}</button></div>
        <div class="cron-time"><label>{{ t('cron.hour') }}<input v-model.number="time.hour" type="number" min="0" max="23"></label><b>:</b><label>{{ t('cron.minute') }}<input v-model.number="time.minute" type="number" min="0" max="59"></label><b>:</b><label>{{ t('cron.second') }}<input v-model.number="time.second" type="number" min="0" max="59"></label></div>
      </div>

      <div v-else-if="mode==='yearly'" class="cron-row yearly-row">
        <label>{{ t('cron.month') }}<select v-model.number="yearly.month"><option v-for="month in monthOptions" :key="month" :value="month">{{month}}</option></select></label>
        <label>{{ t('cron.date') }}<input v-model.number="yearly.day" type="number" min="1" max="31"></label>
        <div class="cron-time"><label>{{ t('cron.hour') }}<input v-model.number="time.hour" type="number" min="0" max="23"></label><b>:</b><label>{{ t('cron.minute') }}<input v-model.number="time.minute" type="number" min="0" max="59"></label><b>:</b><label>{{ t('cron.second') }}<input v-model.number="time.second" type="number" min="0" max="59"></label></div>
      </div>

      <div v-else class="cron-advanced">
        <p>{{ t('cron.advancedHint') }}</p>
        <div><label>{{ t('cron.second') }}<input v-model="advanced.second" required></label><label>{{ t('cron.minute') }}<input v-model="advanced.minute" required></label><label>{{ t('cron.hour') }}<input v-model="advanced.hour" required></label><label>{{ t('cron.day') }}<input v-model="advanced.day" required></label><label>{{ t('cron.month') }}<input v-model="advanced.month" required></label><label>{{ t('cron.week') }}<input v-model="advanced.week" required></label><label>{{ t('cron.yearOptional') }}<input v-model="advanced.year"></label></div>
      </div>
    </div>

    <div class="cron-preview">
      <span class="cron-preview-icon"><CalendarClock :size="18"/></span><div><small>{{ t('cron.preview') }}</small><strong>{{summary}}</strong><code>{{expression||t('cron.waiting')}}</code></div>
      <button type="button" :title="t('cron.copy')" @click="copyExpression"><Check v-if="copied" :size="16"/><Copy v-else :size="16"/></button>
    </div>
  </div>
</template>
