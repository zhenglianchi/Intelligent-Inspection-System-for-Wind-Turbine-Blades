import { reactive, ref } from 'vue'
import { chatStream as chatStreamApi, chat as chatApi, getSessionHistory } from '../api/index.js'

function getOrCreateMemoryId() {
  let id = localStorage.getItem('chat_memory_id')
  if (!id) {
    id = Date.now().toString()
    localStorage.setItem('chat_memory_id', id)
  }
  return id
}

export function useChat() {
  const messages = reactive([])
  const isLoading = ref(false)
  const memoryId = ref(getOrCreateMemoryId())
  let abortController = null

  function addUserMessage(content) {
    messages.push({
      role: 'user',
      content,
      timestamp: new Date().toISOString()
    })
  }

  function addAssistantMessage() {
    const msg = {
      role: 'assistant',
      content: '',
      isLoading: true,
      isStreaming: true,
      timestamp: new Date().toISOString()
    }
    messages.push(msg)
    return messages.length - 1
  }

  // SSE streaming chat
  async function sendStreamMessage(content) {
    if (!content.trim() || isLoading.value) return

    addUserMessage(content)
    const msgIndex = addAssistantMessage()

    abortController = new AbortController()
    isLoading.value = true

    try {
      const response = await chatStreamApi(
        { message: content.trim() },
        memoryId.value,
        abortController.signal
      )

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`)
      }

      const reader = response.body.getReader()
      const decoder = new TextDecoder('utf-8')
      let buffer = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop() || ''

        for (const line of lines) {
          if (line.startsWith('data:')) {
            try {
              const data = JSON.parse(line.replace(/^data:\s*/, ''))
              if (data.content) {
                // 替换整条消息触发 Vue 响应式
                const cur = messages[msgIndex]
                messages[msgIndex] = { ...cur, content: cur.content + data.content, isLoading: false }
              }
              if (data.done) {
                const cur = messages[msgIndex]
                messages[msgIndex] = { ...cur, isStreaming: false }
              }
              if (data.error) {
                const cur = messages[msgIndex]
                messages[msgIndex] = { ...cur, content: data.error, isStreaming: false, isLoading: false }
              }
            } catch {
              // ignore parse errors
            }
          }
        }
      }

      const cur = messages[msgIndex]
      messages[msgIndex] = { ...cur, isStreaming: false, isLoading: false }
    } catch (e) {
      if (e.name !== 'AbortError') {
        const msg = messages[msgIndex]
        msg.content = msg.content || `请求失败: ${e.message}`
        msg.isStreaming = false
        msg.isLoading = false
      }
    } finally {
      isLoading.value = false
      abortController = null
    }
  }

  // Non-streaming chat
  async function sendMessage(content) {
    if (!content.trim() || isLoading.value) return

    addUserMessage(content)
    const msgIndex = addAssistantMessage()
    isLoading.value = true

    try {
      const result = await chatApi({ message: content.trim() }, memoryId.value)
      messages[msgIndex].content = result.answer || '无响应'
    } catch (e) {
      messages[msgIndex].content = `请求失败: ${e.message}`
    } finally {
      messages[msgIndex].isLoading = false
      messages[msgIndex].isStreaming = false
      isLoading.value = false
    }
  }

  function stopResponse() {
    if (abortController) {
      abortController.abort()
      const last = messages.value[messages.value.length - 1]
      if (last?.role === 'assistant') {
        last.isLoading = false
        last.isStreaming = false
      }
      isLoading.value = false
      abortController = null
    }
  }

  function newConversation() {
    messages.splice(0, messages.length)
    const newId = Date.now().toString()
    memoryId.value = newId
    localStorage.setItem('chat_memory_id', newId)
  }

  async function switchSession(sessionId) {
    messages.splice(0, messages.length)
    memoryId.value = sessionId
    localStorage.setItem('chat_memory_id', sessionId)
    // 加载该会话的历史消息
    try {
      const history = await getSessionHistory(sessionId)
      if (Array.isArray(history) || (history && history.data && Array.isArray(history.data))) {
        const msgs = Array.isArray(history) ? history : history.data
        msgs.forEach(m => {
          messages.push({
            role: m.role,
            content: m.content || '',
            isLoading: false,
            isStreaming: false,
            timestamp: new Date().toISOString()
          })
        })
      }
    } catch (e) { /* ignore */ }
  }

  return {
    messages,
    isLoading,
    memoryId,
    sendStreamMessage,
    sendMessage,
    stopResponse,
    newConversation,
    switchSession
  }
}
