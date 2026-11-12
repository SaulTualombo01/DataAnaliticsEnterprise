# Módulo 2 Calidad de datos

Aplicación Spring Boot, React/TypeScript con componentes adaptados de shadcn/ui y Tailwind CSS, Hibernate y PostgreSQL 17. La estructura de carpetas original se conserva. El despliegue local trabaja exclusivamente con archivos CSV subidos por el usuario y una base independiente de Supabase. No crea activos, reglas ni evaluaciones de ejemplo.

## Iniciar con Docker en Windows

Necesitas Docker Desktop abierto, con el motor Linux funcionando. Abre PowerShell en la carpeta que contiene `compose.yaml` y ejecuta:

```powershell
.\iniciar.ps1
```

El script genera las claves y contraseñas locales una sola vez en `target/docker.env`, construye React y Java dentro de Docker, crea la base y levanta los servicios. No necesitas instalar Node, Java ni Maven para esta modalidad. La primera construcción descarga dependencias y puede tardar varios minutos.

Si PowerShell bloquea los scripts por su política de ejecución, puedes ejecutar este script concreto sin cambiar la política del equipo:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\iniciar.ps1
```

Comprueba el estado y abre la aplicación:

```powershell
docker compose --env-file target/docker.env ps
docker compose --env-file target/docker.env logs -f application
```

Cuando `application` esté `healthy`, entra en **http://localhost:8080**. Para dejar de seguir los logs usa Ctrl+C; los contenedores seguirán activos.

## Cuentas y primer recorrido

Las contraseñas se generan al preparar el entorno. Abre `target/docker.env` y consulta:

| Correo | Contraseña en el archivo | Uso |
|---|---|---|
| `calidad@example.test` | `M2_QUALITY_PASSWORD` | Reglas, evaluaciones, indicadores e incidencias. |
| `admin@example.test` | `M2_ADMIN_PASSWORD` | Cuentas y roles del proyecto. |

El administrador técnico no tiene acceso automático a los datos. Usa la cuenta de calidad para probar el módulo.

1. Inicia sesión como `calidad@example.test`.
2. Pulsa **Subir CSV**, selecciona tu archivo y revisa sus columnas y tipos.
3. Importa el archivo y crea las reglas que correspondan a sus datos.
4. En **Panel de calidad** o **Evaluaciones**, pulsa **Nueva evaluación**. Revisa el archivo y su alcance, selecciona las reglas activas y confirma sus versiones en **Revisión** antes de iniciar.
5. Espera a que termine y pulsa **Ver resultados** y **Ver incumplimientos**. El panel muestra las seis dimensiones; las que no se midieron aparecen como **Sin evaluar**.
6. En **Incidencias**, aplica filtros y registra el seguimiento de las detecciones.

El arranque crea únicamente las cuentas y el proyecto necesarios si la base es nueva. Las reglas y evaluaciones requieren una acción explícita sobre un archivo subido; consultar una pantalla no genera datos.

### Guardar y administrar tus reglas

Las reglas se guardan en PostgreSQL y pertenecen al **activo** seleccionado. La aplicación recuerda por cuenta el proyecto, activo y sección al recargar o volver a entrar desde el mismo navegador y dirección (por ejemplo, `http://localhost:8080`). Solo se recuerdan preferencias de navegación: los datos de las reglas siempre se consultan al servidor.

En **Reglas** puedes **Crear regla**, **Ver versiones**, **Modificar regla**, **Cambiar nombre**, **Desactivar** y **Activar**. Modificar recupera la configuración actual y guarda una nueva versión, conservando las anteriores. Al crear desde cualquier página de la lista se muestra la primera página y la nueva regla. El mensaje de confirmación aparece después de que el servidor acepte el guardado.

Para dejar de usar una regla, pulsa **Desactivar**. Su estado queda como **Inactiva** y deja de ofrecerse para nuevas evaluaciones; puedes reactivarla después. El historial permanece disponible para explicar las mediciones realizadas, conforme a RF-05. No se elimina definitivamente.

Reimportar el mismo contenido CSV con igual separador y tipos de columna selecciona el activo existente, con sus reglas e historial. Cambiar solo el nombre no crea una copia. Un contenido o tipos diferentes crean un conjunto independiente para conservar las evaluaciones anteriores. Las cargas antiguas que ya tenían nombres repetidos muestran un identificador corto para distinguirlas y conservan su historial. Si el navegador bloquea o borra el almacenamiento local, selecciona de nuevo el proyecto y activo; las reglas permanecen en la base de datos. `localhost` y `127.0.0.1` tienen preferencias separadas.

## Detener volver a iniciar y actualizar

```powershell
# Detener los servicios y conservar los datos.
docker compose --env-file target/docker.env down

# Levantar de nuevo sin reconstruir.
docker compose --env-file target/docker.env up -d

# Reconstruir después de cambiar el código.
docker compose --env-file target/docker.env up --build -d
```

Conserva `target/docker.env` mientras conserves el volumen `m2_database`: contiene las claves necesarias para leer datos cifrados y las credenciales de la base. No ejecutes `mvn clean` sobre esta carpeta sin respaldar ese archivo. Cambiar una contraseña de aprovisionamiento en el archivo no cambia una cuenta que ya existe en la base.

Para usar otro puerto, modifica `M2_HTTP_PORT` en `target/docker.env`, por ejemplo a `8081`, y vuelve a ejecutar `docker compose --env-file target/docker.env up -d`. La dirección será `http://localhost:8081`.

El comando `down` conserva el volumen. **`down -v` elimina la base local, los archivos subidos y todo su historial**; solo úsalo si quieres comenzar con una base vacía.

## Qué levanta Compose

| Servicio | Contenido | Acceso |
|---|---|---|
| `database` | PostgreSQL 17, esquema privado `m2`, restricciones y RLS. | Red interna de Compose; sin puerto publicado. |
| `database-migration` | Actualiza el permiso de revocación y su política por proyecto. Termina con código 0. | Red interna de Compose; sin puerto publicado. |
| `application` | Spring Boot y React compilado en el mismo JAR. | `127.0.0.1:8080` por defecto. |

El Dockerfile compila el frontend, ejecuta las pruebas Java y empaqueta el monolito. La aplicación usa un usuario PostgreSQL dedicado sin privilegios de superusuario. Compose espera a la base, aplica la migración y comprueba la salud de la aplicación en `/api/v1/health`.

El perfil `docker` usa `m2.integrations.mode=uploads`, sin catálogo sintético ni reglas precargadas, y HTTP en localhost. El perfil normal conserva la configuración de cookies seguras y las variables de conexión para el entorno real. La migración se limita a la base local de Compose.

### Actualización de la revocación de roles

Después de actualizar el código, ejecuta `powershell -NoProfile -ExecutionPolicy Bypass -File .\iniciar.ps1`. El servicio `database-migration` aplica `Backend/src/main/java/org/dataanaliticsent/Calidad/Docker/migrate-role-revocation.sql` antes de iniciar la aplicación. La migración es transaccional y repetible, sirve para volúmenes existentes y conserva las asignaciones actuales. Su estado esperado es `Exited (0)`.

La aplicación puede borrar una asignación solo con contexto de administrador del proyecto. Revocar un rol incrementa la versión de autenticación de la cuenta y registra `ROLE_REVOKED` en la misma transacción. La sesión anterior deja de autenticar. Tras volver a entrar, la cuenta conserva únicamente sus asignaciones restantes.

Los cambios de cuentas y roles coordinan los bloqueos de las cuentas implicadas y de sus proyectos en un orden común. Al desactivar una cuenta, se comprueban todos sus proyectos y se vuelve a validar el permiso del administrador tras esperar. Para desactivar o retirar una asignación administrativa debe quedar otra cuenta activa con ese rol. Una cuenta inactiva no cumple esa condición. Los bloqueos se liberan al confirmar o revertir la transacción.

Para una base usada desde el botón Run de IntelliJ, aplica una vez ese mismo archivo con `psql --set=ON_ERROR_STOP=1 --file=Backend/src/main/java/org/dataanaliticsent/Calidad/Docker/migrate-role-revocation.sql` usando la conexión de mantenimiento y el propietario del esquema `m2` de esa base. Después inicia la clase principal con el usuario habitual de la aplicación.

La regresión de esta corrección usa PostgreSQL y HTTP reales. Ejecuta `powershell -NoProfile -ExecutionPolicy Bypass -File .\Docs\Arquitectura\verificar_revocacion_roles.ps1` con Docker disponible. El script crea un contenedor y una base de pruebas separados, ejecuta Maven y elimina ese contenedor al terminar. Comprueba una actualización desde el esquema anterior, la repetición de la migración, el rechazo de operaciones sin permiso o CSRF, la conservación del último administrador activo, la invalidación de sesión y la auditoría. También ejecuta desactivaciones cruzadas en varios proyectos y una desactivación simultánea con una revocación, forzando que ambas peticiones coincidan dentro de sus transacciones. La prueba de integración se omite en Maven si no se proporciona la conexión aislada del script.

## Recuperación de contraseñas con Gmail

El envío usa Jakarta Mail mediante `spring-boot-starter-mail` y el SMTP de Gmail. La integración usa `smtp.gmail.com`, puerto 587 y STARTTLS obligatorio. Puedes enviar desde tu cuenta `@gmail.com`. La recuperación está deshabilitada por defecto hasta configurar una cuenta de correo operativa. [Servidor y puertos oficiales de Gmail](https://developers.google.com/workspace/gmail/imap/imap-smtp).

1. En tu cuenta Google, activa **Verificación en dos pasos** y abre **Contraseñas de aplicación**. Genera una contraseña de aplicación para `Calidad M2`. Algunas cuentas administradas o con Protección Avanzada restringen esta opción. [Instrucciones oficiales de Google](https://support.google.com/accounts/answer/185833?hl=es).
2. Completa estas variables en `target/docker.env`. Pon tu dirección Gmail completa tanto en `FROM` como en `USERNAME`, y guarda la contraseña de aplicación en `PASSWORD`. Si las variables ya existen, actualiza sus valores.

```dotenv
M2_RECOVERY_ENABLED=true
M2_RECOVERY_SMTP_HOST=smtp.gmail.com
M2_RECOVERY_SMTP_PORT=587
M2_RECOVERY_SMTP_SECURITY=STARTTLS
M2_RECOVERY_SMTP_AUTH=true
M2_RECOVERY_FROM=tu_correo@gmail.com
M2_RECOVERY_SMTP_USERNAME=tu_correo@gmail.com
M2_RECOVERY_SMTP_PASSWORD=TU_CONTRASENA_DE_APLICACION
```

3. Con la imagen actualizada, ejecuta `docker compose --env-file target/docker.env up -d --wait`. Si acabas de actualizar el código, ejecuta primero `iniciar.ps1` para construir la imagen. En Run de IntelliJ usa las mismas variables en la configuración de ejecución.
4. En Cuentas, crea la cuenta que probarás con un correo real y los permisos correspondientes. Las direcciones iniciales `admin@example.test` y `calidad@example.test` pertenecen al entorno local y no reciben correos reales.
5. Desde el acceso, pulsa **Olvidé mi contraseña**, escribe el correo de la cuenta y envía la solicitud. Copia el código recibido, abre **Ya tengo un código de recuperación** e introduce el código y una contraseña de 12 a 128 caracteres. Después inicia sesión con la nueva contraseña.

El código caduca a los 15 minutos y PostgreSQL guarda su hash. El registro se confirma antes del envío SMTP. Al completar el cambio se invalidan todos los códigos anteriores de esa cuenta y sus sesiones previas. Las cuentas inactivas y las identidades técnicas configuradas no pueden usar este flujo. Las solicitudes de cuentas inexistentes, inactivas y técnicas conservan la respuesta genérica.

El canal deshabilitado responde 503. Un fallo de SMTP conserva la respuesta genérica 202, invalida el código que no se pudo entregar y registra `RECOVERY_DELIVERY_FAILED` con el identificador del registro de recuperación. Para diagnosticarlo consulta `docker compose --env-file target/docker.env logs --tail 100 application`. Los correos, las claves y los códigos quedan fuera de esos registros. El envío tiene tiempos máximos de conexión, lectura y escritura de cinco segundos. Una nueva solicitud permite reintentar el envío.

También se admite `SMTPS` para un servidor con TLS directo y el puerto correspondiente. `LOCAL` solo se admite con un servidor SMTP de loopback y se reserva para pruebas. La regresión `Docs/Arquitectura/verificar_revocacion_roles.ps1` ejecuta las pruebas de recuperación con HTTP, PostgreSQL y un receptor SMTP local que nunca reenvía correos. Comprueba la entrega, el token confirmado antes de enviar, la caducidad, el uso único, los códigos concurrentes, la invalidación de sesiones, las cuentas excluidas, CSRF, fallos de envío y STARTTLS obligatorio. [Documentación de correo de Spring Boot](https://docs.spring.io/spring-boot/3.4/reference/io/email.html).

## Desarrollo sin reconstruir Docker

### Levantar todo desde IntelliJ IDEA

1. Abre esta carpeta raíz con **File > Open**. Mantén la estructura de carpetas existente. Si IntelliJ pregunta por Maven, importa el `pom.xml`, que incluye las pruebas y los recursos de la aplicación.
2. Abre Docker Desktop y espera a que su motor esté listo. Comprueba `docker version`: debe mostrar cliente y servidor.
3. En IntelliJ, abre **View > Tool Windows > Terminal** (Alt+F12 en Windows). Utiliza una terminal PowerShell situada en la carpeta que contiene `compose.yaml`.
4. Ejecuta el arranque:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\iniciar.ps1
```

El comando construye React/TypeScript y Spring Boot, ejecuta las pruebas Java y levanta PostgreSQL y la aplicación. La aplicación Java sirve también el frontend compilado. En esta modalidad no hay un segundo servidor React que debas iniciar.

5. Confirma el estado y abre [la aplicación local](http://localhost:8080):

```powershell
docker compose --env-file target/docker.env ps
```

`database` y `application` deben estar `healthy`; `database-migration` termina con código 0. Entra con `calidad@example.test` y la contraseña de `M2_QUALITY_PASSWORD` en `target/docker.env`. Usa **Panel de calidad > Nueva evaluación** para revisar un archivo y sus reglas.

Para siguientes arranques, si no cambiaste código, basta con `docker compose --env-file target/docker.env up -d --wait`. Si cambiaste código, usa `up --build -d --wait`. Para detenerlo usa `down`, conservando los volúmenes.

Este recorrido usa IntelliJ como editor y terminal; Java se ejecuta dentro de Docker. El botón Run de la clase `DataAnaliticsEnterprise` es una modalidad distinta: requiere una base accesible desde Windows, las variables privadas de conexión y cifrado, la identidad del worker y la carpeta de CSV correspondiente. El Compose actual mantiene PostgreSQL y los archivos dentro de sus volúmenes, por lo que pulsar únicamente Run no sustituye este arranque. La clase principal correcta es `org.dataanaliticsent.DataAnaliticsEnterprise`, en `Backend/src/main/java/org/dataanaliticsent/DataAnaliticsEnterprise.java`.

Referencia de IntelliJ: [terminal y Maven](https://blog.jetbrains.com/idea/2021/07/using-maven-in-intellij-idea/), [Docker Compose](https://www.jetbrains.com/help/idea/docker-compose.html).

Para trabajar en el código puedes usar Java 21 o superior, Maven y Node 24. Compila y prueba:

```powershell
npm --prefix Frontend ci
npm --prefix Frontend run build
mvn -f pom.xml verify
```

`pom.xml` concentra dependencias, pruebas y recursos del frontend en un único descriptor Maven. El arranque principal compatible con Maven está en `org.dataanaliticsent.DataAnaliticsEnterprise`.

Conecta el backend a una base configurada mediante las variables de `application.yml`. Para el servidor Vite de desarrollo:

```powershell
npm --prefix Frontend run dev
```

Vite enviará `/api` al backend local en el puerto 8080. No conecta directamente a PostgreSQL ni usa claves privadas en el navegador.

## Dimensiones del panel

Los componentes compartidos de `Frontend/ui.tsx` usan patrones de shadcn/ui: botones con variantes, campos, selectores nativos, tarjetas y tablas. Tailwind compila las utilidades y `styles.css` centraliza los colores corporativos, tipografía y ajustes del módulo. Bootstrap se retiró de las dependencias y del código. No se utiliza un CDN ni otro servidor de estilos.

Las tarjetas muestran medida, estado y fecha; **Ver detalle** abre condiciones, cobertura, umbrales, conteos y versiones. Si una dimensión contiene varias reglas se muestran sus estados sin promediar porcentajes. Las comprobaciones del navegador incluyen teclado, accesibilidad y contraste de hover de las acciones del panel.

El rediseño incorpora una barra lateral, cabecera de proyecto, acceso dividido y tarjetas métricas con indicadores circulares. Motion anima cambios de sección, entradas escalonadas y el trazado de una medición real. Se añaden skeleton de carga y feedback al guardar. La preferencia de movimiento reducido desactiva esas animaciones. No se envían archivos a servicios de diseño ni se cargan fuentes, scripts o imágenes desde CDN. La integración y las referencias de Kokonut UI están descritas en DESIGN.md.

El panel conserva la última evaluación completada de cada dimensión del archivo. Si evalúas unicidad y después validez, ambas quedan visibles, cada una con su fecha y enlace al análisis de origen. Volver a evaluar una dimensión reemplaza sus indicadores mostrados por los de esa nueva ejecución completada; las demás dimensiones se conservan. El historial mantiene todas las ejecuciones.

Las ejecuciones pendientes o fallidas no borran mediciones completadas. Una medición sin denominador sigue mostrando **No evaluable**. El estado de la última ejecución aparece separado del resumen histórico de dimensiones; los resultados pueden corresponder a fechas distintas. **Actualizar panel** vuelve a consultar sin crear evaluaciones.

## Archivos excluidos de Git

`.gitignore` excluye dependencias, compilados, configuración privada, cachés, registros, volcados y respaldos. Los CSV locales deben guardarse en `uploads/`, `csv-inputs/`, `data/` o `target/`, directorios excluidos; los CSV procesados por Docker permanecen en su volumen. Se conservan código, pruebas, contratos, esquemas SQL, Docker y `Frontend/package-lock.json`. Los ejemplos `.env.example` deben contener únicamente valores de ejemplo, nunca claves reales.

Las exclusiones no eliminan archivos del equipo ni retiran automáticamente archivos ya registrados por Git. Antes de publicar revisa lo preparado en IntelliJ/Git y evita agregar datos de clientes o credenciales en otras carpetas.

## Problemas de arranque

- **`Driver org.postgresql.Driver claims to not accept jdbcUrl, ${M2_DB_JDBC_URL}` al pulsar Run:** la JVM de IntelliJ no recibió la configuración de conexión. El mensaje posterior sobre el dialecto de Hibernate es consecuencia de esa URL sin resolver. Para el entorno actual ejecuta `powershell -NoProfile -ExecutionPolicy Bypass -File .\iniciar.ps1` desde la terminal de IntelliJ. Compose proporciona las variables dentro del contenedor; no las exporta al botón Run. Activar únicamente `local` tampoco configura la conexión. Un arranque Java fuera de Docker requiere PostgreSQL accesible desde Windows, credenciales, claves de cifrado, identidad del worker y acceso a los CSV correspondientes; no uses `database:5432` desde Windows, pues es un nombre interno de Compose.
- **Docker no responde:** abre Docker Desktop y comprueba `docker version`. Debe mostrar tanto cliente como servidor.
- **Error `dockerInference`:** es un fallo de Docker Desktop previo al Compose. El procedimiento y el respaldo realizado en este equipo se describen en `Docs/Arquitectura/ESTADO_DESARROLLO.md`. No uses Factory Reset o eliminación de volúmenes para intentar corregirlo.
- **Puerto 8080 ocupado:** cambia `M2_HTTP_PORT` en `target/docker.env`.
- **Servicio que se reinicia:** consulta `docker compose --env-file target/docker.env logs --tail 100 application database`.
- **No se puede recuperar contraseña:** el proveedor de correo aún debe conectarse. La API informa su indisponibilidad; no envía correos simulados.
- **No aparecen activos:** sube un CSV. El catálogo ya no incluye clientes, pedidos ni entradas de ejemplo. Los conectores directos M1/TC13 permanecen pendientes; el flujo actual utiliza tus archivos subidos.

El alcance implementado, las pruebas y las dependencias pendientes se detallan en `Docs/Arquitectura/ESTADO_DESARROLLO.md`.

## Documentación Swagger / OpenAPI

Con la aplicación levantada, abre [Swagger UI](http://localhost:8080/swagger-ui/index.html). La especificación generada está en [OpenAPI JSON](http://localhost:8080/v3/api-docs). Incluye rutas, DTO de entrada, validaciones, permisos, cabeceras y errores. El archivo de `Docs/Contratos` conserva el contrato de referencia; Swagger se genera desde el código que se está ejecutando.

Para probar consultas, inicia sesión en [la aplicación](http://localhost:8080) y abre Swagger en el mismo navegador y origen. El navegador envía la cookie de sesión; no hay un token JWT que debas inventar o pegar en Authorize.

Para una escritura, ejecuta primero `GET /api/v1/session/csrf` en Swagger y copia `data.token` al campo obligatorio `X-CSRF-TOKEN` de la operación. Renueva el token después de iniciar o cerrar sesión. Las modificaciones que lo requieran también necesitan `If-Match` con el ETag actual y las evaluaciones necesitan un UUID `Idempotency-Key`. Swagger ejecuta operaciones reales: los permisos, las revisiones y la persistencia son los mismos de la aplicación.

Swagger está habilitado por defecto en los perfiles `local` y `docker`; el perfil normal lo mantiene deshabilitado. `M2_API_DOCS_ENABLED=false` permite deshabilitarlo también en desarrollo. Se usa Spring Boot 3.5.16 con springdoc 2.8.14. La verificación de entrega comprueba Swagger y OpenAPI junto al flujo de evaluación.

Desde la raíz del proyecto puedes comprobar la calidad de código y compilar con:

```powershell
npm --prefix Frontend run typecheck
npm --prefix Frontend run build
mvn verify
```

`pom.xml` es ahora el único descriptor Maven. El informe de refactorización está en `Docs/Arquitectura/REFACTORIZACION_Y_SWAGGER.md`.

## Subir un CSV y localizar los incumplimientos

1. Entra como responsable de calidad y pulsa **Subir CSV**, junto al selector de activo.
2. Selecciona un archivo `.csv` guardado como **UTF-8**, con nombres de columna en la primera fila. También puedes exportar una tabla de tu base de datos a este formato.
3. Pulsa **Revisar archivo**. Se muestran el número de registros, los encabezados y hasta cinco filas de vista previa. Puedes elegir coma, punto y coma o tabulación si la detección automática no basta.
4. Mantén identificadores, cédulas, correos y fechas como **Texto**. Elige **Número** para cantidades que evaluarás mediante un rango; los decimales usan punto. Los valores numéricos inválidos permanecen disponibles para detectar el incumplimiento.
5. Pulsa **Importar CSV**. El conjunto se selecciona como activo y se abre **Reglas**.
6. Crea las reglas para las columnas que quieras evaluar: completitud, formato de correo, clave única, valores permitidos, rango numérico o comparación entre campos. No se imponen reglas de negocio automáticamente.
7. Abre **Nueva evaluación**, revisa **Archivo y alcance**, selecciona las **Reglas** y confirma en **Revisión** con **Iniciar evaluación**. Si una regla cambió, actualiza su selección antes de enviar de nuevo.
8. En los resultados, pulsa **Ver incumplimientos**. La tabla indica **regla, dimensión, campo y fila original del CSV**, y filtra las incidencias de esa evaluación.

La fila 1 es el encabezado. Si una celda contiene saltos de línea, se conserva la línea física donde empieza cada registro para que puedas localizarlo en el archivo. Los vacíos, duplicados, espacios e identificadores con ceros iniciales se conservan. El análisis no modifica el origen.

### Consultar indicadores, historial y evidencia

**Panel de calidad** presenta la última ejecución del activo y las seis dimensiones. Cada medición muestra su condición, versión, conteos, cobertura y umbral. **Sin evaluar** significa que esa dimensión no se incluyó; **No evaluable** significa que no hubo datos evaluables para calcular su porcentaje. Un porcentaje cero es una medición válida cuando sí existe denominador. No se promedian reglas distintas.

El estado técnico **COMPLETED / Completada** indica que el trabajo terminó. La calificación **Cumple reglas seleccionadas** depende de los umbrales configurados y solo alcanza esas reglas; no declara que todo el activo esté aprobado. El umbral se escribe como porcentaje: `98` significa 98 %, mientras que `0,98` significa 0,98 %.

En **Evaluaciones**, combina fechas inicial/final y estado de ejecución con el activo seleccionado; ambos días se incluyen en hora de Ecuador. Los resultados históricos conservan las versiones usadas. Si la entrada caducó, aparece una advertencia: el resultado permanece, aunque quizá ya no se pueda reproducir ni abrir su evidencia.

En **Incidencias**, **Ver evidencia** consulta únicamente los campos implicados de la fila original y requiere permiso de calidad sobre ese activo. La lista no descarga esos valores. Cerrar el diálogo los retira de la vista; la consulta queda auditada. Si la captura ya no está disponible se muestra la razón, sin reemplazarla por datos actuales.

Límites iniciales: 10 MB, 10.000 registros, 128 columnas y 16.384 caracteres por celda. Un CSV con estructura incorrecta se rechaza; los valores que incumplen reglas de calidad se evalúan y generan incidencias. Un archivo con solo encabezados puede importarse y produce una evaluación sin datos evaluables.

Los CSV de ejemplo del desarrollo se retiraron. Puedes usar tus archivos o exportar tablas de tu base de datos a CSV UTF-8 y definir las reglas apropiadas para sus columnas.

Los CSV originales y sus manifiestos se guardan cifrados en el volumen **m2_inputs**. Las referencias, reglas y resultados permanecen en **m2_database**. Conserva ambos volúmenes y `target/docker.env`; `down` los mantiene y `down -v` los elimina. En el perfil de desarrollo sin Docker, el almacenamiento es `target/csv-inputs`, que debes respaldar antes de usar `mvn clean`.

La carga es un adaptador local de entrada dentro del mismo puerto de evaluación. La integración del catálogo y las lecturas compartidas con M1/TC13 sigue pendiente de sus contratos reales. Exactitud y relaciones con otras entradas requieren una referencia confiable configurada; subir un CSV por sí solo no demuestra exactitud.

## Consultar todas las incidencias y aplicar filtros

1. Abre **Incidencias** desde la navegación. Se muestran todas las detecciones que tu cuenta puede consultar en el proyecto, con su historial de evaluaciones.
2. Selecciona **Activo**, **Estado de incidencia**, **Gravedad** o **Dimensión**. También puedes buscar por nombre de regla, campo o fila.
3. Pulsa **Aplicar filtros**. El servidor filtra antes de paginar; el contador muestra los registros de la página y el total que coincide.
4. Usa **Anterior/Siguiente** para recorrer el resultado. **Limpiar filtros** vuelve a todos los activos autorizados.
5. Cada fila identifica activo, evaluación, fecha, regla, campo, fila del archivo, gravedad y estado. **Seguimiento** permite registrar una transición y consultar los comentarios con fecha.
6. Si llegas desde **Ver incumplimientos** de una evaluación, queda filtrada esa ejecución. Pulsa **Ver todas las incidencias** para salir de ese filtro.

Una misma fila puede aparecer varias veces si incumple varias reglas o se evaluó en ejecuciones distintas. Son detecciones históricas diferentes. Resolver una incidencia no cambia la medición original. La vista global respeta los permisos de cada activo; no concede acceso adicional.

Para comprobar el entorno de uso diario sin generar datos, ejecuta `Docs/Arquitectura/verificar_solo_archivos.py`. Los scripts `verificar_api.py`, `verificar_csv.py` y `verificar_catalogo_incidencias.py` que generan casos ficticios exigen un entorno aislado: `M2_TEST_ISOLATED=1`, `M2_TEST_ENV_FILE`, `M2_TEST_BASE_URL` y `M2_TEST_PROJECT_ID` separados. Bloquean su ejecución sobre la base y el proyecto de uso diario. Las pruebas Java usan datos en memoria/directorios temporales y no precargan el catálogo.

## Seguridad, respaldos y controles de entrega

Consulta [Seguridad y operación](Docs/Arquitectura/SEGURIDAD_Y_OPERACION.md) para la retención de CSV a 30 días, rotación AES, perfil HTTPS, respaldo cifrado, restauración aislada, límites y pruebas de entrega. Las cuentas y los datos diarios se conservan. La publicación requiere también activar el control obligatorio en GitHub y resolver los hallazgos del análisis de seguridad.

## Revisión crítica y recursos de diseño

El informe vigente de puntos buenos, hallazgos priorizados y mejoras está en [Revisión QA y seguridad](Docs/Arquitectura/REVISION_QA_SEGURIDAD_FRONTEND.md). DESIGN.md y UX-CONTRACT.md describen la interfaz sin Bootstrap. Ponytail se integra como ayuda de desarrollo de Codex, fuera de las dependencias de la aplicación; su alcance y limitaciones se documentan junto al informe.
