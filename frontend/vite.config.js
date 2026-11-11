import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// The dev server proxies the backend so the browser sees a single origin (needed for the session cookie).
// changeOrigin must stay false (Vite's string shorthand turns it on): the backend builds the OAuth callback
// URL from the Host header, and it has to be localhost:5173, not the backend's own port.
const backend = { target: 'http://localhost:8081', changeOrigin: false };

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': backend,
      '/oauth2': backend, // start sign-in: /oauth2/authorization/{google|github}
      '/login/oauth2': backend, // provider callback: /login/oauth2/code/{google|github}
      '/swagger-ui': backend,
      '/v3/api-docs': backend,
    },
  },
});
