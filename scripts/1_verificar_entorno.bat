@echo off
setlocal EnableDelayedExpansion
title Diagnostico de Entorno - Mensajeria UNCP IA
cls

echo =====================================================================
echo           MENSAJERIA UNCP IA - DIAGNOSTICO DE ENTORNO
echo =====================================================================
echo.

cd /d "%~dp0\.."

REM 1. Verificacion de Git
echo [1/5] Verificando instalacion de Git...
where git >nul 2>&1
if !errorlevel! neq 0 (
    echo   [X] ERROR: Git no esta instalado o no esta en el PATH del sistema.
    echo       Instala Git desde: https://git-scm.com/
    goto :fin_error
) else (
    for /f "tokens=*" %%v in ('git --version') do echo   [OK] %%v
)
echo.

REM 2. Verificacion de Java / JDK
echo [2/5] Verificando version de Java (JDK 17 o superior requerido)...
where java >nul 2>&1
if !errorlevel! neq 0 (
    echo   [X] ERROR: Java no fue encontrado en el PATH.
    echo       Asegurate de instalar JDK 17 o superior (Adoptium / Eclipse / Oracle),
    echo       o agregar la carpeta 'jbr\bin' de Android Studio al PATH.
    goto :fin_error
) else (
    java -version 2>&1 | findstr /i "version"
    echo   [OK] Java detectado en el sistema.
)
echo.

REM 3. Verificacion de local.properties y Android SDK
echo [3/5] Verificando archivo local.properties y Android SDK...
if not exist "local.properties" (
    echo   [!] AVISO: No se encontro el archivo 'local.properties'.
    set "AUTO_SDK=C:\Users\%USERNAME%\AppData\Local\Android\Sdk"
    if exist "!AUTO_SDK!" (
        echo   [+] Se detecto automaticamente el Android SDK en:
        echo       !AUTO_SDK!
        echo       Creando 'local.properties' automatico...
        (
            echo ## Generado automaticamente por verificar_entorno.bat
            echo sdk.dir=C\:\\Users\\%USERNAME%\\AppData\\Local\\Android\\Sdk
            echo gemini.api.key=
            echo backend.base.url=http://10.0.2.2:8000/
        ) > "local.properties"
        echo   [OK] 'local.properties' ha sido creado exitosamente.
    ) else (
        echo   [X] ERROR: No se encontro el Android SDK en la ruta por defecto.
        echo       Por favor, copia 'local.properties.example' a 'local.properties'
        echo       y especifica la ruta de tu SDK (sdk.dir=...).
        goto :fin_error
    )
) else (
    echo   [OK] 'local.properties' presente en el proyecto.
    findstr /i "sdk.dir" local.properties
)
echo.

REM 4. Verificacion del Gradle Wrapper
echo [4/5] Verificando ejecucion de Gradle Wrapper...
if not exist "gradlew.bat" (
    echo   [X] ERROR: No se encontro gradlew.bat en la raiz del proyecto.
    goto :fin_error
)
call gradlew.bat --version >nul 2>&1
if !errorlevel! neq 0 (
    echo   [X] ERROR: Fallo al ejecutar gradlew.bat.
    echo       Asegurate de que la version de Java configurada sea compatible con Gradle 8.13.
    echo       En Android Studio ve a:
    echo       Settings ^> Build, Execution, Deployment ^> Build Tools ^> Gradle
    echo       y selecciona 'Gradle JDK: jbr-17' o superior.
    goto :fin_error
) else (
    echo   [OK] Gradle Wrapper (8.13) responde correctamente.
)
echo.

REM 5. Prueba de Compilacion de la Aplicacion
echo [5/5] Probando compilacion limpia del APK (assembleDebug)...
echo       (Esto puede tardar unos segundos, por favor espera...)
call gradlew.bat assembleDebug

if !errorlevel! neq 0 (
    echo.
    echo =====================================================================
    echo   [X] FALLO EN LA COMPILACION
    echo =====================================================================
    echo   Posibles causas:
    echo   1. Falta el paquete 'Android SDK Platform 36' en tu SDK Manager.
    echo      Abre Android Studio ^> Tools ^> SDK Manager y asegurate de tenerlo.
    echo   2. Tu version de Android Studio es antigua y no soporta AGP 8.13.
    goto :fin_error
)

echo.
echo =====================================================================
echo   [OK] EXCELENTE! EL PROYECTO COMPILA PERFECTAMENTE EN ESTA PC.
echo =====================================================================
echo   El archivo APK de depuracion se genero en:
echo   app\build\outputs\apk\debug\app-debug.apk
echo.
pause
exit /b 0

:fin_error
echo.
echo =====================================================================
echo   Se encontraron problemas que impiden compilar el proyecto.
echo   Revisa los pasos anteriores e intentalo nuevamente.
echo =====================================================================
echo.
pause
exit /b 1
