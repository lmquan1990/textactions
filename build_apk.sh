#!/bin/bash

# Exit on any error
set -e

echo "🚀 Starting APK build process..."

# Check if gradlew exists
if [ ! -f "./gradlew" ]; then
    echo "❌ Error: gradlew not found in the current directory."
    exit 1
fi

# Run gradle assembleDebug
./gradlew assembleDebug

# Find the generated APK
APK_PATH=$(find app/build/outputs/apk/debug -name "*.apk" | head -n 1)

if [ -f "$APK_PATH" ]; then
    echo "✅ Build Successful!"
    echo "📦 APK location: $APK_PATH"
else
    echo "❌ Error: APK file not found after build."
    exit 1
fi
