# Primer bloque administrativo del tronco común

13 de noviembre de 2026. Implementación de base para TC-08/TC-09, posterior al
cierre técnico de los contratos M1/TC13. No representa finalización de todo Q2.

## Operaciones disponibles

| Método y ruta bajo /api/v1 | Autorización |
| --- | --- |
| GET/POST /admin/organizations | Sistema: consultar/crear empresas. |
| GET /organizations/{organization_id} | Administración explícita de esa empresa. |
| GET/POST /organizations/{organization_id}/projects | Consultar/crear proyectos de esa empresa. |
| GET/POST /admin/organizations/{organization_id}/administrators | Sistema: consultar historial/designar administrador. |
| POST /admin/organizations/{organization_id}/administrators/{assignment_id}/revocations | Sistema: revocar una designación. Repetir es idempotente. |

POST de empresa/proyecto: name y reason. Designación: user_id y reason.
Revocación: reason. No se admiten actor, roles, permisos o contexto por campos
adicionales. El actor viene de IdentidadActualApi. El usuario designado debe
existir y estar activo: este bloque no crea cuentas ni sustituye invitaciones.
Contrato de rutas/DTO: contexto.openapi.json y contexto.schema.json.

Las listas usan page desde1, page_size25/máximo100 y una fila adicional para
has_next; sin total global. Orden created_at,id o assigned_at,id. Filtros de
organización se aplican en SQL. Parámetros desconocidos/duplicados, UUID no
canónicos y entradas inválidas se rechazan. No se promete una foto inmutable de
listas durante altas concurrentes. Respuestas data/meta y Problem Details.

## Alcance organizacional y privilegios

V4 añade ORGANIZATION y ADMIN_EMPRESA, sin reescribir V1/V1_1/V1_2/V3 ni V2 M1.
La asignación administrativa empresarial se guarda separada de las asignaciones
funcionales PROJECT, con FK compuesta a membresía/usuario/empresa. Hay seis roles
y61 permisos iniciales en total. El rol nuevo solo recibe tres acciones ORG:
consultar empresa y consultar/crear proyectos. Las acciones administrativas de
designación/revocación están reservadas al administrador global.

El administrador global recibe esas tres capacidades organizacionales mediante
concesiones explícitas, no por una excepción universal del evaluador. Ni el
administrador empresarial ni el global heredan permisos M1 o de contenido.
La pertenencia empresarial no crea membresías en proyectos automáticamente.

Una persona no puede tener dos designaciones empresariales vigentes. Una nueva
designación rechaza pertenencia activa a otra empresa y no reactiva membresías
inactivas implícitamente. Los servicios de membresías funcionales todavía deberán
implementar la política general de un empleado/empresa y un rol por proyecto;
no se da por aplicada a todos los datos antiguos por este bloque.

Las designaciones revocadas no se reescriben, reasignan ni reactivan: una nueva
autorización crea otra fila. Al desactivar una membresía empresarial, un trigger
revoca la administración asociada, de modo que reactivarla no devuelve el rol.
La revocación de designaciones sigue disponible para el sistema incluso si la
empresa está inactiva. Las lecturas/escrituras empresariales corrientes requieren
empresa, cuenta, membresía, rol y permisos activos. La política de proyecto
archivado y el retiro de roles funcionales se implementarán en su bloque de ciclo
de vida: no se relajó el control de proyecto activo del catálogo.

## Transacciones y auditoría

Alta de proyecto o designación y su evento se confirman o revierten juntos.
Los eventos incluyen actor de servidor, organización, recurso, sujeto cuando
corresponde, motivo y correlación. HTTP, cabecera y auditoría comparten request_id;
fuera de HTTP se genera correlación del servidor. No se guardan contraseñas,
tokens, SQL ni configuración de origen. El motivo administrativo no debe incluir
secretos. Auditoría/versiones históricas siguen protegidas contra cambios.

Las escrituras administrativas usan un advisory lock transaccional común y
bloqueos de cuenta/organización/destinatario, con comprobación vigente de permisos
antes/después. Es una elección conservadora para el volumen inicial, no una
afirmación de escalabilidad ilimitada. Futuros escritores administrativos deben
mantener este protocolo. Un administrador empresarial no puede designarse ni
otorgarse privilegios mediante estas rutas.

Los índices V4 soportan asignaciones por organización y unicidad activa. La
migración se prueba en base nueva y en una base separada que primero llega a V3,
conservando usuarios/contexto/auditoría. No se probaron ni migraron bases reales.
Para un despliegue compartido respaldar y evaluar el tamaño/bloqueos antes de
migrar; no usar Flyway clean, reparación de checksums ni outOfOrder automático.

## Verificación reproducible

Desde el checkout TC en Windows con PostgreSQL18, Maven y JDK24:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-24'
python tools/verificar_postgres_tc.py
```

Se crean dos bases en un clúster temporal, con cuenta de aplicación no
superusuario: prueba funcional y prueba de actualización V3→V4. No se usa el
servicio instalado. Para validar contratos y respuestas HTTP guardadas:

```powershell
python -m pip install -r tools/requirements-contracts.txt
python tools/validar_contratos.py
```

La verificación incluye separación empresarial, revocación inmediata sin renovar
cookie, CSRF, parámetros, auditoría/correlación, rollback y concurrencia. Las
pruebas no prueban pantallas administrativas: aún faltan CRUD de cuentas,
invitaciones/activación, administración de membresías/roles funcionales y su UI.
No se añadieron operaciones que retiren ADMIN_SISTEMA o desactiven cuentas;
la protección del último administrador deberá acompañar esas futuras operaciones.
