# Công nghệ RescueFarm theo nhóm

Đối chiếu ngày 25/10/2026 với HEAD `29577d7f5e4635091b2fe4dc4aa95b6a77dd2257`. Quét danh sách file được Git theo dõi trên toàn repo, dependency/lockfile, import Android, module Node, tài nguyên WebView, script, workflow và tài liệu dữ liệu. Không tính `node_modules`, `build`, cache `.wrangler` hoặc dependency bắc cầu là công nghệ nhóm tự triển khai. Không đọc credential để lập danh sách này.

## Danh sách text theo mẫu nhóm công nghệ

- **Frontend:** Android native Java; XML Views; Android SDK; AndroidX; Material Components; ConstraintLayout; RecyclerView; ViewPager2; Navigation Component. Phần nhúng WebView dùng HTML, CSS và JavaScript.
- **Backend:** Node.js; Express 4; Firebase Admin SDK; REST/HTTPS API; Server-Sent Events; Fetch native; Node Crypto/Web Crypto cho token, chữ ký và webhook. Backend Express chạy trên Render; Cloudflare Worker xử lý media/signing.
- **Database và Caching:** Cloud Firestore; Room/SQLite; SharedPreferences; cache nội bộ Firestore SDK; cache RAM Map/TTL, gom request đang chạy và listener dùng chung ở backend.
- **DevOps và Testing:** Git; GitHub; GitHub Actions; Render; Cloudflare Workers; Wrangler CLI; Android Studio; Android SDK/Emulator/ADB; Gradle và Kotlin DSL; JUnit 4; AndroidX Test; Espresso; Room Testing; Android Lint; Node Test Runner; Firebase Emulator Suite; Firebase JS SDK và Rules Unit Testing cho test.
- **Third-party Services:** Firebase Authentication; Firebase Cloud Messaging; Firebase App Check/Play Integrity; Google Credential Manager/Google ID; Google Play services Fused Location Provider; Android Geocoder; Cloudinary REST API/CDN; Cloudflare Workers; Google reCAPTCHA v2 (chỉ còn endpoint backend cho APK cũ); Leaflet/OpenStreetMap; VietQR; SePay. Glide là thư viện tải ảnh trong app.
- **Dataset JSON:** Snapshot lịch sử ba cấp `daohoangson/dvhcvn`, nguồn JSON được chuyển thành TSV đóng gói để chọn tỉnh/huyện/xã offline; không gọi API địa chỉ mỗi lần người dùng mở form.
- **Kiến trúc và tác vụ nền:** MVVM; Repository Pattern; ViewModel; LiveData; SavedStateHandle; WorkManager; foreground location service; transaction/idempotency; outbox/reconciliation backend.
- **Cấu hình môi trường:** Gradle properties/BuildConfig; Node `process.env`; Render Environment; Worker vars/secrets; GitHub Actions variables/secrets. Không có loader dotenv được cài trong backend.
- **Công cụ tài liệu/audit phụ trợ:** Python, PowerShell, Markdown, SVG/JSON mô tả sơ đồ; builder báo cáo dùng python-docx, Pillow, pypdf và script Word COM. Đây là công cụ dựng tài liệu, không phải dependency runtime Android/backend.

## Phiên bản và bằng chứng chính

| Nhóm | Bằng chứng trong repo | Kết luận |
|---|---|---|
| Android/Java | [app/build.gradle.kts](../app/build.gradle.kts) | Java compatibility 11; minSdk 29; compile/targetSdk 37. Không tìm thấy source `.kt` được Git theo dõi. |
| Gradle | [wrapper](../gradle/wrapper/gradle-wrapper.properties), [catalog](../gradle/libs.versions.toml) | Gradle 9.5.0; AGP 9.1.1; Kotlin DSL chỉ dùng cấu hình build. |
| UI và lifecycle | [catalog](../gradle/libs.versions.toml), [HomeViewModel](../app/src/main/java/com/rescuefarm/ui/home/HomeViewModel.java) | Material 1.14.0; Lifecycle 2.11.0; Navigation 2.10.2; ViewPager2 1.1.0, cùng AndroidX/XML UI. |
| Room/SQLite | [database](../app/src/main/java/com/rescuefarm/data/local/database/RescueFarmDatabase.java), [catalog](../gradle/libs.versions.toml) | Room 2.8.5; schema ứng dụng version 11; entity/DAO/mapper và migration. |
| Firebase Android | [dependencies](../app/build.gradle.kts), [catalog](../gradle/libs.versions.toml) | Firebase BoM 34.19.0; Auth, Firestore, Messaging, App Check Play Integrity; debug dùng provider riêng. |
| Google sign-in/GPS | [GoogleCredentialService](../app/src/main/java/com/rescuefarm/service/auth/GoogleCredentialService.java), [MobileLocationService](../app/src/main/java/com/rescuefarm/service/location/MobileLocationService.java) | Credentials 1.6.0, Google ID 1.1.1, location 21.4.0; foreground GPS, không phải Maps SDK. |
| Backend | [package.json](../functions/package.json), [server.js](../functions/server.js), [Render](../functions/render.yaml) | Node >=20; Express ^4.21.2; firebase-admin ^13.0.0; native Fetch và Crypto. Lockfile quyết định bản cài cụ thể. |
| Media | [Worker](../cloudinary-worker/src/index.js), [Worker package](../cloudinary-worker/package.json), [catalog](../gradle/libs.versions.toml) | Cloudinary REST + Cloudflare Worker/Web Crypto; Glide 5.0.9 phía Android. Không có Cloudinary SDK trong package dependencies. |
| Map/realtime | [HTML](../app/src/main/assets/maps/rescue-map.html), [rescue-map.js](../functions/rescue-map.js), [Leaflet](../app/src/main/assets/maps/leaflet/leaflet.js) | Leaflet 1.9.4; OSM tiles; backend Firestore listener chung → SSE tới client. |
| CAPTCHA cũ | [backend](../functions/password-captcha.js) | Chỉ giữ endpoint cho APK cũ. Từ 27/10/2026, app Android đăng nhập trực tiếp Firebase Auth; đã gỡ widget và kiểm tra token. |
| Thanh toán | [VietQR](../functions/payment-qr.js), [SePay](../functions/sepay.js), [Hũ](../functions/rescue-fund.js) | QR/payment reference, webhook xác minh tiền vào, chống lặp; chi Hũ cần chuyển ngoài app rồi lưu bằng chứng. |
| Cache | [read-cache.js](../functions/read-cache.js), [SnapshotLiveData](../app/src/main/java/com/rescuefarm/data/remote/firebase/SnapshotLiveData.java) | RAM cache có TTL/giới hạn theo process; Room và Firestore SDK cache là các lớp khác nhau. Không dùng cache để chốt tồn/tiền. |
| Kiểm thử | [test package](../tests/firestore/package.json), [test lock](../tests/firestore/package-lock.json), [app dependencies](../app/build.gradle.kts) | JUnit 4.13.2; Espresso 3.7.0; AndroidX Test 1.3.0; Rules Unit Testing 6.0.0; firebase-tools 15.33.0; Firebase JS SDK 13.0.0 dùng cho test. |
| CI/vận hành | [APK](../.github/workflows/android-package.yml), [worker wakeup](../.github/workflows/notification-worker.yml), [Hũ nightly](../.github/workflows/rescue-fund-nightly.yml) | Build/artifacts/release APK và gọi worker có secret. Các workflow không đồng nghĩa provider/live đã nghiệm thu. |
| Dataset | [nguồn/license](../app/src/main/assets/address/README.md), [converter](../scripts/update_vietnam_addresses.py) | Snapshot JSON `eaddcea…` ngày 18/02/2025; đóng gói TSV. Dữ liệu hành chính ba cấp lịch sử, không đại diện hệ thống hành chính hai cấp mới. |
| Công cụ tài liệu | [builder](report/source/build_report.py), [diagram builder](report/source/diagrams.py), [Word render](report/source/render-word.ps1) | python-docx, Pillow, pypdf; Python/PowerShell; xuất SVG/PNG và Word/PDF theo pipeline tài liệu. |

## Phân biệt với mẫu FPT Shop

Không có bằng chứng cấu hình/triển khai các stack PHP, Composer, Bootstrap, Font Awesome, MySQL/phpMyAdmin, Redis/Predis, Docker, Supabase, PHPMailer/Gmail SMTP, PayPal hoặc VNPay trong đường chạy hiện tại của dự án. Không đưa các logo/công nghệ đó vào danh sách RescueFarm chỉ vì chúng xuất hiện trong ảnh mẫu. Không tìm thấy Dockerfile/Compose hoặc Postman collection được theo dõi để coi đó là bộ công cụ dự án đã cấu hình.

Firebase Storage, Firebase Cloud Functions và Google Maps SDK không phải dịch vụ đang được tích hợp chỉ vì tên bucket có trong cấu hình Firebase hoặc vì app mở ứng dụng bản đồ ngoài. Không có source Kotlin UI, React, Flutter hay Jetpack Compose trong phạm vi source được theo dõi. `activity-ktx` là dependency, không chứng minh app được viết bằng Kotlin.

## Giới hạn kiểm kê

Đây là bằng chứng dùng công nghệ ở source/cấu hình, không phải audit production, xác nhận tài khoản trả phí, kết quả kiểm thử mới hoặc cam kết miễn phí. Các số phiên bản lấy từ catalog/manifest/lockfile của repo, không phải phiên bản mới nhất trên thị trường.

Workflow APK hiện chọn Node 22; lockfile Rules Unit Testing yêu cầu Node >=24.12.0. Đây là chênh lệch cấu hình cần đồng bộ; lượt này chỉ cập nhật tài liệu, không đổi workflow hoặc khẳng định CI đang xanh.

License và nội dung của dependency bắc cầu cần được kiểm tra riêng nếu lập hồ sơ phân phối đầy đủ. Repo đã kèm giấy phép riêng cho Leaflet và dữ liệu dvhcvn.
