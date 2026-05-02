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
  getCurrentRebuildStatus, listSessions, deleteSession
} from '../api/index.js'

const { messages, isLoading, memoryId, sendStreamMessage, stopResponse, newConversation, switchSession } = useChat()

const currentInput = ref('')
const degradation = ref(null)
const chatMainRef = ref(null)
const showUpload = ref(false)
const sessions = ref([])

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

onMounted(() => { loadSessions(); refreshStatus(); setInterval(refreshStatus, 30000) })
onUnmounted(() => { if (statusTimer) clearInterval(statusTimer) })
</script>

<style scoped>
@import '../styles/chat.css';

.chat-body { display: flex; flex: 1; overflow: hidden; }
.session-sidebar {
  width: 200px; flex-shrink: 0; background: #f8f8f8; border-right: 1px solid #e0e0e0;
  display: flex; flex-direction: column;
}
.btn-new-session {
  margin: 10px; padding: 8px; border: 1px solid #1976d2; background: #fff; color: #1976d2;
  border-radius: 6px; cursor: pointer; font-size: 13px;
}
.btn-new-session:hover { background: #e3f2fd; }
.session-list { flex: 1; overflow-y: auto; padding: 4px; }
.session-item {
  padding: 10px 12px; cursor: pointer; border-radius: 6px; font-size: 13px;
  color: #555; margin-bottom: 2px; border-bottom: 1px solid #eee;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.session-item { display: flex; align-items: center; }
.session-preview { flex: 1; overflow: hidden; text-overflow: ellipsis; }
.session-del {
  flex-shrink: 0; margin-left: 6px; padding: 2px 6px; border-radius: 3px;
  font-size: 11px; color: #999; cursor: pointer; visibility: hidden;
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
.file-name { font-size: 13px; color: #666; max-width: 200px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.btn-upload, .btn-rebuild { padding: 8px 16px; border: none; border-radius: 6px; font-size: 13px; cursor: pointer; }
.btn-upload { background: #1976d2; color: #fff; }
.btn-upload:hover:not(:disabled) { background: #1565c0; }
.btn-upload:disabled { background: #bbb; cursor: not-allowed; }
.btn-rebuild { background: #fff; color: #e53935; border: 1px solid #e53935; }
.btn-rebuild:hover:not(:disabled) { background: #ffebee; }
.btn-rebuild:disabled { opacity: 0.5; cursor: not-allowed; }
.upload-msg { margin-top: 8px; font-size: 13px; padding: 6px 10px; border-radius: 4px; }
.msg-ok { background: #e8f5e9; color: #2e7d32; }
.msg-err { background: #ffebee; color: #c62828; }
.rebuild-status { margin-top: 4px; font-size: 12px; color: #1976d2; }
</style>
