import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  base: '/',
  server: {
    port: 5180,
    strictPort: true,
    proxy: {
      '/api/auth': {
        target: 'http://localhost:8091',
        changeOrigin: false,
      },
    },
  },
});
