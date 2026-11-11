@echo off
chcp 65001 >nul
title Claude Code - Mensajeria UNCP IA
cd /d "%~dp0\.."

echo ======================================================================
echo          INICIANDO CLAUDE CODE - MENSAJERIA UNCP IA
echo ======================================================================
echo.
echo Claude Code es el asistente de terminal para acelerar el desarrollo.
echo.
echo [1] Si es tu primera vez ejecutandolo:
echo     - Se abrira el navegador para iniciar sesion con tu cuenta de Anthropic.
echo     - Puedes acceder con tu suscripcion de Claude Pro / Team o con tu API Key.
echo     - Una vez autorizado en la web, regresa a esta ventana.
echo.
echo [2] Claude Code leera automaticamente CLAUDE.md con todas las reglas
echo     del proyecto, comandos de compilacion y arquitectura.
echo.
echo ======================================================================
echo.

where claude >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Claude Code no esta instalado en el sistema o no esta en el PATH.
    echo Intentando instalar via npm...
    npm install -g @anthropic-ai/claude-code
    if %ERRORLEVEL% neq 0 (
        echo [ERROR] Fallo la instalacion automatica. Verifica Node.js y npm.
        pause
        exit /b 1
    )
)

echo Iniciando Claude Code en el directorio del proyecto...
echo.
claude
echo.
pause