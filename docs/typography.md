# Font chữ RescueFarm

- Tiêu đề màn hình, tiêu đề mục và tên trong thẻ: IBM Plex Sans.
- Nội dung, số liệu, nhãn, nút, ô nhập, menu và chữ trong biểu đồ: Barlow.
- Font được đóng gói trong res/font, không cần tải từ Internet khi chạy app. Các file đã được kiểm tra có glyph tiếng Việt.
- Family XML khai báo normal/italic và các trọng lượng 400/500/600/700. Các cỡ chữ, màu sắc và quy tắc bố cục được giữ theo giao diện hiện tại.

Theme sáng và tối cùng kế thừa Theme.RescueFarm.Foundation trong values/typography.xml. Các TextAppearance Display/Headline/Title dùng IBM Plex Sans; Body/Label dùng Barlow. Các kiểu TextAppearance.RescueFarm.* trong styles.xml khai báo font riêng để không bị font mặc định của Material ghi đè.

Với tiêu đề mới trong XML, dùng TextAppearance.RescueFarm.SectionTitle, TitleMedium hoặc style tiêu đề khác đã có. Với TextView tạo bằng Java, gọi AppTypography.title(view). Nội dung dùng theme Barlow; khi cần đặt trực tiếp cho TextView/Canvas dùng AppTypography.body(view) hoặc AppTypography.bodyTypeface(context). Không ép Typeface.DEFAULT hay sans-serif vì sẽ làm mất font của app. Chữ in đậm trong nội dung vẫn dùng Barlow Bold, không tự biến thành tiêu đề.

Nguồn font chính thức:

- IBM Plex Sans: https://github.com/google/fonts/tree/main/ofl/ibmplexsans
- Barlow: https://github.com/google/fonts/tree/main/ofl/barlow

Giấy phép SIL Open Font License đi kèm tại docs/licenses/fonts và được đóng gói trong assets/licenses/fonts của APK.

## Quy chuẩn màn chi tiết

Các màn chi tiết dùng chung `TextAppearance.RescueFarm.Detail.*` trong `res/values/detail_typography.xml`:

| Vai trò | Font | Cỡ chữ | Màu mặc định |
| --- | --- | --- | --- |
| PageTitle: tiêu đề trang | IBM Plex Sans Bold | 20sp | rescue_on_surface |
| Name: tên nội dung chính | IBM Plex Sans Bold | 22sp | rescue_on_surface |
| Section: tiêu đề mục | IBM Plex Sans Bold | 18sp | rescue_on_surface |
| Body: nội dung | Barlow | 15sp | rescue_on_surface |
| Meta: thông tin phụ | Barlow | 13sp | rescue_on_surface_variant |
| Price: giá nổi bật | Barlow Bold | 20sp | rescue_primary |

Tiêu đề trang đặt ở hàng đầu màn, căn trái cạnh icon quay lại 48dp và căn giữa theo chiều dọc; lề nội dung 16dp. Sản phẩm/chiến dịch/đơn hàng dùng `view_detail_header.xml`, gắn nhãn và điều hướng qua `DetailTypography.bindHeader`. Header sẵn có của quản trị/danh mục/cửa hàng/gói giải cứu dùng cùng vai trò PageTitle. Bố cục sản phẩm trên tablet vẫn có header toàn chiều rộng bên trên hai cột.

Nội dung Java dùng `DetailTypography.body/meta/section` hoặc font tương ứng từ `AppTypography`. Giá, trạng thái và cảnh báo có thể ghi đè màu theo ý nghĩa; không thay toàn bộ thành một màu cố định. Các cỡ sp vẫn tôn trọng cài đặt phóng chữ của Android.
