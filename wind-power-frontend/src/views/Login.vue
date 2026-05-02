<template>
  <div class="login-page">
    <div class="login-card">
      <div class="login-header">
        <div class="login-icon">&#9889;</div>
        <h1>风电场智能监测系统</h1>
        <p>{{ isRegister ? '创建新账号' : '登录以访问监测大屏和智能助手' }}</p>
      </div>

      <!-- Tab Switcher -->
      <div class="tab-bar">
        <button :class="{ active: !isRegister }" @click="switchTab(false)">登录</button>
        <button :class="{ active: isRegister }" @click="switchTab(true)">注册</button>
      </div>

      <!-- Login Form -->
      <form v-if="!isRegister" class="login-form" @submit.prevent="doLogin">
        <div class="form-group">
          <label>用户名</label>
          <input v-model="loginForm.user" type="text" placeholder="输入用户名" autocomplete="username" />
        </div>
        <div class="form-group">
          <label>密码</label>
          <input v-model="loginForm.pwd" type="password" placeholder="输入密码" autocomplete="current-password" />
        </div>
        <div v-if="loginError" class="error-msg">{{ loginError }}</div>
        <button type="submit" class="btn-submit" :disabled="loginLoading">
          {{ loginLoading ? '登录中...' : '登 录' }}
        </button>
      </form>

      <!-- Register Form -->
      <form v-else class="login-form" @submit.prevent="doRegister">
        <div class="form-group">
          <label>用户名 *</label>
          <input v-model="regForm.user" type="text" placeholder="登录账号" required />
        </div>
        <div class="form-group">
          <label>密码 *</label>
          <input v-model="regForm.pwd" type="password" placeholder="登录密码" required />
        </div>
        <div class="form-group">
          <label>姓名</label>
          <input v-model="regForm.name" type="text" placeholder="真实姓名" />
        </div>
        <div class="form-row">
          <div class="form-group flex-1">
            <label>性别</label>
            <select v-model="regForm.sex">
              <option value="">不限</option>
              <option value="男">男</option>
              <option value="女">女</option>
            </select>
          </div>
          <div class="form-group flex-1">
            <label>年龄</label>
            <input v-model.number="regForm.age" type="number" placeholder="年龄" />
          </div>
        </div>
        <div class="form-group">
          <label>电话</label>
          <input v-model="regForm.tel" type="text" placeholder="手机号" />
        </div>
        <div class="form-group">
          <label>地址</label>
          <input v-model="regForm.address" type="text" placeholder="地址" />
        </div>
        <div v-if="registerError" class="error-msg">{{ registerError }}</div>
        <div v-if="registerSuccess" class="success-msg">{{ registerSuccess }}</div>
        <button type="submit" class="btn-submit" :disabled="regLoading">
          {{ regLoading ? '注册中...' : '注 册' }}
        </button>
      </form>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuth } from '../composables/useAuth.js'

const router = useRouter()
const route = useRoute()
const { login, register, loginError, registerError, registerSuccess } = useAuth()

const isRegister = ref(false)
const loginLoading = ref(false)
const regLoading = ref(false)

const loginForm = reactive({ user: '', pwd: '' })
const regForm = reactive({
  user: '', pwd: '', name: '', sex: '', age: null, tel: '', address: '', position: 1
})

function switchTab(reg) {
  isRegister.value = reg
  loginError.value = ''
  registerError.value = ''
  registerSuccess.value = ''
}

async function doLogin() {
  if (!loginForm.user || !loginForm.pwd) return
  loginLoading.value = true
  const ok = await login(loginForm.user, loginForm.pwd)
  loginLoading.value = false
  if (ok) {
    const redirect = route.query.redirect || '/dashboard'
    router.push(redirect)
  }
}

async function doRegister() {
  if (!regForm.user || !regForm.pwd) {
    registerError.value = '用户名和密码不能为空'
    return
  }
  regLoading.value = true
  const ok = await register({ ...regForm })
  regLoading.value = false
  if (ok) {
    // Clear form and switch to login
    Object.assign(regForm, { user: '', pwd: '', name: '', sex: '', age: null, tel: '', address: '' })
    setTimeout(() => switchTab(false), 1500)
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh; display: flex; align-items: center; justify-content: center;
  background: linear-gradient(135deg, #0d1b2a 0%, #1b2838 50%, #1a3a4a 100%);
}
.login-card {
  background: #112233; border: 1px solid #1a3344; border-radius: 16px;
  padding: 40px; width: 420px; max-width: 90vw;
}
.login-header { text-align: center; margin-bottom: 28px; }
.login-icon { font-size: 48px; margin-bottom: 12px; }
.login-header h1 { font-size: 22px; color: #e0e0e0; margin-bottom: 6px; }
.login-header p { font-size: 13px; color: #667788; }

.tab-bar { display: flex; margin-bottom: 24px; border-radius: 8px; overflow: hidden; border: 1px solid #334455; }
.tab-bar button {
  flex: 1; padding: 10px; border: none; background: transparent;
  color: #667788; font-size: 14px; cursor: pointer; transition: all 0.2s;
}
.tab-bar button.active { background: #1e88e5; color: #fff; }

.login-form { display: flex; flex-direction: column; gap: 16px; }
.form-group { display: flex; flex-direction: column; gap: 4px; }
.form-group label { font-size: 12px; color: #8899aa; text-transform: uppercase; letter-spacing: 1px; }
.form-group input, .form-group select {
  background: #0d1b2a; border: 1px solid #334455; color: #e0e0e0;
  padding: 10px 14px; border-radius: 6px; font-size: 14px; outline: none; transition: border-color 0.2s;
}
.form-group input:focus, .form-group select:focus { border-color: #4fc3f7; }
.form-row { display: flex; gap: 12px; }
.flex-1 { flex: 1; }

.error-msg { color: #ef5350; font-size: 13px; padding: 8px 12px; background: rgba(239,83,80,0.1); border-radius: 6px; }
.success-msg { color: #66bb6a; font-size: 13px; padding: 8px 12px; background: rgba(102,187,106,0.1); border-radius: 6px; }

.btn-submit {
  background: #1e88e5; color: #fff; border: none; padding: 12px; border-radius: 8px;
  font-size: 15px; font-weight: 600; cursor: pointer; transition: background 0.2s; margin-top: 4px;
}
.btn-submit:hover:not(:disabled) { background: #1565c0; }
.btn-submit:disabled { opacity: 0.6; cursor: not-allowed; }
</style>
