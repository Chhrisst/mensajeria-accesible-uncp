@echo off
setlocal
title Diagnostico y Reparacion Automatica - Mensajeria UNCP IA
cls

cd /d "%~dp0\.."

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0diagnostico_y_reparacion.ps1"

if %errorlevel% neq 0 (
    echo.
    echo =====================================================================
    echo   Se encontraron problemas durante la verificacion.
    echo =====================================================================
    echo.
)

pause
exit /b %errorlevel%
