# Autenticación local y sesiones del tronco común

Decisión de David: cuentas locales y sesión del servidor con cookie. El contrato se acompaña de `autenticacion.openapi.json`. La interfaz compartida incorpora el cliente de sesión; no hay login institucional. El bloque administrativo empresarial se documenta en AdministracionEmpresa.md y no sustituye la futura administración completa de cuentas.

## Operaciones

| Método y ruta | Acceso y entrada | Resultado |
| --- | --- | --- |
| GET /api/v1/auth/csrf | Público, mismo almacén de cookies | 200 data con header_name, parameter_name, token; meta.request_id |
| POST /api/v1/auth/login | Público con CSRF por cabecera; application/x-www-form-urlencoded, únicamente email y password sin duplicados | 200 con authenticated/user_id y cookie de sesión renovada; 400 formato de campos, 401 genérico, 403 CSRF, 415 formato, 429 límite, 503 dependencia |
| GET /api/v1/auth/me | Sesión vigente + tc.self.profile.read | 200 perfil propio id/email/display_name; 401/403 |
| POST /api/v1/auth/password | Sesión vigente, CSRF y permiso propio de cambio; JSON current_password/new_password | 204, cambia hash e invalida todas las sesiones; 400/401/403/409/503 |
| POST /api/v1/auth/logout | Sesión con CSRF si está autenticada | 204; invalida sesión/cookie. Si no hay identidad válida, cierre idempotente sin exigir CSRF de una sesión ya inexistente |
| POST /api/v1/admin/users/{id}/sessions/revoke | Sesión, CSRF y permiso PLATFORM tc.sessions.revoke | 204; siguiente solicitud del usuario revocado requiere autenticación |

El servidor obtiene la identidad del contexto de Spring Security. Ninguna ruta acepta actor, rol o proyecto para seleccionar al usuario autenticado. El cambio de contraseña es de la cuenta propia; los campos extra se rechazan. Revocar sesiones no asigna ni retira roles.

## Flujo web

1. Obtener CSRF, conservando la cookie anónima HttpOnly.
2. Enviar POST login con email/password como formulario URL-encoded y el token en la cabecera devuelta.
3. El servidor cambia el identificador de sesión. No se devuelve el secreto de sesión en JSON.
4. Volver a obtener CSRF después del login, pues Spring lo renueva. Consultar /me para cargar el perfil.
5. Adjuntar cookie y CSRF a escrituras. Un logout autenticado sin CSRF se rechaza.
6. Al salir, cambiar contraseña o invalidarse la sesión, pedir un nuevo CSRF antes de iniciar otra sesión.

Frontend y API se asumen en el mismo origen o a través de un proxy de desarrollo. No se habilita CORS con orígenes arbitrarios ni transporte de sesiones por URL. Para HTTP local se debe configurar SESSION_COOKIE_SECURE=false; la cookie es Secure por defecto para despliegue HTTPS.

## Credenciales y vigencia

Las contraseñas se almacenan como hash adaptativo BCrypt coste 12 mediante PasswordEncoder. El mínimo inicial es 15 caracteres y el máximo 72 bytes UTF-8 para evitar truncamiento. No se guardan contraseñas de acceso a la plataforma como credenciales recuperables del catálogo.

Las sesiones de esta primera instancia están en memoria del servidor, con cookie TICSESSION HttpOnly/SameSite=Lax/Secure. Caducan por inactividad (30m configurable) o límite absoluto (8h configurable); un reinicio las descarta. La versión de sesión persistida permite invalidarlas después de cambio de hash, revocación o cambio de estado de cuenta, incluso si se desactiva y reactiva antes de usar una sesión antigua.

Los cambios de rol/permiso siguen siendo efectivos por el evaluador RBAC vigente, sin codificarlos como autoridades duraderas dentro de la cookie. Las peticiones ya iniciadas se consideran operaciones en curso; la cancelación de ejecuciones largas requiere su política específica.

La limitación inicial usa una ventana fija de 15m, 5 intentos por cuenta y 50 por IP, con máximo 10.000 claves en memoria. Se conservan hashes de las claves de agrupación, no contraseñas. El éxito reinicia el contador de cuenta, no el de IP. Los valores son configurables y deben medirse en el despliegue; no constituyen cuotas institucionales aprobadas. Para múltiples instancias se necesitará limitación/sesión compartida.

## Inicialización, auditoría y límites

La inicialización del primer administrador es optativa por variables de entorno, sobre una base sin cuentas, con una transacción y exclusión entre inicializadores. No hay contraseñas ni cuentas predeterminadas. No modifica credenciales de un administrador existente ni recupera automáticamente una plataforma que ya tiene usuarios. Se deben retirar las variables de inicialización tras el primer uso.

Se registran entrada válida/fallida, cambio de contraseña, revocación e inicialización, con actor seguro y correlación, sin contraseña, cookie, CSRF ni hash. El cierre invalida la sesión incluso si la auditoría falla; en ese caso deja un aviso técnico con request_id. La indisponibilidad de identidad interrumpe las demás comprobaciones protegidas y no concede acceso.

No hay endpoints públicos de registro, de modificación de roles ni recuperación por correo. La protección del último administrador y la administración completa de cuentas siguen pendientes. M1 debe integrar el proveedor de recursos y los casos de uso autorizados; este incremento no publica sus operaciones.

## Respuestas y pruebas

Éxitos JSON: data/meta.request_id. Errores: application/problem+json con type/title/status/detail/instance/code/request_id, sin envoltura data y sin detalles de existencia de cuentas en el login. Los mensajes de credenciales inválidas son iguales para usuario ausente, contraseña incorrecta y cuenta inactiva. No se promete tiempo de respuesta idéntico ni se expone el motivo interno de Spring Security.

Las pruebas HTTP usan cookies reales, CSRF real y PostgreSQL temporal: acceso anónimo, suplantación por cabecera, fijación de sesión, cierre/replay, credenciales inválidas, cambio de estado, cambio de contraseña, revocación administrativa y expiración absoluta. El timeout de inactividad lo aplica el contenedor; no se simula esperando 30 minutos. Las pruebas de limitación usan reloj controlado.

Referencias: [Spring Security: sesiones](https://docs.spring.io/spring-security/reference/servlet/authentication/session-management.html), [CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html), [contraseñas](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html).
