# Text Actions – Complete Action Reference

Text Actions là ứng dụng Android cung cấp các hành động thông minh dựa trên văn bản được chọn. Tất cả xử lý đều diễn ra trên thiết bị (offline), không gửi dữ liệu lên server.

## 📋 Mục lục

- [Phân loại Actions](#phân-loại-actions)
- [Contextual Actions](#contextual-actions)
- [Utility Actions](#utility-actions)
- [AI-Powered Features](#ai-powered-features)
- [Text Types](#text-types)

---

## Phân loại Actions

Actions được chia thành 2 nhóm:

1. **Contextual Actions**: Hiển thị dựa trên loại text được phát hiện (phone, email, address, date/time, GPS)
2. **Utility Actions**: Luôn có sẵn cho mọi loại text (copy, share, search, summarize, translate, etc.)

---

## Contextual Actions

### 📞 Communication Actions

#### `call` - Gọi điện
- **Loại text hỗ trợ**: `PHONE_NUMBER`
- **Mô tả**: Thực hiện cuộc gọi trực tiếp đến số điện thoại
- **Cách hoạt động**: 
  - Tự động chuẩn hóa số (loại bỏ ký tự đặc biệt)
  - Mở dialer với số đã chuẩn hóa
- **Priority**: 10 (cao nhất)
- **Icon**: Call

#### `sms` - Nhắn tin SMS
- **Loại text hỗ trợ**: `PHONE_NUMBER`
- **Mô tả**: Gửi tin nhắn SMS đến số điện thoại
- **Cách hoạt động**: Mở ứng dụng SMS với số đã điền sẵn
- **Priority**: 7
- **Icon**: SMS

#### `email` - Gửi Email
- **Loại text hỗ trợ**: `EMAIL`
- **Mô tả**: Soạn email gửi đến địa chỉ email
- **Cách hoạt động**: Mở email client với địa chỉ đã điền sẵn
- **Priority**: 8
- **Icon**: Email

#### `zalo` - Nhắn tin Zalo
- **Loại text hỗ trợ**: `PHONE_NUMBER`
- **Mô tả**: Mở chat Zalo với số điện thoại
- **Cách hoạt động**: Mở Zalo deep link `zalo.me/[số]`
- **Priority**: 8
- **Icon**: Send

### 🗺️ Location Actions

#### `map` - Mở trong Maps
- **Loại text hỗ trợ**: `ADDRESS`
- **Mô tả**: Xem vị trí trên Google Maps
- **Cách hoạt động**: Mở Google Maps với địa chỉ đã tìm kiếm
- **Priority**: 6
- **Icon**: Map

#### `copy_address` - Sao chép địa chỉ
- **Loại text hỗ trợ**: `ADDRESS`
- **Mô tả**: Sao chép địa chỉ vào clipboard
- **Cách hoạt động**: Copy địa chỉ đã phát hiện vào clipboard
- **Priority**: 6
- **Icon**: ContentCopy

#### `direction` - Chỉ đường
- **Loại text hỗ trợ**: `ADDRESS`, `GPS_COORDINATES`
- **Mô tả**: Dẫn đường đến địa chỉ/tọa độ
- **Cách hoạt động**: Mở Google Maps Navigation với điểm đến
- **Priority**: 7
- **Icon**: Explore

#### `gps_map` - Mở tọa độ GPS
- **Loại text hỗ trợ**: `GPS_COORDINATES`
- **Mô tả**: Xem tọa độ lat/long trên bản đồ
- **Cách hoạt động**: 
  - Parse tọa độ từ text (format: `lat, lon` hoặc `(lat, lon)`)
  - Mở Google Maps tại vị trí tọa độ
- **Priority**: 9
- **Icon**: LocationOn

#### `ride_hailing` - Đặt xe
- **Loại text hỗ trợ**: `ADDRESS`
- **Mô tả**: Tìm chuyến xe đến địa điểm (Grab/Be/XanhSM)
- **Cách hoạt động**: Mở bản đồ với địa chỉ, cho phép chọn app đặt xe
- **Priority**: 5
- **Icon**: DirectionsCar

### 📅 Calendar & Reminder Actions

#### `calendar` - Tạo sự kiện lịch
- **Loại text hỗ trợ**: `DATE_TIME`
- **Mô tả**: Tạo sự kiện lịch từ ngày/giờ được phát hiện
- **Cách hoạt động**: 
  - Parse ngày/giờ từ text
  - Tự động điền title, thời gian, địa điểm (nếu có)
  - Mở calendar app để tạo event
- **Điều kiện hiển thị**: Chỉ hiện khi có thời gian rõ ràng trong text
- **Priority**: 7
- **Icon**: CalendarToday

#### `reminder` - Tạo nhắc nhở
- **Loại text hỗ trợ**: `DATE_TIME`
- **Mô tả**: Tạo nhắc nhở trong lịch
- **Cách hoạt động**: Tạo reminder event với title từ text
- **Điều kiện hiển thị**: Chỉ hiện khi không có calendar action (ưu tiên calendar hơn)
- **Priority**: 6
- **Icon**: Notifications

### 🔗 Link Actions

#### `open_link` - Mở liên kết
- **Loại text hỗ trợ**: `URL`
- **Mô tả**: Mở URL trong trình duyệt
- **Cách hoạt động**: 
  - Tự động thêm `https://` nếu thiếu
  - Mở trong browser mặc định
- **Priority**: 7
- **Icon**: Google icon (custom)

#### `shorten_link` - Rút gọn link
- **Loại text hỗ trợ**: `URL`
- **Mô tả**: Rút gọn URL sử dụng TinyURL
- **Cách hoạt động**: Mở TinyURL với URL đã encode
- **Priority**: 4
- **Icon**: FlashOn

#### `reader_mode` - Chế độ đọc
- **Loại text hỗ trợ**: `URL`
- **Mô tả**: Mở URL ở chế độ đơn giản hóa (text-only)
- **Cách hoạt động**: Sử dụng txtify.it để hiển thị nội dung đơn giản
- **Priority**: 5
- **Icon**: ChromeReaderMode

### 👤 Contact Actions

#### `save_contact` - Lưu danh bạ
- **Loại text hỗ trợ**: `PHONE_NUMBER`, `EMAIL`
- **Mô tả**: Thêm liên hệ mới vào danh bạ
- **Cách hoạt động**: 
  - Tự động phát hiện phone hoặc email
  - Mở contact app với thông tin đã điền sẵn
- **Priority**: 8
- **Icon**: PersonAdd

---

## Utility Actions

### 📋 Basic Utilities

#### `copy` - Sao chép
- **Loại text hỗ trợ**: Tất cả (`ALL`)
- **Mô tả**: Sao chép văn bản vào clipboard
- **Cách hoạt động**: Copy text vào clipboard, hiển thị toast (Android < 13)
- **Priority**: 100 (cao nhất trong utilities)
- **Icon**: ContentCopy

#### `quick_share` - Chia sẻ nhanh
- **Loại text hỗ trợ**: Tất cả (`ALL`)
- **Mô tả**: Chia sẻ nội dung với ứng dụng khác
- **Cách hoạt động**: Mở Android Share Sheet
- **Priority**: 10
- **Icon**: Share

#### `search` - Tìm kiếm
- **Loại text hỗ trợ**: `PLAIN_TEXT`, `NUMBER`
- **Mô tả**: Tìm kiếm nội dung trên Google
- **Cách hoạt động**: 
  - Giới hạn query tối đa 200 ký tự
  - Mở Google Search với query đã encode
- **Điều kiện hiển thị**: Chỉ hiện khi không có direct action match (phone/email/url/address)
- **Priority**: 1
- **Icon**: Google icon (custom)

### 🤖 AI-Powered Utilities

#### `summarize` - Tóm tắt
- **Loại text hỗ trợ**: `PLAIN_TEXT`, `NUMBER`
- **Mô tả**: Tóm tắt nội dung bằng Extractive Summarization (local, rule-based)
- **Cách hoạt động**:
  - **Pipeline**: Sentence Splitter → Sentence Scoring → Select Top 1-2 Sentences
  - **Scoring factors**:
    - Độ dài câu (8-25 từ: +3 điểm)
    - Keyword frequency (từ xuất hiện ≥2 lần: +1 điểm, tối đa +5)
    - Vị trí (câu đầu: +4, câu cuối: +2)
    - Entity bonus (DATE/ORGANIZATION/LOCATION từ ML Kit: +1 mỗi loại)
  - **Output**: 1-2 câu quan trọng nhất, giữ nguyên thứ tự gốc
  - **Không viết lại**: Chỉ chọn câu từ text gốc, không tạo nội dung mới
- **Điều kiện hiển thị**: 
  - Text 30-2000 ký tự
  - Không phải pure direct type (phone/email/url/address)
- **Features**:
  - Read aloud cho kết quả summary
  - Copy summary
  - Save as note
- **Priority**: 3
- **Icon**: AutoAwesome

#### `translate` - Dịch
- **Loại text hỗ trợ**: `PLAIN_TEXT`
- **Mô tả**: Dịch văn bản offline bằng ML Kit
- **Cách hoạt động**:
  - Detect ngôn ngữ nguồn bằng ML Kit Language Identification
  - Dịch sang ngôn ngữ hệ thống (mặc định: Vietnamese)
  - Sử dụng ML Kit Translation (offline)
  - Tự động download model nếu cần
- **Features**:
  - Read aloud cho bản dịch (tốc độ 0.95x)
  - Summarize bản dịch (nếu >200 ký tự hoặc >2 câu)
  - Copy bản dịch
- **Priority**: 5
- **Icon**: Translate

#### `smart_read_aloud` - Đọc thông minh
- **Loại text hỗ trợ**: `PLAIN_TEXT`
- **Mô tả**: Đọc nội dung chính quan trọng (AI-filtered)
- **Cách hoạt động**:
  - **Content Filtering**: Loại bỏ header/footer, contact info, metadata
  - **Key Selection**: Áp dụng Extractive Summarization để chọn 2-3 câu quan trọng
  - **TTS**: Đọc với tốc độ 0.95x, detect language tự động
- **Điều kiện hiển thị**:
  - Text >300 ký tự
  - Nhiều câu (dấu hiệu bài viết/email/thông báo)
  - Không phải số/link/địa chỉ thuần túy
  - TalkBack không bật
- **Features**:
  - Tự động lọc nội dung không cần thiết
  - Chỉ đọc phần quan trọng
  - Stop/Resume control
- **Priority**: 5
- **Icon**: AutoAwesome

#### `tts` - Đọc to
- **Loại text hỗ trợ**: `PLAIN_TEXT`
- **Mô tả**: Đọc đoạn văn bản đã chọn bằng TextToSpeech
- **Cách hoạt động**: 
  - Sử dụng Android TTS engine
  - Ngôn ngữ: Vietnamese (mặc định)
  - Đọc toàn bộ text được chọn
- **Priority**: 4
- **Icon**: VolumeUp

### 📝 Note Actions

#### `save_note` - Lưu ghi chú
- **Loại text hỗ trợ**: Tất cả (`ALL`)
- **Mô tả**: Lưu nội dung vào Text Actions Notes
- **Cách hoạt động**: 
  - Mở bottom sheet để nhập title
  - Lưu vào local database
  - Tự động suggest title từ nội dung
- **Features**:
  - Tự động suggest title
  - Lưu vào local storage
  - Xem lại trong app
- **Priority**: 2
- **Icon**: EditNote

---

## AI-Powered Features

### Extractive Summarization
- **Phương pháp**: Rule-based, không dùng AI model
- **Pipeline**: 
  1. Sentence Splitter (dựa trên `.`, `!`, `?`)
  2. Sentence Scoring (length, keyword, position, entity)
  3. Select Top Sentences (1-2 câu)
- **Ưu điểm**: 
  - Hoàn toàn offline
  - Không bịa nội dung
  - Kết quả ổn định, dễ giải thích

### Smart Content Filtering
- **Loại bỏ**: Header/footer, contact info, metadata, câu quá ngắn, câu lặp
- **Giữ lại**: Câu có verb, câu có keyword quan trọng, câu ở vị trí quan trọng

### ML Kit Integration
- **Language Identification**: Detect ngôn ngữ cho Translate
- **Translation**: Offline translation với auto-download model
- **Entity Extraction**: Hỗ trợ scoring cho Summarize (DATE, ORGANIZATION, LOCATION)

---

## Text Types

App hỗ trợ các loại text sau:

- `PHONE_NUMBER`: Số điện thoại (format Việt Nam: +84 hoặc 0)
- `EMAIL`: Địa chỉ email
- `URL`: Liên kết web
- `ADDRESS`: Địa chỉ (có từ khóa: Đường, Phố, Quận, Huyện, etc.)
- `DATE_TIME`: Ngày/giờ (format: dd/mm/yyyy, yyyy-mm-dd, hoặc "Thứ 2", "Ngày mai")
- `GPS_COORDINATES`: Tọa độ GPS (format: `lat, lon` hoặc `(lat, lon)`)
- `NUMBER`: Số thuần túy
- `PLAIN_TEXT`: Văn bản thông thường

---

## Kích thước APK và tải model

Để giảm dung lượng cài đặt, app **không gói sẵn** toàn bộ model ML (OCR, QR, dịch, v.v.) trong APK:

- **Text recognition (OCR)** và **Barcode scanning (QR)**: dùng bản *unbundled* qua Google Play Services. Model được tải xuống lần đầu khi bạn dùng tính năng (hoặc sau khi cài từ Play Store, tùy thiết bị). Dung lượng APK giảm đáng kể so với bản gói model trong app.
- **Dịch offline (Translate)** và **Entity extraction**: model tải on-demand khi bạn dùng lần đầu (đã gọi `downloadModelIfNeeded()` trong code).
- **Digital ink (viết tay)**: model cũng tải khi cần.

Trên màn **AI Status**, "Local AI Engine" vẫn hiển thị **Ready to use**; lần đầu bạn bật Scan Text, Scan QR hoặc Translate, thiết bị có thể tải model qua mạng (Wi‑Fi/mobile) rồi dùng offline sau đó.

---

## Notes

- Tất cả actions đều hoạt động offline
- Actions được sắp xếp theo priority (cao → thấp)
- Contextual actions chỉ hiện khi phát hiện đúng loại text
- Utility actions luôn có sẵn (trừ khi bị disable trong settings)
- AI features (Summarize, Smart Read Aloud) sử dụng rule-based, không cần internet

---

## Version

Document này được tạo cho Text Actions app, cập nhật lần cuối: 2024
