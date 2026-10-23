# Rà Reads và chỉnh giao diện Hũ giải cứu — 23/10/2026

## Kết quả và giới hạn

Rà mã backend/API, worker và các Handler/listener Android; không truy vấn database thật, không deploy, không gọi webhook thật. Chưa có Query Insights hoặc log request production để quy chính xác đợt tăng 31K Reads cho một API. Biểu đồ tổng không chứng minh có vòng lặp vô tận.

## Các điểm đã sửa

| Điểm | Trước | Sau |
|---|---|---|
| Worker trong `server.js` | Chạy cả nhóm 6 tác vụ mỗi 60 giây | Mặc định 300 giây, vẫn chống chồng lần chạy và có quota cooldown. Có thể đặt `BACKGROUND_RECONCILE_INTERVAL_MS` từ 60.000 đến 900.000. Tác vụ nền/thông báo có thể trễ đến khoảng 5 phút. |
| Admin dashboard | Mỗi request đọc lại toàn bộ productBatches và products ACTIVE, không cache response | Cache 60 giây, gộp request đồng thời; chỉ đọc batch được chiến dịch ACTIVE tham chiếu và product của các batch đó; deduplicate ID, tối đa 20 request đồng thời. Auth luôn kiểm tra trước cache. Không chạy reconcile có ghi dữ liệu khi chỉ xem thống kê. |
| Refresh thống kê Android | Admin dashboard/analytics, Seller dashboard/statistics, Rescue Hub refresh API mỗi phút | Chu kỳ dự phòng 5 phút; vẫn nhận thay đổi qua realtime và có refresh khi quay vào màn. Không thay countdown/local render. |
| Realtime làm mất cache | Mỗi snapshot ban đầu của một listener mới xóa toàn bộ cache backend | Snapshot đầu chỉ tạo baseline; snapshot thay đổi tiếp theo vẫn invalidate cache và đẩy revision như trước. |
| Notification backfill | Đọc marker migration mỗi tick | Sau khi xác nhận migration hoàn thành, nhớ trong RAM đến khi restart. Không bỏ audit cursor/outbox/retry. |
| QR Hũ | Poll receipt mỗi 15 giây | 30 giây, chỉ khi màn QR đang resumed và còn chờ tiền; xóa callback cũ trước onResume. Không thay webhook/xác nhận tự động của đơn hàng SePay. |

Chu kỳ 60 → 300 giây giảm 80% số lần tick của các timer đã đổi; đây **không phải** cam kết giảm 80% tổng Reads toàn app. Luồng mua/giữ kho/thanh toán vẫn đọc và xác thực trạng thái thật trong transaction.

## Các điểm đã đối chiếu, không gọi là vòng lặp vô tận

- `/health` và `/`: không đọc Firestore.
- `/campaigns/cards`: tối đa 12 ID/request; mỗi chiến dịch đọc tối đa 3 batch; cache document 30 giây và gộp request trùng. Không quét toàn collection. Cache vẫn bị invalidated khi có thay đổi realtime thực sự.
- `/rescue-fund/public`, `/rescue-fund/ranking`: cache 30 giây; ranking giới hạn 50 PAID. Không còn polling danh sách 5 giây. Receipt cache 10 giây. Bảng xếp hạng chỉ tải khi mở/làm mới trang.
- `/seller/statistics`: đã có cache 45 giây, scope theo seller đã xác thực. Vẫn phải đọc toàn bộ lịch sử của seller để trả thống kê all-time và tương thích ngày cũ; đây là chi phí còn lại, không tự cắt lịch sử làm sai tổng. Có fan-out items của các đơn DELIVERED trong kỳ.
- Admin dashboard vẫn đọc các chiến dịch đã duyệt để tính tiến độ all-time và dùng count/sum aggregations; không diễn giải aggregations là miễn phí.
- Catalog/mine/editor Rescue Programs dùng trang giới hạn 30 hoặc 100 tài liệu. Impact lấy COMPLETED claims trong kỳ, có thể lớn; không giả vờ áp limit mà vẫn trả tổng đủ. Rescue Hub vẫn có realtime và refresh dự phòng.
- Batch expiry dùng query trạng thái + expiryDate và trang giới hạn; audit lô cũ được giới hạn và giãn theo giờ. Campaign expiry dùng ACTIVE + endDate, limit 100. Không có scan toàn products mỗi tick trong hai tác vụ này.
- Hũ: xét lô theo hạn/trạng thái, limit 50, cursor lưu trong rescueFundRuns; có nextScan và completedDay. Không phân bổ lặp toàn kho mỗi phút sau khi hoàn thành ngày.
- Media cleanup: query expiresAt, limit 25 và lease/retry. Worker thông báo: query PENDING + nextAttemptAt, giới hạn 25/20, cursor audit và marker backfill.
- `migrateSellerPaymentProfiles` và `migrateReferences`: vòng while phân trang có cursor và marker một lần; N public profile + N private profile là tuyến tính khoảng 2N tài liệu, không phải tự động N×M/cấp số nhân. Không chạy migration cũ lặp vô hạn sau khi marker đã lưu thành công.
- `BackendSnapshotWatch` gỡ subscription khi LiveData không còn observer; keepalive 45 giây vẫn đọc xác thực/quyền của phiên, không phải timer miễn phí. Không tăng lease làm chậm thu hồi quyền.
- `SnapshotLiveData` gỡ listener ở onInactive; các timer dashboard dừng ở onPause. ExpiryUiRefresh, Home freshness, countdown campaign chỉ tính lại từ dữ liệu trong RAM, không mặc định là gọi API mỗi giây.
- Listener rộng của Admin dashboard, campaign cards và rescue vẫn có chi phí snapshot ban đầu khi vào lại màn. Không giới hạn tùy tiện listener làm mất thay đổi ở dữ liệu ngoài trang đang hiển thị. Có thể cần read model/aggregation được cập nhật bằng event nếu dữ liệu tiếp tục tăng.

Tài liệu Firebase: https://firebase.google.com/docs/firestore/pricing — listener tính Reads khi tải ban đầu, cập nhật và trong các trường hợp kết nối lại; Console cũng có thể phát sinh Reads. Không thay mọi polling bằng listener rộng rồi coi Reads bằng 0.

## UI

Bục ba vị trí luôn giữ thứ tự 2–1–3 kể cả chưa đủ ba lượt; vị trí thiếu ghi “Chưa có”, không tự bịa người/số tiền. Top 1 có cúp vector vàng, avatar chữ cái, bục gradient xanh cao nhất, các bục chung đường đáy; tên giới hạn hai dòng để không đẩy lệch độ cao. Từ hạng 4 là hàng trắng gọn với số thứ hạng, avatar và thông tin đóng góp. Không dùng emoji hoặc ảnh hồ sơ riêng.

## Kiểm tra và phát hành

- `npm --prefix functions run check`.
- `npm --prefix functions run test:unit`: kiểm tra thêm inventory chỉ đọc ID liên quan, deduplicate và không đọc gì khi không có campaign; kiểm tra snapshot đầu không xóa cache nhưng thay đổi sau đó vẫn cập nhật.
- Android JVM unit test và assembleDebug; log `build/quota-ranking-android.log`.
- Log backend `build/quota-ranking-unit.log`; danh sách điểm tìm thấy `build/quota-code-inventory.txt`.

Kết quả lần chạy: backend syntax qua; **183/183 backend**, **9/9 Worker**, **251/251 Android JVM** qua; **assembleDebug thành công**. Không chạy lại suite Firestore Emulator trong lượt này vì không sửa Rules/index hoặc logic giao dịch thanh toán; các test mới là mock offline. Không xem kết quả này là phép đo mức tiết kiệm Reads thực tế.

Cần deploy code backend Render và cài APK mới. Không có index/schema mới trong lượt này. Index ranking từ lượt trước vẫn phải sẵn sàng. Chưa xác minh trực quan trên thiết bị hoặc đo Reads production sau deploy.

Sau deploy, so sánh Usage cùng số lượt mở màn, thời gian backend chạy và cùng số thiết bị; tránh mở Data viewer lâu khi đo. Kiểm tra Query Insights nếu có để xác định collection/query thực tế. Dòng log `[maintenance] intervalMs=300000` xác nhận backend dùng chu kỳ mới; biến môi trường cũ đặt 60000 sẽ giữ chu kỳ một phút.
