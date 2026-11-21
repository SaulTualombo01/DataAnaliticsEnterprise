# Cuentas e invitaciones del tronco común

Actualización14/11/2026: cuentas1.1 añade detalle y edición de nombre con revisión;
la base de invitaciones descrita debajo conserva su comportamiento. Ver
[TC-08/TC-09](TC08_TC09.md); V6 contiene6 roles/77 permisos.
Actualización20/11/2026: la [conciliación M4](../Conciliacion_M0_M4.md)
mantiene6 roles y añade m4.kpis.evaluate, para78 permisos iniciales en V8.

13 de noviembre de 2026. Base técnica 1.0.0, ocho operaciones HTTP implementadas
en modulo0_troncoComun. Contratos: cuentas.openapi.json, cuentas.schema.json y
cuentas.examples.json. La cuenta local/sesión existente sigue vigente.

## Flujo y responsabilidades

1. ADMIN_SISTEMA crea empresa y, para su primer administrador, invita una cuenta
   nueva a esa empresa. Después de activarse puede designarla mediante la API de
   administración empresarial existente. No se crean cuentas activas con claves
   elegidas por un administrador ni se asigna un rol por la invitación.
2. ADMIN_EMPRESA puede invitar empleados, listar invitaciones/empleados y
   desactivar empleados ordinarios exclusivamente de su empresa. No puede
   desactivar administradores de empresa/sistema ni su propia cuenta. Una cuenta
   heredada con otra pertenencia empresarial activa requiere revisión global.
3. La persona recibe el token por un canal privado autorizado y activa con una
   nueva contraseña. Correo/nombre/empresa están fijados por la invitación, sin
   rol/proyecto aceptado del cliente. Se crea una sola membresía empresarial y
   ninguna membresía de proyecto ni concesión funcional. Después inicia sesión.
4. ADMIN_SISTEMA puede listar/desactivar cuentas globalmente. Una autobaja global
   es posible solo si queda otro administrador activo con credencial local y rol
   ADMIN_SISTEMA vigente. La protección y el cambio comparten bloqueo transaccional.

## Canal de entrega de desarrollo

No existe todavía SMTP, envío de correo ni verificación real de control del
buzón. La entrega manual está deshabilitada por defecto: emitir una invitación
sin canal devuelve503 DELIVERY_UNAVAILABLE y no persiste nada. Para pruebas
locales, habilitar explícitamente antes de arrancar:

```powershell
$env:INVITATION_MANUAL_DELIVERY_ENABLED='true'
$env:INVITATION_TTL='24h'
```

El recibo201 entrega activation_token una sola vez al administrador autorizado;
las listas nunca lo devuelven. Cache-Control:no-store. Tratarlo como credencial:
no pegarlo en URLs, logs, capturas públicas, repositorios, memoria TIC o scripts
con argumentos. Si se pierde, emitir otra invitación de la misma empresa/correo;
se revoca la anterior, incluso si expiró. Los ejemplos son exclusivamente
sintéticos. No usar este canal como prueba de correo verificado en producción.
Integrar un transporte real requerirá configuración y contrato de entrega propios.

## Rutas y consumo

| Método bajo /api/v1 | Ruta | Permiso |
| --- | --- | --- |
| POST | /organizations/{organization_id}/invitations | tc.company.users.invite |
| GET | /organizations/{organization_id}/invitations | tc.company.invitations.read |
| POST | /organizations/{organization_id}/invitations/{invitation_id}/revocations | tc.company.invitations.revoke |
| POST | /auth/invitations/accept | Token de invitación; sin sesión autenticada obligatoria. |
| GET | /admin/users | tc.users.read |
| GET | /organizations/{organization_id}/users | tc.company.users.read |
| POST | /admin/users/{user_id}/deactivations | tc.users.deactivate |
| POST | /organizations/{organization_id}/users/{user_id}/deactivations | tc.company.users.deactivate |

Todas las escrituras, incluida la activación anónima, requieren CSRF obtenido
por GET /auth/csrf y la misma cookie; usar header_name/token devueltos por ese
endpoint, sin copiarlos a logs. Invitar: email/display_name/reason. Activar:
activation_token/new_password. Revocar/desactivar: reason. Cuerpos JSON cerrados,
sin campos extra; no parámetros de query en escrituras. Listas page desde1,
page_size25/máximo100, orden created_at,id y has_next, sin total global. Las
listas de empleados incluyen cuentas con membresía histórica de esa empresa;
esto no les concede acceso ni indica membresía vigente por sí solo.

Activación devuelve201 con cuenta no secreta y no inicia sesión automáticamente.
Revocar/desactivar devuelve204 con correlación y no-store; repetir no duplica el
evento. Revocar una invitación ya aceptada devuelve409, no desactiva su cuenta.
Un recurso fuera de empresa devuelve404; plataforma denegada403; sin sesión401.
Token desconocido, vencido, consumido, revocado o cuyo emisor perdió permisos:
422 INVALID_INVITATION, con el mismo detalle público. No revelar SQL o correo
de otra empresa en errores. Cuenta existente al invitar:409 genérico; no cambia
contraseña, empresa o estado. No hay registro público libre.

## Seguridad, persistencia y concurrencia

- Token SecureRandom256bits/Base64URL43 caracteres canónicos; solo SHA-256 en
  identidad.invitacion. TTL24h por defecto, configurable entre5min y7días. Uso
  único y transiciones pending→accepted/revoked; expired se calcula al consultar.
  Destinatario/empresa/emisor/expiración/hash no se reescriben ni eliminan.
- Credenciales bcrypt coste12 con prefijo existente. Nueva clave mínimo15 puntos
  de código y máximo72 bytes UTF-8; no se recorta ni normaliza. No se añade una
  política de composición diferente al login/cambio de clave existentes.
- Activar comprueba empresa y emisor vigentes, permiso actual, plazo y correo
  único; cuenta, credencial, membresía, consumo y auditoría se confirman juntos.
  Dos invitaciones de distintas empresas pueden coexistir, pero solo la primera
  activación válida crea la cuenta; la otra no puede cambiarla de empresa.
- Se reutiliza advisory lock937245813 de administración; cuenta/empresa/invitación
  se bloquean en la transacción. Es una elección conservadora de volumen inicial,
  sin afirmar escalabilidad ilimitada. Nuevos escritores deben conservar protocolo.
- Desactivar aumenta session_version mediante V3 y V5 retira roles, membresías,
  administración empresarial e invitaciones pendientes emitidas por esa cuenta.
  Cookie anterior falla401 en la siguiente solicitud; no se promete abortar una
  operación ya iniciada. Una reactivación futura requiere asignaciones nuevas.
  No se implementa aquí un endpoint de reactivación ni de eliminación física.
- La protección del último administrador cubre estas APIs, no cambios SQL directos,
  edición externa de permisos o bajas del proveedor de infraestructura. No contar
  una cuenta sin credencial como relevo accesible. Recuperación administrativa se
  realiza con el procedimiento de operación autorizado, no saltando el control.
- Activación JSON máxima8192bytes;30 intentos/IP/15min por instancia, memoria
  acotada, Retry-After y429. Ajustes ACTIVATION_WINDOW/ACTIVATION_IP_LIMIT.
  No se confía en X-Forwarded-For. Revisión de proxy/límite distribuido pendiente
  para despliegues compartidos. No es una garantía contra todo abuso.
- Eventos guardan actor/recurso/empresa/motivo/correlación, sin token, hash ni
  contraseña. Los motivos administrativos no deben incluir información secreta.

V5 es aditiva, sin modificar V1..V4; añade cinco permisos ORGANIZATION explícitos
a ADMIN_EMPRESA y ADMIN_SISTEMA: **6 roles/66 permisos** en total. No se cambian
los permisos M1/TC13 ni se migra una base real durante esta implementación.

## Verificación y límites

JDK24/release23, PostgreSQL18 temporal: tools/verificar_postgres_tc.py prueba
el conjunto anterior y la actualización V3→V4→V5 con historial preservado.
tools/validar_contratos.py comprueba Schema/OpenAPI/ejemplos y respuestas HTTP
de target/contract-evidence/cuentas.json cuando existen. Esa evidencia excluye
recibos con tokens de entrega; no guardar secretos para validar un contrato.

Las pruebas cubren login/cookie/CSRF reales, revocación con la misma cookie,
aislamiento empresarial, consumo concurrente/expiración/reemisión, pérdida de
delegación, rollback, no recuperación automática y último administrador.
Todavía faltan pantallas de cuentas/invitaciones, SMTP, recuperación de clave,
reactivación y administración de membresías/roles funcionales por proyecto.
M1 no incorpora este incremento hasta integrarlo expresamente; main queda intacto.

Referencias de seguridad:
[OWASP tokens de recuperación](https://cheatsheetseries.owasp.org/cheatsheets/Forgot_Password_Cheat_Sheet.html),
[OWASP almacenamiento de claves](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html),
[OWASP autenticación](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html).
