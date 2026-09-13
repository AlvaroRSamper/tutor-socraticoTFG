
#>
param(
    [int]$Cantidad = 0,
    [int]$InicioId = 20001,
    [string]$Ids = "",
    [Parameter(Mandatory = $true)][string]$Asignatura,
    [string]$Salida = ".",
    [string[]]$ProfesorId = @(),
    [int]$Longitud = 8
)

$alfabeto = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789".ToCharArray()

function Nueva-Password {
    -join (1..$Longitud | ForEach-Object { $alfabeto | Get-Random })
}

$idsAlumno = @()
if ($Ids -ne "") {
    $idsAlumno = Get-Content $Ids | ForEach-Object { $_.Trim() } | Where-Object { $_ -ne "" }
} elseif ($Cantidad -gt 0) {
    $idsAlumno = $InicioId..($InicioId + $Cantidad - 1) | ForEach-Object { "$_" }
} else {
    Write-Error "Indica -Cantidad N o -Ids fichero.txt"
    exit 1
}

$usuarios = @("id,password,rol,asignatura")
$credenciales = @("id,password,rol")

foreach ($id in $ProfesorId) {
    $pass = Nueva-Password
    $usuarios += "$id,$pass,PROFESOR,$Asignatura"
    $credenciales += "$id,$pass,PROFESOR"
}
foreach ($id in $idsAlumno) {
    $pass = Nueva-Password
    $usuarios += "$id,$pass,ALUMNO,$Asignatura"
    $credenciales += "$id,$pass,ALUMNO"
}

New-Item -ItemType Directory -Force -Path $Salida | Out-Null
$rutaUsuarios = Join-Path $Salida "usuarios.csv"
$rutaCredenciales = Join-Path $Salida "credenciales.csv"
$usuarios | Set-Content -Path $rutaUsuarios -Encoding UTF8
$credenciales | Set-Content -Path $rutaCredenciales -Encoding UTF8

$total = $usuarios.Count - 1
Write-Host "Generadas $total cuentas para la asignatura '$Asignatura'."
Write-Host "  Semilla para la app: $rutaUsuarios   -> TUTOR_USUARIOS_FICHERO"
Write-Host "  Para repartir:       $rutaCredenciales"
