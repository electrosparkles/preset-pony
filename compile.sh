#!/bin/bash
# compile.sh - Build PresetPony on Linux/macOS
# Compiles all source files in src/main/java to build/

echo ""
echo "========================================"
echo "PresetPony - Build Script (Unix)"
echo "========================================"
echo ""

# Create build directory if it doesn't exist
mkdir -p build

# Set classpath with all library JARs
CLASSPATH="lib/hid4java-0.8.0.jar:lib/jna-5.19.1.jar:lib/jna-platform-5.19.1.jar"

# Compile main application
echo "Compiling main application sources..."
javac -d build -cp "$CLASSPATH" src/main/java/com/electrosparkles/presetpony/*.java
if [ $? -ne 0 ]; then
    echo ""
    echo "ERROR: Compilation failed!"
    exit 1
fi

# Compile tests (optional, don't fail on error)
echo ""
echo "Compiling test sources (optional)..."
javac -d build -cp "build:$CLASSPATH" src/test/java/com/electrosparkles/presetpony/*.java
if [ $? -eq 0 ]; then
    echo "Test compilation succeeded."
else
    echo "WARNING: Test compilation failed (continuing - tests are optional)"
fi

echo ""
echo "========================================"
echo "Build complete!"
echo ""
echo "To run the GUI application:"
echo "  ./run.sh"
echo ""
echo "To run tests:"
echo "  java -cp build com.electrosparkles.presetpony.MustangTestSuite"
echo ""
echo "To run command-line diagnostic:"
echo "  java -cp build:$CLASSPATH com.electrosparkles.presetpony.Main"
echo "========================================"
echo ""
