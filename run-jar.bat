@echo off
REM run-jar.bat - Run PresetPony from the built JAR (Windows)

set JAR_FILE=build\jar\preset-pony.jar

if not exist "%JAR_FILE%" (
    echo.
    echo ERROR: %JAR_FILE% not found!
    echo Please build it first:
    echo   build-jar.bat
    echo.
    exit /b 1
)

echo.
echo Launching PresetPony from JAR...
echo.

java --enable-native-access=ALL-UNNAMED -jar "%JAR_FILE%"

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
