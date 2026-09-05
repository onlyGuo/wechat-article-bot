const WEEK_NAMES={SUN:'周日',MON:'周一',TUE:'周二',WED:'周三',THU:'周四',FRI:'周五',SAT:'周六'}

const two=value=>String(value).padStart(2,'0')

export function describeQuartzCron(expression){
  const parts=(expression||'').trim().split(/\s+/)
  if(parts.length<6)return '尚未设置执行计划'
  const [second,minute,hour,day,month,week,year]=parts
  const time=`${two(hour)}:${two(minute)}${second==='0'?'':`:${two(second)}`}`
  if(year&&/^\d{4}$/.test(year)&&/^\d+$/.test(day)&&/^\d+$/.test(month)){
    return `一次性 · ${year}-${two(month)}-${two(day)} ${time}`
  }
  let match=second.match(/^0\/(\d+)$/)
  if(match)return `持续执行 · 每 ${match[1]} 秒`
  match=minute.match(/^0\/(\d+)$/)
  if(match&&hour==='*')return `持续执行 · 每 ${match[1]} 分钟`
  match=hour.match(/^0\/(\d+)$/)
  if(match)return `持续执行 · 每 ${match[1]} 小时，第 ${minute} 分钟`
  if(day==='*'&&month==='*'&&week==='?')return `每天 ${time}`
  if(day==='?'&&month==='*'&&week!=='?'){
    const days=week.split(',').map(value=>WEEK_NAMES[value]||value).join('、')
    return `每周 ${days} ${time}`
  }
  if(month==='*'&&week==='?'){
    const days=day.split(',').map(value=>value==='L'?'最后一天':`${value} 日`).join('、')
    return `每月 ${days} ${time}`
  }
  if(/^\d+$/.test(month)&&week==='?')return `每年 ${month} 月 ${day} 日 ${time}`
  return `自定义计划 · ${expression}`
}

