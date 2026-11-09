@echo off
setlocal EnableDelayedExpansion
title Configurador de Servidor y API - Mensajeria UNCP IA
cls

cd /d "%~dp0\.."

echo =====================================================================
echo      MENSAJERIA UNCP IA - CONFIGURACION DE SERVIDOR Y API
echo =====================================================================
echo.
echo Este asistente te permite configurar la IP del servidor VPS y tu
echo clave de API de Gemini sin necesidad de modificar ningun codigo.
echo.
echo Los datos se guardan de forma segura en 'local.properties' (archivo
echo ignorado por Git que jamas se subira al repositorio publico).
echo =====================================================================
echo.

powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "$projectRoot = (Get-Item -Path '.').FullName;" ^
    "$localPropsPath = Join-Path $projectRoot 'local.properties';" ^
    "$sdkDir = '';" ^
    "$currentKey = '';" ^
    "$currentUrl = '';" ^
    "if (Test-Path $localPropsPath) {" ^
    "    Get-Content $localPropsPath | ForEach-Object {" ^
    "        if ($_ -match '^\s*sdk\.dir\s*=\s*(.*)$') { $sdkDir = $matches[1].Trim() }" ^
    "        if ($_ -match '^\s*gemini\.api\.key\s*=\s*(.*)$') { $currentKey = $matches[1].Trim() }" ^
    "        if ($_ -match '^\s*backend\.base\.url\s*=\s*(.*)$') { $currentUrl = $matches[1].Trim() }" ^
    "    }" ^
    "}" ^
    "if (-not $sdkDir) {" ^
    "    $defaultSdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk';" ^
    "    if (Test-Path $defaultSdk) { $sdkDir = $defaultSdk.Replace('\', '\\') } else { $sdkDir = 'C:\\Users\\' + $env:USERNAME + '\\AppData\\Local\\Android\\Sdk' }" ^
    "}" ^
    "Write-Host '--- ESTADO ACTUAL ---' -ForegroundColor Yellow;" ^
    "if ($currentUrl) { Write-Host ('  Servidor actual : ' + $currentUrl) -ForegroundColor Cyan } else { Write-Host '  Servidor actual : (No configurado)' -ForegroundColor DarkGray };" ^
    "if ($currentKey) {" ^
    "    $masked = if ($currentKey.Length -gt 8) { $currentKey.Substring(0, 6) + '...' + $currentKey.Substring($currentKey.Length - 4) } else { '********' };" ^
    "    Write-Host ('  API Key Gemini  : ' + $masked) -ForegroundColor Cyan;" ^
    "} else { Write-Host '  API Key Gemini  : (No configurada)' -ForegroundColor DarkGray };" ^
    "Write-Host '';" ^
    "Write-Host '1. SERVIDOR BACKEND / VPS' -ForegroundColor Green;" ^
    "Write-Host '   Ingresa la direccion IP (ej. 150.136.172.213) o la URL completa:';" ^
    "Write-Host '   (Presiona ENTER para mantener el valor actual)';" ^
    "$rawUrl = Read-Host '   > ';" ^
    "if ($rawUrl -and $rawUrl.Trim().Length -gt 0) {" ^
    "    $cleanUrl = $rawUrl.Trim();" ^
    "    if (-not ($cleanUrl -match '^https?://')) { $cleanUrl = 'http://' + $cleanUrl };" ^
    "    if (-not ($cleanUrl -match ':\d+')) { $cleanUrl = $cleanUrl + ':8000' };" ^
    "    if (-not $cleanUrl.EndsWith('/')) { $cleanUrl = $cleanUrl + '/' };" ^
    "    $currentUrl = $cleanUrl;" ^
    "} elseif (-not $currentUrl) { $currentUrl = 'http://10.0.2.2:8000/' };" ^
    "Write-Host '';" ^
    "Write-Host '2. CLAVE DE API DE GOOGLE GEMINI' -ForegroundColor Green;" ^
    "Write-Host '   Ingresa tu API Key (ej. AIzaSy...):';" ^
    "Write-Host '   (Presiona ENTER para mantener el valor actual)';" ^
    "$rawKey = Read-Host '   > ';" ^
    "if ($rawKey -and $rawKey.Trim().Length -gt 0) { $currentKey = $rawKey.Trim() };" ^
    "$content = @(" ^
    "    '## Archivo local privado generado por configurar_servidor_y_api.bat'," ^
    "    '## NO SUBIR ESTE ARCHIVO A CONTROL DE VERSIONES (GIT)'," ^
    "    ('sdk.dir=' + $sdkDir)," ^
    "    ''," ^
    "    ('gemini.api.key=' + $currentKey)," ^
    "    ('backend.base.url=' + $currentUrl)" ^
    ");" ^
    "[System.IO.File]::WriteAllLines($localPropsPath, $content);" ^
    "$backendEnvPath = Join-Path $projectRoot 'backend\.env';" ^
    "if (Test-Path (Join-Path $projectRoot 'backend')) {" ^
    "    $envLines = @(" ^
    "        'POSTGRES_DB=mensajeria_uncp'," ^
    "        'POSTGRES_USER=admin_uncp'," ^
    "        'POSTGRES_PASSWORD=uncp_secure_password_2026'," ^
    "        'POSTGRES_PORT=5432'," ^
    "        'ADMINER_PORT=8086'," ^
    "        ('GEMINI_API_KEY=' + $currentKey)" ^
    "    );" ^
    "    [System.IO.File]::WriteAllLines($backendEnvPath, $envLines);" ^
    "}" ^
    "Write-Host '';" ^
    "Write-Host '=====================================================================' -ForegroundColor Green;" ^
    "Write-Host '  [OK] CONFIGURACION ACTUALIZADA EXITOSAMENTE' -ForegroundColor Green;" ^
    "Write-Host '=====================================================================' -ForegroundColor Green;" ^
    "Write-Host ('  Servidor configurado : ' + $currentUrl) -ForegroundColor Cyan;" ^
    "Write-Host ('  Clave Gemini activa  : ' + $(if ($currentKey) { 'Configurada (' + $currentKey.Substring(0, [Math]::Min(6, $currentKey.Length)) + '...)' } else { 'Vacia' })) -ForegroundColor Cyan;" ^
    "Write-Host '  Archivo guardado en  : local.properties' -ForegroundColor Cyan;" ^
    "Write-Host '=====================================================================' -ForegroundColor Green;"

echo.
echo Presiona una tecla para salir...
pause >nul
exit /b 0
