import { defineConfig } from '@playwright/test';
export default defineConfig({
  testDir:'./e2e-real',
  fullyParallel:false,
  workers:1,
  retries:0,
  outputDir:'test-results/real',
  forbidOnly:true,
  timeout:120000,
  reporter:[['list'],['json',{outputFile:'test-results/real-results.json'}]],
  use:{
    baseURL:process.env.TC10_FRONTEND_URL,
    browserName:'chromium',
    headless:true,
    actionTimeout:10000,
    navigationTimeout:15000,
    trace:'off',
    screenshot:'off',
    launchOptions:process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH?{executablePath:process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH}:{},
  },
});
