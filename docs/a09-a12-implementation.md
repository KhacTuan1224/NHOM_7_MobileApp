# A09–A12: triển khai và nghiệm thu

## A09 — Thống kê độc lập với phân trang

- Dashboard Seller và màn Thống kê đều dùng `POST /seller/statistics`. Backend đọc hết các trang (200 tài liệu/trang), không dùng cache Android hoặc 50 đơn đầu của danh sách.
- Đếm đơn theo **ngày tạo**. Doanh thu, lượng giao/giải cứu và top sản phẩm/lý do theo **ngày giao `deliveredAt`**. Doanh thu không gồm phí giao hàng. Đơn cũ chưa có ngày giao dùng `updatedAt`, rồi `createdAt`, và có cảnh báo dữ liệu ngày gần đúng.
- Các kỳ là 7/30/90 ngày gần nhất hoặc toàn thời gian. Đơn đang xử lý và đơn cần giao đang xử lý lấy **toàn thời gian**, độc lập với kỳ. Năm đơn gần đây cũng lấy toàn thời gian; tổng đếm vẫn đầy đủ.
- Sản phẩm/chiến dịch/khuyến mãi được đọc đầy đủ theo Seller. Đơn vị giải cứu theo hợp đồng A05; tổng kg chỉ gồm kg đã xác minh. Top có nhãn đơn vị, không cộng bó/bao vào kg. Tỷ lệ là trung bình tiến độ các Campaign đã xác minh chồng lấn kỳ, không cộng mục tiêu khác đơn vị.
- Dashboard Admin tính lại trạng thái hiển thị và khẩn cấp tại thời điểm truy vấn, không đếm trực tiếp field urgency đã lưu. Home tính lại theo cùng quy tắc: ACTIVE, đã bắt đầu, chưa kết thúc, còn phân bổ; mức CRITICAL/HIGH dựa vào số ngày nguyên còn lại, tỷ lệ chưa phân bổ và hạn lô còn bán được của sản phẩm ACTIVE. Không sửa document chỉ để thay đổi mức khẩn cấp.
- Home, Dashboard và Analytics đang hiển thị cập nhật theo thời gian mỗi 60 giây; dừng lịch khi rời màn hình. Dashboard Seller tải lại khi quay về. Có generation guard để kết quả truy vấn cũ không ghi đè kỳ mới, ViewModel được tách theo UID.
- Cache cũ không được dùng làm số liệu thay thế khi backend lỗi. Có trạng thái tải/lỗi và nút tải lại. Nếu backend chưa cập nhật contract inventory, Seller Home báo cần cập nhật backend, không hiển thị các số 0 giả.

## A10 — Projection tìm kiếm Campaign

- Liên kết Campaign → target batch → Product ACTIVE, cùng đơn vị Campaign VERIFIED và đúng `activeCampaignId`.
- Category, khoảng giá, hạn sử dụng và lượng tồn được kiểm tra trên **cùng lô/sản phẩm**. Lượng có thể nhận là nhỏ nhất của tồn vật lý và phân bổ batch còn lại (đã trừ reserved/rescued và giới hạn tổng Campaign).
- Tên/mô tả Campaign hoặc tên/mô tả/nguồn gốc/địa phương sản phẩm có thể khớp từ khóa. Địa phương khớp điểm cứu trợ hoặc sản phẩm cùng hàng được chọn.
- Giá đại diện là **giá từ** trong các lô khớp bộ lọc; giảm giá là **mức giảm tối đa** từ giá gốc/giá giải cứu. Bán chạy là **lượng đã giao trong Campaign**, lấy `rescuedByBatch`, toàn thời gian, trên các lô khớp còn bán được. Không lấy doanh số ngoài Campaign của cùng lô.
- Sắp xếp giá và lượng bán chia theo đơn vị trước rồi mới so sánh số; không coi giá/bó và giá/kg là cùng đại lượng. UI có nhãn rõ ý nghĩa. Tie-break cuối cùng theo loại và ID để thứ tự ổn định.
- Campaign chưa xác minh đơn vị hoặc không có nguồn cung khớp không được đưa vào kết quả projection. Không suy diễn giá từ field không tồn tại.

## A11 — Report bình luận đúng ngữ cảnh

- Từ bình luận Campaign, nút Báo cáo truyền đường dẫn nội bộ tới form. Form hiển thị loại nội dung cha, mã nội dung cha và mã bình luận, không yêu cầu người dùng nhập đường dẫn kỹ thuật.
- Report mới COMMENT lưu `targetId`, `parentType: CAMPAIGN|POST`, `parentId`, `targetPath`. Rules kiểm tra đường dẫn đúng cha/con, bình luận tồn tại và ACTIVE/EDITED, nội dung cha công khai hoặc người gửi là Admin. Các thông tin target này bất biến sau tạo.
- Admin đọc trực tiếp đường dẫn đã xác thực, hiển thị nội dung cha thật và mở Campaign/Post đúng loại. Transaction xử lý kiểm tra lại targetPath. Không gán mọi bình luận thành postId nữa.
- Report cũ chỉ có ID dùng collection-group query với limit 2. Chỉ tự chọn nếu có đúng một kết quả; trùng ID thì hiện cảnh báo và không cho ẩn/khóa nhầm. Report vẫn có thể được xem và bác. Muốn xử lý một report mơ hồ phải xác minh cha thực tế rồi bổ sung đủ parentType/parentId/targetPath bằng công cụ quản trị tin cậy; không đoán cha.
- Post legacy vẫn chỉ là tương thích dữ liệu cũ theo phần chốt của `final.md`. Không mở lại luồng tạo/sửa Post/comment. Admin được ẩn bình luận legacy khi giải quyết báo cáo; audit dùng COMMENTS và resourcePath, không giả thành REPORTS có cùng ID.
- Index `comments.id` scope COLLECTION_GROUP và rule đọc group chỉ cho Admin đã có trong `firestore.indexes.json`/`firestore.rules`; vẫn cần triển khai chúng trên project thật để fallback legacy chạy. Rules emulator không chứng minh index production đã tồn tại.
- API kết quả report chỉ trả ngữ cảnh và kết quả công khai của đúng người gửi; không trả resolutionNote nội bộ.

## A12 — Ảnh Review cho người đọc

- Renderer dùng chung `ReviewMediaRenderer` gắn dải thumbnail ngay dưới nội dung từng Review, giữ thứ tự tối đa 5 ảnh.
- Chạm ảnh mở gallery lớn, có bộ đếm, Trước/Sau và Đóng; có placeholder và biểu tượng lỗi ảnh. Dialog được đóng và request ảnh được dọn theo vòng đời view.
- Chỉ Review PUBLISHED có ảnh công khai. Renderer không phụ thuộc người đọc là chủ Review; Guest và tài khoản khác dùng cùng dải ảnh. Query/Rules tiếp tục chặn review bị ẩn.
- Lưu/sửa/xóa Review dùng luồng refresh hiện có; dải ảnh được dựng lại theo danh sách ảnh đã lưu.

## Triển khai lên môi trường thật

1. Đẩy các thay đổi sau khi xem diff; GitHub Actions hiện có tự chạy Node unit, Firestore Emulator, JVM và build APK. Thay đổi này không tự commit/push.
2. Deploy phiên bản backend Render chứa seller-analytics, campaign-projection, notifications-api và server mới. Không cần thêm npm dependency hay secret cho A09–A12.
3. Deploy `firestore.rules` và `firestore.indexes.json` vào đúng project bằng luồng quản trị tin cậy của bạn; chờ index comments.id group sẵn sàng. Workflow đóng gói APK hiện tại **không deploy Rules/index**.
4. Cài APK mới. Không cần nâng schema Room hoặc xóa dữ liệu ứng dụng cho A09–A12.

Không có thao tác production hoặc deploy nào đã được chạy trong lượt này.

## Kiểm tra

- Backend unit: dataset 451 đơn, 205 sản phẩm, đơn cũ còn xử lý, đơn tạo kỳ trước/giao kỳ này; kết quả không phụ thuộc pagination/cache. Kiểm tra clock qua ngưỡng urgency và expiry mà không ghi document.
- JVM: projection category+price+stock tương quan cùng lô, sort theo số liệu thật, đơn vị chưa xác minh, deadline Home và media Review PUBLISHED/HIDDEN/DELETED, thứ tự ảnh và ảnh bị xóa.
- Thêm test Firestore cho report Campaign/Post có cùng comment ID, giả mạo parent/path, Admin ẩn đúng một bình luận legacy kèm audit, Guest/Customer đọc 3 ảnh published và bị chặn hidden.
- Kết quả hiện tại: Node 59/59 và JVM 172/172; kiểm tra biên dịch toàn bộ mã Java bằng javac thành công. Không coi javac là kết quả build APK.
- Gradle và Firestore Emulator trên máy Windows này không khởi động được do Java `Unable to establish loopback connection`, kể cả khi chạy ngoài sandbox. Bộ Rules integration mới chưa chạy được tại local; cần kết quả GitHub Actions và nghiệm thu UI trên APK mới. Chưa xác nhận CI hoặc production đã qua.
