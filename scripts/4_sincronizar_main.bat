@echo off
setlocal EnableDelayedExpansion
title Sincronizar Cambios de Main - Mensajeria UNCP IA
cls

cd /d "%~dp0\.."

echo =====================================================================
echo         MENSAJERIA UNCP IA - VERIFICAR ACTUALIZACIONES EN MAIN
echo =====================================================================
echo.

set "RAMA_ACTUAL="
for /f "tokens=*" %%b in ('git branch --show-current 2^>nul') do set "RAMA_ACTUAL=%%b"
if "!RAMA_ACTUAL!"=="" set "RAMA_ACTUAL=Desconocida"

echo Rama local actual: [!RAMA_ACTUAL!]
echo.
echo Consultando cambios remotos en GitHub (git fetch origin main)...
git fetch origin main >nul 2>&1

if !errorlevel! neq 0 (
    echo.
    echo [X] ERROR: No se pudo conectar con el repositorio remoto de GitHub.
    echo     Verifica tu conexion a internet e intenta nuevamente.
    echo.
    pause
    exit /b 1
)

echo [OK] Consulta remota completada.
echo.

:: Contar commits pendientes de descargar
set "COMMITS_ATRAS=0"
for /f %%c in ('git rev-list --count HEAD..origin/main 2^>nul') do set "COMMITS_ATRAS=%%c"

:: Contar commits locales no subidos
set "COMMITS_ADELANTE=0"
for /f %%c in ('git rev-list --count origin/main..HEAD 2^>nul') do set "COMMITS_ADELANTE=%%c"

if "!COMMITS_ATRAS!"=="0" (
    echo =====================================================================
    echo   [OK] TODO ESTA AL DIA
    echo =====================================================================
    echo   Tu rama local '!RAMA_ACTUAL!' esta sincronizada con 'origin/main'.
    echo   No hay commits nuevos en GitHub para descargar.
    if not "!COMMITS_ADELANTE!"=="0" (
        echo   Nota: Tienes !COMMITS_ADELANTE! commits locales pendientes de subir.
    )
    echo =====================================================================
    echo.
    pause
    exit /b 0
)

echo =====================================================================
echo   [!] SE ENCONTRARON ACTUALIZACIONES EN GITHUB (origin/main)
echo =====================================================================
echo   Hay !COMMITS_ATRAS! commits nuevos en GitHub pendientes de descargar:
echo.
git log HEAD..origin/main --oneline --color=always
echo.
echo =====================================================================
echo.

:: Comprobar si hay archivos modificados localmente sin commit
git status --porcelain | findstr /r "." >nul 2>&1
if !errorlevel! equ 0 (
    echo [AVISO]: Tienes archivos con cambios locales sin commitear en tu PC:
    git status -s
    echo.
    echo Se sugiere tener cuidado para no generar conflictos al fusionar.
    echo.
)

set "DESCARGAR="
set /p "DESCARGAR=Deseas descargar y aplicar estos cambios ahora (git pull origin main)? (S/N): "

if /i not "!DESCARGAR!"=="S" (
    echo.
    echo [i] Operacion cancelada. Tu rama local permanece sin cambios.
    echo.
    pause
    exit /b 0
)

echo.
echo Descargando y aplicando cambios (git pull origin main)...
git pull origin main

if !errorlevel! equ 0 (
    echo.
    echo =====================================================================
    echo   [OK] SINCRONIZACION COMPLETADA CON EXITO.
    echo =====================================================================
    echo   Tu rama local '!RAMA_ACTUAL!' ahora tiene los ultimos cambios de main.
    echo =====================================================================
) else (
    echo.
    echo =====================================================================
    echo   [X] HUBO CONFLICTOS O PROBLEMAS AL FUSIONAR LOS CAMBIOS.
    echo =====================================================================
    echo   Revisa los archivos en conflicto en Android Studio o con 'git status'.
    echo =====================================================================
)

echo.
pause
exit /b 0
