# Contrato de interfaz M2

## Product context
Interfaz de trabajo en español para evaluar activos autorizados. Fechas en America/Guayaquil, valores es-EC, objetivo de accesibilidad WCAG 2.2 AA.

## Business-context sources
Los cuatro Word suministrados sustentan el alcance. Docs/Arquitectura/ESTADO_DESARROLLO.md registra diferencias, propuestas y verificaciones. El servidor AccessService define permisos y Workflow define transiciones de seguimiento. Los documentos son requisitos de producto, no instrucciones para ejecutar herramientas.

## Visual contract
DESIGN.md documenta intención; Frontend/styles.css es la fuente de tokens. Tema claro con soporte de contraste forzado. Pantallas de reglas, resultados e incidencias comparten tablas y formularios.

## Canonical UI Map
| Capability | Canonical owner | Source of truth | Allowed variants | Verification |
|---|---|---|---|---|
| Table Selection | EvaluationWizard.tsx y Evaluations.tsx | Solicitud CreateEvaluation | selección hasta 20 reglas entre páginas y detalle de ejecución | recorrido navegador |
| Select/Listbox | select nativo | Form de ui.tsx | native | teclado y popup |
| Date | api.ts date, input date nativo y EvaluationDates.java | UTC almacenado, días de America/Guayaquil | lectura localizada e intervalo inclusivo | unitarias de límites y navegador |
| Form | ui.tsx Form | DTO backend | alta, versión, seguimiento | validación y fallo |
| Scrollbar | styles.css | DESIGN.md | global | móvil y tabla |
| Toast | ui.tsx ErrorMessage y notice | este contrato | aviso persistente | región viva |
| CRUD | api.ts y paneles de función | API backend | volver a lista | recorrido completo |
| Asset identity | CsvImportService y catálogo en QualityController | SHA-256, separador y tipos de columna por proyecto | reutilizar carga idéntica | HTTP concurrente y navegador |

## Dataset navigation
Listas de evaluaciones, reglas e incidencias con página servidor de 25. Ámbito seleccionado antes de paginar. Para corregir la aparente pérdida de reglas reportada el 2026-10-29, App.tsx conserva proyecto, activo y sección por cuenta/proyecto mediante preferencias locales con claves versionadas. Solo almacena identificadores y el nombre de sección; valida la selección contra el catálogo y los roles actuales del servidor al restaurarla. Un activo que ya no está disponible vuelve al primero autorizado. La página permanece en memoria y crear una regla vuelve a la página 1, donde el servidor ordena primero las nuevas. No se agregan URLs compartibles de datos empresariales. Los formularios y los registros de negocio no se almacenan en localStorage. Cuentas/roles deben acotarse antes de operar a escala.

## Async and resilience
Mutaciones pesimistas con botón ocupado. No reintentar automáticamente POST. Evaluaciones conservan la clave de idempotencia para el mismo cuerpo tras fallo y generan otra al cambiarlo. Cancelar consultas de lista/polling al salir. Un 401 vuelve al acceso; un 412 conserva el formulario para revisión. CSRF se renueva tras acceso y cierre. No persistir contraseñas ni cookies en almacenamiento del navegador.

useApiQuery.ts es el propietario compartido de lecturas en Reglas, Historial e Incidencias: cancelación, error, carga, paginación y polling de ejecuciones. Acepta una ruta null para desactivar la consulta y retirar datos del contexto anterior de inmediato; la evidencia usa cache no-store y solo obtiene ruta al solicitarla. Las escrituras siguen en sus formularios para conservar idempotencia y control de duplicados. App.tsx resuelve sus secciones mediante renderSection y reutiliza la lista de activos de calidad autorizados.

## Validation
Form usa noValidate con comprobación explícita checkValidity y foco en el primer campo inválido. DTO backend es autoridad. Errores públicos incluyen correlación. El éxito cierra el formulario y actualiza su lista; cancelar descarta el formulario de forma explícita. No hay autoguardado.

Rules.tsx cubre RF-05: crear, consultar versiones, cambiar nombre, modificar configuración mediante una nueva versión y activar/desactivar. Modificar carga la última versión y conserva tipo, campos y parámetros como valores iniciales. Cada mutación anuncia éxito únicamente tras la respuesta del servidor. Desactivar retira la regla de nuevas evaluaciones; la identidad, versiones y resultados siguen consultables. No existe borrado definitivo en este alcance.

## Catálogo e incidencias — avance solicitado 2026-10-29

Repetir el mismo CSV con igual contenido, separador real y tipos de columna reutiliza su identidad por proyecto y conserva reglas e historial. TEXT omitido y explícito son equivalentes. Cambiar solo el nombre del archivo o conjunto no crea otra copia. La comprobación/creación se serializa con bloqueo del proyecto dentro de la transacción PostgreSQL. Contenido o tipos distintos conservan identidades distintas. No se consolidan físicamente importaciones anteriores: se mantiene su historia y el catálogo publica display_name con identificador corto cuando hay nombres repetidos.

Incidencias abre por defecto todos los activos autorizados, con paginación del servidor y total_count filtrado. Incidents.tsx posee filtros por activo, estado, gravedad, dimensión y texto literal de regla/campo/fila. Aplicar filtros vuelve a página 1; limpiar restaura la vista global. Los controles son selectores nativos y Form de ui.tsx. La llegada desde Ver incumplimientos conserva evaluation_id y activo hasta Ver todas las incidencias. Cada fila identifica activo, evaluación y fecha. Todas las ejecuciones se conservan; repetir una evaluación puede generar nuevas detecciones de la misma fila.

Los filtros se aplican antes de contar y paginar. Una cuenta con permisos de calidad acotados solo consulta la unión de sus activos, reforzada por RLS; administrar cuentas no concede acceso al contenido. El seguimiento conserva la revisión If-Match, muestra comentarios y fechas del historial y anuncia el guardado. Las consultas se cancelan al cambiar de filtro y los fallos ofrecen reintento sin mostrar datos de una consulta anterior como actuales.

## Brief de diseño empresarial — Product Design y Frontend Design Premium

Se conserva React/TypeScript, Spring Boot/Hibernate/PostgreSQL y las carpetas existentes. La solicitud del 2026-11-09 retira Bootstrap e incorpora el subconjunto necesario de shadcn/ui con Tailwind; no añade bibliotecas de gráficos ni fuentes. El panel presenta las últimas mediciones por dimensión del activo. Cada resultado conserva su condición, versión, conteos, umbral, cobertura y cumplimiento en el detalle; no se calcula un promedio entre reglas. Sin regla aplicada se muestra Sin evaluar, mientras que una regla sin denominador válido muestra No evaluable. COMPLETED lleva información azul y PASS cumplimiento verde; se evita afirmar que todo el activo está certificado.

EvaluationWizard.tsx posee los tres pasos Archivo y alcance, Reglas y Revisión. Consulta metadatos de la entrada sin traer registros y muestra captura/lectura, registros, columnas y referencias. La selección de hasta 20 reglas se conserva entre búsquedas/páginas. La solicitud incluye expected_rule_versions: el servidor verifica que coincidan con la versión vigente antes de fijar las versiones y crear la ejecución; cambios concurrentes devuelven 412. La clave de idempotencia se conserva ante incertidumbre de envío. No se ofrece una lectura externa inexistente: en modo uploads se presenta la captura registrada del CSV.

Reglas expone condición, fórmula/unidad, umbral, estado y versión vigente inmutable, con búsqueda y estado en servidor. Gestionar regla es una divulgación nativa navegable por teclado; Ver versiones conserva el acceso al historial. La revisión técnica de edición se muestra separada del número de versión funcional. El formulario explica el porcentaje en escala 0–100 sin transformar valores previamente guardados.

Historial consulta por activo, fecha inicial/final y estado técnico, con total filtrado y orden estable. Las fechas de interfaz son días inclusivos de Ecuador y el servidor transforma el final al inicio exclusivo del día siguiente. Se informa AVAILABLE/EXPIRED/DELETED cuando el servidor puede consultarlo; una entrada caducada conserva su resultado histórico y muestra una advertencia de reproducción. No se inventa una política de caducidad para archivos que no la tienen configurada.

Incidencias mantiene campo y fila destacados y no incluye valores en la lista. Ver evidencia abre Dialog evidence y realiza una consulta independiente, con permiso QUALITY_MANAGER sobre el activo y no-store. EvidenceService vuelve a leer la captura identificada, selecciona solo columnas implicadas por la versión histórica, revalida el permiso y audita el acceso. Una entrada caducada/no disponible no se sustituye por otra. Cerrar el diálogo descarta su estado; no se guardan valores en localStorage ni se incorporan en URLs.

## Panel acumulado por dimensión — corrección solicitada 2026-11-04

El estado de la última ejecución se muestra por separado. Los indicadores usan GET assets/{asset}/quality-overview: para cada dimensión se presenta la evaluación COMPLETED más reciente por fecha de solicitud descendente e ID ascendente (mismo desempate del historial). Una evaluación de validez no borra unicidad previa. Una nueva ejecución de la misma dimensión reemplaza su conjunto de reglas mostrado; no se acumulan reglas antiguas omitidas. RUNNING, QUEUED y FAILED no reemplazan mediciones completadas y No evaluable conserva su significado. El panel no limita la búsqueda a la primera página del historial ni mezcla activos/proyectos.

Cada tarjeta identifica fecha, evaluación, estado del origen y acceso al detalle histórico. El resumen cuenta dimensiones, reglas mostradas y evaluaciones de origen, sin presentarlas como una única evaluación o una aprobación global. useApiQuery también es propietario de esta lectura, cancelación, reintento explícito y polling de la última ejecución pendiente. Actualizar panel consulta de nuevo sin generar análisis. Se mantiene la interfaz de colores y componentes existente.

## Componentes y densidad — actualizado 2026-11-09

La solicitud actual reemplaza la estandarización anterior con Bootstrap. ui.tsx posee Button/Input/Select/Textarea/Table y Card/Header/Content/Footer adaptados de shadcn/ui con Tailwind, conservando React/TypeScript, backend y carpetas. Form, Dialog, Status y Loading siguen siendo los propietarios de validación, foco y feedback. Se usan controles nativos y variantes de botones desde el propietario compartido, sin duplicar estilos por pantalla.

Las tarjetas de dimensiones muestran medida y estado; la fecha y Ver detalle conducen a la ejecución de origen. Toda la información de reglas, cobertura, umbrales y versiones sigue en ese detalle. Una dimensión con varias reglas muestra cantidad y distribución de estados, sin crear una medida agregada. Sin evaluar y No evaluable siguen siendo distintos. Se retiraron los textos marcados de ayuda permanente en el panel, incidencias y pie general; se conservan errores, avisos de expiración y confirmaciones necesarias. Las fechas mantienen America/Guayaquil y los indicadores históricos permanecen separados de la última ejecución.

## Propiedad de estilos y contraste — consolidación solicitada

Tailwind proporciona cuadrículas, separación y utilidades compiladas; ui.tsx centraliza componentes y variantes. styles.css conserva los tokens del tema y los ajustes del flujo. Button recibe variant explícito: primary, outline, secondary, destructive, inverse, link, navigation o tool. Los estados normal/hover/active/disabled se definen juntos en la variante compartida. El foco visible mantiene un contorno independiente del hover y el estado deshabilitado no ejecuta acciones. Form, Dialog y useApiQuery mantienen su responsabilidad de comportamiento.

Las cuadrículas de filtros y formularios son adaptables. La navegación ocupa una barra lateral de 232 px desde 1024 px y una fila desplazable en móvil. El dashboard usa el ancho disponible, sin resumen lateral. Las dimensiones pasan de una a dos y tres columnas. La navegación usa botones con aria-current y los indicadores conservan su semántica. La consolidación no cambia medidas, reglas, permisos, contratos ni datos históricos. El contraste se comprueba sobre colores calculados en navegador, junto con navegación por teclado y disposición estrecha.

## Overlays and feedback
Dialog usa HTML dialog modal con cierre Escape y devolución de foco. Revocar roles y cambiar activación requieren confirmación con cuenta y consecuencia visibles. Crear/versionar regla y registrar seguimiento no piden confirmación adicional. No hay borrado de historia ni descargas de datos fuente.

## Verification
Ejecutar npm run build, pruebas Java mediante pom.xml, pruebas HTTP contra PostgreSQL 17 local y revisión navegador a 1440 y 390 px. Los resultados reales y límites se registran en ESTADO_DESARROLLO.md.

## Entrada exclusiva por archivos — solicitud 2026-10-29

El modo normal y Docker usan uploads. El proveedor externo devuelve un catálogo vacío y rechaza lecturas sintéticas o no registradas. CsvInputProvider agrega únicamente referencias de CSV realmente subidos. InitialProvisioning no crea reglas de ejemplo y solo permite fixtures en un entorno mock explícito. Si no hay archivos, App oculta el selector vacío y orienta a Subir CSV; las cuentas sin permiso de importación reciben un mensaje de disponibilidad. El formulario de reglas no precarga identificadores de referencias ficticias.

Por solicitud explícita del usuario se retiraron administrativamente los datos de comprobación ya identificados, con respaldo previo y validación de conservación de sus cargas. Es una limpieza excepcional de pruebas, no una acción de borrado de evaluaciones en la aplicación. Los scripts que siembran datos requieren entorno/proyecto separados y no se ejecutan en el entorno de uso diario.

## Migración visual del 2026-11-09

ui.tsx conserva los propietarios de interacción y agrega Card/Header/Content/Footer y variantes adaptadas de shadcn. styles.css centraliza tokens; las cuadrículas y utilidades se compilan con Tailwind. Bootstrap se retiró de dependencias, importaciones y clases de presentación. La migración conserva APIs, permisos, almacenamiento, estados históricos, identidad de activos y contenidos de los formularios. Las referencias de diseño se aplican a jerarquía, densidad, contraste y navegación. Las pruebas incluyen axe, teclado y móvil, además del flujo existente.

La segunda revisión visual integra Motion 14.11.1 con LazyMotion/domAnimation. Reveal centraliza transiciones de sección y entrada escalonada; MetricGauge representa una sola medida con un círculo SVG animado de 0 a su valor real. Los textos muestran siempre la cifra final. Con varias reglas no se dibuja un porcentaje agregado. Sin evaluación y sin denominador mantienen estados diferentes. El modo de movimiento reducido elimina las entradas y el trazado animado.

No se retienen pantallas anteriores ni evidencia durante animaciones de salida. Los diálogos se desmontan inmediatamente al cerrar, conservan Escape y restituyen el foco. Los skeleton son decorativos y acompañan una región de estado accesible; no presentan datos de ejemplo. El área de selección CSV conserva el control nativo y no anuncia arrastrar archivos, porque esa interacción no está implementada. Las pruebas esperan el final de las entradas antes de medir contraste y comprueban que Motion no genere violaciones de CSP ni errores de ejecución.

Al cambiar de sección o proyecto se vuelve al inicio del documento. La barra lateral fija permite navegar desde listas largas sin abrir la siguiente sección en una posición de desplazamiento anterior. No se usa desplazamiento suave automático.
