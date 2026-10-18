# Kho khuyến mãi của người mua

## Kết quả rà soát trước thay đổi

Trước đây, khuyến mãi đang bật và trong thời hạn được tự động áp dụng tại:

- **Chi tiết sản phẩm → Tính giá:** `ProductViewModel.calculatePrice()` đọc trực tiếp khuyến mãi của sản phẩm.
- **Giỏ hàng:** thêm sản phẩm, đổi số lượng và kiểm tra lại giỏ dùng khuyến mãi nhận từ `FirebaseProductRepository.getCartQuote()`.
- **Thanh toán:** backend đọc khuyến mãi hiện hành khi báo giá và đọc lại trong transaction tạo đơn. Luồng này áp dụng cho cả customer và guest, chưa có khái niệm lưu thẻ.
- **Home → Mua nhiều giá tốt:** hiển thị mức giá quảng bá theo bậc số lượng. Giá giải cứu thông thường trên thẻ sản phẩm là giá sản phẩm, độc lập với promotion.

## Hành vi mới

Giữ nguyên form, model và quy trình seller tạo/sửa/bật/tắt khuyến mãi.

Người mua mở **Kho khuyến mãi** từ Home, Hồ sơ, Chi tiết sản phẩm, Giỏ hàng hoặc Thanh toán:

- **Khuyến mãi của tôi:** thẻ đã lưu theo tài khoản, gồm cả thẻ không còn hiệu lực để người dùng nhận biết và bỏ lưu.
- **Tất cả khuyến mãi:** các khuyến mãi đang có hiệu lực trên sản phẩm đang bán của seller đang hoạt động, đã duyệt. Có phân trang 24 thẻ/lần.
- Guest xem được danh sách chung, nhưng phải đăng nhập customer để lưu và hưởng khuyến mãi. Guest mua trực tiếp vẫn được giá giải cứu, không được promotion hoặc giá sỉ thuộc promotion.
- Customer lưu thẻ một lần. Khi mua đúng sản phẩm, hệ thống tự áp dụng thẻ đã lưu còn hiệu lực. Các ngưỡng số lượng vẫn phải đáp ứng.
- Thẻ đã hết hạn, bị tắt, sản phẩm ngừng bán hoặc seller không còn hợp lệ không được áp dụng. Bỏ lưu làm mất điều kiện áp dụng cho các lần báo giá/đặt hàng tiếp theo.
- Khi trở lại sản phẩm, giỏ hàng và thanh toán từ kho, giá được tính lại. Báo giá ghi rõ dòng nào đã áp dụng khuyến mãi đã lưu.

Khuyến mãi vẫn gắn theo `productId` như mô hình hiện tại, không phát sinh mã voucher nhập tay, giới hạn lượt dùng hay cơ chế dùng một lần. Khi seller sửa cùng khuyến mãi, các điều kiện hiện hành được dùng lúc báo giá; đây không phải bản sao mức giảm được khóa vĩnh viễn tại thời điểm lưu. Sản phẩm từ seller khác không kế thừa quyền áp dụng đã lưu.

Đơn đã tạo giữ nguyên giá snapshot và không bị tính lại khi thẻ thay đổi. Nếu bỏ lưu hoặc giá thay đổi sau báo giá nhưng trước tạo đơn, backend trả `QUOTE_CHANGED` khi tổng tiền khác để người mua kiểm tra lại; transaction không tạo đơn/giữ tồn kho theo báo giá cũ.

## Lưu trữ và phân quyền

- `customerPromotionWallets/{customerId}`: chủ sở hữu và thời gian cập nhật kho.
- `customerPromotionWallets/{customerId}/items/{productId}`: `customerId`, `productId`, `sellerId`, `productName`, `savedAt`.
- Chỉ backend được ghi/xóa quyền áp dụng sau khi xác thực customer. Client chỉ đọc kho của chính tài khoản customer đang hoạt động. Customer khác, seller, admin và guest không được đọc kho qua SDK hoặc tự ghi giả thẻ.
- Backend báo giá/tạo đơn tự đọc kho theo UID đã xác thực, không nhận quyền áp dụng từ `customerId`, giá giảm hoặc danh sách thẻ do client tự gửi.
- Không tạo document mẫu hay placeholder. Collection xuất hiện khi có người dùng thực sự lưu khuyến mãi.

API mới trong `functions/promotion-wallet.js`, được mount qua commerce router:

| API | Quyền và nội dung |
|---|---|
| `POST /promotions/catalog` | Danh sách chung cho guest; tài khoản customer được biết trạng thái đã lưu. `mine: true` chỉ cho customer; hỗ trợ `cursor`. |
| `POST /promotions/save` | Customer; body `{productId}`. Kiểm tra sản phẩm, seller, thời hạn/bật khuyến mãi trước khi lưu. Lưu trùng không tạo thẻ trùng. |
| `POST /promotions/remove` | Customer; body `{productId}`. Chỉ xóa thẻ trong kho của UID gọi API. |

## Triển khai

Người dùng xác nhận sẽ **tự deploy Render**. Backend mới chưa được triển khai lên Render trong phiên này.

1. Rules mới **đã được áp dụng và đọc lại xác nhận trên Firebase** trong phiên này, không ghi/xóa document kinh doanh. Quyền tạo/sửa khuyến mãi của seller giữ nguyên; bổ sung quyền đọc kho riêng và cấm client ghi thẻ. Backup rules và kết quả deploy cục bộ ở `build/promotion-wallet-deploy/`. Khi triển khai môi trường khác, dùng `firestore.rules` trong repo.
2. Đẩy code backend mới lên repository Render đang sử dụng, rồi deploy service. Không cần thêm API key, biến môi trường hay thay đổi tài khoản SePay.
3. Kiểm tra `GET /health` có `promotionWalletVersion: 1` và `promotionPolicy: "SAVED_CUSTOMER_ONLY"`.
4. Cài `app/build/outputs/apk/debug/app-debug.apk` mới. APK kiểm tra `promotionPolicy` trong báo giá để không đặt đơn theo chính sách cũ của backend chưa cập nhật.
5. Mở kho bằng guest: xem thẻ được, lưu phải đăng nhập. Dùng customer A lưu thẻ rồi mở sản phẩm/checkout; customer B không được hưởng thẻ của A. Bỏ lưu và kiểm tra giá trở lại giá giải cứu. Thử lần lượt phần trăm, tiền cố định và bậc số lượng.

Lưu ý: các APK/backend cũ vẫn theo hành vi cũ cho đến khi được cập nhật. Cần triển khai backend mới trước khi phân phối APK mới.

## Kiểm chứng

- Android assembleDebug, unit tests và assembleDebugAndroidTest thành công; **218 unit tests, không lỗi**. Instrumentation APK đã biên dịch, chưa chạy trên thiết bị.
- **88/88 kiểm thử Firestore/HTTP Emulator đạt**, bao phủ cách ly kho, cấm ghi giả, customer chưa lưu, guest không hưởng thẻ, 3 loại giảm giá, hết hạn/tắt/bỏ lưu, lưu trùng, phân trang, snapshot đơn hàng và thay đổi quyền áp dụng sau báo giá.
- Log cục bộ: `build/promotion-wallet-firestore.log`, `build/promotion-wallet-android.log`.
- Giao diện/resource XML đã kiểm tra cho cả điện thoại và layout màn hình rộng. Chưa kiểm tra trực quan trên emulator trong phiên này.
