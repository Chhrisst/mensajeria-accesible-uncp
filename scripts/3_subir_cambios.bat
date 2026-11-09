@echo off
setlocal EnableDelayedExpansion
title Subir Cambios a Git con Doble Confirmacion - Mensajeria UNCP IA
cls

cd /d "%~dp0\.."

echo =====================================================================
echo         MENSAJERIA UNCP IA - SUBIR CAMBIOS A GITHUB
echo =====================================================================
echo.

set "RAMA_ACTUAL="
for /f "tokens=*" %%b in ('git branch --show-current 2^>nul') do set "RAMA_ACTUAL=%%b"
if "!RAMA_ACTUAL!"=="" (
    echo [X] ERROR: No se detecto un repositorio Git valido o una rama activa.
    pause
    exit /b 1
)

echo Rama activa seleccionada: [!RAMA_ACTUAL!]
echo.
echo =====================================================================
echo Archivos modificados o nuevos detectados:
echo =====================================================================
git status -s
echo =====================================================================
echo.

git status --porcelain | findstr /r "." >nul 2>&1
if !errorlevel! neq 0 (
    echo [i] No hay ningun cambio pendiente para subir. Todo esta al dia.
    echo.
    pause
    exit /b 0
)

echo [1/2] PRIMERA CONFIRMACION:
set "CONFIRM_1="
set /p "CONFIRM_1=Deseas agregar estos archivos al commit para la rama '!RAMA_ACTUAL!'? (S/N): "

if /i not "!CONFIRM_1!"=="S" (
    echo.
    echo [!] Operacion cancelada por el usuario. No se realizo ningun commit ni subida.
    echo.
    pause
    exit /b 0
)

echo.
set "COMMIT_MSG="
set /p "COMMIT_MSG=Ingresa un mensaje claro y descriptivo para el commit: "
if "!COMMIT_MSG!"=="" (
    echo.
    echo [X] ERROR: El mensaje del commit no puede estar vacio.
    pause
    exit /b 1
)

echo.
echo Ejecutando 'git add -A'...
git add -A

echo Ejecutando 'git commit'...
git commit -m "!COMMIT_MSG!"

if !errorlevel! neq 0 (
    echo.
    echo [X] Ocurrio un error al crear el commit.
    pause
    exit /b 1
)

echo.
echo =====================================================================
echo [2/2] SEGUNDA CONFIRMACION CRITICA (DOBLE CONFIRMACION):
echo =====================================================================
echo   Estas a punto de ENVIAR (PUSH) tus cambios a GitHub:
echo   Repositorio: origin
echo   Rama destino: [!RAMA_ACTUAL!]
echo   Mensaje:     "!COMMIT_MSG!"
echo =====================================================================
echo.
set "CONFIRM_2="
set /p "CONFIRM_2=Estas 100%% seguro de hacer PUSH ahora a '!RAMA_ACTUAL!'? (S/N): "

if /i not "!CONFIRM_2!"=="S" (
    echo.
    echo [!] Subida remota CANCELADA.
    echo     Tus cambios quedaron guardados de forma segura en tu commit local,
    echo     pero NO se enviaron a GitHub todavia.
    echo     Puedes enviarlos mas tarde ejecutando 'git push origin !RAMA_ACTUAL!'.
    echo.
    pause
    exit /b 0
)

echo.
echo Enviando cambios a GitHub (git push origin !RAMA_ACTUAL!)...
git push -u origin "!RAMA_ACTUAL!"

if !errorlevel! equ 0 (
    echo.
    echo =====================================================================
    echo   [OK] CAMBIOS SUBIDOS EXITOSAMENTE A LA RAMA '!RAMA_ACTUAL!'.
    echo =====================================================================
) else (
    echo.
    echo [X] Hubo un problema al hacer push a GitHub.
    echo     Asegurate de tener conexion a internet y permisos en el repositorio.
)

echo.
pause
exit /b 0
