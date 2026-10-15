# Manual Test Cases

Đánh dấu: ☐ chưa test · ✅ pass · ❌ fail. Test cả dark và light mode ở các mục UI.

## Đã có unit test (chạy `./gradlew :app:testDebugUnitTest`, 18 test pass)

Không cần test tay các mục này:
- Nhận diện link (`UrlRulesTest`): `vendor.camera`, số, ngày, log tag bị loại; `https://`, `www.`, `.com/.vn/.io` được nhận.
- Format text trong History (`HistoryFormatTest`): gộp dòng trống, "…" không đứng riêng một dòng, cắt đúng 80 ký tự, tiếng Việt và emoji.
- Chuỗi hiển thị (`StringResourcesTest`): `app_name` = "Image to Text", không còn chữ "Snippet" trong tiếng Anh, không còn "ghi chú nhanh" trong tiếng Việt, subtitle Translate không còn "Vietnamese", có nhóm "Pinned".

Instrumented test (chạy trên máy thật: `./gradlew :app:connectedDebugAndroidTest`, 12 test pass trên Xiaomi M2102J2SC / Android 13):
- `HistoryDbTest`: chống trùng ở tầng repository (cùng loại + text trong 60s chỉ 1 bản, quá 60s thì thêm, khác loại/text không trùng, Undo khôi phục), `hasNoteWithContent`, thứ tự pin (pinned trước, mới ghim lên trên, bỏ pin về thứ tự ngày).
- `MigrationTest`: DB v6 có dữ liệu → migration 6→7 giữ History và Note (kể cả pin của note), record cũ mặc định chưa pin, pin được sau khi migrate.

Vì vậy các case 1.7, 3.2, 3.3 và 3.5 (phần dữ liệu) đã được kiểm tra ở tầng DB; bên dưới chỉ cần xem nhanh trên UI. Các luồng đi qua ViewModel/Activity (dịch, lưu note, mở lại record) vẫn phải test tay.

---

## 1. Không tạo record trùng

| # | Bước | Kết quả mong đợi | KQ |
|---|------|------------------|----|
| 1.1 | Chọn text → Translate → đổi ngôn ngữ đích 2-3 lần | Chỉ 1 record Translate mới | ☐ |
| 1.2 | Mở lại record Translate trong History | Không có record mới | ☐ |
| 1.3 | Mở lại record Copy / Share / Reminder / Open chat / Open media | Không có record mới | ☐ |
| 1.4 | Mở lại record "Save to note" | Không tạo thêm note, không thêm record | ☐ |
| 1.5 | Lưu note từ ProcessText, bấm Save 2 lần nhanh | Chỉ 1 note | ☐ |
| 1.6 | Quét QR → Save note (auto action), xoay màn hình | Chỉ 1 note | ☐ |
| 1.7 | Xóa 1 record → Undo | Record trở lại đúng 1 bản | ☐ |
| 1.8 | Chọn nhiều record → xóa → Undo | Tất cả trở lại, không trùng | ☐ |
| 1.9 | Bấm 2 action khác nhau trên cùng text | 2 record (hợp lệ) | ☐ |

## 2. Mở lại Translate từ History

| # | Bước | Kết quả mong đợi | KQ |
|---|------|------------------|----|
| 2.1 | Dịch 1 text → vào History → mở record vừa tạo | Chỉ hiện sheet Translate, không có sheet "Image to Text" | ☐ |
| 2.2 | Như 2.1 | Kết quả hiện ngay, không có "Translating…" | ☐ |
| 2.3 | Như 2.1 | Ngôn ngữ đích đúng với lần dịch đầu | ☐ |
| 2.4 | Trong sheet, đổi ngôn ngữ khác | Dịch mới, không thêm record | ☐ |
| 2.5 | Đóng sheet bằng nút X | Activity đóng, về History | ☐ |
| 2.6 | Vuốt xuống hoặc back để đóng | Như 2.5 | ☐ |
| 2.7 | Mở record Translate **cũ** (tạo trước bản cập nhật) | Vẫn dịch lại được, không crash | ☐ |
| 2.8 | Bấm nút copy trong sheet | Copy đúng bản dịch, có toast | ☐ |
| 2.9 | Tắt mạng, mở record Translate mới | Hiện bản đã lưu ngay | ☐ |
| 2.10 | Mở record bất kỳ | Không còn snackbar "Re-run action" | ☐ |

## 3. Pin

| # | Bước | Kết quả mong đợi | KQ |
|---|------|------------------|----|
| 3.1 | Bấm icon pin trên card Copy / Translate / Share | Icon tô màu, item lên nhóm **Pinned** | ☐ |
| 3.2 | Bấm lại icon pin | Bỏ ghim, về nhóm theo ngày | ☐ |
| 3.3 | Ghim A, B, C lần lượt | Thứ tự trong Pinned: C, B, A | ☐ |
| 3.4 | Tắt app, mở lại | Pin còn nguyên | ☐ |
| 3.5 | Cài đè lên bản cũ có dữ liệu (không xóa data) | Không crash. Dữ liệu đã có test migration; chỉ cần xem app mở được | ☐ |
| 3.6 | Ghim item loại Note ở card | Trong editor, pin đang bật | ☐ |
| 3.7 | Mở Note từ History → pin trong editor | Card cập nhật nhóm Pinned | ☐ |
| 3.8 | Mở item chưa có note tương ứng → pin → lưu | Note được tạo và ghim | ☐ |
| 3.9 | Ghim rồi swipe xóa → Undo | Item về lại, giữ trạng thái pin | ☐ |
| 3.10 | Chế độ chọn nhiều | Icon pin ẩn | ☐ |
| 3.11 | Màn Recent Actions riêng | Pin hoạt động, có nhóm Pinned | ☐ |
| 3.12 | Lọc chip (Translate, Notes...) khi có item ghim | Pinned vẫn nằm đầu | ☐ |

## 4. Giao diện History item

| # | Bước | Kết quả mong đợi | KQ |
|---|------|------------------|----|
| 4.1 | Item text ngắn | Title = nội dung, subtitle = `Loại · thời gian` | ☐ |
| 4.2 | Item OCR có nhiều dòng trống | Hiển thị gọn, không có "…" đứng riêng | ☐ |
| 4.3 | Các loại Copy, Translate, Share, Note, Reminder | Subtitle và icon đúng loại | ☐ |
| 4.4 | Dark / light mode | Đọc rõ | ☐ |

## 5. Màn OCR / ProcessText

| # | Bước | Kết quả mong đợi | KQ |
|---|------|------------------|----|
| 5.1 | Mở ProcessText | Title "Image to Text", nhỏ hơn trước, một dòng | ☐ |
| 5.2 | Có Re-capture + Show more | Hai nút cùng style, không viền, nền sáng hơn card, nằm giữa | ☐ |
| 5.3 | Chỉ có Show more (nguồn text/share) | Nút thu gọn, căn giữa | ☐ |
| 5.4 | Bấm Show more / Show less | Mở rộng / thu gọn đúng | ☐ |
| 5.5 | Bấm Re-capture → camera | Camera mở | ☐ |
| 5.6 | Ở camera bấm X hoặc back | **Quay lại** sheet Image to Text, text giữ nguyên | ☐ |
| 5.7 | Ở camera chụp ảnh | Text trong sheet cập nhật, không mở sheet thứ hai | ☐ |
| 5.8 | Sau 5.7 bấm back | Đóng đúng một sheet | ☐ |
| 5.9 | Re-capture với nguồn Gallery | Như camera, hủy thì quay lại | ☐ |
| 5.10 | Sheet Translate: nút copy | Nút tròn đặc màu primary, nổi hơn icon loa | ☐ |
| 5.11 | Utilities → "Translate text" | Subtitle đúng (không nhắc Vietnamese) | ☐ |

### 5b. Translate khi chưa có language model

Chuẩn bị: Settings → Apps → Image to Text → xóa dữ liệu, hoặc dùng cặp ngôn ngữ chưa từng dịch.

| # | Bước | Kết quả mong đợi | KQ |
|---|------|------------------|----|
| 5b.1 | Chưa có model, bấm Translate | **Không** tự tải. Sheet Translate hiện "Cần tải gói ngôn ngữ…" kèm tên các ngôn ngữ và nút "Download & translate" | ☐ |
| 5b.2 | Ở 5b.1 chọn ngôn ngữ đích khác (ô "To:") | Kiểm tra lại model cho ngôn ngữ mới; ngôn ngữ đã tải thì dịch ngay | ☐ |
| 5b.3 | Bấm "Download & translate" | Hiện "Downloading language model…", xong thì hiện bản dịch | ☐ |
| 5b.4 | Sau 5b.3 dịch lại cùng cặp ngôn ngữ | Dịch ngay, không hỏi tải nữa | ☐ |
| 5b.5 | Cặp không có tiếng Anh (vd Việt → Pháp) | Danh sách cần tải gồm cả English nếu thiếu | ☐ |
| 5b.6 | Đóng sheet khi đang ở màn "cần tải" | Không tải gì, không có record Translate mới trong History | ☐ |
| 5b.7 | Sau khi tải xong và dịch | Có đúng 1 record Translate | ☐ |
| 5b.8 | Mở record Translate cũ từ History khi chưa có model | Hiện bản dịch đã lưu, không hỏi tải | ☐ |

### 5c. Banner "Recent screenshot detected"

| # | Bước | Kết quả mong đợi | KQ |
|---|------|------------------|----|
| 5c.1 | Chụp màn hình, mở app | Banner hiện, có chữ nhỏ "Don't show again" dưới phụ đề | ☐ |
| 5c.2 | Bấm X | Banner ẩn; tắt hẳn app, mở lại: **không** hiện lại cho ảnh đó | ☐ |
| 5c.3 | Chụp ảnh mới sau 5c.2 | Banner hiện lại cho ảnh mới | ☐ |
| 5c.4 | Bấm "Don't show again" | Banner ẩn, snackbar báo đã tắt | ☐ |
| 5c.5 | Sau 5c.4 chụp thêm ảnh, mở app | Banner không hiện | ☐ |
| 5c.6 | Settings → General → "Screenshot shortcut" | Công tắc đang tắt (sau 5c.4); bật lên, về Home sau khi chụp ảnh | ☐ |
| 5c.7 | Sau 5c.6 | Banner hiện lại | ☐ |
| 5c.8 | Tắt công tắc trong Settings | Banner không hiện nữa | ☐ |
| 5c.9 | Bấm vào banner | Mở luồng quét ảnh như trước | ☐ |

## 6. Ask AI

| # | Bước | Kết quả mong đợi | KQ |
|---|------|------------------|----|
| 6.1 | Text thường, đã cài ChatGPT/Gemini | Chip hiện icon sparkle thay chữ cái | ☐ |
| 6.2 | Section Search Media | Icon Play | ☐ |
| 6.3 | Bấm chip | Mở đúng app/web như trước | ☐ |

## 7. Open Link (kiểm tra thực tế, logic đã có unit test)

| # | Bước | Kết quả mong đợi | KQ |
|---|------|------------------|----|
| 7.1 | OCR ảnh chụp log có `vendor.camera` | Không hiện Open Link | ☐ |
| 7.2 | OCR ảnh có link thật | Hiện Open Link, bấm mở trình duyệt | ☐ |

## 8. Tên "Note" và tên app (hiển thị thực tế)

| # | Bước | Kết quả mong đợi | KQ |
|---|------|------------------|----|
| 8.1 | Launcher | Tên app "Image to Text" | ☐ |
| 8.2 | Duyệt Home, editor, dialog xóa/khóa, widget, Settings export | Không còn chữ "Snippet" | ☐ |
| 8.3 | Đổi sang Tiếng Việt | Hiện "Ghi chú" | ☐ |

## 9. Hồi quy chung

| # | Bước | Kết quả mong đợi | KQ |
|---|------|------------------|----|
| 9.1 | Tạo note mới từ nút + | Lưu được, 1 record "Save note" | ☐ |
| 9.2 | Xóa, lưu trữ, khóa note | Như trước | ☐ |
| 9.3 | Quick Note tile / widget | 1 note, 1 record | ☐ |
| 9.4 | Backup / restore | Không lỗi | ☐ |
| 9.5 | Xoay màn hình ở sheet Translate và ProcessText | Không crash, không tạo record | ☐ |
| 9.6 | Quét screenshot → OCR → Translate | Luồng đầy đủ không lỗi | ☐ |
