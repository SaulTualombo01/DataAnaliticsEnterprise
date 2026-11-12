# Revisión crítica de QA, seguridad e interfaz — 2026-11-09

## Dictamen

El módulo tiene una base funcional sólida y controles de seguridad verificables. Está en condiciones de seguir desarrollándose y usarse en un entorno local controlado. Todavía no debe declararse listo para producción empresarial: quedan hallazgos de dependencias, límites de escala y decisiones operativas pendientes. La cantidad de pruebas aprobadas no demuestra ausencia de vulnerabilidades.

Esta revisión combina lectura del código actual, regresión Java/PostgreSQL/HTTP/SMTP/TLS, navegador, accesibilidad automatizada y verificación de fuentes oficiales de los avisos. No se realizó una auditoría externa, pentest de infraestructura ni certificación ASVS.

## Puntos buenos

| Área | Evidencia | Por qué está bien |
|---|---|---|
| Autorización | AccessService, SessionIdentityFilter, políticas RLS y RoleRevocationIntegrationTest. | Se vuelven a comprobar permisos y versión de autenticación; el administrador técnico no obtiene automáticamente acceso al contenido. La matriz usa la cuenta SQL m2_app. |
| Sesiones y recuperación | SecurityConfiguration, IdentityService y PasswordRecoveryIntegrationTest. | Cookies HttpOnly, CSRF, renovación de sesión al autenticar, contraseñas Argon2id, tokens de recuperación con hash, caducidad, bloqueo y uso único. |
| Datos sensibles | SensitiveDataCipher, EvidenceService y CsvFileStore. | AES-GCM con contexto de proyecto/registro; la lista de incidencias no descarga valores y la evidencia se solicita explícitamente. |
| Trazabilidad | RuleService, QualityOverviewService, restricciones y disparadores de PostgreSQL. | Se conservan versiones históricas, conteos, origen y dimensiones evaluadas en ejecuciones distintas. Se separan trabajo completado y cumplimiento. |
| Resiliencia | Idempotencia, revisión If-Match, cuotas, presupuestos y estados de ejecución. | Se controlan duplicados y modificaciones concurrentes; un fallo no publica métricas truncadas como si fueran completas. |
| Recuperación y pruebas | BackupCipherTest, ensayo de restauración y verificar_entrega.ps1. | Se ensayan datos restaurados y permisos; la restauración descarta sesiones anteriores. Los datos ficticios se generan en proyectos aislados. |
| Frontend | React/TypeScript estricto, api.ts, useApiQuery.ts y controles compartidos. | Se centralizan CSRF, formatos, consultas y cancelaciones. La evidencia y las contraseñas no se guardan en localStorage. |

## Hallazgos y pendientes por prioridad

### P1 — Dependencias con avisos pendientes; aplicabilidad todavía sin cerrar

Evidencia: pom.xml; target/security-image.json; documentación oficial de CVE-2026-47884 y CVE-2026-47890. El escáner registra ambos como críticos; Spring clasifica el primero como medio y el segundo como bajo, con condiciones específicas de explotación. El primero requiere XsltView con un mapeo amplio que resuelva nombres de vista implícitos; el segundo requiere fragmentos de vistas enviados mediante SSE y datos controlados por un atacante.

No se observó ese uso en los controladores REST y la consulta periódica del módulo. Esto evita confundir presencia de una dependencia con explotación demostrada, pero no cierra por sí solo la revisión. Mantener el bloqueo hasta documentar formalmente la no aplicabilidad o probar una migración a una rama con corrección pública. No agregar exclusiones generales ni omitir el análisis cuando falle una descarga.

Fuentes: https://spring.io/security/cve-2026-47884/ y https://spring.io/security/cve-2026-47890/.

### P1 — Catálogo truncado silenciosamente a 1.000 activos

Evidencia: CsvInputProvider.java, assets(), llamada repo.list(..., 0, 1000). RuleService.create() usa ese catálogo para localizar el activo. Al superar el límite, activos existentes pueden dejar de ofrecerse en la interfaz y una regla nueva para un activo omitido puede rechazarse como recurso ausente.

Mejora necesaria: paginar el catálogo con total/continuación y resolver un activo por ID sin depender del listado completo. Agregar una prueba que alcance el elemento 1.001. Evitar que una limitación técnica parezca pérdida de datos o de permisos.

### P2 — Los límites por archivo no equivalen a un presupuesto global de memoria

Evidencia: CsvImportController.java, preview()/upload(), file.getBytes(); CsvImportService.parse() y CsvInputProvider.capture(). El procesamiento materializa el contenido y estructuras del CSV en memoria. El límite de archivos y de contenedor es positivo, pero no hay una admisión global que limite cuántos análisis de carga pueden estar activos a la vez. La reserva de almacenamiento se comprueba después del parseo.

Riesgo: varias cargas grandes o JSON de configuración costosos pueden presionar memoria/CPU antes de llegar al límite del almacenamiento. No se reprodujo un agotamiento de memoria: la prueba anterior de cinco usuarios ejecutaba evaluaciones, no cinco cargas máximas simultáneas.

Mejora necesaria: presupuesto de concurrencia para cargas/parseo, límite de cuerpo JSON y pruebas de presión de subida, cancelación y recuperación después de rechazos. Ajustar umbrales con la carga real acordada.

### P2 — Falta comprobar la integridad del inventario de claves en cada respaldo

Evidencia: respaldar-datos.ps1, KeyConfigFile opcional y copia de keys.env. La restauración probada incluye las claves del entorno simple. Si se incorpora un anillo externo y se omite KeyConfigFile, el archivo puede generarse sin las claves antiguas necesarias para leer parte del historial.

Mejora necesaria: registrar identificadores de claves referenciadas, exigir el anillo externo cuando corresponda y ensayar una restauración que incluya datos cifrados antes y después de una rotación. Nunca imprimir los valores de las claves.

### P2 — La política operativa no queda resuelta únicamente por el código

Evidencia: application-production.yml, OperationalMonitor, workflow y SEGURIDAD_Y_OPERACION.md. Hay soporte para HTTPS, secretos y alertas en registros, pero falta evidencia del despliegue real: certificado válido del dominio, entrega Gmail, custodia y retención de respaldos, recepción de alertas y comprobación de reglas obligatorias de la rama remota. El repositorio local no demuestra el estado de GitHub.

Mejora necesaria: prueba operativa con responsables, restauración periódica y medición de tiempo/pérdida de recuperación aceptables. La retención del contenido activo no elimina automáticamente copias de respaldo. Las cuotas de archivos/sesiones son de una instancia; varias réplicas requieren coordinación compartida.

### P2 — Cobertura de QA todavía concentrada en recorridos principales

Evidencia: Docs/Arquitectura y Frontend/quality-flow.spec.mjs. Existen pruebas importantes de API, concurrencia y restauración, pero un solo recorrido de navegador cubre una fracción de la interacción.

Mejora necesaria: cubrir administración de cuentas desde navegador, todos los tipos de regla, modificación histórica, filtros combinados, pérdida de conexión, errores de envío, teclado, móvil y accesibilidad automatizada. Las pruebas visuales deben acompañarse de comprobaciones funcionales y permisos del servidor.

### P3 — Mantenibilidad y documentación visual

Hay funciones compartidas y separación de capas, pero algunos adaptadores, formularios y hojas de estilo concentran muchas responsabilidades o expresiones en una línea. La combinación anterior de Bootstrap y CSS propio duplicaba decisiones de componentes y estados.

Mejora: componentes visuales con variantes explícitas y tokens comunes; formularios con controles nativos; estilos de negocio separados de utilidades. Actualizar DESIGN.md y UX-CONTRACT.md al retirar Bootstrap. Refactorizar por responsabilidad y con pruebas, sin reorganizar carpetas por criterios estéticos.

## Selección de recursos y alcance del cambio visual

Se seleccionan shadcn/ui para componentes reutilizables y sus patrones de variantes, y las recomendaciones de accesibilidad/diseño de Web Design Guidelines como referencia. Tailwind es el soporte de estilos documentado para shadcn con Vite; React y TypeScript se conservan. La integración es manual en los archivos existentes, evitando que un generador cambie las rutas del proyecto.

Se implementaron botones, campos, selectores nativos, tarjetas, etiquetas de estado y tablas reutilizables en ui.tsx. Los diálogos conservan su semántica nativa y gestión de foco. Se retiraron Bootstrap, su importación y sus clases. No se incorporan bibliotecas de animación, modelos 3D, proveedores LLM, plataformas de publicación ni varios sistemas de componentes. No hay una necesidad funcional que justifique esas dependencias en este módulo.

Refero Styles e Impeccable se consultaron como inspiración para jerarquía, densidad y consistencia; no se instalaron ni se copiaron marcas o recursos gráficos. Se conservan la identidad azul corporativa y los iconos existentes. La licencia de los componentes adaptados está en LICENCIAS_FRONTEND.md.

Fuentes: https://ui.shadcn.com/docs/installation/vite, https://ui.shadcn.com/docs/components/native-select y https://github.com/vercel-labs/agent-skills/tree/main/skills/web-design-guidelines.

## Ponytail: evaluación e integración

Se revisó el repositorio original https://github.com/DietrichGebert/ponytail y se instaló exclusivamente la skill skills/ponytail, mediante el instalador oficial de skills de Codex, fijando el commit 96608e078b2d5ea607c5c3b8b5a54d1c1fba6846. Ubicación local: C:/Users/MY LENOVO/.codex/skills/ponytail/SKILL.md. SHA-256: 9BADD22A32E19CB884E46DFA8D3AAE82BB81DFA5484105B8B4F0262DF823A509.

Su utilidad es orientar cambios mínimos pero completos, reutilizar código y evitar dependencias innecesarias. Encaja con las restricciones del proyecto. Es una guía de instrucciones para el asistente: no es un limitador técnico de tokens, no fija un presupuesto de facturación ni garantiza un porcentaje de ahorro. La medición y bandas descritas para Claude Code no se convierten automáticamente en controles de Codex. Se mantienen las pruebas, la seguridad y la accesibilidad aunque impliquen más trabajo.

La instalación queda disponible para Codex a partir del siguiente turno; puede solicitarse «usa Ponytail». No se agregó a package.json, Maven, Docker ni al backend, no se conectaron CSV a proveedores de IA y no se modificó AGENTS.md. Las instrucciones explícitas del usuario y del entorno prevalecen sobre la skill.

## Resultados de verificación

Regresión repetida en este avance: 103 pruebas Java aprobadas con PostgreSQL aislado, sin fallos, errores ni omisiones. Compilación TypeScript estricta y Vite aprobada después de retirar Bootstrap. npm audit de las dependencias finales: cero avisos en todas las severidades. Esto no cubre las dependencias Java.

El recorrido de navegador comprueba subida CSV, persistencia de reglas, evaluación de dos dimensiones, conservación de ambas mediciones, filtrado de incidencias y carga explícita de evidencia. Se añadieron verificaciones axe de WCAG 2 A/AA y 2.1 AA en acceso, formulario de regla, panel, panel móvil y diálogo de evidencia; contraste de texto de al menos 4,5:1 en el hover de Ver historial y Actualizar panel; navegación inicial con Tab y cierre con Escape. En 390 × 844 no hay desbordamiento horizontal del documento. Las tablas conservan su desplazamiento local. Estas comprobaciones no equivalen a certificar accesibilidad completa.

Las capturas de referencia target/ui-shadcn-dashboard.png y target/ui-shadcn-mobile.png contienen exclusivamente el CSV sintético del entorno aislado. No incorporan datos ficticios al catálogo de uso diario. Se mantienen los porcentajes reales, estados no evaluables, identidad de las dimensiones y enlace al origen histórico.

Entrega final aislada aprobada: 17 comprobaciones OpenAPI, 17 del flujo CSV, un recorrido Playwright ampliado (9,8 s), respaldo cifrado, restauración comparada y permisos de evidencia. Cinco usuarios ejecutaron cinco evaluaciones de 10.000 filas y dos reglas: las cinco completaron en 8,398 s totales en este equipo. Es una referencia local y no demuestra capacidad de subidas máximas simultáneas ni un SLA.

Trivy sobre la imagen final m2-delivery-def580d824cf-application: cero avisos altos/críticos npm, cero secretos detectados en fuentes e imagen, y los dos avisos Spring MVC indicados en P1. El control terminó con error y mantiene bloqueada la publicación; no se ocultaron los avisos. Los informes privados están en target/qa-shadcn-security.log y target/security-*.json. Los escáneres no garantizan encontrar todo secreto o vulnerabilidad.

La primera entrega conservó el contenedor diario anterior. La segunda revisión visual, descrita a continuación, ya se aplicó al contenedor local. Para reconstruirlo en siguientes cambios se puede ejecutar .\iniciar.ps1 desde la raíz de la aplicación y recargar http://localhost:8080. El script conserva los volúmenes; no ejecutar down -v. No se modificaron credenciales ni se enviaron correos reales.

## Segunda revisión visual solicitada

Ante la solicitud de un cambio más significativo se integró Motion 14.11.1 y se replanteó la composición: navegación lateral, acceso dividido, cabecera de archivo, contadores reales, indicadores circulares por dimensión, filtros compartidos y selección CSV más visible. Kokonut UI se consultó como referencia de tarjetas métricas y carga; sus datos de ingresos no se incorporaron. DESIGN.md y UX-CONTRACT.md contienen el contrato actualizado.

Las animaciones usan una función compartida Reveal y LazyMotion/domAnimation. Se aplican a cambios de sección y entradas escalonadas; MetricGauge dibuja una medida real. La preferencia de movimiento reducido elimina entradas y trazado. Los diálogos usan apertura CSS y se desmontan inmediatamente al cerrar; la evidencia conserva su acceso explícito. La compilación filtra únicamente el aviso de directivas RSC de Motion, que no aplica al montaje cliente con createRoot; los demás avisos y errores siguen visibles.

Se amplió el recorrido de navegador para verificar listados de reglas, historial e incidencias con axe, el valor final del círculo, movimiento reducido, errores JavaScript y violaciones de CSP. Las pruebas esperan que terminen las entradas antes de medir contraste. Durante la revisión se corrigió la numeración decorativa de las dimensiones que no alcanzaba el contraste mínimo. Se guardan capturas sintéticas ui-motion-*.png en target/. Estas comprobaciones usan Chromium y no sustituyen una revisión manual con lector de pantalla ni pruebas en todos los navegadores.

Resultado final: compilación aprobada sin avisos; npm audit sin avisos; 17 comprobaciones OpenAPI, 17 CSV y recorrido Playwright ampliado (17,2 s) aprobados. Respaldo/restauración y permisos conservados; cinco evaluaciones concurrentes de 10.000 filas aprobadas (7,638 s totales en este equipo). Se aplicó la imagen verificada al contenedor local, se comprobó su estado saludable y el CSS servido, y el contenedor de base mantuvo su identidad. Los hallazgos de backend y operación de este informe siguen pendientes. No se repitió la regresión Java de 103 pruebas en este cambio de presentación; la imagen conserva sus pruebas de compilación y el recorrido de integración descrito.
