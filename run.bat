@echo off
REM run.bat - Run PresetPony GUI on Windows

setlocal enabledelayedexpansion

if not exist build (
    echo.
    echo ERROR: build/ directory not found!
    echo Please compile first:
    echo   compile.bat
    echo.
    exit /b 1
)

set CLASSPATH=build;src/main/resources;lib\hid4java-0.8.0.jar;lib\jna-5.19.1.jar;lib\jna-platform-5.19.1.jar

echo.
echo Launching PresetPony GUI...
echo.

java --enable-native-access=ALL-UNNAMED -cp %CLASSPATH% com.electrosparkles.presetpony.PresetPony

if errorlevel 1 (
    echo.
    echo ERROR: Failed to launch application.
    echo Ensure:
    echo   1. Mustang III v2 is connected via USB
    echo   2. All dependencies in lib/ are present
    echo   3. Java 16+ is installed and in PATH
    echo.
    pause
    exit /b 1
)