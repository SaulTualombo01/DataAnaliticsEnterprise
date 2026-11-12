# Seguridad y operación del módulo 2

## Qué se implementó

Se mantiene Spring Boot, React/TypeScript, Bootstrap, Hibernate y PostgreSQL. Las carpetas del código conservan su ubicación. Las pruebas que crean datos trabajan con proyectos Docker, cuentas, claves y volúmenes temporales, separados del proyecto de uso diario.

| Control | Implementación | Configuración de despliegue pendiente |
|---|---|---|
| Acceso revocable | Revocación y administración concurrente, RLS con `m2_app`, recuperación SMTP con uso único y cierre de sesiones. | Habilitar el canal Gmail con la cuenta y contraseña de aplicación del operador. Las pruebas de SMTP son locales y no envían correos externos. |
| Protección y recuperación | Perfil HTTPS, secretos por archivos externos, anillo de claves AES, retención de CSV, respaldo cifrado y restauración aislada. | Certificado real, directorio externo de secretos, custodia y ciclo de vida de respaldos. |
| Control previo a entregar | Compilación, pruebas PostgreSQL/HTTP/SMTP/TLS, navegador, restauración y carga; análisis de dependencias, secretos e imagen. | Subir el workflow y exigir su resultado en las reglas de protección de la rama de GitHub. |
| Límites y alertas | Cuotas de cola, presupuesto del motor, límites de archivos/disco, sesiones anónimas, recursos de contenedor y registros operativos. | Enviar los registros a un destino operativo y configurar quién recibe las alertas. |

## Retención acordada: 30 días

El plazo empieza en `acquired_at`, al importar el CSV. Reimportar un archivo idéntico vigente reutiliza su activo y no reinicia el plazo. Al caducar deja de poder evaluarse o entregar evidencia, aunque la limpieza física todavía no se haya ejecutado. La regla también alcanza CSV anteriores que no tenían `expires_at`.

La limpieza intenta retirar hasta 100 contenidos por proyecto cada minuto, con los permisos de la identidad técnica. Registra `CSV_CONTENT_PURGED` y puede reintentarse si falla el almacenamiento. Conserva el manifiesto cifrado con nombre, columnas, tipos y referencias; ese manifiesto no contiene celdas. Conserva reglas, versiones, evaluaciones, indicadores e incidencias. Una entrada caducada continúa siendo identificable en el catálogo y el historial.

`M2_CSV_RETENTION_DAYS` tiene valor predeterminado `30`. Cambiarlo requiere una decisión de conservación; no debe utilizarse para simular que se recuperó un archivo eliminado. Los respaldos son copias independientes: deben tener retención y acceso restringido en su almacenamiento. Restaurar una copia antigua no habilita la evidencia cuyo plazo ya venció.

## Claves externas y rotación

En desarrollo se conserva la configuración privada existente de `target/docker.env`. Para despliegue estable utiliza un gestor de secretos o archivos con acceso restringido fuera del repositorio. No guardes claves reales en los ejemplos ni ejecutes `mvn clean` sobre una configuración que aún no hayas custodiado fuera de `target/`.

La rotación de escritura de AES funciona así:

1. Custodia la clave actual y su identificador antes del cambio.
2. Prepara un archivo privado externo, por ejemplo `C:/M2Private/keys.properties`, con la clave anterior:

   ```properties
   m2.security.previous-data-keys.local-v1=BASE64_DE_LA_CLAVE_ANTERIOR
   ```

3. Genera una clave AES nueva de 32 bytes. Configura `M2_DATA_KEY_ID=local-v2` y su valor en `M2_DATA_KEY_BASE64`. Mantén el archivo anterior montado como solo lectura y establece `SPRING_CONFIG_ADDITIONAL_LOCATION=file:/run/m2/keys.properties` en la aplicación. Ambas modificaciones deben aplicarse en el mismo reinicio.
4. Las escrituras nuevas usan `local-v2`. Los correos, comentarios y CSV antiguos siguen descifrándose con `local-v1`.
5. Ensaya la restauración con ambas claves. Al respaldar esta modalidad pasa también `-KeyConfigFile C:/M2Private/keys.properties`.

El identificador activo debe ser distinto de los anteriores y admitir solo letras, números, punto, guion o guion bajo. No retires claves mientras existan registros o respaldos que dependan de ellas. Este avance no hace un recifrado masivo de registros existentes. La clave HMAC de búsqueda de correo requiere una migración y reindexación propias; sustituirla directamente dejaría de localizar cuentas existentes.

## HTTPS de producción

El perfil `production` exige un directorio de secretos existente mediante `configtree`, TLS 1.2/1.3 y un almacén PKCS12. El arranque falla si se desactiva TLS o se degrada la cookie de sesión. Usa `__Host-M2SESSION`, `Secure`, `HttpOnly`, ruta `/` y sin dominio. Swagger y el aprovisionamiento inicial están deshabilitados.

En el directorio externo indicado por `M2_SECRETS_DIRECTORY` cada archivo contiene un valor. Los nombres necesarios incluyen:

```text
spring.datasource.url
spring.datasource.username
spring.datasource.password
m2.security.data-key-id
m2.security.data-key-base64
m2.security.email-lookup-key-base64
server.ssl.key-store-password
m2.csv.directory
m2.worker.user-id
```

Configura `M2_TLS_KEY_STORE` con la ubicación del PKCS12 y activa `SPRING_PROFILES_ACTIVE=production` en un despliegue aprovisionado. Los perfiles `local` y `docker` continúan reservados al desarrollo en loopback. El ejemplo Compose existente no es un despliegue público de producción.

Una comprobación real debe confiar en la CA y verificar el nombre del certificado. No uses opciones que omitan su validación. Si empleas el comprobador del contenedor, establece `M2_HEALTHCHECK_URL` a una URL HTTPS alcanzable cuyo nombre coincida con el certificado. La prueba automatizada usa una CA/certificado temporal confiado únicamente por el cliente del ensayo; no demuestra que el dominio de producción tenga un certificado instalado.

## Respaldar el Compose local

Construye primero la imagen actualizada con `iniciar.ps1`. Crea previamente un destino protegido para el archivo cifrado. Desde la raíz:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File Docs/Arquitectura/respaldar-datos.ps1 -Destination 'C:/M2Backups/calidad-2026-11-09.m2backup'
```

Solicita una contraseña de al menos 20 caracteres. Usa una contraseña aleatoria de un gestor y consérvala por separado. La aplicación se detiene brevemente para capturar juntos `pg_dump`, el volumen de CSV y la configuración de claves; vuelve a arrancar incluso si falla el respaldo. El archivo final usa AES-256-GCM autenticado por bloques y una clave derivada de la contraseña. Los temporales tienen permisos restringidos y se eliminan al terminar. Se necesita espacio libre para los temporales y el archivo final.

Los scripts están diseñados para el Compose local de este repositorio. Un despliegue con más secretos, certificados o almacenamiento externo necesita incorporarlos al procedimiento de custodia. Para un anillo AES externo es obligatorio proporcionar `-KeyConfigFile`. El archivo adicional de propiedades debe contener el conjunto de claves necesario para leer todos los datos respaldados.

## Restaurar sin sobrescribir la base diaria

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File Docs/Arquitectura/restaurar-datos.ps1 -BackupFile 'C:/M2Backups/calidad-2026-11-09.m2backup' -ConfigurationDirectory 'C:/M2Private/restauracion-2026-11-09' -ProjectName 'm2-restore-ensayo' -HttpPort 8088
```

El directorio de configuración y el proyecto deben ser nuevos. La restauración rechaza un proyecto con contenedores o volúmenes existentes. Descifra y verifica el respaldo antes de restaurar PostgreSQL y los CSV; conserva la configuración privada necesaria para futuros arranques. Descarta sesiones anteriores, incrementa la versión de autenticación y consume los códigos de recuperación restaurados. Las cuentas conservan sus contraseñas y permisos.

Comprueba resultados, versiones e incidencias y solicita evidencia con una cuenta autorizada. Comprueba también el rechazo con otra cuenta. El administrador técnico puede recibir `403` o `404`: RLS puede ocultar la existencia del recurso antes de que llegue al servicio de evidencia. Las utilidades no promueven automáticamente la restauración al proyecto de uso diario.

## Límites iniciales y observación

| Recurso | Valor inicial |
|---|---|
| CSV | 10 MB, 10.000 filas; se conservan límites de columnas y celdas del parser. |
| Cola | 50 pendientes por proyecto y 10 por solicitante, incluyendo las que están ejecutándose. |
| Motor | 500.000 unidades de trabajo —filas por campos por reglas— y 20.000 incidencias por evaluación. |
| Almacenamiento CSV | 1.000.000.000 bytes cifrados y una reserva de 100.000.000 bytes libres. |
| Sesiones anónimas nuevas | 20 por dirección/minuto, 1.000 globales/minuto y caducidad de inactividad de cinco minutos. |
| Aplicación Docker | 1 GiB de memoria, 1 CPU, 256 procesos/hilos; raíz de solo lectura y `/tmp` temporal de 64 MiB. |
| HTTP | 64 hilos, 256 conexiones y cola de aceptación de 64. |

Los presupuestos que se superan rechazan la solicitud o cierran la evaluación como fallida, sin publicar métricas truncadas. La cuota de proyecto se cuenta en PostgreSQL incluso para gestores limitados a un activo; solo se exponen contadores agregados y se valida el contexto autorizado. La migración es repetible y se ejecuta antes de arrancar la aplicación.

Los límites de creación de sesión y la reserva de archivos se coordinan dentro de una instancia. El Compose sigue siendo un monolito de una instancia. Varias réplicas necesitan coordinación compartida de esos controles.

`OPERATIONS` registra heap usado/máximo, bytes de CSV, espacio libre y sesiones anónimas; `OPERATIONS_QUEUE` mide la cola por proyecto. Se avisa al alcanzar el 85 % de heap/almacenamiento, el 80 % de la cola, la reserva de disco o 4.000 sesiones anónimas. Son señales en logs; enviar avisos a una persona requiere configurar el recolector externo. Los rechazos de seguridad registran código, estado y UUID de correlación, sin contraseñas, tokens ni valores CSV.

## Control antes de publicar

```powershell
npm --prefix Frontend ci
npm --prefix Frontend exec -- playwright install chromium
powershell -NoProfile -ExecutionPolicy Bypass -File Docs/Arquitectura/verificar_entrega.ps1
```

La verificación completa exige Docker, Node, Java 21+, Maven y Python. Crea y elimina solo entornos ficticios. Incluye PostgreSQL/HTTP/SMTP/TLS, carga CSV, navegador, respaldo cifrado, restauración y cinco usuarios simultáneos sobre 10.000 filas. Los informes se guardan en `target/`, excluido de Git. `-SkipBuild` sirve para repetir el recorrido después de haber ejecutado las pruebas Java y compilado; no reemplaza esas comprobaciones.

Después comprueba la imagen que produjo el ensayo:

```powershell
$delivery = Get-Content target/security-delivery-summary.json -Raw | ConvertFrom-Json
powershell -NoProfile -ExecutionPolicy Bypass -File Docs/Arquitectura/verificar_seguridad.ps1 -ApplicationImage $delivery.image
```

Trivy tiene versión y digest fijados. Revisa dependencias npm —incluidas las de desarrollo—, secretos del código y vulnerabilidades/secretos de la imagen con el JAR real. Los hallazgos altos/críticos bloquean el control; los errores de descarga o análisis también lo bloquean. No se imprimen coincidencias de secretos. Los informes detallados quedan privados en `target/`. La revisión del código fuente excluye temporales, dependencias instaladas, respaldos y Git; las dependencias Java se inspeccionan en el artefacto compilado.

El workflow `M2 quality gate` usa acciones fijadas por commit, permisos de lectura y entornos temporales sin secretos reales. En GitHub activa **Settings → Rules → Rulesets** para la rama de entrega, exige pull request y agrega `m2-quality-gate` como comprobación requerida. La configuración local del workflow no cambia por sí sola las reglas remotas del repositorio. Tampoco publica ni despliega automáticamente.

Spring Boot se actualizó a 3.5.16, Jackson a 2.21.7, el controlador PostgreSQL a 42.7.13 y Bouncy Castle a 1.85; Tomcat se fijó en 10.1.61; una prueba comprueba la dependencia efectiva. Esto corrige el componente señalado en CVE-2025-48976. No demuestra ausencia de otras vulnerabilidades ni resuelve por sí solo los avisos que permanecen en Spring Framework 6.2: los informes de dependencias y el análisis de aplicabilidad guían las actualizaciones siguientes.

## Alcance de la verificación

ASVS se usa como referencia para autenticación, autorización, protección de datos y registro. SSDF orienta los controles de entrega y la conservación de evidencia de pruebas. No se declara una certificación ASVS ni conformidad completa con SSDF. Los resultados locales de carga describen este equipo y este escenario; no son un SLA ni demuestran capacidad para todas las combinaciones de reglas y datos.

Fuentes: [Apache Tomcat](https://tomcat.apache.org/security-10.html), [Spring Boot: configuración externa](https://docs.spring.io/spring-boot/3.4/reference/features/external-config.html), [Playwright](https://playwright.dev/docs/intro), [Trivy](https://trivy.dev/docs/latest/), [OWASP ASVS](https://github.com/OWASP/ASVS), [NIST SSDF](https://csrc.nist.gov/pubs/sp/800/218/final).

## Hallazgos que siguen bloqueando la publicación

La revisión de la imagen inicial encontró avisos altos/críticos en Jackson, Bouncy Castle, PostgreSQL, Spring y paquetes del sistema. Se actualizaron los componentes compatibles y los paquetes APT de la imagen. El control conserva los hallazgos restantes y no utiliza listas de exclusión para ocultarlos.

Los avisos [CVE-2026-47884](https://spring.io/security/cve-2026-47884/) y [CVE-2026-47890](https://spring.io/security/cve-2026-47890/) requieren revisar el uso de vistas XSLT y fragmentos enviados por SSE, respectivamente. Los avisos oficiales sitúan el parche de la rama 6.2.20 en soporte comercial; la corrección pública está en Spring Framework 7.0.9. El módulo actual usa controladores REST, React estático y consulta periódica; no se observó una integración XSLT/SSE. Esa observación no se ha convertido en una excepción automática del escáner. Una migración compatible a Spring Boot 4 o una evaluación formal de no aplicabilidad debe cerrar esta decisión antes de publicar.

## Resultado local de este avance

La regresión aprobó 103 pruebas Java, 17 comprobaciones Swagger/OpenAPI, 17 de CSV, el recorrido Playwright y la restauración comparada. La carga acordada de cinco gestores y 10.000 filas completó cinco evaluaciones en 8,521 segundos; el heap observado alcanzó 102,94 MiB bajo el límite de 1 GiB del contenedor. Son mediciones locales de referencia.

El análisis final no detectó secretos ni avisos npm altos/críticos; quedaron dos avisos críticos en Spring MVC identificados arriba. El control de entrega sigue devolviendo error. No se publicaron artefactos ni se activaron reglas remotas de GitHub. Para aplicar el código en el Compose de desarrollo ejecuta `iniciar.ps1`; la verificación aislada conserva los contenedores diarios con su imagen anterior.
