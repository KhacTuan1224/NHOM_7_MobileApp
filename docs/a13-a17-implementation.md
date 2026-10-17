# A13–A17: triển khai, cấu hình và nghiệm thu

Đối chiếu Function 5.1/6.2/9.3/10.3, U9, BR 12–13, phần 20/34 và quyết định Campaign ở cuối `final.md`. Không thay đổi Bottom Navigation và không mở lại luồng Post công khai.

## A13 — Báo cáo ngay tại nội dung

- Review đã công khai có nút **Báo cáo đánh giá** cho Customer đang đăng nhập và không phải chủ Review.
- Hồ sơ Seller công khai có **Báo cáo người bán**, truyền `USER` và UID Seller thực tế. Không cho tự báo cáo mình; Guest/Seller/Admin không được mở thêm thao tác Customer này.
- Bình luận Campaign giữ lối báo cáo của A11, truyền đủ đường dẫn Campaign/comment. Form tách ngữ cảnh nội bộ và không hiển thị/cho nhập type, ID hoặc parent ID nữa.
- Form có ngữ cảnh chỉ hỏi lý do (tối đa 120 ký tự) và mô tả (1.000 ký tự). Nếu mở trống, hướng dẫn chọn Báo cáo ngay tại nội dung và giữ nút **Báo cáo của tôi**.
- Report/inbox Admin vẫn ghi trong cùng commit; Admin đọc đúng Review/USER hoặc parent Campaign theo A11. Không đưa dữ liệu riêng tư của hồ sơ Customer vào hồ sơ Seller công khai.

## A14 — Hồ sơ doanh nghiệp

- Loại tài khoản là loại đã chọn lúc đăng ký; không cho đổi customerType qua màn chỉnh sửa. BUSINESS thấy tên doanh nghiệp và mã số thuế trong hồ sơ của chính mình và có thể sửa cả hai.
- Đăng ký và chỉnh sửa dùng cùng giới hạn: tên doanh nghiệp 2–160 ký tự, mã số thuế không rỗng và tối đa 40 ký tự. Đây là kiểm tra dữ liệu đầu vào của ứng dụng, không phải xác minh pháp lý mã số thuế.
- INDIVIDUAL chỉ lưu hồ sơ thông thường, không bị bắt nhập thông tin doanh nghiệp.
- Repository cập nhật bằng transaction/whitelist, giữ các trường hiện có; không gửi role/status/customerType hoặc ghi đè toàn bộ User document. Validator được dùng chung qua `ProfileInputPolicy`.
- Rules khóa role/status/customerType và chỉ cho BUSINESS sửa companyName/taxCode trong nhánh chủ sở hữu; các loại tài khoản khác không được tự thêm/sửa dữ liệu doanh nghiệp. Dữ liệu BUSINESS phải đầy đủ cả khi tạo mới.
- Khối doanh nghiệp chỉ ở Profile riêng. Đã giữ trạng thái form khi xoay màn hình, bao gồm các trường doanh nghiệp, tên, điện thoại, avatar và tọa độ. Không tăng phiên bản Room.

## A15 — Thời gian và vị trí có vòng đời

- `CampaignTimeFormatter` dùng chung cho Detail và Mobile Rescue: thời gian tới lúc bắt đầu/kết thúc, tuổi vị trí và thời điểm cập nhật.
- Ticker chạy mỗi giây khi Fragment STARTED, dừng ở STOPPED; Mobile Rescue cập nhật nhãn/nút tại chỗ, không dựng lại toàn bộ danh sách mỗi giây.
- Qua ngưỡng 10 phút, vị trí không còn được dùng để chỉ đường/đặt hàng; callback GPS đến trễ cũng phải kiểm tra lại freshness và Campaign hiện tại.
- Qua deadline, thao tác mua và tương tác phụ thuộc chiến dịch đang diễn ra bị vô hiệu. Trước giờ bắt đầu không nhận đơn.
- Phân biệt lượng hàng với thời gian: hết lượng có thể nhận mới thì không đặt thêm; Seller vẫn cập nhật GPS cho các đơn đã giữ chỗ khi chiến dịch chưa kết thúc. Không khóa GPS chỉ vì toàn bộ hàng đang được reservation.
- Quote/checkout backend tiếp tục kiểm tra trạng thái, start/end, tồn và freshness; nhãn UI không cấp quyền hay thay thế kiểm tra server.

## A16 — Mua nhiều theo tier thật

- Giữ nhóm **Nông sản giá tốt** và thêm nhóm **Mua nhiều giá tốt**. Thứ tự section có VOLUME_OFFERS riêng.
- Quan sát Promotion theo sản phẩm hiện có, kiểm tra active/start/end, cùng Seller/Product và tier có giảm giá thật.
- Chỉ quảng bá một tier có đủ lượng trong cùng lô còn bán được. Lô gắn Campaign còn phải có Campaign hợp lệ, đơn vị đã xác minh, vị trí hợp lệ nếu mobile và đủ phân bổ theo batch.
- Card ghi đúng số lượng/đơn vị và phần trăm, kể cả số thập phân; giá tại tier được tính bằng PricingService, cùng công thức với backend quote. Phần trăm là giảm thêm sau ưu đãi cơ bản, giá hiển thị chưa gồm phí giao hàng.
- Chạm card truyền productId, batchId, campaignId và số lượng tier sang Detail; Detail tính giá theo số lượng đó thay vì luôn tính cho 1 đơn vị.
- Mốc bắt đầu/kết thúc đã đọc được có timer riêng để dựng lại Home tại mốc đó; timer dừng khi rời màn hình. Nếu quyền đọc Promotion bị rút, Home bỏ ưu đãi đã cache. Listener tự thử lại mỗi 60 giây khi lỗi và dừng khi không còn observer.
- Guest giữ quyền đọc hiện hành: không đọc được Promotion tương lai/không công khai. Ưu đãi mới công khai sau đó xuất hiện qua lần thử lại, không quảng bá trước giờ bắt đầu.

## A17 — Ảnh Campaign không ghi đè bản duyệt

### Luồng upload và lưu

1. Android gửi ảnh nhị phân tới Worker `POST /upload-campaign?resourceId=...&slot=0..5` cùng Firebase ID token.
2. Worker xác minh tài khoản ACTIVE/SELLER APPROVED, kiểm tra byte thực tế tối đa 10 MiB, chữ ký định dạng và MIME của JPG/PNG/WebP/GIF. Không nhận URL tệp từ client.
3. Worker gọi backend `/media/campaign/authorize`. Transaction kiểm tra Seller, chủ Campaign và trạng thái DRAFT/REJECTED. Campaign mới dùng `campaignMediaDrafts` để giữ ownership cho UUID nháp ngay trước khi có Campaign document hoàn chỉnh. Tối đa 30 lần cấp upload cho một bản nháp mỗi ngày.
4. Backend tạo lease riêng. Worker upload tới Cloudinary với public ID mới chứa UUID và **overwrite=false**. Client không nhận chữ ký để thay tệp/kích thước/tham số của Campaign upload.
5. Worker hoàn tất lease qua `/media/campaign/uploaded` bằng secret nội bộ. Backend kiểm tra lại trạng thái Campaign; nếu Campaign đổi sang chờ duyệt/ACTIVE giữa lúc tải thì ảnh mới không được chấp nhận để lưu.
6. Android lưu/gửi duyệt qua `/campaigns/save`. Backend kiểm tra dữ liệu nguồn, đơn vị, các lô, ảnh/lease/ownership rồi atomically lưu Campaign, đánh dấu lease COMMITTED và tạo event kiểm duyệt khi submit. Trường đếm/phân quyền từ client không được tin cậy.
7. Rules không cho client tự thêm/thay/xóa imageUrls/imagePublicIds. Submit Campaign có ảnh cũng qua command. ACTIVE chỉ có đường cập nhật mobile/kiểm duyệt hiện hành; ảnh/câu chuyện không được sửa bởi Seller.
8. Approve kiểm tra lại nguồn ảnh đúng cloud/namespace của Campaign; ảnh mới phải có lease COMMITTED. Ảnh cũ bên ngoài namespace cần tải lại qua pipeline an toàn trước khi duyệt mới.

### Ảnh legacy dùng chung với Product/avatar

- Startup backfill các public ID đang chờ duyệt/đã duyệt/đã kết thúc vào `campaignAssetReferences`, kể cả URL cũ thiếu imagePublicIds. Marker `systemMigrations/campaignAssetReferencesV1` tránh quét lại mỗi lần khởi động.
- Submit/approve mới ghi projection bảo vệ trong cùng transaction. API chỉ bắt đầu lắng nghe sau startup migration; thất bại thì không mở một backend bảo vệ dở dang.
- Signer thông thường hỏi `/media/asset-guard` trước khi ký/xóa. Nếu một Product/avatar/banner/... đang được Campaign tham chiếu, upload mới dùng ID phiên bản mới và overwrite=false; delete của asset được bảo vệ bị từ chối. Vì vậy không thể đi vòng bằng signer Product để ghi đè ảnh Campaign legacy.
- Registry và lease là dữ liệu vận hành riêng, client kể cả Admin không được đọc/ghi trực tiếp. Không thêm business/domain class vào mô hình.

### Cancel, cleanup và retry

- Tải ảnh rồi rời form không thay danh sách ảnh của Campaign đã lưu. Upload chưa commit hết hạn sau 24 giờ; ảnh bị gỡ khỏi bản nháp được đánh dấu DETACHED, cũng có khoảng chờ 24 giờ.
- Cleanup được gọi bởi vòng backend 60 giây và endpoint heartbeat đã có của GitHub Actions. Mỗi lượt xử lý tối đa 25 lease đến hạn.
- Transaction claim trạng thái DELETING kiểm tra lại tham chiếu Campaign trước khi xóa; save không thể lấy một lease đang DELETING. Nếu commit trước, cleanup giữ ảnh. Nếu cleanup claim trước, save buộc tải ảnh mới.
- Chỉ backend tin cậy gọi Worker `/internal/delete-campaign`. Xóa lỗi giữ retryCount/lastError và hẹn thử lại sau 15 phút; `ok` và `not found` đều thành công. Lỗi cleanup được đưa vào attentionRequired của heartbeat.
- Ảnh static cũ không có lease không bị tự xóa. URL cũ nằm ngoài Cloudinary do ứng dụng kiểm soát không thể được làm bất biến tại nhà cung cấp bên ngoài; cần Admin rà lại và yêu cầu tải lại trước khi duyệt mới. Không tự thay ảnh ACTIVE bằng migration.

## Cấu hình bắt buộc trước khi dùng bản mới

Không có deploy hay thay đổi production nào được thực hiện trong lượt này. Không cần thêm dịch vụ trả phí, KV, Durable Objects hoặc Firebase TTL/Scheduler; cleanup dùng backend/heartbeat hiện có và vẫn chịu quota dịch vụ đang sử dụng.

### 1. Tạo shared secret

Chạy ở máy của bạn, không gửi secret vào chat hoặc commit:

```powershell
node -e "console.log(require('crypto').randomBytes(32).toString('hex'))"
```

Lưu cùng giá trị vào:

- **Render Environment:** `MEDIA_WORKER_SECRET`.
- **Cloudflare Worker secret:** `npx wrangler secret put MEDIA_WORKER_SECRET` trong thư mục cloudinary-worker.

Secret này khác giao thức `NOTIFICATION_WORKER_SECRET`; giữ nguyên heartbeat đang dùng.

### 2. Render Environment

```text
MEDIA_WORKER_SECRET=<secret ở bước 1>
CLOUDINARY_SIGNING_URL=https://rescuefarm-cloudinary.rescuefarm-app.workers.dev
CLOUDINARY_CLOUD_NAME=dmahghpku
```

Dùng đúng cloud name/Worker URL của môi trường bạn triển khai. Backend không cần Cloudinary API Secret; secret Cloudinary vẫn chỉ nằm ở Worker.

### 3. Worker

- `MEDIA_BACKEND_URL` đã có trong wrangler.toml: URL Render backend.
- Giữ FIREBASE_PROJECT_ID, CLOUDINARY_CLOUD_NAME, CLOUDINARY_API_KEY đúng môi trường; Worker có CLOUDINARY_API_SECRET hiện hành và MEDIA_WORKER_SECRET mới.
- Thu hồi credential ký Cloudinary cũ từng cấp chữ ký overwrite=true, rồi cập nhật Worker bằng credential mới. Việc chặn cấp chữ ký mới không tự vô hiệu chữ ký cũ đã phát ra.
- Deploy backend mới trước để migration bảo vệ ảnh hoàn tất; trong giai đoạn chuyển phiên bản không duyệt Campaign bằng backend cũ. Sau đó deploy Worker và Rules, cuối cùng cài APK mới. Backend/Worker/Rules/ứng dụng phải được phát hành đồng bộ; APK cũ không còn tự sửa media Campaign qua Firestore/sign-upload.

### 4. Vận hành

- Deploy firestore.rules vào đúng project. A13–A17 không cần composite index mới.
- Giữ workflow notification-worker đã cấu hình RENDER_BACKEND_URL và NOTIFICATION_WORKER_SECRET để đánh thức backend/cleanup nếu Render ngủ.
- Xem campaignMediaUploads: AUTHORIZED → UPLOADED → COMMITTED; ảnh bỏ dở/DETACHED → DELETING → DELETED. Không tự sửa trạng thái lease từ app.
- Firestore cached data có thể hiển thị để tham khảo khi offline, nhưng upload/save/submit media cần mạng. Không dùng thanh toán, SMS hoặc Blaze cho luồng này.

## Kiểm tra và giới hạn nghiệm thu

- Node backend: 77/77; Worker: 6/6, gồm mô phỏng JWT RSA hợp lệ qua fetch Worker, chữ ký upload phiên bản, chặn xóa asset chia sẻ, ownership/state/slot/size/type, commit/cleanup race và upload đang chạy khi Campaign được duyệt.
- JVM: 181/181; toàn bộ main Java được kiểm tra biên dịch bằng javac với dependency cache/resource symbols từ XML hiện hành. Đây không phải kết quả build hoặc chạy APK.
- Thêm Rules integration cho BUSINESS registration/edit/privacy/role freeze, report REVIEW/USER, client không tự đổi media/đọc lease. Firebase Emulator vẫn không khởi động được trên Windows này: Java Unable to establish loopback connection / Invalid argument: connect, kể cả ngoài sandbox. Chưa xác nhận bộ integration hoặc CI đã qua.
- GitHub Actions hiện có gọi npm run check/test:unit, nên tự chạy thêm cả kiểm tra Worker trước bước Emulator/JVM/APK. Chưa commit/push/deploy hay thử upload ảnh vào tài khoản Cloudinary thật.
- Nghiệm thu APK sau deploy: Customer report từ Review/Comment/Seller; BUSINESS đăng nhập lại và sửa/lưu; giữ Detail qua mốc 10 phút/deadline; Promotion tới giờ/hết hạn; Seller upload ảnh nháp rồi thoát, gửi bộ ảnh mới để duyệt, thử ghi đè/xóa ảnh ACTIVE và kiểm tra cleanup giữ ảnh đã commit.

Tham khảo hợp đồng nhà cung cấp: [Cloudinary Upload API](https://cloudinary.com/documentation/image_upload_api_reference), [tham số upload](https://cloudinary.com/documentation/upload_parameters). Giới hạn byte của Campaign được kiểm tra tại Worker, không chỉ dựa vào client hoặc upload preset.
