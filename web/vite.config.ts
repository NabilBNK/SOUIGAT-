import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const proxyTarget = env.API_PROXY_URL || 'http://localhost:8002'

  return {
    plugins: [react(), tailwindcss()],
    server: {
      host: true,
      port: parseInt(process.env.PORT || '5173'),
      proxy: {
        '/api': {
          target: proxyTarget,
          changeOrigin: true,
        },
        '/media': {
          target: proxyTarget,
          changeOrigin: true,
        },
      },
    },
  }
})
