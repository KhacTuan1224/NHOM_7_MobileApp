# Càng mua càng giảm và các phương thức giải cứu

## Chính sách đã chốt (phiên bản 2, hạ ngưỡng ngày 21/10/2026)

Áp dụng cho Customer và Guest trong **giỏ hàng/checkout sản phẩm thường và sản phẩm thuộc chiến dịch**. Không gộp lịch sử mua nhiều ngày; chỉ các dòng được chọn trong cùng một lần đặt hàng. Không cộng kg, hộp, quả hoặc số dòng để suy ra cùng một số lượng.

1. Giữ giá giải cứu và chính sách khuyến mãi sản phẩm hiện có. Customer vẫn cần lưu khuyến mãi còn hiệu lực; Guest không được hưởng voucher riêng trong ví Customer. Bậc số lượng của một sản phẩm được xét trên tổng lượng của chính sản phẩm đó qua các lô đã chọn, không trộn đơn vị của sản phẩm khác.
2. Cộng tiền hàng sau khuyến mãi theo từng Seller: từ **50.000đ giảm 3%**, từ **120.000đ giảm 5%**. Chọn bậc cao nhất, không cộng 3% với 5%. Một sản phẩm mua nhiều hoặc nhiều sản phẩm cùng Seller đều đóng góp vào ngưỡng tiền này. Phần giảm này do Seller chịu theo chính sách nền tảng đã chốt.
3. Cộng tiền hàng sau bước 2 của các Seller có tiền hàng lớn hơn 0. Có ít nhất **2 Seller** và từ **80.000đ giảm 2%**, từ **180.000đ giảm 4%**. Chọn bậc cao nhất; phần này do nền tảng tài trợ.
4. Phí giao không tính vào ngưỡng và không được giảm. Mỗi Seller vẫn có phí giao/điểm nhận và thanh toán riêng. Không hứa một chuyến giao chung.

Đơn đã tạo giữ nguyên số tiền, phần giảm và phiên bản chính sách đã chụp; không áp dụng hồi tố ngưỡng mới cho đơn cũ. Giỏ hàng và checkout đọc ngưỡng trực tiếp từ backend nên không hardcode mức tiền trong APK.

Ví dụ: sau khuyến mãi sản phẩm, Seller A có 60.000đ và Seller B có 60.000đ:

- Mỗi Seller giảm 1.800đ, còn 58.200đ; cộng lại 116.400đ.
- Đủ bậc nền tảng 2%: giảm tiếp 2.328đ, phân bổ 1.164đ cho mỗi đơn.
- Người mua trả tiền hàng **114.072đ**, mỗi đơn **57.036đ**, cộng phí giao riêng nếu có.
- Nếu mỗi Seller có 90.000đ, sau giảm cùng Seller tổng còn 174.600đ: chỉ đạt bậc nhiều Seller 2%, chưa đạt 4%.

## Giao diện và tính toàn vẹn

- Giỏ hàng tự lấy báo giá cho các dòng được chọn, hiển thị giảm cùng Seller/nhiều Seller và các ngưỡng còn thiếu. Báo giá giỏ dùng nhận tại cửa hàng để tính riêng tiền hàng; chưa tính phí giao cuối.
- Checkout hiển thị từng khoản giảm, phí giao và tổng phải trả cho từng Seller. Chỉ hiện chuyển khoản khi tất cả Seller đã hỗ trợ; từng đơn có QR/thông tin chuyển khoản riêng.
- Tối đa 20 lô và 5 Seller/lần đặt. Sản phẩm hết hạn, Seller bị khóa, thiếu kho, chiến dịch không hợp lệ vẫn bị từ chối theo quy tắc hiện có. Các dòng của một Seller vẫn phải đáp ứng quy tắc một chiến dịch của checkout hiện tại; trường hợp không tương thích phải chọn lại và đặt riêng.
- Backend đọc lại sản phẩm, voucher, kho, chiến dịch và biểu phí trong giao dịch Firestore. Tất cả đơn con cùng được tạo và giữ kho; một phần thất bại thì toàn bộ lần đặt không được tạo. Không có trường hợp đặt trước một Seller rồi âm thầm mất ưu đãi Seller khác.
- `expectedSubtotal`, `expectedShippingFee`, `expectedQuantityDiscount` giúp APK phát hiện báo giá thay đổi. Số tiền cuối luôn do backend tính, không nhận số giảm/đơn giá từ client.
- Mỗi lần đặt có requestId; thử lại trả cùng danh sách đơn, không giữ kho hoặc dọn giỏ hai lần. Mỗi đơn có biên nhận, thanh toán, vận chuyển, thông báo và mã tra cứu riêng. Guest lưu token cho từng đơn.
- Làm tròn VND và phân bổ phần giảm theo tỷ trọng tiền hàng; tổng phần chia luôn bằng tổng ưu đãi. Lưu `sellerVolumeDiscount` và `platformVolumeDiscount` cả trên đơn và các dòng để đối soát.
- Hủy đơn/nhận hàng vẫn xử lý riêng từng đơn như trước. Giá đã chốt của các đơn khác không tự tăng lại, kể cả một Seller hủy sau đó; lần mua mới phải xét ngưỡng lại từ đầu. Ưu đãi không phải khoản tiền hoàn cho đơn hủy. Không có cơ chế tự gộp các checkout cũ hay tự hoàn tiền SePay mới trong thay đổi này.
- Hộp bí ẩn và Flash Sale hiện dùng `rescueOffers/rescueClaims` với giá gói riêng, không đi qua giỏ sản phẩm; **không tự chồng ưu đãi mua nhiều vào các suất này**. Guest vẫn khám phá được nhưng cần đăng nhập Customer để giữ suất theo luồng hiện có.

## Tài trợ nền tảng: dữ liệu và vận hành

`totalAmount` là số khách trả sau mọi ưu đãi. `sellerReceivableAmount = subtotal - sellerVolumeDiscount + shippingFee` là số Seller có quyền nhận trước khi trừ phần nền tảng tài trợ. Khoản chênh được ghi riêng trong `platformDiscountClaims/{orderId}`, trạng thái `UNSETTLED`, kèm điều kiện đơn phải `DELIVERED`. Collection này không mở quyền đọc/ghi trực tiếp cho client. Không coi trạng thái trong ledger là bằng chứng đã chi tiền.

**Chưa có bộ máy chuyển tiền trợ giá từ nền tảng sang Seller.** Người vận hành cần có ngân sách, đối soát đơn thật sự DELIVERED, chưa hoàn/hủy và chưa được thanh toán trợ giá, rồi chi trả và cập nhật hồ sơ đối soát quản trị. Các đơn chưa giao hoặc đã hủy không được chi trợ giá. Thống kê doanh thu hiện có vẫn phản ánh số tiền khách trả; không giả định phần trợ giá chưa chi là tiền đã thu. Không tự sửa tiền khách đã trả hoặc QR cũ để bù phần này.

## Setup

1. Deploy backend Render gồm `commerce.js`, `purchase-volume.js` và các file backend hiện có. Root Directory `functions`, Build Command `npm ci`, Start Command `npm start`. Không cần thêm env, Firestore index hay thay Rules cho cơ chế mua nhiều này. Mức mặc định trong `purchase-volume.js` là các mức đã được duyệt ở trên.
2. Cài APK mới. APK mới cần backend có `buyMorePolicy`; nếu chưa cập nhật, checkout sẽ yêu cầu cập nhật máy chủ thay vì hứa mức giảm chưa có.
3. Thử giỏ một Seller đạt 50.000đ/120.000đ; tiếp tục thử hai Seller mỗi bên 60.000đ. Kiểm tra báo giá từng Seller, hai đơn được tạo, tiền thanh toán từng đơn và kho được giữ đúng. Với Customer, thử lưu/bỏ lưu voucher để kiểm tra mức tiền xét ngưỡng thay đổi.
4. Quyền lợi nhiều Seller do nền tảng tài trợ nên vận hành chi trả theo mục trên trước khi công bố bán thật.

Kiểm thử: `node --test functions/purchase-volume.test.cjs`; bộ integration commerce chạy trên Firestore emulator demo qua `tests/firestore`.

## Phân loại những gì đã triển khai

| Nhóm | Chức năng hiện có | Vai trò thực tế |
| --- | --- | --- |
| Bán sản phẩm/lô | Nông sản có giá giải cứu, tồn theo lô và hạn dùng | Luồng mua hàng nền tảng |
| Chiến dịch | Bài chiến dịch có câu chuyện, tiến độ, sản phẩm/lô và tương tác | Gom nông sản cần cứu theo đợt; bài viết dẫn đến chính nguồn hàng chiến dịch |
| Điểm cố định | Địa chỉ/GPS của cửa hàng hoặc điểm chiến dịch | Điểm nhận và chỉ đường |
| Điểm di động | Seller cập nhật GPS khi đang di chuyển; người mua tìm/xem vị trí mới | Điểm nhận di động, có kiểm tra độ mới của vị trí |
| Tìm quanh tôi | GPS của người mua, tìm/lọc và khoảng cách đến nơi cung cấp | Kênh khám phá, không phải một cơ chế giá/đặt hàng độc lập |
| Hộp bí ẩn | Gói nông sản theo nhóm, animation mở hộp, giữ suất | Phương thức đóng gói và bán giải cứu riêng |
| Flash Sale | Gói giờ chót tối đa 2 giờ, giới hạn suất, thời hạn, gần đây/nhắc FCM | Phương thức bán gấp theo thời gian |
| Khuyến mãi sản phẩm | Giảm cố định/%, bậc số lượng theo sản phẩm, ví khuyến mãi Customer | Công cụ giá hiện có, được giữ nguyên |
| Càng mua càng giảm | Theo tiền hàng cùng Seller và nhiều Seller | Công cụ giá bổ sung trên giỏ checkout thông thường |

**Mua qua bài viết và tìm qua GPS không phải hai loại đơn hàng khác nhau.** Người mua có thể đọc một bài chiến dịch, xem GPS điểm cố định/di động của chính chiến dịch đó rồi đặt các lô của chiến dịch. Cũng có thể tìm bằng GPS trước, sau đó đọc bài và mua cùng nguồn hàng. Cả hai đi vào cùng luồng kiểm tra tồn và đặt đơn; không ghi hai lần nông sản đã giải cứu.

Các phương thức đã gỡ và không được phục hồi: Mua chung, Đăng ký định kỳ, Thương lượng sỉ, Quyên góp tổ chức. Các chế độ nhận/giao là DELIVERY, PICKUP và ON_SITE_RESCUE; đó là cách nhận hàng, không phải phương thức giải cứu mới.
