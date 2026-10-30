> Đọc bằng ngôn ngữ khác: [English](README.md)

# RescueFarm - Ứng dụng Android giải cứu nông sản

<p align="center"><img width="160" alt="Logo RescueFarm" src="app/src/main/res/drawable/logo.png" /></p>

## Mô tả dự án

RescueFarm là ứng dụng thương mại điện tử Android kết nối người bán với người mua nông sản cần tiêu thụ kịp thời. Ứng dụng kết hợp khám phá sản phẩm, quản lý hàng theo lô, chiến dịch giải cứu, đặt hàng và tương tác cộng đồng.

Hệ thống có bốn vai trò **Guest, Customer, Seller và Admin**. Người bán quản lý nguồn hàng; người mua tìm sản phẩm và đặt hàng; Admin duyệt tài khoản/nội dung ngay trên mobile. Hũ giải cứu hỗ trợ nhận ủng hộ và phân bổ chuyến thiện nguyện gắn với hàng thật.

README mô tả code và cấu hình trong repo, không khẳng định mọi thành phần đã được deploy ở một môi trường cụ thể. Xem [đặc tả nghiệp vụ](final.md), [hình kiến trúc tham khảo](docs/diagram.png) và [bảng công nghệ có đối chiếu source](docs/technology-stack.md).

## Chức năng chính

### Chức năng người mua

- **Danh mục sản phẩm** - Xem nhóm hàng, ảnh, giá giải cứu và các lô đủ điều kiện.
- **Chi tiết sản phẩm** - Xem mô tả, ảnh, người bán và chọn lô. Hạn dùng/tồn khả dụng thuộc Batch, không phải một hạn dùng chung cho Product.
- **Tìm kiếm và khám phá** - Từ khóa, danh mục, giá, vị trí và cách sắp xếp trên dữ liệu đã tải; tải thêm để mở rộng kết quả.
- **Giỏ hàng** - Thêm theo lô, tăng/giảm số lượng và xóa dòng hàng. Guest lưu giỏ bằng Room; Customer đăng nhập dùng Firestore.
- **Thanh toán đơn** - Hỗ trợ giao hàng, đến nhận và giải cứu tại điểm; backend kiểm lại giá, phí giao hàng, hạn lô, tồn và phân bổ chiến dịch; requestId chống tạo đơn/giữ kho trùng.
- **Phương thức thu tiền** - COD khi giao hàng, tiền mặt khi đến nhận/giải cứu tại điểm và QR chuyển khoản theo cấu hình; Seller bật SePay dùng webhook để xác minh tiền vào.
- **Quản lý đơn** - Theo dõi lịch sử/trạng thái, hủy khi được phép và xác nhận đã nhận hàng theo quyền. Guest tra cứu đơn bằng mã đơn và số điện thoại người nhận.
- **Yêu thích và đánh giá** - Lưu sản phẩm và gửi đánh giá khi có giao dịch đủ điều kiện.
- **Chiến dịch và cộng đồng** - Xem tiến độ, sản phẩm liên quan; reaction, báo cáo, bình luận text kèm tối đa ba ảnh. Bình luận ẩn danh dùng tên/avatar mặc định trên giao diện.
- **Bản đồ giải cứu** - Bản đồ nhỏ trên Home mở ra bản đồ lớn; xem điểm cố định và vị trí còn mới do điện thoại Seller chia sẻ.
- **Flash Sale và Hộp bí ẩn** - Khám phá gói có tồn kho thật và quản lý suất nhận. Module dùng luồng claim riêng, nhận tại điểm và trả tiền khi nhận.
- **Hũ giải cứu** - Ủng hộ bằng QR/mã theo dõi và xem hoạt động theo quyền.
- **Xác thực** - Đăng ký, đăng nhập email/password hoặc Google, đặt lại mật khẩu và tiếp tục với Guest. Đăng nhập email/password gọi trực tiếp Firebase Authentication, không yêu cầu CAPTCHA của app.
- **Hồ sơ và địa chỉ** - Sửa hồ sơ/avatar, quản lý địa chỉ nhận hàng và giữ vị trí tìm kiếm Home theo scope phiên/tài khoản.
- **Responsive và offline** - Có bố cục dọc/ngang và cache Room cho dữ liệu được chọn. Checkout, tiền và kho phải kiểm tra online; lưu URL không chứng minh ảnh đã xem được offline.

### Chức năng người bán

- **Đăng ký Seller** - Gửi hồ sơ và chờ Admin duyệt.
- **Cửa hàng và sản phẩm** - Quản lý thông tin, ảnh và sản phẩm thuộc quyền sở hữu.
- **Kho theo lô** - Quản lý ngày thu hoạch, hạn dùng và số lượng; giữ lịch sử biến động kho. Giá gốc/giá giải cứu được quản lý ở sản phẩm, không nằm riêng trên từng lô.
- **Chiến dịch** - Tạo nội dung gắn lô thật và gửi kiểm duyệt.
- **Điểm di động** - Bật/tắt chia sẻ GPS bằng foreground service cho chiến dịch đủ điều kiện; có giới hạn tần suất ghi vị trí.
- **Đơn và giao nhận** - Xử lý đơn thuộc Seller theo các chuyển trạng thái hợp lệ.
- **Gói giải cứu** - Quản lý Flash Sale, Hộp bí ẩn, chuẩn bị hàng và suất nhận.
- **Chuyến thiện nguyện** - Nhận chuyến Hũ phân bổ và gửi chứng từ trong hạn 24 giờ tính từ lúc phân bổ.
- **Hồ sơ thanh toán** - Lưu thông tin ngân hàng riêng tư và cấu hình QR/SePay phù hợp.

### Chức năng quản trị

- **Dashboard mobile** - Xem tổng hợp vận hành, đơn và thống kê giải cứu.
- **Tài khoản** - Duyệt hồ sơ Seller và quản lý trạng thái người dùng.
- **Kiểm duyệt** - Duyệt sản phẩm, chiến dịch, đánh giá và xử lý báo cáo có thông tin quản trị.
- **Danh mục và banner** - Quản lý nội dung hiển thị cùng liên kết chiến dịch.
- **Giám sát đơn** - Xem đơn và dữ liệu đối soát qua màn/API có quyền.
- **Phí vận chuyển** - Cấu hình biểu phí chung.
- **Gói giải cứu** - Quản trị các gói theo nguồn hàng thực tế.
- **Hũ và bằng chứng giao** - Cấu hình ngân sách/nơi đón nhận và xác minh chứng từ.
- **Ghi nhận chi Hũ** - Sau khi chuyển tiền ngoài app, lưu đúng số tiền, mã giao dịch và ảnh chứng từ. SePay nhận diện tiền ủng hộ vào, không tự chuyển tiền ra cho Seller.

Các tác vụ Admin nằm trong ứng dụng Android, không có web admin riêng hoặc mật khẩu Admin mặc định.

## Công nghệ sử dụng

| Tầng | Công nghệ |
|---|---|
| Frontend / Android | Java, XML Views, Android SDK, AndroidX, Material Components, ConstraintLayout, RecyclerView, ViewPager2, Navigation Component |
| Kiến trúc app | MVVM, Repository Pattern, ViewModel, LiveData, SavedStateHandle |
| Nội dung WebView | HTML, CSS, JavaScript, Leaflet 1.9.4 |
| Backend | Node.js, Express 4, Firebase Admin SDK, REST/HTTPS, Server-Sent Events, Node Crypto và Fetch native |
| Database | Cloud Firestore; Room trên SQLite cho dữ liệu cục bộ được chọn |
| Cache và trạng thái | Room/DAO, SharedPreferences, cache Firestore SDK, Map/TTL trong RAM backend và listener dùng chung |
| Xác thực/bảo mật | Firebase Auth, Google Credential Manager/Google ID, App Check Play Integrity, Firestore Rules, kiểm quyền server, chữ ký media/webhook |
| Ảnh | Cloudinary REST API/CDN, Cloudflare Worker, Glide |
| Vị trí/bản đồ | Fused Location Provider, Android Geocoder, Haversine, Leaflet, OpenStreetMap tiles, map intent ngoài app |
| Thanh toán | VietQR, SePay webhook, COD và trả tiền trực tiếp khi nhận gói |
| Nền/thông báo | Firebase Cloud Messaging, WorkManager, foreground location service, outbox/reconciliation backend |
| DevOps/Testing | Git/GitHub, GitHub Actions, Render, Cloudflare Workers/Wrangler, Gradle Kotlin DSL, Android Lint và kiểm tra cú pháp JavaScript |
| Dataset/công cụ | Snapshot JSON lịch sử `daohoangson/dvhcvn` đóng gói TSV offline; Python/PowerShell dựng báo cáo, script Node.js bootstrap Admin và kiểm tra cấu hình SePay |
| Biến môi trường | Gradle properties/BuildConfig cho cấu hình client công khai; process.env, Render Environment, Worker vars/secrets, Actions vars/secrets |

Source ứng dụng là Java; file `.kts` dùng cấu hình Gradle, không phải UI Kotlin. Backend chạy Render, không phải Firebase Cloud Functions. Cloudinary dùng REST/native Fetch, không có SDK Cloudinary được cài làm dependency. Chi tiết phiên bản và đường dẫn kiểm chứng nằm tại [technology-stack.md](docs/technology-stack.md).

## Cài đặt và cấu hình

### Yêu cầu

- **Android Studio/SDK** - SDK API 37 để build; thiết bị tối thiểu API 29.
- **Java** - CI chọn JDK 21; dùng JDK tương thích cấu hình Gradle/AGP của dự án.
- **Node.js/npm** - Node ≥20 cho backend.
- **Firebase CLI và project** - Auth, Firestore, Rules/indexes và App Check phù hợp môi trường.
- **Dịch vụ** - Render cho backend, Cloudflare Worker/Cloudinary cho media; cấu hình provider khi cần QR và webhook.

### Bước 1 Clone repository

```bash
git clone https://github.com/datweb07/NHOM_7_MobileApp.git
cd NHOM_7_MobileApp
```

### Bước 2 Cài dependency

```bash
npm --prefix functions ci
```

Mở root repo bằng Android Studio và Sync Gradle. Thư viện Android ở [app/build.gradle.kts](app/build.gradle.kts) và [version catalog](gradle/libs.versions.toml). Deploy Worker dùng Wrangler CLI.

### Bước 3 Cấu hình biến môi trường

Đặt cấu hình client công khai tại Gradle properties cá nhân (`%USERPROFILE%\.gradle\gradle.properties` trên Windows), hoặc truyền `-P`:

```properties
RENDER_BACKEND_URL=https://your-service.onrender.com
CLOUDINARY_CLOUD_NAME=your-cloud-name
CLOUDINARY_SIGNING_URL=https://your-worker.workers.dev
```

Backend đọc `process.env`, không tự tải `.env` bằng dotenv. Dùng Render Environment hoặc biến của process terminal:

| Biến | Vị trí và mục đích |
|---|---|
| `FIREBASE_SERVICE_ACCOUNT_JSON` | Secret Render/server cho Admin SDK |
| `PORT` | Cổng backend, mặc định 3000 |
| `MEDIA_WORKER_SECRET` | Secret giống nhau trên Render và Worker |
| `NOTIFICATION_WORKER_SECRET` | Secret giống nhau trên Render và Actions |
| `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_SIGNING_URL` | Tích hợp media/QR của backend khi cần |
| `PASSWORD_RECAPTCHA_SITE_KEY`, `PASSWORD_RECAPTCHA_SECRET_KEY`, `PASSWORD_RECAPTCHA_HOSTNAMES` | Chỉ dùng cho endpoint CAPTCHA cũ; app hiện tại không sử dụng |
| `SEPAY_MERCHANTS_JSON` | Cấu hình riêng tư Seller/ngân hàng/webhook |
| `VIETQR_CLIENT_ID`, `VIETQR_API_KEY` | API tạo VietQR tùy chọn |
| `RESCUE_FUND_SELLER_ID` | Chọn kết nối ngân hàng Seller dùng chung cho Hũ |

Không đưa service account, secret, tài khoản ngân hàng hay dữ liệu người dùng thật vào Git/APK. Ví dụ trong tài liệu là placeholder, không phải thông tin đăng nhập mặc định.

### Bước 4 Cấu hình Firebase và database

1. Đăng ký app Android đúng package **`com.example.nhom_7_mobileapp`**.
2. Tải cấu hình vào **`app/google-services.json`**; file này được ignore.
3. Bật provider xác thực cần dùng và tạo Firestore.
4. Kiểm tra project đích rồi triển khai [Rules](firestore.rules) và [indexes](firestore.indexes.json):

   ```bash
   firebase deploy --only firestore --project YOUR_FIREBASE_PROJECT_ID
   ```

Alias mặc định ở `.firebaserc` không tự trở thành project của bạn. Firestore dùng collection/document, không import schema MySQL. Room tạo/migrate SQLite từ mã ứng dụng.

Bản debug dùng App Check debug provider; nếu bật enforcement, đăng ký debug token của thiết bị trong Firebase Console. Bản release dùng Play Integrity và cần cấu hình đăng ký ứng dụng/chứng chỉ ký tương ứng.

### Bước 5 Cấu hình Cloudinary

Đặt `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `FIREBASE_PROJECT_ID`, `MEDIA_BACKEND_URL` đúng môi trường Worker. Trong thư mục `cloudinary-worker`:

```bash
npx wrangler secret put CLOUDINARY_API_SECRET
npx wrangler secret put MEDIA_WORKER_SECRET
npx wrangler deploy
```

Đặt cùng `MEDIA_WORKER_SECRET` trên Render, rồi đưa URL Worker vào cấu hình Android. Media chiến dịch dùng vòng đời authorize/upload/commit; backend, Worker, Rules và APK cần tương thích. Xem [hướng dẫn media](docs/a13-a17-implementation.md) và [Worker](cloudinary-worker/README.md).

### Bước 6 Cấu hình email

Email đặt lại mật khẩu do **Firebase Authentication** gửi. Cấu hình template và domain được phép trong project Firebase. Không cần PHPMailer, Gmail SMTP hoặc mật khẩu SMTP cho repository này. Xem [PasswordResetPolicy](app/src/main/java/com/rescuefarm/service/auth/PasswordResetPolicy.java).

### Bước 7 Cấu hình Google Login

Bật Google trong Firebase Auth, khai báo fingerprint chứng chỉ ký của bản Android rồi tải lại `google-services.json`. Credential Manager sử dụng resource `default_web_client_id` để lấy Google ID token; Firebase Auth đổi credential này thành phiên đăng nhập. Build lại sau khi đổi cấu hình; đối chiếu [GoogleCredentialService](app/src/main/java/com/rescuefarm/service/auth/GoogleCredentialService.java).

### Bước 8 Đăng nhập email/password

App hiện tại đăng nhập trực tiếp qua Firebase Authentication, không cần widget, token hoặc cấu hình reCAPTCHA trên Render. Build và cài APK mới để áp dụng. Endpoint CAPTCHA trên backend được giữ để tương thích APK cũ; xem [tài liệu CAPTCHA lịch sử](docs/password-login-recaptcha.md).

### Bước 9 Cấu hình QR và SePay

Seller đã duyệt lưu hồ sơ thanh toán riêng tư. Để xác minh chuyển khoản tự động, liên kết ngân hàng với SePay, cấu hình webhook của đúng Seller và merchant secret trên Render. Backend kiểm chữ ký/xác thực webhook, mã giao dịch, tài khoản, số tiền và chống ghi trùng.

VietQR API là phần tạo QR tùy chọn. Không thay luồng SePay đã cấu hình bằng xác nhận tiền vào thủ công. Xem [SePay](docs/sepay-setup.md) và [phí giao hàng/ngân hàng](docs/checkout-shipping-and-bank-setup.md).

### Bước 10 Cấu hình vận hành

Admin cấu hình phí giao hàng trên mobile. Hũ dùng cấu hình ngân hàng, ngân sách và nơi đón nhận theo [hướng dẫn Hũ](docs/rescue-fund.md) cùng [ngân hàng dùng chung](docs/rescue-fund-shared-bank.md).

Chuyến Hũ mới có 24 giờ từ lúc phân bổ tới gửi bằng chứng. VERIFY và chuyển tiền ngoài app/ghi chi là các thao tác riêng. Worker Actions dùng `RENDER_BACKEND_URL`, `NOTIFICATION_WORKER_SECRET`; scheduler hoặc FCM không bảo đảm thực thi/đến đúng từng giây.

### Bước 11 Chạy backend

Sau khi đặt biến server và chọn đúng project Firebase:

```bash
npm --prefix functions start
```

Kiểm tra endpoint `/health`. [Blueprint Render](functions/render.yaml) dùng root `functions`, build `npm install`, start `npm start`. Xem [vận hành backend](functions/README.md) trước khi chạy startup migration hoặc tích hợp provider.

Có thể đóng gói backend bằng [Dockerfile](Dockerfile) ở root. [Workflow Docker package](.github/workflows/docker-package.yml) đưa image lên GitHub Packages/GHCR khi cập nhật `main` hoặc push tag phiên bản; xem [hướng dẫn Docker và phát hành](docs/docker-packaging.md). Android được phân phối riêng bằng APK trong Releases.

### Bước 12 Build và mở app

Tại root repo bằng PowerShell:

```powershell
.\gradlew.bat :app:assembleDebug
```

APK nằm tại `app/build/outputs/apk/debug/app-debug.apk`. Chọn đúng thiết bị/emulator trong Android Studio để chạy. Mọi vai trò đều dùng ứng dụng Android; không có URL `/admin` riêng.

### Bước 13 Kiểm tra phát triển

```powershell
.\gradlew.bat :app:assembleDebug :app:lintDebug
npm --prefix functions run check
```

Các bộ test tự động và thư mục `scripts` ở root không còn trong repo. Các lệnh hiện tại kiểm tra build/lint Android và cú pháp JavaScript backend/Worker. [Biên bản dọn repo](docs/repository-cleanup.md) ghi nhận trạng thái dọn trước đó, vẫn liệt kê một số script root đã bị xóa sau này; kết quả test trong báo cáo/audit cũ là dữ liệu lịch sử.

Workflow [đóng gói Android](.github/workflows/android-package.yml) vẫn build APK debug, tạo artifact Packages và đính kèm APK/checksum cho tag `v*`. Cần secret `GOOGLE_SERVICES_JSON` và biến endpoint phù hợp để build dùng được; workflow không triển khai backend, Worker hoặc Firestore.

Workflow APK kiểm tra cú pháp JavaScript backend/Worker, chuẩn bị cấu hình Firebase, build Android và đóng gói APK/checksum; không chạy test hoặc script điều hướng đã bị xóa.

Nội dung phát hành đầu tiên đã soạn tại [release notes v1.0.0](docs/releases/v1.0.0.md); workflow theo tag dùng nội dung này khi tạo bản phát hành tương ứng.

## Nhóm phát triển

| Thành viên | GitHub | Vai trò |
|---|---|---|
| Lại Thành Đạt | [LaiThanhDat-glitch](https://github.com/LaiThanhDat-glitch) | Trưởng nhóm |
| Trương Thành Đạt | [datweb07](https://github.com/datweb07) | Thành viên |
| Phan Khắc Anh Tuấn | [KhacTuan1224](https://github.com/KhacTuan1224) | Thành viên |
| Nguyễn Phương Chinh | [chinhngprit](https://github.com/chinhngprit) | Thành viên |
| Nguyễn Tấn Khiêm | [nguyentankhiem1610](https://github.com/nguyentankhiem1610) | Thành viên |

## Đóng góp

Đọc [CONTRIBUTING.md](CONTRIBUTING.md), [Code of Conduct](CODE_OF_CONDUCT.md) và [chính sách bảo mật](SECURITY.md). Giữ diff đúng phạm vi; bảo toàn phân quyền, transaction/idempotency và cách giữ secret. Chạy kiểm tra phù hợp với thay đổi.

## Giấy phép

Mã dự án phát hành theo **MIT License**, xem [LICENSE](LICENSE).

### Giấy phép thành phần bên thứ ba

Thư viện/dataset giữ giấy phép riêng. Repo có [BSD 2-Clause của Leaflet](app/src/main/assets/maps/leaflet/LICENSE) và [GPL-3.0 của dữ liệu dvhcvn](app/src/main/assets/address/LICENSE.dvhcvn). Dataset không được đổi thành MIT theo app. Điều khoản của các dependency/dịch vụ khác thuộc nhà cung cấp tương ứng; README không thay một cuộc rà soát giấy phép đầy đủ.

## Theo dõi đồ án học phần

Tham khảo [tệp đề bài và mẫu báo cáo trong repo](docs/MOBILE%20FINAL%20PROJECT.pdf). Chưa tìm thấy URL theo dõi lớp riêng của RescueFarm nên không sử dụng link Google Docs của mẫu FPT Shop.

## Theo dõi tài liệu nhóm

- [Đặc tả sản phẩm/nghiệp vụ](final.md)
- [Nguồn và hướng dẫn dựng báo cáo](docs/report/source/README.md)
- [Bản Word báo cáo](docs/report/report.docx) và [PDF](docs/report/report.pdf)
- [Kiểm kê công nghệ và nguồn đối chiếu](docs/technology-stack.md)

Báo cáo/audit là snapshot; đối chiếu ngày và commit trước khi dùng để chứng minh phiên bản hiện tại.
