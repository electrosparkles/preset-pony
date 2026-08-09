@echo off
REM build-jar.bat - Build PresetPony as a runnable JAR (Windows)

setlocal enabledelayedexpansion

set SOURCE_DIR=src\main\java
set BUILD_DIR=build
set CLASSES_DIR=%BUILD_DIR%\classes
set JAR_DIR=%BUILD_DIR%\jar
set JAR_NAME=preset-pony.jar
set MAIN_CLASS=com.electrosparkles.presetpony.PresetPony
set CLASSPATH=lib\hid4java-0.8.0.jar;lib\jna-5.19.1.jar;lib\jna-platform-5.19.1.jar

echo.
echo PresetPony - JAR Build (Windows)
echo ==================================================
echo.

REM Read version from version.properties
for /f "tokens=2 delims==" %%v in ('findstr "app.version" version.properties') do set APP_VERSION=%%v
echo Version: %APP_VERSION%
echo.

echo [1/5] Cleaning previous jar build...
if exist "%CLASSES_DIR%" rmdir /s /q "%CLASSES_DIR%"
if exist "%JAR_DIR%" rmdir /s /q "%JAR_DIR%"
mkdir "%CLASSES_DIR%"
mkdir "%JAR_DIR%"

echo [2/5] Compiling Java sources...
javac -d "%CLASSES_DIR%" -encoding UTF-8 -cp "%CLASSPATH%" %SOURCE_DIR%\com\electrosparkles\presetpony\*.java %SOURCE_DIR%\com\electrosparkles\presetpony\ui\*.java %SOURCE_DIR%\com\electrosparkles\presetpony\ui\shared\*.java %SOURCE_DIR%\com\electrosparkles\presetpony\ui\tabs\*.java %SOURCE_DIR%\com\electrosparkles\presetpony\ui\components\*.java
if errorlevel 1 (
    echo Error: Compilation failed
    exit /b 1
)
echo   Compilation successful

echo [3/5] Copying resources and writing filtered app-version.properties...
if exist "src\main\resources" (
    xcopy "src\main\resources" "%CLASSES_DIR%" /s /e /y >nul
)
if not exist "%CLASSES_DIR%\config" mkdir "%CLASSES_DIR%\config"
echo app.version=%APP_VERSION%> "%CLASSES_DIR%\config\app-version.properties"
echo   Resources copied

echo [4/5] Creating manifest...
set MANIFEST_FILE=%BUILD_DIR%\MANIFEST.MF
(
    echo Manifest-Version: 1.0
    echo Main-Class: %MAIN_CLASS%
    echo Class-Path: ../../lib/hid4java-0.8.0.jar ../../lib/jna-5.19.1.jar ../../lib/jna-platform-5.19.1.jar
    echo Implementation-Title: Mustang III Companion App
    echo Implementation-Version: %APP_VERSION%
    echo Implementation-Vendor: Mustang Project
) > "%MANIFEST_FILE%"
echo   Main-Class: %MAIN_CLASS%

echo [5/5] Creating JAR file...
pushd "%CLASSES_DIR%"
jar cmf "..\..\%MANIFEST_FILE%" "..\jar\%JAR_NAME%" .
popd
if errorlevel 1 (
    echo Error: JAR creation failed
    exit /b 1
)

echo.
echo ==================================================
echo Build complete!  JAR: %JAR_DIR%\%JAR_NAME%
echo Run with: run-jar.bat
echo ==================================================
echo.
