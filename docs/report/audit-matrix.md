# Ma trận bằng chứng RescueFarm

Audit 24/10/2026; HEAD nền `28adb48bb2e5e4ac8230379a1152f64a52251f5e`. APK và working tree khác HEAD bởi công cụ demo chưa commit; xem `evidence/audit-results-current.json`, `source/` và diff. Các log audit cũ chỉ là snapshot, không cộng test với lượt này.

| Tiêu chí | Hành vi/code cụ thể | File/class/collection | Test/ảnh/commit mới | Kết luận | Giới hạn |
|---|---|---|---|---|---|
| TMĐT | Product–Batch–Cart–Checkout–Order | domain/model; functions/commerce.js | HEAD; 173 test API/Rules | Có code, đã qua test | Không tự chấm điểm môn học |
| Bốn vai trò | Hồ sơ ACTIVE, Seller APPROVED; điều hướng theo role | MainActivity; ActorUiPolicy; server.auth | Ảnh Guest/Customer/Seller/Admin | Đã thao tác trên emulator | Mở destination trực tiếp, không mọi đường bấm |
| Rules và quyền | Client role/owner; server kiểm token và owner | firestore.rules; functions/server.js | firestore-head-retry.log: 173/173 | Đã qua test | Admin SDK không dựa client Rules; không test production |
| Product khác Batch | expiry và inventoryVersion thuộc lô | ProductBatch.isSellable; reserve/release | JVM 253 test tổng; module tests | Có code, đã qua test | Không suy một test thành toàn chức năng |
| Hàng hết hạn | sellable 0 nhưng còn tồn vật lý, giữ lịch sử | ProductBatch; batch-expiry.js | API và JVM | Có code, đã qua test | Chưa giao hàng vật lý |
| Khám phá | Text, giá, tỉnh, stock, hạn, GPS/radius | DiscoveryEngine.search | JVM; guest-discovery.png | Có code, đã qua test, đã thao tác trên emulator | Không chứng minh GPS thực địa |
| Quantity và giá | Bộ chọn −/input/+; rescuePrice | ProductDetailFragment, layout thường/w840dp | 9 layout tests; guest-product | Đã qua test, đã thao tác trên emulator | Không sửa UI branding hoặc phục hồi ưu đãi |
| Giỏ Guest | Room theo phiên, click thêm hàng thật | GuestCartRepository/Dao | guest-product → guest-cart | Đã thao tác trên emulator | Dữ liệu demo; chưa every cart action |
| Giỏ đăng nhập | customerCarts UID; chuyển giỏ chống lặp | FirebaseCartRepository; cart-migration | Backend tests; customer-cart | Có code, đã qua test, đã thao tác trên emulator | Không gán phần trăm hoàn thiện |
| Checkout | Quote/revalidate, transaction giữ kho, request fingerprint | commerce.js; checkoutReceipts | API tests; customer-checkout | Có code, đã qua test; đã mở form emulator | Chưa checkout–QR–delivery–review UI liên tục |
| SePay đơn | Xác thực, mã, amount, account và bank lock | sepay.js; bankTransferReferences | Unit/API emulator | Đã qua test | Chưa xác minh tích hợp ngoài, không tiền thật |
| Đơn/giao nhận/đối soát | Payment và order status riêng; snapshot | FirebaseOrderRepository; order-reconciliation | API tests; customer-orders | Có code, đã qua test; màn danh sách đã mở | Fixture danh sách có thể rỗng; không chuyến thực |
| Review/comment/reaction/report | Review đủ điều kiện; tương tác Campaign | FirebaseEngagementRepository; Rules | API/Rules/JVM | Có code, đã qua test theo ca | Chưa chụp thao tác từng luồng |
| Campaign fixed/mobile | batchTargets, counters, vị trí | FirebaseCampaignRepository; campaign-inventory | API/JVM; seller-campaigns | Có code, đã qua test; mở màn rỗng | Chưa Campaign fixture đầy đủ trên UI |
| Flash/Mystery Box | Offer/claim giữ Batch thật; tiền tại điểm | ui/rescue; rescue-programs.js | 173 suite có module rescue | Có code, đã qua test | Chưa offer/claim UI fixture |
| Hũ tiền vào | donate PENDING → webhook PAID; mã khác đơn | rescue-fund.receive; sepayBankTransactions | Hũ trong 173 suite; fund-demo-setup JSON | Đã qua test | Webhook giả local, không SePay live |
| Hũ chọn lô | >24h+1m và ≤48h; Campaign end tương tự | allocation, reconcile | Test biên, buổi tối, campaign ngắn | Đã qua test | Scheduler có thể trễ; không hứa đúng giây |
| Hũ deadline | allocatedAt+24h; version2; không đổi chuyến cũ | allocation/action; RescueFundTime | Test hồi quy; ảnh accepted; 2 JVM timezone | Đã qua test, đã thao tác trên emulator | Chỉ màn Hũ có định dạng giờ VN này |
| Hũ chốt và ghi chi | DELIVER → VERIFY chốt kho → PAY ghi chi | action; ledger; payoutReferences | fund-demo-*; ảnh trạng thái cùng job | Đã qua test, đã thao tác trên emulator | HEAD ảnh stub; không upload/chuyển tiền/giao thật |
| Hũ quá hạn | RESERVED/ACCEPTED → CANCELLED; RELEASE một lần | reconcile/action | suite; expired-verify/pay JSON; fund-cancelled-admin-ledger.png | Đã qua test, đã thao tác trên emulator | Giải phóng tiền không hoàn ngân hàng; ledger UI là dữ liệu demo |
| Hũ số dư | available+reserved+spent = donated+adjustments | rescueFunds; ledger | Harness assert mỗi phase; cuối 36k+0+36k=72k | Đã qua test | Không phải số dư tài khoản dùng chung |
| Room thực | DB v11, 9 entity, SQL DAO/mapper | RescueFarmDatabase; dao; mapper | OfflineRoomEvidenceTest qua | Đã qua test | SDK cache là cơ chế khác |
| Offline UI Room | New process; SDK disk off và network disabled | ReportDemoApplication; screenshot test | offline prepare/restart qua; catalog-room-offline-restart | Đã thao tác trên emulator | Chỉ catalog chữ; ảnh disk cache chưa xác minh |
| Scope riêng | owner check + DAO theo ownerId/sellerId | SnapshotLiveData; OrderCacheDao | Code và test; no cross-user UI proof | Có code, đã qua test theo ca | Chưa UI đầy đủ đổi tài khoản/cache đơn |
| TTL/retry | 30m catalog,10m campaign/order,1h banner;7d hard expiry | CacheSyncPolicy; ReadCacheSyncWorker | JVM policy; code | Có code, đã qua test theo ca | Không mọi DAO tự lọc tuổi cache; periodic không đúng giây |
| Online bắt buộc | CHECKOUT/PAYMENT/APPROVAL/INVENTORY/ORDER_TRANSITION | CriticalActionPolicy | JVM/backend | Có code, đã qua test | Không bán với stock stale offline |
| Responsive | Không khóa orientation; qualifier + ID parity | Manifest; Responsive*; layouts | 9/9 layout; 1/1 rotation; ảnh login pair | Đã qua test, đã thao tác trên emulator | Chưa toàn bộ form/keyboard/màn ngắn |
| Google API | Credential Manager → Firebase Auth | GoogleCredentialService | Code/build; form login | Có code | Chưa xác minh tích hợp ngoài OAuth |
| Media | Worker signing và protected assets | CloudinaryMediaStorage; cloudinary-worker | 9/9 Worker tests | Đã qua test | Chưa upload provider thật |
| Vị trí | Fused/Geocoder/Haversine; geo intent | AndroidLocationProvider; CampaignLocationService | Code/JVM/fixture tọa độ | Có code | Không Google Maps SDK, chưa thực địa |
| FCM | Outbox và worker | notification-outbox; FcmDeviceSyncWorker | Unit; push bị chặn trong launcher | Có code, đã qua test theo ca | Chưa push tới thiết bị thật |
| Git | Merge/PR/branch thật, nhiều alias | git log/shortlog | git-evidence.json, SHA197/196/195/192 | Có bằng chứng | Không đo tỷ lệ đóng góp bằng commit count |
| DOCX/PDF | Theo khung PDF mẫu, Chương1 giữ nguyên | source/build_report.py; report-content.md | Render/QA theo handoff | Được kiểm tra ở đầu ra cuối | Không tự chứng nhận mọi quy định UEH |
| Nhóm và tương đồng | Tên/GV/học phần từ mẫu; MSSV/tỷ lệ thiếu | Mẫu PDF; README | Nhóm cần xác nhận | Thiếu/chưa hoàn thiện | Chưa kiểm tra UEH, không cam kết <20% |

## Sai lệch tài liệu cũ đã nhận diện

- Audit cũ HEAD bf648f2 không bao gồm policy mới tại fca8212/28adb48. Dùng code và lượt test mới.
- Tài liệu Hũ cũ mô tả polling 15 giây; RescueFundFragment HEAD hiện dùng 30 giây. Public cache 30 giây và receipt cache 10 giây là khác polling.
- firestore.schema.json còn promotions/customerPromotionWallets lịch sử; không mô tả thành tính năng hiện hành.
- Hard expiry 7 ngày có trong policy, nhưng không phải mọi câu SQL DAO đều lọc theo tuổi cache.
- Mẫu Chương1 nói tới thiết bị thật; audit lượt này chỉ xác minh emulator. Báo cáo giữ nguyên nội dung nhóm và thêm đoạn đối chiếu phạm vi sau Chương1.
