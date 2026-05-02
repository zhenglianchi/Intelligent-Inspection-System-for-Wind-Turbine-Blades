<template>
  <div class="dashboard-page">
    <!-- Top Bar -->
    <header class="top-bar">
      <div class="top-bar-left">
        <h1 class="page-title">
          <span class="title-icon">&#9889;</span>
          风电场特征值实时监测
        </h1>
      </div>
      <div class="top-bar-center">
        <span class="selected-info" v-if="selectedFarm">
          {{ selectedFarm }}风场 · {{ windturbine || '未选择' }}号风机
        </span>
      </div>
      <div class="top-bar-right">
        <div class="selector-inline">
          <label>刷新间隔</label>
          <select v-model="intervalMs" class="top-select">
            <option :value="1000">1秒</option>
            <option :value="3000">3秒</option>
            <option :value="5000">5秒</option>
            <option :value="10000">10秒</option>
          </select>
        </div>
        <span class="status-badge" :class="loading ? 'loading-badge' : 'ok-badge'">
          {{ loading ? '查询中...' : '在线' }}
        </span>
        <span class="point-count" v-if="windturbine">数据点: {{ points.length }}/{{ capacity || 20 }}</span>
      </div>
    </header>

    <!-- Main Area -->
    <div class="main-area">
      <!-- Left: Wind Farm + Turbine Tree -->
      <aside class="turbine-sidebar">
        <div class="sidebar-header">风场列表</div>
        <div class="farm-list">
          <div v-for="farm in windfarms" :key="farm.windfarm" class="farm-group">
            <div class="farm-item" @click="toggleFarm(farm)">
              <span class="farm-arrow">{{ farm.expanded ? '▼' : '▶' }}</span>
              <span class="farm-name">{{ farm.name }}</span>
              <span class="farm-code">({{ farm.windfarm }})</span>
            </div>
            <div v-if="farm.expanded" class="turbine-sublist">
              <div
                v-for="id in farm.turbineIds"
                :key="id"
                class="turbine-item"
                :class="{ active: windturbine === id && windfarm === farm.windfarm }"
                @click="selectTurbine(farm.windfarm, id)"
              >
                <span class="turbine-id">{{ id }}号风机</span>
                <span class="turbine-dot"></span>
              </div>
            </div>
          </div>
        </div>
      </aside>

      <!-- Right: Chart -->
      <section class="chart-area">
        <div v-if="!windturbine" class="no-selection">
          请从左侧选择一台风机
        </div>
        <template v-else>
          <FeatureChart :points="points" />
          <LatestValueCards :points="points" />
        </template>
        <div class="chart-footer">
          <span v-if="error" class="error-text">{{ error }}</span>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted, watch } from 'vue'
import FeatureChart from '../components/FeatureChart.vue'
import LatestValueCards from '../components/LatestValueCards.vue'
import { useFeatureCurve } from '../composables/useFeatureCurve.js'
import { listWindfarms } from '../api/index.js'

const { windfarm, windturbine, points, capacity, loading, error, fetchLatest,
  startPolling, stopPolling, switchTurbine } = useFeatureCurve()

const intervalMs = ref(1000)
const windfarms = ref([])
const selectedFarm = ref('')
let lastInterval = 1000

async function loadWindfarms() {
  try {
    const res = await listWindfarms()
    if (res.status === '200' && res.data) {
      windfarms.value = res.data.map(f => ({
        ...f,
        expanded: false,
        turbineIds: Array.from({ length: f.turbineCount || 14 }, (_, i) => i + 1)
      }))
    }
  } catch { /* ignore */ }
}

function toggleFarm(farm) {
  farm.expanded = !farm.expanded
}

function selectTurbine(wf, id) {
  selectedFarm.value = wf
  switchTurbine(wf, id, intervalMs.value)
  localStorage.setItem('dashboard_windfarm', wf)
  localStorage.setItem('dashboard_turbine', String(id))
}

function restoreSelection() {
  const savedFarm = localStorage.getItem('dashboard_windfarm')
  const savedTurbine = localStorage.getItem('dashboard_turbine')
  if (savedFarm && savedTurbine) {
    // Expand the saved farm
    const farm = windfarms.value.find(f => f.windfarm === savedFarm)
    if (farm) farm.expanded = true
    selectTurbine(savedFarm, Number(savedTurbine))
  }
}

watch(intervalMs, (newVal) => {
  if (newVal !== lastInterval) {
    lastInterval = newVal
    if (windfarm.value && windturbine.value != null) {
      stopPolling()
      startPolling(newVal)
    }
  }
})

onMounted(async () => {
  await loadWindfarms()
  restoreSelection()
})

onUnmounted(() => stopPolling())
</script>

<style scoped>
/* Top Bar */
.top-bar {
  display: flex; align-items: center; justify-content: space-between;
  padding: 10px 20px; background: #0d1b2a; border-bottom: 1px solid #1a3344;
}
.page-title { font-size: 18px; font-weight: 700; color: #e8e8e8; display: flex; align-items: center; gap: 8px; }
.title-icon { font-size: 22px; }
.selected-info { color: #4fc3f7; font-size: 14px; }
.selector-inline { display: flex; align-items: center; gap: 8px; }
.selector-inline label { font-size: 12px; color: #8899aa; white-space: nowrap; }
.top-select {
  background: #1a2332; border: 1px solid #334455; color: #e0e0e0;
  padding: 4px 8px; border-radius: 5px; font-size: 13px; outline: none;
}
.top-bar-right { display: flex; align-items: center; gap: 16px; }
.status-badge { padding: 3px 12px; border-radius: 20px; font-size: 12px; font-weight: 600; }
.ok-badge { background: rgba(102,187,106,0.15); color: #66bb6a; border: 1px solid rgba(102,187,106,0.3); }
.loading-badge { background: rgba(79,195,247,0.15); color: #4fc3f7; border: 1px solid rgba(79,195,247,0.3); }
.point-count { font-size: 13px; color: #667788; }

/* Main */
.main-area { display: flex; flex: 1; overflow: hidden; }

/* Sidebar */
.turbine-sidebar {
  width: 220px; flex-shrink: 0; background: #0c1a28;
  border-right: 1px solid #1a3344; overflow-y: auto;
}
.sidebar-header {
  padding: 12px 16px; font-size: 12px; color: #667788;
  border-bottom: 1px solid #1a3344; text-transform: uppercase; letter-spacing: 1px;
}
.farm-list { padding: 4px 0; }
.farm-group { border-bottom: 1px solid rgba(26,51,68,0.5); }
.farm-item {
  display: flex; align-items: center; gap: 6px; padding: 10px 14px;
  cursor: pointer; color: #aabbcc; font-size: 13px; transition: background 0.15s;
}
.farm-item:hover { background: rgba(255,255,255,0.04); }
.farm-arrow { font-size: 10px; width: 14px; color: #556677; }
.farm-name { flex: 1; }
.farm-code { font-size: 11px; color: #556677; }
.turbine-sublist { padding-left: 20px; padding-bottom: 4px; }
.turbine-item {
  display: flex; align-items: center; justify-content: space-between;
  padding: 7px 14px; border-radius: 4px; cursor: pointer;
  color: #778899; font-size: 13px; transition: all 0.15s;
}
.turbine-item:hover { background: rgba(255,255,255,0.05); color: #bbb; }
.turbine-item.active { background: rgba(79,195,247,0.12); color: #4fc3f7; }
.turbine-dot { width: 6px; height: 6px; border-radius: 50%; background: #444; }
.turbine-item.active .turbine-dot { background: #66bb6a; }

/* Chart */
.chart-area { flex: 1; display: flex; flex-direction: column; padding: 16px 24px; overflow: auto; }
.no-selection { flex: 1; display: flex; align-items: center; justify-content: center; color: #667788; font-size: 16px; }
.chart-footer { padding: 8px 0; font-size: 13px; color: #556677; border-top: 1px solid #1a3344; margin-top: 8px; }
.error-text { color: #ef5350; }
</style>
