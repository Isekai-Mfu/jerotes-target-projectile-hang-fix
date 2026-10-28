@echo off
setlocal
chcp 65001 >nul
if "%~1"=="" (
    echo Drag the official JerotesWarehouse 1.3.3 JAR onto this file.
    echo Or run: apply-fix.bat "path\to\official.jar"
    pause
    exit /b 2
)
set "JAVA_EXE=%APPDATA%\.minecraft\runtime\java-runtime-epsilon\bin\java.exe"
if not exist "%JAVA_EXE%" set "JAVA_EXE=%APPDATA%\.minecraft\runtime\java-runtime-delta\bin\java.exe"
if not exist "%JAVA_EXE%" set "JAVA_EXE=%APPDATA%\.minecraft\runtime\java-runtime-gamma\bin\java.exe"
if not exist "%JAVA_EXE%" set "JAVA_EXE=java"
"%JAVA_EXE%" -jar "%~dp0JerotesTargetProjectileHangFixer.jar" "%~1"
if errorlevel 1 (
    echo.
    echo Failed. Read the error above. No existing output or backup was overwritten.
    pause
    exit /b 1
)
echo.
pause
