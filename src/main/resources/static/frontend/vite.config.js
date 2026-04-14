import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'

export default defineConfig({
  plugins: [react()],
  base: '/',
  build: {
    // output into src/main/resources/static/react-app
    outDir: path.resolve(__dirname, '../'),
    emptyOutDir: true
  }
})
