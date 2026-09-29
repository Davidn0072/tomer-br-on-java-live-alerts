import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

// Local `npm run dev` proxies /api and /ws to the server's directly-published port; in
// docker-compose, nginx does the equivalent proxying in front of the built static files (see
// nginx.conf) so the client only ever talks to same-origin paths either way.
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
      '/ws': {
        target: 'ws://localhost:8080',
        ws: true,
      },
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: './src/test/setup.ts',
    globals: true,
  },
});
