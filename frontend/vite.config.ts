import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// `npm run dev` serves the dashboard on :5173 and forwards API calls to the Spring Boot app
// on :8080, so cookies and CSRF work exactly as in production (same origin).
const backend = 'http://localhost:8080';

export default defineConfig({
  plugins: [react()],
  build: {
    outDir: 'dist',
    emptyOutDir: true,
    assetsDir: 'assets',
  },
  server: {
    proxy: {
      '/api': backend,
      '/media': backend,
      '/webhook': backend,
    },
  },
});
