# Dọn repository — 27/10/2026

Repo được giữ tập trung vào ứng dụng Android, backend Render, Cloudinary Worker và tài liệu chạy/setup.

## Đã dọn

- Test JVM và instrumentation trong `app/src/test`, `app/src/androidTest`.
- Test Node của backend/Worker, `functions/test-support`, test Auth/Firestore và audit cũ trong `tests`; giữ lại Markdown hướng dẫn.
- Hai cấu hình Emulator chỉ dành cho test: `firebase.test.json`, `firebase.auth-test.json`.
- Script audit, seed dữ liệu mẫu, demo bản đồ và migration/dọn dữ liệu dùng một lần trong `scripts`.
- Log crash/replay, log chạy kiểm tra, bản render từng trang báo cáo, ZIP báo cáo có thể tạo lại và file khóa Word tạm.
- Dependency, lệnh npm và bước CI chỉ phục vụ các test đã xóa.

## Giữ lại vì còn phục vụ dự án

- Toàn bộ Markdown, tài liệu báo cáo DOCX/PDF, ảnh/sơ đồ thiết kế và bằng chứng tổng hợp.
- Source Android `main`, `debug`, `release`, `reportDemo`; schema Room cho migration; tài nguyên và giấy phép.
- Backend, Worker, Firestore Rules/indexes/schema, Gradle Wrapper, cấu hình triển khai và workflow vận hành.
- `scripts/check_navigation.py`, `check-firestore-schema.cjs`: kiểm tra cấu trúc.
- `scripts/create_admin.py`, `publish-firestore-rules.cjs`, `update_vietnam_addresses.py`: tạo Admin, triển khai Rules và cập nhật dataset.
- `functions/scripts/bootstrap-admin.js`, `check-sepay-config.js`: setup Admin và kiểm tra SePay.
- Script dựng lại tài liệu/demo trong `docs/report/source`, `docs/report/part5/source`, `docs/report/assets/diagrams/overview`; giữ `firebase.report-demo.json` cho demo thủ công.
- Cấu hình cá nhân, cache công cụ và dữ liệu trong `build` có thể chứa snapshot/backup cơ sở dữ liệu hoặc máy ảo demo; không xem các dữ liệu này là rác để xóa hàng loạt.

## Lệnh kiểm tra hiện tại

```powershell
.\gradlew.bat :app:assembleDebug :app:lintDebug
npm --prefix functions run check
python scripts/check_navigation.py
```

Các lệnh trên kiểm tra build/lint, cú pháp JavaScript và đích điều hướng. Repo hiện không còn các bộ test hồi quy tự động. Những đường dẫn/lệnh test hoặc script đã xóa trong tài liệu audit/báo cáo cũ là thông tin lịch sử; không dùng chúng làm hướng dẫn chạy cho phiên bản hiện tại. Có thể phục hồi nguồn cũ từ lịch sử Git nếu cần tái lập audit.

Demo thủ công và dựng lại báo cáo: xem [hướng dẫn nguồn báo cáo](report/source/README.md). Kết quả test/screenshot đã lưu trong báo cáo không phải xác nhận rằng phiên bản sau dọn repo đã chạy lại những bộ test đó.

## Xác minh sau dọn

- Build APK debug và lint Android: thành công sau khi clean output cũ.
- Kiểm tra cú pháp backend/Worker: thành công.
- Kiểm tra điều hướng: đủ 60 Fragment.
- Không có Markdown hay source chạy chính bị xóa; 284 file được Git quản lý đã dọn, khoảng 47,4 MiB.
