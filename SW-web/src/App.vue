<script setup>
import { computed, onMounted, ref } from 'vue'
import {
  clearToken, deletePublishedVideos, favoriteVideo, finishUpload, followCreator, getComments, getFeed, getFollowFeed,
  getCreatorAnalytics, getLegacyFeed, getMyFavorites, getMyProcessing, getMyPublished, getMyRejected, getPresignedVideo,
  getProfile, getProfileDetail, getProfileStats, getToken, likeVideo, login, postComment, recordVideoView, register,
  request, searchCreators, searchVideos, setToken, streamAssistant, submitVideo, unfollowCreator, updateProfile
} from './api'

const view = ref('feed')
const feedMode = ref('public')
const search = ref('')
const searchMode = ref('video')
const searchActive = ref(false)
const creators = ref([])
const videos = ref([])
const feedCursor = ref('')
const feedHasMore = ref(false)
const selectedVideo = ref(null)
const comments = ref([])
const commentText = ref('')
const loading = ref(false)
const notice = ref('')
const noticeType = ref('ok')
const demoMode = ref(false)
const token = ref(getToken())
const profile = ref(null)
const profileDraft = ref({ name: '', bio: '' })
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
const favoriteVideos = ref([])
const profileStats = ref({ followingCount: 0, followerCount: 0 })
const analytics = ref({ publishedCount: 0, views: 0, likes: 0, comments: 0, favorites: 0, trends: [] })
const playedVideoIds = new Set()
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
const currentSubtitle = computed(() => view.value === 'feed' ? '可靠发布后的内容消费与互动现场' : view.value === 'creator' ? '上传、处理、失败诊断与状态追踪' : '内容创作建议与权限受控的只读业务工具')
const titleHtml = computed(() => view.value === 'ai' ? 'ASK THE <em>OPS</em>' : view.value === 'creator' ? 'PUBLISH WITH <em>PROOF</em>' : 'ENTER THE <em>FEED</em>')

function showNotice(message, type = 'ok') { notice.value = message; noticeType.value = type; window.clearTimeout(showNotice.timer); showNotice.timer = window.setTimeout(() => { notice.value = '' }, 4200) }
function unwrapList(value) { if (Array.isArray(value)) return value; return value?.items || value?.records || [] }
function mediaUrl(value) { if (!value) return ''; if (/^https?:\/\//.test(value)) return value; return `${import.meta.env.VITE_MEDIA_BASE_URL || 'http://localhost:29000'}/video/${value}` }
function normalizeVideo(item, index = 0) {
  return {
    ...item,
    id: String(item.id || item.videoId),
    likes: Number(item.likes || 0),
    comments: Number(item.comments || 0),
    favorites: Number(item.favorites || 0),
    views: Number(item.views || 0),
    palette: item.palette || demoVideos[index % demoVideos.length].palette
  }
}

async function loadFeed(reset = true) {
  loading.value = true; demoMode.value = false
  try {
    if (reset) { feedCursor.value = ''; feedHasMore.value = false }
    const result = feedMode.value === 'following' ? await getFollowFeed(feedCursor.value) : await getFeed(feedCursor.value)
    let nextItems = unwrapList(result).map(normalizeVideo)
    feedCursor.value = result?.nextCursor || ''
    feedHasMore.value = Boolean(result?.hasMore)
    if (!nextItems.length && reset && feedMode.value === 'public') {
      nextItems = (await getLegacyFeed()).map(normalizeVideo)
    }
    videos.value = reset ? nextItems : [...videos.value, ...nextItems]
  } catch (error) {
    demoMode.value = true; videos.value = demoVideos.map((item) => ({ ...item }))
    feedHasMore.value = false
    showNotice(`后端暂不可用，已进入演示数据模式：${error.message}`, 'error')
  } finally { loading.value = false }
}

function switchFeedMode(mode) { feedMode.value = mode; loadFeed(true) }
function loadMoreFeed() { if (!loading.value && feedHasMore.value) loadFeed(false) }
function loadDemoFeed() { demoMode.value = true; feedHasMore.value = false; videos.value = demoVideos.map((item) => ({ ...item })); showNotice('已加载明确标注的视觉演示信号；真实 Feed 仍需 PUBLISHED 数据') }

async function doSearch() {
  if (!search.value.trim()) return clearSearch()
  loading.value = true
  try {
    view.value = 'feed'; searchActive.value = true; demoMode.value = false; feedHasMore.value = false
    if (searchMode.value === 'creator') {
      creators.value = unwrapList(await searchCreators(search.value.trim()))
      videos.value = []
    } else {
      creators.value = []
      videos.value = unwrapList(await searchVideos(search.value.trim())).map(normalizeVideo)
    }
  }
  catch (error) { showNotice(error.message, 'error') }
  finally { loading.value = false }
}
function clearSearch() { search.value = ''; searchActive.value = false; creators.value = []; return loadFeed(true) }
async function toggleCreatorFollow(creator) {
  const next = !creator.isFollowed
  try {
    if (next) await followCreator(creator.id); else await unfollowCreator(creator.id)
    creator.isFollowed = next
    creator.followerCount = Math.max(0, Number(creator.followerCount || 0) + (next ? 1 : -1))
    showNotice(next ? '已订阅创作者信号' : '已取消订阅')
  } catch (error) { showNotice(error.message, 'error') }
}

async function openVideo(video) {
  selectedVideo.value = video; comments.value = []
  try { comments.value = unwrapList(await getComments(video.id)) } catch { comments.value = [] }
}
function requireLogin() { if (!isLoggedIn.value) { authMode.value = 'login'; authOpen.value = true; showNotice('互动和创作者工作台需要先登录', 'error'); return false } return true }
async function toggleLike(video) { if (!requireLogin() || String(video.id).startsWith('demo-')) return; const next = !video.isLike; try { await likeVideo(video.id, next); video.isLike = next; video.likes = Math.max(0, Number(video.likes || 0) + (next ? 1 : -1)) } catch (error) { showNotice(error.message, 'error') } }
async function toggleFavorite(video) { if (!requireLogin() || String(video.id).startsWith('demo-')) return; const next = !video.isFavorite; try { await favoriteVideo(video.id, next); video.isFavorite = next; video.favorites = Math.max(0, Number(video.favorites || 0) + (next ? 1 : -1)) } catch (error) { showNotice(error.message, 'error') } }
async function recordPlayback(video) {
  if (!isLoggedIn.value || demoMode.value || !video?.id || playedVideoIds.has(String(video.id))) return
  playedVideoIds.add(String(video.id))
  try {
    const counted = await recordVideoView(video.id)
    if (counted) video.views = Number(video.views || 0) + 1
  } catch {
    playedVideoIds.delete(String(video.id))
    /* 观看计数失败不影响媒体播放；服务端仍以每日去重约束为准。 */
  }
}
async function sendComment() { if (!requireLogin() || !selectedVideo.value || !commentText.value.trim()) return; try { await postComment(selectedVideo.value.id, commentText.value.trim()); commentText.value = ''; comments.value = unwrapList(await getComments(selectedVideo.value.id)); showNotice('评论已提交') } catch (error) { showNotice(error.message, 'error') } }

async function submitAuth() {
  try {
    if (authMode.value === 'register') { await register(authForm.value); authMode.value = 'login'; showNotice('注册成功，请登录') }
    else { const result = await login(authForm.value); setToken(result); token.value = result; authOpen.value = false; await loadProfile(); await loadFeed(); showNotice('身份验证通过，欢迎回到 SIGNAL.WAVE') }
  } catch (error) { showNotice(error.message, 'error') }
}
async function loadProfile() {
  if (!isLoggedIn.value) return
  try {
    const [basic, detail] = await Promise.all([getProfile(), getProfileDetail()])
    profile.value = { ...basic, ...detail }
    profileDraft.value = { name: detail?.name || basic?.name || '', bio: detail?.bio || '' }
  } catch { profile.value = null }
}
async function saveProfile() {
  try {
    await updateProfile(profileDraft.value)
    await loadProfile()
    showNotice('创作者资料已更新')
  } catch (error) { showNotice(error.message, 'error') }
}
async function deletePublishedVideo(id) {
  if (!window.confirm('确认删除这条已发布视频？此操作只影响本地开发数据。')) return
  try {
    await deletePublishedVideos([id])
    published.value = published.value.filter((item) => String(item.id) !== String(id))
    showNotice('已删除已发布视频')
  } catch (error) { showNotice(error.message, 'error') }
}
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
  try {
    const [publishedResult, processingResult, rejectedResult, favoritesResult, statsResult, analyticsResult] = await Promise.all([
      getMyPublished(), getMyProcessing(), getMyRejected(), getMyFavorites(), getProfileStats(), getCreatorAnalytics()
    ])
    published.value = unwrapList(publishedResult).map(normalizeVideo)
    processing.value = unwrapList(processingResult)
    rejected.value = unwrapList(rejectedResult)
    favoriteVideos.value = unwrapList(favoritesResult).map(normalizeVideo)
    profileStats.value = statsResult || { followingCount: 0, followerCount: 0 }
    analytics.value = analyticsResult || analytics.value
  } catch { /* 页面仍展示当前操作状态 */ }
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
const trendMax = computed(() => Math.max(1, ...((analytics.value.trends || []).map((item) => Number(item.views || 0)))))
function scrollToFeed() { document.querySelector('.signal-deck, .feed-empty')?.scrollIntoView({ behavior: 'smooth' }) }

onMounted(async () => { if (isLoggedIn.value) { await loadProfile(); await loadFeed() } })
</script>

<template>
  <div class="app-shell">
    <div class="scanlines"></div>
    <section v-if="!isLoggedIn" class="auth-gate">
      <div class="auth-gate-art">
        <div class="auth-grid"></div>
        <div class="auth-code">77</div>
        <div class="danger-tag">RESTRICTED // IDENTITY REQUIRED</div>
        <div class="eyebrow">SIGNAL.WAVE / NIGHT DISTRICT ACCESS NODE</div>
        <h1>接入你的<br><em>神经信号。</em></h1>
        <p>登录后进入公开视频流、点赞收藏评论、创作者投稿链路和 AI 运营助手。所有具体业务页面都受身份门禁保护。</p>
        <div class="auth-capabilities"><span>FEED</span><span>CREATOR OPS</span><span>AI ASSISTANT</span></div>
      </div>
      <form class="auth-terminal" @submit.prevent="submitAuth">
        <div class="brand auth-brand"><span class="brand-mark">SW</span><span class="brand-copy"><strong>SIGNAL.WAVE</strong><small>IDENTITY TERMINAL</small></span></div>
        <div class="eyebrow">AUTH CHANNEL // {{ authMode === 'login' ? 'CONNECT' : 'ENROLL' }}</div>
        <h2>{{ authMode === 'login' ? '身份接入' : '创建身份' }}</h2>
        <div v-if="notice" class="status-strip" :class="noticeType">{{ notice }}</div>
        <div class="form-row"><label>PHONE NUMBER</label><input v-model="authForm.phoneNumber" class="field" autocomplete="username" required /></div>
        <div class="form-row"><label>PASSWORD / MIN 12 CHARACTERS</label><input v-model="authForm.password" class="field" type="password" autocomplete="current-password" minlength="12" required /></div>
        <button class="btn yellow auth-submit">{{ authMode === 'login' ? 'JACK IN →' : 'CREATE IDENTITY →' }}</button>
        <button type="button" class="btn ghost auth-switch" @click="authMode = authMode === 'login' ? 'register' : 'login'">{{ authMode === 'login' ? '没有身份？注册' : '已有身份？登录' }}</button>
        <small class="auth-hint">LOCAL DEV NODE // JWT AUTHENTICATION // GATEWAY 10086</small>
      </form>
    </section>

    <template v-else>
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
        <span class="net-status"><i></i> NETRUN / {{ demoMode ? 'SIM' : 'LIVE' }}</span>
        <select v-model="searchMode" class="search-type" @change="search.trim() && doSearch()"><option value="video">VIDEO</option><option value="creator">CREATOR</option></select>
        <input v-model="search" class="search" :placeholder="searchMode === 'video' ? 'SEARCH VIDEO...' : 'SEARCH CREATOR...'" @keyup.enter="doSearch" />
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
        <section class="hero cp-hero">
          <div class="hero-copy">
            <div class="danger-tag">WARNING // HIGH-BANDWIDTH MEMORY STREAM</div>
            <div class="eyebrow">NIGHT DISTRICT / NEURAL FEED / 2077 MODE</div>
            <h2>把城市的噪声<br><span>烧进信号里。</span></h2>
            <p class="lede">不只是上传和 AI。这里包含公开刷流、关注流、搜索、播放、点赞、收藏与评论；创作者端负责投稿和状态追踪，AI 负责标题建议与失败诊断。</p>
            <div class="hero-cta"><button class="btn yellow" @click="scrollToFeed">JACK INTO FEED ↓</button><button class="btn ghost" @click="setView('creator')">OPEN CREATOR OPS</button></div>
          </div>
          <div class="hero-art cp-art"><div class="hero-grid"></div><div class="mega-code">77</div><div class="city-silhouette"><i></i><i></i><i></i><i></i><i></i><i></i></div><div class="hero-card"><small>SW // EDGE SIGNAL</small><h3>WAKE THE FEED</h3><p>GATEWAY · OUTBOX · RABBITMQ · FFMPEG</p></div><div class="chrome-meter"><span>CHROME</span><b>86%</b></div><div class="hero-coordinates">NIGHT LINK / 77.031<br>BD STREAM READY</div></div>
        </section>
        <div class="capability-strip"><span>PUBLIC FEED</span><span>FOLLOWING</span><span>VIDEO PLAYBACK</span><span>SEARCH</span><span>LIKE</span><span>FAVORITE</span><span>COMMENTS</span></div>
        <div class="section-head"><div><div class="eyebrow">BRAINDANCE CHANNEL / CONSUMER SIDE</div><h2>{{ searchActive ? `SEARCH // ${searchMode.toUpperCase()}` : 'LIVE SIGNALS' }}</h2><p>{{ searchActive ? `QUERY / ${search}` : (demoMode ? 'DEMO DATA / VISUAL PREVIEW ONLY' : 'REAL FEED / Gateway connected') }}</p></div><div class="filter-tabs"><button v-if="searchActive" @click="clearSearch">CLEAR SEARCH</button><button :class="{ active: feedMode === 'public' && !searchActive }" @click="switchFeedMode('public')">PUBLIC</button><button :class="{ active: feedMode === 'following' && !searchActive }" @click="switchFeedMode('following')">FOLLOWING</button></div></div>
        <div v-if="loading" class="empty">LOADING SIGNAL STREAM...</div>
        <div v-else-if="searchActive && searchMode === 'creator'" class="creator-results"><article v-for="creator in creators" :key="creator.id" class="creator-card"><div class="creator-avatar">{{ creator.name?.slice(0, 1) || 'N' }}</div><div><div class="eyebrow">CREATOR ID // {{ creator.id }}</div><h3>{{ creator.name || 'UNKNOWN CREATOR' }}</h3><p>{{ creator.bio || '该创作者暂未写入身份简介。' }}</p><small>{{ formatCount(creator.followerCount) }} FOLLOWERS</small></div><button class="btn" :class="{ ghost: creator.isFollowed }" @click="toggleCreatorFollow(creator)">{{ creator.isFollowed ? 'UNFOLLOW' : 'FOLLOW +' }}</button></article><div v-if="!creators.length" class="empty">NO CREATOR SIGNAL MATCHED</div></div>
        <div v-else-if="videos.length" class="signal-deck">
          <aside class="feed-rail"><div class="rail-line"></div><div class="rail-item active"><b>01</b><span>DISCOVER<br>公开刷流</span></div><div class="rail-item"><b>02</b><span>FOLLOW<br>关注订阅</span></div><div class="rail-item"><b>03</b><span>REACT<br>赞藏评论</span></div><div class="rail-status"><i></i> CHANNEL LIVE</div></aside>
          <div class="video-stream">
            <article v-for="(video, index) in videos" :key="video.id" class="signal-card">
              <div class="media-stage" :style="{ '--a': video.palette?.[0] || '#122c54', '--b': video.palette?.[1] || '#ff267f' }">
                <video v-if="video.url" :src="mediaUrl(video.url)" controls loop muted playsinline preload="metadata" @play="recordPlayback(video)" @click.stop></video>
                <div v-else class="poster"><span>NEURAL CLIP // {{ String(video.id).padStart(4, '0') }}</span></div>
                <div class="media-hud"><span>REC ● {{ String(index + 1).padStart(2, '0') }}</span><span>BD // {{ demoMode ? 'SIMULATION' : 'LIVE DATA' }}</span></div>
                <div class="edge-mark">SW<br><b>77</b></div>
              </div>
              <div class="signal-copy">
                <div><div class="eyebrow">SIGNAL ID // {{ video.id }}</div><h3>{{ video.description || '未命名信号' }}</h3><p>@{{ video.creatorName || 'UNKNOWN CREATOR' }} · ▶ {{ formatCount(video.views) }} VIEWS · {{ demoMode ? '视觉演示信号，不代表后端数据' : '经 Gateway 分发的公开视频' }}</p></div>
                <div class="signal-actions"><button class="action-key" :class="{ on: video.isLike }" @click="toggleLike(video)"><b>♥</b><span>{{ formatCount(video.likes) }}<small>LIKE</small></span></button><button class="action-key" :class="{ on: video.isFavorite }" @click="toggleFavorite(video)"><b>◆</b><span>{{ formatCount(video.favorites) }}<small>SAVE</small></span></button><button class="action-key" @click="openVideo(video)"><b>▤</b><span>{{ formatCount(video.comments) }}<small>COMMENT</small></span></button></div>
              </div>
            </article>
            <button v-if="feedHasMore" class="btn yellow load-more" @click="loadMoreFeed">LOAD NEXT SIGNALS ↓</button>
          </div>
        </div>
        <div v-else class="empty feed-empty"><b>NO PUBLISHED SIGNALS</b><span>Video Service 已连接，但数据库目前没有 `PUBLISHED` 视频。先从 Creator Ops 上传，并启动 Video Processor 完成转码发布。</span><div class="hero-cta"><button class="btn yellow" @click="setView('creator')">GO TO UPLOAD PIPELINE →</button><button class="btn ghost" @click="loadDemoFeed">PREVIEW DEMO SIGNALS</button></div></div>
      </template>

      <template v-else-if="view === 'creator'">
        <div class="workspace"><section class="panel"><h3>UPLOAD PIPELINE // CREATE</h3><div class="form-row"><label>VIDEO SOURCE / MP4</label><div class="dropzone"><div><strong>{{ selectedFile ? selectedFile.name : 'DROP MEDIA HERE' }}</strong><span>预签名直传 MinIO，不经过 Gateway 搬运大文件</span><br /><input type="file" accept="video/mp4,video/*" @change="(e) => onFileChange(e, 'video')" /></div></div></div><div class="form-row"><label>DESCRIPTION</label><textarea v-model="creatorForm.description" class="textarea" placeholder="写下这条信号的描述..."></textarea></div><div class="form-row"><label>TAGS / COMMA SEPARATED</label><input v-model="creatorForm.tags" class="field" /></div><div class="form-row"><label>COVER / OPTIONAL</label><input type="file" accept="image/*" @change="(e) => onFileChange(e, 'cover')" /></div><button class="btn pink" @click="publishVideo">EXECUTE PUBLISH →</button><div class="status-strip ok">{{ uploadState }}</div><h3 class="sub-panel-title">CREATOR PROFILE // UPDATE</h3><div class="form-row"><label>DISPLAY NAME</label><input v-model="profileDraft.name" class="field" maxlength="30" /></div><div class="form-row"><label>BIO</label><textarea v-model="profileDraft.bio" class="textarea compact" maxlength="160" placeholder="写下你的创作者身份简介..."></textarea></div><button class="btn ghost" @click="saveProfile">SYNC PROFILE →</button></section><section class="panel"><h3>CREATOR TELEMETRY // REAL DATA</h3><div class="metric-grid metric-grid-wide"><div class="metric"><b>{{ formatCount(analytics.publishedCount) }}</b><span>PUBLISHED</span></div><div class="metric"><b>{{ formatCount(analytics.views) }}</b><span>VIEWS</span></div><div class="metric"><b>{{ formatCount(profileStats.followerCount) }}</b><span>FOLLOWERS</span></div><div class="metric"><b>{{ formatCount(profileStats.followingCount) }}</b><span>FOLLOWING</span></div><div class="metric"><b>{{ formatCount(analytics.likes) }}</b><span>LIKES</span></div><div class="metric"><b>{{ formatCount(analytics.favorites) }}</b><span>SAVES</span></div></div><h4>7-DAY UNIQUE VIEW TREND</h4><div class="trend-chart"><div v-for="item in analytics.trends || []" :key="item.date" class="trend-bar"><span class="trend-value">{{ item.views }}</span><i :style="{ height: `${Math.max(8, Number(item.views || 0) / trendMax * 100)}%` }"></i><small>{{ item.date?.slice(5) }}</small></div></div><p class="analytics-note">同一登录用户对同一视频每天仅计一次观看；这是本地演示口径，不替代生产环境的完播率、风控与反刷量系统。</p><h4>PUBLISHED LIBRARY // DELETE</h4><div class="job-list"><div v-for="item in published.slice(0, 5)" :key="item.id" class="job"><div><b>{{ item.description || `VIDEO // ${item.id}` }}</b><small>▶ {{ item.views || 0 }} · ♥ {{ item.likes || 0 }} · ▤ {{ item.comments || 0 }} · ◆ {{ item.favorites || 0 }}</small></div><button class="btn small ghost danger" @click="deletePublishedVideo(item.id)">DELETE</button></div><div v-if="!published.length" class="empty">暂无已发布视频。</div></div><h4>MY SAVED SIGNALS</h4><div class="job-list"><div v-for="item in favoriteVideos.slice(0, 5)" :key="item.videoId || item.id" class="job"><div><b>{{ item.description || `VIDEO // ${item.videoId || item.id}` }}</b><small>◆ {{ item.favorites || 0 }} · CREATOR {{ item.creatorName || item.creatorId || 'UNKNOWN' }}</small></div><span class="badge">SAVED</span></div><div v-if="!favoriteVideos.length" class="empty">还没有收藏视频。</div></div><h4>RECENT PROCESSING JOBS</h4><div class="job-list"><div v-for="item in processing.slice(0, 5)" :key="item.id || item.videoId" class="job"><div><b>{{ item.description || `VIDEO // ${item.videoId || 'PENDING'}` }}</b><small>Outbox → RabbitMQ → Processor</small></div><span class="badge">PROCESSING</span></div><div v-if="!processing.length" class="empty">登录并发布视频后，处理状态会出现在这里。</div></div><h4>FAILURE RECOVERY</h4><div v-for="item in rejected.slice(0, 3)" :key="item.id || item.videoId" class="job"><div><b>VIDEO // {{ item.videoId || item.id }}</b><small>{{ item.errorMessage || '可通过 AI 助手诊断' }}</small></div><span class="badge fail">REJECTED</span></div></section></div>
      </template>

      <template v-else>
        <section class="ai-layout"><aside class="ai-sidebar"><h3>CREATOR ASSISTANT</h3><div class="eyebrow">CONTENT COPILOT + READ-ONLY TOOLS</div><div class="ai-note">AI 可以生成标题、简介、标签、选题与发布节奏；涉及业务事实时，只读取本人视频的处理状态与失败摘要。它不会发布视频、修改数据或绕过 creatorId 权限边界。</div><h4>TRY ASKING</h4><div class="ai-quick-grid"><button class="btn ghost small" @click="aiInput = '为可靠异步发布主题生成3个标题，并说明各自适合的受众'; askAssistant()">标题建议</button><button class="btn ghost small" @click="aiInput = '为短视频生成一份简介和5个标签，主题是可靠异步发布'; askAssistant()">简介与标签</button><button class="btn ghost small" @click="aiInput = '给我3个适合后端开发者账号的短视频选题'; askAssistant()">选题策划</button><button class="btn ghost small" @click="aiInput = '给我一周短视频发布节奏建议，并说明理由'; askAssistant()">发布节奏</button><button class="btn ghost small" @click="aiInput = '我想查询一个视频的处理进度，请告诉我需要提供什么'; askAssistant()">处理进度</button><button class="btn ghost small" @click="aiInput = '我想诊断视频处理失败，请告诉我需要提供什么'; askAssistant()">失败诊断</button></div></aside><div class="chat"><div class="chat-head"><b>OPS CHANNEL / TRACE-AWARE</b><span class="online">● {{ aiBusy ? 'STREAMING' : 'ONLINE' }}</span></div><div class="messages"><div v-for="(message, index) in aiMessages" :key="index" class="message" :class="{ user: message.role === 'user' }"><small>{{ message.role === 'user' ? 'YOU' : 'AI OPS' }}</small>{{ message.text || '...' }}</div></div><form class="chat-compose" @submit.prevent="askAssistant"><input v-model="aiInput" class="field" placeholder="输入创作者问题..." :disabled="aiBusy" /><button class="btn" :disabled="aiBusy">SEND</button></form></div></section>
      </template>
    </main>

    <div v-if="selectedVideo" class="modal-backdrop" @click.self="selectedVideo = null"><div class="modal"><button class="modal-close" @click="selectedVideo = null">×</button><h2>VIDEO // {{ selectedVideo.id }}</h2><div class="video-detail"><div class="video-player"><video v-if="selectedVideo.url" :src="mediaUrl(selectedVideo.url)" controls></video><div v-else class="poster" :style="{ '--a': selectedVideo.palette?.[0], '--b': selectedVideo.palette?.[1], width: '100%', height: '100%' }"><span>MEDIA PREVIEW</span></div></div><div><p class="lede" style="font-size: 16px;">{{ selectedVideo.description }}</p><div class="meta" style="margin: 20px 0;">@{{ selectedVideo.creatorName || 'UNKNOWN' }} · {{ formatCount(selectedVideo.comments) }} comments</div><div class="comment-list"><div v-for="comment in comments" :key="comment.id" class="comment"><b>{{ comment.name || comment.senderName || comment.userName || 'ANONYMOUS' }}</b><p>{{ comment.content }}</p></div><div v-if="!comments.length" class="empty">暂无评论信号</div></div><form v-if="isLoggedIn" class="chat-compose" style="padding: 18px 0 0;" @submit.prevent="sendComment"><input v-model="commentText" class="field" placeholder="留下你的信号..." /><button class="btn small">POST</button></form></div></div></div></div>
    </template>
  </div>
</template>
