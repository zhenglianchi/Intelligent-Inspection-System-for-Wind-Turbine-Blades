import { ref, computed } from 'vue'
import { getToken, setToken, removeToken, login as loginApi, register as registerApi } from '../api/index.js'

const currentUser = ref(null)
const isAuthenticated = computed(() => !!getToken())

export function useAuth() {
  const loginError = ref('')
  const registerError = ref('')
  const registerSuccess = ref('')

  async function login(user, pwd) {
    loginError.value = ''
    try {
      const result = await loginApi(user, pwd)
      if (result.status === '200' && result.data?.token) {
        setToken(result.data.token)
        currentUser.value = { username: result.data.username || user }
        return true
      } else {
        loginError.value = result.message || '登录失败'
        return false
      }
    } catch (e) {
      loginError.value = '网络错误，请检查服务是否启动'
      return false
    }
  }

  async function register(userData) {
    registerError.value = ''
    registerSuccess.value = ''
    try {
      const result = await registerApi(userData)
      if (result.status === '200') {
        registerSuccess.value = result.message || '注册成功'
        return true
      }
      registerError.value = result.message || '注册失败'
      return false
    } catch (e) {
      registerError.value = '网络错误'
      return false
    }
  }

  function logout() {
    removeToken()
    currentUser.value = null
    window.location.href = '/login'
  }

  function checkAuth() {
    const token = getToken()
    if (token) {
      try {
        const payload = JSON.parse(atob(token.split('.')[1]))
        currentUser.value = { username: payload.username || payload.sub }
      } catch {
        removeToken()
      }
    }
  }

  return {
    currentUser, isAuthenticated,
    loginError, registerError, registerSuccess,
    login, register, logout, checkAuth
  }
}
