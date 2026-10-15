#!/bin/bash

# Configuration
PACKAGE_NAME="com.freeup.textactions"
MAIN_ACTIVITY=".MainActivity"
APK_PATH="app/build/outputs/apk/debug/app-debug.apk"

# Check for devices
echo "🔍 Checking for connected devices..."
# Prefer physical device first
DEVICE_SERIAL=$(adb devices -l | awk 'NR>1 && $2=="device" && $1 !~ /^emulator-/ {print $1; exit}')

if [ -z "$DEVICE_SERIAL" ]; then
    # Fallback to emulator if already running
    DEVICE_SERIAL=$(adb devices | grep -v "List of devices" | grep "device$" | head -n 1 | awk '{print $1}')
fi

EMULATOR_BIN="/Users/quanlm22/Library/Android/sdk/emulator/emulator"

if [ -z "$DEVICE_SERIAL" ]; then
    echo "⚠️ No devices found. Attempting to start emulator..."
    AVDS=$($EMULATOR_BIN -list-avds)
    if echo "$AVDS" | grep -q "Pixel_3"; then
        echo "🔄 Starting Pixel_3 emulator..."
        $EMULATOR_BIN -avd Pixel_3 -no-snapshot-load -no-audio -no-window &
        
        # Wait for device to be online
        echo "⏳ Waiting for emulator to start..."
        adb wait-for-device
        DEVICE_SERIAL=$(adb devices | grep -v "List of devices" | grep "device$" | head -n 1 | awk '{print $1}')
        
        # Wait for system to be ready
        echo "⏳ Waiting for system to be ready on $DEVICE_SERIAL..."
        while [ "$(adb -s "$DEVICE_SERIAL" shell getprop sys.boot_completed | tr -d '\r')" != "1" ]; do
            sleep 2
        done
        echo "✅ Emulator is ready!"
    else
        echo "❌ No suitable emulator found (Pixel_3 not in: $AVDS)"
        exit 1
    fi
fi

echo "🎯 Targeting device: $DEVICE_SERIAL"

echo "🚀 Building Debug APK..."
./gradlew assembleDebug

if [ $? -ne 0 ]; then
    echo "❌ Build failed!"
    exit 1
fi

echo "📲 Installing APK on $DEVICE_SERIAL..."
adb -s "$DEVICE_SERIAL" install -r "$APK_PATH"

if [ $? -ne 0 ]; then
    echo "❌ Installation failed!"
    exit 1
fi

echo "🎬 Launching App on $DEVICE_SERIAL..."
adb -s "$DEVICE_SERIAL" shell am start -n "$PACKAGE_NAME/$MAIN_ACTIVITY"

if [ $? -ne 0 ]; then
    echo "❌ Launch failed!"
    exit 1
fi

echo "📋 Showing logs (Ctrl+C to stop)..."
# Clear logs first
adb -s "$DEVICE_SERIAL" logcat -c

# Wait for PID to be available (can take a second after launch)
MAX_RETRIES=5
RETRY_COUNT=0
PID=""
while [ -z "$PID" ] && [ $RETRY_COUNT -lt $MAX_RETRIES ]; do
    PID=$(adb -s "$DEVICE_SERIAL" shell pidof -s $PACKAGE_NAME)
    if [ -z "$PID" ]; then
        sleep 1
        RETRY_COUNT=$((RETRY_COUNT + 1))
    fi
done

if [ -n "$PID" ]; then
    # Show logs for the package
    adb -s "$DEVICE_SERIAL" logcat --pid="$PID"
else
    echo "⚠️ Could not find PID for $PACKAGE_NAME. Showing all logs for the package name..."
    adb -s "$DEVICE_SERIAL" logcat | grep "$PACKAGE_NAME"
fi

