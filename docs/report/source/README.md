# Nguồn báo cáo RescueFarm

`report-content.md` là nội dung Chương 2 trở đi. `build_report.py` giữ nguyên Chương 1 bằng trích xuất PDF người dùng, tạo cover/heading/TOC/SEQ/REF, nhúng ảnh và sơ đồ. Không sửa `report.docx` rồi ghi đè bằng builder nếu muốn giữ sửa tay: chuyển các sửa nội dung về Markdown trước khi dựng lại.

`diagrams.py` chứa node/edge/route, xuất JSON editable, SVG và PNG 2x. `evidence.py` tổng hợp metadata nguồn/Git, JUnit XML, Lint và manifest ảnh. `qa.py` kiểm tra PDF/DOCX và tạo contact sheet từ PNG đã raster hóa. `render-word.ps1` dùng Word chạy ẩn để cập nhật field và xuất PDF; không cần LibreOffice cài thêm.

## Dựng tài liệu

1. Từ root repo, dùng runtime Python được Codex `load_workspace_dependencies` trả về, có python-docx/Pillow/pypdf/lxml. Giữ `docs/MOBILE FINAL PROJECT.pdf` người dùng cung cấp tại vị trí hiện tại.
2. Chạy `docs/report/source/diagrams.py`, rồi `evidence.py` khi có các log test/JUnit/APK tương ứng. Không dùng các số liệu JSON cũ nếu code/test đã đổi.
3. Chạy `build_report.py`; trên Windows có Microsoft Word, chạy `render-word.ps1`. Script thay đổi file DOCX vừa tạo để lưu cached fields và xuất report.pdf. Đây là đường export đã dùng, sau khi packaged render_docx.py thất bại do thiếu LibreOffice.
4. Dùng Poppler `pdftoppm -r 120 -png docs/report/report.pdf docs/report/evidence/render-verified/page` rồi chạy `qa.py`. Kiểm tra contact sheet của mọi trang và mở lớn trang có ảnh/bảng/sơ đồ. Không xem text extraction là đủ cho layout QA.

## Dựng môi trường Android demo

- Chỉ dùng AVD riêng và dữ liệu mới, không gỡ/clear data ứng dụng của người dùng. Máy ảo audit là emulator-5580 API33, data ở build/report-avd. APK có hậu tố `.reportdemo`.
- Copy `google-services-demo.template.json` vào `app/src/reportDemo/google-services.json`. File chỉ chứa dữ liệu giả không hoạt động ngoài emulator. Cấu hình Android kiểm tra projectId bằng `demo-rescuefarm` rồi gắn Auth10.0.2.2:9098, Firestore10.0.2.2:8088; HTTP3008 được override theo build type. SDK persistence tắt riêng demo để chứng minh Room.
- Chạy Firebase CLI từ root: `npx --yes firebase-tools emulators:start --project demo-rescuefarm --only "firestore,auth" --config firebase.report-demo.json`. Cần JDK phù hợp. Máy audit cần JAVA_TOOL_OPTIONS với `-Djdk.net.unixdomain.tmpdir=D:/NHOM_7_MobileApp/build/no-unix-sockets` và heap emulator512m để tránh lỗi socket/thiếu RAM.
- Đặt FIRESTORE_EMULATOR_HOST=127.0.0.1:8088 và FIREBASE_AUTH_EMULATOR_HOST=127.0.0.1:9098 trước seed/launcher. `seed-demo.cjs` tạo tài khoản/dữ liệu giả, mật khẩu ngẫu nhiên chỉ ghi build/report-private/auth.json, không log hoặc đóng gói. `demo-backend.cjs` chỉ thay initialization trong bản compile test của server.js, vẫn dùng xác thực Auth Emulator thật; sender FCM bị stub.
- Build APK demo bằng `gradlew.bat :app:assembleReportDemo`; cài bằng adb và thao tác/chụp ảnh thủ công. Các class instrumentation chụp bằng chứng đã được xóa khi dọn repo, vì vậy không còn build/cài test APK hay chạy các method capture cũ.
- `fund-demo.cjs setup/accept/deliver/expire-verify/pay` chạy module Hũ thật với clock tiêm. setup từ chối quỹ đã tồn tại; chỉ chạy trên môi trường demo mới. Không xóa rescueFundRuns hoặc sửa deadline cũ để chạy lại. HEAD ảnh là stub; không upload, bàn giao hoặc chuyển tiền thật. Sau mỗi phase chụp màn hình thủ công; cuối kiểm tra badge PAID và ledger RELEASE. JSON evidence không có token/secret.

## Kiểm tra lại sau thay đổi code

Build/Lint: `gradlew.bat :app:assembleDebug :app:lintDebug`. Backend/Worker: `npm --prefix functions run check`. Điều hướng: `python scripts/check_navigation.py`. Các bộ test JVM, instrumentation và Emulator đã được xóa; số liệu test trong báo cáo/evidence là lịch sử, không phải kết quả xác minh phiên bản hiện tại. Xem [chi tiết dọn repo](../../repository-cleanup.md).
