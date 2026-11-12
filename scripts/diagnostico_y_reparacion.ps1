# =====================================================================
# MENSAJERIA UNCP IA - DIAGNOSTICO Y AUTORREPARACION DE ENTORNO
# =====================================================================

$projectRoot = (Get-Item -Path "$PSScriptRoot\..").FullName
Set-Location $projectRoot
$env:PATH = $env:PATH.Replace('"', '')

Write-Host "=====================================================================" -ForegroundColor Cyan
Write-Host "   MENSAJERIA UNCP IA - DIAGNOSTICO Y REPARACION AUTOMATICA" -ForegroundColor Cyan
Write-Host "=====================================================================" -ForegroundColor Cyan
Write-Host "Este asistente busca, repara y configura automaticamente tu entorno:"
Write-Host "- Java / JDK compatible (17 a 21)"
Write-Host "- Android SDK y local.properties"
Write-Host "- Configuracion limpia de Gradle y compilacion del APK"
Write-Host "=====================================================================" -ForegroundColor Cyan
Write-Host ""

# ---------------------------------------------------------------------
# 1. COMPROBACION Y AUTORREPARACION DE JAVA (JDK 17 A 21)
# ---------------------------------------------------------------------
Write-Host "[1/4] Verificando entorno de Java / JDK (17 a 21)..." -ForegroundColor Yellow

$chosenJdk = ""

# Prioridad 1: Android Studio JBR (siempre optimizado para Gradle y Android)
$asJbrCandidates = @()
Get-ChildItem -Path "C:\Program Files\Android\Android Studio*" -Directory -ErrorAction SilentlyContinue | ForEach-Object {
    $jbr = Join-Path $_.FullName "jbr"
    if (Test-Path "$jbr\bin\java.exe") { $asJbrCandidates += $jbr }
}
$localAs = Join-Path $env:LOCALAPPDATA "Programs\Android Studio"
if (Test-Path "$localAs\jbr\bin\java.exe") { $asJbrCandidates += "$localAs\jbr" }

foreach ($cand in $asJbrCandidates) {
    $verOut = (& "$cand\bin\java.exe" -version 2>&1) | Out-String
    if ($verOut -match 'version "(\d+)') {
        $mv = [int]$matches[1]
        if ($mv -ge 17 -and $mv -le 21) {
            $chosenJdk = $cand
            Write-Host "  [AUTO-REPARADO] Detectado JDK de Android Studio (Java $mv):" -ForegroundColor Green
            Write-Host "  $chosenJdk" -ForegroundColor Cyan
            break
        }
    }
}

# Prioridad 2: Si no hay JBR, buscar JDK 17 o 21 en el sistema
if (-not $chosenJdk) {
    $standaloneJdks = @()
    Get-ChildItem -Path "C:\Program Files\Eclipse Adoptium\jdk-*" -Directory -ErrorAction SilentlyContinue | ForEach-Object {
        if (Test-Path "$($_.FullName)\bin\java.exe") { $standaloneJdks += $_.FullName }
    }
    Get-ChildItem -Path "C:\Program Files\Java\jdk-*" -Directory -ErrorAction SilentlyContinue | ForEach-Object {
        if (Test-Path "$($_.FullName)\bin\java.exe") { $standaloneJdks += $_.FullName }
    }
    Get-ChildItem -Path "C:\Program Files\Amazon Corretto\jdk-*" -Directory -ErrorAction SilentlyContinue | ForEach-Object {
        if (Test-Path "$($_.FullName)\bin\java.exe") { $standaloneJdks += $_.FullName }
    }
    Get-ChildItem -Path "C:\Program Files\Microsoft\jdk-*" -Directory -ErrorAction SilentlyContinue | ForEach-Object {
        if (Test-Path "$($_.FullName)\bin\java.exe") { $standaloneJdks += $_.FullName }
    }
    Get-ChildItem -Path "C:\Program Files\Zulu\zulu-*" -Directory -ErrorAction SilentlyContinue | ForEach-Object {
        if (Test-Path "$($_.FullName)\bin\java.exe") { $standaloneJdks += $_.FullName }
    }

    foreach ($cand in $standaloneJdks) {
        $verOut = (& "$cand\bin\java.exe" -version 2>&1) | Out-String
        if ($verOut -match 'version "(\d+)') {
            $mv = [int]$matches[1]
            if ($mv -ge 17 -and $mv -le 21) {
                $chosenJdk = $cand
                Write-Host "  [AUTO-REPARADO] Detectado JDK compatible (Java $mv):" -ForegroundColor Green
                Write-Host "  $chosenJdk" -ForegroundColor Cyan
                break
            }
        }
    }
}

# Prioridad 3: Java en PATH si es compatible (17 a 21)
if (-not $chosenJdk) {
    $sysJava = Get-Command "java" -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source
    if ($sysJava) {
        $verOut = (& java -version 2>&1) | Out-String
        if ($verOut -match 'version "(\d+)') {
            $mv = [int]$matches[1]
            if ($mv -ge 17 -and $mv -le 21) {
                $chosenJdk = (Split-Path (Split-Path $sysJava -Parent) -Parent)
                Write-Host "  [OK] Java compatible en PATH (version $mv): $chosenJdk" -ForegroundColor Green
            }
        }
    }
}

if (-not $chosenJdk) {
    Write-Host "  [X] No se encontro un JDK compatible (Java 17 o 21 requerido por Gradle 8.13)." -ForegroundColor Red
    Write-Host "      (Versiones como Java 8 o Java 25 no son compatibles con este Gradle)." -ForegroundColor Yellow
    Write-Host "      Solucion: Instala Adoptium Temurin JDK 17 o 21 desde:" -ForegroundColor White
    Write-Host "      https://adoptium.net/temurin/releases/?version=17" -ForegroundColor Cyan
    exit 1
}

$env:JAVA_HOME = $chosenJdk
$env:PATH = "$chosenJdk\bin;$env:PATH"
Write-Host ""

# ---------------------------------------------------------------------
# 2. COMPROBACION Y AUTORREPARACION DE ANDROID SDK Y local.properties
# ---------------------------------------------------------------------
Write-Host "[2/4] Verificando Android SDK y archivo local.properties..." -ForegroundColor Yellow

$sdkPath = ""
# Buscar SDK en rutas estandar
$possibleSdkPaths = @(
    (Join-Path $env:LOCALAPPDATA "Android\Sdk"),
    "C:\Users\$env:USERNAME\AppData\Local\Android\Sdk",
    "D:\Android\Sdk",
    "E:\Android\Sdk"
)

foreach ($p in $possibleSdkPaths) {
    if (Test-Path $p) {
        $sdkPath = $p
        break
    }
}

$localPropsPath = Join-Path $projectRoot "local.properties"
$currentSdkInProps = ""
$currentGeminiKey = ""
$currentBackendUrl = "http://10.0.2.2:8000/"

if (Test-Path $localPropsPath) {
    Get-Content $localPropsPath | ForEach-Object {
        if ($_ -match '^\s*sdk\.dir\s*=\s*(.*)$') { $currentSdkInProps = $matches[1].Trim() }
        if ($_ -match '^\s*gemini\.api\.key\s*=\s*(.*)$') { $currentGeminiKey = $matches[1].Trim() }
        if ($_ -match '^\s*backend\.base\.url\s*=\s*(.*)$') { $currentBackendUrl = $matches[1].Trim() }
    }
}

$needRepairProps = $false
$realSdkPathToUse = ""

if ($currentSdkInProps) {
    $normalized = $currentSdkInProps.Replace("\\", "\").Replace("\:", ":")
    if (Test-Path $normalized) {
        $realSdkPathToUse = $normalized
    } else {
        $needRepairProps = $true
    }
} else {
    $needRepairProps = $true
}

if ($needRepairProps) {
    if ($sdkPath) {
        $realSdkPathToUse = $sdkPath
        $escapedSdk = $sdkPath.Replace("\", "\\").Replace(":", "\:")
        $newPropsContent = @(
            "## Generado y reparado automaticamente por diagnostico_y_reparacion.ps1",
            "sdk.dir=$escapedSdk",
            "",
            "gemini.api.key=$currentGeminiKey",
            "backend.base.url=$currentBackendUrl"
        )
        [System.IO.File]::WriteAllLines($localPropsPath, $newPropsContent)
        Write-Host "  [AUTO-REPARADO] 'local.properties' vinculado con el Android SDK detectado:" -ForegroundColor Green
        Write-Host "  sdk.dir = $sdkPath" -ForegroundColor Cyan
    } else {
        Write-Host "  [X] No se encontro la carpeta del Android SDK en las rutas habituales." -ForegroundColor Red
        Write-Host "      Abre Android Studio > Tools > SDK Manager y revisa la ruta de 'Android SDK Location'." -ForegroundColor Yellow
        exit 1
    }
} else {
    Write-Host "  [OK] local.properties configurado correctamente con Android SDK." -ForegroundColor Green
}
Write-Host ""

# ---------------------------------------------------------------------
# 3. VERIFICACION Y AJUSTE DE gradle.properties
# ---------------------------------------------------------------------
Write-Host "[3/4] Comprobando y ajustando configuracion de Gradle..." -ForegroundColor Yellow

$gradlePropsPath = Join-Path $projectRoot "gradle.properties"
if (Test-Path $gradlePropsPath) {
    $gProps = Get-Content $gradlePropsPath
    $repairedGProps = @()
    foreach ($line in $gProps) {
        if ($line -match '^\s*org\.gradle\.java\.home\s*=') {
            continue # Se sobreescribe abajo con el JDK exacto de esta maquina
        }
        $repairedGProps += $line
    }
    $escapedJavaHome = $chosenJdk.Replace("\", "\\")
    $repairedGProps += "org.gradle.java.home=$escapedJavaHome"
    [System.IO.File]::WriteAllLines($gradlePropsPath, $repairedGProps)
    Write-Host "  [AUTO-REPARADO] org.gradle.java.home vinculado a este equipo en gradle.properties." -ForegroundColor Green
}

# Auto-aceptar licencias de Android SDK si sdkmanager esta disponible
if ($realSdkPathToUse) {
    $sdkManager = Get-ChildItem -Path "$realSdkPathToUse\cmdline-tools" -Filter "sdkmanager.bat" -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $sdkManager) {
        $sdkManager = Get-ChildItem -Path "$realSdkPathToUse\tools" -Filter "sdkmanager.bat" -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1
    }
    if ($sdkManager) {
        try {
            "y`ny`ny`ny`ny`ny`ny`ny" | & $sdkManager.FullName --licenses >$null 2>&1
        } catch {}
    }
}
Write-Host ""

# ---------------------------------------------------------------------
# 4. PRUEBA DE COMPILACION CON AUTORRECUPERACION
# ---------------------------------------------------------------------
Write-Host "[4/4] Probando compilacion limpia del proyecto (assembleDebug)..." -ForegroundColor Yellow
Write-Host "      Descargando librerias faltantes y compilando codigo, por favor espera..." -ForegroundColor DarkGray
Write-Host ""

$gradlewBat = Join-Path $projectRoot "gradlew.bat"
$buildResult = & $gradlewBat "-Dorg.gradle.java.home=$chosenJdk" assembleDebug
$buildExitCode = $LASTEXITCODE

if ($buildExitCode -ne 0) {
    Write-Host ""
    Write-Host "  [!] Primera pasada fallo. Deteniendo daemons y limpiando caches..." -ForegroundColor Yellow
    & $gradlewBat "-Dorg.gradle.java.home=$chosenJdk" --stop >$null 2>&1
    & $gradlewBat "-Dorg.gradle.java.home=$chosenJdk" clean --refresh-dependencies
    Write-Host "  Reintentando assembleDebug..." -ForegroundColor Yellow
    & $gradlewBat "-Dorg.gradle.java.home=$chosenJdk" assembleDebug
    $buildExitCode = $LASTEXITCODE
}

Write-Host ""
if ($buildExitCode -eq 0) {
    Write-Host "=====================================================================" -ForegroundColor Green
    Write-Host "  [OK] FELICITACIONES! EL PROYECTO COMPILA AL 100% EN ESTA COMPUTADORA" -ForegroundColor Green
    Write-Host "=====================================================================" -ForegroundColor Green
    Write-Host "  El APK generado esta listo en:" -ForegroundColor White
    Write-Host "  app\build\outputs\apk\debug\app-debug.apk" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "  Para configurar la IP del servidor o tu clave de Gemini:" -ForegroundColor White
    Write-Host "  Ejecuta: scripts\configurar_servidor_y_api.bat" -ForegroundColor Cyan
    Write-Host "=====================================================================" -ForegroundColor Green
    exit 0
} else {
    Write-Host "=====================================================================" -ForegroundColor Red
    Write-Host "  [X] LA COMPILACION REQUIERE ATENCION MANUAL EN ANDROID STUDIO" -ForegroundColor Red
    Write-Host "=====================================================================" -ForegroundColor Red
    Write-Host "  Pasos para resolverlo en tu laptop:" -ForegroundColor Yellow
    Write-Host "  1. Abre Android Studio y ve a 'Tools > SDK Manager > SDK Platforms'." -ForegroundColor White
    Write-Host "     Marca y aplica 'Android 15 / 16 (API 35/36)'." -ForegroundColor White
    Write-Host "  2. En 'File > Settings > Build Tools > Gradle', asegurate de que:" -ForegroundColor White
    Write-Host "     'Gradle JDK' este configurado en 'jbr-17' o 'jbr-21'." -ForegroundColor White
    Write-Host "=====================================================================" -ForegroundColor Red
    exit 1
}
