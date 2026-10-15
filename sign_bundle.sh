#!/bin/bash
set -e

KEYSTORE="/Users/quanlm22/Desktop/Learning/keystore/text_actions/text_actions.jks"
BUNDLE="app/build/outputs/bundle/release/app-release.aab"
OUTPUT_DIR="app/release"
OUTPUT_BUNDLE="$OUTPUT_DIR/app-release.aab"

if [ ! -f "$BUNDLE" ]; then
    echo "❌ Error: Không tìm thấy file $BUNDLE. Vui lòng chạy ./gradlew bundleRelease trước."
    exit 1
fi

mkdir -p "$OUTPUT_DIR"
cp "$BUNDLE" "$OUTPUT_BUNDLE"

echo "🔐 Đang tiến hành ký file $OUTPUT_BUNDLE..."
jarsigner -verbose -sigalg SHA256withRSA -digestalg SHA-256 -keystore "$KEYSTORE" "$OUTPUT_BUNDLE" upload

echo "✅ Ký AAB thành công!"
echo "📦 File sẵn sàng upload lên Google Play Console: $OUTPUT_BUNDLE"
