<template>
  <div class="chat-page">
    <!-- Header -->
    <header class="chat-header">
      <div class="chat-header-left">
        <router-link to="/dashboard" class="back-link">&#8592; 大屏</router-link>
        <h1 class="chat-title">RAG 智能助手</h1>
      </div>
      <div class="chat-header-right">
        <button class="header-btn" @click="showUpload = !showUpload">📄 知识库</button>
        <span class="degradation-indicator" v-if="degradation && degradation.level !== 'NORMAL'" :class="degradation.level">
          {{ degradation.level }}
        </span>
      </div>
    </header>

    <div class="chat-body">
      <!-- Session Sidebar -->
      <aside class="session-sidebar">
        <button class="btn-new-session" @click="doNewConversation">+ 新会话</button>
        <div class="session-list">
          <div
            v-for="s in sessions" :key="s.id"
            class="session-item" :class="{ active: s.id === memoryId }"
            @click="switchSession(s.id)"
          >
            <span class="session-preview">{{ s.preview || '空会话' }}</span>
            <span class="session-del" @click.stop="doDeleteSession(s.id)" title="删除">✕</span>
          </div>
        </div>
      </aside>

      <!-- Chat Area -->
      <div class="chat-right">
        <section v-if="showUpload" class="upload-panel">
          <div class="upload-row">
            <label class="upload-label">
              <span>上传 PDF 文档</span>
              <input type="file" accept=".pdf" @change="handleFileSelect" ref="fileInput" />
            </label>
            <span v-if="selectedFile" class="file-name">{{ selectedFile.name }}</span>
            <button class="btn-upload" :disabled="!selectedFile || uploadLoading" @click="doUpload">
              {{ uploadLoading ? '上传中...' : '上传并重建知识库' }}
            </button>
            <button class="btn-rebuild" :disabled="rebuildLoading" @click="doRebuild">
              {{ rebuildLoading ? '重建中...' : '清空重建' }}
            </button>
          </div>
          <div v-if="uploadMsg" class="upload-msg" :class="uploadOk ? 'msg-ok' : 'msg-err'">{{ uploadMsg }}</div>
          <div v-if="rebuildStatus" class="rebuild-status">
            任务 {{ rebuildStatus.taskId }}: {{ rebuildStatus.currentStep || rebuildStatus.status }}
            <span v-if="rebuildStatus.progress"> ({{ rebuildStatus.progress }}%)</span>
          </div>
        </section>
        <main class="chat-main" ref="chatMainRef">
          <div class="messages-container">
            <div v-if="messages.length === 0" class="welcome-message">
              <div class="welcome-icon">&#9889;</div>
              <h2>风电场智能助手</h2>
              <p>查询风机运行数据、故障记录、知识库文档等</p>
            </div>
            <ChatMessage
              v-for="(msg, idx) in messages" :key="idx"
              :role="msg.role" :content="msg.content"
              :isLoading="msg.isLoading" :isStreaming="msg.isStreaming" :timestamp="msg.timestamp"
            />
          </div>
        </main>
        <ChatInput
          v-model="currentInput" :disabled="false" :isStreaming="isLoading"
          @send="sendStreamMessage" @stop="stopResponse"
        />
      </div>

      <!-- Metrics Sidebar (right) -->
      <aside class="metrics-sidebar" v-if="showMetricsPanel">
        <div class="metrics-sidebar-header">
          <span>监控指标</span>
          <button class="metrics-close" @click="showMetricsPanel = false">✕</button>
        </div>
        <div class="metrics-sidebar-body" v-if="sessionMetrics.length > 0">
          <div class="metrics-global-card" v-if="globalMetrics">
            <div class="mg-label">全局汇总</div>
            <div class="mg-row"><span>会话/任务</span><span>{{ globalMetrics.sessions }}/{{ globalMetrics.taskCount }}</span></div>
            <div class="mg-row"><span>成功率</span><span :class="rateClass(globalMetrics.successRate)">{{ globalMetrics.successRate }}%</span></div>
            <div class="mg-row"><span>平均TTFT</span><span>{{ globalMetrics.avgTTFT }}ms</span></div>
            <div class="mg-row"><span>平均E2E</span><span>{{ globalMetrics.avgE2E }}ms</span></div>
            <div class="mg-row"><span>平均Token</span><span>{{ globalMetrics.avgTokens }}</span></div>
          </div>
          <div class="metrics-global-card" v-if="globalMetrics.ragRetrieves > 0">
            <div class="mg-label">检索管道耗时 (avg ms, {{ globalMetrics.ragRetrieves }} 次检索)</div>
            <div class="mg-row"><span>Embedding</span><span>{{ globalMetrics.avgEmbeddingMs || 0 }}ms</span></div>
            <div class="mg-row"><span>向量检索</span><span>{{ globalMetrics.avgVectorMs || 0 }}ms</span></div>
            <div class="mg-row"><span>BM25</span><span>{{ globalMetrics.avgBM25Ms || 0 }}ms</span></div>
            <div class="mg-row"><span>Rerank</span><span>{{ globalMetrics.avgRerankMs || 0 }}ms</span></div>
            <div class="mg-row"><span>缓存检查</span><span>{{ globalMetrics.avgCacheMs || 0 }}ms</span></div>
            <div class="mg-row" style="font-weight:600"><span>总检索</span><span>{{ globalMetrics.avgRetrieveMs || 0 }}ms</span></div>
          </div>
          <div v-for="s in sessionMetrics" :key="s.sessionId" class="metrics-session-card" :class="{ current: s.sessionId === memoryId }">
            <div class="ms-header">
              <span class="ms-id" :title="s.sessionId">{{ s.displayName || s.sessionId.substring(0,14) }}</span>
              <span :class="rateClass(s.successRate)">{{ s.successRate }}%</span>
            </div>
            <div class="ms-row"><span>任务</span><span>{{ s.taskSuccess }}/{{ s.taskCount }}</span></div>
            <div class="ms-row"><span>TTFT</span><span>{{ s.avgTTFT }}ms</span></div>
            <div class="ms-row"><span>E2E</span><span>{{ s.avgE2E }}ms</span></div>
            <div class="ms-row"><span>工具调用</span><span>{{ s.toolSuccess }}/{{ s.toolCalls }}</span></div>
            <div class="ms-row"><span>Token</span><span>{{ s.avgTokens }}/轮</span></div>
            <div class="ms-row" v-if="s.ragRetrieves > 0"><span>检索次数</span><span>{{ s.ragRetrieves }}</span></div>
            <div class="ms-row" v-if="s.ragRetrieves > 0"><span>检索耗时</span><span>{{ s.avgRetrieveMs }}ms</span></div>
          </div>
        </div>
        <div v-else class="metrics-empty">暂无监控数据</div>
      </aside>
      <button v-if="!showMetricsPanel" class="metrics-toggle-btn" @click="showMetricsPanel = true" title="显示监控指标">📊</button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import ChatMessage from '../components/ChatMessage.vue'
import ChatInput from '../components/ChatInput.vue'
import { useChat } from '../composables/useChat.js'
import {
  getDegradationStatus, uploadKnowledgePdf, clearAndRebuildKnowledgeBase,
  getCurrentRebuildStatus, listSessions, deleteSession,
  getMetrics, getSessionMetrics
} from '../api/index.js'

const { messages, isLoading, memoryId, sendStreamMessage, stopResponse, newConversation, switchSession } = useChat()

const currentInput = ref('')
const degradation = ref(null)
const chatMainRef = ref(null)
const showUpload = ref(false)
const sessions = ref([])
const showMetricsPanel = ref(true)
const globalMetrics = ref(null)
const sessionMetrics = ref([])
let metricsTimer = null

// Upload state
const fileInput = ref(null)
const selectedFile = ref(null)
const uploadLoading = ref(false)
const rebuildLoading = ref(false)
const uploadMsg = ref('')
const uploadOk = ref(false)
const rebuildStatus = ref(null)
let statusTimer = null

async function loadSessions() {
  try {
    const res = await listSessions()
    if (res && Array.isArray(res)) sessions.value = res
  } catch { /* ignore */ }
}

function doNewConversation() {
  newConversation()
  loadSessions()
}

async function doDeleteSession(sid) {
  await deleteSession(sid)
  if (memoryId.value === sid) {
    messages.splice(0, messages.length)
    const newId = Date.now().toString()
    memoryId.value = newId
    localStorage.setItem('chat_memory_id', newId)
  }
  loadSessions()
}

// Watch for messages change to refresh session list
import { watch } from 'vue'
watch(messages, () => {
  // Refresh session list when messages change (new preview text)
  loadSessions()
}, { deep: true })

function handleFileSelect(e) { selectedFile.value = e.target.files[0] || null; uploadMsg.value = '' }
async function doUpload() {
  if (!selectedFile.value) return
  uploadLoading.value = true; uploadMsg.value = ''
  try {
    const res = await uploadKnowledgePdf(selectedFile.value)
    uploadOk.value = res.data?.success
    uploadMsg.value = res.data?.message || '上传完成'
    if (res.data?.taskId) pollRebuildStatus(res.data.taskId)
  } catch (e) {
    uploadOk.value = false
    uploadMsg.value = '上传失败: ' + (e.response?.data?.message || e.message)
  } finally { uploadLoading.value = false }
}
async function doRebuild() {
  rebuildLoading.value = true; uploadMsg.value = ''
  try {
    const res = await clearAndRebuildKnowledgeBase()
    uploadOk.value = res.data?.success
    uploadMsg.value = res.data?.message || '重建已触发'
    if (res.data?.taskId) pollRebuildStatus(res.data.taskId)
  } catch (e) {
    uploadOk.value = false; uploadMsg.value = '重建失败'
  } finally { rebuildLoading.value = false }
}
function pollRebuildStatus(taskId) {
  if (statusTimer) clearInterval(statusTimer)
  statusTimer = setInterval(async () => {
    try {
      const res = await getCurrentRebuildStatus()
      if (res.data) {
        rebuildStatus.value = res.data
        if (!res.data.isRunning) { clearInterval(statusTimer); statusTimer = null; uploadMsg.value = '知识库重建完成!'; uploadOk.value = true }
      }
    } catch { /* ignore */ }
  }, 2000)
}
async function refreshStatus() { try { const deg = await getDegradationStatus(); if (deg) degradation.value = deg } catch { /* ignore */ } }
function scrollToBottom() { nextTick(() => { if (chatMainRef.value) chatMainRef.value.scrollTop = chatMainRef.value.scrollHeight }) }
watch(messages, scrollToBottom, { deep: true })

async function fetchMetrics() {
  try { const [g, s] = await Promise.all([getMetrics(), getSessionMetrics()]); if (g) globalMetrics.value = g; if (Array.isArray(s)) sessionMetrics.value = s } catch { /* ignore */ }
}
function rateClass(rate) { if (rate == null) return ''; return rate >= 95 ? 'rate-ok' : rate >= 70 ? 'rate-warn' : 'rate-err' }

onMounted(() => { loadSessions(); refreshStatus(); setInterval(refreshStatus, 30000); fetchMetrics(); metricsTimer = setInterval(fetchMetrics, 10000) })
onUnmounted(() => { if (statusTimer) clearInterval(statusTimer); if (metricsTimer) clearInterval(metricsTimer) })
</script>

<style scoped>
@import '../styles/chat.css';

.chat-body { display: flex; flex: 1; overflow: hidden; }
.session-sidebar {
  width: 260px; flex-shrink: 0; background: #f8f8f8; border-right: 1px solid #e0e0e0;
  display: flex; flex-direction: column;
}
.btn-new-session {
  margin: 10px; padding: 8px; border: 1px solid #1976d2; background: #fff; color: #1976d2;
  border-radius: 6px; cursor: pointer; font-size: 14px;
}
.btn-new-session:hover { background: #e3f2fd; }
.session-list { flex: 1; overflow-y: auto; padding: 4px; }
.session-item {
  padding: 10px 12px; cursor: pointer; border-radius: 6px; font-size: 14px;
  color: #555; margin-bottom: 2px; border-bottom: 1px solid #eee;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.session-item { display: flex; align-items: center; }
.session-preview { flex: 1; overflow: hidden; text-overflow: ellipsis; }
.session-del {
  flex-shrink: 0; margin-left: 6px; padding: 2px 6px; border-radius: 3px;
  font-size: 12px; color: #999; cursor: pointer; visibility: hidden;
}
.session-item:hover .session-del { visibility: visible; }
.session-del:hover { color: #e53935; background: #ffebee; }
.session-item:hover { background: #e3f2fd; }
.session-item.active { background: #bbdefb; color: #1565c0; font-weight: 500; }
.chat-right { flex: 1; display: flex; flex-direction: column; overflow: hidden; }

.upload-panel { background: #f0f4f8; border-bottom: 1px solid #ddd; padding: 14px 24px; }
.upload-row { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.upload-label { display: inline-flex; align-items: center; gap: 6px; background: #fff; border: 1px solid #ccc; border-radius: 6px; padding: 8px 14px; cursor: pointer; font-size: 14px; color: #555; }
.upload-label:hover { border-color: #1976d2; color: #1976d2; }
.upload-label input[type="file"] { display: none; }
.file-name { font-size: 14px; color: #666; max-width: 200px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.btn-upload, .btn-rebuild { padding: 8px 16px; border: none; border-radius: 6px; font-size: 14px; cursor: pointer; }
.btn-upload { background: #1976d2; color: #fff; }
.btn-upload:hover:not(:disabled) { background: #1565c0; }
.btn-upload:disabled { background: #bbb; cursor: not-allowed; }
.btn-rebuild { background: #fff; color: #e53935; border: 1px solid #e53935; }
.btn-rebuild:hover:not(:disabled) { background: #ffebee; }
.btn-rebuild:disabled { opacity: 0.5; cursor: not-allowed; }
.upload-msg { margin-top: 8px; font-size: 14px; padding: 6px 10px; border-radius: 4px; }
.msg-ok { background: #e8f5e9; color: #2e7d32; }
.msg-err { background: #ffebee; color: #c62828; }
.rebuild-status { margin-top: 4px; font-size: 12px; color: #1976d2; }

/* Metrics Sidebar */
.metrics-sidebar {
  width: 280px; min-width: 280px; background: #0a1622; border-left: 1px solid #1a3344;
  display: flex; flex-direction: column; overflow-y: auto; max-height: calc(100vh - 72px);
}
.metrics-sidebar-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 8px 12px; color: #445566; font-size: 13px; text-transform: uppercase; letter-spacing: 1px;
  border-bottom: 1px solid #1a3344; position: sticky; top: 0; background: #0a1622;
}
.metrics-close { background: none; border: none; color: #445566; cursor: pointer; font-size: 14px; }
.metrics-close:hover { color: #8899aa; }
.metrics-sidebar-body { padding: 8px; display: flex; flex-direction: column; gap: 6px; }
.metrics-empty { color: #445566; font-size: 12px; text-align: center; padding: 20px 0; }
.metrics-global-card {
  background: rgba(255,255,255,0.02); border: 1px solid #1a3344; border-radius: 6px; padding: 8px 10px;
}
.mg-label { font-size: 12px; color: #4fc3f7; text-transform: uppercase; letter-spacing: 1px; margin-bottom: 4px; }
.mg-row, .ms-row { display: flex; justify-content: space-between; font-size: 13px; color: #667788; padding: 1px 0; }
.mg-row span:last-child, .ms-row span:last-child { color: #8899aa; }
.metrics-session-card {
  background: rgba(255,255,255,0.02); border: 1px solid #1a3344; border-radius: 6px; padding: 6px 10px;
}
.metrics-session-card.current { border-color: #4fc3f7; background: rgba(79,195,247,0.05); }
.ms-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 2px; }
.ms-id { font-size: 12px; color: #445566; font-family: monospace; }
.rate-ok { color: #66bb6a !important; }
.rate-warn { color: #ffa726 !important; }
.rate-err { color: #ef5350 !important; }
.metrics-toggle-btn {
  position: fixed; right: 10px; bottom: 10px; width: 36px; height: 36px; border-radius: 50%;
  background: #0a1622; border: 1px solid #1a3344; color: #667788; font-size: 18px;
  cursor: pointer; display: flex; align-items: center; justify-content: center; z-index: 10;
}
.metrics-toggle-btn:hover { border-color: #4fc3f7; color: #4fc3f7; }
</style>
