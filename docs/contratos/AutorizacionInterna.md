# Contrato Java de autorización compartida

**Estado:** primer incremento de evaluación RBAC del tronco común. No es un contrato HTTP ni un mecanismo de autenticación. Se mantiene la matriz inicial de cinco roles y 55 permisos.

## Interfaz pública

Paquete público: `org.dataanaliticsent.autorizacion`.

| Operación | Uso |
| --- | --- |
| `evaluar(actorId, solicitud)` | Devuelve una decisión con motivo estable, sin ejecutar la operación de negocio |
| `exigir(actorId, solicitud)` | Lanza AccesoDenegadoException si no existe autorización; un error de dependencia también interrumpe el flujo |
| `alcancesPermitidos(actorId, permiso, proyectoId)` | Devuelve restricciones vigentes para búsquedas y conteos; lista vacía equivale a ningún resultado autorizado |

El actor se obtiene en servidor mediante `IdentidadActualApi.usuarioId()`, conectado a la sesión local verificada. Nunca mapear actorId desde un formulario, cabecera libre, query string o cuerpo de un cliente. El filtro de vigencia comprueba estado de cuenta, versión de sesión y expiración absoluta antes de ejecutar rutas protegidas; el contenedor gestiona la inactividad. Consultar una cuenta activa no sustituye autenticarla.

El caso de uso elige un código de permiso fijo correspondiente a su operación, el tipo de recurso correcto y su identificador real. No delegar en el cliente la elección del permiso, propósito o tipo sobre el que se autoriza una escritura. Una decisión se usa para la operación actual; no es un token reutilizable de privilegios.

## Ámbitos

- **SELF:** operaciones conocidas de cuenta propia. Se comprueban cuenta activa, permiso disponible y coincidencia actor/titular; no se requiere pertenencia a un proyecto ni crear un sexto rol. El cierre efectivo de sesión deberá ser idempotente incluso si la identidad ya expiró o se desactivó: el futuro logout no debe depender de una autorización positiva de esta API.
- **PLATFORM:** exige asignación activa del rol ADMIN_SISTEMA en plataforma y permiso correspondiente, con rol/cuenta activos. Una asignación contextual del mismo nombre de rol no satisface esta regla.
- **PROJECT:** exige organización/proyecto, ambas membresías, rol y asignación vigentes, permiso disponible y al menos un alcance explícito. La asignación global de administrador no se hereda.

En ausencia de política de archivo más específica, un contexto inactivo se deniega. No se aprueba todavía la acumulación de roles ni la participación en varios proyectos; el evaluador interpreta las asignaciones existentes, pero el futuro servicio de altas debe imponer la política que se decida.

## Recursos e integridad del contexto

`SolicitudAcceso.recurso(...)` identifica SOURCE o ASSET sin aceptar del cliente su supuesto padre. El módulo propietario implementará `ProveedorRecursosAutorizacion` y resolverá una jerarquía formada por proyecto, fuente y activo cuando corresponda. Debe recuperar sus propios datos por su interfaz/repositorio interno y conservar la separación entre módulos.

El evaluador verifica que la respuesta corresponde al recurso solicitado y al proyecto declarado. No consulta tablas M1 desde el tronco común ni incluye una implementación ficticia de M1. Si no hay proveedor, el recurso no se autoriza; si hay más de uno para el mismo tipo, se produce un error de configuración que no concede acceso.

Alcances:

- PROJECT cubre el proyecto para el permiso concedido, pero una solicitud concreta de recurso sigue necesitando validar su pertenencia.
- SOURCE cubre esa fuente y sus activos.
- ASSET cubre ese activo; no sus hermanos ni su fuente padre.
- Una solicitud sin recurso solo se autoriza como capacidad de proyecto completo si existe alcance PROJECT. Para listados parciales hay que utilizar los filtros de alcances.

Para leer o modificar una fuente/activo concreto, el caso de uso debe usar la solicitud con recurso, no una comprobación genérica de proyecto. Para ejecuciones o versiones, primero identifica en servidor el recurso propietario correspondiente y aplica sus controles adicionales.

Los listados deben traducir los alcances al tipo de recurso que están consultando. Un alcance ASSET no habilita consultar su fuente padre aunque el usuario conozca el ID. Los IDs de un alcance no se usan como prueba de pertenencia: SOURCE/ASSET se validan mediante el proveedor antes de devolverlos. Tampoco se construyen resúmenes amplios descargando datos sin filtrar para ocultarlos en pantalla.

## Vigencia y errores

La evaluación consulta datos vigentes, sin caché. Reconfigurar rol-permiso, revocar una asignación (estado o revoked_at), desactivar cuenta/rol/membresía/contexto o retirar un alcance se refleja en la siguiente evaluación. Las operaciones ya iniciadas necesitan la política de cancelación/consistencia que se acuerde.

Los motivos diferencian ausencia de identidad, cuenta inactiva, permiso desconocido/no disponible, ámbito incorrecto, titular incorrecto, contexto no autorizado, permiso no concedido, recurso no validado y alcance insuficiente. Son resultados internos: una futura respuesta HTTP debe respetar el contrato común y evitar revelar información de recursos ajenos. Los errores JDBC/proveedor no se convierten en una decisión permitida.

La API no registra automáticamente cada comprobación como evento de negocio. El caso de uso deberá registrar la operación y sus fallos conforme a la matriz de auditoría; las futuras mutaciones administrativas deben ser autorizadas y auditadas. No se ofrecen funciones públicas para modificar roles en este incremento.

## Ejemplo de integración del consumidor

Ejemplo conceptual, dentro de un caso de uso que ya ha obtenido una identidad fiable:

```java
autorizacion.exigir(actorVerificadoId,
    SolicitudAcceso.recurso("m1.dictionary.update", proyectoId,
        SolicitudAcceso.TipoRecurso.ASSET, activoId));
// Validar estado del activo, edición/versionado y ejecutar la escritura autorizada.
```

Antes de usarlo con recursos reales, M1 debe registrar su proveedor y el caso de uso debe obtener esos IDs en servidor. Este ejemplo no añade un endpoint ni sustituye autorización por una identidad de servicio privilegiada.

## Verificación

Las pruebas unitarias cubren denegación por defecto, separación de ámbitos, titularidad, alcances, pertenencia de recurso, proveedores ausentes/ambiguos y errores de dependencias. Las pruebas PostgreSQL verifican los roles sembrados, el estado vigente de cuentas/membresías/contextos/asignaciones y el efecto de cambiar rol-permiso sin reiniciar. Los proveedores en memoria usados en esas pruebas son fixtures, no una implementación de catálogo en producción.

Referencia de diseño: [OWASP Authorization Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html).
