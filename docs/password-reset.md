# Khôi phục mật khẩu qua email — 18/10/2026

## Luồng đã triển khai

1. Người dùng mở **Đăng nhập → Quên mật khẩu**, nhập email và nhấn gửi.
2. Android xác thực định dạng, chặn gửi trùng trong lúc chờ và gọi `FirebaseAuth.sendPasswordResetEmail()` với ngôn ngữ `vi`.
3. App hiển thị **Kiểm tra email của bạn**, địa chỉ vừa nhập, hướng dẫn kiểm tra thư rác, nút mở ứng dụng email, gửi lại và đổi email nhận liên kết. Thông báo xác nhận luôn có điều kiện, không tiết lộ tài khoản có tồn tại/bị khóa hay không.
4. Người dùng mở liên kết trong email. Trang mặc định của Firebase xác minh mã và cho đặt mật khẩu mới. Firebase quản lý hiệu lực, việc sử dụng mã và chính sách mật khẩu; app không tự tạo OTP hoặc lưu mật khẩu.
5. Sau khi hoàn tất trên trang Firebase, người dùng quay lại app để đăng nhập bằng mật khẩu mới. UID không đổi, các liên kết đơn hàng/hồ sơ theo UID được giữ nguyên.

Chỉ nhận được email chưa có nghĩa mật khẩu đã đổi. Màn hình trước đây báo **Đặt lại mật khẩu thành công** ngay sau gửi email đã được sửa thành **Kiểm tra email của bạn**.

Quy trình dùng Firebase Authentication, áp dụng cho tài khoản có Email/Password ở mọi vai trò. Người dùng thường đăng nhập Google được hướng dẫn tiếp tục dùng nút Google nếu phù hợp.

## Cách gửi mail và chi phí

Sử dụng dịch vụ email mặc định của Firebase đang được bật trong project. Không cần dịch vụ mail khác, SMTP, tên miền gửi riêng, API key mới hoặc backend Render để gửi reset. Vì vậy luồng không phụ thuộc thời gian khởi động Render Free.

Theo [hạn mức Firebase Authentication](https://firebase.google.com/docs/auth/limits), gói Spark cho phép **150 email đặt lại mật khẩu/ngày**. Đây là hạn mức miễn phí, không phải gửi không giới hạn. Firebase có thêm giới hạn chống lạm dụng.

App chờ **60 giây** sau yêu cầu được chấp nhận trước khi cho gửi lại; lỗi mạng có khoảng chờ ngắn, lỗi bị giới hạn gửi có khoảng chờ dài hơn. Đây là kiểm soát trải nghiệm tại client; hạn mức của Firebase mới là kiểm soát phía dịch vụ.

Tài liệu gốc: [gửi và bản địa hóa email trên Android](https://firebase.google.com/docs/auth/android/manage-users#send_a_password_reset_email), [bảo vệ chống dò email tài khoản](https://docs.cloud.google.com/identity-platform/docs/admin/email-enumeration-protection), [Auth Emulator](https://firebase.google.com/docs/emulator-suite/connect_auth).

## Kiểm tra trực tiếp Firebase/Firestore

Đã dùng service account cục bộ để **chỉ đọc** project `nhom7-rescuefarm`; không gửi email thật, đổi mật khẩu, tạo tài khoản hoặc sửa dữ liệu nghiệp vụ.

| Kiểm tra | Kết quả |
|---|---|
| Email/Password | Đã bật, yêu cầu mật khẩu |
| Bảo vệ dò email | Đã bật |
| Dịch vụ gửi email | Firebase mặc định |
| Mẫu email reset | Đã có |
| Trang xử lý liên kết | Firebase mặc định |
| Domain Firebase xử lý liên kết | Đã được cho phép |
| Hồ sơ `users` | 13 |
| Tài khoản Firebase Auth | 13 |
| Có provider mật khẩu | 8 |
| Không có provider mật khẩu | 5 |
| Hồ sơ và Auth cùng UID nhưng email khác nhau | 0 |
| Field mật khẩu/hash/token reset trong các hồ sơ đã rà | 0 |
| Collection reset/mail hiện có | Không có |

**Không cần thêm field, collection, index hay thay đổi Firestore rules.** Firebase Auth là nguồn xác thực; Firestore `users` chỉ là hồ sơ. Mật khẩu, mã email và liên kết reset không được ghi vào Firestore, analytics hoặc log production của app.

Có hai lệch dữ liệu cần rà riêng: **1 Auth account chưa có hồ sơ `users` và 1 hồ sơ `users` chưa có Auth account tương ứng**. Hai nhóm có thể khác UID dù tổng số đều bằng 13. Quên mật khẩu không tự tạo tài khoản mới từ một hồ sơ Firestore; nếu chỉ có hồ sơ thì không thể khôi phục mật khẩu bằng hồ sơ đó. Không tự tạo/xóa/ghép các tài khoản này vì chưa xác minh chủ sở hữu. Báo cáo không công khai email, UID hoặc thông tin đăng nhập.

Công cụ kiểm tra lại: `node scripts/audit-password-reset.cjs`. Kết quả tổng hợp cục bộ: `build/password-reset/live-audit.json` (Git ignore). Script từ chối môi trường emulator hoặc project khác và không lưu cấu hình SMTP/secret.

## Trạng thái và xử lý lỗi trong app

- ViewModel riêng cho reset, không dùng chung sự kiện đăng nhập/đăng ký.
- Email đã gửi, màn hình xác nhận và thời gian chờ được giữ qua xoay màn hình; dữ liệu cần thiết được lưu bằng `SavedStateHandle` cho việc tái tạo trạng thái.
- Tác vụ đang gửi khóa nút/ô nhập để tránh gửi trùng; bộ đếm chỉ chạy khi màn hình đang hoạt động.
- Lỗi định dạng, mất mạng, bị giới hạn gửi và chưa bật dịch vụ có thông báo phù hợp. Không dùng lỗi “Email hoặc mật khẩu không chính xác” cho thao tác gửi reset.
- Phản hồi tài khoản không tồn tại/bị khóa không được phân biệt ở giao diện reset. Cấu hình bảo vệ chống dò tài khoản của Firebase vẫn bật.
- Mở ứng dụng email chỉ khi người dùng bấm; máy không có ứng dụng email sẽ được hướng dẫn dùng trình duyệt.
- Liên kết hết hạn/đã dùng phải yêu cầu lại. Không tự đăng nhập hoặc tự thay đổi mật khẩu ngay sau gửi email.

## Kiểm thử

- `:app:assembleDebug`, `:app:testDebugUnitTest`, `:app:assembleDebugAndroidTest` thành công.
- **224 unit tests Android đạt**, gồm 6 bài mới cho định dạng, gửi trùng, gửi lại, khôi phục trạng thái, lỗi mạng và chính sách phản hồi.
- **5/5 bài Auth Emulator đạt:** mã email hợp lệ đặt mật khẩu mới; mật khẩu cũ không đăng nhập được; UID giữ nguyên; mã sai/đã dùng bị từ chối; không đổi mật khẩu tài khoản khác; mật khẩu quá yếu bị từ chối; gửi email chưa làm thay đổi mật khẩu.
- Các tài khoản `example.test` trong kiểm thử chỉ tồn tại trong emulator của project `demo-rescuefarm-reset`, không phải dữ liệu mẫu trên database thật.
- Resource XML và liên kết view đã được kiểm tra. Instrumentation APK đã biên dịch, chưa chạy trên thiết bị trong phiên này. Chưa kiểm chứng việc nhận thư trong một hộp thư thật.

Chạy riêng Auth Emulator từ thư mục repository:

```powershell
& '.\tests\firestore\node_modules\.bin\firebase.cmd' emulators:exec --project demo-rescuefarm-reset --only auth --config firebase.auth-test.json 'node --test --test-concurrency=1 tests/auth/password-reset.test.mjs'
```

## Cài và kiểm tra thực tế

Cài `app/build/outputs/apk/debug/app-debug.apk` mới. Riêng chức năng này không cần deploy Render hoặc sửa Firebase config.

Dùng một email tài khoản mình sở hữu: gửi reset, kiểm tra Inbox/Spam, mở link và đặt mật khẩu mới; trở lại đăng nhập bằng mật khẩu mới và kiểm tra các đơn hàng/hồ sơ vẫn còn. Mật khẩu cũ phải không còn đăng nhập được. Nếu chưa nhận thư, chờ hết thời gian gửi lại và kiểm tra đúng địa chỉ; màn hình xác nhận không khẳng định email đó có tài khoản.

Muốn thay tên người gửi, tiêu đề hoặc nội dung email theo thương hiệu, chỉnh template tại Firebase Console → Authentication → Templates → Password reset. Không cần đổi dịch vụ gửi chỉ để thực hiện việc khôi phục cơ bản.
