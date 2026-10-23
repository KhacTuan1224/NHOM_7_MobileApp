# Đồng bộ Firestore và màn hình Android

Ứng dụng ghi dữ liệu bằng repository/API hiện có. Màn hình đang mở nhận thay đổi
bằng `addSnapshotListener`, cập nhật LiveData rồi hiển thị lại. Thay đổi hợp lệ từ
Firebase Console, Admin, Seller hoặc Customer đều đi qua cùng luồng đọc này.
Không cần kéo để tải lại khi có mạng và dịch vụ đã được triển khai đầy đủ.
Độ trễ vẫn phụ thuộc kết nối mạng, Firestore và thời gian trả lời API.
Tham khảo [cơ chế listener của Firebase](https://firebase.google.com/docs/firestore/query-data/listen).

## Phạm vi

| Màn hình / dữ liệu | Nguồn cập nhật |
| --- | --- |
| Home, tìm kiếm, danh sách sản phẩm, danh mục, banner | Query listener + LiveData; Room dùng làm dữ liệu dự phòng |
| Chi tiết sản phẩm, lô hàng, giá và tồn kho trong giỏ | Document/query listener; giá trong giỏ là bản hiển thị, không tự ghi ngược từ callback |
| Danh sách/chi tiết chiến dịch, vị trí, sản phẩm và lô liên quan | Listener chiến dịch và các tài liệu liên quan |
| Reaction, bình luận, đánh giá, yêu thích, thông báo | Listener theo chiến dịch, sản phẩm hoặc chủ tài khoản |
| Hồ sơ, địa chỉ, hồ sơ đăng ký Seller, cửa hàng và QR của chính Seller | Listener hiện có và listener riêng cho cấu hình thanh toán của chủ cửa hàng |
| Danh sách đơn Customer/Seller, chi tiết đơn, thanh toán, vận chuyển, items | Query/document listener; đơn Guest nhận tín hiệu qua backend |
| Quản trị danh sách, bộ lọc, chi tiết và phí vận chuyển | Listener báo thay đổi rồi đọc lại bằng quyền Admin; giữ bộ lọc và số trang đã mở |
| Dashboard Admin, dashboard/thống kê Seller | Listener báo thay đổi từ backend; loại bỏ cache thống kê cũ trước khi tải lại |
| Gói giải cứu, đăng ký, bảng tác động, kết quả báo cáo, thông tin thẻ chiến dịch và báo giá checkout | Tín hiệu Firestore từ backend, nội dung vẫn đọc qua API kiểm tra quyền |

Danh sách phân trang dùng cửa sổ bao gồm các trang đã tải và một tài liệu kiểm tra
`hasMore`. Tài liệu thêm, sửa, xóa hoặc rời bộ lọc đều cập nhật lại cửa sổ. Form
chỉnh sửa đang có bản nháp giữ nội dung nhập; không tự gửi lại bản nháp khi có snapshot.

## Dữ liệu chỉ được đọc qua backend

`functions/realtime.js` cung cấp ba POST endpoint:

- `/realtime/watch`: kiểm tra tài khoản hoặc capability của đơn Guest, chọn phạm vi
  theo dõi cố định, trả về `subscriptionId` ngẫu nhiên và `leaseToken`.
- `/realtime/keepalive`: gia hạn phiên còn hiệu lực; kiểm tra lại trạng thái tài
  khoản và capability Guest. Android gia hạn mỗi 45 giây khi màn hình còn hoạt động.
- `/realtime/close`: gỡ đăng ký. Server cũng thu hồi sau 120 giây không gia hạn.

Backend dùng Admin SDK `onSnapshot` và ghi **chỉ** `revision`, `unavailable`,
`updatedAt` vào `realtimeSubscriptions/{UUID}`. Android dùng `addSnapshotListener`
trên tài liệu này rồi gọi API hiện có để lấy dữ liệu mới. `keepalive` chỉ duy trì
phiên theo dõi, không phải thao tác tải định kỳ dữ liệu nghiệp vụ.

Các scope: `rescue`, `reports`, `guest_order`, `seller_statistics`,
`admin_dashboard`, `campaign_cards`, `checkout`. Client không được chọn đường dẫn
Firestore tùy ý hoặc thay danh tính chủ tài khoản trong scope riêng tư.

Rules cho phép đọc tài liệu tín hiệu theo UUID; cấm liệt kê và mọi thao tác ghi
từ SDK client. Rules còn kiểm tra tài liệu chỉ có ba trường tín hiệu nói trên.
Không đưa ID nghiệp vụ, nội dung báo cáo, thông tin khách hàng, ngân hàng hoặc token
vào tài liệu tín hiệu. Quyền đọc trực tiếp `rescueOffers`/`rescueClaims` vẫn bị chặn.
`leaseToken` chỉ dùng để gia hạn/đóng phiên và không được ghi vào log.

Server dùng chung query listener giữa các phiên có cùng phạm vi, giới hạn 128 phiên
trên mỗi process và 8 phiên cho một tài khoản/IP. Listener thống kê Seller theo dõi
thêm `items` của các đơn thuộc Seller, đồng thời gỡ chúng khi đơn rời query. Listener
được gỡ khi không còn màn hình theo dõi. Các collection tổng hợp lớn vẫn phát sinh
chi phí đọc ban đầu; cần theo dõi quota Firestore khi vận hành.

## Vòng đời và ghi dữ liệu

- `SnapshotLiveData` đăng ký khi có observer hoạt động và gỡ khi không còn observer;
  listener API dùng vòng đời view hoặc `observeOrder`/`stopObservingOrder` hiện có.
- Callback cũ được kiểm tra thế hệ truy vấn/tài khoản; danh sách riêng tư bị xóa
  khỏi màn hình khi đổi tài khoản hoặc bị từ chối quyền.
- Tín hiệu dồn trong khoảng 100–120 ms được gộp; màn API gói giải cứu/đơn Guest xếp
  yêu cầu tải lại nếu dữ liệu đổi khi request trước vẫn đang chạy.
- Render restart hoặc mất kết nối làm client đăng ký lại, với thời gian thử lại
  tăng dần. Tài liệu tín hiệu còn sót sau khi server crash không được tái sử dụng;
  có thể xóa các tài liệu phiên cũ khi bảo trì.
- Các transaction tồn kho, xác nhận thanh toán và phân quyền vẫn giữ nguyên.
  Webhook SePay tiếp tục xác thực tiền riêng; đồng bộ giao diện dùng snapshot.

Sửa trực tiếp Console không tự thực thi quy trình nghiệp vụ của API. Ví dụ thay
`initialQuantity` không tương đương thao tác điều chỉnh tồn kho; thay tên sản phẩm
không sửa tên đã chụp trong lịch sử đơn hàng. Để demo thanh toán/đặt hàng, dùng
đúng thao tác ứng dụng hoặc webhook đã xác thực.

## Triển khai

1. Triển khai thư mục `functions/` lên Render, gồm `realtime.js`, `read-cache.js`
   và `server.js` mới. `/health` phải trả về `realtimeVersion: 1`.
2. Deploy `firestore.rules` lên đúng Firebase project của ứng dụng. Không mở quyền
   đọc các collection nghiệp vụ riêng để thay thế bước này.
3. Build/cài APK mới trên các thiết bị demo. Các listener trực tiếp hoạt động với
   quyền cũ; các scope qua API cần cả hai bước triển khai ở trên.

Thay đổi trong nhánh làm việc không tự triển khai Render hoặc Rules production.

## Kiểm tra

```powershell
# Android build / unit / lint
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --offline
# Backend
npm --prefix functions run check
npm --prefix functions run test:unit
# Firestore Rules và transaction trên project demo
npm --prefix tests/firestore test
```

`SnapshotLiveDataTest` dùng FirebaseApp riêng có project `demo-rescuefarm` và
Firestore Emulator cổng 8088. Khởi động emulator bằng cấu hình `firebase.test.json`,
chạy `adb reverse tcp:8088 tcp:8088`, sau đó:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.rescuefarm.SnapshotLiveDataTest' --offline
```

Kết quả kiểm tra ngày 23/10/2026: Android build/lint thành công, 251 unit test Android,
177 test backend và 8 test Worker thành công; 130 test Firestore Emulator và
`SnapshotLiveDataTest` trên Android emulator thành công. Kiểm thử hai client xác
nhận thay đổi server phát snapshot tới cả hai và API trả kết quả mới đã lọc dữ
liệu riêng tư. Các kiểm tra này chạy trên môi trường demo, không triển khai production.

Kiểm tra thủ công trên môi trường demo sau triển khai:

1. Mở hai thiết bị, giữ một màn sản phẩm/danh mục/chiến dịch. Sửa tên, giá, ảnh,
   trạng thái trên Console; kiểm tra thêm/xóa và tài liệu rời bộ lọc.
2. Mở hơn một trang danh sách; sửa dữ liệu thuộc trang đã tải, giữ nguyên bộ lọc.
3. Seller sửa sản phẩm/lô trên thiết bị thứ nhất; Customer trên thiết bị thứ hai
   thấy dữ liệu mới. Customer đặt đơn; Seller thấy đơn mới và tồn kho thay đổi.
4. Mở chi tiết cùng đơn ở Customer/Seller hoặc Guest. Chuyển trạng thái hợp lệ,
   kiểm tra thông tin payment, shipment và đối soát tự cập nhật.
5. Admin duyệt Seller/chiến dịch, xử lý báo cáo; kiểm tra dashboard và màn liên
   quan ở thiết bị còn lại.
6. Giữ bản nháp form rồi sửa dữ liệu ở thiết bị kia; bản nháp không bị ghi đè.
   Rời màn hình/đổi tài khoản, bảo đảm không tiếp tục hiển thị dữ liệu của chủ cũ.
7. Mất mạng rồi kết nối lại, đưa app nền rồi mở lại; kiểm tra nhận snapshot mới
   và không nhân đôi các dòng dữ liệu.
