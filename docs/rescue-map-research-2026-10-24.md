# Nghiên cứu bản đồ điểm giải cứu cố định và di động

Audit ngày 24/10/2026, HEAD `b6911d07de2b243b900680d78ed62c4d32d6778c`. Phạm vi lượt này: đọc source, truy vấn tổng hợp Firestore chỉ đọc, chạy test liên quan và đề xuất thiết kế. Chưa triển khai UI bản đồ hay cơ chế phát GPS mới. Không thay đổi chiến dịch production.

## 1. Kết luận từ dữ liệu hiện tại

Truy vấn lúc `2026-10-24T14:46:08.431Z` (21:46 giờ Việt Nam; 21:46 Asia/Novosibirsk cùng UTC+7): collection campaigns có **9 document, toàn bộ FIXED_POINT**, gồm 4 ACTIVE và 5 EXPIRED. **Không có MOBILE_POINT**, không có điểm đang chia sẻ vị trí. Bốn điểm cố định ACTIVE có tọa độ hợp lệ, đang trong lịch và còn số lượng theo bộ đếm chiến dịch tại snapshot này; chưa dùng kết quả đó để bảo đảm mọi lô đều còn bán được lúc checkout.

Nguồn: `docs/audits/rescue-map-live-2026-10-24.json`. Script `scripts/inspect-rescue-map.cjs` chỉ đọc các trường cần thiết, chỉ xuất số đếm; không xuất tên, ID document, tọa độ, credential. Không quét người dùng/đơn hàng. Dữ liệu là snapshot, không tự cập nhật theo thời gian.

## 2. Cơ chế di động hiện có

| Bước | Hành vi thật | Source |
|---|---|---|
| Tạo chiến dịch | Seller chọn FIXED_POINT hoặc MOBILE_POINT từ enum trong form, gửi duyệt | CampaignEditorFragment, CampaignViewModel |
| Mở điểm di động | Màn Seller chỉ hiển thị chiến dịch ACTIVE thuộc MOBILE_POINT | ui/campaign/MobileRescueFragment.java:70–106 |
| Phát vị trí | Bấm “Lấy GPS”, xin quyền vị trí, gọi lấy vị trí một lần | MobileRescueFragment.java:102–126; CampaignViewModel.java:150 |
| Lấy GPS | FusedLocationProviderClient.getCurrentLocation, không chấp nhận fix cache cũ; timeout 15 giây | service/location/AndroidLocationProvider.java:43–96 |
| Ghi | Transaction kiểm quyền sở hữu và ACTIVE/MOBILE; lưu currentLatitude/currentLongitude, bật chia sẻ, locationUpdatedAt bằng serverTimestamp | data/remote/firebase/FirebaseCampaignRepository.java:308–337 |
| Phân quyền | Seller được duyệt, sở hữu chiến dịch; chỉ thay tập trường vị trí và timestamp máy chủ | firestore.rules:924–935 |
| Nhận thay đổi | SnapshotLiveData dùng addSnapshotListener; detail cũng theo dõi campaign document | SnapshotLiveData.java:93; FirebaseCampaignRepository.java:157 |
| Gần bạn | Dùng GPS/vị trí lựa chọn của người xem để tính Haversine đến vị trí mới nhất Seller đã gửi; sắp xếp theo khoảng cách | HomeContentBuilder.java:60–99; CampaignLocationService.java |
| Hết hiệu lực | Chia sẻ tắt, thiếu timestamp, timestamp tương lai hoặc quá 10 phút thì không còn fresh | domain/model/RescueCampaign.java:269–272 |
| Dừng | Seller bấm Dừng; current coordinates về 0, locationUpdatedAt null, sharing false | FirebaseCampaignRepository.java:320–334 |

Điểm di động hiện nay là **vị trí công khai mới nhất do điện thoại Seller gửi cho một chiến dịch**. Nó không lấy từ GPS người mua, không phải thiết bị GPS gắn xe, không có đường đi được theo dõi, không có tự nội suy chuyển động. Nếu Seller ở nơi khác xe thì tọa độ cũng ở nơi khác xe.

Handler chạy mỗi giây ở MobileRescueFragment chỉ đổi nhãn thời gian/nút, **không lấy GPS hay ghi database mỗi giây**. AndroidManifest không có location foreground service; source không gọi requestLocationUpdates cho luồng này. Vì vậy bấm GPS xong rồi lái xe không làm điểm tự di chuyển.

Người mua hiện thấy điểm qua nhóm “di động gần bạn” hoặc chi tiết chiến dịch và mở ứng dụng bản đồ ngoài để chỉ đường. Nhóm gần bạn bị ẩn nếu người xem chưa có vị trí hoặc danh sách rỗng (GuestHomeFragment:451). Home lấy observePublicCampaignPage, ban đầu giới hạn 12 mục (FirebaseCampaignRepository:65–69); không phải truy vấn địa lý toàn bộ vùng. Đây cũng là một nguyên nhân có thể khiến một chiến dịch không xuất hiện dù đã tồn tại ngoài trang đã tải.

## 3. Mức độ đã xác minh

- Có code: form, GPS một lần, transaction, Rules, listener, tính khoảng cách, giới hạn 10 phút, nút Dừng, chặn action vị trí cũ.
- Đã chạy mới: 25 JVM test trong 5 class CampaignLocationServiceTest (2), RescueCampaignTest (3), RescueCampaignBoundaryTest (6), HomeContentBuilderTest (10), CampaignTimeFormatterTest (4); 0 fail/error/skipped. Có biên chính xác 10 phút, quá biên 1 ms, timestamp tương lai, chiến dịch hết hàng vẫn cập nhật vị trí cho đơn đã đặt.
- Firestore Emulator demo-rescuefarm: chọn 1 test `Guest ON_SITE requires a fresh active mobile point and reserves campaign without counting rescued`; pass 1, fail 0, skipped 32 không được cộng vào số pass. Test xác nhận vị trí cũ trả 409; sau cập nhật vị trí mới, checkout trả 200, giữ số lượng chiến dịch nhưng chưa ghi là đã giải cứu.
- Test tích hợp này tạo fixture bằng Admin SDK và gọi API; **không chứng minh nút GPS Android, provider GPS thật hoặc Rules cho thao tác Seller đã qua end-to-end**. Rules nêu trên mới đối chiếu source trong lượt này.
- Chưa thao tác luồng Seller GPS → máy người mua trên Android trong lượt nghiên cứu. Không có dữ liệu MOBILE_POINT production để quan sát đường đi thực tế. Không kết luận tính năng tracking hoàn thiện.

Log trong `docs/audits/rescue-map/`. Lần Gradle đầu lỗi loopback môi trường; chạy lại với JAVA_TOOL_OPTIONS đặt thư mục unixdomain trong workspace đã qua. Lần Firebase CLI đầu lỗi truyền đối số Windows; gọi trực tiếp CLI JavaScript và chọn JDK25 đã qua. Truy vấn live đầu trong sandbox lỗi DNS; lượt cho phép network đã thành công. Không sửa source nghiệp vụ để làm test xanh.

## 4. Giao diện đề xuất

Thêm khối “Bản đồ điểm giải cứu” ở Home, sau khu vực tìm kiếm/bộ lọc và trước các nhóm điểm gần bạn, không thay header/logo. Chiều cao khoảng 200–240 dp, có số điểm và chú giải. Preview không giữ cử chỉ pan/zoom để tránh xung đột cuộn trang; chạm mở Fragment bản đồ toàn màn hình.

Màn lớn có: nút quay lại, đưa về vị trí của tôi, lọc Tất cả/Cố định/Di động; marker cố định và marker di động khác hình lẫn màu, vị trí người xem là chấm riêng. Chạm marker mở bottom sheet chứa tên chiến dịch, địa điểm, khoảng cách nếu có vị trí người xem, thời điểm cập nhật, số lượng còn lại tham khảo, nút Xem chiến dịch và Chỉ đường. Marker chỉ là vị trí hiển thị; mọi mua hàng vẫn qua kiểm tra kho/giá/quyền hiện có.

Nếu nhiều chiến dịch ở cùng điểm cố định, nhóm chúng thành một marker có danh sách chiến dịch. Điểm gần nhau có thể cluster theo mức zoom; không tùy tiện gộp các điểm khác nhau thành một địa chỉ. Mobile giữ campaignId riêng, chưa tự gọi các campaign cùng Seller là một xe vì mô hình hiện chưa có vehicleId.

Không bắt người dùng cấp GPS để xem bản đồ: khi chưa có vị trí vẫn fitBounds các điểm đủ điều kiện; chỉ khoảng cách và nút “về tôi” cần vị trí. Khi rỗng hiển thị lý do và lựa chọn vùng, không dựng marker giả.

## 5. Tái sử dụng map của hồ sơ

EditProfileFragment:223–350 đang dùng Leaflet 1.9.4 được bundle trong assets/maps/leaflet, WebView và tile **Esri World Street Map**, không phải Google Maps SDK và cũng không phải tile OSM tiêu chuẩn. Có thể tách bộ tải thư viện, cấu hình WebView, tile/attribution và trạng thái lỗi thành thành phần dùng chung; mở rộng từ một marker thành danh sách marker.

Map hỗ trợ setLatLng để đổi marker, fitBounds để bao các điểm, invalidateSize khi đổi kích thước hoặc xoay. Không reload toàn HTML khi vị trí thay đổi; giữ marker theo ID, cập nhật riêng tọa độ/nội dung, không kéo bản đồ về vị trí mới nếu người dùng đang khám phá vùng khác. Serialize JSON đúng chuẩn, dựng text popup an toàn, bridge chỉ cho action hợp lệ và asset origin tin cậy. Giữ attribution và xử lý tile lỗi/không có mạng.

Leaflet là thư viện mã nguồn mở; điều đó không đồng nghĩa mọi dịch vụ tile miễn phí vô hạn. Endpoint Esri hiện dùng thuộc legacy raster; Esri công bố World Street Map ngừng dịch vụ tháng 12/2029. Có thể tái dùng kiến trúc hiện tại nhưng nên gom tile URL/attribution vào cấu hình để chuyển nhà cung cấp sau khi xác minh điều khoản; không gắn lời hứa “free vĩnh viễn”. Không tự đổi provider trong lượt nghiên cứu này.

Room giữ metadata/tọa độ không chứng minh tile bản đồ có thể xem offline. Offline hiển thị thời điểm cache, danh sách đã lưu và trạng thái mất mạng; không gọi tọa độ mobile cache là “đang ở đây”. Mặc định ẩn marker mobile đã cũ; có thể bổ sung bộ lọc xem vị trí cuối với màu xám, timestamp và vô hiệu hóa chỉ đường/đặt tại điểm. Không lấy địa chỉ cố định làm fallback cho xe.

## 6. Cập nhật vị trí nên theo cách nào?

Khuyến nghị **phát GPS có chu kỳ + nhận dữ liệu theo sự kiện**, thay vì gửi mỗi giây:

1. Seller chọn chiến dịch và bật “Bắt đầu chia sẻ vị trí xe”. Gửi fix mới đầu tiên khi GPS có chất lượng phù hợp.
2. Lấy mẫu mục tiêu khoảng 15–30 giây; giới hạn gửi tối đa khoảng một lần/30 giây khi xe dịch chuyển đáng kể (gợi ý 25–50 m), và heartbeat tối đa khoảng 60 giây khi đứng yên để không bị hiểu nhầm đã ngừng chia sẻ. Đây là thông số thiết kế khởi đầu, cần đo trên thiết bị; Android không bảo đảm callback đúng từng giây.
3. Người xem dùng Firestore snapshot listener khi màn bản đồ hiển thị; marker thay đổi khi server nhận vị trí mới. Không cần polling database mỗi giây. Nêu rõ “cập nhật … trước”, không gọi là vị trí tức thời tuyệt đối.
4. Để vẫn chia sẻ khi tắt màn hình/mở app khác, dùng foreground service loại location do Seller chủ động khởi động khi app đang foreground, có thông báo và nút Dừng. Nếu chỉ triển khai vòng lặp trong Fragment thì phải nói rõ chỉ cập nhật khi màn còn mở; cách đó chưa phù hợp cho theo dõi xe thực tế. Không dùng WorkManager để yêu cầu nhịp cập nhật 30 giây.
5. Dừng theo nút Seller, đăng xuất, mất quyền hoặc chiến dịch kết thúc/bị dừng. Hết mạng không xếp hàng đẩy các fix cũ lên sau rồi đóng dấu server như vị trí mới; kết nối lại phải lấy fix mới. Tách thời điểm đo fix khỏi thời điểm server nhận nếu bổ sung tracking. Cần bỏ callback cũ sau khi Dừng và tránh phiên/thiết bị cũ ghi đè phiên mới.
6. Giữ ngưỡng backend hiện tại 10 phút làm ranh giới hết hiệu lực; UI có thể cảnh báo sớm khi không nhận heartbeat. Muốn đổi ngưỡng phải đồng bộ domain Android, backend commerce, query/index và test, không chỉ đổi nhãn trên bản đồ.

Chu kỳ GPS và listener là hai cơ chế khác nhau: Firestore realtime chỉ truyền dữ liệu vừa được ghi; nó không tự biết xe đã chuyển vị trí.

## 7. Nguồn dữ liệu và phạm vi triển khai

Giai đoạn bản đồ dùng campaigns hiện có, suy ra RescueMapPoint(campaignId, mode, lat, lng, updatedAt, title, trạng thái), chưa cần collection “điểm” mới. Fixed đọc latitude/longitude; mobile chỉ đọc currentLatitude/currentLongitude khi sharing bật và fresh. Lọc ACTIVE, trong lịch, tọa độ hợp lệ, còn lượng phù hợp và dữ liệu công khai. Không dùng GPS hồ sơ riêng của mọi Seller làm điểm cứu trợ công khai.

Với quy mô hiện tại có thể dùng observeActiveCampaigns riêng cho map rồi lọc, tách khỏi public page 12 mục của Home. Không chỉ lấy getNearbyMobile/getNearbyFixed vì chúng phụ thuộc vị trí người xem và trang đã tải. Nếu số chiến dịch tăng, chuyển sang truy vấn vùng/geohash hoặc public projection nhỏ; không duy trì listener toàn bộ campaign mọi nơi trong app. Gắn listener theo lifecycle màn bản đồ, xử lý cache/freshness và expiry bằng đồng hồ ngay cả khi không có snapshot mới.

Trình tự đề xuất: (1) map preview/fullscreen + marker fixed/mobile từ model; (2) bật/tắt phiên chia sẻ GPS tự động; (3) demo hai client trong emulator độc lập. Không cần sửa lịch sử hay chuyển 9 chiến dịch hiện có sang mobile. Tạo một campaign demo đúng luồng Seller → duyệt → ACTIVE, chuyển vị trí giả lập A sang B và quan sát máy người mua; chạy riêng trong project demo.

Tiêu chí chấp nhận: thấy đủ điểm ngoài trang đầu; không cấp GPS vẫn xem được; marker mobile di chuyển đúng ID; đứng yên vẫn heartbeat; ngừng chia sẻ và quá 10 phút cập nhật UI đúng; dữ liệu cũ không dẫn đường; owner khác không ghi được; kết thúc campaign dừng tracking; callback sau Stop không bật lại vị trí; offline/reconnect không tái phát GPS cũ; xoay màn không mở hai listener/service; chạm marker tới đúng campaign; lỗi tile và attribution rõ ràng.

## 8. Tài liệu kỹ thuật đối chiếu

- [Leaflet API 1.9.4](https://leafletjs.com/reference.html): marker.setLatLng, fitBounds, invalidateSize.
- [FusedLocationProviderClient](https://developers.google.com/android/reference/com/google/android/gms/location/FusedLocationProviderClient): lấy fix đơn lẻ và requestLocationUpdates cho cập nhật liên tục.
- [Android: giới hạn vị trí nền](https://developer.android.com/about/versions/oreo/background-location-limits): không bảo đảm tần suất lấy vị trí nền như foreground.
- [Android codelab: while-in-use location](https://codelabs.developers.google.com/codelabs/while-in-use-location): foreground service location và quyền phù hợp.
- [Firestore snapshot listener](https://firebase.google.com/docs/firestore/query-data/listen): nhận snapshot ban đầu và thay đổi sau đó; không phải bộ phát GPS.
- [Esri: Sunsetting Legacy Basemaps, 17/02/2026](https://www.esri.com/arcgis-blog/products/arcgis-living-atlas/announcements/sunsetting-legacy-basemaps): lịch nghỉ World Street Map tháng 12/2029.
