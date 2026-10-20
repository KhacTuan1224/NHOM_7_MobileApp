# Giải cứu mở rộng — triển khai, setup và cách dùng

## Phạm vi đã triển khai

Mở **Giải cứu mở rộng** từ Home của Guest/Customer hoặc dashboard Seller/Admin. Đây là module Android Java theo MVVM, dùng API Express trên Render và giao dịch Firestore. Không thêm nền tảng mới, không thay đổi checkout/SePay hiện tại, không cần Room migration.

| Phương thức | Luồng có thể dùng |
| --- | --- |
| Hộp bí ẩn | Seller chọn nhiều lô thật, lượng trong mỗi hộp, phân loại và thông tin thành phần/dị ứng; backend giữ tồn kho. Customer thấy phân loại/lưu ý, nhận tại điểm; thành phần thực tế được mở trong lịch sử sau khi nhận. |
| Mua chung | Cam kết theo số suất, ngưỡng **cả số người khác nhau và số suất**; chia sẻ deep link; thông báo khi đạt ngưỡng; chốt khi đủ điều kiện; thất bại trả tồn kho. |
| Định kỳ | Theo dõi theo cửa hàng + `planKey`, chu kỳ 7 hoặc 30 ngày, nhắc đến kỳ, chọn gói mới và xác nhận giá từng kỳ, tạm dừng/tiếp tục. |
| Sỉ B2B | Customer loại BUSINESS gửi yêu cầu, Seller đề nghị giá riêng có phiên bản và hạn, Customer chấp nhận đúng phiên bản; chỉ lúc chấp nhận mới giữ suất. |
| Quyên góp | Gói 0 đồng; Customer gửi hồ sơ tổ chức; Admin đối chiếu và duyệt/từ chối/thu hồi xác thực. Chỉ tổ chức VERIFIED được đăng ký nhận. |
| Giờ chót | Gói FLASH tối đa 2 giờ; lọc trong 5 km từ Fused Location Provider; mở điểm nhận trên ứng dụng bản đồ; chủ động bật FCM gần đây trong 24 giờ, tắt/xóa vị trí bất kỳ lúc nào. |

Các gói mới dùng **nhận tại điểm và trả tiền trực tiếp khi nhận**. Đăng ký nhóm không thu tiền trước, vì vậy nhóm thất bại không phát sinh giao dịch hoàn tiền. Đây là lựa chọn triển khai hiện tại, không phải tích hợp giữ tiền/hoàn tiền qua SePay.

Định kỳ là đăng ký nhắc và xác nhận từng kỳ, **không tự động thu tiền/tạo đơn khi người dùng không xác nhận**. Scheduler tin cậy ở backend xử lý nhắc; WorkManager không được dùng để âm thầm mua hàng khi ứng dụng nền bị hệ điều hành hạn chế.

Các lượt mới được quản lý trong **Suất của tôi / Gói cửa hàng**, lưu ở `rescueClaims`, chưa hợp nhất vào `orders`/danh sách đơn hàng thông thường. Phương thức vận chuyển hiện tại của module là PICKUP, chưa giao hàng tận nơi cho gói mới.

## 1. Deploy backend

Từ thư mục repository:

```powershell
npm --prefix functions ci
npm --prefix functions run check
npm --prefix functions run test:unit
```

Trên Render dùng Web Service hiện có:

- Root Directory: `functions`.
- Build Command: `npm ci`.
- Start Command: `npm start`.
- Giữ `FIREBASE_SERVICE_ACCOUNT_JSON` hiện có; service account cần Firestore và quyền gửi FCM.
- Thêm/giữ `NOTIFICATION_WORKER_SECRET`, chuỗi ngẫu nhiên tối thiểu 32 ký tự. Không đưa secret này vào APK hoặc Git.
- Deploy commit chứa thay đổi này.

Kiểm tra khả năng của bản deploy:

```powershell
$backend = 'https://YOUR-SERVICE.onrender.com'
Invoke-RestMethod "$backend/health"
```

Kết quả cần có:

```json
{
  "ok": true,
  "rescueProgramsVersion": 1,
  "rescuePaymentPolicy": "PICKUP_CASH_NO_PREPAYMENT"
}
```

Đường dẫn trên không cần đăng nhập. Không gọi các API giữ suất bằng service account từ Android; app dùng Firebase ID token của người đang đăng nhập.

## 2. Deploy Firestore Rules và indexes

Kiểm tra đúng project Firebase của bạn trước khi deploy:

```powershell
.\tests\firestore\node_modules\.bin\firebase.cmd deploy --only firestore --project YOUR_FIREBASE_PROJECT_ID
```

Chờ indexes mới trong Firebase Console → Firestore Database → Indexes chuyển sang **Enabled** trước khi thử scheduler/gói định kỳ. File `firestore.indexes.json` bổ sung các index cho hạn xử lý, tìm gói định kỳ, FLASH, claims và báo giá.

Không phải tạo collection rỗng hoặc seed hàng loạt. Dữ liệu xuất hiện sau thao tác thực tế đầu tiên. Dữ liệu cũ không cần chuyển `productType`; module dùng một lớp gói phủ trên `productBatches` để giữ đúng nguồn tồn kho của nhiều thành phần.

Các collection `rescueOffers`, `rescueClaims`, `rescueQuotes`, `rescueSubscriptions`, `rescueOrganizations`, `rescueNearbyPreferences`, `rescueImpact` đều **chặn đọc/ghi Firestore trực tiếp từ mọi vai trò trên client**. API chỉ trả dữ liệu được phép xem. Việc Admin trên app vẫn phải đi qua backend là chủ ý.

## 3. Bật scheduler đã có trong repo

Module tận dụng `.github/workflows/notification-worker.yml`, không cần thêm dịch vụ scheduler riêng.

1. Trong Render → Environment đặt `NOTIFICATION_WORKER_SECRET`.
2. GitHub → Repository → Settings → Secrets and variables → Actions:
   - **Repository variable** `RENDER_BACKEND_URL`: URL HTTPS backend, không thêm `/health`.
   - **Repository secret** `NOTIFICATION_WORKER_SECRET`: đúng giá trị trên Render.
3. Đưa workflow lên default branch. Trong Actions bật workflow **Notification worker wakeup** nếu đang tắt.
4. Chọn **Run workflow** và kiểm tra job thành công.

Workflow gọi `POST /internal/notifications/run` mỗi 5 phút theo lịch cấu hình. Endpoint này xử lý đóng nhóm/hết giờ nhận, nhắc định kỳ, thông báo gần đây, rồi gửi outbox. Trong khi Render đang chạy, vòng xử lý nội bộ cũng chạy mỗi phút.

Khi kiểm tra thủ công, lấy secret từ biến môi trường của phiên shell, không chép nó vào source:

```powershell
$headers = @{ 'X-Worker-Secret' = $env:NOTIFICATION_WORKER_SECRET }
Invoke-RestMethod "$backend/internal/notifications/run" -Method Post -Headers $headers -ContentType 'application/json' -Body '{}'
```

Phản hồi có `rescuePrograms` với số gói đã xử lý, số đăng ký và vị trí được xét. Kiểm tra thêm `attentionRequired` của worker và logs Render nếu có lỗi. Timer dừng khi Render Free ngủ; GitHub schedule cũng có thể trễ. Không hứa thông báo chính xác từng phút. Backend luôn kiểm tra giờ trên server khi đặt/nhận nên scheduler chậm không cho mua hoặc nhận hàng đã hết hạn; tồn đang giữ có thể được trả chậm cho đến lần reconcile tiếp theo.

## 4. Build và cấu hình Android

Giữ cấu hình Firebase Android của môi trường tại `app/google-services.json`. Trong Gradle properties đang dùng:

```properties
RENDER_BACKEND_URL=https://YOUR-SERVICE.onrender.com
```

Sau đó:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

APK debug: `app/build/outputs/apk/debug/app-debug.apk`.

Không cần Maps API key: màn hình cung cấp danh sách gói gần bạn và mở **điểm nhận** bằng `geo:` trên ứng dụng bản đồ có sẵn, dự phòng bằng Google Maps URL. Chưa có bản đồ marker nhúng trong app.

Thông báo cần cấu hình FCM hiện có của dự án, đăng ký token thiết bị và quyền thông báo Android 13+. Nút **Thông báo giờ chót** yêu cầu quyền thông báo và quyền vị trí khi dùng. Không yêu cầu quyền vị trí nền.

### Nếu Windows gặp `Unable to establish loopback connection`

Trên máy phát triển này, Java gặp lỗi Unix socket nội bộ. Đã chạy được build và Firestore Emulator bằng cấu hình **chỉ cho phiên PowerShell hiện tại** sau:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
# Đường dẫn cố ý không tồn tại để Java chuyển pipe nội bộ sang TCP.
# Không tạo thư mục no-unix-sockets.
$env:JAVA_TOOL_OPTIONS = '-Djdk.net.unixdomain.tmpdir=D:\NHOM_7_MobileApp\build\no-unix-sockets'
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --no-daemon
```

Điều chỉnh đường dẫn repo/JDK nếu bạn dùng máy khác. Chỉ dùng workaround này khi gặp đúng lỗi socket; không cần sửa Gradle config của toàn dự án. Đóng shell hoặc `Remove-Item Env:JAVA_TOOL_OPTIONS` để bỏ cấu hình phiên.

## 5. Cách dùng và kịch bản thử từng tính năng

### Chuẩn bị Seller và nguồn hàng

1. Đăng nhập Seller đã được Admin duyệt, có sản phẩm ACTIVE và lô AVAILABLE/RESERVED_PARTIAL còn đủ lượng.
2. Đảm bảo lô đã thu hoạch, còn hạn sau **kết thúc nhận hàng**, chưa gắn campaign. Lô còn thuộc campaign cũ cần kết thúc/gỡ liên kết bằng luồng campaign hiện có trước.
3. Dashboard Seller → Giải cứu mở rộng → **Tạo gói giải cứu**.
4. Điền phương thức, phân loại, thông tin thành phần/dị ứng, số suất, giới hạn mỗi khách, giá trị gốc, giá giải cứu, giờ và địa chỉ/GPS điểm nhận.
5. Thêm lô, nhập lượng **trong một suất**. Ví dụ 0,5 kg rau + 0,2 kg củ mỗi hộp, 10 hộp sẽ giữ 5 kg rau và 2 kg củ.
6. Kiểm tra tổng lượng cần giữ không vượt tồn khả dụng. Giá trị gốc mỗi suất không được lớn hơn tổng giá gốc các thành phần. Tạo gói.

Mỗi gói tối đa 12 lô và 100 đăng ký; nhóm mua chung tối đa 50 người. Mỗi Customer có một bản đăng ký cho mỗi gói, gửi lại cùng lựa chọn là replay. Sau khi hủy, không đăng ký lại cùng gói; chọn gói mới của Seller. B2B tối đa 100 đối tác yêu cầu báo giá cho mỗi gói.

Gói đã tạo giữ nguyên giá/nội dung/giờ nhận. Muốn thay đổi hãy dừng gói và tạo gói mới; kiểm tra người đã đăng ký trước khi dừng. Gói dừng chỉ trả phần chưa nhận, không đảo lịch sử đã nhận. Khi mất kết nối sau tạo, gửi lại **nguyên nội dung** với cùng màn hình để backend chống trùng; kiểm tra “Gói cửa hàng” trước khi tạo lại từ màn hình mới.

### Hộp bí ẩn

- Seller chọn MYSTERY_BOX; mô tả nhóm nông sản và những thành phần có thể gây dị ứng.
- Customer xem phân loại, giá, lượng suất và điểm nhận, không thấy tên từng lô trong API công khai.
- Customer giữ suất → Seller vào gói, đến khung giờ nhận bấm **Đã chuẩn bị, mời khách đến nhận**.
- Customer tới điểm, kiểm tra hàng, trả tiền tại điểm và bấm **Xác nhận đã nhận nông sản**. Sau đó có danh sách thành phần mỗi suất trong chi tiết lịch sử.
- Trạng thái thanh toán là **khách xác nhận trả tiền trực tiếp** (`BUYER_CONFIRMED_CASH`), không phải ngân hàng đã xác minh hoặc webhook SePay.

### Mua chung

- Ví dụ mỗi suất 5 kg dưa hấu; tổng 100 suất; ngưỡng 10 người + 100 suất tương đương 500 kg. Phải có đủ tồn cho 100 suất.
- Đặt giờ bắt đầu nhận sau hoặc bằng hạn đăng ký; mọi người nhận ở cùng địa chỉ/GPS của gói.
- Customer giữ số suất và chia sẻ `rescuefarm://rescue/<offerId>` qua share sheet. Link mở app đã cài; chưa có trang web cài đặt/Universal Link cho thiết bị chưa cài RescueFarm.
- Trước khi đạt ngưỡng có thể rút cam kết. Khi đã đạt cả hai ngưỡng, hệ thống gửi thông báo và khóa rút cam kết để không làm vỡ nhóm.
- Đến hạn đăng ký: thiếu ngưỡng → FAILED, hủy đăng ký và trả kho; đủ ngưỡng → LOCKED, trả phần suất dư, giữ đúng phần đã cam kết đến giờ nhận.
- Sau giờ nhận, các suất chưa nhận bị hủy và trả kho. Không có khoản thu trước hoặc hoàn tiền tự động.

### Đăng ký định kỳ

- Seller tạo SUBSCRIPTION và nhập mã ổn định, ví dụ `rau-tuan`. Mỗi tuần Seller bổ sung một gói **mới** bằng lô mới, giữ cùng mã.
- Customer vào gói → **Theo dõi gói định kỳ** → chọn 7 hoặc 30 ngày.
- Đến kỳ nhận được nhắc. Vào **Gói định kỳ** → **Xem và đặt suất kỳ này**, kiểm tra giá rồi xác nhận. Sau khi đặt thành công, kỳ tiếp theo được tính từ lúc xác nhận + số ngày đăng ký.
- Không dùng lại gói đã nhận cho kỳ sau. Nếu Seller chưa đăng gói mới hoặc hết hàng, hiển thị chưa có gói phù hợp; không tạo tồn kho giả và không tự thu tiền.
- Tạm dừng chỉ dừng nhắc tương lai; suất đã đặt được quản lý riêng trong “Suất của tôi”.

### Thương lượng sỉ

- Dùng Customer đăng ký loại **doanh nghiệp** trong luồng tài khoản hiện có; `customerType=BUSINESS`. Customer cá nhân không được gửi yêu cầu.
- Seller tạo BULK, nêu rõ đơn vị mỗi suất, ví dụ “phần 10 kg”; giá trên thẻ là giá khởi điểm.
- Customer gửi số suất và lời nhắn → Seller xem báo giá riêng, nhập giá đề nghị mỗi suất → Customer chấp nhận.
- Báo giá có hạn tối đa 24 giờ và không vượt hạn gói. Thay giá làm tăng revision; chấp nhận bản cũ bị từ chối, yêu cầu tải lại.
- Báo giá không giữ tồn cho riêng đối tác cho đến lúc chấp nhận. Nếu lúc chấp nhận không còn đủ suất, giao dịch thất bại nguyên vẹn.
- Giá đã chấp nhận được chụp vào đăng ký; người mua khác không xem được báo giá, hồ sơ công ty hoặc lời nhắn của đối tác.

### Quyên góp

- Customer đại diện tổ chức vào tab **Tổ chức từ thiện**, gửi tên, mã đăng ký, thông tin liên hệ và thông tin để đối chiếu.
- Admin dashboard → Giải cứu mở rộng → **Xác minh tổ chức**. Đối chiếu hồ sơ thực tế, ghi lý do rồi VERIFIED/REJECTED. Các nút trạng thái cho phép xem lại và thu hồi xác thực.
- Seller tạo DONATION, giá giải cứu 0. Chỉ Customer có hồ sơ VERIFIED được giữ suất.
- Chuẩn bị/nhận hàng giống luồng nhận tại điểm, không có yêu cầu trả tiền.
- Khi Customer xác nhận nhận, thống kê Seller tăng số lượt và lượng nông sản quyên góp theo đơn vị. Không tự cấp chứng từ thuế, xác nhận khấu trừ hoặc giảm phí nền tảng.

### Giải cứu giờ chót và vị trí

- Seller tạo FLASH, hạn đăng ký trong vòng tối đa 2 giờ; giờ nhận phải nằm trước hạn dùng mọi lô. Chọn vị trí điểm nhận đang dùng, kể cả điểm di động; vị trí gói là **snapshot**, chưa bám theo GPS Seller sau khi tạo.
- Customer/Guest có thể bấm **Tìm quanh tôi**; kết quả trong 5 km, sắp khoảng cách trong phần đã tải. Bấm “Xem thêm” để quét tiếp trang dữ liệu, không hiểu trang đầu là toàn bộ cửa hàng quanh bạn.
- Customer chủ động bật **Thông báo giờ chót**. Backend chỉ xét FLASH còn suất trong bán kính, không gửi trùng cùng gói cho cùng Customer. Seller tự chọn khung giờ, không ép tất cả cửa hàng phải sau 20:00.
- Vị trí được lưu riêng, hiệu lực tối đa 24 giờ từ lần bật; không cập nhật nền. Tắt xóa ngay; worker cũng xóa bản ghi hết hạn khi chạy, nên thời điểm xóa vật lý có thể trễ nếu scheduler/Render ngừng. Dữ liệu hết hiệu lực không được dùng gửi thông báo. Worker kiểm tra lại opt-in và khoảng cách khi xếp hàng và trước gửi.
- Gói giờ chót đã hết hạn/hết suất không tiếp tục gửi thông báo gần đây. Thông báo đến muộn không bỏ qua kiểm tra giờ nhận phía server.

## 6. Dữ liệu và tính nhất quán

| Collection | Nội dung / quyền qua API |
| --- | --- |
| `rescueOffers` | Thành phần lô riêng của Seller, snapshot tên/đơn vị, giá, giờ, điểm nhận, số suất và trạng thái. Public API chỉ trả các trường đã chọn. |
| `rescueClaims` | Một bản/Customer/gói; số suất, giá snapshot, trạng thái, xác nhận nhận và thanh toán tại điểm. Chỉ khách sở hữu và Seller của gói đọc. |
| `rescueQuotes` | Một báo giá/đối tác/gói; REQUESTED → PROPOSED → ACCEPTED, revision và validUntil. |
| `rescueSubscriptions` | Customer, Seller, planKey, cadenceDays, nextDueAt, ACTIVE/PAUSED. |
| `rescueOrganizations` | Hồ sơ đối chiếu và quyết định Admin, không công khai. |
| `rescueNearbyPreferences` | Điểm chủ động bật, bán kính, thời điểm hết hạn; chỉ trusted backend truy cập. |
| `rescueImpact` | Lượt nhận và lượng đã nhận/quyên góp theo từng đơn vị vật lý. Không cộng vào số kg campaign một cách suy diễn. |

Tạo gói: `availableQuantity` của từng lô giảm, `reservedQuantity` tăng trong cùng giao dịch tạo offer. Khi nhận: reserved → sold. Khi hủy/hết hạn: reserved → available, đồng thời tính lại trạng thái lô; lô đã hết hạn vẫn EXPIRED. Tổng `initial = available + reserved + sold` luôn được kiểm tra.

Các trường số suất ở offer tuân theo `totalUnits = availableUnits + claimedUnits + completedUnits + releasedUnits`. Phần suất trả về offer trước hạn chưa trả physical inventory vì vẫn thuộc tổng kho giữ của gói; sau hạn sẽ trả về kho vật lý. Lô được ghi `everOrdered` để giữ lịch sử, cần archive thay vì sửa phá tham chiếu.

Các giao dịch checkout thường vẫn chỉ thấy lượng **available** còn lại. Module không nhân bản tồn kho thành “sản phẩm combo” và không sử dụng stock đang phân bổ cho campaign để tạo gói mới.

## 7. API chính

Mọi route là POST với JSON. Route cần đăng nhập dùng `Authorization: Bearer <Firebase ID token>`. Không truyền UID thay cho token ở môi trường thật.

| API dưới `/rescue/` | Quyền |
| --- | --- |
| `catalog`, `detail`, `impact` | Public, trả projection công khai. `detail` có token sẽ thêm đúng claims/quotes của người gọi. |
| `create`, `batches` | Seller ACTIVE/APPROVED. |
| `mine` | Customer xem suất của mình; Seller xem gói của cửa hàng. |
| `join`, `quotes/request`, `quotes/accept` | Customer; sỉ cần BUSINESS; quyên góp cần VERIFIED. |
| `claims`, `claims/action` | Seller sở hữu chuẩn bị; Customer sở hữu hủy/xác nhận nhận theo điều kiện. |
| `close` | Seller sở hữu hoặc Admin. |
| `quotes/list`, `quotes/propose` | Đúng bên trong báo giá; chỉ Seller gửi giá. |
| `subscriptions/save`, `subscriptions/list`, `subscriptions/pause` | Customer sở hữu. |
| `organizations/apply`, `organizations/list` | Customer xem/gửi hồ sơ của mình; Admin xem theo trạng thái. |
| `organizations/review` | Admin. |
| `nearby/preferences` | Customer; `{enabled:false}` xóa vị trí. |

Danh sách public và danh sách cá nhân có cursor 30 bản ghi/trang; có thể có trang không khớp bộ lọc nhưng vẫn `hasMore=true`. Một gói giới hạn 100 claims/quotes để xử lý kết thúc có giới hạn rõ ràng. Bộ chọn nguồn hàng đọc 30 sản phẩm/trang, tối đa 100 lô cho mỗi sản phẩm trong trang đó; nếu sản phẩm có nhiều hơn cần thu gọn/archive lịch sử lô hoặc mở rộng phân trang nguồn hàng trước khi vận hành ở quy mô đó.

## 8. Kiểm thử

Không dùng service account production khi chạy suite. Bộ giao dịch mới từ chối chạy nếu không có `FIRESTORE_EMULATOR_HOST` trên localhost và luôn dùng project `demo-rescuefarm`.

```powershell
npm --prefix functions ci
npm --prefix tests/firestore ci
npm --prefix functions run check
npm --prefix functions run test:unit
npm --prefix tests/firestore test
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
python scripts/check_navigation.py
```

Suite gồm: nguồn kho thật/chống giữ trùng, khách mua đồng thời, replay, thời gian và quyền nhận, hoàn tất một lần, nhóm thiếu/đủ ngưỡng, trả kho hết hạn, hồ sơ tổ chức, báo giá riêng theo phiên bản, nhắc định kỳ không tự mua, vị trí có thời hạn/opt-out, Firestore Rules chặn ghi giả ledger. Kiểm thử thông báo kiểm tra lại việc tắt vị trí trước delivery.

Chưa có thiết bị/emulator Android được kết nối trong phiên triển khai này, nên cần kiểm tra UI và FCM thực tế theo các kịch bản trên sau khi deploy backend/indexes. Build và unit tests không thay thế kiểm thử nhận hàng giữa hai thiết bị.

## Chưa triển khai trong phiên bản này

- Thu tiền trước, escrow, chia tiền nhóm, tự động hoàn tiền ngân hàng; vận chuyển chung tới một người đại diện.
- Auto-debit và tạo đơn định kỳ không cần xác nhận; kế toán công nợ doanh nghiệp.
- Chứng từ khấu trừ thuế, giảm phí hoặc hệ thống reputation cộng dồn vào storefront hiện tại. Đã có thống kê donation riêng để mở rộng sau.
- Bản đồ nhúng, marker thời gian thực và cập nhật GPS điểm di động vào gói đã công khai.
- Web landing page cho link chia sẻ khi chưa cài app, ảnh riêng của hộp, lưu offline riêng cho module mới.

Các phần này cần quy trình tài chính/vận hành và tích hợp bổ sung. Phiên bản hiện tại không giả lập kết quả thành công cho các chức năng đó.
