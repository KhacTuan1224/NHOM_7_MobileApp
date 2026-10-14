# Hoàn thiện 7 mục Customer/Guest còn thiếu

Ngày: 14/10/2026 (Asia/Saigon).

**Đã hoàn thiện cả 7 mục trong source.** Android build và các bộ kiểm thử đều qua. Để chạy trên môi trường thật cần triển khai backend/rules mới và dùng APK mới; lượt kiểm tra này không thực hiện deploy hay cài ứng dụng trên thiết bị.

| # | Mục | Hành vi sau thay đổi | Căn cứ |
|---:|---|---|---|
| 1 | Tạo đơn phải giữ tồn | Customer và Guest đặt qua cùng transaction trên backend: kiểm Seller, giá, lô chưa hết hạn, campaign, phí giao; ghi Order/items/Payment/Shipment khi cần và reserve batch/campaign. Rules chặn client tạo trực tiếp hoặc tự sửa reservation. | [commerce.js](D:/Project_Mobile/NHOM_7_MobileApp/functions/commerce.js), [firestore.rules](D:/Project_Mobile/NHOM_7_MobileApp/firestore.rules) |
| 2 | Giữ dữ liệu đã giao dịch | Thao tác “Xóa lô” đổi thành “Lưu trữ lô” (ARCHIVED). Document và OrderItem references được giữ lại; rules cấm mọi hard-delete batch, kể cả sau khi hủy đơn. | [FirebaseProductRepository.java](D:/Project_Mobile/NHOM_7_MobileApp/app/src/main/java/com/rescuefarm/data/remote/firebase/FirebaseProductRepository.java) |
| 3 | Sửa/xóa bình luận | Chủ bình luận có nút Sửa/Xóa; sửa qua dialog; xóa mềm thành DELETED và hiển thị nội dung đã gỡ. Rules chặn người khác, giả timestamp/identity, sửa bài đã ẩn hoặc khôi phục comment đã xóa. | [PostDetailFragment.java](D:/Project_Mobile/NHOM_7_MobileApp/app/src/main/java/com/rescuefarm/ui/feed/PostDetailFragment.java), [FirebasePostRepository.java](D:/Project_Mobile/NHOM_7_MobileApp/app/src/main/java/com/rescuefarm/data/remote/firebase/FirebasePostRepository.java) |
| 4 | Ghi lượt xem Post | Khi mở bài PUBLISHED, app gọi backend để tăng viewCount atomically, khử trùng lặp theo thiết bị/post/ngày, rồi hiển thị count mới. Guest được tính; bài HIDDEN không tăng và client không tự ghi count. | [PostViewModel.java](D:/Project_Mobile/NHOM_7_MobileApp/app/src/main/java/com/rescuefarm/ui/feed/PostViewModel.java), [commerce.js](D:/Project_Mobile/NHOM_7_MobileApp/functions/commerce.js) |
| 5 | Home gần hết hạn | Khu “Nông sản gần hết hạn” xét ProductBatch thực tế, còn tồn/bán được và hạn dùng trong 3 ngày, xếp hạn gần nhất trước. Hiển thị hạn dùng/tồn và mở đúng lô; không cần Campaign. Home tái dựng dữ liệu theo thời gian mỗi 60 giây khi đang mở. | [HomeContentBuilder.java](D:/Project_Mobile/NHOM_7_MobileApp/app/src/main/java/com/rescuefarm/ui/home/HomeContentBuilder.java), [GuestHomeFragment.java](D:/Project_Mobile/NHOM_7_MobileApp/app/src/main/java/com/rescuefarm/ui/home/GuestHomeFragment.java) |
| 6 | Guest checkout/payment | Guest không cần Firebase Auth User. Có quote và checkout an toàn, 3 fulfillment, COD/transfer/cash theo cách nhận hàng. Guest cart Room được trừ phần đã mua đúng một lần bằng local receipt. Vào Guest Mode kết thúc phiên đăng nhập cũ. | [CheckoutFragment.java](D:/Project_Mobile/NHOM_7_MobileApp/app/src/main/java/com/rescuefarm/ui/checkout/CheckoutFragment.java), [FirebaseOrderRepository.java](D:/Project_Mobile/NHOM_7_MobileApp/app/src/main/java/com/rescuefarm/data/remote/firebase/FirebaseOrderRepository.java) |
| 7 | Guest tra cứu đơn | Tab Đơn hàng của Guest mở màn code+phone lookup. Backend kiểm exact code và phone đã chuẩn hóa, giới hạn số lần thử, cấp token scoped/24h. Chi tiết hiển thị items/payment/shipment và lấy trạng thái mới khi màn hình mở. | [OrderConfirmationFragment.java](D:/Project_Mobile/NHOM_7_MobileApp/app/src/main/java/com/rescuefarm/ui/checkout/OrderConfirmationFragment.java), [commerce.js](D:/Project_Mobile/NHOM_7_MobileApp/functions/commerce.js) |

Các bổ sung gắn với 7 mục: quote và checkout dùng cùng cách làm tròn VND theo từng dòng; request receipt bind owner/payload/secret Guest để replay không giữ tồn hai lần; Cloud backup loại trừ Guest access preferences. Giữ đúng 25 domain class; OrderJsonMapper/ExpiringProduct và các receipt/access records phục vụ kỹ thuật.

## Kiểm chứng

- **139 Android unit tests pass**, 0 failures/errors; **assembleDebug thành công**. Có kiểm batch gần hết hạn không có Campaign, loại batch expired/archived/sold out và Guest Mode không giữ phiên authenticated cũ.
- **39 HTTP/Firestore Emulator regression tests pass**: kiểm concurrent buyers tranh tồn, rollback, replay, giá số lượng lẻ, promo fallback, Guest Delivery/Pickup/On-site/payment, lookup đúng/sai code/phone, token wrong-order/expired, giới hạn lookup, phân quyền comment, archive/history và view count. Seller hủy/hoàn tất đơn Guest cũng được kiểm qua rules thực tế, Guest đọc được trạng thái mới.
- Các negative tests dùng assertFails; log PERMISSION_DENIED trong các ca này là kết quả mong đợi.
- Không đọc/ghi production, không chạy UI trên điện thoại/emulator Android. GPS thật, QR ảnh, Google provider và FCM thật cần nghiệm thu ở môi trường đã cấu hình.

Mã kiểm thử: [checkout.test.mjs](D:/Project_Mobile/NHOM_7_MobileApp/tests/firestore/checkout.test.mjs), [commerce.test.cjs](D:/Project_Mobile/NHOM_7_MobileApp/functions/commerce.test.cjs), [HomeContentBuilderTest.java](D:/Project_Mobile/NHOM_7_MobileApp/app/src/test/java/com/rescuefarm/ui/home/HomeContentBuilderTest.java).

APK: [app-debug.apk](D:/Project_Mobile/NHOM_7_MobileApp/app/build/outputs/apk/debug/app-debug.apk).

## Cách đưa lên môi trường thật

1. Triển khai thư mục functions trên dịch vụ Render hiện có; dùng package-lock/npm ci, giữ FIREBASE_SERVICE_ACCOUNT_JSON trong secret của server và RENDER_BACKEND_URL trỏ đúng máy chủ.
2. Kiểm tra /health và các route quote/checkout mới, sau đó publish firestore.rules đi cùng bản backend này. Client tạo Order/items/payment/shipment hoặc reservation trực tiếp sẽ bị chặn, nên APK cũ cần được thay bằng APK mới trong cùng lần nâng cấp.
3. Kiểm tra appConfig/shipping và tọa độ Seller/địa chỉ nhận cho DELIVERY; kiểm tra ngân hàng Seller cho BANK_TRANSFER. PICKUP và ON_SITE_RESCUE không cần Shipment/phí giao. On-site mobile cần vị trí mới ≤10 phút.
4. Dùng APK mới: vào Guest Mode → thêm lô vào cart → nhập người nhận → chọn fulfillment/payment → kiểm tra quote → đặt. Ghi lại toàn bộ mã đơn và phone; tra cứu lại từ tab Đơn hàng. Với DELIVERY, tọa độ nơi nhận phải hợp lệ; có thể chọn địa chỉ đã lưu khi là Customer hoặc nhập tọa độ khi là Guest.
5. Kiểm thử trên thiết bị: hai tài khoản tranh lượng tồn; Seller hủy/hoàn tất; Guest tra cứu sai/đúng phone; sửa/xóa bình luận của mình; mở Post; mở sản phẩm đúng batch từ khu gần hết hạn.

Chi tiết các routes, dữ liệu private và rate limits: [README.md](D:/Project_Mobile/NHOM_7_MobileApp/functions/README.md). Mã đơn Guest mới có dạng RF- + 32 ký tự hex; các dữ liệu kỹ thuật private được backend quản lý, không cấp public Firestore access cho Guest.

## Các mục ngoài phạm vi 7 yêu cầu

Báo cáo 100 mục đã cập nhật ở [customer-final-audit.md](D:/Project_Mobile/NHOM_7_MobileApp/docs/customer-final-audit.md): 83 Đạt, 15 Một phần, 0 Chưa đạt, 1 Không yêu cầu rõ và 1 Chưa xác minh môi trường thật. Những mục Một phần như filter Campaign theo Category/Price, ảnh review/gallery, trường doanh nghiệp, badge/countdown và push retry chưa được coi là đã hoàn tất bởi lần sửa này.

