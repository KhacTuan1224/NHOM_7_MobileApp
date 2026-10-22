# Gỡ khuyến mãi sản phẩm và càng mua càng giảm — 22/10/2026

Đã bỏ các mức giảm theo tiền hàng cùng Seller/nhiều Seller, giảm theo số lượng,
khuyến mãi PERCENT/FIXED/VOLUME và kho khuyến mãi của Customer.

- Seller: bỏ menu “Khuyến mãi & giá sỉ”, nút ưu đãi trên sản phẩm, màn tạo/sửa và danh sách khuyến mãi.
- Guest/Customer: bỏ “Mua nhiều giá tốt” ở Home, kho khuyến mãi ở hồ sơ/chi tiết sản phẩm/giỏ/thanh toán, các dòng giảm thêm ở checkout.
- Backend: bỏ module và API `/promotions/*`, chính sách mua nhiều, phân bổ giảm giá, tạo yêu cầu nền tảng bù giá và thông báo khuyến mãi.
- Firestore: bỏ quyền truy cập collection cũ, quyền tạo sự kiện khuyến mãi; không còn seed khuyến mãi.

## Giá sau thay đổi

Tiền hàng = tổng từng dòng `làm tròn(đơn giá giải cứu) × số lượng`, làm tròn từng dòng theo VND.
Tổng thanh toán = tiền hàng + phí giao hàng. Khách vẫn đặt giỏ nhiều Seller;
mỗi Seller có đơn, phí giao và thanh toán riêng, giữ tính nguyên tử khi giữ tồn.

Giữ `originalPrice` và `rescuePrice`: đây là giá gốc/giá giải cứu của sản phẩm.
Giữ Flash Sale và Hộp bí ẩn cùng mức giá của từng gói.
Giữ giá chụp trong đơn và thanh toán đã tạo, kể cả đơn đang chờ chuyển khoản.
Không thu thêm tiền, không tính lại đơn cũ. Trường `quantityDiscount` trong bộ đọc/cache chỉ phục vụ lịch sử.
Luồng SePay webhook tự xác nhận thanh toán không thay đổi.

## Dữ liệu Firebase đã xử lý

Project `nhom7-rescuefarm`: đã sao lưu rồi xóa **10 bản ghi**:

| Collection | Số bản ghi đã xóa |
|---|---:|
| `promotions` | 3 |
| `customerPromotionWallets` và subcollection | 4 |
| `notificationOutbox` chỉ sự kiện PROMOTION | 3 |
| `platformDiscountClaims` | 0 (không có dữ liệu) |

Đã quét lại sau xóa, số bản ghi thuộc phạm vi này còn lại: **0**.
Không sửa/xóa `orders`, `payments`, `products`, `productBatches`, giỏ hàng hoặc tài khoản.
Backup dữ liệu: `build/discount-retirement/2026-10-22T16-16-12-626Z/backup.json`.
Kết quả: `applied.json` cùng thư mục. Cần sao chép thư mục backup ra nơi an toàn trước khi chạy Gradle clean.

Rules mới đã triển khai và đọc lại xác minh đúng source local.
Ruleset: `bbd9d75d-ebc5-4fc7-919e-77658a40e2c3`.
Backup Rules trước triển khai và xác minh: `build/rules-publish/2026-10-22T16-21-42-010Z/`.
Không có index khuyến mãi trong file index hiện tại cần gỡ.

## Cập nhật bản đang chạy

1. Đưa mã backend hiện tại lên Render theo luồng deploy của dự án. Không đổi biến môi trường SePay/ngân hàng.
2. Kiểm tra `/health`: phải có `pricingVersion: 3`, không còn `promotionWalletVersion`/`promotionPolicy`.
3. Cài APK mới `app/build/outputs/apk/debug/app-debug.apk`. APK yêu cầu báo giá `pricingVersion: 3`; khi Render còn bản cũ, màn thanh toán báo cần cập nhật máy chủ và không cho đặt.
4. Mở lại giỏ để kiểm tra giá/tồn mới. Ví dụ 2 Seller, mỗi bên 60.000đ tiền hàng, nhận tại điểm: tổng 120.000đ; một Seller 150.000đ tiền hàng: tổng 150.000đ. Không giảm thêm ở các mốc trước đây.
5. Đơn cũ vẫn dùng tổng đã chốt và QR/tham chiếu thanh toán cũ.

Báo giá từ APK cũ còn khoản giảm thêm được backend từ chối với `QUOTE_CHANGED` trước khi giữ kho;
khách phải xác nhận lại giá mới. Thử lại đơn đã tạo vẫn trả biên nhận và số tiền lịch sử.

Không cần đổi schema Room hay nhập lại sản phẩm/lô. Chưa deploy Render hoặc push Git từ phiên này.

## Công cụ bảo trì

`node scripts/retire-discounts.cjs` chỉ quét/sao lưu, không xóa.
`node scripts/retire-discounts.cjs --apply build/discount-retirement/<thư-mục-snapshot>` chỉ xóa đúng snapshot đã sao lưu,
kiểm tra project, đường dẫn và updateTime trước khi ghi. Nếu dữ liệu đổi, công cụ từ chối và yêu cầu snapshot mới.
`node scripts/publish-firestore-rules.cjs` chỉ sao lưu Rules; thêm `--apply` để triển khai và xác minh source.
Các công cụ này dùng `serviceAccountKey.json` local, không đưa khóa hoặc backup dữ liệu lên Git.

## Kiểm thử

- Android: **243/243** unit test, assembleDebug và lintDebug thành công (0 lỗi Lint).
- Backend: kiểm tra cú pháp thành công, **152/152** unit test; Cloudinary Worker **8/8**.
- Firestore/API: **114/114** qua trên emulator project demo. Bao phủ giá mới, tồn kho, giỏ nhiều Seller, idempotency,
  từ chối API khuyến mãi cũ, Rules từ chối mọi vai trò và dữ liệu khuyến mãi cũ không làm thay đổi checkout.
- Script dọn dữ liệu và các planner liên quan: **11/11** qua; có test giới hạn phạm vi và fingerprint snapshot.

Các test không gửi tiền, push hoặc upload dữ liệu giả lên Firebase thật.
Hai thao tác production trong phiên này là xóa dữ liệu đã liệt kê và cập nhật Firestore Rules.
