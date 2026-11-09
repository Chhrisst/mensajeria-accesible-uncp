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
echo   [4] ELIMINAR una rama (con doble confirmacion)
echo   [5] Ver estado detallado (git status)
echo   [6] Salir
echo =====================================================================
echo.

set "OPC="
set /p "OPC=Elige una opcion (1-6): "

if "!OPC!"=="1" goto :cambiar_main
if "!OPC!"=="2" goto :crear_rama
if "!OPC!"=="3" goto :cambiar_existente
if "!OPC!"=="4" goto :eliminar_rama
if "!OPC!"=="5" goto :ver_status
if "!OPC!"=="6" goto :salir

echo.
echo Opcion no valida. Intenta nuevamente.
ping 127.0.0.1 -n 2 >nul
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

:eliminar_rama
echo.
echo =====================================================================
echo                ELIMINAR UNA RAMA DE TRABAJO
echo =====================================================================
echo   Ramas locales disponibles:
git branch
echo =====================================================================
echo.
set "RAMA_A_ELIMINAR="
set /p "RAMA_A_ELIMINAR=Ingresa el nombre de la rama que deseas eliminar (o presiona ENTER para cancelar): "

if "!RAMA_A_ELIMINAR!"=="" (
    echo Operacion cancelada.
    pause
    goto :menu
)

if /i "!RAMA_A_ELIMINAR!"=="main" (
    echo.
    echo [X] SEGURIDAD: Por proteccion, NO esta permitido eliminar la rama principal 'main'.
    pause
    goto :menu
)

if /i "!RAMA_A_ELIMINAR!"=="master" (
    echo.
    echo [X] SEGURIDAD: Por proteccion, NO esta permitido eliminar la rama 'master'.
    pause
    goto :menu
)

if /i "!RAMA_A_ELIMINAR!"=="!RAMA_ACTUAL!" (
    echo.
    echo [X] ERROR: Estas actualmente en la rama '!RAMA_A_ELIMINAR!'.
    echo     No puedes eliminar la rama en la que estas parado actualmente.
    echo     Cambia a 'main' primero (opcion 1) y luego intenta eliminarla.
    pause
    goto :menu
)

echo.
echo =====================================================================
echo [1/2] PRIMERA CONFIRMACION:
echo =====================================================================
echo   Rama seleccionada para eliminar: [!RAMA_A_ELIMINAR!]
echo.
set "CONFIRM_DEL_1="
set /p "CONFIRM_DEL_1=Deseas eliminar esta rama local? (S/N): "

if /i not "!CONFIRM_DEL_1!"=="S" (
    echo.
    echo [!] Eliminacion CANCELADA por el usuario. La rama no fue modificada.
    pause
    goto :menu
)

echo.
echo =====================================================================
echo [2/2] SEGUNDA CONFIRMACION CRITICA (ACCION IRREVERSIBLE):
echo =====================================================================
echo   ATENCION: Se borrara permanentemente la rama local [!RAMA_A_ELIMINAR!].
echo   Los commits no fusionados podrian perderse.
echo =====================================================================
echo.
set "CONFIRM_DEL_2="
set /p "CONFIRM_DEL_2=Confirmas al 100%% la eliminacion definitiva de '!RAMA_A_ELIMINAR!'? (S/N): "

if /i not "!CONFIRM_DEL_2!"=="S" (
    echo.
    echo [!] Eliminacion DEFINITIVA CANCELADA. La rama sigue existiendo intacta.
    pause
    goto :menu
)

echo.
echo Eliminando rama local '!RAMA_A_ELIMINAR!'...
git branch -d "!RAMA_A_ELIMINAR!" 2>nul
if !errorlevel! equ 0 (
    echo [OK] La rama local '!RAMA_A_ELIMINAR!' fue eliminada correctamente.
) else (
    echo [!] La rama contiene cambios que aun no han sido fusionados con 'main'.
    set "FORCE_DEL="
    set /p "FORCE_DEL=Deseas forzar la eliminacion de todas formas (git branch -D)? (S/N): "
    if /i "!FORCE_DEL!"=="S" (
        git branch -D "!RAMA_A_ELIMINAR!"
        echo [OK] Rama '!RAMA_A_ELIMINAR!' forzada y eliminada.
    ) else (
        echo [i] Se cancelo el forzado. La rama se conserva intacta.
    )
)

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
