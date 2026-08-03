#!/bin/bash
# run-jar.sh - Run PresetPony from the built JAR (Linux/macOS)

JAR_FILE="build/jar/preset-pony.jar"

if [ ! -f "$JAR_FILE" ]; then
    echo ""
    echo "ERROR: $JAR_FILE not found!"
    echo "Please build it first:"
    echo "  ./build-jar.sh"
    echo ""
    exit 1
fi

echo ""
echo "Launching PresetPony from JAR..."
echo ""

java --enable-native-access=ALL-UNNAMED -jar "$JAR_FILE"

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
