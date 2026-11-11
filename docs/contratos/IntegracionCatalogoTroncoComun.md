# Integración de catálogo con el tronco común

La rama `modulo1_catalogo` incorpora autenticación, sesiones y autorización de
`modulo0_troncoComun` mediante merge. Los módulos permanecen en el mismo proceso
Spring Boot. No se añade una llamada HTTP entre catálogo y autorización.

## Pertenencia de fuentes y activos

M1 implementa la SPI pública `ProveedorRecursosAutorizacion` mediante
`RecursosCatalogoJdbcAdapter`, dentro de su infraestructura. Consulta únicamente
identificadores reales de proyecto, fuente y activo. No devuelve configuración
de conexión, referencias de secretos, registros empresariales ni entidades internas.

Autorización comprueba que esa jerarquía corresponda al recurso y proyecto de la
operación y que el actor conserve cuenta, membresías, rol, permiso y alcance vigentes.
Los UUID de un cliente no demuestran pertenencia. Un alcance de fuente cubre sus
activos; un alcance de activo no concede acceso a hermanos, a la fuente ni a un
listado completo. Los filtros devueltos para listados deben aplicarse en las consultas.

Resolver jerarquía no ejecuta la operación ni valida sus estados funcionales. Un
recurso retirado conserva identidad e historial; descubrir una fuente deshabilitada
continúa prohibido por el propietario. No se concede acceso automático al catálogo
por administrar la plataforma o por pertenecer a otro componente.

Los futuros casos de uso deben obtener al actor mediante `IdentidadActualApi`,
exigir un permiso fijo en servidor con `AutorizacionApi` y validar las reglas de
negocio antes de invocar su repositorio interno. Esta integración no convierte al
repositorio en una API autorizada ni publica endpoints de catálogo.

## Migraciones y bases de pruebas

El conjunto integrado aplica V1, V1_1, V1_2, V2 y V3 en una base vacía. V2 pertenece
a M1; V3 añade credenciales y revocación comunes. No se alteran migraciones históricas,
checksums ni restricciones para hacer pasar las pruebas.

Una base exclusiva del tronco común que ya aplicó V3 no tiene V2. No reutilizarla
al cambiar a M1 ni activar migraciones fuera de orden: usar una base dedicada nueva
o planificar expresamente una migración de datos. Una base M1 que ya aplicó V2 puede
incorporar V3 por la secuencia normal. Respaldar cualquier entorno con datos propios.

## Pruebas y alcance pendiente

Los casos SQL de RBAC sobre recursos ficticios se trasladan a pruebas M1 con
fuentes/activos persistidos y claves foráneas reales. Las pruebas unitarias comunes
mantienen las verificaciones de proveedores ausentes/ambiguos y errores. La suite
integrada comprueba permisos, aislamiento, revocación, migraciones, catálogo y sesiones.

Quedan para el siguiente incremento el caso de uso público de alta/consulta de M1,
descubrimiento PostgreSQL, contratos de sus respuestas y pantallas. Los contratos de
autenticación y autorización se mantienen en los documentos adyacentes.
