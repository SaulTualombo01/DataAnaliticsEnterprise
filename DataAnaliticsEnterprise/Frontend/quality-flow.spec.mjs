import {test, expect} from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

async function checkAccessibility(page, checkpoint) {
    // Evalúa el contenido legible al terminar las entradas, no un fotograma de opacidad intermedia.
    await expect.poll(() => page.locator('[data-reveal]').evaluateAll(elements => elements.every(element => getComputedStyle(element).opacity === '1'))).toBe(true);
    const report = await new AxeBuilder({page}).withTags(['wcag2a','wcag2aa','wcag21aa']).analyze();
    expect(report.violations, `Accesibilidad: ${checkpoint}`).toEqual([]);
    expect(await page.evaluate(() => window.m2CspViolations ?? [])).toEqual([]);
}

async function checkHoverContrast(button) {
    await button.hover();
    const contrast = await button.evaluate(element => {
        const context = document.createElement('canvas').getContext('2d');
        function luminance(color) {
            context.clearRect(0,0,1,1); context.fillStyle = color; context.fillRect(0,0,1,1);
            const rgb = [...context.getImageData(0,0,1,1).data].slice(0,3).map(value => {
                const s = value / 255; return s <= 0.04045 ? s / 12.92 : ((s + 0.055) / 1.055) ** 2.4;
            });
            return rgb[0] * 0.2126 + rgb[1] * 0.7152 + rgb[2] * 0.0722;
        }
        const styles = getComputedStyle(element);
        const colors = [luminance(styles.color),luminance(styles.backgroundColor)].sort((a,b) => a-b);
        return (colors[1]+0.05)/(colors[0]+0.05);
    });
    expect(contrast).toBeGreaterThanOrEqual(4.5);
}

test('CSV, reglas persistentes, evaluaciones por dimensión y evidencia explícita', async ({page}) => {
    const runtimeErrors = [];
    page.on('pageerror', error => runtimeErrors.push(error.message));
    await page.addInitScript(() => {
        window.m2CspViolations = [];
        document.addEventListener('securitypolicyviolation', event => window.m2CspViolations.push(event.violatedDirective));
    });
    await page.goto('/');
    await checkAccessibility(page,'acceso');
    await page.screenshot({path:'../target/ui-motion-login.png',fullPage:true});
    await page.getByLabel('Correo', {exact: true}).focus();
    await page.keyboard.press('Tab');
    await expect(page.getByLabel('Contraseña', {exact: true})).toBeFocused();
    await page.getByLabel('Correo', {exact: true}).fill('calidad@example.test');
    await page.getByLabel('Contraseña', {exact: true}).fill(process.env.M2_TEST_QUALITY_PASSWORD);
    await page.getByRole('button', {name: 'Iniciar sesión', exact: true}).click();
    await expect(page.getByRole('heading', {name: 'Estado e indicadores'})).toBeVisible();
    await page.getByRole('button', {name: 'Subir CSV', exact: true}).click();
    const upload = page.getByRole('dialog', {name: 'Subir archivo CSV'});
    await upload.getByLabel('Archivo CSV', {exact: true}).setInputFiles({
        name: 'navegador.csv', mimeType: 'text/csv',
        buffer: Buffer.from(`id,correo\n1,uno@example.test\n2,\n2,dos@example.test\n`)
    });
    await upload.getByRole('button', {name: 'Revisar archivo', exact: true}).click();
    await expect(upload.getByRole('heading', {name: 'Revisión de columnas'})).toBeVisible();
    await upload.getByRole('button', {name: 'Importar CSV', exact: true}).click();
    await expect(upload).toHaveCount(0);

    async function createRule(name, kind, field) {
        await page.getByRole('button', {name: '+ Crear regla', exact: true}).click();
        const dialog = page.getByRole('dialog', {name: 'Crear regla'});
        await dialog.getByLabel('Nombre de la regla').fill(name);
        await dialog.getByLabel('Qué evaluar').selectOption(kind);
        await dialog.getByRole('checkbox', {name: field, exact: true}).check();
        await dialog.getByLabel('Umbral mínimo (%)', {exact: true}).fill('100');
        await checkAccessibility(page,'formulario de regla');
        await dialog.getByRole('button', {name: 'Crear regla', exact: true}).click();
        await expect(dialog).toHaveCount(0);
        await expect(page.getByRole('cell').filter({hasText: name})).toBeVisible();
    }
    await createRule('Correo requerido navegador', 'REQUIRED', 'correo');
    await createRule('Identificador único navegador', 'UNIQUE_KEY', 'id');
    await page.reload();
    await expect(page.getByRole('cell').filter({hasText: 'Correo requerido navegador'})).toBeVisible();
    await expect(page.getByRole('cell').filter({hasText: 'Identificador único navegador'})).toBeVisible();
    await checkAccessibility(page,'listado de reglas');
    await page.screenshot({path:'../target/ui-motion-rules.png',fullPage:true});

    async function evaluate(ruleName) {
        await page.getByRole('navigation', {name: 'Navegación principal'}).getByRole('button', {name: 'Evaluaciones', exact: true}).click();
        await page.getByRole('button', {name: 'Nueva evaluación', exact: true}).click();
        const wizard = page.getByRole('dialog', {name: 'Nueva evaluación'});
        await wizard.getByRole('button', {name: 'Continuar a reglas', exact: true}).click();
        await wizard.getByRole('checkbox', {name: new RegExp(ruleName)}).check();
        await wizard.getByRole('button', {name: 'Revisar evaluación', exact: true}).click();
        const accepted = page.waitForResponse(r => r.request().method() === 'POST' && r.url().endsWith('/quality-evaluations'));
        await wizard.getByRole('button', {name: 'Iniciar evaluación', exact: true}).click();
        const response = await accepted;
        expect(response.status()).toBe(202);
        const {data: job} = await response.json();
        await expect.poll(async () => {
            const result = await page.request.get(`/api/v1/projects/${process.env.M2_TEST_PROJECT_ID}/quality-evaluations/${job.id}`);
            expect(result.status()).toBe(200);
            return (await result.json()).data.state;
        }, {timeout: 30000}).toBe('COMPLETED');
        return job.id;
    }
    await evaluate('Correo requerido navegador');
    await evaluate('Identificador único navegador');
    await page.getByRole('navigation', {name: 'Navegación principal'}).getByRole('button', {name: 'Panel de calidad', exact: true}).click();
    for (const name of ['Completitud', 'Unicidad']) {
        const card = page.getByRole('region', {name, exact: true});
        await expect(card).toContainText('66,67');
        await expect.poll(() => card.locator('.gauge-value').evaluate(element => Number.parseFloat(getComputedStyle(element).strokeDasharray))).toBeGreaterThan(.66);
        expect(await card.locator('.gauge-value').evaluate(element => Number.parseFloat(getComputedStyle(element).strokeDasharray))).toBeLessThan(.68);
    }
    await checkAccessibility(page,'panel de calidad');
    await checkHoverContrast(page.getByRole('button',{name:'Ver historial',exact:true}));
    await checkHoverContrast(page.getByRole('button',{name:'Actualizar panel',exact:true}));
    await page.screenshot({path:'../target/ui-motion-dashboard.png',fullPage:true});
    await page.setViewportSize({width:390,height:844});
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390);
    await checkAccessibility(page,'panel móvil');
    await page.screenshot({path:'../target/ui-motion-mobile.png',fullPage:true});
    await page.setViewportSize({width:1440,height:1000});
    await page.emulateMedia({reducedMotion:'reduce'});
    await page.evaluate(() => window.scrollTo(0,document.documentElement.scrollHeight));
    await page.getByRole('navigation', {name:'Navegación principal'}).getByRole('button',{name:'Evaluaciones',exact:true}).click();
    await expect.poll(() => page.evaluate(() => window.scrollY)).toBe(0);
    await expect(page.getByRole('region',{name:'Filtros del historial',exact:true})).toBeVisible();
    await checkAccessibility(page,'historial de evaluaciones');
    await page.screenshot({path:'../target/ui-motion-evaluations.png',fullPage:true});
    await page.getByRole('navigation', {name:'Navegación principal'}).getByRole('button',{name:'Panel de calidad',exact:true}).click();
    await expect(page.locator('.page-content')).toHaveCSS('opacity','1');
    await expect(page.locator('.page-content')).toHaveCSS('transform','none');

    let evidenceRequests = 0;
    page.on('request', request => {if (request.url().endsWith('/evidence')) evidenceRequests++;});
    await page.getByRole('navigation', {name: 'Navegación principal'}).getByRole('button', {name: 'Incidencias', exact: true}).click();
    await page.getByRole('combobox', {name: 'Activo', exact: true}).selectOption({label: 'navegador.csv'});
    await page.getByRole('combobox', {name: 'Dimensión', exact: true}).selectOption('COMPLETENESS');
    await page.getByRole('button', {name: 'Aplicar filtros', exact: true}).click();
    await expect(page.getByRole('cell').filter({hasText: /^correo$/})).toBeVisible();
    await checkAccessibility(page,'listado de incidencias');
    await page.screenshot({path:'../target/ui-motion-incidents.png',fullPage:true});
    expect(evidenceRequests).toBe(0);
    await page.getByRole('button', {name: 'Ver evidencia', exact: true}).click();
    const evidence = page.getByRole('dialog').filter({has: page.getByRole('heading', {name: /Evidencia/})});
    await expect(evidence).toBeVisible();
    await checkAccessibility(page,'evidencia');
    expect(evidenceRequests).toBe(1);
    await page.keyboard.press('Escape');
    await expect(evidence).toHaveCount(0);
    expect(runtimeErrors).toEqual([]);
    expect(await page.evaluate(() => window.m2CspViolations)).toEqual([]);
});
