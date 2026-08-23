const TOKEN_KEY = 'sw-web-token'

export const getToken = () => localStorage.getItem(TOKEN_KEY) || ''
export const setToken = (token) => localStorage.setItem(TOKEN_KEY, token)
export const clearToken = () => localStorage.removeItem(TOKEN_KEY)

function unwrap(payload) {
  return payload && Object.prototype.hasOwnProperty.call(payload, 'data') ? payload.data : payload
}

export async function request(path, options = {}) {
  const headers = new Headers(options.headers || {})
  const token = getToken()
  if (token) headers.set('Authorization', `Bearer ${token}`)
  if (options.body && !(options.body instanceof FormData) && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }

  const response = await fetch(path, { ...options, headers })
  const text = await response.text()
  let payload = null
  try { payload = text ? JSON.parse(text) : null } catch { payload = text }
  if (!response.ok) {
    const error = new Error(payload?.msg || payload?.message || `请求失败（${response.status}）`)
    error.status = response.status
    throw error
  }
  return unwrap(payload)
}

export const login = (body) => request('/user/api/public/auth/login', { method: 'POST', body: JSON.stringify(body) })
export const register = (body) => request('/user/api/public/auth/register', { method: 'POST', body: JSON.stringify(body) })
export const getProfile = () => request('/user/api/me/profile/basic')
export const getProfileDetail = () => request('/user/api/me/profile')
export const getProfileStats = () => request('/user/api/me/profile/stats')
export const updateProfile = (body) => request('/user/api/me/profile', { method: 'PUT', body: JSON.stringify(body) })
export const searchCreators = (query) => request(`/user/api/public/search?query=${encodeURIComponent(query)}`)
export const followCreator = (id) => request(`/user/api/me/follow/${id}`, { method: 'POST' })
export const unfollowCreator = (id) => request(`/user/api/me/follow/${id}`, { method: 'DELETE' })
export const getFeed = (cursor = '') => request(`/video/api/public/feed?pageSize=12${cursor ? `&cursor=${encodeURIComponent(cursor)}` : ''}`)
export const getLegacyFeed = () => request('/video/api/public/list')
export const getFollowFeed = (cursor = '') => request(`/video/api/me/follow-feed?pageSize=12${cursor ? `&cursor=${encodeURIComponent(cursor)}` : ''}`)
export const searchVideos = (keyword) => request(`/video/api/public/search?keyword=${encodeURIComponent(keyword)}`)
export const likeVideo = (id, active) => request(`/video/api/me/interaction/like/${id}`, { method: active ? 'POST' : 'DELETE' })
export const favoriteVideo = (id, active) => request(`/video/api/me/interaction/favorite/${id}`, { method: active ? 'POST' : 'DELETE' })
export const getComments = (id) => request(`/video/api/public/interaction/comment/${id}`)
export const postComment = (id, content) => request(`/video/api/me/interaction/comment/${id}`, {
  method: 'POST',
  body: JSON.stringify({ parentId: null, content })
})
export const getMyPublished = () => request('/video/api/me/published')
export const getMyProcessing = () => request('/video/api/me/processing')
export const getMyRejected = () => request('/video/api/me/rejected')
export const getMyFavorites = () => request('/video/api/me/favorite')
export const getCreatorAnalytics = () => request('/video/api/me/analytics')
export const recordVideoView = (id) => request(`/video/api/me/view/${id}`, { method: 'POST' })
export const deletePublishedVideos = (ids) => request('/video/api/me/published', { method: 'DELETE', body: JSON.stringify({ ids: ids.map(Number) }) })
export const getPresignedVideo = () => request('/video/api/me/presign-put-object', { method: 'POST' })
export const finishUpload = (taskId) => request(`/video/api/me/end?taskId=${taskId}`, { method: 'POST' })

export async function submitVideo({ videoId, description, tags, cover }) {
  const form = new FormData()
  form.append('videoId', videoId)
  form.append('description', description)
  tags.forEach((tag) => form.append('addedTagList', tag))
  if (cover) form.append('cover', cover)
  return request('/video/api/me', { method: 'POST', body: form })
}

export async function streamAssistant(message, onEvent) {
  const headers = { 'Content-Type': 'application/json' }
  const token = getToken()
  if (token) headers.Authorization = `Bearer ${token}`
  const response = await fetch('/ai/api/creator-assistant/stream', {
    method: 'POST', headers, body: JSON.stringify({ message })
  })
  if (!response.ok) throw new Error(`AI 通道不可用（${response.status}）`)
  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  while (true) {
    const { value, done } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    const chunks = buffer.split('\n\n')
    buffer = chunks.pop() || ''
    chunks.forEach((chunk) => {
      const event = chunk.match(/^event:\s*(.*)$/m)?.[1] || 'message'
      const data = chunk.match(/^data:\s*(.*)$/m)?.[1]
      if (data) onEvent({ event, data })
    })
  }
}
