<template>
  <div class="turbine-selector">
    <div class="selector-group">
      <label class="selector-label">风场</label>
      <input
        v-model="localWindfarm"
        type="text"
        placeholder="输入风场名称"
        class="selector-input"
        @change="onWindfarmChange"
      />
    </div>
    <div class="selector-group">
      <label class="selector-label">风机编号</label>
      <select v-model="localWindturbine" class="selector-input" @change="onTurbineChange">
        <option :value="null" disabled>选择风机</option>
        <option v-for="id in turbineOptions" :key="id" :value="id">{{ id }}号风机</option>
      </select>
    </div>
    <div class="selector-group">
      <label class="selector-label">刷新间隔</label>
      <select v-model="localInterval" class="selector-input interval-select" @change="onIntervalChange">
        <option :value="1000">1秒</option>
        <option :value="3000">3秒</option>
        <option :value="5000">5秒</option>
        <option :value="10000">10秒</option>
      </select>
    </div>
    <div class="selector-actions">
      <button class="btn-refresh" @click="$emit('refresh')" :disabled="!canRefresh">
        <span class="btn-icon">&#x21bb;</span> 立即刷新
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'

const props = defineProps({
  windfarm: String,
  windturbine: [Number, String],
  intervalMs: { type: Number, default: 1000 },
  maxTurbineId: { type: Number, default: 0 }
})

const emit = defineEmits(['update:windfarm', 'update:windturbine', 'update:intervalMs', 'refresh'])

const localWindfarm = ref(props.windfarm || '')
const localWindturbine = ref(props.windturbine)
const localInterval = ref(props.intervalMs)

const turbineOptions = computed(() => {
  const max = props.maxTurbineId || 20
  return Array.from({ length: max }, (_, i) => i + 1)
})

const canRefresh = computed(() => localWindfarm.value && localWindturbine.value != null)

function onWindfarmChange() {
  emit('update:windfarm', localWindfarm.value)
}

function onTurbineChange() {
  emit('update:windturbine', localWindturbine.value ? Number(localWindturbine.value) : null)
}

function onIntervalChange() {
  emit('update:intervalMs', Number(localInterval.value))
}

watch(() => props.windfarm, (v) => { localWindfarm.value = v || '' })
watch(() => props.windturbine, (v) => { localWindturbine.value = v })
watch(() => props.intervalMs, (v) => { localInterval.value = v })
</script>

<style scoped>
.turbine-selector {
  display: flex;
  align-items: flex-end;
  gap: 20px;
  flex-wrap: wrap;
}

.selector-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.selector-label {
  font-size: 13px;
  color: #8899aa;
  text-transform: uppercase;
  letter-spacing: 1px;
}

.selector-input {
  background: #1a2332;
  border: 1px solid #334455;
  color: #e0e0e0;
  padding: 10px 14px;
  border-radius: 6px;
  font-size: 15px;
  min-width: 160px;
  outline: none;
  transition: border-color 0.2s;
}

.selector-input:focus {
  border-color: #4fc3f7;
}

.interval-select {
  min-width: 120px;
}

.selector-actions {
  padding-bottom: 2px;
}

.btn-refresh {
  background: #1e88e5;
  color: #fff;
  border: none;
  padding: 10px 20px;
  border-radius: 6px;
  font-size: 14px;
  cursor: pointer;
  transition: background 0.2s;
  display: flex;
  align-items: center;
  gap: 6px;
}

.btn-refresh:hover:not(:disabled) {
  background: #1565c0;
}

.btn-refresh:disabled {
  background: #334455;
  color: #667788;
  cursor: not-allowed;
}

.btn-icon {
  font-size: 18px;
}
</style>
