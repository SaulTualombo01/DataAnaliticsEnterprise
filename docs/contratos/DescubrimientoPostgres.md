# Descubrimiento PostgreSQL — M1-03

`DescubrimientoPostgresApi.descubrir(Solicitud)` recibe projectId, sourceId y una
lista explícita de esquemas. Descubre sus tablas ordinarias/particionadas visibles,
columnas y claves PK/UNIQUE/FK declaradas. La operación es Java, síncrona y local
al monolito. No se añaden endpoints HTTP, cola de trabajos ni motor de calidad.

El actor procede de IdentidadActualApi. Se exige m1.discovery.execute sobre la
fuente antes de leer su configuración y otra vez después de consultar el origen,
antes de publicar. No se conserva una concesión antigua durante una lectura larga.
La cuenta debe conservar permisos y contexto; no existe acceso universal administrativo.

## Alcance y lectura

Solo se consultan pg_namespace, pg_class, pg_attribute, pg_type y pg_constraint.
No se ejecutan consultas a filas empresariales, ni muestras, limpieza o inferencia
a partir de valores. La conexión es de solo lectura y usa una transacción
REPEATABLE READ. Los nombres de esquema se enlazan como parámetros SQL.

La cuenta de origen necesita CONNECT, USAGE del esquema y SELECT o REFERENCES
sobre las tablas. El fixture valida el recorrido con REFERENCES y sin SELECT
sobre filas. Solo se enumeran las tablas accesibles para esa cuenta, no se afirma
haber catalogado todas las tablas de la base. Un esquema inaccesible o inexistente
interrumpe el lote; un esquema accesible vacío registra una observación sin activos.

Se conserva namespace/nombre exactos, tipo nativo, posición, nulabilidad y
procedencia declarada. Los miembros de claves conservan el orden de conkey/confkey,
incluidas claves compuestas. Las columnas eliminadas se omiten. Un tipo sin
equivalencia segura queda UNKNOWN; JSON/JSONB no se supone objeto sin analizar datos.

Las FK hacia activos del mismo lote quedan RESOLVED, incluidas autorreferencias
y ciclos. Una referencia visible fuera del lote queda UNRESOLVED. Si la cuenta
no tiene acceso al destino queda NOT_ACCESSIBLE, sin nombres de sus columnas.
El localizador conserva la referencia declarada en la FK de origen; no concede
permiso para consultar ese destino. Los nombres con puntos/comillas se preservan
en un localizador estructurado, sin concatenarlos como SQL o interpretar su contenido.

## Configuración provisional de acceso

TC-11 aún no está implementado. AccesoPostgresConfigurado es un adaptador local
reemplazable, no una bóveda ni una administración común de secretos. La referencia
credentialRef debe ser canónica: `[a-z][a-z0-9_]{0,63}`. Para la referencia `demo`,
el operador configura en el proceso del servidor:

| Variable | Significado |
| --- | --- |
| TIC_SOURCE_DEMO_HOST | Host autorizado para esta referencia |
| TIC_SOURCE_DEMO_PORT | Puerto autorizado |
| TIC_SOURCE_DEMO_DATABASE | Base autorizada |
| TIC_SOURCE_DEMO_USER | Cuenta de metadatos |
| TIC_SOURCE_DEMO_PASSWORD | Contraseña, nunca almacenada en catálogo |
| TIC_SOURCE_DEMO_SSLMODE | verify-full por defecto; disable solo en el entorno local de pruebas |

Host/puerto/base de la fuente deben coincidir exactamente con la vinculación del
servidor. Cambiar la fuente no permite enviar una credencial a otro destino. Los
parámetros de usuario/contraseña se entregan por Properties, separados de la URL;
el nombre de base se codifica. No se usa DB_USER/DB_PASSWORD de plataforma como
credencial implícita del origen. No hay credenciales predeterminadas.

Los modos permitidos son disable, require, verify-ca y verify-full. El operador
debe mantener los certificados requeridos por pgJDBC. Las variables de entorno son
configuración del proceso; Java/JDBC pueden mantener Strings temporales en memoria.
No se promete borrado seguro de todas sus copias. No se serializan ni se registran.

## Límites y publicación

Valores técnicos iniciales configurables mediante tic.catalogo.discovery:

| Propiedad | Valor inicial |
| --- | --- |
| max-schemas / max-tables | 10 / 50 |
| max-columns por tabla / max-key-members por tabla | 2000 / 4000 |
| connect-timeout-seconds / socket-timeout-seconds | 5 / 15 |
| query-timeout-seconds / total-timeout-seconds | 10 / 60 |

El presupuesto total se comprueba entre consultas; cada consulta tiene timeout y
las operaciones de conexión/socket usan límites del driver. No constituye una
garantía de tiempo real exacto ni los umbrales RNF aprobados. Exceder un límite
interrumpe la operación y no publica una captura parcial como completa.

Primero termina y se cierra la lectura del origen. Después una transacción corta
de plataforma revalida el permiso, bloquea y compara la fuente con su
configuración/revisión original y publica el lote. Se insertan todos los activos/campos antes de
las relaciones, para conservar las FK de catálogo sin desactivarlas. Se guardan
versiones iniciales que incluyen restricciones y auditoría correlacionada. La
observación de fuente incluye esquemas, número de tablas y metadata_only, sin secretos.

La representación histórica v1 añade una colección opcional de restricciones;
las representaciones antiguas sin ella se leen como lista vacía. No se cambian
migraciones ni checksums. CatalogoApi.listarRestricciones usa m1.dictionary.read
y devuelve DTO públicos con miembros ordenados y estado de resolución.

Si la fuente cambia, se deshabilita o se revoca el permiso antes de publicar,
la publicación se rechaza. Un localizador ya catalogado produce conflicto y
revierte todo el lote, incluidas tablas nuevas previas al conflicto. No se
implementa todavía redescubrimiento/idempotencia de M1-11, detección de bajas ni
restauración. Un esquema vacío no retira activos existentes.

## Resultado y errores

Resultado contiene correlación, fuente/proyecto, instante y recibos de tablas
con IDs, versión y conteos de columnas/restricciones. Los detalles se consultan
mediante CatalogoApi con sus permisos propios. La fecha es la de publicación de
la observación; no hay lectura de filas ni fecha de extracción de contenido.

DescubrimientoFallidoException expone únicamente CONFIGURACION_ORIGEN,
ORIGEN_NO_DISPONIBLE, ALCANCE_NO_ACCESIBLE o LIMITE_EXCEDIDO. No propaga mensajes
ni causas SQL del origen. Los errores de permiso y conflictos de publicación
conservan los tipos ya documentados. No hay estados HTTP nuevos por este contrato Java.

## Verificación reproducible

Desde la raíz, con Python, JDK24, Maven y los binarios PostgreSQL18 disponibles:

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-24"
python .\tools\verificar_postgres.py
```

PG_BIN y MAVEN_CMD permiten indicar otras ubicaciones. El script crea un clúster
temporal, dos bases separadas (plataforma/origen), contraseñas aleatorias y un
lector sin SELECT empresarial. Ejecuta clean verify con postgres-it y comprueba
el JAR. Detiene los procesos al finalizar y conserva evidencia en la carpeta
temporal indicada. No se conecta al servicio PostgreSQL instalado ni a sus bases.
TIC_TEST_ARTIFACTS permite elegir el directorio padre de esa evidencia.

Las pruebas contemplan claves compuestas/cíclicas, referencias fuera de alcance,
identificadores citados, tipos desconocidos, límites, credenciales incorrectas,
destino alterado, revocación y cambios de configuración durante la lectura, esquema
vacío y rollback del lote. El fixture SQL es exclusivo de ese origen temporal.

Referencias primarias: [catálogo pg_constraint](https://www.postgresql.org/docs/18/catalog-pg-constraint.html),
[pg_attribute](https://www.postgresql.org/docs/18/catalog-pg-attribute.html) y
[conexiones y propiedades pgJDBC](https://jdbc.postgresql.org/documentation/use/).
