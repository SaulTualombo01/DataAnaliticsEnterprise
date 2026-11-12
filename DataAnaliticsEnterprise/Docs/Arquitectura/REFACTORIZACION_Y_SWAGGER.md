# Refactorización y documentación API — 2026-11-04

Revisión manual de responsabilidades, repetición y código sin uso, reforzada con compilación estricta, pruebas de regresión y comprobaciones del servicio. No se afirma que un análisis de SonarQube haya sido ejecutado ni que todo el repositorio esté libre de code smells.

| Hallazgo | Ajuste |
|---|---|
| Validación y cálculo de paginación repetidos | PageQuery valida tamaño, página y desbordamiento antes de calcular offset y PageInfo. |
| Identificación de actor, correlación y envoltura HTTP repetidas | ApiContext compartido por controladores. |
| UUID canónico validado de varias formas | CanonicalUuid compartido por conversión MVC, deserialización JSON, correlación e idempotencia. |
| Unión de ámbitos autorizados duplicada | AccessService.scope centraliza el contexto y los activos permitidos para cada acción, conservando RLS. |
| Lógica transaccional de seguimiento dentro del controlador | IncidentService concentra autorización, bloqueo, revisión, transición, cifrado y auditoría. El controlador se ocupa del contrato HTTP. |
| Payloads de error diferentes en seguridad y controladores | ApiProblem comparte nombres, tipo, estado y correlación. Los dos advice existentes se conservan porque atienden responsabilidades distintas. |
| Efectos React repetidos para datos, errores, cancelación y temporizadores | useApiQuery comparte lecturas; queryPath arma filtros y predicados comunes controlan el polling. No ejecuta mutaciones ni reintenta escrituras. |
| Selección de pantalla mediante ternarios anidados y filtros de permisos repetidos | renderSection, openHistory, openIncidents, sectionDescriptions y qualityAssets hacen explícita la navegación. |
| Dos descriptores Maven casi idénticos | pom.xml concentra dependencias, pruebas y recursos estáticos; Docker e instrucciones usan el mismo descriptor. Se retiró pom-completo.xml. |
| Dos clases de arranque, una en el paquete predeterminado | Se retiró el archivo redundante en Calidad; se mantiene org.dataanaliticsent.DataAnaliticsEnterprise. |
| Restos de la antigua barra lateral y declaraciones CSS idénticas | Se retiraron los selectores de esa barra y 21 declaraciones exactamente repetidas, conservando el orden de cascada y el diseño. |
| Parámetro TypeScript sin uso | Se retiró de permittedTab; noUnusedLocals/noUnusedParameters quedan activos para futuras compilaciones. |

No se movieron carpetas ni pruebas a otra ubicación. Los archivos nuevos pertenecen a los directorios existentes. Se conservaron los proveedores y scripts de prueba que aún se usan para regresión; no se borraron requisitos, pruebas útiles, archivos subidos ni historia de negocio.

## Swagger

springdoc-openapi-starter-webmvc-ui 2.8.14 incorpora Swagger UI y genera OpenAPI a partir de controladores y DTO existentes. Se documentan reglas de sesión, CSRF, concurrencia, idempotencia, paginación, CSV y estados de evaluación. La configuración usa el ObjectMapper de la aplicación para respetar nombres JSON snake_case y publica los esquemas de error referenciados.

La revisión 2.8.17 compiló, pero su patrón de recursos Swagger no arrancó con Spring Boot 3.4.3. Se fijó 2.8.14 según la evidencia de [springdoc #3210](https://github.com/springdoc/springdoc-openapi/issues/3210), conservando Boot y el motor de rutas. La matriz general 2.8.x no distingue este límite de revisiones.

Rutas: `/swagger-ui/index.html`, `/swagger-ui.html`, `/v3/api-docs`. Habilitadas en local/docker, deshabilitadas por defecto en el perfil normal. La cadena de seguridad de documentación solo publica GET sobre sus recursos. La cadena de la API mantiene sesión, CSRF, permisos y CSP estricta. La excepción CSS inline está acotada a la página Swagger, que la necesita para renderizar sus controles.

Para Try it out se usa la sesión del mismo navegador/origen; las escrituras requieren copiar el token de `GET /api/v1/session/csrf` a X-CSRF-TOKEN. No se guardan credenciales en Swagger y la documentación no concede permisos sobre datos.

## Verificación

Los resultados finales se registran en ESTADO_DESARROLLO.md. ApiUtilitiesTest cubre límites/desbordamiento, UUID y errores compartidos; AccessScopeTest cubre unión de ámbitos, permisos globales y denegación; IncidentServiceTest cubre transición cifrada/auditada y rechazos sin escritura. Todas estas pruebas usan objetos en memoria. verificar_swagger.py y verificar_diseno_ui.py consultan el entorno local sin crear archivos ni evaluaciones.
