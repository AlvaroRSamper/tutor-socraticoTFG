# Tutor Socratico de POO

Este es el proyecto de mi TFG. Consiste en un tutor basado en LLMs para ayudar a estudiantes de programación. En vez de dar la solución directamente va guiando al alumno con preguntas. Por detrás es una aplicación Spring Boot con un frontend estático y se puede integrar en Moodle vía LTI.

## Guía para Windows

### Requisitos previos en Windows
- Java 17
- Para producción necesitas PostgreSQL con la extensión pgvector

### Arrancar en local en Windows
Para desarrollo hay un perfil dev que arranca sin complicaciones. No necesita Docker ni PostgreSQL ni clave de la API. Usa una base de datos H2 en fichero que se crea sola en la carpeta data y el almacén de vectores en memoria.

Abrimos PowerShell y ejecutamos
```powershell
.\mvnw.cmd spring-boot:run
```
Cuando arranque la app estará en http://localhost:8080.
El perfil dev ya está activo por defecto por lo que no hay que indicar nada.

### Usuarios de prueba
En local ya vienen unas cuentas creadas.
- Alumno 1 con usuario 12345 y contraseña 12345
- Alumno 2 con usuario 10001 y contraseña 12345
- Profesor 1 con usuario 99991 y contraseña 99991
- Profesor 2 con usuario 90001 y contraseña 12345

La vista del alumno es la página principal en http://localhost:8080.
La vista del profesor está en http://localhost:8080/profesor.html.

### La clave de la API en local para Windows
La app arranca sin la clave de Anthropic pero las respuestas del tutor no funcionarán. Si quieres probar el chat de verdad define la variable de entorno antes de arrancar.
```powershell
$env:ANTHROPIC_API_KEY = "sk-ant-..."
.\mvnw.cmd spring-boot:run
```

### Compilar y ejecutar los tests en Windows
```powershell
.\mvnw.cmd clean verify
```
Esto compila el código y pasa los tests para generar el archivo .jar en la carpeta target.

### Despliegue en producción para Windows
En producción se usa el perfil prod que cambia bastantes cosas respecto a local. Usa PostgreSQL con pgvector en vez de H2 y usa migraciones con Flyway. También usa cookies seguras y acceso del alumnado y el profesorado por LTI desde Moodle.

#### Base de datos
Necesitas una instancia de PostgreSQL con la extensión pgvector instalada. Las tablas las crea Flyway solo la primera vez que arranca. Las migraciones están en la carpeta db migration así que no hay que montar el esquema a mano.

#### Variables de entorno
Nada sensible va en el repositorio ya que todo se inyecta por entorno. Lo mínimo necesario se muestra a continuación.

```bash
SPRING_PROFILES_ACTIVE=prod
DB_HOST=localhost
DB_PORT=5432
DB_NAME=nombre_bd
DB_USER=usuario
DB_PASSWORD=clave
LLM_PROVIDER=anthropic
ANTHROPIC_API_KEY=sk-ant-...
MAIL_USERNAME=tu_correo@gmail.com
MAIL_PASSWORD=clave_app_gmail
TUTOR_USUARIOS_FICHERO=C:\ruta\usuarios.csv
TUTOR_ASIGNATURA_POR_DEFECTO=PROG1
```

Para la integración con Moodle LTI hay que definir además las variables LTI_ISSUER LTI_CLIENT_ID LTI_JWKS_URI y LTI_AUTH_LOGIN_URL con los datos que da Moodle.

#### Carga de cuentas de alumnos y profesores por fichero CSV
Mientras el Campus Virtual no habilite el acceso LTI el profesorado y el alumnado entran con usuario y contraseña. Las cuentas se leen desde un fichero CSV generado con el script de la carpeta scripts.

Las variables `TUTOR_USUARIOS_FICHERO` y `TUTOR_ASIGNATURA_POR_DEFECTO` le indican a la aplicación la ubicación del fichero y la asignatura por defecto.

Las contraseñas pueden ir en texto plano ya que la app las cifra con bcrypt al arrancar. También pueden ir ya cifradas. El fichero se genera ejecutando el script generar-usuarios.ps1 de la carpeta scripts. El acceso LTI queda configurado en el código para cuando el Campus lo habilite.

Las cuentas se cargan al arrancar. Si añades o cambias cuentas del fichero hay que reiniciar la aplicación para que surta efecto ya que no es en caliente.

Si tienes varias asignaturas en el mismo despliegue debes generar un fichero por asignatura y apuntar TUTOR_USUARIOS_FICHERO a todos ellos separados por coma. Cada cuenta lleva su asignatura en el CSV así que una sola instancia sirve a todas.

#### Generar y arrancar el .jar
```powershell
.\mvnw.cmd clean package
$env:SPRING_PROFILES_ACTIVE="prod"
java -jar target\tutor-socratico-0.0.1-SNAPSHOT.jar
```

#### Proxy inverso
La app está pensada para ir detrás de un proxy inverso que termina el TLS. En producción las cookies son seguras y se necesita HTTPS de verdad sobre todo porque va embebida en un iframe de Moodle. El perfil prod ya está preparado para leer las cabeceras del proxy.


## Guía para Linux y Mac

### Requisitos previos en Linux
- Java 17
- Para producción necesitas PostgreSQL con la extensión pgvector

### Arrancar en local en Linux
Para desarrollo hay un perfil dev que arranca sin complicaciones. No necesita Docker ni PostgreSQL ni clave de la API. Usa una base de datos H2 en fichero que se crea sola en la carpeta data y el almacén de vectores en memoria.

La primera vez hay que dar permisos de ejecución al wrapper y a los scripts.
```bash
chmod +x ./mvnw scripts/*.sh
./mvnw spring-boot:run
```
Cuando arranque la app estará en http://localhost:8080.
El perfil dev ya está activo por defecto por lo que no hay que indicar nada.

### Usuarios de prueba
En local ya vienen unas cuentas creadas.
- Alumno 1 con usuario 12345 y contraseña 12345
- Alumno 2 con usuario 10001 y contraseña 12345
- Profesor 1 con usuario 99991 y contraseña 99991
- Profesor 2 con usuario 90001 y contraseña 12345

La vista del alumno es la página principal en http://localhost:8080.
La vista del profesor está en http://localhost:8080/profesor.html.

### La clave de la API en local para Linux
La app arranca sin la clave de Anthropic pero las respuestas del tutor no funcionarán. Si quieres probar el chat de verdad define la variable de entorno antes de arrancar.
```bash
export ANTHROPIC_API_KEY="sk-ant-..."
./mvnw spring-boot:run
```

### Compilar y ejecutar los tests en Linux
```bash
./mvnw clean verify
```
Esto compila el código y pasa los tests para generar el archivo .jar en la carpeta target.

### Despliegue en producción para Linux
En producción se usa el perfil prod que cambia bastantes cosas respecto a local. Usa PostgreSQL con pgvector en vez de H2 y usa migraciones con Flyway. También usa cookies seguras y acceso del alumnado y el profesorado por LTI desde Moodle.

#### Base de datos
Necesitas una instancia de PostgreSQL con la extensión pgvector instalada. Las tablas las crea Flyway solo la primera vez que arranca. Las migraciones están en la carpeta db migration así que no hay que montar el esquema a mano.

#### Variables de entorno
Nada sensible va en el repositorio ya que todo se inyecta por entorno. Lo mínimo necesario se muestra a continuación.

```bash
export SPRING_PROFILES_ACTIVE=prod
export DB_HOST=localhost
export DB_PORT=5432
export DB_NAME=nombre_bd
export DB_USER=usuario
export DB_PASSWORD=clave
export LLM_PROVIDER=anthropic
export ANTHROPIC_API_KEY=sk-ant-...
export MAIL_USERNAME=tu_correo@gmail.com
export MAIL_PASSWORD=clave_app_gmail
export TUTOR_USUARIOS_FICHERO=/ruta/al/usuarios.csv
export TUTOR_ASIGNATURA_POR_DEFECTO=PROG1
```

Para la integración con Moodle LTI hay que definir además las variables LTI_ISSUER LTI_CLIENT_ID LTI_JWKS_URI y LTI_AUTH_LOGIN_URL con los datos que da Moodle.

#### Carga de cuentas de alumnos y profesores por fichero CSV
Mientras el Campus Virtual no habilite el acceso LTI el profesorado y el alumnado entran con usuario y contraseña. Las cuentas se leen desde un fichero CSV generado con el script de la carpeta scripts.

Las variables `TUTOR_USUARIOS_FICHERO` y `TUTOR_ASIGNATURA_POR_DEFECTO` le indican a la aplicación la ubicación del fichero y la asignatura por defecto.

Las contraseñas pueden ir en texto plano ya que la app las cifra con bcrypt al arrancar. También pueden ir ya cifradas. El fichero se genera ejecutando el script generar-usuarios.sh de la carpeta scripts. El acceso LTI queda configurado en el código para cuando el Campus lo habilite.

Las cuentas se cargan al arrancar. Si añades o cambias cuentas del fichero hay que reiniciar la aplicación para que surta efecto ya que no es en caliente.

Si tienes varias asignaturas en el mismo despliegue debes generar un fichero por asignatura y apuntar TUTOR_USUARIOS_FICHERO a todos ellos separados por coma. Cada cuenta lleva su asignatura en el CSV así que una sola instancia sirve a todas.

#### Generar y arrancar el .jar
```bash
./mvnw clean package
SPRING_PROFILES_ACTIVE=prod java -jar target/tutor-socratico-0.0.1-SNAPSHOT.jar
```

#### Proxy inverso
La app está pensada para ir detrás de un proxy inverso que termina el TLS. En producción las cookies son seguras y se necesita HTTPS de verdad sobre todo porque va embebida en un iframe de Moodle. El perfil prod ya está preparado para leer las cabeceras del proxy.


## Notas útiles
La documentación de la API en Swagger está en http://localhost:8080/swagger-ui.html.
En local puedes mirar la base de datos H2 en http://localhost:8080/h2-console con la URL jdbc:h2:file:./data/tutor usando el usuario sa y sin contraseña.
Acceso en producción. Mientras el Campus Virtual no habilite el acceso LTI el profesorado y el alumnado entran con ID y contraseña usando cuentas provisionadas por el fichero semilla. La integración LTI está lista en el código y en cuanto Moodle la habilite al entrar por el enlace de la asignatura el rol se asigna solo a vista de profesor o vista de alumno sin tocar código.

## Sobre el proyecto
TFG. Universidad de Málaga. Álvaro R. Samper.
