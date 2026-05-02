<template>
  <div class="chat-message" :class="[role]">
    <div class="message-avatar">
      <span v-if="role === 'user'" class="avatar-icon user-icon">U</span>
      <span v-else class="avatar-icon bot-icon">AI</span>
    </div>
    <div class="message-body">
      <div class="message-content" :class="{ streaming: isStreaming }">
        <span v-if="isLoading" class="loading-dots">
          <span class="dot"></span><span class="dot"></span><span class="dot"></span>
        </span>
        <template v-else>
          <span class="message-text">{{ content }}</span>
          <span v-if="isStreaming" class="cursor-blink">|</span>
        </template>
      </div>
      <div class="message-meta">
        <span class="time">{{ formattedTime }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  role: String,        // 'user' | 'assistant'
  content: String,
  isLoading: Boolean,
  isStreaming: Boolean,
  timestamp: String
})

const formattedTime = computed(() => {
  if (!props.timestamp) return ''
  const d = new Date(props.timestamp)
  return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
})
</script>

<style scoped>
.chat-message {
  display: flex;
  gap: 12px;
  padding: 8px 0;
  max-width: 85%;
}

.chat-message.user {
  align-self: flex-end;
  flex-direction: row-reverse;
}

.chat-message.assistant {
  align-self: flex-start;
}

.message-avatar {
  flex-shrink: 0;
  width: 36px;
  height: 36px;
}

.avatar-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  font-size: 14px;
  font-weight: 700;
}

.user-icon {
  background: #1976d2;
  color: #fff;
}

.bot-icon {
  background: #e8f5e9;
  color: #2e7d32;
}

.message-body {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.chat-message.user .message-body {
  align-items: flex-end;
}

.message-content {
  padding: 12px 16px;
  border-radius: 12px;
  font-size: 15px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

.chat-message.user .message-content {
  background: #1976d2;
  color: #fff;
  border-bottom-right-radius: 4px;
}

.chat-message.assistant .message-content {
  background: #f5f5f5;
  color: #333;
  border-bottom-left-radius: 4px;
}

.message-content.streaming {
  min-height: 24px;
}

.loading-dots {
  display: flex;
  gap: 4px;
  align-items: center;
}

.dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #999;
  animation: bounce 1.2s infinite;
}

.dot:nth-child(2) { animation-delay: 0.2s; }
.dot:nth-child(3) { animation-delay: 0.4s; }

@keyframes bounce {
  0%, 100% { opacity: 0.3; transform: translateY(0); }
  50% { opacity: 1; transform: translateY(-4px); }
}

.cursor-blink {
  animation: blink 0.8s infinite;
  color: #1976d2;
  font-weight: 700;
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}

.message-meta {
  display: flex;
  gap: 6px;
  align-items: center;
  padding: 0 4px;
}

.time {
  font-size: 11px;
  color: #999;
}

</style>
