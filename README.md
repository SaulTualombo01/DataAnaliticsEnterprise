# DataAnaliticsEnterprise
Página Web que permite el análisis de datos empresariales

## Backend Spring Boot

El backend es un monolito modular con Spring Boot 4.1.2. La clase
`org.dataanaliticsent.DataAnaliticsEnterprise` inicia la aplicación y detecta
los componentes de los módulos que se añadan bajo `org.dataanaliticsent`.
El código, las pruebas y los recursos conservan sus rutas en `Backend/src/`.

### Requisitos

- Maven 3.6.3 o superior.
- Compilación para Java 23. En el equipo de David se utiliza el JDK 24 instalado.
- En IntelliJ, seleccionar JDK 24 para el proyecto y para Maven, y recargar el POM.

### Ejecutar desde la raíz del repositorio

En PowerShell, seleccionar el JDK de la sesión y arrancar el servicio:

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-24"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
mvn -f .\DataAnaliticsEnterprise\pom.xml spring-boot:run
```

El servidor escucha en `http://localhost:8080`. La variable de entorno `PORT`
permite cambiar el puerto. Detener con `Ctrl+C`.
Todavía no hay endpoints de negocio ni página de inicio: una petición a `/`
devuelve HTTP 404 aunque el servicio esté funcionando.

### Probar y empaquetar

Con el mismo JDK seleccionado:

```powershell
mvn -f .\DataAnaliticsEnterprise\pom.xml verify
& "$env:JAVA_HOME\bin\java.exe" -jar .\DataAnaliticsEnterprise\target\DataAnaliticsEnterprise-1.0-SNAPSHOT.jar
```

La prueba de integración arranca el servidor en un puerto libre y verifica
una respuesta HTTP. El empaquetado genera un JAR ejecutable con sus dependencias.
