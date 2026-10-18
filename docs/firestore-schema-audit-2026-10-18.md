# Kiểm tra cấu trúc Firestore — 18/10/2026

> Báo cáo tại thời điểm kiểm tra trước. Phần Post đã được ngừng sử dụng sau đó; xem [kết quả rà soát và dọn Post](post-retirement-2026-10-18.md).

## Kết quả

Đã kết nối trực tiếp bằng service account cục bộ tới project `nhom7-rescuefarm`, database `(default)`, Firestore Native, vùng `asia-southeast1`.

- Code sử dụng **38 collection gốc** và **7 đường dẫn subcollection**.
- Database hiện có **31 collection gốc có dữ liệu**; tất cả đều còn tham chiếu trong code đang sử dụng.
- **19/19 composite index** trong `firestore.indexes.json` có trên server và ở trạng thái READY.
- Index collection-group cho `comments.id` ở trạng thái READY.
- Nội dung Firestore Rules trên server khớp file `firestore.rules` trong repo sau chuẩn hóa xuống dòng.
- **55 truy vấn chỉ đọc** thành công, gồm truy vấn collection, feed, đơn customer/guest, seller, đánh giá, báo cáo, hàng đợi thông báo, duyệt bình luận và tổng doanh thu.
- **Không tạo, sửa hoặc xóa document nào. Không tạo database phụ. Không có index thiếu cần triển khai.**

Các truy vấn dùng Admin SDK kiểm tra cấu trúc và khả năng phục vụ truy vấn của Firestore. Đây không phải kiểm thử đăng nhập, phân quyền từng vai trò hay toàn bộ luồng ghi của ứng dụng.

## Vì sao không tạo collection rỗng?

Firestore không có lệnh tạo collection rỗng tồn tại độc lập. Collection hình thành khi ghi document đầu tiên và không còn được lưu như collection có dữ liệu sau khi xóa hết document. Gọi `db.collection(name)` chỉ tạo một reference trong code; không tạo đối tượng collection trên server. Không thể đáp ứng đồng thời việc hiển thị collection rỗng trong Console và yêu cầu không có document giữ chỗ.

Theo yêu cầu không tạo dữ liệu mẫu, không tạo `_schema`, `_init`, `_placeholder` hoặc document rỗng. Không ghi rồi xóa một document để giả lập việc tạo collection: cách đó không làm collection rỗng tồn tại lâu dài.

Các chức năng chưa chạy không cần collection được tạo trước. Lần ghi dữ liệu nghiệp vụ hợp lệ đầu tiên sẽ tạo đúng đường dẫn. Query collection chưa có dữ liệu trả kết quả rỗng, không phải lỗi thiếu bảng.

Tài liệu chính thức: [Firestore data model](https://firebase.google.com/docs/firestore/data-model), [quản lý index](https://firebase.google.com/docs/firestore/query-data/indexing).

## Cấu trúc được sử dụng

Danh sách đầy đủ có ID, mục đích và quan hệ nằm trong [`firestore.schema.json`](../firestore.schema.json). File này là bản kê khai để rà soát, **không tự áp schema SQL hoặc tự triển khai cấu trúc**. Việc kiểm soát dữ liệu hiện thuộc Firestore Rules, validation/serialization trong Android repositories và backend `functions/`.

| Nhóm | Collection gốc |
|---|---|
| Tài khoản/cửa hàng | users, sellerPublicProfiles, sellerPaymentProfiles, sellerApplications |
| Danh mục/bán hàng | categories, products, productBatches, promotions, customerCarts |
| Chiến dịch/nội dung | campaigns, banners, posts |
| Đơn hàng | orders, payments, shipments |
| Tương tác | favorites, reviews, reports, notifications |
| Cấu hình/kiểm toán | appConfig, adminAuditLogs, adminBootstrapAudit, systemMigrations |
| Checkout/guest | checkoutReceipts, guestOrderCodes, guestOrderGrants |
| Thanh toán tự động | bankTransferReferences, sepayWebhookEvents, sepayBankTransactions |
| Thông báo nền | notificationOutbox, notificationDeliveries, notificationWorkerState |
| Upload chiến dịch | campaignAssetReferences, campaignMediaDrafts, campaignMediaUploads |
| Chống lặp/giới hạn API | requestLimits, postViewReceipts, campaignViewReceipts |

Subcollection:

- `users/{userId}/addresses/{addressId}`
- `users/{userId}/devices/{deviceId}`
- `orders/{orderId}/items/{itemId}`
- `campaigns/{campaignId}/comments/{commentId}`
- `campaigns/{campaignId}/reactions/{userId}`
- `posts/{postId}/comments/{commentId}`
- `posts/{postId}/reactions/{userId}`

`items` trong giỏ hàng là mảng của document `customerCarts`, không phải collection gốc. Địa chỉ và thiết bị nằm dưới users. Không tạo thêm collection gốc `items`, `addresses`, `devices`, `comments`, `reactions`. Dữ liệu cache Room trên Android và file ảnh Cloudinary cũng không cần database Firestore riêng.

## Collection chưa có document

| Collection | Nơi sử dụng / khi xuất hiện |
|---|---|
| promotions | FirebaseProductRepository ghi khuyến mãi thật, commerce đọc giá, notification worker phát ưu đãi |
| banners | FirebaseAdminRepository tạo banner thật, FirebaseCampaignRepository đọc Home |
| favorites | FirebaseEngagementRepository lưu yêu thích, notification worker truy vấn người theo dõi sản phẩm |
| reports | FirebaseEngagementRepository tạo báo cáo; admin và notifications-api xử lý |
| adminBootstrapAudit | functions/scripts/bootstrap-admin.js ghi khi thực sự đổi vai trò tài khoản thành admin |
| sepayWebhookEvents | functions/sepay.js ghi khi nhận và xử lý webhook thật |
| sepayBankTransactions | functions/sepay.js ghi khi áp dụng giao dịch thanh toán hợp lệ để chống dùng lại giao dịch |

## Quyết định về collection cũ

Không tìm được collection hiện có nào đáp ứng điều kiện chắc chắn không còn được sử dụng. Vì vậy **không xóa collection nào**.

Đặc biệt, giữ `systemMigrations` để tránh migration chạy lại, `campaignAssetReferences` để tránh dọn nhầm ảnh, các collection draft/upload để xử lý vòng đời upload, và các collection guest/notification để giữ quyền truy cập đơn và tiến trình phát thông báo. Collection kỹ thuật hoặc ít document không đồng nghĩa với không dùng.

## Công cụ kiểm tra lại

Tại thư mục repo:

```powershell
node scripts/inspect-firestore-structure.cjs
node scripts/check-firestore-schema.cjs build/schema-audit/<thu-muc-vua-tao> --live
```

Hai công cụ chỉ đọc Firestore. Service account không được đưa vào Git hoặc in ra console. Kết quả kiểm tra lần này nằm trong `build/schema-audit/2026-10-18T02-41-00-611Z/`, được Git bỏ qua:

- `metadata.json`: database, collection count, index và hash Rules.
- `deployed.rules`: bản Rules đọc từ server.
- `schema-check.json`: đối chiếu inventory/code và kết quả 55 truy vấn.
