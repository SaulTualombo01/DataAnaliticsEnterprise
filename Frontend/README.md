# Interfaz compartida de DataAnalitics Enterprise

Contenedor React/TypeScript del monolito modular, entrega Q1 (TC-03/TC-05).
El frontend se construye y sirve por separado; no es otro backend y no agrega
una autenticación distinta de la sesión del tronco común.

## Preparar el entorno

Node 22.12 o superior compatible y npm. Dependencias fijadas en package-lock.json.
Se verificó con Node 22.14.0 y npm 10.9.2 en Windows. Desde esta carpeta:

```powershell
npm ci
npm run dev:demo
```

Abrir http://127.0.0.1:5173. Vite escucha únicamente en loopback. La demostración
empieza con una identidad ficticia y mantiene el aviso visible. No escribir
credenciales reales en una demostración.

## Modos independientes

| Comando | Datos y destino |
| --- | --- |
| `npm run dev:demo` | Identidad, proyectos y catálogo sintéticos en memoria. Sin llamadas a /api. |
| `npm run dev` | API real en /api a través del proxy. No utiliza mocks cuando la API falla. |
| `npm run build` | Bundle real en dist, sin fixtures de demostración. |
| `npm run build:demo` | Bundle explícitamente de demostración en dist-demo. |

No hay cuentas de servidor, contraseñas predeterminadas ni permisos reales
sembrados por el frontend. La selección de proyectos, escenarios y rol visible
de la demostración no demuestra que estén implementados administrativamente.

## Sesión y consultas reales

1. Iniciar el backend con una base propia preparada según el README raíz.
   En HTTP local configurar `SESSION_COOKIE_SECURE=false` en ese proceso.
2. Opcionalmente copiar `.env.example` a `.env.local` y cambiar API_PROXY_TARGET
   para apuntar al backend propio. No añadir secretos a variables VITE_.
3. Ejecutar `npm run dev`. La app consulta /api/v1/auth/me. Si no hay sesión,
   muestra el formulario de acceso de la plataforma.
4. Login usa GET CSRF, POST formulario email/password, GET CSRF renovado y GET me.
   Logout obtiene CSRF y usa el endpoint existente. Cookie HttpOnly administrada
   por el navegador; no se guardan contraseñas, tokens o cookies en web storage.
5. Abrir un activo con IDs de proyecto/activo. Para consultar catálogo real, el
   backend debe incluir las operaciones de M1-04 (la rama TC sola no las contiene).
   Cada solicitud exige los permisos y alcances del servidor. Escribir IDs no
   incorpora al usuario al proyecto ni le concede acceso.

La lista/selección real de contextos y la administración de cuentas, proyectos,
membresías, roles e invitaciones corresponden a Q2. /me no devuelve roles y la
interfaz no los inventa para cuentas reales. La página no asume acceso universal
del administrador ni elimina controles de Spring Security.

En producción servir dist y /api bajo un mismo origen HTTPS, mediante el
despliegue que acuerde el equipo. Vite dev/preview no son servidores de producción.
Este incremento no cambia el backend para servir estáticos ni habilita CORS.

## Estructura y contribución de otros módulos

- `src/shell`: layout, sesión, navegación y selección de recurso.
- `src/modules/registry.ts`: etiquetas y registro de áreas M1–M4.
- `src/modules/catalogo`: lector de activo/diccionario mediante el gateway.
- `src/shared`: DTOs y cliente HTTP común.
- `src/mocks`: adaptador de demostración y fixtures identificados.

Para integrar otra vista, añadir su carpeta bajo modules y conectar el registro
con su componente, conservando sus estados dentro del módulo. Evitar que un
componente importe repositorios o estados privados de otro. M2/M3/M4 tienen
solo un espacio de navegación reservado; no hay resultados ni KPIs inventados.

El lector M1 fija la versión inicial mientras pagina y vuelve a comprobar acceso
al activo. Si la versión pasó a ser histórica, el backend exigirá el permiso
de historial. Cambiar de contexto cancela y descarta peticiones anteriores.

## Verificación

```powershell
npm test
npm run build
npm run build:demo
npx playwright install chromium
npm run test:e2e
```

Las pruebas de navegador levantan dos servidores locales en 4173/4174 y los
detienen al acabar. El modo real se prueba con respuestas HTTP interceptadas:
comprueba el comportamiento del cliente, no sustituye las pruebas PostgreSQL,
cookie/CSRF reales y RBAC del backend. No envía credenciales a un servidor real.
Para usar un Chromium ya instalado, definir opcionalmente
PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH solo en el proceso local de pruebas.

El workflow ../.github/workflows/ci.yml ejecuta Maven/JDK24 (release23), pruebas
frontend, builds real/demo y Chromium en Linux. La fase Maven por defecto excluye
las pruebas etiquetadas postgres; este pipeline mínimo no demuestra integración
con PostgreSQL ni entrega de otros módulos. El workflow comenzará a ejecutarse
en GitHub después de publicar los archivos; no se ejecutó remotamente al crearlo.

El mismo workflow incluye validación de contratos y pruebas del mock TC13,
documentado en ../docs/contratos/tc13/TC13.md. Este mock no participa en la sesión
real del frontend ni demuestra autorización de contenido.
