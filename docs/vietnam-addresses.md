# Chọn địa chỉ Việt Nam

`VietnamAddressView` dùng chung cho sổ địa chỉ của admin/customer/seller, hồ sơ
seller, đăng ký seller, trang cửa hàng, địa điểm chiến dịch và checkout (dọc/ngang).
Chọn tỉnh → quận/huyện thuộc tỉnh → xã/phường thuộc huyện, sau đó nhập số nhà,
tên đường. Hộp chọn hỗ trợ tìm tên không dấu. Đổi tỉnh/huyện xóa lựa chọn cấp dưới.

Customer checkout tự điền địa chỉ mặc định lần đầu, có mục “Nhập địa chỉ nhận hàng
khác”, không ghi đè biểu mẫu khi danh sách Firestore cập nhật. Địa chỉ dùng cho đơn
hàng được chụp thành chuỗi đầy đủ; nhập địa chỉ khác không sửa địa chỉ đã lưu.
Giữ biểu mẫu khi xoay màn hình. Guest cũng có bộ chọn để nhập địa chỉ giao hàng.

Tọa độ GPS vẫn dùng để tính phí giao hàng. Chọn một tỉnh/xã không đủ để xác định
tọa độ số nhà. Khi sửa địa chỉ, GPS cũ bị xóa; lấy vị trí hiện tại nếu đang ở nơi
nhận hàng hoặc nhập tọa độ chính xác. Địa chỉ lưu có GPS sẽ tự điền tọa độ ở checkout.

Dữ liệu offline lấy từ `daohoangson/dvhcvn`, snapshot 2025-02-18, hệ 3 cấp,
63 tỉnh/thành, 696 quận/huyện, 10.047 xã/phường. Nguồn, phiên bản, giấy phép và
cách cập nhật nằm trong `app/src/main/assets/address/README.md`. Địa chỉ cũ/GPS
được khớp tên trong đúng cấp cha; không khớp thì yêu cầu chọn lại, giữ số nhà.

Firestore rules cho phép cả ba vai trò ACTIVE quản lý **địa chỉ của chính mình**.
Không mở quyền đọc sổ địa chỉ của người khác, kể cả admin. Đổi địa chỉ mặc định
cập nhật timestamp của cả địa chỉ cũ, địa chỉ mới và hồ sơ trong cùng transaction.

Đã triển khai thành công các rules lên Firebase project `nhom7-rescuefarm` ngày
16/10/2026 theo yêu cầu mở quyền lưu địa chỉ seller. Seller ACTIVE được tạo, đọc,
sửa, xóa địa chỉ cá nhân và đổi địa chỉ mặc định. Lệnh triển khai lại khi cần:

```powershell
.\tests\firestore\node_modules\.bin\firebase.cmd deploy --only firestore:rules --project nhom7-rescuefarm
```

Kiểm thử:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.rescuefarm.VietnamAddressViewTest --no-configuration-cache
npm --prefix tests/firestore test
```

Kết quả kiểm tra: 20 unit tests liên quan địa chỉ/hồ sơ đạt, 3 kiểm thử giao diện
trên Android Emulator đạt, 3 nhóm kiểm thử quyền địa chỉ Firestore đạt; APK debug
biên dịch thành công. Chạy toàn bộ suite ghi nhận 2 JVM tests có sẵn chưa đạt
(`SellerDashboardAggregatorTest.routesMapNestedManagementToOwningScreen`,
`CampaignViewModelTest.validSubmit_reachesRepositoryAsPending`) và 2 Firestore
tests bình luận chưa đạt. Hai lỗi bình luận tái hiện với `firestore.rules` từ HEAD
trước thay đổi; các lớp và tests liên quan hai lỗi JVM không bị thay đổi.
