# Tutor Socratico de POO

Este es el proyecto de mi TFG. Consiste en un tutor basado en LLMs para ayudar a estudiantes de programación. En vez de dar la solución directamente va guiando al alumno con preguntas. Por detrás es una aplicación Spring Boot con un frontend estático y se puede integrar en Moodle vía LTI.

## Guía para Windows

### Requisitos previos en Windows
- Java 17 o superior

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

### Despliegue en producción
Windows queda para desarrollo en local. El despliegue en producción se hace en un servidor Linux y está explicado paso a paso en la guía de Linux más abajo. Las cuentas de alumnos y profesores sí se pueden generar desde Windows con el script generar-usuarios.ps1 de la carpeta scripts y luego copiar el fichero al servidor.


## Guía para Linux y Mac

### Requisitos previos en Linux
- Java 17 o superior
- Para producción necesitas PostgreSQL con la extensión pgvector y nginx como proxy inverso

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

Los pasos siguientes están probados en Ubuntu 24.04 con una asignatura de 60 alumnos y 2 profesores. La app se instala en /opt/tutor-socratico y se ejecuta con un usuario del sistema propio llamado tutor.

#### 1. Instalar los paquetes
```bash
sudo apt update
sudo apt install -y openjdk-21-jdk postgresql postgresql-16-pgvector nginx git
```

#### 2. Base de datos
Flyway crea las tablas solo la primera vez que arranca, así que no hay que montar el esquema a mano. Lo que sí hay que hacer antes es crear el usuario, la base de datos y la extensión pgvector.

```bash
sudo -u postgres psql -c "CREATE ROLE tutor LOGIN PASSWORD 'una_clave_fuerte';"
sudo -u postgres createdb -O tutor tutor_socratico
sudo -u postgres psql -d tutor_socratico -c "CREATE EXTENSION IF NOT EXISTS vector;"
```

La extensión tiene que crearla el usuario postgres. La primera migración intenta crearla, pero el usuario de la app no es superusuario y sin este paso la app no arranca. La base de datos tiene que pertenecer al usuario de la app porque desde PostgreSQL 15 es la forma de que pueda crear tablas.

#### 3. Descargar y compilar
```bash
sudo useradd --system --create-home --home-dir /opt/tutor-socratico tutor
sudo -u tutor git clone <url-del-repositorio> /opt/tutor-socratico/app
cd /opt/tutor-socratico/app
sudo -u tutor chmod +x mvnw scripts/*.sh
sudo -u tutor ./mvnw clean package
```
El .jar queda en target/tutor-socratico-0.0.1-SNAPSHOT.jar.

#### 4. Cuentas de alumnos y profesores por fichero CSV
Mientras el Campus Virtual no habilite el acceso LTI el profesorado y el alumnado entran con usuario y contraseña. Las cuentas se leen desde un fichero CSV generado con el script generar-usuarios.sh de la carpeta scripts. Por ejemplo para 60 alumnos y 2 profesores:

```bash
sudo -u tutor ./scripts/generar-usuarios.sh --cantidad 60 --inicio 20001 --asignatura PROG1 --profesor 99001,99002 --salida /opt/tutor-socratico/cuentas
sudo chmod 600 /opt/tutor-socratico/cuentas/*.csv
```

Esto genera usuarios.csv, que es el fichero que lee la app, y credenciales.csv, que es la lista para repartir. Los identificadores tienen que ser numéricos de hasta 5 dígitos. Si tienes la lista real de alumnos puedes pasarla con --ids alumnos.txt en vez de --cantidad. En la carpeta scripts hay un README con todas las opciones.

Las contraseñas pueden ir en texto plano ya que la app las cifra con bcrypt al arrancar. También pueden ir ya cifradas. El acceso LTI queda configurado en el código para cuando el Campus lo habilite.

Las cuentas se cargan al arrancar. Si añades o cambias cuentas del fichero hay que reiniciar la aplicación para que surta efecto ya que no es en caliente. Reiniciar cierra las sesiones abiertas pero no borra nada de la base de datos.

Si tienes varias asignaturas en el mismo despliegue debes generar un fichero por asignatura con rangos de ID distintos y apuntar TUTOR_USUARIOS_FICHERO a todos ellos separados por coma. Cada cuenta lleva su asignatura en el CSV así que una sola instancia sirve a todas.

#### 5. Variables de entorno
Nada sensible va en el repositorio ya que todo se inyecta por entorno. Las variables se definen con export en el script de bash /opt/tutor-socratico/arrancar.sh, que es también el que lanza la app.

```bash
#!/usr/bin/env bash
export SPRING_PROFILES_ACTIVE=prod
export DB_HOST=localhost
export DB_PORT=5432
export DB_NAME=tutor_socratico
export DB_USER=tutor
export DB_PASSWORD=una_clave_fuerte
export LLM_PROVIDER=anthropic
export ANTHROPIC_API_KEY=sk-ant-...
export MAIL_USERNAME=tu_correo@gmail.com
export MAIL_PASSWORD=clave_app_gmail
export TUTOR_USUARIOS_FICHERO=/opt/tutor-socratico/cuentas/usuarios.csv
export TUTOR_ASIGNATURA_POR_DEFECTO=PROG1

exec java -Xmx2g -jar /opt/tutor-socratico/app/target/tutor-socratico-0.0.1-SNAPSHOT.jar --server.address=127.0.0.1 --server.port=8080
```

El script contiene claves así que hay que restringir sus permisos. Solo root puede modificarlo y el usuario tutor puede leerlo y ejecutarlo.
```bash
sudo chown root:tutor /opt/tutor-socratico/arrancar.sh
sudo chmod 750 /opt/tutor-socratico/arrancar.sh
```

El correo solo se usa para el informe semanal al profesor. Si no hay cuenta de correo se pueden quitar MAIL_USERNAME y MAIL_PASSWORD y añadir export INFORME_SEMANAL_ENABLED=false.

Para la integración con Moodle LTI hay que añadir además los export de LTI_ISSUER LTI_CLIENT_ID LTI_JWKS_URI y LTI_AUTH_LOGIN_URL con los datos que da Moodle.

Para una prueba rápida se puede lanzar el script a mano con sudo -u tutor /opt/tutor-socratico/arrancar.sh, pero la app se para al cerrar la terminal. Para dejarla en marcha se usa el servicio del paso siguiente.

#### 6. Arrancar la app como servicio
Para que la app siga funcionando al cerrar la sesión y arranque sola si se reinicia el servidor se crea el servicio /etc/systemd/system/tutor-socratico.service, que ejecuta el script anterior.

```ini
[Unit]
Description=Tutor Socratico
After=network.target postgresql.service
Requires=postgresql.service

[Service]
User=tutor
Group=tutor
WorkingDirectory=/opt/tutor-socratico
ExecStart=/opt/tutor-socratico/arrancar.sh
SuccessExitStatus=143
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now tutor-socratico
journalctl -u tutor-socratico -f
```

Si cambias alguna variable en arrancar.sh hay que reiniciar con sudo systemctl restart tutor-socratico.

La app ha arrancado bien cuando en el log aparecen estas tres líneas. La primera solo sale la primera vez y en los siguientes arranques pone que el esquema ya está al día.
- Successfully applied 9 migrations
- Usuarios cargados para login manual: 62
- Started TutorSocraticoApplication

La app escucha solo en 127.0.0.1 para que no se pueda entrar sin pasar por el proxy. Consume en torno a 1 GB de memoria así que el servidor debería tener al menos 2 GB.

#### 7. Proxy inverso con HTTPS
La app va detrás de un proxy inverso que termina el TLS. En producción las cookies son seguras y se necesita HTTPS de verdad sobre todo porque va embebida en un iframe de Moodle. Sin HTTPS el login no funciona. El perfil prod ya está preparado para leer las cabeceras del proxy.

Se crea el fichero /etc/nginx/sites-available/tutor-socratico con el certificado del servidor.

```nginx
server {
    listen 80;
    server_name tutor.ejemplo.uma.es;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl;
    server_name tutor.ejemplo.uma.es;

    ssl_certificate     /etc/ssl/certs/tutor.crt;
    ssl_certificate_key /etc/ssl/private/tutor.key;

    client_max_body_size 100M;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host              $host;
        proxy_set_header X-Real-IP         $remote_addr;
        proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header X-Forwarded-Port  $server_port;
        proxy_read_timeout 120s;
    }
}
```

La línea client_max_body_size es necesaria porque nginx limita las subidas a 1 MB por defecto y el profesor no podría subir los PDF de apuntes. El proxy_read_timeout da margen a las respuestas del modelo que tardan más.

```bash
sudo ln -s /etc/nginx/sites-available/tutor-socratico /etc/nginx/sites-enabled/
sudo rm -f /etc/nginx/sites-enabled/default
sudo nginx -t
sudo systemctl reload nginx
```

#### 8. Comprobar y repartir
Entra con una cuenta de profesor en https://tu-servidor/profesor.html, configura la asignatura y sube los apuntes en PDF. Después entra con una cuenta de alumno en https://tu-servidor y haz una pregunta al tutor.

Cuando todo funcione entrega a cada alumno su fila de credenciales.csv y borra ese fichero del servidor.

#### Actualizar la app
```bash
cd /opt/tutor-socratico/app
sudo -u tutor git pull
sudo -u tutor ./mvnw clean package
sudo systemctl restart tutor-socratico
```
Si hay migraciones nuevas Flyway las aplica solo al arrancar.

#### Copias de seguridad
```bash
sudo -u postgres pg_dump -Fc tutor_socratico > tutor_socratico.dump
```
La copia incluye todos los datos. Algunos textos largos se guardan como objetos grandes de PostgreSQL que tienen propietario. Si insertas datos a mano hazlo con el usuario tutor y no con postgres, porque si no la app no podrá leerlos.


## Notas útiles
La documentación de la API en Swagger está en http://localhost:8080/swagger-ui.html.
En local puedes mirar la base de datos H2 en http://localhost:8080/h2-console con la URL jdbc:h2:file:./data/tutor usando el usuario sa y sin contraseña.
Acceso en producción. Mientras el Campus Virtual no habilite el acceso LTI el profesorado y el alumnado entran con ID y contraseña usando cuentas provisionadas por el fichero semilla. La integración LTI está lista en el código y en cuanto Moodle la habilite al entrar por el enlace de la asignatura el rol se asigna solo a vista de profesor o vista de alumno sin tocar código.
El límite de peticiones al tutor es de 20 por minuto por usuario, así que no afecta a un aula entera que salga a internet por la misma IP.

## Sobre el proyecto
TFG. Universidad de Málaga. Álvaro R. Samper.
