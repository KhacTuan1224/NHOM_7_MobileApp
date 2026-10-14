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

Mỗi seller mở Hồ sơ cửa hàng, cập nhật và lưu:

- Mã ngân hàng VietQR, ví dụ `MB`, `VCB` hoặc mã tương ứng ngân hàng đó.
- Tên chủ tài khoản đúng như ngân hàng.
- Số tài khoản.
- Vĩ độ và kinh độ địa điểm lấy hàng của cửa hàng.

Khi seller lưu hồ sơ, ứng dụng tự cập nhật hồ sơ cửa hàng công khai và `sellerPaymentProfiles/{sellerId}` riêng tư. Chỉ Seller sở hữu và Admin được đọc hồ sơ thanh toán; backend dùng hồ sơ này để xác thực chuyển khoản và chụp thông tin vào Payment của đơn. Tọa độ `0, 0` được coi là chưa khai báo.

Người mua chọn một địa chỉ đã lưu có tọa độ trong Checkout. Màn hình hiển thị phí ước tính; transaction khi đặt đơn tính lại phí từ Firestore và ghi đúng vào Order/Shipment. Không có cấu hình phí hoặc thiếu tọa độ thì đơn giao tận nơi bị chặn kèm thông báo.

## 4. QR chuyển khoản

Sau khi đặt đơn bằng chuyển khoản, trang chi tiết đơn hiển thị QR theo số tiền và mã đơn. Người bán vẫn cần xác nhận đã nhận tiền thủ công; QR không tự đối soát giao dịch ngân hàng. Định dạng ảnh QR dùng VietQR Quick Link: https://www.vietqr.io/en/danh-sach-api/link-tao-ma-nhanh/
