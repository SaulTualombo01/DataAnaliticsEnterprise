# Estado del desarrollo de M2

Fecha de comprobación: 29 de octubre de 2026. Entorno local: Windows, Docker Engine 28.5.1, PostgreSQL 17, Java 21 en contenedor, Spring Boot 3.4.3 con las dependencias del pom original, React 19.3.1, Vite 8.3.5 y TypeScript 7.0.2.

Estado vigente: solo archivos CSV subidos por el usuario. Los perfiles normal, local y Docker usan uploads y no precargan ejemplos. La limpieza autorizada y sus verificaciones se describen en el último apartado; las referencias a mocks de apartados anteriores corresponden al desarrollo previo.

## Alcance y decisiones

Se leyeron los cuatro documentos Word suministrados: plan de desarrollo, cuestionario de obtención de información, convenciones de API y requisitos de M2. Se trataron como fuentes de requisitos y propuestas; sus instrucciones administrativas para devolver documentos o contactar al equipo no se ejecutaron.

El usuario confirmó que aún no existen contratos reales de M1/TC13 y autorizó utilizar datos ficticios. Las integraciones se implementaron como un puerto reemplazable y un adaptador sintético de desarrollo. El esquema remoto de Supabase solo se consultó para contrastar tipos, restricciones y políticas; no se aplicaron cambios ni datos de prueba sobre él.

La estructura original se conserva. Las incorporaciones se colocaron en las carpetas existentes. Los archivos originales de cálculo, cifrado, errores, configuración y el pom no se sustituyeron. Se añadió un punto de entrada con el nombre que ya esperaba el pom y un descriptor `pom-completo.xml` para ejecutar pruebas y empaquetar React sin editar `pom.xml`.

El plan menciona una versión tecnológica posterior, pero el proyecto existente usa Spring Boot 3.4.3. Se mantuvo esa línea para respetar la base del usuario; no se realizó una migración de versión del backend.

## Funcionalidad implementada

| Requisitos | Implementación |
|---|---|
| RF-01 y RF-02 | Selección de activo autorizado, contexto de proyecto y puerto de catálogo/entrada; adaptador ficticio con referencias de lectura y captura. |
| RF-03 y RF-04 | Entrada vacía sin porcentaje artificial, validación de metadatos/estructura y rechazo de entrada parcial o modificada. |
| RF-05 | Crear, consultar versiones, modificar configuración, cambiar nombre y activar/desactivar reglas; versiones inmutables, ETag/If-Match y validación de configuraciones permitidas. |
| RF-06 a RF-09 | Motor de completitud, unicidad, validez, consistencia, exactitud por referencia versionada y actualidad por instante/vigencia. No interpreta SQL ni scripts. |
| RF-10 y RF-11 | Conteos, medida, cobertura y cumplimiento separados; publicación y consulta mediante proyecciones autorizadas. |
| RF-12 | Incidencias con fila/campo, seguimiento con revisión y comentarios cifrados. |
| RF-13 y RF-14 | Historial paginado y estados QUEUED, RUNNING, COMPLETED y FAILED; cola persistida, identidad técnica y detección de arrendamientos vencidos. |
| RF-15 | API local y mocks para integrar con los componentes cuando estén disponibles. |
| RF-16 y RF-17 | Configuración por activo, fórmula m2-1, umbral/cobertura por versión y tratamiento conservador de datos no evaluables. |
| RF-18 | Entrada identificada con huella, versiones fijadas y motor determinista para el mismo instante de medición. Las evaluaciones sucesivas reutilizan la referencia inmutable de captura. |

La identidad usa sesión JDBC, CSRF, Argon2id, cuenta activa, expiración absoluta y validación de versión de autenticación. Los roles se comprueban por proyecto/activo en servidor y PostgreSQL aplica RLS. Administrar cuentas no concede acceso a resultados empresariales. La cuenta técnica del ejecutor tiene una contraseña aleatoria que no se comparte.

La interfaz incluye acceso, recuperación, cuentas/roles, reglas/versiones, solicitud y estado de evaluaciones, resultados, incidencias e historial. Las mutaciones llevan CSRF y los cambios concurrentes usan revisión. No se guardan contraseñas ni cookies en localStorage.

## Docker Compose

`compose.yaml` levanta la base y el monolito. El frontend se compila dentro del Dockerfile y se incluye en el mismo JAR; no requiere un servidor web independiente. La base no publica puerto y la aplicación solo publica localhost. El rol del backend no es superusuario.

El primer arranque crea el proyecto, las cuentas de calidad/administración, la identidad técnica y cuatro reglas de ejemplo. El aprovisionamiento se omite al reiniciar un proyecto existente. `preparar-docker.ps1` conserva las claves y contraseñas de `target/docker.env`; `iniciar.ps1` construye, levanta y espera los controles de salud.

El SQL de `Docker/schema-local.sql` es para una base local nueva. No debe aplicarse de nuevo al proyecto remoto ya existente. El archivo de pruebas `esquema_pruebas_local.sql` sirve para el contenedor de comprobación separado y contiene credenciales ficticias explícitas; Compose genera y usa otras credenciales.

## Evidencia de verificación

| Comprobación | Resultado |
|---|---|
| Construcción React/TypeScript en Docker | Aprobada. |
| Maven verify con Java 21 dentro de Docker | 22 pruebas aprobadas, cero fallos. |
| Validación Hibernate contra PostgreSQL 17 real | Arranque aprobado con ddl-auto=validate. |
| Compose | database y application saludables. |
| Reinicio de application | Aprobado; conserva cuentas, reglas y evaluaciones. |
| API HTTP mediante verificar_api.py | 31 comprobaciones aprobadas. |
| Navegador Edge con Playwright | Acceso, lectura de indicadores, creación de regla, diálogo de seguimiento, cierre por Escape y error de credenciales verificados. |
| Vista estrecha | 390 × 844; sin desbordamiento horizontal del documento, con tablas desplazables. |
| Escritorio | 1440 × 1000; indicadores 95/100 y 98/100 visibles. |
| Errores JavaScript | Ninguno en el recorrido verificado. |
| Auditoría estática de interfaz | Cero errores o advertencias. No sustituye las pruebas de navegador. |
| Contratos JSON | Los cinco archivos se interpretan correctamente como JSON. No se afirma validación semántica completa de OpenAPI. |

Las pruebas HTTP cubren sesión/CSRF, correlación de errores, aislamiento de proyecto, administrador sin permiso implícito de datos, idempotencia, repetición de captura, métricas persistidas, incidencias y comentarios cifrados, entrada vacía/parcial, revisiones y versiones históricas. Las pruebas Java también cubren redondeo, referencia de exactitud, claves incompletas y datos temporales inválidos.

El Browser plugin no estaba disponible; la revisión visual se realizó con Playwright y Edge. Las capturas de comprobación se guardaron fuera del repositorio. La referencia visual se adaptó al rol activo: la cuenta de calidad no muestra administración de cuentas; la tabla muestra solamente evaluaciones que existen para el activo seleccionado.

## Recuperación de Docker Desktop en este equipo

Docker Desktop falló antes de iniciar el motor con un error sobre `dockerInference`. Model Runner ya estaba desactivado. Se confirmó que las dos carpetas afectadas contenían únicamente tres sockets de cero bytes y que el backend de Docker estaba detenido.

Se renombraron como respaldo, sin eliminar su contenido:

- `C:\Users\MY LENOVO\AppData\Local\Docker\run` → `run.respaldo-m2-20261029-133149`.
- `C:\Users\MY LENOVO\AppData\Local\docker-secrets-engine` → `docker-secrets-engine.respaldo-m2-20261029-133149`.

Docker regeneró sus carpetas de ejecución y volvió a responder. Luego se construyeron y ejecutaron los servicios de este proyecto. No se ejecutó Factory Reset, prune, eliminación de volúmenes ni unregister de WSL. Es una recuperación observada en este equipo, no una garantía de que el problema de Docker no reaparezca. Referencia del fallo reportado: https://github.com/docker/desktop-feedback/issues/625.

## Dependencias pendientes para un entorno real

- Contratos y adaptadores reales de M1, TC13, M3 y Gateway/M4; conciliación de identidad y permisos con el equipo.
- Proveedor de correo para RecoveryDelivery. El token de recuperación se genera con hash y vencimiento; sin proveedor la API responde 503 y no simula entrega.
- Credencial técnica y TLS verificado del entorno real, certificado HTTPS, controles del servidor y política de respaldos/restauración.
- Línea base de migraciones y conciliación del esquema antes de adoptar Flyway; no se activó ddl-auto=update.
- Aprobación de referencias, reglas, vigencia, umbrales, retención y capacidad con datos empresariales. No se eliminaron datos mediante una retención propuesta.
- Para reproducir exactamente una regla temporal en una fecha posterior se debe conservar y reutilizar el instante de medición del cálculo original. Una evaluación nueva usa un instante nuevo.
- Ampliar pruebas de integración para revocación durante lectura, recuperación tras caída, concurrencia y carga sostenida. La cola conserva arrendamiento y autorización, pero esos escenarios no se declaran comprobados por la prueba de recorrido básico.
- Acotar/paginar la administración de cuentas y roles antes de utilizarla a gran escala; la primera interfaz está orientada al proyecto de prueba.

Esta entrega permite ejecutar y demostrar localmente el módulo con datos ficticios. No se presenta como validación de producción o integración real ya completada.

## Avance de carga y análisis CSV

Se añadió la carga solicitada por el usuario sin cambiar la organización de carpetas ni el esquema remoto. El adaptador CsvInputProvider utiliza el puerto InputProvider existente y conserva el proveedor ficticio para las pruebas previas.

La vista previa y la importación requieren QUALITY_MANAGER de todo el proyecto, sesión y CSRF. El parser admite UTF-8/BOM, coma, punto y coma, tabulación, comillas escapadas y celdas multilínea. Conserva vacíos, duplicados y espacios, y rechaza filas con diferente estructura, encabezados repetidos y límites excedidos. Las columnas permanecen como texto salvo selección explícita NUMBER. El archivo original se conserva cifrado, con huella SHA-256 y referencias inmutables en input_refs.

La interfaz permite revisar columnas, crear reglas por campo, evaluar la captura y abrir las incidencias de esa ejecución. Cada incidencia incluye la regla y dimensión, el campo y la línea física del CSV. Los encabezados con espacios o tildes se usan sin renombrarlos. Se añadió el volumen persistente m2_inputs y se conservaron los perfiles y las credenciales existentes.

Verificación de este avance: 39 pruebas Java aprobadas, incluidas 17 de CSV y cifrado del archivo; 17 comprobaciones HTTP del recorrido CSV aprobadas. Se comprobó el análisis tras reiniciar la aplicación y el recorrido de navegador con el CSV ficticio de 100 clientes, incluyendo vista previa, tipos, reglas, 95 % de completitud, 98 % de unicidad e incidencias por fila. El flujo previo conserva su prueba verificar_api.py. Las instrucciones de uso y los ejemplos CSV están en README.md y Docs/Contratos.

No se incorporan conectores directos a motores de base de datos: se puede subir una tabla exportada a CSV. La responsabilidad de conectores y acceso común permanece en M1/TC13. No se asigna una política automática de retención a los archivos subidos mientras esa decisión siga pendiente.

## Corrección de reglas aparentemente desaparecidas — 2026-10-29

Se confirmó que el cliente volvía al primer activo y a Evaluaciones al recargar. Como las reglas pertenecen a cada activo, esto ocultaba las del CSV seleccionado aunque permanecieran en PostgreSQL. Además, crear desde una página posterior de la lista no volvía a la primera, donde se ordenan las reglas nuevas.

App.tsx recuerda proyecto, activo y sección por usuario/proyecto con claves locales versionadas, valida la selección contra los roles y el catálogo del servidor, y tolera preferencias inválidas o almacenamiento bloqueado. Solo guarda identificadores de navegación: reglas, formularios y secretos no se copian al navegador. La respuesta tardía del catálogo conserva una sección elegida mientras carga. Las consultas anteriores se cancelan al cambiar de contexto.

Rules.tsx confirma el éxito tras la respuesta del servidor y vuelve a la página 1 al crear. Modificar regla carga la última configuración, con tipo, campos, rango, umbral y criticidad, y guarda una nueva versión. Cambiar nombre utiliza PATCH con If-Match. Activar/desactivar conserva el historial y las reglas inactivas no se ofrecen al crear otra evaluación. Se reutilizaron Form, Dialog, ErrorMessage y las tablas existentes; no se cambió la estructura de carpetas ni se añadió borrado definitivo.

Verificación realizada con un CSV ficticio separado:

- Construcción React/TypeScript y Maven verify en Docker: aprobadas; 39 pruebas Java sin fallos.
- Navegador Edge con Playwright, ya que Browser plugin no estaba disponible: creación, recarga, reapertura del contexto de navegador, cierre/inicio de sesión y consulta después de reiniciar el contenedor aprobados.
- Cambio de nombre, precarga de configuración, nueva versión y conservación de la versión previa aprobados. Conflicto real If-Match rechazado sin crear otra versión ni perder los valores del formulario.
- Desactivación/reactivación persistidas; solo las reglas activas se ofrecen para nuevas evaluaciones.
- Creación desde la segunda página de una lista con 26 reglas vuelve a la primera y muestra la nueva regla. Un fallo de red conserva el formulario y permite reintentar.
- Respuesta lenta del catálogo, preferencias inválidas, activo/proyecto inexistente, cambio entre cuenta de calidad y administrador y almacenamiento local bloqueado comprobados.
- Escritorio 1440 × 1000 y móvil 390 × 844, sin errores JavaScript en el recorrido y sin desbordamiento horizontal del documento. Las tablas conservan desplazamiento propio.
- Auditoría estática de interfaz estricta: cero errores y advertencias. Capturas y scripts temporales de navegador guardados fuera del repositorio.

La reparación cubre la administración de reglas RF-05. Los contratos reales de M1/TC13 y las demás dependencias del entorno real descritas arriba siguen pendientes.

## Catálogo sin nuevas copias e incidencias globales — 2026-10-29

Se continuó el flujo de entrada, evaluación, consulta histórica y seguimiento (RF-01/RF-02, RF-12/RF-13), atendiendo la solicitud del usuario sobre activos con el mismo nombre e incidencias filtrables. No se cambió la organización de carpetas ni el esquema de tablas.

CsvImportService comprueba SHA-256, separador real y tipos de columna dentro del proyecto. TEXT explícito y omitido son equivalentes. Repetir la carga reutiliza identidad, metadatos, captura y reglas, aunque cambie el nombre; responde 200 con import_reused=true. Una entrada nueva responde 201. Un bloqueo PostgreSQL del proyecto serializa cargas concurrentes. Las capturas vencidas no se reutilizan. Contenido o tipos distintos conservan identidades independientes.

No se borraron ni fusionaron importaciones anteriores, que pueden tener reglas e historia diferentes. El catálogo devuelve display_name con un identificador corto cuando el nombre se repite. La interfaz refresca el catálogo y selecciona el activo original después de reutilizarlo, con una explicación visible. Las entradas ficticias de comprobaciones anteriores siguen identificadas por sus nombres de prueba.

Incidencias abre todos los activos autorizados del proyecto y permite aplicar activo, estado, gravedad, dimensión y texto literal de regla/campo/fila/código. Consulta y conteo comparten filtros antes de paginar; total_count refleja el total filtrado. Se conservó el enlace por evaluación desde resultados y se añadió Ver todas las incidencias. La tabla indica activo, evaluación, fecha y gravedad, además de regla/campo/fila/estado. El seguimiento muestra comentarios y fechas y confirma el guardado. Los filtros tienen la variante filters de Form, sin barra adhesiva que tape campos en móvil.

La vista global valida cuenta/proyecto activos y admite la unión de permisos de calidad acotados a varios activos. La consulta aplica esa lista explícitamente además de PostgreSQL RLS. El administrador de cuentas sigue sin acceso implícito a datos. No se relajaron los permisos para mostrar un listado global.

Verificaciones de este avance:

- React/TypeScript y Docker construidos correctamente; 39 pruebas Java aprobadas.
- verificar_catalogo_incidencias.py: 20 comprobaciones HTTP aprobadas, incluidas carga repetida, dos cargas simultáneas, tipos diferentes, etiquetas únicas, total/paginación sobre 121 detecciones, filtros combinados e inválidos, búsqueda literal y permisos de un usuario con uno y dos activos. La cuenta temporal se desactivó al terminar.
- Regresión anterior: verificar_api.py con 31 y verificar_csv.py con 17 comprobaciones aprobadas, total de 68 HTTP junto con las nuevas.
- Edge/Playwright: ocho recorridos comprobados con datos ficticios existentes, sin crear otra copia durante las pruebas de interfaz. Vista global, paginación, filtros, seguimiento, foco al cerrar diálogo, consulta sin resultados, fallo de red/reintento, enlace por evaluación, carga repetida y selección tras recarga aprobados.
- Escritorio 1440 × 1000 y móvil 390 × 844 revisados; sin errores JavaScript en el recorrido ni desbordamiento horizontal del documento. La tabla mantiene desplazamiento propio.
- Auditoría estricta de interfaz: cero errores y advertencias. OpenAPI actualizado y JSON interpretado correctamente; no se afirma validación semántica completa del contrato.
- application y database saludables; se conservaron los volúmenes y las claves existentes.

Los scripts/capturas temporales de navegador y la auditoría se guardaron fuera del repositorio. Las limitaciones de integración real y las decisiones de retención continúan descritas en Dependencias pendientes para un entorno real.

## Estado actual: solo archivos subidos y limpieza de pruebas — 2026-10-29

Por instrucción explícita del usuario se dejó de usar el catálogo sintético y se retiraron los datos de comprobación de la base local de uso diario. Los apartados anteriores conservan la evidencia cronológica del desarrollo; la configuración activa actual es uploads en application.yml, application-local.yml y application-docker.yml.

El proveedor externo en modo uploads devuelve una lista vacía y rechaza adquirir/leer activos sintéticos o desconocidos con 404. CsvInputProvider aporta únicamente los archivos importados. Se deshabilitaron fixture-rules; InitialProvisioning impide precargar reglas ficticias fuera de un modo mock explícito. Se conservaron cuentas, permisos y la identidad técnica para el funcionamiento normal. El nombre del proyecto local se actualizó a Proyecto de calidad.

La limpieza fue una operación administrativa única autorizada, no un endpoint de borrado de evaluaciones. Se detuvo la aplicación tras confirmar que no había trabajos en ejecución. Se respaldaron PostgreSQL en formato custom legible por pg_restore, los CSV cifrados y la configuración privada fuera del repositorio. Se inventariaron identificadores concretos: 14 activos persistidos de prueba, 57 reglas, 61 versiones, 27 evaluaciones, 226 incidencias y 6 eventos de seguimiento se retiraron con sus dependencias en una transacción. Las protecciones immutable_history afectadas se reactivaron dentro de la misma transacción; se confirmó que ninguna quedó deshabilitada. La auditoría histórica y los privilegios del backend se conservaron.

Se preservaron los dos archivos subidos por el usuario: 6d7221c2-154c-4278-80dd-280c67d456c1 y 99220420-44fb-42c0-871d-82a48a94c39f. Sus dos reglas, tres versiones, tres evaluaciones y doce incidencias mantienen exactamente las huellas previas de las nueve tablas relacionadas. Se retiraron los 22 archivos cifrados correspondientes a once cargas automáticas; el almacenamiento activo solo conserva cuatro archivos cifrados de las dos cargas del usuario. Los dos CSV de ejemplo de Docs/Contratos se retiraron tras comprobar sus copias de respaldo.

App muestra instrucciones para Subir CSV cuando no hay archivos y oculta el selector vacío. El formulario de reglas ya no propone una referencia ficticia precargada. Los scripts HTTP que siembran casos de prueba exigen M2_TEST_ISOLATED=1, configuración y proyecto separados y bloquean la ejecución sobre la base de uso diario. verificar_solo_archivos.py comprueba el entorno activo sin importar datos ni crear evaluaciones.

Verificación: construcción React/TypeScript y 43 pruebas Java aprobadas, incluidas cuatro del modo uploads y sus perfiles; ocho comprobaciones HTTP sin creación de datos; seis recorridos Edge/Playwright sobre catálogo, reglas/historial conservados, selección antigua eliminada, incidencias, estado vacío simulado y ausencia de excepciones JavaScript. Escritorio 1440 × 1000 y móvil 390 × 844 revisados. Auditoría estática estricta sin errores ni advertencias. application y database quedaron saludables. Se comprobó el bloqueo de un script generador antes de acceder a la API; no se generaron casos ficticios en la base limpia.

Respaldo y manifiesto de esta operación: C:/Users/MY LENOVO/.codex/visualizations/2026/10/29/01a185be-519b-73d1-a5f1-7d2f67716194/respaldo-limpieza-1793316078497. No se modificó el esquema remoto ni se incorporó una política automática de eliminación para datos futuros.

## Panel y flujo empresarial de evaluación — 2026-10-30

Se implementó el brief solicitado con Product Design y Frontend Design Premium sobre React/TypeScript y CSS propios, Spring Boot/Hibernate y PostgreSQL. No se añadieron dependencias, frameworks visuales, fuentes externas ni carpetas. DESIGN.md, UX-CONTRACT.md y premium-ui.json documentan los componentes compartidos y la propiedad nativa de selectores y fechas.

El Panel de calidad muestra la última ejecución del archivo y las seis dimensiones. Cada regla conserva medida, versión, conteos, cobertura y umbral; no se promedian reglas diferentes. Las dimensiones no incluidas muestran Sin evaluar; denominador cero muestra No evaluable. COMPLETED se distingue de PASS y la calificación se limita a las reglas seleccionadas.

EvaluationWizard recorre archivo/alcance, reglas activas y revisión. Consulta metadatos del origen sin filas, conserva selección entre páginas/búsquedas y fija las versiones revisadas mediante expected_rule_versions. El backend comprueba esas versiones antes de crear la ejecución y rechaza cambios con 412. Se mantiene la compatibilidad de solicitudes antiguas y su huella idempotente. Las tablas de reglas muestran condición, fórmula, umbral y versión inmutable, además del acceso al historial y a las acciones existentes.

El historial aplica activo, días inclusivos de Ecuador y estado técnico antes de contar/paginar, reforzando los permisos de activos y RLS. Muestra disponibilidad y caducidad del origen. La evidencia se consulta únicamente desde Ver evidencia; EvidenceService comprueba autorización, lee la captura histórica, localiza la línea física y devuelve solo los campos implicados. Responde no-store, audita la consulta y el cliente retira los valores al cerrar. La administración de cuentas no concede acceso adicional. No se modificó el esquema de datos ni se agregó borrado de mediciones históricas.

Verificación de la implementación:

- React/TypeScript y construcción Docker aprobados; 52 pruebas Java sin fallos, incluidas fechas, evidencia/permisos y huella de versiones revisadas.
- verificar_diseno_ui.py: 19 comprobaciones HTTP aprobadas sobre el servicio local durante el desarrollo. El script no importa archivos ni crea análisis: su solicitud de versión incorrecta se rechaza antes de crear el trabajo. La consulta explícita de evidencia sí registra auditoría.
- Navegador integrado: panel, reglas/versiones, menú por teclado, historial con intervalo vacío y limpieza, detalle y enlace a incumplimientos; evidencia solicitada y retirada al cerrar con Escape y restitución del foco; asistente hasta revisión, sin iniciar una evaluación. Solo se usaron los archivos del usuario.
- Escritorio 1440 × 1000 y móvil 390 × 844 revisados. Panel, reglas, historial e incidencias sin desbordamiento horizontal del documento; las tablas conservan desplazamiento propio. Selector de fecha nativo y pasos del asistente con foco comprobados. Consola sin errores ni advertencias durante el recorrido.
- Auditoría estática estricta de UI: cero errores y advertencias. Contratos OpenAPI/JSON actualizados e interpretados correctamente; no se afirma validación semántica completa de OpenAPI.

Los estados de origen caducado y de ejecución pendiente/fallida se implementaron y se cubrieron donde corresponde con pruebas unitarias; no se alteraron las capturas del usuario ni se generaron ejecuciones artificiales para mostrarlos en navegador. La integración externa real y las decisiones de retención siguen pendientes. README.md incluye el arranque completo desde la terminal de IntelliJ y distingue esta modalidad del botón Run de una JVM local.

Cierre de comprobaciones del 2026-10-30: tras encontrar Docker detenido, se abrió Docker Desktop y se levantó la imagen existente con Compose, sin reconstruir ni reinicializar los volúmenes. Ambos servicios quedaron saludables y se repitieron las 19 comprobaciones HTTP con éxito. Las huellas de las nueve tablas de negocio coinciden con las preservadas tras la limpieza: dos activos, dos entradas, dos reglas, tres versiones, tres evaluaciones, tres vínculos/reglas, tres resultados, doce incidencias y cero eventos de seguimiento. La auditoría de acceso a evidencia queda fuera de esa comparación porque cada consulta efectiva debe añadir su registro.

## Cambio visual según captura WePanel — 2026-10-30

El usuario solicitó explícitamente cambiar solo los elementos visuales para aproximarse a la captura suministrada. Se sustituyó la navegación lateral por una barra superior azul oscuro y navegación horizontal con iconos. El panel agrupa accesos reales a Evaluaciones, Reglas e Incidencias en tarjetas blancas, presenta cabeceras azules y añade una columna de resumen con datos ya disponibles del archivo y de su última evaluación. La barra de dimensiones incluidas expresa alcance (n de seis), no una aprobación global. No se incorporaron funciones de hosting, recursos CPU/RAM ficticios, marca de terceros, flechas/anotaciones ni chatbot de la captura.

El estilo compartido se aplicó también a tablas, filtros, diálogos, botones y acceso. PanelIcon en ui.tsx usa SVG nativo sin biblioteca adicional. Los cambios se limitan a App.tsx, QualityOverview.tsx, ui.tsx, styles.css y documentación; no cambian contratos, permisos, persistencia, reglas ni motor. No se añadieron carpetas ni dependencias al stack. DESIGN.md registra la dirección visual aprobada y sus tokens. Se corrigió una declaración CSS inválida que contenía texto de un comando de terminal.

Comprobaciones: typecheck y build del frontend aprobados; construcción Docker con 52 pruebas Java aprobadas. Auditoría estática estricta sin hallazgos. Lint oficial de DESIGN.md sin errores (advertencias de tokens descritos en prosa, por ser CSS el propietario de ejecución). Navegador integrado en escritorio de 1440 × 1000 y móvil de 390 × 844: tarjetas enlazadas con las secciones existentes, tabla de reglas, formulario de regla, historial, asistente y cierre con Escape sin enviar datos, incidencias globales y consulta sin resultados. Selectores nativos abiertos y desplazamiento horizontal del documento comprobados. No se crearon archivos, reglas, evaluaciones ni incidencias para esta revisión visual.

## Refactorización y Swagger — 2026-11-04

Se atendió la revisión de code smells mediante inspección manual de responsabilidades, duplicación y código sin uso. ApiContext centraliza actor, correlación y respuesta; PageQuery comparte validación y cálculo de paginación; CanonicalUuid unifica el formato de identificadores. AccessService.scope concentra el alcance autorizado sin conceder permisos nuevos. IncidentService extrae del controlador la transacción de seguimiento, manteniendo bloqueo, revisión, cifrado, transición y auditoría. ApiProblem comparte el contrato de errores de seguridad y controladores.

En React, useApiQuery concentra las lecturas, cancelaciones, errores y temporizadores de reglas, evaluaciones e incidencias; queryPath comparte la construcción de filtros. La evidencia sigue requiriendo una acción explícita, usa no-store y se retira al cerrar. La navegación usa funciones y selección de sección explícitas en lugar de ternarios anidados. Se activaron comprobaciones TypeScript de variables y parámetros sin uso y advertencias Java de conversiones no comprobadas.

Se retiraron pom-completo.xml, la clase de arranque redundante del directorio Calidad, los selectores de la barra lateral antigua y 21 declaraciones CSS exactamente repetidas. pom.xml es el descriptor único para desarrollo y Docker. No se movieron ni crearon carpetas; se conservaron el stack, las pruebas útiles, los archivos subidos y el historial de negocio. Los detalles están en REFACTORIZACION_Y_SWAGGER.md.

Swagger UI está disponible en /swagger-ui/index.html y OpenAPI generado en /v3/api-docs. Incluye operaciones, esquemas con los nombres JSON reales, sesión por cookie, CSRF, idempotencia, revisiones y errores. Se usa springdoc 2.8.14 por compatibilidad comprobada con Spring Boot 3.4.3. La documentación está habilitada en local/docker y deshabilitada por defecto en el perfil normal; README.md explica el uso con la sesión existente y el token CSRF. La excepción de estilos de Swagger queda limitada a sus recursos; la API conserva su CSP estricta y sus permisos.

Verificación final:

- Compilación estricta de TypeScript, construcción del frontend y Maven/Docker verify aprobados. 64 pruebas Java, sin fallos ni errores; 12 nuevas cubren paginación, UUID, errores, alcance autorizado y seguimiento transaccional con rechazos sin escritura. Las pruebas unitarias no usan la base del usuario.
- verificar_swagger.py: 16 comprobaciones HTTP aprobadas. verificar_diseno_ui.py: 19 comprobaciones aprobadas sobre la imagen final. Se verificaron referencias de esquemas, snake_case, cabeceras, permisos, filtros y rechazo de versiones obsoletas antes de crear evaluaciones.
- Navegador integrado: acceso, búsqueda sin resultados y limpieza de reglas, cambio de activo, historial y detalle histórico, incidencias globales y evidencia explícita. Escape cierra la evidencia y elimina los valores del documento. No se crearon archivos, reglas, evaluaciones ni eventos de seguimiento durante este recorrido; la consulta de evidencia registra la auditoría prevista.
- Swagger renderiza sus cinco grupos de operaciones; Try it out sobre GET /api/v1/health devolvió 200 con status UP. Consola sin errores ni advertencias en la revisión. Captura swagger-refactor.png fuera del repositorio, en la carpeta de visualizaciones de esta conversación.
- Auditoría estática estricta de interfaz: cero errores y advertencias. Compose reconstruido y servicios saludables tras la incorporación de Swagger. Se preservaron volúmenes y configuración privada.

Esta revisión no equivale a un análisis de SonarQube ni demuestra ausencia total de code smells. Las conexiones reales M1/TC13 y las decisiones de retención siguen pendientes; no se cambian por esta refactorización.

## Conservación de mediciones por dimensión y exclusiones Git — 2026-11-04

El panel anterior solicitaba una sola evaluación del historial, por lo que evaluar validez después de unicidad ocultaba visualmente la medición previa. La nueva consulta GET /api/v1/projects/{project}/assets/{asset}/quality-overview mantiene separada la última ejecución y reúne la evaluación COMPLETED más reciente de cada dimensión del mismo activo/proyecto. La selección se resuelve en PostgreSQL con DISTINCT ON y no depende del número de páginas del historial. Fecha de solicitud descendente e ID ascendente reproducen el orden estable del historial. Se conservan RLS y READ_RESULTS, sin permiso implícito para administración técnica.

QualityOverviewService concentra la consulta autorizada y sus fuentes; QualityController proyecta únicamente los campos públicos existentes. Una nueva evaluación de una dimensión reemplaza las reglas mostradas de esa dimensión; no rescata reglas omitidas de ejecuciones anteriores. Los resultados pendientes/fallidos no desplazan las mediciones completadas. No se promedian porcentajes ni se altera el significado de No evaluable. No se modifican tablas, evaluaciones, reglas, archivos, incidentes ni eventos históricos.

QualityOverview reutiliza useApiQuery para cancelación, error, reintento y polling de la última ejecución pendiente. Cada dimensión identifica su fecha, origen, disponibilidad y enlace al detalle histórico. El resumen lateral cuenta dimensiones, reglas mostradas y evaluaciones de origen, separadas del estado de la última ejecución. Actualizar panel solo vuelve a consultar. Se mantienen estilo, stack y carpetas. README, DESIGN, UX-CONTRACT y el contrato OpenAPI de referencia documentan el comportamiento solicitado.

Se amplió .gitignore para excluir compilados Maven/React, node_modules, configuración privada, claves, archivos locales de data/uploads/csv-inputs, respaldos, cachés Python, informes generados, registros, archivos temporales y preferencias de editores. No se excluyen globalmente CSV o SQL: contratos, esquemas y posibles fixtures válidos siguen siendo versionables. Se conserva package-lock.json. git check-ignore confirmó ocho rutas representativas, incluido target/docker.env; git ls-files --cached --ignored --exclude-standard no encontró archivos registrados que coincidan con las exclusiones. No se cambió la configuración global de Git ni el índice preparado por el usuario.

Verificación: TypeScript y build aprobados; Maven test y Docker verify con 69 pruebas Java sin fallos, incluidas cinco del panel. verificar_panel.py aprobó 36 comprobaciones HTTP de solo lectura sobre los dos activos del usuario: comparación con todo el historial completado, origen por dimensión, versiones exactas, aislamiento entre activos, rechazo anónimo/administrador y conservación de análisis. Un activo tiene unicidad y validez de ejecuciones distintas; ahora muestra ambas y 2/6 dimensiones. verificar_swagger.py aprobó 17 comprobaciones, incluida la ruta nueva. Auditoría estática estricta sin errores ni advertencias.

Navegador integrado: comprobadas las dos dimensiones simultáneas, el detalle de unicidad mediante teclado, cambio de activo sin mediciones ajenas, actualización manual y recarga conservando el resumen. La revisión a 390 px no detectó desbordamiento horizontal del documento y mantuvo las dos tarjetas medidas; se restauró el tamaño al terminar. Consola sin errores ni advertencias. Los estados vacíos y de última ejecución pendiente/fallida se verificaron con pruebas unitarias sin crear análisis artificiales. Captura panel-dimensiones-corregido.png guardada fuera del repositorio en la carpeta de visualizaciones de esta conversación. Ambos servicios quedaron saludables y los volúmenes existentes se conservaron.

## Dashboard compacto y Bootstrap — solicitud 2026-11-04

Por instrucción explícita del usuario se incorporó Bootstrap 5.3.8 a package.json/package-lock.json y se importa su CSS compilado desde main.tsx antes del estilo propio. No se usa CDN, JavaScript de Bootstrap ni un segundo sistema de diálogos; React y el diálogo nativo mantienen eventos, validación y foco. La integración sigue la documentación oficial https://getbootstrap.com/docs/5.3/getting-started/download/#npm. Los colores existentes alimentan las variables Bootstrap en styles.css.

ui.tsx centraliza Button, Input, Select, Textarea y Table con las clases Bootstrap; los módulos existentes reutilizan esos propietarios. Status usa badge y Loading spinner-border. Las tablas mantienen cabecera azul y desplazamiento propio. Se preservaron labels, nombres, tipos, eventos, referencias y atributos de accesibilidad de los controles. Se corrigió la selección del primer campo inválido de Form: antes podía seleccionar el fieldset inválido, que no recibía el foco; ahora consulta específicamente input/select/textarea inválidos. Se conserva la flecha del selector de proyecto y las etiquetas secundarias de tablas, y se evita partir números de fila o palabras de estados.

Se retiraron los cuatro textos marcados: explicación extensa encima de dimensiones, nota debajo de dimensiones, pie general y nota permanente sobre el historial de incidencias. También se redujo el texto del resumen lateral. Las tarjetas muestran dimensión, medida, estado y fecha con Ver detalle; condiciones, umbrales, cobertura, conteos, versiones y origen permanecen en la evaluación histórica. Con varias reglas se muestra su cantidad y distribución de estados, sin promediar sus porcentajes. Los estados Sin evaluar/No evaluable y las alertas contextuales por origen caducado o ejecución fallida siguen disponibles. Se conserva la selección histórica por dimensión de la corrección anterior.

La cuadrícula usa row, col-12, col-md-6 y col-xl-4 con tarjetas card/h-100. A 1265 px las seis tarjetas medían 197 px de alto y conservaban su alineación, frente a las tarjetas extensas de la captura original. Bajo 768 px se apilan; no se agregaron ni movieron carpetas. Se retiró el CSS exclusivo de las tarjetas anteriores y el pie eliminado; se mantiene dimension-label porque se reutiliza en las tablas de historial, reglas e incidencias. README, DESIGN y UX-CONTRACT describen la nueva presentación.

Verificación final: TypeScript y build aprobados; Docker verify con 69 pruebas Java sin fallos y ambos servicios saludables. verificar_panel.py aprobó 39 comprobaciones HTTP de solo lectura con las tres dimensiones que el usuario ya había evaluado, sus fuentes y el aislamiento de permisos. Auditoría estática estricta sin errores ni advertencias. No se crearon archivos, reglas, evaluaciones ni incidencias para comprobar la interfaz.

Navegador integrado: dashboard compacto con completitud/unicidad/validez y las seis dimensiones; acceso por teclado al informe histórico; formulario de regla vacío rechazado con foco real en el input inválido; cierre Escape; asistente con dos reglas hasta Revisión, sin iniciar el análisis; filtros de incidencias sin resultados y limpieza; formato de referencias y filas; selector de activo nativo abierto. A 390 px, documento sin desbordamiento horizontal y formulario dentro del ancho disponible, con campos de texto de 16 px. Se restauró el tamaño del navegador y la consola no mostró errores ni advertencias. Captura dashboard-bootstrap.png guardada fuera del repositorio, en la carpeta de visualizaciones de esta conversación.

## Consolidación de Bootstrap y contraste de acciones — 2026-11-04

Se atendió la separación solicitada: Bootstrap controla columnas, separación, padding estándar, formularios, tablas, tarjetas y botones; styles.css conserva tema corporativo, tipografía y excepciones del flujo (diálogos nativos, desplazamiento de tablas y estados de calidad). Dashboard, filtros, grupos de campos, metadatos y accesos usan row/col, row-cols y utilidades de espaciado/flex. La navegación usa nav-link. Se retiraron 251 reglas/selectores de controles y disposiciones anteriores y se eliminaron declaraciones anteriores anuladas en el mismo contexto. El CSS propio pasó de 39 645 a 21 640 bytes, aproximadamente 45 % menos, manteniendo las excepciones necesarias.

Button recibe variant explícito en vez de deducir su aspecto por clases heredadas: primary, outline-primary, link, light, outline-light, outline-danger, navigation y tool. Se eliminaron las reglas generales button:hover y los selectores descendientes que recoloreaban los botones de section-heading. Estos mezclaban el fondo claro de la cabecera con el texto que Bootstrap cambiaba a blanco. Ver historial y Actualizar panel usan light, con colores normal, hover, active y disabled definidos conjuntamente mediante variables Bootstrap. Nueva evaluación sobre la cabecera usa outline-light. El foco visible tiene un contorno dorado independiente del hover. Los controles siguen siendo React y no se incorporó JavaScript de Bootstrap.

Verificación: TypeScript y build aprobados; reconstrucción Docker verify aprobada con 69 pruebas Java y servicios saludables. Auditoría estática estricta sin errores ni advertencias. Los archivos de trabajo temporales de la migración se retiraron; target conserva una copia del CSS previo para revisión, excluida de Git. No se movieron carpetas ni se modificó el backend, el esquema, las reglas, los análisis o los archivos del usuario.

En navegador se aplicó hover real mediante el cursor a ambos botones y se leyeron los estilos calculados. Ambos devolvieron texto rgb(32,48,67) sobre fondo rgb(232,243,250), contraste 11,90:1. En estado normal el texto rgb(28,67,93) sobre blanco dio 10,45:1. Se comprobó foco visible con Tab, navegación al historial con Enter y actualización manual sin crear análisis. Se verificaron formulario vacío con alerta de error y foco en el input inválido, filtros de incidencias sin resultados y limpieza, tablas con desplazamiento propio y asistente hasta Revisión sin ejecutar la evaluación.

A 390 px, documento de 375 px útiles sin desbordamiento horizontal; tablas con desplazamiento interno, diálogos de 341 px y campos de texto de 16 px. Se restauró el tamaño del navegador. Consola sin errores ni advertencias en la aplicación desplegada. Capturas hover-bootstrap.png y bootstrap-consolidado.png guardadas fuera del repositorio en la carpeta de visualizaciones de esta conversación. README, DESIGN y UX-CONTRACT documentan la propiedad de estilos y variantes para prevenir nuevas superposiciones.

## Revocación de roles y migración local — 2026-11-09

Se corrigió el privilegio DELETE faltante en role_assignments. El esquema de arranque y el esquema de pruebas ahora conservan políticas independientes para lectura, alta y actualización, y limitan el borrado al administrador del proyecto definido en el contexto transaccional. La política anterior FOR ALL se sustituye para evitar que el nuevo privilegio permita borrados fuera de ese ámbito.

Docker/migrate-role-revocation.sql aplica el cambio de forma transaccional y repetible sobre bases existentes, con bloqueo de migración. Compose incluye database-migration como paso previo a application. El servicio de mantenimiento usa el propietario PostgreSQL y termina con código 0. La aplicación mantiene su cuenta SQL dedicada. README documenta la actualización de Compose, el caso de Run desde IntelliJ y la regresión aislada.

RoleRevocationIntegrationTest ejecuta el flujo HTTP con Spring Boot y PostgreSQL reales. verificar_revocacion_roles.ps1 crea una base y contenedor temporales separados y los elimina al terminar. La prueba reconstruye los permisos anteriores, reproduce el error 500 y el acceso que permanece vigente, ejecuta la migración dos veces y comprueba la revocación 204 con invalidación de la sesión anterior. Verifica auditoría única, incremento de auth_version, conservación de roles de otro proyecto, rechazo sin CSRF o permiso, política RLS con la conexión m2_app y conservación del último administrador. Maven omite estos cuatro casos si no está configurada la conexión aislada.

Resultado de la regresión completa con PostgreSQL 17: 73 pruebas aprobadas, cero fallos, cero errores y cero omisiones. Compose validado y migración aplicada a la base local existente. Las cinco asignaciones conservaron su huella de contenido antes y después. database y application saludables, database-migration terminó con código 0. La reconstrucción de la imagen se intentó y falló al resolver dependencias de Maven Surefire 3.5.2; la aplicación local continúa con la imagen anterior y el permiso SQL corregido. Los registros de esta ejecución están excluidos de Git bajo target/role-revocation-*.log.

## Protección de administradores activos bajo concurrencia — 2026-11-09

AdministrationController reutiliza la coordinación de cuentas y proyectos en las altas, activaciones y asignaciones o revocaciones de roles. QualityRepository toma bloqueos transaccionales de PostgreSQL sobre las identidades implicadas en un orden común. Esto estabiliza los permisos del actor y las membresías de la cuenta objetivo. Después se bloquean los proyectos en orden y se actualizan los datos de la cuenta con bloqueo antes de escribir. Se vuelve a comprobar la autorización del actor después de esperar, incluyendo el estado almacenado en la caché JPA.

La desactivación comprueba todos los proyectos de la cuenta. La desactivación y la revocación de una asignación SYSTEM_ADMIN usan la misma consulta para exigir otra cuenta activa con administración de proyecto. Una asignación de una cuenta inactiva no permite retirar al último administrador activo. La coordinación se libera con la confirmación o reversión de la transacción y usa las funciones existentes de PostgreSQL.

La regresión HTTP/PostgreSQL añade tres casos al archivo RoleRevocationIntegrationTest. Se fuerza la coincidencia de las peticiones dentro de sus transacciones, reteniendo temporalmente las filas de las cuentas y observando dos solicitudes en espera. Se verificaron desactivaciones cruzadas entre dos proyectos, desactivación simultánea con revocación y el intento de revocación propia con otra cuenta administradora inactiva. Se conservó al menos un administrador activo en cada proyecto y se registró solo el cambio autorizado.

Verificación completa en base temporal aislada: 76 pruebas aprobadas, cero fallos, cero errores y cero omisiones. El contenedor de pruebas se eliminó al finalizar. La construcción normal de Docker pasó con 69 pruebas y los siete casos de integración omitidos por falta de conexión aislada durante el build. El JAR local se empaquetó sin conexión después de la regresión. La aplicación local quedó ejecutando la imagen nueva, respondió UP y ambos servicios persistentes quedaron saludables. Las cinco asignaciones existentes conservaron la misma huella de contenido. Los registros de prueba, construcción y despliegue están en target/admin-concurrency-*.log, excluidos de Git.

## Recuperación de cuentas mediante Jakarta Mail y SMTP — 2026-11-09

Se incorporó spring-boot-starter-mail y RecoveryMailConfiguration como adaptador de RecoveryDelivery. La configuración por entorno usa un proveedor SMTP, puerto 587 y STARTTLS obligatorio. También admite TLS directo y un modo LOCAL limitado a loopback para las pruebas. La configuración valida remitente, transporte y autenticación, comprueba la identidad TLS del servidor y establece tiempos máximos de conexión, lectura y escritura. La recuperación se habilita de forma explícita. La condición también se aplica al record SMTP porque la aplicación lo encuentra mediante ConfigurationPropertiesScan.

IdentityService confirma el registro del token antes de entregar el correo. PostgreSQL conserva solo el hash. Si falla el envío, invalida ese código y registra RECOVERY_DELIVERY_FAILED con el identificador del registro, sin correo, contraseña ni token. La respuesta pública sigue siendo genérica. Un canal deshabilitado devuelve 503 RECOVERY_UNAVAILABLE. Las cuentas inactivas y las identidades técnicas configuradas quedan excluidas. Tras confirmar un código válido, se invalidan los otros códigos y las sesiones anteriores. La confirmación comparte la coordinación transaccional de cuentas con la administración para evitar carreras con desactivaciones y confirmaciones distintas.

PasswordRecoveryIntegrationTest y LocalSmtpInbox prueban HTTP, PostgreSQL y SMTP reales en el entorno aislado del script de regresión. El receptor SMTP escucha únicamente en loopback y nunca reenvía mensajes. Se comprobaron entrega y confirmación previa del token en la base, respuesta genérica, uso único, caducidad, códigos simultáneos, cierre de sesiones anteriores, cuentas excluidas, CSRF y fallo de SMTP con reintento. RecoveryMailTest comprueba la configuración, la activación explícita, el rechazo de transporte inseguro fuera de loopback, STARTTLS obligatorio y la ocultación de credenciales al representar la configuración.

Resultado completo: 87 pruebas aprobadas, cero fallos, cero errores y cero omisiones. El contenedor PostgreSQL de pruebas se eliminó al terminar. La construcción Docker pasó con 74 pruebas y 13 casos de integración omitidos durante el build. El JAR local se empaquetó sin conexión. La aplicación está ejecutando la imagen nueva y responde UP, con ambos servicios persistentes saludables. Las cinco asignaciones existentes conservaron la misma huella de contenido. Swagger describe el flujo de correo y se confirmó HTTP 503 RECOVERY_UNAVAILABLE en el entorno local, donde el canal permanece deshabilitado hasta disponer de una cuenta y credenciales SMTP reales.

README incluye los valores de entorno para Compose e IntelliJ y el uso del código desde el formulario existente. Las direcciones iniciales example.test no reciben correo real. La prueba no verificó entregas externas del proveedor SMTP. Los registros de comprobación están en target/password-recovery-*.log y target/recovery-mail-unit.log, excluidos de Git.

## Proveedor SMTP de Gmail — 2026-11-09

Gmail queda como servidor SMTP predeterminado en application.yml, Compose y la plantilla generada por preparar-docker.ps1, con smtp.gmail.com, puerto 587, STARTTLS y autenticación. README explica cómo habilitar la verificación en dos pasos, generar una contraseña de aplicación y usar la misma dirección Gmail completa como remitente y usuario SMTP. Las pruebas y la documentación se actualizaron para retirar las referencias del proveedor anterior.

La configuración privada local se preparó para Gmail y se retiraron los valores de remitente, usuario y contraseña del proveedor anterior. El envío quedó deshabilitado hasta que el usuario complete sus credenciales de Google. Se comprobó que el resto de las variables del archivo privado se conservaron.

La regresión aislada aprobó 87 pruebas sin fallos, errores ni omisiones. La construcción Docker pasó con 74 pruebas y 13 casos de integración omitidos durante el build. El JAR local se empaquetó sin conexión y se inspeccionó su configuración SMTP. La aplicación quedó ejecutando la imagen nueva, saludable y con respuesta UP. Se comprobaron smtp.gmail.com, 587, STARTTLS y el canal deshabilitado en el contenedor. Las cinco asignaciones existentes conservaron su huella de contenido y el contenedor de pruebas se eliminó. La entrega por Gmail queda pendiente de una dirección y una contraseña de aplicación reales. Registros excluidos de Git bajo target/gmail-provider-*.log.


## Protección de datos, entrega y límites operativos — 2026-11-09

Se implementaron InputRetention y CsvRetentionService con la conservación de CSV de 30 días acordada por el usuario. El acceso a celdas caduca inmediatamente por fecha; la purga física retira el contenido por lotes y conserva el manifiesto cifrado y el historial. Se aplica también a entradas anteriores sin expires_at. Las referencias históricas mantienen su inmutabilidad. La limpieza se ejecuta con la identidad técnica y el rol SQL m2_app, conserva auditoría y permite reintentos.

SensitiveDataCipher admite claves AES anteriores por configuración externa y cifra las escrituras con el identificador activo. Se valida el anillo, los identificadores y la separación de HMAC. El perfil production importa secretos externos, exige TLS y cookies seguras y deshabilita Swagger/aprovisionamiento. Se añadieron respaldar-datos.ps1 y restaurar-datos.ps1: captura conjunta de PostgreSQL, CSV y configuración, archivo cifrado autenticado por bloques y restauración restringida a un proyecto nuevo. La restauración invalida sesiones y códigos anteriores. No hay recifrado masivo ni rotación automática de la clave HMAC de búsqueda.

Se incorporaron presupuestos de trabajo e incidencias por evaluación, cuotas de cola por proyecto/solicitante y reserva de espacio y capacidad total de CSV. migrate-operational-limits.sql expone contadores agregados autorizados para que RLS no reduzca incorrectamente la cuota de gestores con alcance por activo. El ejecutor sigue trabajando con permisos normales. Las migraciones se ejecutan mediante migrate-db.sh antes del arranque. Se corrigió la comprobación de disponibilidad de PostgreSQL para esperar al servidor TCP definitivo en lugar del servidor temporal de inicialización.

La creación de sesiones anónimas tiene límites por dirección y globales y caducidad corta. OperationalMonitor registra heap, disco, almacenamiento, sesiones y cola, con umbrales de aviso. Los rechazos de seguridad registran códigos/estado/correlación y excluyen valores sensibles. El Compose limita la aplicación a 1 GiB, 1 CPU y 256 procesos/hilos, con raíz de solo lectura y /tmp temporal. Estos controles de archivos/sesiones corresponden a una sola instancia.

Se añadió Playwright como dependencia de desarrollo. La regresión del navegador sube un CSV ficticio, crea reglas, recarga para comprobar persistencia, ejecuta dimensiones por separado y comprueba ambas en el panel. Filtra incidencias, exige acción explícita para pedir evidencia y cierra el diálogo con Escape. verificar_entrega.ps1 combina las pruebas Java, CSV, Swagger, navegador, respaldo cifrado, restauración y cinco gestores concurrentes sobre un CSV de 10.000 filas. Los proyectos y volúmenes son temporales y se eliminan al terminar.

El workflow .github/workflows/m2-quality.yml se guardó en la raíz real de Git, un nivel por encima de la carpeta de la aplicación. Solo se agregó la ubicación requerida por GitHub; no se movieron carpetas del programa. Se validó su YAML. Usa acciones fijadas por commit y permisos de lectura. Su activación como comprobación obligatoria requiere configurar las reglas remotas de la rama; no se cambió esa configuración ni se publicó código.

La inspección de la imagen inicial identificó 25 avisos altos/críticos. Se actualizaron Spring Boot a 3.5.16, Jackson a 2.21.7, PostgreSQL JDBC a 42.7.13, Bouncy Castle a 1.85 y Tomcat a 10.1.61, además de APT en la imagen. La versión efectiva de Tomcat tiene una prueba y las versiones finales se confirmaron en el JAR de la imagen mediante Trivy.

Resultado de las comprobaciones:

- 103 pruebas Java aprobadas con PostgreSQL real, sin fallos, errores ni omisiones. Incluyen revocación, concurrencia, recuperación local SMTP, HTTPS/certificado temporal, cuota SQL, caducidad y purga de evidencia, rotación y respaldo cifrado.
- 17 comprobaciones Swagger/OpenAPI y 17 del recorrido CSV aprobadas; una prueba Playwright completa aprobada sobre la imagen final.
- Respaldo y restauración comparados: mismas referencias, resultados, versiones, incidencias y evidencia autorizada. La cookie anterior no autentica y la cuenta técnica de administración no obtiene evidencia.
- Cinco evaluaciones concurrentes, 10.000 filas y dos reglas por evaluación: cinco completadas, 8,521 segundos totales en este equipo. Once muestras operativas: heap máximo observado 102,94 MiB sobre heap permitido de 694,12 MiB; cola máxima observada de cuatro pendientes y ningún aviso de capacidad. Es una referencia local, no un SLA.
- Trivy final: cero vulnerabilidades npm altas/críticas, cero secretos detectados en fuentes e imagen y dos avisos críticos restantes en spring-webmvc 6.2.19: CVE-2026-47884 y CVE-2026-47890. El control devuelve error y bloquea la publicación. La revisión de secretos se realiza sin consultas Maven; las dependencias Java se revisan en el JAR de la imagen. No se agregaron excepciones para ocultar los avisos.

Los avisos oficiales restantes requieren analizar XsltView y fragmentos de vistas sobre SSE. No se observaron esos usos en este módulo REST/React con consulta periódica, pero no se declaró una excepción automática. El parche de la rama 6.2 es comercial; una migración evaluada a Spring Boot 4 o una justificación formal de no aplicabilidad debe cerrar la decisión. También quedan por configurar certificado/secreto reales, reglas obligatorias de GitHub, custodia/retención externa de respaldos y destino operativo de alertas. Las conexiones M1/TC13 siguen dependiendo de sus contratos reales.

La guía está en SEGURIDAD_Y_OPERACION.md. Los informes detallados permanecen excluidos de Git en target/security-*.log y target/security-*.json. Las pruebas no agregaron datos al proyecto diario. Su contenedor conserva la imagen anterior hasta ejecutar iniciar.ps1 para aplicar esta versión; no se activó el envío real de correos ni se sustituyeron las claves privadas.

## Revisión crítica e interfaz sin Bootstrap — 2026-11-09

Se documentó una revisión de QA y ciberseguridad en REVISION_QA_SEGURIDAD_FRONTEND.md: fortalezas comprobadas, dos avisos condicionados de Spring MVC, límite silencioso del catálogo a 1.000 activos, cargas CSV concurrentes por medir, inventario de claves de respaldo y obligaciones operativas. Se distinguen problemas observados de hipótesis que requieren pruebas. Los hallazgos de backend quedan explícitamente pendientes; no se mezclaron con el rediseño solicitado.

Se retiró Bootstrap y se adaptaron componentes de shadcn/ui con Tailwind CSS 4 en ui.tsx y los archivos existentes. Se conservan React, TypeScript, APIs, controles nativos y carpetas. Las seis dimensiones muestran tarjetas compactas con porcentaje/estado y acceso al origen. Botones, controles y tablas comparten variantes, foco y colores. Refero Styles e Impeccable se consultaron como inspiración. DESIGN.md, UX-CONTRACT.md y LICENCIAS_FRONTEND.md documentan las decisiones y la atribución.

Se verificaron compilación estricta, 103 pruebas Java/PostgreSQL, 17 comprobaciones OpenAPI, 17 del flujo CSV, navegador con axe/teclado/hover/móvil, respaldo/restauración y cinco usuarios con 10.000 filas (cinco evaluaciones completadas, 8,398 s). npm audit no encontró avisos. La imagen final sigue presentando dos avisos Spring MVC: el control de publicación continúa rechazando la entrega. No se declara aptitud para producción.

Ponytail quedó instalado como skill local de Codex, fijada al commit 96608e078b2d5ea607c5c3b8b5a54d1c1fba6846, disponible a partir del siguiente turno. Es una guía de trabajo y reutilización, no un límite técnico de tokens. No se agregó a las dependencias de la aplicación ni se conectaron datos del usuario a proveedores IA. Para ver la interfaz en el contenedor diario debe ejecutarse iniciar.ps1; las verificaciones se hicieron en entornos aislados.

## Rediseño y animaciones reales — 2026-11-09

Se integró Motion 14.11.1, un recurso de la lista del usuario, mediante LazyMotion/domAnimation y componentes m. Reveal centraliza entradas y transiciones; MetricGauge dibuja una medición real con SVG animado. La interfaz cambia su composición: barra lateral azul petróleo, acciones verdes, acceso dividido, cabecera de archivo, contadores reales, seis dimensiones prioritarias y accesos de trabajo compactos. Kokonut UI se consultó como referencia de tarjetas y carga. No se copian cifras de ejemplo ni se agregan gráficos de tendencias sin datos.

El cambio alcanza acceso, panel, reglas, evaluaciones, incidencias y los controles compartidos de cuentas. Se añadieron FilterPanel, skeleton de carga, selección CSV visual, feedback al guardar, estados en ejecución, apertura de diálogos y favicon propio. Las tablas mantienen controles nativos, líneas sutiles y desplazamiento local. Se corrigió la disposición de las acciones de reglas dentro de una celda nativa de tabla. No se movieron carpetas ni se cambiaron contratos del backend.

Las animaciones respetan movimiento reducido. Al cambiar de sección/proyecto se restablece el desplazamiento al inicio. Los diálogos se desmontan inmediatamente, sin retener evidencia en transiciones de salida. Se mantiene la CSP estricta. TypeScript y Vite compilan; el aviso de directivas RSC se filtra solo en los paquetes Motion, porque esta aplicación monta todo con createRoot. El resto de avisos y errores se conserva.

El navegador comprueba reglas persistentes, evaluación por dimensiones, anillo proporcional, filtros, evidencia explícita, teclado, contraste, móvil de 390 px, movimiento reducido, errores de ejecución y violaciones CSP. Se inspeccionaron las capturas de acceso, reglas, panel, historial e incidencias. npm audit de las dependencias finales no encontró avisos. Las capturas ui-motion-*.png y registros qa-motion-*.log permanecen excluidos de Git en target/. Las pruebas usan proyectos temporales, que se eliminan al finalizar.

Los hallazgos de seguridad del informe anterior siguen pendientes. Esta revisión visual no cambia la decisión de bloqueo de publicación ni equivale a una certificación de accesibilidad o de seguridad.

Verificación final de este rediseño: compilación TypeScript/Vite aprobada sin avisos; 17 comprobaciones OpenAPI y 17 CSV aprobadas; recorrido Playwright ampliado aprobado en 17,2 s, sin violaciones axe, errores JavaScript o CSP en los puntos comprobados. Respaldo/restauración y permisos aprobados. Cinco evaluaciones simultáneas de 10.000 filas y dos reglas completaron en 7,638 s totales en este equipo; es una referencia local. npm audit: cero avisos. El JavaScript principal pesa 131,41 kB comprimido y el CSS 9,89 kB; las animaciones añaden coste de descarga y no se presentan como gratuitas en rendimiento.

Se actualizó únicamente m2-calidad-application-1 con la imagen verificada m2-delivery-2062e6595222-application. Aplicación y base quedaron saludables; se comprobó que el ID del contenedor de PostgreSQL permaneció igual y que HTTP entrega .app-sidebar/.metric-gauge en el CSS nuevo. Los volúmenes y las credenciales se conservaron. El registro local está en target/ui-motion-deployment.json. Se eliminaron los contenedores y volúmenes temporales de las pruebas. La interfaz nueva está disponible en http://localhost:8080; basta recargar con Ctrl+F5.
