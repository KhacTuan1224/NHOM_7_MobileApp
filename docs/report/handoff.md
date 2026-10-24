# Bàn giao báo cáo RescueFarm

## Sản phẩm chính

- `report.docx`: báo cáo chỉnh sửa được, heading styles, TOC/danh mục hình/bảng/SEQ/REF và số trang bằng field Word.
- `report.pdf`: **58 trang** đã xuất bằng Microsoft Word; 7 trang ngang dành cho sơ đồ. Lý thuyết trong Chương 1 nằm trong khoảng 2 trang phần công cụ/kiểm thử, không mở rộng thêm một chương lý thuyết dài.
- 18 hình được đánh số tự động: 7 sơ đồ tự dựng và 11 cụm ảnh UI. Tổng 30 image instance nhúng trong DOCX gồm 7 diagram PNG, 22 screenshot và logo từ mẫu. Không dùng link ảnh ngoài để thay ảnh nhúng.
- 25 bảng được đánh số tự động, hàng bảng được giữ nguyên vẹn khi ngắt trang và có header lặp.
- `source/`: Markdown nội dung, Python dựng DOCX/sơ đồ/evidence/QA và PowerShell export Word. `assets/diagrams/`: SVG, PNG và JSON node/edge có thể sửa; source tập trung trong diagrams.py.
- `assets/screenshots/originals/`: **32 ảnh PNG gốc** chia theo actor, kể cả những ảnh không đưa vào báo cáo. `evidence/screenshots-manifest.json` chứa hash, APK, destination, bước, môi trường, thời điểm và giới hạn.
- `audit-matrix.md`, `demo-5-minutes.md`, `defense-questions.md`: ma trận, kịch bản và 15 câu hỏi bảo vệ.

## Mẫu và Chương 1

Mẫu do người dùng cung cấp: `docs/MOBILE FINAL PROJECT.pdf`, 13 trang. Lấy đúng học phần Phát triển ứng dụng Mobile, chuyên ngành CNTT, K50, giảng viên TS. Đặng Ngọc Hoàng Thành và tên năm thành viên từ mẫu/README. Chương 1 được trích xuất và giữ nguyên nội dung; QA xác nhận `chapter1Preserved: true` sau chuẩn hóa khoảng trắng PDF. Không chỉnh sửa file mẫu gốc.

Mẫu là PDF, không có DOCX gốc để bảo toàn OOXML/style chính xác. Báo cáo tái tạo cấu trúc sáu chương, bìa học thuật và logo từ mẫu; sửa đánh số mục bị trùng và bổ sung kiểm thử/giới hạn. Không tự chứng nhận “đúng mọi quy định UEH”. Các trang góc trang trí có ngày raster sẵn không được dùng làm bìa chỉnh sửa; ngày audit là 24/10/2026.

## Revision và thay đổi code

HEAD nền: `28adb48bb2e5e4ac8230379a1152f64a52251f5e`. Mốc audit 24/10/2026. Đây là HEAD cộng thay đổi thực nghiệm chưa commit. Working tree ban đầu có `docs/report/AI_REPORT_PROMPT_VI.md` thay đổi sẵn; đã giữ nguyên. Mẫu PDF là file người dùng thêm trong lượt làm việc.

Chỉ thêm cấu hình/demo và instrumentation cần cho bằng chứng:

| File | Thay đổi |
|---|---|
| app/build.gradle.kts | build type reportDemo kế thừa debug, package suffix và HTTP/media local; property reportTestBuildType, mặc định debug |
| app/src/reportDemo/AndroidManifest.xml | Application riêng, HTTP cleartext chỉ demo, tắt FCM auto-init/analytics |
| app/src/reportDemo/java/.../ReportDemoApplication.java | Bắt buộc project demo-rescuefarm; useEmulator cả Auth/Firestore; tắt persistence SDK; cờ offline chỉ demo |
| app/src/reportDemo/java/com/rescuefarm/AppCheckInstaller.java | No-op chỉ build type demo, không cấu hình provider ngoài |
| app/src/androidTest/.../ReportScreenshotEvidenceTest.java | Mở Fragment thật, assert destination/fixture, screenshot thật, Room process-restart và đọc PAID/ledger |
| firebase.report-demo.json | Firestore8088/Auth9098 localhost, Rules/indexes repo, project demo qua CLI |

Không đổi nghiệp vụ tiền/kho, header/logo/màu ứng dụng, tìm kiếm hoặc bộ chọn số lượng. Không phục hồi promotion đã bỏ. Không reset, stash, checkout đè, commit, push hoặc deploy. `implementation-diff.patch` lưu diff Gradle; các file mới được bàn giao trong source cây repo/gói ZIP.

APK: package `com.example.nhom_7_mobileapp.reportdemo`, version 1.0/code1. SHA-256 `d9425762529c3cf7f013d5ad195a1c4d90c04f728fc68804f0fe0e5bc803fa34`; kiểm tra giá trị máy đọc trong audit-results-current.json trước khi dùng. App APK không đổi khi chỉ thêm method cuối vào test APK; ảnh ghi cùng hash app. Không cài đè app thường hoặc dùng phiên đăng nhập của người dùng.

## Kết quả kiểm thử mới

| Kiểm tra | Kết quả | Log hoặc nguồn đếm |
|---|---|---|
| Android JVM chạy lại thật | 253 test, 0 failure/error/skipped | jvm-rerun.log và JUnit XML; có 2 RescueFundTime test trong tổng |
| assembleDebug và test APK debug | Thành công | android-final-diff.log |
| Lint debug | 0 error/fatal, 693 warning | XML Lint, audit-results-current.json; log lint-final-helper |
| Backend syntax/check | Exit0 | backend-check.log |
| Backend unit | 183 qua, 0 fail/skipped | backend-unit.log |
| Cloudinary Worker | 9 qua, 0 fail/skipped | Cùng backend-unit.log, phần Worker riêng |
| Firestore/HTTP/Rules Emulator | 173 qua, 0 fail/skipped | firestore-head-retry.log, project demo-rescuefarm |
| Navigation | 59 destination Fragment có class | navigation-head.log |
| Responsive/Room/Rotation | 11 qua, 0 fail/skipped | instrumentation-final-apk.log: 9 layout,1 SQLite,1 Activity rotation |
| UI bốn actor | Method chụp qua, Activity/destination thật | screenshots-final-apk.log |
| Cặp ảnh login ổn định | Cùng email hiển thị dọc/ngang; recreate assert qua | rotation-stable-pair-retry.log |
| Room sau tiến trình mới | prepare và capture đều qua; ảnh cuối tắt cả Wi-Fi/data máy ảo | offline-prepare-network-final.log, offline-device-network-final.log |
| Hũ và ledger | Các phase có assert cân bằng; method đọc PAID/RELEASE qua | fund-demo-* JSON/log, fund-final-details.log |

Không cộng số test thành số chức năng, coverage hay phần trăm hoàn thiện. Không cộng log audit cũ `course-audit-results.json` vào số mới. Backend/API suite dùng harness như trong source test; không thay thế tích hợp provider live.

## Thực nghiệm Hũ và offline

Hũ bắt đầu bằng cấu hình zero balance qua API, nhận 72.000đ demo bằng webhook giả hợp lệ và replay, phân bổ hai chuyến mỗi chuyến 36.000đ bằng module thật. Chuyến chính tạo 21:00 ngày24, hạn21:00 ngày25 giờ VN. ACCEPT không đổi hạn. DELIVER được tiêm clock sát hạn, VERIFY và PAY sau hạn; một chuyến RESERVED khác hết hạn bị reconcile hủy. PAY replay không chi hai lần. Kết thúc available36.000 + reserved0 + spent36.000 = donated72.000. Không gán thẳng status hoặc số dư để tạo ảnh, không xóa rescueFundRuns.

Giới hạn bắt buộc: HEAD kiểm tra tồn tại ảnh Cloudinary là stub trong harness; URL không phải ảnh upload/bàn giao/chuyển khoản thật. Sender FCM trong launcher bị chặn. Ảnh UI PAID và ledger chứng minh trạng thái phần mềm từ module thật, không chứng minh chuyển tiền ra ngân hàng hoặc bàn giao ngoài đời. Mã chi DEMO-NO-BANK-TRANSFER và số tài khoản đều là fixture giả. Ảnh cancelled ledger liên kết chuyến phụ, các phase chính liên kết cùng chuyến chính qua manifest.

Room UI: online catalog được cache bởi repository, app demo tắt persistence SDK, ghi cờ offline rồi force-stop riêng package demo. Tiến trình mới disable Firestore network trước mở Activity; assert catalog có “Cà chua DEMO” và chụp PNG. Ảnh cuối còn tắt Wi-Fi/data trên máy ảo test để NetworkStatusProvider thấy offline, không để nút refresh tiếp tục “Đang tải”. PowerShell finally bật lại cả Wi-Fi/data. Cache memory cũ không còn và SDK disk tắt, nên đây là bằng chứng UI đọc Room rõ hơn disableNetwork trong cùng process. Chỉ chứng minh catalog chữ; fixture không có ảnh Product nên chưa chứng minh Glide disk cache. Test xóa cờ/bật lại network SDK khi xong. Dấu phân cách nguồn gốc trên UI catalog còn chuỗi mã hóa “â€¢”; đây là lỗi trình bày nhỏ quan sát được, chưa sửa source nghiệp vụ để phục vụ ảnh.

## Lỗi môi trường đã giữ bằng chứng

- Gradle đầu bị sandbox chặn file lock trong cache người dùng; chạy lại với quyền cho cache và thành công.
- Firestore đầu timeout hub trong sandbox; lượt sau lỗi Java loopback/Unix socket Windows. Chạy lại với thư mục socket ngắn và heap512m: suite173 qua. Không tính các lượt lỗi là pass.
- Config Firebase đặt dưới docs/report/source từng bị CLI từ chối Rules ngoài project directory; đã thay bằng config ở root và loại file thử không dùng.
- Lượt offline ban đầu mất đăng ký adb, cài test APK không thành công rồi runner báo 0 test. Đã giữ log, kết nối lại và chạy đúng method; các log `*-retry` mới có giá trị pass.
- Ảnh login dọc đầu chụp trước frame vẽ email, dù assertion xoay đã qua. Đã bổ sung chờ frame và chụp lại hai ảnh, cùng email được nhìn thấy và assert. Lượt cài test APK ngay khi AVD cold boot chưa sẵn sàng PackageManager bị lỗi/0 test; sau sys.boot_completed=1 cài lại và method mới qua. Không tính lượt0test là pass.
- Lượt Gradle gộp testDebugUnitTest với property reportTestBuildType=reportDemo không có task debug unit tương ứng; tách JVM mặc định và build instrumentation demo thành hai lượt, đều qua.
- Packaged render_docx.py thất bại do không có LibreOffice trên Windows, kèm lỗi cleanup temp sandbox. Đã export bằng Microsoft Word COM chạy ẩn, cập nhật field và dùng Poppler raster toàn bộ PDF. Không dùng file giả làm PDF.

## Kiểm tra đầu ra

`evidence/document-qa.json`: 58 trang, 7 trang ngang, 30 image instance, 18 SEQ Figure, 25 SEQ Table, 3 TOC/danh mục, không có relationship file ngoài máy. Không có lỗi field “Reference not found”, không có placeholder mục lục chưa cập nhật; Chương1 giữ nguyên. Các trang text ngắn là bìa hoặc trang sơ đồ, không phải trang trắng bất thường.

Đã xem contact sheet toàn bộ trang và mở lớn các trang có hình/bảng, sửa đường kẻ Title mặc định, cạnh sơ đồ đi qua node trung gian, bảng tách một hàng sang trang sau và đoạn cuối Hũ bị rơi trang. Bản PNG kiểm tra cuối ở `evidence/render-verified/`; `render-final/` là vòng QA cũ, không phải bản cuối để đối chiếu số trang. Các source tài liệu và app ảnh không có credential/token; bản scan marker log không thấy private key, JWT hoặc password value. Đây không phải chứng nhận kiểm toán bảo mật toàn repo.

## Các mục nhóm còn phải xác nhận trước nộp

1. MSSV, lớp cụ thể, phân công được giao, sản phẩm bàn giao được nhóm duyệt, alias Git và tỷ lệ hoàn thành. Bảng trong báo cáo ghi “chờ xác nhận”, không chia đều hoặc suy từ commit count.
2. Mẫu PDF đã có, nhưng không có DOCX/style quy định đầy đủ; rà các yêu cầu định dạng của giảng viên nếu có thêm.
3. Chưa kiểm tra tương đồng UEH ở chế độ mặc định. Cần chạy PDF thật, lưu kết quả thật; không cam kết dưới20%.
4. Chưa xác minh Google OAuth thật, media/camera upload thật, FCM tới thiết bị thật, SePay live hoặc production deployment.
5. Chưa có ảnh checkout–QR–delivery–review liên tục, Campaign đầy đủ hoặc Flash/Mystery offer/claim trên UI. Code/test và màn rỗng được ghi đúng mức, không gọi là demo đầu cuối.
6. Cần nhóm diễn tập demo5phút và xác nhận người nói/người bấm. Kịch bản dự phòng dùng dữ liệu local công khai, không giả xác nhận tiền.

## Dựng lại và lưu ý dữ liệu

Theo `source/README.md`. Chỉnh Markdown/source rồi dựng DOCX, cập nhật Word fields, export PDF, raster và kiểm tra lại. Nếu chỉ sửa DOCX bằng tay thì giữ bản đó; không chạy builder ghi đè trước khi đưa sửa đổi về source.

`build/report-private/` chỉ chứa secret giả ngẫu nhiên của emulator phục vụ lượt test, không có trong gói bàn giao; không cần gửi file này cho giảng viên. App/HTTP/provider production không được triển khai trong lượt này. Các dịch vụ test riêng được dừng sau thu thập bằng chứng; dữ liệu AVD và source còn ở workspace để nhóm tái lập có kiểm soát.
