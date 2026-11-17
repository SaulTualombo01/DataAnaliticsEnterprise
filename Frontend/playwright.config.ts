import { defineConfig, devices } from '@playwright/test'

// Puertos reservados por el equipo para pruebas: 4173 en modo demostración y 4174 en modo real.
const DEMO_URL = 'http://127.0.0.1:4173'
const REAL_URL = 'http://127.0.0.1:4174'

export default defineConfig({
  // e2e: pruebas del shell compartido. qa-e2e: pruebas de la vista de analítica (M4).
  testDir: '.',
  testMatch: ['e2e/**/*.spec.ts', 'qa-e2e/**/*.spec.ts'],
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: DEMO_URL,
    trace: 'on-first-retry',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: [
    { command: 'npm run dev:demo -- --port 4173', url: DEMO_URL, reuseExistingServer: !process.env.CI },
    { command: 'npm run dev -- --port 4174', url: REAL_URL, reuseExistingServer: !process.env.CI },
  ],
})
