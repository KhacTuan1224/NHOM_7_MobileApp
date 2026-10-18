# Xác minh chuyển khoản tự động: VietQR và kết nối ngân hàng

Kiểm tra ngày 17/10/2026. Quyết định của chủ dự án: giữ VietQR; chỉ bật tự động sau khi xác minh điều kiện và phí của tài khoản nhận tiền. Không chuyển sang payOS, không đăng ký/nâng cấp gói tính phí tự động.

## Trạng thái triển khai

- Firestore Rules hiện tại đã triển khai thành công lên nhom7-rescuefarm ngày 17/10/2026, bao gồm sửa danh mục mẫu có ảnh HTTPS cũ và thiếu trường id bên trong document.
- Chưa bật xác nhận thanh toán qua webhook. API đang dùng trong functions/payment-qr.js là /v2/generate để tạo ảnh QR, không phải API đọc giao dịch ngân hàng.
- Backend dự án hiện dùng Node.js/Express trên Render. Không cần tạo thêm Spring Boot chỉ vì ứng dụng Android viết Java/XML.
- Customer đã nghe thay đổi Order từ Firestore; có thể tái sử dụng cơ chế đó khi backend xác thực giao dịch. Không đặt key ngân hàng trong APK, không dùng nút Tôi đã thanh toán hay return URL làm bằng chứng nhận tiền.

## Kết quả đối chiếu tài liệu chính thức

1. VietQR có tài liệu paymentRequests và đăng ký webhook, nhưng phần gửi giao dịch áp dụng cho cổng đã liên kết tài khoản qua Casso. Có key tạo ảnh QR không chứng minh tài khoản đã được cấp quyền này.
   https://www.vietqr.io/paymentRequests/
2. Casso phân biệt ảnh VietQR công khai với kết nối lịch sử giao dịch/webhook sau khi liên kết ngân hàng. Phí phụ thuộc ngân hàng, loại kết nối và gói tài khoản.
   https://casso.vn/flow/bang-gia/
   https://casso.vn/api-mbbank/
   https://casso.vn/api-vietcombank/
3. payOS không có căn cứ để cam kết miễn phí vô hạn cho mọi tài khoản/ngân hàng: trang giải thích gói ghi các hạn mức ưu đãi miễn phí và các gói trả phí; tài liệu thông báo tính năng tạo đơn bị hạn chế khi dùng hết gói. Cần kiểm tra ưu đãi thực tế của tài khoản trước khi sử dụng.
   https://payos.vn/3-phut-hieu-ro-ve-payos/
   https://payos.vn/thong-bao-han-che-tinh-nang-tao-don/
4. API VietQR Generate đang dùng có thông báo ngừng hỗ trợ từ 01/01/2027; việc chuyển sang thư viện tạo QR tại hệ thống chỉ thay cách tạo ảnh, không tạo ra khả năng đọc giao dịch ngân hàng.
   https://www.vietqr.io/thong-bao-cap-nhat

## Thông tin phải kiểm tra trước khi bật tự động

- Ngân hàng và loại tài khoản: cá nhân/doanh nghiệp; đã liên kết ngân hàng trong Casso/My VietQR hay mới đăng ký key tạo QR.
- Quyền webhook/API của đúng tài khoản, cơ chế xác thực webhook và khả năng tra soát giao dịch từ nhà cung cấp.
- Tên gói, phí, VAT/phí ngân hàng nếu có, hạn mức giao dịch, thời hạn ưu đãi, phí sau ưu đãi và việc có tự gia hạn hay không. Chỉ dựa trên điều kiện thực tế đã được chủ dự án chấp thuận.
- Mỗi seller nhận tiền vào tài khoản riêng: mỗi tài khoản phải có liên kết được xác minh tương ứng. Không dùng key của một merchant để tự xác nhận tiền vào tài khoản seller khác chỉ dựa trên thông tin ngân hàng họ nhập.
- URL webhook HTTPS trên Render và cấu hình bảo mật ở server. Không gửi API key, webhook secret, mật khẩu hay sao kê vào chat; cấu hình qua Environment/Secrets của backend.

## Yêu cầu triển khai sau khi điều kiện được xác minh

- Giữ VietQR để tạo QR theo từng đơn với số tiền và mã đối soát duy nhất.
- Endpoint nhận webhook ở Express xác thực theo đúng phiên bản nhà cung cấp, ràng buộc liên kết ngân hàng với seller; không tin sellerId, trạng thái PAID hoặc tổng tiền do Android gửi lên.
- Khớp giao dịch ghi có, tài khoản nhận, mã đối soát và số tiền VND với Order/Payment đã lưu; kiểm tra sai tài khoản, thiếu/thừa tiền, sai mã hoặc giao dịch khi đơn đã hủy thì chuyển đối soát, không tự xác nhận đơn.
- Chống xử lý lặp bằng mã giao dịch nhà cung cấp và transaction Firestore. Đơn được xác nhận PAID/CONFIRMED nguyên tử, có nguồn xác minh/audit và không chốt tồn kho lần hai.
- Webhook/đồng bộ tiền về chỉ xác nhận thanh toán và đơn; bước xác nhận đã nhận hàng vẫn do người mua thực hiện theo luồng hiện tại.
- Customer dùng listener Firestore hiện có để nhận trạng thái; Guest chỉ đọc trạng thái bằng token đơn. Không bổ sung polling ngân hàng từ APK.
- Chỉ bật cấu hình cho seller đã kiểm tra đủ điều kiện. Các seller còn lại giữ quy trình xác nhận thủ công hiện tại, hiển thị đúng trạng thái thay vì báo tự động khi chưa có kết nối.

## Kết luận cho tài khoản người dùng cung cấp

Người dùng xác nhận đang nhận tiền qua VietinBank, tài khoản cá nhân và chưa liên kết Casso/My VietQR.

Đối chiếu bảng giá trực tiếp ngày 17/10/2026: dịch vụ Ngân hàng mở VietinBank cá nhân nằm trong phần STANDARD của bảng giá Casso Flow; phần SPONSOR miễn phí liệt kê dịch vụ VA OCB/KienlongBank, không có VietinBank cá nhân. Vì vậy chưa có căn cứ đáp ứng yêu cầu miễn phí của người dùng trên tài khoản hiện tại, và không bật tự xác minh. Ưu đãi dùng thử không được coi là miễn phí lâu dài.

Nguồn bảng giá trực tiếp: https://api.flow.casso.vn/new-pricing-table (liên kết từ https://casso.vn/flow/bang-gia/).

Điều kiện tiếp tục là ngân hàng phải được liên kết vào dịch vụ, quyền webhook/API đã xác minh và có gói phí/ưu đãi cụ thể được chủ tài khoản chấp thuận. Không yêu cầu gửi mật khẩu, OTP, API key vào chat; không thực hiện đăng ký ngân hàng, liên kết hoặc mua gói trong lần triển khai này. Luồng xác nhận thủ công hiện tại được giữ nguyên, không chuyển sang payOS.

## Cập nhật: chủ dự án chọn tích hợp SePay

Sau khi được thông báo gói FREE 50 giao dịch/tháng có phí vượt mức, người dùng đã yêu cầu triển khai giữ VietQR và bổ sung SePay. Người dùng xác nhận sẽ tự liên kết VietinBank/OTP theo hướng dẫn. Code mới hỗ trợ webhook xác thực và đối soát theo seller, chỉ kích hoạt cho đơn mới khi có cấu hình backend tương ứng. Phần phân tích Casso/payOS bên trên được giữ làm lịch sử quyết định; trạng thái tích hợp và hướng dẫn hiện tại xem docs/sepay-setup.md.
