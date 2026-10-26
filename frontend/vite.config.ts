import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');

  const accounts = mode === 'development'
    ? [
      {
        role: 'Viewer',
        username: env.Viewer_Username || '',
        password: env.Viewer_Password || ''
      },
      {
        role: 'Editor',
        username: env.Editor_Username || '',
        password: env.Editor_Password || ''
      },
      {
        role: 'Admin',
        username: env.Admin_Username || '',
        password: env.Admin_Password || ''
      }
    ]
    : [];

  return {
    plugins: [react()],
    define: {
      __QUICK_ACCOUNTS__: JSON.stringify(accounts)
    },
    server: {
      host: '0.0.0.0',
      port: 5173,
      strictPort: true,
      allowedHosts: ['ecm-frontend', 'frontend', 'localhost'],

      proxy: {
        '/api': {
          target: 'http://backend:8080',
          changeOrigin: true
        },
        '/wopi': {
          target: 'http://backend:8080',
          changeOrigin: true
        },
        '/collabora': {
          target: 'http://collabora:9980',
          changeOrigin: true,
          rewrite: path => path.replace(/^\/collabora/, '')
        }
      }
    }
  };
});
