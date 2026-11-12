import { defineConfig } from 'vite';
import tailwindcss from '@tailwindcss/vite';
export default defineConfig({
  plugins: [tailwindcss()],
  server: { proxy: { '/api': { target: 'http://127.0.0.1:8080', changeOrigin: false } } },
  build: {
    outDir: '../target/frontend', emptyOutDir: true,
    rolldownOptions: {
      onLog(level, log, handler) {
        // Esta SPA se renderiza en el cliente; las directivas RSC de Motion no aplican.
        if (log.code === 'MODULE_LEVEL_DIRECTIVE' && log.message.includes('use client') && /node_modules[\\/](?:framer-motion|motion)[\\/]/.test(log.id ?? '')) return;
        handler(level, log);
      }
    }
  }
});
