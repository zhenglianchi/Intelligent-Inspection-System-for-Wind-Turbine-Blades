<template>
  <div class="feature-chart" ref="chartRef"></div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, watch, nextTick } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({
  points: {
    type: Array,
    default: () => []
  }
})

const chartRef = ref(null)
let chart = null

function formatTime(ts) {
  if (!ts) return ''
  if (typeof ts === 'string') {
    // Handle "yyyy-MM-dd HH:mm:ss" format
    const parts = ts.split(' ')
    if (parts.length >= 2) return parts[1] // HH:mm:ss
    return ts
  }
  return ''
}

function fullTime(ts) {
  return ts || ''
}

function buildOption() {
  const points = props.points
  const times = points.map(p => formatTime(p.gmtReceived))
  const f1 = points.map(p => p.feature1 ?? null)
  const f2 = points.map(p => p.feature2 ?? null)
  const f3 = points.map(p => p.feature3 ?? null)

  return {
    backgroundColor: 'transparent',
    textStyle: { color: '#e0e0e0' },
    legend: {
      data: ['特征值1', '特征值2', '特征值3'],
      top: 10,
      textStyle: { color: '#e0e0e0', fontSize: 14 }
    },
    grid: {
      top: 50,
      right: 40,
      bottom: 40,
      left: 60
    },
    tooltip: {
      trigger: 'axis',
      formatter: (params) => {
        const ts = fullTime(points[params[0]?.dataIndex]?.gmtReceived || '')
        let html = `<div style="font-size:13px">时间: ${ts}</div>`
        params.forEach(p => {
          html += `<div style="font-size:13px">${p.marker} ${p.seriesName}: ${p.value?.toFixed(4)}</div>`
        })
        return html
      }
    },
    xAxis: {
      type: 'category',
      data: times,
      axisLine: { lineStyle: { color: '#555' } },
      axisLabel: { color: '#ccc', fontSize: 13, rotate: times.length > 6 ? 30 : 0 },
      boundaryGap: false
    },
    yAxis: {
      type: 'value',
      name: '特征值',
      nameTextStyle: { color: '#ccc', fontSize: 13 },
      axisLine: { lineStyle: { color: '#555' } },
      axisLabel: { color: '#ccc', fontSize: 12 },
      splitLine: { lineStyle: { color: '#333' } }
    },
    series: [
      {
        name: '特征值1',
        type: 'line',
        data: f1,
        smooth: true,
        showSymbol: true,
        symbol: 'circle',
        symbolSize: 8,
        lineStyle: { color: '#4fc3f7', width: 2 },
        itemStyle: { color: '#4fc3f7' },
        areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(79,195,247,0.3)' },
          { offset: 1, color: 'rgba(79,195,247,0.02)' }
        ])}
      },
      {
        name: '特征值2',
        type: 'line',
        data: f2,
        smooth: true,
        showSymbol: true,
        symbol: 'circle',
        symbolSize: 8,
        lineStyle: { color: '#66bb6a', width: 2 },
        itemStyle: { color: '#66bb6a' },
        areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(102,187,106,0.3)' },
          { offset: 1, color: 'rgba(102,187,106,0.02)' }
        ])}
      },
      {
        name: '特征值3',
        type: 'line',
        data: f3,
        smooth: true,
        showSymbol: true,
        symbol: 'circle',
        symbolSize: 8,
        lineStyle: { color: '#ffa726', width: 2 },
        itemStyle: { color: '#ffa726' },
        areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(255,167,38,0.3)' },
          { offset: 1, color: 'rgba(255,167,38,0.02)' }
        ])}
      }
    ],
    animationDuration: 500,
    animationEasing: 'cubicInOut'
  }
}

function initChart() {
  if (!chartRef.value) return
  chart = echarts.init(chartRef.value, null, { devicePixelRatio: window.devicePixelRatio })
  updateChart()
}

function updateChart() {
  if (!chart) return
  chart.setOption(buildOption(), { notMerge: false })
  chart.resize()
}

function handleResize() {
  chart?.resize()
}

watch(() => props.points, () => {
  updateChart()
}, { deep: true })

onMounted(() => {
  nextTick(initChart)
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  chart?.dispose()
  chart = null
})
</script>

<style scoped>
.feature-chart {
  width: 100%;
  height: 100%;
  min-height: 400px;
}
</style>
