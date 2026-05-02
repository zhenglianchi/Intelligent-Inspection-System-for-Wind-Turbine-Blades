<template>
  <div id="app-root">
    <nav class="main-nav">
      <div class="nav-brand">
        <span class="brand-icon">&#9889;</span>
        <span class="brand-text">Wind Power Agent</span>
      </div>
      <div class="nav-links">
        <router-link to="/dashboard" class="nav-link" active-class="active">
          <span class="nav-icon">&#9638;</span> 监测大屏
        </router-link>
        <router-link to="/chat" class="nav-link" active-class="active">
          <span class="nav-icon">&#9743;</span> 智能助手
        </router-link>
      </div>
      <div class="nav-right">
        <template v-if="isAuthenticated">
          <span class="user-name">{{ currentUser?.username }}</span>
          <button class="nav-btn" @click="logout">退出</button>
        </template>
        <router-link v-else to="/login" class="nav-link">登录</router-link>
      </div>
    </nav>
    <div class="main-content">
      <router-view />
    </div>
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import { useAuth } from './composables/useAuth.js'

const { currentUser, isAuthenticated, logout, checkAuth } = useAuth()

onMounted(() => {
  checkAuth()
})
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
  background: #0a1628;
  color: #e0e0e0;
  overflow-x: hidden;
}

#app-root {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.main-nav {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #0d1b2a;
  border-bottom: 1px solid #1a3344;
  padding: 0 24px;
  height: 56px;
  flex-shrink: 0;
}

.nav-brand {
  display: flex;
  align-items: center;
  gap: 10px;
}

.brand-icon {
  font-size: 22px;
}

.brand-text {
  font-size: 17px;
  font-weight: 700;
  color: #e0e0e0;
  letter-spacing: 0.5px;
}

.nav-links {
  display: flex;
  gap: 4px;
}

.nav-link {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  color: #8899aa;
  text-decoration: none;
  border-radius: 6px;
  font-size: 14px;
  transition: all 0.2s;
}

.nav-link:hover {
  color: #e0e0e0;
  background: rgba(255,255,255,0.05);
}

.nav-link.active {
  color: #4fc3f7;
  background: rgba(79, 195, 247, 0.1);
}

.nav-icon {
  font-size: 16px;
}

.nav-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-name {
  font-size: 14px;
  color: #8899aa;
}

.nav-btn {
  background: transparent;
  border: 1px solid #445566;
  color: #aabbcc;
  padding: 6px 14px;
  border-radius: 6px;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
}

.nav-btn:hover {
  background: rgba(255,255,255,0.05);
  color: #e0e0e0;
}

.main-content {
  flex: 1;
  display: flex;
  flex-direction: column;
}
</style>
