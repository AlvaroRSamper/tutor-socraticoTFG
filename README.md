# Tutor Socrático de POO

Este es el proyecto de mi TFG: un tutor basado en LLMs (RAG) para ayudar a estudiar
Programación Orientada a Objetos. En vez de dar la solución directamente, va guiando
al alumno con preguntas. Por detrás es una aplicación Spring Boot con un frontend
estático, y se puede integrar en Moodle vía LTI.

## Qué necesitas tener instalado

- **Java 17** (es la versión con la que está hecho, no uses una más nueva por si acaso)
- No hace falta instalar Maven: el proyecto trae el wrapper (`mvnw` / `mvnw.cmd`)
- Para producción: **PostgreSQL** con la extensión **pgvector**

## Arrancar en local (lo más rápido para probarlo)

Para desarrollo hay un perfil `dev` que arranca sin complicaciones: **no necesita
Docker, ni PostgreSQL, ni clave de la API**. Usa una base de datos H2 en fichero
(se crea sola en `./data`) y el almacén de vectores en memoria.

En Windows (PowerShell):

```powershell
.\mvnw.cmd spring-boot:run
```

En Linux/Mac:

```bash
./mvnw spring-boot:run
```

Cuando arranque, la app está en **http://localhost:8080**.

> Nota: el perfil `dev` ya está activo por defecto, no hay que indicar nada.

### Usuarios de prueba

En local ya vienen unas cuentas creadas (la contraseña es la misma que el ID):

| Usuario | Contraseña | Rol      |
|---------|-----------|----------|
| 12345   | 12345     | Alumno   |
| 10001   | 12345     | Alumno   |
| 99991   | 99991     | Profesor |
| 90001   | 12345     | Profesor |

- La vista del alumno es la página principal: http://localhost:8080
- La vista del profesor está en: http://localhost:8080/profesor.html

### La clave de la API (opcional en local)

La app arranca sin la clave de Anthropic, pero las respuestas del tutor no funcionarán
(sale un aviso). Si quieres probar el chat de verdad, define la variable de entorno
antes de arrancar:

```powershell
$env:ANTHROPIC_API_KEY = "sk-ant-..."
.\mvnw.cmd spring-boot:run
```

## Compilar y ejecutar los tests

```powershell
.\mvnw.cmd clean verify
```

Esto compila, pasa los tests y genera el `.jar` en la carpeta `target/`.

## Despliegue en producción

En producción se usa el perfil `prod`, que cambia bastantes cosas respecto a local:
PostgreSQL + pgvector en vez de H2, migraciones con Flyway, cookies seguras (HTTPS)
y acceso del alumnado y el profesorado por LTI desde Moodle.

### 1. Base de datos

Necesitas una instancia de PostgreSQL con la extensión `pgvector` instalada. Las tablas
las crea Flyway solo la primera vez que arranca (las migraciones están en
`src/main/resources/db/migration`), así que no hay que montar el esquema a mano.

### 2. Variables de entorno

Nada sensible va en el repositorio, todo se inyecta por entorno. Lo mínimo:

```bash
# Activar el perfil de producción
SPRING_PROFILES_ACTIVE=prod

# Base de datos
DB_HOST=...
DB_PORT=5432
DB_NAME=...
DB_USER=...
DB_PASSWORD=...

# Modelo de IA
ANTHROPIC_API_KEY=sk-ant-...

# Correo (para los avisos y el informe semanal del profesor)
MAIL_USERNAME=tu_correo@gmail.com
MAIL_PASSWORD=...   # contraseña de aplicación de Gmail, no la normal

# Usuario administrador/profesor (la contraseña va en bcrypt)
TUTOR_SECURITY_USUARIOS_0_ID=admin
TUTOR_SECURITY_USUARIOS_0_PASSWORDHASH={bcrypt}$2a$10$....
TUTOR_SECURITY_USUARIOS_0_ROL=PROFESOR
```

Para la integración con Moodle (LTI) hay que definir además `LTI_ISSUER`,
`LTI_CLIENT_ID`, `LTI_JWKS_URI` y `LTI_AUTH_LOGIN_URL` con los datos que da Moodle.

### 3. Generar y arrancar el .jar

```bash
./mvnw clean package
SPRING_PROFILES_ACTIVE=prod java -jar target/tutor-socratico-0.0.1-SNAPSHOT.jar
```

### 4. Proxy inverso (HTTPS)

La app está pensada para ir detrás de un proxy inverso que termina
el TLS. En producción las cookies son `Secure` y se necesita HTTPS de verdad, sobre todo
porque va embebida en un iframe de Moodle. El perfil `prod` ya está preparado para leer
las cabeceras `X-Forwarded-*` del proxy.

## Notas útiles

- Documentación de la API (Swagger): http://localhost:8080/swagger-ui.html
- En local puedes mirar la base de datos H2 en http://localhost:8080/h2-console
  (URL JDBC: `jdbc:h2:file:./data/tutor`, usuario `sa`, sin contraseña)
- En producción **todo el mundo entra por Moodle** (LTI): el rol se asigna automáticamente
  según el rol que tenga la persona en el Campus Virtual (Profesor/a → vista de profesor,
  Estudiante → vista de alumno). El login manual con ID y contraseña es sobre todo para
  las pruebas en local y como acceso de respaldo del profesorado.

## Sobre el proyecto

TFG · Universidad de Málaga · Álvaro R. Samper
