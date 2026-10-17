# Kích hoạt SePay cho VietinBank cá nhân

Code đã hỗ trợ SePay Webhooks và giữ VietQR để hiển thị QR theo từng đơn. Việc liên kết ngân hàng và lưu secret là bước người sở hữu tài khoản phải thực hiện. Không gửi mật khẩu, OTP, API key hoặc webhook secret vào chat/Git/APK.

## 1. Liên kết tài khoản ngân hàng

1. Đăng ký/đăng nhập https://my.sepay.vn và chọn gói FREE nếu muốn dùng hạn mức miễn phí.
2. Vào Ngân hàng / Tài khoản ngân hàng → Kết nối thêm → VietinBank → Cá nhân.
3. Nhập thông tin chủ tài khoản theo yêu cầu của SePay và tự xác thực OTP của VietinBank.
4. Kiểm tra đúng tài khoản đã liên kết thành công. Tài khoản này phải trùng số tài khoản đã lưu trong Hồ sơ người bán trên app.

Hướng dẫn chính thức: https://docs.sepay.vn/ket-noi-vietinbank.html

Gói FREE hiện công bố 50 giao dịch/tháng và có thể tính phí vượt mức. Kiểm tra phần Sử dụng/Thanh toán trong tài khoản SePay, cơ chế tính phí và lựa chọn khóa vượt mức nếu họ cung cấp; không mặc định rằng webhook hoặc backend có thể chặn phí của ngân hàng/SePay. Backend không mua gói, không gọi API thanh toán phí. Dừng nhận webhook không ngăn các giao dịch đã phát sinh tại ngân hàng bị nhà cung cấp tính phí. Nguồn: https://sepay.vn/bang-gia.html

## 2. Lấy UID seller và tạo webhook ở SePay

- UID seller là ID document sellerPublicProfiles trong Firebase Console hoặc UID tài khoản seller trong Firebase Authentication. Không dùng UID customer đang mua hàng.
- Xác định URL Render backend hiện tại (cùng URL RENDER_BACKEND_URL của app).
- Tạo webhook tại SePay với URL `https://YOUR_RENDER_HOST/webhooks/sepay/SELLER_FIREBASE_UID`.
- Chọn **Tiền vào** và chọn đúng tài khoản VietinBank vừa liên kết, không chọn tất cả tài khoản.
- Chọn kiểu nội dung **application/json** và xác thực **HMAC-SHA256**. Tạo Secret Key bằng chức năng của SePay, lưu bản sao vào Environment của Render. Không dùng key tạo ảnh VietQR thay cho webhook secret.
- Chưa bật webhook Live cho đến khi backend đã deploy và kiểm tra cấu hình.

Cấu hình nhận diện mã thanh toán tại Cấu hình Công ty → Cấu hình chung → Cấu trúc mã thanh toán:

| Thuộc tính | Giá trị |
| --- | --- |
| Tiền tố | SEVQR |
| Độ dài hậu tố tối thiểu | 20 |
| Độ dài hậu tố tối đa | 20 |
| Loại ký tự | Số và chữ |
| Trạng thái | Đang hoạt động |

Bật nhận diện mã; webhook chọn tiền tố SEVQR và bật bỏ qua giao dịch không có mã. App tạo mã đúng 25 ký tự, bắt đầu bằng SEVQR như yêu cầu của VietinBank cá nhân.

Tài liệu: https://developer.sepay.vn/vi/sepay-webhooks/tao-webhook và https://developer.sepay.vn/vi/sepay-webhooks/cau-hinh-ma-thanh-toan

## 3. Cấu hình Render

Trong Environment của service Render chạy functions/server.js, thêm `SEPAY_MERCHANTS_JSON`. Dùng mẫu functions/sepay-merchants.example.json, thay UID, số tài khoản, secret và đổi enabled thành true **sau khi đã liên kết và kiểm tra điều kiện gói**.

```json
{
  "SELLER_FIREBASE_UID": {
    "enabled": true,
    "bankCode": "ICB",
    "accountNumber": "SO_TAI_KHOAN_VIETINBANK_DA_LIEN_KET",
    "authMode": "hmac",
    "webhookSecret": "SECRET_WEBHOOK_TU_SEPAY_IT_NHAT_32_KY_TU"
  }
}
```

Số tài khoản phải là chuỗi để giữ số 0 đầu. Không lưu JSON này vào Firestore public hoặc Gradle. Một tài khoản ngân hàng không được gán cho nhiều seller. Mỗi seller có kết nối và secret riêng; tiền vẫn về tài khoản riêng của họ.

Deploy code backend mới và Restart service sau khi lưu Environment. Endpoint `/health` vẫn dùng để kiểm tra service hoạt động. Có thể kiểm tra cú pháp và cấu hình trước khi deploy bằng `npm run check` và `npm run check:sepay` với env tương ứng; lệnh check:sepay không in secret hoặc số tài khoản đầy đủ.

Mọi đơn BANK_TRANSFER mới đều có transferContent bắt đầu SEVQR và mapping bankTransferReferences, kể cả khi chưa bật webhook. Không có entry enabled cho seller → verificationProvider vẫn MANUAL; có tiền tố không đồng nghĩa đã bật tự xác minh. Entry đã bật nhưng sai ngân hàng/số tài khoản → từ chối tạo đơn chuyển khoản thay vì sinh QR nhận tiền sai. Đơn tạo trước khi cấu hình vẫn giữ phương thức xác nhận cũ, để không đổi mã khi khách đang thanh toán. Muốn kiểm tra SePay, tạo đơn mới sau khi cấu hình xong.

## 4. Kiểm tra trước khi dùng thật

- Không trỏ webhook Test mode tới dữ liệu production và không dùng chung secret giữa Test/Live. Giao dịch mô phỏng có cấu trúc giống giao dịch thật; secret/endpoint/database phải tách biệt.
- Nút Gửi thử của SePay thường dùng id 0; endpoint trả success nhưng không xác nhận đơn thật cho id 0. Chỉ dùng để kiểm tra kết nối, không coi đây là bằng chứng ngân hàng đã liên kết thành công.
- Sau khi bật webhook Live, tạo đơn mới giá trị nhỏ bằng BANK_TRANSFER. Trang chi tiết phải hiển thị mã SEVQR và thông báo tự xác minh; seller không có nút xác nhận tiền thủ công.
- Quét đúng QR và tự thực hiện giao dịch thật từ ngân hàng của người mua. Sau khi SePay gửi webhook, Order chuyển CONFIRMED/PAID, Payment lưu nguồn SEPAY_WEBHOOK và mã giao dịch. Không xác nhận nhận hàng tự động.
- Customer nhận cập nhật qua Firestore listener hiện có. Guest dùng API trạng thái bằng token đơn (chu kỳ polling hiện có), không gọi ngân hàng từ app.
- Khi đã nhận hàng, người mua bấm Đã nhận hàng như trước.

## 5. Kiểm tra lỗi và đối soát

### QR vẫn có nội dung RF thay vì SEVQR

Kiểm tra document `payments/{orderId}` của chính đơn đang xem:

- `verificationProvider = SEPAY` và `transferContent = SEVQR` + 20 ký tự hex: đây là đơn tự động; Android phải dùng nguyên mã đó. Nếu app vẫn hiện RF, cập nhật APK và mở lại đơn.
- `verificationProvider = MANUAL` và có mã SEVQR hợp lệ: QR đã đúng tiền tố nhưng seller chưa bật xác minh tự động khi tạo đơn. Kiểm tra deploy Render, `SEPAY_MERCHANTS_JSON`, enabled và UID.
- Thiếu transferContent hoặc còn RF: đơn cũ hoặc backend chưa deploy bản sửa sinh mã SEVQR cho cả luồng MANUAL. Không tự thêm SEVQR vào mã RF của đơn cũ vì backend chưa có mapping tương ứng.
- Mọi đơn chuyển khoản thiếu/sai transferContent: app chặn QR; kiểm tra dữ liệu backend, không dùng mã RF thay thế. Không đổi mã của đơn đã chuyển tiền; đối soát giao dịch cũ trước.

VietinBank cá nhân yêu cầu nội dung bắt đầu bằng SEVQR để ngân hàng gửi thông báo tới SePay. Xóa bộ lọc webhook không bảo đảm lấy lại giao dịch cũ thiếu tiền tố. Chỉ gửi lại webhook khi giao dịch thực sự đã xuất hiện trong SePay; kiểm tra `sepayWebhookEvents` sau khi gửi, vì HTTP 200 cũng có thể là giao dịch được lưu REVIEW và chưa xác nhận thanh toán. Đối với tiền đã chuyển bằng mã RF, đối soát giao dịch thật với ngân hàng/SePay, không chuyển thêm lần nữa để trả cùng một đơn và không tự đánh dấu PAID chỉ từ ảnh chụp.

Nguồn điều kiện SEVQR: https://docs.sepay.vn/ket-noi-vietinbank.html

Các collection sau chỉ backend/Admin SDK truy cập; app không có quyền tự ghi:

| Collection | Nội dung |
| --- | --- |
| bankTransferReferences | Mã SEVQR gắn đúng orderId và sellerId |
| sepayWebhookEvents | APPLIED, REVIEW hoặc IGNORED cùng lý do |
| sepayBankTransactions | Chống tái sử dụng một giao dịch ngân hàng |

Các trường hợp REVIEW gồm sai ngân hàng/tài khoản/mã, thiếu hoặc thừa tiền, thiếu mã tham chiếu, giao dịch trước khi tạo đơn, đơn hủy/đã trả tiền và giao dịch đã dùng cho đơn khác. Hệ thống ghi nhận để chủ dự án kiểm tra tại Firebase Console; không cộng dồn tự động khoản thiếu/thừa, không tự hoàn tiền và không giả xác nhận.

HTTP 401: kiểm tra HMAC secret, thời gian server và chế độ xác thực. HTTP 503: seller chưa bật hoặc JSON cấu hình sai. HTTP 409: cùng mã giao dịch gửi lại với dữ liệu khác. Lỗi database trả lỗi để SePay retry; phản hồi success chỉ sau khi đã lưu transaction thành công. Webhook đã xử lý gửi lại không thay paidAt, không tạo thêm thông báo và không cộng tồn kho lần hai.

Có thể dùng API-key mode khi cần tương thích: đổi authMode thành apikey và chọn API Key ở SePay với đúng webhookSecret; header phải là Authorization: Apikey .... Không bật chế độ không xác thực.

Nguồn xác thực/payload chính thức: https://developer.sepay.vn/vi/sepay-webhooks/xac-thuc và https://developer.sepay.vn/vi/sepay-webhooks/tich-hop-webhook
