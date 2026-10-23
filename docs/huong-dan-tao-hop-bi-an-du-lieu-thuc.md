# Tạo một Hộp bí ẩn từ dữ liệu thật hiện có

## Dữ liệu đã đối chiếu

Đã đọc toàn bộ Firestore project `nhom7-rescuefarm`: **804 document**, gồm document con, trong **38 collection gốc**. Hoàn tất lúc **16:27:24 ngày 21/10/2026 (UTC+7)**. Có **23 sản phẩm và 23 lô hàng**. Quá trình rà soát không tạo/sửa/xóa document và không tạo sẵn hộp để tránh giữ kho thay bạn.

Mẫu dưới đây dành cho Seller **Góc Nông Sản Xanh**, tài khoản hiển thị **Seller**, UID `pM9Bn98bO0ZD8gP1YOoeH4B5T303`. Phải đăng nhập đúng Seller này; Seller khác không thể chọn các lô bên dưới.

| Sản phẩm thật | Mã lô để tìm trong app | Khả dụng khi rà | Giá gốc/kg | Hạn dùng hiển thị UTC+7 | Điều kiện |
| --- | --- | ---: | ---: | --- | --- |
| Mồng tơi Long An | `vIWudM8LwIIo3MktV7Kp` | 57 kg | 22.000đ | 22/10/2026 00:00 | RESERVED_PARTIAL; chưa gắn chiến dịch; được chọn nếu kết thúc nhận trước hạn |
| Khoai lang tím Vĩnh Long | `UFqjqAvKPIiEKEZIWVek` | 128 kg | 32.000đ | 05/11/2026 00:00 | AVAILABLE; chưa gắn chiến dịch |

Cả hai sản phẩm đều ACTIVE, thuộc đúng Seller đã APPROVED/ACTIVE; lượng khả dụng + giữ chỗ + đã bán khớp lượng ban đầu. Mẫu dùng **0,5 kg mỗi loại/hộp**, tạo **5 hộp**, nên chỉ cần **2,5 kg từ mỗi lô**. Tổng giá gốc mỗi hộp = `0,5 × 22.000 + 0,5 × 32.000 = 27.000đ`.

**Mẫu rau củ chỉ dùng khi “Nhận hộp muộn nhất” trước 22/10/2026 00:00.** Đây là dữ liệu tại thời điểm rà; trước khi tạo phải kiểm tra lại tồn và trạng thái trong app. Hạn dùng trong bảng là mốc thật của document, không phải ngày dùng để nhập vào một ô mới.

Lô Cà chua chín Lâm Đồng `vYS6fb8Oy1X0rLYixFU5` còn 78 kg nhưng đang gắn chiến dịch `26b8ffeb-4535-4430-8d48-b7b5c1d0c7b4`, nên **không dùng trong mẫu**. Không bỏ liên kết chiến dịch để vượt kiểm tra tồn kho.

## 1. Mở đúng màn Seller

1. Đăng nhập Seller **Góc Nông Sản Xanh**.
2. Trong dashboard **Quản lý cửa hàng**, bấm menu **☰**.
3. Cuộn tới nhóm **GIẢI CỨU** → bấm **Tạo gói giải cứu**.
4. Trong màn **Chọn cách bạn muốn giải cứu**, tại thẻ **Hộp bí ẩn**, bấm **Tạo hộp bí ẩn**.
5. Màn mở ra có tiêu đề **Bất ngờ trong mỗi hộp**, gồm các mục **01 · Giới thiệu hộp**, **02 · Bên trong mỗi hộp**, **03 · Giá và số hộp**, **04 · Hẹn nhận hộp**.

Lưu ý: điền 03 và 04 trước khi chọn lô ở 02. Hộp thoại thêm lô kiểm tra lượng mỗi suất × số hộp, và danh sách lô kiểm tra hạn dùng với giờ nhận cuối đang chọn.

## 2. Điền “01 · Giới thiệu hộp”

| Đúng nhãn ô đang triển khai | Nội dung nhập mẫu |
| --- | --- |
| Tên hộp bí ẩn | `Hộp rau củ cuối ngày - Góc Nông Sản Xanh` |
| Loại hộp (rau củ / trái cây…) | `Rau lá và củ` |
| Câu chuyện và chất lượng của hộp | `Gom rau lá và củ còn lại trong ngày thành hộp 1 kg để hạn chế lãng phí. Vui lòng đến đúng khung giờ nhận và kiểm tra nông sản khi nhận hộp.` |

Người bán cần kiểm tra chất lượng hàng thực tế; việc document còn hạn không thay thế kiểm tra tình trạng nông sản.

## 3. Điền “03 · Giá và số hộp” trước

Cuộn xuống mục 03, thay các giá trị mặc định bằng:

| Đúng nhãn ô đang triển khai | Giá trị nhập (không dấu chấm phân cách) |
| --- | --- |
| Cách đóng gói mỗi suất | `hộp` |
| Tổng giá trị gốc trong một hộp (VND) | `27000` |
| Giá giải cứu một hộp (VND) | `10000` |
| Số hộp có thể chuẩn bị | `5` |
| Số hộp tối đa mỗi khách | `1` |

Không giữ giá gốc mặc định 100000: giá gốc của hai thành phần mẫu chỉ là 27000/hộp, backend sẽ từ chối nếu kê cao hơn giá trị đó.

## 4. Thiết lập “04 · Hẹn nhận hộp”

Ba ô thời gian hiện có:

| Đúng nhãn ô | Ý nghĩa |
| --- | --- |
| Ngừng nhận đặt hộp | Sau mốc này khách không đăng ký thêm hộp |
| Khách bắt đầu nhận hộp | Khách có thể đến nhận và Seller bắt đầu xử lý chuẩn bị trong giờ nhận |
| Nhận hộp muộn nhất | Giới hạn cuối của khung nhận; phải trước hạn dùng của mọi lô |

Đã bổ sung giống Flash Sale, dưới ba ô có **Thiết lập nhanh khung bán và nhận**, kèm ba nút **15 phút**, **30 phút**, **60 phút**. Bấm **30 phút** cho mẫu này, sau khi đã điền các phần khác nếu mất nhiều thời gian.

Các nút tính từ thời điểm bạn bấm và cập nhật đồng thời cả ba ô, hiển thị `dd/MM/yyyy HH:mm` theo **giờ Việt Nam UTC+7**:

| Nút bấm | Ngừng nhận đặt hộp | Khách bắt đầu nhận hộp | Nhận hộp muộn nhất |
| --- | --- | --- | --- |
| 15 phút | Bây giờ + 15 phút | Bây giờ + 1 phút | Bây giờ + 45 phút |
| 30 phút | Bây giờ + 30 phút | Bây giờ + 1 phút | Bây giờ + 60 phút |
| 60 phút | Bây giờ + 60 phút | Bây giờ + 1 phút | Bây giờ + 90 phút |

Ví dụ minh họa: nếu bấm **30 phút** lúc **21/10/2026 17:00**, ba ô sẽ lần lượt là **21/10/2026 17:30**, **21/10/2026 17:01**, **21/10/2026 18:00**. Không nhập lại ví dụ này nếu thời điểm đó đã qua; hãy bấm nút để lấy lịch mới. “30 phút” là thời gian còn nhận đặt, không phải toàn bộ thời gian khách được đến nhận.

Muốn đổi lịch cụ thể: chạm vào ô hoặc icon lịch bên phải → chọn ngày → chọn giờ 24h → xác nhận. Không gõ chuỗi ngày giờ và không phải tự quy đổi sang UTC. Hộp bí ẩn vẫn cho chọn lịch thủ công theo giới hạn riêng của nó; không bị áp giới hạn bán 2 giờ của FLASH.

Ngay bên dưới phần thời gian:

1. Đứng tại **điểm giao hộp thật**.
2. Bấm **Lấy vị trí hiện tại bằng GPS** và cấp quyền vị trí khi dùng ứng dụng nếu được hỏi.
3. Đợi GPS thành công. Nút sau đó đổi thành **Lấy lại vị trí bằng GPS**.
4. Kiểm tra ô **Địa chỉ / hướng dẫn đến điểm nhận**. Giữ địa chỉ được GPS tự điền, bổ sung hướng dẫn nếu cần, ví dụ nối thêm `- Nhận hộp tại quầy Góc Nông Sản Xanh, vui lòng gọi cửa hàng khi đến.`
5. Nếu lấy được GPS nhưng không tìm được địa chỉ, điền địa chỉ thực tế/hướng dẫn đến nơi đang đứng vào chính ô này. Nội dung cần ít nhất 5 ký tự.

Không có ô nhập vĩ độ/kinh độ thủ công. Địa chỉ là mô tả để khách nhận ra nơi giao hàng; GPS là tọa độ để xác định điểm trên bản đồ. Đừng lấy GPS ở một nơi rồi ghi địa chỉ nơi khác.

## 5. Quay lên “02 · Bên trong mỗi hộp”

Điền ô **Thành phần có thể có / lưu ý dị ứng**:

> Rau lá và củ theo các lô còn lại. Rửa sạch và nấu chín trước khi dùng. Khách có dị ứng hoặc yêu cầu ăn uống đặc biệt cần liên hệ cửa hàng trước khi đặt.

### Thêm Mồng tơi

1. Bấm **Chọn lô nông sản (tối đa 12)**.
2. Hộp thoại **Chọn lô nông sản** có ô **Tìm tên nông sản hoặc mã lô**. Nhập `Mồng tơi Long An` hoặc `vIWudM8LwIIo3MktV7Kp`.
3. Tại thẻ đúng lô, kiểm tra **Khả dụng**, **Hạn dùng**, **Mã lô**, rồi bấm **Chọn lô này**.
4. Hộp thoại tên **Mồng tơi Long An** mở ra. Ô **Lượng trong MỖI suất (kg)** nhập `0.5`.
5. Bấm **Thêm lô**. Trong mục 02 xuất hiện thẻ Mồng tơi, nội dung `0.5 kg / suất` và nút **Bỏ lô**.

### Thêm Khoai lang

Lặp lại thao tác trên:

- Bấm **Chọn lô nông sản (tối đa 12)**.
- Tìm `Khoai lang tím Vĩnh Long` hoặc `UFqjqAvKPIiEKEZIWVek`.
- Bấm **Chọn lô này**.
- Tại ô **Lượng trong MỖI suất (kg)** nhập `0.5` → bấm **Thêm lô**.

Danh sách đã chọn phải có đúng **hai lô**, mỗi lô `0.5 kg / suất`. `0.5` là lượng trong **một hộp**, không phải lượng cho cả 5 hộp. Không nhập 2.5 ở đây vì như vậy hệ thống sẽ giữ 12.5 kg của từng lô.

Nếu không thấy nút **Chọn lô này**, đọc lý do màu cảnh báo ngay trên thẻ: có thể hết hạn theo giờ nhận cuối, gắn chiến dịch hoặc đã thêm vào gói. Có nút **Tải lại danh sách lô** cả trên form và trong hộp thoại; nút **Đóng** đóng danh sách. Không đổi sang lô khác chỉ vì trùng tên; đối chiếu mã lô.

## 6. Tạo và kiểm tra kết quả

1. Kiểm tra lại **Số hộp có thể chuẩn bị = 5**, hai thành phần mỗi loại **0.5 kg**, giá gốc **27000**, giá giải cứu **10000**.
2. Kiểm tra ba mốc giờ vẫn ở tương lai; giờ nhận cuối trước **22/10/2026 00:00**. Nếu đã thao tác lâu, bấm lại **30 phút** rồi kiểm tra hạn dùng.
3. Bấm **Tạo hộp bí ẩn và giữ nông sản** ở cuối màn.
4. Hộp xác nhận **Giữ tồn kho cho gói mới?** hiện ra. Bấm **Tạo gói** để tạo thật; **Kiểm tra lại** để quay về kiểm tra nội dung.
5. Sau khi tạo thành công, nếu app quay về màn trước, quay lại dashboard → **☰** → **GIẢI CỨU** → **Gói cửa hàng** → **Làm mới**. Tìm tên hộp đã nhập, bấm **Xem gói giải cứu** để kiểm tra thông tin và đăng ký.

Nếu không có giao dịch khác xen vào, kết quả kho dự kiến sau tạo:

| Lô | Khả dụng trước → sau | Giữ chỗ trước → sau | Đã bán |
| --- | --- | --- | --- |
| Mồng tơi | 57 → 54.5 kg | 3 → 5.5 kg | vẫn 0 kg |
| Khoai lang tím | 128 → 125.5 kg | 0 → 2.5 kg | vẫn 2 kg |

Gói có 5 suất, giá 10.000đ/hộp, tối đa 1 hộp/khách; tạo gói giữ kho chứ chưa ghi là đã bán. Nếu mạng lỗi, giữ nguyên form và gửi lại, tránh thoát ra tạo một gói mới trước khi kiểm tra gói đã được tạo hay chưa.

## Nếu mẫu Mồng tơi hết hạn khi bạn đọc hướng dẫn

Không sửa kéo dài hạn của lô để chạy mẫu. Có thể dùng mẫu trái cây sau, cũng cùng Seller và đã có thật trong snapshot:

| Tên sản phẩm | Mã lô | Khả dụng khi rà | Hạn dùng UTC+7 | Lượng/một hộp |
| --- | --- | ---: | --- | ---: |
| Ổi ruột trắng Long An | `UbD32uqaIFUW02ouJaOT` | 80 kg | 26/10/2026 00:00 | 0.5 kg |
| Cam sành Vĩnh Long | `g21ye8WNdVYl64c95Pun` | 100 kg | 30/11/2026 00:00 | 0.5 kg |

Cả hai chưa gắn chiến dịch tại lúc rà. Đổi **Tên hộp bí ẩn** thành `Hộp trái cây cuối ngày - Góc Nông Sản Xanh`, **Loại hộp (rau củ / trái cây…)** thành `Trái cây`, mô tả/lưu ý cho đúng trái cây và **Tổng giá trị gốc trong một hộp (VND)** thành `26500` (`0.5 × 25000 + 0.5 × 28000`). Giữ 5 hộp, giá 10000, tối đa 1 hộp/khách; chọn lại đúng hai lô, giờ nhận cuối trước hạn sớm nhất và kiểm tra tồn mới. Không giữ đồng thời hai lô của mẫu rau khi chuyển sang mẫu trái cây; dùng **Bỏ lô** để loại các lô đã thêm.

## Cài đặt thay đổi giao diện thời gian

Ba nút nhanh trong mục 04 là thay đổi Android: cần build/cài APK mới. Không cần deploy backend hoặc đổi Firestore Rules/index cho ba nút này. Flash Sale và Hộp bí ẩn dùng chung logic tính khung giờ để tránh lệch cách hiển thị hoặc tính từ thời điểm mở màn thay vì thời điểm bấm.
