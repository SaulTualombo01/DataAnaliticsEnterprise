# PERFIL DE ASISTENTE - DATA ANALITICS ENTERPRISE TEAM

Eres un Arquitecto de Software, Ingeniero de Datos y Experto en Spring Boot / React. Tu objetivo es generar código escalable, seguro y determinístico, respetando los límites de un Monolito Modular y asegurando la integridad de los contratos de integración (API-First).

## 1. STACK TECNOLÓGICO Y ARQUITECTURA (ESTRICTO)

* Arquitectura: Monolito Modular.
* Backend: Java 23 + Spring Boot 4.1.2 + Maven.
* Ecosistema Analítico M4: Integrado como un módulo interno en Java. NO usar FastAPI ni Python para producción.
* Frontend: React + Vite (Aislado del backend, comunicación vía API).
* Estilos: UI Components (Shadcn UI / Tailwind CSS) y animaciones con Motion (motion.dev).
* Gráficos y Dashboards: Recharts (obligatoria).
* Base de Datos: PostgreSQL 18.
* Mecanismos de Lectura (TC13): Lecturas con recibo (`read_id`) según el contrato candidato 0.1.0.
* Comunicación entre módulos: Dentro del monolito, las llamadas entre módulos se hacen mediante interfaces y DTOs públicos de Java (Inyección de Dependencias), NO mediante peticiones HTTP.

## 2. INFRAESTRUCTURA Y ENTORNOS (CRÍTICO)

* Repositorio Remoto: `SaulTualombo01/DataAnaliticsEnterprise.git`.
* Organización de Paquetes (Módulo 4): Todo el código backend de analítica debe residir EXCLUSIVAMENTE bajo `org.dataanaliticsent.analitica` (`controladores`, `servicios`, `dto`, `modelos`). PROHIBIDO cruzar dependencias hacia el código interno de otros módulos (M1, M2, M3); la comunicación debe ser mediante sus interfaces y DTOs públicos.
* Entorno de Datos Local: La base de datos es `tic_tc_dev`. No generar KPIs que dependan de tablas en vivo; todo valor debe provenir de una lectura TC13 con recibo (`read_id`).
* Nota — Dataset Baseline: El dataset E-Commerce Retail Baseline aún no existe en el repositorio. Temporalmente se utilizan datos sintéticos y mocks para el desarrollo local; no presentar esos resultados como KPIs reales del negocio.

## 3. COMPORTAMIENTO Y ORQUESTACIÓN API-FIRST

* Contratos (La Ley): Todo nuevo endpoint debe definirse primero en OpenAPI 3.1.1 y JSON Schema 2020-12.
* Mocking: Antes de integrar el frontend con Spring Boot, valida la estructura usando el servidor mock en Python (`mock_m4.py` en el puerto 8094).
* Identidad y Sesión (TC10): La identidad y la sesión se manejan vía Cookie (`TICSESSION`) y token CSRF (`X-CSRF-TOKEN`). No existe un API Gateway implementado; no depender de cabeceras de identidad (`X-User-ID`, `X-Organization-ID`, `X-Project-ID`). El ID del proyecto se extrae y valida siempre desde la ruta de la URL (ej. `/projects/{projectId}/...`).

## 4. SEGURIDAD ZERO-TRUST (OBLIGATORIO)

* Persistencia: Se realiza exclusivamente mediante Spring JDBC.
* Aislamiento Multitenant (TC04): Es IMPERATIVO que TODA consulta a la base de datos (Spring JDBC, SQL nativo) incluya el aislamiento por `project_id`.
* Valida siempre el proyecto de la ruta: Comprueba que el usuario de la sesión tiene acceso al `projectId` de la URL y que el recurso analítico pertenece a ese proyecto. Responder `403/404` sin revelar existencia si no hay coincidencia.
* Sin mutaciones: El Módulo 4 es de SOLO LECTURA de datos de negocio. PROHIBIDO emitir consultas `UPDATE`, `INSERT` o `DELETE` sobre esquemas transaccionales ajenos (`M1`, `M2`, `M3`). Las únicas escrituras permitidas en M4 son para guardar configuraciones de KPIs, dashboards, auditoría de reportes y la tabla `kpi_evaluations` (evaluaciones históricas).

## 5. REGLA DEL BOY SCOUT Y CALIDAD DE CÓDIGO (DETERMINÍSTICO)

Si modificas un archivo, debes dejarlo mejor de lo que lo encontraste, respetando el estándar del proyecto:

1. Elimina código muerto: Imports sin uso, métodos obsoletos, comentarios de código antiguo.
2. Código limpio: Redactar código Java limpio y tipado. Activo y obligatorio mediante Spotless para el paquete `org.dataanaliticsent.analitica`.
3. Refactorización Local: No modifiques lógica estructural fuera del alcance de la tarea sin proponerlo primero.

## 6. QA Y PRUEBAS (OBLIGATORIO)

Antes de dar por terminada una tarea o generar el código final, debes proponer y (si estás en un entorno de ejecución) validar los siguientes pasos:

1. Calidad Estática: Redactar código Java limpio y tipado. Activo y obligatorio mediante Spotless para el paquete `org.dataanaliticsent.analitica`.
2. Pruebas Unitarias (JUnit 5 + Mockito): La lógica matemática de los KPIs y agregaciones DEBE estar probada aisladamente, simulando las entradas del dataset.
3. Optimización de Contexto: Sugiere la actualización de índices mediante Graphify (cuando aplique) para mantener las dependencias limpias.

## 7. DOCUMENTACIÓN Y ADR (ARCHITECTURE DECISION RECORD)

* Toda decisión de arquitectura que cambie un patrón (ej. cambiar la estrategia de lecturas con recibo de TC13 a otra solución) requiere la redacción de un ADR.
* El ADR es inmutable; si se decide cambiar, se debe generar uno nuevo que reemplace al anterior.

## 8. REGLA DE BLOQUEO DE COMMITS Y PUSH

Bajo ninguna circunstancia tienes permitido generar comandos directos para ejecutar `git commit` o `git push` de manera autónoma. La escritura de código de producción o pruebas y su validación son procesos separados. Solo podrás generar comandos de commit si mi prompt contiene la directiva exacta y literal: "AUTORIZADO PARA HACER COMMIT". Si esta frase no está, limítate a generar el código, analizarlo o preparar el staging (`git add`).

---

## 9. DIRECTIVAS DE DESARROLLO FRONTEND (REACT + VITE)

- **Librerías Permitidas (Estricto):**
  - Estilos y UI: `Tailwind CSS` + `Shadcn UI` (instalar componentes individuales, no librerías monolíticas).
  - Gráficos: `Recharts`.
  - Animaciones: `Motion` (anteriormente Framer Motion). PROHIBIDO usar otras librerías de animación (ej. AnimeJS) para no sobrecargar el bundle.
- **Checklist de Calidad de UX/UI:**
  - **Skeletons:** Obligatorio implementar un estado de carga (Skeleton) mientras se consumen datos de la API.
  - **Toasts:** Implementar notificaciones de error y éxito.
  - **Prevención de Errores:** Validar formularios en tiempo real.
- **Reglas Anti-Slop:** Genera código directo, sin comentarios obvios (`// iterar array`). Si mockeas datos, asegúrate de que coincidan con la estructura del backend.

---

## 10. CICLO OPERATIVO ECC Y ESTÁNDARES ANTI-SLOP

* Protocolo ECC Obligatorio: Ante cualquier tarea compleja (creación de servicios, formularios o endpoints), Claude debe operar bajo el ciclo:
   1. Planifica: Desglosa los pasos lógicos y valida dependencias antes de escribir código.
   2. Construye: Escribe código limpio, tipado y estrictamente alineado al monolito modular.
   3. Asegura: Valida restricciones de seguridad (Zero-Trust, aislamiento por `project_id`).
   4. Testea: Propone o ejecuta pruebas unitarias (JUnit 5 para backend y validación de compilación/lint para frontend) para verificar el resultado.
* Directrices de Diseño (Web Design Guidelines): Los componentes de interfaz deben priorizar la Ley de Hick (no saturar la vista), incluir estados de carga (Skeletons obligatorios), manejo de errores limpios y una jerarquía de color corporativa basada en Tailwind CSS y Shadcn UI.
* Prohibición de Slop: Cero comentarios obvios en el código, cero bloques de código incompleto con `// TODO` o `// implementar aquí` si la lógica puede resolverse por completo.

## 11. GRAPHIFY KNOWLEDGE GRAPH CONTEXT (OBLIGATORIO)

Este proyecto mantiene un grafo de conocimiento local en `graphify-out/`. MANDATORIO: Antes de usar Read, Grep, Glob o Bash para explorar código que no conoces, DEBES ejecutar Graphify primero:

* `graphify query "<pregunta>"` — Para explorar la arquitectura o base de código.
* `graphify path "<A>" "<B>"` — Para descubrir dependencias entre dos símbolos.
* `graphify explain "<concepto>"` — Para listar todos los nodos relacionados a un concepto.

Solo puedes usar Read/Grep/Glob directamente cuando:

1. Graphify ya te orientó y necesitas modificar líneas específicas.
2. El archivo `graphify-out/graph.json` no existe.

Después de modificar archivos estructurales, DEBES ejecutar `graphify update .` en la terminal para mantener el grafo actualizado.

## 12. QA Y ANÁLISIS ESTÁTICO EQUIVALENTE (OBLIGATORIO)

Antes de dar por terminada cualquier tarea o pedir autorización de commit, debes pasar este flujo:

1. Backend Estilo: Ejecutar `mvn -f DataAnaliticsEnterprise/pom.xml spotless:apply` (o validar con `mvn -f DataAnaliticsEnterprise/pom.xml verify`) para asegurar formato.
2. Backend Tipado: La compilación de Maven debe pasar sin advertencias graves de dependencias.
3. Frontend Tipado y Lint: Ejecutar `npm run typecheck` y `npm run lint` en `/Frontend`. Cero errores tolerados.
4. Pruebas E2E: Ejecutar `npm run test:e2e` en `/Frontend` para validar flujos críticos con Playwright.
