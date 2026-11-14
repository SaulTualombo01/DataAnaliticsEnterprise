import { test, expect } from '@playwright/test';

test('navegación, diccionario y cambio de contexto de demostración', async ({ page }) => {
  const calls: string[] = []; page.on('request', request => { if (new URL(request.url()).pathname.startsWith('/api/')) calls.push(request.url()); });
  await page.goto('http://127.0.0.1:4173');
  await expect(page.getByRole('heading', { name: 'orders', exact: true })).toBeVisible();
  await expect(page.getByRole('table')).toBeVisible();
  await expect(page.getByText('Demostración', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'M3 Gobierno' }).click();
  await expect(page.getByRole('heading', { name: 'Vista aún no integrada' })).toBeVisible();
  await page.getByRole('button', { name: 'Volver al catálogo' }).click();
  await expect(page.getByRole('heading', { name: 'orders', exact: true })).toBeVisible();
  await page.getByLabel('Proyecto de demostración').selectOption('1');
  await expect(page.getByRole('heading', { name: 'Este proyecto aún no tiene activos' })).toBeVisible();
  await expect(page.getByRole('table')).toHaveCount(0);
  expect(calls).toEqual([]);
});

test('vacío, denegación, indisponibilidad y expiración son estados distintos', async ({ page }) => {
  await page.goto('http://127.0.0.1:4173');
  await expect(page.getByRole('heading', { name: 'orders', exact: true })).toBeVisible();
  await page.getByLabel('Escenario de demostración').selectOption('empty');
  await expect(page.getByRole('heading', { name: 'No hay campos en esta página' })).toBeVisible();
  await page.getByLabel('Escenario de demostración').selectOption('denied');
  await expect(page.getByRole('alert')).toContainText('no tiene permiso');
  await expect(page.getByRole('table')).toHaveCount(0);
  await page.getByLabel('Escenario de demostración').selectOption('unavailable');
  await expect(page.getByRole('alert')).toContainText('No se pudo completar');
  await page.getByLabel('Escenario de demostración').selectOption('expired');
  await expect(page.getByRole('button', { name: 'Entrar a la demostración' })).toBeVisible();
  await page.getByRole('button', { name: 'Entrar a la demostración' }).click();
  await expect(page.getByRole('heading', { name: 'orders', exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Cerrar sesión' }).click();
  await expect(page.getByRole('button', { name: 'Entrar a la demostración' })).toBeVisible();
});

test('el modo real no usa mocks si la API falla', async ({ page }) => {
  await page.route('**/api/**', route => route.fulfill({ status: 503, contentType: 'application/problem+json', body: JSON.stringify({ status: 503, detail: 'private SQL details' }) }));
  await page.goto('http://127.0.0.1:4174');
  await expect(page.getByRole('heading', { name: 'No se pudo verificar la sesión' })).toBeVisible();
  await expect(page.getByText('Demostración', { exact: true })).toHaveCount(0);
  await expect(page.getByText('private SQL details')).toHaveCount(0);
  await expect(page.getByRole('table')).toHaveCount(0);
});

test('login real usa los endpoints del contrato y limpia la contraseña del formulario', async ({ page }) => {
  const requestId = '00000000-0000-0000-0000-000000000001';
  let logged = false; const posts: string[] = [];
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname;
    if (path.endsWith('/csrf')) return route.fulfill({ json: { data: { header_name: 'X-CSRF-TOKEN', token: 'synthetic-csrf' }, meta: { request_id: requestId } } });
    if (path.endsWith('/login')) {
      posts.push(route.request().postData() || ''); expect(route.request().headers()['x-csrf-token']).toBe('synthetic-csrf'); logged = true;
      return route.fulfill({ json: { data: { authenticated: true, user_id: requestId }, meta: { request_id: requestId } } });
    }
    if (logged) return route.fulfill({ json: { data: { id: requestId, email: 'empleado@example.test', display_name: 'Empleado de prueba' }, meta: { request_id: requestId } } });
    return route.fulfill({ status: 401, json: { status: 401 } });
  });
  await page.goto('http://127.0.0.1:4174');
  await page.getByLabel('Correo electrónico').fill('empleado@example.test');
  await page.getByLabel('Contraseña', { exact: true }).fill('synthetic-test-password');
  await page.getByRole('button', { name: 'Iniciar sesión' }).click();
  await expect(page.getByRole('heading', { name: 'Abrir un activo autorizado' })).toBeVisible();
  expect(posts).toHaveLength(1);
  expect([...new URLSearchParams(posts[0]).keys()]).toEqual(['email', 'password']);
  expect(await page.evaluate(() => ({ local: localStorage.length, session: sessionStorage.length }))).toEqual({ local: 0, session: 0 });
  await expect(page.getByLabel('Proyecto de demostración')).toHaveCount(0);
});

test('interfaz móvil y escritorio sin desbordamiento de página', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 1000 });
  await page.goto('http://127.0.0.1:4173');
  await expect(page.getByRole('heading', { name: 'orders', exact: true })).toBeVisible();
  await page.screenshot({ path: 'test-results/shell-desktop.png', fullPage: true });
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.getByRole('heading', { name: 'orders', exact: true })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.screenshot({ path: 'test-results/shell-mobile.png', fullPage: true });
});
