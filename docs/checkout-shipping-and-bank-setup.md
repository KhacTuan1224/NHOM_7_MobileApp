# Cấu hình phí giao hàng và chuyển khoản

## 1. Triển khai backend, Rules và indexes

Triển khai backend Render trước và chờ log báo `RescueFarm API listening`. Khi khởi động, backend tự chuyển mã ngân hàng, số tài khoản và QR cũ khỏi `sellerPublicProfiles` sang collection riêng `sellerPaymentProfiles`. Không cần chạy migration thủ công. Sau đó, từ thư mục dự án chạy lệnh để triển khai cả Rules và indexes:

```powershell
firebase deploy --only firestore --project nhom7-rescuefarm
```

Đảm bảo Firebase CLI đang đăng nhập đúng tài khoản trước khi deploy. Không phân phối APK mới trước khi backend migration và Rules deploy xong.

## 2. Cấu hình biểu phí theo km

Trong Firebase Console → Firestore Database → Data, tạo document:

- Collection: `appConfig`
- Document ID: `shipping`
- `baseFeeVnd`: Number — phí cơ bản, đơn vị VND
- `perKmFeeVnd`: Number — phí cho mỗi km vượt ngưỡng miễn phí, đơn vị VND/km
- `freeDistanceKm`: Number — số km đầu miễn phí
- `updatedAt`: Timestamp

Công thức hiện dùng là `baseFeeVnd + ceil(max(0, khoảng cách km − freeDistanceKm) × perKmFeeVnd)`. Khoảng cách dùng công thức Haversine theo đường chim bay giữa tọa độ cửa hàng và địa chỉ nhận; đây chưa phải khoảng cách theo lộ trình đường bộ. Giá trị biểu phí do dự án tự quyết định, nên điền theo mức bạn muốn áp dụng.

## 3. Cấu hình từng seller

Mỗi seller mở **Hồ sơ người bán** — nơi duy nhất chỉnh địa chỉ và GPS cửa hàng — rồi cập nhật và lưu:

- Mã ngân hàng VietQR, ví dụ `MB`, `VCB` hoặc mã tương ứng ngân hàng đó.
- Tên chủ tài khoản đúng như ngân hàng.
- Số tài khoản.
- GPS tự điền một ô địa chỉ cửa hàng; kiểm tra và bổ sung số nhà, tên đường nếu cần. Không bắt buộc chọn danh mục tỉnh/huyện/phường ở hồ sơ người bán.
- Đứng tại cửa hàng và nhấn **Lấy GPS cửa hàng**. Không nhập kinh/vĩ độ thủ công.

Khi seller lưu hồ sơ, ứng dụng tự cập nhật hồ sơ cửa hàng công khai và `sellerPaymentProfiles/{sellerId}` riêng tư. Chỉ Seller sở hữu và Admin được đọc hồ sơ thanh toán; backend dùng hồ sơ này để xác thực chuyển khoản và chụp thông tin vào Payment của đơn. Tọa độ `0, 0` được coi là chưa khai báo.

Nguồn chuẩn để tính phí là `sellerPublicProfiles/{sellerId}.latitude/longitude/address`. `users/{sellerId}` chỉ giữ bản sao tương thích; GPS của hồ sơ cá nhân không được dùng làm điểm gửi. Màn hình sửa hồ sơ cá nhân của seller không còn phần vị trí. Trang QR chỉ cập nhật `sellerPaymentProfiles.qrUrl`, không được ghi đè địa chỉ/GPS/ngân hàng của cửa hàng.

Customer chọn địa chỉ nhận đã lưu có GPS hoặc lấy GPS tại nơi nhận. Guest nhập địa chỉ nhận và lấy GPS tại nơi nhận. Checkout tự tải địa chỉ người bán và không có ô nhập điểm gửi. Danh sách tỉnh/huyện/phường phục vụ địa chỉ giao nhận, không dùng thay cho GPS trong công thức số km. Đổi địa chỉ nhận hàng bằng tay sẽ xóa GPS cũ để tránh dùng nhầm điểm nhận; phải lấy lại GPS hoặc chọn địa chỉ đã lưu. Với seller, sửa nhãn địa chỉ cửa hàng không xóa GPS; khi chuyển địa điểm thực tế phải lấy GPS lại tại cửa hàng mới.

Seller và Admin không có sổ địa chỉ nhận hàng của Customer. Các bản ghi cũ không bị xóa nhưng Rules không cho những vai trò này truy cập. Pickup tại cửa hàng và giải cứu tại chỗ không thu phí giao hàng; vị trí giải cứu tại chỗ lấy từ Campaign (hoặc GPS di động còn hiệu lực), không thay đổi vị trí cửa hàng.

Không tự thay GPS lỗi bằng một tọa độ thành phố mặc định. Khi GPS máy ảo chưa được đặt, chọn vị trí trong Extended Controls rồi lấy GPS lại. Với địa chỉ nhận hàng và điểm giải cứu dùng danh mục tỉnh/huyện/phường, địa chỉ từ geocoder chỉ tự điền khi khớp danh mục; nếu không khớp, người dùng giữ/chọn địa chỉ đúng và GPS đã lấy vẫn được giữ.

Màn hình hiển thị phí ước tính; transaction khi đặt đơn tính lại phí từ Firestore và ghi đúng vào Order/Shipment. Không có cấu hình phí hoặc thiếu tọa độ thì đơn giao tận nơi bị chặn kèm thông báo.

Đơn giao hàng lưu ảnh chụp địa chỉ/GPS gửi và nhận trong Shipment để lần sửa hồ sơ sau không làm thay đổi vị trí của đơn đã đặt. Triển khai backend Render và Firestore Rules mới trước khi phân phối APK mới.

## 4. Tạo QR cửa hàng qua VietQR API và lưu Cloudinary

Seller lưu thông tin ngân hàng trong **Hồ sơ người bán**, mở **Mã QR thanh toán** rồi nhấn **Tạo mã QR**. Ứng dụng không còn ô URL, chọn ảnh hoặc nút lưu QR thủ công. Ảnh tạo thành công được hiển thị và tự lưu.

Backend Render cần các biến môi trường sau (chỉ cấu hình trong Environment/Secrets, không đặt trong Gradle hoặc APK):

- `VIETQR_CLIENT_ID`: Client ID của tài khoản My VietQR.
- `VIETQR_API_KEY`: API key của tài khoản My VietQR.
- `CLOUDINARY_SIGNING_URL`: URL Worker đang ký upload avatar.
- `CLOUDINARY_CLOUD_NAME`: Cloud name đang dùng cho avatar.

Triển khai Worker mới trước, rồi backend Render và APK. Endpoint `POST /seller/payment-qr` xác thực Seller, đọc ngân hàng từ `sellerPaymentProfiles` của chính tài khoản đó, tra BIN qua `/v2/banks`, gọi `https://api.vietqr.io/v2/generate` bằng header `x-client-id`/`x-api-key`, rồi upload PNG bằng chữ ký từ Worker. Cloudinary API secret vẫn chỉ nằm ở Worker. URL HTTPS được tự ghi vào `sellerPaymentProfiles.qrUrl` sau khi kiểm tra ngân hàng chưa bị đổi trong lúc tạo.

Mỗi ảnh có public ID riêng, không ghi đè ảnh cũ. Thay đổi ngân hàng trong hồ sơ sẽ xóa liên kết QR cũ và yêu cầu tạo lại. Khi dịch vụ lỗi, ứng dụng báo lỗi và giữ QR đang lưu. Thiếu key sẽ báo rõ cần cấu hình backend; không tạo QR giả.

Đây là QR nhận tiền chung của cửa hàng, không có số tiền cố định. Màn hình chi tiết đơn dùng QR riêng theo thông tin ngân hàng đã chụp vào Payment, kèm đúng số tiền và mã đơn; không dùng ảnh QR chung thay thế. Người bán vẫn xác nhận nhận tiền thủ công.

Tài liệu chính thức: [API Generate](https://www.vietqr.io/danh-sach-api/link-tao-ma-nhanh/api-tao-ma-qr). Theo [thông báo tháng 10/2026](https://www.vietqr.io/thong-bao-cap-nhat), API Generate hoạt động đến hết 31/12/2026 và dừng từ 01/01/2027; cần thay phần tạo QR trong `functions/payment-qr.js` bằng thư viện `vietqr-core` trước thời hạn đó. Luồng upload và lưu Cloudinary có thể giữ nguyên.
