# reCAPTCHA cho đăng nhập bằng mật khẩu (lịch sử)

> Cập nhật 27/10/2026: đã gỡ reCAPTCHA khỏi màn hình và luồng đăng nhập Android. App gọi trực tiếp Firebase Auth sau khi kiểm tra email/mật khẩu; không gọi endpoint CAPTCHA trên Render. Cần build/cài APK mới. Các endpoint backend được giữ để tương thích APK cũ. Nội dung dưới đây mô tả tích hợp trước khi gỡ; các file adapter/token phía Android đã được xóa.

## Phạm vi

WebView chứa **widget reCAPTCHA v2 thật của Google** hiển thị trực tiếp ngay dưới ô mật khẩu. Không còn nút Xác minh reCAPTCHA hoặc hộp thoại của app. Khi Google yêu cầu thử thách ảnh, vùng WebView mở rộng ngay trong trang đăng nhập rồi thu gọn khi thử thách đóng. Hoàn thành rồi bấm **Đăng nhập**: app gửi response token đến Render, server gọi Google `siteverify`; chỉ khi hợp lệ mới gọi Firebase `signInWithEmailAndPassword`.

Bản hiển thị mới dùng `size: normal` của Google, tạo ô ngang khoảng 304 × 78 CSS px thay cho ô compact. Cần cập nhật APK và redeploy backend Render để thay tham số hiển thị; không đổi site key, secret hoặc hostnames. Nếu chỉ cài APK nhưng backend vẫn là bản compact cũ, ô xác minh vẫn giữ hình dạng cũ.

Khi CAPTCHA hoàn thành, dòng trạng thái bên dưới được ẩn hoàn toàn; không còn câu “Bấm Đăng nhập để tiếp tục…”. Kiểm tra GET công khai ngày 25/10/2026 cho thấy endpoint Render vẫn trả `size: compact` tại thời điểm audit. Cần đưa thay đổi `functions/password-captcha.js` lên phiên bản Render đang chạy; chỉ bấm redeploy lại một commit cũ sẽ không áp dụng `normal`. Có thể đối chiếu mã HTML trả về tại `/auth/password-captcha`, tìm `size:'normal'`; không cần in site key hoặc secret để kiểm tra.

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

Không tự tạo key, cấu hình Render hoặc deploy production. Nếu backend chưa có key, app sẽ không cho đăng nhập password tới khi đủ cấu hình. Bản đổi từ popup sang inline trước đây chỉ cần APK; bản ô ngang hiện tại còn cần redeploy backend để dùng `size: normal`. Google Sign-In/đăng ký dùng luồng cũ. Không thay Firestore Rules, collection, index hoặc dữ liệu production.

## Luồng và mức bảo vệ

```text
Login → WebView ngay dưới password → Google v2 checkbox/challenge
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
- Xoay màn, trở lại từ background sau hết hạn, đóng thử thách, mất mạng hoặc mở lại trang → không tự đánh dấu đã xác minh.
- Màn hình ngắn, chữ lớn và dọc/ngang: thử thách nằm trong WebView trên trang đăng nhập; kiểm tra chiều cao tự mở rộng và cuộn được nội dung Google khi cần.
- Google Sign-In và đăng ký không cần hoàn thành CAPTCHA; kiểm tra bằng tài khoản demo phù hợp.
- Không dùng test key để tuyên bố production đã được bảo vệ. Không thay key thật bằng một checkbox tự viết hoặc token stub.

Unit test backend dùng provider stub để kiểm tra nhánh thành công/thất bại, thiếu cấu hình, hostname, chống replay/concurrency, rate limit và không chuyển tiếp password. JVM test kiểm tra thời hạn/token/email và phân biệt password với Google/đăng ký. Chưa test widget Google thật hoặc UI Android trên thiết bị; giữ nguyên emulator5554 theo yêu cầu trước đó. Log và kết quả lần chạy mới nằm tại `docs/audits/password-captcha/`.

## Lịch sử sửa hộp thoại trống ngày 25/10/2026

Bản đầu đặt WebView có height 0 và weight 1 trong custom panel tự co của AlertDialog. Vì panel không cấp phần chiều cao còn lại, WebView có thể bị co hoàn toàn, chỉ còn tiêu đề/mô tả/nút Đóng. Bản sửa dùng viewport có chiều cao thực, điều chỉnh theo màn hình và chiều ngang; cửa sổ đo theo nội dung. Có trạng thái native khi tải, lỗi HTTP hoặc lỗi mạng, không bỏ qua CAPTCHA.

Kiểm tra GET công khai `https://rescuefarm-backend.onrender.com/auth/password-captcha` trong lượt sửa nhận HTTP 200 và HTML chứa widget Google/script/cầu nối ứng dụng. Không gửi tài khoản, mật khẩu hoặc token. Đây là bằng chứng trang backend đã được phục vụ, không thay thế kiểm chứng challenge/site key/secret với Google thật. Đối với lỗi co chiều cao này, chỉ cần cài APK mới; không cần tạo lại key hoặc deploy backend lại. Chưa thao tác UI trên emulator5554.

Luồng hộp thoại này đã được thay bằng widget trực tiếp trên Login. `InlinePasswordCaptcha` gắn WebView vào XML, còn `assets/auth/password-captcha-inline.js` điều chỉnh trình bày trang HTML hiện có: ẩn hướng dẫn thừa, quan sát kích thước iframe và gọi bridge để cập nhật chiều cao. Script không lấy dữ liệu bên trong iframe khác origin, không tạo token, không đổi bước kiểm tra server. Timer 500 ms chỉ đo DOM cục bộ, không polling backend hoặc Firestore. Reset dùng widget Google đang có, không tải lại endpoint mỗi ký tự email.

Bản sửa cắt lưới ảnh nhận diện iframe `/bframe` cả khi nằm trong `#captcha`. Khi người dùng chạm widget, Android dành sẵn ít nhất 620 dp cho phần tương tác, không chặn chiều cao theo vùng màn hình bị bàn phím thu nhỏ. Khi thử thách hiện, chiều cao còn tăng theo iframe; trang đăng nhập bên ngoài cuộn được để tới nút xác nhận. Hoàn thành/reset thì thu về khối ngang. Không sửa nội dung ảnh, checkbox hay thuật toán xác minh của Google.

Unit test DOM mô phỏng kiểm tra thu gọn/mở rộng, lỗi/hết hạn và reset. Đây không thay thế thao tác widget Google thật trên Android; chưa vận hành emulator5554. Nếu có lỗi key/domain, cần lấy đúng thông báo Google để kiểm tra, không thay cấu hình theo suy đoán.

## Nguồn kỹ thuật đối chiếu

- [Google – reCAPTCHA v2 widget](https://developers.google.com/recaptcha/docs/display).
- [Google – server-side verification và giới hạn response token](https://developers.google.com/recaptcha/docs/verify).
- [Google – Identity Platform với reCAPTCHA Enterprise](https://docs.cloud.google.com/identity-platform/docs/recaptcha-enterprise).
- [Google – SDK mobile không cung cấp widget checkbox](https://docs.cloud.google.com/recaptcha/docs/setup-overview-mobile).

Đối chiếu ngày 25/10/2026. Quota/chi phí reCAPTCHA là cấu hình của dịch vụ Google, độc lập quota document reads của Firestore; không cam kết miễn phí vô hạn.
