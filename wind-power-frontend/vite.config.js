import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        configure: (proxy) => {
          proxy.on('proxyRes', (proxyRes) => {
            // SSE 响应禁用缓冲，确保流式输出
            if (proxyRes.headers['content-type']?.includes('text/event-stream')) {
              proxyRes.headers['cache-control'] = 'no-cache'
              proxyRes.headers['x-accel-buffering'] = 'no'
            }
          })
        }
      },
      '/realtime': 'http://localhost:8080',
      '/user': 'http://localhost:8080',
      '/windturbine': 'http://localhost:8080',
      '/searchMaxWindturbineId': 'http://localhost:8080',
      '/windfarms': 'http://localhost:8080'
    }
  }
})
