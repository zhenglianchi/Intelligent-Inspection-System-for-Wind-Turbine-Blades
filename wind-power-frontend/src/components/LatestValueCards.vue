<template>
  <div class="latest-values">
    <div class="value-card" v-for="(item, idx) in cards" :key="idx" :style="{ borderTopColor: item.color }">
      <div class="value-label">{{ item.label }}</div>
      <div class="value-number" :style="{ color: item.color }">{{ item.value ?? '--' }}</div>
      <div class="value-time">{{ item.time || '暂无数据' }}</div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  points: { type: Array, default: () => [] }
})

const cards = computed(() => {
  const latest = props.points[props.points.length - 1]
  const time = latest?.gmtReceived
  const displayTime = time ? time.split(' ').slice(-1)[0] : null

  return [
    {
      label: '特征值 1',
      value: latest?.feature1?.toFixed(4),
      time: displayTime,
      color: '#4fc3f7'
    },
    {
      label: '特征值 2',
      value: latest?.feature2?.toFixed(4),
      time: displayTime,
      color: '#66bb6a'
    },
    {
      label: '特征值 3',
      value: latest?.feature3?.toFixed(4),
      time: displayTime,
      color: '#ffa726'
    }
  ]
})
</script>

<style scoped>
.latest-values {
  display: flex;
  gap: 20px;
  flex-wrap: wrap;
}

.value-card {
  flex: 1;
  min-width: 200px;
  background: #0d1b2a;
  border: 1px solid #1a3344;
  border-top: 3px solid;
  border-radius: 8px;
  padding: 18px 22px;
}

.value-label {
  font-size: 13px;
  color: #667788;
  text-transform: uppercase;
  margin-bottom: 8px;
}

.value-number {
  font-size: 32px;
  font-weight: 700;
  font-family: 'Consolas', 'Courier New', monospace;
  margin-bottom: 6px;
}

.value-time {
  font-size: 12px;
  color: #556677;
}
</style>
