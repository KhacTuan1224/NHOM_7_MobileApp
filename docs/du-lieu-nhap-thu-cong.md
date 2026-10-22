# Bộ nội dung nhập thủ công cho RescueFarm
Đối chiếu form Java/XML và kiểm tra nhập liệu của repo ngày **18/10/2026**. Chỉ cung cấp văn bản để bạn tự nhập; không tạo dữ liệu trong database và không thay đổi UI.

Bộ này gồm **8 danh mục, 32 sản phẩm, 32 lô hàng, 16 chiến dịch và 10 banner**. Đây là nội dung minh họa cho bản demo, không phải thông tin tồn kho, nguồn hàng hay giá thị trường đã được xác minh. Nếu bán thật, thay bằng thông tin đúng của hàng thực tế.

## Cách nhập để các phần liên kết được với nhau
1. Admin tạo/bổ sung danh mục. Nếu đã có **Trái cây** và **Rau xanh**, dùng lại, không tạo trùng.
2. Seller tạo sản phẩm, chọn đúng danh mục và tải ảnh. Mỗi sản phẩm bên dưới dùng đơn vị **kg**, trạng thái **Đang bán**.
3. Mở quản lý lô hàng của từng sản phẩm, tạo một lô theo bảng. Chỉ có sản phẩm mà chưa có lô khả dụng thì chưa đủ để đặt hàng.
4. Seller tạo chiến dịch, chọn hai lô tương ứng, nhập mục tiêu riêng của từng lô rồi gửi duyệt. Admin duyệt để chiến dịch có thể hiển thị công khai khi đến thời gian bắt đầu.
5. Admin tạo banner sau khi đã có chiến dịch hoạt động để chọn theo tên.

**Chiến dịch và bảng tin đã dùng chung một nội dung:** Seller vào **Quản lý chiến dịch** để tạo chiến dịch. Tên, mô tả, ảnh và sản phẩm/lô hàng của chiến dịch cũng là nội dung hiển thị trên bảng tin cộng đồng sau khi được duyệt và đủ điều kiện công khai. Không có bước tạo bài viết Post độc lập, không nhập Campaign ID hay Product ID vào một form bài viết riêng.

**Quy ước chung:** Tiền và số lượng nhập bằng số thuần, ví dụ `30000`, không thêm `đ`, dấu chấm phân cách hay `kg`. Ngày dùng `yyyy-MM-dd`. Mốc ngày được soạn để nhập từ **2026-10-18**; nếu nhập vào ngày khác, dịch toàn bộ lịch tương ứng để hạn dùng và ngày kết thúc vẫn ở tương lai. Hạn dùng trong tài liệu chỉ phục vụ demo, không phải hướng dẫn xác định hạn dùng thực phẩm.

**Ảnh:** Bạn tự chọn và tải bằng nút upload của từng form. Ảnh sản phẩm nên đúng loại và ít chữ; ảnh chiến dịch nên cùng nhóm hàng; banner có thể thiết kế theo tiêu đề đã cho. Form banner không có ô mô tả/nút hành động riêng, nên chỉ điền các trường thực sự được liệt kê. Nếu dùng ảnh ngoài, dùng đường dẫn HTTPS theo tùy chọn hiện có.

**Vị trí:** Chiến dịch hiện yêu cầu GPS và địa chỉ đủ tỉnh/thành, quận/huyện, phường/xã, số nhà/tên đường. Bấm **Lấy GPS** tại điểm giải cứu thật và chọn địa chỉ tương ứng. Tỉnh nguồn gốc của sản phẩm có thể khác địa chỉ cửa hàng. Tài liệu không cung cấp tọa độ hay địa chỉ giả để tránh làm sai khoảng cách và phí vận chuyển.

Các mã **SP01**, **CD01**… chỉ là số thứ tự trong tài liệu, không phải ID Firestore để nhập vào app. Những ID thật do app sinh ra không được thay bằng các mã này.

## 1. Danh mục — Admin
Tất cả bật **Đang hiển thị**. Ảnh tự tải. **Lý do quản trị** để trống khi tạo danh mục đang hiển thị. Thứ tự dùng các số bên dưới. Với hai danh mục có sẵn, giữ ID hiện tại; chỉ bổ sung mô tả/ảnh nếu muốn.

| Tên danh mục | ID ổn định nếu tạo mới | Thứ tự hiển thị | Mô tả danh mục |
|---|---|---:|---|
| Trái cây | Dùng danh mục hiện có | 10 | Trái cây theo mùa, đa dạng kích cỡ và hương vị, phù hợp dùng tươi hoặc chế biến tại nhà. |
| Rau xanh | Dùng danh mục hiện có | 20 | Các loại rau lá cho bữa cơm gia đình, được phân loại và đóng gói theo lượng đặt. |
| Củ & quả | dm-cu-qua | 30 | Cà rốt, khoai, bí và các loại củ quả quen thuộc, thích hợp chế biến nhiều món ăn. |
| Nấm tươi | dm-nam-tuoi | 40 | Nấm tươi cho món canh, món xào và lẩu; thông tin từng lô được cập nhật riêng. |
| Gạo & ngũ cốc | dm-gao-ngu-coc | 50 | Gạo và ngũ cốc cho bữa ăn hằng ngày, bán theo khối lượng để dễ lựa chọn. |
| Đậu & hạt | dm-dau-hat | 60 | Các loại đậu và hạt khô, phù hợp nấu chè, nấu cơm và chế biến món ăn gia đình. |
| Gia vị tươi | dm-gia-vi-tuoi | 70 | Hành, ngò, gừng và sả giúp bữa ăn thêm hương vị, có thể mua theo nhu cầu. |
| Rau củ ăn quả | dm-rau-cu-an-qua | 80 | Dưa leo, khổ qua, bầu và mướp cho các món luộc, xào và canh quen thuộc. |

## 2. Sản phẩm — Seller
Mở **Quản lý sản phẩm → Thêm sản phẩm**. Chung cho cả 32 mục: **Đơn vị: `kg`**; chọn trạng thái đang bán; tải ảnh tương ứng. Nguồn gốc và tỉnh/thành là hai trường riêng. Giá đều tính cho 1 kg.

### SP01. Thanh long ruột đỏ Bình Thuận

- **Danh mục:** Trái cây
- **Tên sản phẩm:** Thanh long ruột đỏ Bình Thuận
- **Mô tả:** Thanh long ruột đỏ, vị ngọt thanh, vỏ có thể không đều màu. Thích hợp ăn tươi, làm sinh tố hoặc sữa chua trái cây. Sản phẩm được bán theo khối lượng, kích cỡ quả có thể khác nhau.
- **Giá gốc:** `30000`
- **Giá giải cứu:** `18000`
- **Đơn vị:** `kg`
- **Nguồn gốc:** Bình Thuận
- **Tỉnh/thành:** Bình Thuận

### SP02. Chuối xiêm Tiền Giang

- **Danh mục:** Trái cây
- **Tên sản phẩm:** Chuối xiêm Tiền Giang
- **Mô tả:** Chuối xiêm theo nải, quả có độ chín khác nhau để thuận tiện sử dụng dần. Có thể ăn tươi, hấp hoặc chế biến món tráng miệng. Khối lượng được tính theo lượng thực tế đóng gói.
- **Giá gốc:** `22000`
- **Giá giải cứu:** `14000`
- **Đơn vị:** `kg`
- **Nguồn gốc:** Tiền Giang
- **Tỉnh/thành:** Tiền Giang

### SP03. Cam sành Vĩnh Long

- **Danh mục:** Trái cây
- **Tên sản phẩm:** Cam sành Vĩnh Long
- **Mô tả:** Cam sành có vị chua ngọt, thích hợp vắt nước hoặc dùng tươi. Vỏ có thể sần và màu sắc không đồng đều. Cửa hàng phân loại quả còn nguyên vẹn trước khi đóng gói.
- **Giá gốc:** `28000`
- **Giá giải cứu:** `19000`
- **Đơn vị:** `kg`
- **Nguồn gốc:** Vĩnh Long
- **Tỉnh/thành:** Vĩnh Long

### SP04. Ổi ruột trắng Long An

- **Danh mục:** Trái cây
- **Tên sản phẩm:** Ổi ruột trắng Long An
- **Mô tả:** Ổi ruột trắng, thịt giòn, hương thơm nhẹ. Quả có nhiều kích cỡ, phù hợp ăn tươi hoặc làm nước ép. Rửa sạch trước khi dùng và bảo quản mát sau khi nhận.
- **Giá gốc:** `25000`
- **Giá giải cứu:** `16000`
- **Đơn vị:** `kg`
- **Nguồn gốc:** Long An
- **Tỉnh/thành:** Long An

### SP05. Cải ngọt Đà Lạt

- **Danh mục:** Rau xanh
- **Tên sản phẩm:** Cải ngọt Đà Lạt
- **Mô tả:** Cải ngọt có thân mềm, lá xanh, phù hợp luộc, xào hoặc nấu canh. Rau được nhặt bỏ phần hư trước khi đóng gói. Rửa sạch và để ráo trước khi chế biến.
- **Giá gốc:** `24000`
- **Giá giải cứu:** `16000`
- **Đơn vị:** `kg`
- **Nguồn gốc:** Đà Lạt, Lâm Đồng
- **Tỉnh/thành:** Lâm Đồng

### SP06. Mồng tơi Long An

- **Danh mục:** Rau xanh
- **Tên sản phẩm:** Mồng tơi Long An
- **Mô tả:** Mồng tơi dùng cho món canh rau và các bữa cơm gia đình. Lá và ngọn được gom theo khối lượng đặt, có thể khác nhau về kích thước. Nên chế biến sớm sau khi nhận.
- **Giá gốc:** `22000`
- **Giá giải cứu:** `15000`
- **Đơn vị:** `kg`
- **Nguồn gốc:** Long An
- **Tỉnh/thành:** Long An

### SP07. Rau muống Củ Chi

- **Danh mục:** Rau xanh
- **Tên sản phẩm:** Rau muống Củ Chi
- **Mô tả:** Rau muống cọng vừa, phù hợp luộc hoặc xào tỏi. Sản phẩm bán theo khối lượng sau khi phân loại sơ bộ. Nhặt, rửa kỹ và nấu chín trước khi sử dụng.
- **Giá gốc:** `20000`
- **Giá giải cứu:** `13000`
- **Đơn vị:** `kg`
- **Nguồn gốc:** Củ Chi, Thành phố Hồ Chí Minh
- **Tỉnh/thành:** Thành phố Hồ Chí Minh

### SP08. Xà lách lô lô Đà Lạt

- **Danh mục:** Rau xanh
- **Tên sản phẩm:** Xà lách lô lô Đà Lạt
- **Mô tả:** Xà lách lô lô có lá xoăn, thích hợp dùng kèm món cuốn hoặc làm salad. Lá ngoài có thể được tỉa bớt khi đóng gói. Rửa kỹ từng lá trước khi dùng và bảo quản mát.
- **Giá gốc:** `38000`
- **Giá giải cứu:** `26000`
- **Đơn vị:** `kg`
- **Nguồn gốc:** Đà Lạt, Lâm Đồng
- **Tỉnh/thành:** Lâm Đồng

### SP09. Cà rốt Đà Lạt

- **Danh mục:** Củ & quả
- **Tên sản phẩm:** Cà rốt Đà Lạt
- **Mô tả:** Cà rốt có nhiều kích cỡ, màu cam tự nhiên, phù hợp nấu canh, hầm hoặc làm nước ép. Củ có hình dáng không đồng đều nhưng được phân loại trước khi đóng gói.
- **Giá gốc:** `28000`
- **Giá giải cứu:** `19000`
- **Đơn vị:** `kg`
- **Nguồn gốc:** Đà Lạt, Lâm Đồng
- **Tỉnh/thành:** Lâm Đồng

### SP10. Khoai lang tím Vĩnh Long

- **Danh mục:** Củ & quả
- **Tên sản phẩm:** Khoai lang tím Vĩnh Long
- **Mô tả:** Khoai lang tím có ruột tím, phù hợp hấp, nướng hoặc làm bánh. Củ có thể cong và khác nhau về kích cỡ. Bảo quản nơi khô thoáng, tránh để trong túi kín khi còn ẩm.
- **Giá gốc:** `32000`
- **Giá giải cứu:** `22000`
- **Đơn vị:** `kg`
- **Nguồn gốc:** Vĩnh Long
- **Tỉnh/thành:** Vĩnh Long

### SP11. Bí đỏ hồ lô Đắk Lắk

- **Danh mục:** Củ & quả
- **Tên sản phẩm:** Bí đỏ hồ lô Đắk Lắk
- **Mô tả:** Bí đỏ hồ lô nguyên quả, thích hợp nấu canh, nấu súp hoặc hấp. Kích thước từng quả khác nhau nên lượng giao có thể cần xác nhận theo khối lượng thực tế.
- **Giá gốc:** `26000`
- **Giá giải cứu:** `18000`
- **Đơn vị:** `kg`
- **Nguồn gốc:** Đắk Lắk
- **Tỉnh/thành:** Đắk Lắk

### SP12. Cà chua chín Lâm Đồng

- **Danh mục:** Củ & quả
- **Tên sản phẩm:** Cà chua chín Lâm Đồng
- **Mô tả:** Cà chua chín có màu đỏ đến đỏ cam, phù hợp nấu sốt, nấu canh hoặc xào. Quả có thể không tròn đều. Cửa hàng chỉ đóng gói quả còn nguyên vẹn, không dập hỏng.
- **Giá gốc:** `30000`
- **Giá giải cứu:** `18000`
- **Đơn vị:** `kg`
- **Nguồn gốc:** Lâm Đồng
- **Tỉnh/thành:** Lâm Đồng

### SP13. Nấm bào ngư trắng Đồng Nai

- **Danh mục:** Nấm tươi
- **Tên sản phẩm:** Nấm bào ngư trắng Đồng Nai
- **Mô tả:** Nấm bào ngư trắng có tai mềm, dùng cho món xào, canh hoặc lẩu. Sản phẩm được đóng theo lượng đặt. Bảo quản lạnh và nấu chín kỹ trước khi sử dụng.
- **Giá gốc:** `65000`
- **Giá giải cứu:** `45000`
- **Đơn vị:** `kg`
- **Nguồn gốc:** Đồng Nai
- **Tỉnh/thành:** Đồng Nai

### SP14. Nấm bào ngư xám Đồng Nai

- **Danh mục:** Nấm tươi
- **Tên sản phẩm:** Nấm bào ngư xám Đồng Nai
- **Mô tả:** Nấm bào ngư xám có tai dày vừa, phù hợp xào rau củ hoặc nấu cháo. Màu sắc và kích cỡ tai nấm có thể khác nhau. Bảo quản lạnh ngay sau khi nhận.
- **Giá gốc:** `75000`
- **Giá giải cứu:** `52000`
- **Đơn vị:** `kg`
- **Nguồn gốc:** Đồng Nai
- **Tỉnh/thành:** Đồng Nai

### SP15. Nấm kim châm Lâm Đồng

- **Danh mục:** Nấm tươi
- **Tên sản phẩm:** Nấm kim châm Lâm Đồng
- **Mô tả:** Nấm kim châm phù hợp nấu lẩu, nấu canh hoặc cuộn cùng nguyên liệu khác. Bán theo khối lượng, không theo số gói. Giữ lạnh và nấu chín kỹ trước khi dùng.
- **Giá gốc:** `80000`
- **Giá giải cứu:** `58000`
- **Đơn vị:** `kg`
- **Nguồn gốc:** Lâm Đồng
- **Tỉnh/thành:** Lâm Đồng

### SP16. Nấm đùi gà Lâm Đồng

- **Danh mục:** Nấm tươi
- **Tên sản phẩm:** Nấm đùi gà Lâm Đồng
- **Mô tả:** Nấm đùi gà có thân chắc, phù hợp áp chảo, xào hoặc kho. Sản phẩm có thể khác nhau về chiều dài và kích cỡ. Bảo quản lạnh và sử dụng theo hạn của lô hàng.
- **Giá gốc:** `95000`
- **Giá giải cứu:** `68000`
- **Đơn vị:** `kg`
- **Nguồn gốc:** Lâm Đồng
- **Tỉnh/thành:** Lâm Đồng

### SP17. Gạo tẻ Long An

- **Danh mục:** Gạo & ngũ cốc
- **Tên sản phẩm:** Gạo tẻ Long An
- **Mô tả:** Gạo tẻ dùng cho bữa cơm hằng ngày, bán theo khối lượng. Bảo quản trong hộp kín ở nơi khô ráo sau khi mở. Vo và nấu chín trước khi sử dụng.
- **Giá gốc:** `24000`
- **Giá giải cứu:** `19000`
- **Đơn vị:** `kg`
- **Nguồn gốc:** Long An
- **Tỉnh/thành:** Long An

### SP18. Gạo lứt đỏ Đồng Tháp

- **Danh mục:** Gạo & ngũ cốc
- **Tên sản phẩm:** Gạo lứt đỏ Đồng Tháp
- **Mô tả:** Gạo lứt đỏ còn lớp cám, thích hợp nấu cơm hoặc kết hợp cùng các loại đậu. Có thể ngâm trước khi nấu tùy khẩu vị. Bảo quản khô ráo, tránh ánh nắng trực tiếp.
- **Giá gốc:** `42000`
- **Giá giải cứu:** `32000`
- **Đơn vị:** `kg`
- **Nguồn gốc:** Đồng Tháp
- **Tỉnh/thành:** Đồng Tháp

### SP19. Bắp nếp tươi Đồng Nai

- **Danh mục:** Gạo & ngũ cốc
- **Tên sản phẩm:** Bắp nếp tươi Đồng Nai
- **Mô tả:** Bắp nếp tươi bán theo khối lượng nguyên trái, thích hợp luộc, hấp hoặc nấu chè. Kích cỡ từng trái khác nhau. Nên chế biến sớm và bảo quản mát khi chưa sử dụng.
- **Giá gốc:** `26000`
- **Giá giải cứu:** `18000`
- **Đơn vị:** `kg`
- **Nguồn gốc:** Đồng Nai
- **Tỉnh/thành:** Đồng Nai

### SP20. Hạt kê vàng Sơn La

- **Danh mục:** Gạo & ngũ cốc
- **Tên sản phẩm:** Hạt kê vàng Sơn La
- **Mô tả:** Hạt kê vàng dạng khô, phù hợp nấu cháo hoặc kết hợp cùng ngũ cốc khác. Nhặt và rửa trước khi nấu. Bảo quản kín ở nơi khô thoáng, tránh ẩm.
- **Giá gốc:** `50000`
- **Giá giải cứu:** `37000`
- **Đơn vị:** `kg`
- **Nguồn gốc:** Sơn La
- **Tỉnh/thành:** Sơn La

### SP21. Đậu xanh nguyên hạt Đắk Lắk

- **Danh mục:** Đậu & hạt
- **Tên sản phẩm:** Đậu xanh nguyên hạt Đắk Lắk
- **Mô tả:** Đậu xanh nguyên hạt, thích hợp nấu chè, nấu cháo hoặc làm nhân bánh. Hạt có thể khác nhau về kích thước. Nhặt, rửa sạch và nấu chín trước khi dùng.
- **Giá gốc:** `48000`
- **Giá giải cứu:** `36000`
- **Đơn vị:** `kg`
- **Nguồn gốc:** Đắk Lắk
- **Tỉnh/thành:** Đắk Lắk

### SP22. Đậu đen Đắk Nông

- **Danh mục:** Đậu & hạt
- **Tên sản phẩm:** Đậu đen Đắk Nông
- **Mô tả:** Đậu đen dạng hạt khô, dùng cho món chè hoặc kết hợp nấu cơm. Bán theo khối lượng để dễ chọn lượng phù hợp. Bảo quản kín, tránh nơi ẩm và nấu chín trước khi dùng.
- **Giá gốc:** `45000`
- **Giá giải cứu:** `33000`
- **Đơn vị:** `kg`
- **Nguồn gốc:** Đắk Nông
- **Tỉnh/thành:** Đắk Nông

### SP23. Đậu đỏ Gia Lai

- **Danh mục:** Đậu & hạt
- **Tên sản phẩm:** Đậu đỏ Gia Lai
- **Mô tả:** Đậu đỏ khô phù hợp nấu chè, cháo hoặc chế biến nhân bánh. Có thể ngâm trước khi nấu để hạt mềm đều. Bảo quản nơi khô ráo sau khi nhận.
- **Giá gốc:** `55000`
- **Giá giải cứu:** `41000`
- **Đơn vị:** `kg`
- **Nguồn gốc:** Gia Lai
- **Tỉnh/thành:** Gia Lai

### SP24. Đậu phộng nhân Tây Ninh

- **Danh mục:** Đậu & hạt
- **Tên sản phẩm:** Đậu phộng nhân Tây Ninh
- **Mô tả:** Đậu phộng nhân khô dùng để rang hoặc chế biến món ăn. Sản phẩm có chứa đậu phộng, không phù hợp với người dị ứng thành phần này. Bảo quản kín và kiểm tra trước khi chế biến.
- **Giá gốc:** `60000`
- **Giá giải cứu:** `45000`
- **Đơn vị:** `kg`
- **Nguồn gốc:** Tây Ninh
- **Tỉnh/thành:** Tây Ninh

### SP25. Hành lá Long An

- **Danh mục:** Gia vị tươi
- **Tên sản phẩm:** Hành lá Long An
- **Mô tả:** Hành lá có cả phần lá và gốc, phù hợp nêm canh, xào hoặc làm mỡ hành. Bán theo khối lượng. Nhặt và rửa sạch trước khi chế biến, bảo quản mát khi chưa dùng.
- **Giá gốc:** `40000`
- **Giá giải cứu:** `27000`
- **Đơn vị:** `kg`
- **Nguồn gốc:** Long An
- **Tỉnh/thành:** Long An

### SP26. Ngò rí Tiền Giang

- **Danh mục:** Gia vị tươi
- **Tên sản phẩm:** Ngò rí Tiền Giang
- **Mô tả:** Ngò rí có mùi thơm đặc trưng, dùng làm rau gia vị cho nhiều món ăn. Cây có thể khác nhau về kích cỡ. Rửa kỹ, để ráo và bảo quản mát sau khi nhận.
- **Giá gốc:** `50000`
- **Giá giải cứu:** `34000`
- **Đơn vị:** `kg`
- **Nguồn gốc:** Tiền Giang
- **Tỉnh/thành:** Tiền Giang

### SP27. Gừng tươi Quảng Nam

- **Danh mục:** Gia vị tươi
- **Tên sản phẩm:** Gừng tươi Quảng Nam
- **Mô tả:** Gừng tươi nguyên củ, thích hợp làm gia vị, pha đồ uống hoặc chế biến món kho. Vỏ có thể bám đất nhẹ. Rửa sạch, cắt bỏ phần không sử dụng trước khi chế biến.
- **Giá gốc:** `45000`
- **Giá giải cứu:** `31000`
- **Đơn vị:** `kg`
- **Nguồn gốc:** Quảng Nam
- **Tỉnh/thành:** Quảng Nam

### SP28. Sả cây Tiền Giang

- **Danh mục:** Gia vị tươi
- **Tên sản phẩm:** Sả cây Tiền Giang
- **Mô tả:** Sả cây bán theo khối lượng, phù hợp nấu nước dùng, kho hoặc ướp món ăn. Cây có thể khác nhau về độ lớn. Bóc lớp ngoài và rửa sạch trước khi sử dụng.
- **Giá gốc:** `22000`
- **Giá giải cứu:** `15000`
- **Đơn vị:** `kg`
- **Nguồn gốc:** Tiền Giang
- **Tỉnh/thành:** Tiền Giang

### SP29. Dưa leo Long An

- **Danh mục:** Rau củ ăn quả
- **Tên sản phẩm:** Dưa leo Long An
- **Mô tả:** Dưa leo có kích cỡ vừa và nhỏ, phù hợp ăn kèm, làm salad hoặc chế biến món xào. Quả có thể cong nhẹ. Rửa kỹ trước khi dùng và bảo quản mát sau khi nhận.
- **Giá gốc:** `25000`
- **Giá giải cứu:** `17000`
- **Đơn vị:** `kg`
- **Nguồn gốc:** Long An
- **Tỉnh/thành:** Long An

### SP30. Khổ qua Tây Ninh

- **Danh mục:** Rau củ ăn quả
- **Tên sản phẩm:** Khổ qua Tây Ninh
- **Mô tả:** Khổ qua có vị đắng đặc trưng, thích hợp nấu canh hoặc xào trứng. Kích cỡ quả không đồng đều. Rửa sạch và sơ chế theo món ăn trước khi nấu.
- **Giá gốc:** `32000`
- **Giá giải cứu:** `22000`
- **Đơn vị:** `kg`
- **Nguồn gốc:** Tây Ninh
- **Tỉnh/thành:** Tây Ninh

### SP31. Bầu xanh Long An

- **Danh mục:** Rau củ ăn quả
- **Tên sản phẩm:** Bầu xanh Long An
- **Mô tả:** Bầu xanh nguyên quả, thích hợp nấu canh, luộc hoặc xào. Khối lượng mỗi quả khác nhau nên cửa hàng có thể xác nhận lượng thực tế khi chuẩn bị đơn.
- **Giá gốc:** `20000`
- **Giá giải cứu:** `14000`
- **Đơn vị:** `kg`
- **Nguồn gốc:** Long An
- **Tỉnh/thành:** Long An

### SP32. Mướp hương Tiền Giang

- **Danh mục:** Rau củ ăn quả
- **Tên sản phẩm:** Mướp hương Tiền Giang
- **Mô tả:** Mướp hương dùng cho món canh hoặc xào, có mùi thơm nhẹ khi nấu. Quả có nhiều chiều dài khác nhau. Bảo quản nơi thoáng mát và sử dụng theo hạn của lô hàng.
- **Giá gốc:** `28000`
- **Giá giải cứu:** `19000`
- **Đơn vị:** `kg`
- **Nguồn gốc:** Tiền Giang
- **Tỉnh/thành:** Tiền Giang

## 3. Lô hàng — Seller
Trong mỗi sản phẩm mở **Quản lý batch và tồn kho**, tạo đúng một lô theo bảng. **Ngày thu hoạch** chung: `2026-10-17`. Điền **Hạn sử dụng** và **Số lượng ban đầu** như dưới. Không có trường để tự nhập đã bán, đã giữ hay tiến độ: các giá trị đó phát sinh từ đơn hàng thực tế. Tồn kho không phải lượng mục tiêu chiến dịch; mục tiêu được nhập ở phần tiếp theo.

| Mã tra cứu | Sản phẩm | Ngày thu hoạch | Hạn sử dụng | Số lượng ban đầu (kg) |
|---|---|---|---|---:|
| SP01 | Thanh long ruột đỏ Bình Thuận | 2026-10-17 | 2026-10-25 | 120 |
| SP02 | Chuối xiêm Tiền Giang | 2026-10-17 | 2026-10-24 | 90 |
| SP03 | Cam sành Vĩnh Long | 2026-10-17 | 2026-10-28 | 100 |
| SP04 | Ổi ruột trắng Long An | 2026-10-17 | 2026-10-26 | 80 |
| SP05 | Cải ngọt Đà Lạt | 2026-10-17 | 2026-10-22 | 50 |
| SP06 | Mồng tơi Long An | 2026-10-17 | 2026-10-22 | 40 |
| SP07 | Rau muống Củ Chi | 2026-10-17 | 2026-10-21 | 60 |
| SP08 | Xà lách lô lô Đà Lạt | 2026-10-17 | 2026-10-21 | 35 |
| SP09 | Cà rốt Đà Lạt | 2026-10-17 | 2026-10-29 | 100 |
| SP10 | Khoai lang tím Vĩnh Long | 2026-10-17 | 2026-11-05 | 130 |
| SP11 | Bí đỏ hồ lô Đắk Lắk | 2026-10-17 | 2026-11-10 | 110 |
| SP12 | Cà chua chín Lâm Đồng | 2026-10-17 | 2026-10-25 | 80 |
| SP13 | Nấm bào ngư trắng Đồng Nai | 2026-10-17 | 2026-10-21 | 25 |
| SP14 | Nấm bào ngư xám Đồng Nai | 2026-10-17 | 2026-10-21 | 25 |
| SP15 | Nấm kim châm Lâm Đồng | 2026-10-17 | 2026-10-23 | 20 |
| SP16 | Nấm đùi gà Lâm Đồng | 2026-10-17 | 2026-10-23 | 20 |
| SP17 | Gạo tẻ Long An | 2026-10-17 | 2027-01-17 | 150 |
| SP18 | Gạo lứt đỏ Đồng Tháp | 2026-10-17 | 2027-01-17 | 100 |
| SP19 | Bắp nếp tươi Đồng Nai | 2026-10-17 | 2026-10-24 | 70 |
| SP20 | Hạt kê vàng Sơn La | 2026-10-17 | 2027-01-17 | 60 |
| SP21 | Đậu xanh nguyên hạt Đắk Lắk | 2026-10-17 | 2027-01-17 | 80 |
| SP22 | Đậu đen Đắk Nông | 2026-10-17 | 2027-01-17 | 70 |
| SP23 | Đậu đỏ Gia Lai | 2026-10-17 | 2027-01-17 | 65 |
| SP24 | Đậu phộng nhân Tây Ninh | 2026-10-17 | 2027-01-17 | 75 |
| SP25 | Hành lá Long An | 2026-10-17 | 2026-10-22 | 25 |
| SP26 | Ngò rí Tiền Giang | 2026-10-17 | 2026-10-22 | 20 |
| SP27 | Gừng tươi Quảng Nam | 2026-10-17 | 2026-11-10 | 60 |
| SP28 | Sả cây Tiền Giang | 2026-10-17 | 2026-10-28 | 50 |
| SP29 | Dưa leo Long An | 2026-10-17 | 2026-10-25 | 80 |
| SP30 | Khổ qua Tây Ninh | 2026-10-17 | 2026-10-25 | 60 |
| SP31 | Bầu xanh Long An | 2026-10-17 | 2026-10-28 | 90 |
| SP32 | Mướp hương Tiền Giang | 2026-10-17 | 2026-10-25 | 70 |

## 4. Chiến dịch — Seller tạo, Admin duyệt

Đây cũng là nơi tạo nội dung xuất hiện trên **bảng tin cộng đồng**. Dùng trực tiếp **Tên chiến dịch**, **Mô tả chiến dịch** và ảnh dưới đây trong form chiến dịch; không cần đăng lại ở một form bài viết khác. Các trang Home, bảng tin và chi tiết chiến dịch cùng tham chiếu chiến dịch đã tạo.
Chung cho cả 16 chiến dịch:

- **Hình thức điểm giải cứu:** Điểm bán cố định.
- **Ngày bắt đầu:** `2026-10-18`.
- **GPS và địa chỉ điểm giải cứu:** dùng địa chỉ thật của điểm nhận hàng; lấy GPS tại đó và chọn đủ địa chỉ trong form.
- **Ảnh:** tự tải, tối đa 6 ảnh theo form; nên có ít nhất một ảnh đúng nhóm hàng.
- Trong danh sách lô, tích đúng hai lô của hai sản phẩm được nêu. Mỗi sản phẩm chỉ có một lô mới theo bảng trên nên dễ nhận diện.
- Sau khi nhập, chọn **Gửi chờ duyệt**; lưu nháp sẽ chưa hiển thị công khai.
- Không nhập mức độ khẩn cấp hoặc tiến độ: code tính từ thời gian/hạn lô và giao dịch. Mỗi lô dưới đây chỉ được dùng trong một chiến dịch để tránh trùng liên kết.

### CD01. Thanh long và chuối xiêm: ngọt lành mùa thu hoạch

- **Tên chiến dịch:** Thanh long và chuối xiêm: ngọt lành mùa thu hoạch
- **Mô tả chiến dịch:** Thanh long ruột đỏ và chuối xiêm đang vào đợt thu hoạch tập trung. Cửa hàng mở chiến dịch để kết nối lượng nông sản còn lại với những bữa ăn gia đình. Bạn có thể chọn từng sản phẩm và số lượng phù hợp, không cần mua cả bộ. Hình dáng và kích cỡ có thể khác nhau giữa các quả.
- **Nguyên nhân giải cứu:** Thu hoạch theo mùa
- **Ngày bắt đầu:** `2026-10-18`
- **Ngày kết thúc:** `2026-10-22`
- **Lô thứ nhất:** Thanh long ruột đỏ Bình Thuận — **Lượng mục tiêu:** `60` kg.
- **Lô thứ hai:** Chuối xiêm Tiền Giang — **Lượng mục tiêu:** `45` kg.
- **Tổng mục tiêu để đối chiếu:** 105 kg; không cần tìm ô nhập tổng riêng.

### CD02. Cam sành, ổi giòn: thêm trái cây cho mỗi ngày

- **Tên chiến dịch:** Cam sành, ổi giòn: thêm trái cây cho mỗi ngày
- **Mô tả chiến dịch:** Lượng cam sành và ổi về cùng thời điểm nhiều hơn kế hoạch bán trong tuần. Cửa hàng dành giá giải cứu cho phần còn lại để nông sản sớm đến tay người dùng. Cam phù hợp vắt nước, ổi thích hợp dùng tươi. Số lượng khả dụng được cập nhật theo từng đơn.
- **Nguyên nhân giải cứu:** Dư nguồn cung
- **Ngày bắt đầu:** `2026-10-18`
- **Ngày kết thúc:** `2026-10-25`
- **Lô thứ nhất:** Cam sành Vĩnh Long — **Lượng mục tiêu:** `50` kg.
- **Lô thứ hai:** Ổi ruột trắng Long An — **Lượng mục tiêu:** `40` kg.
- **Tổng mục tiêu để đối chiếu:** 90 kg; không cần tìm ô nhập tổng riêng.

### CD03. Bữa cơm xanh cùng cải ngọt và mồng tơi

- **Tên chiến dịch:** Bữa cơm xanh cùng cải ngọt và mồng tơi
- **Mô tả chiến dịch:** Cải ngọt và mồng tơi đã được tập kết cho đợt bán trong tuần nhưng lượng đặt thấp hơn dự kiến. Chiến dịch ưu tiên đưa rau đến bữa cơm gia đình trong thời gian còn phù hợp sử dụng. Bạn có thể chọn một trong hai loại hoặc đặt cả hai theo nhu cầu.
- **Nguyên nhân giải cứu:** Dư nguồn cung
- **Ngày bắt đầu:** `2026-10-18`
- **Ngày kết thúc:** `2026-10-21`
- **Lô thứ nhất:** Cải ngọt Đà Lạt — **Lượng mục tiêu:** `25` kg.
- **Lô thứ hai:** Mồng tơi Long An — **Lượng mục tiêu:** `20` kg.
- **Tổng mục tiêu để đối chiếu:** 45 kg; không cần tìm ô nhập tổng riêng.

### CD04. Rau muống, xà lách: nhận sớm trong hôm nay

- **Tên chiến dịch:** Rau muống, xà lách: nhận sớm trong hôm nay
- **Mô tả chiến dịch:** Lô rau muống và xà lách đang đến gần hạn sử dụng đã khai báo. Cửa hàng mở đợt giải cứu ngắn để ưu tiên tiêu thụ sớm. Sản phẩm được kiểm tra trước khi đóng gói; bạn nên bảo quản mát và sử dụng sớm sau khi nhận.
- **Nguyên nhân giải cứu:** Sắp hết hạn
- **Ngày bắt đầu:** `2026-10-18`
- **Ngày kết thúc:** `2026-10-19`
- **Lô thứ nhất:** Rau muống Củ Chi — **Lượng mục tiêu:** `30` kg.
- **Lô thứ hai:** Xà lách lô lô Đà Lạt — **Lượng mục tiêu:** `17` kg.
- **Tổng mục tiêu để đối chiếu:** 47 kg; không cần tìm ô nhập tổng riêng.

### CD05. Cà rốt và khoai tím: ngon từ những củ không đều

- **Tên chiến dịch:** Cà rốt và khoai tím: ngon từ những củ không đều
- **Mô tả chiến dịch:** Cà rốt và khoai lang tím trong đợt này có kích cỡ không đồng đều, một số củ cong hoặc nhỏ hơn quy cách bán thông thường. Cửa hàng phân loại phần còn nguyên vẹn để bán theo khối lượng. Những củ giản dị vẫn có thể trở thành món canh, món hấp và món nướng cho cả nhà.
- **Nguyên nhân giải cứu:** Hình thức không đạt chuẩn
- **Ngày bắt đầu:** `2026-10-18`
- **Ngày kết thúc:** `2026-10-25`
- **Lô thứ nhất:** Cà rốt Đà Lạt — **Lượng mục tiêu:** `50` kg.
- **Lô thứ hai:** Khoai lang tím Vĩnh Long — **Lượng mục tiêu:** `65` kg.
- **Tổng mục tiêu để đối chiếu:** 115 kg; không cần tìm ô nhập tổng riêng.

### CD06. Bí đỏ và cà chua: nguyên liệu cho gian bếp ấm

- **Tên chiến dịch:** Bí đỏ và cà chua: nguyên liệu cho gian bếp ấm
- **Mô tả chiến dịch:** Một đơn đặt số lượng lớn đã được hủy trước khi nhận, để lại bí đỏ và cà chua đã chuẩn bị. Cửa hàng chia lại theo khối lượng nhỏ để các gia đình dễ lựa chọn. Bạn có thể đặt riêng từng loại và theo dõi lượng còn lại ngay trong chiến dịch.
- **Nguyên nhân giải cứu:** Đơn hàng bị hủy
- **Ngày bắt đầu:** `2026-10-18`
- **Ngày kết thúc:** `2026-10-22`
- **Lô thứ nhất:** Bí đỏ hồ lô Đắk Lắk — **Lượng mục tiêu:** `55` kg.
- **Lô thứ hai:** Cà chua chín Lâm Đồng — **Lượng mục tiêu:** `40` kg.
- **Tổng mục tiêu để đối chiếu:** 95 kg; không cần tìm ô nhập tổng riêng.

### CD07. Giải cứu nấm bào ngư trắng và xám

- **Tên chiến dịch:** Giải cứu nấm bào ngư trắng và xám
- **Mô tả chiến dịch:** Hai lô nấm bào ngư đang cần tiêu thụ sớm theo hạn sử dụng đã khai báo. Chiến dịch áp dụng cho nấm bào ngư trắng và nấm bào ngư xám còn khả dụng tại cửa hàng. Sau khi nhận, hãy bảo quản lạnh và nấu chín kỹ trước khi dùng.
- **Nguyên nhân giải cứu:** Sắp hết hạn
- **Ngày bắt đầu:** `2026-10-18`
- **Ngày kết thúc:** `2026-10-19`
- **Lô thứ nhất:** Nấm bào ngư trắng Đồng Nai — **Lượng mục tiêu:** `12` kg.
- **Lô thứ hai:** Nấm bào ngư xám Đồng Nai — **Lượng mục tiêu:** `12` kg.
- **Tổng mục tiêu để đối chiếu:** 24 kg; không cần tìm ô nhập tổng riêng.

### CD08. Nấm kim châm, nấm đùi gà cho bữa lẩu tại nhà

- **Tên chiến dịch:** Nấm kim châm, nấm đùi gà cho bữa lẩu tại nhà
- **Mô tả chiến dịch:** Lượng nấm chuẩn bị cho tuần này còn nhiều do nhu cầu đặt hàng giảm. Cửa hàng điều chỉnh giá để người dùng dễ chọn nguyên liệu cho món canh, món xào hoặc lẩu. Mỗi loại được bán riêng theo khối lượng; hình ảnh chỉ minh họa loại nấm.
- **Nguyên nhân giải cứu:** Nhu cầu giảm
- **Ngày bắt đầu:** `2026-10-18`
- **Ngày kết thúc:** `2026-10-22`
- **Lô thứ nhất:** Nấm kim châm Lâm Đồng — **Lượng mục tiêu:** `10` kg.
- **Lô thứ hai:** Nấm đùi gà Lâm Đồng — **Lượng mục tiêu:** `10` kg.
- **Tổng mục tiêu để đối chiếu:** 20 kg; không cần tìm ô nhập tổng riêng.

### CD09. Gạo tẻ và gạo lứt: chia nhỏ lượng hàng còn lại

- **Tên chiến dịch:** Gạo tẻ và gạo lứt: chia nhỏ lượng hàng còn lại
- **Mô tả chiến dịch:** Cửa hàng đang có lượng gạo tẻ và gạo lứt đỏ lớn hơn kế hoạch lưu kho. Chiến dịch bán theo khối lượng để phù hợp cả nhu cầu gia đình lẫn nhóm mua chung. Mỗi sản phẩm có giá riêng và có thể được đặt độc lập.
- **Nguyên nhân giải cứu:** Dư nguồn cung
- **Ngày bắt đầu:** `2026-10-18`
- **Ngày kết thúc:** `2026-10-30`
- **Lô thứ nhất:** Gạo tẻ Long An — **Lượng mục tiêu:** `75` kg.
- **Lô thứ hai:** Gạo lứt đỏ Đồng Tháp — **Lượng mục tiêu:** `50` kg.
- **Tổng mục tiêu để đối chiếu:** 125 kg; không cần tìm ô nhập tổng riêng.

### CD10. Bắp nếp và hạt kê: đổi vị bữa sáng

- **Tên chiến dịch:** Bắp nếp và hạt kê: đổi vị bữa sáng
- **Mô tả chiến dịch:** Đợt bắp nếp mới về cùng lượng hạt kê còn lại trong kho tạo thành hai lựa chọn cho bữa sáng tại nhà. Bạn có thể mua riêng bắp hoặc kê, không bắt buộc đặt theo bộ. Bắp tươi cần dùng sớm; hạt kê cần được giữ kín, khô ráo.
- **Nguyên nhân giải cứu:** Thu hoạch theo mùa
- **Ngày bắt đầu:** `2026-10-18`
- **Ngày kết thúc:** `2026-10-22`
- **Lô thứ nhất:** Bắp nếp tươi Đồng Nai — **Lượng mục tiêu:** `35` kg.
- **Lô thứ hai:** Hạt kê vàng Sơn La — **Lượng mục tiêu:** `30` kg.
- **Tổng mục tiêu để đối chiếu:** 65 kg; không cần tìm ô nhập tổng riêng.

### CD11. Đậu xanh, đậu đen: cùng chia sẻ nguồn nông sản

- **Tên chiến dịch:** Đậu xanh, đậu đen: cùng chia sẻ nguồn nông sản
- **Mô tả chiến dịch:** Lượng đậu xanh và đậu đen đóng sẵn cho đợt bán trước chưa được tiêu thụ hết. Cửa hàng mở chiến dịch để luân chuyển phần hàng còn lại, bán theo khối lượng thực tế. Phù hợp cho món chè, món cháo hoặc kết hợp cùng cơm gia đình.
- **Nguyên nhân giải cứu:** Dư nguồn cung
- **Ngày bắt đầu:** `2026-10-18`
- **Ngày kết thúc:** `2026-10-30`
- **Lô thứ nhất:** Đậu xanh nguyên hạt Đắk Lắk — **Lượng mục tiêu:** `40` kg.
- **Lô thứ hai:** Đậu đen Đắk Nông — **Lượng mục tiêu:** `35` kg.
- **Tổng mục tiêu để đối chiếu:** 75 kg; không cần tìm ô nhập tổng riêng.

### CD12. Đậu đỏ và đậu phộng: thêm lựa chọn cho căn bếp

- **Tên chiến dịch:** Đậu đỏ và đậu phộng: thêm lựa chọn cho căn bếp
- **Mô tả chiến dịch:** Nhu cầu trong tuần giảm khiến đậu đỏ và đậu phộng còn lại nhiều hơn dự kiến. Chiến dịch đưa ra mức giá dễ tiếp cận cho các đơn mua theo nhu cầu thực tế. Đậu phộng là nguyên liệu có thể gây dị ứng; vui lòng cân nhắc thành phần trước khi chọn.
- **Nguyên nhân giải cứu:** Nhu cầu giảm
- **Ngày bắt đầu:** `2026-10-18`
- **Ngày kết thúc:** `2026-10-30`
- **Lô thứ nhất:** Đậu đỏ Gia Lai — **Lượng mục tiêu:** `32` kg.
- **Lô thứ hai:** Đậu phộng nhân Tây Ninh — **Lượng mục tiêu:** `37` kg.
- **Tổng mục tiêu để đối chiếu:** 69 kg; không cần tìm ô nhập tổng riêng.

### CD13. Hành lá, ngò rí: gom chút xanh cho bữa cơm

- **Tên chiến dịch:** Hành lá, ngò rí: gom chút xanh cho bữa cơm
- **Mô tả chiến dịch:** Một đơn đặt rau gia vị đã được hủy, để lại hành lá và ngò rí đã tập kết tại cửa hàng. Chiến dịch chia lại lượng hàng để người dùng dễ mua cho bữa ăn trong ngày. Hãy chọn lượng vừa đủ và bảo quản mát sau khi nhận.
- **Nguyên nhân giải cứu:** Đơn hàng bị hủy
- **Ngày bắt đầu:** `2026-10-18`
- **Ngày kết thúc:** `2026-10-21`
- **Lô thứ nhất:** Hành lá Long An — **Lượng mục tiêu:** `12` kg.
- **Lô thứ hai:** Ngò rí Tiền Giang — **Lượng mục tiêu:** `10` kg.
- **Tổng mục tiêu để đối chiếu:** 22 kg; không cần tìm ô nhập tổng riêng.

### CD14. Gừng và sả: hương vị quen thuộc, giá dễ chọn

- **Tên chiến dịch:** Gừng và sả: hương vị quen thuộc, giá dễ chọn
- **Mô tả chiến dịch:** Gừng và sả về cùng đợt với số lượng cao hơn kế hoạch bán. Cửa hàng dành giá giải cứu cho phần còn lại, phù hợp chuẩn bị món kho, món hấp hoặc nước dùng. Mỗi loại được bán riêng theo khối lượng để hạn chế mua dư.
- **Nguyên nhân giải cứu:** Dư nguồn cung
- **Ngày bắt đầu:** `2026-10-18`
- **Ngày kết thúc:** `2026-10-25`
- **Lô thứ nhất:** Gừng tươi Quảng Nam — **Lượng mục tiêu:** `30` kg.
- **Lô thứ hai:** Sả cây Tiền Giang — **Lượng mục tiêu:** `25` kg.
- **Tổng mục tiêu để đối chiếu:** 55 kg; không cần tìm ô nhập tổng riêng.

### CD15. Dưa leo, khổ qua: đón vụ mới cùng nhà vườn

- **Tên chiến dịch:** Dưa leo, khổ qua: đón vụ mới cùng nhà vườn
- **Mô tả chiến dịch:** Dưa leo và khổ qua vào đợt thu hoạch tập trung, lượng hàng về nhiều trong thời gian ngắn. Chiến dịch hỗ trợ tiêu thụ phần đã tập kết tại cửa hàng. Bạn có thể chọn từng loại cho món salad, món xào hoặc món canh theo sở thích.
- **Nguyên nhân giải cứu:** Thu hoạch theo mùa
- **Ngày bắt đầu:** `2026-10-18`
- **Ngày kết thúc:** `2026-10-22`
- **Lô thứ nhất:** Dưa leo Long An — **Lượng mục tiêu:** `40` kg.
- **Lô thứ hai:** Khổ qua Tây Ninh — **Lượng mục tiêu:** `30` kg.
- **Tổng mục tiêu để đối chiếu:** 70 kg; không cần tìm ô nhập tổng riêng.

### CD16. Bầu xanh và mướp hương cho bữa cơm nhẹ nhàng

- **Tên chiến dịch:** Bầu xanh và mướp hương cho bữa cơm nhẹ nhàng
- **Mô tả chiến dịch:** Bầu và mướp đợt này có hình dáng cong, chiều dài và kích cỡ không đồng đều. Cửa hàng chọn phần còn nguyên vẹn để bán theo khối lượng với giá giải cứu. Đây là hai nguyên liệu quen thuộc cho món canh và món luộc trong bữa cơm gia đình.
- **Nguyên nhân giải cứu:** Hình thức không đạt chuẩn
- **Ngày bắt đầu:** `2026-10-18`
- **Ngày kết thúc:** `2026-10-22`
- **Lô thứ nhất:** Bầu xanh Long An — **Lượng mục tiêu:** `45` kg.
- **Lô thứ hai:** Mướp hương Tiền Giang — **Lượng mục tiêu:** `35` kg.
- **Tổng mục tiêu để đối chiếu:** 80 kg; không cần tìm ô nhập tổng riêng.

## 5. Banner — Admin
Mở **Ảnh quảng bá → Tạo ảnh quảng bá**. Chung cho các mục: **ID Banner để trống** để app tự tạo; tự tải ảnh; bật **Đang hiển thị**; để trống **Lý do quản trị**. Ngày bắt đầu `2026-10-18`. Chiến dịch liên kết chọn trong danh sách theo tên sau khi được duyệt và hoạt động. Nếu chưa thấy, tạo banner không liên kết trước hoặc quay lại sau khi chiến dịch đủ điều kiện.

| Banner | Tiêu đề Banner | Thứ tự hiển thị | Ngày bắt đầu | Ngày kết thúc | Chiến dịch liên kết chọn theo tên |
|---|---|---:|---|---|---|
| BN01 | Ngọt lành mùa thu hoạch | 10 | 2026-10-18 | 2026-10-22 | Thanh long và chuối xiêm: ngọt lành mùa thu hoạch |
| BN02 | Rau xanh cho bữa cơm hôm nay | 20 | 2026-10-18 | 2026-10-21 | Bữa cơm xanh cùng cải ngọt và mồng tơi |
| BN03 | Củ không đều, món ngon vẫn đủ | 30 | 2026-10-18 | 2026-10-25 | Cà rốt và khoai tím: ngon từ những củ không đều |
| BN04 | Bữa lẩu tại nhà cùng nấm tươi | 40 | 2026-10-18 | 2026-10-22 | Nấm kim châm, nấm đùi gà cho bữa lẩu tại nhà |
| BN05 | Gạo và đậu cho căn bếp gia đình | 50 | 2026-10-18 | 2026-10-30 | Gạo tẻ và gạo lứt: chia nhỏ lượng hàng còn lại |
| BN06 | Thêm hương vị từ gừng và sả | 60 | 2026-10-18 | 2026-10-25 | Gừng và sả: hương vị quen thuộc, giá dễ chọn |
| BN07 | Đón vụ mới cùng nhà vườn | 70 | 2026-10-18 | 2026-10-22 | Dưa leo, khổ qua: đón vụ mới cùng nhà vườn |
| BN08 | Bầu, mướp cho bữa cơm nhẹ nhàng | 80 | 2026-10-18 | 2026-10-22 | Bầu xanh và mướp hương cho bữa cơm nhẹ nhàng |
| BN09 | Mua vừa đủ, sử dụng trọn vẹn | 90 | 2026-10-18 | 2026-10-30 | Không liên kết chiến dịch |
| BN10 | Cùng RescueFarm kết nối nông sản | 100 | 2026-10-18 | 2026-10-30 | Không liên kết chiến dịch |

## 6. Một vài mô tả cửa hàng để chọn thêm
Phần này là tùy chọn nếu bạn muốn hoàn thiện hồ sơ công khai của seller đang có. Chỉ dùng cho hồ sơ thử nghiệm hoặc thay bằng nội dung đúng của cửa hàng; giữ nguyên thông tin ngân hàng, người đại diện và địa chỉ thật. Không cần tạo thêm users để nhập bộ sản phẩm trên.

**Tên cửa hàng:** Góc Nông Sản Xanh

**Mô tả cửa hàng:** Góc Nông Sản Xanh kết nối rau củ và trái cây theo mùa với bữa ăn hằng ngày. Cửa hàng cập nhật lượng hàng theo từng lô để bạn dễ chọn vừa nhu cầu. Các đợt giải cứu ưu tiên nông sản còn phù hợp sử dụng nhưng đang dư lượng hoặc không đồng đều về hình thức.

**Tên cửa hàng:** Bếp Nhà Mùa Vụ

**Mô tả cửa hàng:** Bếp Nhà Mùa Vụ mang đến những nguyên liệu quen thuộc cho căn bếp gia đình: rau xanh, củ quả, nấm và gia vị tươi. Bạn có thể xem nguồn gốc, giá và thông tin lô trước khi đặt. Mỗi đơn vừa đủ là một cách giúp nông sản được sử dụng trọn vẹn.

**Tên cửa hàng:** Trạm Nông Sản Mỗi Ngày

**Mô tả cửa hàng:** Trạm Nông Sản Mỗi Ngày tập hợp nông sản đang cần tiêu thụ theo từng đợt. Cửa hàng bán theo khối lượng và cập nhật các chiến dịch giải cứu để người dùng dễ theo dõi. Vui lòng xem kỹ thời gian, địa điểm nhận và lượng khả dụng trước khi đặt hàng.

## 7. Nhập ít trước để trang chủ có nội dung
Nếu chưa muốn nhập hết, bắt đầu với **SP01–SP12 và 12 lô tương ứng**, sau đó tạo **CD01–CD06**, **BN01–BN03**. Như vậy trang chủ và trang danh mục đã có cả sản phẩm, chiến dịch và banner; bảng tin cộng đồng dùng chính các chiến dịch này. Sau đó bổ sung các nhóm nấm, ngũ cốc, đậu và gia vị để danh mục đa dạng hơn.

Ảnh/banner đẹp sẽ cải thiện phần nhìn, nhưng số đã bán, lượng đã giải cứu, đánh giá và biểu đồ phải đến từ luồng sử dụng thực tế. Bộ này không tạo sẵn số liệu thành tích hoặc đánh giá giả. Chiến dịch mới bắt đầu ở 0% là bình thường.

## Đối chiếu với code đã đọc
- Form sản phẩm: `app/src/main/res/layout/fragment_product_editor.xml`; `ui/product/ProductEditorFragment.java`.
- Lô hàng và kiểm tra ngày/giá: `fragment_batch_manager.xml`; `ui/product/ProductValidator.java`.
- Chiến dịch: `fragment_campaign_editor.xml`; `ui/campaign/CampaignEditorFragment.java`; `CampaignValidator.java`; backend `functions/campaign-media.js`.
- Bảng tin cộng đồng: `ui/feed/FeedFragment.java` dùng `CampaignViewModel.getPublicCampaigns()` và mở `campaignDetailFragment`; không dùng Post làm nội dung bảng tin hiện tại.
- Danh mục/banner được dựng bằng Java: `ui/admin/AdminResourceFragment.java`.
- Tên lựa chọn hiển thị: `ui/common/EnumLabels.java`. Các đường dẫn Java ở trên nằm dưới `app/src/main/java/com/rescuefarm/`.
