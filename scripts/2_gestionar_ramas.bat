@echo off
setlocal EnableDelayedExpansion
title Gestion de Ramas Git - Mensajeria UNCP IA

cd /d "%~dp0\.."

:menu
cls
echo =====================================================================
echo             MENSAJERIA UNCP IA - GESTION DE RAMAS GIT
echo =====================================================================
echo.

set "RAMA_ACTUAL="
for /f "tokens=*" %%b in ('git branch --show-current 2^>nul') do set "RAMA_ACTUAL=%%b"
if "!RAMA_ACTUAL!"=="" set "RAMA_ACTUAL=Desconocida"

echo   RAMA ACTUAL: [!RAMA_ACTUAL!]
echo.
echo   Ramas locales disponibles:
git branch
echo.
echo =====================================================================
echo   [1] Cambiar a la rama principal (main) y actualizar (git pull)
echo   [2] Crear una NUEVA rama a partir de 'main'
echo   [3] Cambiar a otra rama existente
echo   [4] Ver estado detallado (git status)
echo   [5] Salir
echo =====================================================================
echo.

set "OPC="
set /p "OPC=Elige una opcion (1-5): "

if "!OPC!"=="1" goto :cambiar_main
if "!OPC!"=="2" goto :crear_rama
if "!OPC!"=="3" goto :cambiar_existente
if "!OPC!"=="4" goto :ver_status
if "!OPC!"=="5" goto :salir

echo.
echo Opcion no valida. Intenta nuevamente.
timeout /t 2 >nul
goto :menu

:cambiar_main
echo.
echo Cambiando a rama 'main'...
git checkout main
echo Actualizando cambios remotos con git pull...
git pull origin main
echo.
echo Listo.
pause
goto :menu

:crear_rama
echo.
echo Asegurando estar en 'main' actualizado antes de ramificar...
git checkout main
git pull origin main
echo.
set "NUEVA_RAMA="
set /p "NUEVA_RAMA=Ingresa el nombre de la nueva rama (ej. feature/mi-modulo): "
if "!NUEVA_RAMA!"=="" (
    echo El nombre de la rama no puede estar vacio.
    pause
    goto :menu
)
git checkout -b "!NUEVA_RAMA!"
echo.
echo [OK] Ahora estas trabajando en la rama: !NUEVA_RAMA!
pause
goto :menu

:cambiar_existente
echo.
set "RAMA_DESTINO="
set /p "RAMA_DESTINO=Ingresa el nombre exacto de la rama a la que deseas cambiar: "
if "!RAMA_DESTINO!"=="" (
    echo No especificaste ninguna rama.
    pause
    goto :menu
)
git checkout "!RAMA_DESTINO!"
echo.
pause
goto :menu

:ver_status
echo.
git status
echo.
pause
goto :menu

:salir
exit /b 0
