import { defineConfig } from '@playwright/test';
export default defineConfig({workers:1,use:{baseURL:process.env.BASE_URL||'http://localhost:3000'},testDir:'./e2e'});
