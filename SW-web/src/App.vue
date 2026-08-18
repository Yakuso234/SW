<script setup>
import { computed, onMounted, ref } from 'vue'
import {
  clearToken, favoriteVideo, finishUpload, getComments, getFeed, getFollowFeed, getLegacyFeed, getMyProcessing,
  getMyPublished, getMyRejected, getPresignedVideo, getProfile, getToken, likeVideo, login, postComment, register,
  request, searchVideos, setToken, streamAssistant, submitVideo
} from './api'

const view = ref('feed')
const feedMode = ref('public')
const search = ref('')
const videos = ref([])
const selectedVideo = ref(null)
const comments = ref([])
const commentText = ref('')
const loading = ref(false)
const notice = ref('')
const noticeType = ref('ok')
const demoMode = ref(false)
const token = ref(getToken())
const profile = ref(null)
const authOpen = ref(false)
const authMode = ref('login')
const authForm = ref({ phoneNumber: '', password: '' })
const creatorForm = ref({ description: '', tags: '可靠发布,短视频' })
const selectedFile = ref(null)
const selectedCover = ref(null)
const uploadState = ref('等待选择媒体')
const processing = ref([])
const published = ref([])
const rejected = ref([])
const aiInput = ref('')
const aiBusy = ref(false)
const aiMessages = ref([{ role: 'ai', text: '创作者助手已上线。你可以问我：某个视频为什么失败？或者帮我生成标题、简介和标签。' }])

const demoVideos = [
  { id: 'demo-01', creatorId: '101', creatorName: 'NEON//MORI', description: '夜行城市的蓝色脉冲，记录一次可靠发布链路。', likes: 1280, comments: 86, favorites: 340, isLike: false, isFavorite: false, palette: ['#122c54', '#ff267f'] },
  { id: 'demo-02', creatorId: '102', creatorName: 'VOID WALKER', description: '当 FFmpeg 把噪点切成星河，失败也可以被诊断。', likes: 934, comments: 54, favorites: 211, isLike: false, isFavorite: true, palette: ['#063b45', '#753dff'] },
  { id: 'demo-03', creatorId: '103', creatorName: 'KIRA_404', description: '关注 Feed 的下一站：Inbox、重试和可审计恢复。', likes: 768, comments: 39, favorites: 187, isLike: true, isFavorite: false, palette: ['#502131', '#ff8a1f'] },
  { id: 'demo-04', creatorId: '104', creatorName: 'CHROME CHILD', description: '从预签名直传到 Outbox SUCCESS 的一条链路。', likes: 1520, comments: 122, favorites: 486, isLike: false, isFavorite: false, palette: ['#06384a', '#1b8cff'] }
]

const isLoggedIn = computed(() => Boolean(token.value))
const pageTitle = computed(() => view.value === 'feed' ? 'SIGNAL // FEED' : view.value === 'creator' ? 'CREATOR // OPS' : 'AI // OPS ASSISTANT')
const currentSubtitle = computed(() => view.value === 'feed' ? '可靠发布后的内容消费与互动现场' : view.value === 'creator' ? '上传、处理、失败诊断与状态追踪' : '权限受控的只读创作者助手')
const titleHtml = computed(() => view.value === 'ai' ? 'ASK THE <em>OPS</em>' : view.value === 'creator' ? 'PUBLISH WITH <em>PROOF</em>' : 'ENTER THE <em>FEED</em>')

function showNotice(message, type = 'ok') { notice.value = message; noticeType.value = type; window.clearTimeout(showNotice.timer); showNotice.timer = window.setTimeout(() => { notice.value = '' }, 4200) }
function unwrapList(value) { if (Array.isArray(value)) return value; return value?.items || value?.records || [] }
function mediaUrl(value) { if (!value) return ''; if (/^https?:\/\//.test(value)) return value; return `${import.meta.env.VITE_MEDIA_BASE_URL || 'http://localhost:29000'}/video/${value}` }
function normalizeVideo(item, index = 0) { return { ...item, id: String(item.id), palette: item.palette || demoVideos[index % demoVideos.length].palette } }

async function loadFeed() {
  loading.value = true; demoMode.value = false
  try {
    const result = feedMode.value === 'following' ? await getFollowFeed() : await getFeed()
    videos.value = unwrapList(result).map(normalizeVideo)
    if (!videos.value.length && feedMode.value === 'public') {
      videos.value = (await getLegacyFeed()).map(normalizeVideo)
    }
  } catch (error) {
    demoMode.value = true; videos.value = demoVideos.map((item) => ({ ...item }))
    showNotice(`后端暂不可用，已进入演示数据模式：${error.message}`, 'error')
  } finally { loading.value = false }
}

async function doSearch() {
  if (!search.value.trim()) return loadFeed()
  loading.value = true
  try { videos.value = unwrapList(await searchVideos(search.value.trim())).map(normalizeVideo); demoMode.value = false }
  catch (error) { showNotice(error.message, 'error') }
  finally { loading.value = false }
}

async function openVideo(video) {
  selectedVideo.value = video; comments.value = []
  try { comments.value = unwrapList(await getComments(video.id)) } catch { comments.value = [] }
}
function requireLogin() { if (!isLoggedIn.value) { authMode.value = 'login'; authOpen.value = true; showNotice('互动和创作者工作台需要先登录', 'error'); return false } return true }
async function toggleLike(video) { if (!requireLogin() || String(video.id).startsWith('demo-')) return; const next = !video.isLike; try { await likeVideo(video.id, next); video.isLike = next; video.likes = Math.max(0, Number(video.likes || 0) + (next ? 1 : -1)) } catch (error) { showNotice(error.message, 'error') } }
async function toggleFavorite(video) { if (!requireLogin() || String(video.id).startsWith('demo-')) return; const next = !video.isFavorite; try { await favoriteVideo(video.id, next); video.isFavorite = next; video.favorites = Math.max(0, Number(video.favorites || 0) + (next ? 1 : -1)) } catch (error) { showNotice(error.message, 'error') } }
async function sendComment() { if (!requireLogin() || !selectedVideo.value || !commentText.value.trim()) return; try { await postComment(selectedVideo.value.id, commentText.value.trim()); commentText.value = ''; comments.value = unwrapList(await getComments(selectedVideo.value.id)); showNotice('评论已提交') } catch (error) { showNotice(error.message, 'error') } }

async function submitAuth() {
  try {
    if (authMode.value === 'register') { await register(authForm.value); authMode.value = 'login'; showNotice('注册成功，请登录') }
    else { const result = await login(authForm.value); setToken(result); token.value = result; authOpen.value = false; await loadProfile(); showNotice('身份验证通过，欢迎回到 SIGNAL.WAVE') }
  } catch (error) { showNotice(error.message, 'error') }
}
async function loadProfile() { if (!isLoggedIn.value) return; try { profile.value = await getProfile() } catch { profile.value = null } }
function logout() { clearToken(); token.value = ''; profile.value = null; showNotice('已安全断开身份连接') }

function onFileChange(event, kind) { const file = event.target.files?.[0]; if (!file) return; if (kind === 'video') { selectedFile.value = file; uploadState.value = `已载入 ${file.name}` } else selectedCover.value = file }
async function publishVideo() {
  if (!requireLogin() || !selectedFile.value) { if (!selectedFile.value) showNotice('请先选择视频文件', 'error'); return }
  try {
    uploadState.value = '01 / 请求预签名上传地址'; const presign = await getPresignedVideo()
    uploadState.value = '02 / 直传 MinIO 对象存储'; const uploadResponse = await fetch(presign.url, { method: 'PUT', body: selectedFile.value, headers: { 'Content-Type': selectedFile.value.type || 'video/mp4' } })
    if (!uploadResponse.ok) throw new Error('对象存储直传失败')
    uploadState.value = '03 / 创建草稿并发送审核 Outbox'; const ended = await finishUpload(presign.taskId)
    const tags = creatorForm.value.tags.split(',').map((tag) => tag.trim()).filter(Boolean)
    await submitVideo({ videoId: ended.videoId, description: creatorForm.value.description, tags, cover: selectedCover.value })
    uploadState.value = '04 / 已进入处理状态：PENDING_REVIEW'; showNotice('投稿成功，已进入异步处理链路'); await loadCreatorData()
  } catch (error) { uploadState.value = '上传中断'; showNotice(error.message, 'error') }
}
async function loadCreatorData() {
  if (!isLoggedIn.value) return
  try { [published.value, processing.value, rejected.value] = await Promise.all([getMyPublished(), getMyProcessing(), getMyRejected()]) } catch { /* 页面仍展示当前操作状态 */ }
}

async function askAssistant() {
  if (!requireLogin() || !aiInput.value.trim() || aiBusy.value) return
  const message = aiInput.value.trim(); aiInput.value = ''; aiMessages.value.push({ role: 'user', text: message }); aiMessages.value.push({ role: 'ai', text: '' }); const target = aiMessages.value[aiMessages.value.length - 1]; aiBusy.value = true
  try { await streamAssistant(message, ({ event, data }) => { if (event === 'error') target.text += `\n${data}`; else { try { const parsed = JSON.parse(data); target.text += parsed.content || parsed.text || data } catch { target.text += data } } }) }
  catch (error) { target.text = `助手暂时离线：${error.message}` }
  finally { aiBusy.value = false }
}

function setView(next) { view.value = next; if (next === 'feed') loadFeed(); if (next === 'creator') loadCreatorData() }
function formatCount(value) { const n = Number(value || 0); return n > 9999 ? `${(n / 10000).toFixed(1)}W` : n }

onMounted(async () => { await loadProfile(); await loadFeed() })
</script>

<template>
  <div class="app-shell">
    <div class="scanlines"></div>
    <header class="topbar">
      <button class="brand" @click="setView('feed')">
        <span class="brand-mark">SW</span><span class="brand-copy"><strong>SIGNAL.WAVE</strong><small>SHORT VIDEO // OPS</small></span>
      </button>
      <nav class="nav">
        <button :class="{ active: view === 'feed' }" @click="setView('feed')">01 / FEED</button>
        <button :class="{ active: view === 'creator' }" @click="setView('creator')">02 / CREATOR OPS</button>
        <button :class="{ active: view === 'ai' }" @click="setView('ai')">03 / AI ASSISTANT</button>
      </nav>
      <div class="top-actions">
        <input v-model="search" class="search" placeholder="SEARCH SIGNAL..." @keyup.enter="doSearch" />
        <button v-if="isLoggedIn" class="avatar" @click="logout">{{ profile?.name?.slice(0, 1) || 'U' }}</button>
        <button v-else class="btn small ghost" @click="authOpen = true">接入身份</button>
      </div>
    </header>

    <main class="page">
      <div class="eyebrow">{{ pageTitle }} // NODE 10086</div>
      <h1 class="title" v-html="titleHtml"></h1>
      <p class="lede">{{ currentSubtitle }}。从预签名直传、Outbox、RabbitMQ 到处理回写，每一个状态都在这里留下可见证据。</p>
      <div v-if="notice" class="status-strip" :class="noticeType">{{ notice }}</div>

      <template v-if="view === 'feed'">
        <section class="hero">
          <div><div class="eyebrow">CREATOR RELIABILITY / 2026</div><h2 style="font-size: 30px; margin: 18px 0 0;">看见内容，也看见内容背后的系统。</h2><p class="lede">公开 Feed、关注 Feed、点赞、收藏、评论。每次互动都经过 Gateway 身份校验，消费侧状态可追踪。</p></div>
          <div class="hero-art"><div class="hero-grid"></div><div class="hero-card"><h3>TRACE YOUR STORY</h3><p>X-TRACE-ID // OUTBOX // INBOX // RECOVERY</p></div></div>
        </section>
        <div class="section-head"><div><h2>LIVE SIGNALS</h2><p>{{ demoMode ? 'DEMO DATA / 后端服务未连接' : 'REAL FEED / Gateway connected' }}</p></div><div class="filter-tabs"><button :class="{ active: feedMode === 'public' }" @click="feedMode = 'public'; loadFeed()">PUBLIC</button><button :class="{ active: feedMode === 'following' }" @click="requireLogin() && (feedMode = 'following', loadFeed())">FOLLOWING</button></div></div>
        <div v-if="loading" class="empty">LOADING SIGNAL STREAM...</div>
        <div v-else-if="videos.length" class="video-grid">
          <article v-for="video in videos" :key="video.id" class="video-card" @click="openVideo(video)">
            <div class="poster" :style="{ '--a': video.palette?.[0] || '#122c54', '--b': video.palette?.[1] || '#ff267f' }"><span>VIDEO // {{ String(video.id).padStart(4, '0') }}</span></div>
            <div class="card-body"><span class="card-title">{{ video.description || '未命名信号' }}</span><div class="meta"><span><b>@{{ video.creatorName || 'UNKNOWN' }}</b></span><span>{{ formatCount(video.likes) }} likes</span></div><div class="card-actions"><button class="icon-btn" :class="{ on: video.isLike }" @click.stop="toggleLike(video)">♥ {{ formatCount(video.likes) }}</button><button class="icon-btn" :class="{ on: video.isFavorite }" @click.stop="toggleFavorite(video)">◆ {{ formatCount(video.favorites) }}</button></div></div>
          </article>
        </div><div v-else class="empty">NO SIGNALS FOUND // 尝试启动 Video Service</div>
      </template>

      <template v-else-if="view === 'creator'">
        <div class="workspace"><section class="panel"><h3>UPLOAD PIPELINE</h3><div class="form-row"><label>VIDEO SOURCE / MP4</label><div class="dropzone"><div><strong>{{ selectedFile ? selectedFile.name : 'DROP MEDIA HERE' }}</strong><span>预签名直传 MinIO，不经过 Gateway 搬运大文件</span><br /><input type="file" accept="video/mp4,video/*" @change="(e) => onFileChange(e, 'video')" /></div></div></div><div class="form-row"><label>DESCRIPTION</label><textarea v-model="creatorForm.description" class="textarea" placeholder="写下这条信号的描述..."></textarea></div><div class="form-row"><label>TAGS / COMMA SEPARATED</label><input v-model="creatorForm.tags" class="field" /></div><div class="form-row"><label>COVER / OPTIONAL</label><input type="file" accept="image/*" @change="(e) => onFileChange(e, 'cover')" /></div><button class="btn pink" @click="publishVideo">EXECUTE PUBLISH →</button><div class="status-strip ok">{{ uploadState }}</div></section><section class="panel"><h3>PIPELINE TELEMETRY</h3><div class="metric-grid"><div class="metric"><b>{{ published.length }}</b><span>PUBLISHED</span></div><div class="metric"><b>{{ processing.length }}</b><span>PROCESSING</span></div><div class="metric"><b>{{ rejected.length }}</b><span>REJECTED</span></div></div><h4>RECENT PROCESSING JOBS</h4><div class="job-list"><div v-for="item in processing.slice(0, 5)" :key="item.id || item.videoId" class="job"><div><b>{{ item.description || `VIDEO // ${item.videoId || 'PENDING'}` }}</b><small>Outbox → RabbitMQ → Processor</small></div><span class="badge">PROCESSING</span></div><div v-if="!processing.length" class="empty">登录并发布视频后，处理状态会出现在这里。</div></div><h4>FAILURE RECOVERY</h4><div v-for="item in rejected.slice(0, 3)" :key="item.id || item.videoId" class="job"><div><b>VIDEO // {{ item.videoId || item.id }}</b><small>{{ item.errorMessage || '可通过 AI 助手诊断' }}</small></div><span class="badge fail">REJECTED</span></div></section></div>
      </template>

      <template v-else>
        <section class="ai-layout"><aside class="ai-sidebar"><h3>CREATOR ASSISTANT</h3><div class="eyebrow">READ-ONLY TOOLS</div><div class="ai-note">AI 只读取服务端事实：处理状态与失败摘要。它不会发布视频、修改业务数据或绕过 creatorId 权限边界。</div><h4>TRY ASKING</h4><button class="btn ghost small" style="width:100%; margin-bottom: 10px;" @click="aiInput = '帮我诊断最近一次视频处理失败'; askAssistant()">诊断处理失败</button><button class="btn ghost small" style="width:100%;" @click="aiInput = '帮我生成一条可靠发布主题的视频标题'; askAssistant()">生成标题建议</button></aside><div class="chat"><div class="chat-head"><b>OPS CHANNEL / TRACE-AWARE</b><span class="online">● {{ aiBusy ? 'STREAMING' : 'ONLINE' }}</span></div><div class="messages"><div v-for="(message, index) in aiMessages" :key="index" class="message" :class="{ user: message.role === 'user' }"><small>{{ message.role === 'user' ? 'YOU' : 'AI OPS' }}</small>{{ message.text || '...' }}</div></div><form class="chat-compose" @submit.prevent="askAssistant"><input v-model="aiInput" class="field" placeholder="输入创作者问题..." :disabled="aiBusy" /><button class="btn" :disabled="aiBusy">SEND</button></form></div></section>
      </template>
    </main>

    <div v-if="selectedVideo" class="modal-backdrop" @click.self="selectedVideo = null"><div class="modal"><button class="modal-close" @click="selectedVideo = null">×</button><h2>VIDEO // {{ selectedVideo.id }}</h2><div class="video-detail"><div class="video-player"><video v-if="selectedVideo.url" :src="mediaUrl(selectedVideo.url)" controls></video><div v-else class="poster" :style="{ '--a': selectedVideo.palette?.[0], '--b': selectedVideo.palette?.[1], width: '100%', height: '100%' }"><span>MEDIA PREVIEW</span></div></div><div><p class="lede" style="font-size: 16px;">{{ selectedVideo.description }}</p><div class="meta" style="margin: 20px 0;">@{{ selectedVideo.creatorName || 'UNKNOWN' }} · {{ formatCount(selectedVideo.comments) }} comments</div><div class="comment-list"><div v-for="comment in comments" :key="comment.id" class="comment"><b>{{ comment.senderName || comment.userName || 'ANONYMOUS' }}</b><p>{{ comment.content }}</p></div><div v-if="!comments.length" class="empty">暂无评论信号</div></div><form v-if="isLoggedIn" class="chat-compose" style="padding: 18px 0 0;" @submit.prevent="sendComment"><input v-model="commentText" class="field" placeholder="留下你的信号..." /><button class="btn small">POST</button></form></div></div></div></div>
    <div v-if="authOpen" class="modal-backdrop" @click.self="authOpen = false"><form class="modal narrow" @submit.prevent="submitAuth"><button type="button" class="modal-close" @click="authOpen = false">×</button><h2>{{ authMode === 'login' ? 'IDENTITY // LOGIN' : 'IDENTITY // REGISTER' }}</h2><div class="form-row"><label>PHONE NUMBER</label><input v-model="authForm.phoneNumber" class="field" required /></div><div class="form-row"><label>PASSWORD</label><input v-model="authForm.password" class="field" type="password" required /></div><button class="btn" style="width:100%;">{{ authMode === 'login' ? 'CONNECT' : 'CREATE IDENTITY' }}</button><button type="button" class="btn ghost" style="width:100%; margin-top: 12px;" @click="authMode = authMode === 'login' ? 'register' : 'login'">{{ authMode === 'login' ? '没有身份？注册' : '已有身份？登录' }}</button></form></div>
  </div>
</template>
