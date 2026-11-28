import { defineConfig } from 'vite'
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react-swc'
import path from 'path'

const normalizedBasePath = (process.env.VITE_BASE_PATH ?? '').trim().replace(/^\/+|\/+$/g, '')
const base = normalizedBasePath ? `/${normalizedBasePath}/` : '/'

// https://vitejs.dev/config/
export default defineConfig({
  base,
  plugins: [tailwindcss(),react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    proxy: {
      '/api/orchestrator': {
        target: 'http://localhost:8080', // Spring Boot backend
        changeOrigin: true,
        secure: false,
      },
    },
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks: {
          three: ['three', '@react-three/fiber', '@react-three/drei'],
        },
      },
    },
  },
})