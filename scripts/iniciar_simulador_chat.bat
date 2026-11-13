@echo off
chcp 65001 >nul
title Simulador de Actividad Messenger Accesible - Demo en Vivo
echo =====================================================================
echo    INICIANDO SIMULADOR DE INTERACCION EN TIEMPO REAL (E2EE)
echo =====================================================================
echo.
cd /d "%~dp0.."
python scripts\simulador_actividad_chat.py
pause
