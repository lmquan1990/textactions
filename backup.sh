#!/bin/bash

# Thư mục gốc của project
PROJECT_DIR="/Users/quanlm22/Desktop/Learning/textactions"
# Thư mục chứa file backup
BACKUP_DIR="/Users/quanlm22/Desktop/Learning"
# Định dạng thời gian ddMM_hhmm
TIMESTAMP=$(date +"%d%m_%H%M")
# Tên file zip
ZIP_NAME="text_actions_${TIMESTAMP}.zip"

echo "🚀 Bắt đầu quá trình backup cho project: $PROJECT_DIR"

# 1. Di chuyển vào thư mục project
cd "$PROJECT_DIR" || { echo "❌ Không tìm thấy thư mục project!"; exit 1; }

# 2. Dọn dẹp project (Clean)
echo "🧹 Đang dọn dẹp project (./gradlew clean)..."
if [ -f "./gradlew" ]; then
    ./gradlew clean
else
    echo "⚠️ Không tìm thấy gradlew, bỏ qua bước clean."
fi

# 3. Tạo file zip
echo "📦 Đang tạo file zip: $ZIP_NAME tại $BACKUP_DIR"
# Loại bỏ các thư mục không cần thiết để file zip nhẹ hơn
zip -r "${BACKUP_DIR}/${ZIP_NAME}" . -x "*.git*" "*/build/*" ".gradle/*" "*.idea*" "*.DS_Store"

if [ $? -eq 0 ]; then
    echo "✅ Backup thành công! File: ${BACKUP_DIR}/${ZIP_NAME}"
else
    echo "❌ Có lỗi xảy ra trong quá trình nén file."
    exit 1
fi
