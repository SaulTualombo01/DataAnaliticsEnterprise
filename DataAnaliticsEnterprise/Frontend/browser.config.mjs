import {defineConfig} from '@playwright/test';

const url = process.env.M2_TEST_BASE_URL;
if (process.env.M2_TEST_ISOLATED !== '1' || !url || !/^http:\/\/(127\.0\.0\.1|localhost):\d+$/.test(url)
    || !process.env.M2_TEST_QUALITY_PASSWORD || !process.env.M2_TEST_PROJECT_ID
    || process.env.M2_TEST_PROJECT_ID === 'b19b98b7-e715-42ca-b5af-02d665db48ad') {
    throw new Error('Las pruebas de navegador requieren el entorno local aislado de verificar_entrega.ps1.');
}

export default defineConfig({
    testDir: '.',
    testMatch: 'quality-flow.spec.mjs',
    workers: 1,
    retries: 0,
    timeout: 120000,
    expect: {timeout: 15000},
    reporter: [['list']],
    use: {baseURL: url, browserName: 'chromium', viewport: {width: 1440, height: 1000}, actionTimeout: 15000, trace: 'off', screenshot: 'off', video: 'off'}
});
