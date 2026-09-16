#!/usr/bin/env bash
set -euo pipefail

CANTIDAD=0
INICIO_ID=20001
IDS_FICHERO=""
ASIGNATURA=""
SALIDA="."
PROFESORES=""
LONGITUD=8

while [[ $# -gt 0 ]]; do
    case "$1" in
        --cantidad) CANTIDAD="$2"; shift 2 ;;
        --inicio) INICIO_ID="$2"; shift 2 ;;
        --ids) IDS_FICHERO="$2"; shift 2 ;;
        --asignatura) ASIGNATURA="$2"; shift 2 ;;
        --salida) SALIDA="$2"; shift 2 ;;
        --profesor) PROFESORES="$2"; shift 2 ;;
        --longitud) LONGITUD="$2"; shift 2 ;;
        *) echo "Opción desconocida: $1" >&2; exit 1 ;;
    esac
done

if [[ -z "$ASIGNATURA" ]]; then
    echo "Falta --asignatura" >&2; exit 1
fi

nueva_password() {
    LC_ALL=C tr -dc 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789' < /dev/urandom 2>/dev/null | head -c "$LONGITUD" || true
}

alumnos=()
if [[ -n "$IDS_FICHERO" ]]; then
    while IFS= read -r linea; do
        linea="$(echo "$linea" | tr -d '[:space:]')"
        [[ -n "$linea" ]] && alumnos+=("$linea")
    done < "$IDS_FICHERO"
elif [[ "$CANTIDAD" -gt 0 ]]; then
    for ((i = 0; i < CANTIDAD; i++)); do
        alumnos+=("$((INICIO_ID + i))")
    done
else
    echo "Indica --cantidad N o --ids fichero.txt" >&2; exit 1
fi

mkdir -p "$SALIDA"
usuarios="$SALIDA/usuarios.csv"
credenciales="$SALIDA/credenciales.csv"
echo "id,password,rol,asignatura" > "$usuarios"
echo "id,password,rol" > "$credenciales"

if [[ -n "$PROFESORES" ]]; then
    IFS=',' read -ra profs <<< "$PROFESORES"
    for id in "${profs[@]}"; do
        pass="$(nueva_password)"
        echo "$id,$pass,PROFESOR,$ASIGNATURA" >> "$usuarios"
        echo "$id,$pass,PROFESOR" >> "$credenciales"
    done
fi

for id in "${alumnos[@]}"; do
    pass="$(nueva_password)"
    echo "$id,$pass,ALUMNO,$ASIGNATURA" >> "$usuarios"
    echo "$id,$pass,ALUMNO" >> "$credenciales"
done

total=$(( $(wc -l < "$usuarios") - 1 ))
echo "Generadas $total cuentas para la asignatura '$ASIGNATURA'."
echo "  Semilla para la app: $usuarios   -> TUTOR_USUARIOS_FICHERO"
echo "  Para repartir:       $credenciales"
