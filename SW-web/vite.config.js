import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const target = env.VITE_GATEWAY_URL || 'http://localhost:10086'

  return {
    plugins: [vue()],
    server: {
      host: '127.0.0.1',
      port: 18888,
      proxy: {
        '/user': { target, changeOrigin: true },
        '/video': { target, changeOrigin: true },
        '/ai': { target, changeOrigin: true, ws: true }
      }
    }
  }
})
