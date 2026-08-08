#!/bin/bash
# build-jar.sh - Build PresetPony as a runnable JAR (Linux/macOS)

set -e

SOURCE_DIR="src/main/java"
BUILD_DIR="build"
CLASSES_DIR="$BUILD_DIR/classes"
JAR_DIR="$BUILD_DIR/jar"
JAR_NAME="preset-pony.jar"
MAIN_CLASS="com.electrosparkles.presetpony.PresetPony"
CLASSPATH="lib/hid4java-0.8.0.jar:lib/jna-5.19.1.jar:lib/jna-platform-5.19.1.jar"

echo ""
echo "PresetPony - JAR Build (Unix)"
echo "=================================================="
echo ""

echo "[1/5] Cleaning previous jar build..."
rm -rf "$CLASSES_DIR" "$JAR_DIR"
mkdir -p "$CLASSES_DIR" "$JAR_DIR"

echo "[2/5] Compiling Java sources..."
javac -d "$CLASSES_DIR" -encoding UTF-8 -cp "$CLASSPATH" \
  "$SOURCE_DIR"/com/electrosparkles/presetpony/*.java \
  "$SOURCE_DIR"/com/electrosparkles/presetpony/ui/*.java \
  "$SOURCE_DIR"/com/electrosparkles/presetpony/ui/shared/*.java \
  "$SOURCE_DIR"/com/electrosparkles/presetpony/ui/tabs/*.java \
  "$SOURCE_DIR"/com/electrosparkles/presetpony/ui/components/*.java
echo "  Compilation successful"

echo "[3/5] Copying resources..."
if [ -d "src/main/resources" ]; then
    cp -r src/main/resources/. "$CLASSES_DIR/"
    echo "  Resources copied"
fi

echo "[4/5] Creating manifest..."
MANIFEST_FILE="$BUILD_DIR/MANIFEST.MF"
cat > "$MANIFEST_FILE" <<EOF
Manifest-Version: 1.0
Main-Class: $MAIN_CLASS
Class-Path: ../../lib/hid4java-0.8.0.jar ../../lib/jna-5.19.1.jar ../../lib/jna-platform-5.19.1.jar
Implementation-Title: Mustang III Companion App
Implementation-Version: 1.0.0
Implementation-Vendor: Mustang Project
EOF
echo "  Main-Class: $MAIN_CLASS"

echo "[5/5] Creating JAR file..."
( cd "$CLASSES_DIR" && jar cmf "../../$MANIFEST_FILE" "../jar/$JAR_NAME" . )

echo ""
echo "=================================================="
echo "Build complete!  JAR: $JAR_DIR/$JAR_NAME"
echo "Run with: ./run-jar.sh"
echo "=================================================="
echo ""
