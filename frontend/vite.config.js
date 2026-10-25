import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
export default defineConfig({ plugins:[vue()], build:{rollupOptions:{output:{manualChunks(id){if(id.includes('/src/locales/'))return 'translations';if(id.includes('/node_modules/franc-min/'))return 'language-detection';}}}}, server:{port:5173,proxy:{'/api':'http://127.0.0.1:8080'}} });
