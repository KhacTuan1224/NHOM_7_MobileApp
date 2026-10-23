# Sửa quyền tạo/lưu lô và ẩn/hiện sản phẩm — 23/10/2026

## Xác minh

Đã đọc đúng sản phẩm “Bí đỏ hồ lô Đắk Lắk” trong ảnh và dữ liệu lô liên quan trên project `nhom7-rescuefarm`.
Seller sở hữu sản phẩm có `role=SELLER`, `status=ACTIVE`, `sellerStatus=APPROVED`; danh mục đang hoạt động.
Lô hiện có còn hạn, không giữ hàng/đã bán. Không cần đổi trạng thái tài khoản.

## Nguyên nhân và bản sửa

1. Tạo lô: Android đọc ID lô mới trong transaction trước khi tạo. Rules cũ chỉ đọc được lô đã tồn tại,
   vì cần `resource.data.productId`. Đã cho Seller được duyệt đọc bản ghi chưa tồn tại;
   tạo lô vẫn yêu cầu đúng sở hữu sản phẩm, hạn còn hiệu lực và tồn kho hợp lệ.
2. Ẩn sản phẩm: app gửi `sellerHiddenPreviousStatus` nhưng Rules cũ không chấp nhận trường này.
   Rules cũ cũng chặn mọi thao tác của Seller khi trạng thái hiện tại là HIDDEN, nên không hiện lại được.
   Đã đồng bộ chuyển ACTIVE/INACTIVE/SOLD_OUT → HIDDEN → đúng trạng thái trước đó.
   Chỉ cho hiện sản phẩm do chính Seller tự ẩn; Seller không được sửa/xóa metadata kiểm duyệt của Admin.
3. Lưu trữ lô hết hạn: backend ghi `expiredAt` nhưng schema Rules không chấp nhận.
   Đã nhận diện trường server này và giữ nguyên khi lưu trữ. Seller không được tự ghi/sửa dấu thời gian đó.
4. Sửa sản phẩm dùng `update` thay vì thay toàn bộ document để giữ metadata kiểm duyệt/lịch sử.
5. App chặn sửa lô đã phát sinh giao dịch, chặn ngày thu hoạch trùng hạn sử dụng trước khi gửi lên Firestore;
   thông báo Permission Denied không còn mặc định kết luận Seller chưa được duyệt.

Đường sửa lô cũ hợp lệ đã qua test trước và sau bản sửa. Không có thao tác thêm/lưu/ẩn thử trên dữ liệu thật.

## Kiểm thử

- Trước sửa: bộ regression mới có 9 test, 6 thất bại — hai kiểu tạo lô, ba trạng thái ẩn sản phẩm và lưu trữ lô hết hạn.
- Sau sửa: toàn bộ suite Firestore/API **124/124** qua trên emulator `demo-rescuefarm`, gồm **10** regression Seller.
- Android **244/244** unit test qua; assembleDebug và lintDebug thành công.
- Test phủ đúng payload transaction, lô có sẵn, nhập lô mới kích hoạt sản phẩm SOLD_OUT,
  ẩn/hiện, chặn Seller khác/chưa duyệt/bị khóa, chặn giả rating và metadata Admin.

Log: `build/seller-inventory-before.log`, `build/seller-inventory-firestore.log`, `build/seller-inventory-android.log`.

## Triển khai

Rules mới đã được triển khai và đọc lại xác minh đúng source local:

- Ruleset: `7dcb1916-ebff-4730-8b08-9d19bded8c9f`.
- SHA-256: `4370ddb42f41fbcbec8de42871d267fe6527125a29e3931ec8e6bb18e35aa961`.
- Backup Rules cũ: `build/rules-publish/2026-10-23T02-14-21-982Z/previous-ruleset.json`.
- APK mới: `app/build/outputs/apk/debug/app-debug.apk`.

Cài APK mới, mở lại Sản phẩm của cửa hàng. Thử Ẩn → Hiện cho sản phẩm của mình;
vào Lô hàng, dùng Nhập mới để tạo lô hoặc Sửa để cập nhật lô còn hạn chưa phát sinh giao dịch.
Ngày thu hoạch phải trước hạn sử dụng; hạn sử dụng phải ở tương lai.

Phiên này chỉ đọc dữ liệu kinh doanh thật và cập nhật Rules. Không sửa tài khoản, giá, tồn kho, đơn hàng hoặc luồng SePay.
