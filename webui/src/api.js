const API_BASE = import.meta.env.VITE_API_BASE || ''

export function token() {
  return localStorage.getItem('wechat_bot_token') || ''
}

function handleUnauthorized(response) {
  if (response.status !== 401) return
  localStorage.removeItem('wechat_bot_token')
  if (location.pathname !== '/login') location.replace('/login')
}

export async function api(path, options = {}) {
  const headers = new Headers(options.headers || {})
  if (!(options.body instanceof FormData)) headers.set('Content-Type', 'application/json')
  if (token()) headers.set('Authorization', `Bearer ${token()}`)
  const response = await fetch(`${API_BASE}${path}`, { ...options, headers })
  const payload = await response.json().catch(() => null)
  if (!response.ok || payload?.success === false) {
    handleUnauthorized(response)
    throw new Error(payload?.message || `请求失败 (${response.status})`)
  }
  return payload?.data
}

export async function stream(path, body, onEvent) {
  const response = await fetch(`${API_BASE}${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream', Authorization: `Bearer ${token()}` },
    body: JSON.stringify(body),
  })
  if (!response.ok) {
    const payload = await response.json().catch(() => null)
    handleUnauthorized(response)
    throw new Error(payload?.message || `请求失败 (${response.status})`)
  }
  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let eventName = 'message'
  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    const blocks = buffer.split('\n\n')
    buffer = blocks.pop() || ''
    for (const block of blocks) {
      let data = ''
      for (const line of block.split('\n')) {
        if (line.startsWith('event:')) eventName = line.slice(6).trim()
        if (line.startsWith('data:')) data += line.slice(5).trim()
      }
      if (data) onEvent(eventName, JSON.parse(data))
      eventName = 'message'
    }
  }
}

export async function uploadAsset(file, accountId) {
  const body = new FormData()
  body.append('file', file)
  const query = accountId ? `?accountId=${accountId}` : ''
  return api(`/api/assets${query}`, { method: 'POST', body })
}
