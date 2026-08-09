#!/bin/bash
# run.sh - Run PresetPony GUI on Linux/macOS

if [ ! -d build ]; then
    echo ""
    echo "ERROR: build/ directory not found!"
    echo "Please compile first:"
    echo "  ./compile.sh"
    echo ""
    exit 1
fi

CLASSPATH="src/main/resources:build:lib/hid4java-0.8.0.jar:lib/jna-5.19.1.jar:lib/jna-platform-5.19.1.jar"

echo ""
echo "Launching PresetPony GUI..."
echo ""

java --enable-native-access=ALL-UNNAMED -cp "$CLASSPATH" com.electrosparkles.presetpony.PresetPony

if [ $? -ne 0 ]; then
    echo ""
    echo "ERROR: Failed to launch application."
    echo "Ensure:"
    echo "  1. Mustang III v2 is connected via USB"
    echo "  2. All dependencies in lib/ are present"
    echo "  3. Java 16+ is installed and in PATH"
    echo ""
    exit 1
fi
