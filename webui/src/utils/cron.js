import { t } from '../i18n'

const WEEK_KEYS={SUN:'sun',MON:'mon',TUE:'tue',WED:'wed',THU:'thu',FRI:'fri',SAT:'sat'}

const two=value=>String(value).padStart(2,'0')

export function describeQuartzCron(expression){
  const parts=(expression||'').trim().split(/\s+/)
  if(parts.length<6)return t('cron.summaries.notConfigured')
  const [second,minute,hour,day,month,week,year]=parts
  const time=`${two(hour)}:${two(minute)}${second==='0'?'':`:${two(second)}`}`
  if(year&&/^\d{4}$/.test(year)&&/^\d+$/.test(day)&&/^\d+$/.test(month)){
    return t('cron.summaries.once',{date:`${year}-${two(month)}-${two(day)}`,time})
  }
  let match=second.match(/^0\/(\d+)$/)
  if(match)return t('cron.summaries.everySeconds',{count:match[1]})
  match=minute.match(/^0\/(\d+)$/)
  if(match&&hour==='*')return t('cron.summaries.everyMinutes',{count:match[1]})
  match=hour.match(/^0\/(\d+)$/)
  if(match)return t('cron.summaries.everyHours',{count:match[1],minute})
  if(day==='*'&&month==='*'&&week==='?')return t('cron.summaries.daily',{time})
  if(day==='?'&&month==='*'&&week!=='?'){
    const days=week.split(',').map(value=>t(`cron.weekNames.${WEEK_KEYS[value]}`)).join(', ')
    return t('cron.summaries.weekly',{days,time})
  }
  if(month==='*'&&week==='?'){
    const days=day.split(',').map(value=>value==='L'?t('cron.lastDay'):t('cron.dayOfMonth',{day:value})).join(', ')
    return t('cron.summaries.monthly',{days,time})
  }
  if(/^\d+$/.test(month)&&week==='?')return t('cron.summaries.yearly',{month,day,time})
  return t('cron.summaries.custom',{expression})
}
