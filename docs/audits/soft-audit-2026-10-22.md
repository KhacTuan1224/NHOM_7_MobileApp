# Audit mềm toàn app RescueFarm — 22/10/2026

## Cập nhật sau triển khai

AUD-01/02/03/04/05/07/08 đã được triển khai theo quyết định mới. AUD-06 **WON'T FIX**: giữ nguyên QR/webhook SePay tự động và tiếp tục ẩn xác nhận chuyển khoản thủ công. Xem [hướng dẫn và kết quả sau triển khai](audit-fixes-setup-2026-10-22.md).

Các số đo và mô tả phía dưới là **snapshot audit trước khi sửa**, không phải kết quả kiểm thử bản đã sửa.

## Kết luận (snapshot trước triển khai)

Các bộ kiểm thử logic hiện có của Android và backend đều qua. APK debug build được. Tuy nhiên, **chưa thể kết luận mọi chức năng hoàn thiện**: Android Lint bị chặn bởi 2 lỗi; suite Firestore có 3 test đỏ; còn các khoảng trống ở xử lý đơn ngoại lệ và độ tin cậy khi gộp giỏ Guest. Một bộ lọc tồn kho cần chốt lại ý nghĩa nghiệp vụ.

Không ghi hoặc upload lên Firebase thật, không chạy seed/cleanup/deploy production, không gọi webhook thanh toán thật, không gửi email/push thật. Firestore và Auth dùng localhost cùng project `demo-*`. Không khởi chạy APK hoặc instrumentation vào project Firebase của bạn. Source app/backend/Rules không được sửa trong lượt audit này; chỉ thêm tài liệu và probe test.

Đây là audit cấu trúc toàn repo và các luồng chức năng chính, kèm kiểm thử tự động hiện có và một số probe bổ sung. Không phải chứng nhận đã thao tác từng button trên máy thật, hoặc phủ mọi nhánh code. Test logic đúng là điều kiện cần; cấu hình, quyền, mạng, vòng đời Android và tích hợp bên thứ ba vẫn có thể làm app lỗi.

## Kết quả đã chạy

| Bộ kiểm tra | Kết quả thực tế | Ý nghĩa |
|---|---:|---|
| Android JVM unit test | 246/246, 73 class | Các tình huống được viết trong test qua; không phải 246 chức năng độc lập |
| `:app:assembleDebug` | Thành công | Java/resources/đóng gói debug hợp lệ |
| `:app:lintDebug` | 2 error, 620 warning; task thất bại | Có vấn đề chặn quality gate, xem AUD-01 |
| Backend `npm run check` | Thành công | Kiểm tra cú pháp các module trong script |
| Backend unit test | 150/150 | Chính sách, giá, kho, lifecycle, worker, notification với harness/mock |
| Cloudinary Worker | 8/8 | Hợp đồng upload/quyền trong môi trường test, không upload thật |
| Firestore Emulator + API integration | 113/116 | Ba test đỏ cần phân loại, xem AUD-02/03 |
| Auth Emulator reset password | 5/5 | Tài khoản giả, mã reset giả, không gửi email thật |
| Script lập kế hoạch cleanup/retirement | 9/9 | Chỉ plan test; không thực thi cleanup |
| Probe Firestore bổ sung | 3/3 | Payload đúng của app ẩn/khôi phục comment được; xác nhận mismatch của test |
| Probe Java Discovery | Chạy được, tái hiện bộ lọc | Hai lô 6 + 6, lọc tối thiểu 10 → 0 kết quả |
| Navigation | 60 destination có Java Fragment | Không có destination trỏ tới class bị thiếu; chưa chứng minh mọi điều hướng/argument runtime |

Tổng các test case tự động phía trên: **537 lượt, 534 qua và 3 thất bại**. Probe Java và navigation được báo riêng. Không có số đo line/branch coverage; không diễn giải các con số thành phần trăm hoàn thiện app.

Kết quả máy đọc được: [soft-audit-2026-10-22-results.json](soft-audit-2026-10-22-results.json). JSON còn có danh sách navigation và chi tiết lỗi Lint.

## Backlog ưu tiên, có phân biệt bằng chứng

### AUD-01 — P1 quality gate: Android Lint đang đỏ

- **Đã tái hiện** qua Gradle. `RescueMarketFragment.java:77` và `RescueMarketUi.java:19` dùng `setOrientation(1)`; Lint báo `WrongConstant`.
- Số 1 đúng bằng `LinearLayout.VERTICAL`, vì vậy đây **không phải bằng chứng layout crash**. Nhưng pipeline nào chạy Lint sẽ bị chặn.
- Sửa gọn bằng constant có tên `LinearLayout.VERTICAL`, chạy lại Lint. Không tắt toàn bộ Lint để né lỗi.
- 620 warning chủ yếu gồm HardcodedText 202, SetTextI18n 143, UnusedResources 102, Overdraw 58, Autofill 26, TextFields 18. Chúng không đồng nghĩa 620 lỗi chức năng. Ưu tiên input/accessibility/overdraw có tác động thực tế, rồi dọn phần còn lại.

### AUD-02 — P1 test suite: hai test comment đang dùng payload cũ

- **Đã tái hiện** hai test đỏ: `Admin can moderate Campaign comments...` và `Admin can hide the exact Campaign comment...` trong `tests/firestore/checkout.test.mjs`.
- Hai test ẩn comment mà không gửi `moderationPreviousStatus`. Rules hiện yêu cầu lưu trạng thái trước khi ẩn (`firestore.rules:1039–1050`).
- **Không kết luận chức năng ẩn comment hiện tại bị hỏng.** `FirebaseAdminRepository.java:969–970` có ghi trường này. Probe bổ sung dùng đúng dạng dữ liệu app gửi: ẩn comment + ghi audit cùng batch, sau đó khôi phục → đều thành công.
- Cách sửa: cập nhật fixture/payload của hai test để khớp contract hiện tại; giữ một test phủ định xác nhận payload thiếu trường bị từ chối. Không nới Rules bỏ lịch sử trạng thái chỉ để làm test cũ xanh.
- Probe: `tests/firestore/audit-probes.test.mjs`.

### AUD-03 — P2 chính sách Admin: tắt danh mục không cần lý do

- **Đã tái hiện** test thứ ba thất bại: `Admin edits legacy category metadata...`. Test chờ thao tác `active: false` với lý do rỗng bị từ chối, nhưng Rules cho phép.
- Probe độc lập xác nhận Admin thực sự có thể tắt danh mục với `moderationReason: ''`.
- `validAdminCategory` và `match /categories` tại `firestore.rules:666–701` không có ràng buộc tối thiểu 3 ký tự khi chuyển đang hoạt động → ngừng hoạt động. `FirebaseAdminRepository.java:448–467` cũng không chặn trường hợp này.
- Đây là **mismatch chính sách/test**, không phải lỗi không lưu được danh mục. Nếu yêu cầu audit reason là chủ đích, thêm điều kiện ở UI/repository/Rules và giữ assertion hiện có; nếu không yêu cầu, sửa test và tài liệu nghiệp vụ. Không tự chọn thay đổi chính sách trong lượt audit.

### AUD-04 — P1 khoảng trống nghiệp vụ: đơn trả tiền rồi nhưng lô hết hạn chưa có đường xử lý cuối

- **Xác minh bằng code và các regression expiry đã qua**: `functions/campaign-lifecycle.js:268` chặn hủy đơn PAID, đồng thời `:280–281` chặn chuyển tiếp/nhận hàng từ lô hết hạn.
- `functions/batch-expiry.js:41–60` giữ tiền/kho, đánh dấu `expiryReviewRequired`, gửi thông báo. Đây là biện pháp bảo toàn dữ liệu, **chưa phải workflow đối soát/hoàn tiền hoàn chỉnh**.
- Không tìm thấy API/UI refund, hủy có hoàn tiền hoặc bằng chứng bàn giao thực tế trước hạn. Vì vậy đơn có thể ở trạng thái chờ đối soát lâu dài; hoàn tiền ngoài app cũng chưa có luồng chuẩn ghi nhận để kết thúc đơn/kho.
- Ưu tiên thiết kế quy trình Admin/Seller: mở danh sách cần đối soát, ghi bằng chứng, ghi kết quả hoàn tiền thực tế, chốt tồn đúng một lần và audit. Không đặt `REFUNDED` hoặc trả kho chỉ dựa vào một nút mà chưa có căn cứ.
- Việc nhận hàng thực tế trước hạn nhưng khách xác nhận sau hạn cũng chưa có bằng chứng độc lập để hệ thống xử lý tự động.

### AUD-05 — P2 UI: trạng thái cần đối soát hết hạn chưa lên chi tiết đơn

- **Xác minh bằng code**, chưa thao tác trên thiết bị: backend ghi `expiryReviewRequired` và `cancellationReason`; không tìm thấy mapping/sử dụng hai trường này trong source Java.
- `OrderDetailFragment.java:227–276` tính các nút theo status/payment thông thường. Người dùng có thể vẫn thấy nút nhận/chuyển tiếp, bấm rồi mới nhận lỗi backend về lô hết hạn; lý do hủy tự động cũng không được trình bày riêng trong chi tiết đơn.
- Không phải lỗ hổng cho mua hàng hết hạn: backend vẫn chặn. Nên bổ sung mapping/cache, nhãn cảnh báo, lý do hủy và CTA đối soát; chỉ ẩn/khóa nút sau khi có trạng thái đáng tin cậy.

### AUD-06 — WON'T FIX: giữ nguyên SePay tự động

Theo quyết định của người dùng: hệ thống chuẩn hóa chuyển khoản QR qua webhook SePay tự động. UI cố ý ẩn nút xác nhận tay, không thêm lại. Backend giữ hàm `confirmBankTransfer` để kiểm thử/tương thích ngược; không thay đổi luồng tự khớp tiền và tự chuyển PAID/CONFIRMED.

### AUD-07 — P2 ngữ nghĩa bộ lọc: tồn tối thiểu đang xét từng lô

- **Đã tái hiện bằng compiled code thật**, hoàn toàn offline: sản phẩm có hai lô còn hạn 6 kg + 6 kg, tổng 12 kg; mặc định có 1 kết quả; nhập “Tồn kho tối thiểu” 10 → 0 kết quả.
- `DiscoveryEngine.java:114` loại từng lô nhỏ hơn ngưỡng trước khi kiểm tra tổng tại `:60–61`. Trong khi màn chi tiết lại hiển thị tổng tồn sản phẩm.
- Cần chốt UX: nếu lọc tổng tồn theo sản phẩm, bỏ điều kiện per-batch đó nhưng vẫn chọn một lô hợp lệ; nếu cố ý tìm một lô đủ số lượng, đổi nhãn thành “Tồn tối thiểu mỗi lô”. Còn việc tách một yêu cầu mua qua nhiều lô là quyết định khác, không tự thay batchId đơn cũ.
- Tái hiện: `tests/audit/DiscoveryAuditProbe.java`; output `build/full-app-audit-discovery-probe.log`.

### AUD-08 — P1 rủi ro độ tin cậy: gộp giỏ Guest chưa có idempotency xuyên local/cloud

- **Phát hiện qua đọc code, chưa tái hiện cửa sổ crash trên Android**; không gắn nhãn lỗi đã xảy ra với người dùng.
- `CartMigrationService.java:56–60` merge lên Firestore thành công rồi mới xóa Room ở tác vụ tiếp theo. `FirebaseCartRepository.java:56–66` gộp bằng cách cộng số lượng, không thấy token/lần migration đã thực hiện.
- Nếu app bị dừng sau khi cloud commit nhưng trước khi local clear, lần retry có thể cộng lần nữa. `CartViewModelFactory.java:18` còn khởi tạo migration khi mở giỏ; các instance không có coordinator chung.
- Test CartMigrationService hiện không bao phủ transaction Firestore + crash window + retry. Cần regression cho trường hợp này trước khi sửa; giải pháp nên là marker/idempotency key được commit cùng giỏ trên backend/Firestore, rồi mới clear local. Khóa trong RAM đơn thuần không đủ sau process death.

## Ma trận chức năng theo vai trò

“Qua test” dưới đây chỉ áp dụng các tình huống được test, không khẳng định toàn bộ UI hay cấu hình live.

| Vai trò/phần | Nhóm chức năng đã rà | Bằng chứng và giới hạn |
|---|---|---|
| Chung | Email đăng nhập, đăng ký Customer/Seller, xác thực dữ liệu | Unit validator/ViewModel + Rules registration qua; chưa đăng nhập Firebase thật |
| Chung | Google Sign-In | Rà code callback/profile; chưa xác minh OAuth, SHA, consent/account trên thiết bị |
| Chung | Quên/đặt lại mật khẩu | Unit + 5 Auth Emulator qua; giao email thật chưa kiểm |
| Chung | Phân vai, navigation, truy cập chéo user | Domain/policy + Rules và 60 destination qua; không phải mọi đường back/deep link đã thao tác |
| Guest | Home, danh mục, tìm kiếm, lọc, gần hết hạn | Home/Discovery/Category tests qua; AUD-07 cần chốt ngữ nghĩa |
| Guest | GPS cố định/di động/gần nhất | Khoảng cách, địa điểm stale và query logic qua; GPS/permission thực tế chưa kiểm |
| Guest | Giỏ local, nhóm Seller, giá/khuyến mãi/mua nhiều | Mapper/group/revalidation/pricing + backend qua; Room process-death chưa chạy instrumentation |
| Guest | Checkout một/nhiều Seller, phí giao, COD/nhận tại điểm | Firestore/API integration qua, có cạnh tranh kho/idempotency trong suite |
| Guest | Chuyển khoản SePay, tra cứu mã đơn + số điện thoại/token | Emulator webhook giả, grant/token isolation và receipt qua; tiền thật chưa chuyển |
| Guest | Hồ sơ khách, recent orders, nút GPS/hỗ trợ | Rà layout/handlers; lưu preferences và mở app điện thoại/email chưa thao tác |
| Guest | Xem Flash Sale/Hộp bí ẩn | Catalog/quyền backend qua; Guest chỉ xem, giữ suất cần Customer theo thiết kế |
| Customer | Hồ sơ, địa chỉ mặc định, ảnh/QR công khai | Validator/policy/Rules qua; upload media thật chưa kiểm |
| Customer | Yêu thích, thông báo, kho khuyến mãi | Unit + Rules/API scope qua; push thật/badge trên Android chưa kiểm |
| Customer | Giỏ đăng nhập và chuyển từ Guest | Cart/checkout test qua; rủi ro AUD-08 chưa bao phủ |
| Customer | Theo dõi đơn, xác nhận nhận hàng | Order domain/backend ownership/replay qua; ngoại lệ AUD-04/05/06 |
| Customer | Review có ảnh sau mua, bình luận, reaction, report | Eligibility/Rules/summary/API qua; không kiểm nội dung/ảnh production |
| Customer | Flash/Hộp mù: giữ, rút, nhận, không đặt trùng | Rescue Programs integration qua; hiệu ứng mở quà chưa kiểm trực quan |
| Seller | Đăng ký chờ duyệt, hồ sơ công khai/ngân hàng riêng | Rules phân quyền trước/sau duyệt qua; không cấu hình SePay thật |
| Seller | Sản phẩm, ẩn/hiện, batch, phiên bản kho | Domain/Rules/expiry/archival/optimistic checks qua |
| Seller | Khuyến mãi sản phẩm + giảm theo Seller/nền tảng | Pricing, rounding/allocation và snapshot qua |
| Seller | Chiến dịch, media, gửi duyệt, dừng, phân bổ lô | Lifecycle/projection/media/harness/Emulator qua; CDN upload thật chưa kiểm |
| Seller | Điểm cứu trợ di động | Location/time/model logic qua; quyền nền, vị trí thật và định tuyến ngoài app chưa kiểm |
| Seller | Đơn và shipment, tiền mặt/chuyển khoản | Server transitions/quyền/stock qua; provider tracking thực tế không nằm trong tích hợp hiện tại |
| Seller | Tạo Flash Sale/Hộp mù từ tồn thật | Policy, hai giờ, múi giờ, batch picker nhiều trang, concurrency qua |
| Seller | Suất của cửa hàng, chuẩn bị, tác động đã xác nhận | Claims/impact/unit aggregation qua; chart render/performance chưa kiểm |
| Seller | Dashboard/thống kê | Aggregator/analytics unit qua; route server dashboard không có chứng cứ đã test trọn payload UI→HTTP→Firestore ở lượt này |
| Admin | Dashboard, xem tài nguyên, user/Seller approval | Policy/model + các Rules qua; dashboard live/đồ thị chưa kiểm |
| Admin | Kiểm duyệt sản phẩm, chiến dịch, review, xử lý report | Rules/lifecycle qua trong phạm vi fixture; điều kiện chéo dữ liệu production chưa kiểm |
| Admin | Comment/audit đúng parent path | Hai test cũ đỏ, probe đúng payload app qua; xem AUD-02 |
| Admin | Danh mục/banner, bật/tắt, link/ảnh | Các hợp đồng chính qua; category reason mismatch AUD-03 |
| Admin | Shipping settings, phân quyền cấu hình | Rules/config tests qua; không thay cấu hình thật |
| Admin | Xem đơn, đối soát, hoàn tiền | Xem/cảnh báo có code; workflow kết thúc đối soát/hoàn tiền còn thiếu AUD-04 |
| Backend | SePay, replay, thông báo/outbox, token, Cloudinary signing | Unit/mock + Emulator qua; provider production, secrets/network/retry dài hạn chưa kiểm |
| Offline | Room cache, migration, outbox/sync | JVM contracts và offline repository qua; không chạy Android database instrumentation |

Các phương thức BULK/DONATION/GROUP_BUY/SUBSCRIPTION đã bị loại khỏi phạm vi sản phẩm; test chặn type cũ qua. Không tính các phương thức đã yêu cầu xóa là tính năng còn thiếu. Chat, livestream, Universal Link web, bản đồ điều hướng nội bộ và hoàn tiền tự động không được suy ra là đã triển khai.

## Thứ tự sửa đề xuất

1. AUD-01 và AUD-02: đưa Lint và test đúng contract về xanh để lần sửa sau có tín hiệu đáng tin cậy.
2. AUD-04/05: làm rõ đường xử lý đơn ngoại lệ và hiển thị trạng thái đối soát trong app; chốt trách nhiệm tiền/kho.
3. Viết test crash/retry AUD-08 rồi sửa migration idempotency.
4. Chốt chính sách AUD-03 và ngữ nghĩa AUD-07 trước khi đổi logic.
5. Xác định còn đơn legacy để quyết định AUD-06, sau đó kiểm thử thiết bị trên môi trường staging/emulator đã tách production.

## Chạy lại an toàn

Từ root repo, dùng Java Android Studio và `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=D:\NHOM_7_MobileApp\build\no-unix-sockets` nếu Windows gặp lỗi Unix socket.

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --no-daemon --console=plain
npm --prefix functions run check
npm --prefix functions run test:unit
node --test scripts/mock-cleanup-plan.test.cjs scripts/post-retirement-plan.test.cjs
python scripts/check_navigation.py
```

Từ `tests/firestore`, chỉ dùng emulator với project demo:

```powershell
npm test
.\node_modules\.bin\firebase.cmd emulators:exec --project demo-rescuefarm --only firestore --config ../../firebase.test.json "node --test --test-concurrency=1 audit-probes.test.mjs"
.\node_modules\.bin\firebase.cmd emulators:exec --project demo-rescuefarm-reset --only auth --config ../../firebase.auth-test.json "node --test ../auth/password-reset.test.mjs"
```

Probe Java sau khi app đã compile, từ root:

```powershell
& 'C:\Program Files\Android\Android Studio\jbr\bin\java.exe' --class-path app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes tests/audit/DiscoveryAuditProbe.java
```

Log lần chạy: `build/full-app-audit-android.log`, `full-app-audit-backend-check.log`, `full-app-audit-backend-unit.log`, `full-app-audit-firestore.log`, `full-app-audit-auth.log`, `full-app-audit-scripts.log`, `full-app-audit-firestore-probes.log`, `full-app-audit-discovery-probe.log`. Build logs có thể bị xóa khi chạy clean; báo cáo JSON/Markdown lưu kết quả snapshot lần này.
