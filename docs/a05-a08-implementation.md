# A05–A08: đơn vị Campaign, thông báo bền vững và phản hồi kiểm duyệt

## 1. Hợp đồng đã triển khai

### A05 — Một Campaign, một đơn vị

- Các lô trong cùng Campaign phải thuộc các Product có cùng đơn vị sau khi chuẩn hóa chữ hoa/thường, khoảng trắng và Unicode. `kg`, `kilogram`, `kilograms`, `kilôgam`, `ký` là các tên của cùng đơn vị kg; **không đổi giá trị số**. Bó, bao, g… không được coi là kg.
- ViewModel kiểm tra đơn vị trước khi cộng mục tiêu. Repository đọc lại Product của từng lô trong transaction khi lưu. Backend đọc lại toàn bộ Product khi duyệt và kiểm tra đơn vị tại checkout. Đơn vị khai báo khác dữ liệu lô bị từ chối, không âm thầm đổi nhãn.
- Campaign có `quantityUnit`, `unitStatus`. Client chỉ khai báo `DECLARED`; backend mới xác nhận `VERIFIED`. `REVIEW_REQUIRED` đánh dấu dữ liệu cần đối soát. Client không được tự nâng trạng thái xác minh.
- Đơn vị Product đã tạo được giữ nguyên để bảo vệ lịch sử tồn kho/đơn hàng. Muốn bán theo đơn vị khác, tạo Product/lô mới.
- Order item tiếp tục lưu `unitSnapshot`, được chuẩn hóa tên khi checkout. Giao/hủy đơn không đổi đơn vị snapshot.
- Trang chi tiết, Home và Feed lấy đơn vị từ Campaign. Campaign bị đánh dấu đối soát không hiển thị tiến độ cộng sai và không cho đặt hàng từ trang chi tiết.
- Các chỉ số mang nhãn kg ở Dashboard Seller/Admin chỉ cộng Campaign **đã xác minh kg**. Màn Thống kê Seller trình bày riêng theo đơn vị snapshot; lượng giải cứu chỉ tính khi khớp đơn vị Campaign đã xác minh. Tỷ lệ trung bình không cộng lẫn lượng bó và kg. Có thông báo khi Campaign cũ chưa được tính do chưa xác minh đơn vị.
- Room 9 thêm hai cột cache, có migration 8 → 9 và schema xuất từ Room compiler.

### A06 — Ghi sự kiện cùng nghiệp vụ, gửi độc lập với UI

Nguồn sự kiện:

| Nghiệp vụ | Sự kiện bền vững |
| --- | --- |
| Đặt đơn Customer/Guest | Backend ghi outbox cho Seller/Admin và Customer nếu có UID, cùng transaction giữ hàng |
| Xác nhận/chuẩn bị/giao/hủy đơn | Backend ghi outbox Customer cùng transaction trạng thái và tồn kho |
| Xác nhận chuyển khoản | Client ghi `ORDER_PAYMENT` cùng transaction payment/order; Rules xác minh thay đổi |
| Gửi Campaign/hồ sơ Seller/báo cáo | Client ghi outbox trong cùng transaction/write batch với bản gửi |
| Lưu/kích hoạt Promotion | Client ghi outbox trong cùng transaction; worker chờ `startDate` |
| Quyết định kiểm duyệt/xử lý report | `adminAuditLogs` là sự kiện bất biến, được ghi cùng transaction nghiệp vụ; worker đọc stream bằng cursor và tạo outbox cùng commit cập nhật cursor |

`OrderDetailFragment` không còn phát push khi quan sát dữ liệu. Các lời gọi best-effort ở các luồng mới đã được bỏ. Các endpoint cũ được giữ để hỗ trợ retry từ APK cũ, nhưng bảo đảm ghi sự kiện cùng nghiệp vụ yêu cầu dùng APK mới.

Worker:

- Tách `notificationOutbox` (sự kiện), `notificationDeliveries` (giao cho từng UID), `notifications` (inbox).
- Delivery có `inboxCreated`, `pushAttempted`, `pushDelivered`, `retryCount`, `nextAttemptAt`, `lastError`, `deliveredTokenIds`, lease và mã lease để ngăn worker cũ ghi đè worker mới.
- Inbox có ID ổn định. Inbox ORDER đã ghi trong transaction được bổ sung metadata, không tạo bản thứ hai và không đặt lại `isRead`.
- Lỗi FCM không làm mất inbox. Retry tăng dần từ 10 giây, tối đa 1 giờ giữa các lần thử. Chưa có thiết bị/tài khoản đang không hoạt động thì giữ inbox và chờ thử lại sau 1 giờ.
- Multicast thành công một phần chỉ thử lại token chưa được chấp nhận; xóa token FCM xác nhận không còn hợp lệ. Mỗi lần gọi FCM có timeout 30 giây.
- `pushDelivered` nghĩa là FCM đã chấp nhận gửi, không chứng minh thiết bị đã hiển thị/người dùng đã đọc. Push có thể được thử lại sau một sự cố xảy ra đúng lúc FCM đã chấp nhận nhưng backend chưa ghi được kết quả. Android dùng ID thông báo ổn định để thay thế cùng một thông báo; inbox vẫn chỉ có một bản.
- Promotion chưa đến giờ được dời `nextAttemptAt` tới giờ bắt đầu. Bản Promotion đã sửa được phân biệt bằng timestamp đầy đủ, gồm nanosecond. Revision cũ hoặc ưu đãi/chiến dịch đã kết thúc được bỏ qua khi gửi push trễ; inbox lịch sử được giữ.
- Admin không nhận lại thông báo chờ duyệt của bản gửi đã được xử lý/thay thế.
- Có migration một lần cho nội dung đang chờ duyệt và Promotion tương lai đã lưu từ trước. Không quét lại toàn bộ các collection này mỗi phút.

### A07 — Mở đúng nội dung, đúng tài khoản

- Inbox/FCM có `resourceType`, `referenceId`, `action`; FCM còn có `notificationId`, `recipientUid`.
- Android mở `rescuefarm://notification/{notificationId}`, lưu yêu cầu chờ đăng nhập và gọi `/notifications/resolve` sau khi khôi phục phiên.
- Backend đọc lại vai trò hiện tại, kiểm tra UID người nhận và quyền đối với bản ghi. Admin mở đúng `AdminDetailFragment` với section/ID; Seller mở đơn/hồ sơ/chiến dịch của mình; người báo cáo mở kết quả báo cáo của mình.
- Không còn biến mọi MODERATION thành link reports. Link cũ thiếu loại tài nguyên được giải quyết khi xác định được duy nhất một bản ghi, hoặc quay về inbox với lời giải thích.
- Chỉ gọi `/notifications/read` sau khi điều hướng thành công hoặc đã hiển thị giải thích cho thông báo cũ không có đích cụ thể. Lỗi quyền/mạng/đích không còn tồn tại không âm thầm đánh dấu đã đọc.
- Sau khi đổi tài khoản, app lọc lại inbox, xóa nội dung báo cáo cũ đang hiển thị và không áp dụng response của phiên trước.
- Push không hiển thị nội dung gửi cho UID khác với tài khoản đang dùng; quyền thông báo Android vẫn được tôn trọng.

### A08 — Phản hồi kết quả

- Seller nhận quyết định/lý do công khai khi duyệt/từ chối hồ sơ, duyệt/từ chối/dừng Campaign và kiểm duyệt nội dung. Không còn giới hạn mọi recipient về CUSTOMER.
- Quyết định report cuối cùng tạo inbox cho `reporterId`, kể cả khi ứng dụng người nhận không mở.
- Nút **Báo cáo của tôi** mở danh sách phân trang; thông báo mở được một report cụ thể. `/reports/mine` luôn lọc theo UID người gọi.
- Reporter chỉ nhận kết quả công khai theo trạng thái. Không trả `resolutionNote`/ghi chú nội bộ qua API này. Client không được đọc trực tiếp report nội bộ; Admin vẫn đọc để xử lý.
- Ghi chú report có nhãn nội bộ trong UI. Audit của thao tác xử lý report lên nội dung liên quan cũng không chuyển ghi chú này cho chủ nội dung; dùng thông điệp chính sách công khai.
- Guest không có inbox theo Firebase UID; Seller/Admin vẫn nhận được thông báo đơn Guest.

## 2. Setup và phát hành

Các thay đổi trong repo **chưa tự deploy lên Firebase/Render hoặc cấu hình secrets của GitHub**.

1. Deploy `firestore.indexes.json`, chờ các index mới về trạng thái ready: các query due/retry của outbox/delivery và phân trang report theo reporter/ngày tạo.
2. Deploy backend `functions/` mới lên Render bằng cấu hình hiện có: `FIREBASE_SERVICE_ACCOUNT_JSON`, `RENDER_BACKEND_URL` phía app… Không cần thêm dịch vụ gửi SMS hoặc Cloud Function cho tính năng này.
3. Tạo một chuỗi bí mật ngẫu nhiên ít nhất 32 ký tự, khuyến nghị 64 ký tự. Ví dụ tự tạo trong PowerShell bằng `[guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N')`.
4. Đặt **cùng giá trị** vào:
   - Render → Environment → `NOTIFICATION_WORKER_SECRET`.
   - GitHub → Repository Settings → Secrets and variables → Actions → Repository secret `NOTIFICATION_WORKER_SECRET`.
   - GitHub repository variable `RENDER_BACKEND_URL` phải trỏ đúng URL Render, ví dụ `https://<service>.onrender.com`.
5. Push workflow `.github/workflows/notification-worker.yml` lên default branch; chạy **Notification worker wakeup → Run workflow** một lần để kiểm tra. Workflow gọi POST `/internal/notifications/run` với header `X-Worker-Secret`.
6. Backend tự chạy worker mỗi 60 giây khi tiến trình hoạt động. GitHub Actions gọi mỗi 5 phút để đánh thức/khôi phục xử lý khi host ngủ. GitHub/host có thể chạy trễ; đây không phải lịch chính xác tới từng giây. Thông báo không gửi trước giờ hiệu lực; thời gian thực tế phụ thuộc lượt worker tiếp theo.
7. Deploy Rules mới và cài APK mới. Cần cập nhật cả ba phía backend/Rules/APK để có đầy đủ bảo đảm outbox và điều hướng; APK cũ vẫn có luồng UI/thông báo cũ.
8. Kiểm tra `notificationWorkerState/health.lastSuccessAt`, `attentionRequired` và các document delivery có `lastError`. Lỗi gửi lặp lại từ 5 lần hoặc worker quá lâu không hoàn thành sẽ khiến workflow báo thất bại. Đọc log workflow và `notificationOutbox`/`notificationDeliveries` bằng quyền vận hành Firebase để xử lý; không sửa các trạng thái vận hành từ app.

## 3. Dữ liệu Campaign cũ

`reconcileAll` lúc backend khởi động và revalidation khi quote kiểm tra Product của từng lô. Campaign đã có giao dịch còn kiểm tra đơn vị item snapshot của các đơn liên quan. Không đủ chứng cứ hoặc lẫn đơn vị thì ghi `REVIEW_REQUIRED`, không tự coi là kg và không đổi số lượng lịch sử.

Quy trình đối soát:

1. Xem `unitReviewReason`, danh sách lô và `unitSnapshot` của từng đơn. Giữ bản sao dữ liệu trước khi sửa.
2. Nếu Campaign đang ACTIVE nhưng lẫn đơn vị, dừng bằng luồng Admin hiện có. Các lô còn hàng được gỡ liên kết; reservation của đơn cũ giữ nguyên.
3. Tạo Campaign mới riêng theo từng đơn vị trên phần tồn khả dụng. Đơn cũ tiếp tục giao/hủy theo Campaign cũ, không chuyển reservation sang Campaign mới.
4. Không sửa Product/đơn cũ thành kg để làm cho thống kê đẹp hơn. Dữ liệu thực sự thiếu đơn vị cần đối soát nghiệp vụ; chỉ xác minh lại khi có chứng cứ. Campaign cũ bị loại khỏi tổng kg được báo rõ trên màn Thống kê.

## 4. Kiểm tra

- `npm --prefix functions run check`: syntax backend.
- `npm --prefix functions run test:unit`: A01–A08, gồm phân bổ/đơn vị, FCM retry, lease, multicast một phần, Promotion hẹn giờ, backfill, cursor nanosecond, điều hướng theo actor và bảo mật kết quả report.
- `cd tests/firestore; npm test`: project Emulator `demo-rescuefarm` riêng biệt. Có test ghi report/Campaign cùng outbox, rollback khi event bị giả mạo, chặn sửa delivery state, bảo vệ ghi chú nội bộ và thử retry FCM bằng stub trên transaction thật.
- `./gradlew :app:testDebugUnitTest :app:assembleDebug`: kiểm tra Java và đóng gói APK trên CI.
- Local đã kiểm tra bằng JBR trực tiếp: type-check toàn bộ Java main, chạy bộ JUnit, Room compiler xuất schema 9. SQLite migration được đối chiếu với schema đã xuất. Các bước này không thay thế chạy APK trên thiết bị.
- Máy Windows hiện vẫn không khởi động được Firestore Emulator/Gradle vì lỗi Java loopback. Không ghi nhận các suite đó là đã pass ở local; CI/thiết bị cần xác nhận sau khi push.

Nghiệm thu thực tế: gửi một đơn rồi đóng app; tạo lỗi FCM tạm thời rồi khôi phục; kiểm tra chỉ có một inbox. Tạo Promotion bắt đầu trong tương lai, quan sát chưa có push trước giờ và có xử lý khi tới giờ. Thử từng loại thông báo ở foreground/cold start, sau logout/login, và bằng tài khoản khác. Từ chối hồ sơ/Campaign có lý do, giải quyết report có ghi chú nội bộ, kiểm tra đúng người nhận và không lộ ghi chú.
