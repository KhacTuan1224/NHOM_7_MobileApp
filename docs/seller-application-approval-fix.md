# Phê duyệt đăng ký kinh doanh — 23/10/2026

## Nguyên nhân

`FirebaseAdminRepository.runTransition()` dùng `clean()` cho `sellerId`/`id` của hồ sơ. Hàm này trim rồi chuyển thành chữ hoa, phù hợp cho enum nhưng làm sai UID Firestore vốn phân biệt hoa/thường. Admin đọc được `sellerApplications/{uid}` và ảnh bằng ID đúng; tới bước phê duyệt/từ chối, transaction lại đọc `users/{UID_IN_HOA}`, không thấy tài khoản và trả `SELLER_NOT_FOUND`. UI ánh xạ thành “Không tìm thấy dữ liệu cần quản trị”.

## Bản sửa

Dùng `SellerApplicationIdentity.resolve()` giữ nguyên hoa/thường của ID, với thứ tự fallback cũ: `sellerId`, `id`, document ID. Cả phê duyệt và từ chối tiếp tục cập nhật hồ sơ, tài khoản Seller và audit trong một transaction. Không nới Rules, không thay đổi upload ảnh hoặc notification. Rules hiện có chấp nhận payload đúng trong Emulator, kể cả Seller đã APPROVED nhưng mới nộp hồ sơ PENDING.

Cài APK mới rồi Admin mở lại hồ sơ đang chờ để phê duyệt. Không cần Seller upload lại ảnh hoặc gửi lại hồ sơ. Không cần deploy Render hay Rules cho bản sửa này. Chưa thao tác phê duyệt/từ chối tài khoản thật.

## Kiểm thử

- Android: 249 test qua, `assembleDebug` thành công. Ba regression test kiểm tra giữ UID hỗn hợp hoa/thường, fallback dữ liệu cũ và thiếu ID.
- Firestore Emulator project demo: 9 test có tên khớp `Seller` qua, 51 test khác được bỏ qua theo bộ lọc. Bốn test mới tái hiện lỗi UID in hoa và xác nhận phê duyệt/từ chối nguyên tử, giữ ảnh, ghi audit, cho cả Seller PENDING_APPROVAL và APPROVED. Không ghi database thật.
- Log: `build/seller-application-approval-android.log`, `build/seller-application-approval-firestore.log`.
