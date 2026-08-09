@echo off
REM build-exe.bat - Package PresetPony as a native Windows app (.exe) via jpackage
REM Produces dist\PresetPony\PresetPony.exe (a self-contained app-image folder).
REM Requires: JDK 17+ with jpackage on PATH (run "jpackage --version" to check).

setlocal enabledelayedexpansion

set SOURCE_DIR=src\main\java
set RESOURCES_DIR=src\main\resources
set BUILD_DIR=build\exe
set CLASSES_DIR=%BUILD_DIR%\classes
set INPUT_DIR=%BUILD_DIR%\input
set DIST_DIR=dist
set APP_NAME=PresetPony
set JAR_NAME=preset-pony.jar
set MAIN_CLASS=com.electrosparkles.presetpony.PresetPony
set ICON_FILE=src\main\resources\icons\icon.ico
set LIB_DIR=lib
set CLASSPATH=%LIB_DIR%\hid4java-0.8.0.jar;%LIB_DIR%\jna-5.19.1.jar;%LIB_DIR%\jna-platform-5.19.1.jar

echo.
echo PresetPony - EXE Build via jpackage (Windows)
echo ==================================================
echo.

where jpackage >nul 2>&1
if errorlevel 1 (
    echo ERROR: jpackage not found on PATH. Requires JDK 17+.
    exit /b 1
)

echo [1/6] Cleaning previous exe build...
if exist "%BUILD_DIR%" rmdir /s /q "%BUILD_DIR%"
if exist "%DIST_DIR%\%APP_NAME%" rmdir /s /q "%DIST_DIR%\%APP_NAME%"
mkdir "%CLASSES_DIR%"
mkdir "%INPUT_DIR%"

echo [2/6] Compiling Java sources...
javac -d "%CLASSES_DIR%" -encoding UTF-8 -cp "%CLASSPATH%" %SOURCE_DIR%\com\electrosparkles\presetpony\*.java %SOURCE_DIR%\com\electrosparkles\presetpony\ui\*.java %SOURCE_DIR%\com\electrosparkles\presetpony\ui\shared\*.java %SOURCE_DIR%\com\electrosparkles\presetpony\ui\tabs\*.java %SOURCE_DIR%\com\electrosparkles\presetpony\ui\components\*.java
if errorlevel 1 (
    echo Error: Compilation failed
    exit /b 1
)

echo [3/6] Copying resources (icons, config)...
if exist "%RESOURCES_DIR%" (
    xcopy "%RESOURCES_DIR%" "%CLASSES_DIR%" /s /e /y >nul
)

echo [4/6] Creating manifest and jar...
REM Class-Path entries are relative to the jar's own folder, since jpackage
REM copies the jar and all lib jars into the same app\ directory.
set MANIFEST_FILE=%BUILD_DIR%\MANIFEST.MF
(
    echo Manifest-Version: 1.0
    echo Main-Class: %MAIN_CLASS%
    echo Class-Path: hid4java-0.8.0.jar jna-5.19.1.jar jna-platform-5.19.1.jar
) > "%MANIFEST_FILE%"

pushd "%CLASSES_DIR%"
jar cmf "..\..\..\%MANIFEST_FILE%" "..\..\..\%INPUT_DIR%\%JAR_NAME%" .
popd
if errorlevel 1 (
    echo Error: JAR creation failed
    exit /b 1
)

echo [5/6] Staging dependency jars...
copy "%LIB_DIR%\*.jar" "%INPUT_DIR%\" >nul

echo [6/6] Running jpackage...
jpackage ^
    --type app-image ^
    --input "%INPUT_DIR%" ^
    --dest "%DIST_DIR%" ^
    --name "%APP_NAME%" ^
    --main-jar "%JAR_NAME%" ^
    --main-class "%MAIN_CLASS%" ^
    --icon "%ICON_FILE%" ^
    --app-version "1.0.0" ^
    --vendor "Mustang Project" ^
    --java-options "--enable-native-access=ALL-UNNAMED"
if errorlevel 1 (
    echo Error: jpackage failed
    exit /b 1
)

echo.
echo ==================================================
echo Build complete!
echo Run it: %DIST_DIR%\%APP_NAME%\%APP_NAME%.exe
echo.
echo (Optional) To build a real installer instead of an app-image folder,
echo install the WiX Toolset and re-run jpackage with --type exe or --type msi.
echo ==================================================
echo.
