# Dùng lại VietinBank và SePay của Seller cho hũ

Không cần mở hoặc liên kết ngân hàng mới. Không cần tạo webhook hoặc key SePay thứ hai.

## Cách áp dụng cho tài khoản đang có

1. Deploy lại backend Render bằng source mới. APK hũ hiện tại dùng được, không đổi form hoặc QR UI.
2. Giữ nguyên `SEPAY_MERCHANTS_JSON` và webhook `/webhooks/sepay/{SELLER_UID}` hiện tại.
3. Nếu chỉ có một Seller enabled trong `SEPAY_MERCHANTS_JSON`, backend tự chọn đúng Seller đó. Lấy mã ngân hàng/số tài khoản từ kết nối SePay, đối chiếu hồ sơ thanh toán riêng `sellerPaymentProfiles/{uid}`, lấy tên chủ tài khoản từ hồ sơ này. Không hardcode email, mật khẩu hoặc số tài khoản vào source.
4. Nếu đã thêm `RESCUE_FUND_BANK_JSON` theo hướng dẫn cũ, xóa biến này khỏi Render để dùng chế độ tự chọn. Nếu có nhiều merchant enabled, đặt `RESCUE_FUND_SELLER_ID` bằng UID Seller cần dùng; biến này cũng ưu tiên hơn JSON riêng nếu cả hai đang tồn tại. Không nhập email vào biến UID.
5. Admin mở cấu hình hũ, lưu/bật hũ. Customer/Guest mở hũ, nhập tiền, xác nhận để nhận QR có số tiền và nội dung riêng. QR chuyển vào chính VietinBank đang gắn với Seller. Giữ nguyên tên chủ tài khoản thực tế; tên hũ là nhãn riêng trong app.

Các bước deploy ban đầu của toàn tính năng hũ (Worker ảnh, Rules/indexes, cấu hình địa điểm) vẫn theo [hướng dẫn đầy đủ](rescue-fund.md). Riêng bản sửa dùng chung ngân hàng này chỉ đổi backend/tài liệu, không thêm cấu hình ngân hàng vào app.

## Phân biệt tiền hũ và tiền đơn hàng

Callback vẫn đến URL Seller cũ, với cùng HMAC hoặc API Key. Backend xác thực trước khi tra mã:

- Mã có trong `rescueFundReferences`: kiểm tra đúng ngân hàng/số tiền/thời gian rồi ghi donation PAID và cộng số dư hũ. Không tạo đơn, không xác nhận một đơn hàng nào.
- Mã không thuộc hũ: đi tiếp vào xử lý SePay đơn hàng hiện tại.
- Giao dịch ngân hàng dùng khóa `sepayBankTransactions` chung cho cả hai, chống một giao dịch được tính hai lần, kể cả chạy đồng thời.

Số dư ngân hàng và số dư hũ trong app là hai khái niệm riêng. Ví dụ ngân hàng đang có sẵn tiền của Seller, hũ vẫn khởi đầu 0đ; một lần chuyển đúng QR hũ 20.000đ chỉ cộng 20.000đ vào sổ hũ. Không lấy số dư `accumulated` của SePay để cộng vào hũ, cũng không cộng tiền bán hàng thông thường vào hũ.

## Mô phỏng và kiểm thử

Test Emulator dùng webhook giả, không chuyển tiền thật. Đã kiểm tra cả HMAC/API Key, replay, đơn hàng bình thường, hai thứ tự ghi nhận và hai callback cạnh tranh cùng mã giao dịch. Sau khi callback thành công, cache thông tin hũ của tiến trình được làm mới để không tiếp tục hiển thị số dư trước lần nạp.

Kết quả: suite Firestore/API 165/165 qua, chạy lại riêng 37/37 test hũ với bản code cuối đều qua; backend unit 168/168 và Worker 9/9 qua, syntax check thành công. Log: `build/fund-shared-bank-emulator.log`, `build/fund-shared-bank-focused.log`, `build/fund-shared-bank-unit.log`, `build/fund-shared-bank-check.log`. Chưa deploy Render hoặc thử chuyển khoản production trong lượt sửa này.

Khi dùng app với backend production, chỉ callback SePay hợp lệ mới làm donation PAID. Nút “Send test” có `id: 0` không cộng tiền để tránh nhầm tiền giả với tiền đã nhận. Không thêm nút xác nhận/cộng tiền thủ công cho lần ủng hộ. Luồng mô phỏng bằng webhook giả được chạy trên Emulator, không ghi vào Firebase thật.
