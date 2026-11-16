import { expect, test } from '@playwright/test'

test.beforeEach(async ({ page }) => {
  await page.goto('/')
})

test('el resumen muestra el skeleton y luego el gráfico de Ticket Promedio', async ({ page }) => {
  await expect(page.getByText('Ticket Promedio')).toBeVisible()
  await expect(page.getByLabel('Cargando KPI')).toBeVisible()

  const chart = page.locator('.recharts-surface')
  await expect(chart).toBeVisible()
  await expect(chart.locator('.recharts-line-curve')).toBeVisible()
  await expect(chart.getByText('16 nov')).toBeVisible()
  await expect(page.getByLabel('Cargando KPI')).toBeHidden()
})

test('la pestaña Nuevo KPI carga el formulario con sus categorías del contrato', async ({ page }) => {
  await page.getByRole('tab', { name: 'Nuevo KPI' }).click()

  await expect(page.getByLabel('Nombre del KPI')).toBeVisible()
  await expect(page.getByLabel('Umbral de alerta')).toBeVisible()
  await expect(page.getByRole('combobox', { name: 'Fuente de datos' })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Guardar KPI' })).toBeVisible()

  await page.getByRole('combobox', { name: 'Categoría' }).click()
  await expect(page.getByRole('option')).toHaveText(['Calidad', 'Volumen', 'Completitud', 'Rendimiento', 'Comercial'])
})

test('el formulario valida, guarda con un toast y se limpia', async ({ page }) => {
  await page.getByRole('tab', { name: 'Nuevo KPI' }).click()
  await page.getByRole('button', { name: 'Guardar KPI' }).click()
  await expect(page.getByRole('alert')).toHaveCount(4)

  await page.getByLabel('Nombre del KPI').fill('Ticket promedio mensual')
  await page.getByRole('combobox', { name: 'Categoría' }).click()
  await page.getByRole('option', { name: 'Comercial' }).click()
  await page.getByRole('combobox', { name: 'Fuente de datos' }).click()
  await page.getByRole('option', { name: 'ventas_historicas' }).click()
  await page.getByLabel('Umbral de alerta').fill('40.5')
  await expect(page.getByRole('alert')).toHaveCount(0)

  await page.getByRole('button', { name: 'Guardar KPI' }).click()

  await expect(page.getByText('Configuración guardada')).toBeVisible()
  await expect(page.getByLabel('Nombre del KPI')).toHaveValue('')
  await expect(page.getByLabel('Umbral de alerta')).toHaveValue('')
})
