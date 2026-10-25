# reCAPTCHA cho đăng nhập bằng mật khẩu

## Phạm vi

Nút **Xác minh reCAPTCHA** nằm ngay dưới ô mật khẩu. Bấm nút mở hộp thoại chứa widget reCAPTCHA v2 thật của Google (có thể có thử thách ảnh). Hoàn thành rồi bấm **Đăng nhập**: app gửi response token đến Render, server gọi Google `siteverify`; chỉ khi hợp lệ mới gọi Firebase `signInWithEmailAndPassword`.

Google Sign-In, đăng ký, quên mật khẩu và tiếp tục với Guest không yêu cầu bước này. Mật khẩu vẫn đi trực tiếp qua Firebase Auth SDK, không gửi tới endpoint CAPTCHA hoặc WebView. Không có ô tick giả hoặc chế độ tự cho qua khi thiếu cấu hình.

Token chỉ giữ trong RAM, gắn với email đang nhập, dùng một lần và có giới hạn cục bộ 110 giây. Đổi email, rời màn, thử đăng nhập hoặc hết thời gian thì phải xác minh lại. Rotate không lưu token vào preferences/database. Backend xác minh success, domain và timestamp; Google kiểm tra thời hạn hai phút và chống dùng lại. Server còn giữ hash ngắn hạn để chặn gửi trùng đồng thời trong cùng process.

## Tạo key và cấu hình Render

1. Mở [reCAPTCHA Admin](https://www.google.com/recaptcha/admin/create) bằng tài khoản quản trị. Tạo key **reCAPTCHA v2 – “I'm not a robot” Checkbox** dành cho web; không dùng key Android score-based/Enterprise trong luồng này. Widget được phục vụ trên domain Render của backend.
2. Thêm domain backend vào danh sách được phép, ví dụ `your-service.onrender.com`. Nhập hostname thuần, không có `https://`, port hoặc đường dẫn. Nếu dùng custom domain, thêm chính hostname đó.
3. Trong Render → dịch vụ backend → Environment, thêm các biến sau. Secret chỉ đặt trên Render, không đưa vào Git/APK/chat:

   ```text
   PASSWORD_RECAPTCHA_SITE_KEY=<site key công khai của v2 checkbox>
   PASSWORD_RECAPTCHA_SECRET_KEY=<secret key tương ứng>
   PASSWORD_RECAPTCHA_HOSTNAMES=your-service.onrender.com
   ```

   Nếu có nhiều domain hợp lệ, HOSTNAMES phân cách bằng dấu phẩy. Các giá trị phải khớp domain mà app thật sự mở; không dùng wildcard. Secret không xuất hiện trong trang HTML hoặc log của module này.
4. Triển khai backend mới. Mở `https://your-service.onrender.com/auth/password-captcha` để kiểm tra widget tải được. Trang này chỉ làm CAPTCHA, không có form gửi password. Khi chưa đủ biến cấu hình hoặc cấu hình sai, endpoint trả 503; backend từ chối key test công khai luôn trả pass của Google.
5. Build/cài APK mới với `RENDER_BACKEND_URL=https://your-service.onrender.com`. Không cần thêm key CAPTCHA vào Gradle, `google-services.json` hoặc AndroidManifest. Page URL lấy từ backend URL đã cấu hình. Bản app thường chỉ chấp nhận HTTPS; HTTP `10.0.2.2` chỉ được phép ở biến thể reportDemo.

Chưa tạo key, cấu hình Render hay deploy production trong lượt thay đổi này. Vì người dùng chưa có key, bước Google thật còn chờ cấu hình; app mới sẽ không đăng nhập password được cho tới khi backend và key sẵn sàng. Google Sign-In/đăng ký vẫn dùng luồng cũ. Không thay Firestore Rules, collection, index hoặc dữ liệu production.

## Luồng và mức bảo vệ

```text
Login → nút dưới password → WebView HTTPS → Google v2 checkbox/challenge
      → response token trong RAM → POST /auth/password-captcha/verify
      → Render kiểm tra qua Google siteverify → ok
      → Firebase Auth SDK đăng nhập email/password → tải hồ sơ theo quyền cũ
```

Endpoint CAPTCHA không đọc/ghi Firestore, nên không thêm document reads cho bước này. Chống lặp và hạn chế tần suất dùng RAM có giới hạn trên từng process; không phải cache xác thực dùng chung nhiều replica. Google vẫn là bên kiểm tra token dùng một lần. Không cache một lần CAPTCHA để cho qua nhiều lượt password.

**Giới hạn quan trọng:** đây là bước bắt buộc trong luồng đăng nhập của APK RescueFarm, với token được Google xác minh phía server. Nó không bật bảo vệ toàn cục trên Firebase Auth; client tự viết vẫn có thể gọi trực tiếp API đăng nhập email/password của Firebase. Nếu cần chống lách ở cấp provider, phải cấu hình reCAPTCHA/Identity Platform hoặc cơ chế chặn đăng nhập phía provider. Tích hợp Identity Platform theo password provider cũng bao gồm đăng ký và các thao tác email khác; chưa bật vì phạm vi người dùng chỉ yêu cầu trang đăng nhập password. Không coi App Check hiện có là reCAPTCHA cho đăng nhập.

## Kiểm tra sau khi có key

- Email/password đúng + CAPTCHA hợp lệ → đăng nhập được. Thiếu, hết hạn, dùng lại, sai domain hoặc lỗi provider → không gọi password sign-in trong APK.
- Sai password → báo lỗi Firebase, phải xác minh lại trước lần thử tiếp theo.
- Đổi email sau khi hoàn thành CAPTCHA → trạng thái trở về chưa xác minh.
- Xoay màn, trở lại từ background sau hết hạn, đóng thử thách, mất mạng, tải lại dialog → không tự đánh dấu đã xác minh.
- Màn hình ngắn, chữ lớn và dọc/ngang: thử thách nằm trong dialog có vùng WebView riêng; kiểm tra nội dung Google có thể cuộn, nút Đóng dùng được.
- Google Sign-In và đăng ký không cần hoàn thành CAPTCHA; kiểm tra bằng tài khoản demo phù hợp.
- Không dùng test key để tuyên bố production đã được bảo vệ. Không thay key thật bằng một checkbox tự viết hoặc token stub.

Unit test backend dùng provider stub để kiểm tra nhánh thành công/thất bại, thiếu cấu hình, hostname, chống replay/concurrency, rate limit và không chuyển tiếp password. JVM test kiểm tra thời hạn/token/email và phân biệt password với Google/đăng ký. Chưa test widget Google thật hoặc UI Android trên thiết bị; giữ nguyên emulator5554 theo yêu cầu trước đó. Log và kết quả lần chạy mới nằm tại `docs/audits/password-captcha/`.

## Nguồn kỹ thuật

- [Google – reCAPTCHA v2 widget](https://developers.google.com/recaptcha/docs/display).
- [Google – server-side verification và giới hạn response token](https://developers.google.com/recaptcha/docs/verify).
- [Google – Identity Platform với reCAPTCHA Enterprise](https://docs.cloud.google.com/identity-platform/docs/recaptcha-enterprise).
- [Google – SDK mobile không cung cấp widget checkbox](https://docs.cloud.google.com/recaptcha/docs/setup-overview-mobile).

Đối chiếu ngày 25/10/2026. Quota/chi phí reCAPTCHA là cấu hình của dịch vụ Google, độc lập quota document reads của Firestore; không cam kết miễn phí vô hạn.
