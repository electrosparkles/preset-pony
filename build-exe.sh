#!/bin/bash
# build-exe.sh - Package PresetPony as a native Linux app via jpackage
# Produces dist/PresetPony/bin/PresetPony (a self-contained app-image folder)
# plus a .desktop file so it shows up in application menus with its icon.
# Requires: JDK 17+ with jpackage on PATH (check with "jpackage --version").
#
# NOTE: jpackage/jlink build the runtime image using hard links. If this
# project folder is a shared/mounted folder (VirtualBox vboxsf, SMB, 9p,
# etc.), hard links usually aren't supported there and jlink fails with
# "Operation not permitted". To avoid that, all building happens in a
# local temp directory on the VM's own filesystem, then only the final
# app-image + .desktop file are copied (plain file copy, no links) back
# into this project's dist/ folder.

set -e

PROJECT_DIR="$(pwd)"
SOURCE_DIR="src/main/java"
RESOURCES_DIR="src/main/resources"
LIB_DIR="lib"
APP_NAME="PresetPony"
JAR_NAME_BASE="preset-pony"
MAIN_CLASS="com.electrosparkles.presetpony.PresetPony"
ICON_FILE="src/main/resources/icons/icon_256.png"
CLASSPATH="$LIB_DIR/hid4java-0.8.0.jar:$LIB_DIR/jna-5.19.1.jar:$LIB_DIR/jna-platform-5.19.1.jar"

# Read version from version.properties
APP_VERSION=$(head -1 version.properties | grep "app.version" | cut -d'=' -f2 | tr -d ' \r\n')
JAR_NAME="$JAR_NAME_BASE-$APP_VERSION.jar"

# Local (non-shared) scratch space for jlink's hard-linked runtime image
LOCAL_ROOT="$(mktemp -d /tmp/presetpony-jpackage.XXXXXX)"
CLASSES_DIR="$LOCAL_ROOT/classes"
INPUT_DIR="$LOCAL_ROOT/input"
LOCAL_DIST_DIR="$LOCAL_ROOT/dist"

DIST_DIR="dist"

cleanup() {
    rm -rf "$LOCAL_ROOT"
}
trap cleanup EXIT

echo ""
echo "PresetPony - Linux App Build via jpackage"
echo "=================================================="
echo "Version: $APP_VERSION"
echo ""

if ! command -v jpackage >/dev/null 2>&1; then
    echo "ERROR: jpackage not found on PATH. Requires JDK 17+."
    exit 1
fi

echo "[1/7] Cleaning previous build..."
rm -rf "$DIST_DIR/$APP_NAME"
mkdir -p "$CLASSES_DIR" "$INPUT_DIR"

echo "[2/7] Compiling Java sources..."
javac -d "$CLASSES_DIR" -encoding UTF-8 -cp "$CLASSPATH" \
  "$SOURCE_DIR"/com/electrosparkles/presetpony/*.java \
  "$SOURCE_DIR"/com/electrosparkles/presetpony/ui/*.java \
  "$SOURCE_DIR"/com/electrosparkles/presetpony/ui/shared/*.java \
  "$SOURCE_DIR"/com/electrosparkles/presetpony/ui/tabs/*.java \
  "$SOURCE_DIR"/com/electrosparkles/presetpony/ui/components/*.java

echo "[3/7] Copying resources (icons, config) and writing filtered app-version.properties..."
if [ -d "$RESOURCES_DIR" ]; then
    cp -r "$RESOURCES_DIR"/. "$CLASSES_DIR/"
fi
mkdir -p "$CLASSES_DIR/config"
echo "app.version=$APP_VERSION" > "$CLASSES_DIR/config/app-version.properties"

echo "[4/7] Creating manifest and jar..."
# Class-Path entries are relative to the jar's own folder, since jpackage
# copies the jar and all lib jars into the same app/ directory.
MANIFEST_FILE="$LOCAL_ROOT/MANIFEST.MF"
cat > "$MANIFEST_FILE" <<EOF
Manifest-Version: 1.0
Main-Class: $MAIN_CLASS
Class-Path: hid4java-0.8.0.jar jna-5.19.1.jar jna-platform-5.19.1.jar
Implementation-Title: Preset Pony
Implementation-Version: $APP_VERSION
Implementation-Vendor: Electrosparkles
EOF

( cd "$CLASSES_DIR" && jar cmf "$MANIFEST_FILE" "$INPUT_DIR/$JAR_NAME" . )

echo "[5/7] Staging dependency jars..."
cp "$PROJECT_DIR/$LIB_DIR"/*.jar "$INPUT_DIR/"

echo "[6/7] Running jpackage (in local scratch dir $LOCAL_ROOT)..."
jpackage \
    --type app-image \
    --input "$INPUT_DIR" \
    --dest "$LOCAL_DIST_DIR" \
    --name "$APP_NAME" \
    --main-jar "$JAR_NAME" \
    --main-class "$MAIN_CLASS" \
    --icon "$PROJECT_DIR/$ICON_FILE" \
    --app-version "$APP_VERSION" \
    --vendor "Mustang Project" \
    --java-options "--enable-native-access=ALL-UNNAMED"

echo "[7/7] Copying finished app-image back into project dist/..."
mkdir -p "$DIST_DIR"
cp -rL "$LOCAL_DIST_DIR/$APP_NAME" "$DIST_DIR/"

echo "[8/8] Creating zip archive with version in filename..."
(cd "$DIST_DIR" && zip -r "$JAR_NAME_BASE-$APP_VERSION-linux.zip" "$APP_NAME" -q)

APP_DIR="$PROJECT_DIR/$DIST_DIR/$APP_NAME"
BIN_PATH="$APP_DIR/bin/$APP_NAME"
ICON_DEST="$APP_DIR/lib/$APP_NAME.png"

# jpackage app-image doesn't install a .desktop file (that only happens for
# deb/rpm installers), so copy the icon alongside the binary and write one.
cp "$ICON_FILE" "$ICON_DEST"

DESKTOP_FILE="$DIST_DIR/$APP_NAME.desktop"
cat > "$DESKTOP_FILE" <<EOF
[Desktop Entry]
Type=Application
Name=$APP_NAME
Comment=Mustang III Companion App
Exec="$BIN_PATH"
Icon=$ICON_DEST
Categories=Audio;Music;Utility;
Terminal=false
EOF

echo ""
echo "=================================================="
echo "Build complete!"
echo "App folder:  $DIST_DIR/$APP_NAME"
echo "Run it:      $BIN_PATH"
echo "Packaged:    $DIST_DIR/$JAR_NAME_BASE-$APP_VERSION-linux.zip"
echo ""
echo "To add it to your application menu:"
echo "  cp \"$DESKTOP_FILE\" ~/.local/share/applications/"
echo "=================================================="
echo ""
