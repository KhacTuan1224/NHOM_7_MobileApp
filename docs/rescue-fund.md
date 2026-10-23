# Hũ giải cứu nông sản

## Luồng đã triển khai

Một hũ chung (`main`) do Admin quản lý, một địa điểm thiện nguyện đang được chọn. Địa điểm được chụp lại vào từng chuyến; đổi địa chỉ trong cấu hình không đổi chuyến đang giao.

Bảng xếp hạng sẻ chia nằm ngay dưới **Nơi đón nhận sẻ chia**, hiển thị tối đa 50 lượt ủng hộ PAID theo số tiền giảm dần; cùng số tiền thì lượt xác nhận trước đứng trước. Đây là xếp hạng từng lượt, không gộp người theo tên hiển thị. Top 1–3 có bục xếp hạng, từ hạng 4 là danh sách; người không công khai tên vẫn ẩn danh. Làm mới trang để cập nhật, backend cache tối đa 30 giây và vô hiệu hóa khi nhận ủng hộ mới. Customer, Guest và Seller không còn khối “Hũ hoạt động thế nào”.

Khi cập nhật bảng xếp hạng, deploy backend Render và composite index `rescueFundDonations: status ASC, amount DESC, paidAt ASC` trong `firestore.indexes.json`, chờ index sẵn sàng rồi cài APK mới.

1. Customer/Guest mở **Home → Hũ giải cứu → Ủng hộ vào hũ**, nhập 10.000–10.000.000đ, nhấn **Xác nhận và xem QR**. Seller cũng có thể ủng hộ trong màn hũ.
2. QR có đúng số tiền, ngân hàng và mã chuyển khoản riêng cho lần ủng hộ, dùng trong 30 phút. Biên nhận gần nhất lưu trên thiết bị; màn QR kiểm tra trạng thái mỗi 15 giây khi đang mở, dừng khi chuyển màn/đã nhận/hết thời gian. Có nút làm mới trạng thái, không có nút xác nhận đã chuyển tiền.
3. Webhook SePay hợp lệ tự ghi nhận tiền vào hũ. Kiểm tra tài khoản, mã, số tiền, giờ giao dịch; chống lặp ID giao dịch và mã ngân hàng. Giao dịch sai/ngoài thời gian được lưu để đối soát, không tự cộng tiền. Webhook gửi thử `id: 0` không làm tăng số dư.
4. Khoảng 00:00 giờ Việt Nam, tiến trình xét lô còn hạn, gần hết hạn trong cửa sổ 12/24 giờ tính từ nửa đêm đó, không trượt sang ngày kế tiếp nếu chạy muộn. Mặc định cửa sổ 24 giờ, không có giao dịch mua thành công trong 12 giờ; Admin có thể đổi thời gian không giao dịch thành 24 giờ. Không chọn hàng hết hạn hoặc chỉ còn dưới 2 giờ để giao.
5. Ưu tiên lô hết hạn sớm, sau đó ID lô để thứ tự ổn định. Kiểm tra Seller ACTIVE/APPROVED, Product ACTIVE, kho khả dụng, ngân hàng Seller, ngân sách ngày/Seller. Lô/sản phẩm/tài khoản mới tạo chưa đủ thời gian chờ thì chưa đủ điều kiện. Đơn PAID hoặc DELIVERED, lượt Flash Sale/Hộp bí ẩn COMPLETED được xem là giao dịch thành công; dùng `updatedAt` trong cửa sổ chờ, nên một cập nhật gần đây của đơn đã trả tiền cũng có thể trì hoãn hỗ trợ một cách thận trọng.
6. Phân bổ giữ **đồng thời tiền và kho trong transaction**. Một lô chỉ được phân bổ một lần mỗi ngày; không vượt số tiền còn dùng được hoặc ngân sách đã đặt. Giá lấy `rescuePrice` hiện tại, snapshot vào chuyến. Không khôi phục tính năng khuyến mãi/càng mua càng giảm đã bỏ. Khối lượng/thể tích tối đa ba số thập phân; hộp, túi, quả và đơn vị đếm khác chỉ phân bổ số nguyên. Tiền VND làm tròn lên và không vượt ngân sách. Với lô gắn Campaign, giữ/hoàn/chốt cả bộ đếm phân bổ Campaign, chỉ chọn chiến dịch ACTIVE có cùng đơn vị hàng.
7. Seller nhận thông báo → **drawer → Hũ giải cứu · giao thiện nguyện → Nhận chuyến giao**. Xem nơi nhận, hạn chót, số tiền, sản phẩm/chiến dịch và **Xem vị trí nhận** để mở bản đồ. Đây là tiền quỹ đang giữ, chưa phải tiền Seller đã nhận.
8. Seller giao trước hạn, chọn **Gửi ảnh xác nhận giao → Chụp ảnh bàn giao/Chọn ảnh bằng chứng → Gửi bằng chứng**. Ảnh cần gửi lên trước hạn chót; Admin nhận thông báo kiểm tra. Ảnh mới dùng URL riêng, không ghi đè ảnh cũ; backend kiểm tra ảnh trên Cloudinary tồn tại.
9. Admin xem ảnh, đối chiếu nơi nhận, chọn **Xác minh đã giao trước hạn**. Lúc này chuyển kho từ giữ sang đã bán/giải cứu, tiền vẫn đang giữ. Có thể xét duyệt sau hạn nếu bằng chứng đã gửi trước hạn. Ảnh là bằng chứng để Admin kiểm tra, không phải cơ chế tự chứng minh hàng đã được bàn giao.
10. Admin **chuyển khoản ngoài app** đúng số tiền vào tài khoản Seller được snapshot, sau đó chọn **Ghi tiền đã chuyển cho Seller**, nhập mã giao dịch và tải ảnh chuyển tiền, nhấn **Lưu bằng chứng đã chuyển**. App mới chuyển tiền đang giữ thành đã chi, ghi audit và thông báo Seller. Không có API tự chuyển tiền ra ngân hàng. Một mã chi không được dùng cho hai Seller/chuyến khác nhau.

## Trạng thái và cân bằng

`RESERVED → ACCEPTED → DELIVERED → VERIFIED → PAID`.

Chuyến chưa gửi bằng chứng trước hạn được tự hủy và giải phóng tiền/kho đang giữ; Seller cũng có thể hủy trước khi gửi ảnh. Admin có thể hủy chuyến chưa xác minh nhưng phải nhập lý do. Chuyến DELIVERED được giữ lại để Admin xét ảnh, không tự hủy theo giờ. Chuyến VERIFIED giữ nguyên tiền chờ khoản chi; không tự hủy hoặc tự hoàn tiền. Mọi thao tác lại sau timeout đều kiểm tra trạng thái để tránh ghi nhận hai lần.

Hàng hết hạn vẫn được ghi nhận lượng tồn vật lý; việc giải phóng phần đang giữ không biến nó thành hàng được phép bán. Không xóa Product/Batch hoặc lịch sử.

Số dư: `có thể phân bổ + đang giữ + đã chi = tổng ủng hộ SePay + tổng bút toán đối soát` (khởi đầu bằng 0). Không xem `accumulated` của webhook là số dư hũ; tài khoản ngân hàng có thể có giao dịch ngoài hũ. Admin chỉ điều chỉnh phần có thể phân bổ, phải có lý do và ảnh sao kê; không được sửa/xóa ledger. Bút toán đối soát không đánh dấu một donation là PAID và không thay cho webhook xác nhận tiền ủng hộ.

Giới hạn ngày tính theo tổng tiền đã phân bổ, kể cả chuyến hủy; tiền được giải phóng dùng lại ở lượt ngày sau. Không tự tạo vòng lặp phân bổ lại cùng lô trong ngày.

## Kích hoạt trên môi trường thật

Lượt triển khai code này **chưa deploy production, chưa tạo quỹ/số dư và chưa chạy giao dịch thật**. Không seed database để tạo tiền mẫu.

1. Deploy index và Rules từ repo. Không dùng `--force` khi deploy indexes để tránh xóa index ngoài file:

   ```powershell
   .\tests\firestore\node_modules\.bin\firebase.cmd deploy --project nhom7-rescuefarm --only "firestore:rules,firestore:indexes" --non-interactive
   ```

   Index mới: `rescueFundDeliveries(status, deadline)`, `orders(sellerId, paymentStatus, updatedAt)`, `orders(sellerId, status, updatedAt)`, `rescueClaims(sellerId, status, updatedAt)`. Dùng lại index `productBatches(status, expiryDate)` hiện có. Chờ index sẵn sàng trước khi bật hũ.

2. **Dùng lại VietinBank đã kết nối của Seller.** Không cần ngân hàng mới, không cần thêm `RESCUE_FUND_BANK_JSON` hoặc key SePay mới. Backend lấy từ `SEPAY_MERCHANTS_JSON` hiện tại và đối chiếu `sellerPaymentProfiles/{uid}`. Nếu chỉ có một Seller enabled, tự chọn tài khoản đó. Nếu có nhiều Seller enabled, đặt `RESCUE_FUND_SELLER_ID` bằng đúng UID Seller giữ tài khoản, không dùng email. Tên chủ tài khoản lấy từ hồ sơ ngân hàng riêng của Seller, không thay bằng tên quỹ.

   Nếu trước đó đã thêm `RESCUE_FUND_BANK_JSON` để làm theo hướng dẫn cũ, xóa biến đó để dùng chế độ tự chọn một Seller; hoặc đặt `RESCUE_FUND_SELLER_ID` để ưu tiên Seller chỉ định. Cấu hình JSON cũ vẫn được hỗ trợ như chế độ tùy chọn tương thích, không phải điều kiện bắt buộc và không có quy tắc cấm trùng tài khoản Seller/Admin. Không đưa secret hoặc mật khẩu vào app/chat/Git.

3. Deploy source `functions` lên Render và `cloudinary-worker/src/index.js` lên Worker hiện tại (giữ nguyên các biến Cloudinary/Media Worker đang dùng). Worker mới hỗ trợ ảnh `fundDelivery` cho Seller và `fundReceipt` cho Admin; bản cũ sẽ từ chối upload loại ảnh mới.

4. **Giữ nguyên webhook Seller hiện tại** `/webhooks/sepay/{SELLER_UID}`, key và chế độ API Key/HMAC đang dùng. Không thêm webhook hũ khi dùng chung tài khoản. Backend nhận callback ở URL cũ, tìm mã chuyển khoản trong mapping hũ; mã thuộc hũ thì ghi số dư hũ, mã đơn hàng thì đi vào xử lý đơn hàng hiện tại.
   - QR vẫn dùng VietQR với cùng VietinBank. Mỗi lần ủng hộ có số tiền và nội dung `SEVQR` riêng; không dùng một QR tĩnh chung cho mọi lần nạp.
   - Giao dịch ngân hàng dùng khóa chung giữa hai luồng: không thể đồng thời cộng hũ và trả tiền đơn hàng, kể cả callback chạy đồng thời. Không đánh dấu đơn hàng PAID khi chuyển tiền vào hũ.
   - Endpoint hũ riêng `/webhooks/rescue-fund/sepay` chỉ là tùy chọn tương thích cấu hình JSON cũ; không cần dùng cho cách tận dụng tài khoản hiện tại.

   Đối chiếu: [xác thực SePay](https://developer.sepay.vn/vi/sepay-webhooks/xac-thuc), [payload và phản hồi webhook](https://developer.sepay.vn/vi/sepay-webhooks/tich-hop-webhook).

5. Cài APK mới. Admin → drawer → **Hũ giải cứu nông sản → Cấu hình hũ và điểm thiện nguyện**: nhập tên hũ, tổ chức, địa chỉ qua form tỉnh/thành, quận/huyện, phường/xã và số nhà/tên đường, điện thoại, ngân sách ngày, tối đa mỗi Seller/ngày, hai khung giờ. Ban đầu lưu khi đang tắt để kiểm tra thông tin; bật khi tài khoản/webhook/index/Worker đã sẵn sàng. Seller phải lưu ngân hàng hợp lệ trong hồ sơ trước khi được chọn.

6. Lịch đánh thức backend: workflow [rescue-fund-nightly.yml](../.github/workflows/rescue-fund-nightly.yml) chạy `17:00 UTC` (00:00 Việt Nam), có `workflow_dispatch`. Repository variable `RENDER_BACKEND_URL` là URL backend HTTPS; repository secret `NOTIFICATION_WORKER_SECRET` phải khớp Render. Workflow chỉ chạy khi cấu hình đủ; không in secret. Backend còn có timer khi tiến trình đang sống và giữ cursor theo ngày để khởi động lại không phân bổ trùng. Render Free có thể ngủ, GitHub cron có thể chạy muộn; đây không phải cam kết đúng từng giây lúc 00:00. Không có webhook/cron nào tạo tiền khi chưa được SePay xác nhận hoặc chưa có bút toán Admin có bằng chứng.

## Dữ liệu và quyền

Các collection mới: `rescueFunds`, `rescueFundDonations`, `rescueFundReferences`, `rescueFundWebhookEvents`, `rescueFundBankTransactions`, `rescueFundLedger`, `rescueFundDeliveries`, `rescueFundRuns`, `rescueFundPayoutReferences`. Tạo tự nhiên qua API, không tạo thủ công bằng số dư giả. Client không đọc/ghi trực tiếp, kể cả tài khoản Admin; API kiểm tra quyền và chỉ trả dữ liệu phù hợp.

Guest/Customer chỉ xem thông tin hũ công khai và biên nhận có token bí mật của thiết bị. Seller chỉ xem/nhận/giao/hủy chuyến của mình; Admin xem sổ, cấu hình, kiểm tra ảnh và ghi chi. Không công khai sao kê, tài khoản Seller, token hoặc hồ sơ cá nhân. Ảnh bằng chứng dùng path theo chủ thể/chuyến, không thể ghi đè hoặc xóa qua client.

Đọc công khai cache 30 giây, biên nhận cache 10 giây, tránh tự poll khi màn QR không mở. Lượt quét ngày xử lý tối đa 50 lô/trang, có cursor; sau khi xong ngày hoặc không mở hũ, giảm tần suất kiểm tra còn 5 phút. Bộ chặn quota chung của backend vẫn áp dụng. Khóa/transaction đảm bảo tiền và kho không bị phân bổ hai lần, kể cả hai tiến trình chạy đồng thời.

## Kiểm thử mềm

Cập nhật dùng chung VietinBank: thêm regression tự chọn một merchant, kế thừa tên/tài khoản ngân hàng, callback HMAC/API Key ở URL Seller cũ, giữ nguyên xác nhận đơn hàng, chống ghi nhận chéo cả hai thứ tự và khi chạy đồng thời. Dùng webhook giả trên Emulator để mô phỏng tiền app tăng; không chuyển tiền thật. Trong production vẫn cần callback ngân hàng hợp lệ, không có nút cộng tiền giả; probe SePay `id: 0` không ghi tiền.

| Bộ kiểm tra cuối | Kết quả |
|---|---:|
| Android unit test | 249/249 |
| APK debug + Lint | Thành công; 0 lỗi Lint, 671 cảnh báo |
| Backend unit test | 168/168 |
| Cloudinary Worker | 9/9 |
| Firestore/API Emulator toàn repo | 165/165, gồm 37 test hũ |
| Navigation | 59 Fragment destination có class |

Regression hũ bao phủ tiền vào, replay, sai tài khoản/số tiền/thời gian, token Guest, phân bổ đồng thời, đơn mua gần đây, lô hết hạn, giới hạn ngân sách, Campaign counters, giữ/hoàn/chốt kho, ảnh không tồn tại, mã chi trùng giữa hai Seller, khôi phục lượt quét sau restart và đơn vị hàng đếm được. Giữ nguyên logic xác nhận SePay của đơn hàng; chỉ export lại hàm xác thực dùng chung và bổ sung adapter định tuyến phía trước. Commerce và lifecycle đơn hàng không đổi.

Test hũ nằm tại `functions/rescue-fund.test.cjs`, chỉ chấp nhận Firestore Emulator localhost với project demo. Dùng webhook giả, mock kiểm tra ảnh Cloudinary, không upload ảnh, chuyển tiền hoặc gửi push thật. Suite có trong `npm test` của `tests/firestore`.

Chạy Android: `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug`. Nếu máy thiếu RAM, chạy riêng Emulator (`JAVA_TOOL_OPTIONS=-Xmx512m -XX:MaxMetaspaceSize=256m`) rồi Android (`--max-workers=1`), không chạy hai JVM lớn song song.

Kết quả cuối được ghi trong `build/rescue-fund-android-verified.log`, `build/rescue-fund-full-emulator-final.log`, `build/rescue-fund-backend-unit-final.log`, `build/rescue-fund-backend-check.log`. Chưa chứng minh tích hợp SePay live, camera/GPS/mạng trên thiết bị thật hoặc việc giao hàng thực tế; cần kiểm tra môi trường đã cấu hình trước khi mở nhận tiền thật.


