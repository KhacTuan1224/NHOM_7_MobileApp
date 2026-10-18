# Phân trang danh sách và Home

## Hành vi

- Home dùng chung cho guest/customer/seller/admin: mỗi nhóm sản phẩm, chiến dịch, danh mục, lô sắp hết hạn và ưu đãi số lượng hiển thị 6 mục đầu. Nút **Xem thêm** mở thêm 6 mục. Nếu đã dùng hết dữ liệu đang tải, nút yêu cầu trang kế tiếp từ Firestore. Nút cuối Home mở thêm cửa sổ các nhóm và yêu cầu thêm sản phẩm/chiến dịch.
- Sản phẩm công khai, chiến dịch công khai và lịch sử đơn customer: trang mạng 12 mục, đọc thêm 1 bản ghi để biết còn trang tiếp theo. Dùng `startAfter(DocumentSnapshot)` để tiếp tục, không dùng offset và không tải lại toàn bộ collection. ID của kết quả được gộp để tránh trùng. Chiến dịch ưu tiên mức độ khẩn cấp bằng index `status + urgencyLevel` hiện có.
- Quản trị: các danh sách của drawer dùng trang 12 thay cho 40; khóa nút khi tải và loại trùng khi nối trang.
- Seller: sản phẩm, chiến dịch, đơn hàng dùng trang 12 thay cho 50; bài đăng giữ trang 12. Chặn yêu cầu lặp khi đang tải. Danh sách giao hàng/điểm di động vẫn lọc trên dữ liệu đã tải và có nút lấy trang tiếp theo khi chưa tìm thấy kết quả.
- Danh sách danh mục, khám phá/bộ lọc, bảng tin chiến dịch, ưu đãi, yêu thích, thông báo, đánh giá, địa chỉ, lô hàng, bình luận và màn báo cáo cũ dựng giao diện theo cửa sổ 12 mục. Những nguồn đã đồng bộ đủ dữ liệu dùng phân trang tại thiết bị; chưa chuyển tất cả nguồn này thành truy vấn cursor trên mạng.
- Dashboard seller đã giới hạn 5 đơn gần nhất từ backend. Nút **Xem thêm** dẫn tới danh sách đơn đã phân trang. Dashboard admin là các số tổng hợp, không chia nhỏ dữ liệu dùng tính tổng/thống kê.
- Form chọn địa chỉ/lô hàng, giỏ hàng và chi tiết đơn không bị cắt danh sách phục vụ nghiệp vụ. Badge thông báo vẫn đếm toàn bộ thông báo chưa đọc, độc lập cửa sổ đang hiển thị.

## Cache và lỗi

- Trang công khai lưu ID thuộc cửa sổ của repository, không xóa cache sản phẩm/chiến dịch của màn khác khi reset.
- Lỗi mạng giữ kết quả cũ và cho thử lại; chỉ cập nhật cursor sau khi lưu trang thành công. Không tự lặp tải trang khi lọc chưa có kết quả.
- Bộ lọc mới reset cửa sổ giao diện. Dữ liệu realtime không tự tăng số mục hiển thị. Tìm kiếm gợi ý cũng bắt đầu với 6 mục.
- Phân trang các nguồn lọc tại thiết bị chỉ tìm/sắp xếp trong phần đã tải; có nút tải tiếp, không thông báo rằng toàn hệ thống hết kết quả khi vẫn còn trang.
- Truy vấn đơn customer dùng thứ tự ID ổn định để không yêu cầu index mới; không cam kết thứ tự thời gian toàn bộ lịch sử. Query nghiệp vụ lấy toàn bộ đơn để kiểm tra quyền đánh giá được giữ riêng.

## Kiểm tra

- `PageWindowTest`: giới hạn Home, trang cuối lẻ, dữ liệu rỗng/vừa đủ trang, dữ liệu cập nhật, reset bộ lọc và giữ nguyên nguồn.
- `HomePaginationTest`: chỉ gọi API trang hữu hạn, không tự tải tất cả, chặn tải đồng thời, chỉ tải đúng loại, retry sau lỗi, hết dữ liệu và làm mới.
- `CustomerOrderHistoryTest`: dùng API trang theo chủ đơn, giữ cache khi lỗi và khôi phục trạng thái để thử lại.
- Build Android và unit test; chưa chạy tương tác giao diện trên emulator/thiết bị trong lượt triển khai này.

Kết quả ngày 18/10/2026: `assembleDebug`, `assembleDebugAndroidTest` và toàn bộ **220 unit test** đạt, không lỗi/skip. Bộ kiểm thử Android đã biên dịch, chưa thực thi trên thiết bị. APK: `app/build/outputs/apk/debug/app-debug.apk`.
