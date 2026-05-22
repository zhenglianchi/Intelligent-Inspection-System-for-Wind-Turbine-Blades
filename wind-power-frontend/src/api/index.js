import axios from 'axios'

const api = axios.create({
  baseURL: '',
  timeout: 60000
})

// JWT token management
const TOKEN_KEY = 'wind_power_token'

export function getToken() {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token) {
  localStorage.setItem(TOKEN_KEY, token)
}

export function removeToken() {
  localStorage.removeItem(TOKEN_KEY)
}

// Request interceptor: attach JWT for all endpoints except login/register
api.interceptors.request.use(config => {
  const token = getToken()
  const publicPaths = ['/user/login', '/user/createNewUser']
  const isPublic = publicPaths.some(p => config.url.includes(p))

  if (token && !isPublic) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// Response interceptor: unwrap Result<T>
api.interceptors.response.use(
  response => response.data,
  error => {
    if (error.response?.status === 401) {
      removeToken()
    }
    return Promise.reject(error)
  }
)

// ==================== Feature Curve APIs ====================

export function queryLatestFeaCurve(windfarm, windturbine) {
  return api.get('/realtime/quaryLatestFeaCurve', {
    params: { windfarm, windturbine }
  })
}

export function queryWindFarmLastRecord(windfarm, N = 10) {
  return api.get('/realtime/queryWindFarmLastRecord', {
    params: { windfarm, N }
  })
}

export function queryWindFarmLastRecordByStatus(windfarm, status, N = 10) {
  return api.get('/realtime/queryWindFarmLastRecordByStatus', {
    params: { windfarm, status, N }
  })
}

// ==================== Wind Farm / Turbine APIs ====================

export function listWindfarms() {
  return api.get('/windfarms')
}

export function searchMaxWindturbineId(windfarm) {
  return api.get('/searchMaxWindturbineId', { params: { windfarm } })
}

export function queryAllWindturbineStatus(windfarm) {
  return api.get('/windturbine/queryAllWindturbineStatus', { params: { windfarm } })
}

// ==================== Chat APIs ====================

export function chatStream(body, memoryId, signal) {
  const params = memoryId ? `?memoryId=${encodeURIComponent(memoryId)}` : ''
  const headers = { 'Content-Type': 'application/json' }
  const token = getToken()
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }
  return fetch(`/api/chat/stream${params}`, {
    method: 'POST',
    headers,
    body: JSON.stringify(body),
    signal
  })
}

export function chat(body, memoryId) {
  const params = memoryId ? `?memoryId=${encodeURIComponent(memoryId)}` : ''
  return api.post(`/api/chat${params}`, body)
}

// ==================== Degradation / Queue APIs ====================

export function getDegradationStatus() {
  return api.get('/api/degradation/status')
}

export function setDegradationLevel(level) {
  return api.post('/api/degradation/level', null, { params: { level } })
}

export function resetDegradation() {
  return api.post('/api/degradation/reset')
}

export function getQueueStats() {
  return api.get('/api/queue/stats')
}

// ==================== Session APIs ====================

export function listSessions() {
  return api.get('/api/sessions')
}

export function getSessionHistory(memoryId) {
  return api.get(`/api/sessions/${memoryId}/history`)
}

export function deleteSession(memoryId) {
  return api.delete(`/api/sessions/${memoryId}`)
}

// ==================== Knowledge Base APIs ====================

export function uploadKnowledgePdf(file) {
  const formData = new FormData()
  formData.append('file', file)
  // Use raw axios to set Content-Type multipart
  return axios.create({ baseURL: '', timeout: 120000 }).post('/api/knowledge/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export function rebuildKnowledgeBase() {
  return api.post('/api/knowledge/async/rebuild')
}

export function clearAndRebuildKnowledgeBase() {
  return api.post('/api/knowledge/async/clear-and-rebuild')
}

export function getRebuildStatus(taskId) {
  return api.get(`/api/knowledge/async/status/${taskId}`)
}

export function getCurrentRebuildStatus() {
  return api.get('/api/knowledge/async/status')
}

// ==================== Metrics APIs ====================

export function getMetrics() {
  return api.get('/api/metrics/global')
}

export function getSessionMetrics() {
  return api.get('/api/metrics/sessions')
}

// ==================== User APIs ====================

export function login(user, pwd) {
  return api.post('/user/login', { user, pwd })
}

export function register(userData) {
  return api.post('/user/createNewUser', userData)
}

export function searchAllUser() {
  return api.get('/user/searchAllUser')
}

export default api
