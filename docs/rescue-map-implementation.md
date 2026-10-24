# Bản đồ Home và chia sẻ GPS — bàn giao 24/10/2026

Source nền: `b6911d07de2b243b900680d78ed62c4d32d6778c` + working diff. Không commit/push/deploy. Không thay header/logo, giá/số lượng, tìm kiếm, thanh toán, SePay hoặc dữ liệu production. Báo cáo DOCX/PDF trước đó vẫn là snapshot trước tính năng mới này.

## Điều chỉnh giao diện theo ảnh minh họa

Khối Home bo góc 20dp, giữ zoom +/−, không còn tiêu đề hoặc nút mở riêng; chạm nền/marker mở dialog bản đồ lớn. Bản đồ lớn không có tiêu đề; nút Quay lại bo góc ở giữa cạnh dưới đóng dialog, vẫn hỗ trợ Back hệ thống. Cụm +/− được neo góc dưới bên trái để tránh chú giải. Chú giải cố định/di động thành pill nổi cố định trên map; ba bộ lọc là nút tròn xếp dọc góc phải. Ba chấm nhấp nhô lần lượt khi dữ liệu live hoặc đang kết nối, mất kết nối hiển thị dữ liệu đã lưu. Overlay không di chuyển khi pan và không tạo request dữ liệu mới. Popup thông tin hiển thị trên bản đồ lớn. Thay đổi chỉ UI, không thay tần suất GPS/cache/backend.

Đã build/lint lại và chạy 3 test logic HTML hiện có sau sửa (qua). Chưa xác minh trực quan Android theo lựa chọn giữ nguyên emulator5554. Thử preview trình duyệt riêng cũng bị chặn kết nối localhost của môi trường công cụ, không coi đó là kiểm tra hình ảnh thành công.

## Đã triển khai trong source (nghiệp vụ)

- Home chứa khối Leaflet và trạng thái kết nối. Marker cố định là ghim xanh lá, di động là xe tải xanh mint/vàng bo tròn; dùng SVG vector đồng bộ trong chú giải và nút lọc, không dùng emoji. Trên bản đồ lớn, chạm marker mở popup tên, địa điểm, loại điểm, lượng còn lại tham khảo, thời điểm GPS và liên kết tới chiến dịch. Các chiến dịch cố định trùng tọa độ tới 6 chữ số thập phân được gom vào một popup có từng liên kết riêng.
- Chạm preview mở dialog toàn màn hình, pan/zoom và bộ lọc Tất cả/Cố định/Di động. Preview không giữ cử chỉ kéo của trang Home. Chưa cấp GPS vẫn xem được điểm. Chưa bổ sung vị trí người xem/khoảng cách/nút chỉ đường trong popup ở phiên bản này; liên kết chi tiết chiến dịch vẫn dùng luồng chỉ đường sẵn có.
- Marker di động tái sử dụng cùng ID, chuyển trong khoảng 1 giây từ vị trí đã quan sát sang fix mới; không dự đoán đường đi hay giả chuyển động khi chưa nhận GPS. Mặc định ẩn xe khi stream mất kết nối, chia sẻ tắt, điểm hết hạn hoặc GPS quá 10 phút. Điểm cố định cache được đánh dấu dữ liệu đã lưu.
- Seller có nút bắt đầu chia sẻ và Dừng. `MobileLocationService` là foreground service loại location, có thông báo và nút Dừng, không tự khôi phục khi process khởi động lại. Lấy mẫu mục tiêu 5 giây; ghi tối đa một lần/10 giây nếu dịch chuyển ít nhất 25 m; heartbeat tối đa khoảng 60 giây khi đứng yên. Chỉ nhận fix không quá 15 giây, accuracy không quá 100 m. Thông số là mục tiêu, Android/GPS/mạng không bảo đảm callback/độ trễ đúng từng giây.
- Một phiên chỉ chia sẻ một chiến dịch từ một điện thoại. Chủ động dừng khi đăng xuất/đổi tài khoản hoặc hết giờ chiến dịch; lỗi quyền/trạng thái từ transaction cũng dừng. Callback GPS cũ không khởi động lại sau STOP; thao tác tắt server được xếp sau publish đang chạy. Nếu mất mạng lúc STOP hoặc hệ điều hành giết process, fix đã gửi có thể còn hiệu lực tới 10 phút; không tuyên bố xóa tức thì ở server trong trường hợp đó.
- Transaction cũ vẫn kiểm quyền sở hữu, Rules và ACTIVE/MOBILE; bổ sung từ chối bật chia sẻ sau endDate, và bảo vệ cache executor khỏi callback đến sau close. Không đổi Rules/index/schema/collection trong đợt này.

## Reads, cache và phạm vi miễn phí

GPS được gọi bằng FusedLocationProvider của Google Play services trên thiết bị; không dùng Google Maps SDK/Routes/Geolocation HTTP API, không thêm API key hay cấu hình billing Google Maps. Leaflet đã bundle trong repo. Không có phí mới do mỗi lần gọi bộ lấy GPS cục bộ hoặc thư viện Leaflet. Kết nối mạng, hosting và Firestore là các phần riêng.

Tile mới dùng `https://tile.openstreetmap.org/{z}/{x}/{y}.png`, attribution hiển thị, User-Agent nhận diện RescueFarm, WebView cache theo HTTP với LOAD_DEFAULT, không prefetch/bulk download. Dịch vụ OSM tiêu chuẩn không yêu cầu API key, nhưng có giới hạn sử dụng, không có SLA và có thể chặn tải quá mức. Không gọi đây là dịch vụ miễn phí vô hạn. Map hồ sơ cũng dùng chung renderer/nền OSM và khung bo góc 20dp. Chạm mở toàn màn hình với nút Quay lại; chế độ profile chỉ nhận tọa độ hồ sơ, không hiển thị marker/chú giải/bộ lọc giải cứu hoặc trạng thái live và không mở stream dữ liệu giải cứu.

Firestore miễn phí 50.000 reads và 20.000 writes/ngày trong quota được cung cấp cho database đủ điều kiện. Listener không được miễn reads chỉ vì gọi là realtime. **Không có cam kết tính năng hoặc toàn app luôn nằm dưới quota**; không thay đổi gói thanh toán và chưa đọc dashboard billing thực tế của project.

Cách giảm reads của tính năng mới:

1. `functions/rescue-map.js` có một listener ACTIVE campaigns dùng chung cho các client trên cùng process backend. Không tạo Firestore listener/session document riêng cho từng người xem map. SSE HTTP truyền bản chiếu công khai từ RAM; không gọi get() theo từng marker hoặc mỗi lần popup/lọc/mở map lớn.
2. Backend giữ listener thêm 60 giây sau client cuối để tái sử dụng khi điều hướng/xoay; không giữ listener khi không ai xem lâu hơn. Đồng hồ gỡ điểm hết hạn/GPS cũ không đọc database. Mỗi process Render có cache/listener riêng; scale nhiều instance hoặc restart sẽ thêm initial query, không có cache phân tán.
3. `RescueMapRepository` singleton dùng chung một HTTP stream trong mỗi process Android, giữ kết nối 5 giây giữa vòng đời màn, cache bản chiếu công khai trong SharedPreferences tối đa 1 ngày khi nạp lại. Kết nối lại có backoff, tôn trọng Retry-After. Không fallback sang query Firestore riêng nếu backend chưa có endpoint.
4. Thay đổi chỉ tọa độ/timestamp không còn invalidate toàn bộ cache API thẻ chiến dịch hoặc gửi revision tới từng người xem thẻ. Các thay đổi nghiệp vụ và bật/tắt chia sẻ vẫn làm mới bình thường.

Ví dụ dự toán, **không phải phép đo billing**: một xe liên tục đủ điều kiện ghi mỗi 10 giây tạo tối đa khoảng 360 lần publish/giờ, cộng heartbeat/start/stop. Mỗi publish qua transaction có đọc và ghi; listener backend map đọc document thay đổi một lần cho mỗi process, thay vì một lần cho mỗi người xem map. Rules dependent reads, retry và các listener khác vẫn có thể tính thêm. **Home/chi tiết/Seller trong app vẫn còn các listener campaign sẵn có**; nếu đang theo dõi document di động, chúng vẫn nhận/có thể tính read cho các cập nhật GPS. Đợt này không chuyển toàn bộ cơ chế đọc của app sang backend. Muốn tách hẳn fan-out đó ở quy mô lớn cần collection vị trí/public projection độc lập và đồng bộ lại điều kiện checkout, là bước kiến trúc tiếp theo cần kiểm thử riêng.

Giới hạn có chủ ý: backend lấy tối đa 500 campaign ACTIVE, không giới hạn 12 mục như Home trước đây; đạt 500 sẽ thông báo rõ có giới hạn. Tối đa 128 kết nối map đồng thời mỗi process, quá giới hạn trả 503. Chưa có truy vấn vùng/geohash hay cluster các tọa độ khác nhau. Không khẳng định 500 điểm đại diện toàn bộ dữ liệu khi quy mô vượt ngưỡng.

## Kiểm thử và giới hạn xác minh

- Toàn bộ Android JVM: 255 test qua, không fail/error/skipped; gồm 2 test mới về throttle/chống jitter/heartbeat. assembleDebug và lintDebug qua; số warning cụ thể lưu trong `docs/audits/rescue-map/implementation-results.json`.
- Backend check qua; backend unit 193 test qua, Worker 9 test qua, không fail/skipped. Có test shared-watch nhiều client, idle/error, cold start, freshness, loại bỏ trường riêng, giữ marker ID, popup an toàn, gom điểm, lọc tại chỗ và không invalidation cache khi chỉ đổi GPS. Test HTML chạy qua VM/stub Leaflet để kiểm logic; **không phải test giao diện Android/browser**.
- Tích hợp Firestore Emulator: 1 test mới qua, không fail/skipped. Hai HTTP client nhận cùng chuyển động và STOP; SDK Rules chặn Seller khác/timestamp giả, cho owner serverTimestamp hợp lệ; không lộ owner/private fields qua SSE. Permission-denied có trong log là kết quả mong đợi của assertFails.
- Những lần tích hợp đầu thất bại được giữ log; đã sửa vòng đời response SSE và đồng hồ expiry lúc snapshot đầu chưa về. Không bỏ test để làm xanh.
- `RescueMapEvidenceTest` đã được viết và biên dịch trong APK instrumentation demo, nhưng **chưa chạy thành công trên thiết bị**. AVD riêng không khởi động ổn định do thiếu RAM/pagefile và lỗi GPU; người dùng chọn giữ nguyên emulator5554, không dùng nó để test. Không có screenshot Android mới hoặc bằng chứng GPS service nền/rotation/WebView popup đã thao tác. Không gắn nhãn các test Node/JVM thành end-to-end Android.
- Chưa xác minh Android14+ start foreground service, thu hồi quyền lúc đang chạy, thông báo bị từ chối, GPS trên xe thật, mất mạng/reconnect trên thiết bị và nhiều điện thoại cùng chia sẻ một campaign. Rules ngăn người khác ghi nhưng chưa có khóa phiên đa thiết bị cùng owner.
- Cache tọa độ không chứng minh tile/ảnh bản đồ offline. Khi thiếu tile hiển thị lỗi kết nối riêng; không đóng giả nền bản đồ bằng ảnh/mockup.

## Module và cách áp dụng

| Module | Trách nhiệm |
|---|---|
| functions/rescue-map.js | Public projection + shared watch + SSE GET /rescue-map/events |
| functions/realtime.js | Không fan-out invalidation thẻ chỉ vì GPS |
| data/repository/RescueMapRepository.java | Stream dùng chung, disk cache, backoff, lifecycle |
| ui/home/RescueMapView.java; assets/maps/rescue-map.html | Khối Home/fullscreen, marker, popup, lọc và animate |
| service/location/MobileLocationService.java | Phiên GPS foreground, start/stop, kiểm fix |
| service/location/LocationPublishPolicy.java | Giới hạn tần suất và heartbeat |
| ui/campaign/MobileRescueFragment.java | Nút bắt đầu/dừng và trạng thái Seller |

Để chạy ở môi trường triển khai cần backend Render cập nhật source (health có `rescueMapVersion:1`) **và APK mới**. Endpoint SSE phải được proxy cho phép streaming, không buffering và heartbeat 15 giây. Chưa deploy Render hay cài APK lên emulator5554/thiết bị người dùng. APK debug đã build ở `app/build/outputs/apk/debug/app-debug.apk`; mặc định backend/Firebase theo cấu hình hiện có nên không dùng APK này tạo dữ liệu demo production.

Reproduce an toàn: khởi động Firebase Auth/Firestore bằng firebase.report-demo.json project demo-rescuefarm; đặt FIRESTORE_EMULATOR_HOST=127.0.0.1:8088 và FIREBASE_AUTH_EMULATOR_HOST=127.0.0.1:9098; chạy `node scripts/rescue-map-demo.cjs`. Script từ chối host khác, tạo 16 điểm cố định + 1 di động giả và tài khoản giả (chỉ emulator), chạy map backend3008. Build `assembleReportDemo assembleReportDemoAndroidTest -PreportTestBuildType=reportDemo`; APK .reportdemo trỏ đúng demo emulators. Trên AVD/profile test riêng cấp location + mock-location cho package demo, chạy riêng class `com.rescuefarm.RescueMapEvidenceTest`. Test dự kiến bơm fix A/B qua Fused mock, kiểm tra 16 điểm vượt trang12, marker chuyển động, popup, xoay và STOP. Không chạy class này vào APK thường hoặc project thật.

Log test/manifest ở `docs/audits/rescue-map/`. Chạy `npm --prefix functions run check`, `npm --prefix functions run test:unit`; test tích hợp mới có trong scripts test của tests/firestore và phải dùng Emulator. Không seed chiến dịch di động vào production chỉ để biểu diễn.

## Nguồn chính thức đã đối chiếu

- [Firestore pricing](https://firebase.google.com/docs/firestore/pricing): quota và tính reads cho listener.
- [Firebase pricing](https://firebase.google.com/pricing): 50K reads/day, 20K writes/day cho mức miễn phí tương ứng.
- [Google Fused Location Provider](https://developers.google.com/location-context/fused-location-provider): lấy vị trí bằng tín hiệu thiết bị và callback cập nhật.
- [Android foreground service restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start): bắt đầu service khi app foreground và quyền while-in-use.
- [Leaflet API](https://leafletjs.com/reference.html): marker, popup, cập nhật tọa độ.
- [OSM tile policy](https://operations.osmfoundation.org/policies/tiles/): attribution, User-Agent, HTTP cache, không bulk/prefetch, best-effort.
