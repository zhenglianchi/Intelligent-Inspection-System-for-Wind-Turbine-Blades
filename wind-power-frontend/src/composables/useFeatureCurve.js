import { ref } from 'vue'
import { queryLatestFeaCurve } from '../api/index.js'

export function useFeatureCurve() {
  const windfarm = ref('')
  const windturbine = ref(null)
  const points = ref([])       // 后端 Redis 队列返回的完整特征点列表
  const capacity = ref(0)
  const loading = ref(false)
  const error = ref('')

  let pollTimer = null

  // 轮询：直接拿后端 Redis 中 FeaCurveBO.feePoints 完整队列
  async function fetchLatest() {
    if (!windfarm.value || windturbine.value == null) return

    try {
      loading.value = true
      error.value = ''
      const result = await queryLatestFeaCurve(windfarm.value, windturbine.value)

      if (result.status === '200' && result.data) {
        capacity.value = result.data.capacity || 0
        points.value = result.data.feePoints || []
      } else if (result.status === '404') {
        points.value = []
        error.value = ''
      } else {
        error.value = result.message || '查询失败'
      }
    } catch (e) {
      error.value = '网络请求失败'
    } finally {
      loading.value = false
    }
  }

  function startPolling(intervalMs = 10000) {
    stopPolling()
    fetchLatest()
    pollTimer = setInterval(fetchLatest, intervalMs)
  }

  function stopPolling() {
    if (pollTimer) {
      clearInterval(pollTimer)
      pollTimer = null
    }
  }

  // 切换风机：停旧轮询 → 清空旧数据 → 启新轮询
  function switchTurbine(newWindfarm, newWindturbine, intervalMs = 10000) {
    stopPolling()
    windfarm.value = newWindfarm
    windturbine.value = newWindturbine
    points.value = []
    startPolling(intervalMs)
  }

  return {
    windfarm,
    windturbine,
    points,
    capacity,
    loading,
    error,
    fetchLatest,
    startPolling,
    stopPolling,
    switchTurbine
  }
}
