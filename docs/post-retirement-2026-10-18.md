# Rà soát và loại bỏ module Post — 18/10/2026

## Kết luận

Trước lần kiểm tra này, việc gộp chưa hoàn tất: bảng tin đã hiển thị Campaign, nhưng vẫn còn model, repository, màn hình, cache, thống kê, moderation, API và dữ liệu Post cũ. Đã loại bỏ các nhánh chức năng này khỏi frontend/backend trong repo. Campaign là nơi duy nhất quản lý nội dung câu chuyện, bình luận, cảm xúc và lượt xem.

## Dữ liệu thật đã xử lý

Đã kết nối bằng service account cục bộ tới `nhom7-rescuefarm`, database `(default)`, rà toàn bộ document và subcollection trước khi xóa.

Hai Post còn lại đều là nội dung thử nghiệm, không liên kết chiến dịch/sản phẩm, không có bình luận hay cảm xúc con. Không phát hiện dữ liệu nghiệp vụ cần chuyển sang Campaign từ hai Post này.

| Nhóm dữ liệu | Đã xóa |
|---|---:|
| posts | 2 |
| postViewReceipts | 8 |
| notifications chỉ thuộc Post | 2 |
| notificationOutbox chỉ thuộc Post | 2 |
| notificationDeliveries chỉ thuộc Post | 2 |
| adminAuditLogs chỉ thuộc Post | 2 |
| **Tổng** | **18** |

Việc xóa document được commit trong một transaction, kiểm tra lại nội dung và updateTime trước khi xóa. Bản kiểm tra sau xóa xác nhận:

- Không còn document Post, subcollection Post, lượt xem Post hoặc metadata phụ thuộc Post.
- Đối chiếu **579 document còn lại** với snapshot ngay trước khi xóa: không thay đổi. Bao gồm users, đơn hàng/items, payments, sản phẩm, lô hàng, chiến dịch, danh mục và banner. Heartbeat của notification worker được loại khỏi phép so sánh vì tự cập nhật khi hệ thống hoạt động.
- Rules mới đã được publish lên Firestore và đọc lại để đối chiếu đúng nội dung local.
- **Hai composite index Post vẫn còn trên server:** Google trả về `The caller does not have permission` khi dùng service account để xóa. Cấu hình local đã bỏ hai index này. Chủ project cần xóa hai composite index của collection `posts` trong Firebase Console; không xóa index của collection khác. Index không chứa bài viết và không khôi phục chức năng Post.

Snapshot trước/sau, kế hoạch xóa, danh sách document đã xóa và metadata rules được lưu cục bộ tại:

`build/post-retirement/live-2026-10-18T10-11-31-681Z/`

Thư mục này chứa dữ liệu riêng tư, bị Git ignore; không đưa vào tài liệu công khai hoặc commit. Không xóa ảnh Cloudinary vì cùng ảnh có thể được dữ liệu khác sử dụng.

## Thay đổi code

- Android: bỏ màn hình tạo/xem/quản lý Post, navigation, model, repository, đồng bộ nền và các nhánh admin/seller liên quan. Đổi liên kết bình luận/cảm xúc sang tên `campaignId`.
- Room nâng lên phiên bản 10: xóa `post_cache` và metadata feed cũ; giữ cache và dữ liệu nghiệp vụ khác. Các schema/migration lịch sử được giữ để nâng cấp ứng dụng đã cài, không phải tính năng Post còn hoạt động.
- Backend: bỏ API `/recordPostView`, truy vấn/thống kê Post, moderation/notification Post và quyền sở hữu media Post. Thông báo cũ không còn nội dung được xử lý an toàn.
- Cloudinary Worker: không cấp quyền upload/xóa theo loại tài nguyên Post.
- Rules, schema, seed và index config không còn khai báo module Post. README hiện hành đã cập nhật.
- Công cụ audit, backup, kiểm thử phủ định và báo cáo lịch sử có thể nhắc Post để kiểm chứng việc ngừng sử dụng; không được xóa những bằng chứng này bằng thao tác thay thế chuỗi hàng loạt. HTTP `POST` và Android `View.post()` không liên quan đến module nội dung.

## Kiểm chứng

| Kiểm tra | Kết quả |
|---|---|
| Android assembleDebug, unit tests, assembleDebugAndroidTest | Build thành công, 217 unit tests đạt |
| Backend unit tests | 131 tests đạt |
| Cloudinary Worker | 8 tests đạt |
| Firestore rules + HTTP integration trên Emulator | 78 tests đạt |
| Phân loại phạm vi dữ liệu cần xóa | 3 tests đạt |
| Migration SQLite 9 → 10 trên dữ liệu kiểm tra | Xóa cache Post, giữ nguyên các bảng nghiệp vụ còn lại |
| So sánh dữ liệu Firestore sau xóa | 579 document không thay đổi |

Instrumentation APK đã build, chưa chạy trên thiết bị trong lần kiểm tra này.

## Phạm vi triển khai còn lại

Firestore rules và việc xóa dữ liệu đã thực hiện trực tiếp. Backend Render và Cloudinary Worker mới chỉ sửa code trong workspace, **chưa deploy** trong lần này; cần triển khai hai service và cài APK mới để môi trường chạy dùng toàn bộ code đã dọn. Không có thông tin deploy Render được cấu hình trong phiên làm việc này.

APK: `app/build/outputs/apk/debug/app-debug.apk`.

Đây là báo cáo thay thế các nhận định về Post trong các báo cáo schema/audit trước đó.
