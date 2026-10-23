# Card Chiến dịch giải cứu

Đã đổi card trên màn Chiến dịch giải cứu, danh sách chiến dịch và bài chiến dịch trong trang danh mục sang bố cục bài đăng gọn:

1. Ảnh cửa hàng tròn, tên công khai và icon dấu tích viền đặt sát tên; không có nhãn chữ hoặc nền bao quanh icon.
2. Thời gian đăng, địa điểm; vẫn hiển thị khoảng cách nếu đang tìm gần đây.
3. Tiêu đề tối đa 2 dòng, nội dung tối đa 2 dòng, thông tin sản phẩm tối đa 2 dòng.
4. Thanh tiến độ và sản lượng giải cứu thực tế theo đơn vị của chiến dịch.
5. Ảnh chiến dịch cao 168dp, bo góc.
6. Nút “Xem chiến dịch giải cứu”: nền trong suốt, viền xanh, không emoji.

Title dùng IBM Plex Sans; nội dung, metadata và nút dùng Barlow.
Không thêm tương tác like/comment giả hoặc tạo số liệu tiến độ minh họa.

## Nguồn dữ liệu và xác minh

API mới `POST /campaigns/cards`, body `{ "campaignIds": ["id"] }`, tối đa 12 ID mỗi request; dùng được cho Guest.
Chỉ trả dữ liệu cho chiến dịch ACTIVE đang trong thời gian hoạt động.

- Tên/ảnh: `sellerPublicProfiles.shopName` và `shopAvatarUrl`. Thiếu ảnh dùng icon cửa hàng;
  thiếu tên dùng “Cửa hàng nông sản”. Tuyệt đối không fallback sang ảnh/tên tài khoản cá nhân.
- Icon tích xanh khi tài khoản có role SELLER, ACTIVE, sellerStatus APPROVED và hồ sơ
  `sellerApplications/{sellerId}` đúng chủ, status APPROVED, có `proofImageUrl` HTTPS.
- Thiếu hồ sơ/minh chứng, chưa duyệt hoặc không lấy được xác minh: icon tích xám.
- Thời gian lấy `publishedAt`, nếu chưa có lấy `moderatedAt`, cuối cùng `createdAt`.
  Thiếu cả ba thì ghi “Chưa có thời gian đăng”; không tự dùng giờ hiện tại hay ngày bắt đầu chiến dịch.
- Địa điểm lấy `campaign.locationName`, thiếu thì địa chỉ cửa hàng công khai.
- Preview sản phẩm lấy từ các lô thuộc chiến dịch, chỉ trả sản phẩm ACTIVE của đúng Seller;
  không hiển thị dữ liệu đơn hàng hoặc người mua.

Backend chỉ trả cờ xác minh, không trả hồ sơ đăng ký, URL giấy tờ, email, thông tin ngân hàng hay avatar cá nhân.
Client không được cấp quyền đọc `users` hoặc `sellerApplications` của người khác.

## Cập nhật

1. Deploy backend Render với `functions/campaign-cards.js` và `functions/server.js` mới.
   Không cần biến môi trường, collection hoặc Rules mới.
2. Cài `app/build/outputs/apk/debug/app-debug.apk` mới.
3. Vào Chiến dịch giải cứu → Làm mới để tải thông tin.

Khi Render còn bản cũ hoặc mất mạng, card vẫn mở chi tiết được nhưng hiển thị thông tin cửa hàng mặc định,
icon tích xám và chưa có thời gian đăng. Không suy đoán xác minh từ việc chiến dịch đang ACTIVE.

Kiểm thử: 4 test API mới về nguồn công khai, xác minh/minh chứng, phạm vi chiến dịch và validation;
toàn bộ unit backend 156 test qua, Cloudinary Worker 8 test qua. Android unit test, assembleDebug và lintDebug qua.
Không thay dữ liệu Firebase thật hoặc deploy Render trong phiên chỉnh giao diện này.
