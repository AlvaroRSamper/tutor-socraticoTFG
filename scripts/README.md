# Alta de cuentas para el login manual

Mientras el Campus Virtual no habilite el acceso LTI, el profesorado y el alumnado
entran con **usuario y contraseña**. El acceso LTI sigue configurado en el código: en
cuanto esté disponible, basta con rellenar las variables `LTI_*` del entorno.

El profesor prepara las cuentas **una vez, al desplegar la asignatura**, con estos pasos.

## 1. Generar las cuentas

Desde esta carpeta `scripts/`:

**Windows (PowerShell):**

```powershell
./generar-usuarios.ps1 -Cantidad 60 -Asignatura PROG1 -ProfesorId 99001
```

**Linux / servidor de producción (Bash):**

```bash
./generar-usuarios.sh --cantidad 60 --asignatura PROG1 --profesor 99001
```

Esto crea dos ficheros:

- `usuarios.csv` — la **semilla** que lee la aplicación (`id,password,rol,asignatura`).
- `credenciales.csv` — la lista `id / contraseña` para **repartir** al alumnado.

En lugar de `-Cantidad`, puedes pasar una lista de identificadores (uno por línea)
con `-Ids alumnos.txt` (`--ids` en Bash). Los identificadores deben ser **numéricos de
hasta 5 dígitos** (es lo que admite el formulario de acceso).

Las contraseñas se generan en **texto plano**; la aplicación las cifra con bcrypt al
arrancar, así que nunca se almacenan en claro dentro de la app. Si prefieres, puedes
editar `usuarios.csv` a mano y poner contraseñas ya cifradas (`{bcrypt}$2a$...`).

## 2. Conectar la semilla al desplegar

Al arrancar la aplicación, define dos variables de entorno:

```bash
export TUTOR_USUARIOS_FICHERO=/ruta/al/usuarios.csv
export TUTOR_ASIGNATURA_POR_DEFECTO=PROG1
```

- `TUTOR_USUARIOS_FICHERO`: ruta al `usuarios.csv` generado.
- `TUTOR_ASIGNATURA_POR_DEFECTO`: asignatura para las cuentas que no la indiquen en el CSV.

Las cuentas se leen **al arrancar** la aplicación. Si más adelante añades cuentas al fichero
(o cuelgas un fichero nuevo), hay que **reiniciar la aplicación** para que las relea; no es en
caliente. Reiniciar solo cierra las sesiones activas (no borra nada de la base de datos).

También puedes seguir dando de alta cuentas sueltas por variable de entorno
(`TUTOR_SECURITY_USUARIOS_0_ID`, etc.); se combinan con las del fichero.

## Varias asignaturas en el mismo despliegue

Una sola instancia sirve varias asignaturas a la vez: cada cuenta lleva su asignatura en
su fila del CSV. Genera **un fichero por asignatura**, con **rangos de ID distintos** para
que no se solapen (si se repite un ID, la app no arranca):

```powershell
./generar-usuarios.ps1 -Cantidad 60 -InicioId 20001 -Asignatura "Estructura de Datos" -ProfesorId 99001 -Salida eda
./generar-usuarios.ps1 -Cantidad 60 -InicioId 30001 -Asignatura "Programacion Orientada a Objetos" -ProfesorId 99002 -Salida poo
```

Luego apunta `TUTOR_USUARIOS_FICHERO` a **los dos ficheros separados por coma**:

```bash
export TUTOR_USUARIOS_FICHERO=/ruta/eda/usuarios.csv,/ruta/poo/usuarios.csv
```

Notas:

- Cada asignatura necesita **su propia cuenta de profesor** (IDs distintos): la asignatura
  va ligada a la sesión, así que un mismo profesor entra en una u otra según con qué cuenta
  inicie sesión.
- `TUTOR_ASIGNATURA_POR_DEFECTO` deja de ser relevante si todas las filas indican asignatura;
  queda solo como respaldo.

## 3. Repartir y proteger

- Entrega a cada alumno su fila de `credenciales.csv`.
- Restringe los permisos de ambos ficheros (contienen contraseñas):
  `chmod 600 usuarios.csv credenciales.csv`.
- Borra `credenciales.csv` una vez repartidas las contraseñas.

## Formato del CSV

```
id,password,rol,asignatura
20001,Kf7mNq2t,ALUMNO,PROG1
99001,Zx4np8Wd,PROFESOR,PROG1
```

- `rol`: `ALUMNO` (por defecto) o `PROFESOR`.
- `asignatura`: opcional; si se omite se usa `TUTOR_ASIGNATURA_POR_DEFECTO`.
- Se admite una cabecera `id,...` (se ignora) y contraseñas en claro o `{bcrypt}$2a$...`.
