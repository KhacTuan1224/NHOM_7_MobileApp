# Đối chiếu yêu cầu đồ án Mobile — RescueFarm

Ngày kiểm tra: 24/10/2026. HEAD được đối chiếu: `bf648f2fbcbd9cc3b8a0eb319e3c23fda3f3186f` và các thay đổi chưa commit của lượt kiểm tra này. Nguồn tiêu chí là nội dung và ảnh đề bài người dùng cung cấp. Đây là tài liệu tiền kiểm để viết báo cáo, chưa phải báo cáo PDF theo mẫu.

## 1. Kết luận có giới hạn

Repo phù hợp đề tài **thương mại điện tử giải cứu nông sản**, có chiến dịch và tương tác cộng đồng. Không cần triển khai cả thương mại điện tử, tin tức và mạng xã hội như ba sản phẩm độc lập. Firebase Cloud Firestore, bố cục responsive, API hỗ trợ và Room/SQLite đều có triển khai thực tế, không chỉ khai báo dependency.

Chưa có cơ sở kết luận “đã đáp ứng toàn bộ đồ án”: còn cần demo xoay Activity và offline UI với dữ liệu demo, kiểm chứng Google OAuth trên thiết bị, chụp ảnh giao diện thực tế cho báo cáo, mẫu báo cáo chính thức, phân công nhóm được xác nhận, PDF đã render và kết quả tương đồng UEH. Số test không biểu thị phần trăm hoàn thiện hay điểm môn học.

## 2. Ma trận tiêu chí

| Tiêu chí | Bằng chứng hiện tại | Đánh giá và phần còn cần làm |
|---|---|---|
| Nội dung TMĐT/tin tức/mạng xã hội | Sản phẩm–lô–giỏ–checkout–đơn–giao nhận; chiến dịch, bình luận/reaction/report; hũ giải cứu | Phù hợp TMĐT có tương tác cộng đồng. Không tự gọi là mạng xã hội/tin tức đầy đủ. |
| Admin trên mobile | `ui/admin/`, navigation, quản trị Seller/tài nguyên, duyệt và cấu hình | Có triển khai; chọn các thao tác đại diện để demo, tránh đưa toàn bộ độ phức tạp backend lên slide. |
| Firebase Cloud Firestore | `data/remote/firebase/`, `firestore.rules`, `firestore.indexes.json`, backend Admin SDK và bộ test emulator | Có sử dụng thật. Lượt này không đọc/ghi production và không chạy lại Rules suite; cần test emulator phù hợp khi viết mô tả chi tiết quyền. |
| Responsive khi xoay | MainActivity không khóa orientation; `values-land`, `values-w600dp`, `layout-w600dp`, `layout-w840dp`; các lớp Responsive* | Có kiến trúc và test layout. Đã sửa hai mismatch layout; bài Activity rotation chỉ chạy khi emulator chưa đăng nhập. Chưa chứng nhận mọi màn, mọi bàn phím và trạng thái form đều giữ nguyên. |
| SQLite/offline — khuyến khích | Room database version 11, 9 entity, DAO SQL, repository fallback và WorkManager | Có thực hiện vượt mức chỉ cache Firestore SDK. Test SQLite đóng/mở lại database qua. Demo offline UI toàn luồng còn cần làm. |
| API hỗ trợ | Credential Manager/Google Sign-In + Firebase Auth, vị trí, Render HTTP, Cloudinary Worker, SePay, FCM | Có tích hợp. Provider/configuration/network runtime không được chứng minh chỉ bằng test mock hoặc compile. |
| Code phân chia khoa học — 40% | UI/Fragment → ViewModel → Repository; domain/model/policy; data local/remote; service; backend và test tách module | Có phân lớp; server/repository lớn vẫn cần trình bày giới hạn bảo trì. Không tự chấm điểm 40%. |
| PDF theo mẫu — 40% | Chưa tìm thấy mẫu chính thức DOCX/PDF trong repo | Chưa đủ để xác nhận; phải nhận mẫu, tạo và render báo cáo. |
| Lý thuyết 1–2 trang | Yêu cầu đã đưa vào prompt | Chưa viết báo cáo; tổng hợp Android và TMĐT ngắn, phần chính bám triển khai. |
| 60 ±5 trang | Prompt đặt mục tiêu 55–60, trần 65 | Chỉ xác nhận sau đếm trang PDF thực tế. |
| Phân công và mức hoàn thành | Có Git history, branch/merge/PR | Chưa có bảng chính thức được nhóm xác nhận; alias Git và commit count không tự thành phần trăm đóng góp. |
| Trình bày/demo 5 phút — 20% | Có app và luồng nghiệp vụ để demo | Cần kịch bản có thời gian và chạy thử; không tự chấm phần trình bày. |
| UEH mặc định, dưới 20% | Yêu cầu do người dùng cung cấp | Chưa chạy hệ thống UEH; không cam kết kết quả tương đồng. |

## 3. Bằng chứng kỹ thuật để người viết báo cáo kiểm tra lại

Các đường dẫn dưới đây tính từ root repo; class và phương thức là điểm tra cứu, không thay thế kiểm chứng runtime.

### Firestore và kiến trúc

- `app/build.gradle.kts`, `gradle/libs.versions.toml`: Firebase/Room/WorkManager/Credential Manager và Android build configuration.
- `app/src/main/java/com/rescuefarm/data/remote/firebase/`: repository Auth, sản phẩm, chiến dịch, giỏ, đơn, Admin và các dữ liệu khác.
- `firestore.rules` và test trong `tests/firestore/`: quyền client. Backend Admin SDK cần kiểm tra quyền ở API vì không bị client Rules kiểm soát như SDK Android.
- `functions/server.js`, các module nghiệp vụ, `cloudinary-worker/`: backend Express/Render và media Worker. Không mô tả nhầm là Firebase Cloud Functions/Firebase Storage chỉ vì thư mục tên `functions`.
- Các HTTP API/hũ/SePay và thao tác ghi quan trọng vẫn cần mạng. Giữ nguyên webhook xác nhận tiền vào; không bổ sung nút xác nhận thủ công để phục vụ ảnh báo cáo.

### SQLite/offline

- `data/local/database/RescueFarmDatabase.java`: Room v11; entity cache category, product, batch, campaign, banner, order, metadata; giỏ Guest và migration outbox. Có migrations 1→11.
- `data/local/dao/`: truy vấn SQL thực tế; Room là lớp abstraction trên SQLite, không phải database cloud thứ hai.
- `data/repository/OfflineProductRepository.java`: đọc cache local; không cho phép biến các hành động ghi nghiệp vụ thành ghi offline tùy tiện.
- `FirebaseProductRepository`: danh sách public/categories/batches có Room fallback qua `SnapshotLiveData`; `getProduct` có fallback DAO khi remote lỗi. `observeProduct` dùng document listener và ghi cache, không có cùng cách fallback Room tường minh như public page; không suy rộng mọi phương thức đều giống nhau.
- `FirebaseCampaignRepository`, `FirebaseOrderRepository`: có cache/fallback theo luồng; dữ liệu riêng phải được scope theo user.
- `service/sync/ReadCacheSyncWorker.java`, `OfflineSyncScheduler.java`: sync khi có mạng, lịch unique work; periodic 6 giờ, startup không phải vòng poll mỗi vài giây.
- `CacheSyncPolicy.java`: TTL catalog 30 phút, campaign/order 10 phút, banner 1 giờ; hard expiry 7 ngày. Tính mới của tồn kho khi checkout vẫn do server kiểm tra.
- `CriticalActionPolicy.java`: read sync khác với checkout/payment/approval/inventory/order transition. Không mô tả app có thể thanh toán hoặc duyệt dữ liệu hoàn toàn offline.
- SQLite lưu URL ảnh không đồng nghĩa file ảnh đã tồn tại offline; Glide cache và ảnh chưa xem cần kiểm chứng riêng.

### Responsive và vòng đời

- `AndroidManifest.xml`: không ép portrait và không dùng `configChanges` để né Activity recreation.
- `ui/common/ResponsiveContentLayout`, `ResponsiveActionLayout`, `ResponsiveGridLayout`: giới hạn chiều rộng, xếp lại action/grid.
- `layout-w600dp` có dashboard hai vùng; `layout-w840dp` có các màn Discovery/Checkout/Product Detail. Có biến thể dimensions theo màn hình/hướng.
- `ResponsiveLayoutsTest`: inflate/measure nhiều layout ở 320×480, 640×360, 600×960, 960×600 dp; font 1 và 1,6; một số kiểm tra LTR/RTL/sidebar và ID parity. Đây là kiểm tra layout, không phải thao tác từng Fragment hay chứng nhận mọi màn không bị cắt chữ.
- Ảnh do test Canvas render không được trình bày thành ảnh chụp màn hình app đang chạy. Báo cáo phải lấy ảnh thật qua emulator.

### API hỗ trợ

- `service/auth/GoogleCredentialService.java`: Credential Manager/GetSignInWithGoogleOption → token → Firebase Auth. Phải kiểm tra SHA, OAuth client/provider/consent và thiết bị khi demo; không đưa credential vào tài liệu.
- Location có Fused Location/Geocoder; mở bản đồ ngoài app không tự đồng nghĩa dùng Google Maps SDK.
- Cloudinary Worker dùng media signing; QR/SePay dùng xác nhận ngân hàng; FCM phục vụ thông báo; Render phục vụ HTTP nghiệp vụ. Test mock/emulator không thay thế chứng cứ dịch vụ production.
- Android hiện compile/target SDK 37, min SDK 29, source/target Java 11; Gradle có daemon toolchain riêng. Không đánh đồng JDK chạy build với mức Java source hay Android API.

## 4. Sửa lỗi trong lượt kiểm tra

1. `app/src/main/res/layout-w840dp/fragment_product_detail.xml`: layout rộng còn nút tính giá cũ và thiếu ID bộ chọn số lượng mà `ProductDetailFragment` gọi. Đồng bộ bộ −/ô số/+ với layout mặc định; giữ cách tính giá hiện có. Tránh lỗi truy cập View null ở màn rộng và mất chức năng số lượng.
2. `app/src/main/res/layout/fragment_seller_dashboard.xml`: thêm ID `sellerDashboardContent` và `sellerDashboardNavigation` vào hai container sẵn có, khớp layout tablet và hợp đồng test. Không đổi vị trí, header, màu hoặc nội dung giao diện.
3. Thêm `OfflineRoomEvidenceTest`: database giả riêng có tên ngẫu nhiên, ghi sản phẩm/lô, đóng/mở lại SQLite rồi đọc qua offline repository; xóa đúng database test sau chạy.
4. Thêm `CourseRotationEvidenceTest`: chỉ chạy khi không có tài khoản đăng nhập và có cờ `courseAuditIsolated=true` sau khi đã cô lập Firebase/HTTP, tắt Firestore network trước khi mở Activity, kiểm tra form đăng nhập qua landscape và Activity recreation. Cờ không tự chuyển app sang emulator. Nếu emulator có tài khoản, bỏ qua; không tự đăng xuất phiên người dùng.

Không thay đổi source backend, Rules, luồng thanh toán hoặc dữ liệu production trong lượt này. Không khôi phục việc đổi heading hàng loạt đã bị người dùng hủy.

## 5. Kiểm thử ngày 24/10/2026

| Kiểm tra | Kết quả | Giới hạn |
|---|---:|---|
| Android JVM | 251 test, 0 failure/error | Phạm vi các test đã viết, không phải 251 chức năng độc lập |
| Backend syntax/check | Qua | Các file được script kiểm tra |
| Backend unit | 183/183 | Harness/mock/local logic |
| Cloudinary Worker | 9/9 | Không upload thật |
| Navigation script | 59 destination Fragment có class | Không chứng minh mọi argument/deep link runtime |
| Android build debug | Qua | Kiểm tra lại sau sửa layout; xem log recheck |
| Android Lint | 0 error, 693 warning | Warning chưa được dọn toàn bộ; không gọi quality hoàn hảo |
| SQLite instrumentation | Qua bài database đóng/mở lại | Database riêng và repository đọc; chưa phải demo offline UI end-to-end |
| Responsive instrumentation sau sửa | 9/9 qua | Không đồng nghĩa toàn app đã thao tác khi xoay |
| Activity rotation | Bị bỏ qua trên phiên emulator đã đăng nhập | Cần chạy trên emulator sạch/demo, giữ nguyên phiên hiện tại |
| Firestore/Auth Emulator suite | Không chạy lại trong lượt này | Không chép số test từ audit cũ sang đây |

Lượt Gradle `connectedDebugAndroidTest` đầu thất bại do instrumentation báo process crashed trước khi chạy test (0 test); nguyên nhân crash của launcher chưa được xác định. Chạy trực tiếp bằng adb sau đó thực thi được; lượt đầu trực tiếp phát hiện test dashboard Seller thiếu hai ID container. Sau sửa và cài lại APK mới: **11 test được chọn, 10 qua, 1 bỏ qua, 0 thất bại**. Runner in `OK (11 tests)` nhưng status `-4` là assumption skipped của bài rotation, vì thế không được gọi là 11 test đã qua. Các lượt thất bại trước đó không được tính là test qua.

Log ở `build/course-requirements-android.log`, `build/course-requirements-android-recheck.log`, `build/course-requirements-backend-check.log`, `build/course-requirements-backend-unit.log`, `build/course-requirements-navigation.log`, `build/course-requirements-emulator.log`, `build/course-requirements-emulator-final.log`. `build/` có thể bị clean; kết quả tổng hợp được lưu tại `evidence/course-audit-results.json`, log emulator cuối được giữ cùng thư mục evidence.

Lệnh kiểm tra chính (PowerShell, từ root, JDK/SDK cần có sẵn):

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --console=plain --max-workers=1
npm --prefix functions run check
npm --prefix functions run test:unit
python scripts/check_navigation.py
# Chỉ trên emulator kiểm thử; giữ phiên người dùng, không clear data.
& 'D:\Sdk\platform-tools\adb.exe' install -r app/build/outputs/apk/debug/app-debug.apk
& 'D:\Sdk\platform-tools\adb.exe' install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
& 'D:\Sdk\platform-tools\adb.exe' shell am instrument -w -r -e class com.rescuefarm.ResponsiveLayoutsTest,com.rescuefarm.OfflineRoomEvidenceTest,com.rescuefarm.CourseRotationEvidenceTest com.example.nhom_7_mobileapp.test/androidx.test.runner.AndroidJUnitRunner
```

Máy này dùng Android Studio JBR và `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=D:\NHOM_7_MobileApp\build\no-unix-sockets`; Gradle recheck giới hạn heap 1024 MB/metaspace 512 MB. Không chạy cả suite instrumentation vào Firebase thật. Bài rotation chỉ được thực thi trên môi trường test đã cô lập toàn bộ network/API; trạng thái bỏ qua hiện tại không thay thế bước chuẩn bị đó.

## 6. Việc cần hoàn thành trước khi nộp

1. Cung cấp mẫu chính thức, môn/lớp/giảng viên, họ tên–MSSV, bảng phân công và mức hoàn thành do nhóm xác nhận. Có các merge thật trong Git history (ví dụ PR #185, #187, #191–195); kiểm tra SHA và diff tương ứng. Không suy ra danh tính/tỷ lệ chỉ bằng tên Git.
2. Cấu hình môi trường demo tách production cho cả Android, backend và Auth/Firestore Emulator. Chụp UI thật theo Guest/Customer/Seller/Admin; ảnh cùng dữ liệu khi dọc/ngang; chứng minh offline UI sau mở lại; thử Google OAuth trên thiết bị nếu môi trường cho phép.
3. Vẽ một sơ đồ tổng hợp cho mỗi loại: use-case chung actor; kiến trúc đầy đủ online/offline/API; dữ liệu collection/document; luồng nghiệp vụ trọng tâm. Giữ nguồn editable; không chia vụn theo từng vai trò, cũng không trộn các ký pháp thành một hình khó hiểu.
4. Soạn PDF theo mẫu, lý thuyết 1–2 trang, chức năng có giải thích luồng–code–ảnh–kiểm thử. Mục tiêu 55–60 trang, tối đa 65; đếm sau render. Nhúng ảnh thật và sơ đồ vào file, không chỉ link.
5. Diễn tập demo 5 phút và hỏi đáp; kiểm tra UEH mặc định với kết quả thật dưới 20% trước khi tuyên bố đạt.

Prompt triển khai chi tiết: [AI_REPORT_PROMPT_VI.md](AI_REPORT_PROMPT_VI.md). Lượt này chưa tạo DOCX/PDF báo cáo hoặc bộ ảnh giao diện để nộp.

## 7. Tài liệu kỹ thuật chính thức

- Android SQLite/Room: https://developer.android.com/training/data-storage/sqlite
- Android screen support: https://developer.android.com/guide/topics/manifest/supports-screens-element
- Firestore offline persistence: https://firebase.google.com/docs/firestore/manage-data/enable-offline
- Firebase Google Sign-In Android: https://firebase.google.com/docs/auth/android/google-signin

Các nguồn này giải thích công nghệ; bằng chứng app triển khai nằm trong source/test ở trên. Quy định đồ án lấy từ đề người dùng cung cấp, chưa được thay thế bởi một mẫu trường tự suy đoán.
