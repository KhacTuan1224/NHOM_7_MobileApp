# Bình luận chiến dịch có ảnh và thông tin người gửi

Ngày kiểm tra: 25/10/2026. HEAD nền: `a227460bf4cf81f940f287d0f421f5367509d9a3`; các thay đổi tính năng này chưa commit. Không thay đổi dữ liệu production.

## Giao diện và thao tác

Trong chi tiết chiến dịch, Customer nhập nội dung bình luận (1–500 ký tự), tùy chọn **Bình luận ẩn danh**, bấm **Thêm ảnh (0/3)** để chọn từng ảnh rồi **Gửi bình luận**. Tối đa ba ảnh; có thumbnail và nút **Gỡ ảnh** trước khi đăng. Upload và gửi cần mạng, có trạng thái tiến trình/lỗi và chặn gửi khi ảnh đang tải.

Mỗi bình luận hiển thị avatar, tên người gửi, thời gian, nội dung và ảnh đính kèm nếu có. Chạm ảnh để mở lớn. Bình luận ẩn danh luôn dùng tên **Người dùng ẩn danh** và avatar mặc định cố định, không tải avatar hồ sơ thật. Bình luận thường thiếu avatar dùng ảnh mặc định. Chủ bình luận vẫn có thể sửa nội dung hoặc xóa; chức năng báo cáo được giữ lại.

Ẩn danh chỉ áp dụng cách trình bày: UID vẫn được lưu trong document để xác định chủ sở hữu và quản trị, theo mô hình quyền hiện có. Đây không phải cơ chế che danh tính tuyệt đối khỏi người có quyền đọc document. Tên/avatar bình luận mới là snapshot lúc đăng; thay hồ sơ sau đó không sửa lịch sử bình luận.

Bản nháp cùng các URL ảnh đã upload được giữ qua tái tạo view/Activity và scope theo UID. Đổi tài khoản xóa bản nháp đang giữ. Upload hoàn tất sau khi view cũ bị hủy không tự thêm ảnh vào view mới; có thể cần chọn lại.

## Luồng dữ liệu và quyền

- Android dùng `CloudinaryMediaStorage` hiện có, loại tài nguyên `campaignComment`, gọi Worker ký upload. Worker chỉ chấp nhận Customer đang hoạt động, ba slot, tạo public ID UUID không ghi đè trong namespace riêng của UID/bình luận. Giới hạn upload hiện có là 10 MB/ảnh.
- `POST /campaigns/comments/add` xác thực Customer, đọc tên/avatar từ hồ sơ phía server, kiểm tra chiến dịch ACTIVE và trong lịch. Client không tự chọn tên/avatar người khác. Ảnh phải thuộc đúng cloud và namespace UID/commentId; không chấp nhận URL tùy ý hoặc ảnh của người khác.
- Firestore transaction dùng commentId ổn định để chống tạo trùng khi retry. Document đặt tại `campaigns/{campaignId}/comments/{commentId}`; bổ sung `senderName`, `senderAvatarUrl`, `anonymous`, `imageUrls`.
- Rules giữ tương thích client cũ tạo bình luận text, nhưng không cho client tự ghi các trường trình bày/ảnh mới. Chủ sở hữu chỉ sửa nội dung hoặc xóa theo quyền hiện có; xóa làm rỗng nội dung/danh sách ảnh. Quản trị hiện có được giữ.
- Listener lấy tối đa 100 bình luận mới nhất, sau đó phân trang hiển thị cục bộ. Không tải lại toàn bộ listener sau khi gửi thành công. Giới hạn này chưa phải phân trang server cho toàn bộ lịch sử.
- Bình luận cũ chưa có snapshot tác giả được giải quyết qua `POST /campaigns/comments/presentation`: trả riêng tên/avatar công khai, cache chung phía backend TTL 60 giây, tối đa 1.000 entry và cache trong repository Android. Không tạo một listener hồ sơ cho mỗi dòng. Cache RAM phụ thuộc từng process, không bảo đảm miễn reads hoặc không bao giờ chạm quota Firebase.

Không thêm collection/index hoặc yêu cầu migration dữ liệu. Bình luận cũ thiếu dữ liệu tác giả vẫn có tên/avatar mặc định khi backend không truy cập được.

## Áp dụng

Cần cập nhật đồng bộ backend Render, Cloudinary Worker, Firestore Rules và APK. Endpoint health backend có `campaignCommentsVersion: 1`. Dùng cấu hình cloud/auth/media signing hiện có; không đưa secret vào app. Chưa deploy bất kỳ thành phần production nào trong lượt này.

Sau khi áp dụng trong môi trường demo, kiểm tra Customer gửi text + 1–3 ảnh; chọn ẩn danh; chạm ảnh; sửa/xóa; đọc lại bằng Guest; xoay khi nhập; lỗi mạng khi tải ảnh/gửi; tài khoản khác không được sửa. Backend và Rules đã kiểm tra bằng dữ liệu giả lập, nhưng chưa thao tác UI Android hoặc upload Cloudinary thật. Theo yêu cầu người dùng, giữ nguyên emulator5554, không cài/gỡ/clear app trên thiết bị đó.

Gỡ ảnh khỏi bản nháp, bỏ dở hoặc xóa bình luận chỉ bỏ tham chiếu hiển thị; chưa có tác vụ dọn asset Cloudinary không còn được dùng. Không quảng bá đây là xóa file vật lý. Chỉnh sửa bình luận hiện chỉ sửa text, chưa thay bộ ảnh sau khi đăng. Yêu cầu vẫn cần text, chưa hỗ trợ bình luận chỉ có ảnh.

## Kiểm tra đã chạy

| Kiểm tra | Kết quả mới |
|---|---|
| Android JVM `testDebugUnitTest` | 261 pass; 0 fail/error/skipped |
| `assembleDebug`, biên dịch instrumentation | Qua; chưa chạy instrumentation trên thiết bị |
| `lintDebug` | Qua; 0 error, 710 warning toàn dự án |
| Backend `npm --prefix functions run check` | Qua |
| Backend unit | 200 pass; 0 fail/skipped |
| Cloudinary Worker unit | 10 pass; 0 fail/skipped |
| Firestore Emulator regression, project `demo-rescuefarm` | 175 pass; 0 fail/skipped |

Test mới bao gồm: tên/avatar ẩn danh, xóa ảnh khỏi model, namespace ảnh, giới hạn slot, tác giả do server quyết định, retry/idempotency, chiến dịch ngoài lịch, cache tác giả cũ không trả thông tin riêng; Rules chặn giả mạo tác giả/ảnh và sửa của người khác, cho phép chủ sửa/xóa. Test HTTP dùng identity stub trong harness local; không chứng minh Google OAuth, GPS hay provider ngoài hoạt động.

Log lần chạy thành công và bản tổng hợp đặt trong `docs/audits/campaign-comments/`. Các lần thử ban đầu có lỗi compile style Android và chờ HTTP listen trong harness đã được sửa; log thử ban đầu vẫn nằm trong thư mục `build/`, không được tính là kết quả pass. Chưa cập nhật báo cáo DOCX/PDF của đồ án theo tính năng mới.
