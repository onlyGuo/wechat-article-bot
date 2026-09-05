<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { CalendarClock, Check, Copy } from 'lucide-vue-next'
import { describeQuartzCron } from '../utils/cron'

const props=defineProps({modelValue:{type:String,default:''}})
const emit=defineEmits(['update:modelValue'])
const modes=[
  ['once','一次性'],['interval','持续间隔'],['daily','每天'],['weekly','每周'],
  ['monthly','每月'],['yearly','每年'],['advanced','高级']
]
const weekOptions=[['MON','一'],['TUE','二'],['WED','三'],['THU','四'],['FRI','五'],['SAT','六'],['SUN','日']]
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
        <label>执行日期与时间<input v-model="onceAt" type="datetime-local" required></label>
        <p>任务只执行一次，成功或失败后都会自动停用，不再重复触发。</p>
      </div>

      <div v-else-if="mode==='interval'" class="cron-row interval-row">
        <label>间隔单位<select v-model="interval.unit"><option value="SECONDS">秒</option><option value="MINUTES">分钟</option><option value="HOURS">小时</option></select></label>
        <label>每隔<input v-model.number="interval.step" type="number" min="1" :max="interval.unit==='HOURS'?23:59"><span>{{interval.unit==='SECONDS'?'秒':interval.unit==='MINUTES'?'分钟':'小时'}}</span></label>
        <label v-if="interval.unit==='HOURS'">第几分钟<input v-model.number="interval.minute" type="number" min="0" max="59"></label>
        <label v-if="interval.unit!=='SECONDS'">秒<input v-model.number="interval.second" type="number" min="0" max="59"></label>
        <p v-if="highFrequency" class="cron-warning">高频任务可能产生大量 AI 调用与费用，请确认确实需要。</p>
      </div>

      <div v-else-if="mode==='daily'" class="cron-row"><div class="cron-time"><label>时<input v-model.number="time.hour" type="number" min="0" max="23"></label><b>:</b><label>分<input v-model.number="time.minute" type="number" min="0" max="59"></label><b>:</b><label>秒<input v-model.number="time.second" type="number" min="0" max="59"></label></div><p>每天在指定时间执行。</p></div>

      <div v-else-if="mode==='weekly'" class="cron-stack">
        <span class="cron-label">执行星期</span><div class="cron-chips week"><button v-for="item in weekOptions" :key="item[0]" type="button" :class="{active:weekDays.includes(item[0])}" @click="toggle(weekDays,item[0])">{{item[1]}}</button></div>
        <div class="cron-time"><label>时<input v-model.number="time.hour" type="number" min="0" max="23"></label><b>:</b><label>分<input v-model.number="time.minute" type="number" min="0" max="59"></label><b>:</b><label>秒<input v-model.number="time.second" type="number" min="0" max="59"></label></div>
      </div>

      <div v-else-if="mode==='monthly'" class="cron-stack">
        <span class="cron-label">执行日期（可多选）</span><div class="cron-chips days"><button v-for="day in dayOptions" :key="day" type="button" :class="{active:monthDays.includes(String(day))}" @click="toggleMonthDay(String(day))">{{day}}</button><button type="button" :class="{active:monthDays.includes('L')}" @click="toggleMonthDay('L')">最后</button></div>
        <div class="cron-time"><label>时<input v-model.number="time.hour" type="number" min="0" max="23"></label><b>:</b><label>分<input v-model.number="time.minute" type="number" min="0" max="59"></label><b>:</b><label>秒<input v-model.number="time.second" type="number" min="0" max="59"></label></div>
      </div>

      <div v-else-if="mode==='yearly'" class="cron-row yearly-row">
        <label>月份<select v-model.number="yearly.month"><option v-for="month in monthOptions" :key="month" :value="month">{{month}} 月</option></select></label>
        <label>日期<input v-model.number="yearly.day" type="number" min="1" max="31"></label>
        <div class="cron-time"><label>时<input v-model.number="time.hour" type="number" min="0" max="23"></label><b>:</b><label>分<input v-model.number="time.minute" type="number" min="0" max="59"></label><b>:</b><label>秒<input v-model.number="time.second" type="number" min="0" max="59"></label></div>
      </div>

      <div v-else class="cron-advanced">
        <p>分别设置 Quartz Cron 的七个字段。支持 <code>*</code>、<code>?</code>、范围、列表、步长、<code>L</code>、<code>W</code> 和 <code>#</code> 等语法。</p>
        <div><label>秒<input v-model="advanced.second" required></label><label>分<input v-model="advanced.minute" required></label><label>时<input v-model="advanced.hour" required></label><label>日<input v-model="advanced.day" required></label><label>月<input v-model="advanced.month" required></label><label>星期<input v-model="advanced.week" required></label><label>年（可选）<input v-model="advanced.year"></label></div>
      </div>
    </div>

    <div class="cron-preview">
      <span class="cron-preview-icon"><CalendarClock :size="18"/></span><div><small>执行计划</small><strong>{{summary}}</strong><code>{{expression||'等待配置'}}</code></div>
      <button type="button" title="复制 Cron" @click="copyExpression"><Check v-if="copied" :size="16"/><Copy v-else :size="16"/></button>
    </div>
  </div>
</template>
