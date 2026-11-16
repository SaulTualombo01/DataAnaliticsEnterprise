# Agrupación de M0 en TroncoComun

Refactor del 16 de noviembre de 2026, rama modulo0_troncoComun.

La raíz de fuentes Maven conserva su ruta Backend/src/main/java. Todos los
paquetes Java existentes de M0 se agrupan bajo org.dataanaliticsent.TroncoComun,
con la capitalización solicitada. La clase de arranque queda en el paquete padre.

~~~text
DataAnaliticsEnterprise/
└── Backend/
    └── src/
        ├── main/
        │   ├── java/org/dataanaliticsent/
        │   │   ├── DataAnaliticsEnterprise.java
        │   │   └── TroncoComun/
        │   │       ├── autorizacion/
        │   │       ├── catalogo/       (esqueleto previo sin implementación M1)
        │   │       ├── contexto/
        │   │       ├── errores/
        │   │       └── identidad/
        │   └── resources/
        └── test/
            └── java/org/dataanaliticsent/
                ├── DataAnaliticsEnterpriseTests.java
                └── TroncoComun/
                    ├── TroncoComunPostgresTests.java
                    ├── autorizacion/
                    ├── contexto/
                    └── identidad/
~~~

Se actualizaron declaraciones package, imports y referencias Java, junto con
las rutas públicas documentadas de autorización. La prueba general
TroncoComunPostgresTests pertenece ahora al paquete org.dataanaliticsent.TroncoComun.
El movimiento troncocomun→TroncoComun de las pruebas utiliza un nombre intermedio
para resolver correctamente las diferencias de mayúsculas en Windows.

## Consumidores Java

Las fachadas y SPI conservan sus métodos, pero cambia su nombre completamente
cualificado. Los consumidores deben actualizar los imports y recompilar:

| Antes | Ahora |
| --- | --- |
| org.dataanaliticsent.autorizacion | org.dataanaliticsent.TroncoComun.autorizacion |
| org.dataanaliticsent.contexto | org.dataanaliticsent.TroncoComun.contexto |
| org.dataanaliticsent.errores | org.dataanaliticsent.TroncoComun.errores |
| org.dataanaliticsent.identidad | org.dataanaliticsent.TroncoComun.identidad |

Ejemplo:

~~~java
import org.dataanaliticsent.TroncoComun.autorizacion.AutorizacionApi;
import org.dataanaliticsent.TroncoComun.identidad.IdentidadActualApi;
~~~

Es un cambio de namespace Java y requiere adaptar módulos consumidores como M1
al integrar esta rama. Las fachadas públicas siguen siendo el límite entre
módulos; las clases internal son privadas a su componente.
La implementación funcional de catálogo del worktree M1 mantiene su ubicación
propia hasta esa integración; el esqueleto movido aquí no añade funcionalidades.

## Arranque y comprobaciones

DataAnaliticsEnterprise.java conserva package org.dataanaliticsent y
SpringApplication.run(DataAnaliticsEnterprise.class, args). Spring Boot continúa
explorando sus paquetes descendientes. Maven, Dockerfile y comandos de arranque
usan las raíces existentes, sin otro main ni configuración de escaneo duplicada.

Después de cambiar de rama o antes de ejecutar en IntelliJ, compilar limpiamente
para eliminar clases residuales con el namespace antiguo:

~~~powershell
mvn -f .\DataAnaliticsEnterprise\pom.xml clean verify
~~~

La suite PostgreSQL se ejecuta en una base temporal aislada:

~~~powershell
python tools/verificar_postgres_tc.py
~~~

Las rutas HTTP, JSON Schema y migraciones SQL conservan sus contenidos. Este
refactor no requiere migrar datos por un cambio de nombres Java. El frontend y
sus recursos de marca mantienen su ubicación.

Para reconstruir el contenedor del servidor con el nuevo código:

~~~powershell
docker compose --profile servidor build backend
~~~

Un contenedor ya iniciado utiliza su imagen previa hasta recrearlo. No se
reinician contenedores ni se eliminan volúmenes de desarrollo automáticamente.
