---
version: 3
name: Calidad M2
updated: 2026-11-09
ui: shadcn-ui-adapted
styling: tailwindcss-v4
motion: motion-react
---

## Propósito

Interfaz empresarial para cargar CSV, configurar reglas, evaluar calidad y localizar incidencias. La solicitud del 2026-11-09 reemplaza Bootstrap por componentes adaptados de shadcn/ui. Se conservan React, TypeScript, los controles nativos, las APIs y las carpetas originales.

## Referencias seleccionadas

shadcn/ui aporta componentes y variantes. Motion se integra realmente como dependencia React para transiciones, entrada escalonada y trazado de indicadores SVG. Kokonut UI (Metric Card y Loader) orienta el uso de tarjetas métricas y feedback animado; no se copia su componente de ingresos, datos de ejemplo ni código. Refero Styles e Impeccable sirven como referencias de composición. Web Interface Guidelines orienta accesibilidad e interacción. La licencia de los componentes adaptados está en Docs/Arquitectura/LICENCIAS_FRONTEND.md.

## Tokens

Frontend/styles.css es la única fuente de tokens. Tailwind los expone mediante @theme inline.

| Token | Valor | Uso |
|---|---|---|
| background | #f5f7f9 | Fondo de trabajo |
| foreground | #192f3c | Texto principal |
| card | #ffffff | Superficies |
| primary | #0b746d | Acciones y enlaces |
| secondary | #eef4f4 | Acciones secundarias |
| muted-foreground | #536875 | Texto secundario legible |
| accent | #e0efeb | Interacción y selección |
| destructive | #b42318 | Errores y acciones destructivas |
| border | #dce5e9 | Separadores y tarjetas |

La tipografía es local: Segoe UI/Tahoma; Consolas para referencias y fórmulas. No se descargan fuentes. Las tarjetas usan radio de 14 px; los campos y botones, 8 px. Texto general de 14 px y campos de al menos 16 px en móvil para evitar zoom al editarlos.

## Composición

Barra lateral azul petróleo de 232 px desde 1024 px, selección animada y contexto de archivos; en pantallas menores pasa a navegación horizontal desplazable. Cabecera blanca con proyecto y sesión. Contenido hasta 1600 px con márgenes de 32/24/16 px. El acceso tiene una composición dividida: presentación del flujo y formulario. En móvil se conserva el formulario, sin decoración lateral.

El dashboard presenta el archivo en una cabecera verde suave, tres contadores reales, las seis dimensiones en primer plano y, al final, la última evaluación y accesos de trabajo. No hay resumen lateral que estreche las mediciones. Las dimensiones se distribuyen en tres columnas desde 1280 px, dos desde 640 px y una antes. Sin reglas aplicadas muestran Sin evaluar; sin denominador válido, No evaluable. Un círculo representa solo una medición real, nunca el promedio de varias reglas. Las cifras se muestran directamente con su valor final, sin números intermedios inventados.

Las tablas tienen encabezado claro, separación por bordes y desplazamiento horizontal propio. No usan bandas alternadas. Los identificadores, columnas y filas de error siguen siendo precisos. Los formularios usan cuadrículas adaptables y acciones consistentes.

## Componentes y propietarios

Frontend/ui.tsx concentra Button con variantes, Input, Select nativo, Textarea, Table, Card, CardHeader, CardContent, CardFooter, FilterPanel, Status, Form, Dialog, Pagination y Loading. Reveal centraliza las entradas animadas; MetricGauge traza el indicador; BrandMark y PanelIcon conservan iconos propios compartidos. cn combina clases mediante clsx/tailwind-merge; cva administra variantes de botones.

Se integró el subconjunto necesario de shadcn manualmente. No se usa un generador que reorganice carpetas ni dependencias de diálogos/menús que dupliquen controles nativos existentes. Tailwind se compila mediante el plugin de Vite; no se carga CSS por CDN.

Motion se carga mediante LazyMotion/domAnimation y componentes m; no se carga soporte de arrastre o layout. Reveal usa entradas de 320 ms y desplazamiento de 12 px; las dimensiones se escalonan 35 ms y el círculo se traza en 800 ms. CSS aporta hover, pulsación, spinner, skeleton y apertura de diálogo. No hay animaciones de salida que mantengan evidencia o formularios antiguos montados. MotionConfig y useReducedMotion respetan la preferencia del usuario; CSS desactiva desplazamientos y reduce animaciones en ese modo.

Los diálogos nativos conservan showModal, Escape, foco restaurado y bloqueo de acciones mientras guardan. La evidencia requiere una petición explícita y desaparece al cerrar. Las fechas y porcentajes conservan es-EC/America-Guayaquil.

## Estados y accesibilidad

COMPLETED describe ejecución y usa azul; PASS describe cumplimiento y usa verde. Error rojo y advertencia ámbar siempre incluyen texto. Los botones tienen estados hover, active, disabled y foco visible. El objetivo para contraste de texto es al menos 4,5:1. No se usa transition:all. Se respetan movimiento reducido y colores forzados.

El asistente sigue Archivo y alcance → Reglas → Revisión. Se mantienen las versiones revisadas, la idempotencia y el rechazo de cambios obsoletos. Las tarjetas compactas muestran medición/estado/fecha y enlazan al detalle completo; no promedian reglas ni certifican todo el activo.

## Verificación

Playwright comprueba persistencia de reglas, evaluaciones separadas por dimensión, proporción del círculo y evidencia explícita. Incluye axe para acceso, regla, historial, panel, incidencias y evidencia; teclado, viewport de 390 px sin desbordamiento de página, contraste de hover y modo de movimiento reducido. Comprueba errores de ejecución y violaciones CSP sin relajar la política. Las capturas ui-motion-*.png se guardan en target/, exclusivamente con datos sintéticos de un entorno aislado.

Fuentes: https://motion.dev/docs/react, https://motion.dev/docs/react-accessibility, https://motion.dev/docs/react-reduce-bundle-size, https://kokonutui.com/docs/cards/metric-card, https://kokonutui.com/docs/inputs/loader y https://ui.shadcn.com/docs/components/skeleton.
