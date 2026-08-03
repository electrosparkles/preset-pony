@echo off
REM compile.bat - Build PresetPony on Windows
REM Compiles all source files in src/main/java to build/

setlocal enabledelayedexpansion

echo.
echo ========================================
echo PresetPony - Build Script (Windows)
echo ========================================
echo.

REM Check if build directory exists, create if not
if not exist build mkdir build

REM Set classpath with all library JARs
set CLASSPATH=lib\hid4java-0.8.0.jar;lib\jna-5.19.1.jar;lib\jna-platform-5.19.1.jar

REM Find all .java files in src/main/java/com/electrosparkles/presetpony/
echo Compiling main application sources...
javac -d build -cp %CLASSPATH% src\main\java\com\electrosparkles\presetpony\*.java
if errorlevel 1 (
    echo.
    echo ERROR: Compilation failed!
    exit /b 1
)

echo.
echo Compiling test sources (optional)...
javac -d build -cp build;%CLASSPATH% src\test\java\com\electrosparkles\presetpony\*.java
if errorlevel 1 (
    echo WARNING: Test compilation failed (continuing - tests are optional)
) else (
    echo Test compilation succeeded.
)

echo.
echo ========================================
echo Build complete!
echo.
echo To run the GUI application:
echo   run.bat
echo.
echo To run tests:
echo   java -cp build com.electrosparkles.presetpony.PresetPonyTestSuite
echo.
echo To run command-line diagnostic:
echo   java -cp build;%CLASSPATH% com.electrosparkles.presetpony.VolumeTest
echo ========================================
echo.
