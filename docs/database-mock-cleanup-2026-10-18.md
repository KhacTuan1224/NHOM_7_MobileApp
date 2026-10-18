# Dọn dữ liệu mẫu trên Firestore — 18/10/2026

> Báo cáo tại thời điểm kiểm tra trước. Phần Post đã được ngừng sử dụng sau đó; xem [kết quả rà soát và dọn Post](post-retirement-2026-10-18.md).

Đã thực hiện trực tiếp trên project `nhom7-rescuefarm` bằng service account cục bộ. Toàn bộ 165 thao tác được commit trong **một transaction**: xóa 160 document (kể cả subcollection), cập nhật 3 document và tạo 2 danh mục thay thế.

## Cách xác định dữ liệu mẫu

- Đối chiếu ID và các trường nội dung với `scripts/seed_firestore.py`. Sản phẩm khớp tên và ảnh seed; danh mục, banner, bài viết khớp tên/tiêu đề và ảnh seed. Không coi mọi ảnh ngoài Cloudinary là dữ liệu mẫu.
- Một số document seed đã được gán sang seller thật. Vì vậy không chỉ kiểm tra `sellerId == mock-seller-rescuefarm`.
- Xóa các lô của 8 sản phẩm seed và 5 chiến dịch chỉ sử dụng những lô này. Trong đó có một chiến dịch UUID do người dùng tạo nhưng toàn bộ hàng hóa là khoai lang seed.
- Xóa 5 đơn có toàn bộ sản phẩm/lô là seed, gồm cả đơn đã hoàn thành. Dữ liệu thanh toán, vận chuyển, item, tra cứu khách, quyền truy cập khách và thông báo liên quan được xóa cùng transaction.
- Xóa đánh giá, yêu thích, lượt xem, bình luận/phản ứng con và khuyến mãi gắn với tài nguyên đã xóa. Chỉ bỏ dòng hàng mẫu khỏi giỏ; giữ dòng hàng thật.
- Hai bài viết có tiêu đề `test` được tạo qua ứng dụng không khớp seed được giữ lại. Tên `test` hoặc URL ảnh bên ngoài, đứng riêng, không đủ để xóa nội dung người dùng.

## Dữ liệu chính trước và sau

Số lượng trong bảng chỉ tính document cấp collection, không cộng document con.

| Collection | Trước | Sau |
|---|---:|---:|
| users | 13 | 13 |
| categories | 5 | 2 mới |
| banners | 2 | 0 |
| products | 10 | 2 |
| productBatches | 6 | 2 |
| campaigns | 7 | 2 |
| posts | 4 | 2 |
| promotions | 2 | 0 |
| orders | 11 | 6 |
| payments | 11 | 6 |
| shipments | 11 | 6 |

Theo lựa chọn của người dùng, đã xóa cả 5 danh mục seed. Tạo lại **Trái cây** và **Rau xanh** bằng ID mới, ảnh rỗng; chuyển 2 sản phẩm thật sang danh mục tương ứng. Giữ nguyên tên và cách phân loại cũ của sản phẩm, không tự phân loại lại.

Giữ nguyên users và toàn bộ địa chỉ/thiết bị con, hồ sơ cửa hàng, thông tin ngân hàng/QR, hồ sơ đăng ký seller, cấu hình phí, ánh xạ chuyển khoản của đơn thật và lịch sử kiểm toán. Lịch sử kiểm toán có thể nhắc tới tài nguyên đã xóa; đây là dữ liệu lịch sử được giữ có chủ đích.

## Ràng buộc đã kiểm tra

- `Product.updateDetails` yêu cầu categoryId; Firestore Rules yêu cầu danh mục đang hoạt động khi ghi sản phẩm. Chuyển danh mục và xóa seed được thực hiện nguyên tử.
- `campaign-lifecycle.js` cần lô và chiến dịch lịch sử khi hủy/hoàn tất đơn. Tất cả lô/chiến dịch của 6 đơn còn lại được giữ nguyên.
- Đọc lại database sau commit: 75 document cần bảo vệ khớp hoàn toàn dữ liệu trước xóa, bao gồm 26 document users và document con.
- Cả 6 đơn còn đủ item, sản phẩm, lô, thanh toán và vận chuyển; trạng thái thanh toán giữa đơn/payment khớp nhau.
- Tổng tồn kho ban đầu bằng tồn khả dụng + giữ chỗ + đã bán. Số lượng giữ chỗ/đã bán của lô và số lượng giữ chỗ/giải cứu của chiến dịch khớp các đơn được giữ lại.
- Không còn tham chiếu tới ID đã xóa trong các collection nghiệp vụ đã rà. Không xóa tài sản Cloudinary.
- 6 bài kiểm thử cho bộ lập kế hoạch đều pass, gồm chặn xóa đơn/chiến dịch trộn hàng thật và hàng mẫu, giữ users và giữ sản phẩm có ảnh ngoài.

## Sao lưu và công cụ

Thư mục sao lưu được Git bỏ qua: `build/database-cleanup/2026-10-18T02-09-57-515Z/`.

- `snapshot.json`: bản rà ban đầu.
- `pre-delete-snapshot.json`: dữ liệu đọc trong transaction ngay trước khi xóa; timestamp, reference, geopoint và bytes được lưu có kiểu.
- `applied-plan.json`: đường dẫn, lý do và nội dung từng thao tác.
- `applied.json`: kết quả commit và SHA-256 bản sao lưu.
- `verification.json`: kết quả đối chiếu sau xóa, `integrity: PASS`.

Bản đọc lại database nằm tại `build/database-cleanup/2026-10-18T02-14-21-528Z/`. Các bản sao lưu chứa dữ liệu riêng tư, không đưa vào Git.

Công cụ trong `scripts/`: `audit-live-firestore.cjs` chỉ đọc; `cleanup-mock-firestore.cjs` mặc định lập kế hoạch, chỉ ghi khi có `--apply`; `verify-mock-cleanup.cjs` đối chiếu bản trước/sau. Công cụ giới hạn project và không sửa users.

Ứng dụng làm mới dữ liệu Home bằng Firestore và thay cache Room; sau thao tác này cần mở app có mạng và nhấn **Làm mới**. Bản cache offline cũ không chứng minh dữ liệu còn trên server. Không có luồng tự seed trong ứng dụng; script seed là công cụ chạy thủ công.
