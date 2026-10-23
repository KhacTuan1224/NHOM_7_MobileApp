# Hạn sử dụng và tồn kho theo lô

## Quy tắc đang triển khai

- Product giữ tên, giá, ảnh và danh mục; Batch giữ hạn sử dụng và tồn kho.
- Lô được bán khi có hạn sử dụng hợp lệ trong tương lai, trạng thái AVAILABLE/RESERVED_PARTIAL và còn số lượng. Tại đúng thời điểm hạn sử dụng, lô không còn bán được.
- Home, Khám phá, Danh mục, danh sách sản phẩm và cửa hàng chỉ liệt kê sản phẩm ACTIVE có ít nhất một lô bán được. Màn chi tiết sắp lô theo hạn tăng dần, rồi ID để thứ tự ổn định; mặc định chọn lô hết hạn sớm nhất. Lựa chọn lô cụ thể còn hợp lệ của khách được giữ lại.
- Không thay batchId của dòng giỏ hàng hoặc đơn cũ sang lô mới. Giá và lịch sử đơn vẫn giữ nguyên. Checkout kiểm tra lại trên backend dù màn hình/cache chưa cập nhật.
- Khi nhập hàng mới, dùng **Nhập mới** trong Quản lý batch và tồn kho. Lô hết hạn/EXPIRED/ARCHIVED không được sửa để kéo dài hạn hoặc tăng tồn, kể cả gọi trực tiếp Firestore. Vẫn có thể lưu trữ lô đủ điều kiện.
- Sản phẩm ACTIVE tự xuất hiện lại sau khi tải lô mới. Tạo lô mới còn hạn cũng chuyển sản phẩm SOLD_OUT sang ACTIVE trong cùng transaction. HIDDEN/INACTIVE giữ nguyên quyết định ẩn/ngừng bán; Seller phải chủ động hiện/kích hoạt lại.

## Số lượng được bán và tồn thực tế

`getSellableQuantity(now)` trả 0 cho lô không được bán. `availableQuantity` giữ lượng tồn chưa bán thực tế, gồm hàng đã hết hạn. Giao diện quản lý hiển thị riêng hai số này.

Luôn giữ:

```text
initialQuantity = availableQuantity + reservedQuantity + soldQuantity
```

Không xóa lô, không đặt số tồn thực tế về 0, không tự ghi hàng hết hạn thành đã bán hay đã tiêu hủy. `batchExpiryEvents/{batchId}` ghi một lần thời điểm phát hiện, hạn dùng, đơn vị và ảnh chụp các lượng tồn. Đây là sổ phát hiện tồn hết hạn, **không phải chứng từ tiêu hủy hoặc số hao hụt đã xác nhận**. Sau khi hủy đơn, lượng trả về lô vẫn là tồn hết hạn, không được bán lại.

## Đơn đang giữ kho khi lô hết hạn

- Backend kiểm tra hạn lô khi chuyển tiếp CONFIRMED/PREPARING/READY_FOR_PICKUP/SHIPPING/DELIVERED.
- Đơn COD/CASH_ON_SITE còn UNPAID, chưa giao (PENDING/CONFIRMED/PREPARING/READY_FOR_PICKUP): tác vụ nền yêu cầu hủy qua transaction hiện có, trả đúng phần giữ, cập nhật phân bổ chiến dịch và không trả kho hai lần.
- Đơn chuyển khoản (kể cả đang chờ tiền), đơn đã thanh toán hoặc SHIPPING: giữ nguyên thanh toán và lượng giữ, đánh dấu `expiryReviewRequired`, thông báo Seller/Customer cần đối soát. Không giả lập hoàn tiền hoặc coi hàng đang vận chuyển đã trở về kho.
- DELIVERED/CANCELLED đã hoàn tất giữ nguyên lịch sử; replay xác nhận nhận thành công trước đó vẫn idempotent.
- Đã bổ sung bản ghi bàn giao bằng timestamp server trước hạn, xác nhận thực nhận của đúng người mua, bằng chứng thu hồi và Admin chốt đối soát. Khách xác nhận sau hạn đi qua trang đối soát; không dùng ngày tự nhập hay thời điểm bắt đầu SHIPPING làm bằng chứng đã nhận. Ghi nhận hoàn tiền chỉ sau khi Admin đối chiếu hoàn tiền thực tế; không tự thực hiện lệnh chuyển tiền. Xem [hướng dẫn UI và triển khai](audits/audit-fixes-setup-2026-10-22.md).

## Tác vụ nền và triển khai

1. Deploy Rules từ thư mục gốc:

   ```powershell
   firebase deploy --only firestore:rules
   ```

2. Deploy lại backend Node.js trên Render với source mới (bao gồm `functions/batch-expiry.js`). Dùng biến môi trường và service account hiện có, không cần secret mới, không cần xóa collection hay tạo lại sản phẩm. Không có bước đổi cấu trúc số lượng hoặc migration ghi đè lịch sử.
3. Build/cài APK mới để nhận bộ lọc, thứ tự lô và cảnh báo trên Android.

Worker chạy khi backend khởi động và mỗi 60 giây, mỗi lượt tối đa 200 lô và 200 đơn, có cursor để tiếp tục trang sau rồi quay vòng. Với database lớn, một vòng quét có thể cần nhiều lượt. Worker cần backend đang chạy; Render ngủ thì không chạy nền. Kiểm tra chặn bán ở checkout và chuyển trạng thái không phụ thuộc worker. Danh sách app được tính lại khi nhận dữ liệu, quay về màn hình và theo thời gian; một số màn kiểm tra lại mỗi 30 giây.

`batchExpiryEvents` chỉ Admin/Seller sở hữu được đọc, client không được sửa/xóa. Giữ trạng thái ARCHIVED khi quét lại. Không cộng các đơn vị kg/hộp/quả thành một số hao hụt chung.

## Kiểm tra thủ công

1. Tạo sản phẩm ACTIVE có hai lô: một lô đã sát hạn, một lô còn hạn lâu hơn. Chi tiết mặc định chọn lô có hạn sớm hơn.
2. Khi lô đầu hết hạn, lô đó không còn trong lựa chọn; lô còn hạn vẫn bán được.
3. Khi không còn lô bán được, sản phẩm biến mất khỏi danh sách mua, nhưng vẫn còn trong quản lý Seller và đơn lịch sử.
4. Màn quản lý lô hết hạn hiện lượng được bán bằng 0, tồn thực tế được giữ; nút Sửa bị khóa.
5. Nhấn Nhập mới, tạo lô còn hạn cho cùng sản phẩm; tải lại danh sách mua, sản phẩm xuất hiện lại nếu ACTIVE/SOLD_OUT, vẫn ẩn nếu HIDDEN/INACTIVE.
6. Đơn tiền mặt chưa giao hết hạn: sau worker, trạng thái CANCELLED, phần giữ về tồn hết hạn. Chạy worker lại không tăng tồn lần hai.
7. Đơn đã trả tiền/đang giao: có thông báo đối soát, không tự ghi nhận nhận hàng hay tự hoàn tiền.

## Kết quả xác minh

- Android: `:app:testDebugUnitTest :app:assembleDebug` thành công, 246 test không lỗi.
- `functions`: `npm run check` thành công; `npm run test:unit` qua 150 test backend và 8 test Cloudinary.
- Firestore Emulator: 13 regression có tên chứa Batch/batch đều qua, gồm quét hết hạn thực tế, hủy trả kho, khóa sửa lô và sổ riêng tư.
- Bộ Firestore đầy đủ vẫn có 3 lỗi ở kiểm duyệt bình luận Campaign và chỉnh danh mục legacy. Đã chạy đối chiếu Rules và test từ HEAD, tái hiện đúng 3 lỗi đó; không chỉnh những phần ngoài nghiệp vụ hết hạn trong thay đổi này. Log đối chiếu ở `build/batch-expiry-baseline-tests.log`; log lượt đầy đủ ở `build/batch-expiry-firestore.log`.
- Các kiểm tra dùng dữ liệu giả lập cục bộ, không thao tác database production.
