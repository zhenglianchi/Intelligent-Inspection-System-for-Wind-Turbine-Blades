<template>
  <div class="chat-input-area">
    <div class="input-wrapper">
      <textarea
        ref="textareaRef"
        v-model="localInput"
        class="chat-textarea"
        placeholder="输入问题，Enter 发送，Shift+Enter 换行"
        rows="1"
        @keydown.enter.exact.prevent="send"
        @keydown.shift.enter.prevent="newline"
        @input="autoResize"
        :disabled="disabled"
      ></textarea>
      <div class="input-actions">
        <button
          class="btn-send"
          :class="{ stop: isStreaming }"
          @click="isStreaming ? $emit('stop') : send()"
          :disabled="!localInput.trim() && !isStreaming"
        >
          <template v-if="isStreaming">
            <span class="stop-icon">&#9632;</span>
          </template>
          <template v-else>
            <span class="send-icon">&#10148;</span>
          </template>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, nextTick } from 'vue'

const props = defineProps({
  modelValue: String,
  disabled: Boolean,
  isStreaming: Boolean
})

const emit = defineEmits(['update:modelValue', 'send', 'stop'])

const localInput = ref(props.modelValue || '')
const textareaRef = ref(null)

function autoResize() {
  const el = textareaRef.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = Math.min(el.scrollHeight, 200) + 'px'
  emit('update:modelValue', localInput.value)
}

function send() {
  if (!localInput.value.trim()) return
  emit('send', localInput.value.trim())
  localInput.value = ''
  nextTick(() => {
    if (textareaRef.value) {
      textareaRef.value.style.height = 'auto'
    }
  })
}

function newline() {
  localInput.value += '\n'
  nextTick(autoResize)
}

watch(() => props.modelValue, (v) => {
  if (v === '') localInput.value = ''
})
</script>

<style scoped>
.chat-input-area {
  background: #fff;
  border-top: 1px solid #e0e0e0;
  padding: 16px 20px;
}

.input-wrapper {
  max-width: 900px;
  margin: 0 auto;
  display: flex;
  gap: 10px;
  align-items: flex-end;
  background: #f5f5f5;
  border: 1px solid #ddd;
  border-radius: 12px;
  padding: 8px 8px 8px 16px;
  transition: border-color 0.2s;
}

.input-wrapper:focus-within {
  border-color: #1976d2;
  background: #fff;
}

.chat-textarea {
  flex: 1;
  border: none;
  background: transparent;
  outline: none;
  font-size: 15px;
  line-height: 1.5;
  resize: none;
  min-height: 24px;
  max-height: 200px;
  font-family: inherit;
}

.btn-send {
  width: 40px;
  height: 40px;
  border: none;
  border-radius: 10px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.2s;
  flex-shrink: 0;
}

.btn-send:not(.stop) {
  background: #1976d2;
  color: #fff;
}

.btn-send:not(.stop):hover:not(:disabled) {
  background: #1565c0;
}

.btn-send.stop {
  background: #e53935;
  color: #fff;
}

.btn-send.stop:hover {
  background: #c62828;
}

.btn-send:disabled {
  background: #bdbdbd;
  cursor: not-allowed;
}

.send-icon {
  font-size: 16px;
  transform: rotate(-30deg);
}

.stop-icon {
  font-size: 11px;
}
</style>
