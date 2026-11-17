# Contenedor web y mocks compartidos Q1

Fecha: 11 de noviembre de 2026. Implementación local en modulo0_troncoComun,
en checkout aislado para conservar los cambios pendientes de M1.

## Alcance entregado

TC-03: proyecto React/TypeScript construible, configuración de ejemplo y workflow
de verificación. TC-05: navegación base, estados de carga/vacío/denegación/error,
identidad/contexto sintéticos y diccionario de ejemplo. Consulte Frontend/README.md
para comandos y límites. La revisión del equipo y ejecución remota de CI quedan
pendientes; no se declara Q1 aceptada.

Las rutas reales de identidad son las existentes en autenticacion.openapi.json.
El frontend no crea /contexts ni operaciones de administración falsas. Un rol
visible en un fixture no representa autorización real. La selección real de
contextos y sus servicios administrativos continúan en Q2.

## Contrato de los mocks

Frontend/src/mocks/identidad-contexto.json se identifica con kind=mock y cumple
mocks-identidad-contexto.schema.json (JSON Schema 2020-12). profile usa la forma
del perfil propio: id/email/display_name. contexts es una colección de fixtures
locales, no una respuesta de endpoint aprobado. Ambas pertenencias de ejemplo
corresponden a una sola empresa. El segundo proyecto no tiene activo de ejemplo.
El esquema valida forma; la coherencia entre IDs se comprueba por separado.

catalogo.examples.json se copia de los ejemplos públicos de M1-04 disponibles
localmente el 11-11-2026. Contiene metadatos sintéticos, no registros empresariales
ni credenciales. No se importan clases/entidades privadas del backend M1 y no se
afirma revisión de M2/M3/M4. El adaptador respeta proyecto/activo/versión; una
petición incompatible falla. El escenario vacío devuelve éxito sin campos;
denegado, indisponible y expirado mantienen estados distintos.

La latencia simulada hace visible la carga. Cambiar de contexto cancela y
descarta el resultado anterior. Logout e identidad de demostración solo viven
en memoria de esa pestaña; no son una implementación alternativa de seguridad.
El bundle real excluye el adaptador y los fixtures de demostración. No hay
fallback de API real hacia datos sintéticos.

## Integración y entrega

La rama TC puede ejecutar autenticación real con su propio backend. Las dos
consultas reales del catálogo requieren el backend integrado de M1-04. El proxy
de Vite mantiene /api bajo el mismo origen del navegador y no altera RBAC,
CSRF, HttpOnly o SameSite. La interfaz no maneja directamente TICSESSION.

CI mínima: tres jobs de solo lectura del repositorio, sin despliegue. Maven
compila release23 con JDK24 y ejecuta pruebas sin DB. Node22 usa npm ci y
package-lock, prueba el cliente, construye ambos modos y ejecuta Chromium con
HTTP simulado. Los errores no se silencian con continue-on-error ni reintentos.
La suite PostgreSQL existente sigue siendo una comprobación separada.

El tercer job valida OpenAPI/Schema/ejemplos y prueba el mock de TC13; su contrato
candidato está en tc13/TC13.md. David fue designado responsable técnico provisional;
la revisión de consumidores y el servicio real siguen pendientes.

Los procedimientos anteriores no cierran TC-01/TC-02/M1-01 ni asignan responsable
o aprueban el contrato TC13. Esas decisiones requieren revisión de los responsables.

Referencias técnicas consultadas: [React](https://react.dev/versions),
[Vite](https://vite.dev/guide/),
[GitHub Actions](https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax).
