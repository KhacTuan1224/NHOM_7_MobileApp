# Hoàn thiện backlog audit — 22/10/2026

## Trạng thái

| Mục | Kết quả |
|---|---|
| AUD-01 | Thay số orientation bằng constant; build, unit test và Lint được kiểm tra. Không tắt Lint. Các warning không chặn vẫn giữ để dọn theo phạm vi riêng. |
| AUD-02 | Cập nhật hai fixture ẩn comment theo contract `moderationPreviousStatus`; giữ test từ chối payload thiếu trạng thái cũ. |
| AUD-03 | Danh mục ẩn/hiện ngay khi bấm, không hỏi lý do; bỏ ô lý do ở editor danh mục và assertion cũ. Audit vẫn lưu người và thời điểm thao tác. Banner/kiểm duyệt khác giữ chính sách riêng. |
| AUD-04 | Có danh sách đối soát, ghi bằng chứng bàn giao/thu hồi, xác nhận của người mua, Admin chốt kết quả tiền/kho/audit trong một transaction, có idempotency. |
| AUD-05 | Map/cache cờ đối soát và lý do hủy; hiện cảnh báo ở danh sách/chi tiết đơn, khóa nút chuyển tiếp/nhận thông thường trong lúc đối soát, mở trang xử lý riêng. |
| AUD-06 | **WON'T FIX theo quyết định của người dùng.** Giữ SePay tự động; không mở lại xác nhận chuyển khoản thủ công. Hàm legacy giữ để kiểm thử/tương thích ngược. |
| AUD-07 | “Tồn kho tối thiểu” tính tổng các lô còn bán được, đúng bộ lọc địa điểm/chiến dịch/hạn. Không cộng khác sản phẩm hoặc đơn vị; không tự đổi batchId giỏ/đơn. |
| AUD-08 | Outbox Room bền vững + UUID mỗi snapshot + receipt backend cùng transaction với giỏ; có retry WorkManager, kiểm tra đúng tài khoản, không cộng lại khi mất phản hồi hoặc gửi đồng thời. |

Không sửa `functions/sepay.js`, không sửa xác thực/signature webhook, khớp tiền, mã QR hay cơ chế tự chuyển PAID/CONFIRMED. Suite SePay nguyên trạng được chạy cùng regression. Đối soát không có quyền đánh dấu tiền chuyển khoản vào là PAID thủ công; chỉ ghi nhận hoàn tiền đã thực hiện hoặc hủy sau khi đối chiếu sao kê chưa có tiền vào.

## Triển khai

Chưa deploy production trong lượt thực hiện. Không cần secret mới, không xóa collection hay dữ liệu cũ.

1. Deploy backend Render từ source mới. Đảm bảo đi kèm `cart-migration.js`, `order-reconciliation.js`, thay đổi `campaign-lifecycle.js`, `server.js` và `package.json`. Dùng các biến môi trường hiện có.
2. Từ root repo, deploy Rules và hai index mới cho danh sách đối soát:

   ```powershell
   firebase deploy --only "firestore:rules,firestore:indexes"
   ```

   Hai index được thêm cho `orders`: `sellerId + expiryReviewRequired`, `ownerId + expiryReviewRequired`. Không có thay đổi xóa index cũ trong file cấu hình.
3. Cài đè APK mới để giữ dữ liệu máy. Room tự nâng 10 → 11, thêm hai cột order cache và bảng `cart_migration_outbox`; không dùng destructive migration, không yêu cầu xóa data.

   ```powershell
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

Nên cập nhật backend trước APK. Nếu backend chưa có endpoint mới, job chuyển giỏ được giữ để retry thay vì bỏ dữ liệu. Endpoint đối soát và chuyển giỏ đều yêu cầu backend online.

## Sử dụng đúng UI hiện tại

### Seller: ghi bàn giao và thu hồi

1. Vào **Đơn hàng & giao nhận** → chi tiết đơn → **Bằng chứng giao nhận / Đối soát**.
2. Tại ô **Đường dẫn HTTPS tới bằng chứng bàn giao/thu hồi**, dán link ảnh/chứng từ có thể đối chiếu được. Link phải dùng HTTPS; không nhập đường dẫn file local như `C:\...`. Chức năng này lưu link chứng từ, không upload file lên Firebase.
3. Khi đã bàn giao thực tế và lô còn hạn, bấm **Ghi nhận đã bàn giao trước hạn**, xác nhận dialog. Đơn phải ở trạng thái đã chuẩn bị/sẵn sàng nhận/đang giao. Nếu chuyển khoản, tiền phải được SePay xác minh trước đó.
4. Hệ thống dùng timestamp server. Không có ô nhập ngày để ghi lùi thời điểm bàn giao. Khi lô đã hết hạn, không thể tạo mới bằng chứng bàn giao trước hạn.
5. Nếu hàng đã bàn giao/đang giao được thu hồi thực tế, thay link bằng chứng phù hợp, bấm **Ghi nhận đã thu hồi hàng**. Đây là bằng chứng trả hàng, chưa tự cộng kho hoặc hoàn tiền; đơn chuyển sang diện đối soát.

Link chứng từ cần có quyền xem phù hợp cho bên đối chiếu. Màn hình cung cấp nút **Mở bằng chứng bàn giao** / **Mở bằng chứng thu hồi** khi đã có bản ghi.

### Customer hoặc Guest: yêu cầu đối soát và xác nhận thực nhận

- Customer có **Đơn cần đối soát** ở danh sách đơn. Guest mở từ chi tiết đơn đã checkout/tra cứu trên thiết bị có token truy cập hợp lệ. Không được xem bằng cách đoán orderId.
- Mở **Bằng chứng giao nhận / Đối soát** → **Yêu cầu Admin đối soát** nếu cần xử lý ngoại lệ.
- Khi Seller đã ghi bàn giao, có nút **Xác nhận tôi đã nhận hàng thực tế**. Dialog xác nhận đã nhận đủ hàng và thanh toán tiền mặt khi nhận nếu có. Với chuyển khoản, nút này không thay SePay xác minh tiền.
- Xác nhận thực nhận này là bằng chứng cho Admin xử lý nhận muộn, không tự sửa tồn/tiền bằng thao tác đối soát.
- Nếu đơn đang chờ đối soát, nút nhận/chuyển tiếp thông thường bị ẩn và backend cũng chặn. Chi tiết đơn hiển thị cảnh báo hoặc lý do hủy tự động.

### Admin: chốt đơn có ngoại lệ

Vào danh sách đơn của Admin → **Đơn cần đối soát** → chọn đơn. Danh sách có phân trang **Xem thêm**. Hoặc mở chi tiết quản trị đơn → **Mở đối soát đơn hàng**.

Form có:

- Dropdown kết quả: **Đã hoàn đủ tiền — hủy đơn**; **Chưa thanh toán — hủy đơn**; **Đã bàn giao trước hạn — hoàn tất nhận hàng**.
- **Kết luận đối soát**.
- **Đường dẫn HTTPS tới bằng chứng đã đối chiếu**.
- **Mã giao dịch hoàn tiền (khi hoàn tiền)**.
- **Số tiền đã hoàn thực tế**: nhập số VND, ví dụ `100000`, không nhập `100.000đ`. Số tiền phải đúng tổng snapshot của đơn khi hoàn tiền.
- Checkbox **Đã đối chiếu bằng chứng hoàn đủ tiền thực tế**.
- Checkbox **Đã đối chiếu bằng chứng hàng đã thu hồi về kho**.
- Checkbox **Đã đối chiếu sao kê: chưa có tiền chuyển khoản vào (khi hủy đơn chờ chuyển khoản)**.
- Nút **Chốt đối soát và ghi lịch sử**, sau đó dialog xác nhận.

Điều kiện từng kết quả:

| Kết quả | Điều kiện |
|---|---|
| Đã hoàn đủ tiền — hủy đơn | Đơn/payment đang PAID; Admin đã đối chiếu hoàn tiền thực tế, có mã giao dịch, link bằng chứng, số tiền bằng tổng đơn và checkbox hoàn đủ tiền. Nếu đã bàn giao/đang giao thì cần bản ghi thu hồi hợp lệ và checkbox hàng về kho. |
| Chưa thanh toán — hủy đơn | Tiền mặt còn UNPAID; nếu chuyển khoản còn PENDING thì phải có bằng chứng đối chiếu sao kê và checkbox chưa có tiền vào. Không được dùng kết quả này cho đơn PAID. Hàng đã giao/đang giao vẫn cần thu hồi thực tế. |
| Đã bàn giao trước hạn — hoàn tất nhận hàng | Có bản ghi bàn giao từ Seller trước hạn mọi lô, có xác nhận thực nhận của đúng người mua, chưa ghi thu hồi. Chuyển khoản phải PAID bởi luồng SePay hiện có. |

Hệ thống không thực hiện lệnh chuyển tiền hoàn. `REFUNDED` nghĩa là Admin **đã ghi nhận bằng chứng hoàn đủ tiền thực tế**. Không tự tạo chứng từ ngân hàng hoặc suy diễn đã hoàn tiền từ việc khách yêu cầu hủy.

Nếu Admin đã hủy một đơn chờ chuyển khoản sau đối chiếu, giao dịch vào muộn sẽ đi theo kiểm tra REVIEW sẵn có của SePay; không thay đổi webhook để tự khôi phục đơn đã hủy. Cần kiểm tra chứng từ cẩn thận trước khi chốt hủy.

Sau khi chốt, order/payment/tồn kho/phân bổ chiến dịch và event Admin được ghi cùng transaction. Gửi lại cùng yêu cầu không trả kho hay tính đã bán hai lần; đơn kết thúc từ trước không được báo thành công giả. Lô đã hết hạn nhận lượng trả về vẫn EXPIRED, không bán lại tồn đó. Màn hình kết quả hiển thị kết luận, chứng từ và mã/số tiền hoàn nếu có.

### Danh mục Admin

- Danh sách danh mục: bấm **Ẩn** là ẩn, **Hiện** là hiện, không dialog hỏi lý do.
- Editor danh mục không còn ô lý do và không bắt nhập lý do khi bỏ chọn **Đang hiển thị**.
- Audit vẫn giữ người/thời điểm thao tác. Không áp dụng thay đổi này cho quy tắc kiểm duyệt sản phẩm, báo cáo hoặc banner.

### Chuyển giỏ Guest khi đăng nhập

- Chỉ chuyển sang Customer, không chuyển sang Seller/Admin.
- Snapshot được đưa vào `cart_migration_outbox` và lấy ra khỏi giỏ Guest trong cùng transaction Room. Job chứa payload bất biến và tài khoản nhận, không chỉ giữ trong RAM.
- Backend gộp giỏ và tạo `cartMigrationReceipts/{userId}_{requestId}` cùng transaction. Receipt không cho client tự đọc/ghi.
- Nếu mất phản hồi rồi retry, backend trả replay, không cộng lại. Job mới sau đó có UUID mới nên có thể chuyển một giỏ hợp lệ có nội dung giống trước đó.
- Hoàn tất chỉ xóa job tương ứng, không clear lại giỏ Guest có thể đã có hàng mới. Đổi tài khoản không chuyển job của tài khoản cũ sang tài khoản mới.
- WorkManager thử lại khi có mạng; có recovery định kỳ 15 phút cho các job còn giữ. Đây là lịch do Android điều phối, không cam kết đúng từng phút.
- Nếu giỏ Customer đã đạt giới hạn 100 dòng, dữ liệu job vẫn giữ; cần giảm số dòng để có thể gộp. App có thông báo chuyển giỏ chưa xong.

## Test cục bộ

Không khởi chạy APK vào Firebase production trong quá trình test. Các test mới dùng project demo/local emulator hoặc SQLite `:memory:`.

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --no-daemon --console=plain
npm --prefix functions run check
npm --prefix functions run test:unit
python tests/audit/test_room_migration.py
python scripts/check_navigation.py
```

Từ `tests/firestore`: `npm test`. Script gồm Rules, probe chính sách mới, API đối soát/chuyển giỏ và các regression commerce/rescue/SePay hiện có.

Regression mới bao gồm replay/concurrency, sai tài khoản, sai tiền hoàn, quyền Admin, Guest token, bàn giao không ghi lùi, xác nhận của người mua, thu hồi hàng trước trả kho, không hoàn tất hàng đã thu hồi, không chốt lại đơn lịch sử, giữ chứng từ SePay gốc và SQL nâng Room giữ dữ liệu. Chưa chứng minh camera/file provider, quyền xem chứng từ ngoài app hoặc thao tác Android thực tế bằng instrumentation; SQLite probe kiểm tra schema/transaction chứ không thay thế device test.

## Kết quả xác minh cuối

- Android: **247/247** unit test, assembleDebug thành công, **0 lỗi Lint**. Còn **626 warning** không chặn; không tắt/suppress Lint để làm xanh.
- Backend: **156/156** unit test; Cloudinary Worker **8/8**; syntax check thành công.
- Firestore/API emulator: **125/125**; smoke test riêng API mới **6/6**.
- SQLite trong bộ nhớ: **2/2**; Java probe tổng tồn 6 + 6, lọc 10 trả đúng sản phẩm; navigation có **61** Fragment tồn tại.
- Đối chiếu source với HEAD xác nhận `functions/sepay.js` và phần hàm `confirmBankTransfer` legacy giữ nguyên.
- Kết quả máy đọc: [audit-fix-results-2026-10-22.json](audit-fix-results-2026-10-22.json). Log ở `build/audit-fix-*.log`; chưa deploy hoặc ghi dữ liệu production.
