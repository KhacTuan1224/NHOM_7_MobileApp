# RescueFarm — Các phần chưa triển khai hoàn chỉnh

Ngày rà soát: 17/10/2026. Mốc source: `b41dd07` và các file đang có trong workspace khi rà soát.

## Phạm vi và cách đọc

Đối chiếu `final.md` với cấu trúc 293 file Java chính, 54 layout, navigation graph, repository/service/domain, backend trong `functions/`, Firestore Rules/indexes, Cloudinary Worker và 54 file Java test hiện có. Rà soát các đường đi theo bốn actor và lần ngược UI → ViewModel → repository → Rules/backend. Số file là phạm vi source được thống kê, không phải số chức năng hay tỷ lệ nghiệm thu.

Đây là audit source và cấu hình trong repo. Chưa xác nhận trạng thái triển khai trên Firebase/Render/Cloudflare, chưa chạy nghiệm thu toàn bộ màn hình trên thiết bị. Chỉ ghi các khoảng trống tìm thấy; không liệt kê lại chức năng đã có đủ phần triển khai liên quan trong lượt rà soát này. Việc một mục không xuất hiện không đồng nghĩa được chứng nhận working trên môi trường thật.

Áp dụng quyết định cuối tài liệu: **Campaign là nội dung công khai duy nhất**. Post cũ được giữ để tương thích, không coi việc không có Feed Post mới là thiếu. OTP đã được người dùng yêu cầu bỏ, không nằm trong danh sách cần bổ sung. Thứ tự Home/emoji theo yêu cầu UI mới cũng không bị chấm thiếu vì khác sơ đồ Home cũ.

Mức ưu tiên: **P0** chặn một màn hình; **P1** ảnh hưởng nghiệp vụ, dữ liệu, kiểm duyệt hoặc vận hành; **P2** chức năng/UX còn thiếu nhưng không nhất thiết chặn đặt hàng.

## Danh sách cần xử lý

| Mã | Mức | Actor chịu ảnh hưởng | Phần chưa hoàn chỉnh |
|---|---|---|---|
| A01 | P0 | Seller | Đích điều hướng Thống kê không có Fragment implementation |
| A02 | P1 | Seller, Customer, Guest, Admin | Vòng đời Campaign và gỡ liên kết batch chưa khép kín |
| A03 | P1 | Admin, Seller, Customer, Guest | Approve Campaign chưa kiểm tra lại nguồn cung/thời gian |
| A04 | P1 | Customer, Guest, Seller | Chưa quản lý phần phân bổ còn lại riêng từng batch trong Campaign |
| A05 | P1 | Cả bốn | Cộng trực tiếp số lượng khác đơn vị rồi hiển thị kg |
| A06 | P1 | Admin, Seller, Customer | Thông báo còn gửi kiểu best effort, chưa bảo đảm retry |
| A07 | P1 | Admin, Customer | Deep link thông báo kiểm duyệt thiếu loại tài nguyên/ngữ cảnh |
| A08 | P2 | Seller, Customer, Admin | Thiếu thông báo kết quả duyệt/xử lý báo cáo |
| A09 | P1 | Seller, Admin | Thống kê Seller phụ thuộc trang đã tải; Admin phụ thuộc trạng thái/urgency lưu cũ |
| A10 | P2 | Customer, Guest, Seller xem công khai | Filter/sort chưa áp dụng đúng cho kết quả Campaign |
| A11 | P1 | Admin, Customer | Xử lý report bình luận chưa hoàn chỉnh sau khi chuyển sang Campaign |
| A12 | P2 | Customer, Guest, Seller xem công khai | Ảnh Review đã lưu nhưng chưa hiển thị cho người đọc |
| A13 | P2 | Customer | Thiếu lối báo cáo theo ngữ cảnh Review/Comment/User |
| A14 | P2 | Customer doanh nghiệp | Hồ sơ doanh nghiệp chưa có giao diện xem/sửa đầy đủ |
| A15 | P2 | Customer, Guest, Seller | Detail thiếu countdown và cập nhật trạng thái vị trí theo thời gian |
| A16 | P2 | Customer, Guest | Home chưa có nhóm ưu đãi mua nhiều đúng Promotion tiers |
| A17 | P1 | Seller, Admin, Customer | Upload ảnh Campaign chưa gắn chặt với vòng đời kiểm duyệt |

## A01 — Thống kê Seller có route nhưng không có màn hình

**Căn cứ:** U33, Function 8.4 và mục 35 về kết quả kinh doanh/giải cứu của Seller.

**Bằng chứng:** [main_navigation.xml](D:/NHOM_7_MobileApp/app/src/main/res/navigation/main_navigation.xml:272) có action tới `sellerStatisticsFragment`; dòng 275 khai báo `com.rescuefarm.ui.seller.SellerStatisticsFragment`. Trong source không có class này. `SellerDashboardFragment.navigate()` vẫn gọi action khi chọn Thống kê. Đây là thiếu implementation thật, không chỉ thiếu dữ liệu biểu đồ.

**Tình huống:** Seller mở drawer → Thống kê. Navigation sẽ không khởi tạo được class đích; cần chạy APK để xác nhận stacktrace cụ thể, nhưng thiếu class đã xác định được từ source.

**Hướng sửa:** Tạo Fragment/layout và ViewModel thống kê thực sự; tái sử dụng snapshot phù hợp sau khi xử lý A09. Có chọn khoảng thời gian, doanh thu, đơn hàng, khối lượng giải cứu, chiến dịch hoàn thành, top sản phẩm/lý do; biểu đồ phải dùng dữ liệu đúng ý nghĩa, kèm loading/error/empty. Nếu chỉ muốn dùng thống kê trong dashboard, bỏ route và nút Thống kê riêng một cách nhất quán.

**Nghiệm thu:** Điều hướng và Back không crash; dữ liệu thay đổi đúng theo kỳ; không để route trỏ tới class vắng mặt. Thêm kiểm tra tự động tất cả class trong navigation graph tồn tại — lượt audit này phát hiện đúng một đích Fragment thiếu.

## A02 — Campaign kết thúc nhưng batch vẫn bị giữ liên kết

**Căn cứ:** §1.1, §15–17; Function 1.3, 3.2, 7 và 8.

**Bằng chứng:** [FirebaseCampaignRepository.java](D:/NHOM_7_MobileApp/app/src/main/java/com/rescuefarm/data/remote/firebase/FirebaseCampaignRepository.java:270) gán `activeCampaignId` ngay khi gửi chờ duyệt. [commerce.js](D:/NHOM_7_MobileApp/functions/commerce.js:97) thấy ID này thì yêu cầu Campaign ACTIVE và còn thời gian. [FirebaseOrderRepository.java](D:/NHOM_7_MobileApp/app/src/main/java/com/rescuefarm/data/remote/firebase/FirebaseOrderRepository.java:454) chuyển COMPLETED nhưng không gỡ ID khỏi batch. Nhánh Admin gỡ batch hiện gắn với STOPPED; chưa có nhánh tương đương cho REJECTED/COMPLETED/EXPIRED, và chưa tìm thấy tác vụ cập nhật Campaign hết hạn trong backend.

**Tình huống:** Batch còn 100 kg, mục tiêu Campaign chỉ 20 kg. Giao đủ 20 kg → Campaign COMPLETED nhưng 80 kg còn lại vẫn trỏ tới Campaign đó. Quote đơn mới bị từ chối vì Campaign không ACTIVE; tạo Campaign khác gặp `BATCH_IN_USE`. Campaign bị từ chối hoặc hết thời gian cũng có thể gây mắc liên kết tới khi có thao tác khác sửa lại dữ liệu. Trong thời gian chờ duyệt, batch đã có `activeCampaignId` nên cũng có thể bị chặn bán ngoài Campaign.

**Hướng sửa:** Tách khái niệm “đã chọn batch cho bản gửi duyệt” với “chiến dịch đang hoạt động”. Có quy trình idempotent chuyển trạng thái và gỡ liên kết: chỉ gỡ khi ID hiện tại còn đúng Campaign đang đóng; không xóa reservation của đơn đã tồn tại. Dùng backend cập nhật EXPIRED theo thời gian, đồng thời xử lý lười khi quote gặp Campaign đã hết hạn. Đơn đang giữ chỗ vẫn phải hoàn tất/hủy đúng Campaign lịch sử.

**Nghiệm thu:** Sau REJECTED/COMPLETED/EXPIRED/STOPPED, batch còn hàng có thể bán hoặc tham gia Campaign hợp lệ mới; hoàn tất/hủy đơn cũ không phá tồn kho hay liên kết mới. Kiểm thử cả trường hợp Campaign đạt mục tiêu trước khi batch hết hàng.

## A03 — Duyệt Campaign chưa tái kiểm tra tính hợp lệ tại thời điểm duyệt

**Căn cứ:** Function 1.2–1.3; BR 4, 6, 8, 9, 14.

**Bằng chứng:** [FirebaseAdminRepository.java](D:/NHOM_7_MobileApp/app/src/main/java/com/rescuefarm/data/remote/firebase/FirebaseAdminRepository.java:915) thực hiện chuyển trạng thái theo policy và metadata kiểm duyệt. Nhánh ACTIVE chưa đọc lại toàn bộ batch để kiểm tra hạn dùng, lượng mục tiêu, liên kết và trạng thái người bán. [firestore.rules](D:/NHOM_7_MobileApp/firestore.rules:993) cho phép Admin chuyển PENDING_APPROVAL → ACTIVE bằng điều kiện trạng thái/metadata; chưa thay thế được các kiểm tra nguồn cung này.

**Tình huống:** Seller gửi Campaign hợp lệ hôm trước; tới lúc Admin duyệt, batch hoặc Campaign đã hết hạn, Seller đã bị khóa, hoặc nguồn cung thay đổi. UI vẫn có thể ghi trạng thái ACTIVE, còn checkout chặn ở bước sau.

**Hướng sửa:** Tạo command approve phía backend, trong transaction đọc Campaign, Seller và các batch: Seller ACTIVE/APPROVED; thời gian còn hợp lệ; batch thuộc đúng Seller, chưa hết hạn, còn đủ lượng; không xung đột chiến dịch khác. Chỉ sau đó chuyển trạng thái và liên kết batch. Giới hạn quyền cập nhật ACTIVE từ client nếu chọn backend làm source of truth.

**Nghiệm thu:** Duyệt Campaign có lô đã hết hạn, Seller bị khóa, sai chủ sở hữu hoặc thiếu lượng đều trả lý do cụ thể và không ghi ACTIVE nửa chừng.

## A04 — Giới hạn số lượng theo từng batch chưa được trừ cộng dồn

**Căn cứ:** Campaign có `Map<String, Double> batchTargets`, kiểm soát lượng cần giải cứu ở cấp batch; Function 1.2 và 8.

**Bằng chứng:** [commerce.js](D:/NHOM_7_MobileApp/functions/commerce.js:109) chỉ so `row.item.quantity <= campaign.batchTargets[batchId]` cho từng lần checkout. Sau đó kiểm tra lượng còn lại trên tổng Campaign. Chưa có map lượng đã giữ/đã giải cứu theo batch để trừ vào phân bổ của batch đó.

**Tình huống:** A được phân bổ 10 kg, B 90 kg; batch A thực tế còn 50 kg. Hai đơn riêng cùng mua 10 kg từ A vẫn có thể qua kiểm tra vì mỗi đơn không quá 10 và tổng Campaign chưa quá 100. Kết quả phần A vượt mục tiêu đã khai báo.

**Hướng sửa:** Chốt rõ `batchTargets` là phân bổ cứng hay chỉ là thông tin. Với phân bổ cứng đang được code dùng để giới hạn checkout, bổ sung map `reservedByBatch`/`rescuedByBatch` hoặc tài liệu phân bổ dưới Campaign, cập nhật trong cùng transaction đặt/giao/hủy. Lượng được nhận mới = target batch − reserved batch − rescued batch. Giữ map trong cùng domain Campaign, không cần thêm business class vào sơ đồ 25 class.

**Nghiệm thu:** Hai checkout đồng thời không vượt phân bổ A; hủy trả đúng phân bổ A; giao chỉ chuyển reserved A sang rescued A, đồng thời tổng Campaign vẫn nhất quán.

## A05 — Chưa thống nhất đơn vị đo cho Campaign và analytics

**Căn cứ:** Function 2.2 và 8.4; tiến độ/khối lượng giải cứu phải phản ánh lượng thực tế. Product có `unit`, thống kê trình bày kg.

**Bằng chứng:** [CampaignViewModel.java](D:/NHOM_7_MobileApp/app/src/main/java/com/rescuefarm/ui/campaign/CampaignViewModel.java:100) cộng trực tiếp toàn bộ quantity trong targets. Chưa có kiểm tra các Product thuộc các batch có cùng đơn vị hay quy đổi. [CampaignDetailFragment.java](D:/NHOM_7_MobileApp/app/src/main/java/com/rescuefarm/ui/campaign/CampaignDetailFragment.java:312) dùng đơn vị kg cho tổng tiến độ; Seller/Admin analytics cũng cộng `rescuedQuantity` rồi gắn kg.

**Tình huống:** Gộp batch rau tính bó với batch bơ tính kg; tổng số bị cộng như cùng đơn vị. Kể cả Campaign chỉ chứa rau bó, tổng analytics vẫn có thể ghi kg không đúng.

**Hướng sửa:** Chọn một hợp đồng: (1) chỉ cho Campaign dùng một đơn vị, hiển thị đúng đơn vị đó và chỉ tổng hợp kg với các Campaign đo kg; hoặc (2) thêm hệ số quy đổi có ý nghĩa nghiệp vụ về đơn vị chuẩn, snapshot hệ số khi đặt đơn. Không tự coi một bó/bao là một kg. Xử lý dữ liệu cũ bằng migration có kiểm tra.

**Nghiệm thu:** Campaign khác đơn vị bị từ chối hoặc quy đổi chính xác; tiến độ, order item và analytics dùng cùng hợp đồng; thống kê không ghi kg cho số lượng chưa quy đổi.

## A06 — Luồng thông báo chưa bảo đảm gửi khi có lỗi hoặc app không mở màn liên quan

**Căn cứ:** Function 9.3 Notification và các thay đổi trạng thái đơn/nội dung.

**Bằng chứng:** [AdminNotificationClient.java](D:/NHOM_7_MobileApp/app/src/main/java/com/rescuefarm/service/notification/AdminNotificationClient.java:9) gọi HTTP và bỏ qua lỗi. Push ORDER được gọi từ [OrderDetailFragment.java](D:/NHOM_7_MobileApp/app/src/main/java/com/rescuefarm/ui/order/OrderDetailFragment.java:261). [server.js](D:/NHOM_7_MobileApp/functions/server.js:169) ghi inbox trước rồi mới gửi FCM; nếu inbox đã có thì trả duplicate, không thử lại FCM. Việc tạo inbox đơn trong transaction đã có, nhưng chưa tương đương với cơ chế giao push bền vững. Chưa thấy worker cho Promotion được lên lịch bắt đầu trong tương lai.

**Tình huống:** Gửi đơn/nội dung thành công nhưng HTTP báo lỗi; app đóng trước request thông báo; FCM lỗi sau khi inbox được ghi. Lần retry có thể bị nhận diện duplicate và push vẫn không được gửi. Promotion chưa tới giờ lúc lưu thì bị skip, tới giờ không có sự kiện mới để gửi.

**Hướng sửa:** Ghi event outbox trong cùng transaction với nghiệp vụ. Worker backend xử lý event theo `eventId`, tách trạng thái inboxCreated và pushDelivered/attempted; lưu retryCount/nextAttemptAt/error. Không đồng nhất “có inbox” với “đã gửi push”. Đưa gửi ORDER ra khỏi Fragment. Thêm xử lý sự kiện khi Promotion bắt đầu có hiệu lực. Nếu worker không chạy liên tục, cần một lịch gọi được triển khai và theo dõi, không chỉ một hàm không ai gọi.

**Nghiệm thu:** Tắt app ngay sau mutation hoặc giả lập FCM lỗi rồi khôi phục vẫn giao thông báo theo chính sách; retry không nhân đôi inbox; Promotion tương lai phát sinh thông báo đúng lúc hiệu lực.

## A07 — Thông báo kiểm duyệt mở nhầm màn hình

**Căn cứ:** Notification cần dẫn người nhận đến đúng nội dung/công việc.

**Bằng chứng:** [server.js](D:/NHOM_7_MobileApp/functions/server.js:224) gửi sự kiện chờ duyệt Campaign với type MODERATION và referenceId là Campaign ID. [NotificationDeepLinkService.java](D:/NHOM_7_MobileApp/app/src/main/java/com/rescuefarm/service/engagement/NotificationDeepLinkService.java:3) biến mọi MODERATION thành `rescuefarm://reports/{id}`. Sự kiện SellerApplication lại dùng SYSTEM nên chỉ quay về thông báo. `AdminReportFragment` không tải chọn report theo argument reportId.

**Hướng sửa:** Thêm resourceType/action vào inbox và FCM payload, ví dụ SELLER_APPLICATION/CAMPAIGN/REPORT; resolver nhận thêm actor. Admin mở đúng AdminDetail của hồ sơ/chiến dịch/report, Customer mở kết quả report của chính mình. Trì hoãn deep link nếu chưa đăng nhập và kiểm tra lại quyền sau đăng nhập. Chỉ mark read sau khi xử lý điều hướng thích hợp, hoặc có UX báo rõ điều hướng không khả dụng.

**Nghiệm thu:** Từng loại thông báo mở đúng một bản ghi, khi app đang mở và khi app vừa khởi động; tài khoản không có quyền không đi vào màn quản trị.

## A08 — Thiếu vòng phản hồi kết quả duyệt và xử lý báo cáo

**Căn cứ:** Seller approval, content moderation, report handling và Notification trong Function 9–10.

**Bằng chứng:** [FirebaseAdminRepository.java](D:/NHOM_7_MobileApp/app/src/main/java/com/rescuefarm/data/remote/firebase/FirebaseAdminRepository.java:1007) hoàn tất transaction và chỉ gọi gửi Campaign ACTIVE cho Customer theo dõi; chưa tạo inbox kết quả cho Seller khi duyệt/từ chối hồ sơ, từ chối/dừng Campaign, hoặc cho người báo cáo khi xử lý xong. [server.js](D:/NHOM_7_MobileApp/functions/server.js:293) còn lọc recipients của `/send-notification` về CUSTOMER nên không thể dùng nguyên nhánh đó để gửi cho Seller.

**Hướng sửa:** Phát event quyết định kiểm duyệt trong cùng transaction/outbox của A06, gồm recipientUid, resourceType/id, quyết định và lý do công khai. Thêm màn/khối trạng thái report của chính người gửi; tránh gửi thông tin nội bộ nhạy cảm. Phân quyền người nhận theo loại sự kiện thay vì chỉ CUSTOMER.

**Nghiệm thu:** Seller nhận đúng quyết định và lý do, Customer xem được kết quả báo cáo của mình; không nhận kết quả của tài khoản khác; cập nhật vẫn lưu được vào inbox khi người nhận đang offline.

## A09 — Thống kê Seller/Admin chưa bảo đảm đầy đủ và cập nhật

**Căn cứ:** U33 và Function 8.4: doanh thu, đơn hàng, top sản phẩm, lý do và kết quả giải cứu.

**Bằng chứng:** [FirebaseOrderRepository.java](D:/NHOM_7_MobileApp/app/src/main/java/com/rescuefarm/data/remote/firebase/FirebaseOrderRepository.java:65) có page size 50; [SellerViewModel.java](D:/NHOM_7_MobileApp/app/src/main/java/com/rescuefarm/ui/seller/SellerViewModel.java:93) refresh tập đầu và aggregate danh sách/cache hiện có, không tự lấy hết trang cho thống kê. Product/Campaign Seller cũng có phân trang. `SellerDashboardAggregator` lọc kỳ từ danh sách đầu vào; không truy vấn aggregate đầy đủ theo kỳ. Đã có ghi chú giới hạn ở một số UI, nhưng đây chưa phải thống kê hoàn chỉnh.

**Tình huống:** Seller có trên 50 đơn; số doanh thu hoặc top sản phẩm có thể thiếu đơn cũ. Việc trước đó tải thêm danh sách/có cache có thể làm kết quả khác với một thiết bị mới. Ô “đang xử lý” trên Home còn chịu kỳ lọc thống kê, dễ không thấy đơn tồn từ kỳ trước.

**Phía Admin:** [server.js](D:/NHOM_7_MobileApp/functions/server.js:102) đếm ACTIVE/CRITICAL theo field trạng thái/urgency đã lưu. Urgency ở Android được tính lại khi đọc dữ liệu nhưng không đồng nghĩa field server đã được cập nhật. Campaign có thể hết hạn hoặc chuyển mức khẩn cấp theo thời gian mà không có người sửa document; Dashboard Admin khi đó có thể khác Home công khai. Đây là phần phụ thuộc việc khép kín vòng đời/thời gian ở A02, không phải thiếu toàn bộ dashboard Admin.

**Hướng sửa:** Thống kê query phía backend theo sellerId/kỳ và tổng hợp trên tập đầy đủ, tách với pagination của UI. Chốt doanh thu theo ngày tạo hay ngày giao; nếu theo ngày giao thì cần deliveredAt tương ứng. Tách hàng chờ xử lý toàn bộ khỏi filter thống kê lịch sử. Với Admin, tính/duy trì projection trạng thái và urgency từ deadline, hạn batch và lượng còn lại, dùng cùng mốc thời gian và rule với Home. Tính tổng lượng theo hợp đồng đơn vị ở A05.

**Nghiệm thu:** Dataset trên 50 đơn, có đơn xử lý kéo dài qua kỳ và đơn tạo kỳ trước/giao kỳ này; kết quả giống nhau trên cache trống/có cache và khớp phép tổng hợp độc lập. Giữ dữ liệu Campaign không thay đổi, cho thời gian đi qua ngưỡng hết hạn/CRITICAL: số Admin và trạng thái Home vẫn phải nhất quán.

## A10 — Search/filter/sort Campaign còn thiếu

**Căn cứ:** Function 5.2–5.4 và U2.

**Bằng chứng:** [DiscoveryEngine.java](D:/NHOM_7_MobileApp/app/src/main/java/com/rescuefarm/ui/home/DiscoveryEngine.java:155) `matchesCampaign()` kiểm tra reason/urgency/mode/province/text nhưng không áp category/minPrice/maxPrice theo Product liên quan. `price()` trả Double.MAX_VALUE cho Campaign, `discount()` và `sold()` trả -1 khi không có Product trực tiếp. Vì vậy lựa chọn rẻ nhất/giảm nhiều/bán chạy chưa thực sự xếp hạng Campaign theo các batch của nó.

**Hướng sửa:** Tạo projection tìm kiếm Campaign → batch → Product. Áp đồng thời category, giá và tồn lên cùng batch/sản phẩm thỏa điều kiện, tránh lấy category từ batch A nhưng giá từ batch B. Chốt giá đại diện là “từ”, mức giảm tối đa và lượng bán trong kỳ/toàn thời gian, hiển thị đúng ý nghĩa. Với Campaign nhiều đơn vị, giải quyết A05 trước khi so sánh giá.

**Nghiệm thu:** Chọn danh mục rau không trả Campaign chỉ có trái cây; lọc giá không trả Campaign ngoài khoảng; sort Campaign có kết quả xác định dựa trên dữ liệu, không mặc định đẩy hết xuống cuối.

## A11 — Report bình luận chưa khép kín sau khi gộp Post vào Campaign

**Căn cứ:** Quyết định cuối `final.md`: Comment nằm dưới Campaign; Admin xử lý report trên nội dung hiện hành.

**Bằng chứng:** [FirebaseAdminRepository.java](D:/NHOM_7_MobileApp/app/src/main/java/com/rescuefarm/data/remote/firebase/FirebaseAdminRepository.java:645) tìm bình luận bằng `collectionGroup("comments").whereEqualTo("id", targetId)` nhưng ghi ID cha thành `postId` dù có thể là Campaign. Repo [firestore.indexes.json](D:/NHOM_7_MobileApp/firestore.indexes.json) chưa khai báo index collection-group cho comments.id (`fieldOverrides` rỗng), và Rules chưa có rule collection-group comments tổng quát dành riêng cho Admin. Vì vậy triển khai sạch chỉ theo repo chưa đủ bảo đảm truy vấn moderation này chạy được; index/rule đã tạo thủ công trên production chưa được xác minh.

Firebase yêu cầu index có scope collection group cho query group có filter/order: [tài liệu index](https://firebase.google.com/docs/firestore/query-data/index-overview). Đây là điểm cần kiểm tra trên môi trường thật, không thể dùng kết quả emulator để suy ra mọi index production đã tồn tại.

**Hướng sửa ưu tiên:** Report lưu parentType/parentId hoặc targetPath được xác thực; đọc trực tiếp `campaigns/{id}/comments/{commentId}`. Phân biệt parent Post legacy/Campaign khi render ngữ cảnh và điều hướng. Nếu giữ collectionGroup thì khai báo index/rule cần thiết trong repo và triển khai chúng; không mở quyền đọc bình luận tùy ý cho mọi tài khoản.

**Nghiệm thu:** Admin mở/xử lý một report bình luận Campaign và một bình luận Post legacy trên môi trường dựng từ cấu hình repo; đúng parent, đúng bản ghi, không nhầm sang Post cùng ID.

## A12 — Review có upload nhưng người đọc không xem được ảnh

**Căn cứ:** Review và media trong Function 9.3; đây là phần còn thiếu của chức năng ảnh Review đã có UI upload.

**Bằng chứng:** [ReviewFragment.java](D:/NHOM_7_MobileApp/app/src/main/java/com/rescuefarm/ui/engagement/ReviewFragment.java:327) render rating/content và thao tác của người sở hữu, không render `review.getImageUrls()` cho người đọc.

**Hướng sửa:** Hiển thị dải thumbnail dưới từng Review; chạm mở gallery, có placeholder/error, giữ đúng thứ tự tối đa 5 ảnh. Tái sử dụng renderer cho các nơi xem Review nếu có; không chỉ hiện ảnh ở form chỉnh sửa. Kiểm tra trạng thái moderation trước khi công khai ảnh.

**Nghiệm thu:** Customer tạo Review 3 ảnh; tài khoản khác và Guest thấy đúng 3 ảnh, mở ảnh lớn được; ảnh lỗi không phá danh sách; xóa ảnh khỏi Review cập nhật giao diện sau lưu.

## A13 — Thiếu lối vào report đúng ngữ cảnh

**Căn cứ:** Report, xử lý nội dung vi phạm trong Function 9.3/10.3; enum và backend đã hỗ trợ REVIEW/COMMENT/USER.

**Bằng chứng:** [ReportFragment.java](D:/NHOM_7_MobileApp/app/src/main/java/com/rescuefarm/ui/engagement/ReportFragment.java:2) vẫn cho chọn type/nhập ID. Review list và PublicSellerProfile chưa có thao tác report đối tượng đang xem; comment trong Campaign chỉ có sửa/xóa của người sở hữu. Backend hỗ trợ loại report không có nghĩa người dùng biết ID để thực hiện luồng.

**Hướng sửa:** Thêm menu báo cáo ở Review, Comment và hồ sơ Seller/người dùng công khai nếu loại đối tượng đó được phép. Truyền sẵn type, targetId, parent Campaign cho Comment; khóa các field ID khỏi người dùng. Form chỉ hỏi lý do và mô tả. Giữ điều kiện đăng nhập/quyền hiện hành.

**Nghiệm thu:** Customer báo cáo một Review/Comment/hồ sơ ngay từ đối tượng đó mà không sao chép ID. Admin nhìn thấy đúng nội dung ngữ cảnh và xử lý theo A11.

## A14 — Hồ sơ Customer doanh nghiệp chưa đi hết từ đăng ký tới quản lý hồ sơ

**Căn cứ:** Customer.customerType/companyName/taxCode trong domain và U9 Manage Profile.

**Bằng chứng:** Đăng ký/model có dữ liệu doanh nghiệp, nhưng [EditProfileFragment.java](D:/NHOM_7_MobileApp/app/src/main/java/com/rescuefarm/ui/profile/EditProfileFragment.java:143) populate/lưu tên, điện thoại, avatar, tọa độ; chưa có field doanh nghiệp. Profile công khai cho chính người dùng cũng chưa trình bày khối doanh nghiệp.

**Hướng sửa:** Thêm khối theo customerType; hiển thị tên doanh nghiệp/mã số thuế cho chính tài khoản, cho sửa các field được chính sách cho phép. Mở rộng validator, ViewModel, repository và Rules với whitelist field; tránh việc sửa hồ sơ làm mất các field cũ hoặc cho phép đổi role/status.

**Nghiệm thu:** Đăng ký tài khoản doanh nghiệp, đăng nhập lại, xem/sửa/lưu thông tin và tải lại vẫn đúng. Tài khoản cá nhân không bị ép nhập field doanh nghiệp.

## A15 — Thời gian còn lại và vị trí mobile trên Detail chưa cập nhật đầy đủ

**Căn cứ:** Function 2.2 “Thời gian còn lại”; §1.4 và BR 12–13 về freshness vị trí.

**Bằng chứng:** [CampaignDetailFragment.java](D:/NHOM_7_MobileApp/app/src/main/java/com/rescuefarm/ui/campaign/CampaignDetailFragment.java:314) hiển thị khoảng ngày, chưa có countdown. Fresh/stale được tính trong render quanh dòng 437; chưa có ticker theo lifecycle để màn đang mở tự chuyển trạng thái khi hết ngưỡng. MobileRescueFragment cũng dựng trạng thái khi render danh sách. Home đã có phần tái dựng theo thời gian nên không đánh đồng mọi màn đều thiếu freshness handling.

**Hướng sửa:** Tạo formatter thời gian dùng chung, hiển thị “Còn … giờ/ngày” và thời điểm cập nhật vị trí. Cập nhật khi màn STARTED, dừng khi STOPPED; tại ngưỡng 10 phút tự đổi nhãn và vô hiệu thao tác phụ thuộc vị trí mới. Backend tiếp tục kiểm tra thời gian khi quote/checkout, không dựa vào nhãn UI.

**Nghiệm thu:** Giữ Detail mở qua ngưỡng freshness/deadline mà không thao tác refresh; nhãn và hành động đổi đúng, không tiếp tục quảng bá vị trí cũ như đang hoạt động.

## A16 — Home chưa khám phá đúng các ưu đãi mua nhiều

**Căn cứ:** Home §34 và Function 5.1/6.2 “Mua nhiều giá tốt”, quantityDiscountTiers.

**Bằng chứng:** [HomeContentBuilder.java](D:/NHOM_7_MobileApp/app/src/main/java/com/rescuefarm/ui/home/HomeContentBuilder.java:97) chọn valueProducts chỉ từ rescue discount > 0, chưa dùng Promotion/tier. UI mới đã đổi tên thành “Nông sản giá tốt”, nên nhãn hiện tại không sai; phần còn thiếu là khả năng khám phá volume discount mà tài liệu đề ra, không phải toàn bộ engine tính giá mua nhiều.

**Hướng sửa:** Giữ nhóm giá tốt hiện tại nếu cần, đồng thời thêm/đổi nhóm được lọc theo Promotion ACTIVE, trong thời gian hiệu lực và có tier số lượng. Card trình bày “Từ N đơn vị: giảm X%” dựa trên tier thật, dẫn đến đúng sản phẩm/batch và hiển thị giá minh bạch. Không quảng bá ưu đãi chỉ vì Product có rescuePrice thấp hơn originalPrice.

**Nghiệm thu:** Một sản phẩm chỉ giảm giá giải cứu nhưng không có tier không được gọi là ưu đãi mua nhiều; tier hết hạn biến mất khỏi nhóm; giá ở chi tiết/quote khớp điều kiện được quảng bá.

## A17 — Ảnh Campaign có thể bị thay mà không đi lại quy trình duyệt

**Căn cứ:** Quyết định cuối tài liệu: ảnh và câu chuyện là một phần Campaign được Seller gửi/Admin duyệt; §20 về bảo vệ media.

**Bằng chứng:** [Cloudinary Worker](D:/NHOM_7_MobileApp/cloudinary-worker/src/index.js:33) ký upload `overwrite: true`, public ID cố định theo user/resource/slot. `resolveAsset()` quanh dòng 144 kiểm tra Seller APPROVED nhưng chưa kiểm tra trạng thái thực tế của Campaign hay quyền sửa resource trước khi ký. Firestore khóa sửa Campaign ACTIVE không khóa được nội dung ảnh bị ghi đè ở Cloudinary. Đây là khoảng trống cần kiểm thử tích hợp upload; chưa khẳng định đã xảy ra trên tài khoản thật.

**Tình huống:** Một public ID đã được Campaign ACTIVE tham chiếu được ký upload lại cùng slot. Tài liệu Firestore có thể giữ nguyên trong khi nội dung ảnh ở nguồn media thay đổi. Việc upload ảnh rồi rời form cũng không có bước commit/cancel tài nguyên rõ ràng.

**Hướng sửa:** Worker xác minh resource thuộc người gọi và Campaign đang DRAFT/REJECTED trước khi cho upload. Dùng public ID mới cho mỗi lần upload, ảnh bản đã duyệt không bị ghi đè; chỉ đổi danh sách tham chiếu khi lưu/gửi duyệt hợp lệ. Có draft asset hoặc cleanup ảnh không được commit. Giới hạn slot và kiểm tra loại/kích thước ảnh ở hạ tầng upload, không chỉ tin vào limit trong app. Giữ đường upload cho Campaign mới bằng cơ chế draftId/ownership có xác thực.

**Nghiệm thu:** Không thể ghi đè ảnh đang được Campaign ACTIVE tham chiếu bằng quyền Seller thông thường; cancel form không đổi ảnh đã công khai; gửi bộ ảnh mới phải được duyệt theo chính sách.

## Thứ tự triển khai đề nghị

1. **Khôi phục đường đi và tính đúng dữ liệu:** A01 → A02/A03 → A04/A05. Chốt hợp đồng lượng/đơn vị và trạng thái trước khi sửa thêm UI thống kê.
2. **Khép kín kiểm duyệt và thông báo:** A17, A11, A06/A07/A08. Làm event/resource contract dùng chung để tránh vá từng màn.
3. **Thống kê và khám phá:** A09/A10, sau đó nối màn Thống kê ở A01 vào dữ liệu tổng hợp đã đúng.
4. **Hoàn thiện giao diện chức năng:** A12–A16 và các entry point ở A13.

Mỗi nhóm thay đổi cần cập nhật đồng bộ schema, repository/backend, Rules/indexes, dữ liệu cũ và APK. Không triển khai UI trước rồi bỏ qua backend/rules cần thiết.

## Kiểm tra đã thực hiện và giới hạn

- Parse toàn bộ XML resources: thành công.
- Đối chiếu class đích Fragment trong navigation graph: phát hiện một class thiếu ở A01.
- `node --check` cho server.js, commerce.js và cú pháp module Cloudinary Worker: thành công.
- Thử chạy bộ regression hiện có tại `tests/firestore`: chưa chạy được test nghiệp vụ. Trong sandbox emulator hub timeout; thử ngoài sandbox thì Firestore Emulator thoát do Java `Unable to establish loopback connection` / `Invalid argument: connect`.
- Lệnh chạy riêng commerce.test.cjs bị guard từ chối khi chưa có Firestore Emulator; đây không phải bằng chứng test nghiệp vụ thất bại.
- Gradle trên máy đã có cùng lỗi loopback trong các lượt kiểm tra trước. Không ghi nhận audit này là build/test E2E thành công và không thay đổi Firewall/Windows networking để ép chạy.
- Không sửa logic ứng dụng, Rules hay dữ liệu production trong lượt audit này. Báo cáo là đề xuất xử lý, không phải tuyên bố 17 mục đã được sửa.

## Checklist nghiệm thu trước khi tuyên bố full implementation

1. Dựng môi trường kiểm thử từ repo, triển khai đúng backend/rules/indexes/Worker và APK cùng phiên bản.
2. Smoke test tất cả route cho bốn actor, đặc biệt Seller Thống kê và notification deep link.
3. Chạy ma trận Guest/Customer × DELIVERY/PICKUP/ON_SITE_RESCUE × phương thức thanh toán hợp lệ; xác minh đơn, items, payment, shipment và inventory trong database kiểm thử.
4. Chạy cạnh tranh checkout, replay request, hủy/giao lặp lại, Campaign hết hạn/bị từ chối/đạt mục tiêu còn dư batch và checkout nhiều batch.
5. Kiểm thử duyệt nội dung và media cùng quyền ghi, không chỉ ẩn button ở Android.
6. Thông báo phải được thử khi sender/receiver đóng app, offline, FCM lỗi và retry; mở thông báo tới đúng resource.
7. Đối chiếu thống kê bằng dataset trên một trang, gồm đơn qua kỳ và sản phẩm nhiều đơn vị.

Các bước này xác minh phần còn chưa đủ bằng chứng ở source; chúng không thay thế danh sách khoảng trống cụ thể phía trên.
